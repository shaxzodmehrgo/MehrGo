package uz.teamwork.mehrgodriver.common

import android.net.Uri
import uz.teamwork.mehrgodriver.common.PendingDeepLink.consume

/**
 * One-slot holder for an order id that arrived from OUTSIDE the app — currently the Telegram bot's
 * "Buyurtmani qabul qilish" App Link, `https://mehrgo.uz/driver/order/<id>` (see
 * [Constants.DEEPLINK_HOST] / [Constants.DEEPLINK_ORDER_PATH]).
 *
 * A process-wide holder rather than an Intent extra threaded through the screens, because the link
 * can land in states that do not reach the orders pool in one hop — a logged-out driver goes to the
 * login flow first, and the id has to survive until they are actually inside the app. `MainActivity`
 * retries the routing whenever it lands back on the map, and `OrdersMapFragment` [consume]s the id
 * once its pool has loaded, so the offer never re-opens on a later visit.
 */
object PendingDeepLink {

    private var pendingOrderId: Int? = null

    /** Store the order id carried by [uri], if it is one of ours. Returns true when something was stored. */
    fun capture(uri: Uri?): Boolean {
        val id = parseOrderId(uri) ?: return false
        pendingOrderId = id
        return true
    }

    /**
     * `https://mehrgo.uz/driver/order/80893` → `80893`. Null for anything else — a foreign host, a
     * different path, or a trailing segment that is not a positive integer. The manifest filter
     * already narrows this down, but the check is repeated here so a malformed link is dropped
     * rather than turning into a lookup for order 0.
     */
    fun parseOrderId(uri: Uri?): Int? {
        if (uri == null) return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals(Constants.DEEPLINK_HOST, ignoreCase = true)) return null
        if (uri.path?.startsWith(Constants.DEEPLINK_ORDER_PATH) != true) return null
        return uri.lastPathSegment?.toIntOrNull()?.takeIf { it > 0 }
    }

    /** The pending id without clearing it — for "is there routing left to do?" checks. */
    fun peek(): Int? = pendingOrderId

    /** Take the pending id and empty the slot. */
    fun consume(): Int? {
        val id = pendingOrderId
        pendingOrderId = null
        return id
    }

    fun clear() {
        pendingOrderId = null
    }
}
