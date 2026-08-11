package uz.teamwork.mehrgodriver.presentation.maps.yandex_map.trip_finish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.asHHMMSS
import uz.teamwork.mehrgodriver.common.asSumString
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.socket.MySocketListener
import uz.teamwork.mehrgodriver.databinding.FragmentTripFinishBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import uz.teamwork.mehrgodriver.domain.model.TripReceipt
import java.util.Locale

/**
 * Post-settlement trip receipt — Android twin of the iOS `TripFinishView`.
 * Renders the already-settled [TripReceipt] (no network call) and resets to a
 * fresh map on Done. System back is routed to the same Done action so the
 * terminal screen can't leave a stale order on the stack.
 */
@AndroidEntryPoint
class TripFinishFragment : Fragment() {

    private var _binding: FragmentTripFinishBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripFinishViewModel by viewModels()

    private lateinit var receipt: TripReceipt

    /** Set once the wait for a settlement is over without one — the figures are then labelled. */
    private var settlementGaveUp = false
    private var settleJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripFinishBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        receipt =
            BundleCompat.getParcelable(requireArguments(), ARG_RECEIPT, TripReceipt::class.java)!!

        bind()

        // The receipt is built from the SUBMITTED preview (agreed price for B-orders), but the
        // authoritative bill is the server's `order_completed` settlement frame — the complete
        // REST response carries no receipt at all. The frame usually races the navigation here
        // (MapFragment already consumed it if so); this observer covers the LATE arrival, and
        // LiveData replay covers a frame that landed in between. The LiveData is only the
        // change signal — the settled Order is read from the id-keyed store, so a frame for
        // ANOTHER order (branch-wide broadcasts) can neither trigger a wrong adoption nor
        // evict ours.
        MySocketListener.listenerOrderCompletedData.observe(viewLifecycleOwner) { _ ->
            val settled = MySocketListener.settledOrderFor(receipt.order.id) ?: return@observe
            // Positivity gates: lenient Gson coerces a malformed ""/null price/distance on the
            // non-null primitives to 0 — never let that beat the correct submitted figures.
            val gross = settled.price.takeIf { it > 0 }?.toLong() ?: return@observe
            val distanceKm = settled.distance.takeIf { it > 0.0 }
                ?.let { "%.2f".format(Locale.US, it) } ?: receipt.distanceKm
            // SERVER-applied discounts (the submitted ones were computed off the agreed
            // preview and drift on meter-billed trips); null on older payloads -> keep ours.
            val bonus = settled.bonusPayment ?: receipt.bonus
            val promo = settled.promoCodePayment ?: receipt.promo
            if (gross == receipt.grossTotal && distanceKm == receipt.distanceKm &&
                bonus == receipt.bonus && promo == receipt.promo
            ) return@observe

            // A REST settlement has already been adopted, and it is the stronger
            // source: it carries the server's own `final_price` and payment
            // method, which this frame does not have (`Order` has no such
            // fields). Taking the frame's gross/bonus/promo now would leave
            // `finalTotal` pinned to the old settled figure while the rows above
            // it moved — the column would stop footing and the driver would
            // collect a number the breakdown contradicts. Adopt the tracked
            // distance and nothing else.
            if (receipt.settledFinalTotal != null) {
                if (distanceKm != receipt.distanceKm) {
                    receipt = receipt.copy(distanceKm = distanceKm)
                    bind()
                }
                return@observe
            }
            // Only the settled numbers are adopted — receipt.order stays the build-time copy
            // (a partial frame could Gson-null non-null fields bind() dereferences).
            receipt = receipt.copy(
                grossTotal = gross,
                // Same clamp as at build time: parts exceeding the total is a bug, a negative
                // ride line on the driver's receipt is worse than a 0 one.
                rideCost = (gross - receipt.waitCost - receipt.servicesCost).coerceAtLeast(0L),
                distanceKm = distanceKm,
                bonus = bonus,
                promo = promo,
                settled = true
            )
            settleJob?.cancel()
            bind()
        }

        // The frame is the fast path but not a guaranteed one (socket down, missed frame). Until
        // a settlement exists the screen shows a spinner instead of the approved preview, and
        // history's `myOrder.payment` is polled as the REST fallback.
        // Gated on the SERVER'S OWN final price, not merely on `settled`. The
        // socket frame sets settled = true but carries no `final_price` and no
        // payment method, so gating on `settled` meant that whenever the frame
        // won the race — the documented usual case — history's `payment` block
        // was never fetched and the receipt silently fell back to
        // reconstructing the total. That is the rounding split this was
        // supposed to close.
        if (receipt.settledFinalTotal == null) awaitSettlement()

        binding.btnDone.setDebouncedClickListener { finishToMap() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finishToMap()
            }
        )
    }

    private fun bind() = binding.run {
        val order = receipt.order

        // Nothing that depends on the money is shown until the SERVER has settled it. The trip
        // facts (distance, duration, waiting, route) are the driver's own meter and are always
        // true, so they stay up — only the amounts wait.
        val awaitingBill = !receipt.settled && !settlementGaveUp
        pbSettling.visibility = if (awaitingBill) View.VISIBLE else View.GONE
        llFinalTotalRow.visibility = if (awaitingBill) View.GONE else View.VISIBLE
        cardBreakdown.visibility = if (awaitingBill) View.GONE else View.VISIBLE
        tvProvisional.visibility =
            if (settlementGaveUp && !receipt.settled) View.VISIBLE else View.GONE

        // Hero + chips. The figure is finalTotal = gross − promo − bonus, i.e. what the CLIENT
        // still owes — NOT the order total (that is the breakdown subtotal, and it is the number
        // order/history and `total_price` on order/complete carry). Labelling it "total price"
        // made the same trip read 19 130 here and 33 000 in history, so the label states what the
        // figure actually is; on a card order nothing is collected in cash, hence the branch.
        tvFinalTotal.text = collectedTotal().asSumString()
        val payableLabel = if (order.isCardPayment == true) R.string.amount_payable
        else R.string.amount_cash_to_collect
        tvFinalTotalLabel.setText(if (awaitingBill) R.string.receipt_settling else payableLabel)
        tvBreakdownTotalLabel.setText(payableLabel)
        val tariffName = order.tariff.name
        if (!tariffName.isNullOrBlank()) {
            tvTariffChip.visibility = View.VISIBLE
            tvTariffChip.text = tariffName
        } else {
            tvTariffChip.visibility = View.GONE
        }
        tvPaymentChip.setText(paymentRes(order))

        // Metrics.
        tvDistanceValue.text = "${receipt.distanceKm} ${getString(R.string.km)}"
        tvDurationValue.text = receipt.durationMs.asHHMMSS()
        tvWaitedValue.text = receipt.waitMs.asHHMMSS()

        // Driver earnings (only when the tariff carries a commission).
        //
        // The commission base is the GROSS order total, NOT the cash collected — wire-proved
        // twice on 2026-07-31 (orders 79870 and 79874, identical shape): gross 17 000, bonus
        // 10 000, cash 7 000, and the driver's balance moved +7 620 both times.
        // 7 620 = 10 000 − 2 380, and 2 380 is exactly 14% of 17 000. Billing off the cash
        // would have charged 980 and shown net 6 020 — understating the driver's commission by
        // 1 400 and his actual earnings by more than half.
        //
        // Net is therefore gross − commission (= 14 620 on that trip): the driver keeps 7 000 in
        // hand and the platform credits the remaining 7 620 (the bonus it reimbursed, less the
        // commission it took) to his balance.
        val ratio = commissionRatio(order.tariff.commission)
        if (!awaitingBill && ratio != null && ratio > 0.0) {
            cardEarnings.visibility = View.VISIBLE
            val commission = Math.round(receipt.grossTotal * ratio)
            val net = receipt.grossTotal - commission
            tvNetEarnings.text = money(net)
            tvCommissionAmount.text = "-${money(commission)}"
        } else {
            cardEarnings.visibility = View.GONE
        }

        // Breakdown.
        tvRidePrice.text = money(receipt.rideCost)
        llWaitRow.visibility = if (receipt.waitCost > 0) View.VISIBLE else View.GONE
        tvWaitPrice.text = money(receipt.waitCost)
        llServicesRow.visibility = if (receipt.servicesCost > 0) View.VISIBLE else View.GONE
        tvServicesPrice.text = money(receipt.servicesCost)
        tvSubtotal.text = money(receipt.grossTotal)
        llPromoRow.visibility = if (receipt.promo > 0) View.VISIBLE else View.GONE
        tvPromoPrice.text = "-${money(receipt.promo)}"
        llBonusRow.visibility = if (receipt.bonus > 0) View.VISIBLE else View.GONE
        tvBonusPrice.text = "-${money(receipt.bonus)}"
        // Cash rounding, on its own line so the column still foots to the total. The settled
        // amount is printed just above it: the earnings card below is computed from THAT figure,
        // not from the rounded cash, and a driver should be able to see where his net came from.
        val rounding = collectedTotal() - receipt.finalTotal
        llSettledRow.visibility = if (rounding != 0L) View.VISIBLE else View.GONE
        tvSettledPrice.text = money(receipt.finalTotal)
        llRoundingRow.visibility = if (rounding != 0L) View.VISIBLE else View.GONE
        tvRoundingPrice.text = if (rounding > 0L) "+${money(rounding)}" else "-${money(-rounding)}"
        tvBreakdownTotal.text = money(collectedTotal())

        // Route.
        tvRoutePickup.text = pickupAddress(order) ?: "—"
        uz.teamwork.mehrgodriver.common.RoutePointsBinder.bindMiddle(
            llMiddlePoints,
            order.locations.drop(1).dropLast(1)
                .map { it.name.ifEmpty { getString(R.string.not_showed) } }
        )
        val dropoff = dropoffAddress(order)
        if (dropoff != null) {
            llRouteDropoff.visibility = View.VISIBLE
            tvRouteDropoff.text = dropoff
        } else {
            llRouteDropoff.visibility = View.GONE
        }

    }

    /**
     * Poll `order/history` until this order's `payment` block appears, then adopt it.
     *
     * The row can lag a beat behind `order/complete`, so a null result is "not yet", not an error —
     * hence the retries. The socket observer above cancels this the moment a frame lands, and it
     * in turn stops as soon as REST wins, so only one source is ever adopted.
     *
     * If every attempt comes back empty the preview is revealed with [R.string.receipt_provisional]
     * rather than left spinning: the driver still needs the trip summary, he just must not be told
     * a provisional figure is the bill.
     */
    private fun awaitSettlement() {
        // NO spinner here. `bind()` is the single owner of that visibility, and it
        // only spins while there is genuinely nothing to show (`awaitingBill`).
        //
        // Forcing it visible from this method spun it on top of a receipt that was
        // already complete: the socket frame settles the bill but carries no
        // `final_price`, so `settled` is true — amounts on screen — while this poll
        // still goes to history for the exact figure. The driver saw a finished
        // receipt with a loader over it for the whole retry ladder.
        settleJob = viewLifecycleOwner.lifecycleScope.launch {
            repeat(SETTLE_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) SETTLE_FIRST_DELAY_MS else SETTLE_RETRY_DELAY_MS)
                // Same gate as the caller: keep polling until the SERVER'S OWN
                // final price is in hand, not merely until something set
                // `settled` (the socket frame does, without carrying one).
                if (receipt.settledFinalTotal != null) return@launch
                var adopted = false
                viewModel.settlement(receipt.order.id).collect { state ->
                    val payment = (state as? Resource.Success)?.data?.myOrder?.payment
                    // gross > 0 gate: lenient Gson turns a malformed ""/null price into 0, and a
                    // zero bill must never overwrite the figure the driver approved.
                    if (payment != null && payment.gross > 0L) {
                        adoptSettlement(payment)
                        adopted = true
                    }
                }
                if (adopted) return@launch
            }
            settlementGaveUp = true
            bind()
        }
    }

    private fun adoptSettlement(payment: OrderHistory.MyOrder.Payment) {
        // The wait/services split is not in the payment block, so the dialog's rows are kept and
        // the ride line is re-derived against the settled gross — same rule as the socket path,
        // which is what keeps the two adoption routes from footing differently.
        receipt = receipt.copy(
            grossTotal = payment.gross,
            rideCost = (payment.gross - receipt.waitCost - receipt.servicesCost)
                .coerceAtLeast(0L),
            bonus = payment.bonus,
            promo = payment.promo,
            settled = true,
            // Adopt the server's OWN figures rather than re-deriving them. The
            // reconstruction gross − bonus − promo is not guaranteed to equal
            // `final_price`, and the cash/card decision was being taken from
            // `order.isCardPayment` here but from `payment.isCard` in history —
            // two different signals rounding the same trip differently, which
            // is how one ride read 19 130 on this screen and 19 000 in Tarix.
            settledFinalTotal = payment.finalTotal,
            settledIsCard = payment.isCard
        )
        bind()
    }

    private fun finishToMap() {
        findNavController().navigate(R.id.action_tripFinishFragment_to_mapFragment)
    }

    // ----- helpers ------------------------------------------------------

    private fun money(v: Long) = "${v.asSumString()} ${getString(R.string.sum)}"

    /**
     * What is actually settled: a cash trip rounds to the nearest 1 000 so'm (the driver cannot
     * hand back 130 so'm), a card trip stays exact because the card is billed the precise amount.
     * Same rule as `OrderHistory.MyOrder.Payment.collectedTotal`, so one trip reads the same figure
     * here and in history. The commission base below stays on the UNROUNDED settlement — that is
     * what the company bills against, and rounding is between the driver and the passenger.
     */
    private fun collectedTotal(): Long {
        // The settlement's own cash/card verdict wins when there is one: it
        // also counts `card_paid > 0`, so it can disagree with the order's flag
        // — and history rounds by that verdict. Deciding it differently on the
        // two screens is what let one trip read 19 130 here and 19 000 there.
        val isCard = receipt.settledIsCard ?: receipt.order.isCardPayment
        // NULL rounds. That looks like "guessing on an unknown method", and a
        // code review argued it should not — but the wire says otherwise:
        // `is_card_payment` is present-and-null on every captured CASH trip
        // (order 80216, card_payments.total_payment 0), never a genuine
        // unknown. Rounding only on an explicit `false` made this screen print
        // 10 740 on a trip where 11 000 was collected. The client now applies
        // the identical rule, so the two agree.
        return if (isCard == true) receipt.finalTotal
        else Helper.roundToThousand(receipt.finalTotal)
    }

    // Prefer the SETTLEMENT's verdict, same as collectedTotal — otherwise the
    // label can say "cash" on a receipt that was rounded as card, or vice versa.
    private fun paymentRes(order: Order) =
        if ((receipt.settledIsCard ?: order.isCardPayment) == true) R.string.payment_type_card
        else R.string.payment_type_cash

    // Prefer the route-point names (order.locations) so the receipt reads the SAME street addresses
    // as the order-list view; fall back to the address/landmark name when a location has no name.
    // A taximeter (driver-created meter) has no stored street (locations[0].name empty), so it falls
    // back to order.address.name — the nearest landmark ("Turkiston to'yxonasi") is shown because
    // it's more useful than nothing (per boss).
    private fun pickupAddress(order: Order): String? =
        order.locations.firstOrNull()?.name?.ifEmpty { null } ?: order.address?.name

    private fun dropoffAddress(order: Order): String? =
        order.locations.takeIf { it.size > 1 }?.lastOrNull()?.name?.ifEmpty { null }
            ?: order.addressFinish?.name

    private fun passengerName(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("\\s+"))
        val first = parts.firstOrNull() ?: return null
        return if (parts.size >= 2 && parts.last().isNotEmpty())
            "$first ${parts.last().first()}." else first
    }

    /** Commission "15" / "15%" -> 0.15; null when absent / zero. */
    private fun commissionRatio(raw: String?): Double? {
        val value = raw?.replace("%", "")?.trim()?.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return value / 100.0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_RECEIPT = "arg_receipt"

        /** Give the settlement frame a moment first — it usually races the navigation here. */
        private const val SETTLE_FIRST_DELAY_MS = 1_500L
        private const val SETTLE_RETRY_DELAY_MS = 3_000L

        /** ~13 s of waiting in total before the preview is revealed as provisional. */
        private const val SETTLE_ATTEMPTS = 4
    }
}
