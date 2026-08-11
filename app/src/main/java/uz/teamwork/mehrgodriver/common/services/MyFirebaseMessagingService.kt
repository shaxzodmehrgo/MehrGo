package uz.teamwork.mehrgodriver.common.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.ACTION_ORDER_PRICE_RECALCULATED
import uz.teamwork.mehrgodriver.common.Constants.ACTION_VERIFICATION_STATUS_CHANGED
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_BALANCE_ID
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_NOTIFY_DRIVER_ID
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_PUSH_KEY
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_REPRICED_ORDER_ID
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DATA_BALANCE_ID
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DATA_NOTIFY_DRIVER_ID
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DOCUMENT_APPROVED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DOCUMENT_REJECTED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DRIVER_BONUS_CREDITED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_NEW_DRIVER_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Constants.FCM_TYPE_ORDER_PRICE_RECALCULATED
import uz.teamwork.mehrgodriver.common.Constants.FCM_TYPE_PAYLOV_WITHDRAWAL
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME_NEW_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.shared_pref.FcmTokenManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegisterDeviceTokenUC
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registerDeviceTokenUC: RegisterDeviceTokenUC

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag("FCM").d("onNewToken token=%s", token)
        FcmTokenManager.saveToken(token)
        FcmTokenManager.markUnsynced()

        // Reactive half of the contract: push the new token to the backend immediately
        // when the driver is logged in. If they aren't, the defensive sync on the next
        // login / version-driver call will pick it up.
        if (!UserManager.getToken().isNullOrEmpty()) {
            Timber.tag("FCM").d("onNewToken: user logged in, POSTing /device-token")
            scope.launch {
                registerDeviceTokenUC.invoke(token).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            FcmTokenManager.markSynced()
                            Timber.tag("FCM").d(
                                "onNewToken: /device-token OK, token_registered=%s",
                                resource.data?.data?.tokenRegistered
                            )
                        }

                        is Resource.Error -> Timber.tag("FCM")
                            .e("onNewToken: /device-token FAILED: %s", resource.message)

                        is Resource.Loading -> Timber.tag("FCM")
                            .d("onNewToken: /device-token loading")
                    }
                }
            }
        } else {
            Timber.tag("FCM").d("onNewToken: user not logged in, deferring sync to next login")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.tag("FCM").d(
            "onMessageReceived from=%s key=%s data=%s notification=%s",
            message.from,
            message.data["key"],
            message.data,
            message.notification
        )

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        // Only raise a banner for a push that actually HAS something to say.
        // Data-only pushes (the reprice signal is one) carry no notification
        // block and no title/body keys, so the fallbacks above degraded them to
        // "MehrGo Driver" with an empty body — a heads-up notification with
        // sound, on IMPORTANCE_HIGH, stacking one per silent push because the
        // id is Random.nextInt(). Gated on content rather than on the key, so
        // any future content-less type is covered too, and the waiting-surcharge
        // push (same mechanism, but genuinely user-facing) still shows as long
        // as the backend sends its text.
        val hasContent = message.notification != null ||
                !message.data["title"].isNullOrBlank() ||
                !message.data["body"].isNullOrBlank()
        if (hasContent) showNotification(title, body, message.data)

        // A moderation decision changed document state — tell any open screen to re-fetch.
        val key = message.data["key"]
        if (key == FCM_KEY_DOCUMENT_APPROVED || key == FCM_KEY_DOCUMENT_REJECTED) {
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(ACTION_VERIFICATION_STATUS_CHANGED))
        }

        // The server repriced a live order (observed in prod when the CLIENT toggles a service
        // mid-trip). This is the ONLY zero-latency signal for that event: the socket's
        // order_price_updated rides the GPS-batch cycle and lands seconds later, and /user/me
        // only refreshes when something else asks it to. Forward it so the open trip screen
        // re-pulls immediately.
        //
        // The push carries a `price`, but we deliberately do NOT use it — it is a hint that
        // something changed, not a quotable amount. The trip screen re-fetches order-gps/fare
        // and shows what the server itself returns.
        if (message.data["type"] == FCM_TYPE_ORDER_PRICE_RECALCULATED) {
            val orderId = message.data["order_id"]?.toIntOrNull()
            Timber.tag("FCM").d(
                "order repriced (order_id=%s, reason=%s) -> asking trip screen to re-pull fare",
                orderId, message.data["reason"]
            )
            if (orderId != null) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    Intent(ACTION_ORDER_PRICE_RECALCULATED)
                        .putExtra(EXTRA_REPRICED_ORDER_ID, orderId)
                )
            }
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundResId = resolveSoundResource()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION,
                NOTIFICATION_CHANNEL_NAME_NEW_NOTIFICATION,
                IMPORTANCE_HIGH
            )
            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundResId),
                null
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            buildTapIntent(data),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
            else FLAG_UPDATE_CURRENT
        )

        val notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(title)
                .setContentText(Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY))
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(Random.nextInt(), notification)
    }

    private fun buildTapIntent(data: Map<String, String>): Intent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Most pushes route via data.key; the Paylov withdrawal decision comes as data.type.
        when (val key = data["key"] ?: data["type"]) {
            FCM_TYPE_PAYLOV_WITHDRAWAL -> {
                intent.putExtra(EXTRA_PUSH_KEY, key)
                Timber.tag("FCM").d(
                    "tap intent => Paylov withdrawal history, request_id=%s status=%s",
                    data["request_id"], data["status"]
                )
            }

            FCM_KEY_DRIVER_BONUS_CREDITED -> {
                intent.putExtra(EXTRA_PUSH_KEY, key)
                data[FCM_KEY_DATA_BALANCE_ID]?.let { intent.putExtra(EXTRA_BALANCE_ID, it) }
                Timber.tag("FCM")
                    .d("tap intent => Earnings, balance_id=%s", data[FCM_KEY_DATA_BALANCE_ID])
            }

            FCM_KEY_NEW_DRIVER_NOTIFICATION -> {
                intent.putExtra(EXTRA_PUSH_KEY, key)
                data[FCM_KEY_DATA_NOTIFY_DRIVER_ID]?.let {
                    intent.putExtra(EXTRA_NOTIFY_DRIVER_ID, it)
                }
                Timber.tag("FCM").d(
                    "tap intent => Notifications, notify_driver_id=%s",
                    data[FCM_KEY_DATA_NOTIFY_DRIVER_ID]
                )
            }

            FCM_KEY_DOCUMENT_APPROVED, FCM_KEY_DOCUMENT_REJECTED -> {
                intent.putExtra(EXTRA_PUSH_KEY, key)
                Timber.tag("FCM").d("tap intent => Verification")
            }

            else -> Timber.tag("FCM").d("tap intent => MainActivity (no routing for key=%s)", key)
        }
        return intent
    }

    /**
     * The backend pushes `"sound": "new_chat.wav"` in both Android and APNS payloads
     * (see `driver_fcm_mobile_integration.md` §2). Use that file if a release has
     * shipped it under res/raw; otherwise fall back to the existing notification sound
     * so the channel doesn't end up silent.
     */
    private fun resolveSoundResource(): Int {
        val newChatId = resources.getIdentifier("new_chat", "raw", packageName)
        return if (newChatId != 0) newChatId else R.raw.audio_notification
    }
}
