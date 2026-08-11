package uz.teamwork.mehrgodriver.common

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_KAZAKH
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_KYRGYZ
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_RUSSIAN
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_UZBEK
import uz.teamwork.mehrgodriver.common.Helper.roundPrice
import uz.teamwork.mehrgodriver.common.location_utils.PolygonUtils
import uz.teamwork.mehrgodriver.common.model.Distances
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.common.services.Polyline
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.domain.model.Order
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object Helper {

    /**
     * The number the "call the operator" buttons should dial.
     *
     * The driver's own branch dispatcher is ALWAYS preferred — that is the person who actually
     * handles their orders. [Constants.SUPPORT_PHONE_NUMBER] is only a fallback for when no
     * dispatcher is attached, which is the normal state on the under-review screen (a driver
     * has no branch until their documents are approved) and can also happen later if the
     * branch record is incomplete. Returns null when neither exists, so callers keep showing
     * `not_assigned_dispatcher` instead of dialling nothing.
     *
     * Shared because four screens ask this question — NotActiveUser, Settings, MapSettings and
     * the map's dispatcher button — and they had drifted into four copies of the same `if`.
     */
    fun dispatcherOrSupportNumber(): String? =
        UserManager.getUser()?.branch?.dispatcherNumber?.takeIf { it.isNotBlank() }
            ?: Constants.SUPPORT_PHONE_NUMBER.takeIf { it.isNotBlank() }

    // Format
    // Uzbek
    fun formatPhoneNumber(phoneNumber: String): String {
        var formattedPhoneNumber = ""
        for (index in phoneNumber.indices) {
            if (index == 3 || index == 5 || index == 8 || index == 10) {
                formattedPhoneNumber += "${phoneNumber[index]} "
            } else {
                formattedPhoneNumber += phoneNumber[index]
            }
        }

        return formattedPhoneNumber
    }

    // Kazakh
//    fun formatPhoneNumber(phoneNumber: String): String {
//        var formattedPhoneNumber = ""
//        for (index in phoneNumber.indices) {
//            if (index == 1 || index == 4 || index == 7 || index == 9) {
//                formattedPhoneNumber += "${phoneNumber[index]} "
//            } else {
//                formattedPhoneNumber += phoneNumber[index]
//            }
//        }
//
//        return formattedPhoneNumber
//    }

    // Kyrgyz
//    fun formatPhoneNumber(phoneNumber: String): String {
//        var formattedPhoneNumber = ""
//        for (index in phoneNumber.indices) {
//            if (index == 3 || index == 6) {
//                formattedPhoneNumber += "${phoneNumber[index]} "
//            } else {
//                formattedPhoneNumber += phoneNumber[index]
//            }
//        }
//
//        return formattedPhoneNumber
//    }

    // Group only the DIGITS. The old index%3==0 loop also inserted a separator
    // at index 0 (so every amount carried a leading space), let a minus sign
    // occupy a grouping slot ("- 100"), and grouped straight through a decimal
    // point when the backend sent a money column as "33000.00" — which comes
    // out as nonsense rather than a number. Same implementation the client
    // already uses, so both apps print one trip identically.
    fun formatPrice(price: String): String {
        val trimmed = price.trim()
        val negative = trimmed.startsWith("-")
        // "33000.00" -> "33000". Truncating is right for so'm: there are no
        // subunits in circulation, and the server's own rounding owns the rest.
        val digits = trimmed.removePrefix("-").substringBefore('.').ifEmpty { "0" }

        val grouped = StringBuilder()
        val reversed = digits.reversed()
        for (index in reversed.indices) {
            if (index > 0 && index % 3 == 0) {
                grouped.append(' ')
            }
            grouped.append(reversed[index])
        }

        val formatted = grouped.reverse().toString()
        return if (negative) "-$formatted" else formatted
    }

    fun formatTime(ms: Long, includeMillis: Boolean = false): String {
        var milliseconds = ms

        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        if (!includeMillis) {
            return "${if (hours < 10) "0" else ""}$hours:" +
                    "${if (minutes < 10) "0" else ""}$minutes:" +
                    "${if (seconds < 10) "0" else ""}$seconds"
        }

        milliseconds -= TimeUnit.SECONDS.toMillis(seconds)
        milliseconds /= 10
        return "${if (hours < 10) "0" else ""}$hours:" +
                "${if (minutes < 10) "0" else ""}$minutes:" +
                "${if (seconds < 10) "0" else ""}$seconds:" +
                "${if (milliseconds < 10) "0" else ""}$milliseconds"
    }

    // Round
    fun metreToRoundKm(metre: String): String {
        val km = metre.toFloat() / 1000f
        val roundKm = (km * 10.0).roundToInt() / 10.0
        return roundKm.toString()
    }

    fun roundPrice(price: Long): String {
        val roundPrice = (price / 100.0).roundToInt() * 100
        return roundPrice.toString()

//        return price.toString()
    }

    fun roundPriceTotalPriceForPremiumTaxi(price: Long): String {
        val roundPrice = (price / 50.0).roundToInt() * 50
        return roundPrice.toString()
    }

    /**
     * Settlement rounding — nearest 1 000 so'm, half UP (19 130 -> 19 000, 19 500 -> 20 000).
     *
     * This is the granularity a driver can actually hand back in cash, so it applies to money
     * COLLECTED IN CASH only: a card charge must stay exact or the app misstates what the card is
     * billed. Distinct from [roundPrice] (nearest 100), which is the older display rounding on the
     * offer / live-fare surfaces and is deliberately left alone.
     */
    fun roundToThousand(price: Long): Long =
        ((price.coerceAtLeast(0L) + 500L) / 1000L) * 1000L

    // Add for UI
    fun addNolIsNeeded(value: Int): String {
        return if ((value) < 10) {
            "0${value}"
        } else {
            "$value"
        }
    }

    // Calculate distance between points
    fun calculateBetweenTwoPoints(latLng1: LatLng, latLng2: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            latLng1.latitude,
            latLng1.longitude,
            latLng2.latitude,
            latLng2.longitude,
            result
        )

        return result[0]
    }

    fun calculateBetweenAllPoints(locations: List<Order.Location>): Float {
        var result = 0f

        for (index in 0..locations.size - 2) {
            val current = locations[index]
            val next = locations[index + 1]

            result += calculateBetweenTwoPoints(
                LatLng(current.latitude, current.longitude),
                LatLng(next.latitude, next.longitude)
            )
        }

        return result
    }

    // New algorithm for calculate distance
    fun filterTrackLocations(locations: Polyline): MutableList<MyLocation> {
        val filteredLocations = mutableListOf<MyLocation>()

        var i = 1
        for (index in 0 until locations.size) {
            val location = locations[index]

            if (location.accuracy < 10) { // First value for filter
                filteredLocations.add(location)
                i = 0
            }

            if (i >= 5) {

                var exist = false
                var min = locations[index - i + 1]
                for (k in index - i + 1..index) {
                    if (locations[k].accuracy < min.accuracy) {
                        min = locations[k]

                        if (min.accuracy < 15) { // Second value for filter
                            exist = true
                            filteredLocations.add(min)
                        }
                    }
                }

                if (exist) {
                    i = 0
                } else if (min.accuracy < 200) { // Last value for filter
                    filteredLocations.add(min)
                    i = 0
                }
            }

            i++
        }

        return filteredLocations
    }

    fun calculateTrackLocations(
        locations: Polyline,
        cityRadius: Long?,
        latCityCenter: Double?,
        lonCityCenter: Double?
    ): Distances {
        var distanceInCity = 0f
        var distanceOutCity = 0f

        for (index in 1 until locations.size) {
            val current = locations[index]
            val previous = locations[index - 1]

            if (cityRadius != null && latCityCenter != null && lonCityCenter != null) {
                val disCurrentAndCityCenter = calculateBetweenTwoPoints(
                    LatLng(current.latitude, current.longitude),
                    LatLng(latCityCenter, lonCityCenter)
                ).toLong()

                if (disCurrentAndCityCenter < cityRadius.toLong()) {
                    // Driver in City
                    distanceInCity += calculateBetweenTwoPoints(
                        LatLng(
                            current.latitude,
                            current.longitude
                        ), LatLng(previous.latitude, previous.longitude)
                    )
                } else {
                    // Driver out City
                    distanceOutCity += calculateBetweenTwoPoints(
                        LatLng(
                            current.latitude,
                            current.longitude
                        ), LatLng(previous.latitude, previous.longitude)
                    )
                }
            } else {
                // Driver in City or out City but to calculate as in City
                distanceInCity += calculateBetweenTwoPoints(
                    LatLng(
                        current.latitude,
                        current.longitude
                    ), LatLng(previous.latitude, previous.longitude)
                )
            }
        }

        return Distances(distanceInCity, distanceOutCity)
    }

    fun calculateTrackLocations(locations: Polyline, polygonCity: List<LatLng>?): Distances {
        var distanceInCity = 0f
        var distanceOutCity = 0f

        for (index in 1 until locations.size) {
            val current = locations[index]
            val previous = locations[index - 1]

            if (polygonCity != null) {
                val isInside = PolygonUtils.isPointInPolygon(
                    polygonCity,
                    LatLng(current.latitude, current.longitude)
                )

                if (isInside) {
                    // Driver in City
                    distanceInCity += calculateBetweenTwoPoints(
                        LatLng(
                            current.latitude,
                            current.longitude
                        ), LatLng(previous.latitude, previous.longitude)
                    )
                } else {
                    // Driver out City
                    distanceOutCity += calculateBetweenTwoPoints(
                        LatLng(
                            current.latitude,
                            current.longitude
                        ), LatLng(previous.latitude, previous.longitude)
                    )
                }
            } else {
                // Driver in City or out City but to calculate as in City
                distanceInCity += calculateBetweenTwoPoints(
                    LatLng(
                        current.latitude,
                        current.longitude
                    ), LatLng(previous.latitude, previous.longitude)
                )
            }
        }

        return Distances(distanceInCity, distanceOutCity)
    }

    // Route decode from code to points
    fun decode(encodedPath: String, precision: Int): ArrayList<LatLng> {
        val len = encodedPath.length

        // OSRM uses precision=6, the default Polyline spec divides by 1E5, capping at precision=5
        val factor = Math.pow(10.0, precision.toDouble())

        // For speed we preallocate to an upper bound on the final length, then
        // truncate the array before returning.
        val path: ArrayList<LatLng> = ArrayList()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < len) {
            var result = 1
            var shift = 0
            var temp: Int
            do {
                temp = encodedPath[index++].toInt() - 63 - 1
                result += temp shl shift
                shift += 5
            } while (temp >= 0x1f)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 1
            shift = 0
            do {
                temp = encodedPath[index++].toInt() - 63 - 1
                result += temp shl shift
                shift += 5
            } while (temp >= 0x1f)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            path.add(LatLng(lat / factor, lng / factor))
        }
        return path
    }

    // Translate text
    fun getUnexpectedError(): String {
        return when (LanguageManager.getLanguage()) {
            LANGUAGE_UZBEK -> {
                "Aniqlanmagan xatolik"
            }

            LANGUAGE_KAZAKH -> {
                "Белгісіз қате"
            }

            LANGUAGE_KYRGYZ -> {
                "Белгисиз ката"
            }

            LANGUAGE_RUSSIAN -> {
                "Невыявленная ошибка"
            }

            else -> {
                "Aniqlanmagan xatolik"
            }
        }
    }

    fun getConnectionError(): String {
        return when (LanguageManager.getLanguage()) {
            LANGUAGE_UZBEK -> {
                "Internet bilan bog'lik xatolik. Iltimos aloqangizni tekshiring"
            }

            LANGUAGE_KAZAKH -> {
                "Интернетке байланысты қате. Байланысыңызды тексеріңіз."
            }

            LANGUAGE_KYRGYZ -> {
                "Интернетке байланысты қате. Байланысыңызды тексеріңіз."
            }

            LANGUAGE_RUSSIAN -> {
                "Ошибка связи с Интернетом. Пожалуйста, проверьте ваше соединение"
            }

            else -> {
                "Internet bilan bog'lik xatolik. Iltimos aloqangizni tekshiring"
            }
        }
    }

    fun getServerError(): String {
        return when (LanguageManager.getLanguage()) {
            LANGUAGE_UZBEK -> {
                "Server bilan bog'lik xatolik"
            }

            LANGUAGE_KAZAKH -> {
                "Серверге байланысты қате"
            }

            LANGUAGE_KYRGYZ -> {
                "Серверге байланыштуу ката"
            }

            LANGUAGE_RUSSIAN -> {
                "Ошибка связи с сервером"
            }

            else -> {
                "Server bilan bog'lik xatolik"
            }
        }
    }

    /**
     * Server error bodies sometimes put a raw machine key (e.g. "pinfl_not_match") in `message`
     * instead of a human sentence, and the UI was showing that key verbatim in a toast. Map the
     * keys we know to a localized, user-friendly message; for any other bare snake_case key fall
     * back to the generic server error; pass genuine human-readable messages through unchanged.
     */
    fun humanizeServerError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        if (msg.isEmpty()) return getServerError()

        when (msg.lowercase()) {
            // The added Paylov card must belong to the driver: the card owner's PINFL (personal ID)
            // did not match the account holder's, so the card is registered to someone else.
            "pinfl_not_match" -> return when (LanguageManager.getLanguage()) {
                LANGUAGE_UZBEK ->
                    "Bu karta boshqa shaxsga tegishli. Faqat o'zingizning kartangizni qo'sha olasiz."

                LANGUAGE_KAZAKH ->
                    "Бұл карта басқа адамға тиесілі. Тек өзіңіздің картаңызды қоса аласыз."

                LANGUAGE_KYRGYZ ->
                    "Бул карта башка адамга таандык. Сиз өзүңүздүн картаңызды гана кошо аласыз."

                LANGUAGE_RUSSIAN ->
                    "Эта карта принадлежит другому лицу. Вы можете добавить только свою карту."

                else ->
                    "Bu karta boshqa shaxsga tegishli. Faqat o'zingizning kartangizni qo'sha olasiz."
            }

            // The card is blocked (by the bank / Paylov) — a foreign key with no meaning to a driver.
            "card_is_blocked" -> return when (LanguageManager.getLanguage()) {
                LANGUAGE_UZBEK ->
                    "Bu karta bloklangan. Iltimos, boshqa karta kiriting yoki bankingizga murojaat qiling."

                LANGUAGE_KAZAKH ->
                    "Бұл карта бұғатталған. Басқа карта енгізіңіз немесе банкіңізге хабарласыңыз."

                LANGUAGE_KYRGYZ ->
                    "Бул карта бөгөттөлгөн. Башка карта киргизиңиз же банкыңызга кайрылыңыз."

                LANGUAGE_RUSSIAN ->
                    "Эта карта заблокирована. Введите другую карту или обратитесь в банк."

                else ->
                    "Bu karta bloklangan. Iltimos, boshqa karta kiriting yoki bankingizga murojaat qiling."
            }
        }

        // An unknown value that looks like a machine key (snake_case, no spaces) is never fit to
        // show — degrade to the generic server error instead of leaking the key.
        val looksLikeKey =
            msg.contains('_') && msg.none { it.isWhitespace() } && msg == msg.lowercase()
        return if (looksLikeKey) getServerError() else msg
    }

    // Convert from string to List the City Polygon
    fun convertPolygon(polygonString: String): ArrayList<LatLng> {
        val coordinatesString = polygonString.removePrefix("POLYGON((").removeSuffix("))")
        val coordinatePairs = coordinatesString.split(",")
        val coordinates = coordinatePairs.map { pair ->
            pair.split(" ").map { it.toDouble() }
        }

        val polygonCity = ArrayList<LatLng>()
        coordinates.forEach { (lon, lat) ->
            polygonCity.add(LatLng(lat, lon))
        }

        return polygonCity
    }

    // Calculate price with distance intervals
    fun calculatePriceWithDistanceIntervals(
        distanceMeters: Long,
        intervals: List<Order.Tariff.DistanceInterval>,
        priceInCity: Int
    ): Float {
        var totalPrice = 0
        var remainingDistance = distanceMeters

        // Tartiblab olamiz: startDistance bo‘yicha
        val sortedIntervals = intervals.sortedBy { it.start }

        for (interval in sortedIntervals) {
            if (remainingDistance <= 0) break

            val start = interval.start
            val end = interval.end

            // Bu intervalda hisoblanadigan masofa (masofa interval ichida qolgan qismi)
            val applicableStart = maxOf(start, distanceMeters - remainingDistance)
            val applicableEnd = minOf(end, distanceMeters)

            if (applicableEnd > applicableStart) {
                val intervalDistance = applicableEnd - applicableStart
                val intervalDistanceKm = intervalDistance / 1000.0
                totalPrice += (intervalDistanceKm * interval.price).toInt()

                remainingDistance -= intervalDistance
            }
        }

        // Qolgan masofa bo‘yicha asosiy narx (priceInCity) ni hisoblash
        if (remainingDistance > 0) {
            val remainingDistanceKm = remainingDistance / 1000.0
            totalPrice += (remainingDistanceKm * priceInCity).toInt()
        }

        return totalPrice.toFloat()
    }
}