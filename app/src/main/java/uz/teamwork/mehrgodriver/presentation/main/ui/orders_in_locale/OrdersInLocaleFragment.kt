package uz.teamwork.mehrgodriver.presentation.main.ui.orders_in_locale

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentOrdersInLocaleBinding
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.presentation.main.adapter.CalculationAdapter

@AndroidEntryPoint
class OrdersInLocaleFragment : Fragment(), CalculationAdapter.OnCalculationClickListener {
    private var _binding: FragmentOrdersInLocaleBinding? = null
    private val binding get() = _binding!!

    private var calculationAdapter: CalculationAdapter? = null

    // Client-side pagination: Room hands us the whole (newest-first) list at once, but we reveal it a
    // page at a time so a driver with many saved trips gets a short, snappy list instead of one huge
    // bind (each row also runs a distance sum over its GPS points).
    private var fullList: List<Calculation> = emptyList()
    private var visibleCount = PAGE_SIZE

    // For ViewModel
    private val viewModel: OrdersInLocaleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersInLocaleBinding.inflate(inflater, container, false)

        calculationAdapter = CalculationAdapter(this@OrdersInLocaleFragment)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.rvOrders.adapter = calculationAdapter
        // Reveal the next page when the driver scrolls near the bottom of what's shown.
        binding.rvOrders.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= visibleCount - PAGE_PREFETCH &&
                    visibleCount < fullList.size
                ) {
                    visibleCount = (visibleCount + PAGE_SIZE).coerceAtMost(fullList.size)
                    submitPage()
                }
            }
        })

        // Loading state while Room reads the saved trips — the screen sat fully blank until the
        // first emission, which read as broken on slower devices.
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            viewModel.getCalculations().collect { list ->
                binding.progressBar.visibility = View.GONE
                fullList = list
                // Keep the driver's revealed depth across refreshes, but always show at least one page
                // (and never more than exist).
                visibleCount = visibleCount.coerceIn(minOf(PAGE_SIZE, list.size), list.size)
                submitPage()
                binding.tvOrdersEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                // Count pill in the toolbar — hidden when there's nothing saved.
                binding.tvCount.text = list.size.toString()
                binding.tvCount.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun submitPage() {
        calculationAdapter?.submitList(fullList.take(visibleCount))
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val PAGE_PREFETCH = 4
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCalculationClick(calculation: Calculation) {
        var locations = ""
        calculation.locations.forEach {
            locations += "lat:${it.latitude}, lon:${it.longitude}, acy:${it.accuracy}\n"
        }

        val clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("label", locations)
        clipboardManager.setPrimaryClip(clipData)
        // The copy is otherwise invisible — confirm it so the tap doesn't feel dead.
        showToast(getString(R.string.gps_track_copied))
    }
}