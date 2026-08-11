package uz.teamwork.mehrgodriver.common.socket

import uz.teamwork.mehrgodriver.domain.model.fare.FareResponse

/**
 * Envelope for the live-fare push that the server sends after every batch:
 * `{ "key": "order_price_updated", "data": { ...FareResponse } }`.
 * Also used to decode the ack of an outgoing `order_gps_batch` frame
 * — the payload shape is the same.
 */
data class SocketFareResponse(
    val status: Int,
    val key: String,
    // NULLABLE on purpose. Kotlin's non-null is a compile-time promise Gson does not keep: it
    // populates fields reflectively, so a frame with a missing/null `data` yields a null here
    // regardless of the declared type, and the NPE then surfaces at the call site instead of
    // the parse. Declaring the truth forces callers to check. (Same class of bug as the
    // Long-vs-fractional `waiting_cost` crash — trust the wire, not the declaration.)
    val data: FareResponse?
)
