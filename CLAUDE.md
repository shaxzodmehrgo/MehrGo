# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android driver app for taxi services in Uzbekistan / Kazakhstan / Kyrgyzstan. The current active build target is **Mehrgo Driver** (`uz.teamwork.mehrgodriver`), but the same Kotlin source is white-labeled into ~15 other branded driver apps (Sevimli, Qulay, Yengil, Premium, Rayxon, Karavan, Alo, Barakat, Eco, Golden, Humo, Kirakash, Elga, Tezgo, Ayol). Gradle root project: `TeamworkTaxiDriver`.

- Kotlin 2.0.21, AGP 8.8.0-alpha05, JDK 17
- `minSdk 26`, `compileSdk 35`, `targetSdk 35`
- Single module: `:app`. No tests of substance — only the default `ExampleUnitTest` / `ExampleInstrumentedTest` scaffolding.

## Build & run

The repo uses the Gradle wrapper. On Windows:

```bash
./gradlew.bat assembleDebug          # build debug APK
./gradlew.bat installDebug           # install on connected device
./gradlew.bat assembleRelease        # release APK (minify is OFF — see app/build.gradle)
./gradlew.bat bundleRelease          # AAB for Play
./gradlew.bat clean
./gradlew.bat lint
./gradlew.bat test                   # unit tests
./gradlew.bat connectedAndroidTest   # instrumented tests (needs device/emulator)
```

`local.properties` must define `sdk.dir`. Project requires JDK 17 (set `kotlinOptions.jvmTarget = '17'`).

## Switching the active brand (CRITICAL)

`app/src/main/java/uz/teamwork/mehrgodriver/common/Constants.kt` is **the single source of brand configuration**, structured as commented-out blocks. To switch from Mehrgo to another brand you must:

1. Comment out the active block (currently the Mehrgo block defining `BASE_URL`, `IMAGE_URL`, `BASE_URL_FOR_SOCKET`, `MAPKIT_KEY`, `MERCHANT_*`, `APPLICATION_ID`, `APP_VERSION`, `APP_VERSION_NAME`, `ACTION_SEND_*_BY_BROADCAST`, `WAITING_TIME_TURN_AUTO`, `MIN_WAITING_TIME_SINGLE`, `TELEGRAM_BOT_USERNAME` — empty username = no OTP bot for that brand, bot-open buttons no-op).
2. Uncomment the target brand's block.
3. Adjust `PHONE_NUMBER_SIZE` (UZ/KG=13, KZ=12) and `BASE_URL_ROUTE` (UZ/KG/KZ — only one is active at a time).
4. Update `applicationId`, `versionCode`, `versionName`, and `namespace` in `app/build.gradle` to match.
5. Update package names in `AndroidManifest.xml` `Activity`/`Service`/`provider` declarations and the `FileProvider` `authorities`.

Don't edit broadcast-action strings without keeping `ACTION_SEND_ORDER_DATA_BY_BROADCAST` and `ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST` in sync between Constants.kt and any matching IntentFilter usages.

## Architecture

Clean-architecture layers under `app/src/main/java/uz/teamwork/mehrgodriver/`:

- **`common/`** — Application class (`App`, `@HiltAndroidApp`), `Constants`, helpers, `HeaderInterceptor`, `Localisation`, services, websocket, SharedPrefs singletons, location utilities, models that don't belong in the domain layer (e.g. `MyLocation`, `Distances`).
- **`data/`** — `di/` (Hilt modules: `NetworkModule`, `CommonModule`, `LocaleDatabaseModule`, `repository_module/`), `locale/` (Room `LocaleDatabase` + `CalculationsDao`), `remote/` (`ApiService`, `RouteApiService`, `RetrofitClient`), `repository/` (`AuthRepositoryImpl`, `MainRepositoryImpl`, `RouteRepositoryImpl`, plus a `locale/` sub-repo).
- **`domain/`** — `model/` DTOs, `repository/` interfaces, `use_case/` grouped by feature: `auth/`, `main/`, `route/`, `locale/`. Each use case is a single-responsibility class named `*UC` exposing `operator fun invoke(...)`.
- **`presentation/`** — `activity/splash/FirstActivity` (launcher: version check, blocked-app check) and `activity/main/MainActivity` (bottom nav host). Feature fragments live under `auth/ui/<feature>` and `main/ui/<feature>`. Maps are split: legacy Google Maps in `maps/legacy/`, current Yandex MapKit in `maps/yandex_map/`.

### Networking

- Two Retrofit instances injected via Hilt — the default one for `ApiService` (`Constants.BASE_URL`) and a `@Named("retrofit_route")` one for `RouteApiService` (`Constants.BASE_URL_ROUTE`). All API responses are wrapped in `BaseResponse<T>`.
- `HeaderInterceptor` adds `Authorization: Bearer <authKey>` from `UserManager` and `Accept-Language` from `LanguageManager`. On non-2xx it serializes an `ErrorRequest` into `ErrorRequestManager` SharedPrefs for later upload via `POST user/report`.
- `ChuckerInterceptor` is wired in `NetworkModule` but **commented out** — re-enable for debug HTTP inspection.

### Use case → ViewModel pattern

Use cases return `Flow<Resource<T>>` where `Resource` is the sealed class in `common/Resource.kt` (`Loading` / `Success` / `Error`). The standard error funnel is:

```kotlin
} catch (e: HttpException) {
    val errorResponse = gson.fromJson(e.response()?.errorBody()?.string(), ErrorResponse::class.java)
    emit(Resource.Error(errorResponse.message ?: Helper.getServerError()))
} catch (e: IOException) {
    emit(Resource.Error(Helper.getConnectionError()))
}
```

Match this exact pattern when adding new use cases — UI layers expect `Resource<BaseResponse<T>>` (or sometimes `Resource<T>`) and switch on the three states.

### Realtime: WebSocket + foreground services + LocalBroadcastManager

The driver app's runtime spine is three Android services and a websocket. Order/notification events flow:

1. `MyTrackingService` (foreground, `foregroundServiceType="location"`) opens a websocket to `Constants.BASE_URL_FOR_SOCKET` with `MySocketListener`.
2. `MySocketListener.onMessage` parses `SocketBaseResponse.key` and dispatches:
   - `ORDER_NEW`, `ORDER_NEW_PRIVATE`, `ORDER_ACCEPTED`, `ORDER_CANCELLED`, `ORDER_CANCELLED_PRIVATE` → `LocalBroadcastManager` broadcast on `ACTION_SEND_ORDER_DATA_BY_BROADCAST` with payload key `KEY_ORDER_DATA`.
   - `NOTIFICATION_NEW` → `listenerNotificationData` LiveData.
   - `RECEIVE_PONG` → `listenerPongData` LiveData (heartbeat).
3. `MainActivity` and `MapFragment` register `BroadcastReceiver`s on `ACTION_SEND_ORDER_DATA_BY_BROADCAST` / `ACTION_SEND_SOCKET_LISTENER_BY_BROADCAST` to react in-app.
4. `AutoOfferService` shows an auto-accept dialog overlay (`SYSTEM_ALERT_WINDOW`) when an order arrives while the app is backgrounded; `WindowToAppService` pops a window-to-app shortcut overlay.

Service start/stop is driven by `Intent` actions defined as `Constants.ACTION_*` (`ACTION_START_SERVICE`, `ACTION_START_TRACKING`, `ACTION_START_WAY`, `ACTION_START_TIME_WAIT`, `ACTION_STOP_*`). When extending tracking behavior, route through these actions rather than calling service methods directly — there is no bound-service interface.

### State enums

Order lifecycle and user status are encoded as plain `Int` constants in `Constants.kt`:

- Driver state: `USER_INFO_DELETED=1`, `USER_INFO_COMPLETED=5`, `VERIFY_CODE_CONFIRMED=7`, `DRIVER_INFO_COMPLETED=9`, `DRIVER_ACTIVE=10`, `DRIVER_TURNED_NOT_ACTIVE=11`.
- Order state: `ACCEPTED=2`, `STARTED=7`, `CHANGED_ARRIVED=8`, `CHANGED_GONE=9`.
- Order history status: `CANCELLED=10`, `ACCEPTED=11`, `FINISHED=12`, `DOING=15`.
- Order originator: `CLIENT=25`, `DRIVER=1` (taximeter), `MANAGER=3`, `DISPATCHER=4`, `ADMIN=10`. **Note:** `MANAGER`/`ADMIN` numbering changed historically — the old block is still in Constants.kt as a comment; check both when chasing legacy bugs.

### Storage

- `UserManager`, `LanguageManager`, `ThemeManager`, `MapTypeManager`, `AppTypeManager` (`navigator` vs `list` UI), `IntroduceManager`, `ErrorRequestManager` are `object` singletons over `SharedPreferences`. They must be initialized in `App.onCreate()` before use.
- Room: `LocaleDatabase` (`my_db`, version 2) with a single `Calculation` entity used by the offline taximeter (`UpdateLocationsUC`, `UpdateTrackedTimeUC`, `UpdateWaitedTimeUC`, etc.). `LocationTypeConverter` serializes lists of `LatLng` as JSON. The legacy `data/locale/AppDatabase.kt` is fully commented out — don't bring it back.

### Maps

`MapTypeManager` controls which external map app handles navigation deep-links — `google`, `yandex`, `yandex_navi`, `2gis`, `waze`. The in-app map is **Yandex MapKit** (`com.yandex.android:maps.mobile:4.25.0-lite`) initialized in `App.onCreate` via `MapKitFactory`. `MAPKIT_KEY` is set per brand in `Constants.kt`. The Google Maps API key in `AndroidManifest.xml` is shared across the legacy `maps/legacy` flow.

The blocked-app feature in `FirstActivity` uses `manifest <queries>` (Android 11+ package visibility) plus `Constants.blockAppsInManifest` to detect and block competitor driver apps; the server pushes the blocklist via `mobile/version-driver` (see `UpdateAppUC` and `UpdateApp.blockedApps`). Both lists must be kept in sync with the manifest's `<queries>` block.

### Localization

Four supported languages — `uz` (default `values/`), `kk` (`values-kk/`), `ky` (`values-ky/`), `ru` (`values-ru-rRU/`). Locale is applied **without** `AppCompatDelegate.setApplicationLocales` (on API 33+ with a live Activity it delegates to the framework `LocaleManager`, which relaunches the task from `FirstActivity` — a visible splash flash). Instead `Localisation.wrap()` runs in `attachBaseContext` (App + MainActivity + FirstActivity) and `Localisation.applyToConfig()` re-asserts the locale on the process resources config (so services/overlays follow); the language-change screens then call `Activity.recreate()` for an in-place swap. Persisted in `LanguageManager` (`myLanguagePref`). `bundle.language.enableSplit = false` keeps all translations in every APK split.

## Conventions

- Use cases are named `<Action>UC` and live under `domain/use_case/<feature>/`. Create a corresponding suspend method on the relevant `*Repository` interface and `*RepositoryImpl`.
- New screens are `Fragment`s under `presentation/main/ui/<feature>/` with their own ViewModel; nav graph is `app/src/main/res/navigation/mobile_navigation.xml`. Use `kirich1409:viewbindingpropertydelegate` for view binding, not manual `_binding` patterns (though older code uses both).
- Strings live in `values/strings.xml` — translate to all four locales when adding user-visible text.
- New Retrofit endpoints go in `ApiService` (default base URL) or `RouteApiService` (route-server base URL). Don't add a third Retrofit instance without a strong reason — extend `NetworkModule` if you must.
- Background work that needs to outlive the UI must be expressed as one of the three existing services (`MyTrackingService` for tracking + websocket, `AutoOfferService` for offer overlay, `WindowToAppService` for window-to-app overlay) plus an `ACTION_*` constant. Don't introduce a fourth service casually.
