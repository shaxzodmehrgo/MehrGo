package uz.teamwork.mehrgodriver.presentation.main.ui.orders_history

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.ORDER_HISTORY_CANCELLED
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.RoutePointsBinder
import uz.teamwork.mehrgodriver.common.ServiceIcons
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.databinding.FragmentOrderHistoryDetailBinding
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import java.util.Locale

class OrderHistoryDetailFragment : Fragment() {

    private var _binding: FragmentOrderHistoryDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener { findNavController().navigateUp() }

        val item = arguments?.let {
            BundleCompat.getParcelable(it, ARG_ITEM, OrderHistory.OrderHistoryItem::class.java)
        } ?: run {
            findNavController().navigateUp()
            return
        }

        bind(item)
    }

    @SuppressLint("SetTextI18n")
    private fun bind(item: OrderHistory.OrderHistoryItem) = binding.apply {
        val order = item.myOrder

        // Hero. With a settlement block on the row the figure is what was actually collected
        // (final_price = gross − promo − bonus), the same number the trip receipt shows — the same
        // trip used to read 19 130 there and 33 000 here. The gross is not lost: it heads the fare
        // breakdown below. Legacy rows (no `payment`) keep the order total under "Jami".
        //
        // A CANCELLED row is deliberately excluded: prod still sends it a full settlement block
        // with final_price == price (verified 2026-07-31, orders 79715 / 79683 / 79607), and
        // nothing was collected on a cancelled trip — labelling that "Naqd olinadi" would tell the
        // driver to take cash for a ride that never happened. Those rows keep the neutral total.
        val payment = order.payment?.takeIf { item.status.value != ORDER_HISTORY_CANCELLED }
        tvStatusPill.text = item.status.name
        tvDate.text = item.date.datetime.replace("y ", " ")
        if (payment != null) {
            tvTotal.text = Helper.formatPrice(payment.collectedTotal.toString())
            tvTotalLabel.setText(collectedLabel(payment))
        } else {
            tvTotal.text = Helper.formatPrice(order.price)
            tvTotalLabel.setText(R.string.total_earned)
        }

        // Route
        val locs = order.locations?.sortedBy { it.position?.toIntOrNull() ?: 0 } ?: emptyList()
        // Pickup: prefer the real street (locations[].name); fall back to the landmark address.name.
        // A taximeter has no stored street, so it shows the nearest landmark ("Turkiston to'yxonasi")
        // — more useful than nothing (per boss). "—" only if both are empty.
        val from = locs.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: order.address?.name?.takeIf { it.isNotBlank() } ?: "—"
        val to = if (locs.size >= 2) locs.last().name?.takeIf { it.isNotBlank() }
        else order.addressFinish?.name?.takeIf { it.isNotBlank() }
        tvFrom.text = from
        // Middle stops (positions 1..n-1) → show EVERY point, not just pickup → dropoff.
        val middle = if (locs.size > 2) {
            locs.subList(1, locs.size - 1)
                .mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
        } else emptyList()
        RoutePointsBinder.bindMiddle(llMiddlePoints, middle)
        if (to != null) {
            llTo.visibility = View.VISIBLE
            tvTo.text = to
        } else {
            llTo.visibility = View.GONE
        }

        // Stats
        tvTariff.text = order.tariff?.name?.takeIf { it.isNotBlank() } ?: "—"
        val distanceKm = order.distance?.toDoubleOrNull() ?: 0.0
        tvDistance.text =
            if (distanceKm > 0.0) String.format(Locale.US, "%.1f km", distanceKm) else "—"
        val execMs = order.executionTime?.toLongOrNull() ?: 0L
        tvTime.text = if (execMs > 0L) formatDuration(execMs) else "—"

        // Trip details — waited time
        val waitedMs = waitedMillis(order.waitingTime, execMs)
        tvWaited.text = if (waitedMs > 0L) formatDuration(waitedMs) else "—"

        // Payment method — `payment.method` + `payment.card_paid` (a trip with a card-settled part
        // is a card trip whatever the method label says). `is_card_payment` is the fallback for
        // rows that predate the settlement block. The value stays UNKNOWN — and the row stays
        // hidden — when neither field is present AND when the method is a label this app doesn't
        // recognise: asserting "cash" on a trip that was in fact prepaid would corrupt the driver's
        // end-of-day cash reconciliation, which is why this row was kept dark for months.
        val isCard = payment?.isCard ?: order.isCardPayment
        if (isCard != null) {
            llPayment.visibility = View.VISIBLE
            tvPayment.setText(
                if (isCard) R.string.payment_type_card else R.string.payment_type_cash
            )
        } else {
            llPayment.visibility = View.GONE
        }

        // Fare breakdown — only when something moved the gross: a promo, a spent bonus, cash
        // rounding, or a card-settled part. Otherwise the hero already is the whole story.
        if (payment != null &&
            (payment.hasDiscount || payment.card > 0L || payment.roundingDelta != 0L)
        ) {
            cvFare.visibility = View.VISIBLE
            // Gross: prefer the settlement figure, fall back to the row's own price.
            val gross = payment.gross.takeIf { it > 0L }
                ?: order.price.trim().toLongOrNull() ?: payment.finalTotal
            tvFareGross.text = money(gross)

            llFarePromo.visibility = if (payment.promo > 0L) View.VISIBLE else View.GONE
            val code = payment.promoCode?.takeIf { it.isNotBlank() }
            tvFarePromoLabel.text = getString(R.string.promo_code) +
                    (code?.let { " · $it" } ?: "")
            tvFarePromo.text = "-${money(payment.promo)}"

            llFareBonus.visibility = if (payment.bonus > 0L) View.VISIBLE else View.GONE
            tvFareBonus.text = "-${money(payment.bonus)}"

            // Cash rounding, carried as its own line so the column still foots to the total.
            val delta = payment.roundingDelta
            llFareRounding.visibility = if (delta != 0L) View.VISIBLE else View.GONE
            tvFareRounding.text = if (delta > 0L) "+${money(delta)}" else "-${money(-delta)}"

            tvFareTotalLabel.setText(collectedLabel(payment))
            tvFareTotal.text = money(payment.collectedTotal)

            // A card trip splits once more: what the card already covered, and any remainder the
            // driver still takes in hand.
            llFareCard.visibility = if (payment.card > 0L) View.VISIBLE else View.GONE
            tvFareCard.text = money(payment.card)
            llFareCash.visibility =
                if (payment.card > 0L && payment.cashToCollect > 0L) View.VISIBLE else View.GONE
            tvFareCash.text = money(payment.cashToCollect)
        } else {
            cvFare.visibility = View.GONE
        }

        // Activated services — a breakdown of what the hero price is made of.
        val services = order.services ?: emptyList()
        if (services.isEmpty()) {
            cvServices.visibility = View.GONE
        } else {
            cvServices.visibility = View.VISIBLE
            tvServicesCount.text = getString(R.string.services_active_count, services.size)
            llServicesList.removeAllViews()
            services.forEach { s ->
                val row =
                    layoutInflater.inflate(R.layout.adapter_service_row, llServicesList, false)
                row.findViewById<TextView>(R.id.tvServiceName).text = s.service.name
                // Server artwork when the backend ships one, else the local glyph — keyed by NAME,
                // not id, because the catalogue is per-branch and ids are not portable.
                ServiceIcons.bindInto(
                    row.findViewById(R.id.ivServiceIcon), s.service.name, s.service.icon
                )
                row.findViewById<TextView>(R.id.tvServicePrice).text =
                    "${Helper.formatPrice(s.total.toString())}${getString(R.string.sum)}"
                llServicesList.addView(row)
            }
            tvServicesTotal.text =
                "${
                    Helper.formatPrice(services.sumOf { it.total }.toString())
                }${getString(R.string.sum)}"
        }

        // Dispatcher — the client's name/phone is deliberately NOT surfaced on a completed
        // order (privacy). For any post-trip question the driver calls the branch dispatcher.
        val dispatcherPhone =
            UserManager.getUser()?.branch?.dispatcherNumber?.takeIf { it.isNotBlank() }
        if (dispatcherPhone != null) {
            tvDispatcherPhone.text = dispatcherPhone
            llCallDispatcher.visibility = View.VISIBLE
            llCallDispatcher.setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dispatcherPhone")))
            }
        } else {
            tvDispatcherPhone.text = getString(R.string.not_assigned_dispatcher)
            llCallDispatcher.visibility = View.GONE
        }

        // Order id — the ACTUAL order id (order.id), not item.id which is the history-row id
        // (a separate id-space). Support/dispatch look orders up by order.id, so that is what the
        // driver must be able to read off this screen.
        tvOrderId.text = "${getString(R.string.id_title)}${order.id}"
    }

    /**
     * `execution_time` is milliseconds, but `waiting_time` is NOT — and it changed units mid-flight.
     * Order 77390 is the proof: the app posted `waiting_time=2802813` (ms) on complete and the
     * history row came back as `2803`, i.e. the backend now stores SECONDS. Read as millis that is
     * "00:00:02" for a 47-minute wait, which is what the driver was seeing. Rows written before the
     * order-gps rewrite (~2026-07-18) are still raw milliseconds, so a fixed unit breaks the other half.
     *
     * Waiting always happens inside the trip, so `waiting <= execution` is a hard invariant: read the
     * value as seconds, and fall back to millis only when seconds would exceed the trip duration.
     * Drop this once the backend reports one unit for both fields.
     */
    private fun waitedMillis(raw: String?, execMs: Long): Long {
        // toDoubleOrNull, not toLongOrNull: the backend formats some numeric
        // columns as "2803.00", which toLongOrNull rejects outright — the row
        // then showed no wait at all.
        val value = raw?.trim()?.toDoubleOrNull()?.toLong() ?: return 0L
        if (value <= 0L) return 0L
        val asSeconds = value * 1000
        // With no trip duration to test the invariant against, fall back to the
        // magnitude rule the client uses rather than silently assuming seconds:
        // a legacy MILLISECOND row was otherwise inflated a thousandfold (a
        // 30 s wait rendering as 8 hours), and the two apps then disagreed
        // about the same trip.
        if (execMs <= 0L) return if (value > 86_400L) value else asSeconds
        return if (asSeconds <= execMs) asSeconds else value
    }

    /**
     * Caption for the collected amount. Only a trip the wire says is CASH gets the "take this in
     * hand" wording; a card trip and an unrecognised method both get the neutral "payable", so an
     * unknown settlement method never reads as an instruction to collect cash.
     */
    private fun collectedLabel(payment: OrderHistory.MyOrder.Payment): Int =
        if (payment.isCard == false) R.string.amount_cash_to_collect else R.string.amount_payable

    /** "19 130 so'm" — same grouping + suffix the services rows on this screen use. */
    private fun money(value: Long): String =
        "${Helper.formatPrice(value.toString())}${getString(R.string.sum)}"

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ITEM = "item"
    }
}
