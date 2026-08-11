package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

// status: 0 pending, 1 approved, 2 rejected, 3 driver-cancelled.
data class WithdrawalRequest(
    val id: Int? = null,
    @SerializedName("card_id") val cardId: String? = null,
    @SerializedName("card_number") val cardNumber: String? = null,
    val amount: Long? = null,
    @SerializedName("approved_amount") val approvedAmount: Long? = null,
    val status: Int? = null,
    @SerializedName("status_name") val statusName: String? = null,
    val note: String? = null,
    @SerializedName("admin_note") val adminNote: String? = null,
    @SerializedName("paylov_transaction_id") val paylovTransactionId: String? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("processed_at") val processedAt: Long? = null
)
