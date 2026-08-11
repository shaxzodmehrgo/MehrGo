package uz.teamwork.mehrgodriver.common

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.ServiceIcons.iconFor

/**
 * Service name → icon, ported 1:1 from the iOS twin's `glyph(for:)`
 * (MehrgoDriver/View/Main/Home/TripFinishView.swift:677-706, duplicated in TripServicesSheet).
 *
 * Matched on the NAME, not the id, and that is deliberate — the same decision iOS documents:
 * "Localized names from the backend can be in uz / ru — case-folding + substring check on both
 * keeps the mapping forgiving."
 *
 * Why not the id, which would look sturdier: there is no global service catalogue. Services are
 * fetched per order (`GET service/order?id=<orderId>`) out of a per-BRANCH table, and the ~15
 * white-label brands each run their own backend, so an id minted on one host means nothing on
 * another. `Order.Service.id` is in all likelihood the order_item row id anyway — nothing in
 * either client has ever read it, and the only two captures of "Konditsioner" disagree (1 vs 3).
 *
 * Keep these families in sync with iOS. Anything unmatched falls back to a neutral sparkle, which
 * is what iOS shows for "Ayol haydovchi" too — it has no woman-driver branch either.
 */
object ServiceIcons {

    private val FAMILIES: List<Pair<List<String>, Int>> = listOf(
        listOf("kondits", "кондиц", "conditioner", "air") to R.drawable.ph_snowflake,
        listOf("kutis", "kutish", "ожида", "wait") to R.drawable.ph_clock,
        listOf("bola", "дет", "child", "baby") to R.drawable.ph_baby,
        listOf("hayvon", "живот", "pet", "dog") to R.drawable.ph_paw_print,
        listOf("suv", "вода", "water") to R.drawable.ph_drop,
        listOf("musiqa", "музык", "music") to R.drawable.ph_music_note,
        listOf("wi-fi", "wifi", "internet") to R.drawable.ph_wifi_high,
        listOf("yuk", "багаж", "luggage") to R.drawable.ph_suitcase,
        listOf("tezkor", "экспресс", "express", "speed") to R.drawable.ph_lightning,
    )

    @DrawableRes
    fun iconFor(name: String?): Int {
        // lowercase(Locale.ROOT) would be wrong here: Turkish-style locales map 'I' to a dotless
        // 'ı', which would stop "Internet"/"Wi-Fi" matching. The default lowercase() on these
        // Latin/Cyrillic keys is what we want, and the app never runs under a Turkish locale.
        val key = name?.lowercase().orEmpty()
        if (key.isBlank()) return R.drawable.ph_sparkle
        return FAMILIES.firstOrNull { (keywords, _) -> keywords.any { key.contains(it) } }
            ?.second
            ?: R.drawable.ph_sparkle
    }

    /**
     * Paint [view] with this service's icon: the backend's own artwork when it ships one, and the
     * local glyph otherwise.
     *
     * NOTE the `icon` field does not exist on the wire yet. Verified against prod on 2026-07-21:
     * neither `order_items[].service` nor `GET service/order` returns an icon or photo — only
     * TARIFFS do. This is wired ahead of the backend adding it, so the day the field appears the
     * icons light up with no further app change; until then every call falls straight through to
     * [iconFor] and nothing hits the network.
     *
     * The glyph is painted FIRST, synchronously, and doubles as the placeholder and the failure
     * state — so a slow or dead image never leaves an empty box, and a failed load never falls back
     * to Glide's own untinted error drawable (which is how icons end up rendering black).
     */
    fun bindInto(view: ImageView, name: String?, icon: String? = null) {
        val glyph = iconFor(name)
        val accent = ContextCompat.getColorStateList(view.context, R.color.app_color)

        // Cancel anything still in flight from a recycled row before repainting.
        Glide.with(view).clear(view)
        ImageViewCompat.setImageTintList(view, accent)
        view.setImageResource(glyph)

        val url = MediaUrl.of(icon) ?: return

        // SVG goes to AndroidSVG; Glide has no decoder for it and would fail silently, leaving the
        // glyph up forever even once the backend starts serving artwork.
        if (MediaUrl.isSvg(url)) {
            SvgIconLoader.load(view, url) { ImageViewCompat.setImageTintList(view, null) }
            return
        }

        Glide.with(view)
            .load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean = true // keep the tinted glyph that is already on screen

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    // Server artwork carries its own colour; leaving the accent tint on would
                    // flatten it into a solid amber blob.
                    ImageViewCompat.setImageTintList(view, null)
                    return false
                }
            })
            .into(view)
    }

}
