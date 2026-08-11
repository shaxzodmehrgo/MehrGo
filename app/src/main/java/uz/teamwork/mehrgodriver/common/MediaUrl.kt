package uz.teamwork.mehrgodriver.common

import uz.teamwork.mehrgodriver.common.Constants.IMAGE_URL

/**
 * Absolute URL for a media path the backend hands us.
 *
 * The backend is not consistent about the shape, and neither is [IMAGE_URL] across brands, which
 * has already cost us one shipped bug: `IMAGE_URL + icon.drop(1)` on a tariff icon built
 * "https://prod.mehrgo.uzuploads/tarif/icons/x.png" — an unresolvable host — so every tariff had
 * been quietly showing its placeholder. The three shapes actually seen in prod responses:
 *
 *   "@uploads/tarif/icons/x.png"      uploads, '@'-prefixed, NO leading slash
 *   "/admin/images/defaultAvatar.png" admin assets, leading slash, no '@'
 *   "https://…"                        already absolute
 *
 * and [IMAGE_URL] itself is "https://prod.mehrgo.uz" here but "https://prod.mehrgo.uz/" in the
 * client app. Joining those by hand is four ways to get it wrong, so nothing should concatenate
 * [IMAGE_URL] directly — call this instead and the constant's shape stops mattering.
 */
object MediaUrl {

    fun of(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
        return "${IMAGE_URL.trimEnd('/')}/${raw.removePrefix("@").removePrefix("/")}"
    }

    /** SVG needs AndroidSVG — Glide has no decoder for it and would fail silently. */
    fun isSvg(url: String?): Boolean =
        url?.substringBefore('?')?.endsWith(".svg", ignoreCase = true) == true
}
