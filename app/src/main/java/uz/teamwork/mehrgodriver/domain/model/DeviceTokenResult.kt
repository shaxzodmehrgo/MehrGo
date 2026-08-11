package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class DeviceTokenResult(
    @SerializedName("token_registered")
    val tokenRegistered: Boolean? = null
)
