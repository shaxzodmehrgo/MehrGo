package uz.teamwork.mehrgodriver.domain.model.requests

data class RequestOrderFinish(
    var distance: String,
    var latitude_finish: Double?,
    var longitude_finish: Double?,
    /**
     * NOT SENT — the backend prices the trip itself and ignores whatever the app
     * puts here. Wire-proved repeatedly (79687/79688/79711, and again on 80296:
     * the app posted 25 000, the server booked 5 000). Posting a figure only
     * created the illusion that the driver's approval set the amount.
     *
     * Left nullable rather than deleted so the contract stays visible; Gson is
     * not configured with serializeNulls, so a null is omitted from the body.
     */
    var total_price: String? = null,
    /** Pickup wait ONLY (ms) — the wait until Go/"Kettik". Older app versions sent the
     *  combined wait here; the backend still accepts that, but the split is preferred. */
    var waiting_time: String,
    var execution_time: String,
    var finish_address_id: Int?,
    var bonus_payment: Long,
    var promo_code_payment: Long,
    var accuracy: Float?,
    /** On-route wait (ms) — waits AFTER the client boarded (Go/"Kettik"). Additive field
     *  (ORDER_COMPLETE_WAITING.md); the backend treats a missing value as 0 and prices it
     *  with price_of_waiting_on_way (falling back to price_of_waiting). */
    var waiting_time_ontheway: String = "0"
)
