package uz.teamwork.mehrgodriver.domain.model

data class OrderService(
    val name: String,
    val id: Int,
    val value: Int,
    var enabled: Boolean = false,
    val info: String? = null
)