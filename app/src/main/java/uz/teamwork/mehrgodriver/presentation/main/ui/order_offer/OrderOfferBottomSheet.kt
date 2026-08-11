package uz.teamwork.mehrgodriver.presentation.main.ui.order_offer

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.RouteDistanceFetcher
import uz.teamwork.mehrgodriver.common.RoutePointsBinder
import uz.teamwork.mehrgodriver.common.asSumString
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.databinding.DialogBshOrderOfferBinding
import uz.teamwork.mehrgodriver.domain.model.Order

/**
 * Incoming-order offer sheet — the Android twin of the iOS `OrderOfferSheet`.
 *
 * Review mode (`hasCountdown = false`): a pure "review + Accept/Skip" surface
 * opened from the orders pool. Live mode (`hasCountdown = true`): adds the
 * depleting countdown ring and auto-skips on expiry.
 *
 * Visual + timing only — the host performs accept/skip through its own
 * ViewModel via [onAccept] / [onSkip], then drives [setLoading] and dismisses.
 */
class OrderOfferBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogBshOrderOfferBinding? = null
    private val binding get() = _binding!!

    private lateinit var order: Order
    private var hasCountdown: Boolean = false
    private var isPrivate: Boolean = false

    private var countdown: CountDownTimer? = null
    private var accepting: Boolean = false

    /** Host runs OrderAcceptUC; it should call [setLoading] then dismiss. */
    var onAccept: ((Order) -> Unit)? = null

    /** Host runs OrderSkipUC; close on both success and error (iOS parity). */
    var onSkip: ((Order) -> Unit)? = null

    override fun getTheme(): Int = R.style.AppBottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).fitSystemBars()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBshOrderOfferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // [onAccept] / [onSkip] are plain instance lambdas the host assigns after newInstance(),
        // and nothing re-wires them. A sheet the FragmentManager restores by itself — rotation,
        // the theme/locale recreate, process death — therefore comes back with both null: the
        // order survives in the arguments so the sheet renders in full, but Accept silently does
        // nothing while the offer countdown runs out. Closing it is honest; a dead Accept button
        // on a timed offer is not. The host still has the order in its pool.
        if (onAccept == null || onSkip == null) {
            dismissAllowingStateLoss()
            return
        }

        order = BundleCompat.getParcelable(requireArguments(), ARG_ORDER, Order::class.java)!!
        hasCountdown = requireArguments().getBoolean(ARG_HAS_COUNTDOWN, false)
        isPrivate = requireArguments().getBoolean(ARG_IS_PRIVATE, false)

        bind()
        wireActions()
        if (hasCountdown) startCountdown()
    }

    override fun onStart() {
        super.onStart()
        // Open as a single full page — skip the collapsed peek so all detail shows at once,
        // without scrolling.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    // ----- binding ------------------------------------------------------

    private fun bind() = binding.run {
        // Header — private vs public differentiation.
        if (isPrivate) {
            ivOfferIcon.setImageResource(R.drawable.ph_user_circle)
            tvOfferTitle.setText(R.string.individual_order)
            tvOfferTitle.setTextColor(color(R.color.app_color))
        } else {
            ivOfferIcon.setImageResource(R.drawable.ph_car)
            tvOfferTitle.setText(R.string.new_order)
            tvOfferTitle.setTextColor(color(R.color.black_white))
        }

        // Route — pickup, ALL intermediate stops, then dropoff. Address only (no venue/district sub).
        val pickup = pickupAddress()
        tvPickup.text = pickup ?: "—"
        tvPickupSub.visibility = View.GONE
        RoutePointsBinder.bindMiddle(
            llMiddlePoints,
            order.locations.drop(1).dropLast(1)
                .map { (it.name ?: "").ifEmpty { getString(R.string.not_showed) } }
        )
        val dropoff = dropoffAddress()
        if (dropoff != null) {
            llDropoff.visibility = View.VISIBLE
            tvDropoff.text = dropoff
            tvDropoffSub.visibility = View.GONE
        } else {
            llDropoff.visibility = View.GONE
        }

        // Passenger card hidden — the client's name is not shown on a new-order offer.
        cardPassenger.visibility = View.GONE

        // Meta — payment, tariff, info, badges. The payment chip is colour-coded like the
        // trip detail: cash = green on green_soft, card = blue on blue_soft.
        val payColor: Int
        val paySoft: Int
        if (order.isCardPayment == true) {
            ivPayment.setImageResource(R.drawable.ph_credit_card)
            tvPayment.setText(R.string.payment_type_card)
            payColor = ContextCompat.getColor(requireContext(), R.color.blue_middle)
            paySoft = ContextCompat.getColor(requireContext(), R.color.blue_soft)
        } else {
            ivPayment.setImageResource(R.drawable.ph_money)
            tvPayment.setText(R.string.payment_type_cash)
            payColor = ContextCompat.getColor(requireContext(), R.color.green)
            paySoft = ContextCompat.getColor(requireContext(), R.color.green_soft)
        }
        ivPayment.setColorFilter(payColor)
        tvPayment.setTextColor(payColor)
        llPaymentChip.backgroundTintList = ColorStateList.valueOf(paySoft)
        val tariffName = order.tariff.name
        if (!tariffName.isNullOrBlank()) {
            tvTariff.visibility = View.VISIBLE
            tvTariff.text = tariffName
        } else {
            tvTariff.visibility = View.GONE
        }
        val info = order.info
        if (!info.isNullOrBlank()) {
            llInfo.visibility = View.VISIBLE
            tvInfo.text = info
        } else {
            llInfo.visibility = View.GONE
        }

        // Services / wishes (air conditioner, baggage, …) — same chips as the order card.
        val services = order.services ?: emptyList()
        chipGroupServices.removeAllViews()
        // The chips live in their own labelled card now — hide the card, not just the group,
        // or an empty labelled box is left behind.
        llServices.visibility = if (services.isEmpty()) View.GONE else View.VISIBLE
        for (service in services) {
            val chip = layoutInflater.inflate(R.layout.adapter_chip, null, false) as TextView
            chip.text = service.service.name
            chipGroupServices.addView(chip)
        }

        tvBadgeBonus.visibility = if (order.useBonus) View.VISIBLE else View.GONE
        llBadges.visibility = if (tvBadgeBonus.isVisible()) View.VISIBLE else View.GONE

        // Price + additional surge (↑) — same data types the order list header shows.
        tvOfferPrice.text = order.price.asSumString()
        // Show the surge row only for a real surge — the server ships 0 for plain orders.
        val addPrice = order.addPrice
        if (addPrice != null && addPrice > 0) {
            llOfferAddPrice.visibility = View.VISIBLE
            tvOfferAddPrice.text = "${addPrice.asSumString()} ${getString(R.string.sum)}"
        } else {
            llOfferAddPrice.visibility = View.GONE
        }
        // Whole-route trip distance removed from order details per request — keep hidden.
        llOfferDistance.visibility = View.GONE

        // Distance + time to the pickup (driver → first point) — hidden until the driver location is
        // known. Same 30 km/h (= 500 m/min) ETA as the order list / new-order view.
        val driverLoc = MyTrackingService.lastLatLngWholeApp.value
        val pickupLoc = order.locations.firstOrNull()
        if (driverLoc != null && pickupLoc != null) {
            // Instant straight-line estimate first (no network wait)…
            val m = Helper.calculateBetweenTwoPoints(
                driverLoc, LatLng(pickupLoc.latitude, pickupLoc.longitude)
            ).toInt()
            llOfferToPickup.visibility = View.VISIBLE
            applyToPickup(m)
            // …then refresh with the ROAD distance from the route server — the order-list row
            // shows the road number, so the sheet must match it (straight-line read shorter,
            // e.g. 18.2 km on the sheet vs 22.2 km on the card for the same order).
            RouteDistanceFetcher.fetch(
                driverLoc, LatLng(pickupLoc.latitude, pickupLoc.longitude)
            ) { roadMetres ->
                if (roadMetres != null && _binding != null) applyToPickup(roadMetres)
            }
        } else {
            llOfferToPickup.visibility = View.GONE
        }
    }

    /** Renders the to-pickup row for [metres] with the shared 30 km/h (= 500 m/min) ETA. */
    private fun applyToPickup(metres: Int) = binding.run {
        tvOfferToPickupDistance.text =
            if (metres < 1000) "$metres ${getString(R.string.metre)}"
            else "${Helper.metreToRoundKm(metres.toString())} ${getString(R.string.km)}"
        val minutes = Math.round(metres / 500.0).toInt()
        tvOfferToPickupTime.text =
            if (minutes < 1) "<1 ${getString(R.string.minute)}"
            else "~$minutes ${getString(R.string.minute)}"
    }

    private fun wireActions() = binding.run {
        btnAcceptContainer.setOnClickListener {
            if (accepting) return@setOnClickListener
            onAccept?.invoke(order)
        }
        if (hasCountdown) {
            // Live offer: a real decline goes through the host's skip use-case.
            btnSkip.setText(R.string.skip)
            btnSkip.setOnClickListener {
                if (accepting) return@setOnClickListener
                isCancelable = true
                countdown?.cancel()
                onSkip?.invoke(order)
            }
        } else {
            // Pool review: "Close" just dismisses the sheet — the order is NOT re-skipped.
            btnSkip.setText(R.string.close)
            btnSkip.setOnClickListener {
                if (accepting) return@setOnClickListener
                dismiss()
            }
        }
    }

    private fun startCountdown() {
        val totalSec = order.branch.acceptWaiting ?: Constants.DEFAULT_ACCEPT_WAIT_TIME
        isCancelable = false
        binding.countdownRing.visibility = View.VISIBLE
        binding.countdownRing.setTime(totalSec, totalSec)
        countdown = object : CountDownTimer(totalSec * 1000L, 1000L) {
            override fun onTick(msUntilFinished: Long) {
                val remaining = Math.ceil(msUntilFinished / 1000.0).toInt()
                binding.countdownRing.setTime(remaining, totalSec)
            }

            override fun onFinish() {
                binding.countdownRing.setTime(0, totalSec)
                // Auto-skip only if no accept is in flight (iOS race guard).
                if (!accepting) {
                    isCancelable = true
                    onSkip?.invoke(order)
                }
            }
        }.start()
    }

    /** Host toggles this around its accept request. */
    fun setLoading(loading: Boolean) {
        accepting = loading
        if (_binding == null) return
        isCancelable = !loading && !(hasCountdown)
        binding.pbAccept.visibility = if (loading) View.VISIBLE else View.GONE
        binding.llAcceptContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        binding.btnAcceptContainer.isEnabled = !loading
        binding.btnSkip.isEnabled = !loading
    }

    // ----- helpers ------------------------------------------------------

    // Prefer the route-point names (order.locations) so the pickup/dropoff read the SAME as the
    // order-list view; fall back to the address-category name only when a location has no name.
    private fun pickupAddress(): String? =
        order.locations.firstOrNull()?.name?.ifEmpty { null } ?: order.address?.name

    private fun dropoffAddress(): String? =
        order.locations.takeIf { it.size > 1 }?.lastOrNull()?.name?.ifEmpty { null }
            ?: order.addressFinish?.name

    private fun color(res: Int) =
        androidx.core.content.ContextCompat.getColor(requireContext(), res)

    private fun View.isVisible() = visibility == View.VISIBLE

    override fun onDestroyView() {
        super.onDestroyView()
        countdown?.cancel()
        countdown = null
        _binding = null
    }

    companion object {
        private const val ARG_ORDER = "arg_order"
        private const val ARG_HAS_COUNTDOWN = "arg_has_countdown"
        private const val ARG_IS_PRIVATE = "arg_is_private"

        private const val TRUSTED_DISTANCE_FLOOR_METERS = 100.0

        fun newInstance(
            order: Order,
            hasCountdown: Boolean = false,
            isPrivate: Boolean = false
        ): OrderOfferBottomSheet = OrderOfferBottomSheet().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_ORDER, order)
                putBoolean(ARG_HAS_COUNTDOWN, hasCountdown)
                putBoolean(ARG_IS_PRIVATE, isPrivate)
            }
        }
    }
}
