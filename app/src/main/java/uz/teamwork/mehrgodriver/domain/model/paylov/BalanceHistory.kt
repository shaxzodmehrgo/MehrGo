package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class BalanceHistory(
    val items: List<BalanceHistoryItem>? = null,
    val total: Int? = null,
    @SerializedName("total_income") val totalIncome: Long? = null,
    @SerializedName("total_expense") val totalExpense: Long? = null,
    val page: Int? = null,
    @SerializedName("per_page") val perPage: Int? = null
)
