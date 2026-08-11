package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class TermsOfUse(
    @SerializedName("text")
    val text: String? = null
)
