package uz.teamwork.mehrgodriver.presentation.main.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.SubscriptionData
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.PurchaseSubscriptionUC
import uz.teamwork.mehrgodriver.domain.use_case.main.data_source.SubscriptionsDataSource
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionsDataSource: SubscriptionsDataSource,
    private val purchaseSubscriptionUC: PurchaseSubscriptionUC
) : ViewModel() {
    fun getSubscriptions(): Flow<PagingData<SubscriptionData.Subscription>> {
        return Pager(PagingConfig(1), pagingSourceFactory = { subscriptionsDataSource })
            .flow.cachedIn(viewModelScope)
    }

    fun purchaseSubscription(id: Int): Flow<Resource<BaseResponse<Any>>> {
        return purchaseSubscriptionUC.invoke(id)
    }
}