package uz.teamwork.mehrgodriver.domain.model.paylov

import com.google.gson.annotations.SerializedName

data class CardCreateResult(
    val cid: String? = null,
    @SerializedName("otpSentPhone") val otpSentPhone: String? = null
)
