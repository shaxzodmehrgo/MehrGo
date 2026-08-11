# MehrGo Client App — iOS Feature Parity Specification

> **Purpose of this document.** This file is the source of truth for behaviour and features of the **Android** client app (package `uz.teamwork.mehrgo`, version 6.2.2 / versionCode 54). The iOS app is behind on functionality and needs to be brought to parity.
>
> **Workflow for the iOS Claude Code agent:**
> 1. Read this document end-to-end.
> 2. Audit the iOS project and produce a delta: what already exists, what is missing, what is partially implemented.
> 3. Implement the missing items. Keep the iOS design system untouched — colours, fonts, spacing, iconography stay as they are on iOS. Only behaviour / features / screens / data flows from this document should be added or aligned.
> 4. Where iOS has a more idiomatic equivalent (e.g. `UITextContentType.oneTimeCode` for SMS auto-fill instead of Android's `SmsRetriever`), use the iOS-native API. Do not port the Android implementation literally.
>
> **Out of scope:** visual design, colour palette, typography, asset reskinning. The iOS app already has its own style — preserve it.

---

## 0. New UI Features That Must Be Added (Explicit Asks)

These three behaviours are required additions and must be implemented even if no equivalent exists today on iOS:

1. **Phone-number field auto-focus + keyboard auto-show on login screen open.**
   - The phone number `UITextField` must call `becomeFirstResponder()` as soon as the login screen appears, and the keyboard must be visible without the user tapping. Trigger from `viewDidAppear` (not `viewDidLoad`) so the transition animation does not eat the focus event.
   - Keep input type = phone pad. Mask format: `+998 99 XXX XX XX` (9 digits after the `+998` prefix).
   - Once the user has typed all 9 digits, automatically dismiss the keyboard (`resignFirstResponder()`).

2. **OTP auto-fill from incoming SMS.**
   - Use iOS native `UITextContentType.oneTimeCode` on the OTP input. iOS surfaces the code in the QuickType bar automatically — no library, no permissions, no manual SMS parsing.
   - When the field becomes fully filled (4 digits in the current Android flow — confirm with the actual count rendered server-side, see §3.2), automatically submit the verification request. **Guard against double-submission**: if a submit is already in-flight, ignore the auto-trigger. After a failed verification, clear the guard so the user can re-edit and retry.

3. **User agreement checkbox on the Login (phone number) screen.**
   - A checkbox + label "I agree to the User Agreement" sits above (or beside) the Submit button.
   - The Submit button is **disabled** until the agreement is accepted (visually dimmed + tap shows the agreement sheet).
   - Tapping the checkbox row (when unchecked) opens a modal bottom sheet containing the agreement text. Tapping it again (when checked) un-checks it without opening the sheet.
   - The agreement sheet:
     - Fetches its body text from `GET /api/v1/license/index?type=client` (response is `{ data: { text: "..." } }` — same endpoint the driver app uses).
     - Shows a loading state while fetching, an error state with a Retry button if the request fails.
     - **The "Accept" button is disabled until the user has scrolled the agreement text to the bottom** (legally meaningful "I read it" signal). For content short enough to fit without scrolling, enable Accept on first layout pass.
     - Shows a "scroll down" affordance (chevron / button) while there is more content below the fold; it disappears once the user reaches the end.
     - Has an "Accept" and a "Decline" button. Accept → mark agreement accepted, dismiss sheet, enable Submit. Decline → just dismiss, leave checkbox unchecked.
   - Persist accepted state **in-memory for the current session only** (do not store across launches — the Android side re-prompts on each fresh sign-in).

The remainder of this document is the full behavioural spec for the app.

---

## 1. App Metadata

| Field | Value |
|---|---|
| Package / App ID | `uz.teamwork.mehrgo` |
| Version name | 6.2.2 |
| Version code | 54 |
| Min Android | 7.0 (API 24) — for iOS, match the project's existing minimum |
| Production API base | `https://prod.mehrgo.uz/api/v1/` |
| Payments API base | `https://prod.mehrgo.uz/api/paylov/` |
| Routing API base | `https://route.teamwork.uz/` |
| WebSocket URL | `wss://prod.mehrgo.uz/socket?token={authKey}` |

This is a ride-hailing / taxi client app (Uzbekistan / CIS region). Supported phone country: **+998 (Uzbekistan)** by default; code in the repo also references **+7 (Kazakhstan)** and **+996 (Kyrgyzstan)** for future activation but they are currently commented out on Android.

---

## 2. Navigation Map

Start destination: `CheckVersionFragment` (a splash that calls `GET /mobile/version`, then decides where to go).

```
CheckVersion
   │
   ├─ first launch ──► Language → Introduce (intro carousel) → SignIn
   ├─ not logged in ─► SignIn
   └─ logged in ─────► Home (main tab)
```

Authenticated section uses a single host with a bottom-tab / drawer-style entry to:

- **Home** (map + order creation)
- **My Orders** (active orders) / **Order History** (paginated past orders)
- **Settings** (profile, payment methods, addresses, language, logout, support links)

Map of every screen:

| Screen | Purpose |
|---|---|
| CheckVersion | Version gate / startup router |
| Language | Pick language (uz/ru/en/kk/ky) |
| Introduce | Onboarding slide carousel, fetched from `GET /slider/index?id=0` |
| SignIn | Phone-number entry + agreement checkbox |
| SignInVerify | 4-digit OTP entry with SMS auto-fill + resend countdown |
| SignUp | First name, last name, gender (for first-time users only) |
| Home | Map + tariff / address / service / promo / payment / comment selection |
| SelectLocation (home) | Full-screen map picker for start/finish |
| OrderCreated | Live tracking of an active order (driver location, state, ETA) |
| MyOrders | List of active/in-progress orders |
| MyOrdersHistory | Paginated history of completed/cancelled orders (Paging 3 on Android) |
| Settings | Root of profile section |
| EditProfile | Change first name / last name / gender |
| PaymentTypes | Pick / manage payment method (cash + saved cards) |
| AddCard | Enter card number / expiry / CVV |
| AddCardConfirm | 4-digit OTP confirmation for the new card |
| DeleteCard | Remove a saved card |
| MyAddresses | List saved addresses (Home / Work / Other) |
| MyAddress | View a saved address |
| AddMyAddress | Create a new saved address |
| EditMyAddress | Edit an existing saved address |
| SelectLocationAddAddress | Map picker for an address record |

`MyTestFragment` exists in the repo but is internal/debug — do not port.

---

## 3. Authentication & Onboarding Flow

### 3.1 Phone-number entry (SignIn)

1. On screen appearance, request runtime permissions in this order: `POST_NOTIFICATIONS` (iOS: request notification authorization), location When-In-Use. On Android the GPS-enabled prompt is also shown — on iOS this is implicit through `CLLocationManager` authorization.
2. Phone field auto-focuses + keyboard shows automatically (see §0.1).
3. Mask: `+998 99 XXX XX XX`. Only allow Uzbek 9-digit input. (Code path for +7 / +996 exists but is currently disabled; do not enable on iOS until backend confirms.)
4. When 9 digits are entered, keyboard auto-dismisses.
5. Submit is gated on the agreement checkbox being checked (see §0.3).
6. Submit → `POST /client/register` with `{ phone_number: "+998..." }`. Response includes:
   - `auth_key` — opaque token to be passed back on OTP verification
   - `waiting_time` — seconds the user must wait before resend is allowed (defaults to `DEFAULT_SMS_WAITING_TIME` if absent — current default = 60 s)
7. Navigate to SignInVerify with `(phoneNumber, authKey, waitingTime)`.

### 3.2 OTP verify (SignInVerify)

1. OTP field is a 4-digit boxed input (the Android app uses the `aabhasr/otp-view` library — on iOS use a single hidden `UITextField` with `keyboardType = .numberPad` and `textContentType = .oneTimeCode`, then render 4 styled boxes that mirror its `text` value).
2. OTP field auto-focuses on appearance.
3. SMS auto-fill: iOS will surface incoming OTP via the QuickType bar (see §0.2). When the field reaches 4 digits — whether via auto-fill, paste, or manual typing — auto-submit, guarded against double-fire.
4. Submit → `POST /client/confirm` with:
   - `auth_key` (from SignIn response)
   - `code` (the OTP)
   - `fcm_token` (the device's push token — see §5.2)
   - `language` (uz/ru/en/kk/ky — current selection from local storage)
5. Response is the user object. Persist it (see §6.1).
   - If `data.firstName == "Client"`, the user is new → navigate to SignUp.
   - Otherwise → navigate to Home.
6. Below the OTP field: a "Send SMS again" chip. Disabled and ticking down `mm:ss · Send SMS again` until the countdown reaches 0, then becomes tappable. Tap → `POST /client/register` again with the same phone, refresh `auth_key` + `waiting_time`, restart timer.
7. Title text shows: "Code sent to {formatted phone number}". Use the same `Helper.formatPhoneNumber` semantics (e.g. `+998 99 123 45 67`).
8. The phone-number entry on the previous screen is **not** editable from the verify screen — the user must back-navigate to change it.

### 3.3 First-time sign-up (SignUp)

Only shown when `firstName == "Client"` on confirm response.
- Fields: first name (required), last name (required), gender (radio Male/Female, required).
- Submit → `POST /client/fill-data` with `{ firstName, lastName, gender }`.
- On success → navigate to Home, replacing the auth stack.

### 3.4 Logout

From Settings → Logout. Clears the user object, FCM token mapping, saved-card cache, in-memory `SharedData`. Returns to SignIn. The selected language is preserved.

---

## 4. Main Features

### 4.1 Home (order creation)

Layout: interactive map (not full-screen — bottom area is the order-building UI) showing the user's current position. Default centre if no permission/location: Tashkent (lat `41.313`, lon `69.274`).

**Order-building UI** (driven by modal sheets):

| Sheet | Trigger | Content |
|---|---|---|
| Finish location | Tap the "where to?" field | List of saved addresses + recent + search bar; tap → set finish |
| Map picker | "Pick on map" from inside finish sheet | Full-screen map; centred pin, address resolved via reverse geocode |
| Tariff selection | After start+finish known | Tariff groups (`/order-new/calculate` returns grouped tariffs); pick one |
| Tariff info | Info button on a tariff row | Description sheet |
| Services | "Add services" button | Checkbox list of add-ons (e.g. child seat, extra bags); fetched from `/service/order` |
| Promo code | "Promo code" entry | Text input + Apply; success/error toast |
| Payment method | Payment chip | List of saved cards + Cash; tap → set; "Add card" navigates to AddCard |
| Comment | "Note for driver" | Free-text input |
| AD / Promotion | Auto-show on home open | Promotional dialog (server-driven) |
| No service available | Error state | When server has no driver/tariff for the route |
| Location permission | If denied | CTA → system settings |

**Address resolution.** As the user pans the map for start/finish, do reverse-geocoding via `GET /elasticsearch/send?lat=&lon=` (returns a display name). Search-by-text uses `GET /elasticsearch/by-address?text=`.

**Pricing.** Each time start, finish, tariff, services, promo, bonus, or payment changes, call `POST /order-new/calculate` with the full current selection. Display the returned estimate.

**Bonus.** The user has a bonus balance returned in `/client/me`. Home shows it; a toggle on the order builder applies bonus credit to the current order (recalculates price).

**Create order.** `POST /order-new/create` with the assembled payload. On success, navigate to OrderCreated with the new order id.

### 4.2 OrderCreated (active-order tracking)

Full-screen map dominates. Bottom card shows order state and driver info.

**Order states** (received via WebSocket events and reflected by the UI):

- `new` — just created, searching for driver
- `searching` — searching driver (Android `state_given_near_driver` is the "offered to a driver" event)
- `offered` — a specific driver has been offered the order
- `accepted` — driver accepted, en route to pickup
- `started` — passenger in the car, trip in progress
- `arrived` — driver arrived at finish
- `done` (completed) — trip ended, prompts for rating
- `cancelled` — order ended without completion

**Map behaviour:**
- Start + finish markers always rendered.
- Driver marker updates from WebSocket `driver_location` events (lat / lon / bearing) with smooth animation. Camera auto-follows the driver while in `accepted` / `started` states.
- Polyline route drawn between relevant endpoints depending on state (pickup leg before accept; trip leg after start) — driver-app's routing data is fetched via the Direction API host.
- Nearby-drivers visualisation while searching (small "ghost" pins, if the backend provides them).

**Bottom card content:**
- Driver name, rating, completed-trips count, avatar
- Car: model, colour, licence plate
- Call button → opens dialer with the driver's number
- ETA / current distance
- Live price (updates as the trip progresses)
- Current services attached to the order

**Actions while the order is live:**
- Change payment method (sheet → `GET /order/change-order-card?orderId=&cardId=`)
- Add / remove services (sheet → `POST /order/item`)
- Cancel order (sheet → reason picker from `GET /order-cancel-issue?type=2` → `POST /order/cancel`)
- After state = `done`: rating sheet appears (stars 1–5 + optional comment) → `POST /order/rate`

**Foreground service / background tracking.** On Android, the WebSocket runs in a foreground service with a sticky notification so the connection survives going to background. On iOS, use one of:
- Silent push (`content-available`) to wake the app for state updates, and
- A short-lived `URLSessionWebSocketTask` that runs while the app is foreground/active, reconnecting on resume.

Do **not** request `audio` or `location` background modes just to keep a socket alive — Apple will reject the build. Coordinate with backend on whether silent push notifications cover the state-change events the user needs to see immediately.

### 4.3 My Orders (active list)

Map view + a list/card stack of orders currently in non-terminal states. Tap a card → OrderCreated for that order.

### 4.4 Order History

Paginated list of past orders. On Android this uses Paging 3 with infinite scroll, pull-to-refresh, load-state footers (loading / error with retry / end-of-pagination). On iOS, replicate with a `UITableView`/`UICollectionView` paging by `page` parameter on `GET /order/history?page=`. Implement:
- Pull to refresh
- Infinite scroll (trigger next page at ~80% scroll)
- Empty state when the first page is empty
- Error state with retry on a failed page load

Tap a row → order detail view (currently shown via the same `OrderCreated`-style UI, read-only for historical orders).

### 4.5 Payment methods

**PaymentTypes screen.**
- Lists "Cash" (always present, selected by default for new users) + every saved card from `GET /user-card/cards`.
- Tapping a row marks it as selected and persists the selection.
- "Add card" CTA at the bottom → AddCard.
- Long-press / swipe → delete (DeleteCard flow).

**Add card flow.**
1. AddCard: card number (validates Humo + Uzcard prefixes), expiry `MM/YY`, optional cardholder name. Submit → `POST /user-card/create` returns `card_id` + a flag indicating OTP is required.
2. AddCardConfirm: 4-digit OTP sent by the card issuer to the card's registered phone number. Same auto-fill semantics as login OTP (see §0.2). Submit → `POST /user-card/confirm` with `card_id` + `code`.
3. On success, navigate back to where the user came from: PaymentTypes (if added from settings), OrderCreated (if added mid-order), or Home (if added pre-order).

**Delete card.** `DELETE /user-card/delete?cardId=` with a confirmation dialog. Removes from local cache on success.

**Card storage.** On Android the saved-card cache is in `EncryptedSharedPreferences`. On iOS, store in the **Keychain** (never `UserDefaults`). Only store the masked card number, expiry, internal `cardId`, and which card is currently selected — never the full PAN or CVV.

### 4.6 Saved addresses

**MyAddresses.** Lists every saved address (Home, Work, Other types — type ids 1/2/3 respectively). Empty state if none.

**AddMyAddress.** Label (e.g. "Mom's place"), type picker, then "Pick location" → SelectLocationAddAddress map picker. Submit creates the address record.

**EditMyAddress.** Same fields, pre-populated. Save replaces, "Delete" removes.

These saved addresses surface in the Home order-builder's finish-location sheet for one-tap selection.

### 4.7 Profile / Settings

Top of Settings shows: user avatar (placeholder for now — there is no avatar upload screen, do not invent one), full name, internal user id.

Rows:
- Edit profile
- Order history
- Payment methods
- My addresses
- Language (opens language picker; calls `GET /user/change-language?language=` then triggers locale change)
- Support / News / About — open external URLs (`NEWS_URL`, `ABOUT_US_URL` constants in `Constants.kt`)
- Logout

**Language change.** Five locales: `uz`, `ru`, `en`, `kk`, `ky`. The selection is persisted locally and sent to the backend. On iOS, swap the app locale by re-loading the active strings bundle and rebuilding the UI on the current key window (or use `Bundle.setLanguage` swizzling if your existing iOS code already does it — keep the existing pattern).

---

## 5. Background Services & Integrations

### 5.1 WebSocket (real-time order updates)

- URL: `wss://prod.mehrgo.uz/socket?token={authKey}` — `authKey` from the persisted user object.
- Library on Android: OkHttp WebSocket (raw, **not** Socket.IO). Messages are JSON strings.
- Lifecycle: open when an active order exists, close after the order reaches a terminal state.
- Client → server: send `PING` text frame every 10 seconds.
- Server → client message types observed:
  - `PONG` — keep-alive reply
  - `driver_location` — `{ lat, lon, bearing }` updates
  - `state_given_near_driver` — order offered to a driver
  - `state_*` — order state transitions (matches the states in §4.2)
- Parse defensively (try/catch around each frame) — malformed messages must not kill the connection.
- On iOS: use `URLSessionWebSocketTask`. Reconnect with exponential backoff on disconnect while an order is still active. Stop on `done` / `cancelled`.

### 5.2 Push notifications

- Provider: Firebase Cloud Messaging (cross-platform — iOS can use Firebase Messaging on top of APNs, which is what the project already does on Android).
- The FCM token is fetched during SignInVerify and sent on `POST /client/confirm`. If the token rotates (delegate callback), POST it to the backend immediately. The Android app stores the latest token in a manager class; iOS should mirror this.
- Notification channel on Android is `IMPORTANCE_HIGH` (heads-up); on iOS request `.alert + .sound + .badge` authorization, foreground presentation options = `.banner + .sound + .badge`.
- Notification payload kinds (from observed behaviour): order state changes, driver-accepted alerts, promotional pushes, system messages. The payload's `title` and `body` are displayed; tapping opens the app to the relevant screen (deep linking by order id if present in the data payload — verify with backend).

### 5.3 Maps & location

- Maps: Google Maps Android SDK on Android. **For iOS, prefer the Google Maps SDK for iOS** to keep tiles / styling identical to the Android app — switching to MapKit is allowed but introduces visual differences (handle as a design choice for the iOS team; the existing iOS app's choice should be preserved).
- Location: `FusedLocationProviderClient` on Android with `PRIORITY_HIGH_ACCURACY`. On iOS use `CLLocationManager` with `desiredAccuracy = kCLLocationAccuracyBest`, `requestWhenInUseAuthorization()`.
- Reverse geocoding goes through the backend (`/elasticsearch/send`), **not** Apple's `CLGeocoder` — keep the backend call so display names match.

### 5.4 Routing

- Direction API host (`route.teamwork.uz`) returns polyline + distance + duration between two points. Used for the route line drawn on the order-tracking map.

---

## 6. Local Persistence

### 6.1 What is stored

| Data | Android storage | iOS equivalent |
|---|---|---|
| User object (id, firstName, lastName, authKey) | `EncryptedSharedPreferences` ("mySecureUserPref" / "my_user_key") | **Keychain** |
| FCM token | Encrypted prefs (FcmTokenManager) | Keychain or `UserDefaults` (token isn't sensitive but lives near auth — Keychain is fine) |
| Selected language | Plain SharedPreferences (LanguageManager) | `UserDefaults` |
| Has-seen-intro flag | Plain SharedPreferences (IntroduceManager) | `UserDefaults` |
| Saved cards cache | Encrypted prefs (CardManager) — masked number, expiry, cardId, isSelected | Keychain |
| Saved locations cache | Encrypted prefs (SavedLocationManager) | Keychain or `UserDefaults` (not sensitive) |
| In-flight session data (current order draft, selected start/finish, bonus, promo) | In-memory singleton `SharedData` | Equivalent in-memory holder / app coordinator |

**No SQLite / Room / Core Data is used.** All persistent data is key/value. Do not introduce a database on iOS.

### 6.2 What is **not** stored

- Full card PAN, CVV.
- The agreement-accepted flag (in-memory only, re-prompts every fresh sign-in).
- Order data (re-fetched from the backend on every relevant screen).

---

## 7. Permissions

### 7.1 Manifest / Info.plist requirements

| Android permission | iOS equivalent |
|---|---|
| `POST_NOTIFICATIONS` | `UNUserNotificationCenter.requestAuthorization` |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | `NSLocationWhenInUseUsageDescription` in Info.plist + `CLLocationManager.requestWhenInUseAuthorization()` |
| `INTERNET` | (implicit on iOS) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Background modes: **avoid abusing**; see §4.2 — prefer silent push |

### 7.2 Runtime request points

- **SignIn screen first appearance**: notifications + location When-In-Use.
- **Home screen**: re-prompt location if denied, show a "we need location to find drivers near you" sheet with a CTA to system settings.
- **Add card OTP**: no permissions needed — `oneTimeCode` does not require any.

---

## 8. Localization

Five locales: `uz` (default), `ru`, `en`, `kk`, `ky`.

- All user-facing strings should be looked up by key. The Android side stores them in `res/values{-ru,-en,-kk,-ky}/strings.xml` — the iOS side should mirror the same string ids in `Localizable.strings`. Use the existing iOS string ids where they already exist; for new strings (agreement checkbox, agreement sheet, scroll-to-continue, etc.), add new keys to all five locales. **Ask the user for translations** rather than guessing — only English and Russian can be written without confirmation; uz/kk/ky should be confirmed with the user before shipping.
- Language change must:
  - Update local store (`UserDefaults` key)
  - Send `GET /user/change-language?language=`
  - Reload UI in the new language without restarting the app

---

## 9. API Surface

> Base: `https://prod.mehrgo.uz/api/v1/` unless noted.
> Every successful response is wrapped as `{ data: <T>, status: number, message?: string }`. HTTP 401 → force re-login (clear user + navigate to SignIn).

### Auth & user
- `POST /client/register` — start sign-in, returns `{ authKey, waitingTime }`
- `POST /client/confirm` — submit OTP, returns the user object
- `POST /client/fill-data` — first-time profile or edit profile
- `GET  /client/me` — current user (includes bonus balance)
- `GET  /user/change-language?language=` — persist language server-side

### Onboarding
- `GET /slider/index?id=0` — intro slides
- `GET /license/index?type=client` — user-agreement text (driver app uses the same endpoint with a different type)

### Orders
- `POST /order-new/calculate` — price estimate
- `POST /order-new/create` — create an order
- `GET  /order/history?page=` — paginated history
- `GET  /order/cancel-issue?type=2` — fetch cancel reasons (note: actual path is `/order-cancel-issue?type=2` per the repo — confirm against `ApiService.kt`)
- `POST /order/cancel` — cancel an active order
- `POST /order/rate` — submit rating + comment
- `GET  /order/change-order-card` — change the payment method on an active order
- `GET  /service/order` — list of services available for the order
- `POST /order/item` — add or remove a service from an active order

### Geocoding (backend-hosted Elasticsearch)
- `GET /elasticsearch/send?lat=&lon=` — reverse geocode
- `GET /elasticsearch/by-address?text=` — text search

### Payments (base: `https://prod.mehrgo.uz/api/paylov/`)
- `POST  /user-card/create` — start adding a card
- `POST  /user-card/confirm` — confirm with OTP
- `DELETE /user-card/delete?cardId=` — remove card
- `GET   /user-card/cards` — list saved cards

### Misc
- `GET /mobile/version` — version gate (called by CheckVersion)

### Routing (base: `https://route.teamwork.uz/`)
- Polyline / distance / duration between two points (specific path varies — read `DirectionApiService` to mirror parameter names).

---

## 10. Notable UI / Interaction Behaviours

These are the non-obvious behaviours beyond the basic screens. Implement all of them.

- **Phone-field auto-keyboard** — see §0.1.
- **OTP auto-fill + auto-submit, with double-submit guard** — see §0.2.
- **Agreement gating** — Submit disabled until agreement accepted; agreement sheet requires scroll-to-bottom before Accept enables; see §0.3.
- **Resend countdown** — Disabled "Send SMS again" chip with `mm:ss` countdown; tappable after 0.
- **Reverse-geocode debounce** — As the map idles after a pan, call `/elasticsearch/send` once; do not fire on every camera-move event.
- **Pricing recompute** — Every change to start, finish, tariff, services, promo, bonus, or payment recomputes price via `/order-new/calculate`. Debounce by ~300 ms to avoid spamming the endpoint.
- **Bottom-sheet UX everywhere** — Tariff, services, payment, promo, comment, rate, cancel-reason, etc. are all modal sheets, not full screens. On iOS use `UISheetPresentationController` with `.medium()` / `.large()` detents as appropriate; existing iOS sheet patterns in the project should be reused.
- **Order-tracking camera follow** — When the driver is moving (state `accepted`/`started`), the map camera animates to keep the driver marker centred. The user can pan away; if they do, suspend follow until they re-centre or order state changes.
- **Smooth driver-marker animation** — On each WebSocket `driver_location`, interpolate the marker between previous and new coords over ~1 second; do not "teleport".
- **Pagination load-state UI** — Loading footer while fetching next page; retry button on error; "no more" footer when end reached.
- **No biometric login** — Phone + OTP only. Don't add Face ID / Touch ID without an explicit ask.
- **Status bar** — Auth screens use a specific status-bar style (light vs dark) handled by `StatusBarHelper.applyAuthStyle`. On iOS use the existing per-VC `preferredStatusBarStyle` pattern; coordinate with the design owner if the auth screens look wrong post-implementation.

---

## 11. Data Models (minimum fields the iOS layer needs)

These are the shapes the API returns; mirror as Swift `Codable` types or whatever ORM-free model layer the iOS project already uses. Field names below are camelCase as Android sees them post-Gson deserialization — actual JSON keys are snake_case (the Android side uses `@SerializedName`; on iOS use `CodingKeys`).

- **User** — `id`, `firstName`, `lastName`, `authKey`, plus bonus / phone on `/client/me`
- **Order** — `id`, `price`, `state`, `startLocation`, `finishLocation`, `tariff`, `services[]`, `promo`, `bonus`, `driver` (nested Car: `model`, `color`, `licensePlate`, `name`, `rating`, `phone`, `avatar`), `cancellation`, `route` (polyline + distance + duration)
- **Tariff** — `id`, `name`
- **TariffGroup** — `id`, `name`, `tariffs: [Tariff]`
- **Service** — `id`, `name`, `price`, plus a client-side `isSelected` flag for the sheet
- **Card** — `cardId`, `cardNumber` (masked), `expiryDate`, `isSelected`
- **Address** — `id`, `name`, `type` (1/2/3), `lat`, `lon`, plus a `category` on `AddressByLocation` for the geocoded variant
- **OrderCancelReason** — `id`, `reason`
- **Socket payloads** — `SocketDriverLocationResponse { lat, lon, bearing }`, `SocketOfferedDriverResponse { ... }`, `SocketOrderResponse { state, ... }`

Source of truth for exact JSON keys: `app/src/main/java/uz/teamwork/mehrgo/domain/model/*.kt`. Read those files when wiring up `Codable` types so snake_case keys match.

---

## 12. Known Existing Behaviour the iOS Team Should Preserve

These are likely already implemented on iOS and should **not** be regressed by this parity work:

- The iOS app's existing visual design (colours, fonts, spacing, asset library). Do not theme to match Android.
- Any platform-specific niceties iOS already has that Android does not (e.g. haptic feedback on tap, dark mode, Dynamic Type support). Keep them.
- Existing analytics / Crashlytics wiring (the Android side uses Firebase Analytics + Crashlytics; the iOS side likely does the same — keep its event names consistent).

---

## 13. Suggested Implementation Order

When the iOS Claude agent starts work, tackle the gaps in this order — each step is independently shippable:

1. **§0 explicit asks first**: auto-keyboard, OTP auto-fill, agreement checkbox. Smallest scope, biggest user-visible improvement.
2. Auth-flow parity (phone + OTP + first-time profile) — make sure the FCM token and language are sent on `/client/confirm`.
3. Home order-creation parity — sheets for tariff, services, promo, payment, comment; live price recompute.
4. OrderCreated tracking parity — WebSocket connection, driver marker animation, state-driven UI, cancel + rate flows.
5. Order history pagination parity.
6. Payment management (add card with OTP, delete card).
7. Saved addresses (CRUD + map picker).
8. Settings (edit profile, language change, support links, logout).
9. Polish pass: pagination load states, empty states, error retries, push deep linking, reconnect logic on WebSocket.

After each step, run the app on a real device, log in with a test phone number, and walk the golden path before moving on.

---

## 14. Open Questions to Surface to the User

If, during audit or implementation, the iOS agent hits any of these, **stop and ask** rather than guessing:

- The OTP length — code currently expects 4 digits on the client. Backend may now send 5 or 6 in some flows; verify against a real SMS.
- The exact state machine — Android handles `new`, `searching`, `offered`, `accepted`, `started`, `arrived`, `done`, `cancelled`. Backend may have added or renamed states since 6.2.2 (versionCode 54).
- Background WebSocket strategy — silent push vs. on-foreground-only. Needs product decision; do not request privileged background modes unprompted.
- Translations for the new agreement checkbox / sheet strings in `uz` / `kk` / `ky`.
- Whether the iOS app should keep Google Maps SDK or migrate to MapKit (visual & feature consequences).

---

End of spec. When in doubt about behaviour, **read the corresponding Kotlin file** at the matching path under `app/src/main/java/uz/teamwork/mehrgo/presentation/screens/` — the Android source is authoritative for this document.
