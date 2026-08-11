package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class WithdrawalInfo(
    val balance: Long? = null,
    @SerializedName("withdrawable_balance") val withdrawableBalance: Long? = null,
    @SerializedName("bonus_left") val bonusLeft: Long? = null,
    // Admin-set minimum that must REMAIN on the balance after a withdrawal
    // (withdrawable = balance − bonus_left − min_balance_left).
    @SerializedName("min_balance_left") val minBalanceLeft: Long? = null,
    @SerializedName("min_amount") val minAmount: Long? = null,
    @SerializedName("max_amount") val maxAmount: Long? = null,
    @SerializedName("daily_limit") val dailyLimit: Long? = null,
    @SerializedName("today_withdrawn") val todayWithdrawn: Long? = null,
    @SerializedName("pending_request") val pendingRequest: WithdrawalRequest? = null
)
