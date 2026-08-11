package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class Region(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("name")
    val name: String? = null
)