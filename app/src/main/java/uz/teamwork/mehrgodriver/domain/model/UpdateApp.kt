package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class UpdateApp(
    val version: String,

    @SerializedName("version_code")
    val versionCode: Int,

    val required: Boolean,

    @SerializedName("blocked_aps")
    val blockedApps: String? = null,

    @SerializedName("device_token")
    val deviceToken: String? = null
)