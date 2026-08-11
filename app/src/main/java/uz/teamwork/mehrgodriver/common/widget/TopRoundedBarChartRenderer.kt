package uz.teamwork.mehrgodriver.common.widget

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.math.abs
import kotlin.math.min

/**
 * [BarChartRenderer] that rounds ONLY the TOP corners of each bar (top-left + top-right),
 * leaving the bottom edge and the vertical sides as a sharp rectangle — the "capsule top,
 * flat base" look. [radiusDp] is the corner radius in dp; it is clamped per-bar so a thin or
 * short bar can never over-round.
 *
 * MPAndroidChart 3.1.0 has no native rounded-bar support, so we re-implement drawDataSet()
 * and draw each bar as a Path instead of a rect. Highlight drawing is left to the base class
 * (the earnings chart disables highlighting anyway).
 */
class TopRoundedBarChartRenderer(
    chart: BarDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val radiusDp: Float = 6f,
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val barPath = Path()
    private val rect = RectF()

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val trans = mChart.getTransformer(dataSet.axisDependency)

        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth = Utils.convertDpToPixel(dataSet.barBorderWidth)
        val drawBorder = dataSet.barBorderWidth > 0f

        val buffer = mBarBuffers[index]
        buffer.setPhases(mAnimator.phaseX, mAnimator.phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)
        buffer.feed(dataSet)
        trans.pointValuesToPixel(buffer.buffer)

        val singleColor = dataSet.colors.size == 1
        if (singleColor) {
            mRenderPaint.color = dataSet.color
        }

        val radius = Utils.convertDpToPixel(radiusDp)
        val values = buffer.buffer

        var j = 0
        while (j < values.size) {
            val left = values[j]
            val top = values[j + 1]
            val right = values[j + 2]
            val bottom = values[j + 3]

            if (!mViewPortHandler.isInBoundsLeft(right)) {
                j += 4
                continue
            }
            if (!mViewPortHandler.isInBoundsRight(left)) break

            if (!singleColor) {
                mRenderPaint.color = dataSet.getColor(j / 4)
            }

            // Clamp the radius so it never exceeds half the bar width or the bar height.
            val r = min(radius, min((right - left) / 2f, abs(bottom - top)))
            rect.set(left, top, right, bottom)
            barPath.reset()
            // Corner radii: top-left, top-right rounded; bottom-right, bottom-left sharp.
            barPath.addRoundRect(
                rect,
                floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f),
                Path.Direction.CW
            )
            c.drawPath(barPath, mRenderPaint)
            if (drawBorder) c.drawPath(barPath, mBarBorderPaint)

            j += 4
        }
    }
}
