package uz.teamwork.mehrgodriver.domain.model.fare

import com.google.gson.annotations.SerializedName

data class FareGpsBatchRequest(
    @SerializedName("points") val points: List<FareGpsPoint>,
    /** Cumulative pickup wait, ms (§7.1) — only attached when changed since last send. */
    @SerializedName("waiting_time") val waitingTime: Long? = null,
    /** Cumulative on-way wait, ms (§7.1) — only attached when changed since last send. */
    @SerializedName("waiting_time_ontheway") val waitingTimeOntheway: Long? = null
)
