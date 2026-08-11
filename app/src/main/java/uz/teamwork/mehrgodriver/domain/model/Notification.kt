package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class Notification(
    val id: Int,
    val message: String,
    val text: String,
    val branches: List<String>,

    @SerializedName("photo_url")
    val photoUrl: String,

    @SerializedName("created_at")
    val createdAt: CreatedDate
) {
    data class CreatedDate(
        @SerializedName("datetime")
        val dateTime: String
    )
}