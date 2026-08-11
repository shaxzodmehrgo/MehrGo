package uz.teamwork.mehrgodriver.domain.repository

import okhttp3.MultipartBody
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

interface MainRepository {

    suspend fun getUser(): BaseResponse<User>

    suspend fun getOrderAddress(): BaseResponse<List<OrderAddress>>

    suspend fun getOrders(orderAddressId: String): BaseResponse<List<Order>>

    suspend fun getAllOrders(): BaseResponse<List<Order>>

    suspend fun orderAccept(orderId: Int): BaseResponse<Order>

    suspend fun orderSkip(orderId: Int): BaseResponse<Any>

    suspend fun getOrderCancelReasons(): BaseResponse<List<OrderCancelReason>>

    suspend fun orderCancel(orderId: Int, issueId: Int): BaseResponse<Any>

    suspend fun socketOrderCancel(orderId: Int, issueId: Int): BaseResponse<Any>

    suspend fun socketOrderCancelAndRenew(orderId: Int, issueId: Int): BaseResponse<Any>

    suspend fun orderStart(orderId: Int): BaseResponse<Order>

    suspend fun orderArrive(orderId: Int): BaseResponse<Order>

    suspend fun orderGo(orderId: Int): BaseResponse<Order>

    suspend fun getOrderServices(orderId: Int): BaseResponse<List<OrderService>>

    suspend fun addRemoveService(orderId: Int, serviceId: Int, action: Int): BaseResponse<Any>

    suspend fun orderFinish(
        orderId: String,
        requestOrderFinish: RequestOrderFinish
    ): BaseResponse<User>

    suspend fun getAddressInBranch(branchId: String): BaseResponse<List<AddressInBranch>>

    suspend fun getRecommendOrders(): BaseResponse<List<Order>>

    suspend fun getActiveMyOrders(): BaseResponse<List<Order>>

    suspend fun uploadLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float
    ): BaseResponse<Any>

    suspend fun getInstruction(): BaseResponse<List<Instruction>>

    suspend fun getNotifications(): BaseResponse<List<Notification>>

    suspend fun getVideos(): BaseResponse<List<Video>>

    suspend fun updateApp(token: String?, deviceToken: String? = null): UpdateApp

    suspend fun sendErrors(errors: List<ErrorRequest>): BaseResponse<Any>

    suspend fun purchaseSubscription(id: Int): BaseResponse<Any>

    suspend fun startWork(deviceToken: String? = null): BaseResponse<Any>

    suspend fun finishWork(): BaseResponse<Any>

    suspend fun getVerificationStatus(): BaseResponse<VerificationStatus>

    suspend fun uploadLicense(license: MultipartBody.Part): BaseResponse<LicenseUploadResult>

    suspend fun getTariffs(branchId: Int): BaseResponse<List<Tariff>>

    suspend fun orderCreate(request: OrderCreateRequest): BaseResponse<Any>

    suspend fun getDriverEarningsSummary(
        period: String,
        from: String? = null,
        to: String? = null,
        tz: String? = null
    ): BaseResponse<DriverEarningsSummary>

    suspend fun changeLanguage(language: String): BaseResponse<String>

    suspend fun sendGpsBatch(
        orderId: Int,
        request: FareGpsBatchRequest
    ): BaseResponse<FareResponse>

    suspend fun getFare(orderId: Int): BaseResponse<FareResponse>

    /**
     * One-shot history page. The paged list has its own Paging source; this exists so the receipt
     * screen can read a just-finished order's settled `myOrder.payment` when the `order_completed`
     * socket frame never arrives — `order/complete` itself returns no receipt.
     */
    suspend fun getOrderHistory(page: Int): BaseResponse<OrderHistory>

    // Paylov cards + balance withdrawal
    suspend fun paylovCardCreate(
        cardNumber: String,
        expireDate: String,
        phoneNumber: String? = null
    ): BaseResponse<CardCreateResult>

    suspend fun paylovCardConfirm(
        cardId: String,
        otp: String,
        cardName: String? = null,
        pinfl: String? = null
    ): BaseResponse<PaylovCard>

    suspend fun paylovCards(): BaseResponse<CardsResponse>

    suspend fun paylovCardDelete(cardId: String): BaseResponse<Any>

    suspend fun paylovWithdrawalInfo(): BaseResponse<WithdrawalInfo>

    suspend fun paylovWithdrawalCreate(
        cardId: String,
        amount: Long,
        note: String? = null
    ): BaseResponse<WithdrawalRequest>

    suspend fun paylovWithdrawalIndex(page: Int, perPage: Int): BaseResponse<WithdrawalList>

    suspend fun paylovWithdrawalCancel(id: Int): BaseResponse<Any>

    suspend fun paylovFillBalance(cardId: String, amount: Long): BaseResponse<Any>

    suspend fun paylovPaymentCreate(cardId: String, amount: Long): BaseResponse<PaymentCreateResult>

    suspend fun paylovPaymentConfirm(transactionId: String, otp: String): BaseResponse<Any>

    suspend fun getBalanceHistory(
        page: Int,
        perPage: Int,
        type: Int? = null,
        reason: Int? = null,
        from: String? = null,
        to: String? = null
    ): BaseResponse<BalanceHistory>
}