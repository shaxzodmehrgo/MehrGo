# Rayxon (Райхон Такси) — iOS client port spec

Port of the **Android client** rebrand (MehrGo → Rayxon) to the **iOS client**. Everything below is already done and verified on Android (debug + signed release build green); this doc is the equivalent change-list for iOS.

**Asset source:** `Telegram Desktop/Files.zip` → `Files/1.iOS/rayxon/`
(Android used `Files/taxi_client_apps/rayxon/` — the iOS folder already ships a full `AppIcon.appiconset`.)

---

## ⚠️ 1. Bundle ID — read this first

The two platforms **intentionally differ**. This is not a typo — it's how the Firebase project `rayhon-2301b` is registered:

| Platform | Identifier | Source of truth |
|---|---|---|
| Android | `uz.teamwork.rayhontaxi` — **H** | `google-services.json` → `package_name` |
| **iOS** | **`uz.teamwork.rayxontaxi`** — **X** | `GoogleService-Info.plist` → `BUNDLE_ID` |

➡️ **iOS `PRODUCT_BUNDLE_IDENTIFIER` must be `uz.teamwork.rayxontaxi` (with an X).** It must match `BUNDLE_ID` in the plist exactly or Firebase init fails at runtime. Do **not** "fix" it to match Android.

Provisioning profile / App ID must exist for `uz.teamwork.rayxontaxi`.

---

## 2. Firebase

Drop in `Files/1.iOS/rayxon/GoogleService-Info.plist` (replaces the MehrGo one). Values it carries:

| Key | Value |
|---|---|
| `PROJECT_ID` | `rayhon-2301b` |
| `BUNDLE_ID` | `uz.teamwork.rayxontaxi` |
| `GOOGLE_APP_ID` | `1:79991112237:ios:8e6b708377d5a5b3f56ea4` |
| `GCM_SENDER_ID` | `79991112237` |
| `API_KEY` | `AIzaSyDaEkw1V_hLsX6mHDvbCdiu6b3A0j6LTtc` |
| `STORAGE_BUCKET` | `rayhon-2301b.firebasestorage.app` |

> This `API_KEY` is the **Firebase** key, *not* the Google Maps SDK key. See §9.

---

## 3. Environment / endpoints

Android `Constants.kt` active block → the iOS equivalent constants file:

| Constant | Value |
|---|---|
| `BASE_URL` | `https://rayxon1.teamwork.uz/api/v1/` |
| `BASE_URL_PAYMENT` | `https://rayxon1.teamwork.uz/api/paylov/` |
| `SOCKET_BASE_URL` | `wss://rayxon1.teamwork.uz/socket` |
| `IMAGE_URL` | `https://rayxon1.teamwork.uz/` |
| `BASE_URL_DIRECTION` | `https://kgroute.teamwork.uz/` ← **kg**route, not `route` |
| `NEWS` | `https://www.instagram.com/nookattaxi` |
| `ABOUT_US` | `https://t.me/nookattaxi` |
| `DEFAULT_LOCATION` | `40.265734, 72.619172` (Nookat, KG) |

Note the host is `rayxon1` (**X**) even though the bundle id is `rayhontaxi`/`rayxontaxi` — the backend host spelling is its own thing. Don't "normalise" it.

---

## 4. Version

| | Value |
|---|---|
| Marketing version (`CFBundleShortVersionString`) | `1.7.5` |
| Build (`CFBundleVersion`) | `175` |

Keep the in-code constants in sync too (Android has `APP_VERSION = 175` / `APP_VERSION_NAME = "1.7.5"`, used for the force-update check against the backend). If the app is already live, the build number must exceed the last uploaded one.

---

## 5. App name

`Райхон Такси` (Cyrillic) — for **every** localisation, including the English one. Android sets the same string in all locales.

- `CFBundleDisplayName` = `Райхон Такси`

---

## 6. Accent colour — gold on black

There was **no colour file** in the assets; the gold below was sampled from the brand logo.

| Token | Value | Notes |
|---|---|---|
| Accent / `app_color` | **`#E3B14C`** | primary buttons, highlights |
| Accent @ 8% | `#E3B14C` α`0.08` | Android `#14E3B14C` |
| Accent @ 50% | `#E3B14C` α`0.50` | Android `#80E3B14C` |
| Button label on accent | **`#000000`** | ⚠️ black, **not** white — gold is too light for white text |

Previous MehrGo accent was teal `#00B7A8` with white button text. The brand is **gold on black**.

---

## 7. Assets

| Source (`Files/1.iOS/rayxon/`) | Use |
|---|---|
| `AppIcon.appiconset/` (20…1024 + `Contents.json`) | App icon — replace the whole appiconset wholesale |
| `logo_in_app.png` | In-app logo: splash / sign-in / sign-up / edit-profile header (transparent gold wordmark) |
| `ic_start.png` | Map **pickup** pin |
| `ic_finish.png` | Map **dropoff** pin |

On Android these map to `logo_in_app`, `ic_start`, `ic_finish` and are used on: splash/version-check, sign-in, sign-up, edit-profile, home, and the select-location screens.

---

## 8. Languages — Kyrgyz + Russian ONLY

Reduce the language picker to exactly two options:

| Show | Code | Label |
|---|---|---|
| ✅ | `ky` | `Кыргыз` |
| ✅ | `ru` | `Русский` |
| ❌ | `uz` | removed |
| ❌ | `kk` | removed |
| ❌ | `en` | removed |

Applies to **both** entry points (Android has two, iOS likely mirrors):
1. the **first-launch** language screen, and
2. the **change-language** sheet in Settings.

Android kept the string resources for the removed locales and only hid them in the picker — existing users keep a previously-saved language until they pick again. Match that unless you want a forced migration.

---

## 9. Phone number — Kyrgyzstan

| | Value |
|---|---|
| Country code prefix | **`+996`** (was `+998`) |
| Subscriber digits | **9** |
| Total length incl. `+996` | **13** (Android `PHONE_NUMBER_SIZE = 13`) |
| Mask / hint | `(___) ______` |
| Renders as | `+996 (907) 800660` |

The `+996` prefix is a fixed label next to the field; only the 9 digits are typed. Submit `"+996" + <unmasked digits>`.

> The last six digits currently run together (`800660`). Grouped alternatives were offered (`800 660` or `80-06-60`) — **pending Umar's decision**; keep iOS identical to whatever Android lands on.

---

## 10. ⚠️ Outstanding — Google Maps key

Android still ships the **MehrGo** Maps SDK key, which is Cloud-restricted to the MehrGo bundle/cert, so the map goes blank ("For development purposes only") under the new identifier. iOS will hit the **same class of problem** with its own Maps key.

Action (Google Cloud Console → Maps SDK key → iOS restrictions): authorise bundle **`uz.teamwork.rayxontaxi`**, or issue a Rayxon-specific key.

For reference, the Android release signing cert (needed for *its* key restriction) is:
- SHA-1 `7e7ef510e705b67f78be7fe0b1f36739cd3a3deb`

---

## 11. Checklist

- [ ] `PRODUCT_BUNDLE_IDENTIFIER` = `uz.teamwork.rayxontaxi` (**X**) + matching provisioning profile
- [ ] `GoogleService-Info.plist` replaced
- [ ] Endpoints → `rayxon1.teamwork.uz` + `kgroute.teamwork.uz`
- [ ] Default map location → Nookat `40.265734, 72.619172`
- [ ] Version `1.7.5` / build `175`
- [ ] Display name → `Райхон Такси`
- [ ] Accent `#E3B14C`, button label black
- [ ] `AppIcon.appiconset` replaced
- [ ] `logo_in_app`, `ic_start`, `ic_finish` replaced
- [ ] Language picker → `ky` + `ru` only (both entry points)
- [ ] Phone → `+996`, 9 digits, mask `(___) ______`
- [ ] Maps key authorised for the iOS bundle
