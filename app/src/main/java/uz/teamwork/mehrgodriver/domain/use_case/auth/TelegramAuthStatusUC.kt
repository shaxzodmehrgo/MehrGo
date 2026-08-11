package uz.teamwork.mehrgodriver.domain.use_case.auth

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject

/**
 * Best-effort Telegram-OTP status lookup. The endpoint isn't deployed on every backend, so a
 * 404/HTTP/IO error surfaces as [Resource.Error] and the UI simply keeps the SMS-safe default
 * (Telegram button shown, channel=telegram falls back to SMS server-side).
 */
class TelegramAuthStatusUC @Inject constructor(
    private val repository: AuthRepository,
    private val gson: Gson
) {
    operator fun invoke(phone: String): Flow<Resource<BaseResponse<TelegramAuthStatus>>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.telegramAuthStatus(phone)
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
        }
    }
}
