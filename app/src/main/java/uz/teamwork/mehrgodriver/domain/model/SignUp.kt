package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class SignUp(
    @SerializedName("auth_key")
    val authKey: String,

    @SerializedName("waiting_time")
    val waitingTime: Int
)