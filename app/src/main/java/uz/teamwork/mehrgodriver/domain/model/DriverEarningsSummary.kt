package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class DriverEarningsSummary(
    val period: Period,
    val totals: Totals,
    val graph: Graph,
    val meta: Meta? = null
) {
    data class Period(
        val type: String,
        val from: String,
        val to: String,
        val tz: String
    )

    data class Totals(
        @SerializedName("orders_count") val ordersCount: Int,
        @SerializedName("gross_amount") val grossAmount: Long,
        val commission: Long,
        val bonus: Long,
        val earned: Long,
        @SerializedName("distance_km") val distanceKm: Double,
        val currency: String
    )

    data class Graph(
        val granularity: String,
        val points: List<Point>
    ) {
        data class Point(
            val date: String,
            @SerializedName("orders_count") val ordersCount: Int,
            val earned: Long
        )
    }

    data class Meta(
        @SerializedName("generated_at") val generatedAt: String? = null,
        @SerializedName("cache_ttl_seconds") val cacheTtlSeconds: Int? = null
    )
}
