package uz.teamwork.mehrgodriver.common

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import uz.teamwork.mehrgodriver.R

/**
 * Fills [container] with the order's INTERMEDIATE route stops — everything between the first
 * (pickup) and last (dropoff) point, which each view still renders itself. One row per stop: an
 * app_color dot + the address only (no venue/district subtitle). The container is hidden for
 * 2-point orders (no middle stops). Shared by every new-order view (offer sheet, overlay, pool
 * list item) so multi-point orders show ALL points everywhere.
 */
object RoutePointsBinder {
    fun bindMiddle(
        container: LinearLayout,
        points: List<String>,
        rowLayout: Int = R.layout.item_route_point
    ) {
        container.removeAllViews()
        if (points.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(container.context)
        points.forEach { name ->
            val row = inflater.inflate(rowLayout, container, false)
            row.findViewById<TextView>(R.id.tvPointAddress).text = name
            container.addView(row)
        }
    }
}
