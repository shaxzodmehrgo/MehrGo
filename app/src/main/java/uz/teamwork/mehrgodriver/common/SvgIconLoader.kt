package uz.teamwork.mehrgodriver.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads a server-provided SVG into an ImageView, rendered to a fixed-size bitmap and cached per URL.
 *
 * Ported from the client app (common/helper/SvgIconLoader.kt) so both apps render the same service
 * artwork the same way. Glide cannot do this job — it has no SVG decoder, so a ".svg" would fail
 * silently and sit on the fallback forever.
 *
 * [onLoaded] fires on the main thread only when a bitmap is actually applied; callers use it to
 * drop the accent tint, which must stay on while the local glyph is showing.
 */
object SvgIconLoader {

    private const val RENDER_SIZE_PX = 96

    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** @return true when a cached bitmap was applied synchronously. */
    fun load(imageView: ImageView, url: String, onLoaded: () -> Unit): Boolean {
        // Guards a recycled row: a slow response must not land on a view that has moved on.
        imageView.tag = url

        cache[url]?.let {
            imageView.setImageBitmap(it)
            onLoaded()
            return true
        }

        scope.launch {
            val bitmap = runCatching {
                val svg = URL(url).openStream().use { SVG.getFromInputStream(it) }
                // Without a viewBox AndroidSVG renders at the document's own pixel size, so a large
                // icon comes out as a tiny clipped corner — synthesize one so the viewport scaling
                // below always applies.
                if (svg.documentViewBox == null &&
                    svg.documentWidth > 0f && svg.documentHeight > 0f
                ) {
                    svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")
                Bitmap.createBitmap(RENDER_SIZE_PX, RENDER_SIZE_PX, Bitmap.Config.ARGB_8888)
                    .also { bmp ->
                        svg.renderToCanvas(
                            Canvas(bmp),
                            RectF(0f, 0f, RENDER_SIZE_PX.toFloat(), RENDER_SIZE_PX.toFloat())
                        )
                    }
            }.getOrNull() ?: return@launch // failure is terminal: the glyph already on screen stays

            cache[url] = bitmap
            withContext(Dispatchers.Main) {
                if (imageView.tag == url) {
                    imageView.setImageBitmap(bitmap)
                    onLoaded()
                }
            }
        }
        return false
    }
}
