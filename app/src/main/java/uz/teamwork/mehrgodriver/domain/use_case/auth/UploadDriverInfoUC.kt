package uz.teamwork.mehrgodriver.domain.use_case.auth

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.base.ErrorResponse
import uz.teamwork.mehrgodriver.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject

class UploadDriverInfoUC @Inject constructor(
    private val repository: AuthRepository,
    private val gson: Gson
) {

    operator fun invoke(
        authKey: RequestBody,
        specialityId: RequestBody,
        regionId: RequestBody,
        genderTypeId: RequestBody,
        birthday: RequestBody,
        address: RequestBody,
        passportNumber: RequestBody,
        passportGiveBy: RequestBody,
        passportGiveDate: RequestBody,
        licenseNumber: RequestBody,
        licenseTypeId: RequestBody,
        carModelId: RequestBody,
        carColorId: RequestBody,
        carNumber: RequestBody,
        carMade: RequestBody,
        photo: MultipartBody.Part,
        photos: List<MultipartBody.Part>
    ): Flow<Resource<BaseResponse<User>>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.uploadDriverInfo(
                authKey,
                specialityId,
                regionId,
                genderTypeId,
                birthday,
                address,
                passportNumber,
                passportGiveBy,
                passportGiveDate,
                licenseNumber,
                licenseTypeId,
                carModelId,
                carColorId,
                carNumber,
                carMade,
                photo,
                photos
            )
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
