package uz.teamwork.mehrgodriver.domain.use_case.main

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.requests.RequestOrderFinish
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import java.io.IOException
import javax.inject.Inject

class OrderFinishUC @Inject constructor(
    private val mainRepository: MainRepository,
    private val gson: Gson
) {

    operator fun invoke(
        orderId: String,
        requestOrderFinish: RequestOrderFinish
    ): Flow<Resource<BaseResponse<User>>> = flow {
        emit(Resource.Loading)

        try {
            val response = mainRepository.orderFinish(orderId, requestOrderFinish)
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            // Read first, emit AFTER: this exact emit-inside-catch(Exception) swallowed a
            // downstream collector failure and re-emitted → SafeCollector
            // exceptionTransparencyViolated (prod crash). Raw body kept — the finish flow
            // parses the error JSON itself.
            val body = try {
                e.response()?.errorBody()?.string()
            } catch (read: Exception) {
                null
            }
            emit(Resource.Error(body ?: Helper.getUnexpectedError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}