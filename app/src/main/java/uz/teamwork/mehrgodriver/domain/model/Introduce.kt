package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class Introduce(
    @SerializedName("id")
    val id: Int,

    @SerializedName("header")
    val header: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("url")
    val imageUrl: String
)
