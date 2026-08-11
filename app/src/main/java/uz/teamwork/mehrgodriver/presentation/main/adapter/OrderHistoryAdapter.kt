package uz.teamwork.mehrgodriver.presentation.main.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.ORDER_HISTORY_ACCEPTED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_HISTORY_CANCELLED
import uz.teamwork.mehrgodriver.common.Constants.ORDER_HISTORY_DOING
import uz.teamwork.mehrgodriver.common.Constants.ORDER_HISTORY_FINISHED
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.databinding.AdapterOrderHistoryBinding
import uz.teamwork.mehrgodriver.databinding.AdapterOrderHistoryHeaderBinding
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A history-list row: either a date-section header ("Bugun" / "1 iyul") or an order card. */
sealed class HistoryRow {
    data class Header(val dayKey: String) : HistoryRow()
    data class Item(val order: OrderHistory.OrderHistoryItem) : HistoryRow()
}

/**
 * Wraps paged orders as [HistoryRow]s for [OrderHistoryAdapter]. With [withHeaders], inserts a
 * date-section header before the first order of each day (client-parity grouping, main history);
 * without it, a flat card list (the My-Orders finished/cancelled tabs). Assumes the source is
 * ordered newest-first by day.
 */
fun PagingData<OrderHistory.OrderHistoryItem>.toHistoryRows(withHeaders: Boolean): PagingData<HistoryRow> {
    val rows = this.map<OrderHistory.OrderHistoryItem, HistoryRow> { HistoryRow.Item(it) }
    if (!withHeaders) return rows
    return rows.insertSeparators { before, after ->
        val afterItem = after as? HistoryRow.Item ?: return@insertSeparators null
        val beforeItem = before as? HistoryRow.Item
        val afterKey = afterItem.order.date.date
        if (beforeItem == null || beforeItem.order.date.date != afterKey) {
            HistoryRow.Header(afterKey)
        } else {
            null
        }
    }
}

class OrderHistoryAdapter :
    PagingDataAdapter<HistoryRow, RecyclerView.ViewHolder>(HistoryRowDiff()) {
    lateinit var context: Context
    var onItemClick: ((OrderHistory.OrderHistoryItem) -> Unit)? = null

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is HistoryRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(AdapterOrderHistoryHeaderBinding.inflate(inflater, parent, false))
        } else {
            OrderViewHolder(AdapterOrderHistoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HistoryRow.Header -> (holder as HeaderViewHolder).bind(row)
            is HistoryRow.Item -> (holder as OrderViewHolder).bind(row.order)
            null -> Unit
        }
    }

    // Date-section header
    inner class HeaderViewHolder(private val binding: AdapterOrderHistoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: HistoryRow.Header) {
            binding.tvHeader.text = formatDayHeader(context, header.dayKey)
        }
    }

    // Order card
    inner class OrderViewHolder(private val binding: AdapterOrderHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: OrderHistory.OrderHistoryItem) {
            binding.apply {
                root.setOnClickListener { onItemClick?.invoke(item) }

                // Status → pill (soft-tinted background + matching text colour).
                val (colorRes, softRes) = when (item.status.value) {
                    ORDER_HISTORY_CANCELLED -> R.color.red to R.color.red_soft
                    ORDER_HISTORY_ACCEPTED -> R.color.blue_middle to R.color.blue_soft
                    ORDER_HISTORY_FINISHED -> R.color.green to R.color.green_soft
                    ORDER_HISTORY_DOING -> R.color.app_color to R.color.amber_soft
                    else -> R.color.app_color to R.color.app_color_soft
                }
                val color = ContextCompat.getColor(context, colorRes)
                val softColor = ContextCompat.getColor(context, softRes)
                // In-progress orders ship no status label from the backend (it arrives as "-"),
                // so show a proper "Bajarilmoqda" instead of a bare dash.
                tvStatus.text = if (item.status.value == ORDER_HISTORY_DOING) {
                    context.getString(R.string.status_doing)
                } else {
                    item.status.name
                }
                tvStatus.setTextColor(color)
                tvStatus.backgroundTintList = ColorStateList.valueOf(softColor)

                // Route: from → to (street-level locations, fall back to area names).
                val locs = item.myOrder.locations
                    ?.sortedBy { it.position?.toIntOrNull() ?: 0 } ?: emptyList()
                // Pickup: prefer the real street (locations[].name); fall back to the landmark
                // address.name so a taximeter shows "Turkiston to'yxonasi" rather than nothing (per
                // boss). "—" only if both are empty.
                val from = locs.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                    ?: item.myOrder.address?.name?.takeIf { it.isNotBlank() } ?: "—"
                val to = if (locs.size >= 2) locs.last().name?.takeIf { it.isNotBlank() }
                else item.myOrder.addressFinish?.name?.takeIf { it.isNotBlank() }
                tvFrom.text = from
                if (to != null) {
                    llTo.visibility = View.VISIBLE
                    tvTo.text = to
                } else {
                    llTo.visibility = View.GONE
                }

                // Intermediate stops → a compact "+N nuqta" chip between pickup and dropoff.
                val midCount = if (locs.size > 2) locs.size - 2 else 0
                if (midCount > 0) {
                    llVia.visibility = View.VISIBLE
                    tvVia.text = context.getString(R.string.route_extra_points, midCount)
                } else {
                    llVia.visibility = View.GONE
                }

                // Date — strip the backend "y " year-suffix artefact.
                tvDate.text = item.date.datetime.replace("y ", " ")

                // Tariff pill (hidden when the tariff is unknown).
                val tariff = item.myOrder.tariff?.name?.takeIf { it.isNotBlank() }
                if (tariff != null) {
                    tvTariff.visibility = View.VISIBLE
                    tvTariff.text = tariff
                } else {
                    tvTariff.visibility = View.GONE
                }

                // Price — bold hero, e.g. "19 000 so'm". With a settlement block on the row this is
                // the amount actually collected (final_price, rounded to the nearest 1 000 on a
                // cash trip), matching the trip receipt and the detail screen. Deliberately ONE
                // figure: the gross belongs to the detail screen's fare breakdown, not on a card
                // the driver scans. Legacy rows (no `payment`) keep showing the plain order total.
                // Cancelled rows still carry a settlement block (final_price == price) even though
                // nothing was collected — they keep the plain order total. See the detail screen.
                val price = item.myOrder.payment
                    ?.takeIf { item.status.value != ORDER_HISTORY_CANCELLED }
                    ?.collectedTotal?.toString() ?: item.myOrder.price
                tvPrice.text =
                    "${Helper.formatPrice(price)}${context.getString(R.string.sum)}"
            }
        }
    }

    /**
     * Formats a backend day key ("Jul 3, 2026") into a section title: "Bugun" for today, otherwise
     * a localized "d MMMM" ("1 iyul", "30 iyun") in the app's chosen language. Falls back to the raw
     * key if parsing fails.
     */
    private fun formatDayHeader(context: Context, dayKey: String): String = try {
        val parsed = LocalDate.parse(
            dayKey.trim(),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        )
        if (parsed == LocalDate.now()) {
            context.getString(R.string.today)
        } else {
            val lang = LanguageManager.getLanguage() ?: "uz"
            parsed.format(DateTimeFormatter.ofPattern("d MMMM", Locale(lang)))
        }
    } catch (e: Exception) {
        dayKey
    }

    // DiffUtil
    class HistoryRowDiff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(oldItem: HistoryRow, newItem: HistoryRow): Boolean = when {
            oldItem is HistoryRow.Header && newItem is HistoryRow.Header ->
                oldItem.dayKey == newItem.dayKey

            oldItem is HistoryRow.Item && newItem is HistoryRow.Item ->
                oldItem.order.id == newItem.order.id

            else -> false
        }

        override fun areContentsTheSame(oldItem: HistoryRow, newItem: HistoryRow): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
