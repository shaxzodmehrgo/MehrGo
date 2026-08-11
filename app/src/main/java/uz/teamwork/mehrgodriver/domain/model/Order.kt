package uz.teamwork.mehrgodriver.domain.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Order(
    val id: Int,
    val contact: Contact?,

    @SerializedName("starting_price")
    val startingPrice: Int,

    @SerializedName("price_of_per_unit")
    val priceInCity: Int,

    @SerializedName("address_category")
    val addressCategory: AddressCategory?,

    @SerializedName("address")
    val address: Address?,

    @SerializedName("address_category_finish")
    val addressCategoryFinish: AddressCategoryFinish?,

    @SerializedName("address_finish")
    val addressFinish: AddressFinish?,

    val price: Int,
    val latitude: String,
    val longitude: String,
    val info: String? = null,

    val status: Status,

    // This field can change
    var state: Int,

    @SerializedName("podacha")
    val addPrice: Int? = null,

    @SerializedName("order_items")
    val services: List<Service>?,

    val branch: Branch,
    val distance: Double,
    val locations: List<Location>,
    val tariff: Tariff,

    @SerializedName("driver_number")
    val driverNumber: String,

    val car: Car? = null,
    val from: Int,

    @SerializedName("use_bonus")
    val useBonus: Boolean,

    // SERVER-applied discounts. On the `order_completed` settlement frame these are the
    // amounts the server actually deducted — the finish receipt prefers them over the
    // preview-computed values so finalTotal foots to the real settlement (a percent bonus
    // computed off the agreed preview drifts when the server 2c-bills its meter). Nullable:
    // pre-completion payloads may omit them.
    @SerializedName("bonus_payment")
    val bonusPayment: Long? = null,

    @SerializedName("promo_code_payment")
    val promoCodePayment: Long? = null,

    @SerializedName("promo")
    val promoCode: PromoCode?,

    @SerializedName("is_card_payment")
    val isCardPayment: Boolean?,

    // When the order was placed — nested {int, datetime, date} like the history model's created_at
    // (verified against the iOS driver's order payload). Nullable so a slim/older payload can omit it.
    @SerializedName("created_at")
    val createdAt: CreatedAt? = null

) : Parcelable {

    @Parcelize
    data class CreatedAt(
        @SerializedName("int")
        val timestamp: Long? = null,
        val datetime: String? = null,
        val date: String? = null,
        val time: String? = null
    ) : Parcelable

    @Parcelize
    data class AddressCategory(
        val id: Int,
        val name: String,
    ) : Parcelable

    @Parcelize
    data class Address(
        val id: Int,
        val name: String,
        val latitude: String,
        val longitude: String
    ) : Parcelable

    @Parcelize
    data class AddressCategoryFinish(
        val id: Int,
        val name: String,
    ) : Parcelable

    @Parcelize
    data class AddressFinish(
        val id: Int,
        val name: String,
        val latitude: String,
        val longitude: String
    ) : Parcelable

    @Parcelize
    data class Status(
        @SerializedName("int")
        val value: Int,

        @SerializedName("string")
        val name: String,
    ) : Parcelable

    @Parcelize
    data class Service(
        val id: Int,
        val price: String,
        val total: Int,
        val service: ServiceName
    ) : Parcelable {
        @Parcelize
        data class ServiceName(
            val name: String,
            val info: String?,
            // Not on the wire yet (verified against prod 2026-07-21 — only tariffs carry an icon).
            // Wired ahead of the backend so the artwork appears with no further app change; until
            // then it stays null and ServiceIcons falls back to the local glyph.
            val icon: String? = null
        ) : Parcelable
    }

    @Parcelize
    data class Branch(
        val id: String,
        val name: String,

        @SerializedName("lat")
        val latitudeCity: String?,

        @SerializedName("lon")
        val longitudeCity: String?,

        @SerializedName("radius")
        val radiusCity: String?,

        @SerializedName("accept_waiting")
        val acceptWaiting: Int?,

        @SerializedName("dispetcher_number")
        val dispatcherNumber: String,

        @SerializedName("clientBonusSettings")
        val clientBonusSettings: ClientBonusSettings?,

        val polygon: Polygon?

    ) : Parcelable {
        @Parcelize
        data class ClientBonusSettings(
            @SerializedName("minimum_amount_to_use_bonus")
            val minAmount: Long,

            @SerializedName("maximum_amount_to_use_bonus_per_order")
            val maxAmount: String
        ) : Parcelable

        @Parcelize
        data class Polygon(
            val boundary: String?
        ) : Parcelable
    }

    @Parcelize
    data class Tariff(
        val id: Int,
        val name: String?,

        @SerializedName("min_distance")
        val minDistance: Int? = null,

        @SerializedName("distances")
        val distanceIntervals: List<DistanceInterval>? = null,

        @SerializedName("price_of_out")
        val priceOfOut: String,

        @SerializedName("min_wait_time")
        val minWaitTime: String,

        @SerializedName("price_of_waiting")
        val priceOfWaiting: String,

        @SerializedName("min_wait_time_on_way")
        val minWaitTimeOnWay: Int? = null,

        @SerializedName("price_of_waiting_on_way")
        val priceOfWaitingOnWay: Int? = null,

        @SerializedName("comission")
        val commission: String?

    ) : Parcelable {

        @Parcelize
        data class DistanceInterval(
            val id: Int,
            val start: Long,
            val end: Long,
            val price: Long
        ) : Parcelable
    }

    @Parcelize
    data class Location(
        val name: String,

        val position: Int,

        @SerializedName("lat")
        val latitude: Double,

        @SerializedName("lon")
        val longitude: Double
    ) : Parcelable

    @Parcelize
    data class Contact(
        val id: Int,
        val name: String,
        val phone: String,
        val bonus: Long?
    ) : Parcelable

    @Parcelize
    data class Car(
        @SerializedName("car_number")
        val carNumber: String? = null,

        @SerializedName("car_model")
        val carModel: CarModel? = null,

        @SerializedName("car_color")
        val carColor: CarColor? = null
    ) : Parcelable {

        @Parcelize
        data class CarModel(
            val id: Int? = null,
            val name: String? = null,

            @SerializedName("second_name")
            val secondName: String? = null
        ) : Parcelable

        @Parcelize
        data class CarColor(
            val id: Int? = null,
            val name: String? = null,

            @SerializedName("second_name")
            val secondName: String? = null
        ) : Parcelable
    }

    @Parcelize
    data class PromoCode(
        val usage: Usage?,
    ) : Parcelable {
        @Parcelize
        data class Usage(
            val code: String,
            val amount: String
        ) : Parcelable
    }
}