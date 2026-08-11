package uz.teamwork.mehrgodriver.common

/**
 * Display formatters — Kotlin extensions mirroring the iOS
 * `Double+Currency` / `Int64.asHHMMSS` helpers so order cards, the offer
 * sheet, the trip panel and the receipt all format money / distance / time
 * identically. Thin wrappers over [Helper] where equivalents already exist.
 */

/** "120000" -> "120 000" (space-grouped, no fraction). */
fun Int.asSumString(): String = Helper.formatPrice(this.toString())

fun Long.asSumString(): String = Helper.formatPrice(this.toString())

/** Metres -> "1.2" km string (one decimal), matching the broadcast list. */
fun Double.asKmString(): String {
    val km = this / 1000.0
    return String.format("%.1f", km)
}

/**
 * Driver→pickup style distance: "<350 m" under 1 km, "1.2 km" otherwise.
 */
fun Double.asPickupDistanceString(): String =
    if (this >= 1000.0) "${asKmString()} km" else "${Math.round(this)} m"

/** Milliseconds -> "HH:MM:SS" (zero-padded). Delegates to [Helper.formatTime]. */
fun Long.asHHMMSS(): String = Helper.formatTime(this)

/** Whole minutes for an ETA at ~30 km/h city average (500 m/min). */
fun Double.asEtaMinutes(): Int = Math.round(this / 500.0).toInt()
