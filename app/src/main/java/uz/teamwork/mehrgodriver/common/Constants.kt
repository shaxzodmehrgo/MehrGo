package uz.teamwork.mehrgodriver.common

import uz.teamwork.mehrgodriver.common.Constants.EXTRA_REPRICED_ORDER_ID


object Constants {
    const val BASE_URL_ROUTE = "https://route.teamwork.uz/" // Uzbek
//    const val BASE_URL_ROUTE = "https://kgroute.teamwork.uz/" // Kyrgyz
//    const val BASE_URL_ROUTE = "https://kzroute.teamwork.uz/" // Kazakh

//    const val BASE_URL = "https://dashboard.ayoltaxi.uz/api/v1/"
//    const val IMAGE_URL = "https://dashboard.ayoltaxi.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://dashboard.ayoltaxi.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 35839
//    const val MERCHANT_ID_CLICK = 27682
//    const val MERCHANT_ID_PAY_ME = "66a1f85bd69d25572f43e273"
//    const val APPLICATION_ID: String = "uz.teamwork.mehrgodriver"
//    const val APP_VERSION: Int = 1000
//    const val APP_VERSION_NAME: String = "1.0.0"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.mehrgodriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.mehrgodriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = false
//    const val TELEGRAM_BOT_USERNAME = "mehrgo_bot"

    const val BASE = "prod.mehrgo.uz" // "dashboard.ayoltaxi.uz"
    const val BASE_URL = "https://$BASE/api/v1/"
    const val MAKTABGO_BASE_URL = "https://maktabgo.uz/api/" // MaktabGo (С€РєРѕР»СЊРЅС‹Р№ С€Р°С‚С‚Р», РІРЅСѓС‚СЂ. РєРѕРґ-РЅРµР№Рј Birga) вЂ” РѕС‚РґРµР»СЊРЅС‹Р№ Retrofit (retrofit_birga)
    const val IS_MAKTABGO = true // MaktabGo-СЂРµР¶РёРј: HomeFragment СЂРѕСѓС‚РёС‚ РІ OTP-РїРѕС‚РѕРє С€Р°С‚С‚Р»Р° (false = РѕР±С‹С‡РЅРѕРµ С‚Р°РєСЃРёС€РЅРѕРµ РїРѕРІРµРґРµРЅРёРµ)
    const val IMAGE_URL = "https://$BASE"
    const val BASE_URL_FOR_SOCKET = "wss://$BASE/socket"
    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
    const val MERCHANT_SERVICE_ID_CLICK = 35839
    const val MERCHANT_ID_CLICK = 27682
    const val MERCHANT_ID_PAY_ME = "66a1f85bd69d25572f43e273"
    const val APPLICATION_ID: String = "uz.teamwork.mehrgodriver"
    const val APP_VERSION: Int = 71
    const val APP_VERSION_NAME: String = "2.7.1"

    // Brand support line. Fallback for "call the operator" when the driver has no dispatcher
    // number yet вЂ” which is the NORMAL case on the under-review screen, since a driver is not
    // attached to a branch until their documents are approved. Without this the call button
    // was dead on the one screen where the driver most needs to reach someone.
    // Blank = no support line for that brand в†’ the call button hides instead of failing.
    const val SUPPORT_PHONE_NUMBER: String = "+998555161919"
    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String =
        "uz.teamwork.mehrgodriver.WEBSOCKET_ORDER_DATA"
    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String =
        "uz.teamwork.mehrgodriver.WEBSOCKET_SOCKET_LISTENER"
    const val WAITING_TIME_TURN_AUTO = false
    const val MIN_WAITING_TIME_SINGLE = false

    // This brand's Telegram OTP bot USERNAME (no @, no URL) вЂ” the app opens
    // https://t.me/<username> for linking. Empty = no bot for this brand: the bot-open
    // buttons become no-ops and only the backend's bot_url (if any) is used.
    const val TELEGRAM_BOT_USERNAME = "mehrgo_bot"

    /**
     * Android App Link that the Telegram bot's "Buyurtmani qabul qilish" button points at:
     * `https://<DEEPLINK_HOST><DEEPLINK_ORDER_PATH><order_id>` вЂ” e.g.
     * `https://mehrgo.uz/driver/order/80893`. Telegram only allows `https://` (and `tg://`) in
     * inline buttons, so a custom scheme is not an option.
     *
     * PER BRAND, and **duplicated in AndroidManifest.xml** вЂ” the `<data>` element of MainActivity's
     * VIEW filter cannot read a Kotlin constant, so host + pathPrefix have to be changed in BOTH
     * places when switching brands (same ritual as APPLICATION_ID, see CLAUDE.md).
     *
     * The link only actually opens the app once the domain serves
     * `https://<DEEPLINK_HOST>/.well-known/assetlinks.json` carrying this build's release SHA-256 вЂ”
     * until then Android hands the URL to the browser.
     */
    const val DEEPLINK_HOST = "mehrgo.uz"
    const val DEEPLINK_ORDER_PATH = "/driver/order/"

    /**
     * True when the app is pointed at the dev/staging backend (the shared `ayoltaxi` dashboard)
     * instead of a production host. Drives the "DEV MODE" warning on the settings screen so a dev
     * build can't be shipped or advertised by accident. Keep in sync with the env-switch convention
     * (dev = dashboard.ayoltaxi.uz, prod = prod.mehrgo.uz et al.).
     */
    val IS_DEV_SERVER: Boolean
        get() = BASE.contains("ayoltaxi", ignoreCase = true)

//    const val BASE_URL = "https://prod.sevimlitaxi.uz/api/v1/"
//    const val IMAGE_URL = "https://prod.sevimlitaxi.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://prod.sevimlitaxi.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 27859
//    const val MERCHANT_ID_CLICK = 20271
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.sevimlitaxidriver"
//    const val APP_VERSION: Int = 36
//    const val APP_VERSION_NAME: String = "2.5.1"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.sevimlitaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.sevimlitaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://qulay1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://qulay1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://qulay1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.yukladriver"
//    const val APP_VERSION: Int = 11
//    const val APP_VERSION_NAME: String = "2.2.2"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.yukladriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.yukladriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://yengil1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://yengil1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://yengil1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.yengiltaxidriver"
//    const val APP_VERSION: Int = 6
//    const val APP_VERSION_NAME: String = "1.0.6"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.yengiltaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.yengiltaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://premium1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://premium1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://premium1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.premiumtaxidriverkz"
//    const val APP_VERSION: Int = 3
//    const val APP_VERSION_NAME: String = "1.0.3"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.premiumtaxidriverkz.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.premiumtaxidriverkz.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://rayxon1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://rayxon1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://rayxon1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.rayxontaxidriverkg"
//    const val APP_VERSION: Int = 2
//    const val APP_VERSION_NAME: String = "1.0.2"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.rayxontaxidriverkg.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.rayxontaxidriverkg.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://karavan1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://karavan1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://karavan1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 74310
//    const val MERCHANT_ID_CLICK = 40581
//    const val MERCHANT_ID_PAY_ME = "685a74b59e81e69da2e92199"
//    const val APPLICATION_ID: String = "uz.teamwork.karavandriver"
//    const val APP_VERSION: Int = 11
//    const val APP_VERSION_NAME: String = "1.1.1"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.karavandriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.karavandriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://alo1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://alo1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://alo1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.alotaxidriver"
//    const val APP_VERSION: Int = 1
//    const val APP_VERSION_NAME: String = "1.0.0"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.alotaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.rayxontaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://barakat1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://barakat1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://barakat1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.barakattaxidriver"
//    const val APP_VERSION: Int = 5
//    const val APP_VERSION_NAME: String = "1.0.5"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.barakattaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.barakattaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://prod.eco-taxi.uz/api/v1/"
//    const val IMAGE_URL = "https://prod.eco-taxi.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://prod.eco-taxi.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.ecotaxi.driver"
//    const val APP_VERSION: Int = 151
//    const val APP_VERSION_NAME: String = "4.1.1"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.ecotaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.ecotaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://dashboard.goldentaxi.uz/api/v1/"
//    const val IMAGE_URL = "https://dashboard.goldentaxi.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://dashboard.goldentaxi.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.goldentaxidriver"
//    const val APP_VERSION: Int = 3
//    const val APP_VERSION_NAME: String = "1.0.3"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.goldentaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.goldentaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://humo1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://humo1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://humo1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.humotaxidriver"
//    const val APP_VERSION: Int = 5
//    const val APP_VERSION_NAME: String = "1.0.5"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.humotaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.humotaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://kirakashgo1.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://kirakashgo1.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://kirakashgo1.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.kirakashgodriver"
//    const val APP_VERSION: Int = 4
//    const val APP_VERSION_NAME: String = "1.0.4"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.kirakashgodriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.kirakashgodriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = false
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://prod.elgataxi.uz/api/v1/"
//    const val IMAGE_URL = "https://prod.elgataxi.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://prod.elgataxi.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 25197
//    const val MERCHANT_ID_CLICK = 15998
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.elgataxidriver"
//    const val APP_VERSION: Int = 2
//    const val APP_VERSION_NAME: String = "1.0.2"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.elgataxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.elgataxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

//    const val BASE_URL = "https://tezkorgotaxi.teamwork.uz/api/v1/"
//    const val IMAGE_URL = "https://tezkorgotaxi.teamwork.uz"
//    const val BASE_URL_FOR_SOCKET = "wss://tezkorgotaxi.teamwork.uz/socket"
//    const val MAPKIT_KEY = "YOUR_MAPKIT_KEY"
//    const val MERCHANT_SERVICE_ID_CLICK = 0
//    const val MERCHANT_ID_CLICK = 0
//    const val MERCHANT_ID_PAY_ME = ""
//    const val APPLICATION_ID: String = "uz.teamwork.tezgotaxidriver"
//    const val APP_VERSION: Int = 6
//    const val APP_VERSION_NAME: String = "1.0.6"
//    const val ACTION_SEND_ORDER_DATA_BY_BROADCAST: String = "uz.teamwork.tezgotaxidriver.WEBSOCKET_ORDER_DATA"
//    const val ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST: String = "uz.teamwork.tezgotaxidriver.WEBSOCKET_SOCKET_LISTENER"
//    const val WAITING_TIME_TURN_AUTO = true
//    const val MIN_WAITING_TIME_SINGLE = true
//    const val TELEGRAM_BOT_USERNAME = ""

    // Blocking apps
    val blockAppsInManifest = listOf(
        "uz.gotaxi.margilan.driver",
        "uz.bo1056.driver",
        "uz.onlinetaxi.driver",
        "uz.onlinetaxi.taxi1313.driver",
        "taximaster.tmtaxicaller.id3265",
        "ru.yandex.taximeter",
        "uz.promo.uzDriver",
        "uz.royaltaxi.driver",
        "ru.tmdriver.new"
    )

    // Others
    const val WAIT_TIME_VERIFY_CODE = 60
    const val TIME_DATE_INTERVAL = 50L
    const val MINIMAL_SPEED = 7

    // Drop location fixes that come from a mock provider (fake-GPS apps). Turned OFF so
    // simulated routes can be tested on release builds too; flip to true to restore the
    // block. Debug builds never enforced it.
    const val BLOCK_MOCK_LOCATIONS = false

    // Max speed (km/h) at which the driver may START paid waiting manually вЂ”
    // above this the car is still rolling, so we ask them to stop first (iOS parity).
    const val ON_ROUTE_WAIT_MAX_START_SPEED_KMH = 10

    // At the pickup, if the car pulls away above this speed (km/h) the ride
    // auto-starts вЂ” the rider boarded and the driver forgot to slide "Kettik".
    const val AUTO_START_RIDE_SPEED_KMH = 20

    // Driver must be within this many metres of the pickup point to mark "arrived".
    const val ARRIVE_PICKUP_RADIUS_M = 500

    // Driver within this many metres of the FINAL destination в†’ "finish the order" notice.
    const val FINISH_DESTINATION_RADIUS_M = 100

    // While the client-wait meter is running, if the car moves faster than this
    // (km/h) it's no longer waiting вЂ” the meter auto-stops (driver may restart it).
    const val WAIT_AUTO_STOP_SPEED_KMH = 20
    const val DEFAULT_ACCEPT_WAIT_TIME = 20
    const val KEY_ORDER_DATA = "order_data"
    const val KEY_SOCKET_LISTENER = "socket_listener"

    // Order service
    const val REMOVE_ORDER_SERVICE = 0
    const val ADD_ORDER_SERVICE = 1

    // Validate
    // --- Auth delivery channel (SMS / Telegram OTP) ---------------------------------
    // The confirmation code can be delivered by SMS or Telegram. "telegram" в†’ Telegram if the
    // number is linked to the bot, else the backend SMS-falls-back (never dead-ends); "sms" в†’
    // always SMS. Backend accepts `channel` on register/refresh/recover.
    const val CHANNEL_SMS = "sms"
    const val CHANNEL_TELEGRAM = "telegram"

    // NOTE: TELEGRAM_BOT_USERNAME is PER-BRAND вЂ” it lives in the brand config blocks above.

    // Resend cooldowns: SMS uses the server's waiting_time (falls back to this); Telegram is
    // instant/free, so a much shorter cooldown.
    const val DEFAULT_SMS_WAITING_TIME = 60
    const val TELEGRAM_RESEND_WAITING_TIME = 20

    const val PHONE_NUMBER_SIZE = 13 // Uzbek

    //    const val PHONE_NUMBER_SIZE = 12 // Kazakh
//    const val PHONE_NUMBER_SIZE = 13 // Kyrgyz
    const val VERIFICATION_CODE_SIZE = 4
    const val PASSWORD_SIZE = 5

    // Theme
    const val THEME_DAY = "day"
    const val THEME_NIGHT = "night"
    const val THEME_SYSTEM = "system"

    // Language
    const val LANGUAGE_UZBEK = "uz"
    const val LANGUAGE_KAZAKH = "kk"
    const val LANGUAGE_KYRGYZ = "ky"
    const val LANGUAGE_RUSSIAN = "ru"

    // Map Type
    const val GOOGLE = "google"
    const val YANDEX = "yandex"
    const val YANDEX_NAVI = "yandex_navi"
    const val TWO_GIS = "2gis"
    const val WAZE = "waze"

    // Socket
    const val MINIMAL_TIME_CONNECT_SOCKET = 10_000L
    const val WEB_SOCKET_CLOSE_CODE = 1000

    // Consecutive missed app-level pongs tolerated before the socket is declared dead and
    // reconnected. Was effectively zero вЂ” a single lost/slow pong (common on mobile networks)
    // dropped + reconnected the socket every ~10s and flashed the "reconnecting" banner.
    const val MAX_MISSED_SOCKET_PONGS = 3

    const val ORDER_NEW_PRIVATE = "order_new_for_nurse"
    const val ORDER_NEW = "order_new"
    const val ORDER_ACCEPTED = "order_accepted"
    const val ORDER_CANCELLED = "order_cancelled"
    const val ORDER_CANCELLED_PRIVATE = "order_cancelled_for_nurse"

    // Settlement frame: full Order with the SERVER-judged final price (the complete REST
    // response is a bare user object вЂ” this frame is the only client-visible receipt).
    const val ORDER_COMPLETED = "order_completed"
    const val NOTIFICATION_NEW = "notification_new"
    const val RECEIVE_PONG = "pong"

    // Server-side fare socket keys (see MOBILE.md В§2.2 / В§6).
    const val ORDER_GPS_BATCH = "order_gps_batch"
    const val ORDER_PRICE_UPDATED = "order_price_updated"

    // Service actions
    const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
    const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
    const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
    const val ACTION_START_MAKTABGO_TRACKING = "ACTION_START_MAKTABGO_TRACKING"
    const val ACTION_STOP_MAKTABGO_TRACKING = "ACTION_STOP_MAKTABGO_TRACKING"
    const val ACTION_START_WAY = "ACTION_START_WAY"
    const val ACTION_START_TIME_WAIT = "ACTION_START_TIME_WAIT"
    const val ACTION_ARRIVED_AT_PICKUP = "ACTION_ARRIVED_AT_PICKUP"
    const val ACTION_STOP_TIME_WAIT = "ACTION_STOP_TIME_WAIT"

    // Window Service actions
    const val ACTION_START_WINDOW_SERVICE = "ACTION_START_WINDOW_SERVICE"
    const val ACTION_STOP_WINDOW_SERVICE = "ACTION_STOP_WINDOW_SERVICE"

    // Window Service actions
    const val ACTION_START_ROUTE_INSTRUCTION_SERVICE = "ACTION_START_ROUTE_INSTRUCTION_SERVICE"
    const val ACTION_STOP_ROUTE_INSTRUCTION_SERVICE = "ACTION_STOP_ROUTE_INSTRUCTION_SERVICE"

    // Navigate actions
    const val ACTION_SHOW_MY_TRACKING_FRAGMENT = "ACTION_SHOW_MY_TRACKING_FRAGMENT"
    const val ACTION_SHOW_DIALOG_ORDER_ACCEPT = "ACTION_SHOW_DIALOG_ORDER_ACCEPT"
    const val ACTION_SHOW_DIALOG_INSIDE_APP = "ACTION_SHOW_DIALOG_INSIDE_APP"

    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Tracking"
    const val NOTIFICATION_ID = 1

    const val NOTIFICATION_CHANNEL_ID_NEW_PRIVATE_ORDER = "new_private_order_channel"
    const val NOTIFICATION_CHANNEL_NAME_NEW_PRIVATE_ORDER = "Individual buyurtma"

    const val NOTIFICATION_CHANNEL_ID_NEW_ORDER = "new_order_channel"
    const val NOTIFICATION_CHANNEL_NAME_NEW_ORDER = "Yangi buyurtma"

    const val NOTIFICATION_CHANNEL_ID_CANCEL_MY_ORDER = "my_order_cancel_channel"
    const val NOTIFICATION_CHANNEL_NAME_CANCEL_MY_ORDER = "Bekor qilingan buyurtma"

    const val NOTIFICATION_CHANNEL_ID_NEW_NOTIFICATION = "new_notification_channel"
    const val NOTIFICATION_CHANNEL_NAME_NEW_NOTIFICATION = "Bildirishnomalar"

    // User state
    const val USER_INFO_DELETED = 1
    const val USER_INFO_COMPLETED = 5
    const val VERIFY_CODE_CONFIRMED = 7
    const val DRIVER_INFO_COMPLETED = 9
    const val DRIVER_ACTIVE = 10
    const val DRIVER_TURNED_NOT_ACTIVE = 11

    // Order history status
    const val ORDER_HISTORY_CANCELLED = 10
    const val ORDER_HISTORY_ACCEPTED = 11
    const val ORDER_HISTORY_FINISHED = 12
    const val ORDER_HISTORY_DOING = 15

    // Order state
    const val ORDER_STATE_ACCEPTED = 2
    const val ORDER_STATE_STARTED = 7
    const val ORDER_STATE_CHANGED_ARRIVED = 8
    const val ORDER_STATE_CHANGED_GONE = 9

//    // Who created the order
//    const val ORDER_CREATED_CLIENT = 25
//    const val ORDER_CREATED_MANAGER = 10
//    const val ORDER_CREATED_DISPATCHER = 4
//    const val ORDER_CREATED_DRIVER = 1 // Tachometer

    // Who created the order
    const val ORDER_CREATED_CLIENT = 25
    const val ORDER_CREATED_DRIVER = 1 // Tachometer
    const val ORDER_CREATED_MANAGER = 3
    const val ORDER_CREATED_DISPATCHER = 4
    const val ORDER_CREATED_ADMIN = 10

    // Videos
    const val VIDEO_MY_BALANCE = "mening_balansim"
    const val VIDEO_SIGN_UP = "registratsiya"
    const val VIDEO_PROFILE = "profil_oynasi"
    const val VIDEO_ORDERS = "buyurtmalar"
    const val VIDEO_MY_ORDERS = "mening_buyurtmalarim"
    const val VIDEO_CHAT = "chat_oynasi"
    const val VIDEO_OPERATOR = "operator_bilan_aloqa"
    const val VIDEO_ORDER_ACCEPTED = "zakaz_olingan_oynasi"
    const val VIDEO_PASSWORD_RECOVERY = "parolni_tiklash"
    const val VIDEO_ORDERS_IN = "buyurtmalar_ichki"
    const val VIDEO_ADDITIONAL = "qoshimcha"

    // Payment
    const val CLICK = "click"
    const val PAY_ME = "pay_me"

    // Errors
    const val ERROR_UNAUTHORIZED = "401"

    // FCM push payload keys (data.key)
    const val FCM_KEY_DRIVER_BONUS_CREDITED = "DRIVER_BONUS_CREDITED"
    const val FCM_KEY_NEW_DRIVER_NOTIFICATION = "NEW_DRIVER_NOTIFICATION"
    const val FCM_KEY_DOCUMENT_APPROVED = "DOCUMENT_APPROVED"
    const val FCM_KEY_DOCUMENT_REJECTED = "DOCUMENT_REJECTED"
    const val FCM_KEY_DATA_BALANCE_ID = "balance_id"
    const val FCM_KEY_DATA_NOTIFY_DRIVER_ID = "notify_driver_id"
    const val FCM_KEY_DATA_DOCUMENT_TYPE = "type"

    // Paylov withdrawal decision push arrives under data.type (not data.key) вЂ”
    // MOBILE (3).md В§5: { type: "paylov_withdrawal", request_id, status "1"/"2" }.
    const val FCM_TYPE_PAYLOV_WITHDRAWAL = "paylov_withdrawal"

    // Intent extras for push-tap deep links into MainActivity
    const val EXTRA_PUSH_KEY = "extra_push_key"
    const val EXTRA_BALANCE_ID = "extra_balance_id"
    const val EXTRA_NOTIFY_DRIVER_ID = "extra_notify_driver_id"

    // In-process LocalBroadcast telling an open verification screen to re-fetch
    // (fired when a DOCUMENT_APPROVED/REJECTED push arrives).
    const val ACTION_VERIFICATION_STATUS_CHANGED = "verification_status_changed"

    /** FCM data `type` the backend sends when it reprices a live order (client toggled a
     *  service, surge applied, вЂ¦). Payload: order_id, price, reason, surcharge. */
    const val FCM_TYPE_ORDER_PRICE_RECALCULATED = "order_price_recalculated"

    /** In-process LocalBroadcast telling the open trip screen that the server repriced the
     *  order, so it should re-pull the fare. Carries [EXTRA_REPRICED_ORDER_ID]. */
    const val ACTION_ORDER_PRICE_RECALCULATED = "order_price_recalculated_local"
    const val EXTRA_REPRICED_ORDER_ID = "repriced_order_id"
}
