package uz.teamwork.mehrgodriver.presentation.main.ui.orders_history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.databinding.FragmentOrdersHistoryBinding
import uz.teamwork.mehrgodriver.presentation.main.adapter.OrderHistoryAdapter
import uz.teamwork.mehrgodriver.presentation.main.adapter.OrderHistoryLoadStateAdapter

@AndroidEntryPoint
class OrdersHistoryFragment : Fragment() {
    private var _binding: FragmentOrdersHistoryBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val ordersHistoryViewModel: OrdersHistoryViewModel by viewModels()

    private val orderHistoryAdapter = OrderHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersHistoryBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderHistoryAdapter.onItemClick = { item ->
            findNavController().navigate(
                R.id.action_ordersHistoryFragment_to_orderHistoryDetailFragment,
                bundleOf(OrderHistoryDetailFragment.ARG_ITEM to item)
            )
        }
        // Infinite scroll with a footer: spinner while the next page loads, retry on error.
        binding.rvOrdersHistory.adapter = orderHistoryAdapter.withLoadStateFooter(
            OrderHistoryLoadStateAdapter { orderHistoryAdapter.retry() }
        )

        orderHistoryAdapter.addLoadStateListener { state ->
            if (_binding == null) return@addLoadStateListener
            val refresh = state.source.refresh
            // Full-screen spinner only for the very first page; later pages use the footer.
            binding.progressBar.visibility =
                if (refresh is LoadState.Loading && orderHistoryAdapter.itemCount == 0) View.VISIBLE
                else View.GONE
            val isEmpty = refresh is LoadState.NotLoading &&
                    state.append.endOfPaginationReached &&
                    orderHistoryAdapter.itemCount == 0
            binding.tvOrdersEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            ordersHistoryViewModel.ordersHistory.collectLatest { ordersHistory ->
                orderHistoryAdapter.submitData(ordersHistory)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvOrdersHistory.adapter = null
        _binding = null
    }
}