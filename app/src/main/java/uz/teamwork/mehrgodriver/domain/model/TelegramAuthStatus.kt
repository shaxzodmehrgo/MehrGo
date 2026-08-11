package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Response of GET /telegram-auth/status?phone=. Drives the auth screens:
 *  - [enabled] — Telegram OTP turned on at all (global setting). When false the
 *    "Telegram orqali" button is hidden and only SMS is offered.
 *  - [linked]  — this phone is linked to the bot. When true a telegram code can
 *    be requested straight away; when false the driver opens [botUrl] to link
 *    first (backend still SMS-falls-back either way).
 *  - [botUrl]  — deep link that opens the bot for linking.
 *
 * Not deployed on every backend yet — callers must tolerate a 404/failure and
 * fall back to SMS (channel=telegram is SMS-safe anyway).
 */
class TelegramAuthStatus(
    @SerializedName("enabled")
    val enabled: Boolean = false,

    @SerializedName("linked")
    val linked: Boolean = false,

    @SerializedName("bot_url")
    val botUrl: String? = null
)
