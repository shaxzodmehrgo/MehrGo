package uz.teamwork.mehrgodriver.domain.use_case.main.data_source

import androidx.paging.PagingSource
import androidx.paging.PagingState
import timber.log.Timber
import uz.teamwork.mehrgodriver.data.remote.ApiService
import uz.teamwork.mehrgodriver.domain.model.SubscriptionData
import javax.inject.Inject

class SubscriptionsDataSource @Inject constructor(private val apiService: ApiService) :
    PagingSource<Int, SubscriptionData.Subscription>() {
    override fun getRefreshKey(state: PagingState<Int, SubscriptionData.Subscription>): Int? {
        return state.anchorPosition
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubscriptionData.Subscription> {
        return try {
            val nextPage: Int = params.key ?: FIRST_PAGE_INDEX
            val response = apiService.getSubscriptions(nextPage)

            Timber.d("RESPONSE: $response")
            val pageCount = response.data?._meta?.pageCount!!
            if (nextPage <= pageCount) {
                LoadResult.Page(
                    data = response.data?.items ?: emptyList(),
                    prevKey = null,
                    nextKey = nextPage + 1
                )
            } else {
                LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    companion object {
        private const val FIRST_PAGE_INDEX = 1
    }
}