package uz.teamwork.mehrgodriver.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import uz.teamwork.mehrgodriver.R
import kotlin.math.min

/**
 * Circular countdown ring — the Android twin of the iOS `CountdownTimerView`.
 *
 * A depleting brand-orange arc over a faint track, with the remaining whole
 * seconds drawn bold in the centre. Starts full at 12 o'clock and sweeps
 * clockwise as time runs out. This view is intentionally dumb: the host owns
 * the `CountDownTimer` and pushes state in via [setTime]; the ring only draws.
 *
 *     ring.setTime(remainingSeconds = 12, totalSeconds = 20)
 */
class CountdownRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringColor = ContextCompat.getColor(context, R.color.app_color)

    private val strokePx = dp(3.5f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
        color = ringColor
        alpha = (255 * 0.18f).toInt()
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
        color = ringColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val oval = RectF()

    /** 1f = full ring, 0f = empty. */
    private var fraction: Float = 1f
    private var label: String = ""

    /** Push the current countdown state; clamps and repaints. */
    fun setTime(remainingSeconds: Int, totalSeconds: Int) {
        val safeTotal = if (totalSeconds <= 0) 1 else totalSeconds
        val safeRemaining = remainingSeconds.coerceIn(0, safeTotal)
        fraction = safeRemaining.toFloat() / safeTotal.toFloat()
        label = safeRemaining.toString()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokePx / 2f + dp(1f)
        oval.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        canvas.drawArc(oval, -90f, 360f * fraction, false, arcPaint)

        textPaint.textSize = min(width, height) * 0.42f
        val cy = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, width / 2f, cy, textPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
