package uz.teamwork.mehrgodriver.common.fare

import android.location.Location
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.fare.GpsBatchUploader.Companion.BATCH_LIMIT
import uz.teamwork.mehrgodriver.common.fare.GpsBatchUploader.Companion.MAX_SERVER_REJECTS
import uz.teamwork.mehrgodriver.common.shared_pref.FareCpidManager
import uz.teamwork.mehrgodriver.domain.model.fare.FareGpsBatchRequest
import uz.teamwork.mehrgodriver.domain.model.fare.FareGpsPoint
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.model.locale.PendingGpsPoint
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import uz.teamwork.mehrgodriver.domain.repository.locale.PendingGpsPointsRepository
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min

/**
 * Server-side fare implementation per MOBILE.md, hardened per ORDER_GPS_FIXES.md.
 *
 * Lifecycle (per active order):
 *   start(orderId) -> enqueue(location)* -> flushNow() -> stop()
 *
 * Responsibilities:
 *   1. Client-side filtering with a cadence floor (§3) so smooth curves are not
 *      collapsed into a straight chord ("oncoming-lane" artifact).
 *   2. Room queue persistence — every accepted point survives app kill / no network.
 *      Persist failures are logged, never swallowed silently (§4).
 *   3. Monotonic per-order `cpid` allocation (idempotency key on the server).
 *   4. FIFO drain via REST `order-gps/batch`, batches of [BATCH_LIMIT], triggered by
 *      the periodic loop AND from the enqueue/location-callback path (§2).
 *   5. Backoff + retry on transient errors (5xx, network) with the SAME cpid.
 *   6. 403/404 are treated as TRANSIENT and retried with the same cpids (§1); only
 *      after [MAX_SERVER_REJECTS] consecutive rejects do we give up and latch the gate
 *      closed until a lifecycle transition (start / flushNow / reArm) re-arms it.
 *
 * Thread-safety: a single `Mutex` serialises uploads so we never send the same
 * batch twice from two coroutines. Enqueue is fast and non-blocking.
 */
class GpsBatchUploader(
    private val repository: MainRepository,
    private val pendingRepo: PendingGpsPointsRepository,
    private val socketChannel: GpsBatchSocketChannel? = null
) {

    companion object {
        // Per-request payload cap and flush cadence (ORDER_GPS_FIXES.md final tuning table).
        const val BATCH_LIMIT = 50            // max points per order-gps/batch POST
        const val FLUSH_THRESHOLD_POINTS =
            20 // flush once this many points are buffered since last flush
        const val FLUSH_INTERVAL_MS = 15_000L // ...or this long has elapsed since the last flush

        // Client-side filter (ORDER_GPS_FIXES.md §3).
        const val MIN_DISTANCE_M = 50.0
        const val MIN_BEARING_DELTA_DEG = 15.0
        const val SOFT_ACCURACY_GATE_M =
            50.0f     // drop above this UNLESS a moving point is overdue
        const val HARD_ACCURACY_CEILING_M = 150.0f // always drop above this
        const val MAX_GAP_SECONDS =
            6L             // moving cadence floor: force a point at least this often
        const val MIN_SPEED_MS =
            1.94f             // isMoving threshold ≈ 7 km/h (matches Constants.MINIMAL_SPEED)

        // Transient 403/404 tolerance before giving up (ORDER_GPS_FIXES.md §1).
        const val MAX_SERVER_REJECTS = 5

        // Network backoff for transient errors. Capped so retries do not pile up.
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L

        // Live fare snapshot — UI observes this directly without round-tripping the service.
        val latestFare = MutableLiveData<FareResponse?>()
    }

    private var orderId: Int = -1
    private var scope: CoroutineScope? = null
    private var flushJob: Job? = null

    private val uploadMutex = Mutex()

    // cpid generator. Seeded from the DB so a process restart doesn't reissue ids.
    private val cpidCounter = AtomicLong(0L)

    // Last accepted point used for client-side dedupe (distance / bearing delta).
    @Volatile
    private var lastAccepted: PendingGpsPoint? = null

    // When [lastAccepted] was accepted (wall clock, ms) — drives the moving cadence floor.
    @Volatile
    private var lastAcceptedAtMs: Long = 0L

    // Flush cadence bookkeeping (§2): points enqueued since the last drain, and when it ran.
    private val pendingSinceFlush = AtomicLong(0L)

    @Volatile
    private var lastFlushAtMs: Long = 0L

    // Order state (Constants.ORDER_STATE_*) stamped onto every point at ENQUEUE time, so the
    // server bills each point under the state it was CAPTURED in even when the batch drains
    // after a transition (backend directive 2026-07-30: a post-Kettik flush of the buffered
    // approach leg was billed at the full trip rate — order 79557). 0 = not yet known.
    @Volatile
    private var currentOrderState: Int = 0

    // Consecutive 403/404 rejects, and the gate latch that stops hammering a server-rejected
    // order until a lifecycle transition (start / flushNow / reArm) re-enables it (§1).
    @Volatile
    private var consecutiveServerRejects = 0

    @Volatile
    private var isBatchUploadEnabled = true

    private val _fareUpdates = MutableSharedFlow<FareResponse>(extraBufferCapacity = 16)
    val fareUpdates: SharedFlow<FareResponse> = _fareUpdates.asSharedFlow()

    /**
     * Live wait timers (ORDER_COMPLETE_WAITING.md §7.1): returns (pickupWaitMs, onWayWaitMs)
     * as CUMULATIVE totals. Installed by MyTrackingService; the values ride along in the
     * batch body only when they changed since the last successful send.
     */
    @Volatile
    var waitTimersProvider: (() -> Pair<Long, Long>)? = null

    // Last successfully-sent cumulative timers — "only send when changed" bookkeeping.
    @Volatile
    private var lastSentWaitMs = -1L

    @Volatile
    private var lastSentOnWayMs = -1L

    /** Starts the uploader for [orderId]. Idempotent: re-calling is a no-op. */
    suspend fun start(orderId: Int, initialState: Int = 0) {
        if (this.orderId == orderId && scope != null) return
        stop()

        this.orderId = orderId
        // Install the state stamp and reset the fast-path bookkeeping BEFORE the first
        // suspension (the Room cpid query below): from the orderId assignment above enqueue()
        // accepts points for THIS order, and a fix delivered while that query runs must carry
        // this order's state — not the previous order's final 9/GONE, which would open the new
        // order's billable track at the accept position (the 79557 over-billing class).
        currentOrderState = initialState
        lastAccepted = null
        lastAcceptedAtMs = 0L
        pendingSinceFlush.set(0L)
        lastFlushAtMs = System.currentTimeMillis()
        consecutiveServerRejects = 0
        isBatchUploadEnabled = true
        // 0L (not -1) so a fresh order doesn't fire a pointless {0,0} timer-only batch.
        lastSentWaitMs = 0L
        lastSentOnWayMs = 0L
        latestFare.postValue(null)
        // The DB may have been purged after a successful drain, so we can't trust it
        // alone — seed from the persisted max-ever-issued cpid. Whichever is higher wins.
        // (A point racing this query increments the pre-seed counter and bumps the prefs
        // high-water first, so the max below can never reissue its cpid.)
        val fromDb = pendingRepo.lastCpid(orderId) ?: 0L
        val fromPrefs = FareCpidManager.last(orderId)
        cpidCounter.set(maxOf(fromDb, fromPrefs))

        // Server-initiated price pushes (recompute between batches, incl. §7.4 events)
        // flow into the same publish pipe as batch acks.
        socketChannel?.onPricePush = { fare -> publishFare(fare) }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { s ->
            flushJob = s.launch {
                while (true) {
                    runCatching { drainOnce() }
                        .onFailure { Timber.w(it, "drainOnce failed") }
                    delay(FLUSH_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
        scope?.cancel()
        scope = null
        orderId = -1
        // A finished order's 9/GONE must never leak onto the next order's first points.
        currentOrderState = 0
        lastAccepted = null
        lastAcceptedAtMs = 0L
        pendingSinceFlush.set(0L)
        socketChannel?.onPricePush = null
    }

    /**
     * Stops only if the uploader is still serving [orderId]. The finish teardown's flush can
     * retry offline for minutes; by the time its trailing stop runs a NEWER order may own the
     * uploader — a blanket stop() would silently kill that order's whole track upload.
     */
    fun stopIf(orderId: Int) {
        if (this.orderId == orderId) stop()
    }

    /**
     * Re-arms the uploader after a give-up (5 consecutive 403/404). Called on an order
     * lifecycle transition (e.g. the driver pressing "on the way") so a stalled uploader
     * gets a fresh chance once the server has surely committed the order's state.
     */
    fun reArm() {
        consecutiveServerRejects = 0
        isBatchUploadEnabled = true
    }

    /**
     * Installs the order state stamped onto every point enqueued from now on. On a CHANGE it
     * also re-arms + drains immediately, so (a) a reject-latched gate gets a fresh chance at
     * every lifecycle step — the observed "track not arriving during the approach" starvation
     * can never outlive a state transition — and (b) the previous state's points (already
     * stamped with THEIR state) leave right at the boundary instead of piling into a mixed
     * batch that the server used to bill entirely at the new state's rate.
     */
    fun setOrderState(state: Int) {
        if (state == currentOrderState) return
        currentOrderState = state
        reArm()
        nudge()
    }

    /**
     * Accepts a fresh GPS sample. Applies client-side filtering and, if kept,
     * persists it to Room. Flush is nudged from here (the location-callback path) on
     * either the point-count threshold or the elapsed-time floor, so a backgrounded /
     * locked device still drains promptly instead of waiting for the periodic loop.
     */
    suspend fun enqueue(location: Location) {
        val orderId = this.orderId
        if (orderId <= 0) return
        if (!passesClientFilter(location)) return

        val now = System.currentTimeMillis()
        val cpid = cpidCounter.incrementAndGet()
        // Persist the high-water mark so a crash/restart between enqueue and the
        // periodic flush doesn't reissue a cpid the server has already seen.
        FareCpidManager.bumpAtLeast(orderId, cpid)
        val point = PendingGpsPoint(
            orderId = orderId,
            cpid = cpid,
            lat = location.latitude,
            lon = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null, // m/s
            bearing = if (location.hasBearing()) location.bearing else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            provider = location.provider,
            ts = if (location.time > 0) location.time else now,
            state = currentOrderState.takeIf { it > 0 }
        )

        // Never let a persist failure become an invisible gap in the route (§4): log loudly.
        try {
            pendingRepo.enqueue(point)
        } catch (e: Exception) {
            Timber.w(
                e,
                "Failed to persist GPS point (order=%d, cpid=%d) — point dropped",
                orderId,
                cpid
            )
            return
        }
        lastAccepted = point
        lastAcceptedAtMs = now
        pendingSinceFlush.incrementAndGet()

        val elapsed = now - lastFlushAtMs
        if (pendingSinceFlush.get() >= FLUSH_THRESHOLD_POINTS || elapsed >= FLUSH_INTERVAL_MS) {
            scope?.launch {
                runCatching { drainOnce() }
                    .onFailure { Timber.w(it, "enqueue-driven flush failed") }
            }
        }
    }

    /**
     * Fire-and-forget drain nudge. Called on every 5 s wait tick and at Go/"Kettik" so
     * timer changes and the first post-Go fare reach the server in seconds instead of
     * the 15 s cadence — the billing sound (§7.4) and the no-B hero sync depend on it.
     * drainOnce() is mutex-serialised, so overlapping nudges are harmless.
     */
    fun nudge() {
        scope?.launch {
            runCatching { drainOnce() }
                .onFailure { Timber.w(it, "nudge drain failed") }
        }
    }

    /**
     * Drains as many full batches as possible right now (used on socket reconnect / trip end).
     * [includeTimers] = false for the TRIP-END flush: the order is already completed
     * server-side and the final timers rode in the complete body, so a trailing timer-only
     * batch would just bounce with 403 "Order is not in progress".
     */
    suspend fun flushNow(includeTimers: Boolean = true) {
        if (orderId <= 0) return
        // Give the trip-end / reconnect flush a fresh budget even if we'd given up mid-trip.
        reArm()
        drainOnce(includeTimers)
    }

    private fun passesClientFilter(location: Location): Boolean {
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val speed = if (location.hasSpeed()) location.speed else 0f
        val isMoving = speed >= MIN_SPEED_MS
        val now = System.currentTimeMillis()

        // Hard ceiling: an obviously-bad fix is never useful, even for drawing the route.
        if (accuracy != null && accuracy > HARD_ACCURACY_CEILING_M) return false

        // Cadence floor: while moving, force at least one point every MAX_GAP_SECONDS so a
        // smooth curve isn't reduced to its endpoints (the corner-cutting artifact).
        val movingStale = isMoving &&
                (lastAcceptedAtMs == 0L || now - lastAcceptedAtMs >= MAX_GAP_SECONDS * 1000L)

        // Soft accuracy gate: drop coarse fixes to save bandwidth, but NOT when that would
        // open a gap while moving — a coarse point beats no point for route drawing.
        if (accuracy != null && accuracy > SOFT_ACCURACY_GATE_M && !movingStale) return false

        val prev = lastAccepted ?: return true

        // Stop<->move transitions matter for the waiting calculation — always keep them.
        val prevMoving = (prev.speed ?: 0f) >= MIN_SPEED_MS
        if (prevMoving != isMoving) return true

        // Cadence floor reached — send.
        if (movingStale) return true

        // Distance: skip near-duplicates.
        val out = FloatArray(1)
        Location.distanceBetween(prev.lat, prev.lon, location.latitude, location.longitude, out)
        if (out[0] >= MIN_DISTANCE_M) return true

        // Bearing change still matters — turns should be captured even when stationary in metres.
        if (location.hasBearing() && prev.bearing != null) {
            val delta = abs(normaliseBearing(location.bearing - prev.bearing))
            if (delta >= MIN_BEARING_DELTA_DEG) return true
        }

        return false
    }

    private fun normaliseBearing(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    private suspend fun drainOnce(includeTimers: Boolean = true) {
        val orderId = this.orderId
        if (orderId <= 0) return

        uploadMutex.withLock {
            // Gate closed after giving up on a server-rejected order — stop hammering until re-armed.
            if (!isBatchUploadEnabled) return

            var backoff = INITIAL_BACKOFF_MS
            while (true) {
                val batch = pendingRepo.nextBatch(orderId, BATCH_LIMIT)
                if (batch.isEmpty()) {
                    // No points — but a changed wait timer must still reach the server
                    // (§7.1 rule 3: a stationary driver produces no GPS points while the
                    // waiting bill is running). Points-less batch carries just the timers.
                    val (waitMs, onWayMs) =
                        if (includeTimers) pendingTimers() else Pair(null, null)
                    if (waitMs != null || onWayMs != null) {
                        val result = uploadBatch(
                            orderId,
                            FareGpsBatchRequest(
                                points = emptyList(),
                                waitingTime = waitMs,
                                waitingTimeOntheway = onWayMs
                            ),
                            emptyList(),
                            sentWaitMs = waitMs,
                            sentOnWayMs = onWayMs
                        )
                        // Same latch as the point path: a TERMINAL failure (401, or the Nth
                        // 403/404) must not keep hammering every 15 s just because the wait
                        // timer keeps ticking. TRANSIENT is simply retried next cycle.
                        if (result == UploadResult.TERMINAL_FAIL) {
                            isBatchUploadEnabled = false
                        }
                    }
                    // Fully drained — reset the flush cadence for the next window.
                    pendingSinceFlush.set(0L)
                    lastFlushAtMs = System.currentTimeMillis()
                    return
                }

                val (waitMs, onWayMs) = pendingTimers()
                val request = FareGpsBatchRequest(
                    points = batch.map { it.toApiPoint() },
                    waitingTime = waitMs,
                    waitingTimeOntheway = onWayMs
                )

                when (uploadBatch(orderId, request, batch, waitMs, onWayMs)) {
                    UploadResult.SUCCESS -> {
                        backoff = INITIAL_BACKOFF_MS
                        // Loop — there may be more full batches buffered.
                    }

                    UploadResult.TRANSIENT_FAIL -> {
                        delay(backoff)
                        backoff = min(backoff * 2, MAX_BACKOFF_MS)
                    }

                    UploadResult.TERMINAL_FAIL -> {
                        // Gave up after MAX_SERVER_REJECTS — latch the gate so the periodic loop
                        // stops retrying until a lifecycle transition re-arms us. Points stay queued.
                        isBatchUploadEnabled = false
                        return
                    }
                }
            }
        }
    }

    /** Current cumulative timers, or nulls when unchanged since the last successful send. */
    private fun pendingTimers(): Pair<Long?, Long?> {
        val timers = waitTimersProvider?.invoke() ?: return Pair(null, null)
        val wait = timers.first.takeIf { it != lastSentWaitMs }
        val onWay = timers.second.takeIf { it != lastSentOnWayMs }
        return Pair(wait, onWay)
    }

    private suspend fun uploadBatch(
        orderId: Int,
        request: FareGpsBatchRequest,
        batch: List<PendingGpsPoint>,
        sentWaitMs: Long? = null,
        sentOnWayMs: Long? = null
    ): UploadResult {
        fun markTimersSent() {
            if (sentWaitMs != null || sentOnWayMs != null) {
                Timber.tag("FareLive").d(
                    "batch sent: points=%d waiting_time=%s waiting_time_ontheway=%s",
                    batch.size, sentWaitMs, sentOnWayMs
                )
            }
            sentWaitMs?.let { lastSentWaitMs = it }
            sentOnWayMs?.let { lastSentOnWayMs = it }
        }
        // Prefer the live socket if it's connected — saves HTTP overhead and the latency
        // matters for the live-price push. REST is the safe fallback.
        socketChannel?.let { ch ->
            if (ch.isConnected()) {
                val fare = ch.sendBatchAwaitAck(orderId, request)
                if (fare != null) {
                    if (batch.isNotEmpty()) pendingRepo.deleteByIds(batch.map { it.id })
                    consecutiveServerRejects = 0
                    markTimersSent()
                    publishFare(fare)
                    return UploadResult.SUCCESS
                }
                // Socket failed silently — fall through to REST.
            }
        }

        return try {
            val response = repository.sendGpsBatch(orderId, request)
            val fare = response.data
            if (batch.isNotEmpty()) pendingRepo.deleteByIds(batch.map { it.id })
            consecutiveServerRejects = 0
            markTimersSent()
            if (fare != null) publishFare(fare)
            UploadResult.SUCCESS
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> {
                    // Payload is structurally bad. Don't get stuck retrying it forever —
                    // drop the offending batch but keep newer points.
                    Timber.w("400 from order-gps/batch — dropping batch of %d", batch.size)
                    pendingRepo.deleteByIds(batch.map { it.id })
                    UploadResult.TRANSIENT_FAIL
                }

                403, 404 -> {
                    // Usually transient: the first flush after STARTED can beat the server's
                    // STARTED commit. Retry the SAME points (server de-dupes on cpid via
                    // ON CONFLICT DO NOTHING). Only give up after MAX_SERVER_REJECTS so a
                    // genuinely-not-ours order doesn't loop forever. Genuine cancel is handled
                    // separately (the order-event socket tears the uploader down).
                    consecutiveServerRejects++
                    if (consecutiveServerRejects >= MAX_SERVER_REJECTS) {
                        Timber.w(
                            "Persistent %d on order-gps/batch (%d rejects) — giving up until re-armed",
                            e.code(), consecutiveServerRejects
                        )
                        UploadResult.TERMINAL_FAIL
                    } else {
                        Timber.w(
                            "Transient %d on order-gps/batch (reject %d/%d) — will retry same points",
                            e.code(), consecutiveServerRejects, MAX_SERVER_REJECTS
                        )
                        UploadResult.TRANSIENT_FAIL
                    }
                }

                in 401..499 -> {
                    // Other client errors (e.g. 401) — stop sending; a lifecycle transition
                    // (start / flushNow / reArm) gives us a fresh chance.
                    Timber.w("Terminal %d on order-gps/batch — stopping uploader", e.code())
                    UploadResult.TERMINAL_FAIL
                }

                else -> {
                    // 5xx and anything else — transient, retry with backoff.
                    Timber.w(e, "Transient %d on order-gps/batch — will retry", e.code())
                    UploadResult.TRANSIENT_FAIL
                }
            }
        } catch (e: IOException) {
            Timber.d("Network down — keeping batch queued")
            UploadResult.TRANSIENT_FAIL
        } catch (e: Exception) {
            Timber.w(e, "Unexpected error on order-gps/batch — will retry")
            UploadResult.TRANSIENT_FAIL
        }
    }

    private fun publishFare(fare: FareResponse) {
        // Debug trace for backend-side testing (adb logcat -s FareLive).
        Timber.tag("FareLive").d(
            "live: price=%s dist=%s waitSec=%s onWaySec=%s waitCost=%s services=%s agreed=%s surcharge=%s events=%s (top: price=%s)",
            fare.live?.price, fare.live?.distanceKm, fare.live?.waitingSec,
            fare.live?.onWaySec, fare.live?.waitingCost, fare.live?.servicesPrice,
            fare.live?.agreedPrice, fare.live?.surcharge, fare.events, fare.price
        )
        latestFare.postValue(fare)
        _fareUpdates.tryEmit(fare)
    }

    /**
     * Called by the socket listener when a server-pushed `order_price_updated`
     * arrives — keeps the LiveData / flow in sync without an extra HTTP round-trip.
     */
    fun onSocketFare(fare: FareResponse) {
        publishFare(fare)
    }

    private enum class UploadResult { SUCCESS, TRANSIENT_FAIL, TERMINAL_FAIL }
}

internal fun PendingGpsPoint.toApiPoint(): FareGpsPoint = FareGpsPoint(
    lat = lat,
    lon = lon,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    altitude = altitude,
    provider = provider,
    ts = ts,
    cpid = cpid,
    state = state
)
