package uz.teamwork.mehrgodriver.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Everything the post-settlement receipt ([TripFinishView] on iOS →
 * `TripFinishFragment` on Android) renders, captured at finish time from the
 * already-submitted `RequestOrderFinish` + the settled [Order] so the screen
 * does NO network call and foots to the exact same numbers the server was sent.
 *
 *   finalTotal = grossTotal − bonus − promo
 *   rideCost   = grossTotal − waitCost − servicesCost   (the "ride/track" slice)
 */
@Parcelize
data class TripReceipt(
    val order: Order,
    val distanceKm: String,
    val grossTotal: Long,
    val rideCost: Long,
    val waitCost: Long,
    val servicesCost: Long,
    val waitMs: Long,
    val durationMs: Long,
    val bonus: Long,
    val promo: Long,
    val completedAt: Long,
    /**
     * True only when these numbers came from the SERVER's settlement — the `order_completed`
     * frame, or history's `myOrder.payment`. False means they are the locally previewed total the
     * driver approved, which the server may still judge up or down; the receipt must not present
     * that as the final bill (nor compute a commission line off it), so the screen waits instead.
     */
    val settled: Boolean = false,

    /**
     * The server's OWN `final_price`, when the settlement carried one.
     *
     * `grossTotal − bonus − promo` is only this app's RECONSTRUCTION of that number, and the two
     * need not agree — the server may apply a cap or an adjustment the app knows nothing about.
     * When the real figure is available it wins outright, so this screen and the history row
     * (which reads the same `final_price`) cannot print two different totals for one trip.
     */
    val settledFinalTotal: Long? = null,

    /**
     * Cash/card as the SETTLEMENT reports it (`payment.isCard`), which also counts `card_paid > 0`
     * and is therefore not always the same answer as `order.isCardPayment`. It decides whether the
     * cash rounding applies, so both screens must decide it the same way or they differ by up to
     * 999 so'm on the same trip.
     */
    val settledIsCard: Boolean? = null
) : Parcelable {

    // Floored at 0: bonus/promo were capped against the PREVIEW total at dialog time, but
    // grossTotal may later be swapped to the server-settled figure (order_completed frame),
    // which can be lower — a negative driver-facing total is never right.
    val finalTotal: Long
        get() = settledFinalTotal ?: (grossTotal - bonus - promo).coerceAtLeast(0L)
}
