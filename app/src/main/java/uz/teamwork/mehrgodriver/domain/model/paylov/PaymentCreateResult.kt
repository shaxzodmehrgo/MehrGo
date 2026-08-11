package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class PaymentCreateResult(
    @SerializedName("transactionId") val transactionId: String? = null
)
