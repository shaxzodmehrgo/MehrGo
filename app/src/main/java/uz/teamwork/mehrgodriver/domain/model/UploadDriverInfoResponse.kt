package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class UploadDriverInfoResponse(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("auth_key")
    val authKey: String? = null
)