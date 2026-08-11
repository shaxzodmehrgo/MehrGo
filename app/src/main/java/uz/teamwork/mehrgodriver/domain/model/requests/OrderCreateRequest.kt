package uz.teamwork.mehrgodriver.domain.model.requests

import com.google.gson.annotations.SerializedName

data class OrderCreateRequest(
    @SerializedName("tariff_id")
    val tariffId: Int,

    @SerializedName("branch_id")
    val branchId: Int,
    val info: String,
    val price: Int,

    @SerializedName("starting_price")
    val startingPrice: Int,
    val distance: Double,

    @SerializedName("use_bonus")
    val useBonus: Boolean,
    val services: List<OrderCreateService>,
    val locations: List<OrderCreateLocation>,

    // Creator's GPS fix at create time — populates the "created" order-history row,
    // matching the lat/long/accuracy the other order actions send. Filled in the
    // repository from MyTrackingService.lastLocationWholeApp; null when unavailable.
    val lat: Double? = null,
    @SerializedName("long")
    val long: Double? = null,
    val accuracy: Float? = null
) {
    data class OrderCreateService(
        val id: Int,
        val count: Int,
        val value: Int
    )

    data class OrderCreateLocation(
        val lat: Double,
        val lon: Double,
        val position: Int,
        val name: String
    )
}