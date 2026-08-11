package uz.teamwork.mehrgodriver.domain.model.birga

import com.google.gson.annotations.SerializedName

/**
 * DTO контракта водителя Birga (школьный шаттл).
 *
 * Все ответы бэкенда ПЛОСКИЕ: {"ok":true, ...} на успех, либо HTTP 4xx + {"ok":false,"error":"..."}
 * на ошибку (см. app/controllers/DriverController.php + BaseApiController::ok/abort в MaktabGo).
 * Поэтому здесь НЕ используется taxi-обёртка BaseResponse<T> ({data:...}) — поля лежат в корне.
 * Все поля с дефолтами: Gson получает синтетический no-arg конструктор и не падает на пропусках.
 */

/** Тело ошибки Birga (парсится в UC из errorBody при HttpException). */
data class BirgaError(
    val ok: Boolean = false,
    val error: String? = null
)

/** Водитель (driverData()). */
data class BirgaDriver(
    val id: Int = 0,
    val name: String? = null,
    val phone: String? = null,
    @SerializedName("vehicle_class") val vehicleClass: String? = null,
    val capacity: Int = 0,
    val plate: String? = null,
    val online: Boolean = false,
    @SerializedName("car_model") val carModel: String? = null,
    @SerializedName("car_color") val carColor: String? = null,
    val verified: Boolean = false,
    val status: String? = null,
    val balance: Int = 0
)

/** POST /api/driver/otp */
data class BirgaOtpResponse(
    val ok: Boolean = false,
    val phone: String? = null,
    val channel: String? = null,           // sms | telegram | dev
    val message: String? = null,
    @SerializedName("dev_code") val devCode: String? = null   // только dev-режим
)

/** POST /api/driver/login (вход = регистрация: новый водитель создаётся pending). */
data class BirgaLoginResponse(
    val ok: Boolean = false,
    val token: String? = null,             // auth_key → Bearer
    val driver: BirgaDriver? = null,
    @SerializedName("is_new") val isNew: Boolean = false,
    val verified: Boolean = false
)

/** POST /api/driver/online */
data class BirgaOnlineResponse(
    val ok: Boolean = false,
    val driver: BirgaDriver? = null
)

/**
 * Краткая карточка рейса (routeBrief). ВНИМАНИЕ: здесь school — это ИМЯ (String) или null,
 * тогда как в полном рейсе (BirgaRouteFull) school — объект. Поэтому классы раздельные.
 */
data class BirgaRouteBrief(
    val id: Int = 0,
    val status: String? = null,            // forming | active | running | done | cancelled
    val school: String? = null,
    @SerializedName("vehicle_class") val vehicleClass: String? = null,
    val children: Int = 0,
    val capacity: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0,
    @SerializedName("pickup_mode") val pickupMode: String? = null,   // door | points
    @SerializedName("start_date") val startDate: String? = null,
    val interested: Int = 0
)

/** GET /api/driver/routes */
data class BirgaRoutesResponse(
    val ok: Boolean = false,
    val available: List<BirgaRouteBrief> = emptyList(),
    val mine: List<BirgaRouteBrief> = emptyList()
)

/** Школа в полном рейсе (routeFull.school — объект). */
data class BirgaSchool(
    val name: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/** Одна семья/посадка внутри точки сбора. */
data class BirgaPickup(
    @SerializedName("children_count") val childrenCount: Int = 0,
    val address: String? = null,
    @SerializedName("walk_m") val walkM: Int = 0,
    val status: String? = null             // assigned | picked | dropped
)

/** Точка сбора (stop), сгруппированная по pickup_seq. */
data class BirgaStop(
    val seq: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val type: String? = null,              // door | point
    @SerializedName("children_total") val childrenTotal: Int = 0,
    val pickups: List<BirgaPickup> = emptyList()
)

/** Полный рейс (routeFull) — точки в порядке pickup_seq, финиш — школа. */
data class BirgaRouteFull(
    val id: Int = 0,
    val status: String? = null,
    @SerializedName("vehicle_class") val vehicleClass: String? = null,
    val capacity: Int = 0,
    val children: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0,
    @SerializedName("pickup_mode") val pickupMode: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    val polyline: String? = null,
    val school: BirgaSchool? = null,
    val stops: List<BirgaStop> = emptyList()
)

/** Ответы с полным рейсом: accept / view / start. */
data class BirgaRouteResponse(
    val ok: Boolean = false,
    val route: BirgaRouteFull? = null
)

/** POST /api/driver/routes/{id}/signup (предзапись/интерес). */
data class BirgaSignupResponse(
    val ok: Boolean = false,
    @SerializedName("signed_up") val signedUp: Boolean = false,
    @SerializedName("route_id") val routeId: Int = 0,
    @SerializedName("start_date") val startDate: String? = null,
    val interested: Int = 0
)

/** POST /api/driver/routes/{id}/pickup — либо один ребёнок, либо вся точка. */
data class BirgaPickupResponse(
    val ok: Boolean = false,
    val picked: BirgaPicked? = null,
    @SerializedName("picked_point") val pickedPoint: Int? = null,
    val children: Int? = null
)

data class BirgaPicked(
    @SerializedName("child_id") val childId: Int = 0,
    val seq: Int = 0
)

/** POST /api/driver/routes/{id}/complete (route здесь краткий; billing опущен — не нужен водителю). */
data class BirgaCompleteResponse(
    val ok: Boolean = false,
    val route: BirgaRouteBrief? = null
)

/** POST /api/driver/routes/{id}/track и прочие простые ok-ответы. */
data class BirgaSimpleResponse(
    val ok: Boolean = false,
    val saved: Boolean = false
)
