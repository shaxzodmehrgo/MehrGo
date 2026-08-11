package uz.teamwork.mehrgodriver.data.repository

import okhttp3.MultipartBody
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.data.remote.ApiService
import uz.teamwork.mehrgodriver.domain.model.AddressInBranch
import uz.teamwork.mehrgodriver.domain.model.DriverEarningsSummary
import uz.teamwork.mehrgodriver.domain.model.Instruction
import uz.teamwork.mehrgodriver.domain.model.LicenseUploadResult
import uz.teamwork.mehrgodriver.domain.model.Notification
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderAddress
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason
import uz.teamwork.mehrgodriver.domain.model.OrderHistory
import uz.teamwork.mehrgodriver.domain.model.OrderService
import uz.teamwork.mehrgodriver.domain.model.Tariff
import uz.teamwork.mehrgodriver.domain.model.UpdateApp
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.VerificationStatus
import uz.teamwork.mehrgodriver.domain.model.Video
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.fare.FareGpsBatchRequest
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.BalanceHistory
import uz.teamwork.mehrgodriver.domain.model.paylov.CardCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.CardsResponse
import uz.teamwork.mehrgodriver.domain.model.paylov.PaylovCard
import uz.teamwork.mehrgodriver.domain.model.paylov.PaymentCreateResult
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalInfo
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalList
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalRequest
import uz.teamwork.mehrgodriver.domain.model.requests.ErrorRequest
import uz.teamwork.mehrgodriver.domain.model.requests.OrderCreateRequest
import uz.teamwork.mehrgodriver.domain.model.requests.RequestOrderFinish
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import javax.inject.Inject

class MainRepositoryImpl @Inject constructor(private val apiService: ApiService) : MainRepository {

    override suspend fun getUser(): BaseResponse<User> {
        return apiService.getUser()
    }

    override suspend fun getOrderAddress(): BaseResponse<List<OrderAddress>> {
        return apiService.getOrderAddress()
    }

    override suspend fun getOrders(orderAddressId: String): BaseResponse<List<Order>> {
        return apiService.getOrders(orderAddressId)
    }

    override suspend fun getAllOrders(): BaseResponse<List<Order>> {
        return apiService.getAllOrders()
    }

    override suspend fun orderAccept(orderId: Int): BaseResponse<Order> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderAccept(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun orderSkip(orderId: Int): BaseResponse<Any> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderSkip(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun getOrderCancelReasons(): BaseResponse<List<OrderCancelReason>> {
        return apiService.getOrderCancelReasons()
    }

    override suspend fun orderCancel(orderId: Int, issueId: Int): BaseResponse<Any> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderCancel(
            orderId,
            issueId,
            loc?.latitude,
            loc?.longitude,
            loc?.accuracy
        )
    }

    override suspend fun socketOrderCancel(orderId: Int, issueId: Int): BaseResponse<Any> {
        val loc = MyTrackingService.lastLocationWholeApp
        // NOTE: route to order/cancel, NOT socket/order-cancel. The socket/* routes
        // reject the driver bearer token with 401 ("register to use the app") — they're
        // a different auth surface (gateway/internal). order/cancel is the documented
        // driver cancel endpoint and carries the same lat/long/accuracy body.
        return apiService.orderCancel(
            orderId,
            issueId,
            loc?.latitude,
            loc?.longitude,
            loc?.accuracy
        )
    }

    override suspend fun socketOrderCancelAndRenew(orderId: Int, issueId: Int): BaseResponse<Any> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.socketOrderCancelAndRenew(
            orderId,
            issueId,
            loc?.latitude,
            loc?.longitude,
            loc?.accuracy
        )
    }

    override suspend fun orderStart(orderId: Int): BaseResponse<Order> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderStart(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun orderArrive(orderId: Int): BaseResponse<Order> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderArrive(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun orderGo(orderId: Int): BaseResponse<Order> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderGo(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun getOrderServices(orderId: Int): BaseResponse<List<OrderService>> {
        return apiService.getOrderServices(orderId)
    }

    override suspend fun addRemoveService(
        orderId: Int,
        serviceId: Int,
        action: Int
    ): BaseResponse<Any> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.addRemoveService(
            orderId,
            serviceId,
            action,
            loc?.latitude,
            loc?.longitude,
            loc?.accuracy
        )
    }

    override suspend fun orderFinish(
        orderId: String,
        requestOrderFinish: RequestOrderFinish,
    ): BaseResponse<User> {
        return apiService.orderFinish(orderId, requestOrderFinish)
    }

    override suspend fun getAddressInBranch(branchId: String): BaseResponse<List<AddressInBranch>> {
        return apiService.getAddressInBranch(branchId)
    }

    override suspend fun getRecommendOrders(): BaseResponse<List<Order>> {
        return apiService.getRecommendOrder()
    }

    override suspend fun getActiveMyOrders(): BaseResponse<List<Order>> {
        return apiService.getActiveMyOrders()
    }

    override suspend fun uploadLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float
    ): BaseResponse<Any> {
        return apiService.uploadLocation(latitude, longitude, bearing)
    }

    override suspend fun getInstruction(): BaseResponse<List<Instruction>> {
        return apiService.getInstruction()
    }

    override suspend fun getNotifications(): BaseResponse<List<Notification>> {
        return apiService.getNotifications()
    }

    override suspend fun getVideos(): BaseResponse<List<Video>> {
        return apiService.getVideos()
    }

    override suspend fun updateApp(token: String?, deviceToken: String?): UpdateApp {
        return apiService.updateApp(token, deviceToken)
    }

    override suspend fun sendErrors(errors: List<ErrorRequest>): BaseResponse<Any> {
        return apiService.sendErrors(errors)
    }

    override suspend fun purchaseSubscription(id: Int): BaseResponse<Any> {
        return apiService.purchaseSubscription(id)
    }

    override suspend fun startWork(deviceToken: String?): BaseResponse<Any> {
        return apiService.startWork(deviceToken)
    }

    override suspend fun finishWork(): BaseResponse<Any> {
        return apiService.finishWork()
    }

    override suspend fun getVerificationStatus(): BaseResponse<VerificationStatus> {
        return apiService.getVerificationStatus()
    }

    override suspend fun uploadLicense(license: MultipartBody.Part): BaseResponse<LicenseUploadResult> {
        return apiService.uploadLicense(license)
    }

    override suspend fun getTariffs(branchId: Int): BaseResponse<List<Tariff>> {
        return apiService.getTariffs(branchId)
    }

    override suspend fun orderCreate(request: OrderCreateRequest): BaseResponse<Any> {
        // Stamp the creator's GPS fix onto the request so the "created" history row
        // carries location like every other order action.
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.orderCreate(
            request.copy(lat = loc?.latitude, long = loc?.longitude, accuracy = loc?.accuracy)
        )
    }

    override suspend fun getDriverEarningsSummary(
        period: String,
        from: String?,
        to: String?,
        tz: String?
    ): BaseResponse<DriverEarningsSummary> {
        return apiService.getDriverEarningsSummary(period, from, to, tz)
    }

    override suspend fun changeLanguage(language: String): BaseResponse<String> {
        return apiService.changeLanguage(language)
    }

    override suspend fun sendGpsBatch(
        orderId: Int,
        request: FareGpsBatchRequest
    ): BaseResponse<FareResponse> {
        return apiService.sendGpsBatch(orderId, request)
    }

    override suspend fun getFare(orderId: Int): BaseResponse<FareResponse> {
        val loc = MyTrackingService.lastLocationWholeApp
        return apiService.getFare(orderId, loc?.latitude, loc?.longitude, loc?.accuracy)
    }

    override suspend fun getOrderHistory(page: Int): BaseResponse<OrderHistory> {
        return apiService.getOrderHistory(page)
    }

    override suspend fun paylovCardCreate(
        cardNumber: String,
        expireDate: String,
        phoneNumber: String?
    ): BaseResponse<CardCreateResult> {
        return apiService.paylovCardCreate(cardNumber, expireDate, phoneNumber)
    }

    override suspend fun paylovCardConfirm(
        cardId: String,
        otp: String,
        cardName: String?,
        pinfl: String?
    ): BaseResponse<PaylovCard> {
        return apiService.paylovCardConfirm(cardId, otp, cardName, pinfl)
    }

    override suspend fun paylovCards(): BaseResponse<CardsResponse> {
        return apiService.paylovCards()
    }

    override suspend fun paylovCardDelete(cardId: String): BaseResponse<Any> {
        return apiService.paylovCardDelete(cardId)
    }

    override suspend fun paylovWithdrawalInfo(): BaseResponse<WithdrawalInfo> {
        return apiService.paylovWithdrawalInfo()
    }

    override suspend fun paylovWithdrawalCreate(
        cardId: String,
        amount: Long,
        note: String?
    ): BaseResponse<WithdrawalRequest> {
        return apiService.paylovWithdrawalCreate(cardId, amount, note)
    }

    override suspend fun paylovWithdrawalIndex(
        page: Int,
        perPage: Int
    ): BaseResponse<WithdrawalList> {
        return apiService.paylovWithdrawalIndex(page, perPage)
    }

    override suspend fun paylovWithdrawalCancel(id: Int): BaseResponse<Any> {
        return apiService.paylovWithdrawalCancel(id)
    }

    override suspend fun paylovFillBalance(cardId: String, amount: Long): BaseResponse<Any> {
        return apiService.paylovFillBalance(cardId, amount)
    }

    override suspend fun paylovPaymentCreate(
        cardId: String,
        amount: Long
    ): BaseResponse<PaymentCreateResult> {
        return apiService.paylovPaymentCreate(cardId, amount)
    }

    override suspend fun paylovPaymentConfirm(
        transactionId: String,
        otp: String
    ): BaseResponse<Any> {
        return apiService.paylovPaymentConfirm(transactionId, otp)
    }

    override suspend fun getBalanceHistory(
        page: Int,
        perPage: Int,
        type: Int?,
        reason: Int?,
        from: String?,
        to: String?
    ): BaseResponse<BalanceHistory> {
        return apiService.getBalanceHistory(page, perPage, type, reason, from, to)
    }
}