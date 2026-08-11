package uz.teamwork.mehrgodriver.common.socket

import uz.teamwork.mehrgodriver.domain.model.Order

data class SocketOrderResponse(
    val status: Int,
    val key: String,
    val data: Order
)
