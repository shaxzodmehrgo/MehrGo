package uz.teamwork.mehrgodriver.presentation.maps.yandex_map

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.ncorti.slidetoact.SlideToActView
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.ui_view.ViewProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.AppReloadFlag
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.ACTION_ORDER_PRICE_RECALCULATED
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_ORDER_DATA_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_SERVICE
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_TIME_WAIT
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.ACTION_START_WAY
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_SERVICE
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_TIME_WAIT
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_TRACKING
import uz.teamwork.mehrgodriver.common.Constants.ACTION_VERIFICATION_STATUS_CHANGED
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_REPRICED_ORDER_ID
import uz.teamwork.mehrgodriver.common.Constants.KEY_ORDER_DATA
import uz.teamwork.mehrgodriver.common.Constants.KEY_SOCKET_LISTENER
import uz.teamwork.mehrgodriver.common.Constants.MIN_WAITING_TIME_SINGLE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED_PRIVATE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CREATED_CLIENT
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CREATED_DRIVER
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_ARRIVED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_CHANGED_GONE
import uz.teamwork.mehrgodriver.common.Constants.ORDER_STATE_STARTED
import uz.teamwork.mehrgodriver.common.Constants.THEME_DAY
import uz.teamwork.mehrgodriver.common.Constants.THEME_NIGHT
import uz.teamwork.mehrgodriver.common.DriverAvatar
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.MetaEvents
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.ServiceIcons
import uz.teamwork.mehrgodriver.common.fare.GpsBatchUploader
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.services.instroction.RouteNavControllerService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.AccessPermissionsManager
import uz.teamwork.mehrgodriver.common.shared_pref.MapTypeManager
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.common.socket.MySocketListener
import uz.teamwork.mehrgodriver.common.socket.SocketOrderResponse
import uz.teamwork.mehrgodriver.databinding.DialogArrivedToClientBinding
import uz.teamwork.mehrgodriver.databinding.DialogCallDispatcherBinding
import uz.teamwork.mehrgodriver.databinding.DialogDestinationBinding
import uz.teamwork.mehrgodriver.databinding.DialogFinishDestinationBinding
import uz.teamwork.mehrgodriver.databinding.DialogFinishWorkBinding
import uz.teamwork.mehrgodriver.databinding.DialogInfoPopupBinding
import uz.teamwork.mehrgodriver.databinding.DialogOrderCancelBinding
import uz.teamwork.mehrgodriver.databinding.DialogPassingNextDestinationBinding
import uz.teamwork.mehrgodriver.databinding.DialogPaymentErrorBinding
import uz.teamwork.mehrgodriver.databinding.DialogRetryRequestBinding
import uz.teamwork.mehrgodriver.databinding.DialogTrackingFinishBinding
import uz.teamwork.mehrgodriver.databinding.DialogTripMapFullBinding
import uz.teamwork.mehrgodriver.databinding.FragmentMapBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason
import uz.teamwork.mehrgodriver.domain.model.TripReceipt
import uz.teamwork.mehrgodriver.domain.model.VerificationStatus
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.model.error.ErrorOrderFinishResponse
import uz.teamwork.mehrgodriver.domain.model.fare.FareLive
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.model.requests.RequestOrderFinish
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import uz.teamwork.mehrgodriver.presentation.main.adapter.DestinationAdapter
import uz.teamwork.mehrgodriver.presentation.main.adapter.OrderCancelReasonAdapter
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment.Companion.FINISH_PROMPT_RADIUS_M
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment.Companion.PASSED_FINAL_M
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment.Companion.REACHED_FINAL_M
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.trip_finish.TripFinishFragment
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Process-scoped: true only until the first active-order check after a COLD app launch. A warm
 * resume (home button → back) keeps the process, so it stays false and whatever view was showing
 * (order detail or home map) is preserved; a cold launch resets it to true → open on the home map.
 */
private var mapFreshProcessLaunch = true

@AndroidEntryPoint
class MapFragment : Fragment(), CameraListener, DestinationAdapter.OnDestinationClickListener,
    OrderCancelReasonAdapter.OnOrderCancelReasonClickListener {

    companion object {
        /**
         * Set by the order-accept flow (pool accept / auto-offer accept) so the next active-order
         * load opens the trip detail EXPANDED instead of the minimised resume card — regardless of
         * cold-start or prior minimise state. Consumed once, only when an order is actually present.
         * Without this, accepting the first order of a process (mapFreshProcessLaunch == true) would
         * land the driver on the minimised home map instead of the order details.
         */
        var openTripDetailOnNextLoad = false

        /** Longest the finish receipt will wait for a fresh server fare before rendering
         *  from the cached snapshot. Bounded so a slow/offline fare cannot block finishing. */
        // "Drive on past B": the driver must come this close to the last stop before the pass-by
        // latch can arm, so a traffic detour around it never counts as having reached it.
        private const val REACHED_FINAL_M = 80.0

        // ...and then leave it by this much before the route is dropped. Wide enough that parking
        // manoeuvres / GPS drift at the kerb don't clear the line while the driver is still there.
        private const val PASSED_FINAL_M = 300.0

        /**
         * How long the finish flow waits for the fresh `order-gps/fare` before giving up and
         * telling the driver to retry. NOT a fallback window — on expiry the popup is refused,
         * never painted from cache. Generous because it is only a deadlock guard against a
         * wedged socket; a healthy request answers in a few hundred ms.
         */
        private const val FINISH_FARE_WAIT_MS = 20_000L

        /**
         * How long "Ortga" on the final-stop prompt keeps it away. It must come back — the driver
         * may just not be ready to finish yet — but it was previously re-fired by the trackLocations
         * observer, i.e. on every GPS batch, so dismissing it did nothing. Matches the multi-stop
         * checkpoint cadence in [startCheckpointPrompt].
         */
        private const val FINISH_PROMPT_COOLDOWN_MS = 10_000L

        /** How close to the LAST stop counts as "arrived" for the finish prompt. Same 100 m the
         *  mid-stop checkpoint prompt uses, so the two read the map the same way. */
        private const val FINISH_PROMPT_RADIUS_M = 100f

        /** Floor between order-refresh-driven fare re-pulls. getActiveMyOrders() fires from a
         *  dozen call sites (socket events, onResume, state changes); without this the refresh
         *  below would turn a burst of order events into a burst of requests. */
        private const val FARE_REFRESH_MIN_INTERVAL_MS = 8_000L

        /** How often to re-pull the order, before the ride is under way, so a rider's service
         *  toggle shows up. See [startPreGoRepricePolling] — until Go there is no reprice signal
         *  at all, so polling is the only way to notice. */
        private const val PRE_GO_REPRICE_POLL_MS = 5_000L

        /**
         * How long the live-fare stream may stay silent after Go before the poll
         * takes over. A driver who is DRIVING gets a frame every batch, so this
         * never fires for them; a driver parked at the pickup uploads nothing and
         * would otherwise never learn the rider switched a service off.
         */
        private const val POST_GO_FARE_SILENCE_MS = 6_000L
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val mapViewModel: MapViewModel by viewModels()

    // The same @Singleton instance MyTrackingService drives — injected here only to nudge()
    // an immediate queue drain when the finish flow starts (see prepareOrderFinish).
    @Inject
    lateinit var gpsBatchUploader: GpsBatchUploader

    private var _dialogTrackingFinishBinding: DialogTrackingFinishBinding? = null
    private val dialogTrackingFinishBinding get() = _dialogTrackingFinishBinding!!
    private var dialogTrackingFinish: Dialog? = null

    private var _dialogDestinationBinding: DialogDestinationBinding? = null
    private val dialogDestinationBinding get() = _dialogDestinationBinding!!
    private var dialogDestination: Dialog? = null

    private var _dialogFinishWorkBinding: DialogFinishWorkBinding? = null
    private val dialogFinishWorkBinding get() = _dialogFinishWorkBinding!!
    private var dialogFinishWork: Dialog? = null

    private var _dialogRetryRequestBinding: DialogRetryRequestBinding? = null
    private val dialogRetryRequestBinding get() = _dialogRetryRequestBinding!!
    private var dialogRetryRequest: Dialog? = null

    private val dialogPaymentErrorBinding get() = _dialogPaymentErrorBinding!!
    private var dialogPaymentError: Dialog? = null

    private var _dialogPaymentErrorBinding: DialogPaymentErrorBinding? = null

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @Inject
    lateinit var gson: Gson

    private var currentLocation: Location? = null
    private var selectedLocation: LatLng? = null

    private var destinationAdapter: DestinationAdapter? = null

    /**
     * The order on screen.
     *
     * Setting a DIFFERENT order drops the server fare snapshot. Those fields
     * (`latestLive`, `serverPrice`, `serverWaitCost`, `estimateExtras`, …) live
     * until the next frame arrives, and between accepting a new order and its
     * first frame every reader — the hero, `agreedServiceDelta()`, the finish
     * dialog — was quoting the trip that had just ended. That is the "it shows
     * the previous order's fare" report.
     *
     * Done in the setter rather than at the six assignment sites: a per-site
     * reset is one refactor away from being forgotten, and the sites are spread
     * across accept, start, refresh and restore paths.
     */
    private var order: Order? = null
        set(value) {
            if (value?.id != field?.id) clearServerFareSnapshot()
            field = value
        }
    private var destinationLocation: Point? = null

    // "Drive on past B" latch — see updateFinalDestinationPassed(). Keyed by order id rather than
    // cleared at each `order = …` site: a latch leaking into the NEXT trip would leave that trip
    // with no route at all, and there are five assignment sites to miss.
    private var finalPassedOrderId: Int? = null
    private var reachedFinalDestination = false
    private var finalDestinationPassed = false

    private var clientTotalBonus: Long? = null
    private var maxAmount: String? = null
    private var minAmount: Long? = null

    // Latest server fare snapshot. Source of truth for displayed price/distance
    // when present — local calc remains as optimistic UI fallback (MOBILE.md §1).
    /**
     * Forgets everything the server told us about the PREVIOUS order's fare.
     *
     * Every one of these is a snapshot with no owner stamped on it, so the only
     * safe rule is that they belong to whichever order is on screen — the moment
     * that changes they are wrong, not merely stale, and must not be read again
     * until a frame for the new order lands.
     */
    private fun clearServerFareSnapshot() {
        serverPrice = null
        serverDistanceKm = null
        serverWaitCost = null
        serverPriceIncludesExtras = false
        latestLive = null
        estimateExtras = null
        serverFareOrderId = null
    }

    private var serverPrice: Long? = null
    private var serverDistanceKm: Double? = null

    // Waiting-related cost lines from the server breakdown (wait + on-way + traffic) so the
    // finish receipt's "Kutish narxi" row matches the server, not the local meter.
    private var serverWaitCost: Long? = null

    // Latest `live` block (ORDER_COMPLETE_WAITING.md §7.2/§7.4) — carries the agreed-price
    // extras (surcharge / kept_projection) for the hero card on B-point orders.
    private var latestLive: FareLive? = null

    // Booked-at-estimate extras (services + podacha + extra) frozen in `breakdown.estimate` —
    // the baseline for [agreedServiceDelta]. Kept across frames that omit the block.
    private var estimateExtras: Double? = null

    // true when serverPrice came from `live.price`, which ALREADY contains services +
    // podacha + extras + waiting (§7.2) — the receipt must NOT add them again. The legacy
    // top-level `price` lacks services (backend bug 76967) and still needs them added.
    private var serverPriceIncludesExtras = false

    // Which order the applied server-fare fields (serverPrice/serverDistanceKm/…) belong to —
    // the stale-frame guard in applyServerFare() only compares distances within one order.
    private var serverFareOrderId: Long? = null

    private var lastFareRefreshAtMs = 0L

    /**
     * When a server fare frame last LANDED (not when one was last requested).
     *
     * Drives the post-Go poll: silence here means the live-fare stream is not
     * running — a parked driver uploads no GPS batch — and the poll has to cover
     * for it. See startPreGoRepricePolling.
     */
    private var lastServerFareAtMs = 0L

    /**
     * Whether the server's last order list actually contained a trip.
     *
     * Not derivable from `order`: that field is never cleared (the socket-cancel
     * teardown reads the retained object), so it still points at the trip that
     * just ended. Set from the two branches of getActiveMyOrders, which is the
     * only place the answer is authoritative.
     */
    private var hasActiveOrder = false

    /** Last services total we rendered, so a client-side add/remove can be detected on the
     *  next order refresh and repainted without waiting for the network. Null until the first
     *  payload lands — the first observation is a baseline, not a change. */
    private var lastSeenServicesTotal: Long? = null

    /**
     * Re-pull the server fare when the ORDER refreshes.
     *
     * Without this the live hero can only ever change when a GPS batch is acked, and
     * `GpsBatchUploader.drainOnce()` sends nothing at all while no point passes the accuracy /
     * movement filter and no wait timer moves. A stationary driver therefore gets zero acks, so
     * `serverPrice` freezes — and any server-side reprice (the client adding or removing a
     * service is the one that bit us) stays invisible until the finish swipe, which is the only
     * other place that ever calls getFare. The services CHIPS updated correctly the whole time
     * because they come from `order.services`, which is what made the bug look like a rendering
     * glitch rather than a stale snapshot.
     *
     * Debounced: getActiveMyOrders() has a dozen call sites (socket events, onResume, state
     * changes) and must not turn an event burst into a request burst.
     */
    private fun refreshServerFareOnOrderChange(
        currentOrder: Order,
        fromPush: Boolean = false
    ) {
        if (currentOrder.state < ORDER_STATE_STARTED) {
            // Not started yet (ACCEPTED): there is no GPS track, so order-gps/fare has nothing to
            // report — but the ORDER itself IS repriced server-side when the rider toggles a
            // service, and the pre-start hero reads `order.price` (painted in setView). Nothing
            // else refetches the order while the driver just sits on the accept screen, so the
            // price sat frozen on the old amount while the service chips already showed the new
            // set. Re-pull the order; setView then repaints both.
            //
            // Only from the push path: the other call site is INSIDE getActiveMyOrders' own
            // success handler, where the order is already fresh — refetching from there would
            // loop forever.
            if (fromPush) getActiveMyOrders()
            return
        }

        // Services total off the FRESH payload. Deliberately not `totalServicePrice` — that field
        // is recomputed later, in commandStartTracking(), so right here it still holds the
        // pre-refresh value and would report "no change" on the very refresh that changed it.
        val servicesNow = currentOrder.services?.sumOf { it.total.toLong() }
        val servicesBefore = lastSeenServicesTotal
        val servicesChanged =
            servicesNow != null && servicesBefore != null && servicesNow != servicesBefore
        if (servicesNow != null) lastSeenServicesTotal = servicesNow

        // Repaint the hero IMMEDIATELY, with no round trip. `live.price` is
        // fare + services + podacha + extra + waiting, so swapping in the fresh services
        // component is exact for a services-only change — which is precisely the event that
        // got us here. Without this the driver stares at the old number for as long as the
        // getFare below takes (and, before the throttle carve-out, potentially much longer).
        //
        // DISPLAY ONLY: `serverPrice` is deliberately NOT written. A locally-derived number
        // must never become the billed one — the receipt reads serverPrice, and only the
        // server's own response is allowed to set it.
        // No-B (meter) orders only: the formula below swaps the services component inside
        // live.price, which is a METER total — painting it on a B-order would flash the
        // meter over the agreed hero. B-orders get their instant repaint from the getFare
        // below via applyServerFareToTrackingViews (agreed + service delta).
        if (servicesChanged && _binding != null && currentOrder.locations.size < 2) {
            val live = latestLive
            val livePrice = live?.price
            val liveServices = live?.servicesPrice
            if (livePrice != null && liveServices != null && servicesNow != null) {
                val estimate = (livePrice - liveServices).toLong() + servicesNow
                val text =
                    "${Helper.formatPrice(Helper.roundPrice(estimate))}${getString(R.string.sum)}"
                binding.includeDialog.tvTotalPrice.text = text
                updateLiveHeroPrice(text)
                // Tagged FareLive so a single logcat filter shows the optimistic repaint next to
                // the server acks — that is what makes "did the instant update fire?" answerable
                // from a log instead of from watching the screen.
                Timber.tag("FareLive").d(
                    "services changed %d -> %d, hero repainted optimistically %d (live.price %.0f, live.services %.0f)",
                    servicesBefore ?: -1L, servicesNow, estimate, livePrice, liveServices
                )
            }
        }

        val now = System.currentTimeMillis()
        // The throttle exists to absorb redundant order events, NOT to swallow a real reprice.
        // A services change always fetches; everything else waits its turn.
        if (!servicesChanged && now - lastFareRefreshAtMs < FARE_REFRESH_MIN_INTERVAL_MS) return
        lastFareRefreshAtMs = now
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getFare(currentOrder.id).collect { fare ->
                if (fare is Resource.Success) fare.data?.data?.let {
                    applyServerFare(it)
                    applyServerFareToTrackingViews()
                    // Covers the reprice-push path too: a service toggle arriving while the
                    // finish dialog is up must repaint the bill the driver is about to approve.
                    refreshFinishDialogIfShowing()
                }
            }
        }
    }

    /** Ingest one server fare snapshot (batch ack / socket push / finish-time GET).
     *  The `live` block is the preferred source; top-level fields cover older backends. */
    /**
     * The order this fragment has already refused a finish for once. A second
     * consecutive attempt is allowed through on a provisional total — see the
     * escape hatch in prepareOrderFinish.
     */
    private var finishGateRefusedOrderId: Int = -1

    /**
     * Does this frame carry the number the finish popup would actually BILL?
     *
     * Order-shape aware on purpose: a taximeter trip bills the meter
     * (`live.price`), a fixed-route trip bills `kept_projection` (agreed +
     * waiting) and its `live.price` is the meter, which on such an order is
     * simply the wrong figure. Checking only `live.price` therefore blocked
     * finishes on B-orders whose billable projection was present.
     */
    private fun isPricedForFinish(fare: FareResponse): Boolean {
        val fixedRoute = (order?.locations?.size ?: 0) >= 2
        if (fixedRoute && (fare.live?.keptProjection ?: 0.0) > 0.0) return true
        return fare.live?.price != null || fare.price != null
    }

    private fun applyServerFare(fare: FareResponse) {
        // Drop OUT-OF-ORDER frames. Live distance is cumulative within an order, so a frame
        // carrying a smaller distance than the one already applied FOR THE SAME ORDER is an
        // older snapshot arriving late. That really happens at finish: prepareOrderFinish
        // races the nudged batch (socket ack) against a parallel HTTP GET fare — the server
        // can compute the GET before ingesting the batch, yet the GET response can arrive
        // AFTER the ack. Last-writer-wins would then repaint the finish dialog DOWN to the
        // pre-batch total and bill it (the 79688 class through the fix's own path).
        // FareResponse carries no sequence number, so distance is the only ordering key;
        // frames without a live block (legacy shape) can't be compared and pass through as
        // before. The id check keeps a NEW order's first frame (0 km) from being judged
        // against the previous order's meter in the window before the uploader's start()
        // null-post clears these fields. The 0.01 km slack absorbs the 2-dp rounding of the
        // top-level distance fallback.
        val incomingKm = fare.live?.distanceKm
        val appliedKm = serverDistanceKm
        if (incomingKm != null && appliedKm != null &&
            fare.orderId != null && fare.orderId == serverFareOrderId
        ) {
            if (incomingKm < appliedKm - 0.01) {
                Timber.tag("FareLive")
                    .d("stale fare frame dropped: dist=%.3f < applied=%.3f", incomingKm, appliedKm)
                return
            }
            // Distance tie (stationary driver — all points already acked): the wait clock is
            // the remaining monotonic axis. A timers-only ack and a pre-ingest GET report the
            // same km but different waiting seconds; last-writer-wins on the older one would
            // repaint the dialog DOWN a paid waiting minute. Fail-open when either side lacks
            // the fields (legacy shape). Service-toggle reprices at identical km AND identical
            // wait remain unordered — nothing client-side can sequence those (needs a server
            // sequence field).
            val incomingWait = fare.live?.waitingSec?.plus(fare.live?.onWaySec ?: 0L)
            val appliedWait = latestLive?.waitingSec?.plus(latestLive?.onWaySec ?: 0L)
            if (incomingKm <= appliedKm + 0.01 &&
                incomingWait != null && appliedWait != null && incomingWait < appliedWait
            ) {
                Timber.tag("FareLive")
                    .d(
                        "stale fare frame dropped: wait=%ds < applied=%ds",
                        incomingWait,
                        appliedWait
                    )
                return
            }
        }
        // A NEW order's frames must not inherit the previous order's booked-extras baseline —
        // the `?: estimateExtras` retention below is only valid within one order, and the
        // uploader's start() null-post (the other clearing path) is coalescible while the
        // view is stopped. The `order` setter clears the whole snapshot too; this stays as
        // the second line of defence, for a frame that arrives before the new order does.
        if (fare.orderId != null && serverFareOrderId != null &&
            fare.orderId != serverFareOrderId
        ) {
            estimateExtras = null
        }
        lastServerFareAtMs = System.currentTimeMillis()
        serverFareOrderId = fare.orderId
        latestLive = fare.live
        serverPriceIncludesExtras = fare.live?.price != null
        serverPrice = fare.live?.price?.roundToLong() ?: fare.price
        serverDistanceKm = fare.live?.distanceKm ?: fare.distanceKm
        serverWaitCost = fare.live?.waitingCost?.roundToLong() ?: fare.breakdown?.let { b ->
            fun cost(key: String): Double = try {
                b.get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
            } catch (_: Exception) {
                0.0
            }
            (cost("wait_cost") + cost("on_way_cost") + cost("traffic_cost")).toLong()
        }
        // Booked extras frozen at estimate time. The batch ack carries them in `breakdown`,
        // the GET fare in `price_breakdown` — both opaque JsonObjects, walked defensively.
        estimateExtras = try {
            (fare.breakdown ?: fare.priceBreakdown)
                ?.takeIf { it.has("estimate") && it.get("estimate").isJsonObject }
                ?.getAsJsonObject("estimate")
                ?.let { e ->
                    fun num(k: String): Double =
                        e.get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble ?: 0.0
                    num("services_price") + num("podacha") + num("extra_price")
                } ?: estimateExtras
        } catch (_: Exception) {
            estimateExtras
        }
    }

    /**
     * Mid-trip service add/remove delta on top of the agreed price.
     *
     * The server does NOT reprice `order.price` / `live.agreed_price` when the rider toggles
     * a service after the trip starts — wire-proved on order 79711: adding AC mid-trip left
     * both at 21 000 while `live.services_price` moved 10 000 → 12 000 and the final bill
     * included the 12 000. The booked set stays frozen in `breakdown.estimate`; the CURRENT
     * set rides in the live block. agreed-now = order.price + (live extras − booked extras).
     * 0 while either side is missing (no fare snapshot yet / legacy shape).
     */
    private fun agreedServiceDelta(): Long {
        val lv = latestLive ?: return 0L
        val booked = estimateExtras ?: return 0L

        // Services come from the ORDER, not from the fare frame.
        //
        // Both describe the same money, but they arrive at different times: the
        // order refresh carries the new service set the moment the rider toggles
        // one (the reprice poll fetches it), while `order-gps/fare` needs a couple
        // of seconds to recompute server-side. Reading services off the frame is
        // what made the chip vanish instantly and the amount follow seconds later.
        //
        // `order_items[].total` is the same figure the frame reports as
        // `services_price` (wire: item total 2 000 ↔ live.services_price 2 000), so
        // the two agree once the frame catches up — this only wins the race, it
        // does not invent a different number. Falls back to the frame while the
        // order carries no item list.
        val services = order?.services?.sumOf { it.total.toDouble() }
            ?: (lv.servicesPrice ?: 0.0)

        val liveExtras = services + (lv.podacha ?: 0.0) + (lv.extraPrice ?: 0.0)
        return (liveExtras - booked).roundToLong()
    }

    // Guards observerMyTrackingService() to register the tracking LiveData observers exactly
    // once per view lifetime. commandStartTracking() (hence getActiveMyOrders / onResume) can
    // run repeatedly during a trip, and re-.observe(viewLifecycleOwner) would STACK duplicate
    // observers — each tick would then recompute price/distance N times (double-counting).
    // Reset in onDestroyView (the viewLifecycleOwner is destroyed there → observers auto-removed).
    private var trackingObserversRegistered = false

    // Identity of the last fare whose `services_changed` event was already acted on. LiveData
    // replays its last value on (re)registration — without this, a view recreation would
    // re-trigger the order refetch for an event that was already handled.
    private var lastServicesChangedFare: FareResponse? = null

    private var verification: VerificationStatus? = null

    // Moderator approved/rejected a document while the map is open → refresh the chip.
    private val verificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadVerification()
        }
    }

    /**
     * The server repriced this order (client toggled a service, surge, …) and told us by push.
     * This is the only signal that arrives while the driver just sits in the app: nothing else
     * refreshes the order, and the socket's order_price_updated rides the GPS-batch cycle.
     *
     * We ignore the price in the push and re-pull `order-gps/fare` instead — a pushed amount is
     * a hint, never a quotable number. Bypasses the poll throttle: a push means something
     * really changed.
     */
    private val orderRepricedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val repricedId = intent?.getIntExtra(EXTRA_REPRICED_ORDER_ID, -1) ?: -1
            val current = order ?: return
            if (repricedId != current.id) return
            Timber.tag("FareLive").d("repriced push for order %d -> re-pulling order", repricedId)
            lastFareRefreshAtMs = 0L
            // Re-pull the ORDER, not just the fare. The fare endpoint answers
            // "what does the meter say"; it does not carry the service rows or
            // `order.price`, and on an A→B trip the hero is painted from the
            // CACHED order.price. So a rider toggling a service mid-trip moved
            // nothing on the driver's screen — not the chips, not the "N
            // xizmat" count, not the agreed hero — until he backgrounded the
            // app and came back.
            //
            // getActiveMyOrders() calls refreshServerFareOnOrderChange itself
            // (line ~2091) with the FRESH payload, which is also what makes the
            // services-changed detection and the instant repaint fire at all.
            // Calling BOTH would launch two overlapping getFare collectors
            // whose responses race to write `serverPrice` — the number the
            // receipt bills — with no ordering guard.
            getActiveMyOrders()
        }
    }

    private val gpsReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                if (CheckPermissions.isGPSEnabled(requireContext())) {
                    binding.cvMyCurrentLocationEnabled.visibility = View.VISIBLE
                    binding.cvMyCurrentLocationDisabled.visibility = View.GONE
                } else {
                    binding.cvMyCurrentLocationEnabled.visibility = View.GONE
                    binding.cvMyCurrentLocationDisabled.visibility = View.VISIBLE
                }
            }
        }
    }

    private val socketListenerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isOpenedSocket = intent?.getBooleanExtra(KEY_SOCKET_LISTENER, false)
            if (isOpenedSocket == true) {
                destinationLocation = null
                mapObjectsDestinationPoint.clear()
                mapObjectsPolyline.clear()
                // Reset the live-route state so it rebuilds from the current position on reopen.
                trackedRouteTail.clear()
                drawnRoutePoints.clear()
                miniRouteLine?.clear()
                lastTrimLat = 0.0
                needsCursorSeed = true
                rerouteInFlight = false
                getOrders()
                getActiveMyOrders()
//                showToast("Salom1")
            }
        }
    }

    private val orderDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra(KEY_ORDER_DATA)

            // A malformed / string-typed numeric field in the raw socket frame must never crash
            // the map (crash A). Drop the bad frame instead of parsing it unguarded.
            val response = try {
                gson.fromJson(data, SocketOrderResponse::class.java)
            } catch (e: Exception) {
                Timber.e(e, "orderDataReceiver: bad ORDER_DATA payload: $data")
                return
            } ?: return
            if (response.key == ORDER_NEW || response.key == ORDER_ACCEPTED) {
                getOrders()
            } else if (response.key == ORDER_CANCELLED || response.key == ORDER_CANCELLED_PRIVATE) {
                // A cancellation arrived — but the socket fires for ANY order, including pool orders
                // the driver never accepted (a client cancels a still-"new" order in the list). It
                // only concerns THIS driver when the cancelled order is the one we're actively
                // handling, so guard on the id. Without this, an unrelated pool cancellation wrongly
                // popped "your order cancelled" and wiped the still-active trip's route/markers.
                val cancelledIsActive = order != null && response.data.id == order!!.id
                if (cancelledIsActive) {
                    // Our active order is gone → alert, clear the route + markers, and re-fetch the
                    // active order: an empty result runs the full teardown (hides the trip detail;
                    // clears the home trip overlay + mini route + polyline).
                    // The server sends the SAME order_cancelled_for_nurse frame whether the client
                    // or the driver cancelled — the frame doesn't say who. So the popup always
                    // shows, but with NEUTRAL wording ("order cancelled"), never attributing the
                    // cancel to the client.
                    showInfoPopup(
                        R.drawable.ic_error_circle,
                        R.string.your_order_cancelled,
                        R.string.order_cancelled_info
                    )
                    destinationLocation = null
                    mapObjectsDestinationPoint.clear()
                    mapObjectsPolyline.clear()
                    // Cancelled from the other side — retire the order-scoped prompts with it, same
                    // as the driver-side cancel does.
                    endedOrderId = order?.id
                    dismissTripPrompts()
                    getActiveMyOrders()
                }
                // The public-pool key always refreshes the available list (the cancelled order, if it
                // was a pool order, must drop out of it).
                if (response.key == ORDER_CANCELLED) getOrders()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Reuse the already-inflated map across back-navigation so the MapView (and its
        // loaded tiles + camera) isn't rebuilt every time the driver returns from Profile.
        // Rebuild only the first time — OR when the theme (uiMode) changed, so a reused
        // view never shows stale colors / insets.
        val uiMode =
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val existing = cachedRoot
        if (existing != null && _binding != null && uiMode == cachedUiMode) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        (cachedRoot?.parent as? ViewGroup)?.removeView(cachedRoot)
        cachedRoot = null
        insetBasesCaptured = false

        _binding = FragmentMapBinding.inflate(inflater, container, false)
        cachedRoot = binding.root
        cachedUiMode = uiMode

        destinationAdapter = DestinationAdapter(this@MapFragment)

        mapKit = binding.mapKit
        mapObjectsCurrentPoint = mapKit.map.mapObjects.addCollection()
        mapObjectsDestinationPoint = mapKit.map.mapObjects.addCollection()
        mapObjectsPolyline = mapKit.map.mapObjects.addCollection()
        mapKit.map.addCameraListener(this@MapFragment)

        // The reused-view rebuild made brand-new MapViews — drop the stale trip/home map
        // collections + marks bound to the destroyed old map so they're re-created against the new
        // one (otherwise the mini-map puck/pins/route silently stop drawing after a rebuild).
        miniMapObjects = null
        miniMapDriverMark = null
        miniCameraListener = null
        followHandler.removeCallbacksAndMessages(null)
        miniMapRouteObjects = null
        miniMapRouteKey = null
        miniRouteLine = null
        homeTripObjects = null
        // Reset the guard key too, else the new (empty) collection matches the stale key and the
        // home-map pins never redraw after a theme/locale rebuild.
        homeTripKey = null
        // The home puck placemark also lived on the OLD map's collection — null it so the next fix
        // re-creates it (moveCamera -> setMarkerCurrentPoint) on the new map, else the puck vanishes
        // after a theme/locale rebuild.
        marker = null

        val theme = ThemeManager.getTheme()
        mapKit.map.isNightModeEnabled = when (theme) {
            THEME_DAY -> false
            THEME_NIGHT -> true
//            THEME_SYSTEM -> ThemeManager.isSystemNightMode(requireContext())
            else -> ThemeManager.isSystemNightMode(requireContext())
        }

        setupTripMiniMap()

        // Branded loading mask hides the map's black GL build — but ONLY on a theme/locale
        // reload (flagged via AppReloadFlag), NOT on cold start. On cold start FirstActivity's
        // splash already covers the launch, so showing this here too looked like two splash
        // screens. Consume the flag once, then fade out to reveal the map.
        if (AppReloadFlag.brandedReload) {
            AppReloadFlag.brandedReload = false
            binding.flMapLoadingMask.visibility = View.VISIBLE
            binding.flMapLoadingMask.alpha = 1f
            binding.root.postDelayed({
                _binding?.flMapLoadingMask?.let { mask ->
                    mask.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction { _binding?.flMapLoadingMask?.visibility = View.GONE }
                        .start()
                }
            }, 800)
        } else {
            binding.flMapLoadingMask.visibility = View.GONE
        }

        return binding.root
    }

    /**
     * If any required permission (or the battery-optimisation exemption) is missing, navigate to
     * the access-permissions screen and return true. Called on the first view AND on every resume,
     * so a permission the driver revokes in Settings while backgrounded bounces them here on return
     * instead of letting the app run with a missing requirement. The first-launch-only overlay
     * prompt stays gated by AccessPermissionsManager so OEM builds that misreport canDrawOverlays
     * after a fresh grant don't trap the user.
     */
    private fun redirectToAccessPermissionsIfNeeded(): Boolean {
        if (_binding == null) return false
        val missingCore = !CheckPermissions.isGrantedAllPermission(requireContext()) ||
                !CheckPermissions.checkBatteryOptimisation(requireContext())
        val missingFirstLaunchOverlay = !AccessPermissionsManager.isCompleted() &&
                CheckPermissions.isOverlayPermissionAvailable(requireContext()) &&
                !CheckPermissions.checkHasDrawOverlayPermissions(requireContext())
        if (missingCore || missingFirstLaunchOverlay) {
            // Only navigate when the action resolves from the CURRENT destination (i.e. we're on the
            // map) so we never crash off-screen or double-navigate once already on the access screen.
            findNavController().currentDestination
                ?.getAction(R.id.action_mapFragment_to_accessPermissionsFragment)?.let {
                    findNavController().navigate(R.id.action_mapFragment_to_accessPermissionsFragment)
                }
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyEdgeToEdgeInsets()
        setupBackHandler()

        if (!redirectToAccessPermissionsIfNeeded()) {
            if (UserManager.getStatusValue() == DRIVER_ACTIVE) {
                getLastLocation()
                serviceListener()
                socketListener()
                mainActivityListener()
                updateLocationForRoute()
                onClick()

                // NOTE: getActiveMyOrders() is intentionally NOT called here. onResume ALWAYS
                // runs immediately after onViewCreated on a fresh view, under the identical
                // (_binding != null && DRIVER_ACTIVE) guard, and fetches /user/me there. Calling
                // it here too fired two back-to-back /user/me round-trips + two full trip
                // re-renders on every navigation return (the "whole view re-renders" flicker).
                // onResume is now the single owner of that fetch.
                if (MyTrackingService.isServiceRunning.value == true) {
                    getOrders()
                } else {
                    setTripDetailVisible(false)
                    binding.flBottom.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions on every return to the foreground: if the driver revoked a required
        // permission (or the battery-optimisation exemption) in Settings while we were backgrounded,
        // bounce them to the access screen instead of running with a missing requirement.
        if (redirectToAccessPermissionsIfNeeded()) return
        // The map is Activity-hosted (persistent) and therefore ALWAYS resumed — it is the visible
        // "front surface" only when it is the NavHost's current destination (nothing pushed on top).
        // onResume fires on app-foreground (and first mount); gate popup routing + the refresh on the
        // front state so a foreground while another screen covers the map doesn't mark it visible or
        // reload behind that screen.
        val front = (activity as? MainActivity)?.isMapFront() == true
        setMapFront(front)
        if (CheckPermissions.isGPSEnabled(requireContext())) {
            binding.cvMyCurrentLocationEnabled.visibility = View.VISIBLE
            binding.cvMyCurrentLocationDisabled.visibility = View.GONE
        } else {
            binding.cvMyCurrentLocationEnabled.visibility = View.GONE
            binding.cvMyCurrentLocationDisabled.visibility = View.VISIBLE
        }

        // Refresh the top earnings pill (today's price + count) on app-foreground while the map is
        // the front surface — e.g. after finishing an order. onViewCreated only fires for a fresh
        // view and the socket may now be closed (no active order), so without this the pill goes stale.
        if (front && _binding != null && UserManager.getStatusValue() == DRIVER_ACTIVE) {
            getActiveMyOrders()
            loadVerification()
        }
    }

    override fun onPause() {
        super.onPause()
        // The Activity-hosted map only pauses with the Activity (app backgrounded), NOT on in-app
        // navigation — so this means "app went to background": stop treating the map as the visible
        // surface so notices route to the overlay/notification.
        setMapFront(false)
    }

    // The map is Activity-hosted (mounted in flPersistentMap, NOT inside the NavHost), so it has no
    // NavController of its own — Fragment.findNavController() would throw. Shadowing the extension
    // with this member reroutes EVERY findNavController() call site in this fragment to the Activity's
    // NavHost controller with zero call-site edits. The nav-graph `mapFragment` placeholder keeps the
    // same id + actions, so currentDestination/getAction guards still resolve while the map is front.
    private fun findNavController(): androidx.navigation.NavController =
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main)

    /**
     * Guarded navigation for ASYNC / late call sites (Flow collectors, socket events, slider
     * completions). A stale emission — e.g. getActiveMyOrders' /user/me returning 401, startWork's
     * 401, or the finishOrder ack — can fire AFTER the driver navigated off the map. The map is
     * Activity-hosted, so findNavController() is the Activity controller and a mapFragment-scoped
     * action no longer resolves once currentDestination is another screen, so navigate() threw
     * IllegalArgumentException (crash C). Bail if the view is gone, only navigate when the action
     * resolves from the current node (getAction() also resolves graph-level global actions), and
     * swallow the residual race as a last resort.
     */
    private fun safeNavigate(actionId: Int, args: android.os.Bundle? = null) {
        if (_binding == null || !isAdded) return
        val nav = findNavController()
        if (nav.currentDestination?.getAction(actionId) == null) return
        try {
            nav.navigate(actionId, args)
        } catch (e: IllegalArgumentException) {
            Timber.tag("Nav").w(e, "safeNavigate skipped: action %d not resolvable", actionId)
        }
    }

    /** Whether the map is currently the visible front surface (nothing pushed on top of it). Drives
     *  popup routing, the mini-map GL, the Back handler, and the offline status-bar tint. */
    private var mapIsFront = false
    private var mapBackCallback: androidx.activity.OnBackPressedCallback? = null

    /** Single switch for "the map is the visible surface". Set explicitly by MainActivity's
     *  destination listener ([onMapBecameFront]/[onMapBecameCovered]) and by our own onResume/onPause
     *  (app foreground/background) — NOT by the fragment nav lifecycle, which no longer changes on
     *  in-app navigation now that the map is Activity-hosted. */
    private fun setMapFront(front: Boolean) {
        mapIsFront = front
        MyTrackingService.mapScreenVisible = front
        mapBackCallback?.isEnabled = front
        if (front) {
            // Re-assert the connection/offline status-bar state now that the front map owns the bars.
            updateConnectionBadge()
        } else {
            // Clear any map alert tint directly — setStatusBarAlert is now gated to the front map, so
            // a gated call here (mapIsFront just became false) would no-op.
            (activity as? MainActivity)?.applyStatusBarAlert(false)
        }
        syncMiniMapLifecycle()
    }

    /** MainActivity calls this when the NavHost returns to the (transparent) map placeholder — i.e.
     *  nothing is pushed on top of the persistent map. This is the "returned to the map" signal for
     *  in-app navigation, since the Activity-hosted map never leaves RESUMED. */
    fun onMapBecameFront() {
        if (_binding == null) return
        // setMapFront(true) already re-asserts the connection/status-bar state via updateConnectionBadge.
        setMapFront(true)
        // Refresh the active order + earnings pill and consume openTripDetailOnNextLoad (pool-accept
        // opens the detail expanded). onResume won't fire on an in-app return — the map stayed resumed.
        if (UserManager.getStatusValue() == DRIVER_ACTIVE) {
            getActiveMyOrders()
            loadVerification()
        }
    }

    /** MainActivity calls this when a screen is pushed on top of the persistent map. */
    fun onMapBecameCovered() {
        setMapFront(false)
    }

    /**
     * Poll for reprices until the ride is actually under way.
     *
     * When the rider toggles a service, the driver's only notification is the
     * `order_price_updated` socket frame — and that rides the GPS-batch cycle. Verified on live
     * orders (2026-07-24): with the driver stationary, NO batch is ever sent (`drainOnce` skips
     * when no point passed the filter and no wait timer moved), so no frame arrives and no
     * `order_price_recalculated` FCM push comes either — the socket carried nothing but `pong`.
     * Checked at ACCEPTED **and again after Boshlash at STARTED**: zero batches, zero signal.
     * The agreed price and the service chips sat frozen while the rider's screen already showed
     * the new set.
     *
     * So the dead window is everything before Go, not just before start. Polling stops at
     * CHANGED_GONE: from there the driver is driving (and the wait timer covers the stops), so
     * batches flow and the live-fare stream takes over.
     *
     * Deliberately narrow: only while the order is on the visible map. Delete this once the
     * backend pushes a reprice signal for orders that haven't reached Go — see the matching note
     * in refreshServerFareOnOrderChange.
     */
    private var preGoRepriceJob: kotlinx.coroutines.Job? = null

    private fun startPreGoRepricePolling() {
        preGoRepriceJob?.cancel()
        preGoRepriceJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(PRE_GO_REPRICE_POLL_MS)
                val o = order ?: continue
                if (!fragmentStarted || !mapIsFront || _binding == null) continue

                // NO TRIP IN PROGRESS -> no polling. This loop used to end at Go; now that it
                // runs on past Go it needs its own stop, and neither of the obvious ones works:
                // `order` is never set to null (the socket-cancel teardown relies on the retained
                // object), and the map fragment never leaves RESUMED on in-app navigation, so
                // onStop's cancel never fires either. Without this the poll hit client/me every
                // 5s forever after a completed trip — and toasted on every failed one.
                //
                // Skipping the REQUEST rather than breaking the loop: the next order arrives
                // through the socket/FCM paths, which call getActiveMyOrders() themselves and
                // set the flag back — a broken loop would never resume.
                if (!hasActiveOrder) continue

                // After Go the live-fare stream is normally the faster signal, so the
                // poll stands down — but ONLY while that stream is actually talking.
                // It rides the GPS batch, and a parked driver sends no batch, so the
                // silence is exactly the case where a rider's service toggle would
                // otherwise never arrive. Poll into the silence, stay out of the way
                // of a driver who is moving.
                if (o.state >= ORDER_STATE_CHANGED_GONE &&
                    System.currentTimeMillis() - lastServerFareAtMs < POST_GO_FARE_SILENCE_MS
                ) continue

                getActiveMyOrders()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        fragmentStarted = true
        startPreGoRepricePolling()
        startCheckpointPrompt()
        MapKitFactory.getInstance().onStart()
        if (_binding != null) {
            // Yandex needs BOTH the global factory AND each MapView ticked — the home map was
            // missing its per-view onStart, risking blank/stale tiles after backgrounding.
            binding.mapKit.onStart()
            // The mini-map only ticks while the trip detail is expanded (see syncMiniMapLifecycle).
            // A Yandex MapView keeps compositing its GL surface OVER the home map while onStart()ed
            // even when its container is GONE, so ticking it while minimised leaks the mini-map on
            // top of the home map.
            syncMiniMapLifecycle()
        }
        if (dialogTripMap?.isShowing == true) _dialogTripMapBinding?.mapTripFull?.onStart()

//        ContextCompat.registerReceiver(requireContext(), gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        requireContext().registerReceiver(
            gpsReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        )

        val intentFilter = IntentFilter(ACTION_SEND_ORDER_DATA_BY_BROADCAST)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(orderDataReceiver, intentFilter)

        val intentFilter2 = IntentFilter(ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(socketListenerReceiver, intentFilter2)

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                verificationReceiver,
                IntentFilter(ACTION_VERIFICATION_STATUS_CHANGED)
            )

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                orderRepricedReceiver,
                IntentFilter(ACTION_ORDER_PRICE_RECALCULATED)
            )
    }

    override fun onStop() {
        super.onStop()
        fragmentStarted = false
        preGoRepriceJob?.cancel()
        preGoRepriceJob = null
        stopCheckpointPrompt()
        if (_binding != null) {
            binding.mapKit.onStop()
            // Mirror onStart: stop the mini-map if it was running (fragmentStarted is now false).
            syncMiniMapLifecycle()
        }
        if (dialogTripMap?.isShowing == true) _dialogTripMapBinding?.mapTripFull?.onStop()
        requireContext().unregisterReceiver(gpsReceiver)

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(orderDataReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(orderRepricedReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(socketListenerReceiver)
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(verificationReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // viewLifecycleOwner is destroyed here (its observers are auto-removed), so the next
        // view must re-register the tracking observers — re-arm the one-shot guard.
        trackingObserversRegistered = false
        // Leaving the map — clear the red "not on the line" status-bar tint so it
        // doesn't linger on other screens.
        setStatusBarAlert(false)
        unregisterNetworkCallback()
        // NOTE: _binding is intentionally NOT nulled here — the inflated map view is
        // cached and reused on re-entry (released in onDestroy) so it never reloads.
        // These two were the last stragglers of the dismiss-on-destroy sweep below: only their
        // BINDING was released, never the dialog itself, so one still on screen when the view
        // died leaked its window. Seen in prod as
        //   WindowLeaked: MainActivity has leaked window ... at Dialog.show(MapFragment.kt)
        // after a theme/locale recreate() while the finish-work dialog was open.
        dialogDestination?.dismiss()
        _dialogDestinationBinding = null
        dialogDestination = null

        dialogFinishWork?.dismiss()
        _dialogFinishWorkBinding = null
        dialogFinishWork = null

        // The view can go away with the bill still up (the trip is NOT finished then). Hand the
        // frozen meter back before tearing the dialog down, or the rest of the ride waits free.
        resumeWaitAfterFinishAborted()
        dialogTrackingFinish?.dismiss()
        _dialogTrackingFinishBinding = null
        dialogTrackingFinish = null

        // infoPopup was the one member Dialog left out of the dismiss-on-destroy sweep — it leaked
        // its window on every Activity recreate (theme / locale change) while a notice was up.
        // NOTE: this is a hand-rolled duplicate of common/InfoPopup.kt, which already auto-dismisses
        // via a Lifecycle observer. Worth collapsing into that helper (it needs an autoDismissMs
        // parameter first); until then the two must be kept in sync.
        infoPopup?.dismiss()
        infoPopup = null

        dialogArrivedToClient?.dismiss()
        _dialogArrivedToClientBinding = null
        dialogArrivedToClient = null

        dialogPassingNextDestination?.dismiss()
        _dialogPassingNextDestinationBinding = null
        dialogPassingNextDestination = null

        dialogFinishDestination?.dismiss()
        _dialogFinishDestinationBinding = null
        dialogFinishDestination = null

        dialogRetryRequest?.dismiss()
        _dialogRetryRequestBinding = null
        dialogRetryRequest = null

        dialogPaymentError?.dismiss()
        _dialogPaymentErrorBinding = null
        dialogPaymentError = null

        // Cancel-reason dialog was the one dialog left un-dismissed here — a still-open sheet's
        // Submit tap would otherwise run orderCansel() against a destroyed view.
        dialogOrderCancel?.dismiss()
        _dialogOrderCancelBinding = null
        dialogOrderCancel = null

        dialogContactSupport?.dismiss()
        _dialogContactSupportBinding = null
        dialogContactSupport = null

        // Expanded route map (its onDismiss stops the MapView + nulls the binding).
        dialogTripMap?.dismiss()
        _dialogTripMapBinding = null
        dialogTripMap = null

        // marker is intentionally NOT nulled — its placemark lives in the cached map's
        // collection, so keeping the reference prevents a duplicate puck on re-entry.
        // The puck animator MUST be cancelled, or its lerp keeps mutating the placemark after the
        // view is gone.
        puckAnimator?.cancel()
        puckAnimator = null

        fusedLocationProviderClient.removeLocationUpdates(locationCallBackForRoute)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the cached map view only when the fragment is truly destroyed.
        cachedRoot = null
        _binding = null
    }

    /**
     * Edge-to-edge: the map draws behind the transparent status bar, so nudge the
     * floating top controls (profile, speed, today-earnings) down by the status-bar
     * height. Base margins are captured once, so re-applying stays idempotent.
     */
    private fun applyEdgeToEdgeInsets() {
        if (_binding == null) return
        // Capture base margins ONCE. Since the map view is cached across back-navigation,
        // re-reading the already-inset-shifted margins on return would accumulate the
        // inset each time — capture the originals and reuse them.
        if (!insetBasesCaptured) {
            settingsTopBase =
                (binding.cvSettings.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            speedTopBase = (binding.cvSpeed.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            todayTopBase =
                (binding.cvTodayEarnings.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            noConnTopBase =
                (binding.llConnectionBadge.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            chipTopBase =
                (binding.cvVerificationChip.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            bottomBaseMargin =
                (binding.llBottomArea.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            insetBasesCaptured = true
        }
        val settingsTop = settingsTopBase
        val speedTop = speedTopBase
        val todayTop = todayTopBase
        val noConnTop = noConnTopBase
        val chipTop = chipTopBase
        val bottomBase = bottomBaseMargin

        // The ignoring-visibility status inset keeps the top safe-area spacing even though
        // the bar is hidden (immersive) and the live statusBars() inset is 0.
        fun applyInsets(top: Int, bottom: Int) {
            binding.cvSettings.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = settingsTop + top
            }
            binding.cvSpeed.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = speedTop + top
            }
            binding.cvTodayEarnings.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = todayTop + top
            }
            // The "not on the line" badge sits just below the earnings pill; it must
            // track the same status-bar inset, or the inset-shifted pill collides with it.
            binding.llConnectionBadge.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = noConnTop + top
            }
            // The verification chip occupies the same slot as the connection badge (they never
            // show together), so it needs the identical inset — without it the chip rode up over
            // the earnings pill instead of sitting just below it like the badge does.
            binding.cvVerificationChip.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = chipTop + top
            }
            binding.llBottomArea.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomBase + bottom
            }
            // The trip detail is now a bottom sheet (it sits BELOW the status bar), so its toolbar
            // no longer takes the status-bar top inset — that left a big empty strip above the stage
            // title. The root still takes the bottom gesture inset so the pinned bar clears the nav bar.
            binding.includeDialog.llBottomSheet.setPadding(0, 0, 0, bottom)
        }
        // Constant status-bar-height fallback: the bar is hidden (immersive) so the live
        // inset can be 0 or not-yet-computed right after a theme-change recreate, but the
        // height itself never changes — coerce to it so the top never collapses to the edge.
        val statusFallback = run {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else 0
        }

        fun applyFromWindow() {
            if (_binding == null) return
            val r = ViewCompat.getRootWindowInsets(binding.root)
            val top =
                (r?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top ?: 0)
                    .coerceAtLeast(statusFallback)
            val bottom =
                r?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())?.bottom
                    ?: 0
            applyInsets(top, bottom)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val top = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
                .coerceAtLeast(statusFallback)
            applyInsets(
                top,
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        // Apply now, on attach, AND next frame — the deferred requestApplyInsets pass
        // alone doesn't reliably reach a fragment view rebuilt by a theme-change recreate,
        // and getRootWindowInsets can still be empty the instant the view attaches.
        applyFromWindow()
        binding.root.doOnAttach { applyFromWindow() }
        binding.root.post { applyFromWindow() }
        ViewCompat.requestApplyInsets(binding.root)
    }

    // Functions
    private var isWaiting = false

    /** True while the driver has minimised the full-screen trip page to the home map. */
    private var isTripMinimized = false

    /** Last order state the trip sheet was opened for. Drives "rise to at least HALF when the stage
     *  changes, but leave the driver's detent alone on a plain refresh" so the sheet doesn't jump. */
    private var lastShownTripState: Int? = null

    /** Expanded full-screen route map (iOS fullScreenCover), opened from the mini-map. Held
     *  separately from the trip detail so opening it never dismisses the detail underneath. */
    private var dialogTripMap: Dialog? = null
    private var _dialogTripMapBinding: DialogTripMapFullBinding? = null

    // Live driver puck on the full-screen trip map — moved on every GPS fix so the arrow
    // tracks the driver instead of freezing at the position the map was opened at.
    private var expandedMapDriverMark: PlacemarkMapObject? = null
    private var expandedDriverColl: MapObjectCollection? = null

    // Navigation-style camera follow on the full-screen trip map. On by default; a manual
    // pan/zoom (GESTURES) suspends it so the camera doesn't fight the user; the locate button
    // resumes it and the fit-route button stops it.
    private var expandedFollowDriver = true

    // The trip-map camera listener — kept so it can be removed on dismiss (the dialog map is
    // recreated each open; balance addCameraListener with removeCameraListener).
    private var expandedCameraListener: CameraListener? = null

    /** Cached inflated map view, reused across back-navigation so the map never reloads. */
    private var cachedRoot: View? = null
    private var cachedUiMode = 0
    private var insetBasesCaptured = false
    private var settingsTopBase = 0
    private var speedTopBase = 0
    private var todayTopBase = 0
    private var noConnTopBase = 0
    private var chipTopBase = 0
    private var bottomBaseMargin = 0
    private fun serviceListener() {
        MyTrackingService.isServiceRunning.observe(viewLifecycleOwner, Observer { isRunning ->
            if (isRunning == true) {
                binding.llConnecting.visibility = View.GONE
                binding.sbStartJob.visibility = View.GONE
                binding.llFinishJobAndCall.visibility = View.VISIBLE
            } else {
                binding.llFinishJobAndCall.visibility = View.GONE
                binding.sbStartJob.visibility = View.VISIBLE
                resetStartJobSlider()
            }
            updateConnectionBadge()
        })

        MyTrackingService.isWaiting.observe(viewLifecycleOwner) {
            isWaiting = it
            updateWaitTime()
        }

        // Wait meter auto-stopped because the car drove off (> 20 km/h) — pop a non-blocking
        // notice (the service also fires a heads-up notification for the backgrounded case).
        MyTrackingService.waitAutoStoppedBySpeed.observe(viewLifecycleOwner) {
            if (it == true) {
                showInfoPopup(
                    R.drawable.ic_error_circle,
                    R.string.wait_auto_stopped_title,
                    R.string.wait_auto_stopped_msg,
                    autoDismissMs = 5000
                )
                MyTrackingService.waitAutoStoppedBySpeed.value = false
            }
        }

        // Ride auto-started in the background (client aboard, the car drove off). Show the
        // acknowledge popup (Ok button — no auto-dismiss) + refresh the order to its new state.
        MyTrackingService.rideAutoStartedBySpeed.observe(viewLifecycleOwner) {
            if (it == true) {
                showInfoPopup(
                    R.drawable.baseline_directions_car_24,
                    R.string.ride_started_auto,
                    R.string.ride_started_auto_hint
                )
                getActiveMyOrders()
                MyTrackingService.rideAutoStartedBySpeed.value = false
            }
        }
    }

    private fun socketListener() {
        MySocketListener.isSocketListener.observe(viewLifecycleOwner, Observer {
            updateConnectionBadge()
        })
        // Also refresh the badge on raw connectivity changes (no-internet ⇄ reconnecting).
        registerNetworkCallback()
    }

    /**
     * Single source of truth for the top status pill + offline wash, derived from
     * BOTH the shift state and the socket state so the two never contradict.
     *
     * The bottom shift controls (Exit / Dispatcher) say "you're on shift"; a red
     * "No connection" badge on top read as a contradiction. Since MyTrackingService
     * auto-reconnects the socket, a drop while working is transient — so on shift we
     * surface a calm amber "Reconnecting…" instead of an alarm:
     *
     *  - on shift + online   → no pill, no wash, normal status bar (clean)
     *  - on shift + offline  → red "reconnecting…" + red wash + red status bar
     *  - off shift           → red "not on the line" + red wash + red status bar (iOS)
     */
    private fun updateConnectionBadge() {
        if (_binding == null) return
        val onShift = MyTrackingService.isServiceRunning.value == true
        val online = MySocketListener.isSocketListener.value == true

        when {
            !onShift -> {
                // Not on the line (work not started) — static red alert, like iOS.
                applyConnectionBadge(R.string.you_do_not_at_work, connecting = false)
                setStatusBarAlert(true)
            }

            !hasNetworkConnection() -> {
                // On shift but NO network at all (airplane / no data / no Wi-Fi). Show the red wash +
                // "no internet" badge even when the socket still reports connected — its state lags a
                // real network drop by several seconds, which previously left the screen looking
                // "clean" (only a toast) with no red gradient or chip.
                applyConnectionBadge(R.string.no_internet_retry, connecting = false)
                setStatusBarAlert(true)
            }

            online -> {
                // On the line — real network + live socket — clean map, normal status bar.
                binding.llConnectionBadge.visibility = View.GONE
                binding.vOfflineWash.visibility = View.GONE
                setStatusBarAlert(false)
            }

            else -> {
                // On shift, network present, but the socket dropped — calm "reconnecting…" spinner.
                applyConnectionBadge(R.string.reconnecting, connecting = true)
                setStatusBarAlert(true)
            }
        }

        // The optional chip shares this badge's top slot — re-evaluate after the badge changes.
        updateChip()
    }

    private fun setStatusBarAlert(alert: Boolean) {
        // Only the FRONT (visible) map may drive the system bars. The connection badge is driven by
        // session-long socket/service observers that keep firing while another screen covers the map;
        // without this gate a socket flap would flip the covering screen's status-bar icons.
        if (!mapIsFront) return
        (activity as? MainActivity)?.applyStatusBarAlert(alert)
    }

    /** Red "not on the line" badge + red gradient wash from the top (iOS parity). */
    private fun applyConnectionBadge(textRes: Int, connecting: Boolean) {
        val red = ContextCompat.getColor(requireContext(), R.color.red)
        binding.llConnectionBadge.visibility = View.VISIBLE
        binding.tvNoConnection.text = getString(textRes)
        binding.tvNoConnection.setTextColor(red)
        if (connecting) {
            binding.ivBadgeIcon.visibility = View.GONE
            binding.pbBadgeSpinner.visibility = View.VISIBLE
            binding.pbBadgeSpinner.indeterminateTintList = ColorStateList.valueOf(red)
        } else {
            binding.pbBadgeSpinner.visibility = View.GONE
            binding.ivBadgeIcon.visibility = View.VISIBLE
            binding.ivBadgeIcon.setColorFilter(red)
        }
        binding.vOfflineWash.setBackgroundResource(R.drawable.bg_offline_wash)
        binding.vOfflineWash.visibility = View.VISIBLE
    }

    // --- Network connectivity: tells "no internet at all" apart from a slow/reconnecting socket ---
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** True when an active network advertises internet — even if it's slow. Only false when there's
     *  no usable network at all (airplane mode / no mobile data / no Wi-Fi). */
    private fun hasNetworkConnection(): Boolean {
        val cm = connectivityManager
            ?: (requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Re-evaluates the badge whenever connectivity changes, so "no internet" ⇄ "reconnecting…"
     *  flips live — the socket state alone can lag a few seconds behind a real network drop. */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm =
            (requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?: return
        connectivityManager = cm
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postBadgeRefresh()
            override fun onLost(network: Network) = postBadgeRefresh()
        }
        networkCallback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        try {
            connectivityManager?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    /** Callbacks arrive off the main thread; bounce to the view and only touch a live binding. */
    private fun postBadgeRefresh() {
        view?.post { if (_binding != null) updateConnectionBadge() }
    }

    private fun mainActivityListener() {
        MainActivity.isActiveShowDialogInsideAppForMap.observe(viewLifecycleOwner, Observer {
            if (it == true && MyTrackingService.isServiceRunning.value == true) {
                MainActivity.isActiveShowDialogInsideAppForMap.value = null
                getActiveMyOrders()
//                showToast("Salom4")
            }
        })
    }

    private fun getOrders() {
        // Offline driver can't take broadcast orders — keep the badge empty (ORDER_GPS_FIXES.md §5).
        if (UserManager.getStatusValue() != DRIVER_ACTIVE) {
            binding.cvOrderCount.visibility = View.GONE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getAllOrders().collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        val orders = it.data?.data ?: emptyList()

                        if (orders.isNotEmpty()) {
                            binding.cvOrderCount.visibility = View.VISIBLE
                            binding.tvOrderCount.text = orders.size.toString()
                        } else {
                            binding.cvOrderCount.visibility = View.GONE
                        }
                    }

                    is Resource.Error -> {
                        binding.cvOrderCount.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun onClick() {
        binding.apply {
            sbStartJob.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    // A mandatory check is failing → don't start the shift; reset the knob
                    // and open the verification screen so the driver can fix it.
                    if (verification?.hasBlocking == true) {
                        sbStartJob.setCompleted(completed = false, withAnimation = true)
                        safeNavigate(R.id.action_global_to_verificationFragment)
                        return
                    }
                    // Swap the slider out for the "Ulanmoqda…" section: the swipe knob is hidden so
                    // the driver can only wait — a second swipe can't re-fire the start flow. The
                    // section is swapped for the finish buttons once MyTrackingService starts
                    // (serviceListener), or back to the slider on failure (resetStartJobSlider).
                    sbStartJob.setCompleted(completed = false, withAnimation = false)
                    showConnecting()
                    if (_binding != null) {
                        checkAndStartWork()
                    }
                }
            }

            cvFinishJob.setDebouncedClickListener {
                // Re-entry guard, same as every other member dialog in this file. The
                // assignment below OVERWRITES `dialogFinishWork`, so showing a second one
                // orphans the first window with no reference left to dismiss it — the
                // debounce alone only narrows that race, it does not close it.
                if (dialogFinishWork?.isShowing == true) return@setDebouncedClickListener
                dialogFinishWork = Dialog(requireContext())
                _dialogFinishWorkBinding = DialogFinishWorkBinding.inflate(layoutInflater)
                dialogFinishWork!!.apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setContentView(dialogFinishWorkBinding.root)
                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    // Dismiss only via the dialog's own buttons — block back + outside tap.
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    // A custom-view dialog defaults to wrap_content width, which squeezed
                    // the card into a sliver and clipped the title/message/buttons. Pin it
                    // to 88% of the screen width so everything reads on one line.
                    window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }


                dialogFinishWorkBinding.cvExit.setDebouncedClickListener {
                    finishWork()
                    dialogFinishWork?.dismiss()
                }

                dialogFinishWorkBinding.cvCancel.setOnClickListener {
                    dialogFinishWork?.dismiss()
                }

                dialogFinishWork?.show()
            }

            cvMyCurrentLocationDisabled.setDebouncedClickListener {
                openGpsSettings()
            }

            cvVerificationChip.setDebouncedClickListener {
                safeNavigate(R.id.action_global_to_verificationFragment)
            }

            cvVerificationCard.setDebouncedClickListener {
                safeNavigate(R.id.action_global_to_verificationFragment)
            }

            cvOrders.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapFragment_to_ordersMapFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapFragment_to_ordersMapFragment)
                    }
            }

            cvSettings.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapFragment_to_mapSettingsFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapFragment_to_mapSettingsFragment)
                    }
            }
            // Paint the profile button from cache straight away; getActiveMyOrders() re-binds it
            // from the live user/me a moment later. Same URL both times, so Glide serves the
            // second from memory and the button never flickers.
            DriverAvatar.bind(UserManager.getUser()?.photo, ivProfilePhoto, ivProfilePlaceholder)

            cvNotifications.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapFragment_to_notificationsMapFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapFragment_to_notificationsMapFragment)
                    }
            }

            cvDispatcher.setDebouncedClickListener {
                // Branch dispatcher first; brand support line only as a fallback.
                val number = Helper.dispatcherOrSupportNumber()
                if (number != null) {
                    call(number)
                } else {
                    showToast(getString(R.string.not_assigned_dispatcher))
                }
            }

            cvMyCurrentLocationEnabled.setOnClickListener {
                // Geo button = the 3 s auto-resume on demand: re-enable the per-fix follow and snap to
                // the live puck facing along the route. Cancel any pending resume so they don't fight.
                followHandler.removeCallbacks(resumeHomeFollow)
                homeFollowDriver = true
                if (currentLocation != null) {
                    // Zoom IN to a comfortable street level (and keep the follow there), animated.
                    cameraZoom = maxOf(cameraZoom, 16.5f)
                    mapKit.map.move(
                        CameraPosition(
                            Point(
                                currentLocation!!.latitude,
                                currentLocation!!.longitude
                            ), cameraZoom, routeCourse() ?: currentLocation!!.bearing, 0.0f
                        ), Animation(Animation.Type.SMOOTH, 1f), null
                    )
                } else {
                    getLastLocation()
                }
            }

            cvMinus.setOnClickListener {
                if (cameraZoom > 2 && selectedLocation != null && currentLocation != null) {
                    cameraZoom -= 2

                    mapKit.map.move(
                        CameraPosition(
                            Point(
                                selectedLocation!!.latitude,
                                selectedLocation!!.longitude
                            ), cameraZoom, currentLocation!!.bearing, 0.0f
                        ), Animation(Animation.Type.SMOOTH, 1f), null
                    )
                }
            }

            cvPlus.setOnClickListener {
                if (cameraZoom < 20 && selectedLocation != null && currentLocation != null) {
                    cameraZoom += 2

                    mapKit.map.move(
                        CameraPosition(
                            Point(
                                selectedLocation!!.latitude,
                                selectedLocation!!.longitude
                            ), cameraZoom, currentLocation!!.bearing, 0.0f
                        ), Animation(Animation.Type.SMOOTH, 1f), null
                    )
                }
            }

            // Bottom Sheet Dialog onClicks and onSlides
            includeDialog.apply {
                llOrderCancel.setDebouncedClickListener {
                    // Who may cancel, and until when, depends on WHO created the order — see
                    // [canDriverCancel]. When the driver may not, "Bekor qilish" opens the
                    // contact-support popup instead of the reasons sheet.
                    if (canDriverCancel(order)) {
                        getOrderCanselReasons()
                    } else {
                        showContactSupportDialog()
                    }
                }

                llCallClient.setDebouncedClickListener {
                    if (order?.contact?.phone != null) {
                        call(order!!.contact!!.phone)
                    } else {
                        showToast(getString(R.string.no_phone_number))
                    }
                }

                llCallDispatcher.setDebouncedClickListener {
                    call(order!!.branch.dispatcherNumber)
                }

                llGoToMap.setDebouncedClickListener { openNavigationOrPicker() }

                llWait.setOnClickListener {
                    if (Constants.WAITING_TIME_TURN_AUTO) {
                        showToast(getString(R.string.this_feature_work_auto))
                    } else {
                        toggleWaitTimeService()
                    }
                }

                cvWaitToggle.setOnClickListener {
                    if (Constants.WAITING_TIME_TURN_AUTO) {
                        showToast(getString(R.string.this_feature_work_auto))
                    } else {
                        toggleWaitTimeService()
                    }
                }

                sbStart.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        sbStart.setCompleted(completed = false, withAnimation = true)

                        if (_binding != null) {
                            orderStart(order!!.id)
                        }
                    }
                }

                sbArrive.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        sbArrive.setCompleted(completed = false, withAnimation = true)

                        if (_binding != null) {
                            orderArrive(order!!.id)
                        }
                    }
                }

                sbGo.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        sbGo.setCompleted(completed = false, withAnimation = true)

                        if (_binding != null) {
                            orderGo(order!!.id)
                        }
                    }
                }

                sbFinish.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        sbFinish.setCompleted(completed = false, withAnimation = true)

                        if (_binding != null && dialogTrackingFinish?.isShowing != true) {
                            prepareOrderFinish()
                        }
                    }
                }

                // Minimise chevron removed — the driver drags the sheet. A TAP on the top section
                // (toolbar) also cycles it open: peek → half → expanded → peek.
                llTripToolbar.setOnClickListener { toggleTripSheet() }
                // Tap the mini-map → open the expanded full-screen route map. The trip
                // detail stays mounted underneath (iOS fullScreenCover), so closing the
                // expanded map returns straight to it — it is NOT minimised/dismissed.
                vMapTripClick.setDebouncedClickListener { openExpandedTripMap() }
                // iOS navigationMenu icon → launch the chosen external nav app.
                cvTripNavigate.setDebouncedClickListener { openNavigationOrPicker() }
                // iOS servicesCancelRow → Услуги opens the client's active add-ons in a sheet.
                llServicesBtn.setDebouncedClickListener { showServicesSheet() }
                // The whole activated-services card is the tap target (not just the header row),
                // so the chevron's affordance covers everything the driver would aim at.
                llServices.setDebouncedClickListener { showServicesSheet() }
            }

            // Resume card removed — the trip sheet is always shown (at least at the peek detent),
            // so there's no minimised home state to resume from.
            cvResumeTrip.visibility = View.GONE
        }
    }

    // The trip mini-map (Yandex MapView) keeps compositing its GL surface over the home map while
    // onStart()ed even when its container (llBottomSheet) is GONE. So it must run ONLY while the
    // trip detail is expanded AND the fragment is started — tracked here to keep the MapView's
    // onStart()/onStop() balanced.
    private var fragmentStarted = false
    private var miniMapStarted = false

    /** Single funnel for the trip-detail visibility so the mini-map's render loop always tracks it. */
    private fun setTripDetailVisible(visible: Boolean) {
        if (_binding == null) return
        binding.includeDialog.llBottomSheet.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) applySheetExpandedOffset()
        syncMiniMapLifecycle()
    }

    /** Drive the trip info sheet to a detent (peek/half/expanded) — the sheet is never hidden while
     *  an order is active, so "minimise" is just STATE_COLLAPSED, not a visibility toggle. */
    private fun setTripSheetState(state: Int) {
        if (_binding == null) return
        BottomSheetBehavior.from(binding.includeDialog.tripInfoSheet).state = state
    }

    /** Tap the top section to cycle the sheet open: peek → half → expanded → peek. */
    private fun toggleTripSheet() {
        if (_binding == null) return
        val b = BottomSheetBehavior.from(binding.includeDialog.tripInfoSheet)
        b.state = when (b.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_HALF_EXPANDED
            BottomSheetBehavior.STATE_HALF_EXPANDED -> BottomSheetBehavior.STATE_EXPANDED
            else -> BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /** Cap the EXPANDED sheet so its top reaches up to the TOP side of the floating profile button
     *  (top-right) — the fully-open sheet rises to that line, no higher. The profile is inset by the
     *  status bar, so reading its laid-out top tracks every device. */
    private fun applySheetExpandedOffset() {
        if (_binding == null) return
        val profile = binding.cvSettings
        val apply = {
            if (_binding != null && profile.top > 0) {
                BottomSheetBehavior.from(binding.includeDialog.tripInfoSheet).expandedOffset =
                    profile.top
            }
        }
        if (profile.top > 0) apply() else profile.post { apply() }
    }

    /** Start/stop the trip mini-map with its on-screen visibility: it renders only while the trip
     *  detail is expanded and the fragment is started, otherwise its GL surface draws over the home
     *  map. Balanced via [miniMapStarted] so the MapView's onStart()/onStop() are never doubled. */
    private fun syncMiniMapLifecycle() {
        if (_binding == null) return
        // Mini-map removed from the trip detail (cvMiniMap gone) — never start its GL surface,
        // which would otherwise composite over the home map.
        if (binding.includeDialog.cvMiniMap.visibility != View.VISIBLE) return
        // Also require the map to be the front surface: the Activity-hosted map stays STARTED while a
        // screen covers it, so without this the mini-map GL would keep compositing behind that screen.
        val shouldRun = fragmentStarted && mapIsFront &&
                binding.includeDialog.llBottomSheet.visibility == View.VISIBLE
        if (shouldRun && !miniMapStarted) {
            binding.includeDialog.mapTripMini.onStart()
            miniMapStarted = true
            // The sheet defaults to GONE, so the first onStart() can land on a not-yet-measured
            // (0-size) MapView and come up with a blank GL surface. Nudge a layout pass so the
            // surface picks up the real 240dp bounds on the next frame.
            binding.includeDialog.mapTripMini.requestLayout()
        } else if (!shouldRun && miniMapStarted) {
            binding.includeDialog.mapTripMini.onStop()
            miniMapStarted = false
        }
    }

    /** Re-open the full-screen trip page (from the home resume card / on accept). */
    private fun showTripFull() {
        isTripMinimized = false
        if (_binding == null) return
        setTripDetailVisible(true)
        binding.flBottom.visibility = View.GONE
        setTripSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED)
    }

    /** Drop the full-screen trip page back to the home map + resume mini-card. */
    private fun minimizeTrip() {
        isTripMinimized = true
        if (_binding == null) return
        // The sheet is NEVER hidden — "minimise" just drops it to the peek detent so the map fills
        // the rest of the screen. No resume card (the peek is always shown). Exit stays hidden mid-trip.
        setTripDetailVisible(true)
        binding.flBottom.visibility = View.GONE
        setTripSheetState(BottomSheetBehavior.STATE_COLLAPSED)
        binding.cvFinishJob.visibility = View.GONE
    }

    private var lastBackPressTime = 0L

    /** Back never closes the app from an inner state: a full-screen order/trip detail drops back to
     *  the home map (instead of the app fully closing + cold-restarting from splash), and on the
     *  home map a second press within 2s is required to exit. Inner nav screens (Orders / Settings /
     *  Notifications) are handled by the default nav back, which pops them to this home map. */
    private fun setupBackHandler() {
        // Keep the reference so setMapFront can enable it only while the map is the front surface —
        // otherwise this always-registered callback would swallow Back on pushed screens (which must
        // pop via the NavHost). Enabled here by default; MainActivity gates it right after.
        mapBackCallback =
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                val tripVisible = _binding != null &&
                        binding.includeDialog.llBottomSheet.visibility == View.VISIBLE
                if (tripVisible &&
                    BottomSheetBehavior.from(binding.includeDialog.tripInfoSheet).state !=
                    BottomSheetBehavior.STATE_COLLAPSED
                ) {
                    // Sheet is expanded → drop it to the peek detent, don't close the app.
                    minimizeTrip()
                    lastBackPressTime = 0L
                } else {
                    // Home map (with or without an active order) → require a SECOND Back within 2s
                    // to close, so a stray tap never drops the driver out of the app. The tracking
                    // foreground service keeps any active trip alive across the close; relaunching
                    // restores it.
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000L) {
                        requireActivity().finish()
                    } else {
                        lastBackPressTime = now
                        showToast(getString(R.string.press_again_to_exit))
                    }
                }
            }
    }

    /** Fetch verification status to drive the map chip (entry to the full screen). */
    private var verifyLoadJob: kotlinx.coroutines.Job? = null

    private fun loadVerification() {
        verifyLoadJob?.cancel()
        verifyLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getVerificationStatus().collect {
                if (it is Resource.Success) {
                    verification = it.data?.data
                    updateChip()
                }
            }
        }
    }

    private fun updateChip() {
        if (_binding == null) return
        val v = verification
        val online = MyTrackingService.isServiceRunning.value == true
        val actionable = v?.hasActionableBlocking == true
        val suggestion = v?.hasSuggestion == true
        val pending = v?.hasPending == true

        // Off-shift verification card above the slide-to-start: a RED "fix it" prompt for a mandatory
        // blocker, OR an AMBER "!" prompt for optional / under-review docs so the driver still sees
        // them (not only required ones).
        val hasVerif = actionable || suggestion || pending
        binding.cvVerificationCard.isVisible = !online && hasVerif
        if (!online && hasVerif) {
            val colorRes = if (actionable) R.color.red else R.color.app_color
            val color = ContextCompat.getColor(requireContext(), colorRes)
            val soft = (color and 0x00FFFFFF) or (0x26 shl 24)
            binding.cvVerificationCard.strokeColor = color
            binding.flVerifDot.backgroundTintList = android.content.res.ColorStateList.valueOf(soft)
            binding.ivVerifIcon.setImageResource(
                if (actionable || suggestion) R.drawable.ph_warning else R.drawable.ph_clock
            )
            binding.ivVerifIcon.setColorFilter(color)
            binding.ivVerifCaret.setColorFilter(color)
            binding.tvVerifStatus.setTextColor(color)
            binding.tvVerifStatus.setText(
                when {
                    actionable -> R.string.verification_status_required
                    suggestion -> R.string.verification_status_optional
                    else -> R.string.verification_status_pending
                }
            )
            binding.tvVerifTitle.setText(
                when {
                    actionable -> R.string.verification_chip_required
                    suggestion -> R.string.verification_chip_suggestion
                    else -> R.string.verification_chip_pending
                }
            )
        }

        // Chip in the top slot once the connection badge has cleared (online + connected): any state
        // worth surfacing — INCLUDING a mandatory block that lands mid-shift (the card is off-shift only).
        val showChip = !binding.llConnectionBadge.isVisible && (actionable || pending || suggestion)
        binding.cvVerificationChip.isVisible = showChip
        if (showChip) {
            val (iconRes, colorRes, textRes) = when {
                actionable -> Triple(
                    R.drawable.ph_warning, R.color.red, R.string.verification_chip_required
                )

                pending -> Triple(
                    R.drawable.ph_clock, R.color.app_color, R.string.verification_chip_pending
                )

                else -> Triple(
                    R.drawable.ph_info, R.color.app_color, R.string.verification_chip_suggestion
                )
            }
            val color = ContextCompat.getColor(requireContext(), colorRes)
            binding.ivChipIcon.setImageResource(iconRes)
            binding.ivChipIcon.setColorFilter(color)
            binding.tvChip.setText(textRes)
            binding.tvChip.setTextColor(color)
        }
    }

    private var goingOnline = false

    /** Gate going online on the mandatory checks before sending driver/start. Single-flight so a
     *  rapid double-tap can't fire two startWork()s. */
    private fun checkAndStartWork() {
        // The slide-to-start completion fires from an animation-END callback, which can be
        // delivered after the view is torn down — touching viewLifecycleOwner then throws
        // "getView() is null" (prod crash). Bail before anything else.
        if (!isAdded || view == null) return
        if (goingOnline) return
        goingOnline = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                mapViewModel.getVerificationStatus().collect {
                    when (it) {
                        is Resource.Loading -> {}
                        is Resource.Success -> {
                            verification = it.data?.data
                            updateChip()
                            // Client-authoritative gate (VerificationStatus.canDriverGoOnline): blocks
                            // on any mandatory failing check; can_go_online is advisory (enforcement OFF).
                            val status = verification
                            if (status != null && status.canDriverGoOnline) {
                                startWork()
                            } else {
                                resetStartJobSlider()
                                safeNavigate(R.id.action_global_to_verificationFragment)
                            }
                        }
                        // Pre-check failed (e.g. network) — don't block; the server still gates.
                        is Resource.Error -> startWork()
                    }
                }
            } finally {
                goingOnline = false
            }
        }
    }

    private fun startWork() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.startWork().collect {
                when (it) {
                    is Resource.Loading -> {
                        // Connecting section (spinner + "Ulanmoqda…") shows in place of the slider;
                        // keep the map + chrome visible — no full-screen spinner.
                        showConnecting()
                    }

                    is Resource.Success -> {
                        commandStartService()

                        // serviceListener swaps the slider for the finish buttons once
                        // MyTrackingService reports running, and socketListener clears the
                        // offline banner once the socket connects — no manual UI restore needed.
                    }

                    is Resource.Error -> {
                        if (it.message == "401") {
                            commandStopService()
                            UserManager.deleteUser()
                            safeNavigate(R.id.action_mapFragment_to_loginFragment)
                        } else {
                            resetStartJobSlider()
                            showToast(it.message!!)
                        }
                    }
                }
            }
        }
    }

    /** Connecting state: hide the slider (and its swipe knob) and show the "Ulanmoqda…" section, so
     *  the driver can only wait — there's no knob to swipe again. */
    private fun showConnecting() {
        if (_binding == null) return
        binding.sbStartJob.visibility = View.GONE
        binding.llConnecting.visibility = View.VISIBLE
    }

    // Returns the slide-to-start control to its idle state and hides the connecting section.
    private fun resetStartJobSlider() {
        if (_binding == null) return
        binding.llConnecting.visibility = View.GONE
        binding.sbStartJob.visibility = View.VISIBLE
        binding.sbStartJob.setCompleted(completed = false, withAnimation = true)
        binding.sbStartJob.text = getString(R.string.start_job)
        binding.sbStartJob.isLocked = false
    }

    private fun finishWork() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.finishWork().collect {
                when (it) {
                    is Resource.Loading -> {
                        // Keep the map + chrome on screen: hiding `content` here blanked the
                        // whole view for the duration of the request — a visible BLINK right
                        // before the red offline state appeared. A centred spinner over the
                        // intact map is feedback enough.
                        binding.progressBar.visibility = View.VISIBLE
                    }

                    is Resource.Success -> {
                        commandStopService()
                        // Going offline — clear the broadcast order badge immediately (ORDER_GPS_FIXES.md §5).
                        binding.cvOrderCount.visibility = View.GONE
                        binding.apply {
                            progressBar.visibility = View.GONE
                            content.visibility = View.VISIBLE
                        }
                    }

                    is Resource.Error -> {
                        binding.apply {
                            progressBar.visibility = View.GONE
                            content.visibility = View.VISIBLE
                        }
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    private fun getActiveMyOrders() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getUserMe().collect {
                when (it) {
                    is Resource.Loading -> {
                        // Silent background refresh. This runs on every socket
                        // (re)connect, so keep the map + chrome on screen instead of
                        // blanking to a full-screen spinner. Data populates on Success.
                    }

                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.content.visibility = View.VISIBLE

                        val data = it.data?.data

                        val todayPrice = data?.today?.price ?: 0
                        val todayCount = data?.today?.count ?: 0

                        binding.tvTodayPrice.text = Helper.formatPrice(todayPrice.toString())
                        binding.tvTodayCount.text = Helper.formatPrice(todayCount.toString())

                        DriverAvatar.bind(
                            data?.photo,
                            binding.ivProfilePhoto,
                            binding.ivProfilePlaceholder
                        )

                        // Cold launch vs warm resume: a cold start opens on the home map (minimised);
                        // a warm resume (home → back) keeps the process and preserves the view.
                        val wasColdStart = mapFreshProcessLaunch

                        val orders = data?.orders ?: emptyList()
                        if (orders.isNotEmpty()) {
                            // Consume the cold-start flag only when an order is actually present, so an
                            // earlier empty/transient Success can't burn it before the order-bearing one.
                            mapFreshProcessLaunch = false
                            // Same guard for the "just accepted → open the detail" signal: consume it
                            // only once a real order is in hand, never on an empty interim refresh.
                            val justAccepted = openTripDetailOnNextLoad
                            openTripDetailOnNextLoad = false
                            hasActiveOrder = true
                            order = orders[0]
                            setView(order!!)
                            setOrderStateButton(order!!)
                            // The order just changed server-side — re-pull the fare so the live
                            // hero follows a reprice instead of waiting for the next GPS batch,
                            // which a stationary driver never sends. See the function doc.
                            refreshServerFareOnOrderChange(order!!)
                            // Resume banner subtitle shows the trip STATUS (stage), not the address —
                            // same stage text as the order-detail header (tvStageTitle).
                            binding.tvResumeAddress.text = getString(
                                when (order!!.state) {
                                    ORDER_STATE_ACCEPTED -> R.string.stage_accepted
                                    ORDER_STATE_STARTED -> R.string.stage_going_pickup
                                    ORDER_STATE_CHANGED_ARRIVED -> R.string.stage_waiting_pickup
                                    ORDER_STATE_CHANGED_GONE -> R.string.stage_in_trip
                                    else -> R.string.stage_accepted
                                }
                            )
                            // Show the active order's ID next to the "Faol buyurtma" label.
                            binding.tvResumeLabel.text =
                                "${getString(R.string.active_trip)} #${order!!.id}"
                            // Active order → never show the Exit (off-shift) button; the driver must
                            // finish/cancel the trip first. The Dispatcher button stays available.
                            binding.cvFinishJob.visibility = View.GONE
                            // A fresh accept always opens the detail; otherwise a cold launch shows the
                            // home map (minimised). justAccepted wins over wasColdStart so accepting the
                            // first order of a session still opens the order details, not the home map.
                            if (justAccepted) {
                                isTripMinimized = false
                            } else if (wasColdStart) {
                                isTripMinimized = true
                            }
                            // Sheet always shows for an active order; "minimised" = the peek detent.
                            val sheetWasGone =
                                binding.includeDialog.llBottomSheet.visibility != View.VISIBLE
                            setTripDetailVisible(true)
                            binding.flBottom.visibility = View.GONE
                            val newTripState = order!!.state
                            when {
                                // First open this session → land on the driver's start detent.
                                sheetWasGone -> setTripSheetState(
                                    if (isTripMinimized) BottomSheetBehavior.STATE_COLLAPSED
                                    else BottomSheetBehavior.STATE_HALF_EXPANDED
                                )
                                // Stage changed → rise to at least HALF so the new step is visible,
                                // but KEEP it if the driver already dragged it up to EXPANDED (max).
                                // Deferred one frame: setView + setOrderStateButton just mutated the
                                // sheet content (at STARTED the metrics row + Xarita button appear for
                                // the FIRST time), and driving the sheet in the same pass let the
                                // pending layout interrupt the settle and snap it back to peek — the
                                // "jump then return to minimize" seen only on the Boshlash transition.
                                lastShownTripState != null && lastShownTripState != newTripState -> {
                                    val sheet = binding.includeDialog.tripInfoSheet
                                    sheet.post {
                                        if (_binding == null) return@post
                                        val b = BottomSheetBehavior.from(
                                            binding.includeDialog.tripInfoSheet
                                        )
                                        if (b.state == BottomSheetBehavior.STATE_COLLAPSED) {
                                            b.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                                        }
                                    }
                                }
                                // Plain periodic refresh (no stage change) → leave the driver's detent
                                // untouched, so the sheet no longer jumps back on every poll.
                            }
                            lastShownTripState = newTripState
                            if (order!!.state >= ORDER_STATE_STARTED) {
                                commandStartTracking(order!!)
                            } else if (order!!.state >= ORDER_STATE_ACCEPTED) {
                                // Upload the order's GPS track (order-gps/batch) from ACCEPT so the
                                // driver→pickup approach leg reaches the backend. The trip timer +
                                // local meter still begin at Boshlash (with_timer=false).
                                commandArmGpsFromAccept(order!!)
                            }
                            // Re-arm the background ride auto-start if we resumed at ARRIVED.
                            if (order!!.state == ORDER_STATE_CHANGED_ARRIVED) {
                                commandArrivedAtPickup(order!!.id)
                            }
                            // Reopened / refreshed while already parked at a stop → show the
                            // "next stop?" prompt IMMEDIATELY now that order + destinationLocation
                            // are loaded, instead of waiting for the next 10 s tick or GPS fix.
                            maybeShowCheckpointPrompt()
                        } else {
                            // Authoritative "no trip in progress". `order` itself is deliberately
                            // NOT cleared here (the socket-cancel teardown depends on the retained
                            // object), so this flag is the only honest signal the reprice poll has.
                            hasActiveOrder = false
                            isTripMinimized = false
                            setTripDetailVisible(false)
                            binding.flBottom.visibility = View.VISIBLE
                            binding.cvResumeTrip.visibility = View.GONE
                            // No active order → the Exit (off-shift) button is available again.
                            binding.cvFinishJob.visibility = View.VISIBLE
                            // No active order → clear the trip overlay + route from the maps.
                            homeTripObjects?.clear()
                            // Clear the guard key so re-accepting an order redraws its pins.
                            homeTripKey = null
                            miniRouteLine?.clear()
                            if (::mapObjectsPolyline.isInitialized) mapObjectsPolyline.clear()
                            trackedRouteTail.clear()
                            drawnRoutePoints.clear()
                            // Drop the destination too, so a late tracking tick / in-flight reroute
                            // can't repaint the route (drawActiveRoute is gated on this).
                            destinationLocation = null
                        }

                        // Socket lifecycle: stop when off-shift with no orders, and AUTO-CONNECT
                        // ONLY to resume an active order on reopen. The driver goes online
                        // explicitly via the start-job slider (startWork → commandStartService);
                        // we do NOT force the socket open just because the shift is open, so with
                        // no active order the app stays disconnected until a job is started.
                        // Mirrors HomeFragment (list mode).
                        val workStatus = data!!.workStatus.status
                        val serviceRunning = MyTrackingService.isServiceRunning.value
                        if (workStatus == "completed" && serviceRunning == true && orders.isEmpty()) {
                            // Off-shift with no active order — tear down any ORPHANED tracking
                            // (covers an order that vanished server-side without a cancel event).
                            // Gated on workStatus=="completed" so a transient empty mid-trip (still
                            // on-shift) can never kill a live trip.
                            if (MyTrackingService.isTracking.value == true) {
                                commandStopTracking()
                            }
                            commandStopService()
                        } else if (serviceRunning != true && orders.isNotEmpty()) {
                            commandStartService()
                        }
                    }

                    is Resource.Error -> {
                        if (it.message == "401") {
                            commandStopService()
                            UserManager.deleteUser()
                            safeNavigate(R.id.action_mapFragment_to_loginFragment)
                        } else {
                            binding.apply {
                                progressBar.visibility = View.GONE
                                content.visibility = View.VISIBLE
                            }
                            showToast(it.message!!)
                        }
                    }
                }
            }
        }
    }

    private fun setView(order: Order) {
        // The mini-map is a gesture-locked route OVERVIEW — updateMiniMapRoute frames the whole
        // A→B route once (the puck then moves within it). No per-render zoom-16 crop on the car.
        updateMiniMapDriver()
        updateMiniMapRoute()
        updateHomeMapTrip()
        binding.includeDialog.apply {
            clientTotalBonus = order.contact?.bonus
            maxAmount = order.branch.clientBonusSettings?.maxAmount
            minAmount = order.branch.clientBonusSettings?.minAmount

            if (order.isCardPayment == true) {
                ivPaymentType.setImageResource(R.drawable.icon_card)
                tvPaymentType.text = getString(R.string.payment_type_card)
            } else {
                ivPaymentType.setImageResource(R.drawable.icon_cash)
                tvPaymentType.text = getString(R.string.payment_type_cash)
            }

            // Distance between locations
            if (order.locations.size > 1) {
//                val distanceMetre = Helper.calculateBetweenAllPoints(order.locations).toInt()
//                if (distanceMetre < 1000) {
//                    tvDistanceTrack.text = distanceMetre.toString() + getString(R.string.metre)
//                } else {
//                    tvDistanceTrack.text = Helper.metreToRoundKm(distanceMetre.toString()) + getString(R.string.km)
//                }

                tvDistanceTrack.text = order.distance.toString() + getString(R.string.km)
            }

            // Distance until client
            if (MyTrackingService.lastLatLngWholeApp.value != null) {
                val clientLatLng = LatLng(order.latitude.toDouble(), order.longitude.toDouble())
                val distanceTrackMetre = Helper.calculateBetweenTwoPoints(
                    MyTrackingService.lastLatLngWholeApp.value!!,
                    clientLatLng
                ).toInt()

                if (distanceTrackMetre < 1000) {
                    tvDistanceClient.text = "$distanceTrackMetre${getString(R.string.metre)}"
                } else {
                    tvDistanceClient.text =
                        "${Helper.metreToRoundKm(distanceTrackMetre.toString())}${getString(R.string.km)}"
                }
            } else {
                tvDistanceClient.text = getString(R.string.not_defined)
            }

            tvTariff.text = order.tariff.name ?: ""
//            tvAddress.text = "${order.address?.name ?: getString(R.string.not_showed)} (${order.addressCategory?.name ?: getString(R.string.not_showed)})"
//            tvAddressFinish.text = "${order.addressFinish?.name ?: getString(R.string.not_showed)} (${order.addressCategoryFinish?.name ?: getString(R.string.not_showed)})"

            // Pickup: prefer the real street (locations[].name). A taximeter has none (empty), so it
            // falls back to order.address.name — the nearest landmark ("Turkiston to'yxonasi") is
            // shown because it's more useful than nothing (per boss). Placeholder only if both empty.
            val pickup = order.locations.firstOrNull()
            tvAddress.text = pickup?.name?.takeIf { it.isNotEmpty() }
                ?: order.address?.name?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.not_showed)

            // Destination (red-dot) row: show only when there's a real 2nd point. A taximeter /
            // single-point order has no destination — hide the row instead of "Ko'rsatilmagan".
            if (order.locations.size > 1 && order.locations.last().name.isNotEmpty()) {
                llAddressFinish.visibility = View.VISIBLE
                tvAddressFinish.text = order.locations.last().name
            } else {
                llAddressFinish.visibility = View.GONE
            }

            tvStops.text = "${order.locations.size} ${getString(R.string.points)}"

            // All intermediate stops between pickup and dropoff → full multi-point route,
            // address-only rows (same as the new-order offer view).
            uz.teamwork.mehrgodriver.common.RoutePointsBinder.bindMiddle(
                llMiddlePoints,
                order.locations.drop(1).dropLast(1)
                    .map { it.name.ifEmpty { getString(R.string.not_showed) } }
            )

            if (!order.info.isNullOrEmpty()) {
                tvInfo.text = order.info
                llInfo.visibility = View.VISIBLE
            } else {
                llInfo.visibility = View.GONE
            }

            // No surge OR zero surge — the server ships 0 for plain orders; "0 so'm ↑" is noise.
            if ((order.addPrice ?: 0) <= 0) {
                tvAddPrice.visibility = View.GONE
                ivAddPrice.visibility = View.GONE
                val roundTotalPrice = Helper.roundPrice(order.price.toLong())
                tvPrice.text = "${Helper.formatPrice(roundTotalPrice)}${getString(R.string.sum)}"
            } else {
                tvAddPrice.visibility = View.VISIBLE
                ivAddPrice.visibility = View.VISIBLE
                tvAddPrice.text =
                    "${Helper.formatPrice(order.addPrice.toString())}${getString(R.string.sum)}"
                val roundTotalPrice = Helper.roundPrice(order.price.toLong())
                tvPrice.text = "~${Helper.formatPrice(roundTotalPrice)}${getString(R.string.sum)}"
            }
            // Both branches above seed the hero with BARE order.price. A B-order is then corrected
            // by updateAgreedHeroPrice (agreed + mid-trip service delta); a no-B order had no such
            // corrector, so the seed was the final word until the first GPS-batch fare arrived —
            // which is never, before the trip starts. Services the client added after booking were
            // therefore invisible in the price the whole time the sheet said "Заказ принят".
            // Self-gating: no-op on a B-order.
            updateBlessHeroPrice()
            // B-order with a mid-trip service change: the bare order.price just written above
            // misses the delta (server never folds a toggle into order.price — 79711); put it
            // back in the same frame so the refetch that carried the change can't clobber it.
            updateAgreedHeroPrice()

            // No-B (taximeter) order: there IS no agreed price to freeze — the fare card
            // becomes a LIVE meter (updated by the tracking observers via updateLiveHeroPrice).
            if (order.locations.size < 2) {
                tvPriceLabel.setText(R.string.live_price_label)
                tvPriceHint.setText(R.string.live_price_hint)
                // setView wrote order.price above — overwrite with the CURRENT live total in
                // the same frame, or every refresh flashes the stale quote (15 000 ↔ 6 000
                // flicker the drivers reported). Guarded until the tariff fields are loaded.
                if (startingPrice != null && additionalPrice != null) {
                    calculateTotalPrice()
                }
            } else {
                tvPriceLabel.setText(R.string.agreed_price)
                // Surcharge-aware: don't clobber an active "+X kutish" hint on refresh.
                updateAgreedSurchargeHint()
            }

            val services = order.services ?: emptyList()
            chipGroup.removeAllViews()
            if (services.isNotEmpty()) {
                llServices.visibility = View.VISIBLE

                tvServicesCount.text =
                    getString(R.string.services_active_count, services.size)

                for (service in services) {
                    val chip =
                        layoutInflater.inflate(
                            R.layout.adapter_service_chip,
                            null,
                            false
                        ) as TextView
                    // Name only — the PER-SERVICE PRICE is deliberately NOT shown on the order
                    // view: some drivers read it as money ON TOP of the fare, when it is already
                    // inside order.price. The full breakdown (name + price + total) lives in the
                    // services sheet (showServicesSheet), one tap away via the card's chevron.
                    chip.text = service.service.name
                    // Icon is chosen from the NAME, not the id — there is no global service
                    // catalogue (it is per-branch, and each white-label brand has its own
                    // backend), so ids are not portable. Same rule the iOS app uses.
                    chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        ServiceIcons.iconFor(service.service.name), 0, 0, 0
                    )
                    chipGroup.addView(chip)
                }
                // Services total row is hidden on the order view (see the price note above); it is
                // shown only in the services sheet.
            } else {
                llServices.visibility = View.GONE
            }
        }
    }

    /** iOS "processing" pill — shown in place of the slide CTA while a state
     *  transition request is in flight (replaces blanking the whole sheet). */
    private fun showOrderProcessing() {
        binding.includeDialog.apply {
            sbStart.visibility = View.GONE
            sbArrive.visibility = View.GONE
            sbGo.visibility = View.GONE
            sbFinish.visibility = View.GONE
            llProcessing.visibility = View.VISIBLE
        }
    }

    /** Clear the pill and re-render the current state's CTA (transition failed). */
    private fun hideOrderProcessing() {
        binding.includeDialog.llProcessing.visibility = View.GONE
        order?.let { setOrderStateButton(it) }
    }

    private fun setOrderStateButton(order: Order) {
        // Tell the tracking service the trip's FINAL destination up front so it can alert a
        // BACKGROUNDED driver to finish once they reach it. Set here — while the fragment is
        // foreground — so it survives a later backgrounding + background auto-start; the service
        // gates the notice on rideHasGone, so it only fires once the ride is actually on-route.
        MyTrackingService.finalDestination =
            if (order.locations.size == 2)
                order.locations.lastOrNull()?.let { LatLng(it.latitude, it.longitude) }
            else null // only single-destination A→B trips. Multi-stop (size>2) is handled in-app —
        // the bg notice can't track per-leg progress; a 1-point taximeter order has no dest leg.
        binding.includeDialog.apply {
            llProcessing.visibility = View.GONE
            // Metrics row (distance/speed/time) is hidden until the CLIENT is aboard
            // (Go/"Kettik") — the meter only runs from there, so showing three zero tiles
            // during the approach is noise (boss/user request 2026-07-19).
            if (order.state < ORDER_STATE_CHANGED_GONE) {
                llMetricsRow.visibility = View.GONE
                tvDistance.text = "0.00${getString(R.string.km)}"
                tvTripTime.text = Helper.formatTime(0)
            } else {
                llMetricsRow.visibility = View.VISIBLE
            }
            // iOS: external-nav (cvTripNavigate) is disabled until the trip actually starts —
            // ACCEPTED = the driver hasn't tapped Boshlash yet.
            val canNav = order.state != ORDER_STATE_ACCEPTED
            // ...and there has to be somewhere to go: a B-less order has nothing to route to
            // once the client is aboard, so drop the nav card entirely rather than open a
            // route back to the pickup the driver is already on.
            val hasNavTarget = hasNavigableTarget(order)
            cvTripNavigate.visibility = if (hasNavTarget) View.VISIBLE else View.GONE
            cvTripNavigate.isEnabled = canNav
            // Keep the button (white card + border + shadow) fully visible even when disabled —
            // dim only the icon to signal the not-yet-active state.
            ivTripNavigate.alpha = if (canNav) 1f else 0.4f
            // Taximeter (driver-created meter, from == ORDER_CREATED_DRIVER): there is no client
            // to call, no rider-applied services, and no fixed destination to navigate to — so hide
            // Mijoz / Xizmatlar / Xarita and leave only Cancel in the action row.
            val isTaximeter = order.from == ORDER_CREATED_DRIVER
            llCallClient.visibility = if (isTaximeter) View.GONE else View.VISIBLE
            llServicesBtn.visibility = if (isTaximeter) View.GONE else View.VISIBLE
            // Xarita action button: hidden until navigation is openable (ACCEPTED = before Boshlash),
            // always hidden for a taximeter, and hidden once a B-less order has no leg left to
            // navigate (nowhere to go — see hasNavigableTarget).
            llGoToMap.visibility =
                if (canNav && !isTaximeter && hasNavTarget) View.VISIBLE else View.GONE
            if (currentLocation != null) {
                when (order.state) {
                    ORDER_STATE_ACCEPTED -> {
                        tvStageTitle.setText(R.string.stage_accepted)
                        sbStart.visibility = View.VISIBLE
                        sbArrive.visibility = View.GONE
                        sbGo.visibility = View.GONE
                        sbFinish.visibility = View.GONE

                        llOrderCancel.visibility = View.VISIBLE
                        llWait.visibility = View.GONE
                        llOrderInfo.visibility = View.VISIBLE
                        llAddressInfo.visibility = View.VISIBLE
                        llTrackingInfo.visibility = View.GONE
                        llTrackTime.visibility = View.GONE
                        llTariff.visibility = View.VISIBLE
                        llDistanceClient.visibility = View.VISIBLE
                        llFinishAddress.visibility = View.GONE
                        llTotalPrice.visibility = View.GONE

                        llAddressFinish.visibility =
                            if (order.locations.size > 1) View.VISIBLE else View.GONE

                        // locations can be empty on a partial payload — guard the pickup read
                        // (raw [0] here vs the firstOrNull() used elsewhere in setView) and skip the
                        // destination marker + route setup when absent, instead of IndexOutOfBounds.
                        val pickup = order.locations.firstOrNull()
                        if (pickup != null) {
                            destinationLocation = Point(pickup.latitude, pickup.longitude)

                            mapObjectsDestinationPoint.clear()
                            setMarkerDestinationPoint(
                                destinationLocation!!.latitude,
                                destinationLocation!!.longitude
                            )

                            routePath.clear()
                            rebuildActiveRoute(currentLocation!!)
                        }
                    }

                    ORDER_STATE_STARTED -> {
                        tvStageTitle.setText(R.string.stage_going_pickup)
                        sbStart.visibility = View.GONE
                        sbArrive.visibility = View.VISIBLE
                        sbGo.visibility = View.GONE
                        sbFinish.visibility = View.GONE

                        llOrderCancel.visibility = View.VISIBLE
                        llWait.visibility = View.GONE
                        llOrderInfo.visibility = View.VISIBLE
                        llAddressInfo.visibility = View.VISIBLE
                        llTrackingInfo.visibility = View.GONE
                        llTrackTime.visibility = View.GONE
                        llTariff.visibility = View.VISIBLE
                        llDistanceClient.visibility = View.VISIBLE
                        llFinishAddress.visibility = View.GONE
                        llTotalPrice.visibility = View.GONE

                        llAddressFinish.visibility =
                            if (order.locations.size > 1) View.VISIBLE else View.GONE

                        // locations can be empty on a partial payload — guard the pickup read
                        // (raw [0] here vs the firstOrNull() used elsewhere in setView) and skip the
                        // destination marker + route setup when absent, instead of IndexOutOfBounds.
                        val pickup = order.locations.firstOrNull()
                        if (pickup != null) {
                            destinationLocation = Point(pickup.latitude, pickup.longitude)

                            mapObjectsDestinationPoint.clear()
                            setMarkerDestinationPoint(
                                destinationLocation!!.latitude,
                                destinationLocation!!.longitude
                            )

                            routePath.clear()
                            rebuildActiveRoute(currentLocation!!)
                        }
                    }

                    ORDER_STATE_CHANGED_ARRIVED -> {
                        tvStageTitle.setText(R.string.stage_waiting_pickup)
                        stopRouteService()

                        // Pickup-wait line only (no on-way leg yet).
                        llWaitOnWay.visibility = View.GONE
                        tvRatePickup.text =
                            "${Helper.formatPrice(order.tariff.priceOfWaiting)}${getString(R.string.per_min)}"
                        tvRatePickup.visibility = View.VISIBLE
                        tvRateOnWay.visibility = View.GONE

                        sbStart.visibility = View.GONE
                        sbArrive.visibility = View.GONE
                        sbGo.visibility = View.VISIBLE
                        sbFinish.visibility = View.GONE

                        llTrackTime.visibility = View.VISIBLE
                        llTariff.visibility = View.GONE
                        llTrackingInfo.visibility = View.VISIBLE

                        llOrderCancel.visibility = View.VISIBLE
                        llWait.visibility = View.GONE
                        llOrderInfo.visibility = View.VISIBLE
                        llAddressInfo.visibility = View.VISIBLE
                        llDistanceClient.visibility = View.VISIBLE
                        llFinishAddress.visibility = View.GONE
                        // iOS: the single price hero stays at the top; never the bottom total.
                        llTotalPrice.visibility = View.GONE

                        llAddressFinish.visibility =
                            if (order.locations.size > 1) View.VISIBLE else View.GONE

//                    if (order.useBonus) {
//                        llBonus.visibility = View.VISIBLE
//                        tvBonusPrice.text = "-${Helper.formatPrice(order.branch.clientBonusSettings!!.bonus)}${getString(R.string.sum)}"
//                    } else {
//                        llBonus.visibility = View.GONE
//                    }

                        if (order.locations.size > 1) {
                            destinationLocation =
                                Point(order.locations[1].latitude, order.locations[1].longitude)

                            mapObjectsDestinationPoint.clear()
                            setMarkerDestinationPoint(
                                destinationLocation!!.latitude,
                                destinationLocation!!.longitude
                            )

                            routePath.clear()
                            rebuildActiveRoute(currentLocation!!)
                        } else {
                            mapObjectsDestinationPoint.clear()
                            mapObjectsPolyline.clear()
                            destinationLocation = null
                        }
                    }

                    ORDER_STATE_CHANGED_GONE -> {
                        tvStageTitle.setText(R.string.stage_in_trip)

                        // Pickup wait (frozen) + live on-way wait, each with a rate.
                        llWaitOnWay.visibility = View.VISIBLE
                        tvRatePickup.text =
                            "${Helper.formatPrice(order.tariff.priceOfWaiting)}${getString(R.string.per_min)}"
                        tvRatePickup.visibility = View.VISIBLE
                        // Same §2 rule as the BILLING path (see priceOfWaitingOnWay assignment):
                        // ≤0 means "admin left it blank", not "free". A bare elvis here made the
                        // label read "0 /daq" while the driver was actually billed the pickup rate.
                        val onWayRate =
                            order.tariff.priceOfWaitingOnWay?.takeIf { it > 0 }
                                ?: order.tariff.priceOfWaiting.toInt()
                        tvRateOnWay.text =
                            "${Helper.formatPrice(onWayRate.toString())}${getString(R.string.per_min)}"
                        tvRateOnWay.visibility = View.VISIBLE

                        sbStart.visibility = View.GONE
                        sbArrive.visibility = View.GONE
                        sbGo.visibility = View.GONE
                        sbFinish.visibility = View.VISIBLE

                        llTrackTime.visibility = View.VISIBLE
                        llTariff.visibility = View.GONE
                        llTrackingInfo.visibility = View.VISIBLE

                        llOrderCancel.visibility = View.VISIBLE
                        llWait.visibility = View.GONE
                        llOrderInfo.visibility = View.VISIBLE
                        llAddressInfo.visibility = View.GONE
                        llDistanceClient.visibility = View.GONE
                        llFinishAddress.visibility = View.VISIBLE
                        // iOS: the single price hero stays at the top; never the bottom total.
                        llTotalPrice.visibility = View.GONE

                        llAddressFinish.visibility =
                            if (order.locations.size > 1) View.VISIBLE else View.GONE

//                    if (order.useBonus) {
//                        llBonus.visibility = View.VISIBLE
//                        tvBonusPrice.text = "-${Helper.formatPrice(order.branch.clientBonusSettings!!.bonus)}${getString(R.string.sum)}"
//                    } else {
//                        llBonus.visibility = View.GONE
//                    }

                        if (order.locations.size > 1) {
                            val location: Order.Location
                            if (MyTrackingService.destinationPosition != null) {
                                location = order.locations.firstOrNull {
                                    it.position == MyTrackingService.destinationPosition
                                } ?: order.locations.last()

                                if (location.name.isNotEmpty()) {
                                    tvFinishAddress.text = location.name
                                } else {
                                    tvFinishAddress.text = getString(R.string.not_showed)
                                }

                                val target = Point(location.latitude, location.longitude)
                                // Rebuild ONLY when the target actually moved (or nothing is drawn
                                // yet). setOrderStateButton runs on EVERY order refresh — socket
                                // events, getActiveMyOrders, and the /user/me inside
                                // prepareOrderFinish — and the rebuild it used to do
                                // unconditionally re-drew the whole remaining route from the
                                // driver's position, throwing away the per-fix trim. That is why
                                // tapping Finish and then coming back repainted legs the driver had
                                // already driven; with a stale destinationPosition it even pointed
                                // backwards to the previous stop. The trim/reroute system owns the
                                // line while the target is unchanged.
                                val targetChanged = destinationLocation
                                    ?.let { metersBetween(it, target) > 1.0 } ?: true

                                destinationLocation = target

                                mapObjectsDestinationPoint.clear()
                                setMarkerDestinationPoint(target.latitude, target.longitude)

                                // Respect the "drove on past the last stop" latch — that route was
                                // dropped on purpose and must not come back on a refresh.
                                if (!finalDestinationPassed &&
                                    (targetChanged || trackedRouteTail.size < 2)
                                ) {
                                    routePath.clear()
                                    currentLocation?.let { rebuildActiveRoute(it) }
                                }
                            } else {
                                MyTrackingService.destinationPosition = 1
                                // Fresh trip starts on the FULL remaining route.
                                routeThroughAllStops = true
                                location = order.locations[1]

                                if (location.name.isNotEmpty()) {
                                    tvFinishAddress.text = location.name
                                } else {
                                    tvFinishAddress.text = getString(R.string.not_showed)
                                }

                                destinationLocation = Point(location.latitude, location.longitude)

                                mapObjectsDestinationPoint.clear()
                                setMarkerDestinationPoint(
                                    destinationLocation!!.latitude,
                                    destinationLocation!!.longitude
                                )

                                routePath.clear()
                                rebuildActiveRoute(currentLocation!!)
                            }
                        } else {
                            stopRouteService()
                            tvFinishAddress.text = getString(R.string.not_showed)
                            mapObjectsDestinationPoint.clear()
                            mapObjectsPolyline.clear()
                            destinationLocation = null
                        }
                    }
                }
            } else {
                if (dialogRetryRequest?.isShowing != true) {
                    setTripDetailVisible(false)
                    if (MyTrackingService.isServiceRunning.value == true) {
                        binding.flBottom.visibility = View.GONE
                    } else {
                        binding.flBottom.visibility = View.VISIBLE
                    }

                    dialogRetryRequest = Dialog(requireContext())
                    _dialogRetryRequestBinding = DialogRetryRequestBinding.inflate(layoutInflater)
                    dialogRetryRequest!!.apply {
                        requestWindowFeature(Window.FEATURE_NO_TITLE)
                        setContentView(dialogRetryRequestBinding.root)
                        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        setCancelable(false)
                        setCanceledOnTouchOutside(false)
                    }

                    dialogRetryRequestBinding.cvRetry.setDebouncedClickListener {
                        if (CheckPermissions.checkLocationPermission(requireContext())) {
                            if (CheckPermissions.isGPSEnabled(requireContext())) {
                                // getLastLocation
                                fusedLocationProviderClient.lastLocation.addOnCompleteListener {
                                    val location = it.result

                                    if (location != null) {
                                        currentLocation = location
                                        getActiveMyOrders()
                                        dialogRetryRequest?.dismiss()

                                        selectedLocation =
                                            LatLng(location.latitude, location.longitude)

                                        if (marker == null) {
                                            moveCamera(currentLocation!!)
                                        }
                                    } else {

                                        // getNewLocation
                                        val request = LocationRequest.Builder(
                                            Priority.PRIORITY_HIGH_ACCURACY,
                                            0
                                        )
                                            .setWaitForAccurateLocation(false)
                                            .setMaxUpdates(1)
                                            .build()

                                        fusedLocationProviderClient.requestLocationUpdates(
                                            request,
                                            object : LocationCallback() {
                                                override fun onLocationResult(p0: LocationResult) {
                                                    super.onLocationResult(p0)
                                                    val lastLocation: Location = p0.lastLocation!!

                                                    currentLocation = lastLocation
                                                    getActiveMyOrders()
                                                    dialogRetryRequest?.dismiss()

                                                    selectedLocation = LatLng(
                                                        lastLocation.latitude,
                                                        lastLocation.longitude
                                                    )

                                                    if (marker == null) {
                                                        moveCamera(currentLocation!!)
                                                    }
                                                }

                                            },
                                            Looper.myLooper()
                                        )
                                    }
                                }
                            } else {
                                openGpsSettings()
                            }
                        } else {
                            openSystemSettings()
                        }
                    }

                    dialogRetryRequest?.show()
                }
            }
        }
    }

    private fun showDialogDestinations() {
        if (dialogDestination?.isShowing == true) return
        dialogDestination = Dialog(requireContext())
        _dialogDestinationBinding = DialogDestinationBinding.inflate(layoutInflater)
        dialogDestination!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogDestinationBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Without an explicit width the dialog defaults to wrap_content and the
            // address rows (ellipsize=end, weighted tvName) collapse to "…" beside the
            // icons. Pin to 90% of the screen so full addresses read on each row.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogDestinationBinding.apply {
            rvDestinations.adapter = destinationAdapter
            // Accent border marks the ACTIVE row: the current stop in single-target mode, or the
            // Full-route row in full-route mode (then no stop is accented).
            destinationAdapter!!.activeStopPosition =
                if (routeThroughAllStops) null else MyTrackingService.destinationPosition
            destinationAdapter!!.submitList(order?.locations ?: emptyList())
            destinationAdapter!!.notifyDataSetChanged()

            mcvFullRoute.setStrokeColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (routeThroughAllStops) R.color.app_color else R.color.gray_light_black_light
                )
            )

            // Full route — Local (in-app) draws the whole remaining route; External opens it in the
            // driver's chosen map/nav app.
            mcvFullRouteInApp.setDebouncedClickListener {
                val cur = currentLocation
                if (cur == null) {
                    showToast(getString(R.string.location_not_found))
                    return@setDebouncedClickListener
                }
                routeThroughAllStops = true
                rebuildActiveRoute(cur)
                dialogDestination?.dismiss()
                isTripMinimized = true
                setTripSheetState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            mcvFullRouteMap.setDebouncedClickListener {
                routeThroughAllStops = true
                dialogDestination?.dismiss()
                openTripNavigation()
            }
        }

        dialogDestination?.show()
    }

    private fun orderStart(orderId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.orderStart(orderId).collect {
                when (it) {
                    is Resource.Loading -> {
                        showOrderProcessing()
                    }

                    is Resource.Success -> {
                        binding.includeDialog.content.visibility = View.VISIBLE
                        binding.includeDialog.progressBar.visibility = View.GONE

                        if (order?.from == ORDER_CREATED_DRIVER) {
                            getActiveMyOrders()
                        } else {
                            order = it.data?.data!!
                            setOrderStateButton(order!!)
                            // Meter starts at Boshlash (Start): begin distance + time tracking now.
                            commandStartTracking(order!!)

                            // Auto-open the external navigator on Start: driver → pickup (the rider
                            // isn't aboard yet, so we route to the pickup only, not the destination).
                            if (MapTypeManager.getChoose()) openTripNavigation()
                        }
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        hideOrderProcessing()
                    }
                }
            }
        }
    }

    private fun orderArrive(orderId: Int) {
        // Guard: the driver must be within ARRIVE_PICKUP_RADIUS_M of the pickup point (the first
        // location) to mark "arrived". Skipped only if we genuinely can't locate either point
        // (no GPS fix / no pickup) — never block the driver on missing data.
        val pickup = order?.locations?.firstOrNull()
        val driver = MyTrackingService.lastLatLngWholeApp.value
        if (pickup != null && driver != null) {
            val distance = Helper.calculateBetweenTwoPoints(
                LatLng(driver.latitude, driver.longitude),
                LatLng(pickup.latitude, pickup.longitude)
            )
            if (distance > Constants.ARRIVE_PICKUP_RADIUS_M) {
                showInfoPopup(
                    R.drawable.ic_error_circle,
                    R.string.arrive_too_far_title,
                    R.string.arrive_too_far_hint,
                    autoDismissMs = 5000
                )
                return
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.orderArrive(orderId).collect {
                when (it) {
                    is Resource.Loading -> {
                        showOrderProcessing()
                    }

                    is Resource.Success -> {
                        order = it.data?.data!!
                        setOrderStateButton(order!!)

                        // Tracking already runs from Boshlash (Start); don't reset it here — that
                        // would wipe the distance/time so far. Just ensure it's on (defensive).
                        if (!MyTrackingService.isTracking.value!!) {
                            commandStartTracking(order!!)
                        }

                        // Auto-start the client-wait meter on arrival (the service's own design
                        // intent — see MyTrackingService startTracking comment). The driver can
                        // stop/start it manually afterwards via the wait toggle. Fired ONLY here
                        // (the one-shot ARRIVED transition), NEVER from getActiveMyOrders refresh,
                        // so a manual stop sticks; startWaitingTimer() is idempotent and the
                        // speedCar observer stops it if the car is still rolling.
                        commandStartTimeWait()
                        // Arm the background ride auto-start (driver reached the pickup).
                        commandArrivedAtPickup(orderId)

                        binding.includeDialog.content.visibility = View.VISIBLE
                        binding.includeDialog.progressBar.visibility = View.GONE
                    }

                    is Resource.Error -> {
                        hideOrderProcessing()
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    private fun orderGo(orderId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.orderGo(orderId).collect {
                when (it) {
                    is Resource.Loading -> {
                        showOrderProcessing()
                    }

                    is Resource.Success -> {
                        val fresh = it.data?.data!!
                        // Mirror the service's stale-Go guard: a delayed ack for an order
                        // that was cancelled/replaced meanwhile must not resurrect it in the
                        // UI (CTA flip to finish, navigator auto-open, later commands built
                        // from the stale `order`).
                        if (order?.id != fresh.id) {
                            hideOrderProcessing()
                            return@collect
                        }
                        order = fresh
                        setOrderStateButton(order!!)

                        commandStartWay(orderId)
                        binding.includeDialog.content.visibility = View.VISIBLE
                        binding.includeDialog.progressBar.visibility = View.GONE

                        // Auto-open the external navigator on Go: driver → next point.
                        if (MapTypeManager.getChoose()) openTripNavigation()
                    }

                    is Resource.Error -> {
                        hideOrderProcessing()
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    // In-flight guard for the finish preparation: the slider library's reset animation
    // re-enables the view ~250 ms after slide-complete (SlideToActView.onAnimationEnd calls
    // setEnabled(true)), so `isEnabled` alone can't stop a second slide — use isLocked + this.
    private var finishPrepInFlight = false

    // True while POST order/complete is in flight. The finish dialog live-refreshes on every
    // server fare frame (refreshFinishDialogIfShowing), but once the driver has slid Submit the
    // number they approved is the number that was posted — a late frame must not repaint the
    // receipt under the spinner to a total that differs from the submitted one.
    private var finishSubmitInFlight = false

    // Order id the open finish dialog was built for — refreshFinishDialogIfShowing() bails when
    // the mutable `order` member no longer matches (see showDialogTrackingFinish).
    private var finishDialogOrderId: Int? = null

    // 10s fare poll that runs only while the finish popup is showing — see
    // showDialogTrackingFinish. Cancelled on dismiss and by the view lifecycle scope.
    private var finishDialogPollJob: Job? = null

    // Order finish
    private fun prepareOrderFinish() {
        // Triggered from a Dialog/CTA tap that can outlive the view — guard viewLifecycleOwner.
        if (!isAdded || view == null) return
        // An order can only be completed AFTER Go ("Kettik", state 9). The backend derives the
        // billable distance from the stage-9 GPS track alone, so completing straight from ARRIVED
        // leaves it with no track at all and the trip bills as if nothing was driven. Second line
        // of defence behind the caller-side gates (the Finish slider is only visible at state 9,
        // and the arrive-at-destination prompt is gated too) — so a future third entry point
        // cannot silently reopen the hole.
        if (order != null && order!!.state < ORDER_STATE_CHANGED_GONE) {
            showInfoPopup(
                R.drawable.ic_error_circle,
                R.string.press_go_before_finish
            )
            return
        }
        if (finishPrepInFlight) return
        finishPrepInFlight = true

        // Flush any QUEUED GPS point right now instead of at the next 15 s cadence tick. On
        // order 79688 the final point sat ~9 s in the queue; its batch went out only after the
        // finish-time GET fare below had already answered, so the ack's higher meter (40 000)
        // landed one second AFTER the dialog captured 39 000 — the server billed its own 40 000
        // and the driver approved a number 1 000 lower. Draining first makes the ack land inside
        // the bounded fare wait in the common case; the dialog live-refresh below covers the rest.
        //
        // reArm() first: finishing is an order lifecycle transition, same as setOrderState()/
        // flushNow(). Without it a reject-latched uploader (5× 403 mid-trip) makes this nudge
        // silently inert in exactly the state where points are guaranteed to be queued — the
        // final leg would then never reach the server before order/complete.
        gpsBatchUploader.reArm()
        gpsBatchUploader.nudge()

        // Fresh fare snapshot, started in PARALLEL so it overlaps /user/me — but the receipt
        // now WAITS for it (bounded join below).
        //
        // The old comment here claimed "the live stream already keeps serverPrice ≤15 s fresh".
        // That is NOT true: serverPrice is fed only by GPS-batch acks, and drainOnce() skips the
        // request entirely when no point passed the filter AND no wait timer moved. A stationary
        // driver therefore sends nothing and serverPrice can sit minutes stale — observed on a
        // real trip where the client removed a 2 000 service and the receipt still had the
        // pre-removal total. Rendering the receipt from that snapshot BILLS the stale number,
        // because the total is captured into the Submit lambda and a late applyServerFare()
        // updates fields without re-rendering the dialog.
        var fareFresh = false
        val fareJob = order?.id?.let { oid ->
            viewLifecycleOwner.lifecycleScope.launch {
                mapViewModel.getFare(oid).collect { fare ->
                    if (fare is Resource.Success) fare.data?.data?.let {
                        // "Fresh" has to mean fresh AND PRICED. A 200 whose body
                        // carries no price at all still satisfied this latch, and
                        // applyServerFare then left `serverPrice` null — so the gate
                        // below passed, the popup opened, and the total fell through
                        // to the app's own meter (starting price + distance intervals
                        // + local wait + local services), which is what got POSTed as
                        // total_price. A priceless answer is not an answer.
                        //
                        // A FIXED-ROUTE order bills `kept_projection`, not `live.price`
                        // — so a frame carrying only the projection IS priced for that
                        // order shape, and refusing it would block a finish while the
                        // billable number sat in hand.
                        if (isPricedForFinish(it)) fareFresh = true
                        applyServerFare(it)
                        // A slow fare can outlive the join below and land while the dialog is
                        // already on screen — repaint it instead of billing the pre-fetch
                        // snapshot.
                        refreshFinishDialogIfShowing()
                    }
                }
            }
        }

        // Explicit progress. The finish flow BLOCKS on a fresh server fare now, so a slider
        // that merely went dim is not enough feedback — say what is happening.
        val finishSliderLabel = binding.includeDialog.sbFinish.text

        viewLifecycleOwner.lifecycleScope.launch {
            // finally: always clear the in-flight latch + unlock the slider, even on
            // cancellation — else a cancelled prep would leave Finish permanently dead
            // (the map fragment is retained, so the flag would never reset itself).
            try {
                mapViewModel.getUserMe().collect {
                    when (it) {
                        is Resource.Loading -> {
                            // The Finish slider dims + locks while /user/me is in flight — NOT a
                            // full-screen white loading (the old code hid the whole map).
                            binding.includeDialog.sbFinish.apply {
                                isLocked = true
                                alpha = 0.6f
                                text = getString(R.string.calculating_fare)
                            }
                        }

                        is Resource.Success -> {
                            // /user/me can return an empty orders array (order already
                            // finished/cancelled server-side, or a race where Finish fires after
                            // the active order is gone). Guard instead of orders[0] to avoid
                            // IndexOutOfBoundsException.
                            val orders = it.data?.data?.orders ?: emptyList()
                            val activeOrder = orders.firstOrNull()
                            if (activeOrder == null) {
                                // This order is already over server-side. The prompt that led here
                                // is a non-cancellable modal, so toasting and returning left the
                                // driver stuck staring at it — retire it with the order.
                                endedOrderId = order?.id
                                dismissTripPrompts()
                                showToast(getString(R.string.no_active_orders))
                                return@collect
                            }
                            order = activeOrder
                            setView(order!!)
                            setOrderStateButton(order!!)

                            // A slim/partial payload (socket refresh) can omit `services` — keep
                            // the previous total instead of zeroing it, or the live hero briefly
                            // drops to base-only (16 000 → 6 000 flicker the drivers reported).
                            order!!.services?.let { services ->
                                totalServicePrice = 0L
                                services.forEach { service ->
                                    totalServicePrice += service.total
                                }
                            }

                            calculateTotalPrice()

                            // HARD GATE on a fresh server fare (user decision 2026-07-31): the
                            // popup is never painted from cache. Its total is read ONCE here and
                            // closed over by the Submit lambda, so a stale snapshot is a stale
                            // BILL — that is how a service the client removed mid-trip could
                            // still be charged, and how order 79874 quoted 16 000 against a
                            // 17 000 settlement.
                            //
                            // Refusing to open on failure does not strand the driver: order/
                            // complete needs the network too, so a driver who cannot reach the
                            // fare endpoint could not have finished anyway. He retries the
                            // slider, which is unlocked by the finally below.
                            //
                            // FINISH_FARE_WAIT_MS is a deadlock guard, not a fallback window —
                            // a wedged socket must not leave the slider locked forever.
                            // `fareJob == null` means order?.id was null, i.e. we
                            // never even asked — that is the cached-total open this
                            // gate exists to forbid, not a pass.
                            val gotFreshFare = fareJob != null &&
                                    withTimeoutOrNull(FINISH_FARE_WAIT_MS) { fareJob.join() } != null
                                    && fareFresh
                            if (!gotFreshFare) {
                                // ESCAPE HATCH. Refusing forever is worse than
                                // quoting a provisional number: if the fare endpoint
                                // keeps answering 200-without-a-price (or 500, or a
                                // null envelope), the hard gate leaves the driver
                                // unable to complete the trip AT ALL — passenger
                                // gone, meter still running, no other route to
                                // order/complete. And the posted total_price is
                                // discarded server-side anyway (wire-proved on
                                // 79687/79688/79711), so opening on the existing
                                // provisional total cannot misbill; it can only
                                // quote a figure the settlement then corrects, which
                                // is exactly what the receipt's provisional state
                                // already communicates.
                                //
                                // So: hard-refuse the FIRST attempt (that is what
                                // catches a transient blip), and let a second
                                // consecutive attempt for the same order through.
                                val orderId = order?.id ?: -1
                                if (finishGateRefusedOrderId != orderId) {
                                    finishGateRefusedOrderId = orderId
                                    showInfoPopup(
                                        R.drawable.ic_error_circle,
                                        R.string.fare_fetch_failed
                                    )
                                    return@collect
                                }
                            }
                            finishGateRefusedOrderId = -1

                            showDialogTrackingFinish(order!!)
                        }

                        is Resource.Error -> {
                            showToast(it.message!!)
                        }
                    }
                }
            } finally {
                finishPrepInFlight = false
                _binding?.includeDialog?.sbFinish?.apply {
                    isLocked = false
                    alpha = 1f
                    text = finishSliderLabel
                }
            }
        }
    }

    /**
     * True while the finish dialog has a RUNNING wait meter paused on its behalf, so an
     * aborted finish ("Davom etish") knows it owes a resume. See [pauseWaitForFinishDialog].
     */
    private var waitPausedForFinish = false

    /**
     * Freeze the waiting meter for as long as the final-bill dialog is on screen.
     *
     * The dialog's total is computed ONCE, when the dialog is built, but the wait meter used
     * to keep billing behind it: every tick raised the server's live total while the driver
     * read the very screen that says "check the amount before finishing". Reproduced on prod
     * order 77702 — the dialog and receipt both said 11 000 while the server had already moved
     * to 12 000, and that is the figure the trip was booked at. The driver quotes the passenger
     * one number and the system records another.
     *
     * Freezing (rather than recomputing on confirm) is deliberate: the number the driver
     * approved is the number that gets posted, instead of silently changing under the tap.
     * It also keeps the server in step, because the server derives waiting_sec from the
     * waiting_time we upload in each GPS batch — a frozen meter uploads a frozen wait.
     *
     * Stop/start is safe here: the service accumulates `timeWait += lapWaitTime` when the
     * timer loop exits (MyTrackingService.kt:1032), so a resume continues rather than restarts.
     */
    private fun pauseWaitForFinishDialog() {
        // Set UNCONDITIONALLY, before the early return: on auto-wait brands the meter being off
        // right now is no protection — the next parked-speed fix would arm it behind the dialog.
        MyTrackingService.finishBillOpen = true
        if (waitPausedForFinish || !isWaiting) return
        waitPausedForFinish = true
        commandStopTimeWait()
    }

    /** Give the meter back when the driver returns to the trip instead of finishing it. */
    private fun resumeWaitAfterFinishAborted() {
        // Likewise unconditional: auto-wait must be re-armed even when we never paused a
        // running meter, or a brand build would lose auto-wait for the rest of the ride.
        MyTrackingService.finishBillOpen = false
        if (!waitPausedForFinish) return
        waitPausedForFinish = false
        commandStartTimeWait()
    }

    private fun showDialogTrackingFinish(order: Order) {
        // A double-tap on Finish can call this twice; the second Dialog would overwrite the
        // member reference and orphan the first as an unclosable (setCancelable=false) window
        // stuck over the receipt screen. Never stack a second instance.
        if (dialogTrackingFinish?.isShowing == true) return
        dialogTrackingFinish = Dialog(requireContext())
        _dialogTrackingFinishBinding = DialogTrackingFinishBinding.inflate(layoutInflater)
        dialogTrackingFinish!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogTrackingFinishBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Dismiss only via the dialog's own buttons — block back + outside tap.
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            // A custom-view dialog defaults to wrap_content width, which squeezed the
            // receipt into a sliver — every label/value ellipsized to "…" and the
            // Continue/Finish buttons clipped to one letter. Pin it to 92% of the
            // screen width so the whole receipt reads cleanly.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Fresh dialog, fresh submit state — a previous finish attempt's in-flight latch must
        // not suppress this dialog's live-refresh.
        finishSubmitInFlight = false
        // Pin the dialog to the order it was built for. The live-refresh re-reads the mutable
        // `order` member, which getActiveMyOrders() reassigns to orders[0] — with a second
        // accepted order queued, a mid-dialog refetch (socket reconnect, services_changed,
        // cancel broadcast) could swap the member and a repaint would silently rebind Submit
        // to the WRONG order, completing it with this trip's meters.
        finishDialogOrderId = order.id

        setViewDialogTrackingFinish(order)

        // Freeze the meter for exactly as long as this dialog is up. Done here rather than in
        // prepareOrderFinish() so the only paths that owe a resume are the dialog's own two
        // buttons — both real taps, so they can never land inside the timer loop's 100 ms
        // stop/start window. A prep that fails before the dialog appears never pauses at all.
        pauseWaitForFinishDialog()

        // While the popup is up, re-pull the server fare every 10 s (user request 2026-07-31).
        // The passive refresh channels (batch acks / socket pushes) go quiet the moment the
        // driver stands still — no movement means no points, and the frozen wait meter means
        // no timer delta — so without this poll the "real server" number could sit stale for
        // the whole read. Cancelled on dismiss (both buttons dismiss) and by the view scope.
        dialogTrackingFinish?.setOnDismissListener { finishDialogPollJob?.cancel() }
        dialogTrackingFinish?.show()

        // No immediate pull here: prepareOrderFinish() already blocked on a fresh fare, so the
        // dialog is painted from a snapshot seconds old at most. This poll only keeps it honest
        // while the driver reads it.
        finishDialogPollJob?.cancel()
        finishDialogPollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (dialogTrackingFinish?.isShowing != true) break
                if (finishSubmitInFlight) continue
                // Backgrounded (home / screen off) with the uncancelable dialog up: the view
                // scope stays alive until DESTROY, so without this gate the poll would keep
                // hitting the network with nobody looking at the number.
                if (!viewLifecycleOwner.lifecycle.currentState
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                ) continue
                val id = finishDialogOrderId ?: break
                mapViewModel.getFare(id).collect { fare ->
                    if (fare is Resource.Success) fare.data?.data?.let {
                        applyServerFare(it)
                        refreshFinishDialogIfShowing()
                    }
                }
            }
        }
    }

    /**
     * Repaint the OPEN finish dialog after a fresh server fare landed.
     *
     * The dialog total is computed once when the dialog is built, but a GPS batch that was
     * already queued/in-flight at that moment can ack a moment later with a higher meter.
     * Wire-verified on order 79688: the dialog captured live.price 39 000, the final point's
     * ack brought 40 000 ~1 s later, `order/complete` posted the stale 39 000 — the server
     * re-judged and billed its own 40 000, so the driver approved a number 1 000 so'm lower
     * than the actual bill.
     *
     * Re-running [setViewDialogTrackingFinish] recomputes every row AND re-sets the Submit
     * closure, so the number ON SCREEN is always the number that gets posted — the "what the
     * driver approved is what is billed" contract of [pauseWaitForFinishDialog] extended to
     * the distance meter (which, unlike the wait meter, cannot be frozen client-side: the
     * server prices whatever track it has already received).
     *
     * No-ops once Submit is in flight: after the slide, the approved number must not repaint
     * under the spinner.
     *
     * What a repaint can change: the server's running total (live.price — the number this
     * popup shows), its service/waiting rows, and the offline agreed fallback after a
     * mid-trip service add/remove. No client-side judging — the server's settlement reaches
     * the receipt via the `order_completed` frame regardless.
     */
    private fun refreshFinishDialogIfShowing() {
        if (!isAdded || view == null) return
        if (finishSubmitInFlight) return
        if (dialogTrackingFinish?.isShowing != true) return
        if (_dialogTrackingFinishBinding == null) return
        val o = order ?: return
        // Identity guard: the member may have been swapped to a DIFFERENT order by a mid-dialog
        // getActiveMyOrders (queued second order + cancel/reconnect). Repainting would rebind
        // Submit to that order and complete the wrong trip — keep the build-time snapshot
        // instead; its closure is still pinned to the original order.
        if (o.id != finishDialogOrderId) return
        setViewDialogTrackingFinish(o)
    }

    private fun setViewDialogTrackingFinish(order: Order) {
        dialogTrackingFinishBinding.apply {
            // Payment chip is colour-coded: cash = green, card = blue (matching
            // the "Xarita" map button in the trip detail view).
            if (order.isCardPayment == true) {
                val blue = ContextCompat.getColor(requireContext(), R.color.blue_middle)
                ivPaymentType.setImageResource(R.drawable.ph_credit_card)
                ivPaymentType.setColorFilter(blue)
                tvPaymentType.setTextColor(blue)
                tvPaymentType.text = getString(R.string.payment_type_card)
                llPaymentType.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.blue_soft
                        )
                    )
            } else {
                val green = ContextCompat.getColor(requireContext(), R.color.green)
                ivPaymentType.setImageResource(R.drawable.ph_money)
                ivPaymentType.setColorFilter(green)
                tvPaymentType.setTextColor(green)
                tvPaymentType.text = getString(R.string.payment_type_cash)
                llPaymentType.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.green_soft
                        )
                    )
            }

            // Prefer server distance (MOBILE.md §6). Falls back to local taximeter.
            val fDistance = serverDistanceKm
                ?.let { "%.2f".format(Locale.US, it) }
                ?: Helper.metreToRoundKm((distanceOutCity + distanceInCity).toString())

            tvDistance.text = "$fDistance${getString(R.string.km)}"
            // Waiting line from the server breakdown (wait + on-way + traffic costs) when a
            // snapshot exists; the local meter only covers offline.
            val waitLine = serverWaitCost ?: totalWaitingPrice.toLong()
            tvWaitTimePrice.text =
                "${Helper.formatPrice(Helper.roundPrice(waitLine))}${getString(R.string.sum)}"
            // Services row mirrors the SERVER components when the total is live.price —
            // subtracting app-side values from a server total made the rows drift (order
            // 77704). App-side values cover the offline fallbacks (agreed / local meter),
            // where they ARE the composition of the displayed total.
            val servicesRow = if (serverPriceIncludesExtras) {
                val lv = latestLive
                ((lv?.servicesPrice ?: 0.0) + (lv?.podacha ?: 0.0) + (lv?.extraPrice ?: 0.0))
                    .roundToLong()
            } else {
                totalServicePrice + additionalPrice!!
            }
            tvAddServicesPrice.text =
                "${Helper.formatPrice(Helper.roundPrice(servicesRow))}${
                    getString(R.string.sum)
                }"
            // Old method
//            tvTrackPrice.text = "${Helper.formatPrice(Helper.roundPrice(startingPrice!! + totalTrackingPriceInCity.toLong() + totalTrackingPriceOutCity.toLong()))}${getString(R.string.sum)}"
//            val totalPrice = Helper.roundPrice(startingPrice!! + additionalPrice!! + totalServicePrice + totalWaitingPrice.toLong() + totalTrackingPriceInCity.toLong() + totalTrackingPriceOutCity.toLong()).toLong()
//            tvTotalPrice.text = "${Helper.formatPrice(Helper.roundPrice(totalPrice))}${getString(R.string.sum)}"

            // Order-complete pricing (user decision 2026-07-31, superseding the short-lived
            // judge preview): this popup shows the SERVER'S OWN running total — live.price =
            // fare + services + podacha + extra + waiting — the client neither mirrors the
            // endpoint judge nor composes a fare of its own. The trip-sheet HERO keeps the
            // friendly agreed(+service delta) preview; this popup is the "real server"
            // number; the RECEIPT then adopts the settled bill from the `order_completed`
            // frame (which the server may judge DOWN to the agreed price when the driver
            // finishes at B — that judgment is backend-only, and the posted total_price is
            // ignored either way: wire-proved 79687/79688/79711).
            val serverTotal = serverPrice?.let { sp ->
                if (serverPriceIncludesExtras) {
                    // live.price already = round(fare + services + podacha + extra + waiting).
                    Helper.roundPrice(sp).toLong()
                } else {
                    Helper.roundPrice(sp + additionalPrice!! + totalServicePrice).toLong()
                }
            }
            // Offline fallbacks (no server snapshot at all): the agreed preview (+ mid-trip
            // service delta — the server does NOT fold a toggle into order.price, 79711) for
            // B-orders, the local meter for taximeter orders.
            val runningTotal = serverTotal
                ?: if (order.locations.size >= 2) {
                    Helper.roundPrice(
                        order.price.toLong() + agreedServiceDelta() + waitLine
                    ).toLong()
                } else {
                    Helper.roundPrice(
                        startingPrice!! + additionalPrice!! + totalServicePrice +
                                totalWaitingPrice.toLong() +
                                totalTrackingPriceInCity.toLong() + totalTrackingPriceOutCity.toLong()
                    ).toLong()
                }
            // THE SERVER'S OWN NUMBER. No composing, no judging, no local arithmetic — that is
            // the standing decision recorded above (2026-07-31) and it is restored here.
            //
            // It was briefly overridden by a `kept_projection` branch, on the strength of 79874
            // (agreed 17 000 / meter 16 000 → settled 17 000) and 79959 (agreed 38 000 / meter
            // 43 000 → settled 38 000). Order 80296 shows why that was the wrong lever: booked
            // WITH the AC, the rider switched it off before arrival, and `kept_projection` sat at
            // 25 000 while the trip was worth less. The popup quoted 25 000, the driver approved
            // 25 000 — and the backend settled 5 000. Quoting the projection put a number on the
            // approve button that the backend never intended to charge.
            //
            // The client cannot out-guess the settlement and must not try: `total_price` is
            // ignored by the backend anyway (79687/79688/79711). Show what the server says now,
            // and let the RECEIPT show what it actually billed.
            val totalPrice = runningTotal
            // Same clamp as the receipt's rideCost — these two must stay identical, they are
            // now the same number shown on two consecutive screens.
            tvTrackPrice.text =
                "${
                    Helper.formatPrice(
                        Helper.roundPrice((totalPrice - (servicesRow + waitLine)).coerceAtLeast(0L))
                    )
                }${getString(R.string.sum)}"
            tvTotalPrice.text =
                "${Helper.formatPrice(totalPrice.toString())}${getString(R.string.sum)}"

            // Promo code
            var calculatedPromo = 0L
            val promoCodeValue = order.promoCode?.usage?.amount
            if (promoCodeValue != null) {
                llPromoCode.visibility = View.VISIBLE
                llTotalPrice.visibility = View.VISIBLE

                var isPercentExist = false
                var percent = 0
                if (promoCodeValue[promoCodeValue.length - 1] == '%') {
                    isPercentExist = true
                    percent = promoCodeValue.substring(0, promoCodeValue.length - 1).toInt()
                }

                calculatedPromo = if (isPercentExist) {
                    (totalPrice / 100) * percent
                } else {
                    if (promoCodeValue.toLong() < totalPrice) {
                        promoCodeValue.toLong()
                    } else {
                        totalPrice
                    }
                }

                tvPromoCode.text =
                    "-${Helper.formatPrice(calculatedPromo.toString())}${getString(R.string.sum)}"
            } else {
                llPromoCode.visibility = View.GONE
            }

            // Bonus. Snapshot the three inputs — they are re-seeded by setView() from NULLABLE
            // payload fields, and setView can run WHILE this dialog is up (getActiveMyOrders on
            // socket reconnect / services_changed). The live-refresh re-invokes this function at
            // arbitrary times, so the old `!!` derefs would turn a slim refetch payload into a
            // KotlinNullPointerException with the uncancelable bill dialog stuck on screen.
            // Missing inputs -> render as no-bonus (0 discount is the safe direction: the driver
            // is never under-paid, and the server applies the real bonus on complete).
            var calculatedBonusFinal = 0L
            val calculatedBonus: Long
            val bonusMaxAmount = maxAmount
            val bonusClientTotal = clientTotalBonus
            val bonusMinAmount = minAmount
            if (order.useBonus && !bonusMaxAmount.isNullOrEmpty() &&
                bonusClientTotal != null && bonusMinAmount != null
            ) {
                llBonus.visibility = View.VISIBLE
                llTotalPrice.visibility = View.VISIBLE

                var isPercentExist = false
                var percent = 0
                if (bonusMaxAmount[bonusMaxAmount.length - 1] == '%') {
                    isPercentExist = true
                    percent = bonusMaxAmount.substring(0, bonusMaxAmount.length - 1).toInt()
                }

                calculatedBonus = if (isPercentExist) {
                    ((totalPrice - calculatedPromo) / 100) * percent
                } else {
                    bonusMaxAmount.toLong()
                }

                calculatedBonusFinal = if (calculatedBonus < bonusClientTotal) {
                    calculatedBonus
                } else {
                    bonusClientTotal
                }

                if (bonusClientTotal < bonusMinAmount) {
                    calculatedBonusFinal = 0
                }
                // The bonus can never discount more than the fare left after the promo —
                // otherwise the final total goes negative (the flat maxAmount path is otherwise
                // uncapped against the fare).
                calculatedBonusFinal = calculatedBonusFinal
                    .coerceIn(0L, (totalPrice - calculatedPromo).coerceAtLeast(0L))
            } else {
                llBonus.visibility = View.GONE
            }
            tvBonusPrice.text =
                "-${Helper.formatPrice(calculatedBonusFinal.toString())}${getString(R.string.sum)}"

            // Final total price — floored at 0 so bonus + promo can never produce a negative bill.
            val finalTotalPrice = (totalPrice - calculatedBonusFinal - calculatedPromo)
                .coerceAtLeast(0L)
            // Rounded EXACTLY like the receipt (TripFinishFragment.collectedTotal): cash to the
            // nearest 1 000, card left precise. The two screens are one number shown twice — the
            // driver approves it here and hands it over there — so they must not differ by so much
            // as the rounding step. This line used to round to the nearest 100 while the receipt
            // rounded to 1 000, which left a residual mismatch even once the totals agreed.
            val collectedTotal = if (order.isCardPayment == true) finalTotalPrice
            else Helper.roundToThousand(finalTotalPrice)
            tvFinalTotalPrice.text =
                "${Helper.formatPrice(collectedTotal.toString())}${getString(R.string.sum)}"

            // OnClick
            mcvCancel.setOnClickListener {
                // "Davom etish" = back to the trip, so the wait this dialog froze must run again
                // — otherwise a driver who peeks at the bill and returns waits for free.
                resumeWaitAfterFinishAborted()
                dialogTrackingFinish?.dismiss()
            }

            mcvSubmit.setDebouncedClickListener {
                // Read the last GPS fix once so lat/long/accuracy come from one coherent location.
                val finishLocation = MyTrackingService.lastLocationWholeApp
                // Wait split (ORDER_COMPLETE_WAITING.md): waiting_time = pickup wait only
                // (frozen at Go), waiting_time_ontheway = waits after the client boarded —
                // the backend prices the two legs with their own tariffs. Before Go the
                // whole wait IS the pickup wait (untilGone is still 0), so guard on state.
                val pickupWaitMs = if (order.state >= ORDER_STATE_CHANGED_GONE) {
                    MyTrackingService.waitedTimeUntilGone.coerceAtMost(currentWaitTimeInMillis)
                } else {
                    currentWaitTimeInMillis
                }
                val onWayWaitMs = (currentWaitTimeInMillis - pickupWaitMs).coerceAtLeast(0)
                // No total_price: the backend prices the trip and ignores the
                // app's figure — see RequestOrderFinish.total_price.
                val requestOrderFinish = RequestOrderFinish(
                    fDistance,
                    finishLocation?.latitude,
                    finishLocation?.longitude,
                    null,
                    pickupWaitMs.toString(),
                    currentTimeInMillis.toString(),
                    null,
                    calculatedBonusFinal,
                    calculatedPromo,
                    finishLocation?.accuracy,
                    waiting_time_ontheway = onWayWaitMs.toString()
                )

                // waitLine / servicesRow are the very values rendered above in this dialog.
                orderFinish(requestOrderFinish, order, waitLine, servicesRow, totalPrice)
            }
        }
    }

    /**
     * @param waitCost     the wait row exactly as the finish dialog rendered it (server snapshot
     *                     first, local meter only as the offline fallback)
     * @param servicesCost likewise the services/podacha/extra row from that same snapshot
     *
     * @param shownTotal   the total the dialog displayed — the SERVER's own running figure. Used
     *                     only as the receipt's fallback while the settled frame is still in
     *                     flight; it is deliberately NOT posted (see RequestOrderFinish).
     *
     * Both are passed in rather than recomputed so the receipt cannot disagree with the bill the
     * driver just approved. Parameters, not fields, so a finish that never went through the dialog
     * can never inherit a previous order's numbers.
     */
    private fun orderFinish(
        requestOrderFinish: RequestOrderFinish,
        order: Order,
        waitCost: Long,
        servicesCost: Long,
        shownTotal: Long
    ) {
        // The finish receipt is a plain Dialog; a Submit tap can still be delivered after the view
        // is torn down (dialog is dismissed in onDestroyView, but an already-queued click fires).
        // Bail before touching viewLifecycleOwner, which throws "getView() is null" once view==null.
        if (!isAdded || view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.orderFinish(order.id.toString(), requestOrderFinish).collect {
                when (it) {
                    is Resource.Loading -> {
                        // From here on the dialog shows the SUBMITTED bill — freeze the
                        // live-refresh so a late fare frame can't repaint a different total
                        // under the spinner.
                        finishSubmitInFlight = true
                        // In-button spinner — keep the receipt visible instead of
                        // blanking the whole dialog to a centred ProgressBar.
                        dialogTrackingFinishBinding.apply {
                            pbSubmit.visibility = View.VISIBLE
                            tvSubmit.visibility = View.INVISIBLE
                            mcvSubmit.isEnabled = false
                            mcvCancel.isEnabled = false
                        }
                    }

                    is Resource.Success -> {
                        UserManager.saveUser(it.data?.data!!)
                        // Capture the receipt from the SUBMITTED values (so it
                        // foots to exactly what the server was sent) BEFORE the
                        // tracking teardown clears the fare fields.
                        //
                        // The wait/services rows are the ones the DIALOG just showed, passed in
                        // rather than re-derived: this screen used to rebuild them from the local
                        // meters while the dialog decomposed the same gross server-first, so one
                        // screen later the driver saw a different split of an identical total
                        // (observed on order 77704: 8 700 / 1 300 in the dialog, 9 000 / 1 000 here).
                        //
                        // GROSS prefers the SERVER-judged bill from the `order_completed`
                        // settlement frame when it has already landed (it usually races this
                        // very REST response — wire: 79688). The dialog shows the server's
                        // LIVE running total; the settled frame still wins because the server
                        // re-judges at complete (typically DOWN to the agreed price when the
                        // driver finishes at B, or up on a late final point). TripFinishFragment
                        // also observes the frame for the late-arrival case. The > 0 gates keep
                        // a lenient-Gson zero-coerced field ("" / null price — the malformation
                        // class AppGson exists for) from beating the correct submitted figure.
                        // Frame distance is the TRACKED km (wire 79688: 7.68 vs planned 4.17).
                        val settledFrame = MySocketListener.settledOrderFor(order.id)
                        val gross = settledFrame?.price?.takeIf { p -> p > 0 }?.toLong()
                            ?: shownTotal
                        MetaEvents.logTripCompleted(requireContext(), gross)
                        val receipt = TripReceipt(
                            order = order,
                            distanceKm = settledFrame?.distance?.takeIf { d -> d > 0.0 }
                                ?.let { "%.2f".format(Locale.US, it) }
                                ?: requestOrderFinish.distance,
                            grossTotal = gross,
                            // Clamped: a breakdown whose parts exceed the total is a bug, but a
                            // NEGATIVE ride line on the driver's receipt is worse than a 0 one.
                            rideCost = (gross - waitCost - servicesCost).coerceAtLeast(0L),
                            waitCost = waitCost,
                            servicesCost = servicesCost,
                            // Display the TOTAL wait (pickup + on-route) — waiting_time alone is
                            // now pickup-only after the wire-contract split.
                            waitMs = (requestOrderFinish.waiting_time.toLongOrNull() ?: 0L) +
                                    (requestOrderFinish.waiting_time_ontheway.toLongOrNull() ?: 0L),
                            durationMs = requestOrderFinish.execution_time.toLongOrNull() ?: 0L,
                            // Prefer the SERVER-applied discounts from the settlement frame —
                            // the submitted ones were computed off the previewed total, which
                            // the settled bill can differ from in either direction.
                            bonus = settledFrame?.bonusPayment ?: requestOrderFinish.bonus_payment,
                            promo = settledFrame?.promoCodePayment
                                ?: requestOrderFinish.promo_code_payment,
                            completedAt = System.currentTimeMillis(),
                            // Only the frame makes these numbers the BILL. Without it the receipt
                            // holds the previewed total the driver approved, and TripFinishFragment
                            // waits for the truth (frame or history) rather than presenting it as
                            // final — see TripReceipt.settled.
                            settled = settledFrame != null
                        )

                        // The trip is over — the frozen meter is never resumed, just disarmed so
                        // the next order inherits neither a stale "owes a resume" flag nor a
                        // suppressed auto-wait.
                        waitPausedForFinish = false
                        finishSubmitInFlight = false
                        MyTrackingService.finishBillOpen = false
                        commandStopTracking()
                        stopRouteService()
                        dialogTrackingFinish?.dismiss()
                        // Latch the id BEFORE dropping the prompts: commandStopTracking is an Intent,
                        // so a last GPS batch can still land between here and onDestroyView and the
                        // driver is standing right on the drop-off — without the latch that fix
                        // re-opened "Oxirgi manzil!" on top of the receipt screen.
                        endedOrderId = order?.id
                        dismissTripPrompts()

                        binding.includeDialog.progressBar.visibility = View.GONE
                        binding.includeDialog.content.visibility = View.VISIBLE

                        destinationLocation = null
                        mapObjectsDestinationPoint.clear()
                        mapObjectsPolyline.clear()

                        // Trip done: hide the detail through the funnel so the mini-map is stopped and
                        // the sheet isn't left VISIBLE (the one teardown path that otherwise bypasses
                        // setTripDetailVisible) before navigating to the receipt screen.
                        isTripMinimized = false
                        setTripDetailVisible(false)

                        safeNavigate(
                            R.id.action_mapFragment_to_tripFinishFragment,
                            bundleOf(TripFinishFragment.ARG_RECEIPT to receipt)
                        )
                    }

                    is Resource.Error -> {
                        // Submit failed — the dialog is interactive again, so the live-refresh
                        // may resume (the driver will re-approve whatever is on screen).
                        finishSubmitInFlight = false
                        dialogTrackingFinishBinding.apply {
                            pbSubmit.visibility = View.GONE
                            tvSubmit.visibility = View.VISIBLE
                            mcvSubmit.isEnabled = true
                            mcvCancel.isEnabled = true
                        }
                        // A fare frame that landed DURING the failed POST was applied to the
                        // member fields (applyServerFare still runs) but its dialog repaint was
                        // suppressed by the in-flight latch — and LiveData won't replay it.
                        // Without this, a retry re-posts the pre-submit total: the 79688 stale
                        // bill recreated on the retry path.
                        refreshFinishDialogIfShowing()

                        // The finish flow ships the RAW error body in message — it may be
                        // plain text (proxy/HTML error page) or null; never parse unguarded.
                        val errorResponse = try {
                            gson.fromJson(it.message, ErrorResponse::class.java)
                        } catch (parse: Exception) {
                            null
                        }
                        if (errorResponse?.status == 402) {
                            showDialogPaymentError(
                                errorResponse.message ?: Helper.getUnexpectedError()
                            )
                        } else {
                            try {
                                val error =
                                    gson.fromJson(it.message, ErrorOrderFinishResponse::class.java)
                                showToast(error.message ?: Helper.getUnexpectedError())

                                clientTotalBonus = error.data.clientTotalBonus
                                maxAmount = error.data.clientBonusSettings?.maxAmount
                                minAmount = error.data.clientBonusSettings?.minAmount

                                setViewDialogTrackingFinish(order)
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDialogPaymentError(message: String) {
        if (dialogPaymentError?.isShowing == true) return
        dialogPaymentError = Dialog(requireContext())
        _dialogPaymentErrorBinding = DialogPaymentErrorBinding.inflate(layoutInflater)
        dialogPaymentError?.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogPaymentErrorBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Dismiss only via the dialog's own buttons — block back + outside tap.
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        dialogPaymentErrorBinding.apply {
            tvInfo.text = message

            mcvBack.setOnClickListener {
                dialogPaymentError?.dismiss()
            }
        }

        dialogPaymentError?.show()
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }

    // Send command to service
    private var minFreeDistance: Int? = null

    private var additionalPrice: Int? = null
    private var startingPrice: Int? = null
    private var priceInCity: Int? = null
    private var priceOutCity: Int? = null

    private var minWaitingTime: Int? = null
    private var priceOfWaiting: Int? = null
    private var minWaitingTimeOnWay: Int? = null
    private var priceOfWaitingOnWay: Int? = null
    private var totalWaitingPrice = 0f

    private var totalServicePrice: Long = 0L

    private var totalTrackingPriceInCity = 0f
    private var distanceInCity = 0L
    private var totalTrackingPriceOutCity = 0f
    private var distanceOutCity = 0L

    private var polygonCity: ArrayList<LatLng>? = null

    private var currentTimeInMillis = 0L
    private var currentWaitTimeInMillis = 0L

    private fun commandStartService() =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_START_SERVICE
            requireContext().startService(it)
        }

    private fun commandStopService() =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_STOP_SERVICE
            requireContext().startService(it)
        }

    private fun commandStartTracking(order: Order) {
        val polygonString = order.branch.polygon?.boundary
        if (polygonString != null) {
            polygonCity = Helper.convertPolygon(polygonString)
        }

        minFreeDistance = order.tariff.minDistance ?: 0

        additionalPrice = order.addPrice ?: 0
        startingPrice = order.startingPrice
        priceInCity = order.priceInCity
        priceOutCity = order.tariff.priceOfOut.toInt()

        minWaitingTime = order.tariff.minWaitTime.toInt()
        priceOfWaiting = order.tariff.priceOfWaiting.toInt()
        minWaitingTimeOnWay = order.tariff.minWaitTimeOnWay ?: 0
        // Server fallback rule (ORDER_COMPLETE_WAITING.md §2): a NULL **or ≤0** on-way rate
        // means "not set" — fall back to the pickup rate, exactly like the backend does.
        priceOfWaitingOnWay =
            order.tariff.priceOfWaitingOnWay?.takeIf { it > 0 }
                ?: order.tariff.priceOfWaiting.toInt()

        // Same slim-payload guard as the refresh path: only recompute when the array is present.
        order.services?.let { services ->
            totalServicePrice = 0L
            services.forEach { service ->
                totalServicePrice += service.total
            }
        }

        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_START_TRACKING
            it.putExtra("order_id", order.id)
            // Taximeter/street-hail (driver-created meter): the passenger is already aboard when the
            // driver creates the order, so there is no drive-to-pickup leg to exclude — the trip is
            // "gone" from the moment tracking starts. The service uses this to open the point gate
            // (rideHasGone) immediately; without it a taximeter order uploads NO track and the
            // backend bills ~0 distance (reported on order 78261).
            it.putExtra("is_taximeter", order.from == ORDER_CREATED_DRIVER)
            // Server-known state: on a FRESH-prefs resume (reinstall / second phone) the service
            // can't derive that the order is already ARRIVED/GONE — the per-point state stamp
            // would label the rest of a live trip as pre-Go and the server would bill it ~0.
            it.putExtra("order_state", order.state)
            requireContext().startService(it)
        }

        observerMyTrackingService()
    }

    /**
     * Arm GPS-track upload the moment the order is ACCEPTED (state 2), so order-gps/batch carries
     * the driver→pickup approach leg from acceptance — not only from Boshlash. `with_timer=false`
     * tells the service to turn tracking on WITHOUT starting the execution-time timer or the local
     * distance meter; those still begin at Boshlash (commandStartTracking, with_timer default true).
     * The service ignores a repeat once tracking is already on, so the poll can call this freely.
     */
    private fun commandArmGpsFromAccept(order: Order) {
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_START_TRACKING
            it.putExtra("order_id", order.id)
            it.putExtra("is_taximeter", order.from == ORDER_CREATED_DRIVER)
            it.putExtra("with_timer", false)
            it.putExtra("order_state", order.state)
            requireContext().startService(it)
        }
        observerMyTrackingService()
    }

    private fun commandStopTracking() =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_STOP_TRACKING
            requireContext().startService(it)
        }

    private fun commandStartWay(orderId: Int) =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_START_WAY
            // Scopes the Go to ITS order so a delayed ack can't relabel a newer order as GONE.
            it.putExtra("order_id", orderId)
            requireContext().startService(it)
        }

    // Route Service
    private fun startRouteService(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        Intent(requireContext(), RouteNavControllerService::class.java).also { intent ->
            intent.putExtra("lat1", lat1)
            intent.putExtra("lon1", lon1)
            intent.putExtra("lat2", lat2)
            intent.putExtra("lon2", lon2)
            requireContext().startService(intent)
        }

//    private fun startRouteService(reDroveRoute: Boolean, route: DirectionLocations.Route) =
//        Intent(requireContext(), RouteNavControllerService::class.java).also { intent ->
//            intent.putExtra("reDroveRoute", true)
//            intent.putExtra("route", route)
//            requireContext().startService(intent)
//        }

    private fun stopRouteService() =
        Intent(requireContext(), RouteNavControllerService::class.java).also {
            requireContext().stopService(it)
        }

    private fun observerMyTrackingService() {
        // Register at most once per view — re-entry (commandStartTracking fired again on every
        // resume / getActiveMyOrders during a live trip) would otherwise stack duplicate
        // viewLifecycleOwner observers and double-count price/distance on every tick.
        if (trackingObserversRegistered) return
        trackingObserversRegistered = true

        // Server-side fare push (per MOBILE.md §6). Updates display whenever the
        // server replies to a batch — covers both the live-price ack and the
        // socket `order_price_updated` push.
        GpsBatchUploader.latestFare.observe(viewLifecycleOwner) { fare ->
            if (fare == null) {
                serverPrice = null
                serverDistanceKm = null
                serverWaitCost = null
                // This flag describes the fare we just cleared (set from `fare.live?.price != null`
                // in applyServerFare), so it has to be cleared with it. Left stale at `true` it
                // makes the finish breakdown take the "server total already contains the extras"
                // branch while there is no server total at all.
                serverPriceIncludesExtras = false
                latestLive = null
                estimateExtras = null
                serverFareOrderId = null
                return@observe
            }
            applyServerFare(fare)
            applyServerFareToTrackingViews()
            // The finish dialog snapshots its total at build time — a batch ack landing while
            // it is up (the final point's frame, typically) must repaint it, or the driver
            // approves yesterday's meter (order 79688: showed 39 000, server billed 40 000).
            refreshFinishDialogIfShowing()
            // §7.4 `services_changed`: the client toggled a service mid-order. The live block
            // above already repriced the hero; ALSO re-pull the order so the service NAME list
            // in the trip sheet updates. After Go the pre-Go 10s poll is off, so this event is
            // the only prompt signal for the list.
            if (fare.events?.contains("services_changed") == true && fare !== lastServicesChangedFare) {
                lastServicesChangedFare = fare
                getActiveMyOrders()
            }
        }

        MyTrackingService.timeTrackInMillis.observe(viewLifecycleOwner) {
            // currentTimeInMillis stays the TOTAL (sent as execution_time — the backend caps
            // waits against it). The DISPLAYED clock starts at Go/"Kettik" (boss spec: the
            // order starts with the client), so pre-Go it shows 00:00:00.
            currentTimeInMillis = it

            val displayMs = if (order?.state == ORDER_STATE_CHANGED_GONE) {
                (it - MyTrackingService.trackTimeAtGoneMs).coerceAtLeast(0L)
            } else {
                0L
            }
            val formattedTrackTime = Helper.formatTime(displayMs)
            binding.includeDialog.tvTrackTime.text = formattedTrackTime
            binding.includeDialog.tvTripTime.text = formattedTrackTime
        }

        MyTrackingService.timeWaitInMillis.observe(viewLifecycleOwner) {
            currentWaitTimeInMillis = it
            updateWaitMeterDisplay(it)

            // A wait tick can land before the order + tariff fields are loaded (view recreated while
            // the service keeps ticking on a START_STICKY resume) — no-op until the fare inputs
            // exist rather than NPE on the !! derefs below. Once loaded, the math is unchanged.
            val o = order ?: return@observe
            val minWait = minWaitingTime ?: return@observe
            val priceWait = priceOfWaiting ?: return@observe

            val seconds = TimeUnit.MILLISECONDS.toSeconds(it)

            // Wait pricing ticks per WHOLE minute (integer division) — the price jumps by the
            // per-minute rate once a full paid minute completes, it doesn't creep per second.
            if (MIN_WAITING_TIME_SINGLE) {
                if (seconds > minWait) {
                    totalWaitingPrice = ((seconds - minWait) / 60 * priceWait).toFloat()
                }
            } else {
                when (o.state) {
                    ORDER_STATE_CHANGED_ARRIVED -> {
                        if (seconds > minWait) {
                            totalWaitingPrice =
                                ((seconds - minWait) / 60 * priceWait).toFloat()
                        }
                    }

                    ORDER_STATE_CHANGED_GONE -> {
                        val minWaitOnWay = minWaitingTimeOnWay ?: return@observe
                        val priceWaitOnWay = priceOfWaitingOnWay ?: return@observe

                        val secondsUntilGone =
                            TimeUnit.MILLISECONDS.toSeconds(MyTrackingService.waitedTimeUntilGone)

                        val secondOnWay = seconds - secondsUntilGone
                        var waitingPriceOnWay = 0f
                        if (secondOnWay > minWaitOnWay) {
                            waitingPriceOnWay =
                                ((secondOnWay - minWaitOnWay) / 60 * priceWaitOnWay).toFloat()
                        }

                        var waitingPriceUntilGone = 0f
                        if (secondsUntilGone > minWait) {
                            waitingPriceUntilGone =
                                ((secondsUntilGone - minWait) / 60 * priceWait).toFloat()
                        }

                        totalWaitingPrice = waitingPriceOnWay + waitingPriceUntilGone
                    }
                }
            }

            calculateTotalPrice()
        }

        // Auto-start-ride now lives in MyTrackingService (works in the background too) — the
        // service fires orderGo and posts rideAutoStartedBySpeed; this fragment just reacts
        // to that signal (observer below).

        MyTrackingService.trackLocations.observe(viewLifecycleOwner) {
            if (MyTrackingService.isTracking.value == true) {
                val lastSegment = it.lastOrNull() ?: return@observe
                // Same cold-reopen race as the wait observer — skip the tick until the tariff price
                // fields are populated instead of NPE-ing on the !! derefs below.
                val minFree = minFreeDistance ?: return@observe
                val inCity = priceInCity ?: return@observe
                val outCity = priceOutCity ?: return@observe
                val distances = Helper.calculateTrackLocations(
                    Helper.filterTrackLocations(lastSegment),
                    polygonCity
                )

                distanceInCity = distances.inCity.toLong()
                distanceOutCity = distances.outCity.toLong()

                // Server distance is the single billing source of truth (it's what
                // the price and the receipt use, computed from the ride start).
                // Only show the LOCAL tracked distance until the first server fare
                // lands — otherwise the two writers (this observer + the fare push)
                // fight and the displayed "Yurilgan yo'l" flickers between values.
                if (serverDistanceKm == null) {
                    binding.includeDialog.tvDistance.text =
                        "${Helper.metreToRoundKm((distanceInCity + distanceOutCity).toString())}${
                            getString(R.string.km)
                        }"
                }

                // Calculate in city
//                if (distanceInCity > minFreeDistance!!) {
//                    totalTrackingPriceInCity = ((distanceInCity - minFreeDistance!!) / 1000f) * priceInCity!!
//                }

//                val distanceIntervals = arrayListOf(
//                    Order.Tariff.DistanceInterval(1, 0, 1000, 200000),
//                    Order.Tariff.DistanceInterval(2, 2000, 3000,100000),
//                    Order.Tariff.DistanceInterval(3, 3000, 4000, 50000)
//                )

                val distanceIntervals = order?.tariff?.distanceIntervals ?: emptyList()

                val remainingDistance = distanceInCity - minFree
                if (remainingDistance > 0) {
                    totalTrackingPriceInCity = Helper.calculatePriceWithDistanceIntervals(
                        remainingDistance,
                        distanceIntervals,
                        inCity
                    )
                }

                // Calculate out city
                totalTrackingPriceOutCity = (distanceOutCity / 1000f) * outCity

                calculateTotalPrice()

                // New feature that determine come to destination
                val latestSegment = it.lastOrNull()
                if (destinationLocation != null && latestSegment != null && latestSegment.isNotEmpty()
                    && order?.state == ORDER_STATE_CHANGED_GONE
                ) {
                    val location = latestSegment.last()

                    val distanceTillDestination: Float = Helper.calculateBetweenTwoPoints(
                        LatLng(
                            destinationLocation!!.latitude,
                            destinationLocation!!.longitude
                        ), LatLng(location.latitude, location.longitude)
                    )

                    val locations = order?.locations ?: return@observe
                    // The final-stop prompt is judged on the LAST stop, not on the current target,
                    // so it must be offered on every fix — not only when the driver happens to be
                    // near the stop the app is currently routing to. Above the destPos guard on
                    // purpose: a cold reopen can leave destinationPosition null, and that must not
                    // cost the driver his finish prompt. Self-gating (radius, cooldown,
                    // already-showing, mid-stop dialog up), so this call site is free.
                    maybeShowFinishPrompt()
                    // destinationPosition is a service field left null after a cold reopen for a
                    // single-point order at GONE state — guard instead of !! (the reachable NPE).
                    val destPos = MyTrackingService.destinationPosition ?: return@observe
                    if (distanceTillDestination < 100 && locations.size - 1 > destPos) {
                        // Reached the current drop-off and MORE stops remain. Advancing is a
                        // CONFIRMED action now (not silent): the 10 s checkpoint prompt
                        // (startCheckpointPrompt) asks "go to the next stop?" — it also works
                        // while the driver is STOPPED at the stop, when this GPS observer freezes.
                        // Fire it promptly here too so it can appear without waiting a full tick;
                        // maybeShowCheckpointPrompt self-gates (range, prefs-suppression, already
                        // showing, final stop reached), so this and the timer can't double-show.
                        maybeShowCheckpointPrompt()
                    }
                }
            }
        }
    }

    // Old method
//    private fun calculateTotalPrice() {
//        val totalPrice = startingPrice!! + additionalPrice!! + totalServicePrice + totalWaitingPrice.toLong() + totalTrackingPriceInCity.toLong() + totalTrackingPriceOutCity.toLong()
//        val roundTotalPrice = Helper.roundPrice(totalPrice)
//        binding.includeDialog.tvTotalPrice.text = "${Helper.formatPrice(roundTotalPrice)}${getString(R.string.sum)}"
//    }

    // New method
    private fun calculateTotalPrice() {
        // B-order: the visible fare-card hero (tvPrice) shows agreed + mid-trip service
        // delta (updateAgreedHeroPrice); the hidden tvTotalPrice mirrors the same figure so
        // the two writers (this tick path and applyServerFareToTrackingViews) never
        // flip-flop. The final amount is recalculated at completion in
        // setViewDialogTrackingFinish.
        val o = order
        if (o != null && o.locations.size >= 2 && o.price > 0) {
            // Same composition as the visible hero — see updateAgreedHeroPrice for why the
            // service delta is added locally. These two must never disagree; they are the
            // same number written by two paths.
            binding.includeDialog.tvTotalPrice.text =
                "${
                    Helper.formatPrice(
                        Helper.roundPrice(o.price.toLong() + agreedServiceDelta())
                    )
                }${getString(R.string.sum)}"
            updateAgreedHeroPrice()
            return
        }
        // Server fare wins when available (MOBILE.md §1). Local calc below is
        // the optimistic-UI fallback for the very first seconds of a trip and
        // for offline mode.
        val price = serverPrice
        if (price != null) {
            val text = "${Helper.formatPrice(price.toString())}${getString(R.string.sum)}"
            binding.includeDialog.tvTotalPrice.text = text
            // No-B hero composes its own total: a top-level `price` (not `live.price`) carries no
            // services, and painting it bare dropped them off the driver's number.
            updateBlessHeroPrice()
            return
        }

        if (order!!.locations.size < 2) {
            calculateTotalPrice1()
        } else {
            val distanceTracked = distanceInCity + distanceOutCity
            val distanceBetweenLocations = order!!.distance * 1000
            if ((distanceTracked - distanceBetweenLocations) > 1000) {
                calculateTotalPrice1()
            } else {
                calculateTotalPrice2()
            }
        }
    }

    /**
     * Pushes the latest server fare into the live tracking dialog views.
     * Called from the [GpsBatchUploader.latestFare] observer — no-ops if the
     * tracking UI isn't bound yet (e.g. price push arrived before the dialog
     * inflated).
     */
    private fun applyServerFareToTrackingViews() {
        if (_binding == null) return
        val o = order
        if (o != null && o.locations.size >= 2 && o.price > 0) {
            // Agreed-price order: the agreed price plus any service the rider added mid-trip.
            // See updateAgreedHeroPrice — a mid-trip toggle moves neither `order.price` nor
            // `kept_projection`, so the delta has to be composed here or the driver's number
            // never changes.
            binding.includeDialog.tvTotalPrice.text =
                "${
                    Helper.formatPrice(
                        Helper.roundPrice(o.price.toLong() + agreedServiceDelta())
                    )
                }${getString(R.string.sum)}"
            updateAgreedHeroPrice()
            updateAgreedSurchargeHint()
        } else {
            val price = serverPrice ?: return
            val text = "${Helper.formatPrice(price.toString())}${getString(R.string.sum)}"
            binding.includeDialog.tvTotalPrice.text = text
            updateBlessHeroPrice()
        }

        serverDistanceKm?.let { km ->
            binding.includeDialog.tvDistance.text =
                "${"%.2f".format(Locale.US, km)}${getString(R.string.km)}"
        }
    }

    /**
     * B-orders only: repaint the VISIBLE fare-card hero (tvPrice) with the agreed price plus
     * the mid-trip service delta. tvTotalPrice (written above) sits in llTotalPrice, which is
     * GONE in every trip state — tvPrice is the number the driver actually sees, and setView
     * seeds it with bare order.price and re-runs on every services_changed refetch, which
     * would clobber the delta back off without this.
     */
    private fun updateAgreedHeroPrice() {
        val o = order ?: return
        if (o.locations.size < 2 || o.price <= 0) return
        val b = _binding ?: return
        // The hero is a LIVE preview of what the passenger will owe, not a frozen quote: the
        // agreement covers the ride, but a mid-trip service change and billed waiting are on
        // top of it and the driver collects them too. Keeping the number pinned at the booking
        // price (with the extras relegated to the footnote) meant the sheet and the final bill
        // disagreed for the whole trip. `surcharge` is the server's own waiting charge, so this
        // stays in step with what the backend will settle.
        //
        // `agreedServiceDelta()` is LOAD-BEARING — do not remove it again. It was removed
        // once, on the reasoning that the server absorbs a service into the agreed price.
        // That is true of the PRE-BOOKING quote — `order-new/calculate` returns 25 000 with
        // the woman-driver service and 15 000 with it excluded, same route, same second — but
        // it is NOT true of a MID-TRIP `order/item` toggle: there neither `order.price` nor
        // `kept_projection` moves. Quoting the server alone therefore froze the driver's
        // number while the rider was being charged more. Reported from the field.
        val surcharge = latestLive?.surcharge?.roundToLong() ?: 0L
        val text = "${
            Helper.formatPrice(
                Helper.roundPrice(o.price.toLong() + agreedServiceDelta() + surcharge)
            )
        }${getString(R.string.sum)}"
        // Keep setView's "~" marker on surged orders.
        b.includeDialog.tvPrice.text = if ((o.addPrice ?: 0) <= 0) text else "~$text"
    }

    /**
     * B-point orders: the footnote explains how much of the hero is billed waiting. It no
     * longer quotes a separate projection — the hero itself now carries the surcharge, so a
     * second total underneath was just the same number twice.
     */
    private fun updateAgreedSurchargeHint() {
        val b = _binding ?: return
        val surcharge = latestLive?.surcharge?.roundToLong() ?: 0L
        b.includeDialog.tvPriceHint.text = if (surcharge > 0) {
            getString(
                R.string.waiting_included_hint,
                "${Helper.formatPrice(surcharge.toString())}${getString(R.string.sum)}"
            )
        } else {
            getString(R.string.agreed_price_hint)
        }
    }

    /** No-B (taximeter) orders only: mirror the live running total into the fare-card HERO
     *  (tvPrice) — there is no agreed price to freeze, so the driver watches the meter.
     *  Used by the OFFLINE meter path ([calculateTotalPrice1]), whose text already carries
     *  services + waiting + tracking. Everything server-driven goes through
     *  [updateBlessHeroPrice] instead, which composes the extras itself. */
    private fun updateLiveHeroPrice(totalText: String) {
        val o = order ?: return
        if (o.locations.size >= 2) return
        binding.includeDialog.tvPrice.text = totalText
    }

    /**
     * Services on this order, read off the FRESH payload rather than [totalServicePrice] — that
     * field is only recomputed in commandStartTracking() / prepareOrderFinish(), so before the trip
     * starts it still holds the previous value. Same read the services_changed path uses.
     */
    private fun orderServicesTotal(o: Order): Long =
        o.services?.sumOf { it.total.toLong() } ?: totalServicePrice

    /**
     * Running total for a no-B (taximeter / street-hail) order, in the SAME composition the finish
     * breakdown already uses for these orders (see setViewDialogTrackingFinish):
     *
     *  - `live.price` is `fare + services + podacha + extra + waiting` → taken as-is;
     *  - the legacy top-level `price` does NOT carry services (backend bug 76967) → extras added;
     *  - before any fare frame there is only `order.price`, the starting price → extras added.
     *
     * That last branch is the one that was missing. On an accepted no-B order nothing recomputed
     * the hero until the first GPS-batch fare landed, so a service the client added after booking
     * stayed out of the number the driver reads: reported as an 8 000 hero on an order carrying a
     * 10 000 AC, which should have read 18 000. The service chips updated instantly because they
     * come straight off `order.services`; only the price was left behind.
     */
    private fun blessHeroTotal(o: Order): Long {
        val extras = orderServicesTotal(o) + (additionalPrice?.toLong() ?: 0L)
        val sp = serverPrice
        return when {
            sp != null && serverPriceIncludesExtras -> Helper.roundPrice(sp).toLong()
            sp != null -> Helper.roundPrice(sp + extras).toLong()
            else -> Helper.roundPrice(o.price.toLong() + extras).toLong()
        }
    }

    /**
     * No-B twin of [updateAgreedHeroPrice]: repaint the visible hero (and the hidden tvTotalPrice
     * mirror) for a taximeter order. Self-gating on order shape, so callers that serve both shapes
     * can call it unconditionally. Safe to call in ANY order state — before the meter runs it
     * simply reads `order.price` as the base.
     */
    private fun updateBlessHeroPrice() {
        val o = order ?: return
        if (o.locations.size >= 2) return
        val b = _binding ?: return
        val text = "${
            Helper.formatPrice(Helper.roundPrice(blessHeroTotal(o)))
        }${getString(R.string.sum)}"
        b.includeDialog.tvPrice.text = text
        b.includeDialog.tvTotalPrice.text = text
    }

    private fun calculateTotalPrice1() {
        val totalPrice =
            startingPrice!! + additionalPrice!! + totalServicePrice + totalWaitingPrice.toLong() + totalTrackingPriceInCity.toLong() + totalTrackingPriceOutCity.toLong()
        val roundTotalPrice = Helper.roundPrice(totalPrice)
        val text = "${Helper.formatPrice(roundTotalPrice)}${getString(R.string.sum)}"
        binding.includeDialog.tvTotalPrice.text = text
        updateLiveHeroPrice(text)
    }

    private fun calculateTotalPrice2() {
        val totalPrice = totalWaitingPrice.toLong() + order!!.price.toLong()
        val roundTotalPrice = Helper.roundPrice(totalPrice)
        binding.includeDialog.tvTotalPrice.text =
            "${Helper.formatPrice(roundTotalPrice)}${getString(R.string.sum)}"
    }

    private var _dialogArrivedToClientBinding: DialogArrivedToClientBinding? = null
    private val dialogArrivedToClientBinding get() = _dialogArrivedToClientBinding!!
    private var dialogArrivedToClient: Dialog? = null
    private fun showDialogArrivedToClient() {
        if (dialogArrivedToClient?.isShowing == true) return
        dialogArrivedToClient = Dialog(requireContext())
        _dialogArrivedToClientBinding = DialogArrivedToClientBinding.inflate(layoutInflater)
        dialogArrivedToClient!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogArrivedToClientBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            // The two weighted (0dp) Back/Submit buttons collapse to a sliver at
            // wrap_content width. Pin to 90% of the screen.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogArrivedToClientBinding.cvArrived.setDebouncedClickListener {
            orderArrive(order!!.id)

            dialogArrivedToClient?.dismiss()
            _dialogArrivedToClientBinding = null
            dialogArrivedToClient = null
        }

        dialogArrivedToClientBinding.cvCancel.setOnClickListener {
            dialogArrivedToClient?.dismiss()
            _dialogArrivedToClientBinding = null
            dialogArrivedToClient = null
        }

        dialogArrivedToClient?.show()
    }

    private var _dialogPassingNextDestinationBinding: DialogPassingNextDestinationBinding? = null
    private val dialogPassingNextDestinationBinding get() = _dialogPassingNextDestinationBinding!!
    private var dialogPassingNextDestination: Dialog? = null
    private fun showDialogPassingNextDestination(locations: List<Order.Location>) {
        if (dialogPassingNextDestination?.isShowing == true) return
        dialogPassingNextDestination = Dialog(requireContext())
        _dialogPassingNextDestinationBinding =
            DialogPassingNextDestinationBinding.inflate(layoutInflater)
        dialogPassingNextDestination!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogPassingNextDestinationBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            // The two weighted (0dp) Back/Passing buttons collapse to a sliver at
            // wrap_content width. Pin to 90% of the screen.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }


        dialogPassingNextDestinationBinding.cvPassing.setDebouncedClickListener {
            // Manual fallback — share the exact same advance logic as auto-advance (atomic
            // position+location, cap at last stop, latch reset, multi-leg rebuild).
            advanceDestination(locations)
        }

        dialogPassingNextDestinationBinding.cvCancel.setOnClickListener {
            // "No" — keep the current full route (route stays targeting this stop) and remember the
            // choice for this stop so the 10 s prompt stops nagging. The driver can still advance
            // later via the map-button stops picker.
            order?.let { o ->
                MyTrackingService.destinationPosition?.let { pos -> suppressCheckpoint(o.id, pos) }
            }
            dialogPassingNextDestination?.dismiss()
            _dialogPassingNextDestinationBinding = null
            dialogPassingNextDestination = null
        }

        dialogPassingNextDestination?.show()
    }

    /**
     * Advance the active order to the NEXT stop — the single funnel for BOTH the auto-advance
     * (proximity / "clearly passed" heuristic) and the manual dialog tap. It moves
     * destinationPosition + destinationLocation together (so the finish/proximity gate never sees a
     * mismatched pair and can't fire finish at the wrong stop), CAPS at the last stop (never
     * over-increments past the final drop-off — unlike the old raw `+1`), resets the per-leg
     * closest-approach latch, and rebuilds the MULTI-leg route through tripWaypoints with the NEW
     * position, so the drawn + external route drop the passed waypoint (driver→C→D for 4+ stops).
     */
    private fun advanceDestination(locations: List<Order.Location>) {
        val nextPosition = (MyTrackingService.destinationPosition ?: 0) + 1
        val maxPos = locations.maxOfOrNull { it.position } ?: return
        if (nextPosition > maxPos) return
        MyTrackingService.destinationPosition = nextPosition
        // New leg target — the driver has not reached THIS stop yet, so the pass-by latch starts over.
        reachedFinalDestination = false
        finalDestinationPassed = false
        val loc = locations.firstOrNull { it.position == nextPosition }
            ?: locations.lastOrNull() ?: return
        destinationLocation = Point(loc.latitude, loc.longitude)
        _binding?.includeDialog?.tvFinishAddress?.text =
            loc.name.ifEmpty { getString(R.string.not_showed) }
        mapObjectsDestinationPoint.clear()
        setMarkerDestinationPoint(destinationLocation!!.latitude, destinationLocation!!.longitude)
        routePath.clear()
        currentLocation?.let { rebuildActiveRoute(it) }
        dialogPassingNextDestination?.dismiss()
        _dialogPassingNextDestinationBinding = null
        dialogPassingNextDestination = null
    }

    // --- Multi-stop checkpoint prompt (10 s) --------------------------------------------------
    // A separate 10 s loop drives the "reached the stop — go to the next one?" prompt because the
    // trackLocations observer FREEZES while the driver is stopped/waiting at the stop (addPathPoint
    // is gated on !isWaiting), which is exactly when we need to ask. It reads the app-wide last fix
    // (lastLatLngWholeApp, updated independently of tracking) so it works even while parked.
    private var checkpointPromptJob: kotlinx.coroutines.Job? = null
    private val checkpointPrefs
        get() = requireContext().getSharedPreferences("checkpoint_prompt", Context.MODE_PRIVATE)

    private fun checkpointSuppressKey(orderId: Int, pos: Int) = "cp_no_${orderId}_$pos"

    /** The driver answered "No" for this stop → don't re-prompt every 10 s; keep the full route. */
    private fun isCheckpointSuppressed(orderId: Int, pos: Int) =
        checkpointPrefs.getBoolean(checkpointSuppressKey(orderId, pos), false)

    private fun suppressCheckpoint(orderId: Int, pos: Int) =
        checkpointPrefs.edit().putBoolean(checkpointSuppressKey(orderId, pos), true).apply()

    private fun startCheckpointPrompt() {
        if (checkpointPromptJob?.isActive == true) return
        checkpointPromptJob = viewLifecycleOwner.lifecycleScope.launch {
            // delay() is cancellable — stopCheckpointPrompt()/onStop cancels the job and the loop
            // unwinds via CancellationException, so no explicit isActive check is needed.
            while (true) {
                maybeShowCheckpointPrompt()
                // Same loop drives the final-stop prompt: the two are mutually exclusive (one
                // needs a next stop, the other needs to BE the last), so only one can fire.
                maybeShowFinishPrompt()
                kotlinx.coroutines.delay(10_000L)
            }
        }
    }

    private fun stopCheckpointPrompt() {
        checkpointPromptJob?.cancel()
        checkpointPromptJob = null
    }

    /**
     * Driver position for the two arrival prompts below.
     *
     * NOT `MyTrackingService.lastLatLngWholeApp` on its own, which is what both prompts used to read.
     * That LiveData is fed only by `locationCallBackForUpload`, whose request is
     * `interval 20 s, setMinUpdateDistanceMeters(100f)` — the OS withholds a new fix until the driver
     * has moved another 100 m, and **100 m is exactly the prompt radius**
     * ([FINISH_PROMPT_RADIUS_M]). So the last delivered fix is typically ~100 m short of the stop,
     * the driver rolls the final stretch and parks without ever producing another one, and the
     * distance check keeps failing. Reopening the app primes a one-shot fix, which is why both
     * prompts "appear immediately on reopen" — the arrival was real, the position was quantised.
     *
     * [currentLocation] comes from `updateLocationForRoute`: 1 s interval, no distance filter, not
     * gated on `isWaiting`. It keeps the property the original comment wanted (it does not freeze
     * while the driver is stopped) without being quantised to the radius being tested.
     */
    private fun promptPosition(): LatLng? =
        currentLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: MyTrackingService.lastLatLngWholeApp.value

    /**
     * Id of the order whose life is OVER — completed, cancelled by the driver, or cancelled from the
     * other side. The local [order] object is never rewritten when a trip ends (it keeps `state 9`
     * and its drop-off coordinates), so without this latch every "am I standing at the drop-off?"
     * check below still reads a live-looking order and re-opens the prompt over whatever screen came
     * next — the finish receipt included.
     *
     * Never cleared: the next order carries a different id, so the latch just stops matching.
     */
    private var endedOrderId: Int? = null

    /**
     * Tear down BOTH order-scoped map prompts and the timer that re-opens them. Every path that ends
     * an order has to call this.
     *
     * They are `setCancelable(false)` + `setCanceledOnTouchOutside(false)` modals, so one left
     * standing after the order is gone is not something the driver can tap away: "Yakunlash" on it
     * runs [prepareOrderFinish], which now finds no active order and just toasts, and the 10 s loop
     * re-opens it if he does get it closed.
     */
    private fun dismissTripPrompts() {
        stopCheckpointPrompt()

        dialogPassingNextDestination?.dismiss()
        _dialogPassingNextDestinationBinding = null
        dialogPassingNextDestination = null

        dialogFinishDestination?.dismiss()
        _dialogFinishDestinationBinding = null
        dialogFinishDestination = null
    }

    /** Show the "go to next stop?" confirmation IF: on-trip (GONE), a next stop exists, within 100 m
     *  of the current target, not already answered "No" for this stop, and not already showing.
     *  Self-gating so both the 10 s timer and the GPS observer can call it safely. */
    private fun maybeShowCheckpointPrompt() {
        // `view`, NOT `_binding`: onDestroyView deliberately keeps the binding alive so the cached
        // map view survives back-navigation, which makes a _binding null-check inert here.
        if (view == null) return
        val ord = order ?: return
        if (ord.id == endedOrderId) return
        if (ord.state != ORDER_STATE_CHANGED_GONE) return
        val locations = ord.locations
        val destPos = MyTrackingService.destinationPosition ?: return
        if (locations.size - 1 <= destPos) return                 // already on the last stop
        val dest = destinationLocation ?: return
        val here = promptPosition() ?: return
        val dist = Helper.calculateBetweenTwoPoints(
            LatLng(dest.latitude, dest.longitude),
            LatLng(here.latitude, here.longitude)
        )
        if (dist > 100f) return
        if (isCheckpointSuppressed(ord.id, destPos)) return
        if (dialogPassingNextDestination?.isShowing == true) return
        if (dialogFinishDestination?.isShowing == true) return
        // Standing at the FINAL stop: "go to the next one?" is the wrong question — the finish
        // prompt owns this spot. Only reachable when two stops are within a block of each other.
        val last = locations.maxByOrNull { it.position }
        if (last != null && Helper.calculateBetweenTwoPoints(
                LatLng(last.latitude, last.longitude),
                LatLng(here.latitude, here.longitude)
            ) <= FINISH_PROMPT_RADIUS_M
        ) return
        showDialogPassingNextDestination(locations)
    }

    private var _dialogFinishDestinationBinding: DialogFinishDestinationBinding? = null
    private val dialogFinishDestinationBinding get() = _dialogFinishDestinationBinding!!
    private var dialogFinishDestination: Dialog? = null
    private fun showDialogFinishDestination() {
        if (dialogFinishDestination?.isShowing == true) return
        dialogFinishDestination = Dialog(requireContext())
        _dialogFinishDestinationBinding = DialogFinishDestinationBinding.inflate(layoutInflater)
        dialogFinishDestination!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogFinishDestinationBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            // The two weighted (0dp) Back/Finish buttons collapse to a sliver at
            // wrap_content width. Pin to 90% of the screen.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }


        dialogFinishDestinationBinding.cvFinish.setDebouncedClickListener {
//            showDialogTrackingFinish(order!!)
            // Step aside for the bill: leaving this modal stacked under the finish sheet is what
            // made it reappear whenever the sheet was dismissed or the flow bailed out. The stamp
            // gives the same 10 s breather as "Ortga", so aborting the bill doesn't bring it
            // straight back — and if the finish succeeds, endedOrderId retires it for good.
            finishPromptDismissedAt = SystemClock.elapsedRealtime()
            dialogFinishDestination?.dismiss()
            _dialogFinishDestinationBinding = null
            dialogFinishDestination = null
            prepareOrderFinish()
        }

        dialogFinishDestinationBinding.cvCancel.setOnClickListener {
            // Stamp BEFORE dismissing so the cooldown starts at the driver's tap — see
            // maybeShowFinishPrompt. Without it the next GPS batch re-opened this instantly.
            finishPromptDismissedAt = SystemClock.elapsedRealtime()
            dialogFinishDestination?.dismiss()
            _dialogFinishDestinationBinding = null
            dialogFinishDestination = null
        }

        dialogFinishDestination?.show()
    }

    // --- "Reached the final stop — finish?" prompt (10 s, same cadence as the checkpoint one) ---
    /**
     * Monotonic stamp of the last "Ortga" tap. The prompt used to be fired straight from the
     * trackLocations observer, which ticks on every GPS batch — so dismissing it just meant it
     * reappeared a second or two later and the driver could not get back to the map. The prompt
     * still has to come back (he may simply not be ready yet), so it is a COOLDOWN, not a
     * suppression: 10 s, matching [startCheckpointPrompt].
     */
    private var finishPromptDismissedAt = 0L

    /**
     * Show the finish confirmation IF: on-trip (GONE), this IS the last stop, within 100 m of it,
     * not already showing, the finish sheet isn't up, and the cooldown has elapsed.
     *
     * Reads `lastLatLngWholeApp` rather than the tracking stream for the same reason the checkpoint
     * prompt does: the observer freezes while the driver is stopped, which is exactly when he is at
     * the drop-off. Self-gating, so the 10 s loop and the GPS observer can both call it.
     */
    private fun maybeShowFinishPrompt() {
        // `view`, NOT `_binding` — see maybeShowCheckpointPrompt.
        if (view == null) return
        val ord = order ?: return
        // The trip is over: no prompt, whatever the stale local order still says. Without this the
        // prompt reopened on top of the finish receipt / the map after a cancel.
        if (ord.id == endedOrderId) return
        // From the moment "Yakunlash" is tapped the bill flow owns the screen — and it has windows
        // with NO dialog up that the isShowing checks below sail straight through:
        // prepareOrderFinish spends up to FINISH_FARE_WAIT_MS fetching a fresh fare before the sheet
        // appears, and the submit has another gap after the sheet is dismissed.
        if (finishPrepInFlight || finishSubmitInFlight) return
        // There has to be somewhere to ARRIVE. Two order shapes have no drop-off at all:
        //  - a TAXIMETER (driver-created meter) has a single location, the spot where the meter was
        //    started, and no destination is ever set;
        //  - a B-less client order carries only its pickup.
        // In both, the `last` stop below is the point the driver is already standing on, so the
        // prompt fired the moment the trip started — reported on a taximeter sitting at A. Those
        // trips end through the Finish slider, which is always available; only a real A→B order can
        // meaningfully "reach the last stop".
        if (ord.from == ORDER_CREATED_DRIVER) return
        if (ord.locations.size < 2) return
        // Finish is offered ONLY after Go/"Kettik" (state 9). Before that the backend has no
        // stage-9 track and prices the trip at ~0 distance — the gate that fixed order 78188.
        if (ord.state != ORDER_STATE_CHANGED_GONE) return
        // Judged on the LAST stop and the driver's real position — NOT on destinationPosition.
        // It used to require `destinationPosition == last`, which a driver only reaches by tapping
        // "Ha" on the mid-stop prompt: answer "Ortga" at B once (it is then suppressed for that
        // stop) and the app still targets B while the car is parked at C, so neither prompt ever
        // came back and the final-stop popup looked broken. Where the app thinks the target is has
        // no bearing on whether the driver has physically arrived at the drop-off.
        val last = ord.locations.maxByOrNull { it.position } ?: return
        val here = promptPosition() ?: return
        val dist = Helper.calculateBetweenTwoPoints(
            LatLng(last.latitude, last.longitude),
            LatLng(here.latitude, here.longitude)
        )
        if (dist > FINISH_PROMPT_RADIUS_M) return
        if (dialogFinishDestination?.isShowing == true) return
        if (dialogTrackingFinish?.isShowing == true) return
        // At the final stop the mid-stop question is moot — don't stack the two dialogs.
        if (dialogPassingNextDestination?.isShowing == true) return
        if (SystemClock.elapsedRealtime() - finishPromptDismissedAt < FINISH_PROMPT_COOLDOWN_MS) return
        showDialogFinishDestination()
    }

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun openGpsSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    // Location call back for route and Map configurations
    private lateinit var mapKit: MapView
    private lateinit var mapObjectsCurrentPoint: MapObjectCollection
    private lateinit var mapObjectsDestinationPoint: MapObjectCollection
    private lateinit var mapObjectsPolyline: MapObjectCollection
    private var cameraZoom = 16f
    private var puckAnimator: ValueAnimator? = null

    // Home-map camera auto-follow; suspended when the driver pans/zooms by hand (set in
    // onCameraPositionChanged on GESTURES), resumed by the recenter button. Mirrors the trip map's
    // expandedFollowDriver.
    private var homeFollowDriver = true

    // Smoothed heading for the camera follow — deadbands + eases the noisy raw GPS bearing so the
    // map doesn't swivel under the puck at low speed.
    private var smoothedAzimuth = 0f

    // Get current location first time
    private fun getLastLocation() {
        if (CheckPermissions.checkLocationPermission(requireContext())) {
            if (CheckPermissions.isGPSEnabled(requireContext())) {
                fusedLocationProviderClient.lastLocation.addOnCompleteListener {
                    val location = it.result

                    if (location != null) {
                        currentLocation = location
                        selectedLocation = LatLng(location.latitude, location.longitude)

                        if (marker == null) {
                            moveCamera(currentLocation!!)
                        }
                    } else {
                        getNewLocation()
                    }
                }
            } else {
                openGpsSettings()
            }
        } else {
            openSystemSettings()
        }
    }

    private fun getNewLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .build()

        if (CheckPermissions.checkLocationPermission(requireContext())) {
            fusedLocationProviderClient.requestLocationUpdates(
                request, object : LocationCallback() {
                    override fun onLocationResult(p0: LocationResult) {
                        super.onLocationResult(p0)
                        val lastLocation: Location = p0.lastLocation!!

                        currentLocation = lastLocation
                        selectedLocation = LatLng(lastLocation.latitude, lastLocation.longitude)

                        if (marker == null) {
                            moveCamera(currentLocation!!)
                        }
                    }

                },
                Looper.myLooper()
            )

        } else {
            openSystemSettings()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationForRoute() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L).apply {
//            setMinUpdateDistanceMeters(10f)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallBackForRoute,
            Looper.getMainLooper()
        )
    }

    private val locationCallBackForRoute = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.locations.let { locations ->
                for (location in locations) {
                    if (_binding != null) {
                        // Single speed source for the main view, the metrics tile, and the
                        // expanded map — so they always read the same km/h.
                        val kmh = "${(location.speed * 3.6).toInt()}"
                        binding.tvSpeed.text = kmh
                        binding.includeDialog.tvSpeed.text = kmh
                        if (dialogTripMap?.isShowing == true)
                            _dialogTripMapBinding?.tvFullSpeed?.text = kmh
                        // Mini-map follows the driver — only when the fix actually moved (~2 m),
                        // so it doesn't re-render every GPS tick while parked or jittering.
                        if (order != null) {
                            val d = MyTrackingService.lastLatLngWholeApp.value
                            if (d != null &&
                                (Math.abs(d.latitude - lastMiniDriverLat) > 0.00002 ||
                                        Math.abs(d.longitude - lastMiniDriverLon) > 0.00002)
                            ) {
                                lastMiniDriverLat = d.latitude
                                lastMiniDriverLon = d.longitude
                                updateMiniMapDriver()
                                followMiniMapDriver()
                            }
                            // Build the route on reopen (socket clears it), then trim + reroute.
                            updateFinalDestinationPassed(location)
                            ensureActiveRoute(location)
                            trimRouteOnFix(location)
                        }
                        // Puck + camera follow on EVERY fix (not only > 5 m/s) so the home puck
                        // doesn't freeze when slow/parked; the route-redraw inside stays speed-gated.
                        mapObjectsListener(location)

                        // Expanded full-screen trip map: track the driver on EVERY fix with the FRESH
                        // GPS location (the lastLatLngWholeApp-gated update could lag/freeze), so its
                        // puck never sticks where the map was opened and properly follows the route.
                        if (dialogTripMap?.isShowing == true) {
                            val p = Point(location.latitude, location.longitude)
                            // Skip sub-5 m GPS jitter (parked / standing) so the camera doesn't keep
                            // micro-animating in place, while still tracking real movement every fix.
                            if (lastExpandedDriverLat == 0.0 ||
                                metersBetween(
                                    Point(lastExpandedDriverLat, lastExpandedDriverLon),
                                    p
                                ) >= 2.0
                            ) {
                                lastExpandedDriverLat = location.latitude
                                lastExpandedDriverLon = location.longitude
                                updateExpandedMapDriver(p)
                                if (expandedFollowDriver) followExpandedMapDriver(p)
                            }
                        }
                    }

//                    if (destinationLocation != null && order?.state == ORDER_STATE_STARTED) {
//                        val distanceTillDestination: Float = Helper.calculateBetweenTwoPoints(LatLng(destinationLocation!!.latitude, destinationLocation!!.longitude), LatLng(location.latitude, location.longitude))
//
//                        if (distanceTillDestination < 100) {
//                            if (dialogArrivedToClient == null) {
//                                showDialogArrivedToClient()
//                            }
//                        }
//                    }
                }
            }
        }
    }

    private fun mapObjectsListener(location: Location?) {
        currentLocation = location
        if (location != null) {
            if (marker != null) {
                moveMarkerToPositionWithAnimation(marker!!, location)
            } else {
                moveCamera(location)
            }
        }

        // Route is owned entirely by the modern trim/reroute system (trimRouteOnFix +
        // rebuildActiveRoute, wired in locationCallBackForRoute): while the driver stays on the
        // already-built route it only trims the passed portion LOCALLY (no network), and it reroutes
        // — debounced 5 s, single-flight, through ALL mid points from the driver's position — only
        // when the driver leaves the route by more than the tolerance, or on app reopen. The old
        // per-fix getRoute() that lived here fired an un-debounced 2-point (no mid-points) request
        // on every GPS tick and fought that system, so it's removed.
    }

//    private fun mapObjectsListener(location: Location?) {
//        currentLocation = location
//        if (location != null) {
//            if (marker != null) {
//                moveMarkerToPositionWithAnimation(marker!!, location)
//            } else {
//                moveCamera(location)
//            }
//        }
//
//        if (location != null && destinationLocation != null && routePath.isNotEmpty()) {
//            val isLocationInsideRoute = DetermineLocationInsideRoute.isLocationInsideRoute(LatLng(location.latitude, location.longitude), routePath)
//
//            if (isLocationInsideRoute) {
//                val index = findNearestLocationIndex(LatLng(location.latitude, location.longitude), routePath)?.first
//
//                if (index != null) {
//                    val remainingLocations = ArrayList<LatLng>()
//                    remainingLocations.add(LatLng(location.latitude, location.longitude))
//                    for (i in routePath.indices) {
//                        if (index < i) {
//                            remainingLocations.add(LatLng(routePath[i].latitude, routePath[i].longitude))
//                        }
//                    }
//                    viewLifecycleOwner.lifecycleScope.launch {
//                        delay(3_500L)
//                        route(remainingLocations)
//                    }
////                    route(remainingLocations)
//
//                } else {
//                    route(routePath)
//                }
//
//                Timber.d("Hello 1")
//            } else {
//                getRoute(location, destinationLocation!!, true)
//                Timber.d("Hello 2")
//            }
//        } else if (location != null && destinationLocation != null && routePath.isEmpty()) {
//            getRoute(location, destinationLocation!!)
//            Timber.d("Hello 3")
//        }
//    }

    private fun moveCamera(location: Location) {
        cameraZoom = 16f
        smoothedAzimuth = location.bearing
        setMarkerCurrentPoint(location.latitude, location.longitude)
        mapKit.map.move(
            CameraPosition(
                Point(location.latitude, location.longitude),
                cameraZoom,
                location.bearing,
                0.0f
            ), Animation(Animation.Type.SMOOTH, 0f), null
        )
    }

    /**
     * Deadband + ease the raw GPS bearing for the camera azimuth: ignore sub-8° jitter (so a noisy
     * low-speed fix doesn't spin the map) and ease toward real heading changes over a few fixes.
     */
    private fun smoothAzimuth(bearing: Float): Float {
        var delta = bearing - smoothedAzimuth
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        if (kotlin.math.abs(delta) < 8f) return smoothedAzimuth
        smoothedAzimuth = ((smoothedAzimuth + delta * 0.5f) % 360f + 360f) % 360f
        return smoothedAzimuth
    }

    private var miniMapObjects: MapObjectCollection? = null

    /** Mini-map driver puck — same navigator icon the home map uses. */
    private fun setupTripMiniMap() {
        if (_binding == null) return
        if (binding.includeDialog.cvMiniMap.visibility != View.VISIBLE) return // mini-map removed
        val map = binding.includeDialog.mapTripMini.map
        map.isNightModeEnabled = mapKit.map.isNightModeEnabled
        if (miniMapObjects == null) {
            miniMapObjects = map.mapObjects.addCollection()
            miniMapDriverMark = null
        }
        // Follow the puck (like the home/expanded maps): a hand pan/zoom suspends it, then it
        // resumes 3 s after the last gesture.
        if (miniCameraListener == null) {
            miniFollowDriver = true
            miniCameraListener = CameraListener { _, _, reason, _ ->
                if (reason == CameraUpdateReason.GESTURES) {
                    miniFollowDriver = false
                    scheduleMiniFollowResume()
                }
            }
            map.addCameraListener(miniCameraListener!!)
        }
        updateMiniMapDriver()
    }

    private var miniMapDriverMark: PlacemarkMapObject? = null
    private var lastMiniDriverLat = 0.0
    private var lastMiniDriverLon = 0.0

    // Last position the expanded full-screen map puck/camera tracked, to gate out sub-5 m GPS jitter.
    private var lastExpandedDriverLat = 0.0
    private var lastExpandedDriverLon = 0.0

    // ── Camera follow auto-resume (home / mini / expanded all behave the same) ───────────
    // A hand pan/zoom suspends the per-fix camera follow on a map; 3 s after the LAST manual
    // gesture the map snaps back to the driver puck and resumes following. Each map keeps its
    // own follow flag (homeFollowDriver / miniFollowDriver / expandedFollowDriver).
    private val followHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var miniFollowDriver = true
    private var miniCameraListener: CameraListener? = null

    private val resumeHomeFollow = Runnable {
        homeFollowDriver = true
        if (::mapKit.isInitialized) MyTrackingService.lastLatLngWholeApp.value?.let { d ->
            cameraZoom = maxOf(cameraZoom, 16.5f)
            val cp = mapKit.map.cameraPosition
            mapKit.map.move(
                CameraPosition(Point(d.latitude, d.longitude), cameraZoom, cp.azimuth, 0f),
                Animation(Animation.Type.SMOOTH, 0.4f), null
            )
        }
    }
    private val resumeExpandedFollow = Runnable {
        expandedFollowDriver = true
        followExpandedMapDriver()
    }
    private val resumeMiniFollow = Runnable {
        miniFollowDriver = true
        followMiniMapDriver()
    }

    private fun scheduleHomeFollowResume() {
        followHandler.removeCallbacks(resumeHomeFollow)
        followHandler.postDelayed(resumeHomeFollow, 3000L)
    }

    private fun scheduleExpandedFollowResume() {
        followHandler.removeCallbacks(resumeExpandedFollow)
        followHandler.postDelayed(resumeExpandedFollow, 3000L)
    }

    private fun scheduleMiniFollowResume() {
        followHandler.removeCallbacks(resumeMiniFollow)
        followHandler.postDelayed(resumeMiniFollow, 3000L)
    }

    /** Mini-map camera follow — pans to keep the live driver puck centred, zoomed to a navigation
     *  (street) level so it actually tracks the puck instead of sitting at the whole-route overview. */
    private fun followMiniMapDriver() {
        if (_binding == null || !miniFollowDriver) return
        if (binding.includeDialog.cvMiniMap.visibility != View.VISIBLE) return // mini-map removed
        val d = MyTrackingService.lastLatLngWholeApp.value ?: return
        try {
            val map = binding.includeDialog.mapTripMini.map
            val cp = map.cameraPosition
            map.move(
                CameraPosition(
                    Point(d.latitude, d.longitude), maxOf(cp.zoom, 15.5f),
                    routeCourse() ?: cp.azimuth, cp.tilt
                ),
                Animation(Animation.Type.SMOOTH, 0.5f), null
            )
        } catch (_: Exception) {
        }
    }

    /** Draw/refresh the driver placemark on the mini-map at the current GPS fix. */
    private fun updateMiniMapDriver() {
        if (_binding == null || binding.includeDialog.cvMiniMap.visibility != View.VISIBLE) return // mini-map removed
        val driver = MyTrackingService.lastLatLngWholeApp.value ?: return
        val coll = miniMapObjects ?: return
        val point = Point(driver.latitude, driver.longitude)
        try {
            // Move the existing puck instead of clearing + re-creating the whole ViewProvider
            // placemark every GPS tick (that full re-render was the mini-map flicker).
            val mark = miniMapDriverMark
            if (mark != null) {
                mark.geometry = point
            } else {
                val view = View(requireContext())
                view.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.navigator_icon)
                miniMapDriverMark = coll.addPlacemark(point, ViewProvider(view)).apply {
                    setIconStyle(IconStyle().apply { scale = 0.2f })
                }
            }
        } catch (_: Exception) {
            miniMapDriverMark = null
        }
    }

    /** Draw/refresh the driver placemark on the full-screen trip map at the current GPS fix,
     *  so the puck tracks the driver live instead of freezing where the map was opened. */
    private fun updateExpandedMapDriver(freshPoint: Point? = null) {
        val point = freshPoint
            ?: MyTrackingService.lastLatLngWholeApp.value
                ?.let { Point(it.latitude, it.longitude) }
            ?: return
        val coll = expandedDriverColl ?: return
        try {
            val mark = expandedMapDriverMark
            if (mark != null) {
                mark.geometry = point
            } else {
                val view = View(requireContext())
                view.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.navigator_icon)
                expandedMapDriverMark = coll.addPlacemark(point, ViewProvider(view)).apply {
                    setIconStyle(IconStyle().apply { scale = 0.25f })
                }
            }
        } catch (_: Exception) {
            expandedMapDriverMark = null
        }
    }

    /** Navigation-style camera follow on the full-screen trip map (keeps the user's current
     *  zoom). Suspended when the user pans by hand; resumed via the locate button. */
    private fun followExpandedMapDriver(freshPoint: Point? = null) {
        val point = freshPoint
            ?: MyTrackingService.lastLatLngWholeApp.value
                ?.let { Point(it.latitude, it.longitude) }
            ?: return
        val map = _dialogTripMapBinding?.mapTripFull?.map ?: return
        try {
            val cp = map.cameraPosition
            map.move(
                CameraPosition(point, maxOf(cp.zoom, 16.5f), routeCourse() ?: cp.azimuth, cp.tilt),
                Animation(Animation.Type.SMOOTH, 0.5f), null
            )
        } catch (_: Exception) {
        }
    }

    private var miniMapRouteObjects: MapObjectCollection? = null
    private var miniMapRouteKey: String? = null

    /** Draw the booked route (order.locations) + pickup (green) / dropoff (red) markers on the
     *  trip mini-map, framed to fit — the marks + route the home map shows. Driver puck stays on
     *  its own collection so this static route isn't cleared on every GPS tick. */
    private fun updateMiniMapRoute() {
        val ord = order ?: return
        if (_binding == null) return
        if (binding.includeDialog.cvMiniMap.visibility != View.VISIBLE) return // mini-map removed
        val map = binding.includeDialog.mapTripMini.map
        val coll = miniMapRouteObjects
            ?: map.mapObjects.addCollection().also { miniMapRouteObjects = it }
        val pts = ord.locations.map { Point(it.latitude, it.longitude) }
        // Only rebuild + re-frame when the booked points actually change — the old per-render
        // clear/redraw flickered the pins and jumped the camera on every socket refresh.
        val key = ord.locations.joinToString { "${it.latitude},${it.longitude}" }
        try {
            // Rebuild pins + line ONLY when the booked points change — the old per-render
            // clear/redraw flickered the pins on every socket refresh.
            if (key != miniMapRouteKey) {
                miniMapRouteKey = key
                coll.clear()
                // Pins only — the route line is the LIVE road route (miniRouteLine, drawn by
                // drawActiveRoute). The old straight booked-points connector just overlaid a
                // redundant "direct" line on top of the real road-following route.
                ord.locations.forEachIndexed { i, loc ->
                    coll.addPlacemark(
                        Point(loc.latitude, loc.longitude),
                        letterMarker(('A' + i).toString())
                    )
                }
            }
            // Re-center ON THE DRIVER every render (puck stays in the MIDDLE), zoom to include the
            // whole route around it. Runs on order render/refresh — NOT per GPS fix — so no jitter,
            // but the puck stays centered after a reopen / as the trip progresses. The old plain
            // bounding-box of [route + driver] made the driver an outlier: off the straight A→B line
            // it pinned the puck to the frame's top edge with the route squashed to the far side.
            val driver = MyTrackingService.lastLatLngWholeApp.value
            if (driver != null) {
                val dPt = Point(driver.latitude, driver.longitude)
                var maxD = 0.0015 // floor so a driver sitting on the pickup isn't over-zoomed
                pts.forEach {
                    maxD = maxOf(
                        maxD,
                        Math.abs(it.latitude - dPt.latitude),
                        Math.abs(it.longitude - dPt.longitude)
                    )
                }
                // x2: driver is centered, so the farthest point is ~half a frame away; x1.3 padding.
                val span = maxD * 2.0 * 1.3
                val zoom = (Math.log(360.0 / span) / Math.log(2.0)).toFloat().coerceIn(11f, 16f)
                map.move(CameraPosition(dPt, zoom, 0f, 0f))
            } else if (pts.isNotEmpty()) {
                fitExpandedCamera(map, pts)
            }
        } catch (_: Exception) {
        }
    }

    private var homeTripObjects: MapObjectCollection? = null

    // Change-guard mirroring updateMiniMapRoute's miniMapRouteKey: when the same order's same
    // waypoints re-render (the everyday warm-return / periodic-refresh case), skip the
    // clear()+re-add so the home-map A/B/C pins don't visibly flash. Reset to null wherever
    // homeTripObjects is nulled/cleared (onCreateView theme rebuild + the no-active-order branch)
    // so a rebuilt/emptied collection is repopulated on the next order.
    private var homeTripKey: String? = null

    /** Mirror the trip onto the MAIN map: booked route line + lettered waypoint badges, on their
     *  own collection (separate from the live nav route / driver puck). Refreshed on order / route
     *  change; the home map's driver puck + camera follow stay on the existing home-map logic. */
    private fun updateHomeMapTrip() {
        if (_binding == null || !::mapKit.isInitialized) return
        val ord = order
        val newKey = ord?.locations?.joinToString(";") { "${it.latitude},${it.longitude}" }
        // Same order, same waypoints, pins already drawn → nothing changed, don't re-clear/re-add.
        if (newKey == homeTripKey && homeTripObjects != null) return
        val coll = homeTripObjects
            ?: mapKit.map.mapObjects.addCollection().also { homeTripObjects = it }
        try {
            coll.clear()
            homeTripKey = newKey
            if (ord == null) return
            // Lettered pins only — the route line is mapObjectsPolyline (trimmed, live).
            ord.locations.forEachIndexed { i, loc ->
                coll.addPlacemark(
                    Point(loc.latitude, loc.longitude),
                    letterMarker(('A' + i).toString())
                )
            }
        } catch (_: Exception) {
        }
    }

    private val tripRoutePath = ArrayList<Point>()
    private var tripRouteKey: String? = null
    private var expandedRouteLine: MapObjectCollection? = null

    /** A lettered route badge (A, B, C…) drawn to a crisp bitmap: white-bordered gray rounded
     *  rectangle with the letter inside. A bitmap ImageProvider (not a ViewProvider) so MapKit
     *  renders it reliably at a fixed size instead of re-measuring it down to nothing. */
    private fun letterMarker(letter: String): ImageProvider {
        val d = resources.displayMetrics.density
        val size = (26 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val strokeW = 1.5f * d
        val rect = RectF(strokeW, strokeW, size - strokeW, size - strokeW)
        val radius = 7f * d
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(requireContext(), R.color.gray_full)
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokeW
        })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, baseline, textPaint)
        return object : ImageProvider() {
            override fun getId() = "letter_marker_$letter"
            override fun getImage() = bmp
        }
    }

    /** Pull the real road route through the booked points (A→B→…) from the route API and redraw
     *  the trip mini-map with it; the expanded map reads [tripRoutePath] on its next open. */
    private fun fetchTripRoute() {
        val ord = order ?: return
        val locs = ord.locations
        if (locs.size < 2) {
            tripRoutePath.clear()
            tripRouteKey = null
            return
        }
        val key = locs.joinToString("|") { "${it.latitude},${it.longitude}" }
        if (key == tripRouteKey && tripRoutePath.isNotEmpty()) return
        tripRouteKey = key
        viewLifecycleOwner.lifecycleScope.launch {
            val combined = ArrayList<Point>()
            for (i in 0 until locs.size - 1) {
                val a = locs[i]
                val b = locs[i + 1]
                try {
                    mapViewModel.getRoute(a.longitude, a.latitude, b.longitude, b.latitude)
                        .collect { res ->
                            if (res is Resource.Success) {
                                val steps = res.data?.routes?.firstOrNull()
                                    ?.legs?.firstOrNull()?.steps ?: emptyList()
                                steps.forEach { step ->
                                    Helper.decode(step.geometry, 5).forEach { ll ->
                                        combined.add(Point(ll.latitude, ll.longitude))
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {
                }
            }
            if (_binding == null) return@launch
            tripRoutePath.clear()
            tripRoutePath.addAll(combined)
            if (combined.isNotEmpty()) {
                updateMiniMapRoute()
                updateHomeMapTrip()
                if (dialogTripMap?.isShowing == true) drawExpandedRouteLine()
            }
        }
    }

    /** (Re)draw the expanded-map route polyline on its own collection so it can be swapped from
     *  the straight connector to the API road route in place, without disturbing the pins/puck. */
    private fun drawExpandedRouteLine() {
        val coll = expandedRouteLine ?: return
        try {
            coll.clear()
            // Prefer the live trimmed road route; fall back to a straight booked-points connector
            // so a route line is ALWAYS visible (e.g. in ACCEPTED, before the live route is built).
            val pts = if (drawnRoutePoints.size >= 2) drawnRoutePoints
            else order?.locations?.map { Point(it.latitude, it.longitude) }.orEmpty()
            if (pts.size >= 2) {
                coll.addPolyline(Polyline(pts)).apply {
                    strokeWidth = 10f
                    setStrokeColor(ContextCompat.getColor(requireContext(), R.color.app_color))
                    outlineWidth = 2f
                    outlineColor = ContextCompat.getColor(requireContext(), R.color.white)
                }
            }
        } catch (_: Exception) {
        }
    }

    private var marker: PlacemarkMapObject? = null
    private fun setMarkerCurrentPoint(lat: Double, lon: Double) {
        val view = View(requireContext())
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.navigator_icon)
        marker = mapObjectsCurrentPoint.addPlacemark(Point(lat, lon), ViewProvider(view))

        marker!!.opacity = 1.0f

        val iconStyle = IconStyle()
        iconStyle.scale = 0.2f
        marker!!.setIconStyle(iconStyle)
    }

    private fun moveMarkerToPositionWithAnimation(marker: PlacemarkMapObject, location: Location) {
        // One animator at a time: cancel the previous before starting a new ~1s lerp. The old 10s
        // animator with no cancel stacked ~10 concurrent animators (fixes arrive ~1s apart), all
        // fighting over marker.geometry → a permanently-lagging, jittery puck.
        puckAnimator?.cancel()
        val start = marker.geometry
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1_000L
        animator.addUpdateListener {
            val fraction = it.animatedFraction
            try {
                // Lerp from a FIXED start captured once (not re-read live), so the puck converges.
                val lat = start.latitude + fraction * (location.latitude - start.latitude)
                val lon = start.longitude + fraction * (location.longitude - start.longitude)
                marker.geometry = Point(lat, lon)
            } catch (e: Exception) {
                Timber.d("Error occurred")
            }
        }
        puckAnimator = animator
        animator.start()

        // Camera follow on EVERY fix (live) while homeFollowDriver — not only when moving > 1 m/s,
        // so a slow/parked puck is still tracked. The stable route-course heading means it no longer
        // spins the map at low speed; only fall back to the GPS bearing when there's no route AND the
        // car is actually moving, otherwise keep the current azimuth.
        if (homeFollowDriver) {
            mapKit.map.move(
                CameraPosition(
                    Point(location.latitude, location.longitude),
                    cameraZoom,
                    routeCourse()
                        ?: if (location.speed > 1) smoothAzimuth(location.bearing)
                        else mapKit.map.cameraPosition.azimuth,
                    0.0f
                ), Animation(Animation.Type.SMOOTH, 1f), null
            )
        }
    }

    private var routePath = ArrayList<LatLng>()
    private fun getRoute(
        currentLocation: Location,
        destinationLocation: Point,
        reDroveRoute: Boolean = false
    ) {
        // Newest route request wins: a slower in-flight build (e.g. a deviation reroute with the
        // old [B,C] waypoints) must not call setActiveRoute AFTER a newer one and snap the line
        // back through a stop the driver already passed.
        val gen = ++activeRouteGen
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getRoute(
                currentLocation.longitude,
                currentLocation.latitude,
                destinationLocation.longitude,
                destinationLocation.latitude
            ).collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        if (_binding == null) return@collect
                        val firstRoute = it.data?.routes?.firstOrNull()
                        val firstLeg = firstRoute?.legs?.firstOrNull()
                        val steps = firstLeg?.steps ?: emptyList()

                        val points = ArrayList<LatLng>()
                        steps.forEach { item ->
                            points.addAll(Helper.decode(item.geometry, 5))
                        }

//                        val points = Helper.decode(it.data!!.routes[0].geometry, 5)

                        val newPoints = ArrayList<LatLng>()
                        newPoints.add(LatLng(currentLocation.latitude, currentLocation.longitude))
                        newPoints.addAll(points)
                        newPoints.add(
                            LatLng(
                                destinationLocation.latitude,
                                destinationLocation.longitude
                            )
                        )

                        if (gen != activeRouteGen) return@collect
                        setActiveRoute(newPoints)

                        routePath.clear()
                        newPoints.forEach { point ->
                            routePath.add(LatLng(point.latitude, point.longitude))
                        }

                        // Start or Resume RouteInstructionService
                        startRouteService(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            destinationLocation.latitude,
                            destinationLocation.longitude
                        )

//                        startRouteService(reDroveRoute, it.data.routes[0])
                    }

                    is Resource.Error -> {
                        rerouteInFlight = false
                    }
                }
            }
        }
    }

    // --- Live route: trim the driven prefix + reroute on deviation/reopen (iOS parity) --------
    private val trackedRouteTail = ArrayList<Point>()   // full driver→next route geometry
    private val drawnRoutePoints = ArrayList<Point>()   // last drawn (trimmed) route
    private var trimCursor = 0
    private var needsCursorSeed = false
    private var lastTrimLat = 0.0
    private var lastTrimLon = 0.0
    private var rerouteInFlight = false
    private var lastRerouteAtMs = 0L
    private var miniRouteLine: MapObjectCollection? = null

    // Monotonic route-build token: a stale [B,C] build landing after an advance must not snap the
    // drawn line back through the passed stop. Bumped per request in getRoute/rebuildActiveRoute.
    private var activeRouteGen = 0

    // Route-draw mode for a multi-stop trip: true = draw the FULL remaining route (current target →
    // … → final), false = draw ONLY the single chosen target. The driver switches via the stops
    // picker: tapping a stop = single (route to that exact point), the "Full route" row = full.
    private var routeThroughAllStops = true

    /** Capture a freshly-fetched driver→next route, reset the trim cursor, and draw it. */
    private fun setActiveRoute(points: List<LatLng>) {
        trackedRouteTail.clear()
        points.forEach { trackedRouteTail.add(Point(it.latitude, it.longitude)) }
        trimCursor = 0
        needsCursorSeed = true
        lastTrimLat = 0.0
        lastTrimLon = 0.0
        rerouteInFlight = false
        drawActiveRoute(trackedRouteTail)
    }

    /** Draw the (possibly trimmed) route line on the home, mini and expanded maps. Markers + driver
     *  puck live on their own collections, so this only ever touches the route polyline. */
    private fun drawActiveRoute(points: List<Point>) {
        // No active destination → never (re)draw the route. A tracking tick queued before the trip
        // finished, or an in-flight reroute that lands AFTER it, would otherwise repaint the
        // just-cleared line onto the home map — "the route still shows after completing the order".
        if (destinationLocation == null) {
            drawnRoutePoints.clear()
            if (::mapObjectsPolyline.isInitialized) try {
                mapObjectsPolyline.clear()
            } catch (_: Exception) {
            }
            miniRouteLine?.let {
                try {
                    it.clear()
                } catch (_: Exception) {
                }
            }
            return
        }
        drawnRoutePoints.clear()
        drawnRoutePoints.addAll(points)
        if (::mapObjectsPolyline.isInitialized) {
            try {
                mapObjectsPolyline.clear()
                if (points.size >= 2) {
                    mapObjectsPolyline.addPolyline(Polyline(points)).apply {
                        strokeWidth = 12f
                        setStrokeColor(ContextCompat.getColor(requireContext(), R.color.app_color))
                        outlineWidth = 2f
                        outlineColor = ContextCompat.getColor(requireContext(), R.color.white)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (_binding != null) {
            val coll = miniRouteLine ?: try {
                binding.includeDialog.mapTripMini.map.mapObjects.addCollection()
                    .also { miniRouteLine = it }
            } catch (_: Exception) {
                null
            }
            try {
                coll?.clear()
                if (coll != null && points.size >= 2) {
                    coll.addPolyline(Polyline(points)).apply {
                        strokeWidth = 6f
                        setStrokeColor(ContextCompat.getColor(requireContext(), R.color.app_color))
                        outlineWidth = 1.5f
                        outlineColor = ContextCompat.getColor(requireContext(), R.color.white)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (dialogTripMap?.isShowing == true) drawExpandedRouteLine()
    }

    private fun closestPointOnSegment(p: Point, a: Point, b: Point): Point {
        val segLat = b.latitude - a.latitude
        val segLon = b.longitude - a.longitude
        val segLen2 = segLat * segLat + segLon * segLon
        if (segLen2 <= 0.0) return a
        val t = (((p.latitude - a.latitude) * segLat + (p.longitude - a.longitude) * segLon)
                / segLen2).coerceIn(0.0, 1.0)
        return Point(a.latitude + segLat * t, a.longitude + segLon * t)
    }

    private fun metersBetween(a: Point, b: Point): Double =
        Helper.calculateBetweenTwoPoints(
            LatLng(a.latitude, a.longitude), LatLng(b.latitude, b.longitude)
        ).toDouble()

    private fun bearingBetween(a: Point, b: Point): Float {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /** Heading along the drawn route at its head (the driver end) so the camera faces the route
     *  direction → the puck points ALONG the route line (not the noisy GPS bearing). Null until a
     *  usable 2-point route exists; callers fall back to the GPS bearing / current azimuth. */
    private fun routeCourse(): Float? {
        val pts = drawnRoutePoints
        if (pts.size < 2) return null
        val a = pts[0]
        var b = pts[1]
        var i = 1
        // Skip tiny lead segments so the bearing isn't jittery right at the driver.
        while (i < pts.size - 1 && metersBetween(a, b) < 15.0) {
            i++
            b = pts[i]
        }
        return if (metersBetween(a, b) < 1.0) null else bearingBetween(a, b)
    }

    private fun nearestSegment(p: Point, from: Int, to: Int): Int {
        var bestIdx = from
        var best = Double.MAX_VALUE
        var i = from
        while (i < to - 1) {
            val d = metersBetween(
                p, closestPointOnSegment(p, trackedRouteTail[i], trackedRouteTail[i + 1])
            )
            if (d < best) {
                best = d
                bestIdx = i
            }
            i++
        }
        return bestIdx
    }

    private fun distanceToRouteAhead(p: Point, from: Int, to: Int): Double {
        var best = Double.MAX_VALUE
        var i = from
        while (i < to - 1) {
            best = minOf(
                best,
                metersBetween(
                    p,
                    closestPointOnSegment(p, trackedRouteTail[i], trackedRouteTail[i + 1])
                )
            )
            i++
        }
        return best
    }

    /** Per fix: drop the driven prefix (project the driver onto the road ahead) and reroute when
     *  the driver is clearly off the planned road. A bounded forward window avoids loop-back jumps. */
    private fun trimRouteOnFix(location: Location) {
        val n = trackedRouteTail.size
        if (n < 2) return
        val driver = Point(location.latitude, location.longitude)
        if (lastTrimLat != 0.0 &&
            metersBetween(Point(lastTrimLat, lastTrimLon), driver) < 12.0
        ) return
        lastTrimLat = location.latitude
        lastTrimLon = location.longitude

        if (needsCursorSeed) {
            needsCursorSeed = false
            trimCursor = nearestSegment(driver, 0, n) // global once (fresh route / app reopen)
        }
        val cursor = trimCursor.coerceIn(0, n - 2)
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0
        // Stay on the built route within ~50–100 m (boss spec): small deviations / GPS jitter only
        // trim, never reroute; leaving the corridor by more than this reroutes (through mid points).
        val tolerance = maxOf(50.0, minOf(accuracy * 2.0, 100.0))
        if (distanceToRouteAhead(driver, cursor, n) > tolerance) {
            requestReroute(location)
            // The reroute is debounced (<=5 s); until it lands, draw a provisional straight connector
            // from the driver to the route ahead so the line keeps tracking the car instead of
            // freezing on the stale pre-deviation prefix.
            val provisional = ArrayList<Point>(n - cursor + 1)
            provisional.add(driver)
            for (i in cursor until n) provisional.add(trackedRouteTail[i])
            drawActiveRoute(provisional)
            return
        }
        val windowEnd = minOf(n, cursor + 60)
        trimCursor = nearestSegment(driver, cursor, windowEnd).coerceIn(0, n - 2)
        val head = closestPointOnSegment(
            driver, trackedRouteTail[trimCursor], trackedRouteTail[trimCursor + 1]
        )
        val drawn = ArrayList<Point>(n - trimCursor + 1)
        drawn.add(head)
        for (i in (trimCursor + 1) until n) drawn.add(trackedRouteTail[i])
        drawActiveRoute(drawn)
    }

    /** Full remaining route from the driver, by state — through ALL remaining stops (mid points) to
     *  the final destination, not just the next leg. Before pickup (Accepted/Started/Arrived): the
     *  pickup then every drop-off in order. After pickup (Gone): the current target
     *  (destinationPosition) and every stop after it; the per-fix trim peels off whatever the driver
     *  has already driven past. */
    private fun tripWaypoints(ord: Order): List<Point> {
        val locs = ord.locations
        if (locs.isEmpty()) return emptyList()
        return when {
            ord.state >= ORDER_STATE_CHANGED_GONE -> {
                // Current leg onward: every stop whose position >= the current target. On a cold
                // reopen destinationPosition is null → start from 1 (first drop-off); the trim then
                // peels the already-passed prefix off the drawn polyline as the driver advances.
                val currentPos = MyTrackingService.destinationPosition ?: 1
                // Full route → every stop at/after the current target; single-target → just the
                // current target (route to that EXACT point, don't thread the following stops).
                val remaining = locs
                    .filter { if (routeThroughAllStops) it.position >= currentPos else it.position == currentPos }
                    .map { Point(it.latitude, it.longitude) }
                remaining.ifEmpty {
                    listOf(
                        destinationLocation ?: Point(
                            locs.last().latitude,
                            locs.last().longitude
                        )
                    )
                }
            }
            // Heading to pickup: pickup + every drop-off, the full ordered route.
            else -> locs.map { Point(it.latitude, it.longitude) }
        }
    }

    /** Build the (multi-leg) active route from [origin] through the state's waypoints, decode +
     *  stitch the legs, and hand it to the trim/draw system. Single-flight + 5 s debounced. */
    private fun rebuildActiveRoute(origin: Location) {
        val ord = order ?: return
        val wps = tripWaypoints(ord)
        if (wps.isEmpty()) return
        rerouteInFlight = true
        lastRerouteAtMs = System.currentTimeMillis()
        val gen = ++activeRouteGen
        viewLifecycleOwner.lifecycleScope.launch {
            val combined = ArrayList<LatLng>()
            combined.add(LatLng(origin.latitude, origin.longitude))
            var prev = Point(origin.latitude, origin.longitude)
            for (wp in wps) {
                try {
                    mapViewModel.getRoute(prev.longitude, prev.latitude, wp.longitude, wp.latitude)
                        .collect { res ->
                            if (res is Resource.Success) {
                                val steps = res.data?.routes?.firstOrNull()
                                    ?.legs?.firstOrNull()?.steps ?: emptyList()
                                steps.forEach { combined.addAll(Helper.decode(it.geometry, 5)) }
                            }
                        }
                } catch (_: Exception) {
                }
                combined.add(LatLng(wp.latitude, wp.longitude))
                prev = wp
            }
            rerouteInFlight = false
            if (_binding == null) return@launch
            if (gen != activeRouteGen) return@launch
            if (combined.size >= 2) setActiveRoute(combined)
            wps.firstOrNull()?.let {
                startRouteService(origin.latitude, origin.longitude, it.latitude, it.longitude)
            }
        }
    }

    /**
     * The client asked the driver to keep going past the LAST stop ("drive on a bit"). Without
     * this the trip is permanently off-corridor, so [trimRouteOnFix] rerouted on every fix and the
     * line kept pointing BACKWARDS to a point the car had already left.
     *
     * Two-stage so an ordinary detour can't trip it: the driver must first actually reach the stop
     * ([REACHED_FINAL_M]) and only then leave it by [PASSED_FINAL_M]. A traffic detour never enters
     * that inner circle. Once passed, the route is dropped and not rebuilt — metering is unaffected
     * (the server bills the uploaded GPS track, the polyline is only guidance), and the driver can
     * re-target any stop by tapping it, which clears the latch in [onDestinationClick].
     */
    private fun updateFinalDestinationPassed(location: Location) {
        val ord = order ?: return
        if (finalPassedOrderId != ord.id) {
            finalPassedOrderId = ord.id
            reachedFinalDestination = false
            finalDestinationPassed = false
        }
        if (finalDestinationPassed) return
        if (ord.state < ORDER_STATE_CHANGED_GONE) return
        val last = ord.locations.maxByOrNull { it.position } ?: return
        // Only when the LAST stop is the current target — with stops still ahead the normal
        // checkpoint/auto-advance flow owns the route.
        val currentPos = MyTrackingService.destinationPosition ?: return
        if (currentPos != last.position) return

        val d = metersBetween(
            Point(location.latitude, location.longitude),
            Point(last.latitude, last.longitude)
        )
        if (d <= REACHED_FINAL_M) {
            reachedFinalDestination = true
            return
        }
        if (!reachedFinalDestination || d <= PASSED_FINAL_M) return

        finalDestinationPassed = true
        trackedRouteTail.clear()
        drawActiveRoute(emptyList())
    }

    /** Build the route after a reopen — the state handler only fetches once a location is known. */
    private fun ensureActiveRoute(location: Location) {
        val ord = order ?: return
        if (finalDestinationPassed) return
        // Build from ACCEPTED onward (route to the pickup), not only from STARTED — otherwise an
        // ACCEPTED order resumed on a cold start where currentLocation was null when
        // setOrderStateButton ran would never get its pickup route drawn until the trip is started.
        if (ord.state < ORDER_STATE_ACCEPTED) return
        if (trackedRouteTail.size >= 2 || rerouteInFlight) return
        rebuildActiveRoute(location)
    }

    /** Reroute from the driver's current position through the remaining waypoints — on deviation
     *  or reopen. Debounced (5 s) + single-flight so GPS noise can't spam the routing API. */
    private fun requestReroute(location: Location) {
        if (rerouteInFlight) return
        if (System.currentTimeMillis() - lastRerouteAtMs < 5000L) return
        if (order == null) return
        rebuildActiveRoute(location)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setMarkerDestinationPoint(lat: Double, lon: Double) {
        // Blue destination pin removed on request — the lettered A/B/C badges mark the waypoints.
    }

    // Override functions
    override fun onCameraPositionChanged(
        // Fully qualified on purpose: `Map` collides with kotlin.collections.Map, and Android
        // Studio's "Optimize Imports" strips the `com.yandex.mapkit.map.Map` import, which silently
        // breaks this override + the .move() calls below. Naming it in full is immune to that.
        p0: com.yandex.mapkit.map.Map,
        p1: CameraPosition,
        p2: CameraUpdateReason,
        p3: Boolean
    ) {
        // A hand pan/zoom (GESTURES) suspends the per-fix camera follow so it isn't yanked back on
        // the next fix (the recenter button resumes it), and syncs cameraZoom to the user's choice.
        if (p2 == CameraUpdateReason.GESTURES) {
            homeFollowDriver = false
            cameraZoom = p1.zoom
            scheduleHomeFollowResume()
        }
        if (_binding != null && p3) {
            selectedLocation = LatLng(p1.target.latitude, p1.target.longitude)
        }
    }

    override fun onDestinationClick(location: Order.Location) {
        // Allow the pickup (A, position 0) too — a driver may legitimately need to route back there
        // (e.g. the client forgot something at home). Positions are always >= 0.
        if (location.position >= 0) {
            MyTrackingService.destinationPosition = location.position
            // Hand-picking a stop is an explicit "route me there again" — drop the pass-by latch
            // so the route can be rebuilt even after the driver had already driven past the end.
            reachedFinalDestination = false
            finalDestinationPassed = false
            // Manually retargeting a stop clears any earlier "No" for it, so the 10 s checkpoint
            // prompt can work again from here.
            order?.let {
                checkpointPrefs.edit()
                    .remove(checkpointSuppressKey(it.id, location.position)).apply()
            }

            binding.includeDialog.tvFinishAddress.text =
                location.name.ifEmpty { getString(R.string.not_showed) }

            destinationLocation = Point(location.latitude, location.longitude)
            mapObjectsDestinationPoint.clear()
            setMarkerDestinationPoint(
                destinationLocation!!.latitude,
                destinationLocation!!.longitude
            )
            routePath.clear()

            // Guard: currentLocation is only set after the first GPS fix; tapping a destination row
            // before then would NPE on currentLocation!! (the map-icon sibling already guards).
            val cur = currentLocation
            if (cur == null) {
                showToast(getString(R.string.location_not_found))
                return
            }
            // Tapping a stop routes to THAT EXACT point only — not through the stops after it
            // (that's what the "Full route" row is for). Then close the picker and minimise the
            // sheet so the driver sees the route on the map.
            routeThroughAllStops = false
            rebuildActiveRoute(cur)
            dialogDestination?.dismiss()
            isTripMinimized = true
            setTripSheetState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    override fun onDestinationMapClick(location: Order.Location) {
        // Allow the pickup (A, position 0) too — see onDestinationClick.
        if (location.position >= 0) {
            val mapType = MapTypeManager.getType()
            if (MyTrackingService.lastLatLngWholeApp.value != null) {
                when (mapType) {
                    Constants.GOOGLE -> {
                        openGoogleMap(location)
                    }

                    Constants.YANDEX -> {
                        openYandexMap(location)
                    }

                    Constants.YANDEX_NAVI -> {
                        openYandexNavi(location)
                    }

                    Constants.TWO_GIS -> {
                        open2GISMap(location)
                    }

                    Constants.WAZE -> {
                        openWazeMap(location)
                    }
                }
            } else {
                showToast(getString(R.string.location_not_found))
            }
        }
    }

    // Open Maps
    private fun openOverallMap(location: Order.Location) {
        val uri = "geo:${location.latitude},${location.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        startActivity(intent)
    }

    /**
     * Is there anywhere for the external navigator to actually go right now?
     *
     * Approach phase (before ARRIVED) the target is the PICKUP, so any order with a location
     * qualifies — including a B-less order, where the driver still has to reach the client.
     * From ARRIVED onward the target is a destination leg, so an order with NO B point has
     * nothing left to route to: [externalRouteWaypoints] would fall back to the pickup the
     * driver is already standing on and the navigator would open a route to where they are.
     * Hide the nav controls in that case instead of offering a useless route (boss request).
     *
     * "Has a B point" is tested by POSITION (> 0 = a destination, 0 = the pickup), matching
     * how MyDirectionFragment decides which rows get a map icon — robust to the server ever
     * returning locations unsorted.
     */
    private fun hasNavigableTarget(ord: Order): Boolean {
        if (ord.locations.isEmpty()) return false
        if (ord.state < ORDER_STATE_CHANGED_ARRIVED) return true
        return ord.locations.any { it.position > 0 }
    }

    /**
     * Ordered points the EXTERNAL nav routes through from the driver's position. Before the pickup
     * (Accepted/Started) it's just the pickup — the rider isn't aboard, so we never route past it.
     * From ARRIVED onward it's the remaining stops from the current target to the final destination,
     * selected by location POSITION (tracks destinationPosition). Last element = destination, the
     * rest = mid waypoints.
     */
    private fun externalRouteWaypoints(ord: Order): List<Order.Location> {
        val locs = ord.locations
        if (locs.isEmpty()) return emptyList()
        // Heading to the pickup (Accepted/Started): the rider is NOT aboard yet, so the external
        // navigator must guide the driver to the PICKUP only — never route past it to the final
        // destination. Bug: tapping "Boshlash" opened the navigator on point B (the destination)
        // instead of the pickup, because we returned the whole route and used its last point as
        // the nav destination. The destination legs are navigated from ARRIVED onward (below).
        if (ord.state < ORDER_STATE_CHANGED_ARRIVED) {
            return listOf(locs.first())
        }
        // From ARRIVED onward: the remaining stops at/after the current target, selected by location
        // POSITION — not list index — so this stays correct even if the server ever returns
        // locations unsorted or non-0-based. (Previously this did locs.drop(destinationPosition),
        // treating a position VALUE as a list index, which silently disagreed with the in-app route
        // builder tripWaypoints, which already filters by position.) Falls back to the final stop
        // when nothing remains at/after the current position.
        val currentPos = MyTrackingService.destinationPosition ?: 1
        return locs.filter { it.position >= currentPos }.ifEmpty { listOf(locs.last()) }
    }

    /**
     * Map-button entry point. On a MULTI-drop order that is under way (GONE), open the stops picker
     * so the driver can choose which stop to route to and whether to do it IN-APP or in an EXTERNAL
     * navigator. A single-destination order (or the approach-to-pickup phase) goes straight to the
     * external navigator — no needless extra tap.
     */
    private fun openNavigationOrPicker() {
        val ord = order ?: run { openTripNavigation(); return }
        val dropoffs = ord.locations.count { it.position > 0 }
        if (ord.state >= ORDER_STATE_CHANGED_GONE && dropoffs > 1) {
            showDialogDestinations()
        } else {
            openTripNavigation()
        }
    }

    /**
     * iOS navigationMenu: launch the driver's chosen external map/nav app with the route through
     * the trip's remaining waypoints (current target → mid stops → destination). Disabled until
     * the trip starts (ACCEPTED). Shared by the "Map" button and the mini-map navigation icon.
     */
    private fun openTripNavigation() {
        val ord = order ?: return
        // iOS: building a route is disabled until the trip starts (ACCEPTED = not yet).
        if (ord.state == ORDER_STATE_ACCEPTED) return
        // Nothing to route to (B-less order past the pickup) — also guards the AUTOMATIC
        // opens (MapTypeManager.getChoose()), not just the buttons we hide above.
        if (!hasNavigableTarget(ord)) return
        if (MyTrackingService.lastLatLngWholeApp.value == null) {
            showToast(getString(R.string.location_not_found))
            return
        }
        val locs = ord.locations
        if (locs.isEmpty()) {
            showToast(getString(R.string.location_not_found))
            return
        }
        // Full remaining route from the driver: current target → every mid stop → final
        // destination. Apps with waypoint support (Google/Yandex/Navi) get all the mid points;
        // 2GIS/Waze (no waypoint support) get only the immediate next stop, then re-nav.
        val waypoints = externalRouteWaypoints(ord)
        if (waypoints.isEmpty()) {
            showToast(getString(R.string.location_not_found))
            return
        }
        val dest = waypoints.last()
        val vias = waypoints.dropLast(1)
        when (MapTypeManager.getType()) {
            Constants.GOOGLE -> openGoogleMap(dest, vias)
            Constants.YANDEX -> openYandexMap(dest, vias)
            Constants.YANDEX_NAVI -> openYandexNavi(dest, vias)
            Constants.TWO_GIS -> open2GISMap(dest, vias)
            Constants.WAZE -> openWazeMap(dest, vias)
        }
    }

    /**
     * iOS fullScreenCover: tapping the trip mini-map opens this expanded full-screen route
     * map. It is a SEPARATE dialog window over the trip detail, so the detail is never
     * dismissed — closing this returns straight to it. Reuses the booked-route waypoints
     * (order.locations) for the polyline plus the live driver puck and the destination pin.
     */
    private fun openExpandedTripMap() {
        val ord = order ?: return
        if (_binding == null) return
        dialogTripMap?.dismiss()

        val b = DialogTripMapFullBinding.inflate(layoutInflater)
        _dialogTripMapBinding = b
        val dialog = Dialog(requireContext(), R.style.FullScreenMapDialog)
        dialogTripMap = dialog
        dialog.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(b.root)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Immersive full-screen like the main view: map edge-to-edge, system bars hidden
        // (swipe to reveal). Controls take the status/nav insets so they clear the notch/gesture.
        dialog.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            // Draw the map into the status-bar / display-cutout strip — without this the dialog
            // leaves an opaque bar at the top instead of showing the map there. Only needed up
            // to Android 14: on 15+ (edge-to-edge enforcement) the DEFAULT cutout mode is
            // already interpreted as ALWAYS for non-floating windows, and SHORT_EDGES is a
            // deprecated API that Google Play flags.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
            ) {
                @Suppress("DEPRECATION")
                w.attributes = w.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                w.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                w.navigationBarColor = Color.TRANSPARENT
            }
            WindowInsetsControllerCompat(w, w.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        val pad16 = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            b.llFullSpeed.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = pad16 + bars.top
            }
            b.cvCloseTripMap.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = pad16 + bars.top
            }
            b.cvFullNavigate.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = pad16 + bars.bottom
            }
            insets
        }
        ViewCompat.requestApplyInsets(b.root)

        val map = b.mapTripFull.map
        map.isNightModeEnabled = mapKit.map.isNightModeEnabled
        // Navigation-style camera follow — on by default; a hand pan/zoom (GESTURES) suspends
        // it so the camera doesn't fight the user, and the locate button resumes it.
        expandedFollowDriver = true
        // Re-seed the jitter gate so the first fix after opening always re-positions the puck.
        lastExpandedDriverLat = 0.0
        lastExpandedDriverLon = 0.0
        expandedCameraListener = CameraListener { _, _, reason, _ ->
            if (reason == CameraUpdateReason.GESTURES) {
                expandedFollowDriver = false
                scheduleExpandedFollowResume()
            }
        }
        map.addCameraListener(expandedCameraListener!!)
        val objects = map.mapObjects.addCollection()

        val routePts = ord.locations.map { Point(it.latitude, it.longitude) }
        // Route line on its own collection so it can be redrawn in place when the API route lands.
        expandedRouteLine = map.mapObjects.addCollection()
        drawExpandedRouteLine()
        // Lettered pins A, B, C… at each booked point (green pickup, red last, amber stops).
        ord.locations.forEachIndexed { i, loc ->
            objects.addPlacemark(
                Point(loc.latitude, loc.longitude),
                letterMarker(('A' + i).toString())
            )
        }
        // Live driver puck — same navigator icon as the home/mini maps. On its OWN collection
        // + a field reference so it can be MOVED live as the driver drives
        // (updateExpandedMapDriver), instead of being a one-shot snapshot that freezes.
        val cameraPts = ArrayList(routePts)
        expandedDriverColl = map.mapObjects.addCollection()
        MyTrackingService.lastLatLngWholeApp.value?.let { driver ->
            cameraPts.add(Point(driver.latitude, driver.longitude))
        }
        updateExpandedMapDriver()

        fitExpandedCamera(map, cameraPts)

        // Speed badge mirrors the main view's km/h; build-route disabled until start (ACCEPTED).
        b.tvFullSpeed.text = if (_binding != null) binding.tvSpeed.text else "0"
        val canNav = ord.state != ORDER_STATE_ACCEPTED
        // A B-less order has no leg left to navigate once the client is aboard — drop the bar
        // rather than route back to the pickup the driver is already on (see hasNavigableTarget).
        b.cvFullNavigate.visibility = if (hasNavigableTarget(ord)) View.VISIBLE else View.GONE
        b.cvFullNavigate.isEnabled = canNav
        // Disabled (only-accepted) state reads as a gray bar, not a faded amber one.
        b.cvFullNavigate.setCardBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (canNav) R.color.app_color else R.color.gray
            )
        )
        // Disabled bar reads as a faded gray (≈70%); fully opaque once the trip can start.
        b.cvFullNavigate.alpha = if (canNav) 1f else 0.7f

        b.mapTripFull.onStart()
        b.cvCloseTripMap.setOnClickListener { dialog.dismiss() }
        b.cvFullNavigate.setDebouncedClickListener { openNavigationOrPicker() }
        // Swapped per request: crosshair button → re-frame the whole A→B route (animated).
        // Framing the whole route means we're no longer following the driver.
        b.cvRecenter.setOnClickListener {
            expandedFollowDriver = false
            fitExpandedCamera(map, cameraPts, animate = true)
        }
        // Navigation-arrow button → recenter on the live driver AND resume the follow.
        b.cvFitRoute.setOnClickListener {
            // Geo button = the 3 s auto-resume on demand: resume the follow + snap to the live puck,
            // facing along the route. Cancel any pending resume so they don't fight.
            followHandler.removeCallbacks(resumeExpandedFollow)
            expandedFollowDriver = true
            MyTrackingService.lastLatLngWholeApp.value?.let {
                // Zoom IN to the driver (at least street level), animated — so it isn't left at the
                // zoomed-out whole-route level set by the crosshair "fit route" button.
                val cp = map.cameraPosition
                map.move(
                    CameraPosition(
                        Point(it.latitude, it.longitude),
                        maxOf(cp.zoom, 16.5f),
                        routeCourse() ?: cp.azimuth,
                        cp.tilt
                    ),
                    Animation(Animation.Type.SMOOTH, 0.4f), null
                )
            }
        }
        b.cvZoomIn.setOnClickListener {
            // Zooming to inspect ahead suspends follow, so the next GPS fix doesn't snap back.
            expandedFollowDriver = false
            val cp = map.cameraPosition
            map.move(
                CameraPosition(cp.target, (cp.zoom + 1f).coerceAtMost(19f), cp.azimuth, cp.tilt),
                Animation(Animation.Type.SMOOTH, 0.2f), null
            )
        }
        b.cvZoomOut.setOnClickListener {
            expandedFollowDriver = false
            val cp = map.cameraPosition
            map.move(
                CameraPosition(cp.target, (cp.zoom - 1f).coerceAtLeast(3f), cp.azimuth, cp.tilt),
                Animation(Animation.Type.SMOOTH, 0.2f), null
            )
        }
        dialog.setOnDismissListener {
            followHandler.removeCallbacks(resumeExpandedFollow)
            expandedCameraListener?.let { map.removeCameraListener(it) }
            expandedCameraListener = null
            _dialogTripMapBinding?.mapTripFull?.onStop()
            _dialogTripMapBinding = null
            dialogTripMap = null
            expandedRouteLine = null
            expandedMapDriverMark = null
            expandedDriverColl = null
        }
        dialog.show()
    }

    /**
     * Frame the camera around the route + driver. The lite SDK has no bounding-box camera
     * helper available here, so estimate the zoom from the coordinate span (world width is
     * 360° at zoom 0; each level halves it), padded ~1.4× so the route isn't edge-to-edge.
     */
    private fun fitExpandedCamera(
        map: com.yandex.mapkit.map.Map,
        points: List<Point>,
        animate: Boolean = false
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            moveExpandedCamera(map, CameraPosition(points[0], 15f, 0f, 0f), animate)
            return
        }
        var minLat = points[0].latitude
        var maxLat = minLat
        var minLon = points[0].longitude
        var maxLon = minLon
        points.forEach {
            if (it.latitude < minLat) minLat = it.latitude
            if (it.latitude > maxLat) maxLat = it.latitude
            if (it.longitude < minLon) minLon = it.longitude
            if (it.longitude > maxLon) maxLon = it.longitude
        }
        val center = Point((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
        val span = maxOf(maxLat - minLat, maxLon - minLon, 0.004)
        // Pad generously (1.8×) + a slightly lower upper clamp so the A/B endpoints aren't framed
        // under the top speed badge / bottom navigate bar overlaying the full-screen map.
        val zoom = (Math.log(360.0 / (span * 1.8)) / Math.log(2.0)).toFloat().coerceIn(10f, 15f)
        moveExpandedCamera(map, CameraPosition(center, zoom, 0f, 0f), animate)
    }

    private fun moveExpandedCamera(
        map: com.yandex.mapkit.map.Map,
        pos: CameraPosition,
        animate: Boolean
    ) {
        if (animate) map.move(pos, Animation(Animation.Type.SMOOTH, 0.5f), null)
        else map.move(pos)
    }

    /** iOS services sheet — the add-ons the client turned on for this order (name + price). */
    private fun showServicesSheet() {
        val ord = order ?: return
        val sheet =
            com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
                .fitSystemBars()
        val view = layoutInflater.inflate(R.layout.dialog_bsh_services, null)
        sheet.setContentView(view)

        val list = view.findViewById<android.widget.LinearLayout>(R.id.llServicesList)
        val empty = view.findViewById<TextView>(R.id.tvServicesEmpty)
        val count = view.findViewById<TextView>(R.id.tvServicesSheetCount)
        val card = view.findViewById<android.widget.LinearLayout>(R.id.llServicesCard)
        val total = view.findViewById<TextView>(R.id.tvServicesSheetTotal)

        val services = ord.services ?: emptyList()
        if (services.isEmpty()) {
            empty.visibility = View.VISIBLE
            // Hide the whole grouped card, not just its rows — an empty card would still
            // draw its 1dp stroke as a stranded sliver above the empty message.
            count.visibility = View.GONE
            card.visibility = View.GONE
        } else {
            count.text = getString(R.string.services_active_count, services.size)
            services.forEach { s ->
                val row = layoutInflater.inflate(R.layout.adapter_service_row, list, false)
                row.findViewById<TextView>(R.id.tvServiceName).text = s.service.name
                ServiceIcons.bindInto(
                    row.findViewById(R.id.ivServiceIcon), s.service.name, s.service.icon
                )
                // `info` is an optional server-side blurb; most services ship it empty.
                val info = row.findViewById<TextView>(R.id.tvServiceInfo)
                val infoText = s.service.info
                if (!infoText.isNullOrBlank()) {
                    info.text = infoText
                    info.visibility = View.VISIBLE
                }
                row.findViewById<TextView>(R.id.tvServicePrice).text =
                    "${Helper.formatPrice(s.total.toString())}${getString(R.string.sum)}"
                list.addView(row)
            }
            total.text =
                "${
                    Helper.formatPrice(services.sumOf { it.total }.toString())
                }${getString(R.string.sum)}"
        }
        sheet.show()
    }

    private fun openGoogleMap(dest: Order.Location, vias: List<Order.Location> = emptyList()) {
        try {
            stopRouteService()
            val cur = MyTrackingService.lastLatLngWholeApp.value
            // Google chains waypoints: "daddr=VIA1+to:VIA2+to:...+to:DEST".
            val viaPart = vias.joinToString("") { "${it.latitude},${it.longitude}+to:" }
            val mapUri =
                Uri.parse("https://maps.google.com/maps?saddr=${cur?.latitude},${cur?.longitude}&daddr=$viaPart${dest.latitude},${dest.longitude}")
            val intent = Intent(Intent.ACTION_VIEW, mapUri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openYandexMap(dest: Order.Location, vias: List<Order.Location> = emptyList()) {
        try {
            stopRouteService()
            val cur = MyTrackingService.lastLatLngWholeApp.value
            // Yandex Maps rtext supports multiple route points: current~via1~via2~...~dest.
            val viaPart = vias.joinToString("") { "~${it.latitude},${it.longitude}" }
            val uri =
                Uri.parse("yandexmaps://maps.yandex.ru/?rtext=${cur?.latitude},${cur?.longitude}$viaPart~${dest.latitude},${dest.longitude}&rtt=auto")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openYandexNavi(dest: Order.Location, vias: List<Order.Location> = emptyList()) {
        try {
            stopRouteService()
            val cur = MyTrackingService.lastLatLngWholeApp.value
            // Yandex Navi supports indexed via points: lat_via_0/lon_via_0, lat_via_1/lon_via_1, ...
            val viaPart = vias.mapIndexed { i, v ->
                "&lat_via_$i=${v.latitude}&lon_via_$i=${v.longitude}"
            }.joinToString("")
            val uri =
                Uri.parse("yandexnavi://build_route_on_map?lat_to=${dest.latitude}&lon_to=${dest.longitude}$viaPart&lat_from=${cur?.latitude}&lon_from=${cur?.longitude}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    private fun open2GISMap(dest: Order.Location, vias: List<Order.Location> = emptyList()) {
        try {
            stopRouteService()
            val cur = MyTrackingService.lastLatLngWholeApp.value
            // 2GIS deep-link has no via point — navigate to the immediate next stop, then re-nav.
            val target = vias.firstOrNull() ?: dest
            val uri =
                Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/from/${cur?.longitude},${cur?.latitude}/to/${target.longitude},${target.latitude}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openWazeMap(dest: Order.Location, vias: List<Order.Location> = emptyList()) {
        try {
            stopRouteService()
            // Waze has no via point — navigate to the immediate next stop, then re-nav.
            val target = vias.firstOrNull() ?: dest
            val uri =
                Uri.parse("https://waze.com/ul?ll=${target.latitude},${target.longitude}&navigate=yes")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    // Order cancel
    private var _dialogOrderCancelBinding: DialogOrderCancelBinding? = null
    private val dialogOrderCancelBinding get() = _dialogOrderCancelBinding!!
    private var dialogOrderCancel: Dialog? = null

    // "Contact support instead of cancelling" popup — a member (not a local) so a recreate mid-popup
    // can't leak its window (same class of bug as the infoPopup / cancel-reason dialogs).
    private var _dialogContactSupportBinding: DialogCallDispatcherBinding? = null
    private var dialogContactSupport: Dialog? = null

    /**
     * May the driver cancel this order himself? (Product rule, 2026-08-06 — supersedes the blanket
     * "no driver cancellation" rule of 2026-07-22 and the earlier `state < STARTED` gate.)
     *
     * Keyed on WHO created the order (`order.from`) and how far the trip has gone:
     *
     *  - **Client order** (`from == 25`) — yes, but only up to "Mijoz oldiga yetib keldim". Once the
     *    driver has slid Arrive (state >= [ORDER_STATE_CHANGED_ARRIVED]) the client is already
     *    waiting at the car, so cancelling is off the table.
     *  - **Taximeter** (`from == 1`, driver-created meter) — yes, but only up to "Boshlash": after
     *    state >= [ORDER_STATE_STARTED] the meter is running and the order must be finished, not
     *    dropped.
     *  - **Manager (3) / dispatcher (4) / admin (10)** — never. The driver did not take these on
     *    himself and cannot drop them; he is sent to the operator instead.
     *
     * Anything unknown falls into the same "no" bucket: the support popup is always a safe answer,
     * while wrongly allowing a cancel is not.
     *
     * The states are monotonic in the trip order (ACCEPTED 2 → STARTED 7 → ARRIVED 8 → GONE 9), so
     * the `<` comparisons read as "before that step".
     */
    private fun canDriverCancel(ord: Order?): Boolean {
        val o = ord ?: return false
        return when (o.from) {
            ORDER_CREATED_CLIENT -> o.state < ORDER_STATE_CHANGED_ARRIVED
            ORDER_CREATED_DRIVER -> o.state < ORDER_STATE_STARTED
            else -> false
        }
    }

    /**
     * Shown when the driver may NOT cancel this order himself (see [canDriverCancel]) — a
     * dispatcher/manager/admin order, or a client/taximeter order that is already past its cut-off
     * point. Shows the support number — the SAME number as the top-right support icon
     * (`cvDispatcher`, [Helper.dispatcherOrSupportNumber]: branch dispatcher first, brand support
     * line as fallback) — with a Call button, and nothing else.
     */
    private fun showContactSupportDialog() {
        if (_binding == null) return
        if (dialogContactSupport?.isShowing == true) return

        val number = Helper.dispatcherOrSupportNumber()
        if (number == null) {
            showToast(getString(R.string.not_assigned_dispatcher))
            return
        }

        _dialogContactSupportBinding = DialogCallDispatcherBinding.inflate(layoutInflater)
        val b = _dialogContactSupportBinding!!
        dialogContactSupport = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(b.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // The two weighted (0dp) Back/Call buttons collapse to a sliver at wrap_content width.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // The two sources arrive in different shapes — the branch number is already spaced
        // ("+998 55 516 19 19"), SUPPORT_PHONE_NUMBER is not ("+998555161919"). Normalise, then
        // format once, so the popup reads the same regardless of which one is active.
        b.tvInfo.text = Helper.formatPhoneNumber(number.replace(" ", ""))
        b.mcvCall.setDebouncedClickListener {
            dialogContactSupport?.dismiss()
            call(number)
        }
        b.mcvCancel.setOnClickListener { dialogContactSupport?.dismiss() }
        dialogContactSupport?.show()
    }

    private var orderCancelReasonAdapter: OrderCancelReasonAdapter? = null
    private var listOrderCancelReason: List<OrderCancelReason>? = ArrayList()
    private var orderCanselReasonId: Int? = null
    private fun getOrderCanselReasons() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.getOrderCancelReasons().collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        listOrderCancelReason = it.data?.data ?: emptyList()
                        showDialogOrderCancel()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    private fun showDialogOrderCancel() {
        if (dialogOrderCancel?.isShowing == true) return
        // iOS-style bottom sheet (matches the services sheet) instead of a centered card.
        dialogOrderCancel =
            com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
                .fitSystemBars()
        _dialogOrderCancelBinding = DialogOrderCancelBinding.inflate(layoutInflater)
        dialogOrderCancel!!.setContentView(dialogOrderCancelBinding.root)

        // Each cancel starts with a fresh, unselected list and a disabled submit. Copies, not an
        // in-place isChecked flip: ListAdapter.submitList() no-ops on the SAME list instance, so
        // mutating the items the adapter already holds is invisible to it.
        orderCanselReasonId = null
        listOrderCancelReason = listOrderCancelReason?.map { it.copy(isChecked = false) }

        dialogOrderCancelBinding.apply {
            orderCancelReasonAdapter = OrderCancelReasonAdapter(this@MapFragment)
            // Reasons render as wrap-content chips, so they have to flow and wrap onto as many
            // rows as they need — a LinearLayoutManager would stack one stretched chip per line.
            rvReasons.layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
                justifyContent = JustifyContent.FLEX_START
            }
            rvReasons.adapter = orderCancelReasonAdapter
            orderCancelReasonAdapter?.submitList(listOrderCancelReason ?: emptyList())

            // Confirm stays disabled (gray, per bg_submit_state) until a reason is picked; the
            // toast below is the fallback for the brief window before the first bind.
            tvSubmit.isEnabled = false

            // Single Confirm button (sheet dismisses by swipe / tap-outside). Guarded: if no reason
            // is picked yet, prompt to choose one instead of cancelling.
            tvSubmit.setDebouncedClickListener {
                if (orderCanselReasonId != null) {
                    // Keep the sheet open — orderCansel shows an in-button spinner and dismisses
                    // only once the backend responds (success), or restores the button on error.
                    orderCansel()
                } else {
                    showToast(getString(R.string.select_reason))
                }
            }
        }

        dialogOrderCancel?.show()
    }

    private fun orderCansel() {
        // The cancel-reason Submit tap can outlive the view — guard viewLifecycleOwner and the
        // order/reason (order!!/reasonId!! could be null after a concurrent teardown) before launch.
        if (!isAdded || view == null) return
        val orderId = order?.id ?: return
        val reasonId = orderCanselReasonId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.orderCancel(orderId, reasonId).collect {
                when (it) {
                    is Resource.Loading -> setCancelSubmitLoading(true)

                    is Resource.Success -> {
                        dialogOrderCancel?.dismiss()
                        // The order is gone — so are its prompts. Cancelling used to leave the
                        // "Oxirgi manzil! / Yakunlaysizmi?" modal (and the mid-stop one) standing
                        // over the map, and neither can be tapped away.
                        endedOrderId = orderId
                        dismissTripPrompts()
                        showToast(getString(R.string.your_order_cancelled))
                        // The cancelled order is over — tear down its tracking explicitly so the
                        // NEXT order's beginTracking starts clean (cancel never sent
                        // ACTION_STOP_TRACKING before, leaving isTracking/orderId/flags stale).
                        if (MyTrackingService.isTracking.value == true) {
                            commandStopTracking()
                        }
                        getActiveMyOrders()
                    }

                    is Resource.Error -> {
                        // Restore the button so the driver can retry; keep the sheet open.
                        setCancelSubmitLoading(false)
                        showToast(Helper.humanizeServerError(it.message))
                    }
                }
            }
        }
    }

    /** In-button spinner on the cancel sheet's Confirm button while the request is in flight:
     *  hide the label + show the ProgressBar, restore on error. */
    private fun setCancelSubmitLoading(loading: Boolean) {
        val b = _dialogOrderCancelBinding ?: return
        b.tvSubmit.text = if (loading) "" else getString(R.string.submit)
        b.tvSubmit.isEnabled = !loading
        b.pbSubmit.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onOrderCancelReasonClick(orderCancelReason: OrderCancelReason) {
        // Single-select, non-deselecting (iOS parity): tapping the chosen chip
        // again keeps it selected rather than clearing it.
        // A NEW list of copies each time, so DiffUtil sees the isChecked change and rebinds just
        // the two affected chips — the old in-place mutation + notifyDataSetChanged() redrew the
        // whole group on every tap.
        val updated =
            listOrderCancelReason?.map { it.copy(isChecked = it.id == orderCancelReason.id) }
                ?: return
        listOrderCancelReason = updated
        orderCanselReasonId = orderCancelReason.id
        orderCancelReasonAdapter?.submitList(updated)
        _dialogOrderCancelBinding?.tvSubmit?.isEnabled = true
    }

    // Toggle wait time
    private fun toggleWaitTimeService() {
        if (isWaiting) {
            commandStopTimeWait()
        } else {
            // Block starting paid waiting while the car is still rolling (iOS parity).
            val speed = MyTrackingService.speedCar.value ?: 0
            if (speed > Constants.ON_ROUTE_WAIT_MAX_START_SPEED_KMH) {
                showInfoPopup(
                    R.drawable.ic_error_circle,
                    R.string.wait_stop_car_first,
                    R.string.wait_stop_car_hint
                )
                return
            }
            commandStartTimeWait()
        }
    }

    /** Reusable centered info/warning popup (replaces bare toasts for guard rails). */
    private var infoPopup: Dialog? = null
    private fun showInfoPopup(
        iconRes: Int,
        titleRes: Int,
        messageRes: Int? = null,
        autoDismissMs: Long? = null
    ) {
        if (_binding == null) return
        // The map is Activity-hosted and stays active while another screen covers it; a service-driven
        // notice (order cancelled / wait auto-stopped) must NOT pop as a dialog over that covering
        // screen. Guard-rail popups are triggered by taps on the map itself, so the map is front then.
        // Any state change that accompanies a notice (route teardown, refetch) runs at the call site,
        // outside this method, so suppressing the dialog here doesn't drop it.
        if (!mapIsFront) return
        infoPopup?.dismiss()
        val popupBinding = DialogInfoPopupBinding.inflate(layoutInflater)
        infoPopup = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(popupBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            // Dismiss only via the OK button (or the auto-dismiss timer) — block back + outside tap
            // so the driver can't accidentally swipe the notice away without reading it.
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
        popupBinding.ivIcon.setImageResource(iconRes)
        popupBinding.tvTitle.setText(titleRes)
        if (messageRes != null) {
            popupBinding.tvMessage.visibility = View.VISIBLE
            popupBinding.tvMessage.setText(messageRes)
        } else {
            popupBinding.tvMessage.visibility = View.GONE
        }
        popupBinding.btnOk.setOnClickListener { infoPopup?.dismiss() }
        infoPopup?.show()
        // Force a comfortable width (~88% of the screen) + wrap height AFTER show() so the window
        // LayoutParams actually take effect. Setting them before show() is unreliable — when it
        // doesn't apply, the match_parent card collapses to a narrow column (title breaks
        // mid-word, message + OK button get clipped). After show() the window exists, so it sticks.
        infoPopup?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        // Auto-dismiss for non-blocking notices (e.g. ride auto-started while
        // the driver is already moving — they shouldn't have to tap a button).
        if (autoDismissMs != null) {
            popupBinding.root.postDelayed({ infoPopup?.dismiss() }, autoDismissMs)
        }
    }

    private fun commandStartTimeWait() {
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_START_TIME_WAIT
            requireContext().startService(it)
        }
    }

    /** Tell the service the driver reached the pickup, so it can auto-start the ride. */
    private fun commandArrivedAtPickup(orderId: Int) {
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = Constants.ACTION_ARRIVED_AT_PICKUP
            // Scopes the arrival to ITS order — the service ignores a stale one.
            it.putExtra("order_id", orderId)
            requireContext().startService(it)
        }
    }

    private fun commandStopTimeWait() {
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = ACTION_STOP_TIME_WAIT
            requireContext().startService(it)
        }
    }

    /**
     * Two-line waiting meter (iOS parity). Pickup wait stays in [tvWaitedTime];
     * once the rider is aboard (CHANGED_GONE) it FREEZES at the pickup slice and
     * the live on-way wait counts in [tvWaitOnWay]. The active line is amber.
     */
    private fun updateWaitMeterDisplay(totalWaitMs: Long) {
        binding.includeDialog.apply {
            if (order?.state == ORDER_STATE_CHANGED_GONE) {
                val untilGone = MyTrackingService.waitedTimeUntilGone
                tvWaitedTime.text = Helper.formatTime(untilGone)
                tvWaitedTime.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.black_white)
                )
                tvWaitOnWay.text =
                    Helper.formatTime((totalWaitMs - untilGone).coerceAtLeast(0))
            } else {
                tvWaitedTime.text = Helper.formatTime(totalWaitMs)
                tvWaitedTime.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isWaiting) R.color.app_color else R.color.black_white
                    )
                )
            }
        }
    }

    private fun updateWaitTime() {
        if (_binding != null) {
            // iOS waiting control: red "To'xtatish" while waiting, amber "Kutish" otherwise.
            binding.includeDialog.cvWaitToggle.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isWaiting) R.color.red else R.color.app_color
                )
            )
            binding.includeDialog.tvWaitToggle.text =
                getString(if (isWaiting) R.string.to_stop else R.string.waiting)
            // Amber "Kutish" follows the CTA convention (white in light / dark surface in
            // dark); the red "To'xtatish" state stays white for contrast on red.
            val waitFg = ContextCompat.getColor(
                requireContext(),
                if (isWaiting) R.color.white else R.color.white_black
            )
            binding.includeDialog.tvWaitToggle.setTextColor(waitFg)
            binding.includeDialog.ivWaitToggle.setColorFilter(waitFg)
        }
        if (isWaiting) {
            if (!Constants.WAITING_TIME_TURN_AUTO) {
                binding.includeDialog.tvWaitTimeTitle.text = getString(R.string.to_stop)
            }
            binding.includeDialog.ivWaitRound.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.app_color
                )
            )
            binding.includeDialog.ivWaitIcon.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.includeDialog.tvWaitTimeTitle.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.app_color
                )
            )

        } else {
            if (!Constants.WAITING_TIME_TURN_AUTO) {
                binding.includeDialog.tvWaitTimeTitle.text = getString(R.string.waiting)
            }
            binding.includeDialog.ivWaitRound.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.gray_light
                )
            )
            binding.includeDialog.ivWaitIcon.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.black_light
                )
            )
            binding.includeDialog.tvWaitTimeTitle.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.black_white
                )
            )
        }
    }
}