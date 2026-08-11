package uz.teamwork.mehrgodriver.domain.model.fare

import com.google.gson.annotations.SerializedName

/**
 * `data.live` block (ORDER_COMPLETE_WAITING.md §7.2/§7.4) — the SINGLE source of truth
 * for the trip screen: distance, both waits, waiting cost and the current total. Returned
 * in every `order-gps/batch` ack, `GET order-gps/fare` and the socket price push.
 *
 * The agreed-price extras (`agreed_price`/`surcharge`/`kept_projection`/`waiting_billing`)
 * are only present on orders WITH a B point; `null` otherwise.
 */
/**
 * ALL money fields are Double: the backend sends unrounded fractions for some of them
 * (live capture: `"waiting_cost": 2613.33`) — a Long field made Gson throw
 * NumberFormatException and KILLED the app on the getFare path. Consumers round.
 */
data class FareLive(
    @SerializedName("distance_km") val distanceKm: Double?,
    @SerializedName("to_client_km") val toClientKm: Double?,
    @SerializedName("waiting_sec") val waitingSec: Long?,
    @SerializedName("on_way_sec") val onWaySec: Long?,
    @SerializedName("waiting_cost") val waitingCost: Double?,
    @SerializedName("services_price") val servicesPrice: Double?,
    @SerializedName("podacha") val podacha: Double?,
    @SerializedName("extra_price") val extraPrice: Double?,
    @SerializedName("fare") val fare: Double?,
    /** Current TOTAL = round(fare + services + podacha + extra + waiting_cost). */
    @SerializedName("price") val price: Double?,
    @SerializedName("agreed_price") val agreedPrice: Double?,
    /** Waiting surcharge on top of the agreed price (rounded estimate). */
    @SerializedName("surcharge") val surcharge: Double?,
    /** Projected total if finished at B: agreed + waiting. */
    @SerializedName("kept_projection") val keptProjection: Double?,
    /** true = the free waiting window is over, waiting is billing right now. */
    @SerializedName("waiting_billing") val waitingBilling: Boolean?
)
