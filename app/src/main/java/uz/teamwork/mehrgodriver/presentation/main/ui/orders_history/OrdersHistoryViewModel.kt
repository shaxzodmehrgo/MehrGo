package uz.teamwork.mehrgodriver.presentation.main.ui.orders_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uz.teamwork.mehrgodriver.domain.use_case.main.data_source.OrderHistoryDataSource
import uz.teamwork.mehrgodriver.presentation.main.adapter.HistoryRow
import uz.teamwork.mehrgodriver.presentation.main.adapter.toHistoryRows
import javax.inject.Inject

@HiltViewModel
class OrdersHistoryViewModel @Inject constructor(private val orderHistoryDataSource: OrderHistoryDataSource) :
    ViewModel() {
    /**
     * Built ONCE. This was a function, so every `getOrdersHistory()` call — and the fragment calls
     * it from onViewCreated — constructed a fresh Pager with its own `cachedIn(viewModelScope)`.
     * History → detail → back therefore refetched the whole paged list from the network and left
     * the previous PageFetcher alive in viewModelScope. As a val the cache is what it is meant to
     * be: the list survives the round trip and the scroll position with it.
     */
    val ordersHistory: Flow<PagingData<HistoryRow>> =
        Pager(PagingConfig(1), pagingSourceFactory = { orderHistoryDataSource })
            .flow
            // Group the list by day with "Bugun" / "1 iyul" headers, like the client app.
            .map { it.toHistoryRows(withHeaders = true) }
            .cachedIn(viewModelScope)
}
