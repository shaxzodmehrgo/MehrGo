package uz.teamwork.mehrgodriver.domain.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import uz.teamwork.mehrgodriver.common.Helper

data class OrderHistory(
    val items: List<OrderHistoryItem>,
    val _meta: Meta,
) {
    @Parcelize
    data class OrderHistoryItem(
        val id: Int,
        val status: Status,

        @SerializedName("created_at")
        val date: Date,

        val myOrder: MyOrder,
    ) : Parcelable {
        @Parcelize
        data class Status(
            @SerializedName("int")
            val value: Int,

            @SerializedName("string")
            val name: String
        ) : Parcelable
    }

    data class Meta(
        val totalCount: Int,
        val pageCount: Int,
        val currentPage: Int,
        val perPage: Int
    )

    @Parcelize
    data class Date(
        val datetime: String,
        val date: String,
        val time: String
    ) : Parcelable

    @Parcelize
    data class MyOrder(
        val id: Int,
        val price: String,

        @SerializedName("address_category")
        val addressCategory: AddressCategory?,

        val address: Address?,

        @SerializedName("address_finish")
        val addressFinish: Address? = null,

        val locations: List<Location>? = null,

        val tariff: Tariff? = null,

        // Numbers arrive as JSON strings (like `price`); Gson reads them as String safely.
        val distance: String? = null,

        val contact: Contact? = null,

        @SerializedName("execution_time")
        val executionTime: String? = null,

        @SerializedName("waiting_time")
        val waitingTime: String? = null,

        // The add-ons the client had on for this trip. Their total is already inside `price`
        // (order_price_includes_services), so this is a breakdown, never something to add on top.
        @SerializedName("order_items")
        val services: List<Service>? = null,

        // NOT on the history wire (verified prod 2026-07-31 — user/me and order_completed carry
        // it, order/history does not; `payment` below is what history ships instead). Wired ahead
        // of the backend: null renders as CASH, matching the app-wide convention
        // (TripFinishFragment treats null as cash). `payment` wins when present.
        @SerializedName("is_card_payment")
        val isCardPayment: Boolean? = null,

        // The settlement block (shipped 2026-07-31). See [Payment].
        val payment: Payment? = null,

        val info: String? = null
    ) : Parcelable {

        /**
         * How the trip was actually settled — `myOrder.payment` on order/history.
         *
         * [price] is the GROSS order total (same figure as [MyOrder.price]); the client's
         * [promoCodeDiscount] and [bonusUsed] come off it and [finalPrice] is what is really
         * collected — the number the trip receipt shows as "Naqd olinadi". [cardPaid] is the part
         * of [finalPrice] already settled by card, so cash-in-hand = final − cardPaid.
         *
         * Every field is a nullable String even where the wire sends a JSON number: Gson coerces
         * numbers into String safely, whereas a numeric field blows up the WHOLE page parse if the
         * backend ever sends `""`/`null` for one row. Same reasoning as [MyOrder.price]/[distance].
         * The block itself is null on rows written before the backend shipped it.
         */
        @Parcelize
        data class Payment(
            val method: String? = null,

            val price: String? = null,

            @SerializedName("promo_code")
            val promoCode: String? = null,

            @SerializedName("promo_code_discount")
            val promoCodeDiscount: String? = null,

            @SerializedName("bonus_used")
            val bonusUsed: String? = null,

            @SerializedName("final_price")
            val finalPrice: String? = null,

            @SerializedName("card_paid")
            val cardPaid: String? = null
        ) : Parcelable {
            /** Gross order total; 0 when the backend omitted it. */
            val gross: Long get() = price.toAmount()

            /** Promo-code discount taken off the gross. Never negative. */
            val promo: Long get() = promoCodeDiscount.toAmount()

            /** Client bonus points spent on this trip. Never negative. */
            val bonus: Long get() = bonusUsed.toAmount()

            /** Part of [finalTotal] already settled by card (0 on a pure-cash trip). */
            val card: Long get() = cardPaid.toAmount()

            /**
             * What the client actually owes. Falls back to gross − discounts when the backend
             * omits the field, so the figure never silently reads as 0.
             */
            val finalTotal: Long
                get() = finalPrice?.trim()?.toDoubleOrNull()?.toLong()
                    ?: (gross - promo - bonus).coerceAtLeast(0L)

            /**
             * What is actually settled: a cash trip rounds to the nearest 1 000 so'm (the driver
             * cannot hand back 130 so'm), a card trip stays exact because the card is billed the
             * precise amount. An unrecognised method is left exact too — never invent a rounding
             * on a settlement this app doesn't understand.
             */
            val collectedTotal: Long
                get() = if (isCard == false) Helper.roundToThousand(finalTotal) else finalTotal

            /**
             * Rounding applied to reach [collectedTotal]; 0 when none. Shown as its own breakdown
             * line so gross − promo − bonus ± rounding still foots to the figure on the hero.
             */
            val roundingDelta: Long get() = collectedTotal - finalTotal

            /** Cash the driver takes in hand — the card-settled part is already collected. */
            val cashToCollect: Long get() = (collectedTotal - card).coerceAtLeast(0L)

            /**
             * Card trip — `true` card, `false` cash, `null` UNKNOWN.
             *
             * Only the two method labels the wire is known to use are decided; anything else
             * (a wallet/`payme`/`click` method the backend adds later) stays null so the screen
             * hides the row rather than telling the driver to collect cash on a prepaid trip.
             * Guessing "cash" for an unrecognised label is exactly the corruption this row was
             * kept hidden for. `card_paid > 0` wins outright: money already on a card is card
             * money whatever the label says.
             */
            val isCard: Boolean?
                get() = when {
                    card > 0L -> true
                    method.equals("card", ignoreCase = true) -> true
                    method.equals("cash", ignoreCase = true) -> false
                    else -> null
                }

            /** True when there is a discount worth breaking out on screen. */
            val hasDiscount: Boolean get() = promo > 0L || bonus > 0L

            // toDoubleOrNull, NOT toLongOrNull: these are money columns and the
            // backend formats them as "33000.00" on some rows. toLongOrNull
            // returns null for that, which sent `gross` to 0 and `finalTotal`
            // to a fallback computed from zeros — the hero then read "0 so'm"
            // on a real trip. The client parses the identical block the same
            // tolerant way, so both apps agree row for row.
            private fun String?.toAmount(): Long =
                this?.trim()?.toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: 0L
        }

        @Parcelize
        data class Service(
            val service: ServiceName,
            val total: Int
        ) : Parcelable {
            // `icon` is not on the wire yet — see Order.Service.ServiceName.
            @Parcelize
            data class ServiceName(val name: String, val icon: String? = null) : Parcelable
        }

        @Parcelize
        data class AddressCategory(val name: String?) : Parcelable

        @Parcelize
        data class Address(val name: String?) : Parcelable

        @Parcelize
        data class Location(val name: String? = null, val position: String? = null) : Parcelable

        @Parcelize
        data class Tariff(val name: String? = null) : Parcelable

        @Parcelize
        data class Contact(val name: String? = null, val phone: String? = null) : Parcelable
    }
}
