package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class PasswordRecovery(
    @SerializedName("auth_key")
    val authKeyVerify: String
)