package uz.teamwork.mehrgodriver.domain.model

data class OrderAddress(
    val id: Int,
    val name: String,
    val orders: String,
    val cars: Int,
    val deliveries: Int
)