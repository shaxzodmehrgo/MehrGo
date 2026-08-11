# MehrGo Driver App (Android)

Driver application for the MehrGo mobility platform (Uzbekistan) — including the
**MaktabGo school shuttle** mode: subscription rides that pool children from the
same school into shared multi-stop routes with live GPS tracking for parents.

**Live product:** published on App Store / Google Play, 20k+ downloads.

## Stack
Kotlin · Clean Architecture (`common` / `data` / `domain` / `presentation`) · Hilt DI ·
Retrofit + OkHttp · Coroutines & Flow · Room · Yandex MapKit · WebSocket realtime · FCM

## Key modules
- `presentation/maps/yandex_map` — live map, order lifecycle, navigator deep-links
- `common/services` — foreground GPS tracking + WebSocket order dispatch
- `domain/use_case` — single-responsibility use cases (`Flow<Resource<T>>` pattern)
- `presentation/.../maktabgo_*` — school-shuttle flow: driver OTP onboarding, route list, multi-stop pickup
- White-label: one codebase ships ~15 branded taxi apps (`common/Constants.kt`)

## Note for reviewers
Sanitized snapshot for code review: API keys replaced with placeholders, signing
configs and `google-services.json` excluded — not buildable as-is. Day-to-day
development lives in a private GitLab (full commit history since 2024). Full
history / live demo available on request.

**Repo map:** `driver_app` — this Android app · `backend_part` — MaktabGo school-shuttle backend (PHP/Yii2 + PostgreSQL).
