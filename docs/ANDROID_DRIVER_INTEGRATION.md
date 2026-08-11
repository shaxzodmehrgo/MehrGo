# Подключение существующего Android-водителя к MaktabGo

Приложение `mehrgo_driver_app` уже спроектировано под white-label (см. его `CLAUDE.md`):
бренд задаётся блоком в `Constants.kt`, а приём заказов идёт через WebSocket →
`LocalBroadcastManager`. Поэтому подключение к MaktabGo — это **новый бренд-блок + один
socket-ключ + один экран рейса**, без переписывания приложения.

Для демо за 1 день гарантированный путь — **web-view водителя** (`web/driver.html`, уже готов).
Ниже — как довести до нативного Android, когда будет время собрать APK.

## 1. Новый бренд-блок в `common/Constants.kt`
Скопировать активный блок Mehrgo и заменить хосты на MaktabGo:
```kotlin
// --- MaktabGo ---
const val BASE_URL = "https://maktabgo.<домен>/api/"
const val BASE_URL_FOR_SOCKET = "wss://maktabgo.<домен>/ws"   // если поднимете WS; иначе polling
const val BASE_URL_ROUTE = "https://route.teamwork.uz/"        // резервный OSRM
const val MAPKIT_KEY = "<Yandex MapKit key>"
const val APPLICATION_ID = "uz.teamwork.maktabgodriver"
// TELEGRAM_BOT_USERNAME = "" → OTP-бот не обязателен (демо-код 1111)
```
Плюс `applicationId`/`namespace`/`versionCode` в `app/build.gradle` и пакеты в `AndroidManifest.xml`
(как описано в разделе «Switching the active brand» их CLAUDE.md).

## 2. Контракт API (уже реализован бэкендом)
Эндпоинты водителя MaktabGo намеренно близки к такси:
- `POST /api/driver/login` `{phone, code}` → `{token, driver}`
- `POST /api/driver/online` `{online, lat, lon}`
- `GET  /api/driver/routes` → `{available:[...], mine:[...]}`  ← **рейсы вместо одиночных заказов**
- `POST /api/driver/routes/{id}/accept|start|complete`
- `POST /api/driver/routes/{id}/pickup` `{seq|child_id}`  ← отметка посадки ребёнка
- `POST /api/driver/routes/{id}/track` `{lat, lon}`

Ответы — тот же стиль (`{ok, ...}`); `HeaderInterceptor` уже шлёт `Authorization: Bearer`.

## 3. Приём рейса (реалтайм)
Два варианта:
- **Быстрый (демо/MVP):** polling `GET /api/driver/routes` раз в 5–10 с из существующего
  `MyTrackingService`. Ничего в socket-слое менять не нужно.
- **Как в такси:** поднять WS на бэкенде и в `MySocketListener.onMessage` добавить ключ
  `ROUTE_NEW` → broadcast на `ACTION_SEND_ORDER_DATA_BY_BROADCAST` (тот же паттерн, что
  `ORDER_NEW`). Экран списка подписывается и показывает карточку рейса.

## 4. Экран рейса (multi-stop)
Отличие от такси — **несколько точек посадки** вместо одной A→B:
- Reuse экрана заказа; вместо одной точки рисуем `route.stops[]` (Yandex MapKit) в порядке
  `pickup_seq`, финиш — школа (`route.school`).
- Кнопки: «Начать рейс» (`/start`), у каждой точки «Посадил» (`/pickup {seq}`),
  «Завершить» (`/complete`). Трекинг — существующий `MyTrackingService` → `/track`.

## 5. Что не менять
- Auth-поток, `HeaderInterceptor`, `Resource<T>`/use-case паттерн, локализацию — переиспользуются.
- Не добавлять 4-й foreground-сервис: трекинг рейса идёт через существующий `MyTrackingService`.

Итог: ориентировочно 1 бренд-блок + 1 экран (список рейсов) + расширение экрана заказа до
multi-stop. Всё остальное уже есть.
