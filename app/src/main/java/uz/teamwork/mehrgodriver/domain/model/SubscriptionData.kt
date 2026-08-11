package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class SubscriptionData(
    val items: List<Subscription>,
    val _meta: Meta,
) {
    data class Subscription(
        val id: Int,
        val name: String,
        val descriptions: String?,
        val image: String?,

        @SerializedName("background_image")
        val backgroundImage: String,

        @SerializedName("validity_period")
        val validityPeriod: Long,
        val price: Long,

        val tariffs: List<Tariff>?,

        val purchased: Purchased?

    ) {
        data class Tariff(
            val id: Int,
            val name: String
        )

        data class Purchased(
            val id: Int,

            @SerializedName("valid_to")
            val validTo: Long,

            @SerializedName("expires_in")
            val expiresIn: Long
        )
    }

    data class Meta(
        val totalCount: Int,
        val pageCount: Int,
        val currentPage: Int,
        val perPage: Int
    )

    data class Date(
        val datetime: String,
        val date: String,
        val time: String
    )

    data class MyOrder(
        val id: Int,
        val price: String,

        @SerializedName("address_category")
        val addressCategory: AddressCategory?,

        val address: Address?
    ) {
        data class AddressCategory(
            val name: String?
        )

        data class Address(
            val name: String?
        )
    }
}
