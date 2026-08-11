package uz.teamwork.mehrgodriver.domain.repository

import okhttp3.MultipartBody
import okhttp3.RequestBody
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

interface AuthRepository {
    suspend fun getIntroduceData(): BaseResponse<List<Introduce>>

    suspend fun login(phoneNumber: String, password: String): BaseResponse<User>

    suspend fun signUp(
        phoneNumber: String,
        firstName: String,
        fatherName: String,
        lastName: String,
        password: String,
        confirmPassword: String,
        channel: String = "sms"
    ): BaseResponse<SignUp>

    suspend fun signUpResendCode(
        authKey: String,
        channel: String = "sms"
    ): BaseResponse<SignUpResendCode>

    // Telegram OTP availability/link status; callers tolerate 404/failure and fall back to SMS.
    suspend fun telegramAuthStatus(phone: String): BaseResponse<TelegramAuthStatus>

    suspend fun signUpVerifyCode(
        authKey: String,
        verifyCode: Int,
        deviceToken: String? = null
    ): BaseResponse<User>

    suspend fun registerDeviceToken(token: String): BaseResponse<DeviceTokenResult>

    suspend fun passwordRecovery(
        phoneNumber: String,
        channel: String = "sms"
    ): BaseResponse<PasswordRecovery>

    suspend fun passwordRecoveryResendCode(
        authKey: String,
        channel: String = "sms"
    ): BaseResponse<PasswordRecoveryResendCode>

    suspend fun passwordRecoveryChangePassword(
        password: String,
        code: Int,
        authKeyVerify: String
    ): BaseResponse<User>

    suspend fun getRegions(): BaseResponse<List<Region>>

    suspend fun getCarBrands(): BaseResponse<List<CarBrand>>

    suspend fun getCarModels(): BaseResponse<List<CarModel>>

    suspend fun getCarColors(): BaseResponse<List<CarColor>>

    suspend fun uploadDriverInfo(
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
    ): BaseResponse<User>

    suspend fun termsOfUse(): BaseResponse<TermsOfUse>
}