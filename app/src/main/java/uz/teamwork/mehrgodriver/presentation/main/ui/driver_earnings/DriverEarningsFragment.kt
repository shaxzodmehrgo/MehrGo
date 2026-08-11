package uz.teamwork.mehrgodriver.presentation.main.ui.driver_earnings

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.common.widget.TopRoundedBarChartRenderer
import uz.teamwork.mehrgodriver.databinding.DialogBshEarningsPeriodBinding
import uz.teamwork.mehrgodriver.databinding.FragmentDriverEarningsBinding
import uz.teamwork.mehrgodriver.domain.model.DriverEarningsSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@AndroidEntryPoint
class DriverEarningsFragment : Fragment() {

    private var _binding: FragmentDriverEarningsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverEarningsViewModel by viewModels()

    private enum class PeriodType { DAY, WEEK, MONTH, CUSTOM }

    private var currentPeriodType: PeriodType = PeriodType.MONTH
    private var referenceDate: LocalDate = LocalDate.now()
    private var customStart: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var customEnd: LocalDate = LocalDate.now()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverEarningsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        bindClicks()
        refresh()
    }

    private fun bindClicks() {
        binding.mcvBack.setDebouncedClickListener { findNavController().navigateUp() }
        binding.mcvRefresh.setDebouncedClickListener { showPeriodPicker() }
        binding.mcvPrevMonth.setOnClickListener {
            referenceDate = referenceDate.minus(1, periodUnit())
            refresh()
        }
        binding.mcvNextMonth.setOnClickListener {
            val next = referenceDate.plus(1, periodUnit())
            if (!periodStart(next).isAfter(LocalDate.now())) {
                referenceDate = next
                refresh()
            }
        }

        binding.llStartDate.setDebouncedClickListener { pickDate(isStart = true) }
        binding.llEndDate.setDebouncedClickListener { pickDate(isStart = false) }
        binding.mcvApply.setDebouncedClickListener {
            if (customStart.isAfter(customEnd)) {
                val tmp = customStart
                customStart = customEnd
                customEnd = tmp
                updateDateRangeLabels()
            }
            loadSummary()
        }
    }

    private fun showPeriodPicker() {
        val dialog = BottomSheetDialog(requireContext()).fitSystemBars()
        val sheet = DialogBshEarningsPeriodBinding.inflate(LayoutInflater.from(requireContext()))

        // Mark the currently-active period: accent label + a trailing check so the driver can
        // see which report period is selected at a glance.
        val accent = ContextCompat.getColor(requireContext(), R.color.app_color)
        val normal = ContextCompat.getColor(requireContext(), R.color.black_white)
        listOf(
            sheet.tvPeriodDay to PeriodType.DAY,
            sheet.tvPeriodWeek to PeriodType.WEEK,
            sheet.tvPeriodMonth to PeriodType.MONTH,
            sheet.tvPeriodCustom to PeriodType.CUSTOM,
        ).forEach { (tv, type) ->
            val selected = type == currentPeriodType
            tv.setBackgroundResource(
                if (selected) R.drawable.bg_nav_option_selected else R.drawable.bg_nav_option
            )
            tv.setTextColor(if (selected) accent else normal)
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.baseline_calendar_month_24, 0,
                if (selected) R.drawable.ph_check_circle else 0, 0
            )
        }

        sheet.tvPeriodDay.setOnClickListener {
            selectPeriodType(PeriodType.DAY)
            dialog.dismiss()
        }
        sheet.tvPeriodWeek.setOnClickListener {
            selectPeriodType(PeriodType.WEEK)
            dialog.dismiss()
        }
        sheet.tvPeriodMonth.setOnClickListener {
            selectPeriodType(PeriodType.MONTH)
            dialog.dismiss()
        }
        sheet.tvPeriodCustom.setOnClickListener {
            selectPeriodType(PeriodType.CUSTOM)
            dialog.dismiss()
        }
        dialog.setContentView(sheet.root)
        dialog.show()
    }

    private fun selectPeriodType(type: PeriodType) {
        currentPeriodType = type
        if (type == PeriodType.CUSTOM) {
            // Default the custom range to "this month so far".
            customStart = LocalDate.now().withDayOfMonth(1)
            customEnd = LocalDate.now()
            updateDateRangeLabels()
        } else {
            referenceDate = LocalDate.now()
        }
        refresh()
    }

    private fun pickDate(isStart: Boolean) {
        val initial = if (isStart) customStart else customEnd
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                if (isStart) customStart = picked else customEnd = picked
                updateDateRangeLabels()
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    private fun updateDateRangeLabels() {
        binding.tvStartDate.text = customStart.format(dateFormatter)
        binding.tvEndDate.text = customEnd.format(dateFormatter)
    }

    private fun refresh() {
        val isCustom = currentPeriodType == PeriodType.CUSTOM
        binding.cardDateRange.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding.llMonthNav.visibility = if (isCustom) View.GONE else View.VISIBLE

        binding.tvPeriodPill.text = periodTypeLabel()
        if (!isCustom) {
            binding.tvMonthLabel.text = formatPeriodLabel()
            binding.mcvNextMonth.alpha =
                if (periodStart(referenceDate.plus(1, periodUnit())).isAfter(LocalDate.now())) 0.3f
                else 1f
        }
        loadSummary()
    }

    private fun periodTypeLabel(): String = when (currentPeriodType) {
        PeriodType.DAY -> getString(R.string.driver_earnings_period_day)
        PeriodType.WEEK -> getString(R.string.driver_earnings_period_week)
        PeriodType.MONTH -> getString(R.string.driver_earnings_period_month)
        PeriodType.CUSTOM -> getString(R.string.driver_earnings_period_custom)
    }

    private fun periodUnit() = when (currentPeriodType) {
        PeriodType.DAY -> java.time.temporal.ChronoUnit.DAYS
        PeriodType.WEEK -> java.time.temporal.ChronoUnit.WEEKS
        PeriodType.MONTH -> java.time.temporal.ChronoUnit.MONTHS
        PeriodType.CUSTOM -> java.time.temporal.ChronoUnit.DAYS
    }

    private fun periodStart(date: LocalDate): LocalDate = when (currentPeriodType) {
        PeriodType.DAY -> date
        PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodType.MONTH -> date.withDayOfMonth(1)
        PeriodType.CUSTOM -> customStart
    }

    private fun periodEnd(date: LocalDate): LocalDate = when (currentPeriodType) {
        PeriodType.DAY -> date
        PeriodType.WEEK -> periodStart(date).plusDays(6)
        PeriodType.MONTH -> date.with(TemporalAdjusters.lastDayOfMonth())
        PeriodType.CUSTOM -> customEnd
    }

    private fun loadSummary() {
        val rangeStart = if (currentPeriodType == PeriodType.CUSTOM) customStart
        else periodStart(referenceDate)
        val rangeEnd = if (currentPeriodType == PeriodType.CUSTOM) customEnd
        else periodEnd(referenceDate)
        val from = rangeStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val to = rangeEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getSummary(PERIOD_CUSTOM, from, to).collect { state ->
                when (state) {
                    is Resource.Loading -> loadingVisible()
                    is Resource.Success -> {
                        screenVisible()
                        state.data?.data?.let { renderSummary(it) }
                    }

                    is Resource.Error -> {
                        screenVisible()
                        showToast(state.message ?: "")
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderSummary(summary: DriverEarningsSummary) {
        val sum = getString(R.string.sum)
        binding.tvEarned.text = Helper.formatPrice(summary.totals.earned.toString())
        binding.tvTripsCount.text = summary.totals.ordersCount.toString()
        binding.tvDistance.text =
            "${
                String.format(
                    Locale.US,
                    "%.2f",
                    summary.totals.distanceKm
                )
            }${getString(R.string.km)}"
        binding.tvBonus.text = Helper.formatPrice(summary.totals.bonus.toString()) + sum

        val bestDay = summary.graph.points.maxOfOrNull { it.earned } ?: 0L
        if (bestDay > 0L) {
            binding.llBestDay.visibility = View.VISIBLE
            binding.tvBestDay.text = Helper.formatPrice(bestDay.toString())
        } else {
            binding.llBestDay.visibility = View.GONE
        }

        renderChart(summary.graph.points)
    }

    private fun setupChart() {
        val axisTextColor = ContextCompat.getColor(requireContext(), R.color.gray_full)
        val axisGridColor =
            ContextCompat.getColor(requireContext(), R.color.gray_middle_black_middle)
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            isHighlightPerTapEnabled = false
            isHighlightPerDragEnabled = false
            // Bars rounded on the TOP corners only (flat base + sides) — iOS-style.
            renderer = TopRoundedBarChartRenderer(this, animator, viewPortHandler, radiusDp = 6f)
            extraTopOffset = 18f
            extraBottomOffset = 4f
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                setDrawAxisLine(false)
                gridColor = axisGridColor
                enableGridDashedLine(6f, 6f, 0f)
                textColor = axisTextColor
                textSize = 10f
                valueFormatter = PriceAxisFormatter()
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                labelRotationAngle = 0f
                textColor = axisTextColor
                textSize = 10f
            }
        }
    }

    private fun renderChart(points: List<DriverEarningsSummary.Graph.Point>) {
        // The API fills every day in the period, so an "empty" period still returns
        // zero-valued points — treat all-zero (or no points) as "no orders".
        if (points.none { it.earned > 0L }) {
            binding.barChart.visibility = View.GONE
            binding.tvGraphEmpty.visibility = View.VISIBLE
            return
        }
        binding.barChart.visibility = View.VISIBLE
        binding.tvGraphEmpty.visibility = View.GONE

        val amber = ContextCompat.getColor(requireContext(), R.color.app_color)
        val entries = points.mapIndexed { i, p -> BarEntry(i.toFloat(), p.earned.toFloat()) }
        val labels = points.map { formatDayLabel(it.date) }

        val dataSet = BarDataSet(entries, "earned").apply {
            color = amber
            highLightAlpha = 0
            setDrawValues(true)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.black_white)
            valueTextSize = 9f
            valueFormatter = BarValueFormatter()
        }

        binding.barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.72f }
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = minOf(labels.size, VISIBLE_BARS.toInt())
            setFitBars(true)
            // Show a fixed window of bold bars and let the user drag horizontally
            // through the rest of the period instead of squeezing every day in.
            isDragEnabled = true
            notifyDataSetChanged()
            setVisibleXRangeMaximum(VISIBLE_BARS)
            animateY(600)
            invalidate()
        }
        // Open centered on the most recent NON-EMPTY day (today / the last day with earnings),
        // not the empty first-of-month that sits far from today. moveViewToX sets the window's
        // LEFT edge; posted so the chart viewport is laid out before we scroll. The chart clamps
        // the right edge automatically, so a focus near the end lands at the right of the window.
        val focusIndex = points.indexOfLast { it.earned > 0L }.coerceAtLeast(0)
        val leftEdge = (focusIndex - VISIBLE_BARS / 2f).coerceAtLeast(0f)
        binding.barChart.post {
            if (_binding != null) binding.barChart.moveViewToX(leftEdge)
        }
    }

    private fun formatPeriodLabel(): String {
        val locale = Locale.getDefault()
        return when (currentPeriodType) {
            PeriodType.DAY -> {
                val d = referenceDate
                val month = d.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                "${d.dayOfMonth} $month ${d.year}"
            }

            PeriodType.WEEK -> {
                val start = periodStart(referenceDate)
                val end = periodEnd(referenceDate)
                val month = start.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                "${start.dayOfMonth} – ${end.dayOfMonth} $month ${start.year}"
            }

            PeriodType.MONTH -> {
                val ym = YearMonth.from(referenceDate)
                val name = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                "$name ${ym.year}"
            }

            PeriodType.CUSTOM ->
                "${customStart.format(dateFormatter)} – ${customEnd.format(dateFormatter)}"
        }
    }

    private fun formatDayLabel(isoDate: String): String {
        return try {
            val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
            "%02d.%02d".format(date.dayOfMonth, date.monthValue)
        } catch (_: Exception) {
            isoDate.takeLast(5)
        }
    }

    private fun loadingVisible() {
        binding.progressBar.visibility = View.VISIBLE
        binding.content.visibility = View.GONE
    }

    private fun screenVisible() {
        binding.progressBar.visibility = View.GONE
        binding.content.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PriceAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            if (value <= 0f) return "0"
            return Helper.formatPrice(value.toLong().toString())
        }
    }

    /** Draws the earned amount above each bar; blank for empty (zero) days. */
    private class BarValueFormatter : ValueFormatter() {
        override fun getBarLabel(barEntry: BarEntry?): String {
            val value = barEntry?.y ?: 0f
            if (value <= 0f) return ""
            return Helper.formatPrice(value.toLong().toString())
        }
    }

    companion object {
        private const val PERIOD_CUSTOM = "custom"

        // How many bars stay visible at once; the rest scroll horizontally.
        private const val VISIBLE_BARS = 8f
    }
}
