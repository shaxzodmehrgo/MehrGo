package uz.teamwork.mehrgodriver.presentation.maps.yandex_map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.OrderCancelReason
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.VerificationStatus
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse
import uz.teamwork.mehrgodriver.domain.model.requests.RequestOrderFinish
import uz.teamwork.mehrgodriver.domain.use_case.main.AllOrdersUC
import uz.teamwork.mehrgodriver.domain.use_case.main.FinishWorkUC
import uz.teamwork.mehrgodriver.domain.use_case.main.GetFareUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderArriveUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderCancelReasonsUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderFinishUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderGoUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderStartUC
import uz.teamwork.mehrgodriver.domain.use_case.main.SocketOrderCancelUC
import uz.teamwork.mehrgodriver.domain.use_case.main.StartWorkUC
import uz.teamwork.mehrgodriver.domain.use_case.main.UserUC
import uz.teamwork.mehrgodriver.domain.use_case.main.VerificationStatusUC
import uz.teamwork.mehrgodriver.domain.use_case.route.RouteUC
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val orderCancelReasonUC: OrderCancelReasonsUC,
    private val socketOrderCancelUC: SocketOrderCancelUC,
    private val orderStartUC: OrderStartUC,
    private val orderArriveUC: OrderArriveUC,
    private val orderGoUC: OrderGoUC,
    private val orderFinishUC: OrderFinishUC,
    private val routeUC: RouteUC,
    private val allOrdersUC: AllOrdersUC,
    private val userUC: UserUC,
    private val startWorkUC: StartWorkUC,
    private val finishWorkUC: FinishWorkUC,
    private val verificationStatusUC: VerificationStatusUC,
    private val getFareUC: GetFareUC
) : ViewModel() {

    fun getUserMe(): Flow<Resource<BaseResponse<User>>> {
        return userUC.invoke()
    }

    /** Authoritative server fare snapshot (`GET order-gps/fare`) — same shape as the batch ack. */
    fun getFare(orderId: Int): Flow<Resource<BaseResponse<FareResponse>>> {
        return getFareUC.invoke(orderId)
    }

    fun getOrderCancelReasons(): Flow<Resource<BaseResponse<List<OrderCancelReason>>>> {
        return orderCancelReasonUC.invoke()
    }

    fun orderCancel(orderId: Int, issueId: Int): Flow<Resource<BaseResponse<Any>>> {
        return socketOrderCancelUC.invoke(orderId, issueId)
    }

    fun orderStart(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderStartUC.invoke(orderId)
    }

    fun orderArrive(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderArriveUC.invoke(orderId)
    }

    fun orderGo(orderId: Int): Flow<Resource<BaseResponse<Order>>> {
        return orderGoUC.invoke(orderId)
    }

    fun orderFinish(
        orderId: String,
        requestOrderFinish: RequestOrderFinish
    ): Flow<Resource<BaseResponse<User>>> {
        return orderFinishUC.invoke(orderId, requestOrderFinish)
    }

    fun getRoute(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): Flow<Resource<DirectionLocations>> {
        return routeUC.invoke(lon1, lat1, lon2, lat2)
    }

    fun getAllOrders(): Flow<Resource<BaseResponse<List<Order>>>> {
        return allOrdersUC.invoke()
    }

    fun startWork(): Flow<Resource<BaseResponse<Any>>> {
        return startWorkUC.invoke()
    }

    fun finishWork(): Flow<Resource<BaseResponse<Any>>> {
        return finishWorkUC.invoke()
    }

    fun getVerificationStatus(): Flow<Resource<BaseResponse<VerificationStatus>>> {
        return verificationStatusUC.invoke()
    }
}