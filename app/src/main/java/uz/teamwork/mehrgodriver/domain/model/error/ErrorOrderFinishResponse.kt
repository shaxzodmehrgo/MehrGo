package uz.teamwork.mehrgodriver.domain.model.error


import com.google.gson.annotations.SerializedName

data class ErrorOrderFinishResponse(
    val message: String? = null,
    val data: Data
) {
    data class Data(
        @SerializedName("client_total_bonus")
        val clientTotalBonus: Long,

        val clientBonusSettings: ClientBonusSettings?
    ) {
        data class ClientBonusSettings(
            @SerializedName("minimum_amount_to_use_bonus")
            val minAmount: Long,

            @SerializedName("maximum_amount_to_use_bonus_per_order")
            val maxAmount: String,
        )
    }
}