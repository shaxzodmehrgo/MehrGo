package uz.teamwork.mehrgodriver.domain.model.fare

import com.google.gson.annotations.SerializedName

/**
 * Single GPS point sent to the server in a batch.
 *
 * `speed` is **m/s** (server multiplies by 3.6 itself). `ts` is **ms epoch**.
 * `cpid` is a monotonically-increasing per-order client point id used by the
 * server as the idempotency key (`ON CONFLICT DO NOTHING`). The same `cpid`
 * may be re-sent safely after a retry/reconnect — distance/fare will not grow.
 *
 * `state` is the ORDER state (Constants.ORDER_STATE_*: 2 accepted / 7 started /
 * 8 arrived / 9 gone) at the moment this fix was CAPTURED — not at upload time.
 * A batch can be flushed after a state transition (retry backlog, reject latch),
 * so the server must bucket each point by ITS state: only state 9 points are the
 * billable trip; earlier states are the approach leg (backend directive
 * 2026-07-30, after order 79557 billed the driver→pickup leg at the trip rate).
 * Null only for points captured by a pre-update app version.
 */
data class FareGpsPoint(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("accuracy") val accuracy: Float?,
    @SerializedName("speed") val speed: Float?,
    @SerializedName("bearing") val bearing: Float?,
    @SerializedName("altitude") val altitude: Double?,
    @SerializedName("provider") val provider: String?,
    @SerializedName("ts") val ts: Long,
    @SerializedName("cpid") val cpid: Long,
    @SerializedName("state") val state: Int?
)
