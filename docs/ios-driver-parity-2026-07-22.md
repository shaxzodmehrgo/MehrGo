# iOS Driver — parity task: port the Android driver's fare, service-status and safety changes

**Read this whole brief before writing code.** You are given TWO projects:

- **The Android driver app (`mehrgo_driver_app` branch)** — the *reference / source of truth* for
  the behaviour below. It has just been corrected and device-verified against the live backend.
- **The iOS driver app (SwiftUI `MehrgoDriver`)** — the target. Same backend, same models, same
  brand. Your job is to bring it to behavioural parity with the Android app for the areas listed.

Both apps talk to the **same production backend**. The changes below are about matching what the
Android app now does on the wire and on screen. Do **not** invent new backend behaviour.

---

## How to work (non-negotiable)

1. **Analyze both codebases first.** For each item, find the Android implementation (it is the
   spec), then find the corresponding iOS screen/flow, then port the behaviour idiomatically in
   SwiftUI — do not transliterate Kotlin.
2. **Verify on the wire, never trust a comment or a field name.** Several bugs on both platforms
   came from trusting a stale comment or a plausible-looking field. When a money value or a
   status is involved, confirm the actual request/response shape from a real order (Charles/Proxyman
   capture or backend logs) before you code the rule.
3. **The backend is the single source of truth for money.** The app displays what the server
   computes; it does not compute the final fare itself.
4. **Every user-facing string ships in all supported locales** (uz, kk, ky, ru), matching the
   Android strings.
5. **Quality bar: user-friendly, professional, best.** Match the Android UX, not a rough port.
6. When something is **blocked on the backend** (see §8), implement the best available client-side
   behaviour and clearly flag the gap — do not fake it.

---

## 1. Fare calculation — the finish/receipt flow (HIGHEST PRIORITY)

This is where the real money bugs were. Implement the backend contract exactly.

### 1.1 Backend contract — `POST /v1/order/complete` (verified on prod)

Body fields and their units:

| Field | Unit | Notes |
|---|---|---|
| `waiting_time` | **milliseconds** | pickup (podacha) wait |
| `waiting_time_ontheway` | **milliseconds** | on-route wait (rider asked to wait); optional, backend treats missing as 0 |
| `execution_time` | **milliseconds** | total trip time; the backend caps total wait against this |
| `distance` | — | **IGNORED by the backend** — server recomputes distance from its own GPS track |
| `total_price` | — | **IGNORED by the backend** — server recomputes the price |
| `services` | array of `{count, value}` | optional; if omitted the backend uses the DB `order_item` sum |

> ⚠️ Because `total_price` and `distance` are ignored, **whatever the app posts is decoration.**
> The server's own figure is what gets booked. A stale posted `total_price` silently disagreeing
> with the booked amount is exactly the class of bug we fixed — do not rely on it.

### 1.2 Server-side price formula (mirror it for display only)

```
price = GIS_fare (server GPS track, from "Kettik"/Go onward, NO waiting)
      + services + podacha + extra
      + waiting_cost
      → rounded up ONCE to around_number (e.g. 1000)
```

Waiting cost (per leg, free window subtracted ONCE from the CUMULATIVE time, not per stop):

```
onWayRate = (price_of_waiting_on_way != null && > 0) ? price_of_waiting_on_way : price_of_waiting
podacha_cost = max(0, waitSec  - min_wait_time)        /60 * price_of_waiting
onway_cost   = max(0, onWaySec - min_wait_time_on_way) /60 * onWayRate
waiting_cost = podacha_cost + onway_cost
```

**§2 trap (must implement):** `price_of_waiting_on_way` arrives as **`null` OR `0`** when the admin
left it blank — in BOTH cases fall back to `price_of_waiting`. A bare "if null" is a bug; a `0`
must fall back too. (On Android this bit the on-way *rate label*, which showed "0/min" while
actually billing the pickup rate.)

### 1.3 `price_kept_near_b` (the app cannot know this locally)

If the driver finishes within ~300 m of the planned B point, the backend **keeps the agreed
(booked) price** instead of recomputing by taximeter — only waiting is added on top. The app
**cannot** determine this locally, so the final screen must reflect the server, not a local meter.

### 1.4 `order.price` already includes booking-time services

Auto/booked services are **already inside `order.price`**. Never add `estimate.services_price` on
top of `order.price` — that double-counts. Bill only the live − estimate *delta* for mid-trip
changes.

### 1.5 The finish-dialog "wait freeze" bug (port this fix exactly)

**Symptom (real prod order):** the finish confirmation dialog computed its total once, but the
waiting meter kept counting while the driver read the dialog. Held ~30 s, the server total crossed
a rounding step; the app then POSTed its stale snapshot together with a *fresh* wait time — an
internally inconsistent request — and the driver quoted the passenger one number while the system
booked another.

**Fix on Android, to mirror on iOS:** while the final-bill dialog/sheet is on screen, **pause the
waiting meter** (stop uploading a growing wait), and resume it if the driver dismisses the dialog
("Continue"). On confirm, post exactly the numbers the driver saw. Because the server derives its
live wait from what the app uploads, freezing the local meter freezes the server total too — so
the approved number and the booked number match.

Watch for an **auto-wait re-arm**: on brands where waiting auto-starts when the car is stationary,
the meter re-arms itself on the next parked GPS fix — so pausing is not enough; you must also
suppress the auto re-arm while the bill is on screen.

### 1.6 `live.price` DOES include `waiting_cost` (verified across 62 frames)

`live.price = round_to_around_number(fare + services + waiting_cost + podacha + extra)`. Do not
"fix" a low-wait frame where `price == fare` — at small wait amounts the rounding hides it. Round
is to **nearest** `around_number` (not up on the client side): the driver can lose up to
`around_number − 1` per trip; that is backend policy, not an app bug.

### 1.7 Receipt breakdown must match the dialog

The finish **dialog** and the post-finish **receipt** must show the SAME row breakdown (ride /
wait / services). On Android they diverged because the dialog read the server snapshot while the
receipt re-derived from local meters. Compute the rows once and pass them through; clamp the ride
line at `>= 0`.

---

## 2. Live fare + service-status sync during the trip

### 2.1 Live protocol — `POST /v1/order-gps/batch` and socket `order_price_updated`

- The app uploads `waiting_time` / `waiting_time_ontheway` (ms, **cumulative totals, not deltas**)
  in the GPS batch, **only when they change** — sending with no GPS points is allowed (stationary
  driver).
- Every batch response (and the socket `order_price_updated` frame) returns a `live` block — the
  **single source of truth** for the on-screen figures during the trip:
  `distance_km`, `to_client_km`, `waiting_sec`, `on_way_sec`, `waiting_cost`, `services_price`,
  `podacha`, `extra_price`, `fare`, `price`, and for B-point orders `agreed_price`, `surcharge`,
  `kept_projection`, `waiting_billing`.
- The local meter is only a smoothing animation between server responses; resync to the server on
  every response.

### 2.2 Mid-order service toggle MUST move the price

When the rider toggles a service mid-trip, the driver's price must update. On Android the bug was:
the price only refreshed when a GPS batch uploaded, so a **stationary** driver never saw it. Fixes:

- React to the socket frame whose `events` contains `"services_changed"` → refresh.
- React to the FCM **data** push `type == "order_price_recalculated"` (fields: `order_id`,
  `reason`, `price`, `surcharge`) — treat it as a **signal to refetch**, not as a value.
- **Guard every socket/push on the order id** (`data.order_id == currentOrder.id`). On the driver
  side `order_cancelled` fires for *any* pooled order, incl. ones never accepted — an unguarded
  handler wiped the wrong screen. Assume the same for every frame.
- The backend takes **~2 s** to reflect a services change. Optionally repaint optimistically
  (`newTotal = live.price − live.services_price + newServicesTotal`), but the optimistic number is
  **DISPLAY ONLY** — never write it into the field the receipt/complete flow reads (it can differ
  by one rounding step).

### 2.3 Distance is metered only after Go ("Kettik")

The drive-to-pickup leg and movement during waiting must NOT be metered by the local taximeter
(the server excludes stage-7/8 and only prices stage-9). Start the local distance meter at Go.

---

## 3. Service icons from the backend, with a local glyph fallback

- Service objects may carry an `icon` (e.g. `@uploads/services/icons/x.svg`). Render it, but keep a
  **local glyph fallback keyed on the service NAME, not its id** — the same "Konditsioner" is
  `id:1` inside `order_items[].service` and `id:3` in the `service/order` catalogue (two id spaces).
- Media URL shapes vary: `@uploads/...` (no leading slash), `/admin/...` (leading slash), absolute
  `https://`. Normalise before loading. SVGs need an SVG-capable loader.
- `service.value` is the catalogue list price; the **billed** amount is `order_items[].total`
  (differ for percent-priced services).

---

## 4. Driver may NOT cancel an order → show the support number instead

Product rule: the driver cannot cancel. The trip sheet's "Cancel" button must **always** open a
contact-support popup showing the **same number as the top-of-screen support icon** (branch
dispatcher first, brand support line as fallback), with a Call button — never a cancel-reason
sheet. Format the number consistently regardless of source (one source is pre-spaced, the other is
not). Do not remove the underlying cancel API wiring if it's shared; just make the button
unreachable-to-cancel.

---

## 5. Double-back / exit-guard on the home/map screen

On the root map screen, a single back/exit gesture must **not** close the app. Show a
"Press again to close" message and require a second back within ~2 s to actually exit. If a trip
sheet is expanded, back first collapses it. Keep any active trip alive across the close via the
background/foreground tracking mechanism.

---

## 6. History screen unit inconsistency

`order/history` sends `execution_time` in **ms** but `waiting_time` in **seconds** for newer rows
(older rows are ms). Infer the unit (invariant: wait ≤ execution) so a ~47-minute wait is not
rendered as `00:00:02`. Also parse `order_items` for the per-order service breakdown.

---

## 7. Security parity (release builds)

Check the iOS equivalents and fix if present:

- **No credentials in logs on release builds.** The Android release build was logging the login
  password, the OTP code, and the `Authorization: Bearer` token via an ungated HTTP body logger.
  Ensure iOS network logging is DEBUG-only.
- **Do not persist / upload the bearer token** in an error/diagnostics report. The server already
  authenticates the sender.
- **Logout must stop the tracking service/session** and rebuild any socket connection from the
  *current* token — a logout that leaves tracking running under the old token is a real leak.

---

## 8. Known backend gaps — do NOT chase these client-side

- **`POST /v1/order/complete` returns the driver USER object, NOT a `final`/price block.** So the
  spec line "draw the final screen from the complete response" is **not implementable today**.
  Until the backend adds a `final` block, draw the receipt from the values the app posted / the
  last `live` block, and keep the dialog and receipt consistent (§1.7). Flag this to the backend
  team; do not fabricate a receipt.
- **~2 s repricing latency** on `order-gps/batch` / `order-gps/fare` after a services change — plan
  the UX around it (optimistic repaint, §2.2).

---

## 9. Deliverable

For each of §1–§7: a short note of what the iOS app did before, what you changed, and how you
verified it (ideally against a real order capture). Call out anything you could not verify on the
wire, and anything blocked by §8. Keep the same UX polish as the Android app. Ask before making a
settlement-affecting change you cannot verify against a real payload — **getting fare wrong
under-pays drivers.**
