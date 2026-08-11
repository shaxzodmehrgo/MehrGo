# Telegram "Buyurtmani qabul qilish" → Android App Link

Answer to `DEEPLINK_INSTRUCTIONS.md`. Android side is implemented; two items are blocked on the
backend/devops side and are listed at the bottom.

## 1. Final URL format — accepted as proposed

```
https://mehrgo.uz/driver/order/<order_id>
```

e.g. `https://mehrgo.uz/driver/order/80893`. No change to path or domain. The bot can build the
button against this today.

Defined once in [`Constants.kt`](../app/src/main/java/uz/teamwork/mehrgodriver/common/Constants.kt)
as `DEEPLINK_HOST` + `DEEPLINK_ORDER_PATH`, and duplicated in `AndroidManifest.xml` (a `<data>`
element cannot read a Kotlin constant). **Both** have to change when the brand changes — this app is
white-labeled into ~15 driver apps and only the Mehrgo build answers `mehrgo.uz`. Every other brand
needs its own domain + its own `assetlinks.json`, or its bot must not show the button.

## 2. What the app does with the link

Contrary to the suggested `OrderDeepLinkActivity`, the filter sits on **`MainActivity`**
(`launchMode="singleTop"`). It is the screen that owns the NavController, and it is where the FCM
notification `PendingIntent` already points — a separate Activity would have to duplicate that
routing and would bypass the app's startup gating.

Flow: `MainActivity.onCreate` / `onNewIntent` → `PendingDeepLink.capture(intent.data)` → navigate to
the orders pool (`OrdersMapFragment`) → once `order/list` resolves, the matching order opens in the
existing `OrderOfferBottomSheet` with its normal **Accept / Skip** buttons.

The link **opens** the order; it does not accept it. Accepting stays an explicit tap by the driver,
through the same sheet and the same `order/accept` call as a pool tap — a one-tap-from-Telegram
accept would let a mis-tap bind a driver to a trip.

### Scenarios the doc asked about

| Situation | Behaviour |
|---|---|
| Not signed in | The id survives the login flow (`PendingDeepLink` is process-scoped, not an Intent extra). `MainActivity` retries the routing every time the driver lands on the map, so the offer opens right after sign-in. |
| Shift not started | Pool is empty by design; the existing "start work first" empty state shows and the link is dropped. |
| Order already taken / cancelled | Info popup: **"Buyurtma allaqachon qabul qilingan"** + "Bu buyurtma ro'yxatda yo'q — uni boshqa haydovchi olgan yoki mijoz bekor qilgan." (uz / ru / kk / ky). |
| App not installed | Out of our hands — `https://mehrgo.uz/driver/order/<id>` must serve a page that redirects to Google Play. **Backend task.** |
| Link tapped while app is open | `singleTop` → `onNewIntent`, no second Activity, routes immediately. |

Note: there is no "fetch one order by id" endpoint. The sheet renders from a full `Order` object, and
the only place that object exists for an unaccepted order is the driver's own pool (`order/list`). So
an order that is real but not offered to *this* driver reads as "already taken". If that distinction
matters, the backend would need `GET order/{id}`.

## 3. Still blocked — needed from backend / devops

**a) Host the verification file** at `https://mehrgo.uz/.well-known/assetlinks.json` — HTTPS, no
redirect, `Content-Type: application/json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "uz.teamwork.mehrgodriver",
    "sha256_cert_fingerprints": ["<RELEASE_SHA256>"]
  }
}]
```

**b) The release SHA-256.** Not obtainable from this repo — there is no `signingConfig` in
`app/build.gradle`, release builds are signed from Android Studio, and the keystore is not tracked.
Take it from **Play Console → Setup → App integrity → App signing**, and use the **App signing key**
fingerprint (Play re-signs the upload). If both an upload key and a Play signing key are in play,
list **both** fingerprints in the array.

Until (a) and (b) are live, `autoVerify` fails and Android opens the URL in the browser instead of
the app. Nothing in the app needs to change when they land.

### Verify — tested on device 2026-08-06 (Infinix X6886, Android 15)

`am start -a VIEW -d <url>` is an **implicit** intent, so before the domain is approved it goes to
**Chrome**, not the app — confirmed on device. Two ways to test the app side anyway:

Deliver the intent straight to the component (skips the resolver entirely):

```bash
adb shell am start -n "uz.teamwork.mehrgodriver/.presentation.activity.main.MainActivity" -a android.intent.action.VIEW -d "https://mehrgo.uz/driver/order/80893"
```

Or approve the domain locally, which is exactly what `assetlinks.json` will do in production, and then
the normal implicit link opens the app:

```bash
adb shell pm set-app-links-user-selection --user 0 --package uz.teamwork.mehrgodriver true mehrgo.uz
```

**Result with the override in place:** the plain `am start -a VIEW -d …` opened MainActivity (not
Chrome) and landed on the orders pool, both warm (`onNewIntent`) and from a cold start
(`onCreate` → `mapFragment` → `ordersMapFragment`, ~0.9 s). Undo the override with `… false mehrgo.uz`.

Once the domain file is live, this must report `verified` for `mehrgo.uz`:

```bash
adb shell pm get-app-links uz.teamwork.mehrgodriver
```

**Not yet tested:** the offer sheet actually opening, and the "already taken" popup — both need the
driver to be on shift with a real pool order. Off shift, the pool is empty and the link correctly
falls through to the "start work first" empty state.

## iOS

Not done — this repo is Android only. The AASA half of the original instructions still applies to the
SwiftUI driver app, using the same path (`/driver/order/*`).
