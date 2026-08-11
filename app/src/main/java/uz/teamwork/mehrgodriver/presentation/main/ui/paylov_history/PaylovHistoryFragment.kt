package uz.teamwork.mehrgodriver.presentation.main.ui.paylov_history

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.AdapterOrderHistoryHeaderBinding
import uz.teamwork.mehrgodriver.databinding.FragmentPaylovHistoryBinding
import uz.teamwork.mehrgodriver.databinding.ItemBalanceHistoryBinding
import uz.teamwork.mehrgodriver.databinding.ItemWithdrawalRequestBinding
import uz.teamwork.mehrgodriver.domain.model.paylov.BalanceHistoryItem
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalRequest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Paylov history — two tabs (withdrawal requests / balance ledger) with the SAME day-section
 * style as the orders history ("Bugun" / "19 iyul" headers, reused adapter_order_history_header,
 * scrolling with the list — NOT sticky, per user). Paged 20/request, load-more on scroll.
 */
@AndroidEntryPoint
class PaylovHistoryFragment : Fragment() {
    private var _binding: FragmentPaylovHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyVM: PaylovHistoryVM by viewModels()

    private var showBalance = false

    private val requests = mutableListOf<WithdrawalRequest>()
    private var requestsPage = 1
    private var requestsTotal = Int.MAX_VALUE
    private var requestsLoading = false

    private val ledger = mutableListOf<BalanceHistoryItem>()
    private var ledgerPage = 1
    private var ledgerTotal = Int.MAX_VALUE
    private var ledgerLoading = false
    private var totalIncome: Long? = null
    private var totalExpense: Long? = null

    /** Ledger filter: null = all, 1 = income only, 2 = expense only (server `type` param). */
    private var ledgerFilter: Int? = null

    /** Flattened list for the CURRENT tab: day headers + entries. */
    private sealed class Row {
        data class Header(val label: String) : Row()
        data class Request(val item: WithdrawalRequest) : Row()
        data class Ledger(val item: BalanceHistoryItem) : Row()
    }

    private val rows = mutableListOf<Row>()
    private val adapter = HistoryAdapter()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaylovHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showBalance = arguments?.getString("tab") == "balance"

        binding.mcvBack.setDebouncedClickListener { findNavController().navigateUp() }
        binding.chipRequests.setOnClickListener { switchTab(balance = false) }
        binding.chipBalance.setOnClickListener { switchTab(balance = true) }
        // Tap a totals pill = show only that type; tap it again = back to all.
        binding.llIncomeTotal.setOnClickListener { toggleLedgerFilter(1) }
        binding.llExpenseTotal.setOnClickListener { toggleLedgerFilter(2) }

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
        binding.rvHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) loadNextPage()
            }
        })

        applyTab()
        loadNextPage()
    }

    private fun switchTab(balance: Boolean) {
        if (showBalance == balance) return
        showBalance = balance
        applyTab()
        if ((balance && ledger.isEmpty()) || (!balance && requests.isEmpty())) loadNextPage()
    }

    private fun applyTab() {
        val b = _binding ?: return
        val ctx = requireContext()
        val accent = ContextCompat.getColor(ctx, R.color.app_color)
        val idleText = ContextCompat.getColor(ctx, R.color.black_white)

        // Segmented style: selected = soft-accent card with accent border (bg_nav_option_selected),
        // idle = plain card with a subtle border — the app's standard option look.
        b.chipRequests.setBackgroundResource(
            if (showBalance) R.drawable.bg_nav_option else R.drawable.bg_nav_option_selected
        )
        b.chipRequests.setTextColor(if (showBalance) idleText else accent)
        b.chipBalance.setBackgroundResource(
            if (showBalance) R.drawable.bg_nav_option_selected else R.drawable.bg_nav_option
        )
        b.chipBalance.setTextColor(if (showBalance) accent else idleText)

        b.tvTitle.setText(if (showBalance) R.string.balance_history else R.string.withdrawal_history)
        rebuildRows()
        renderTotals()
    }

    /** Restart the ledger under a new filter (or back to all) and reload page 1.
     *  Totals are kept — both pills always show the all-time numbers (see [loadLedgerPage]). */
    private fun toggleLedgerFilter(type: Int) {
        if (!showBalance) return
        ledgerFilter = if (ledgerFilter == type) null else type
        ledger.clear()
        ledgerPage = 1
        ledgerTotal = Int.MAX_VALUE
        rebuildRows()
        renderTotals()
        loadLedgerPage()
    }

    private fun renderTotals() {
        val b = _binding ?: return
        val show = showBalance && (totalIncome != null || totalExpense != null)
        b.llTotals.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            b.tvIncomeTotal.text = fmt(totalIncome)
            b.tvExpenseTotal.text = fmt(totalExpense)
        }
        // Both pills always show; the selected one gets a darker border.
        b.llIncomeTotal.background =
            pillBackground(R.color.green_soft, R.color.green, ledgerFilter == 1)
        b.llExpenseTotal.background =
            pillBackground(R.color.red_soft, R.color.red, ledgerFilter == 2)
    }

    /** Rounded pill with an optional selection stroke (backgroundTint would tint the stroke too). */
    private fun pillBackground(fillRes: Int, strokeRes: Int, selected: Boolean): GradientDrawable {
        val ctx = binding.root.context
        return GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_pill)
            setColor(ContextCompat.getColor(ctx, fillRes))
            if (selected) {
                val strokeW = (1.5f * resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(strokeW, ContextCompat.getColor(ctx, strokeRes))
            }
        }
    }

    private fun renderEmpty() {
        val b = _binding ?: return
        val loading = if (showBalance) ledgerLoading else requestsLoading
        b.tvEmpty.visibility = if (rows.isEmpty() && !loading) View.VISIBLE else View.GONE
    }

    private fun fmt(value: Long?): String = Helper.formatPrice((value ?: 0L).toString()).trim()

    /** "Bugun" for today, else a localized "d MMMM" — same rule as the orders history. */
    private fun dayLabel(epochSec: Long?): String {
        if (epochSec == null || epochSec <= 0) return ""
        return try {
            val date = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate()
            if (date == LocalDate.now()) {
                getString(R.string.today)
            } else {
                val lang = LanguageManager.getLanguage() ?: "uz"
                date.format(DateTimeFormatter.ofPattern("d MMMM", Locale(lang)))
            }
        } catch (e: Exception) {
            ""
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuildRows() {
        rows.clear()
        var lastLabel: String? = null
        if (showBalance) {
            ledger.forEach { item ->
                val label = dayLabel(item.createdAt)
                if (label.isNotEmpty() && label != lastLabel) {
                    rows += Row.Header(label)
                    lastLabel = label
                }
                rows += Row.Ledger(item)
            }
        } else {
            requests.forEach { item ->
                val label = dayLabel(item.createdAt)
                if (label.isNotEmpty() && label != lastLabel) {
                    rows += Row.Header(label)
                    lastLabel = label
                }
                rows += Row.Request(item)
            }
        }
        adapter.notifyDataSetChanged()
        renderEmpty()
    }

    private fun loadNextPage() {
        if (showBalance) loadLedgerPage() else loadRequestsPage()
    }

    private fun setLoadingUi(initialEmpty: Boolean, loading: Boolean) {
        val b = _binding ?: return
        b.progressBar.visibility = if (loading && initialEmpty) View.VISIBLE else View.GONE
        b.pbMore.visibility = if (loading && !initialEmpty) View.VISIBLE else View.GONE
    }

    private fun loadRequestsPage() {
        if (requestsLoading || requests.size >= requestsTotal) return
        requestsLoading = true
        setLoadingUi(requests.isEmpty(), true)
        viewLifecycleOwner.lifecycleScope.launch {
            historyVM.withdrawalHistory(requestsPage).collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        requestsLoading = false
                        setLoadingUi(false, false)
                        val data = it.data?.data
                        requestsTotal = data?.total ?: 0
                        val items = data?.items.orEmpty()
                        if (items.isNotEmpty()) {
                            requests.addAll(items)
                            requestsPage++
                        } else {
                            requestsTotal = requests.size
                        }
                        if (!showBalance) rebuildRows()
                    }

                    is Resource.Error -> {
                        requestsLoading = false
                        setLoadingUi(false, false)
                        // Old backend without the endpoint → quiet empty state.
                        requestsTotal = requests.size
                        renderEmpty()
                    }
                }
            }
        }
    }

    private fun loadLedgerPage() {
        if (ledgerLoading || ledger.size >= ledgerTotal) return
        ledgerLoading = true
        setLoadingUi(ledger.isEmpty(), true)
        viewLifecycleOwner.lifecycleScope.launch {
            historyVM.balanceHistory(ledgerPage, ledgerFilter).collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        ledgerLoading = false
                        setLoadingUi(false, false)
                        val data = it.data?.data
                        ledgerTotal = data?.total ?: 0
                        // Filtered responses return filtered totals (the other side comes back 0) —
                        // only refresh the pills from unfiltered loads so both always show real sums.
                        if (ledgerFilter == null) {
                            totalIncome = data?.totalIncome ?: totalIncome
                            totalExpense = data?.totalExpense ?: totalExpense
                        }
                        val items = data?.items.orEmpty()
                        if (items.isNotEmpty()) {
                            ledger.addAll(items)
                            ledgerPage++
                        } else {
                            ledgerTotal = ledger.size
                        }
                        renderTotals()
                        if (showBalance) rebuildRows()
                    }

                    is Resource.Error -> {
                        ledgerLoading = false
                        setLoadingUi(false, false)
                        showToast(it.message ?: getString(R.string.error))
                        ledgerTotal = ledger.size
                        renderEmpty()
                    }
                }
            }
        }
    }

    // region adapter

    private inner class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val typeHeader = 0
        private val typeRequest = 1
        private val typeLedger = 2

        inner class HeaderHolder(val b: AdapterOrderHistoryHeaderBinding) :
            RecyclerView.ViewHolder(b.root)

        inner class RequestHolder(val b: ItemWithdrawalRequestBinding) :
            RecyclerView.ViewHolder(b.root)

        inner class LedgerHolder(val b: ItemBalanceHistoryBinding) :
            RecyclerView.ViewHolder(b.root)

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> typeHeader
            is Row.Request -> typeRequest
            is Row.Ledger -> typeLedger
        }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
            typeHeader -> HeaderHolder(
                AdapterOrderHistoryHeaderBinding.inflate(layoutInflater, parent, false)
            )

            typeRequest -> RequestHolder(
                ItemWithdrawalRequestBinding.inflate(layoutInflater, parent, false)
            )

            else -> LedgerHolder(
                ItemBalanceHistoryBinding.inflate(layoutInflater, parent, false)
            )
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).b.tvHeader.text = row.label
                is Row.Request -> bindRequest((holder as RequestHolder).b, row.item)
                is Row.Ledger -> bindLedger((holder as LedgerHolder).b, row.item)
            }
        }

        private fun bindRequest(b: ItemWithdrawalRequestBinding, item: WithdrawalRequest) {
            val ctx = b.root.context

            // 0 pending amber / 1 approved green / 2 rejected red / 3 cancelled gray.
            val (color, soft, icon) = when (item.status) {
                1 -> Triple(R.color.green, R.color.green_soft, R.drawable.ph_check_circle)
                2 -> Triple(R.color.red, R.color.red_soft, R.drawable.ph_x_circle)
                3 -> Triple(
                    R.color.gray_full,
                    R.color.gray_middle_black_middle,
                    R.drawable.ph_minus
                )

                else -> Triple(R.color.app_color, R.color.app_color_soft, R.drawable.ph_clock)
            }
            val statusColor = ContextCompat.getColor(ctx, color)
            b.tvStatus.text = item.statusName ?: item.status?.toString() ?: ""
            b.tvStatus.setTextColor(statusColor)
            b.ivStatus.setImageResource(icon)
            b.ivStatus.imageTintList = ColorStateList.valueOf(statusColor)
            b.llStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, soft))

            // Approved with a different amount → requested struck through, approved in green.
            val sum = getString(R.string.sum)
            val approved = item.approvedAmount
            if (item.status == 1 && approved != null && approved != item.amount) {
                val gray = ContextCompat.getColor(ctx, R.color.gray_full)
                val green = ContextCompat.getColor(ctx, R.color.green)
                b.tvAmount.text = SpannableStringBuilder().apply {
                    var from = length
                    append(fmt(item.amount))
                    setSpan(StrikethroughSpan(), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(
                        ForegroundColorSpan(gray),
                        from,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    from = length
                    append(" → ")
                    setSpan(
                        ForegroundColorSpan(gray),
                        from,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    from = length
                    append("${fmt(approved)} $sum")
                    setSpan(
                        ForegroundColorSpan(green),
                        from,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                b.tvAmount.text = "${fmt(item.amount)} $sum"
                // Cancelled requests read as inactive.
                b.tvAmount.setTextColor(
                    ContextCompat.getColor(
                        ctx, if (item.status == 3) R.color.gray_full else R.color.black_white
                    )
                )
            }

            // "860013******4751" → "8600 13** **** 4751".
            val cardDigits = item.cardNumber.orEmpty()
            b.llCard.visibility = if (cardDigits.isBlank()) View.GONE else View.VISIBLE
            b.tvCard.text = cardDigits.chunked(4).joinToString(" ")

            b.tvDate.text =
                item.createdAt?.let { ts -> timeFormat.format(Date(ts * 1000)) } ?: ""

            // Admin's rejection reason (red box) beats the driver's own note (gray box).
            val adminNote = item.adminNote?.takeIf { it.isNotBlank() }
            val note = adminNote ?: item.note?.takeIf { it.isNotBlank() }
            b.llNote.visibility = if (note != null) View.VISIBLE else View.GONE
            if (note != null) {
                b.tvNote.text = note
                val rejected = item.status == 2 && adminNote != null
                val noteColor = ContextCompat.getColor(
                    ctx, if (rejected) R.color.red else R.color.gray_full
                )
                b.tvNote.setTextColor(noteColor)
                b.ivNote.setImageResource(
                    if (rejected) R.drawable.ph_warning_circle else R.drawable.ph_chat_text
                )
                b.ivNote.imageTintList = ColorStateList.valueOf(noteColor)
                b.llNote.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        ctx, if (rejected) R.color.red_soft else R.color.gray_light_black_light
                    )
                )
            }
        }

        private fun bindLedger(b: ItemBalanceHistoryBinding, item: BalanceHistoryItem) {
            val ctx = b.root.context
            val income = item.type == 1

            b.tvReason.text = item.reasonName ?: item.typeName ?: ""
            // The day lives in the sticky header — the row only needs the time.
            b.tvDate.text =
                item.createdAt?.let { ts -> timeFormat.format(Date(ts * 1000)) }
                    ?: item.datetime ?: ""
            b.tvRemaining.text = getString(R.string.remaining_after, fmt(item.totalAfter))

            b.tvValue.text = (if (income) "+" else "−") + fmt(item.value)
            b.tvValue.setTextColor(
                ContextCompat.getColor(ctx, if (income) R.color.green else R.color.red)
            )
        }
    }

    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
