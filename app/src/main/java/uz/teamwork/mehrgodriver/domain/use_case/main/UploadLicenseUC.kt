package uz.teamwork.mehrgodriver.domain.use_case.main

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.LicenseUploadResult
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import java.io.IOException
import javax.inject.Inject

class UploadLicenseUC @Inject constructor(
    private val repository: MainRepository,
    private val gson: Gson
) {
    operator fun invoke(
        license: MultipartBody.Part
    ): Flow<Resource<BaseResponse<LicenseUploadResult>>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.uploadLicense(license)
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
