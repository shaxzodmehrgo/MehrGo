package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class BalanceHistoryItem(
    val id: Int? = null,
    val type: Int? = null,
    @SerializedName("type_name") val typeName: String? = null,
    val value: Long? = null,
    @SerializedName("total_after") val totalAfter: Long? = null,
    val reason: Int? = null,
    @SerializedName("reason_name") val reasonName: String? = null,
    val info: String? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
    val datetime: String? = null
)
