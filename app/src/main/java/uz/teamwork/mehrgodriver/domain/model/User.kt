package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int? = null,

    @SerializedName("first_name")
    val firstName: String? = null,

    @SerializedName("father_name")
    val fatherName: String? = null,

    @SerializedName("last_name")
    val lastName: String? = null,

    val phone: String? = null,

    // Driver avatar, added to Nurse::fields() right after `phone` (backend, 2026-07-31).
    // Relative path ("/uploads/nurse-data/x.jpg", or "/admin/images/defaultAvatar.png" when the
    // driver has none) — resolve through MediaUrl, never by concatenating IMAGE_URL.
    val photo: String? = null,

    val status: Status? = null,

    @SerializedName("auth_key")
    val authKey: String? = null,

    var balance: Int? = null,

    val branch: Branch? = null,

    val today: Today? = null,

    val tariffs: List<Tariff>? = null,

    // Profile-screen fields (iOS parity). The backend ships these on user/me;
    // `car` reuses Order.Car (same car_number / car_model / car_color shape).
    val car: Order.Car? = null,

    val rating: Double? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    val orders: List<Order>? = null,

    @SerializedName("device_token")
    val deviceToken: String? = null,

    val workStatus: WorkStatus
) {
    data class Status(
        @SerializedName("string")
        val valueText: String? = null,

        @SerializedName("int")
        val valueNumber: Int? = null
    )

    data class Branch(
        val id: Int,

        // Working-branch label shown on the profile. user/me ships it as `name`;
        // some payloads use `city` — mirror the iOS "name OR city" fallback.
        val name: String? = null,

        @SerializedName("city")
        val city: String? = null,

        @SerializedName("dispetcher_number")
        val dispatcherNumber: String? = null,

        @SerializedName("blocked_aps")
        val blockedApps: String? = null,
    )

    data class Today(
        val count: Int? = null,
        val price: Long? = null
    )

    data class Tariff(val name: String? = null)

    data class WorkStatus(val status: String)
}
