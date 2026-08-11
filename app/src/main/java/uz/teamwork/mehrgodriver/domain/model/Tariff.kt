package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class Tariff(
    val id: Int,
    val name: String,

    @SerializedName("starting_price")
    val startingPrice: Int,
    val icon: String,
    val isSelected: Boolean = false
)