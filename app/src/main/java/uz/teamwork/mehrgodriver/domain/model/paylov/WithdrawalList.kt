package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class WithdrawalList(
    val items: List<WithdrawalRequest>? = null,
    val total: Int? = null,
    val page: Int? = null,
    @SerializedName("per_page") val perPage: Int? = null
)
