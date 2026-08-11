package uz.teamwork.mehrgodriver.domain.use_case.main

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import java.io.IOException
import javax.inject.Inject

/**
 * The settled bill for ONE just-finished order, read back from `order/history?expand=myOrder`.
 *
 * Why this exists: `order/complete` returns the driver User object and no receipt at all, so the
 * only authoritative settlement the app can see is the `order_completed` socket frame — and if the
 * socket is down or that frame is missed, the receipt screen would otherwise keep showing the
 * locally previewed total forever, with the commission line computed off it. History's
 * `myOrder.payment` block carries the same numbers the server actually billed
 * (`price`, `promo_code_discount`, `bonus_used`, `final_price`), so it is the REST fallback.
 *
 * A finished order is the newest row, so page 1 is enough; `null` means "not settled yet" (the row
 * can lag a moment behind the complete), which the caller retries rather than treats as an error.
 */
class OrderSettlementUC @Inject constructor(
    private val repository: MainRepository,
    private val gson: Gson
) {

    operator fun invoke(orderId: Int): Flow<Resource<OrderHistory.OrderHistoryItem?>> = flow {
        emit(Resource.Loading)

        try {
            val page = repository.getOrderHistory(1)
            emit(Resource.Success(page.data?.items?.firstOrNull { it.myOrder.id == orderId }))
        } catch (e: HttpException) {
            val message = try {
                gson.fromJson(
                    e.response()?.errorBody()?.string(),
                    ErrorResponse::class.java
                )?.message
            } catch (parse: Exception) {
                null
            }
            emit(Resource.Error(message ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.e(e, "orderSettlement: unexpected error (converter/contract?)")
            emit(Resource.Error(Helper.getUnexpectedError()))
        }
    }
}
