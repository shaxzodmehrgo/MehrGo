package uz.teamwork.mehrgodriver.common.fare

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import timber.log.Timber
import uz.teamwork.mehrgodriver.domain.model.fare.FareGpsBatchRequest
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Wraps the existing tracking websocket for sending `order_gps_batch` frames and
 * awaiting the matching `order_gps_batch` ack. Acks are not message-id'd by the
 * server, so we use a FIFO of pending deferreds: send N batches, complete N acks
 * in arrival order. In practice the [GpsBatchUploader] serialises sends, so the
 * queue depth is at most 1.
 */
class GpsBatchSocketChannel(
    private val gson: Gson
) {

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var connected: Boolean = false

    /**
     * Server-initiated `order_price_updated` pushes (recompute between batches — e.g. a
     * waiting-charge event). Installed by [GpsBatchUploader.start]; without it the push
     * payload (incl. §7.4 `events`) would be silently dropped.
     */
    @Volatile
    var onPricePush: ((FareResponse) -> Unit)? = null

    private val pendingAcks = ConcurrentLinkedQueue<CompletableDeferred<FareResponse?>>()

    fun attach(webSocket: WebSocket?) {
        this.webSocket = webSocket
        connected = webSocket != null
    }

    fun setConnected(value: Boolean) {
        connected = value
        if (!value) {
            // Drain pending acks — let the uploader fall through to REST.
            while (true) {
                val d = pendingAcks.poll() ?: break
                d.complete(null)
            }
        }
    }

    fun isConnected(): Boolean = connected && webSocket != null

    suspend fun sendBatchAwaitAck(
        orderId: Int,
        request: FareGpsBatchRequest
    ): FareResponse? {
        val ws = webSocket ?: return null
        val deferred = CompletableDeferred<FareResponse?>()
        pendingAcks.offer(deferred)

        val payload = JsonObject().apply {
            addProperty("key", KEY_ORDER_GPS_BATCH)
            add(
                "data",
                gson.toJsonTree(
                    SocketBatchData(
                        order_id = orderId,
                        points = request.points,
                        waiting_time = request.waitingTime,
                        waiting_time_ontheway = request.waitingTimeOntheway
                    )
                )
            )
        }

        val ok = ws.send(gson.toJson(payload))
        if (!ok) {
            pendingAcks.remove(deferred)
            return null
        }

        val result = try {
            withTimeoutOrNull(ACK_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Timber.w(e, "sendBatchAwaitAck failed")
            null
        }
        // Whether we got an ack, timed out, or hit an error, the deferred must not
        // linger in the queue — otherwise the *next* batch's ack would complete
        // this stale slot and the new batch would block until timeout.
        if (result == null) pendingAcks.remove(deferred)
        return result
    }

    /**
     * Called by [MySocketListener] when a frame's key is `order_gps_batch`
     * (ack) or `order_price_updated` (live push). Returns true when the message
     * was consumed and shouldn't be re-broadcast.
     */
    fun onSocketFare(key: String, data: FareResponse): Boolean {
        return when (key) {
            KEY_ORDER_GPS_BATCH -> {
                val d = pendingAcks.poll()
                d?.complete(data)
                true
            }

            KEY_ORDER_PRICE_UPDATED -> {
                // Forward the push (live block + one-shot billing events) to the uploader's
                // publish pipe — the events only come in THIS payload, never repeated.
                onPricePush?.invoke(data)
                true
            }

            else -> false
        }
    }

    // Gson omits nulls by default, so unchanged timers stay off the wire (§7.1 rule 1).
    private data class SocketBatchData(
        val order_id: Int,
        val points: List<uz.teamwork.mehrgodriver.domain.model.fare.FareGpsPoint>,
        val waiting_time: Long? = null,
        val waiting_time_ontheway: Long? = null
    )

    companion object {
        const val KEY_ORDER_GPS_BATCH = "order_gps_batch"
        const val KEY_ORDER_PRICE_UPDATED = "order_price_updated"
        private const val ACK_TIMEOUT_MS = 5_000L
    }
}
