package uz.teamwork.mehrgodriver.common.socket

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_ORDER_DATA_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.KEY_ORDER_DATA
import uz.teamwork.mehrgodriver.common.Constants.KEY_SOCKET_LISTENER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_NEW
import uz.teamwork.mehrgodriver.common.Constants.ORDER_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED_PRIVATE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_GPS_BATCH
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW_PRIVATE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_PRICE_UPDATED
import uz.teamwork.mehrgodriver.common.Constants.RECEIVE_PONG
import uz.teamwork.mehrgodriver.common.fare.GpsBatchSocketChannel
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.socket.MySocketListener.Companion.settledOrderFor
import uz.teamwork.mehrgodriver.domain.model.Order

class MySocketListener(
    private val context: Context,
    private val fareChannel: GpsBatchSocketChannel? = null
) : WebSocketListener() {
    companion object {
        var isSocketListener = MutableLiveData(false)

        var listenerNotificationData = MutableLiveData<String>()
        var listenerPongData = MutableLiveData<String>()

        /**
         * `order_completed` settlement frames — the full Order carrying the SERVER-judged
         * final `price` (the complete REST response is a bare user object with no receipt, so
         * this frame is the only place the billed figure reaches the client).
         *
         * Stored ID-KEYED rather than last-wins: cancel frames are branch-wide broadcasts
         * (see the order_cancelled id-guard), so a completion frame for ANOTHER order could
         * otherwise evict the driver's own before the receipt reads it. The LiveData below is
         * only the change SIGNAL (replay covers frames that land before an observer
         * registers); consumers look their order up via [settledOrderFor].
         */
        private val settledOrders = object : LinkedHashMap<Int, Order>(8, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Order>) =
                size > 8
        }

        fun settledOrderFor(orderId: Int): Order? =
            synchronized(settledOrders) { settledOrders[orderId] }

        var listenerOrderCompletedData = MutableLiveData<SocketOrderResponse?>()
    }

    // Lenient shared Gson so a malformed numeric field in a socket frame can never throw here
    // (this parser feeds the LocalBroadcast that the tracking service re-parses — crash A).
    private val gson = uz.teamwork.mehrgodriver.common.AppGson.gson

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        Timber.d("onOpen: Connected")

        fareChannel?.attach(webSocket)
        fareChannel?.setConnected(true)

        isSocketListener.postValue(true)
        sendSocketListenerToOtherComponents(true)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Timber.d("Received: $text")

        try {
            val baseResponse = gson.fromJson(text, SocketBaseResponse::class.java)
            when (baseResponse.key) {
                RECEIVE_PONG -> {
                    listenerPongData.postValue(text)
                }

                NOTIFICATION_NEW -> {
                    listenerNotificationData.postValue(text)
                }

                // Order — new offers only reach the driver while online (ORDER_GPS_FIXES.md §5).
                ORDER_NEW_PRIVATE -> {
                    if (UserManager.getStatusValue() == DRIVER_ACTIVE) {
                        sendOrderDataToOtherComponents(text)
                    }
                }

                ORDER_NEW -> {
                    if (UserManager.getStatusValue() == DRIVER_ACTIVE) {
                        sendOrderDataToOtherComponents(text)
                    }
                }

                ORDER_ACCEPTED -> {
                    sendOrderDataToOtherComponents(text)
                }

                ORDER_CANCELLED_PRIVATE -> {
                    sendOrderDataToOtherComponents(text)
                }

                ORDER_CANCELLED -> {
                    sendOrderDataToOtherComponents(text)
                }

                // Settlement: the server-judged final bill. Parsed here (defensively) instead
                // of broadcast-as-text because the sole consumers are the finish receipt paths,
                // which need the typed Order.
                ORDER_COMPLETED -> {
                    runCatching { gson.fromJson(text, SocketOrderResponse::class.java) }
                        .getOrNull()
                        // `data` is DECLARED non-null but Gson (Unsafe instantiation) leaves a
                        // missing/null payload field null at runtime — same trap the fare
                        // branch below guards with `envelope.data?.let`. Never publish a
                        // poisoned envelope: consumers deref data.id.
                        ?.takeIf { @Suppress("SENSELESS_COMPARISON") (it.data != null) }
                        ?.let { envelope ->
                            synchronized(settledOrders) {
                                settledOrders[envelope.data.id] = envelope.data
                            }
                            listenerOrderCompletedData.postValue(envelope)
                        }
                }

                // Server-side fare: ack for a batch we sent, and live-price push.
                // Parsed defensively: this is the only socket branch that decodes a nested
                // money payload, and a malformed or partial frame here used to be able to
                // kill the app (see the catch below).
                ORDER_GPS_BATCH, ORDER_PRICE_UPDATED -> {
                    runCatching { gson.fromJson(text, SocketFareResponse::class.java) }
                        .getOrNull()
                        ?.let { envelope ->
                            envelope.data?.let { fareChannel?.onSocketFare(envelope.key, it) }
                        }
                }
            }
        } catch (e: Exception) {
            // Was `catch (e: JSONException)` — org.json's exception, which Gson NEVER throws.
            // Gson raises JsonSyntaxException/JsonParseException (RuntimeExceptions), so a
            // malformed frame escaped this handler entirely and crashed the socket thread.
            // Same failure mode that took the app down on `"waiting_cost": 2613.33`.
            e.printStackTrace()
            Timber.e("Socket message parse failed: $e")
        }
    }

    private fun sendSocketListenerToOtherComponents(data: Boolean) {
        val intent = Intent(ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST)
        intent.putExtra(KEY_SOCKET_LISTENER, data)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun sendOrderDataToOtherComponents(data: String) {
        val intent = Intent(ACTION_SEND_ORDER_DATA_BY_BROADCAST)
        intent.putExtra(KEY_ORDER_DATA, data)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        webSocket.close(code, reason)

        fareChannel?.setConnected(false)

        Timber.d("onClosing: $code / $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        webSocket.close(code, reason)

        fareChannel?.setConnected(false)

        Timber.d("onClosed: $code / $reason")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        fareChannel?.setConnected(false)

        // Reflect the drop right away so the "reconnecting" badge is truthful instead of
        // staying falsely green until the ~30s heartbeat notices. This does NOT itself
        // reconnect — the service observer reacts only to `true`, so the paced heartbeat
        // stays the single reconnect driver and a down server can't trigger a tight loop.
        isSocketListener.postValue(false)

        Timber.d("onFailure: ${t.message}")
    }
}
