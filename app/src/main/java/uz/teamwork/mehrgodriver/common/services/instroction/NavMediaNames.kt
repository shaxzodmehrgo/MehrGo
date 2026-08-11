package uz.teamwork.mehrgodriver.common.services.instroction

import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_RUSSIAN
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_UZBEK

object NavMediaNames {
    const val NAV_DESTINATION = "arrive"

    private const val NAV_U_TURN = "uturn"
    const val NAV_STRAIGHT = "straight"

    private const val NAV_LEFT = "left"
    private const val NAV_SLIGHT_LEFT = "slight left"
    private const val NAV_SHARP_LEFT = "sharp left"

    private const val NAV_SLIGHT_RIGHT = "slight right"
    private const val NAV_RIGHT = "right"
    private const val NAV_SHARP_RIGHT = "sharp right"

    fun getNavIcon(modifier: String?): Int {
        return when (modifier) {
            NAV_U_TURN -> {
                R.drawable.ic_u_left
            }

            NAV_STRAIGHT -> {
                R.drawable.ic_arrow_up
            }

            NAV_SLIGHT_LEFT -> {
                R.drawable.ic_slight_left
            }

            NAV_LEFT -> {
                R.drawable.ic_left
            }

            NAV_SHARP_LEFT -> {
                R.drawable.ic_turn_left
            }

            NAV_RIGHT -> {
                R.drawable.ic_right
            }

            NAV_SHARP_RIGHT -> {
                R.drawable.ic_turn_right
            }

            NAV_SLIGHT_RIGHT -> {
                R.drawable.ic_slight_right
            }

            else -> {
                R.drawable.ic_box
            }
        }
    }

    fun getNavType(modifier: String?, lang: String): String {
        val parent = when (lang) {
            LANGUAGE_RUSSIAN -> {
                "voiceru/navigation"
            }

            LANGUAGE_UZBEK -> {
                "voice/navigation"
            }

            else -> {
                "voice/navigation"
            }
        }
        return when (modifier) {
            NAV_STRAIGHT -> {
                "$parent/Forward.opus"
            }

            NAV_U_TURN -> {
                "$parent/TurnBack.opus"
            }

            NAV_SLIGHT_LEFT -> {
                "$parent/TurnLeft.opus"
            }

            NAV_LEFT -> {
                "$parent/TurnLeft.opus"
            }

            NAV_SHARP_LEFT -> {
                "$parent/HardTurnLeft.opus"
            }

            NAV_SLIGHT_RIGHT -> {
                "$parent/TurnRight.opus"
            }

            NAV_RIGHT -> {
                "$parent/TurnRight.opus"
            }

            NAV_SHARP_RIGHT -> {
                "$parent/HardTurnRight.opus"
            }

            else -> {
                ""
            }
        }
    }

    fun getDistanceName(distance: Double, lang: String): ArrayList<String> {
        return if (distance < 1000) {
            getDistanceNameM(distance, lang)
        } else {
            getDistanceNameKM(distance, lang)
        }
    }

    private fun getDistanceNameM(distance: Double, lang: String): ArrayList<String> {
        val names = ArrayList<String>()
        val nameD = when (lang) {
            LANGUAGE_RUSSIAN -> {
                "voiceru/navigation"
            }

            LANGUAGE_UZBEK -> {
                "voice/navigation"
            }

            else -> {
                "voice/navigation"
            }
        }

        when (distance.toInt() / 10) {
            1 -> names.add("$nameD/10.opus")
            2 -> names.add("$nameD/20.opus")
            3 -> names.add("$nameD/30.opus")
            4 -> names.add("$nameD/40.opus")
            5 -> names.add("$nameD/50.opus")
            6 -> names.add("$nameD/60.opus")
            7 -> names.add("$nameD/70.opus")
            8 -> names.add("$nameD/80.opus")
            9 -> names.add("$nameD/90.opus")
            10 -> names.add("$nameD/100.opus")
            in 10..19 -> {
                names.add("$nameD/100.opus")
            }

            in 20..29 -> {
                names.add("$nameD/200.opus")
            }

            in 30..39 -> {
                names.add("$nameD/300.opus")
            }

            in 40..49 -> {
                names.add("$nameD/400.opus")
            }

            in 50..59 -> {
                names.add("$nameD/500.opus")
            }

            in 60..69 -> {
                names.add("$nameD/600.opus")
            }

            in 70..79 -> {
                names.add("$nameD/700.opus")
            }

            in 80..89 -> {
                names.add("$nameD/800.opus")
            }

            in 90..99 -> {
                names.add("$nameD/900.opus")
            }
        }

        if (names.size != 0)
            names.add("$nameD/Meter.opus")

        return names
    }

    private fun getDistanceNameKM(distance: Double, lang: String): ArrayList<String> {
        val names = ArrayList<String>()
        val nameD = when (lang) {
            LANGUAGE_RUSSIAN -> {
                "voiceru/navigation"
            }

            LANGUAGE_UZBEK -> {
                "voice/navigation"
            }

            else -> {
                "voice/navigation"
            }
        }
        names.add("$nameD/${distance.toInt() / 1000}.opus")
        names.add("$nameD/Kilometer.opus")

        names.addAll(getDistanceNameM(distance % 1000, lang))

        return names
    }

    fun getDistanceAsString(distance: Int, lang: String): String {
        return if (distance < 1000) {
            translateKmAndMeter(lang) { _, m ->
                "${distance % 1000} $m"
            }
        } else {
            translateKmAndMeter(lang) { km, m ->
                "${distance / 1000} $km ${distance % 1000} $m"
            }
        }
    }

    private inline fun translateKmAndMeter(
        language: String,
        block: (km: String, m: String) -> String
    ): String {
        val km = when (language) {
            "ru" -> "км"
            "uk" -> "км"
            "zen" -> "км"
            else -> "km"
        }
        val m = when (language) {
            "ru" -> "м"
            "uk" -> "м"
            "zen" -> "м"
            else -> "m"
        }
        return block(km, m)
    }
}