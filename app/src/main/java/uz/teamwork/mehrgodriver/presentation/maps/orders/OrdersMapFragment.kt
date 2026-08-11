package uz.teamwork.mehrgodriver.presentation.maps.orders

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SEND_ORDER_DATA_BY_BROADCAST
import uz.teamwork.mehrgodriver.common.Constants.KEY_ORDER_DATA
import uz.teamwork.mehrgodriver.common.Constants.ORDER_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_CANCELLED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_NEW
import uz.teamwork.mehrgodriver.common.InfoPopup
import uz.teamwork.mehrgodriver.common.PendingDeepLink
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.common.socket.SocketOrderResponse
import uz.teamwork.mehrgodriver.databinding.DialogBshChooseTariffBinding
import uz.teamwork.mehrgodriver.databinding.FragmentOrdersMapBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.Tariff
import uz.teamwork.mehrgodriver.domain.model.requests.OrderCreateRequest
import uz.teamwork.mehrgodriver.presentation.main.adapter.OrderAdapter
import uz.teamwork.mehrgodriver.presentation.main.adapter.TariffAdapter
import uz.teamwork.mehrgodriver.presentation.main.ui.order_offer.OrderOfferBottomSheet
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment
import javax.inject.Inject

@AndroidEntryPoint
class OrdersMapFragment : Fragment(), OrderAdapter.OnOrderClickListener,
    TariffAdapter.OnTariffClickListener {
    private var _binding: FragmentOrdersMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrdersMapViewModel by viewModels()

    private var _dialogBshChooseTariffBinding: DialogBshChooseTariffBinding? = null
    private val dialogBshChooseTariffBinding get() = _dialogBshChooseTariffBinding!!
    private var dialogBshChooseTariff: Dialog? = null

    private var tariffAdapter: TariffAdapter? = null
    private var tariffs: List<Tariff> = emptyList()
    private var selectedTariff: Tariff? = null

    @Inject
    lateinit var gson: Gson

    private var orderAdapter: OrderAdapter? = null

    private val orderDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra(KEY_ORDER_DATA)

            // Drop a malformed / string-typed numeric frame instead of crashing the pool-orders
            // map (crash A). gson is the lenient shared instance; this guards the non-JSON case.
            val response = try {
                gson.fromJson(data, SocketOrderResponse::class.java)
            } catch (e: Exception) {
                return
            } ?: return
            if (response.key == ORDER_NEW || response.key == ORDER_ACCEPTED || response.key == ORDER_CANCELLED) {
                getOrders()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersMapBinding.inflate(inflater, container, false)

        orderAdapter =
            OrderAdapter(this@OrdersMapFragment, MyTrackingService.lastLatLngWholeApp.value)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialogBshChooseTariff = BottomSheetDialog(requireContext()).fitSystemBars()
        _dialogBshChooseTariffBinding =
            DialogBshChooseTariffBinding.inflate(LayoutInflater.from(requireContext()))

        tariffAdapter = TariffAdapter(this@OrdersMapFragment)

        if (!CheckPermissions.isGPSEnabled(requireContext())) {
            enableGPS()
        }

        // Attached once, here — never inside the load result. DiffUtil then dispatches granular
        // updates and the driver's scroll position survives a refresh.
        binding.rvOrders.adapter = orderAdapter
        ordersLoadedOnce = false

        binding.mcvBack.setDebouncedClickListener {
            findNavController().navigateUp()
        }

        binding.mcvTachometer.setDebouncedClickListener {
            if (MyTrackingService.isServiceRunning.value == true) {
                tariffs = emptyList()
                selectedTariff = null
                getTariffs(UserManager.getUser()!!.branch!!.id)
            } else {
                showToast(getString(R.string.pls_start_job))
            }
        }

        getOrders()
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(ACTION_SEND_ORDER_DATA_BY_BROADCAST)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(orderDataReceiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(orderDataReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        orderAdapter = null

        // The tariff sheet is re-created unconditionally in onViewCreated, so without this every
        // view recreation minted a new dialog and orphaned the previous one's window.
        dialogBshChooseTariff?.dismiss()
        _dialogBshChooseTariffBinding = null
        dialogBshChooseTariff = null
    }

    // Functions

    var orders: List<Order> = emptyList()

    /** In-flight pool refresh, so bursts can be coalesced instead of racing. */
    private var ordersJob: Job? = null

    /** A refresh arrived while one was already running — run exactly one more when it lands. */
    private var refreshQueued = false

    /** First load blanks the screen; every later refresh repaints in place. */
    private var ordersLoadedOnce = false

    /**
     * ORDER_NEW / ORDER_ACCEPTED / ORDER_CANCELLED are **branch-wide** keys — they fire for every
     * order any client in the branch creates and every order any other driver accepts, so this can
     * be re-entered several times a second.
     *
     * Two things used to go wrong. Each frame launched an independent collect with nothing
     * cancelling the previous one, so N requests raced and whichever finished LAST won — which
     * could resurrect an order the driver had just skipped. And each frame blanked the whole
     * screen to a spinner and reassigned `rvOrders.adapter`, which clears the recycled-view pool
     * and re-anchors the layout manager at position 0: a driver reading order #7 got snapped to
     * the top before he could tap it.
     *
     * Coalescing rather than cancel-and-relaunch is deliberate: with frames arriving faster than
     * the request completes, cancelling the previous one every time would starve the list and it
     * would never refresh at all.
     */
    private fun getOrders() {
        // Off-shift driver (work not started) can't accept broadcast orders — list none and show the
        // "start work first" hint. Gate on the ON-SHIFT state (isServiceRunning), NOT the DRIVER_ACTIVE
        // account status: an active driver who simply hasn't started a shift must still see no orders.
        if (MyTrackingService.isServiceRunning.value != true) {
            orders = emptyList()
            orderAdapter?.submitList(orders)
            screenVisible()
            binding.tvOrdersEmpty.text = getString(R.string.enable_start_work_button)
            // Burn a pending deep link here too. The empty state already explains that the shift has
            // to be started; leaving the id set would send MainActivity's retry back to this screen
            // every time the driver backed out to the map.
            PendingDeepLink.clear()
            return
        }
        if (ordersJob?.isActive == true) {
            refreshQueued = true
            return
        }

        ordersJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllOrders().collect {
                if (_binding == null) return@collect
                when (it) {
                    is Resource.Loading -> {
                        // Only the FIRST load may blank the screen. Doing it on every branch-wide
                        // frame made the pool strobe continuously.
                        if (!ordersLoadedOnce) loadingVisible()
                    }

                    is Resource.Success -> {
                        ordersLoadedOnce = true
                        orders = it.data?.data ?: emptyList()
                        // Reset the empty message: after an off-shift visit it still said
                        // "start work first" even though the driver is now on shift.
                        binding.tvOrdersEmpty.text = getString(R.string.there_are_not_orders)
                        screenVisible()

                        // Adapter is attached ONCE in onViewCreated — see the note above on why
                        // reassigning it here destroyed scroll position.
                        orderAdapter?.submitList(orders)

                        openDeepLinkOrderIfPending()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        errorVisible()
                        // The pool never loaded, so the offer can't be opened — drop the link rather
                        // than have it fire against a stale list on some later refresh.
                        PendingDeepLink.clear()
                    }
                }
            }

            // The request finished; run the one refresh that arrived while it was in flight.
            if (refreshQueued) {
                refreshQueued = false
                getOrders()
            }
        }
    }

    private fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
            llOrdersEmpty.visibility = View.GONE
        }
    }

    private fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE

            if (orders.isEmpty()) {
                llOrdersEmpty.visibility = View.VISIBLE
            } else {
                llOrdersEmpty.visibility = View.GONE
            }
        }
    }

    private fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE

            if (orders.isEmpty()) {
                llOrdersEmpty.visibility = View.VISIBLE
            } else {
                llOrdersEmpty.visibility = View.GONE
            }
        }
    }

    override fun onOrderClick(orderId: Int) {
        if (MyTrackingService.isServiceRunning.value != true) {
            showToast(getString(R.string.enable_start_work_button))
            return
        }
        // iOS parity: tapping a pool row opens a review sheet (Accept / Skip),
        // it no longer accepts immediately.
        val order = orders.firstOrNull { it.id == orderId } ?: return
        showOfferSheet(order)
    }

    /**
     * Open the offer sheet for an order that came in through the Telegram link (see
     * [PendingDeepLink]). Runs once the pool has actually resolved, because the sheet renders from a
     * full [Order] and there is no "fetch one order by id" endpoint — the driver's own pool
     * (`order/list`) is the only place that object exists.
     *
     * The id is consumed either way: an order that is no longer in the pool was taken by another
     * driver, cancelled, or never belonged to this driver, and the bot has no way of knowing that.
     */
    private fun openDeepLinkOrderIfPending() {
        val orderId = PendingDeepLink.consume() ?: return
        val order = orders.firstOrNull { it.id == orderId }
        if (order != null) {
            showOfferSheet(order)
        } else {
            InfoPopup.show(
                context = requireContext(),
                title = getString(R.string.order_already_taken),
                message = getString(R.string.order_already_taken_info),
                lifecycle = viewLifecycleOwner.lifecycle
            )
        }
    }

    private fun showOfferSheet(order: Order) {
        val sheet = OrderOfferBottomSheet.newInstance(order, hasCountdown = false)
        sheet.onAccept = { o -> acceptFromSheet(o, sheet) }
        sheet.onSkip = { o -> skipFromSheet(o, sheet) }
        sheet.show(childFragmentManager, "order_offer")
    }

    private fun acceptFromSheet(order: Order, sheet: OrderOfferBottomSheet) {
        lifecycleScope.launch {
            viewModel.orderAccept(order.id).collect {
                when (it) {
                    is Resource.Loading -> sheet.setLoading(true)

                    is Resource.Success -> {
                        sheet.dismissAllowingStateLoss()
                        // Tell the map screen to open the trip detail expanded (not the minimised
                        // resume card) for the order we just accepted.
                        MapFragment.openTripDetailOnNextLoad = true
                        findNavController().navigate(R.id.action_ordersMapFragment_to_mapFragment)
                    }

                    is Resource.Error -> {
                        // Accept failed (order already taken / expired, or the driver already has an
                        // active order not yet started). CLOSE the sheet and surface the server
                        // reason in a clear popup — a toast is too easy to miss for a state the
                        // driver must act on.
                        sheet.dismissAllowingStateLoss()
                        // Collected on the fragment (not view) scope, so guard against the view
                        // being gone before anchoring the popup to viewLifecycleOwner.
                        if (_binding != null) {
                            InfoPopup.show(
                                context = requireContext(),
                                title = getString(R.string.order_not_accepted),
                                message = it.message,
                                lifecycle = viewLifecycleOwner.lifecycle,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun skipFromSheet(order: Order, sheet: OrderOfferBottomSheet) {
        // iOS parity: skip closes the sheet and drops the row on both success
        // and error; the row leaves the pool either way.
        sheet.dismissAllowingStateLoss()
        orders = orders.filter { it.id != order.id }
        orderAdapter?.submitList(orders)
        screenVisible()
        lifecycleScope.launch {
            viewModel.orderSkip(order.id).collect { /* fire-and-forget; UI already updated */ }
        }
    }

    private fun sendSmsToClient(order: Order?, clientPhoneNumber: String) {
        val carColor = order?.car?.carColor?.name ?: ""
        val carNumber = order?.car?.carNumber ?: ""
        val carModel = order?.car?.carModel?.name ?: ""
        val driverPhoneNumber = order?.driverNumber ?: ""

        val message =
            "${getString(R.string.app_name_capital)}: Sizga $carColor $carNumber $carModel mashina belgilandi. Haydovchi: $driverPhoneNumber. Ilovamizni yuklab oling: elgataxi.uz/app"

        if (CheckPermissions.checkSendSmsPermission(requireContext())) {
            val smsManager: SmsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(clientPhoneNumber, null, message, null, null)
        }
    }

    private fun orderCreate(request: OrderCreateRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.orderCreate(request).collect {
                when (it) {
                    is Resource.Loading -> {
                        dialogBshChooseTariffBinding.llContent.visibility = View.INVISIBLE
                        dialogBshChooseTariffBinding.progressBar.visibility = View.VISIBLE
                    }

                    is Resource.Success -> {
                        dialogBshChooseTariff?.dismiss()
                        findNavController().navigateUp()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        dialogBshChooseTariffBinding.llContent.visibility = View.VISIBLE
                        dialogBshChooseTariffBinding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun getTariffs(branchId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getTariffs(branchId).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        val tariffsList = it.data?.data ?: emptyList()
                        tariffs = tariffsList
                        showDialogBshChooseTariff(tariffsList)
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        errorVisible()
                    }
                }
            }
        }
    }

    /**
     * Make sure a fix young enough to BOOK AN ORDER AT is available, so taximeter creation
     * neither dead-ends on the "wait while your location is determined" toast nor books at a
     * position the car has left. The service's fixes arrive only every 20 s / 100 m (and its
     * 5 s stream is armed only during an order), so a parked driver relies on this: the cached
     * fused fix when it is fresh, an actively requested one when it is not.
     */
    @SuppressLint("MissingPermission")
    private fun primeLastKnownLocation() {
        if (MyTrackingService.freshWholeAppFix() != null) return
        if (!CheckPermissions.checkLocationPermission(requireContext())) return
        val client = LocationServices.getFusedLocationProviderClient(requireContext())
        client.lastLocation.addOnSuccessListener { location ->
            // Freshness gate: unlike AutoOfferService's prime (display only), this value becomes
            // the PICKUP COORDINATES of a created taximeter order — a cached fix from hours ago
            // would book the order kilometres from the car.
            if (location != null && System.currentTimeMillis() - location.time <= FIX_MAX_AGE_MS) {
                adoptFix(location)
                return@addOnSuccessListener
            }
            // Cached fix missing or stale. A parked driver produces no new passive fixes, so
            // waiting for one would dead-end the create button — ask the GPS for a current one.
            requestCurrentFix(client)
        }
    }

    /** Active one-shot fix, used when the cached one is too old to book an order at. */
    @SuppressLint("MissingPermission")
    private fun requestCurrentFix(client: FusedLocationProviderClient) {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { current -> current?.let { adoptFix(it) } }
    }

    private fun adoptFix(location: Location) {
        // Both halves: the MyLocation copy (with accuracy) is what the order/create request body
        // and the pickup coordinates are built from, the LiveData drives the map/UI. Publish the
        // LiveData only if the fix passed the service's glitch guard, so the two agree.
        if (MyTrackingService.rememberWholeAppFix(location)) {
            MyTrackingService.lastLatLngWholeApp.value =
                LatLng(location.latitude, location.longitude)
        }
    }

    private fun showDialogBshChooseTariff(tariffsList: List<Tariff>) {
        if (dialogBshChooseTariff?.isShowing == true) return
        // Warm the location up-front: by the time the driver picks a tariff and taps
        // create, the fix is usually already in.
        primeLastKnownLocation()
        dialogBshChooseTariff?.apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setContentView(dialogBshChooseTariffBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialogBshChooseTariffBinding.apply {
            rvTariffs.adapter = tariffAdapter
            tariffAdapter?.submitList(tariffsList)
            // Create button is disabled until the driver picks a tariff.
            setCreateEnabled(selectedTariff != null)

            mcvOrderCreate.setDebouncedClickListener {
                if (selectedTariff != null) {
                    // Book at a FRESH fix only. The map may still be showing an old position
                    // (AutoOfferService primes the LiveData with an any-age cached fix), and
                    // that point becomes the order's pickup — where the fare starts counting.
                    val fix = MyTrackingService.freshWholeAppFix()
                    if (fix != null) {
                        val locations = listOf(
                            OrderCreateRequest.OrderCreateLocation(
                                fix.latitude,
                                fix.longitude,
                                0,
                                ""
                            )
                        )

                        val request = OrderCreateRequest(
                            selectedTariff!!.id,
                            UserManager.getUser()!!.branch!!.id,
                            "",
                            selectedTariff!!.startingPrice,
                            selectedTariff!!.startingPrice,
                            0.0,
                            false,
                            emptyList(),
                            locations
                        )

                        orderCreate(request)
                    } else {
                        // No fix yet: tell the driver AND re-request one, so the next tap
                        // (or the auto-primed fix from the sheet opening) goes through.
                        showToast(getString(R.string.wait_taking_your_location))
                        primeLastKnownLocation()
                    }
                } else {
                    showToast(getString(R.string.pls_choose_tariff))
                }
            }
        }

        dialogBshChooseTariff?.show()
    }

    private fun enableGPS() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    override fun onTariffClick(tariff: Tariff) {
        selectedTariff = tariff
        val updatedGroups = tariffs.map { it.copy(isSelected = it.id == tariff.id) }
        tariffAdapter?.submitList(updatedGroups)
        setCreateEnabled(true)
    }

    /** Enable the "create order" CTA only once a tariff is picked. tvCreate drives the
     *  bg_submit_state selector (grey when disabled); the card's isEnabled blocks the tap. */
    private fun setCreateEnabled(enabled: Boolean) {
        _dialogBshChooseTariffBinding?.apply {
            mcvOrderCreate.isEnabled = enabled
            mcvOrderCreate.isClickable = enabled
            tvCreate.isEnabled = enabled
        }
    }

    companion object {
        // Oldest fix this screen will book a taximeter order at. Same window the tracking
        // service uses for its own prime.
        private const val FIX_MAX_AGE_MS = 5 * 60_000L
    }
}