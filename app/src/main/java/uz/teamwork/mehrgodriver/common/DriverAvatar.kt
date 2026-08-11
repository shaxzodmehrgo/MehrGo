package uz.teamwork.mehrgodriver.common

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import uz.teamwork.mehrgodriver.BuildConfig

/**
 * The driver's own avatar, shared by every screen that shows one — the profile hero and the
 * settings header. Both used to be a bare `icon_user` glyph, and the profile screen grew its own
 * copy of this logic first; keeping one implementation is the point, because "identical layout ids
 * in two screens, only one of them patched" is a bug this codebase has already shipped twice.
 *
 * The pair of views is deliberate: [placeholder] holds the brand-tinted glyph and stays visible
 * until real pixels decode, so a slow network or a missing file never leaves an empty circle.
 * [photoView] is revealed only in `onResourceReady`.
 *
 * **[photoView] must be INVISIBLE, never GONE, while it waits.** Glide's `ViewTarget` will not
 * start a load until the view reports a positive measured size, and a GONE view is skipped by its
 * parent's measure/layout pass, so it never gets one. The request then hangs forever with no
 * success AND no failure — the exact silence seen in the 2026-08-01 capture, where this log line
 * printed the correct URL five times and `onLoadFailed` never fired. INVISIBLE is measured and
 * laid out, it simply isn't drawn, which is all the reveal needs.
 *
 * On prod a missing `/uploads/...` file does NOT 404 — nginx hands it to PHP, which 302s to
 * `/site/login`. Glide follows that and fails to decode an HTML page, so a bad filename in the DB
 * arrives here as an ordinary decode failure. That is why the DEBUG log prints the resolved URL:
 * on screen a wrong path, an absent field, and "no photo uploaded" all look the same.
 */
object DriverAvatar {

    private const val TAG = "MehrgoAvatar"

    /**
     * @param photo the raw relative path from `user/me` (`Nurse::fields()`), which the backend
     *   fills with its own `/admin/images/defaultAvatar.png` when the driver never uploaded one.
     *   That generic grey PNG is treated as "no photo" so the brand glyph stays instead.
     */
    fun bind(photo: String?, photoView: ImageView, placeholder: ImageView) {
        val url = MediaUrl.of(photo)
            ?.takeUnless { it.contains("defaultavatar", ignoreCase = true) }
        if (BuildConfig.DEBUG) Log.d(TAG, "photo=$photo -> url=$url")

        if (url == null) {
            Glide.with(photoView).clear(photoView)
            photoView.setImageDrawable(null)
            photoView.visibility = View.INVISIBLE
            placeholder.visibility = View.VISIBLE
            return
        }

        Glide.with(photoView)
            .load(url)
            .circleCrop()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    if (BuildConfig.DEBUG) Log.w(TAG, "load failed: $url", e)
                    photoView.visibility = View.INVISIBLE
                    placeholder.visibility = View.VISIBLE
                    return true // keep the glyph that is already on screen
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    if (BuildConfig.DEBUG) Log.d(TAG, "ready (${dataSource}): $url")
                    photoView.visibility = View.VISIBLE
                    placeholder.visibility = View.GONE
                    return false
                }
            })
            .into(photoView)
    }
}
