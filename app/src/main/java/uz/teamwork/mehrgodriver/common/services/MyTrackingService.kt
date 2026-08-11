package uz.teamwork.mehrgodriver.common.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.graphics.BitmapFactory
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import timber.log.Timber
import uz.teamwork.mehrgodriver.BuildConfig
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.ACTION_ARRIVED_AT_PICKUP
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_ORDER_DATA_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SHOW_DIALOG_INSIDE_APP
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SHOW_MY_TRACKING_FRAGMENT
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_SERVICE
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_TIME_WAIT
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_WAY
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_SERVICE
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_TIME_WAIT
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_MAKTABGO_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_MAKTABGO_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.BASE_URL_FOR_SOCKET
import uz.teamwork.mehrgodriver.common.Constants.KEY_ORDER_DATA
import uz.teamwork.mehrgodriver.common.Constants.MAX_MISSED_SOCKET_PONGS
import uz.teamwork.mehrgodriver.common.Constants.MINIMAL_SPEED
import uz.teamwork.mehrgodriver.common.Constants.MINIMAL_TIME_CONNECT_SOCKET
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID_CANCEL_MY_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID_NEW_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_ID_NEW_PRIVATE_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME_CANCEL_MY_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME_NEW_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME_NEW_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_CHANNEL_NAME_NEW_PRIVATE_ORDER
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_ID
import uz.teamwork.mehrgodriver.common.Constants.NOTIFICATION_NEW
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED_PRIVATE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW_PRIVATE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_ARRIVED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_GONE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_STARTED
import uz.teamwork.mehrgodriver.common.Constants.RECEIVE_PONG
import uz.teamwork.mehrgodriver.common.Constants.WAITING_TIME_TURN_AUTO
import uz.teamwork.mehrgodriver.common.Constants.WAIT_AUTO_STOP_SPEED_KMH
import uz.teamwork.mehrgodriver.common.Constants.WEB_SOCKET_CLOSE_CODE
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.fare.GpsBatchSocketChannel
import uz.teamwork.mehrgodriver.common.fare.GpsBatchUploader
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.common.services.MyTrackingService.Companion.finishNoticeShown
import uz.teamwork.mehrgodriver.common.services.MyTrackingService.Companion.lastLocationWholeApp
import uz.teamwork.mehrgodriver.common.services.MyTrackingService.Companion.rideAutoStartedBySpeed
import uz.teamwork.mehrgodriver.common.shared_pref.ErrorRequestManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.socket.MySocketListener
import uz.teamwork.mehrgodriver.common.socket.SocketNotificationResponse
import uz.teamwork.mehrgodriver.common.socket.SocketOrderResponse
import uz.teamwork.mehrgodriver.common.socket.SocketPongResponse
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.model.requests.ErrorRequest
import uz.teamwork.mehrgodriver.domain.use_case.locale.AddCalculationUC
import uz.teamwork.mehrgodriver.domain.use_case.locale.GetCalculationUC
import uz.teamwork.mehrgodriver.domain.use_case.locale.UpdateLocationsUC
import uz.teamwork.mehrgodriver.domain.use_case.locale.UpdateTrackedTimeUC
import uz.teamwork.mehrgodriver.domain.use_case.locale.UpdateWaitedTimeUC
import uz.teamwork.mehrgodriver.domain.use_case.locale.UpdateWaitedTimeUntilGoneUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderGoUC
import uz.teamwork.mehrgodriver.domain.use_case.main.SendLocationUC
import uz.teamwork.mehrgodriver.domain.use_case.birga.BirgaTrackUC
import uz.teamwork.mehrgodriver.domain.use_case.main.UploadLocationUC
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import javax.inject.Inject
import kotlin.random.Random

typealias Polyline = MutableList<MyLocation>
typealias Polylines = MutableList<Polyline>

@AndroidEntryPoint
class MyTrackingService : LifecycleService() {
    // Api
    @Inject
    lateinit var uploadLocationUC: UploadLocationUC

    // Locale
    @Inject
    lateinit var addCalculationUC: AddCalculationUC

    @Inject
    lateinit var getCalculationUC: GetCalculationUC

    @Inject
    lateinit var updateWaitedTimeUC: UpdateWaitedTimeUC

    @Inject
    lateinit var updateWaitedTimeUntilGoneUC: UpdateWaitedTimeUntilGoneUC

    @Inject
    lateinit var updateTrackedTimeUC: UpdateTrackedTimeUC

    @Inject
    lateinit var updateLocationsUC: UpdateLocationsUC

    @Inject
    lateinit var sendLocationUC: SendLocationUC

    @Inject
    lateinit var birgaTrackUC: BirgaTrackUC

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @Inject
    lateinit var gson: Gson

    // Server-side fare (MOBILE.md). Lifecycle is bound to ACTION_START/STOP_TRACKING.
    @Inject
    lateinit var gpsBatchUploader: GpsBatchUploader

    @Inject
    lateinit var gpsBatchSocketChannel: GpsBatchSocketChannel

    @Inject
    lateinit var orderGoUC: OrderGoUC

    private var orderId = -1

    // Between ARRIVED and Go the client is aboard at the pickup. If the car then drives off
    // (> AUTO_START_RIDE_SPEED_KMH) the ride auto-starts — even while the app is backgrounded.
    private var isArrivedAtPickup = false
    private var rideAutoStartInFlight = false

    // True once Go has happened for the current order (manual slide or auto-start). Blocks a
    // stale ARRIVED snapshot (from a getActiveMyOrders refresh) re-arming the auto-start after
    // the ride already left the pickup. Cleared per order in beginTracking / ACTION_STOP_TRACKING.
    // @Volatile: read from the GPS-batch uploader's Dispatchers.IO thread (waitTimersProvider)
    // while written on Main — keep the pickup/on-way split from reading a stale flag at Go.
    @Volatile
    private var rideHasGone = false

    // Order state (Constants.ORDER_STATE_*) as the service knows it — the source of the
    // per-point `state` stamp the uploader writes onto every GPS sample, so the server bills
    // each point under the state it was DRIVEN in, not the state at flush time. Advanced only
    // through raiseOrderState (monotonic 2→7→8→9) and persisted for a START_STICKY restart.
    private var currentOrderState = 0

    // Set when the server reports GONE while beginTracking's async timer-restore is still
    // pending: the pickup-wait freeze must run AFTER the restore (from the restored value),
    // or it freezes 0 and the restored pickup minutes re-bill as on-way wait.
    private var pendingGoneWaitFreeze = false

    private var mpStartedTack: MediaPlayer? = null
    private var mpFinishedTrack: MediaPlayer? = null

    // Waiting-charge beep de-dup. LIVE-VERIFIED (2026-07-20): the backend repeats
    // `waiting_charge_started` in EVERY response (not one-shot as the spec says), and the
    // waiting cost grows each tick — so value-comparison would beep on every flush.
    // Instead: "started" sounds ONCE per order; "step" sounds when the 5 000-so'm bucket
    // of the surcharge/waiting-cost actually advances.
    private var chargeStartedBeeped = false
    private var lastChargeStepBucket = -1L

    private var client: OkHttpClient? = null
    private var request: Request? = null
    private var mySocketListener: MySocketListener? = null
    private var webSocket: WebSocket? = null

    private var isSocketConnect: Boolean = false

    // MaktabGo (школьный шаттл): фоновый трекинг рейса, без таксишной order-логики.
    private var maktabGoRouteId: Int = 0
    private var lastMaktabGoTrackMs: Long = 0L

    companion object {
        /**
         * Ceiling on the process-death gap that may be credited as billable waiting when the
         * service restarts with the wait clock still armed. See the resume block in
         * [beginTracking]: whatever lands in `timeWait` is uploaded and MAX-latched server-side,
         * so an over-credit is permanent. 10 min is a wait long enough to be plausible; past it
         * the driver was almost certainly not still sitting at the pickup.
         */
        private const val MAX_WAIT_RESUME_GAP_MS = 10 * 60 * 1000L

        // Oldest cached fix [primeLastFixOnce] will adopt as the driver's position for the
        // order requests. Matches OrdersMapFragment.primeLastKnownLocation's gate.
        private const val LAST_FIX_PRIME_MAX_AGE_MS = 5 * 60 * 1000L

        val isServiceRunning = MutableLiveData(false)

        val isTracking = MutableLiveData(false)

        val trackLocations = MutableLiveData<Polylines>()
        val speedCar = MutableLiveData<Int>()

        val timeTrackInMillis = MutableLiveData<Long>()
        val timeWaitInMillis = MutableLiveData<Long>()

        // @Volatile: read from the uploader's IO thread (waitTimersProvider) while written on Main.
        @Volatile
        var waitedTimeUntilGone: Long = 0

        // Track-timer reading at Go/"Kettik" — the trip-time DISPLAY shows (total − this) so
        // the visible clock starts with the client aboard; execution_time still sends the
        // total (the backend caps waits against it). Persisted with the arm state.
        @Volatile
        var trackTimeAtGoneMs: Long = 0

        val isWaiting = MutableLiveData(false)

        /**
         * True while the final-bill dialog is on screen, which freezes the wait meter so the
         * total the driver approves is the total that gets posted (see MapFragment's
         * pauseWaitForFinishDialog).
         *
         * Stopping the meter is not enough on its own: on the 9 brands whose Constants block
         * sets WAITING_TIME_TURN_AUTO = true, the parked-speed branch below re-arms the timer on
         * the very next fix — and the driver is by definition parked when finishing, so the
         * freeze would last about one GPS interval and the fix would silently do nothing there.
         * Mehrgo has the flag false, so this only shows up on a rebranded build.
         *
         * @Volatile: written on Main from the fragment, read on the location callback thread.
         */
        @Volatile
        var finishBillOpen = false

        // One-shot UI signal: the wait meter auto-stopped because the car drove off
        // (> WAIT_AUTO_STOP_SPEED_KMH). MapFragment shows a popup then resets it to false.
        val waitAutoStoppedBySpeed = MutableLiveData(false)

        // One-shot UI signal: the ride was auto-started in the background (client aboard,
        // the car drove off past AUTO_START_RIDE_SPEED_KMH). MapFragment shows the popup.
        val rideAutoStartedBySpeed = MutableLiveData(false)
        val isSocketConnected = MutableLiveData<Boolean>()

        val listenerNewPrivateOrder = MutableLiveData<Order?>()

        val lastLatLngWholeApp = MutableLiveData<LatLng?>()

        // Same last GPS fix as [lastLatLngWholeApp] but keeps accuracy. Attached to
        // order state-change requests (accept/start/arrive/go/cancel/complete) so the
        // backend can record where the driver was at each transition. Volatile: written
        // from the location callbacks (main thread), read by the repository on IO.
        // Setter is private so value and age can only move together — see [rememberWholeAppFix].
        @Volatile
        var lastLocationWholeApp: MyLocation? = null
            private set

        // Fix time (epoch ms) of [lastLocationWholeApp]. A caller that BOOKS something at the
        // position — taximeter creation — must know how old it is; the request-stamping callers
        // just take whatever is newest. Written only by [rememberWholeAppFix], so the pair
        // cannot drift apart.
        @Volatile
        var lastLocationWholeAppAtMs: Long = 0L
            private set

        /**
         * Remember [location] as the app-wide fix behind the order requests, keeping the value
         * and its age in step. Drops "null island" (0,0) glitches the same way [addPathPoint]
         * does — one reaching this field would be posted as the driver's position, including
         * `latitude_finish`/`longitude_finish` on order/complete.
         *
         * @return false when the fix was rejected, so a caller publishing the same position
         *   elsewhere (the map LiveData) can drop it too.
         */
        fun rememberWholeAppFix(location: Location): Boolean {
            if (kotlin.math.abs(location.latitude) < 0.1 &&
                kotlin.math.abs(location.longitude) < 0.1
            ) return false
            lastLocationWholeApp =
                MyLocation(location.latitude, location.longitude, location.accuracy)
            lastLocationWholeAppAtMs = location.time
            return true
        }

        /**
         * The remembered fix, but only while it is young enough to act on. Returns null for a
         * stale one so the caller can ask for a current fix instead of booking an order at a
         * position the car has long left.
         */
        fun freshWholeAppFix(maxAgeMs: Long = LAST_FIX_PRIME_MAX_AGE_MS): MyLocation? {
            val fix = lastLocationWholeApp ?: return null
            val at = lastLocationWholeAppAtMs
            if (at <= 0L || System.currentTimeMillis() - at > maxAgeMs) return null
            return fix
        }

        var cdtAcceptingIndividualOrder: CountDownTimer? = null
        val timeAcceptingIndividualOrder = MutableLiveData<Int?>()

        var destinationPosition: Int? = null

        // Final destination of the active order (set by the fragment when the order goes on-route
        // / GONE). The service watches proximity so it can alert a BACKGROUNDED driver to open the
        // app and finish. finishNoticeShown fires that notice at most once per order.
        var finalDestination: LatLng? = null
        var finishNoticeShown: Boolean = false

        // True only while the trip MapFragment is resumed — the single screen that can show the
        // in-app popup AND has a live observer. Distinct from app-wide foreground: on any OTHER
        // in-app screen this is false, so notices route to the overlay/notification instead of a
        // LiveData signal nobody consumes. Set from MapFragment.onResume / onPause.
        @Volatile
        var mapScreenVisible: Boolean = false
    }

    private fun postInitialValues() {
        isTracking.postValue(false)

        trackLocations.postValue(mutableListOf())
        speedCar.postValue(0)

        timeTrackInMillis.postValue(0L)
        timeWaitInMillis.postValue(0L)
        waitedTimeUntilGone = 0

        isWaiting.postValue(false)
        waitAutoStoppedBySpeed.postValue(false)
        rideAutoStartedBySpeed.postValue(false)
        isSocketConnected.postValue(false)

        listenerNewPrivateOrder.postValue(null)

        lastLatLngWholeApp.postValue(null)
        lastLocationWholeApp = null
        lastLocationWholeAppAtMs = 0L

        timeAcceptingIndividualOrder.value = null

        destinationPosition = null
        finalDestination = null
        finishNoticeShown = false
    }

    private val orderDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra(KEY_ORDER_DATA)

            // Defensive parse: KEY_ORDER_DATA carries the RAW socket frame (see
            // MySocketListener.sendOrderDataToOtherComponents). SocketOrderResponse.data is a
            // non-null Order full of numeric fields, so a malformed / string-typed / null money
            // field made Gson throw JsonSyntaxException on the MAIN thread and took the tracking
            // service — the app spine — down (crash A). A bad frame must never crash the service.
            val socketOrderResponse = try {
                gson.fromJson(data, SocketOrderResponse::class.java)
            } catch (e: Exception) {
                Timber.e(e, "orderDataReceiver: bad ORDER_DATA payload: $data")
                return
            } ?: return

            when (socketOrderResponse.key) {
                ORDER_NEW_PRIVATE -> {
                    listenerNewPrivateOrder.postValue(socketOrderResponse.data)

                    if (CheckPermissions.checkHasDrawOverlayPermissions(this@MyTrackingService)) {
                        val intent2 = Intent(context, AutoOfferService::class.java)
                        startService(intent2)
                    } else {
                        val time = socketOrderResponse.data.branch.acceptWaiting ?: 20

                        setTimerOrderAccept(time)
                        createNotificationForNewPrivateOrder(time.toLong() * 1000)
                    }
                }

                ORDER_NEW -> {
                    createNotificationForNewOrder()
                }

                ORDER_CANCELLED_PRIVATE -> {
                    // Only tear down if it is THIS active order that was cancelled — ignore a
                    // stale/duplicate cancel scoped to a different order.
                    if (isTracking.value == true && socketOrderResponse.data.id == orderId) {
                        // Genuine cancel: reset the whole tracking state (incl. auto-start arm
                        // flags + persisted order) so the next order starts clean, and tear the
                        // GPS uploader down so a 403/404 retry can't keep hammering a dead order
                        // (ORDER_GPS_FIXES.md §1).
                        clearTrackingState()
                        lifecycleScope.launch { gpsBatchUploader.stop() }
                    }
                    createNotificationForCancelMyOrder()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val intentFilter = IntentFilter(ACTION_SEND_ORDER_DATA_BY_BROADCAST)
        LocalBroadcastManager.getInstance(this).registerReceiver(orderDataReceiver, intentFilter)

        postInitialValues()

        request = buildSocketRequest()
        // NO OkHttp pingInterval on purpose. This server speaks an APP-LEVEL ping/pong — we send
        // the text "ping" and it answers with a RECEIVE_PONG message — not RFC6455 control frames.
        // With pingInterval on, OkHttp's own PING control frames went unanswered and OkHttp tore
        // the (otherwise healthy) socket down on every interval → constant "reconnecting" flapping.
        // The app-level heartbeat below (a text frame every 10s) already keeps carrier NAT alive
        // and detects a genuinely dead socket within ~30s, so protocol pings add only the flapping.
        client = OkHttpClient.Builder()
            .build()
        mySocketListener = MySocketListener(this, gpsBatchSocketChannel)

        mpStartedTack = MediaPlayer.create(this, R.raw.started_track)
        mpFinishedTrack = MediaPlayer.create(this, R.raw.finished_track)

        // Live wait timers ride along in the GPS batches (ORDER_COMPLETE_WAITING.md §7.1):
        // CUMULATIVE pickup wait + on-way wait, split at Go/"Kettik". The uploader attaches
        // them only when they changed since the last successful send.
        gpsBatchUploader.waitTimersProvider = {
            val total = timeWaitInMillis.value ?: 0L
            if (rideHasGone) {
                val pickup = waitedTimeUntilGone
                Pair(pickup, (total - pickup).coerceAtLeast(0L))
            } else {
                Pair(total, 0L)
            }
        }

        // §7.4: billing events — the driver must HEAR when waiting starts charging
        // (free window over / +5k steps). Service-level so it sounds in both UI modes
        // and with the screen off; background pushes are covered by FCM server-side.
        lifecycleScope.launch {
            gpsBatchUploader.fareUpdates.collect { fare ->
                val events = fare.events.orEmpty()
                val shouldBeep = when {
                    events.contains("waiting_charge_started") && !chargeStartedBeeped -> {
                        chargeStartedBeeped = true
                        true
                    }

                    events.contains("waiting_charge_step") -> {
                        val bucket = ((fare.live?.surcharge ?: fare.live?.waitingCost ?: 0.0)
                                / 5000.0).toLong()
                        (bucket != lastChargeStepBucket).also {
                            if (it) lastChargeStepBucket = bucket
                        }
                    }

                    else -> false
                }
                if (shouldBeep) {
                    runCatching {
                        MediaPlayer.create(this@MyTrackingService, R.raw.audio_notification)
                            ?.apply { setOnCompletionListener { mp -> mp.release() } }
                            ?.start()
                    }
                }

                // Reconnect / second-device sync (Muhammad, 2026-07-20): the server keeps the
                // MAX of everything ever sent for this order — if IT knows a longer wait than
                // we do (fresh install, other phone), adopt it and CONTINUE from there. The
                // echo of our own timer is floor(ms/1000)·1000 ≤ local, so this can only fire
                // on a genuine gap (2 s threshold guards sec-rounding jitter).
                adoptServerWaits(fare)
            }
        }

        updateLocationForUpload()
        primeLastFixOnce()

        isTracking.observe(this, Observer { isTrack ->
            updateLocationTracking(isTrack)

            if (!isTrack) {
                addEmptyPolyline()

                timeTrackInMillis.postValue(0L)
                timeWaitInMillis.postValue(0L)
                waitedTimeUntilGone = 0

                lapTrackTime = 0L
                timeTrack = 0L

                lapWaitTime = 0L
                timeWait = 0L

                speedCar.postValue(0)

                destinationPosition = null
            }
        })

        speedCar.observe(this, Observer {
            if (it > WAIT_AUTO_STOP_SPEED_KMH) {
                if (isArrivedAtPickup && !rideAutoStartInFlight) {
                    // Client aboard at the pickup and the car drove off → auto-start the ride.
                    // Runs in the service, so it works even while the app is backgrounded.
                    // Leave the pickup wait RUNNING until the Go actually confirms (onGoConfirmed
                    // freezes it on Success). Stopping it here and then failing the Go would
                    // freeze the meter with no notice (Mehrgo has no speed re-arm) and silently
                    // lose billable wait time. The on-route notice below is gated off at pickup.
                    rideAutoStartInFlight = true
                    autoStartRide()
                } else if (isWaiting.value == true && !isArrivedAtPickup) {
                    // On-route wait stopped by movement (ride already in progress).
                    isWaiting.postValue(false)
                    // Manual-wait brands (Mehrgo): the driver started this wait, so warn them
                    // it auto-stopped (in-app popup + heads-up notification). Auto-wait brands
                    // stop/start the meter constantly — stay silent there.
                    if (!WAITING_TIME_TURN_AUTO) {
                        // Foreground → in-app popup (via the signal); background → over-other-apps
                        // overlay (with permission); else → heads-up notification.
                        showNotice(
                            R.drawable.ic_error_circle,
                            R.string.wait_auto_stopped_title,
                            R.string.wait_auto_stopped_msg,
                            5000L,
                            foregroundSignal = { waitAutoStoppedBySpeed.postValue(true) },
                            notificationFallback = { notifyWaitAutoStopped() }
                        )
                    }
                }
            } else if (it <= MINIMAL_SPEED) {
                // Effectively parked (<= 7 km/h): auto-wait brands re-arm the meter here;
                // Mehrgo (WAITING_TIME_TURN_AUTO=false) leaves it driver-controlled.
                // Never re-arm behind the final-bill dialog — the driver is parked precisely
                // because they are finishing, so this branch would undo the freeze immediately.
                if (WAITING_TIME_TURN_AUTO && !finishBillOpen && !isWaiting.value!!) {
                    startWaitingTimer()
                }
            }
        })

        MySocketListener.listenerNotificationData.observe(this, Observer {
            val socketNotificationResponse = try {
                gson.fromJson(it, SocketNotificationResponse::class.java)
            } catch (e: Exception) {
                Timber.e(e, "notificationData: bad payload: $it")
                return@Observer
            } ?: return@Observer

            if (socketNotificationResponse.key == NOTIFICATION_NEW) {
                Timber.d("Notification: ${socketNotificationResponse.key}")
                createNotificationForNotification(
                    socketNotificationResponse.data.message,
                    socketNotificationResponse.data.text
                )
            }
        })

        MySocketListener.listenerPongData.observe(this, Observer {
            val socketPongResponse = try {
                gson.fromJson(it, SocketPongResponse::class.java)
            } catch (e: Exception) {
                Timber.e(e, "pongData: bad payload: $it")
                return@Observer
            } ?: return@Observer

            if (socketPongResponse.key == RECEIVE_PONG) {
                Timber.d("PONG: ${socketPongResponse.key}")
                isSocketConnect = true
            }
        })

        MySocketListener.isSocketListener.observe(this, Observer {
            if (it) {
                Timber.d("Socket connected")

                isSocketConnect = true
                checkingConnectToSocket()
            }
        })

        // Upload Errors
        val typeToken = object : TypeToken<List<ErrorRequest>>() {}
        val errors: List<ErrorRequest>? = ErrorRequestManager.getList(typeToken)
        if (!errors.isNullOrEmpty()) {
            uploadErrors(errors)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // START_STICKY restart after a low-memory process kill: resume the
            // live order so tracking + the waiting clock keep counting AND the
            // websocket reconnects, even if the app was fully closed in between.
            val resumeId = readActiveOrder()
            if (resumeId > 0 && isTracking.value != true) {
                isServiceRunning.postValue(true)
                startForegroundService()
                startSocketListener()
                beginTracking(resumeId)
            }
        }
        intent?.let {
            when (it.action) {
                ACTION_START_SERVICE -> {
                    isServiceRunning.postValue(true)

                    startForegroundService()
                    startSocketListener()
                }

                ACTION_START_MAKTABGO_TRACKING -> {
                    maktabGoRouteId = it.getIntExtra("route_id", 0)
                    isServiceRunning.postValue(true)
                    startForegroundService()          // foreground => фоновый трекинг переживает сворачивание
                    startMaktabGoLocation()
                }

                ACTION_STOP_MAKTABGO_TRACKING -> {
                    maktabGoRouteId = 0
                    stopMaktabGoLocation()
                    isServiceRunning.postValue(false)
                    stopForeground(true)
                    stopSelf()
                }

                ACTION_STOP_SERVICE -> {
                    isServiceRunning.postValue(false)
                    maktabGoRouteId = 0
                    stopMaktabGoLocation()
                    // NOTE: deliberately do NOT clearTrackingState() here — STOP_SERVICE is also
                    // reachable mid-trip (off-shift "Exit"), and tearing tracking down would kill a
                    // live trip. Order-end teardown is tied to the explicit end events instead
                    // (ACTION_STOP_TRACKING on finish, orderCansel + the socket cancel handler).

                    if (runnable != null) {
                        handler?.removeCallbacks(runnable!!)
                    }

                    MySocketListener.isSocketListener.postValue(false)
                    MySocketListener.listenerNotificationData = MutableLiveData<String>()
                    MySocketListener.listenerPongData = MutableLiveData<String>()

                    webSocket?.close(WEB_SOCKET_CLOSE_CODE, "Work finished")

                    stopSelf()
                }

                ACTION_START_TRACKING -> {
                    // `with_timer` splits the two things tracking used to bundle:
                    //  • ACCEPT  → with_timer=false: arm GPS now so order-gps/batch uploads the
                    //    driver→pickup approach leg from acceptance, but do NOT start the
                    //    execution-time timer (that must run from Boshlash, not from accept).
                    //  • BOSHLASH → with_timer=true (default): start the trip timer now; if GPS was
                    //    already armed at accept, just kick the timer off without re-arming.
                    val cmdOrderId = it.getIntExtra("order_id", 0)
                    val withTimer = it.getBooleanExtra("with_timer", true)
                    val wasTracking = isTracking.value == true
                    if (wasTracking && cmdOrderId > 0 && cmdOrderId != orderId) {
                        // A stale command carrying a PREVIOUS order (delayed ack / stale
                        // fragment snapshot after a cancel) must not start the live order's
                        // timer or relabel its state stamp.
                        Timber.w(
                            "Stale START_TRACKING for order %d (active=%d) — ignored",
                            cmdOrderId, orderId
                        )
                        return@let
                    }
                    if (wasTracking) {
                        if (withTimer) {
                            startTripTimerIfNotRunning()
                            // Boshlash while GPS was already armed at accept — the order
                            // advanced to STARTED; stamp subsequent points accordingly.
                            raiseOrderState(ORDER_STATE_STARTED)
                        }
                    } else {
                        beginTracking(
                            cmdOrderId,
                            isTaximeter = it.getBooleanExtra("is_taximeter", false),
                            startTimer = withTimer
                        )
                    }
                    // Server-known order state riding along with the command. Without it a
                    // FRESH-prefs resume (reinstall / cleared data / second phone) of an order
                    // the server already has past the local seed would stamp the rest of the
                    // trip with an earlier state — at state 9 that under-bills the whole
                    // remaining ride to ~0. raiseOrderState is monotonic, so a stale poll
                    // snapshot (or the missing-extra default 0) can never lower the stamp.
                    when (val srvState = it.getIntExtra("order_state", 0)) {
                        ORDER_STATE_ACCEPTED, ORDER_STATE_STARTED,
                        ORDER_STATE_CHANGED_ARRIVED -> raiseOrderState(srvState)

                        ORDER_STATE_CHANGED_GONE -> {
                            if (!rideHasGone) {
                                // Trust the server: the client is already aboard. Mirror
                                // onGoConfirmed's flags + pickup-wait freeze so the wait
                                // split can't shift already-billed pickup wait into the
                                // on-way bucket (the server MAXes the buckets separately).
                                isArrivedAtPickup = false
                                rideHasGone = true
                                if (wasTracking) {
                                    // Timers are live — freeze the pickup slice right now.
                                    waitedTimeUntilGone = timeWaitInMillis.value ?: 0L
                                    updateWaitedTimeUntilGoneInLocale(waitedTimeUntilGone)
                                } else {
                                    // beginTracking above restores the timers from Room
                                    // ASYNCHRONOUSLY — freezing from the LiveData here would
                                    // freeze 0 and let the restored pickup minutes re-bill
                                    // as ON-WAY wait. Defer the freeze until after restore.
                                    pendingGoneWaitFreeze = true
                                }
                            }
                            raiseOrderState(ORDER_STATE_CHANGED_GONE)
                            persistArmState()
                        }
                    }
                }

                ACTION_STOP_TRACKING -> {
//                    mpFinishedTrack?.start()
                    // Capture BEFORE clearTrackingState() wipes it: the flush below can retry
                    // offline for minutes, and by the time its trailing stop runs a NEWER
                    // order may own the uploader — a blanket stop() would silently kill that
                    // order's whole track upload (billed ~0 class).
                    val finishingId = orderId
                    clearTrackingState()

                    // Flush whatever is queued then stop the uploader — order is over. Timers
                    // are skipped: they already rode in the complete body, and the server 403s
                    // ("Order is not in progress") a timer-only batch for a finished order.
                    lifecycleScope.launch {
                        runCatching { gpsBatchUploader.flushNow(includeTimers = false) }
                        gpsBatchUploader.stopIf(finishingId)
                    }
                }

                ACTION_START_WAY -> {
                    // Go happened via the manual slide — same side effects as the background
                    // auto-start (shared with autoStartRide Success so the two can't drift).
                    // Defaulting the extra to the current orderId keeps legacy behavior for a
                    // sender that doesn't attach the id.
                    onGoConfirmed(it.getIntExtra("order_id", orderId))
                }

                ACTION_START_TIME_WAIT -> {
                    startWaitingTimer()
                }

                ACTION_STOP_TIME_WAIT -> {
                    isWaiting.postValue(false)
                }

                ACTION_ARRIVED_AT_PICKUP -> {
                    // Scoped to its order: a delayed ARRIVED for a cancelled previous order
                    // must not re-arm the auto-start or raise the state stamp of the next one.
                    val arrivedOrderId = it.getIntExtra("order_id", 0)
                    if (arrivedOrderId > 0 && orderId > 0 && arrivedOrderId != orderId) {
                        Timber.w(
                            "Stale ARRIVED for order %d (active=%d) — ignored",
                            arrivedOrderId, orderId
                        )
                        return@let
                    }
                    // Driver reached the pickup; the client is aboard. Arm the background
                    // auto-start — but only if the ride hasn't already left the pickup, so a
                    // stale ARRIVED snapshot from a getActiveMyOrders refresh can't re-arm it
                    // after Go. Deliberately do NOT touch rideAutoStartInFlight here:
                    // clobbering it mid-orderGo would let the next speed tick fire a 2nd Go.
                    if (!rideHasGone) {
                        isArrivedAtPickup = true
                    }
                    // Monotonic, so the same stale-ARRIVED refresh can't relabel post-Go points.
                    raiseOrderState(ORDER_STATE_CHANGED_ARRIVED)
                    // Persist so a START_STICKY restart can re-arm without a live fragment.
                    persistArmState()
                }
            }
        }

        super.onStartCommand(intent, flags, startId)
        // Stay sticky so the OS restarts the tracking service after a low-memory kill —
        // GPS collection + order-gps/batch must survive backgrounding (ORDER_GPS_FIXES.md item A).
        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(orderDataReceiver)

        releaseMpStartedTrack()
        releaseMpFinishedTrack()

        fusedLocationProviderClient.removeLocationUpdates(locationCallBack)
        fusedLocationProviderClient.removeLocationUpdates(locationCallBackForUpload)

        gpsBatchUploader.stop()
        gpsBatchSocketChannel.setConnected(false)

        client = null
        request = null
        mySocketListener = null
        webSocket = null

        cdtAcceptingIndividualOrder?.cancel()
        cdtAcceptingIndividualOrder = null
    }

    // Functions
    // Start foreground service and create notification
    private fun startForegroundService() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Small icon: brand steering-wheel monochrome silhouette (Android tints it
        // for the status bar). Large icon: the colourful brand bitmap so the
        // expanded notification body shows full brand identity, not a generic mark.
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_notification_steering_wheel)
            .setLargeIcon(largeIcon)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(getMainActivityPendingIntent())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notificationBuilder.build(),
                FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private fun getMainActivityPendingIntent(): PendingIntent? {
        val pendingIntent: PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(this, MainActivity::class.java)
            intent.action = ACTION_SHOW_MY_TRACKING_FRAGMENT
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.action = ACTION_SHOW_MY_TRACKING_FRAGMENT
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }

        return pendingIntent
    }

    /**
     * Socket Request built from the CURRENT token, every time.
     *
     * It used to be built once in onCreate and reused by both connect paths, so a driver who
     * logged out and back in kept talking to the server as the PREVIOUS user for the whole life
     * of the service — the stale token survived every reconnect. A service that started with no
     * user stored connected permanently as `?token=null`; returning null here skips the connect
     * instead, and the next start with a real token builds a real Request.
     */
    private fun buildSocketRequest(): Request? {
        val token = UserManager.getToken()
        if (token.isNullOrEmpty()) return null
        return Request.Builder()
            .url("$BASE_URL_FOR_SOCKET?token=$token")
            .header("Connection", "open")
            .build()
    }

    // Start socket
    private fun startSocketListener() {
        if (!MySocketListener.isSocketListener.value!!) {
            // Rebuilt per connect (see buildSocketRequest). The old `request!!` also crashed
            // outright if a connect raced the teardown that nulls the field.
            val req = buildSocketRequest() ?: return
            request = req
            webSocket = null
            webSocket = client?.newWebSocket(req, mySocketListener ?: return)
        }
    }

    /**
     * Force a fresh socket after the heartbeat declared the current one dead. Unlike
     * [startSocketListener] this does NOT gate on `isSocketListener.value`: the old dead
     * socket may still report `true` (onFailure never flipped it), and reading the value
     * we just posted `false` to would be stale on the same main-thread frame — so the gated
     * path silently skipped the reconnect for a whole cycle. Called only from the paced
     * ~30s heartbeat, so cancel-then-recreate here can never tight-loop.
     */
    private fun reconnectSocketNow() {
        isSocketConnect = false
        MySocketListener.isSocketListener.postValue(false) // banner → "reconnecting"
        webSocket?.cancel() // abandon the dead socket immediately (no lingering half-open)
        // Rebuild from the current token — a reconnect must never resurrect a logged-out session.
        val req = buildSocketRequest() ?: return
        request = req
        webSocket = client?.newWebSocket(req, mySocketListener ?: return)
    }

    // True once the execution-time timer coroutine is running for the current order — guards
    // against a second start when GPS was armed at accept and the timer only begins at Boshlash.
    private var tripTimerRunning = false

    /** Start the execution-time timer once per order (idempotent). Split out of [startTracking] so
     *  an accept-time GPS arm can turn tracking on WITHOUT the timer, then Boshlash starts it. */
    private fun startTripTimerIfNotRunning() {
        if (tripTimerRunning) return
        tripTimerRunning = true
        startTrackingTimer()
    }

    // Start tracking
    private fun startTracking(withTimer: Boolean = true) {
        isTracking.postValue(true)
        // Timer is deferred when GPS is armed at accept (withTimer=false) — see the
        // ACTION_START_TRACKING handler. It is started at Boshlash instead.
        if (withTimer) startTripTimerIfNotRunning()
        // The client-wait clock is SEPARATE — it must start on arrival (ACTION_START_TIME_WAIT),
        // not here. Starting it with tracking made it run from Boshlash/Start and equal the trip
        // time. (Mehrgo has WAITING_TIME_TURN_AUTO = false, so there's no speed-based auto-wait.)
    }

    /**
     * Begin (or resume) tracking an order. Restores the accumulated tracked/wait
     * time + GPS from Room, so the waiting clock keeps counting from the order's
     * arrival even after a backgrounding or a low-memory process kill. The active
     * order id is persisted so a START_STICKY null-intent restart can resume it.
     */
    private fun beginTracking(id: Int, isTaximeter: Boolean = false, startTimer: Boolean = true) {
        if (isTracking.value == true) return
        startTracking(startTimer)
        // A START_STICKY restart re-begins the SAME persisted order — restore the background
        // auto-start arm state so it survives a process kill (the fragment may not be alive to
        // re-send ACTION_ARRIVED_AT_PICKUP). A genuinely new order starts disarmed.
        val resuming = readActiveOrder() == id
        orderId = id
        rideAutoStartInFlight = false
        // A new order starts with no destination (the fragment supplies it once it renders the
        // order). A resuming order restores the PERSISTED destination below, so the background
        // "reached destination" notice survives a process kill even with no fragment alive.
        finalDestination = null
        finishNoticeShown = false
        if (resuming) {
            isArrivedAtPickup = trackingPrefs.getBoolean("arrived_at_pickup", false)
            rideHasGone = trackingPrefs.getBoolean("ride_has_gone", false)
            trackTimeAtGoneMs = trackingPrefs.getLong("track_time_at_gone", 0L)
            val lat = trackingPrefs.getFloat("final_dest_lat", Float.NaN)
            val lon = trackingPrefs.getFloat("final_dest_lon", Float.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                finalDestination = LatLng(lat.toDouble(), lon.toDouble())
            }
        } else {
            isArrivedAtPickup = false
            rideHasGone = false
            trackTimeAtGoneMs = 0L
        }
        // Taximeter/street-hail (from == ORDER_CREATED_DRIVER): no drive-to-pickup leg exists — the
        // passenger is aboard at creation, so the trip is "gone" from the first tracked moment.
        // Open the point gate here (overriding the `false` just set for a new order) so
        // forwardToFareUploader/addPathPoint actually record + upload the track; otherwise it stays
        // shut until Kettik, which a taximeter never reaches, and the server bills ~0 distance.
        // persistArmState() below saves it, so a mid-trip service restart resumes correctly.
        if (isTaximeter) rideHasGone = true
        // Seed the per-point state stamp. A resume restores the persisted value (falling back
        // to deriving it from the restored flags for a pre-update install); a fresh order maps
        // straight off how it was armed: taximeter = client already aboard (GONE), Boshlash
        // (withTimer) = STARTED, accept-armed = ACCEPTED. persistArmState() below saves it.
        currentOrderState = when {
            rideHasGone -> ORDER_STATE_CHANGED_GONE
            resuming -> trackingPrefs.getInt(
                "order_state",
                when {
                    isArrivedAtPickup -> ORDER_STATE_CHANGED_ARRIVED
                    startTimer -> ORDER_STATE_STARTED
                    else -> ORDER_STATE_ACCEPTED
                }
            )

            startTimer -> ORDER_STATE_STARTED
            else -> ORDER_STATE_ACCEPTED
        }
        persistActiveOrder(id)
        persistArmState()
        lifecycleScope.launch {
            val savedCalculation: Calculation? = getCalculationUC.invoke(id)
            if (savedCalculation == null) {
                // New order — every counter starts from 0 (never inherit a previous order's wait,
                // e.g. if the last order didn't stop cleanly).
                timeTrack = 0L
                lapTrackTime = 0L
                timeTrackInMillis.postValue(0L)
                timeWait = 0L
                lapWaitTime = 0L
                timeWaitInMillis.postValue(0L)
                waitedTimeUntilGone = 0L
                isWaiting.postValue(false)
                // Fresh order → fresh charge-beep state (don't depend solely on the prior
                // order's teardown having run).
                chargeStartedBeeped = false
                lastChargeStepBucket = -1L
                addCalculationToLocale(
                    Calculation(
                        orderId = id,
                        trackedTime = 0L,
                        waitedTime = 0L,
                        waitedTimeUntilGone = 0L,
                        locations = emptyList()
                    )
                )
            } else {
                timeTrack = savedCalculation.trackedTime
                timeTrackInMillis.postValue(timeTrack)

                timeWait = savedCalculation.waitedTime
                timeWaitInMillis.postValue(timeWait)

                waitedTimeUntilGone = savedCalculation.waitedTimeUntilGone

                trackLocations.value?.apply {
                    last().addAll(savedCalculation.locations)
                    trackLocations.postValue(this)
                }

                // The wait was ON when the process died (was_waiting only survives a kill —
                // every clean stop clears it): credit the dead-gap and RESUME the clock.
                // Prod report: "kutish taymeri qayta ochilganda to'xtab qolgan".
                if (resuming && trackingPrefs.getBoolean("was_waiting", false)) {
                    val lastTick = trackingPrefs.getLong("wait_last_tick", 0L)
                    // CAPPED. The credited gap is billable waiting AND it is uploaded in every
                    // GPS batch, where the server keeps the MAX of every value it has ever
                    // seen (see adoptServerWaits) — so an over-credit can never be walked back,
                    // not even by a correct later value. Uncapped, an OEM battery-manager kill
                    // during pickup wait billed the passenger for the entire downtime: kill at
                    // 09:00, driver reopens at 12:00 → 3 h of "waiting" latched server-side.
                    // Beyond this ceiling the driver was almost certainly not sitting there, so
                    // credit the ceiling and stop. Tunable — raise it if real waits legitimately
                    // run longer than this.
                    val gap = if (lastTick > 0) {
                        (System.currentTimeMillis() - lastTick)
                            .coerceAtLeast(0L)
                            .coerceAtMost(MAX_WAIT_RESUME_GAP_MS)
                    } else 0L
                    if (gap > 0) {
                        timeWait += gap
                        timeWaitInMillis.postValue(timeWait)
                        updateWaitedTimeInLocale(timeWait)
                        // Keep waits ≤ execution_time (the backend proportionally shrinks
                        // otherwise): the trip clock was equally dead for the same gap.
                        timeTrack += gap
                        timeTrackInMillis.postValue(timeTrack)
                        updateTrackedTimeInLocale(timeTrack)
                    }
                    startWaitingTimer()
                    Timber.tag("FareLive").d("resumed waiting after restart, gap=%dms", gap)
                }
            }

            // Deferred Go-freeze (see ACTION_START_TRACKING's GONE branch): the server had
            // this order past Go before we began tracking, so everything restored above is
            // PICKUP wait — freeze it now, from the restored value. Freezing before the
            // restore would freeze 0 and let the restored minutes re-bill as ON-WAY wait
            // (the server MAXes the two buckets separately).
            if (pendingGoneWaitFreeze) {
                pendingGoneWaitFreeze = false
                waitedTimeUntilGone = timeWait
                updateWaitedTimeUntilGoneInLocale(timeWait)
            }

            // Server-side fare uploader spins up here so even points captured before
            // the first batch interval get persisted to Room. currentOrderState is read at
            // call time, so a raise that landed between beginTracking's synchronous seed and
            // this coroutine (e.g. the order_state intent extra) is already included.
            if (id > 0) gpsBatchUploader.start(id, currentOrderState)
        }
    }

    /**
     * Monotonic per-order state advance (2→7→8→9) — never lowers, so a stale/re-delivered
     * intent can't relabel later points with an earlier state. Feeds the uploader's per-point
     * `state` stamp; the uploader also re-arms + drains on the change, so each state's track
     * leaves at its own boundary ("har bitta state da location treki ketsin", 2026-07-30).
     */
    private fun raiseOrderState(state: Int) {
        if (state <= currentOrderState) return
        currentOrderState = state
        trackingPrefs.edit().putInt("order_state", state).apply()
        gpsBatchUploader.setOrderState(state)
    }

    /**
     * Full teardown of the per-order tracking state — used whenever an order ENDS by any path
     * (finish, local cancel, server cancel, off-shift). Without this, a path that leaves
     * isTracking==true makes the NEXT order's beginTracking early-return with stale flags/orderId.
     */
    private fun clearTrackingState() {
        isTracking.postValue(false)
        isWaiting.postValue(false)
        // Timer coroutine will exit on the isTracking=false above; reset the guard now so the next
        // order can start its timer even if this one never actually ran (accept-armed, no Boshlash).
        tripTimerRunning = false
        // Safety net: a dialog torn down without its resume path running must not leave
        // auto-wait disarmed for the NEXT order.
        finishBillOpen = false
        isArrivedAtPickup = false
        rideAutoStartInFlight = false
        rideHasGone = false
        chargeStartedBeeped = false
        lastChargeStepBucket = -1L
        trackTimeAtGoneMs = 0L
        orderId = -1
        currentOrderState = 0
        pendingGoneWaitFreeze = false
        finalDestination = null
        finishNoticeShown = false
        clearActiveOrder()
    }

    private val trackingPrefs get() = getSharedPreferences("tracking_state", MODE_PRIVATE)
    private fun persistActiveOrder(id: Int) =
        trackingPrefs.edit().putInt("active_order_id", id).apply()

    /** Persist the background-auto-start arm state (+ destination) so it survives a START_STICKY
     *  restart. finalDestination is in-memory only otherwise — without this the #5 finish notice
     *  can't fire headless after a process kill (the fragment isn't alive to re-supply it). */
    private fun persistArmState() =
        trackingPrefs.edit()
            .putBoolean("arrived_at_pickup", isArrivedAtPickup)
            .putBoolean("ride_has_gone", rideHasGone)
            .putInt("order_state", currentOrderState)
            .putLong("track_time_at_gone", trackTimeAtGoneMs)
            .putFloat("final_dest_lat", finalDestination?.latitude?.toFloat() ?: Float.NaN)
            .putFloat("final_dest_lon", finalDestination?.longitude?.toFloat() ?: Float.NaN)
            .apply()

    private fun clearActiveOrder() =
        trackingPrefs.edit()
            .remove("active_order_id")
            .remove("arrived_at_pickup")
            .remove("ride_has_gone")
            .remove("order_state")
            .remove("track_time_at_gone")
            .remove("was_waiting")
            .remove("wait_last_tick")
            .remove("final_dest_lat")
            .remove("final_dest_lon")
            .apply()

    private fun readActiveOrder(): Int =
        trackingPrefs.getInt("active_order_id", 0)

    // This lines for socket reconnect
    private var handler: Handler? = null
    private var runnable: Runnable? = null

    // Consecutive heartbeats with no pong. Reconnect only once this crosses the tolerance so a
    // single lost/slow pong (routine on mobile networks) no longer tears the socket down and
    // flashes the "reconnecting" banner every ~10s.
    private var missedPongs = 0
    private fun checkingConnectToSocket() {
        if (runnable != null) {
            handler?.removeCallbacks(runnable!!)
        }
        missedPongs = 0

        handler = Handler(Looper.myLooper()!!)
        runnable = object : Runnable {
            override fun run() {
                handler?.postDelayed(this, MINIMAL_TIME_CONNECT_SOCKET)
                if (isSocketConnect) {
                    // A pong arrived since the last ping → healthy. Reset the streak and ping again.
                    missedPongs = 0
                    sendSocketData()
                } else {
                    missedPongs++
                    if (missedPongs >= MAX_MISSED_SOCKET_PONGS) {
                        // Sustained silence (~30s) → the socket really is dead. Reconnect, then reset.
                        missedPongs = 0
                        reconnectSocketNow()
                    } else {
                        // Still within tolerance → give it another ping before giving up.
                        sendSocketData()
                    }
                }
            }
        }
        handler?.post(runnable!!)
    }

    private fun sendSocketData() {
        isSocketConnect = false
        webSocket?.send("ping")
    }

    // This lines for tracking time
    private var lapTrackTime = 0L
    private var timeTrack = 0L
    private fun startTrackingTimer() {
        val timeStarted = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.Main) {
            while (isTracking.value!!) {
                lapTrackTime = System.currentTimeMillis() - timeStarted

                val timeMillis = timeTrack + lapTrackTime
                timeTrackInMillis.postValue(timeMillis)

                val timeSecond = timeMillis / 1000
                if (timeSecond >= 10 && timeSecond % 10 == 0L) {
                    updateTrackedTimeInLocale(timeMillis)
                }

                delay(100L)
            }

            timeTrack += lapTrackTime
            // Loop exited (isTracking went false) — allow the NEXT order's timer to start.
            tripTimerRunning = false
        }
    }

    /**
     * Server-side wait sync (reconnect / second device — Muhammad, 2026-07-20): the server
     * stores the MAX of every timer value ever sent for this order. When ITS cumulative
     * wait exceeds ours (fresh install / other phone continued the order), lift the local
     * base so billing continues from the server's value instead of restarting from zero.
     * NOTE: the server ROUNDS the echoed timer to whole seconds (live: sent 315939ms →
     * waiting_sec=316), so its echo can exceed our local value by up to ~500ms; the 2 s
     * threshold below absorbs that, so a single-device self-ratchet can't fire. Do NOT lower
     * the threshold below ~1 s or the round-up would start inflating the local wait.
     */
    private fun adoptServerWaits(fare: uz.teamwork.mehrgodriver.domain.model.fare.FareResponse) {
        val live = fare.live ?: return
        val srvPickupMs = (live.waitingSec ?: 0L) * 1000L
        // DELIBERATE: pre-Go the server's on_way component is NOT adopted. The local model is
        // single-bucket before Go (everything = pickup), and Go freezes waitedTimeUntilGone
        // from the running total — an adopted on-way slice would land inside that freeze and
        // then be re-sent as PICKUP wait, which the server MAXes upward = double-billed
        // waiting. The un-adopted slice only affects the transient display; billing stays
        // correct because the server keeps its own per-field MAX until complete.
        val srvOnWayMs = if (rideHasGone) (live.onWaySec ?: 0L) * 1000L else 0L
        if (srvPickupMs <= 0L && srvOnWayMs <= 0L) return

        val curTotal = timeWaitInMillis.value ?: 0L
        val curPickup = if (rideHasGone) waitedTimeUntilGone else curTotal
        val curOnWay = (curTotal - curPickup).coerceAtLeast(0L)

        val newPickup = maxOf(curPickup, srvPickupMs)
        val newOnWay = maxOf(curOnWay, srvOnWayMs)
        val newTotal = newPickup + newOnWay
        if (newTotal <= curTotal + 2_000L) return // sec-rounding jitter, not a real gap

        timeWait += newTotal - curTotal
        if (rideHasGone) waitedTimeUntilGone = newPickup
        timeWaitInMillis.postValue(newTotal)
        updateWaitedTimeInLocale(newTotal)
        Timber.tag("FareLive").d(
            "adopted server waits: pickup=%dms onWay=%dms (was total=%dms)",
            newPickup, newOnWay, curTotal
        )
    }

    // This lines for waiting time
    private var lapWaitTime = 0L
    private var timeWait = 0L
    private fun startWaitingTimer() {
        if (isWaiting.value == true) return // already running — don't spawn a second timer
        isWaiting.postValue(true)
        // Survives a process kill: a restart sees was_waiting=true and RESUMES the clock —
        // the driver never switched it off, so it must not silently stop (prod report).
        trackingPrefs.edit().putBoolean("was_waiting", true).apply()

        val timeWaitStarted = System.currentTimeMillis()
        // The 100ms loop keeps timeSecond constant for a whole second, so a bare
        // `timeSecond % 5 == 0` block would fire ~10× per boundary — latch on the second
        // itself so the Room write / heartbeat / server nudge run exactly once per 5s.
        var lastHeartbeatSecond = -1L
        lifecycleScope.launch(Dispatchers.Main) {
            while (isTracking.value!! && isWaiting.value!!) {
                lapWaitTime = System.currentTimeMillis() - timeWaitStarted

                val timeMillis = timeWait + lapWaitTime
                timeWaitInMillis.postValue(timeMillis)

                val timeSecond = timeMillis / 1000
                if (timeSecond >= 5 && timeSecond % 5 == 0L && timeSecond != lastHeartbeatSecond) {
                    lastHeartbeatSecond = timeSecond
                    updateWaitedTimeInLocale(timeMillis)
                    // Heartbeat for the dead-gap credit on resume (see beginTracking).
                    trackingPrefs.edit()
                        .putLong("wait_last_tick", System.currentTimeMillis())
                        .apply()
                    // Push the grown timer to the server NOW — a stationary driver produces
                    // no GPS points, and waiting for the 15 s cadence would delay the
                    // waiting_charge_started sound/hint by up to that much.
                    gpsBatchUploader.nudge()
                }

                delay(100L)
            }

            timeWait += lapWaitTime
            // Clean exit (driver/movement stopped the wait) — a later restart must NOT resume.
            // A process kill never reaches this line, leaving was_waiting=true as intended.
            trackingPrefs.edit().putBoolean("was_waiting", false).apply()
        }
    }

    // Release media players
    private fun releaseMpStartedTrack() {
        if (mpStartedTack != null) {
            try {
                mpStartedTack?.release()
                mpStartedTack = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun releaseMpFinishedTrack() {
        if (mpFinishedTrack != null) {
            try {
                mpFinishedTrack?.release()
                mpFinishedTrack = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Create notification for new private order
    private fun setTimerOrderAccept(time: Int) {
        timeAcceptingIndividualOrder.value = time
        cdtAcceptingIndividualOrder = object : CountDownTimer(time.toLong() * 1000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(p0: Long) {
                timeAcceptingIndividualOrder.value = timeAcceptingIndividualOrder.value!! - 1
            }

            override fun onFinish() {
                timeAcceptingIndividualOrder.value = null

                cdtAcceptingIndividualOrder?.cancel()
                cdtAcceptingIndividualOrder = null
            }

        }.start()
    }

    private fun createNotificationForNewPrivateOrder(time: Long) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_NEW_PRIVATE_ORDER,
                NOTIFICATION_CHANNEL_NAME_NEW_PRIVATE_ORDER,
                IMPORTANCE_HIGH
            )

            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.audio_private_order),
                null
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_NEW_PRIVATE_ORDER)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(getString(R.string.individual_order))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(getPendingIntentForNewPrivateOrder())
                .setAutoCancel(true)
                .setTimeoutAfter(time)

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    private fun getPendingIntentForNewPrivateOrder(): PendingIntent {
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(this, MainActivity::class.java)
            intent.action = ACTION_SHOW_DIALOG_INSIDE_APP
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                intent,
                FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.action = ACTION_SHOW_DIALOG_INSIDE_APP
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }

        return pendingIntent
    }

    // Create notification for new order
    private fun createNotificationForNewOrder() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Create channel
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_NEW_ORDER,
                NOTIFICATION_CHANNEL_NAME_NEW_ORDER,
                IMPORTANCE_HIGH
            )

            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.audio_order),
                null
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_NEW_ORDER)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(getString(R.string.new_order))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(getPendingIntentForNewOrder())
                .setAutoCancel(true)
                .setTimeoutAfter(10_000)

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    private fun getPendingIntentForNewOrder(): PendingIntent {
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                intent,
                FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }

        return pendingIntent
    }

    // Create notification for cancel my-order
    private fun createNotificationForCancelMyOrder() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Create channel
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_CANCEL_MY_ORDER,
                NOTIFICATION_CHANNEL_NAME_CANCEL_MY_ORDER,
                IMPORTANCE_HIGH
            )

            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.audio_order_cancel),
                null
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_CANCEL_MY_ORDER)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(getString(R.string.your_order_cancelled))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(getPendingIntentForCancelMyOrder())
                .setAutoCancel(true)

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    private fun getPendingIntentForCancelMyOrder(): PendingIntent {
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                intent,
                FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }

        return pendingIntent
    }

    // Create notification for new notification
    private fun createNotificationForNotification(title: String, moreMessage: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Create channel
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION,
                NOTIFICATION_CHANNEL_NAME_NEW_NOTIFICATION,
                IMPORTANCE_HIGH
            )

            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.audio_notification),
                null
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setStyle(NotificationCompat.BigTextStyle())
                .setContentTitle(title)
                .setContentText(Html.fromHtml(moreMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(getPendingIntentForNotification())
                .setAutoCancel(true)

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    /** Heads-up notification when the wait meter auto-stops because the car drove off. */
    private fun notifyWaitAutoStopped() {
        createNotificationForNotification(
            getString(R.string.wait_auto_stopped_title),
            getString(R.string.wait_auto_stopped_msg)
        )
    }

    /** Heads-up notification when the ride is auto-started (incl. in the background). */
    private fun notifyRideAutoStarted() {
        createNotificationForNotification(
            getString(R.string.ride_started_auto),
            getString(R.string.ride_started_auto_hint)
        )
    }

    /**
     * Surface a trip notice where the driver will actually see it:
     *  • app FOREGROUND → in-app popup (the fragment shows it via [foregroundSignal]);
     *  • app BACKGROUND + overlay permission → an over-other-apps overlay (InfoPopupOverlayService);
     *  • app BACKGROUND, no overlay permission → a heads-up notification ([notificationFallback]).
     * Exactly one fires, so the driver never gets a doubled alert.
     */
    private fun showNotice(
        iconRes: Int,
        titleRes: Int,
        messageRes: Int,
        autoDismissMs: Long,
        foregroundSignal: () -> Unit,
        notificationFallback: () -> Unit
    ) {
        when {
            mapScreenVisible -> foregroundSignal()
            CheckPermissions.checkHasDrawOverlayPermissions(this) ->
                InfoPopupOverlayService.show(this, iconRes, titleRes, messageRes, autoDismissMs)

            else -> notificationFallback()
        }
    }

    /**
     * Background-only "you've reached the destination — finish the order" notice. In the
     * FOREGROUND the fragment's own proximity check shows the finish dialog, so this fires ONLY
     * while backgrounded, with overlay permission, and at most once per order ([finishNoticeShown]).
     */
    private fun maybeNotifyFinishDestination(location: android.location.Location) {
        // Only once the ride is on-route (Go confirmed) — never while still heading to the pickup,
        // even if the pickup happens to sit within the finish radius of the destination.
        if (mapScreenVisible || finishNoticeShown || isTracking.value != true || !rideHasGone) return
        val dest = finalDestination ?: return
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            location.latitude, location.longitude, dest.latitude, dest.longitude, results
        )
        if (results[0] <= Constants.FINISH_DESTINATION_RADIUS_M) {
            finishNoticeShown = true
            if (CheckPermissions.checkHasDrawOverlayPermissions(this)) {
                InfoPopupOverlayService.show(
                    this,
                    R.drawable.ic_error_circle,
                    R.string.finish_destination_title,
                    R.string.finish_destination_msg,
                    openApp = true
                )
            } else {
                notifyFinishDestination()
            }
        }
    }

    /** Heads-up notification fallback for the "reached destination" notice (tap → opens app). */
    private fun notifyFinishDestination() {
        createNotificationForNotification(
            getString(R.string.finish_destination_title),
            getString(R.string.finish_destination_msg)
        )
    }

    /**
     * Shared "Go confirmed" side effects for BOTH the manual slide (ACTION_START_WAY) and the
     * background auto-start (autoStartRide Success): latch the order past the pickup window,
     * freeze the pickup wait, and re-arm the uploader. One copy so the two Go paths can't drift.
     */
    private fun onGoConfirmed(goOrderId: Int) {
        // Stale-Success guard: a delayed orderGo ack (slow network) can land after this order
        // was cancelled and the NEXT one accepted — it must not relabel the new order as GONE
        // (its whole approach leg would upload stamped state 9 and bill at the trip rate).
        // orderId <= 0 (service restarted before resume ran) keeps the legacy behavior of
        // persisting the Go flags for the upcoming resume to restore.
        if (orderId > 0 && goOrderId > 0 && goOrderId != orderId) {
            Timber.w("Stale Go confirm for order %d (active=%d) — ignored", goOrderId, orderId)
            return
        }
        // Leave the pickup window — block any stale-ARRIVED re-arm for this order.
        isArrivedAtPickup = false
        rideHasGone = true
        // From here every point is the billable trip (state 9). The raise also drains the
        // buffered pre-Go points — stamped with THEIR states — right at the boundary, so no
        // approach-leg point can ride into a batch the server bills at the trip rate.
        raiseOrderState(ORDER_STATE_CHANGED_GONE)
        // Freeze the timer reading so the DISPLAYED trip time starts at zero from Go.
        trackTimeAtGoneMs = timeTrackInMillis.value ?: 0L
        if (isTracking.value == true) {
            mpStartedTack?.start()
            // Freeze the pickup wait and STOP the auto-wait at Go — on-route waits are
            // started manually by the driver (Mehrgo has no speed-based auto-wait).
            waitedTimeUntilGone = timeWaitInMillis.value ?: 0L
            updateWaitedTimeUntilGoneInLocale(timeWaitInMillis.value ?: 0L)
            isWaiting.postValue(false)
            // Order progressed (driver on the way) — re-arm the uploader in case it
            // gave up on early post-STARTED 403s (ORDER_GPS_FIXES.md §1).
            gpsBatchUploader.reArm()
            // Seed the server track with the Go-moment position so the A→B metering starts
            // exactly at the pickup, not at the first post-Go GPS callback a few seconds
            // later. (forwardToFareUploader now uploads EVERY sample from order/start — the
            // approach leg included — and the server, not the app, buckets approach vs trip;
            // this Go-seed just guarantees a point exactly at the Go transition.)
            lastLocationWholeApp?.let { loc ->
                forwardToFareUploader(Location("go-seed").apply {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    accuracy = loc.accuracy
                    time = System.currentTimeMillis()
                })
            }
            // Drain immediately so the FIRST server fare lands seconds after Kettik — the
            // no-B hero then flips from the local estimate to live.price almost instantly
            // instead of showing a visible jump after the first 15 s cadence.
            gpsBatchUploader.nudge()
            // The meter has run since Boshlash (STARTED) — i.e. across the driver->pickup approach
            // leg, with no client aboard. Now the client boards (Go/GONE), so drop that leg: open a
            // fresh polyline segment (the receipt-distance observers and the DB persist both read
            // trackLocations.last(), so the meter restarts at zero) and clear the stored approach
            // points so a process restart can't restore them. The taximeter distance + final fare now
            // count only the WITH-CLIENT ride (pickup->dropoff), not the approach.
            addEmptyPolyline()
            updateLocationsInLocale(emptyList())
        }
        // Persist the arm state (Go already happened) so a START_STICKY restart stays disarmed.
        persistArmState()
    }

    /**
     * Fires the order's Go transition from the service so the ride can auto-start even while
     * the app is backgrounded. On success it mirrors the manual flow (freeze the pickup wait,
     * re-arm the uploader), signals the UI via [rideAutoStartedBySpeed] for the in-app
     * acknowledge popup, and posts a heads-up notification.
     */
    private fun autoStartRide() {
        val id = orderId
        if (id <= 0) {
            rideAutoStartInFlight = false
            return
        }
        lifecycleScope.launch {
            try {
                orderGoUC.invoke(id).collect { res ->
                    when (res) {
                        is Resource.Success -> {
                            onGoConfirmed(id)
                            // Only surface UI / fire the heads-up if the order is still active.
                            // If it finished or was cancelled while orderGo was in flight, a
                            // stale Success must not pop a spurious "ride started" notice.
                            if (isTracking.value == true) {
                                // Foreground → in-app acknowledge popup (via the signal, which
                                // also refreshes the order); background → over-other-apps overlay;
                                // else → heads-up notification.
                                showNotice(
                                    R.drawable.baseline_directions_car_24,
                                    R.string.ride_started_auto,
                                    R.string.ride_started_auto_hint,
                                    0L,
                                    foregroundSignal = { rideAutoStartedBySpeed.postValue(true) },
                                    notificationFallback = { notifyRideAutoStarted() }
                                )
                            }
                            rideAutoStartInFlight = false
                        }

                        is Resource.Error -> {
                            // Allow a retry on the next speed tick.
                            rideAutoStartInFlight = false
                        }

                        is Resource.Loading -> {}
                    }
                }
            } catch (e: CancellationException) {
                // Never swallow structured-concurrency cancellation (service teardown / scope
                // cancel) — rethrow so the coroutine unwinds cleanly.
                throw e
            } catch (e: Exception) {
                // OrderGoUC maps Http/IO errors to Resource.Error, but an unmapped throwable
                // (e.g. a malformed response body) would otherwise leave rideAutoStartInFlight
                // stuck true and permanently disable background auto-start for the session.
                Timber.w(e, "Background ride auto-start failed for order %d", id)
                rideAutoStartInFlight = false
            }
        }
    }

    private fun getPendingIntentForNotification(): PendingIntent {
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                intent,
                FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }

        return pendingIntent
    }

    // Location call back for calculate distance during track
    @SuppressLint("MissingPermission")
    // ---- MaktabGo (школьный шаттл): фоновая локация + POST driver/routes/{id}/track ----
    private fun startMaktabGoLocation() {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(false)
            }.build()
        try {
            fusedLocationProviderClient.requestLocationUpdates(
                req, maktabGoLocationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Timber.e("MaktabGo loc: ${e.message}")
        }
    }

    private fun stopMaktabGoLocation() {
        fusedLocationProviderClient.removeLocationUpdates(maktabGoLocationCallback)
    }

    private val maktabGoLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            val loc = result.lastLocation ?: return
            val id = maktabGoRouteId
            if (id <= 0) return
            val now = System.currentTimeMillis()
            if (now - lastMaktabGoTrackMs < 10_000L) return
            lastMaktabGoTrackMs = now
            lifecycleScope.launch {
                birgaTrackUC(id, loc.latitude, loc.longitude).collect { }
            }
        }
    }

    private fun updateLocationTracking(isTrack: Boolean) {
        if (isTrack) {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5_000L
            )
                .apply {
                    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    setWaitForAccurateLocation(false)
                }.build()

            fusedLocationProviderClient.requestLocationUpdates(
                request,
                locationCallBack,
                Looper.getMainLooper()
            )
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallBack)
        }
    }

    private val locationCallBack = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result.locations.let { locations ->
                    for (location in locations) {
//                        Timber.d("NEW LOCATION: ${location.latitude}, ${location.longitude}, ${location.accuracy}")

                        if (isBlockedMockLocation(location)) {
                            Toast.makeText(
                                this@MyTrackingService,
                                getString(R.string.you_are_using_mock_location),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // This 5 s stream keeps the order-request position fresh during a
                            // trip. The upload callback alone lands a fix only every 20 s / 100 m,
                            // so a driver who slid "Boshlash" without having moved 100 m since
                            // going online posted none at all (Retrofit drops null @Field values).
                            rememberWholeAppFix(location)
                            speedCar.postValue((location.speed * 3.6).toInt())
                            forwardToFareUploader(location)
                            if (!isWaiting.value!!) {
                                addPathPoint(location)
                            }
                        }
                    }
                }
            }
        }
    }

    // Fake-GPS guard for both location callbacks. Currently disabled through
    // Constants.BLOCK_MOCK_LOCATIONS so simulated routes can be driven on release builds
    // too; debug builds never enforced it. When the flag goes back on, a mock fix is
    // dropped entirely — no speed, no fare point, no uploaded position.
    private fun isBlockedMockLocation(location: Location): Boolean {
        if (!Constants.BLOCK_MOCK_LOCATIONS || BuildConfig.DEBUG) return false
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
        else location.isFromMockProvider
    }

    // Server-side fare needs *all* fresh samples, including stationary ones — the
    // server derives waiting time from gaps in movement. `addPathPoint` only fires
    // when the local taximeter says we're driving, so we forward to the fare
    // uploader from the GPS callback above.
    private fun forwardToFareUploader(location: Location) {
        // Backend spec (Muhammad, 2026-07-24): the order-gps/batch cycle must run from
        // ORDER/START — as soon as the order reaches state 7 (regular) or state 9
        // (driver/taximeter) — NOT from Kettik. This supersedes the 2026-07-18 "A→B leg only"
        // rule. So forward EVERY fresh sample the moment tracking is running; the uploader itself
        // only starts once beginTracking wired an order id (gpsBatchUploader.start), which happens
        // at order/start. The server buckets by the order's state at upload time — approach-leg
        // points land in `to_client_km` (diagnostic), trip points in `distance_km` (billed) — so
        // sending the approach leg does NOT over-bill. (The LOCAL display meter, addPathPoint, is
        // still gated on rideHasGone below, so the driver's on-screen distance still starts at Go.)
        lifecycleScope.launch {
            runCatching { gpsBatchUploader.enqueue(location) }
                .onFailure { Timber.w(it, "Failed to enqueue GPS point for fare upload") }
        }
    }

    // Timestamp of the last ACCEPTED path point — glitch guard denominator. Not advanced on
    // dropped points, so after a real gap (tunnel) the growing dt lets a genuine fix through.
    private var lastPathPointTimeMs = 0L

    private fun addPathPoint(location: Location?) {
        // Boss spec: the trip meter runs ONLY from Go/"Kettik" — while heading to the client
        // no path points accumulate, so the distance/price display stays at zero.
        if (!rideHasGone) return
        location ?: return

        // GPS glitch guards: (a) "null island" (0,0) fixes — one such point once inflated the
        // live meter by a ~7 157 km jump (26.5M so'm); (b) physically impossible jumps
        // (> ~300 km/h between consecutive accepted fixes).
        if (kotlin.math.abs(location.latitude) < 0.1 && kotlin.math.abs(location.longitude) < 0.1) {
            return
        }
        val lastPoint = trackLocations.value?.lastOrNull()?.lastOrNull()
        if (lastPoint != null && lastPathPointTimeMs > 0) {
            val result = FloatArray(1)
            Location.distanceBetween(
                lastPoint.latitude, lastPoint.longitude,
                location.latitude, location.longitude, result
            )
            val dtSec = ((location.time - lastPathPointTimeMs) / 1000.0).coerceAtLeast(0.5)
            if (result[0] / dtSec > 84) return // ~300 km/h — impossible, drop the glitch
        }
        lastPathPointTimeMs = location.time

        trackLocations.value?.apply {
            last().add(MyLocation(location.latitude, location.longitude, location.accuracy))
            trackLocations.postValue(this)
        }

        if (trackLocations.value != null) {
            updateLocationsInLocale(trackLocations.value!!.last())
        }
    }

    private fun addEmptyPolyline() = trackLocations.value?.apply {
        add(mutableListOf())
        trackLocations.postValue(this)
    } ?: trackLocations.postValue(mutableListOf(mutableListOf()))

    // Location call back for upload location
    @SuppressLint("MissingPermission")
    private fun updateLocationForUpload() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            20_000L
        ).apply {
            setMinUpdateDistanceMeters(100f)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallBackForUpload,
            Looper.getMainLooper()
        )
    }

    private val locationCallBackForUpload = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.locations.let { locations ->
                for (location in locations) {
//                    Timber.d("Location for upload: ${location.latitude}, ${location.longitude}")

                    maybeNotifyFinishDestination(location)

                    if (isBlockedMockLocation(location)) {
                        Toast.makeText(
                            this@MyTrackingService,
                            getString(R.string.you_are_using_mock_location),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        rememberWholeAppFix(location)
                        lastLatLngWholeApp.postValue(LatLng(location.latitude, location.longitude))
                        uploadLocation(location)
                    }
                }
            }
        }
    }

    /**
     * Seed [lastLocationWholeApp] the moment the driver goes on shift. The live callbacks that
     * feed it need a real GPS fix first — the tracking one is armed only from ACCEPT, and the
     * upload one waits for an accurate fix — so an offer accepted in the first seconds of a
     * shift (parked at a rank, indoors) posted order/accept with no position at all. The fused
     * provider almost always has a cached fix; one shot is enough, the callbacks take over.
     */
    @SuppressLint("MissingPermission")
    private fun primeLastFixOnce() {
        if (!CheckPermissions.checkLocationPermission(this)) return
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            location ?: return@addOnSuccessListener
            // Never overwrite a live fix that landed while this one-shot was in flight.
            if (lastLocationWholeApp != null) return@addOnSuccessListener
            // Freshness gate, same as OrdersMapFragment's prime: this becomes the driver's
            // recorded position at accept/skip. A cached fix from hours ago would put them
            // kilometres away — staying null (an honest absence) is the better wire value.
            if (System.currentTimeMillis() - location.time > LAST_FIX_PRIME_MAX_AGE_MS) {
                return@addOnSuccessListener
            }
            if (isBlockedMockLocation(location)) return@addOnSuccessListener
            rememberWholeAppFix(location)
        }
    }

    private fun uploadLocation(location: Location) {
        lifecycleScope.launch {
            uploadLocationUC.invoke(location.latitude, location.longitude, location.bearing)
                .collect {
                    when (it) {
                        is Resource.Loading -> {
                            Timber.d("Loading...")
                        }

                        is Resource.Success -> {
                            Timber.d("Success: " + it.data?.data)
                        }

                        is Resource.Error -> {
                            Timber.d("Error: ${it.message}")
                        }
                    }
                }
        }
    }

    // Locale Database
    private fun addCalculationToLocale(calculation: Calculation) {
        lifecycleScope.launch {
            addCalculationUC.invoke(calculation)
        }
    }

    private fun updateLocationsInLocale(locations: List<MyLocation>) {
        lifecycleScope.launch {
            updateLocationsUC.invoke(orderId, locations)
        }
    }

    private fun updateWaitedTimeInLocale(waitedTime: Long) {
        lifecycleScope.launch {
            updateWaitedTimeUC.invoke(orderId, waitedTime)
        }
    }

    private fun updateWaitedTimeUntilGoneInLocale(waitedTimeUntilGone: Long) {
        lifecycleScope.launch {
            updateWaitedTimeUntilGoneUC.invoke(orderId, waitedTimeUntilGone)
        }
    }

    private fun updateTrackedTimeInLocale(trackedTime: Long) {
        lifecycleScope.launch {
            updateTrackedTimeUC.invoke(orderId, trackedTime)
        }
    }

    private fun uploadErrors(errors: List<ErrorRequest>) {
        lifecycleScope.launch {
            sendLocationUC.invoke(errors).collect {
                when (it) {
                    is Resource.Loading -> {
                        Timber.d("Loading...")
                    }

                    is Resource.Success -> {
                        Timber.d("ERRORS UPLOAD SUCCESS ${errors}")
                        ErrorRequestManager.clearAll()
                    }

                    is Resource.Error -> {
                        Timber.d("ERRORS UPLOAD FAILED: ${it.message}")
                    }
                }
            }
        }
    }
}