package uz.teamwork.mehrgodriver.domain.model.fare

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Server-side fare snapshot. Returned by both
 * `POST /v1/order-gps/batch` (ack) and `GET /v1/order-gps/fare`,
 * and pushed as `order_price_updated` over the socket.
 *
 * `price` is the rounded amount to **display** (so'm). `fare` is the
 * raw amount before rounding (for debug / dispute). `breakdown` /
 * `priceBreakdown` are opaque JSON — kept as `JsonObject` so we don't
 * couple the client to the server's evolving breakdown shape.
 */
data class FareResponse(
    @SerializedName("order_id") val orderId: Long?,
    @SerializedName("distance_km") val distanceKm: Double?,
    @SerializedName("in_city_km") val inCityKm: Double?,
    @SerializedName("out_city_km") val outCityKm: Double?,
    @SerializedName("waiting_sec") val waitingSec: Long?,
    @SerializedName("traffic_sec") val trafficSec: Long?,
    @SerializedName("price") val price: Long?,
    @SerializedName("fare") val fare: Double?,
    @SerializedName("breakdown") val breakdown: JsonObject?,
    @SerializedName("price_breakdown") val priceBreakdown: JsonObject?,
    /** Live screen block (§7.2) — preferred over the top-level fields when present. */
    @SerializedName("live") val live: FareLive?,
    /** One-shot billing events (§7.4): waiting_charge_started / waiting_charge_step. */
    @SerializedName("events") val events: List<String>?
)
