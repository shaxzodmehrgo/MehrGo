package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class OrderCancelReason(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("name")
    val name: String? = null,

    var isChecked: Boolean = false
)