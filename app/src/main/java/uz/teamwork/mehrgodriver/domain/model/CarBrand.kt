package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class CarBrand(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("parent_id")
    val parentId: Int?
)