package uz.teamwork.mehrgodriver.domain.use_case.main

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import java.io.IOException
import javax.inject.Inject

class GetFareUC @Inject constructor(
    private val repository: MainRepository,
    private val gson: Gson
) {

    operator fun invoke(orderId: Int): Flow<Resource<BaseResponse<FareResponse>>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.getFare(orderId)
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            // Parse first, emit AFTER: emitting inside a catch(Exception) can swallow a
            // downstream collector failure and re-emit — exception-transparency crash.
            val message = try {
                val jsonString = e.response()?.errorBody()?.string()
                gson.fromJson(jsonString, ErrorResponse::class.java)?.message
            } catch (parse: Exception) {
                null
            }
            emit(Resource.Error(message ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Converter/contract errors (e.g. a server field-type mismatch made Gson throw
            // NumberFormatException) escaped the Http/IO funnel and KILLED the app on the
            // finish path (prod crash 2026-07-20). Surface as a soft error instead — but LOG
            // loudly so a genuine defect here isn't silently downgraded to a toast.
            timber.log.Timber.e(e, "getFare: unexpected error (converter/contract?)")
            emit(Resource.Error(Helper.getUnexpectedError()))
        }
    }
}
