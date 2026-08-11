package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class CarColor(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String
)