package uz.teamwork.mehrgodriver.domain.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DirectionLocations(
    val routes: List<Route>
) : Parcelable {

    @Parcelize
    data class Route(
        val geometry: String,
        val legs: List<Leg>,
        val distance: Double
    ) : Parcelable {

        @Parcelize
        data class Leg(
            val steps: List<Step>,
            val summary: String,
            val weight: Double,
            val duration: Double,
            val distance: Double
        ) : Parcelable {

            @Parcelize
            data class Step(
                val geometry: String,
                val maneuver: Maneuver?,
                val name: String,
                val weight: Double,
                val duration: Double,
                val distance: Double
            ) : Parcelable {

                @Parcelize
                data class Maneuver(
                    @SerializedName("bearing_after")
                    val bearingAfter: Int,

                    @SerializedName("bearing_before")
                    val bearingBefore: Int,

                    // There is location here
                    val modifier: String?,
                    val type: String,
                    val location: List<Double>
                ) : Parcelable
            }
        }
    }
}