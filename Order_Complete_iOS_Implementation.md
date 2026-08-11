# Mehrgo Driver — Order Complete: iOS Implementation Spec

> **Audience** — Claude Code (or any iOS engineer), building the trip-finish flow inside the iOS Mehrgo Driver app.
>
> **Goal** — Replicate the **corrected** Android `Order Complete` logic from `MapFragment.kt:1364–1643` in SwiftUI / Swift, including the fixed multi-stop pricing rules. The iOS app must produce **identical fare values** to the Android app for every input.
>
> **Source of truth** — Android branch `mehrgo_driver_app`, version `1.9.2 (48)`. The Android logic in this document is the *fixed* version (post bug-fix); do not port the previous, inverted logic.

---

## Table of Contents

1. [What "Order Complete" does](#1-what-order-complete-does)
2. [End-to-end flow](#2-end-to-end-flow)
3. [Pricing business rules (authoritative)](#3-pricing-business-rules-authoritative)
4. [Variables, sources & units](#4-variables-sources--units)
5. [Reference Swift implementation](#5-reference-swift-implementation)
6. [Submit body (`POST order/complete`)](#6-submit-body-post-ordercomplete)
7. [Error handling](#7-error-handling)
8. [Promo & bonus deductions](#8-promo--bonus-deductions)
9. [Post-success side effects](#9-post-success-side-effects)
10. [Test plan](#10-test-plan)
11. [File-level checklist for the iOS engineer](#11-file-level-checklist-for-the-ios-engineer)
12. [Android cross-references](#12-android-cross-references)

---

## 1. What "Order Complete" does

Driver is in an active trip (`OrderState.started = 7`) and taps **"Finish trip"**. The app:

1. Refreshes the user (`GET user/me`) so bonus / balance / order state are fresh.
2. Computes the fare locally using the rules in §3.
3. Shows a fare-summary sheet with the gross total, the bonus deduction, the promo deduction, and the final amount to collect from the client.
4. On confirmation, `POST order/complete` with the body in §6.
5. Tears down the trip session — stops GPS tracking, clears the route polyline, navigates back to the map's idle state.

The fare math runs **client-side** so the driver can see the live total. The server independently recomputes and either accepts the request or returns a correction (see §7).

---

## 2. End-to-end flow

```
Driver taps "Finish trip"
        ↓
prepareFinish() in TripMVVM
        ↓
GET user/me                       ── refresh user, active order, bonus settings
        ↓
totalServicePrice = Σ order.services[*].total
        ↓
totalPrice = computeTotalPrice(order, taximeter)        ── §3 + §5
        ↓
Show TripFinishView (fare summary sheet)
        ↓
Driver taps "Submit"
        ↓
finalTotalPrice = totalPrice − calculatedBonus − calculatedPromo
        ↓
POST order/complete?order_id={id}                       ── body in §6
        ↓
On success: User returned with cleared active orders
        ↓
LocationTracker.stopTripTracking()
RouteService.clearActiveTripRoute()
Navigate back to MapHomeView (idle)
```

Promo and bonus deductions are **always** applied after `totalPrice`, regardless of which case (§3) computed it. Wait-time is **always** added inside `totalPrice`.

---

## 3. Pricing business rules (authoritative)

Only the **final endpoint C** (`order.locations.last()`) is considered for the radius and distance checks. **Mid points do not affect the radius or distance check** — they only contribute wait time, which is already accumulated inside `totalWaitingPrice`.

| Case | Condition | Total Price Formula |
|------|-----------|---------------------|
| **1 — Driver taximeter (no destination)** | `order.locations.count < 2` | `startingPrice + additionalPrice + totalServicePrice + totalWaitingPrice + totalTrackingPriceInCity + totalTrackingPriceOutCity` |
| **2 — Has destination, GPS unavailable** | `lastLocation == nil` | Same as Case 1 (safety fallback) |
| **3a — Has destination, driver within `END_RADIUS` of C** | `distanceWay <= 300 m` | `order.price + totalWaitingPrice` |
| **3b — Has destination, driver outside `END_RADIUS` but drove planned distance** | `distanceWay > 300 m AND abs(distanceTracked - distanceBetweenLocations) < 1000 m` | `order.price + totalWaitingPrice` |
| **3c — Has destination, driver outside `END_RADIUS` AND distance mismatch** | `distanceWay > 300 m AND abs(distanceTracked - distanceBetweenLocations) >= 1000 m` | `startingPrice + additionalPrice + totalServicePrice + totalWaitingPrice + totalTrackingPriceInCity + totalTrackingPriceOutCity` |

### Locked thresholds

```swift
static let endRadius: Double = 300          // metres — "driver is at C"
static let distanceTolerance: Double = 1000 // metres — "tracked vs planned mismatch"
```

These are **product-team decisions**. Do not change them without explicit business approval. Keep them as named constants — never magic numbers in the formula.

### Why only the last endpoint?

Mid points are intermediate stops where the client wants the driver to wait briefly (e.g. picking up a passenger, dropping off a package). The fare contribution of these stops is already captured by `totalWaitingPrice` (which accumulates wait time across the entire trip). They are NOT used to gate which fare formula applies.

### Worked examples

Setup: planned distance **10 km**, `order.price` = **50 000 sum**, taximeter natural total **≈ 48 000**.

| # | Scenario | `distanceWay` | `distanceTracked` vs planned | Case | Driver gets |
|---|---|---|---|---|---|
| 1 | No destination, drove 7 km | – | – | 1 | Taximeter |
| 2 | Single A→C, ended at C | 50 m | 10 km | 3a | 50 000 |
| 3 | Single A→C, ended 800 m short | 800 m | 9.7 km (off by 300 m) | 3b | 50 000 |
| 4 | Single A→C, abandoned mid-trip | 4 km | 6 km (off by 4 km) | 3c | Taximeter |
| 5 | Multi-stop A→B1→B2→C, ended at C | 30 m | 10 km | 3a | 50 000 |
| 6 | Multi-stop, waited 10 min at B1, ended at C | 30 m | 10 km | 3a | 50 000 + waiting |
| 7 | Multi-stop, ended 5 km short of C | 5 km | 7 km (off by 3 km) | 3c | Taximeter |

---

## 4. Variables, sources & units

All distances are in **metres** unless noted.

| Variable | Type | Source | Unit | Meaning |
|---|---|---|---|---|
| `order.locations` | `Array<OrderLocation>` | Server | – | All trip stops including pickup (A) and final dropoff (C). Ordered by `position`. |
| `order.locations.last()` | `OrderLocation` | Server | – | Final endpoint **C**. |
| `order.price` | `Int` | Server | sum (UZS) | Pre-calculated trip price. |
| `order.distance` | `Double` | Server | **km** | Pre-calculated planned route distance. |
| `lastLocation` | `CLLocation?` | `LocationTracker.lastLocation` | – | Driver's most recent GPS fix. May be `nil` very rarely. |
| `distanceWay` | `Double` | `lastLocation.distance(from: cLocation)` | metres | Distance from driver's last GPS fix to C. |
| `distanceInCity` | `Double` | `LocationTracker` (accumulated while inside `branch.polygon`) | metres | Total metres driven inside the city polygon. |
| `distanceOutCity` | `Double` | `LocationTracker` (accumulated while outside polygon) | metres | Total metres driven outside the city polygon. |
| `distanceTracked` | `Double` | `distanceInCity + distanceOutCity` | metres | Real total distance driven. |
| `distanceBetweenLocations` | `Double` | `order.distance * 1000` | metres | Planned distance in metres. |
| `startingPrice` | `Int` | `order.startingPrice` | sum | Base fare. |
| `additionalPrice` | `Int` | `order.addPrice` | sum | Optional additional fare from server. |
| `totalServicePrice` | `Int` | `Σ order.services[*].total` | sum | Sum of selected services. |
| `totalWaitingPrice` | `Double` | `LocationTracker` (pre-arrival + on-way wait × tariff rates) | sum | Wait-time charges. |
| `totalTrackingPriceInCity` | `Double` | `distanceInCity` × tariff intervals (or `priceInCity`) | sum | In-city kilometre charge. |
| `totalTrackingPriceOutCity` | `Double` | `distanceOutCity` × `tariff.priceOfOut` | sum | Out-of-city kilometre charge. |

`distanceWay` MUST be computed using a geodesic distance function (iOS: `CLLocation.distance(from:)`). Do **not** use straight Pythagorean math on lat/lon.

---

## 5. Reference Swift implementation

Place this on `TripMVVM` (the ViewModel that owns the active-trip state). Keep tokens at the top of the file so business changes are a one-line edit.

```swift
//
//  TripMVVM+Pricing.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import os.log
import CoreLocation
import Foundation


// MARK: - Pricing tokens (product-locked)
enum PricingTokens {
    /// Driver is considered "at C" when within this radius (metres).
    static let endRadius: Double = 300
    
    /// Allowed mismatch between tracked and planned distance (metres) before
    /// the local taximeter overrides the server price.
    static let distanceTolerance: Double = 1000
}


// MARK: - Total-price computation
extension TripMVVM {
    
    /// Compute the gross fare for an order, applying the order-complete rules
    /// from the iOS spec §3. Wait time is included in every branch.
    ///
    /// Inputs:
    /// - order: the active Order being finalised.
    /// - taximeter: live counters accumulated in LocationTracker.
    /// - lastLocation: driver's most recent CLLocation, or nil if GPS dropped.
    ///
    /// Returns: gross totalPrice in sum (UZS), rounded.
    func computeTotalPrice(
        order: Order,
        taximeter: TaximeterSnapshot,
        lastLocation: CLLocation?
    ) -> Int64 {
        let logTag: String = "APP-TripMVVM"
        
        // Case 1 — driver taximeter mode (no destination entered).
        if order.locations.count < 2 {
            let v = taximeterTotal(order: order, taximeter: taximeter)
            os_log(.info, "\(logTag): pricing case=1 (taximeter, no destination) total=\(v)")
            return v
        }
        
        // Case 2 — safety fallback when GPS is unavailable.
        guard let lastLocation else {
            let v = taximeterTotal(order: order, taximeter: taximeter)
            os_log(.info, "\(logTag): pricing case=2 (no GPS, fallback to taximeter) total=\(v)")
            return v
        }
        
        // Case 3 — has destination(s); check only the final endpoint C.
        guard
            let endLat = order.locations.last?.latitude,
            let endLon = order.locations.last?.longitude
        else {
            // Corrupt order data — fall back to taximeter rather than crash.
            let v = taximeterTotal(order: order, taximeter: taximeter)
            os_log(.error, "\(logTag): pricing case=2 (missing endpoint, fallback) total=\(v)")
            return v
        }
        
        let endLocation: CLLocation = CLLocation(latitude: endLat, longitude: endLon)
        let distanceWay: Double = lastLocation.distance(from: endLocation)         // metres
        let distanceTracked: Double = taximeter.distanceInCity + taximeter.distanceOutCity
        let distanceBetweenLocations: Double = (order.distance ?? 0) * 1000        // km → m
        
        let nearEnd: Bool = distanceWay <= PricingTokens.endRadius
        let drovePlannedDistance: Bool =
            abs(distanceTracked - distanceBetweenLocations) < PricingTokens.distanceTolerance
        
        if nearEnd || drovePlannedDistance {
            // Cases 3a / 3b — server-calculated price + waiting.
            let serverPrice: Int64 = Int64(order.price ?? 0)
            let waiting: Int64 = Int64(taximeter.totalWaitingPrice)
            let total: Int64 = roundPrice(serverPrice + waiting)
            os_log(.info, "\(logTag): pricing case=3a/b (server price) total=\(total) distanceWay=\(distanceWay)")
            return total
        } else {
            // Case 3c — driver did not reach C and tracked != planned.
            let v = taximeterTotal(order: order, taximeter: taximeter)
            os_log(.info, "\(logTag): pricing case=3c (taximeter) total=\(v) distanceWay=\(distanceWay)")
            return v
        }
    }
    
    
    // MARK: - Helpers
    
    /// Local taximeter total: starting + additional + services + waiting + km charges.
    private func taximeterTotal(order: Order, taximeter: TaximeterSnapshot) -> Int64 {
        let base: Int64 = Int64(order.startingPrice ?? 0)
                       + Int64(order.addPrice ?? 0)
                       + Int64(taximeter.totalServicePrice)
                       + Int64(taximeter.totalWaitingPrice)
                       + Int64(taximeter.totalTrackingPriceInCity)
                       + Int64(taximeter.totalTrackingPriceOutCity)
        return roundPrice(base)
    }
    
    /// Match Android Helper.roundPrice: round to the nearest 100 sum.
    /// `(price / 100.0).rounded() * 100`
    private func roundPrice(_ price: Int64) -> Int64 {
        let rounded: Double = (Double(price) / 100.0).rounded() * 100
        return Int64(rounded)
    }
}


// MARK: - Taximeter snapshot
struct TaximeterSnapshot {
    let distanceInCity: Double          // metres
    let distanceOutCity: Double         // metres
    let totalServicePrice: Int          // sum (UZS)
    let totalWaitingPrice: Double       // sum
    let totalTrackingPriceInCity: Double  // sum
    let totalTrackingPriceOutCity: Double // sum
}
```

### Why `Int64` for money
UZS amounts can exceed `Int32` for premium tariffs (e.g. inter-city rides). Use `Int64` end-to-end and only cast at the JSON boundary.

### Why floor at `startingPrice`
We don't floor explicitly because all branches already include `startingPrice`. If you later refactor and have a branch where `startingPrice` could be missing, add `max(startingPrice, computed)` to preserve the legacy guarantee.

---

## 6. Submit body (`POST order/complete`)

```http
POST /api/v1/order/complete?order_id={orderId}
Content-Type: application/json
Authorization: Bearer {auth_token}
Accept-Language: {uz|kk|ky|ru}

{
  "distance":           "{km, rounded, as String}",
  "latitude_finish":    {lastLocation.coordinate.latitude},
  "longitude_finish":   {lastLocation.coordinate.longitude},
  "total_price":        "{totalPrice as String — GROSS, before promo/bonus}",
  "waiting_time":       "{waiting_ms as String}",
  "execution_time":     "{trip_total_ms as String}",
  "finish_address_id":  null,
  "bonus_payment":      {calculatedBonusFinal as Int64},
  "promo_code_payment": {calculatedPromo as Int64}
}
```

### Swift model

```swift
struct RequestOrderFinish: Codable {
    let distance: String
    let latitudeFinish: Double?
    let longitudeFinish: Double?
    let totalPrice: String
    let waitingTime: String
    let executionTime: String
    let finishAddressId: Int?
    let bonusPayment: Int64
    let promoCodePayment: Int64
    
    enum CodingKeys: String, CodingKey {
        case distance
        case latitudeFinish    = "latitude_finish"
        case longitudeFinish   = "longitude_finish"
        case totalPrice        = "total_price"
        case waitingTime       = "waiting_time"
        case executionTime     = "execution_time"
        case finishAddressId   = "finish_address_id"
        case bonusPayment      = "bonus_payment"
        case promoCodePayment  = "promo_code_payment"
    }
}
```

### Notes
- `distance`, `total_price`, `waiting_time`, `execution_time` are **stringified numbers** on the wire — the backend expects that exact shape. Don't "fix" them to be JSON numbers.
- `finish_address_id` is always `null` in the current build.
- `bonus_payment` and `promo_code_payment` are the **amounts deducted** (sum), not flags.
- Response body is `BaseResponse<User>` — the refreshed user with new `balance` and cleared `orders`.

---

## 7. Error handling

| Server response | Driver-facing UX | Retry? |
|---|---|---|
| **HTTP 200** | Trip closed; navigate back to map idle. | – |
| **HTTP 402** with `{ "status": 402, "message": "…" }` | Show payment-error alert with server message. Typical cause: client cannot pay this amount via card/bonus. Driver collects in cash. | **No** — final |
| **Other 4xx** with `ErrorOrderFinishResponse` payload | The server is rejecting the **bonus** calculation. The payload carries corrected `clientTotalBonus`, `minAmount`, `maxAmount`. Update the local copies on the active order, re-render the fare-summary sheet, let the driver submit again with the corrected bonus. | **Yes** — silent re-submit possible |
| **401** | `UserManager.signOut()`; route to LoginView. | – |
| **5xx** / network | Top toast: `error_no_connection` or `error_server`. Driver can retry from the fare summary. | **Yes** |

### `ErrorOrderFinishResponse` model

```swift
struct ErrorOrderFinishResponse: Codable {
    let status: Int?
    let message: String?
    let clientTotalBonus: Double?
    let minAmount: Double?
    let maxAmount: Double?      // String or Double — handle both server forms
    
    enum CodingKeys: String, CodingKey {
        case status, message
        case clientTotalBonus = "client_total_bonus"
        case minAmount        = "min_amount"
        case maxAmount        = "max_amount"
    }
}
```

### Why duplicate client + server math?
The server is authoritative, but the client computes locally so the driver sees the fare live while driving. On mismatch, the server quietly hands back the corrected bonus settings and the client re-submits — the driver never sees a hard error from the bonus subsystem.

---

## 8. Promo & bonus deductions

Applied **after** `totalPrice` is computed, regardless of which §3 case produced it.

### Promo code

```swift
func computePromo(totalPrice: Int64, promoUsage: PromoUsage?) -> Int64 {
    guard let raw = promoUsage?.amount else { return 0 }
    if raw.hasSuffix("%") {
        // Percentage form, e.g. "10%"
        let pctString: String = String(raw.dropLast())
        let pct: Double = Double(pctString) ?? 0
        return Int64(Double(totalPrice) / 100.0 * pct)
    }
    // Flat sum form, e.g. "5000"
    let flat: Int64 = Int64(raw) ?? 0
    return min(flat, totalPrice)
}
```

### Bonus

```swift
func computeBonus(
    totalPrice: Int64,
    promo: Int64,
    useBonus: Bool,
    bonusSettings: ClientBonusSettings?,
    clientTotalBonus: Double
) -> Int64 {
    guard useBonus, let s = bonusSettings else { return 0 }
    
    let base: Double = Double(totalPrice - promo)
    
    // maxAmount can be either "30%" or a flat string/double.
    let cap: Double = parseAmount(s.maxAmount, base: base)
    let computed: Double = min(cap, clientTotalBonus)
    
    // Threshold: bonus inactive if client's wallet < minAmount.
    let minA: Double = parseAmount(s.minAmount, base: base)
    if clientTotalBonus < minA { return 0 }
    
    return Int64(computed)
}


private func parseAmount(_ raw: Any?, base: Double) -> Double {
    if let s = raw as? String {
        if s.hasSuffix("%") {
            return base / 100.0 * (Double(s.dropLast()) ?? 0)
        }
        return Double(s) ?? 0
    }
    if let d = raw as? Double { return d }
    if let i = raw as? Int { return Double(i) }
    return 0
}
```

### Final number to the driver

```swift
let finalTotalPrice: Int64 = totalPrice - bonus - promo
```

The driver sees `totalPrice` (gross), `bonus`, `promo`, and `finalTotalPrice` (cash to collect from the client). The server receives `totalPrice` plus the bonus and promo amounts in separate fields (see §6).

---

## 9. Post-success side effects

When `POST order/complete` returns 200, the iOS app MUST:

| Action | iOS implementation |
|---|---|
| Persist refreshed user | `UserManager.shared.save(user)` |
| Stop tracking | `LocationTracker.shared.stopTripTracking()` — releases the high-accuracy CL session, deletes the row from `CalculationStore` |
| Stop route polling | `RouteService.shared.clear()` |
| Dismiss fare-summary sheet | `dismiss()` on the `TripFinishView` sheet |
| Reset map state | `MapMVVM.destination = nil`, clear placemarks + polyline |
| Navigate back to map idle | `NavigationStack` → pop back to `MapHomeView` |

In SwiftUI, all of the above happen by changing state — `dismiss()` and updating `@Published` variables on `TripMVVM` and `MapMVVM`. Don't reach into UIKit unless you're forced to.

---

## 10. Test plan

Build a unit test target for the pricing function (`computeTotalPrice`) since it's pure logic. Mock `Order`, `TaximeterSnapshot`, and `CLLocation`.

| # | Test | Expected |
|---|---|---|
| 1 | `locations.count == 0`, taximeter = 40 000 | Taximeter (40 000) |
| 2 | `locations.count == 1`, taximeter = 40 000 | Taximeter (40 000) |
| 3 | `locations.count == 2`, lastLocation 50 m from C | Server price + waiting |
| 4 | `locations.count == 2`, lastLocation 800 m from C, tracked off by 300 m | Server price + waiting |
| 5 | `locations.count == 2`, lastLocation 4 km from C, tracked off by 4 km | Taximeter |
| 6 | `locations.count == 4` (multi-stop), driver at C (30 m) | Server price + waiting |
| 7 | `locations.count == 4`, waited 10 min at mid stop, ended at C | Server price + waiting (waiting included) |
| 8 | `locations.count == 4`, ended 5 km short of C | Taximeter |
| 9 | `locations.count == 2`, `lastLocation == nil` | Taximeter (safety fallback) |
| 10 | `endRadius` boundary: `distanceWay == 300 m` | Server price (≤ is inclusive) |
| 11 | `distanceTolerance` boundary: `abs(tracked - planned) == 1000 m` | Taximeter (< is exclusive) |
| 12 | `order.price == 0` (corrupt) | Server branch returns just `waiting` — log a warning, do not crash |

Verify the rounding helper (`roundPrice`) against Android's `Helper.roundPrice`: both must round to the nearest 100.

### Integration test (real backend)

Run on a TestFlight build with a staging driver account:

1. Accept a multi-stop offer (3+ stops).
2. Drive the planned route, end exactly at C.
3. Finish trip. Verify the fare equals `order.price + waiting`.
4. Run again, but end the trip 5 km away from C. Verify fare equals taximeter (lower).
5. Verify the bonus retry path: trigger the `ErrorOrderFinishResponse` flow by submitting with an outdated `clientTotalBonus`.

---

## 11. File-level checklist for the iOS engineer

- [ ] `PricingTokens` enum defined with `endRadius = 300` and `distanceTolerance = 1000`.
- [ ] `TaximeterSnapshot` struct mirrors the Android counters: `distanceInCity`, `distanceOutCity`, `totalServicePrice`, `totalWaitingPrice`, `totalTrackingPriceInCity`, `totalTrackingPriceOutCity`.
- [ ] `LocationTracker` exposes a `snapshot()` that returns a `TaximeterSnapshot`.
- [ ] `TripMVVM.computeTotalPrice(...)` matches the §5 reference.
- [ ] All three case branches log via `os_log` with `logTag = "APP-TripMVVM"` and case identifier (`case=1`, `case=3a/b`, etc.) so we can diff a driver report against Android.
- [ ] `RequestOrderFinish` Codable model matches §6 exactly, including the **string-typed** numeric fields.
- [ ] Distance from driver to C uses `CLLocation.distance(from:)`, not Pythagorean math.
- [ ] Promo / bonus parsers (`computePromo`, `computeBonus`) handle the **percent-suffix-vs-flat** ambiguity.
- [ ] HTTP 402 surfaces a payment-error alert (no retry).
- [ ] `ErrorOrderFinishResponse` triggers a fare-sheet re-render with corrected bonus settings.
- [ ] Post-success teardown stops `LocationTracker`, clears the route, dismisses sheets, navigates back to map idle.
- [ ] Unit tests for the 12 scenarios in §10.

---

## 12. Android cross-references

For the iOS engineer who wants to look at the source of truth:

| Android file | Purpose |
|---|---|
| [`presentation/maps/yandex_map/MapFragment.kt:1364–1643`](../app/src/main/java/uz/teamwork/mehrgodriver/presentation/maps/yandex_map/MapFragment.kt) | Orchestration: `prepareOrderFinish()`, `setViewDialogTrackingFinish()`, `orderFinish()` |
| [`presentation/maps/yandex_map/MapFragment.kt:1446–1506`](../app/src/main/java/uz/teamwork/mehrgodriver/presentation/maps/yandex_map/MapFragment.kt) | **The exact pricing block this spec ports** |
| `presentation/maps/yandex_map/MapViewModel.kt:65` | `orderFinish(orderId, request)` |
| `domain/use_case/main/OrderFinishUC.kt` | Repository wrapper |
| `domain/model/requests/RequestOrderFinish.kt` | Request body model |
| `domain/model/error/ErrorOrderFinishResponse.kt` | Bonus-correction error payload |
| `data/remote/ApiService.kt:212` | Retrofit endpoint definition |
| `common/Helper.kt:108–113` | `roundPrice(Long): String` — nearest-100 rounder |
| `common/Helper.kt:130–141` | `calculateBetweenTwoPoints(LatLng, LatLng): Float` — Android's `Location.distanceBetween` wrapper, identical semantics to iOS `CLLocation.distance(from:)` |

### Differences from Android worth flagging
- Android stores money as a mix of `Int`, `Long`, and `Float`. iOS standardises on `Int64`. Casts happen at the model layer and at the JSON boundary.
- Android uses `MyTrackingService` (a foreground service) for live distance accumulation. iOS uses `CLLocationManager` with background-location updates. The taximeter math is identical; only the lifecycle differs.
- Android persists in-flight taximeter counters to Room (`CalculationsDao`). iOS persists in Core Data (`CalculationStore`). Both rehydrate on relaunch mid-trip.

---

*Spec version: 1.0 — aligned with Android `mehrgo_driver_app` branch, version `1.9.2 (48)`. If the Android pricing rules change again, bump this spec and re-port.*
