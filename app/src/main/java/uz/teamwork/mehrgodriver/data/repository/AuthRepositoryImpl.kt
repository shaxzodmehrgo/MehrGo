package uz.teamwork.mehrgodriver.data.repository

import okhttp3.MultipartBody
import okhttp3.RequestBody
import uz.teamwork.mehrgodriver.data.remote.ApiService
import uz.teamwork.mehrgodriver.domain.model.CarBrand
import uz.teamwork.mehrgodriver.domain.model.CarColor
import uz.teamwork.mehrgodriver.domain.model.CarModel
import uz.teamwork.mehrgodriver.domain.model.DeviceTokenResult
import uz.teamwork.mehrgodriver.domain.model.Introduce
import uz.teamwork.mehrgodriver.domain.model.PasswordRecovery
import uz.teamwork.mehrgodriver.domain.model.PasswordRecoveryResendCode
import uz.teamwork.mehrgodriver.domain.model.Region
import uz.teamwork.mehrgodriver.domain.model.SignUp
import uz.teamwork.mehrgodriver.domain.model.SignUpResendCode
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus
import uz.teamwork.mehrgodriver.domain.model.TermsOfUse
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.requests.DeviceTokenRequest
import uz.teamwork.mehrgodriver.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(private val apiService: ApiService) : AuthRepository {

    override suspend fun getIntroduceData(): BaseResponse<List<Introduce>> {
        return apiService.getIntroduceData()
    }

    override suspend fun login(phoneNumber: String, password: String): BaseResponse<User> {
        return apiService.login(phoneNumber, password)
    }

    override suspend fun signUp(
        phoneNumber: String, firstName: String, fatherName: String,
        lastName: String, password: String, confirmPassword: String, channel: String
    ): BaseResponse<SignUp> {
        return apiService.signUp(
            phoneNumber,
            firstName,
            fatherName,
            lastName,
            password,
            confirmPassword,
            channel
        )
    }

    override suspend fun signUpResendCode(
        authKey: String,
        channel: String
    ): BaseResponse<SignUpResendCode> {
        return apiService.signUpResendCode(authKey, channel)
    }

    override suspend fun telegramAuthStatus(phone: String): BaseResponse<TelegramAuthStatus> {
        return apiService.telegramAuthStatus(phone)
    }

    override suspend fun signUpVerifyCode(
        authKey: String,
        verifyCode: Int,
        deviceToken: String?
    ): BaseResponse<User> {
        return apiService.signUpVerifyCode(authKey, verifyCode, deviceToken)
    }

    override suspend fun registerDeviceToken(token: String): BaseResponse<DeviceTokenResult> {
        return apiService.registerDeviceToken(DeviceTokenRequest(token))
    }

    override suspend fun passwordRecovery(
        phoneNumber: String,
        channel: String
    ): BaseResponse<PasswordRecovery> {
        return apiService.passwordRecovery(phoneNumber, channel)
    }

    override suspend fun passwordRecoveryResendCode(
        authKey: String,
        channel: String
    ): BaseResponse<PasswordRecoveryResendCode> {
        return apiService.passwordRecoveryResendCode(authKey, channel)
    }

    override suspend fun passwordRecoveryChangePassword(
        password: String,
        code: Int,
        authKeyVerify: String
    ): BaseResponse<User> {
        return apiService.passwordRecoveryChangePassword(password, password, code, authKeyVerify)
    }

    override suspend fun getRegions(): BaseResponse<List<Region>> {
        return apiService.getRegions()
    }

    override suspend fun getCarBrands(): BaseResponse<List<CarBrand>> {
        return apiService.getCarBrands()
    }

    override suspend fun getCarModels(): BaseResponse<List<CarModel>> {
        return apiService.getCarModels()
    }

    override suspend fun getCarColors(): BaseResponse<List<CarColor>> {
        return apiService.getCarColors()
    }

    override suspend fun uploadDriverInfo(
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
    ): BaseResponse<User> {
        return apiService.uploadDriverInfo(
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
    }

    override suspend fun termsOfUse(): BaseResponse<TermsOfUse> {
        return apiService.getTermOfUse()
    }
}