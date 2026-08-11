package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class OrderFinished(
    val id: Int,
    val driver_number: String,
    val car: Car
) {
    data class Car(
        @SerializedName("car_number")
        val carNumber: String,

        @SerializedName("car_model")
        val carModel: CarModel,

        @SerializedName("car_color")
        val carColor: CarColor
    ) {
        data class CarModel(
            val id: Int,
            val name: String,

            @SerializedName("second_name")
            val secondName: String
        )

        data class CarColor(
            val id: Int,
            val name: String,

            @SerializedName("second_name")
            val secondName: String
        )
    }
}