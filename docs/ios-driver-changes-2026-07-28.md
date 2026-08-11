# iOS Driver — port the Android driver changes of 25–27 July 2026

**Read this whole brief before writing code.** You are given TWO projects:

- **The Android driver app (`mehrgo_driver_app` branch, v2.6.0 / build 66)** — the *reference and
  source of truth* for every behaviour below. All of it is built, and most of it is
  device-verified against the live backend (each section says which).
- **The iOS driver app (SwiftUI `MehrgoDriver`)** — the target. Same backend, same models, same
  brand.

This document covers **only what changed between 25 and 27 July 2026** (Android commits
`f269a1f3..ca72eb52`). It is a follow-up to `docs/ios-driver-parity-2026-07-22.md`; everything in
that earlier brief still applies **except where §4 below explicitly supersedes it**.

> ### ⚠️ This supersedes §4 of the 22-July brief
> The old brief said *"Driver may NOT cancel an order → show the support number instead."*
> **That is no longer true.** The driver **can** now cancel — with a reason — while the order is
> still in the *accepted* state. After the trip starts, the support-number behaviour stays.
> See **§4** for the exact gate. Do not implement the old rule.

---

## How to work (non-negotiable)

1. **Analyze both codebases first.** For each item, find the Android implementation (it is the
   spec), then find the corresponding iOS screen/flow, then port the behaviour idiomatically in
   SwiftUI — do not transliterate Kotlin.
2. **Each section names real iOS files and real symbols.** They were verified against the iOS
   tree while writing this. If a file has since moved, find its successor — do not invent a new
   architecture around it.
3. **Verify on the wire, never trust a comment or a field name.** When money, a unit or a status
   is involved, confirm the real request/response from a live order (Proxyman capture or backend
   logs) before you code the rule. Appendix A gives every contract this release touches.
4. **The backend is the single source of truth for money.** The app displays what the server
   computes; it does not compute the final fare itself.
5. **Every user-facing string ships in all supported locales.** Android carries uz / kk / ky / ru;
   iOS carries en / ru / uz. Each section lists the uz and ru text to copy.
6. **Quality bar: user-friendly, professional, best.** Match the Android UX, not a rough port.
7. **When something is blocked on the backend, say so and stop** — implement the best available
   client-side behaviour and flag the gap (see Appendix B). Do not fake a value.

### Priority

| § | Area | Priority | iOS counterpart today |
|---|---|---|---|
| 1 | Lenient JSON decoding | **P0** | exists, partially lenient — finish it |
| 2 | Withdrawal (Pul chiqarish) | **P0** | **does not exist — build it** |
| 3 | Multi-stop checkpoints + stop picker | P1 | exists, but wrong behaviour |
| 4 | Driver cancel before start | P1 | code exists but is unwired |
| 5 | GPS armed at accept | P1 | exists, starts too late |
| 6 | Human-readable errors + confirm dialog | P1 | error path exists; card screens do not |
| 7 | Order card footer + created_at + history id | P1 | exists |
| 8 | Service chips: no per-service prices | P1 | exists |
| 9 | Late-navigation guard + platform notes | P1 / Android-only | exists |

---

## 1. Lenient JSON decoding (the two production JSON crashes)

### What Android changed and why

Two Play-console crashes, same root cause: stock Gson throws `JsonSyntaxException` when a numeric/boolean leaf arrives as `""`, `null`, or the wrong JSON type. The use-case funnel only catches `HttpException`/`IOException`, so that `RuntimeException` escaped and hard-killed the process.

| # | Crash site | Thread | Trigger |
|---|---|---|---|
| A | `MyTrackingService` order/notification/pong receivers parsing a raw socket frame | main (LocalBroadcast dispatch) | malformed money/numeric field inside `SocketOrderResponse.data` |
| B | Retrofit/Gson decoding an element **inside a list** (earnings `graph.points[]`) | OkHttp async | `"earned": ""` / `null` on a non-null Kotlin field |

Fix = one shared lenient parser, `app/src/main/java/uz/teamwork/mehrgodriver/common/AppGson.kt` (branch `mehrgo_driver_app`), registered on **both** Retrofit converters, the injected error-body `Gson`, the socket parser, `RouteDistanceFetcher`, `OrderAdapter`'s ad-hoc route Retrofit — plus defensive `try/catch` around every unguarded `fromJson` of a socket/broadcast payload.

**Coercion contract** (this is what iOS must reproduce, leaf by leaf):

| Input JSON value | Non-optional field | Optional field |
|---|---|---|
| `25000`, `12.5`, `true` | parsed | parsed |
| `"25000"`, `"12.5"`, `"true"` (numeric/bool as string) | parsed | parsed |
| `""` (empty / whitespace string) | `0` / `0.0` / `false` | `nil` |
| `null` | `0` / `0.0` / `false` | `nil` |
| non-numeric garbage (`"abc"`, object, array) | `0` / `0.0` / `false` | `nil` |
| bool from number | `"1"`/`"1.0"` → `true`, `"0"`/`"0.0"` → `false` | same |
| Double-shaped value into an Int field | truncated (`12.9` → `12`) | truncated |

**Serialisation is deliberately unchanged** — ints still write without a decimal point, so the `order-gps/batch` body is byte-identical. Android has a unit test asserting parity with stock Gson output.

---

### iOS: current state (verified, not assumed)

Good news first — iOS is **structurally safer than Android was**:

* `MehrgoDriver/Shared/DynamicClient.swift:219` and `:278` are the only two REST decode points, and both are already inside `do/catch` → `ClientError.decoding`. **No decode failure can crash the app.** The failure mode is a silently empty screen or an error toast, not a `SIGABRT`.
* `MehrgoDriver/Shared/WebSocketManager.swift` `handle(_:)` (lines 154–223) parses every frame through `JSONSerialization` + `try?`/`do-catch` with `os_log` on failure. **The websocket path is already crash-guarded** — the Android `try/catch` hardening (MyTrackingService / MapFragment / OrdersMapFragment receivers) has **no iOS counterpart to write**. Do not port it.
* `MehrgoDriver/Shared/Models/SharedModels.swift:19-32` already ships `enum FlexDecode { double(_:_:) , int(_:_:) }`, used by ~18 models with hand-written `init(from decoder:)`. `DriverEarningsSummary` / `EarningsDay` (the crash-B family) are **already lenient** — no work there.

The real iOS exposure is different and, in one place, worse than a crash: **a `DecodingError` thrown inside `WebSocketManager.handle` swallows the whole order push.** The driver never sees the offer. That is lost revenue with zero diagnostic surface beyond an `os_log` line.

### The actual gaps to close

| # | File : line | Symptom when the backend misbehaves | Sev |
|---|---|---|---|
| 1 | `Shared/Models/SharedModels.swift:398` — `Order.init(from:)` does `try c.decode(Int.self, forKey: .id)` | `"id": "78188"` (string) or `null` → whole `Order` throws → `WebSocketManager.handle` logs and returns → **ride offer never reaches `HomeMVVM`**; the same throw empties `GET user/me → orders`, `my-orders`, active-order restore | P0 |
| 2 | Same strict `try c.decode(Int.self, forKey: .id)` in `Address:599`, `OrderService:655`, `Tariff:888`, `DriverNotification:1074` | one bad row throws → the **parent** `Order`/`Array` decode fails (a throw inside an element aborts the whole array) → empty services sheet / empty history / no tariff → fare falls back wrong | P0 |
| 3 | ~30 structs still on **synthesized** `Codable` with non-optional `let id: Int` and `Double?` money leaves: `AddressCategory:608`, `Contact:452`, `OrderStatusName:575`, `CarModel:823`, `CarColor:841`, `CarBrand:853`, `DistanceInterval:922`, `OrderCancelReason:1008`, `OrderAddress:1015`, `AddressInBranch:1023`, `Instruction:1089`, `Video:1100`, `OrderHistoryItem:1219`, `HistoryDate`, `PageMeta:1296`, `Introduce:1305`, `SubscriptionPlan:1362`, `SubscriptionTariff:1385`, `SubscriptionPurchased:1391`, `TodayStats:160`, `User:53` (`id: Int`, `balance/rating: Double?`), `RouteEntry:1409`, `RouteStep:1417`, `UpdateApp:1329`, `DeviceTokenResult:1350`, `SignUp:982` | `"price": ""` or `"balance": null` → **entire response** throws → screen renders empty with no error the driver can act on | P1 |
| 4 | `Order.useBonus` / `Order.isCardPayment` (`SharedModels.swift:421,423`) use `try? c.decode(Bool.self,…)` | server sends `1` / `"1"` → decodes to **`nil`, not `true`** → payment chip shows the wrong method, bonus silently not applied. Gson's `BoolAdapter` accepts `1`/`"1"`/`"true"` | P1 |
| 5 | `WebSocketManager.swift:170` `d["id"] as? Int`; `PushNotificationCenter.swift:121,145,148` `userInfo["order_id"] as? Int`, `balance_id`, `notify_driver_id` | APNs/`JSONSerialization` hands back a `String` when the backend quotes the value → cast fails → notification dropped / **push tap routes to Home instead of the offer** | P1 |

---

### Implementation

#### 1. New file `MehrgoDriver/Shared/Models/LenientDecoding.swift`

Two tools in one file: property wrappers (for structs on synthesized `Codable` — gap 3) and container helpers (for structs that already hand-write `init(from:)` — gaps 1, 2, 4). Do **not** convert 30 structs to hand-written decoders; that is what forced `Branch` and `OrderService` to also hand-write `encode(to:)`, and it is the main source of key-drift bugs in this file already.

```swift
import Foundation

// MARK: - Raw leaf coercion
// Single place where "what does the backend actually mean by this leaf" lives.
// Mirrors Android common/AppGson.kt: numeric-as-string parses, ""/null/garbage
// coerces, Double-into-Int truncates. Never throws.
enum LenientLeaf {

    static func int(_ c: SingleValueDecodingContainer) -> Int? {
        if let v = try? c.decode(Int.self)    { return v }
        if let v = try? c.decode(Double.self) { return Int(v) }
        if let s = try? c.decode(String.self) {
            let t = s.trimmingCharacters(in: .whitespaces)
            if t.isEmpty { return nil }
            return Int(t) ?? Double(t).map(Int.init)
        }
        return nil
    }

    static func double(_ c: SingleValueDecodingContainer) -> Double? {
        if let v = try? c.decode(Double.self) { return v }
        if let s = try? c.decode(String.self) {
            let t = s.trimmingCharacters(in: .whitespaces)
            return t.isEmpty ? nil : Double(t)
        }
        return nil
    }

    static func bool(_ c: SingleValueDecodingContainer) -> Bool? {
        if let v = try? c.decode(Bool.self)   { return v }
        if let v = try? c.decode(Double.self) { return v != 0 }
        if let s = try? c.decode(String.self) {
            switch s.trimmingCharacters(in: .whitespaces).lowercased() {
            case "true", "1", "1.0":  return true
            case "false", "0", "0.0": return false
            default:                  return nil     // "" and garbage
            }
        }
        return nil
    }
}


// MARK: - Property wrappers
// `@Lenient var id: Int = 0` — non-optional, coerces to the declared default.
// `@Lenient var price: Double?` — optional, coerces to nil.
// Encode is a straight pass-through of wrappedValue, so request bodies and the
// UserManager UserDefaults round-trip stay byte-identical.
@propertyWrapper
struct Lenient<Value: LenientDecodable>: Codable, Hashable {
    var wrappedValue: Value

    init(wrappedValue: Value) { self.wrappedValue = wrappedValue }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        wrappedValue = Value.lenient(from: c) ?? Value.lenientFallback
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(wrappedValue)
    }
}

protocol LenientDecodable: Codable, Hashable {
    static func lenient(from c: SingleValueDecodingContainer) -> Self?
    static var lenientFallback: Self { get }
}

extension Int: LenientDecodable {
    static func lenient(from c: SingleValueDecodingContainer) -> Int? { LenientLeaf.int(c) }
    static var lenientFallback: Int { 0 }
}
extension Double: LenientDecodable {
    static func lenient(from c: SingleValueDecodingContainer) -> Double? { LenientLeaf.double(c) }
    static var lenientFallback: Double { 0 }
}
extension Bool: LenientDecodable {
    static func lenient(from c: SingleValueDecodingContainer) -> Bool? { LenientLeaf.bool(c) }
    static var lenientFallback: Bool { false }
}
extension Optional: LenientDecodable where Wrapped: LenientDecodable {
    static func lenient(from c: SingleValueDecodingContainer) -> Wrapped?? {
        if (try? c.decodeNil()) == true { return .some(nil) }
        return .some(Wrapped.lenient(from: c))
    }
    static var lenientFallback: Wrapped? { nil }
}


// MARK: - Missing-key tolerance  (REQUIRED — do not skip)
// A property wrapper is decoded via `container.decode(Lenient<T>.self, forKey:)`,
// which throws `.keyNotFound` when the server simply omits the key. These two
// overloads make an absent key behave like an absent value.
extension KeyedDecodingContainer {
    func decode<T>(_ type: Lenient<T>.Type, forKey key: Key) throws -> Lenient<T> {
        (try? decodeIfPresent(Lenient<T>.self, forKey: key)) as? Lenient<T>
            ?? Lenient(wrappedValue: T.lenientFallback)
    }
}

extension KeyedEncodingContainer {
    // Optional-wrapped nils must stay OFF the wire, not be written as `null`.
    mutating func encode<T>(_ value: Lenient<T?>, forKey key: Key) throws where T: LenientDecodable {
        guard let v = value.wrappedValue else { return }
        try encode(v, forKey: key)
    }
}


// MARK: - Container helpers for hand-written decoders
// Use these in the ~18 models that already have `init(from decoder:)`.
extension KeyedDecodingContainer {
    func lenientInt(_ key: Key) -> Int? {
        guard let c = try? superDecoderLeaf(key) else { return nil }
        return LenientLeaf.int(c)
    }
    func lenientInt(_ key: Key, default d: Int) -> Int { lenientInt(key) ?? d }

    func lenientDouble(_ key: Key) -> Double? {
        guard let c = try? superDecoderLeaf(key) else { return nil }
        return LenientLeaf.double(c)
    }
    func lenientBool(_ key: Key) -> Bool? {
        guard let c = try? superDecoderLeaf(key) else { return nil }
        return LenientLeaf.bool(c)
    }

    private func superDecoderLeaf(_ key: Key) throws -> SingleValueDecodingContainer {
        try superDecoder(forKey: key).singleValueContainer()
    }
}
```

> `Hashable` on `Lenient` is load-bearing: `SharedModels.swift:1523-1558` declares `extension User: Hashable {}` etc. and synthesis requires every stored property to be `Hashable`.

#### 2. Retire `FlexDecode`, keep the call sites

`FlexDecode.double/int` (`SharedModels.swift:19-32`) is the same idea with two gaps: no `Bool`, and it treats `""` as a valid `String` that fails `Double("")` → returns `nil`, which is right for optionals but leaves non-optionals uncovered. Keep the enum as a two-line shim so the ~40 existing call sites don't churn:

```swift
enum FlexDecode {
    static func double<K: CodingKey>(_ c: KeyedDecodingContainer<K>, _ key: K) -> Double? { c.lenientDouble(key) }
    static func int<K: CodingKey>(_ c: KeyedDecodingContainer<K>, _ key: K) -> Int? { c.lenientInt(key) }
}
```

#### 3. Fix the strict `id` decodes (gaps 1 & 2) — five one-line edits

In `SharedModels.swift`, replace `try c.decode(Int.self, forKey: .id)` with `c.lenientInt(.id, default: 0)` at lines **398** (`Order`), **599** (`Address`), **655** (`OrderService`), **888** (`Tariff`), **1074** (`DriverNotification`).

Behaviour rule to keep in mind: **`id == 0` means "the server sent us something unusable"**. `HomeMVVM`/`TripMVVM` already treat order ids as truthy identifiers; add `guard order.id != 0` in `WebSocketManager.handle`'s order branch before `orderSubject.send(...)` so a garbage frame is dropped loudly rather than opening an offer sheet for order `0`.

Same file, line **421/423** (gap 4):

```swift
self.useBonus      = c.lenientBool(.useBonus)
self.isCardPayment = c.lenientBool(.isCardPayment)
```

#### 4. Adopt the wrapper on synthesized models (gap 3) — one worked example

Before (`SharedModels.swift:1219-1233`):

```swift
struct OrderHistoryItem: Codable, Identifiable {
    let id: Int
    let status: OrderStatusName?
    let date: HistoryDate?
    let myOrder: HistoryOrderRef?
    enum CodingKeys: String, CodingKey { case id, status; case date = "created_at"; case myOrder }
}
```

After — no `init(from:)`, no `encode(to:)`, `Codable` still synthesized, `Identifiable` still satisfied (`item.id` is still `Int`):

```swift
struct OrderHistoryItem: Codable, Identifiable {
    @Lenient var id: Int = 0
    let status: OrderStatusName?
    let date: HistoryDate?
    let myOrder: HistoryOrderRef?

    enum CodingKeys: String, CodingKey {
        case id, status
        case date = "created_at"
        case myOrder
    }
}
```

Apply the identical treatment to every struct listed in gap 3 — every non-optional `Int`/`Double`/`Bool`, and every `Double?` that carries money or distance (`price`, `total`, `balance`, `amount`, `distance`, `commission`, `bonus`, `rating`, `min_amount`). Strings need no wrapper. Nested model types (`Branch?`, `Tariff?`) need no wrapper — they are already `try?`-decoded or have their own lenient init.

`User` (line 51) is the one with a persistence side-effect: `UserManager.swift:38/61` encodes it into `UserDefaults` and decodes it back. Wrapping `id`/`balance`/`rating` is safe because `Lenient.encode` writes the raw value — the round-trip stays lossless. Verify with the `roundTrip` test below.

#### 5. Untyped-dictionary casts (gap 5)

Add next to the wrappers and use it at `WebSocketManager.swift:170` and `PushNotificationCenter.swift:121,145,148`:

```swift
func anyInt(_ v: Any?) -> Int? {
    switch v {
    case let n as Int:    return n
    case let n as Double: return Int(n)
    case let n as NSNumber: return n.intValue
    case let s as String: return Int(s.trimmingCharacters(in: .whitespaces))
    default: return nil
    }
}
func anyBool(_ v: Any?) -> Bool? {
    if let b = v as? Bool { return b }
    if let n = v as? NSNumber { return n.boolValue }
    if let s = v as? String { return ["true","1","1.0"].contains(s.lowercased()) }
    return nil
}
```

`PushNotificationCenter.routeTap` becomes `let orderId = anyInt(userInfo["order_id"])`, `let isPrivate = anyBool(userInfo["is_private"]) ?? (key == Constants.socketOrderNewPrivate)`, and the same for `balance_id` / `notify_driver_id`.

---

### Encode path — confirm unchanged

| Path | File | Uses `Codable` encode? | Impact |
|---|---|---|---|
| `POST order-gps/batch` body | `Shared/GpsBatchUploader.swift:388-402` `encode(_ point:)` builds `[String: Any]` → `JSONSerialization` in `DynamicClient.encodeBody` | **No** | zero — `ts`/`cpid` are `Int64`, JSONSerialization emits them without a decimal point. **Do not "tidy" this into `JSONEncoder`.** |
| `POST user/report` queue | `Persistence/ErrorRequestStore.swift:55` `JSONEncoder().encode(QueuedErrorReport)` | Yes | none — no wrappers needed on that struct |
| `User` cache | `Persistence/UserManager.swift:38` | Yes | pass-through encode keeps it lossless; covered by the round-trip test |
| `RequestOrderFinish`, `OrderCreateRequest` | `SharedModels.swift:1434,1460` | Yes | leave completely alone — `distance: Double` non-optional is deliberate (Yii2 `?float` rejects strings) |

---

### Tests — port Android's 7 cases

Android's contract test is `app/src/test/java/uz/teamwork/mehrgodriver/common/AppGsonTest.kt` (7 cases). New file `MehrgoDriverTests/LenientDecodingTests.swift` — the target globs the folder in `project.yml`, so it is picked up after `xcodegen generate`.

```swift
import XCTest
@testable import MehrgoDriver

final class LenientDecodingTests: XCTestCase {

    private struct Row: Codable, Hashable {
        @Lenient var earned: Double = 0
        @Lenient var distanceKm: Double = 0
        @Lenient var ordersCount: Int = 0
        @Lenient var flag: Bool = false
    }
    private struct Wrap: Codable { let byDay: Array<Row>
        enum CodingKeys: String, CodingKey { case byDay = "by_day" } }
    private struct Nullable: Codable {
        @Lenient var total: Int?
        @Lenient var price: Double?
        @Lenient var active: Bool?
    }

    private func decode<T: Decodable>(_ s: String, _ t: T.Type) throws -> T {
        try JSONDecoder().decode(t, from: Data(s.utf8))
    }

    // 1 — "" on non-optional leaves inside a LIST (crash B shape)
    func testEmptyStringNumericCoercesToDefault() throws {
        let r = try decode(#"{"by_day":[{"earned":"","distanceKm":"","ordersCount":"","flag":""}]}"#, Wrap.self).byDay[0]
        XCTAssertEqual(r.earned, 0); XCTAssertEqual(r.distanceKm, 0)
        XCTAssertEqual(r.ordersCount, 0); XCTAssertFalse(r.flag)
    }

    // 2 — null on non-optional leaves
    func testNullNumericOnNonOptionalCoercesToDefault() throws {
        let r = try decode(#"{"by_day":[{"earned":null,"distanceKm":null,"ordersCount":null,"flag":null}]}"#, Wrap.self).byDay[0]
        XCTAssertEqual(r.earned, 0); XCTAssertEqual(r.ordersCount, 0)
    }

    // 3 — valid values parse exactly (no silent rounding/coercion drift)
    func testValidValuesParseExactly() throws {
        let r = try decode(#"{"by_day":[{"earned":25000,"distanceKm":12.5,"ordersCount":3,"flag":true}]}"#, Wrap.self).byDay[0]
        XCTAssertEqual(r.earned, 25000); XCTAssertEqual(r.distanceKm, 12.5)
        XCTAssertEqual(r.ordersCount, 3); XCTAssertTrue(r.flag)
    }

    // 4 — numeric-as-string keeps working (this is the COMMON live shape)
    func testNumericAsStringStillParses() throws {
        let r = try decode(#"{"by_day":[{"earned":"25000","distanceKm":"12.5","ordersCount":"3","flag":"true"}]}"#, Wrap.self).byDay[0]
        XCTAssertEqual(r.earned, 25000); XCTAssertEqual(r.distanceKm, 12.5)
        XCTAssertEqual(r.ordersCount, 3); XCTAssertTrue(r.flag)
    }

    // 5 — optionals keep nil on blank/null, they do NOT become 0
    func testOptionalsKeepNilOnBlankOrNull() throws {
        let n = try decode(#"{"total":"","price":null,"active":""}"#, Nullable.self)
        XCTAssertNil(n.total); XCTAssertNil(n.price); XCTAssertNil(n.active)
    }

    // 6 — encode stays byte-identical: ints carry no decimal point
    func testEncodeIsByteIdenticalIntsHaveNoDecimal() throws {
        let enc = JSONEncoder(); enc.outputFormatting = .sortedKeys
        let out = String(data: try enc.encode(Row(earned: 25000, distanceKm: 12.5, ordersCount: 3, flag: true)), encoding: .utf8)
        XCTAssertEqual(out, #"{"distanceKm":12.5,"earned":25000,"flag":true,"ordersCount":3}"#)
    }

    // 7 — round-trip parity: encode → decode is lossless (User cache in UserManager)
    func testRoundTripParity() throws {
        let row = Row(earned: 25000, distanceKm: 12, ordersCount: 3, flag: false)
        XCTAssertEqual(try decode(String(data: try JSONEncoder().encode(row), encoding: .utf8)!, Row.self), row)
    }
}
```

Add two iOS-only regression tests beyond the Android seven — they cover the gaps Gson never had:

* `Order` decodes when `"id"` is `"78188"` (string) and when the `id` key is missing entirely (→ `id == 0`, event dropped by the new guard rather than crashing).
* `Order.isCardPayment` decodes `1` and `"1"` as `true` (currently `nil`).

---

### Edge cases / gotchas

* **Missing key ≠ null.** Without the `KeyedDecodingContainer.decode(Lenient<T>.Type…)` overload, every wrapper turns an omitted key into `.keyNotFound` and you have *introduced* the bug you set out to fix. Ship the overload in the same commit as the first adoption.
* **A throw inside an array element aborts the whole array.** That is why gap 2 is P0 and not cosmetic: one broken `order_items` row currently empties the services sheet.
* **Don't wrap `String` fields.** Gson's leniency is numeric/bool only; coercing a number into a `String` field would change display behaviour (e.g. `PromoUsage.amount` intentionally keeps `"10%"` as text).
* **Don't loosen `Order.state`, `OrderStatusName.value`, or `DriverStatus` mapping semantics** — those already go through `FlexDecode.int` and feed the state machine (`ACCEPTED=2`, `STARTED=7`, `CHANGED_ARRIVED=8`, `CHANGED_GONE=9`). A coerced `0` there must continue to read as "unknown state", never as a valid state.
* **`ErrorResponse.status: Int?`** (`SharedModels.swift:44`) is decoded with `try?` at `DynamicClient.swift:202/273`, so it already degrades to `nil`. No change needed.
* **No user-facing strings** in this area. The 8 new strings in the Android commit (`choose_address_hint`, `full_route`, `full_route_desc`, `or_single_stop`, `yes`, `no`, `max_withdraw_amount`, `withdraw_over_daily`) belong to other sections — nothing here reaches the UI. Log-only diagnostics stay in `os_log`.
* **Android-only, do not port:** the `try/catch` hardening in `MyTrackingService`, `MapFragment`, and `OrdersMapFragment` — these guard `LocalBroadcastManager` payload re-parsing, an Android-specific IPC hop. iOS delivers socket frames in-process to `WebSocketManager.handle`, which is already guarded.

---

## 2. Withdrawal (Pul chiqarish) — limits, sheet behaviour, request-history card

**iOS status: the whole Paylov card + withdrawal feature does not exist yet.** `MehrgoDriver/View/Main/Settings/BalanceView.swift` + `MehrgoDriver/MVVM/BalanceMVVM.swift` implement only *balance display + top-up* (Click / PayMe via `SFSafariViewController`). A repo-wide grep for `withdraw|paylov|user-card|balance/index` returns **zero** Swift/strings hits. So an iOS driver currently cannot cash out at all — this is money-path work, not polish. Everything below is greenfield on iOS; the Android commits (`b7338fab`, `43cc534a`) are the *refinements* on top, and they are folded into the spec so you build the final shape once.

### 0. Where it goes (iOS folder layout)

| File | Status | Contents |
|---|---|---|
| `MehrgoDriver/Shared/Models/PaylovModels.swift` | **new** | `PaylovCard`, `WithdrawalInfo`, `WithdrawalRequest`, `WithdrawalList` (`Codable`) |
| `MehrgoDriver/MVVM/BalanceMVVM.swift` | edit | add cards + `WithdrawalInfo` load, withdraw-sheet state, limit validation |
| `MehrgoDriver/View/Main/Settings/BalanceView.swift` | edit | add "Pul chiqarish" CTA, withdrawable line, pending-request card, two history rows |
| `MehrgoDriver/View/Main/Settings/WithdrawSheet.swift` | **new** | the sheet described in §2 |
| `MehrgoDriver/View/Main/Settings/WithdrawalHistoryView.swift` | **new** | "So'rovlar tarixi" list + the redesigned card (§3) |
| `MehrgoDriver/MVVM/WithdrawalHistoryMVVM.swift` | **new** | paging VM (mirror `HistoryMVVM`'s `page` / `hasMore` / `loadMore()` shape) |
| `MehrgoDriver/Localization/uz.lproj/Localizable.strings`, `ru.lproj/Localizable.strings` | edit | keys in §5 (`en.lproj` has no `Localizable.strings` today — English falls back to the key, so no en entry is required) |

### 1. Wire contract

All withdrawal routes live under `/api/paylov/…`, **not** under `/api/v1/`. `DynamicClient.buildURL` does a raw `base.url + path` string concat and `URLComponents` will **not** collapse `..` segments, so do *not* copy Android's `"../paylov/x"` relative-path trick. Add a third base instead:

```swift
enum ApiBase: String { case main, route, paylov
    var url: String { … case .paylov: return "https://\(Constants.BASE_URL)/api/paylov/" }
}
```

| Call | Method / path (base) | Params | Returns |
|---|---|---|---|
| Limits + pending | `GET withdrawal/info` (.paylov) | — | `BaseResponse<WithdrawalInfo>` |
| Saved cards | `GET user-card/cards` (.paylov) | — | `BaseResponse<CardsResponse>` → `.cards: [PaylovCard]` |
| Create request | `POST withdrawal/create` (.paylov, **form-urlencoded**) | `card_id`, `amount`, `note?` | `BaseResponse<WithdrawalRequest>` |
| Cancel own request | `POST withdrawal/cancel` (.paylov, form) | `id` | `BaseResponse<…>` |
| Request history | `GET withdrawal/index` (.paylov) | `page`, `per-page` (note the **hyphen**), Android uses 20 | `BaseResponse<WithdrawalList>` |
| Balance ledger (sibling tab) | `GET balance/index` (**.main / v1**, the one exception) | `page`, `per-page`, `type?` (1 income, 2 expense) | ledger items + `total_income` / `total_expense` |

`WithdrawalInfo` (snake_case → use a `CodingKeys` map or `.convertFromSnakeCase`):

| Field | Type | Meaning | 0 / nil means |
|---|---|---|---|
| `balance` | Int | raw balance, **so'm** | — |
| `withdrawable_balance` | Int | server-computed `balance − bonus_left − min_balance_left` | hard ceiling |
| `bonus_left` | Int | bonus part, not payable to a card | hide note |
| `min_balance_left` | Int | must REMAIN on balance | hide note |
| `min_amount` | Int | minimum per request | **no minimum** |
| `max_amount` | Int | maximum per single request | **no per-request cap** |
| `daily_limit` | Int | cap per calendar day | **no daily cap** |
| `today_withdrawn` | Int | already withdrawn today | — |
| `pending_request` | `WithdrawalRequest?` | the one open request | none open |

`WithdrawalRequest`: `id:Int`, `card_id:String`, `card_number:String` (raw masked digits, e.g. `860013******4751`), `amount:Int`, `approved_amount:Int?`, `status:Int`, `status_name:String?` (already localized by the server — **display this, don't build your own label**), `note:String?` (driver's), `admin_note:String?` (moderator's), `paylov_transaction_id:String?`, `created_at:Int` (**epoch SECONDS**), `processed_at:Int?`.

> Units: every withdrawal amount is whole **so'm** (Int). Only `PaylovCard.balance` is in tiyin — it is not used by this flow. `created_at` is epoch seconds, so `Date(timeIntervalSince1970: Double(createdAt))` (the order-history string parsing in `HistoryView.parsedDay` is *not* needed here).

**No limit is a client constant.** There is nothing in `Constants.kt` and there must be nothing in `Constants.swift`: min / max / daily / withdrawable all come from `withdrawal/info` on every open. Only the page size (20) is a client constant.

**Push:** the moderator's decision arrives as FCM `data.type == "paylov_withdrawal"` with `request_id` and `status` `"1"`/`"2"`. Android deep-links the tap to the requests-history screen. Do the same in `PushNotificationCenter` (route to `WithdrawalHistoryView`) and invalidate the cached `WithdrawalInfo` so the balance screen re-fetches.

### 2. The withdraw sheet

**Gating before it opens** (do this in the VM, from the last `WithdrawalInfo`):
- `pendingRequest != nil` → **don't** open the form. Show an alert titled `pending_request` with body `"<amount> so'm · <card_number>"`. One active request per driver.
- `cards.isEmpty` → toast `no_cards_yet`, don't open.

**Presentation (this is change (a)):** the sheet must open **fully expanded** and must never fall back to a half detent, because the number keypad otherwise hides the submit button and the last limit line.

```swift
.sheet(isPresented: $vm.showWithdraw) { WithdrawSheet(vm: vm) }
```
and inside the sheet:
```swift
.presentationDetents([.large])          // NOT [.medium] like the existing top-up sheet
.presentationDragIndicator(.hidden)     // it draws its own 40×4 grabber
.scrollDismissesKeyboard(.interactively)
```
Wrap the body in a `ScrollView` so the content is reachable when the keypad is up; `.large` + scroll is the SwiftUI equivalent of Android's `STATE_EXPANDED` + `skipCollapsed = true` + `SOFT_INPUT_ADJUST_RESIZE`.

**Layout, top to bottom** (matches `dialog_withdraw.xml`; reuse `Color.textColor` / `Color.grayFull` / `Color.canvasBackground`):

1. Grabber `40×4`, `Color.grayMiddleBlack`, capsule, top 12.
2. Title `withdraw_money`, 18pt bold, centered.
3. Subtitle = `withdrawable_amount` filled with `withdrawable_balance`, 13pt `grayFull`, centered, top 6.
4. Section label `my_cards`, 12pt medium `grayFull`, top 16.
5. Card list — one selectable row per `PaylovCard` (number + `bankName ?? vendor`, checkmark on the selected one, no delete affordance inside the sheet). Default selection = first card. 6pt gaps.
6. Section label `amount`, 12pt medium `grayFull`, top 16.
7. Amount field: `TextField` `.keyboardType(.numberPad)`, 22pt semibold, trailing static `sum` label 14pt `grayFull`, inside a 12-radius `canvasBackground` box (H14 / V12). **Live thousand grouping** while typing — reuse the exact `amountBinding` + `groupFormatter` pattern already in `BalanceView.swift` (store raw digits, render grouped).
8. **Limits block** — 12pt `grayFull`, one line each, joined by `\n`, hidden when empty:

   | Shown when | String key | Args |
   |---|---|---|
   | `min_amount > 0` | `min_withdraw_amount` | min |
   | `max_amount > 0` | `max_withdraw_amount` | max — **new in b7338fab**; it was already enforced but never shown, so hitting it silently greyed the button |
   | `daily_limit > 0` | `daily_limit_today` | daily limit, today_withdrawn |
   | `min_balance_left > 0` | `min_balance_note` | min_balance_left |
9. **Inline error line** (change (b)) — 12pt medium, `Color.brandRed`, top 6, hidden when `reason == nil`.
10. `PrimaryButton(title: "withdraw_money", isLoading: vm.isSubmitting, isEnabled: vm.canSubmit)`, top 20. `PrimaryButton` already renders the in-button spinner and the disabled gray.

**Validation rule — exact, order-sensitive.** Compute one optional reason; the *first* bound the amount actually breaks wins:

```
dailyRemainder = daily_limit > 0 ? max(0, daily_limit - today_withdrawn) : .max

reason:
  amount <= 0                                  -> nil        // nothing typed yet: neutral, no red line
  min_amount > 0 && amount < min_amount        -> min_withdraw_amount(min_amount)
  amount > withdrawable_balance                -> withdrawable_amount(withdrawable_balance)
  max_amount > 0 && amount > max_amount        -> max_withdraw_amount(max_amount)
  daily_limit > 0 && amount > dailyRemainder   -> withdraw_over_daily(dailyRemainder)
  otherwise                                    -> nil

canSubmit = amount > 0 && reason == nil && selectedCardId != nil
```

Notes / edge cases:
- `amount == 0` must show **no** error — an empty field is not a mistake yet.
- `withdrawable_balance` is checked **before** `max_amount`: a driver with 5 000 so'm and a 50 000 cap is told "Chiqarish mumkin: 5 000", not "Maksimum 50 000".
- `withdraw_over_daily` reports the **remainder** (`limit − today`), not the limit.
- A `0`/missing limit means "no limit" — never treat 0 as "you can withdraw nothing".
- Re-evaluate the reason on **every** keystroke *and* on card selection change.
- The reason text must be recomputed on locale change; build it from `String(format: LocalizedAppString(key), amount.asSumString)`.

**Submit:** `POST withdrawal/create`. On success → dismiss sheet, toast `withdraw_request_sent`, re-fetch `withdrawal/info` + cards. On failure → keep the sheet open, restore the button (retry must stay possible), surface the server message via `errorAlert` / toast. A `.timeout` from `DynamicClient` on a create is a possible double-submit — warn rather than silently retry.

**Balance screen (parent) additions:** `withdrawable_amount` line under the hero card; a gray note block joining `bonus_not_withdrawable` and `min_balance_note` when those are > 0; a pending-request card (`pending_request` + `"<amount> so'm · <card>"` + a cancel button hitting `withdrawal/cancel`, toast `withdrawal_cancelled` on success); the "Pul chiqarish" CTA rendered at 0.6 opacity while a request is pending; and two navigation rows → `withdrawal_history` / `balance_history`.

### 3. Request-history card ("So'rovlar tarixi" / "История заявок")

Screen: `WithdrawalHistoryView`, `navigationTitleBrand("withdrawal_history")`, two segmented chips (requests / balance) exactly like Android's `PaylovHistoryFragment`. Rows are grouped into **day sections** with the same header rule as `HistoryView`: `today` for today, else localized `"d MMMM"` via `LanguageManager.shared.locale` — headers **scroll with the list, not pinned** (use `LazyVStack` without `pinnedViews`). Paging: 20 per page, auto-fire from a `loadMoreFooter` `.task` (copy `HistoryMVVM.loadMore()`); stop when `loaded >= total` or a page comes back empty. **A failure on `withdrawal/index` must degrade to a quiet empty state (`history_empty`), not an error toast** — older backends 404 this route.

Card = `.orderCard(cornerRadius: 14, padding: 14)`, 10pt gap between cards.

```
┌──────────────────────────────────────────────┐
│ (● Tasdiqlandi)                        13:02 │   ← status pill (capsule) + Spacer + time
│                                              │
│  ╭────╮   50 000  →  40 000 so'm             │   ← 42pt round badge + amount 18pt bold
│  │ 💵 │   💳 9860 60** **** 9257              │      + masked card 13pt grayFull
│  ╰────╯                                       │
│                                              │
│ ┌──────────────────────────────────────────┐ │   ← optional note / rejection box
│ │ ⚠  Rad sababi: hujjat yetishmaydi        │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

- **Row 1** — `HStack`: status pill = `HStack(spacing:4)` of a 13pt icon + `status_name` at 12pt medium, `.padding(.horizontal,10).padding(.vertical,4)`, `.background(soft).clipShape(Capsule())`; then `Spacer()`; then `created_at` formatted `HH:mm`, 12pt `grayFull`.
- **Row 2** (top 12) — `HStack(spacing:12)`: a 42×42 `Circle().fill(Color.brandOrangeSoft)` with a 22pt cash glyph tinted `Color.brandOrange` (`banknote` / the Phosphor cash asset), then `VStack(alignment:.leading, spacing:3)` { amount, card line }. Card line = `HStack(spacing:6)` of a 15pt credit-card glyph `grayFull` + the masked number, 13pt `grayFull`. **Hide the whole card line when `card_number` is blank.**
- **Row 3** (top 12, optional) — note box: `HStack(alignment:.center, spacing:6)` of a 16pt icon + 13pt text, `.padding(10)`, rounded background.

**Card masking:** the server ships raw digits (`860013******4751`); render in groups of four separated by spaces → `8600 13** **** 4751`.

```swift
extension String { var groupedByFour: String {
    stride(from: 0, to: count, by: 4).map { i in
        String(self[index(startIndex, offsetBy: i)..<index(startIndex, offsetBy: min(i+4, count))])
    }.joined(separator: " ")
} }
```

#### Status → colour / icon (confirmed by the user — do not "improve" it)

| `status` | Meaning | Text + icon tint | Pill background | SF-ish glyph (Phosphor equivalent) |
|---|---|---|---|---|
| `0` (and any unknown) | pending / on review | `Color.brandOrange` (#F59E0B) | `Color.brandOrangeSoft` | clock (`ph_clock`) |
| `1` | approved | `Color.brandGreen` (#10B981) | `Color.brandGreenSoft` | check-in-circle (`ph_check_circle`) |
| `2` | **rejected** | `Color.brandRed` (#EF4444) | `Color.brandRedSoft` | x-in-circle (`ph_x_circle`) |
| `3` | **cancelled by driver** | `Color.grayFull` (#6B7280) | `Color.grayMiddleBlack` | minus (`ph_minus`) |

Cancelled is **grey on purpose** — it is neutral/inactive (the driver withdrew their own request), while rejected is the negative outcome. Do not collapse 2 and 3 into one red state. Unknown/`nil` statuses fall through to the amber pending style.

#### Amount rendering

- **Approved for a different sum** (`status == 1 && approved_amount != nil && approved_amount != amount`): one attributed string, three runs —
  `"50 000"` with `.strikethroughStyle(.single)` + `.foregroundColor(.grayFull)`, then `" → "` in `grayFull`, then `"40 000 so'm"` in `Color.brandGreen`. All at 18pt bold.

```swift
var s = AttributedString(Double(item.amount).asSumString)
s.strikethroughStyle = .single
s.foregroundColor = .grayFull
var arrow = AttributedString(" → "); arrow.foregroundColor = .grayFull
var ok = AttributedString("\(Double(approved).asSumString) \(LocalizedAppString("sum"))")
ok.foregroundColor = .brandGreen
Text(s + arrow + ok).font(.system(size: 18, weight: .bold))
```

- **Otherwise**: plain `"<amount> so'm"`, 18pt bold, coloured `Color.grayFull` when `status == 3` (cancelled reads as inactive) and `Color.textColor` in every other case.
- The `sum` key on iOS is `"so‘m"` with **no** leading space (Android's has one) — always compose with an explicit space.

#### Note box rule

`admin_note` (moderator) beats `note` (driver's own); show nothing if both are blank/whitespace.

| Condition | Text + icon | Background | Icon |
|---|---|---|---|
| `status == 2 && admin_note != nil` | `Color.brandRed` | `Color.brandRedSoft` | warning-circle (`ph_warning_circle`) |
| any other note | `Color.grayFull` | `Color.grayLight` (#F4F5F8) | chat-text (`ph_chat_text`) |

Note the exact predicate: a **rejected** request whose only text is the *driver's* `note` still renders **grey**, not red.

### 4. Sibling tab (out of scope here, named so you don't duplicate it)

The "Balans tarixi" tab in the same screen renders a different row (`reason_name`, `+`/`−` value in green/red, `remaining_after`) plus two tappable income/expense total pills that filter the ledger. It hangs off `GET balance/index` (v1). Build it in the same `WithdrawalHistoryView` host as a second segment.

### 5. Strings (uz / ru)

| Key | uz | ru | Note |
|---|---|---|---|
| `withdraw_money` | Pul chiqarish | Вывести деньги | sheet title + CTA |
| `withdrawable_amount` | Chiqarish mumkin: %@ so'm | Доступно к выводу: %@ сум | subtitle **and** the over-balance error |
| `min_withdraw_amount` | Eng kam summa: %@ so'm | Минимальная сумма: %@ сум | limit line + error |
| `max_withdraw_amount` | Bir martada eng ko'pi: %@ so'm | Максимум за раз: %@ сум | **new (b7338fab)** |
| `withdraw_over_daily` | Bugun yana %@ so'm chiqara olasiz | Сегодня можно вывести ещё %@ сум | **new (b7338fab)**, arg = daily remainder |
| `daily_limit_today` | Kunlik limit: %1$@ so'm (bugun: %2$@) | Дневной лимит: %1$@ сум (сегодня: %2$@) | two args |
| `min_balance_note` | Balansda kamida %@ so'm qolishi kerak | На балансе должно остаться не менее %@ сум | |
| `bonus_not_withdrawable` | Shundan %@ so'm — bonus, kartaga chiqarilmaydi | Из них %@ сум — бонус, не выводится на карту | balance screen note |
| `no_cards_yet` | Hali karta qo'shilmagan | Карта ещё не добавлена | |
| `pending_request` | Kutilayotgan so'rov | Заявка на рассмотрении | |
| `withdrawal_cancelled` | So'rov bekor qilindi | Заявка отменена | |
| `withdraw_request_sent` | So'rov qabul qilindi. Administratsiya tasdiqlagach pul kartangizga o'tadi | Заявка принята. После подтверждения администрацией деньги поступят на карту | |
| `withdrawal_history` | So'rovlar tarixi | История заявок | screen/tab title |
| `balance_history` | Balans tarixi | История баланса | sibling tab |
| `history_empty` | Hozircha yozuv yo'q | Пока нет записей | quiet empty state |
| `my_cards` | Mening kartalarim | Мои карты | already needed by the card picker |

Already present in `uz/ru.lproj`: `amount` (Summa / Сумма), `sum` (so‘m / сум), `today` (Bugun / Сегодня), `yes`, `no`, `cancel`. Android also ships `kk` and `ky` for all of the above; iOS ships uz/ru only, so ignore them.

### 6. Android-only — no iOS work

- `BottomSheetBehavior.skipCollapsed` / `SOFT_INPUT_ADJUST_RESIZE` / `WindowInsetsCompat` IME padding: Android plumbing. The iOS equivalent is just `.presentationDetents([.large])` + a `ScrollView`; do **not** port an insets listener.
- `ItemPaylovCardBinding` re-inflation on every selection change: an Android view-recycling workaround. SwiftUI's `ForEach` + `@State selectedCardId` covers it.

---

## 3. Multi-stop checkpoints: the 100 m prompt + destination picker

Android source of truth: `MapFragment.kt` (commit `e07e9c0e`, plus current `mehrgo_driver_app` head), `DestinationAdapter.kt`, `dialog_destination.xml`, `adapter_destination.xml`, `dialog_passing_next_destination.xml`.

No new backend endpoints, no new wire fields. Everything here is client-side state on top of `order.locations[].position / latitude / longitude / name` and the existing route API (`route/v1/driving/...`, already wrapped by `RouteService`).

### 1. The behaviour rule (what a 3-stop trip must do)

| # | Rule |
|---|---|
| R1 | The app holds ONE "current target stop" for the active order. Everything (drawn polyline, external-nav deep link, finish prompt) routes from that target onward. |
| R2 | The target NEVER advances silently. It advances only when the driver taps "Yes" on a confirmation prompt, or picks a stop manually in the picker. Silent proximity auto-advance was implemented, tested and **rejected by the user** — it advanced while the driver was still approaching the stop. |
| R3 | The prompt is armed by a repeating **10 s** check (not by the GPS stream), because the driver is *parked* at the stop when we need to ask and location callbacks go quiet. It reads the last known fix. |
| R4 | Prompt condition: rider aboard (`.changedGone`) **and** a later stop exists **and** driver is within **100 m** of the current target **and** the driver has not already said "No" for this stop **and** the prompt isn't already on screen. |
| R5 | "No" is remembered **per order + per stop** and never re-asks for that stop. The route is left untouched (still targeting that stop, full remaining route intact). |
| R6 | "Yes" advances the target by one, capped at the last stop, moves the target coordinate in the SAME transaction as the index, and rebuilds the route from the driver through the remaining stops. |
| R7 | The check also fires **immediately** when the trip screen (re)opens/refreshes, so a driver who parked with the app backgrounded is asked as soon as they look at the phone — not after a full 10 s tick. |
| R8 | A slow in-flight route build must never land after a newer one and snap the line back through an already-passed stop (generation token). |
| R9 | The map/nav button on a multi-drop trip that is under way opens a **stop picker** instead of firing the navigator; single-drop-off trips and the pre-pickup phase go straight to the navigator (no extra tap). |

### 2. What iOS does today, and why it must change

`MehrgoDriver/MVVM/TripMVVM.swift`:

- `@Published private(set) var nextStopIndex: Int = 1` (line 103) — index into the position-sorted `order.locations`.
- `advanceStopIndexIfReached(driverAt:)` (line 1105) is subscribed to `LocationTracker.shared.$lastLocation` and **silently bumps `nextStopIndex` at 80 m**, then shows a haptic + a `"passing_next_destination"` toast.

That is exactly the rejected Android behaviour, with three additional problems:

1. It is driven off the GPS sink, which goes quiet at a standstill — so a parked driver may never advance at all, or advance late.
2. `TripMVVM` is destroyed when the trip panel is minimised (see the comment in `HomeMVVM.swift:426` and `TripView.swift:191`), so a fresh mount restarts at `nextStopIndex = 1` and the target regresses. The current mitigation is a one-way high-water mark (`HomeMVVM.syncStopProgress` / `stopProgress(for:)`) that only feeds the *map*, not the nav target.
3. There is no way for the driver to override the target at all — no picker exists.

**Work:** delete the auto-advance, move the target index up a level, add the prompt and the picker.

### 3. Where to hold the current target (move it out of TripMVVM)

Put the multi-stop nav state on `HomeMVVM` (`MehrgoDriver/MVVM/HomeMVVM.swift`) — it survives panel minimise, already owns `activeOrder`, already subscribes `LocationTracker`, and already runs an equivalent always-on detector (`armDepartureConfirmIfNeeded`, line ~509).

```swift
// HomeMVVM — multi-stop nav target, keyed to activeOrder.
@Published private(set) var targetStopIndex: Int = 1      // index into position-sorted locations
@Published private(set) var routeThroughAllStops: Bool = true
@Published var shouldConfirmCheckpoint: Bool = false      // drives the prompt
private var targetOrderId: Int?                            // reset guard on order change
```

- Reset `targetStopIndex = 1`, `routeThroughAllStops = true`, `shouldConfirmCheckpoint = false` whenever `activeOrder?.id` changes (Android does the same in its "Kettik" handler: `destinationPosition = 1; routeThroughAllStops = true`).
- `TripMVVM.nextStopIndex` becomes a read-through of `homeVM.targetStopIndex` (or is deleted and every call site reads `homeVM` directly). Keep `TripView.mapRouteStopsCompleted` and `MapHomeView.routeStopsCompleted` sourcing from it via the existing `Order.completedChainStops(fullOrderCompleted:)` index-space conversion — do not bypass that helper, coordinate-less stops still must not shift the map chain.
- `HomeMVVM.syncStopProgress` / `activeOrderStopProgress` stay as-is for the map, but they are no longer the source of truth for navigation.

### 4. The 10 s check

Run it on `HomeMVVM`, off a repeating timer, **not** off the GPS sink:

```swift
private var checkpointTimer: Timer?

func startCheckpointWatch() {
    guard checkpointTimer == nil else { return }
    maybeArmCheckpointPrompt()                       // R7: immediate check
    checkpointTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
        Task { @MainActor in self?.maybeArmCheckpointPrompt() }
    }
}
func stopCheckpointWatch() { checkpointTimer?.invalidate(); checkpointTimer = nil }
```

- Start it when `activeOrder` becomes non-nil, stop it on `clearActiveOrder()`. The app already sets `allowsBackgroundLocationUpdates = true` (`LocationTracker.swift:112`), so the process stays alive and the timer keeps ticking while backgrounded — that is the point. Do **not** gate it on `scenePhase`.
- Also call `maybeArmCheckpointPrompt()` once from `TripView.onAppear` and from `.onChange(of: scenePhase)` when it becomes `.active`, and once after the order refresh that sets state to `.changedGone` — this is Android's extra immediate call inside its order-state handler.

Gate exactly as R4 (an `AsyncStream`/`Task.sleep` loop is equally fine; a `Timer` matches the existing `fareTimer` idiom):

```swift
private func maybeArmCheckpointPrompt() {
    guard let order = activeOrder,
          OrderState(rawValue: order.state ?? 0) == .changedGone,
          !shouldConfirmCheckpoint,
          !shouldConfirmDeparture else { return }
    let stops = (order.locations ?? []).sorted { ($0.position ?? 0) < ($1.position ?? 0) }
    guard targetStopIndex < stops.count - 1 else { return }         // already on the final leg
    let target = stops[targetStopIndex]
    guard let lat = target.latitude, let lon = target.longitude,
          let here = LocationTracker.shared.lastLocation?.coordinate else { return }
    guard Geometry.distanceMeters(here, CLLocationCoordinate2D(latitude: lat, longitude: lon))
            <= Constants.checkpointPromptRadiusMeters else { return }
    guard !isCheckpointSuppressed(orderId: order.id, position: target.position ?? targetStopIndex) else { return }
    shouldConfirmCheckpoint = true
    SoundPlayer.shared.play(.notification)
    HapticManager.notify(.warning)
}
```

Add to `MehrgoDriver/Shared/Constants.swift`, next to the existing `arrivePickupRadiusMeters = 500` / `finishDestinationRadiusMeters = 150`:

```swift
static let checkpointPromptRadiusMeters: Double = 100   // Android: 100 m
```

The checkpoint prompt and the finish prompt can never both be valid (finish is final-leg-only via `nextStopIndex >= stops.count - 1`, checkpoint is non-final-leg-only), but keep `!vm.shouldConfirmFinish` in the presentation gate anyway.

### 5. Prompt UI

Reuse the existing card — `MehrgoDriver/View/Main/Home/DepartureConfirmSheet.swift` already exposes `.departureConfirm(isPresented:titleKey:messageKey:icon:onYes:onNo:)`, which the finish prompt reuses at `TripView.swift:148`. Same chrome: dim backdrop, no tap-to-dismiss, "Yo'q" (neutral) + "Ha" (orange).

**Dual-attach exactly like the departure prompt** — the driver may be parked at stop B with the trip panel minimised:

- `TripView.swift`: `.departureConfirm(isPresented: homeVM.shouldConfirmCheckpoint && !homeVM.isTripMinimized && !vm.shouldConfirmFinish, ...)`
- `MainView.swift`: `.departureConfirm(isPresented: vm.shouldConfirmCheckpoint && vm.isTripMinimized, ...)`

Icon: `"location.north.fill"` (Android uses a horizontally mirrored near-me arrow, `scaleX="-1"`; SF Symbol needs no mirroring, do not add `.scaleEffect(x: -1)`).

`onYes` → `homeVM.advanceToNextStop()`; `onNo` → `homeVM.suppressCheckpoint()` (sets the flag, dismisses, leaves the route alone).

```swift
func advanceToNextStop() {
    guard let order = activeOrder else { return }
    let stops = (order.locations ?? []).sorted { ($0.position ?? 0) < ($1.position ?? 0) }
    let next = targetStopIndex + 1
    guard next <= stops.count - 1 else { shouldConfirmCheckpoint = false; return }  // R6 cap
    targetStopIndex = next            // index + coordinate move together: every
    shouldConfirmCheckpoint = false   // consumer derives the coord from the index
    bumpRouteGeneration()             // §7
}
```

Because the coordinate is *derived* from `targetStopIndex` (never stored separately), Android's "atomic position+location" requirement is satisfied by construction — do not introduce a parallel `targetCoordinate` stored property.

### 6. Per-stop "No" persistence

| | Android | iOS (implement) |
|---|---|---|
| Store | `SharedPreferences("checkpoint_prompt")` | `UserDefaults.standard` |
| Key | `cp_no_<orderId>_<position>` | `"checkpoint-no-\(orderId)-\(position)"` (kebab style matches `MapTypeManager`'s `"external-map"` keys) |
| Value | `Bool` — `true` = suppressed | same |
| Keyed on | the location's **`position` value**, not the list index | same — use `location.position ?? sortedIndex` so the semantics match Android across platforms |
| Written | on "No" | on "No" |
| Cleared | when the driver manually retargets that stop from the picker (`onDestinationClick` removes the key) | same |

Android never prunes these keys (order ids are unique, so a stale key is harmless). On iOS, optionally remove the keys for an order in `clearActiveOrder()` to bound `UserDefaults` growth — behaviourally a no-op.

### 7. Route mode + waypoints (`tripWaypoints` equivalent)

Android's `tripWaypoints(order)` reduces to two modes driven by `routeThroughAllStops`:

| Mode | Waypoints (state `.changedGone`) | Set by |
|---|---|---|
| Full route (default) | every stop with `position >= currentTarget.position` | fresh trip start; "Full route" row in the picker |
| Single leg | ONLY the current target stop (`position == currentTarget.position`) | tapping an individual stop row in the picker |
| Pre-pickup (`< .changedGone`) | every stop in order, unchanged | — |
| Fallback | if the filter yields nothing → the final stop | — |

On iOS this maps onto the two things that consume waypoints:

1. **In-app polyline** — `MapHomeView.routeWaypoints` (line 299) currently returns `order.orderedStopCoordinates` (the whole booked route, always). Change it to slice from `homeVM.targetStopIndex` and, in single-leg mode, to `[stops[targetStopIndex]]`. Keep the `count >= 2 ? stops : nil` guard by prepending nothing — `YandexMap` builds from the driver, so a one-element chain is valid; adjust the guard to `stops.isEmpty ? nil : stops`.
2. **External nav** — `TripView.navigationWaypoints` (line 535) + `navigationCoordinate` (line 499). Today `navigationCoordinate` always returns `order.dropoffCoordinate` for `.changedGone`; in single-leg mode it must return the *selected* stop and `navigationWaypoints` must be empty.

### 8. Generation token in Swift

The Yandex coordinator already implements this pattern (`fetchGeneration` at `YandexMap.swift:532`, `rerouteGeneration` at `:1556`, both checked in the completion handler before touching state) — copy that shape for any new async route work:

```swift
private var routeGeneration: Int = 0
func bumpRouteGeneration() { routeGeneration &+= 1 }

let gen = routeGeneration
let result = try await RouteService.shared.route(from: origin, to: target)
guard gen == routeGeneration else { return }   // superseded — drop it
apply(result)
```

**Existing bug to fix while you are here:** in `YandexMap.Coordinator.sync` (line 1649) a change to `routeStopsCompleted` seeds `reachedStopIndices` but does **not** bump `rerouteGeneration` or cancel `rerouteSession`. An off-route rebuild that was in flight when the target advanced can therefore land afterwards and redraw the line through the just-passed stop — the exact failure `activeRouteGen` was added on Android to kill. Bump the generation (and `rerouteSession?.cancel()`) inside that `if`.

### 9. The stops picker sheet (new file)

Create `MehrgoDriver/View/Main/Home/StopsPickerSheet.swift` (sits beside `TripServicesSheet.swift` / `CancelReasonSheet.swift`, presented from `TripView` with `.sheet(isPresented:)`, `.presentationDetents([.medium, .large])`, `.presentationDragIndicator(.visible)`).

**Entry point.** Replace `TripView.openDefaultNavigation()` on the Map circle (`circleActionsRow`, line 843) with:

```swift
let dropoffs = (vm.order.locations ?? []).filter { ($0.position ?? 0) > 0 }.count
if vm.currentState == .changedGone && dropoffs > 1 { showStopsPicker = true }
else { openDefaultNavigation() }
```

Keep the existing `.contextMenu` long-press map chooser untouched.

**Layout — one row per stop, plus a "Full route" row at the bottom:**

| Element | Spec |
|---|---|
| Row container | Rounded card, corner radius 14, fill `Color(.systemBackground)`, 1.2 pt stroke, 6 pt vertical gap between rows, 12 pt horizontal / 10 pt vertical inner padding |
| Stroke colour | `Color.brandOrange` when this row is the ACTIVE target (single-leg mode only), otherwise `Color.grayLight`. In full-route mode NO stop row is accented — the Full-route row is |
| Leading badge | 30×30 circle, fill `Color.brandOrangeSoft`, text = `Order.routeLetter(for: index)` ("A", "B", "C" — by list order, already exists in `Order+MapMarkers.swift:108`), 14 pt bold, `Color.brandOrange` |
| Title | 15 pt regular, `Color.textColor`, `lineLimit(2)`, truncate tail, takes remaining width. Row 0 renders `"\(name) (\(LocalizedAppString("client_address")))"`; empty name → `not_showed` |
| Trailing button 1 — in-app | Rounded square (radius 8) `Color.brandOrangeSoft`, 8 pt inset icon `"location.north.fill"` tinted `Color.brandOrange`. Routes to this stop INSIDE the app |
| Trailing button 2 — external | Same chip, icon `"map.fill"`. Opens this stop in the driver's chosen navigator |
| Full-route row | Identical card; badge shows `"arrow.triangle.swap"`/`"point.topleft.down.curvedto.point.bottomright.up"` instead of a letter; title = `full_route` (15 pt medium); the same two trailing buttons; accent stroke when `routeThroughAllStops == true` |
| Header | Centered icon + title `choose_address` (existing key), centered 13 pt `Color.grayFull` subtitle `choose_address_hint` |

**The removed radio-check.** Android deleted the filled-circle "selected" icon; selection is conveyed purely by the accent border. Do not add a checkmark.

**Row actions:**

| Action | Effect |
|---|---|
| Stop row → in-app button | `targetStopIndex = index`; `routeThroughAllStops = false`; clear that stop's suppression key; rebuild route (bump generation); dismiss the sheet; `homeVM.minimizeTrip()` so the driver actually sees the new line on the map |
| Stop row → external button | Open the driver's default navigator routed to THAT stop only (`ChooseMapMVVM.openExternalRoute(through: [], to:)`). Does **not** change the in-app target |
| Full route → in-app | `routeThroughAllStops = true`; rebuild; dismiss; `minimizeTrip()` |
| Full route → external | `routeThroughAllStops = true`; dismiss; `ChooseMapMVVM.openExternalRoute(through: navigationWaypoints, to: dropoff)` |

**Row A (the pickup) must be fully enabled.** Android had a `position > 0` guard that hid/disabled the pickup row; it was removed on purpose — the client may have forgotten something at A and the driver needs to route back. Do not re-introduce an index-0 guard on either button.

**No location yet:** if `LocationTracker.shared.lastLocation == nil`, both in-app buttons must show the `location_not_found` toast and no-op rather than routing.

### 10. External map apps

Android supports **Google Maps, Yandex Maps, Yandex Navigator, 2GIS, Waze** (`MapTypeManager`), and picks per-app waypoint support: Google/Yandex/Navi get all mid-stops; 2GIS and Waze get only the immediate next stop.

iOS is already at parity and needs **no change** here — `ExternalMap` (`Shared/DomainEnums.swift:110`) covers all five plus **Apple Maps** (iOS-only default, always installed), and `ChooseMapMVVM.buildURL` (line 238) already implements the identical per-app waypoint policy (`maps.apple.com` `daddr=A+to:B`, `https://www.google.com/maps/dir/?api=1&waypoints=`, `yandexmaps://…rtext=from~via~to`, `yandexnavi://build_route_on_map?lat_via_N`, and next-stop-only for `dgis://` and `waze://`). Just call the existing `openExternalRoute(through:to:)` overloads from the picker.

### 11. Strings

Add to `MehrgoDriver/Localization/uz.lproj/Localizable.strings` and `ru.lproj/Localizable.strings` (en.lproj has only `InfoPlist.strings` — no work there):

| Key | uz | ru |
|---|---|---|
| `checkpoint_confirm_title` | `Keyingi nuqtaga o'tish` | `Переход к следующей точке` |
| `checkpoint_confirm_message` | `Siz manzilga yetib keldingiz. Keyingi nuqtaga o'tishni hohlaysizmi?` | `Вы достигли места назначения. Хотите перейти к следующей точке?` |
| `choose_address_hint` | `Marshrutni qayerga quramiz?` | `Куда построить маршрут?` |
| `full_route` | `To'liq marshrut` | `Полный маршрут` |

Already present and reusable: `choose_address` (`Manzilni tanlang` / `Выберите адрес`), `not_showed`, `client_address` (add if missing: `Mijoz joylashuvi` / `Местоположение клиента`), `location_not_found` (`Joylashuvingiz topilmadi` / `Ваше местоположение не найдено`), `yes`, `no`, `map_type`.

**Delete** the now-unused `"passing_next_destination"` toast key (uz `Keyingi manzilga o'tildi` / ru `Переход к следующей точке`) together with the silent-advance toast — a past-tense "moved to next stop" notice contradicts the new confirm-first rule.

Android also added `full_route_desc` and `or_single_stop` but **never renders them** (they are not referenced by `dialog_destination.xml`) — skip them on iOS.

### 12. Edge cases checklist

- Prompt must not fire before the rider boards (`.changedGone` only) — a driver waiting at the pickup is within 100 m of stop A the whole time.
- Prompt must not fire on the final leg — that is the finish prompt's job (150 m, `Constants.finishDestinationRadiusMeters`).
- `lastLocation == nil` (cold start, no fix yet) → skip the check silently; the immediate-on-appear call will retry.
- Advance is capped at the last stop; a repeated "Yes" (double tap) must not push the index past the dropoff.
- Suppression must survive a panel minimise/restore and an app relaunch — that is why it is `UserDefaults`, not in-memory.
- Manually retargeting a stop clears its suppression, so the prompt can work again from that stop forward.
- After an advance, the drawn polyline, the external-nav target and the `routeStopsCompleted` fed to `YandexMap` must all move in the same update; do not let a stale in-flight reroute repaint the old chain (§8).
- A 2-stop (A→B) order must behave exactly as today: no picker, no checkpoint prompt, nav button fires the navigator directly.

### 13. Explicitly NOT iOS work

- Android's cancel-reason sheet rework, the in-button cancel spinner, and the `adapter_order.xml` / `dialog_order_cancel.xml` restyles that ride along in the same commit are separate areas.
- The `scaleX="-1"` mirroring of the near-me/navigation-arrow drawable is an Android asset fix; SF Symbols already point the right way.

---

## 4. Driver-initiated cancel before start (with reason)

### What changed on Android (commit `e07e9c0e`, `MapFragment.kt`)

The trip sheet's red **"Bekor qilish"** circle used to *always* open a "call the operator" popup — the driver literally could not cancel. It is now **state-gated**:

| `order.state` | Value | Tap behaviour |
|---|---|---|
| ACCEPTED | 2 | Load cancel reasons → show reason sheet → `POST order/cancel` |
| STARTED (going to pickup) | 7 | Contact-operator popup (no cancel) |
| CHANGED_ARRIVED | 8 | Contact-operator popup |
| CHANGED_GONE (rider aboard) | 9 | Contact-operator popup |

Rule, verbatim: `if ((order?.state ?: Int.MAX_VALUE) < 7) showReasonSheet() else showContactSupportDialog()`. A `nil`/unknown state falls to the **support** branch (fail-closed). The cancel affordance itself stays **visible in every state** — only the tap target diverges. The reason flow (`getOrderCanselReasons` → sheet → `orderCancel`) already existed as dead code and was simply re-pointed.

### Wire contract

**1. Fetch reasons** — fired lazily on tap, not preloaded.

| | |
|---|---|
| Method / path | `GET order-cancel-issue?type=1` |
| Query | `type=1` (fixed — driver-side reason set; `type` is not driver-configurable) |
| Auth | standard bearer + `Accept-Language` (server returns names already localised) |
| Response | `{ "data": [ { "id": Int, "name": String } ] }` |

`name` may be null in the payload → render `"—"`.

**2. Cancel** — form-url-encoded POST, **not** JSON, **not** the `socket/*` route (the socket routes 401 the driver bearer token).

| Field | Type | Required | Note |
|---|---|---|---|
| `order_id` | Int | yes | active order id |
| `issue_id` | Int | yes | id of the chosen reason |
| `lat` | Double | no | driver's fix at cancel time |
| `long` | Double | no | " |
| `accuracy` | Double | no | metres; omitted when the fix is invalid |

Path: `POST order/cancel`, `Content-Type: application/x-www-form-urlencoded`. Response body is ignored (`BaseResponse<Any>`); only HTTP success matters. A missing GPS fix must **never** block the cancel — send the call without the three location fields.

### iOS: what exists, what to do

Everything is already in the repo but **unwired** — `TripView.swift:1197` says *"servicesCancelRow removed … `CancelReasonSheet` and `TripMVVM.cancel(reasonId:)` are kept for an easy restore."* This is that restore.

| File | Work |
|---|---|
| `MehrgoDriver/View/Main/Home/TripView.swift` | Add the gated cancel affordance + `.sheet` presentation |
| `MehrgoDriver/View/Main/Home/CancelReasonSheet.swift` | Rebuild the body to the new card layout + in-button confirm |
| `MehrgoDriver/MVVM/TripMVVM.swift` | Add `isCancelling`; surface reason-load and cancel errors |
| `MehrgoDriver/Localization/{en,ru,uz}.lproj/Localizable.strings` | 2 new keys (see table) |

`OrderCancelReason` in `Shared/Models/SharedModels.swift:1007` (`id: Int`, `name: String?`, `Identifiable`, `Hashable`) already matches the wire shape — **no model change needed**. `TripMVVM.loadCancelReasons()` (line 840) and `TripMVVM.cancel(reasonId:)` (line 856) already hit the correct endpoints with the correct bodies, including `LocationTracker.shared.orderActionLocationParams` merged into the form body — **do not rewrite them**, only add the loading flag and error surfacing.

#### 1. The gate — `TripView.swift`

`vm.currentState` is `OrderState` (`Shared/DomainEnums.swift:25`, raw values 2/7/8/9). Add a computed gate next to `canNavigate`:

```swift
/// The driver may self-cancel ONLY before the trip starts (state 2).
/// From `.started` onward cancellation is operator-mediated.
private var canSelfCancel: Bool { vm.currentState == .accepted }
```

Add a **Cancel** circle to `circleActionsRow` (`TripView.swift:825`) as the last item, styled off the existing `circleAction(titleKey:systemImage:action:)` helper but in the red palette — `Color.brandRed` glyph on `Color.brandRedSoft` circle, SF Symbol `xmark`, caption key `cancellation`. Show it in **all** states (Android parity); the tap branches:

```swift
circleAction(titleKey: "cancellation", systemImage: "xmark", tint: .brandRed) {
    if canSelfCancel { showCancelReasons = true } else { callDispatcher() }
}
```

`circleAction` currently hard-codes `Color.brandOrange` / `Color.brandOrangeSoft` — add a `tint: Color = .brandOrange` parameter rather than forking the helper. Present with a new `@State private var showCancelReasons: Bool = false` and

```swift
.sheet(isPresented: $showCancelReasons) {
    CancelReasonSheet(vm: vm) { reasonId in await vm.cancel(reasonId: reasonId) }
}
```

Teardown is already wired: `TripView.swift:292` `.onChange(of: vm.didCancel)` clears `presentFinish` and calls `homeVM.clearActiveOrder()`. `TripMVVM.cancel` already stops trip tracking, purges the GPS outbox, deletes the local calculation, ends the Live Activity and toasts `order_cancelled_toast`. Nothing extra to add there.

**Operator branch delta:** `callDispatcher()` (`TripView.swift:443`) only reads `UserManager.shared.user?.branch?.dispatcherNumber` and toasts `not_defined` when absent. Android falls back to the brand support line (`Helper.dispatcherOrSupportNumber()` → `Constants.SUPPORT_PHONE_NUMBER`) and shows a **confirm popup with the formatted number + a Call button** rather than dialling immediately. Low-priority parity nit — dialling straight through is acceptable on iOS; the missing brand-support fallback is worth adding if iOS ever ships a support constant.

#### 2. The reason sheet — `CancelReasonSheet.swift`

The current implementation is a `List` with a nav-bar Confirm; the Android sheet is now **bordered cards + one full-width primary button**. Rebuild the body:

- `NavigationStack` → title `select_cancellation_reason`, `.navigationBarTitleDisplayMode(.inline)`, `.presentationDetents([.medium, .large])`, `.presentationDragIndicator(.visible)`. **Drop the leading "Cancel" toolbar button** — the sheet dismisses by swipe / tap-outside only.
- `ScrollView` → `VStack(spacing: 12)` of reason rows, `.padding(.horizontal, 16)`, over `Color.canvasBackground`.
- **Reason row**: `HStack` — reason text left (`.system(size: 15)`, `Color.textColor`, multiline, no truncation), `Spacer()`, radio glyph right (24pt). Row: `minHeight 56`, `.padding(.horizontal, 16)`, `.padding(.vertical, 14)`, background `Color.grayLight`, `RoundedRectangle(cornerRadius: 14, style: .continuous)`. Whole row is the tap target.
- **Radio glyph**: unselected `circle` in `Color.grayMid`; selected `checkmark.circle.fill` in `Color.brandOrange`. Radio sits on the **right**, text on the left (Android moved it there in this commit).
- **Selection is single-select and non-deselecting** — re-tapping the chosen row keeps it selected, it does not clear. `@State private var selected: Int?`.
- **Footer**: pinned below the scroll (`.safeAreaInset(edge: .bottom)`), 16pt horizontal / 12pt vertical padding, one `PrimaryButton`.

#### 3. In-button spinner — reuse `PrimaryButton`

`Utilities/PrimaryButton.swift` already implements exactly the Android `setCancelSubmitLoading` pattern: a `ZStack` where the label gets `.opacity(isLoading ? 0 : 1)` and a `ProgressView().progressViewStyle(.circular).tint(.white)` fades in over it, with `.disabled(!isEnabled || isLoading)`. **No new component.** Wire it:

```swift
PrimaryButton(
    title: "submit",
    isLoading: vm.isCancelling,
    isEnabled: selected != nil
) {
    guard let id = selected else { return }
    Task { await onConfirm(id) }
}
```

Add to `TripMVVM`:

```swift
@Published var isCancelling: Bool = false
```
set `true` at the top of `cancel(reasonId:)` and `false` in the `catch` (leave it `true` on success — the sheet is about to unmount with the order).

Sheet-lifecycle rules, matching Android exactly:

- The sheet stays **open** while the request is in flight. Only a **successful** cancel dismisses it (drive dismissal off `vm.didCancel` via `.onChange`, since `TripView` also tears the panel down on that flag).
- On **error**: restore the button (`isCancelling = false`), keep the sheet open so the driver can retry, and toast the message. `TripMVVM.cancel` already toasts `error.localizedDescription`; there is no `humanizeServerError` equivalent on iOS, so a raw snake_case key from the backend will reach the driver — acceptable for now, but a shared humaniser is the eventual fix.
- Disabled state carries the intent — Android additionally toasts `select_reason` when Confirm is pressed with nothing selected, which cannot happen on iOS because the button is disabled. Don't add the toast.
- **Reset on every open**: selection starts `nil` and the sheet re-fetches on `.onAppear { vm.loadCancelReasons() }`. Don't cache the list across opens.

### Edge cases

- **Reason fetch fails** — `loadCancelReasons()` currently only `os_log`s. Add a toast and, since the sheet is presented before the list lands, show an empty-list state (`EmptyStateView`) rather than a blank sheet. Android fetches *first* and only presents on success; presenting-then-loading is the better iOS pattern as long as the empty/error state is handled.
- **Empty reason list from the server** — `EmptyStateView`, Confirm stays disabled.
- **State advances while the sheet is open** (rider socket event / the driver's own "Boshlash" behind the sheet): re-evaluate the gate — `.onChange(of: vm.currentState) { if $1 != .accepted { dismiss() } }`, otherwise a cancel can land against a started trip.
- **Order cancelled by the client under the sheet** — the existing socket cancel path clears the active order and unmounts `TripView`, taking the sheet with it. No extra handling.
- **Taximeter orders** — Android leaves the cancel circle visible for driver-created taximeter orders too (unlike Call/Services, which it hides). Match that.
- **No GPS fix** — `orderActionLocationParams` returns `[:]`; the POST goes out with just `order_id` + `issue_id`. Already correct in `TripMVVM.cancel`.

### Strings

Existing iOS keys to reuse: `cancellation` *(add if absent)*, `select_reason`, `order_cancelled_toast`.

| Key | uz | ru | en (suggested) | Status |
|---|---|---|---|---|
| `select_cancellation_reason` | `Buyurtmani bekor qilish sababini tanlang` | `Укажите причину отмены заказа` | `Choose a cancellation reason` | **new** — sheet title |
| `submit` | `Tasdiqlash` | `Подтвердить` | `Confirm` | **new** — primary button (or reuse existing `confirm_cancel`, same text) |
| `cancellation` | `Bekor qilish` | `Отменить` | `Cancel order` | **new** — circle caption |
| `select_reason` | `Sababni tanlang` | `Выберите причину` | `Select a reason` | exists (uz 182 / ru 181) |
| `order_cancelled_toast` | `Buyurtma bekor qilindi` | `Заказ отменён` | `Order cancelled` | exists (uz 153 / ru 163) |

`confirm_cancel` (uz `Tasdiqlash` / ru `Подтвердить`) already exists and is identical to `submit` — reuse it instead of adding a duplicate key if you prefer.

---

## 5. GPS track upload armed at ACCEPT (`with_timer` decouple)

### What changed on Android

Until now the driver app only started feeding `order-gps/batch` when the driver pressed **"Boshlash"** (order → `started (7)`). Everything between *accept* and *Boshlash* — the driver→pickup approach leg — never reached the backend, so the server had no record of how far the driver actually drove to reach the client.

Commit `b7338fab` splits the single "start tracking" command into two independently-armed concerns:

| Concern | Old trigger | New trigger |
|---|---|---|
| GPS points uploaded to `order-gps/batch` | Boshlash (state 7) | **Accept (state 2)** |
| Trip execution timer (`execution_time`) | Boshlash | Boshlash (unchanged) |
| Local on-screen distance meter | Kettik / Go (state 9) | Kettik / Go (unchanged) |

Mechanically, the tracking-start intent gained a boolean `with_timer`. `commandArmGpsFromAccept()` sends it as `false` (turn tracking + uploader on, do **not** start the execution timer); `commandStartTracking()` at Boshlash sends the default `true`. The service guards the timer with a `tripTimerRunning` flag so the second call only kicks the timer and does not re-arm anything already running.

The resume / cold-open branch is now:

```
if order.state >= 7  -> start tracking WITH timer
else if state >= 2   -> arm GPS upload only (no timer)
```

This runs on every active-order refresh (the 10 s poll), not just once — arming is idempotent, so re-issuing it is free.

### Wire contract

**Endpoint:** `POST {BASE_URL}order-gps/batch?order_id={id}` — `BASE_URL` is `https://<host>/api/v1/`. Response is `BaseResponse<FareSummary>` (the live fare ack). Nothing about the endpoint or the payload changed; only *when the first request is sent*.

**Request body**

| Field | Type | Notes |
|---|---|---|
| `points` | array | ≤ 50 per request (`BATCH_LIMIT`) |
| `waiting_time` | int (ms) | cumulative pickup wait; sent **only when changed** since last successful send. Android-only today — see "adjacent gap" below |
| `waiting_time_ontheway` | int (ms) | cumulative on-route wait; same "only when changed" rule |

**Point object** (optional fields omitted entirely when unknown — never `null`, never a sentinel):

| Field | Type | Unit / meaning |
|---|---|---|
| `lat` | double | required |
| `lon` | double | required |
| `ts` | int64 | **unix epoch milliseconds**, required |
| `cpid` | int64 | **required.** Per-order monotonic client point id. Server idempotency key (`ON CONFLICT DO NOTHING`) — replaying the same `cpid` after a retry/crash cannot inflate distance or fare |
| `accuracy` | double | metres, horizontal |
| `speed` | double | **m/s** (the server multiplies by 3.6 itself) |
| `bearing` | double | degrees |
| `altitude` | double | metres |
| `provider` | string | `"gps"` / `"network"` |

**Cadence** (identical on both platforms today, do not change):

- flush every **15 s**, OR
- flush as soon as **20** points have buffered since the last flush, OR
- explicit flush on trip end / Go / a wait tick.

**Client-side point filter** (applied before persisting, so filtered points never consume a `cpid`):

| Rule | Value |
|---|---|
| Hard accuracy ceiling — always drop | > 150 m |
| Soft accuracy gate — drop unless cadence-forced | > 50 m |
| Moving cadence floor — force a point at least this often while moving | 6 s |
| Min displacement to send | 50 m |
| Min bearing change to send | 15° |
| "Moving" threshold | 1.94 m/s (≈ 7 km/h) |
| stop ↔ move transition | always sent (it is the server's waiting boundary) |

**Failure handling** (unchanged, restated because arming earlier makes the 403 window bigger):

| HTTP | Treatment |
|---|---|
| 400 | payload structurally bad → drop that batch, keep newer points, retry next cycle |
| 403 / 404 | **transient.** Retry the *same* cpids. Only after **5 consecutive** rejects latch the uploader off until a lifecycle transition re-arms it |
| other 4xx (401 …) | terminal — stop until re-armed |
| 5xx / network | transient, exponential backoff 2 s → 60 s (capped) |

Arming at accept means the very first batches now land while the order is still at state 2. If the backend only whitelists ingest from state 7, those batches come back 403 — which is exactly what the 5-reject tolerance absorbs, but it also means **the whole approach leg would be silently discarded**. Confirm ingest is open at state 2 before shipping.

### ⚠️ Caveat — approach-leg bucketing is NOT confirmed in writing

The Android code comments state that the server buckets points by the order's state at upload time:

- points uploaded while the order is pre-`changedGone` land in **`to_client_km`** — diagnostic, **not billed**;
- points uploaded after the passenger boards land in **`distance_km`** — **billed**.

This is the *stated* rationale for why sending the approach leg does not over-bill. **It was verified for driver-created / taximeter orders only. For regular (client-created) orders the bucketing was never confirmed in writing by the backend team.** If the server folds pre-boarding points into `distance_km` for regular orders, arming at accept will over-bill every ride by the length of the driver's approach.

**Action for the iOS dev: confirm with Muhammad (backend owner) that `order-gps/batch` ingest is open at order state 2 and that pre-`changedGone` points are bucketed into `to_client_km` for regular orders, before enabling accept-time arming in a build that reaches real drivers.** Ship it behind a flag or hold it until the answer is in writing. The local iOS meter is unaffected either way — this is purely about what the server bills.

A cheap way to verify on device once ingest is enabled: read `live.to_client_km` vs `live.distance_km` from the batch ack after a long approach (see "adjacent gap" — iOS does not decode the `live` block yet).

### iOS work

The counterpart already exists — this is a small, precise change, not a new subsystem.

| File | Current behaviour | Change |
|---|---|---|
| `MehrgoDriver/Shared/LocationTracker.swift` | `isBatchUploadEnabled` opened by `enableBatchUpload()`, first called from `markOrderStarted()` (state 7). `startTripTracking(orderId:branchPolygon:)` starts CoreLocation + the 1 s `ticker` immediately | add `markOrderAccepted()`; gate the execution-time accrual |
| `MehrgoDriver/MVVM/TripMVVM.swift` | `restoreLifecyclePhase()` `case .accepted: break` — nothing armed | call `markOrderAccepted()` |
| `MehrgoDriver/Shared/GpsBatchUploader.swift` | already implements the exact contract above | no change |
| `MehrgoDriver/Persistence/GpsBatchStore.swift` | CoreData outbox, atomic cpid allocation | no change |

**1. `LocationTracker` — add the accept-time arm**

```swift
/// Order is ACCEPTED (2). Opens the GPS batch-upload gate ONLY, so
/// `order-gps/batch` carries the driver→pickup approach leg. Does NOT start
/// the execution timer or the billed-distance meter — those begin at
/// "Boshlash" (`markOrderStarted`) and "Kettik" (`markPassengerOnboard`).
func markOrderAccepted() {
    enableBatchUpload()
}
```

`enableBatchUpload()` is already idempotent (`guard !isBatchUploadEnabled`) and already binds `GpsBatchUploader.shared.start(orderId:)`, so calling it from a re-entrant path is safe. Nothing else in the arming path needs to move.

**2. `LocationTracker` — decouple the execution timer (this is the `with_timer` half)**

Today `startTicker()` runs from `startTripTracking(...)`, i.e. from the moment `TripView` mounts — which is *at accept*. So `trackedTimeMs` already accrues during the accepted stage, and it is submitted verbatim as `execution_time` on `order/complete` (`TripMVVM.finish()`). Once you arm GPS at accept you must NOT also let the timer keep its accept-time start, or Android and iOS will report different `execution_time` for the same ride.

Do **not** move `startTicker()` — the 1 s tick also drives wait-segment open/close and the persistence flushes. Instead gate only the accrual:

```swift
private var executionTimerRunning: Bool = false

func markOrderStarted() {
    enableBatchUpload()
    executionTimerRunning = true            // idempotent
}

// in tick():
if executionTimerRunning { trackedTimeMs &+= dtMs }
```

Reset `executionTimerRunning = false` in `stopTripTracking()` and in the non-re-entry branch of `startTripTracking(...)`, next to the other transient flags (`isWaiting`, `wayStarted`, `isBatchUploadEnabled`) — mirrors Android clearing `tripTimerRunning` in its teardown so the *next* order can start its timer even when the previous one was accept-armed and never started.

`markArrivedAtPickup()` and `markPassengerOnboard()` must also set `executionTimerRunning = true` — they already call `enableBatchUpload()`, and a cold relaunch at state 8/9 must not leave the timer dead.

**3. `TripMVVM.restoreLifecyclePhase()`**

```swift
case .accepted:
    // Arm GPS upload from ACCEPT so the driver→pickup approach leg reaches
    // the backend. No timer, no billed distance — those start at Boshlash /
    // Kettik. (Android parity: commandArmGpsFromAccept, with_timer=false.)
    LocationTracker.shared.markOrderAccepted()
```

`restoreLifecyclePhase()` already runs after `beginTracking()` on every `TripMVVM` init, and `MainView` mounts `TripPanel → TripView → TripMVVM` for the whole life of `HomeMVVM.activeOrder`, so this covers accept, resume-from-Active-orders, and cold relaunch mid-approach. No change is needed in `OrderOfferMVVM.accept()`.

**4. Queued orders — do nothing**

`HomeMVVM.handleAccepted(_:)` parks a second accepted order in `queuedOrder` with no `TripMVVM`. That order must **not** arm — `GpsBatchUploader.start(orderId:)` rebinds the singleton to a new order id and would orphan the active trip's outbox drain. Android behaves the same way (its service tracks exactly one order id). Leaving the queued path untouched is the correct behaviour, not an omission.

### iOS background-location considerations

Already correct in the repo — verify, do not re-add:

- `MehrgoDriver/Info.plist` → `UIBackgroundModes` contains `location`. ✅
- `LocationTracker.init()` sets `allowsBackgroundLocationUpdates = true`, `pausesLocationUpdatesAutomatically = false`, `activityType = .automotiveNavigation`, `desiredAccuracy = kCLLocationAccuracyBestForNavigation`, `showsBackgroundLocationIndicator = true`. ✅

What arming earlier actually stresses:

- **The approach leg is the longest backgrounded stretch of a ride.** The driver accepts, backgrounds the app, and drives to the pickup with the screen off or a navigator in front. `flushTimer` is a main-runloop `Timer` and is frozen while suspended — the drain therefore has to come from the location callback. `GpsBatchUploader.onEnqueued()` already handles this (it evaluates both the burst trigger and a `flushInterval`-elapsed staleness trigger inside the callback Task). Keep that path; do not "simplify" the double trigger away.
- **`Always` authorization is required** for the approach leg to keep producing fixes with the app backgrounded. `requestPermission()` escalates `whenInUse → always`; if the driver is stuck on `whenInUse`, accept-time arming yields a truncated approach track rather than a wrong one. Do not block accept on it.
- **Unsent points survive suspension already.** Every fix that passes the filter is written to the CoreData outbox via `GpsBatchStore.appendNext(...)`, which allocates the `cpid` atomically with the insert. On relaunch the uploader replays with the original `cpid`, so the server de-dupes. `AppDelegate` purges outbox rows older than 7 days; `GpsBatchStore.purge(orderId:)` is called on finish/cancel. Arming at accept only widens the window in which rows exist — no new persistence work is needed.
- The `uz.teamwork.mehrgodriver.location.upload` BGTask path (`LocationTracker.uploadBatchToServer()`) posts a single fix to `location/send` (dispatcher heartbeat) — it is unrelated to `order-gps/batch` and needs no change.

### Edge cases

- **Cold relaunch during the approach.** `TripMVVM` init → `beginTracking()` → `restoreLifecyclePhase()` → `.accepted` → arm. The outbox replays with the original cpids; distance cannot double-count.
- **Accept → cancel before Boshlash.** `TripMVVM`'s cancel path already runs `stopTripTracking()` + `GpsBatchStore.purge(orderId:)`; with accept-time arming there will now genuinely be rows to purge. Verify the purge fires on both the socket-cancel and the driver-cancel paths (`HomeMVVM` line ~731 handles the minimised case).
- **Persistent 403 streak while still at state 2.** `stopRequestedSubject` fires → `TripMVVM` stops the uploader and calls `disableBatchUpload()`, which clears the gate so `markOrderStarted()` at Boshlash re-arms it. That means "backend refuses pre-start ingest" degrades gracefully to today's behaviour instead of breaking the trip. Confirm this path still re-arms after you add `markOrderAccepted()`.
- **Driver-created / taximeter orders (`order.from == 1`).** Android opens the point gate immediately for these because there is no approach leg. iOS's upload gate is already independent of `wayStarted`, so nothing extra is required; iOS has no driver-created-order flow today anyway.
- **No new user-facing strings.** This change is entirely silent — the four Android string additions in this commit range (`choose_address_hint`, `full_route`, `yes`/`no`, `max_withdraw_amount`, …) belong to other areas.

### Adjacent gap worth flagging (not this area's work)

`MehrgoDriver/Shared/Models/GpsBatch.swift` — `GpsBatchRequest` sends only `points`, and `FareSummary` decodes only the flat top-level fields. Android additionally sends `waiting_time` / `waiting_time_ontheway` in the batch body and decodes a nested `live` block containing `distance_km`, **`to_client_km`**, `waiting_sec`, `on_way_sec`, `waiting_cost`, `price`, plus an `events` array. Until iOS decodes `live.to_client_km` there is no in-app way to verify the approach/trip bucketing described in the caveat above — worth adding `to_client_km` even as a debug-only field while validating this change.

---

## 6. Human-readable server errors + the reusable confirm dialog

Two Android commits: `b7338fab` (added `Helper.humanizeServerError`, wired into the two add-card screens) and `43cc534a` (added `InfoPopup.confirm` + `InfoPopup.Loader`, the two-button `dialog_info_popup.xml`, and rewired delete-card).

### 1. Why this exists (the bug)

The backend does **not** guarantee that `message` in a non-2xx body is a human sentence. For some Paylov card operations it returns a bare machine key, and the app toasted it verbatim — the driver saw literally `pinfl_not_match`. Confirmed keys so far:

| raw `message` value | what actually happened |
|---|---|
| `pinfl_not_match` | The card being added belongs to someone else — the card owner's PINFL ≠ the account holder's PINFL. |
| `card_is_blocked` | The card is blocked by the issuing bank / Paylov. |

There is no separate error-code field on the wire; the key arrives in the same `message` string slot that normally carries a sentence. So the fix is a **string-level** sanitiser, not a code-based one.

Error body shape (unchanged, already modelled on iOS as `ErrorResponse` in `MehrgoDriver/Shared/Models/SharedModels.swift`):

| field | type | note |
|---|---|---|
| `status` | Int? | HTTP-ish status echo |
| `message` | String? | **either** a human sentence **or** a machine key |

### 2. Behaviour rule — `humanize(raw:)`

Exactly three outcomes, evaluated in this order:

1. `raw` is nil / empty after trimming → **generic server-error string** (`error_server`).
2. `raw.lowercased()` matches a **known key** → that key's localized friendly sentence.
3. Otherwise, `raw` **"looks like a machine key"** → generic server-error string. The heuristic is all three of:
   - contains `_`, **and**
   - contains **no** whitespace character anywhere, **and**
   - is already all-lowercase (`raw == raw.lowercased()`).
4. Anything else (a genuine sentence — it has spaces, or capitals, or no underscore) → **passed through untouched**. Server-authored human messages must keep reaching the driver; this layer only suppresses leaked keys.

The point of rule 3 is that an *unknown* key is still never fit to show. New keys the backend invents degrade gracefully instead of leaking.

### 3. iOS: put it at the one choke point

iOS already funnels **every** API failure through a single computed property, so no call-site edits are needed. In `MehrgoDriver/Shared/DynamicClient.swift`:

```swift
enum ClientError: Error, LocalizedError {
    case server(status: Int, message: String, body: Data?)
    ...
    var errorDescription: String? {
        switch self {
        case .server(_, let m, _): return m      // <- the leak
        ...
```

All 44 `errorMessage = error.localizedDescription` / `ToastCenter.shared.show(error.localizedDescription, style: .error)` sites across `MVVM/` read that property. Fixing it there fixes the whole app in one line.

**New file — `MehrgoDriver/Shared/ServerMessage.swift`:**

```swift
import Foundation

/// The backend sometimes puts a raw machine key (e.g. "pinfl_not_match") in the
/// error body's `message` instead of a human sentence. Map the keys we know to a
/// localized sentence; degrade any *other* bare snake_case token to the generic
/// server error; pass genuine human messages through untouched.
enum ServerMessage {

    /// raw wire key -> Localizable.strings key
    private static let known: [String: String] = [
        "pinfl_not_match": "server_error_pinfl_not_match",
        "card_is_blocked": "server_error_card_is_blocked",
    ]

    static func humanize(_ raw: String?) -> String {
        let msg: String = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !msg.isEmpty else { return LocalizedAppString("error_server") }

        if let key = known[msg.lowercased()] { return LocalizedAppString(key) }

        let looksLikeMachineKey: Bool =
            msg.contains("_")
            && msg.rangeOfCharacter(from: .whitespacesAndNewlines) == nil
            && msg == msg.lowercased()

        return looksLikeMachineKey ? LocalizedAppString("error_server") : msg
    }
}
```

Use `LocalizedAppString(_:)` (defined in `MehrgoDriver/Persistence/LanguageManager.swift:107`), **not** `NSLocalizedString` — the app's in-app language override lives in `UserDefaults["app-language"]` and only `LocalizedAppString` respects it. Do **not** port Kotlin's `when (LanguageManager.getLanguage())` switch; on iOS the language fan-out belongs in `Localizable.strings`.

**Edits required:**

| file | change |
|---|---|
| `MehrgoDriver/Shared/DynamicClient.swift` | `case .server(_, let m, _): return ServerMessage.humanize(m)` |
| `MehrgoDriver/Shared/DynamicClient.swift` (×2 — JSON path and multipart path) | The fallback `message: msg ?? "Server error \(http.statusCode)"` builds an English developer string that will now pass the heuristic (it has spaces) and reach the driver. Change both to `message: msg ?? ""` and let `humanize` turn empty into `error_server`. |
| `MehrgoDriver/MVVM/TripMVVM.swift:~784` | `catch let ClientError.server(status, message, _) where status == 402 { paymentErrorMessage = message }` reads `message` off the associated value directly, bypassing `errorDescription`. Wrap it: `paymentErrorMessage = ServerMessage.humanize(message)`. |

Grep for any other `case ClientError.server(` pattern-match before shipping — those are the only paths that skip the choke point.

**Localizable.strings additions** (`MehrgoDriver/Localization/uz.lproj/Localizable.strings`, `.../ru.lproj/Localizable.strings` — these are the only two `Localizable.strings` in the project; `en.lproj` holds `InfoPlist.strings` only and `AppLanguage` is `uz` + `ru`):

| key | uz | ru |
|---|---|---|
| `server_error_pinfl_not_match` | `Bu karta boshqa shaxsga tegishli. Faqat o‘zingizning kartangizni qo‘sha olasiz.` | `Эта карта принадлежит другому лицу. Вы можете добавить только свою карту.` |
| `server_error_card_is_blocked` | `Bu karta bloklangan. Iltimos, boshqa karta kiriting yoki bankingizga murojaat qiling.` | `Эта карта заблокирована. Введите другую карту или обратитесь в банк.` |

`error_server` already exists (uz `Server xatosi`, ru `Ошибка сервера`) — reuse it, do not add a second generic string.

> Apostrophes: the uz file uses the typographic `‘` (U+2018) throughout (`"no" = "Yo‘q"`). Use `‘`, not ASCII `'`, in the two new uz strings so they match the rest of the file. Android's kk/ky variants exist but iOS ships no Kazakh/Kyrgyz bundle — skip them.

### 4. The reusable confirm dialog

Android's `InfoPopup` (single OK) gained a `confirm(...)` sibling: same card, but a two-button row and a `Loader` handle passed to the caller.

**Behaviour contract:**

- Two buttons, side by side, equal width, 8dp gap. **Left = No** (secondary/gray fill, primary text color), **right = Yes** (primary/amber fill, white text).
- Tapping **Yes does not dismiss.** It invokes the caller's action and hands over a loading handle.
- While loading: Yes label is replaced by an **in-button spinner** (label hidden, not removed — the button must not resize), Yes is disabled, **No is disabled too**, and the dialog becomes non-dismissible (no backdrop tap, no swipe/drag dismiss).
- On success the caller dismisses the dialog and refreshes the list behind it.
- On error the caller **clears loading and leaves the dialog open** — the driver can read the toast and hit Yes again. No auto-dismiss on failure.
- Tapping **No** dismisses immediately (no network call).
- The dialog auto-dismisses if its host is torn down mid-flight.

**iOS: there is no shared popup component yet.** The closest existing thing is `TripView.stopCarPopupOverlay` (`MehrgoDriver/View/Main/Home/TripView.swift:588`) — a hand-rolled centred overlay with dimmed backdrop, soft circular glyph, bold title, gray hint, single full-width button, spring scale-in. That is the *visual* spec; it's inlined in one screen and should be extracted.

Create **`MehrgoDriver/Utilities/ConfirmPopup.swift`** (sits beside `PrimaryButton.swift` / `SecondaryButton.swift`). Do **not** port the Kotlin `Loader` interface literally — in SwiftUI express the same contract as an async closure returning success:

```swift
import SwiftUI

/// Centred confirm dialog: dimmed backdrop, glyph, title, optional message,
/// [No | Yes]. `onConfirm` runs with an in-button spinner on Yes; returning
/// `true` dismisses, `false` restores the button so the driver can retry.
/// Nothing is dismissible while the action is in flight.
struct ConfirmPopupModifier: ViewModifier {

    @Binding var isPresented: Bool
    let title: LocalizedStringKey
    let message: String?
    var yesTitle: LocalizedStringKey = "yes"
    var noTitle: LocalizedStringKey = "no"
    var systemIcon: String = "exclamationmark.triangle.fill"
    let onConfirm: () async -> Bool

    @State private var isLoading: Bool = false

    func body(content: Content) -> some View {
        content.overlay {
            if isPresented {
                ZStack {
                    Color.black.opacity(0.45)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture { if !isLoading { close() } }

                    VStack(spacing: 18) {
                        ZStack {
                            Circle().fill(Color.brandOrangeSoft).frame(width: 72, height: 72)
                            Image(systemName: systemIcon)
                                .font(.system(size: 32, weight: .bold))
                                .foregroundStyle(Color.brandOrange)
                        }
                        .padding(.top, 4)

                        VStack(spacing: 8) {
                            Text(title)
                                .font(.system(size: 19, weight: .bold))
                                .foregroundStyle(Color.textColor)
                                .multilineTextAlignment(.center)
                            if let message, !message.isEmpty {
                                Text(message)                       // dynamic String, not a key
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.grayFull)
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }

                        HStack(spacing: 8) {
                            SecondaryButton(title: noTitle, isEnabled: !isLoading) { close() }
                            PrimaryButton(title: yesTitle, isLoading: isLoading) {
                                Task {
                                    isLoading = true
                                    let ok: Bool = await onConfirm()
                                    isLoading = false
                                    if ok { close() }
                                }
                            }
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: 320)
                    .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .shadow(color: .black.opacity(0.18), radius: 24, x: 0, y: 10)
                    .padding(.horizontal, 32)
                    .transition(.scale(scale: 0.9).combined(with: .opacity))
                }
                .animation(.spring(response: 0.32, dampingFraction: 0.82), value: isPresented)
            }
        }
    }

    private func close() {
        HapticManager.selection()
        withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) { isPresented = false }
    }
}

extension View {
    func confirmPopup(
        isPresented: Binding<Bool>,
        title: LocalizedStringKey,
        message: String? = nil,
        yesTitle: LocalizedStringKey = "yes",
        noTitle: LocalizedStringKey = "no",
        systemIcon: String = "exclamationmark.triangle.fill",
        onConfirm: @escaping () async -> Bool
    ) -> some View {
        modifier(ConfirmPopupModifier(
            isPresented: isPresented, title: title, message: message,
            yesTitle: yesTitle, noTitle: noTitle, systemIcon: systemIcon,
            onConfirm: onConfirm
        ))
    }
}
```

Notes for the implementer:

- `PrimaryButton` (`MehrgoDriver/Utilities/PrimaryButton.swift`) **already** implements the in-button spinner exactly as required — `Text` kept in the `ZStack` at `.opacity(0)` with a `ProgressView` layered over it, and `.disabled(!isEnabled || isLoading)`. Use it; don't hand-roll a second spinner button.
- `SecondaryButton` has 16pt vertical padding vs `PrimaryButton`'s 18pt. Add `.padding(.vertical, 2)` to the secondary or normalise both, otherwise the row is visibly uneven.
- Backdrop tap is the iOS equivalent of Android's `setCanceledOnTouchOutside`; the `if !isLoading` guard is the equivalent of `dialog.setCancelable(!loading)`. There is no swipe-to-dismiss on an overlay, so nothing else to block.
- Android's lifecycle auto-dismiss has no iOS analogue — a SwiftUI overlay disappears with its host view. No work needed.
- Icon: Android uses `ic_error_circle`. Per the project's icon rule, **do not use SF Symbols in shipped Android assets**, but on iOS SF Symbols are fine — `exclamationmark.triangle.fill` for warnings, `trash.fill` for destructive confirms.
- The **title** is a localization key (`LocalizedStringKey`); the **message** is a plain `String` because callers pass dynamic data (Android passes the masked card number as the message). Keep those two types distinct.

**Strings already present** in both `Localizable.strings` — no additions needed:

| key | uz | ru |
|---|---|---|
| `yes` | `Ha` | `Да` |
| `no` | `Yo‘q` | `Нет` |
| `ok` | `OK` | `OK` |

### 5. Where to use it on iOS today

The Android caller that this shipped for — delete-saved-card in `MyPaymentFragment` — **has no iOS counterpart** (see §6). Adopt the component on the confirmations that *do* exist, all of which currently use bare `.alert` with no loading state and therefore leave the driver staring at an unresponsive screen while the request runs:

| iOS file | current | change |
|---|---|---|
| `MehrgoDriver/View/Main/Settings/SettingsView.swift:76` | `.alert("delete_account_confirm_title", …)` + a *separate* `.alert("delete_account_failed")` for the error | Replace both with one `.confirmPopup`. `onConfirm` returns `false` on failure, popup stays open, and `ToastCenter.shared.show(ServerMessage.humanize(vm.deleteAccountError), style: .error)`. Kills the two-alert dance. |
| `MehrgoDriver/View/Main/Settings/SubscriptionsView.swift:61` | `.alert("purchase_confirm", …)` fires `vm.purchase(id:)` and dismisses instantly — the driver has no idea whether the purchase landed | `.confirmPopup` with the spinner until the purchase call responds. Money flow — highest value adoption. |
| `MehrgoDriver/View/Main/Home/TripView.swift:588` | inline `stopCarPopupOverlay` | Refactor to a sibling `.infoPopup` (single-button variant of the same file) so the two popups can't drift visually. Optional but cheap. |

Leave the plain `.alert("logout_confirm")` (`SettingsView.swift:69`, `BlockedDriverView.swift:92`) alone — logout is local and synchronous, there is nothing to spin on.

### 6. Android-only / missing counterpart

- `AddCardFragment`, `AddCardConfirmFragment`, and the saved-card list + delete + withdrawal UI in `MyPaymentFragment` **do not exist on iOS at all**. `BalanceMVVM` / `BalanceView` are top-up-only (Click and PayMe deep links via `SFSafariViewController`); there is no Paylov card CRUD, no card list, no withdrawal request flow. Consequently `pinfl_not_match` and `card_is_blocked` cannot currently fire on iOS.
  **Ship the humaniser anyway** — rule 3 (unknown-snake_case → generic) protects the ~44 existing error surfaces on every screen from the next leaked key, and having the mapping in place means the card screens land correct when that feature is ported. If/when the Paylov flow is built, it belongs at `MehrgoDriver/View/Main/Settings/PaymentCardsView.swift` + `MehrgoDriver/MVVM/PaymentCardsMVVM.swift`.
- The other parts of commit `b7338fab` (withdraw bottom-sheet expansion, `max_withdraw_amount` / `withdraw_over_daily` limit lines) and `43cc534a` (`item_withdrawal_request.xml` redesign) are inside that same non-existent flow — **no iOS work**, they are covered by the missing-feature note above rather than by this section.
- `dialog_info_popup.xml` and Android's `Loader` interface are pure Android view plumbing — nothing to port beyond the behaviour contract in §4.

### 7. Test checklist

1. Force a 4xx whose `message` is `pinfl_not_match` → uz driver sees `Bu karta boshqa shaxsga tegishli…`, ru driver sees `Эта карта принадлежит другому лицу…`.
2. Force `message` = `some_unknown_backend_key` → driver sees `Server xatosi` / `Ошибка сервера`, **never** the key.
3. Force `message` = `Buyurtma allaqachon bekor qilingan` (a real sentence) → shown verbatim, unmodified.
4. Force `message` = `null` on a 500 → generic string, **not** `Server error 500`.
5. Confirm popup: tap Yes on a slow endpoint → spinner inside Yes, both buttons dead, backdrop tap does nothing; on failure the popup is still up with Yes restored and a toast underneath; on success it closes and the list reloads.
6. Switch language while a mapped error is on screen → the *next* error uses the new language (the humaniser resolves at throw time, not at bundle-load time).

---

## 7. Order card footer + created-at time + history-detail order id

Three small changes to the driver's order cards, plus one data bug. Android commits: `b7338fab` (model + binder + first layout), `e07e9c0e` (card restructure), `ca72eb52` (final polish).

### 1. `Order.created_at` — new nested object

The pool/broadcast payload (`GET order/list`, and the same `Order` shape on `order/my-orders`, socket `ORDER_NEW`) carries a nested `created_at`. iOS already has this in a DEBUG fixture (`HomeMVVM.mapDiagOrderJSON` line 148) but **never decodes it** — `Order` in `MehrgoDriver/Shared/Models/SharedModels.swift:352` has no `createdAt` property.

Wire shape:

```json
"created_at": { "int": 1783614801, "datetime": "Jul 09, 2026y 21:33", "date": "Jul 9, 2026", "time": "21:33" }
```

| JSON key | Type | Meaning | Notes |
|---|---|---|---|
| `int` | Int (unix seconds) | order creation timestamp | present in the live payload; not used for display |
| `datetime` | String | `"MMM dd, yyyyy HH:mm"` | the display value. Note the literal `y` the backend appends after the year |
| `date` | String | `"MMM d, yyyy"` | day only |
| `time` | String | `"HH:mm"` | may be absent on some serializers |

Whole object is **optional** — a slim/older payload omits it, and the card must simply not render the line.

**Swift.** `HistoryDate` (`SharedModels.swift:1236`) is already exactly this type minus `int`. Reuse it rather than adding a parallel struct:

```swift
struct HistoryDate: Codable {
    let timestamp: Int?      // NEW — unix seconds, `int` on the wire
    let datetime: String?
    let date: String?
    let time: String?

    enum CodingKeys: String, CodingKey {
        case datetime, date, time
        case timestamp = "int"
    }
}
```

Adding an optional key does not affect existing `OrderHistoryItem.date` decoding. `HistoryDate` is already `Hashable` (`SharedModels.swift:1553` block), which `Order: Hashable` requires.

Then in `Order`:

- add `let createdAt: HistoryDate?`
- add `case createdAt = "created_at"` to `Order.CodingKeys`
- **`Order` has a hand-written `init(from:)`** (`SharedModels.swift:396`) — the property will stay `nil` unless you add `self.createdAt = try? c.decode(HistoryDate.self, forKey: .createdAt)`
- append `createdAt: HistoryDate? = nil` to the memberwise `init` at line 427 (used by fixtures/tests; defaulted, so callers stay source-compatible)

### 2. Card footer layout

Android's pool card footer is now: **left = payment chip stacked over the tariff chip; flexible spacer; right = created-at time, bottom-aligned.** Final polish in `ca72eb52` removed the clock glyph that preceded the time and switched the time block from vertically-centred to `layout_gravity="bottom"`, so its baseline sits level with the tariff pill.

iOS counterpart: `MehrgoDriver/View/Main/MyOrders/MyOrderRow.swift` (used by `RecommendedOrdersView.row(for:)` for the pool and by `MyOrdersView` line 134 for accepted orders).

Current iOS structure differs: `paymentBadge` sits in the **top row** next to the price, and the tariff chip is a lone trailing-aligned capsule at the bottom. To match Android, move `paymentBadge` down into a real footer:

```swift
// Footer: payment over tariff (leading) · created-at (trailing, bottom-aligned)
HStack(alignment: .bottom, spacing: 8) {
    VStack(alignment: .leading, spacing: 6) {
        paymentBadge
        if let tariff = order.tariff?.name, !tariff.isEmpty {
            Text(tariff)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.brandOrange)
                .lineLimit(1)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.brandOrangeSoft)
                .clipShape(Capsule())
        }
    }
    Spacer(minLength: 8)
    if showsCreatedAt, let when = createdAtText {
        Text(when)
            .font(.system(size: 12))
            .foregroundStyle(Color.grayFull)
            .lineLimit(1)
    }
}
```

`HStack(alignment: .bottom)` is the direct equivalent of the Android `layout_gravity="bottom"` — do not use `.center`, that is exactly what the last commit undid. No icon in front of the time and no icon in the tariff pill (Android has neither).

Metric parity with the Android XML:

| Element | Android | SwiftUI |
|---|---|---|
| chip padding | `paddingHorizontal 10dp`, `paddingVertical 5dp` | `.padding(.horizontal, 10).padding(.vertical, 5)` |
| chip shape | `bg_badge_pill` | `Capsule()` |
| gap payment→tariff | `layout_marginTop = space_6` (6dp) | `VStack(spacing: 6)` |
| gap column→time | `layout_marginStart = space_8` (8dp) | `Spacer(minLength: 8)` |
| chip / time text | 12sp, medium (chips), regular (time) | `.system(size: 12, weight: .semibold)` / `.system(size: 12)` |
| tariff colours | `app_color` on `app_color_soft` | `Color.brandOrange` on `Color.brandOrangeSoft` |
| time colour | `gray_full` | `Color.grayFull` |
| payment colours | cash `green`/`green_soft`, card `blue_middle`/`blue_soft` | already correct in `paymentColor` |

Date formatting — **no `DateFormatter`.** Display the server `datetime` string verbatim with the year-suffix artefact stripped. iOS already has the helper (`String.strippedHistoryDate`, `MehrgoDriver/Shared/Extensions/Date+Formatters.swift:55`), and it is safer than Android's naive `replace("y ", " ")` because it is anchored to a digit boundary:

```swift
private var createdAtText: String? {
    guard let raw = order.createdAt?.datetime, !raw.isEmpty else { return nil }
    return raw.strippedHistoryDate      // "Jul 24, 2026y 22:25" → "Jul 24, 2026 22:25"
}
```

Full stripped datetime, not just `HH:mm` — the point is letting the driver spot a stale pooled order ("is this yesterday's order nobody took?"), which needs the date.

**Where it shows.** Android binds created-at only in `OrderAdapter` (the broadcast pool on the map screen); `OrderAcceptedAdapter` reuses the same XML and never touches it, so on the accepted "My orders" list the block stays `gone`. Mirror that with a flag on `MyOrderRow`:

```swift
var showsCreatedAt: Bool = false
```

`RecommendedOrdersView.row(for:)` passes `showsCreatedAt: true`; `MyOrdersView` leaves the default. Also hide the line when `createdAt` is nil, independently of the flag.

Edge cases: `created_at` absent → no line, footer stays single-row height; `datetime` present but empty string → treat as absent; tariff name empty → no tariff chip, payment chip alone (the time must still bottom-align to the payment chip, which `HStack(alignment: .bottom)` handles for free).

### 3. History detail showed the wrong order id

`MehrgoDriver/View/Main/MyOrders/OrderHistoryDetailView.swift:403` renders `Text("\(item.id)")`. `item.id` is the **history-row id**, a different id-space from the order id. Support/dispatch look trips up by the real order id, so the driver cannot use the number currently on screen.

Fix — read it off `myOrder`:

```swift
Text("\(item.myOrder?.id ?? item.id)")
```

`HistoryOrderRef.id` (`SharedModels.swift:1244`) is `Int?`; fall back to `item.id` only when the relation is missing. Note the same file already does this correctly at line 77 (`let orderId: Int = item.myOrder?.id ?? item.id` for `GET service/order`) — the footer was just never updated.

Android prefixes the value with `R.string.id_title`; iOS uses an SF `number` glyph instead, which is equivalent — no new string needed. For reference the Android label is uz `ID: ` / ru `ID: `.

### Strings

No new user-facing strings in this area. Existing keys already present in `uz.lproj` / `ru.lproj` `Localizable.strings`:

| Key | uz | ru | Android source |
|---|---|---|---|
| `payment_cash` | Naqd | Наличные | `payment_type_cash` — uz "To'lov naqd pulda" / ru "Оплата наличными" (Android uses the long form; iOS's short form is fine on a chip) |
| `payment_card` | Karta | Карта | `payment_type_card` — uz "To'lov kartadan" / ru "Оплата картой" |

Tariff name and the created-at datetime come from the server unlocalised.

### Not needed on iOS

`ca72eb52` also swapped the route-Retrofit converter to a lenient `AppGson` in `OrderAdapter.kt` — an Android JSON-strictness fix with no iOS analogue (`FlexDecode` already covers the string-vs-number tolerance).

---

## 8. Service chips on the trip sheet must NOT show per-service prices

**Android files:** `MapFragment.setView(...)` (service chip binding),
`res/layout/dialog_bsh_order_state.xml` (the services-total row).
**Status:** built, device-verified on the active-order sheet.

### The rule

On the **active-order / trip sheet**, a service chip shows the **service name only**.

| | before | after |
|---|---|---|
| Chip label | `Женщина водитель · 10 000` (name + `order_items[].total`) | `Женщина водитель` |
| "Services total" row + its divider | visible | **`gone`** |

### Why (this is the important part — do not "fix" it back)

`order.price` **already includes** every booking-time service. Printing the per-service amount next
to the fare made drivers read it as money *on top of* the fare, and they complained the totals did
not add up. This is the same contract as §1.4 of the 22-July brief ("`order.price` already includes
booking-time services") — this change is that contract's concrete UI consequence.

The priced breakdown is **not deleted**, it moves one tap away: the services **detail sheet**
(Android `showServicesSheet` / `dialog_bsh_services`, reached from the card chevron) still lists
each service with its price and the services subtotal. That screen is the right place for it,
because there the driver is explicitly asking "what is in this fare?".

### iOS work

1. Find the active-order/trip view's service chip row (grep the iOS project for the service chip
   or `OrderService` rendering used on the trip screen) and drop the price from the chip label —
   name only.
2. Hide any services-subtotal row on that same screen (and its divider/spacing, so you do not
   leave a gap).
3. Leave the **services detail sheet** untouched: name + price + subtotal stay there.
4. Do **not** add the service amounts to anything you display as the fare; they are already inside
   `order.price`.

No new strings. No backend change.

---

## 9. Navigation-race guard + platform/toolchain notes

### (a) Late-async navigation must never be driven from a detached view

**Android behaviour rule (what shipped in `ca72eb52`).** Every navigation triggered from an *asynchronous* continuation — a periodic `/user/me` + active-orders refresh collector, a socket event, a slide-to-act completion, or the `order/complete` ack — now goes through one helper instead of calling the navigation controller directly:

1. If the view is already torn down → do nothing.
2. If the requested transition does not resolve **from the destination the user is currently on** → do nothing (the user has moved on; the transition is stale).
3. If it still throws → swallow + log.

Seven previously-unguarded async call sites were converted: 401-driven forced logout (×2), finish → receipt, and four verification-screen entries. Production symptom was a hard crash (`IllegalArgumentException`) when the periodic refresh's 401 landed after the driver had opened another screen.

**iOS has no navigation controller, but it has the identical bug class**, in three shapes: writing `@State` that backs a `navigationDestination`/`sheet` after the owning view left the hierarchy (SwiftUI logs *"Accessing State's value outside of being installed on a View"* and the transition is silently lost); presenting a second sheet from an anchor that is already presenting (*"already presenting"* — the presentation is dropped, no crash); and mutating a **shared** `HomeMVVM` flag that now refers to a *different* order than the one the async call was about.

#### What is already safe — no work needed

| Flow | Why it cannot race |
|---|---|
| 401 → forced sign-out | Centralised. `DynamicClient.unauthorizedSubject` (`Shared/DynamicClient.swift:86`, sent at :198/:269) → `RootMVVM.handleSessionRevoked` (`MVVM/RootMVVM.swift:125`) → `didSignOut()` sets `route = .auth`. It is a root-enum swap, guarded (`isAuthenticated`, auth-flow path allow-list, pending-activation carve-out) and idempotent under a burst of concurrent 401s. This is structurally better than Android's per-fragment navigate and needs **no** change. |
| Login / sign-up pushes | `LoginView` drives `goSignUp` / `goRecovery` / `goCompleteInfo` from `.onChange(of: vm.…)` (`View/Auth/LoginView.swift:43-79`). `.onChange` only fires while the view is installed, so a late response on a popped screen is a no-op. **This is the pattern to copy everywhere else.** |
| `MapMVVM.loadVerification()` | Already single-flight (`verifyLoadTask?.cancel()`), and `goOnline()` supersedes it before the gate fetch. |

#### What must be fixed

| # | iOS site | Race | Consequence |
|---|---|---|---|
| 1 | `View/Main/Home/TripView.swift:1008` `performFinish()` — unstructured `Task { if await vm.finish() != nil { … presentFinish = true } }` | The rider cancels (socket) while `order/complete` is in flight → `TripMVVM.didCancel` → `TripView.onChange` (`:292`) runs `presentFinish = false; homeVM.clearActiveOrder()` → `MainView` unmounts `TripPanel` (`View/Main/MainView.swift:59`) → **then** the finish continuation resumes and writes `@State presentFinish` on a dead view | Receipt never shows; the settled fare is invisible to the driver |
| 2 | same continuation, `homeVM.markActiveFinished()` | `clearActiveOrder()` auto-promotes `queuedOrder` after 350 ms (`MVVM/HomeMVVM.swift:349-357`). If the finish ack lands after that promote, `markActiveFinished()` passes its `activeOrder != nil` guard (`:374`) and marks the **brand-new** trip finished | The next, un-started trip is treated as settled: first drag to peek routes through `minimizeTrip()`'s terminalisation branch (`:364`) and destroys it; the busy-offer guard (`:200`) also stops blocking. Money/blocking-grade |
| 3 | `MVVM/TripMVVM.swift:607` `finish()` | Guards `!isTransitioning, !didFinish` but **not** `!didCancel`, and never re-checks after `await` | A cancelled order still POSTs `order/complete` and reports success |
| 4 | `MVVM/MapMVVM.swift:184` `presentVerification = true` after the `driver/verification-status` await | `View/Main/Home/MapHomeView.swift` hangs **five** independent `.sheet`s off one anchor (`:230, 233, 241, 244, 253`). If the driver opened Settings / Notifications / Earnings during the ~network round-trip, the verification sheet is dropped by UIKit | Driver slides "Ishni boshlash", nothing visibly happens, they stay offline with no explanation — the exact iOS analogue of Android's stale-action crash |
| 5 | `View/Main/MyOrders/MyOrdersView.swift:45` `.navigationDestination(item: $tripOrder) { TripView(order: $0) }` | A **second** `TripView` (and `TripMVVM`) for a possibly *different* order, sharing the same `HomeMVVM`; `markActiveFinished()` / `clearActiveOrder()` take no order id | Finishing order X from the list would terminate the panel's order Y. **Currently latent** — `MyOrdersView` is not referenced from anywhere in the project (orphaned file, matching Android's removal of list mode). Fix the id-scoping anyway before it is re-wired, or delete the file |

#### Required implementation

**R1 — one rule: async work publishes, mounted views navigate.** Never write navigation `@State` from inside a `Task` continuation. Move the outcome onto the view model and let the view react:

```swift
// TripMVVM
@Published private(set) var finishReceipt: FareBreakdown?   // nil until settled

func finish() async -> User? {
    guard !isTransitioning, !didFinish, !didCancel else { return nil }   // R3
    …
    guard !didCancel else { return nil }   // re-check AFTER every await
    finishReceipt = breakdown              // publish, don't navigate
    didFinish = true
    return res.data
}
```

```swift
// TripView
.onChange(of: vm.finishReceipt) { _, breakdown in
    guard breakdown != nil,
          homeVM.activeOrder?.id == vm.order.id else { return }   // still MY order
    homeVM.markActiveFinished(orderId: vm.order.id)
    presentFinish = true
}
```
`.onChange` is only delivered while the view is installed, which is the SwiftUI equivalent of Android's "the view is gone → bail" + "the action must resolve from the current destination" checks — together they cover items 1, 2 and 5. Do **not** cancel the finish `Task` on disappear: the `order/complete` POST must reach the server regardless; only its UI continuation is suppressed.

**R2 — order-scope the shared teardown API.** Change `HomeMVVM.markActiveFinished()` → `markActiveFinished(orderId: Int)` and `clearActiveOrder(orderId: Int? = nil)`, both bailing when `activeOrder?.id != orderId`. Same for `resumeTrip` / `minimizeTrip` if they ever gain async callers. This is the "only navigate when the action resolves from the CURRENT destination" rule expressed in model terms.

**R3 — `finish()` must be terminal-state-aware.** Add `!didCancel` to the entry guard and re-assert it after `submit()` and after the bonus-correction retry.

**R4 — one presentation anchor on the home map.** Collapse `MapHomeView`'s five booleans into a single enum and `.sheet(item:)`:

```swift
enum MapSheet: String, Identifiable { case notifications, settings, recommended, verification, earnings
    var id: String { rawValue } }
@State private var activeSheet: MapSheet?
```
Then the *late* setter must be routed, not raw: replace `vm.presentVerification` with a `MapMVVM` request flag that `MapHomeView` consumes via `.onChange` and applies **only when `activeSheet == nil`**; if something else is presenting, either defer (re-apply in the sheet's `onDismiss`) or drop with a log — mirroring Android's "swallow the residual race". This also removes a whole family of future double-present drops (offer sheet vs. verification vs. settings).

**R5 — route every trip-terminal transition through one place.** `presentFinish = false` + teardown is currently duplicated in three `onChange` handlers (`TripView.swift:263` receipt Done, `:292` `didCancel`, `:305` `receiptDismissRequest`). Extract a single `private func teardownTrip()` and call it from all three, so the pop-then-clear ordering (unmounting the panel with a page still pushed zombie-freezes the receipt on screen — already discovered and commented at `:264-270`) can never be re-broken by a fourth caller.

#### Edge cases to cover in testing

- Rider cancels *during* the `order/complete` round trip (trigger with a throttled network) — expect: no receipt, no crash, trip cleanly torn down, **queued order promotes clean and is NOT flagged finished**.
- Finish while a queued 2nd order exists → promoted order must start at its accepted stage and be minimisable without being destroyed.
- Slide "Ishni boshlash", immediately open Settings, let the verification gate resolve → verification must still surface (after Settings dismiss) or be explicitly logged as dropped; the slider must reset either way.
- Forced 401 while the trip panel is up: `handleSessionRevoked` already stops trip tracking (`RootMVVM.swift:141`) — verify the panel disappears with the root swap and no receipt is pushed afterwards.
- Backgrounded during finish → foreground: `RootView`'s `validateSessionIfDue` fires on `.active`; the receipt must still appear once, not twice.

**Localisation:** none — this whole item is silent-guard behaviour. No new user-facing strings on either platform.

---

### (b) Platform / toolchain — **ANDROID-ONLY, no iOS code work**

| Change | Value | iOS action |
|---|---|---|
| `compileSdk` / `targetSdk` | 35 → **36** (Android 16), for the Google Play policy deadline **31 Aug 2026** | none |
| `gradle.properties` | `android.suppressUnsupportedCompileSdk=36` (AGP 8.8.2 is "tested up to 35"; stable API level, warning is noise) | none |
| AGP | 8.8.2, **no bump required** | none |
| App version | `versionName 2.6.0` / `versionCode 66` (was 2.5.0 / 65) | see below |

**Version lockstep.** iOS is currently at `MARKETING_VERSION = 1.5.0`, `CURRENT_PROJECT_VERSION = 30` (`MehrgoDriver.xcodeproj/project.pbxproj`), so the two platforms are **not** in lockstep today. If the team wants them aligned, set the iOS marketing version to **2.6.0** for this release and let the build number keep its own sequence; `Constants.appVersion` / `appVersionName` read `CFBundleVersion` / `CFBundleShortVersionString` automatically (`Shared/Constants.swift:42-45`), so nothing else needs editing — unlike Android, there is no hand-maintained constant to bump. If they are deliberately independent, do nothing and note it.

**⚠️ Store-blocking, shared with Android — server/DNS, not app code.** Google Play flagged the listing because the app-level **privacy-policy** and **account-deletion** URLs on `mehrgo.uz` serve an **expired TLS certificate issued for a different domain (`atsteelsupplier.com`)**. App Store Connect requires the same Privacy Policy URL and it must resolve, so **the identical failure will block Apple review**. Two extra iOS-specific consequences:

- `Constants.privacyPolicyURL = "https://mehrgo.uz/politics.html"` (`Shared/Constants.swift:30`) is opened in-app from the Settings privacy row (`View/Main/Settings/SettingsView.swift:249-253`) — with a mismatched/expired cert this is a visible in-app failure, not just a metadata problem.
- Account deletion itself is implemented **in-app** (`MVVM/SettingsMVVM.swift:47` → `DELETE user/delete`), which satisfies Guideline 5.1.1(v) on its own; but if the web deletion URL is also submitted in App Store Connect it must resolve too.

**Fix owner: backend/infra** — install a valid certificate for `mehrgo.uz` (and any `www.` alias) covering both pages, then re-verify both URLs before submitting either store build. No Swift change is needed unless the pages move, in which case only `Constants.privacyPolicyURL` updates.

---

## Appendix A - backend contracts touched by this release

Every endpoint below was transcribed from the Android driver client on branch `mehrgo_driver_app`
(paths, parameter names, model fields and units). Read A.0 first - it defines the envelope,
headers and error contract that every other endpoint inherits.

### 0. Envelope, headers, errors and the websocket (read this first)

Everything below applies to **every** endpoint in the later sections. Endpoint sections do not repeat it.

#### Hosts and clients

| | value | notes |
|---|---|---|
| Prod host | `prod.mehrgo.uz` | `Constants.BASE` — brand-switchable (dev/staging is `dashboard.ayoltaxi.uz`) |
| Main REST base URL | `https://prod.mehrgo.uz/api/v1/` | `Constants.BASE_URL`; the trailing slash is load-bearing (see Paylov note) |
| Media base | `https://prod.mehrgo.uz` | `Constants.IMAGE_URL`; relative image paths from the API are appended to this |
| Websocket | `wss://prod.mehrgo.uz/socket` | `Constants.BASE_URL_FOR_SOCKET` |
| Route server | `https://route.teamwork.uz/` | `Constants.BASE_URL_ROUTE`; OSRM-compatible, **not** the Mehrgo backend |

Two HTTP clients exist (`ApiService` on `BASE_URL`, `RouteApiService` on `BASE_URL_ROUTE`) but they share **one** `OkHttpClient`, so the route server receives the same headers, including `Authorization`. Timeouts: connect **20 s**, write **30 s**, read **40 s**.

Paylov routes are declared as relative paths `../paylov/...` against the `…/api/v1/` base, which RFC-3986 resolution turns into `https://prod.mehrgo.uz/api/paylov/...` — i.e. Paylov is **not** under `/v1`. On iOS build those URLs absolutely; do not rely on relative-path resolution.

#### `BaseResponse<T>` — the success envelope

```kotlin
class BaseResponse<T>(var data: T? = null)
```

| field | type | notes |
|---|---|---|
| `data` | `T?` | The payload. **Optional/nullable** — the app treats a 2xx with no `data` as success-with-nothing, never as an error. |

The client parses **no other top-level key** on success. If the backend also sends `status`, `message`, `pagination`, etc. alongside `data`, the Android app silently ignores them (pagination that the app does use is inside `data`, e.g. `OrderHistory`, `WithdrawalList`).

```json
{ "data": { "...": "endpoint-specific payload" } }
```

**Envelope exceptions** — only two, both real:
- `GET mobile/version-driver` returns the `UpdateApp` object **bare**, not wrapped (`version`, `version_code`, `required`, `blocked_aps`, `device_token`).
- `RouteApiService` (`route.teamwork.uz`) returns raw OSRM JSON (`{ "routes": [ { "geometry", "legs", "distance" } ] }`) — no envelope.

#### `ErrorResponse` — the error body

```kotlin
class ErrorResponse(var status: Int? = null, var message: String? = null)
```

| field | type | notes |
|---|---|---|
| `status` | `Int?` | Backend's own status code. Parsed but **never used** by the app — it switches on the HTTP status only. |
| `message` | `String?` | Shown to the driver — *sometimes*. See below. |

```json
{ "status": 422, "message": "pinfl_not_match" }
```

`message` is **not reliably a human sentence**. It is sometimes a machine key. `Helper.humanizeServerError(raw)` is the mandatory gate before display:

1. Blank → generic server error string.
2. Known key → hardcoded localized sentence. Currently mapped keys (lowercased compare): `pinfl_not_match` ("this card belongs to someone else"), `card_is_blocked` ("this card is blocked, use another or contact your bank").
3. Unknown value that *looks like* a key — contains `_`, contains no whitespace, and equals its own lowercase — → generic server error (the key is **never** shown).
4. Anything else → shown verbatim.

Currently applied at the three add-card sites only; other call sites still toast `message` raw. iOS should apply the same humanizer everywhere.

Generic fallback strings (localized by `LanguageManager`, uz/kk/ky/ru):
- server error → uz `"Server bilan bog'lik xatolik"`
- connection error (`IOException`) → uz `"Internet bilan bog'lik xatolik. Iltimos aloqangizni tekshiring"`

One endpoint has a richer error body: `POST order/complete` can return `ErrorOrderFinishResponse` = `{ message, data: { client_total_bonus: Long, clientBonusSettings: { minimum_amount_to_use_bonus: Long, maximum_amount_to_use_bonus_per_order: String } } }` — note `maximum_amount_to_use_bonus_per_order` is a **String** while `minimum_amount_to_use_bonus` is a number.

#### The three-state result funnel

Every call is modelled as `Loading → Success | Error`:

```kotlin
try { emit(Resource.Success(repository.call())) }
catch (e: HttpException) {           // any non-2xx
    val msg = gson.fromJson(e.response()?.errorBody()?.string(), ErrorResponse::class.java)?.message
    emit(Resource.Error(msg ?: Helper.getServerError()))
} catch (e: IOException) {           // no network / timeout
    emit(Resource.Error(Helper.getConnectionError()))
}
```

The error-body parse is wrapped in its own `try` and the emit happens **after** it — a malformed error body must degrade to the generic string, not throw.

#### Lenient JSON decoding (mandatory on iOS too)

`common/AppGson.kt` (added in this range, wired into both Retrofit converters, the socket parser and the error-body parser via Hilt) coerces bad scalars instead of throwing, because this backend intermittently sends numbers as `""`, `null`, or the wrong JSON type (`"total": ""`, `"waiting_cost": null`, `"distance_km": ""`, and famously `"waiting_cost": 2613.33` into a `Long` field, which hard-crashed the app).

Rules:

| wire value | non-optional field | optional field |
|---|---|---|
| `""` / `null` / unparseable | `0`, `0L`, `0.0`, `0f`, `false` | `null` |
| numeric string `"5"` | `5` | `5` |
| `"3.7"` into an Int/Long | truncated → `3` | `3` |
| booleans as `"1"`, `"0"`, `"1.0"`, `"0.0"`, `"true"`, `"false"` | coerced | coerced |
| object/array where a scalar was expected | skipped → default | skipped → `null` |

Serialization is unchanged (nulls are omitted by Gson's default, which several request shapes rely on — see `waiting_time` in the GPS batch).

**iOS:** a stock `JSONDecoder` will throw on all of the above. Decode money/distance/time scalars through a lenient wrapper (`try Int` → `try Double` → `try String` + parse → default) or the app will crash on live data.

#### `HeaderInterceptor` — headers attached to *every* request

Confirmed by reading `common/HeaderInterceptor.kt`. All are added on both the main and route clients.

| header | value | notes |
|---|---|---|
| `Authorization` | `Bearer <authKey>` | From `UserManager.getBearerToken()`. **Always added.** With no user stored the literal string `"Bearer null"` goes on the wire — the app never omits the header. |
| `Accept-Language` | `uz` \| `kk` \| `ky` \| `ru` | `LanguageManager.getLanguage()`, or **empty string** when the driver has not chosen a language yet. |
| `X-Platform` | `android` | Static. iOS should send `ios` — **confirm the accepted value with the backend**, the panel may match on an enum. |
| `X-App-Version` | `<versionName>+<versionCode>`, e.g. `2.6.0+66` | From `BuildConfig`, not from `Constants.APP_VERSION`. The `+` separator is literal. |
| `X-OS-Version` | `Build.VERSION.RELEASE`, e.g. `14` | OS release string only, no SDK int. |
| `X-Device-Brand` | `Build.MANUFACTURER`, e.g. `samsung` | |
| `X-Device-Model` | `Build.MODEL`, e.g. `SM-A155F` | |

Sanitisation rule for the four device/app headers (**not** applied to `Authorization`, `Accept-Language`, `X-Platform`):

```kotlin
raw.filter { it.code in 32..126 }.take(64)   // printable ASCII only, then truncate to 64 chars
```

i.e. strip every char outside ASCII 0x20–0x7E, then take the first 64 characters. Backend truncates at 64 and expects ASCII; the client enforces both. These are diagnostic-only and must never carry secrets.

Note the header names are `X-App-Version` / `X-OS-Version` / `X-Device-Brand` / `X-Device-Model` — **`X-`-prefixed**, including the version header (the internal MOBILE.md shorthand writes them without the prefix; the code is authoritative).

#### Failed-response capture → `POST user/report`

The same interceptor, on any non-2xx, buffers the response body and appends an `ErrorRequest` record to `ErrorRequestManager` SharedPrefs; `MyTrackingService` uploads the accumulated list on next start via `POST user/report` (`@Body List<ErrorRequest>`).

```json
[{
  "url": "https://prod.mehrgo.uz/api/v1/order/accept?id=78188",
  "token": "",
  "requestTime": "2026-07-28 11:02:33.412",
  "responseTime": "2026-07-28 11:02:34.008",
  "duration": 596,
  "socketStatus": true,
  "responseCode": 422,
  "responseBody": "{\"status\":422,\"message\":\"order_already_taken\"}"
}]
```

`token` is **deliberately always the empty string** — it used to carry the driver's live bearer token into plaintext prefs and back up to the server; the field is kept so the wire shape is unchanged. iOS must send `""` too, never the real token. Empty body is recorded as the literal `"Bo'sh satr"`.

#### Auth: where the token comes from, and 401

- The token is `User.auth_key`, returned by `POST user/login`, `POST user/confirm` and `POST user/change-password`, and persisted inside the whole serialized `User` object in SharedPreferences (`myUserPref` / `my_user_key`).
- There is **no refresh flow**. The same `auth_key` is used until logout or 401.
- There is **no interceptor-level 401 handling**. 401 is handled per-call-site: `UserUC`, `StartWorkUC` and `OrderAddressUC` translate `HttpException.code() == 401` into the sentinel `Resource.Error("401")` (the literal string, `Constants.ERROR_UNAUTHORIZED`). `MapFragment` reacts to that sentinel by: stopping `MyTrackingService`, `UserManager.deleteUser()` (clears the whole prefs file), then navigating to the login screen. Every other endpoint just shows the 401 body's `message` as a toast.
- `GpsBatchUploader` treats any status in `401..499` as terminal and stops uploading for that order rather than retrying.

**iOS notes**
- Send `Authorization` unconditionally; a missing header and `Bearer null` are not equivalent to this backend as far as we can tell — **confirm**, but match Android's behaviour to be safe.
- Do not model `BaseResponse.data` as non-optional: 2xx-with-null-`data` is a normal success (e.g. `POST driver/end`).
- Treat `message` as untrusted display text; run it through the humanizer before showing it.
- Centralise the 401 → clear-session → login transition instead of copying Android's three-call-sites-only handling.

---

#### `WS wss://prod.mehrgo.uz/socket?token=<auth_key>` — realtime order/notification/fare channel

**When it fires:** `MyTrackingService` (foreground service) opens the socket when the driver goes on shift and reconnects from the heartbeat. The request is rebuilt **from the current token on every connect** — a reconnect must never resurrect a logged-out session; if the stored token is null/empty the connect is skipped entirely.

**Connection**

| item | value |
|---|---|
| URL | `wss://prod.mehrgo.uz/socket?token=<auth_key>` — token in the **query string**, not a header |
| Extra header | `Connection: open` (set explicitly by the client) |
| Protocol ping | **Application-level**, not RFC-6455. Client sends the bare text frame `ping`; server replies with a JSON frame `key: "pong"`. OkHttp's own `pingInterval` is deliberately **off** — the server does not answer control-frame PINGs and the socket was being torn down every interval. |
| Heartbeat | every `10_000 ms`; tolerate `MAX_MISSED_SOCKET_PONGS = 3` consecutive silent cycles (~30 s) before declaring the socket dead, cancelling it and reconnecting |
| Close code | `1000` on normal shutdown |

**Inbound frame envelope** — every frame is JSON with the same two top-level fields plus an optional `data`:

```json
{ "status": 200, "key": "order_new", "data": { } }
```

| field | type | notes |
|---|---|---|
| `status` | `Int` | Parsed, never used by the app. |
| `key` | `String` | The discriminator; unknown keys are ignored silently. |
| `data` | object | Shape depends on `key`. **Nullable on every frame** — declared non-null on the order/notification models but Gson populates reflectively, so a missing `data` yields null and the app must (and now does) drop the frame. |

**Frame catalogue**

| `key` constant | wire value | `data` shape | what the driver app does |
|---|---|---|---|
| `ORDER_NEW` | `order_new` | full `Order` | Only dispatched **while the driver's status == 10 (`DRIVER_ACTIVE`)**; otherwise dropped client-side. Broadcasts to UI → refreshes the pool list; posts a "new order" notification. |
| `ORDER_NEW_PRIVATE` | `order_new_for_nurse` | full `Order` | Same online gate. Personal offer → shows the auto-offer overlay (if overlay permission) or a countdown notification using `data.branch.accept_waiting` seconds (default 20). |
| `ORDER_ACCEPTED` | `order_accepted` | full `Order` | Refetches the pool list (the order is gone from it). |
| `ORDER_CANCELLED` | `order_cancelled` | full `Order` | Fires for **any** order incl. pool orders this driver never took → must compare `data.id` with the active order id before tearing anything down; always refreshes the pool list. |
| `ORDER_CANCELLED_PRIVATE` | `order_cancelled_for_nurse` | full `Order` | Same id-guard; on a match clears the trip state, stops the GPS uploader, shows "your order was cancelled". |
| `NOTIFICATION_NEW` | `notification_new` | `{ id: Int, message: String, text: String }` | System notification with `message` as title and `text` as body. |
| `RECEIVE_PONG` | `pong` | *(none)* | Heartbeat ack; resets the missed-pong counter. |
| `ORDER_GPS_BATCH` | `order_gps_batch` | `FareResponse` | **Ack** for a batch the client sent over the socket. Acks are not message-id'd — matched FIFO against pending sends, 5 s timeout, then the client falls back to `POST order-gps/batch` over REST. |
| `ORDER_PRICE_UPDATED` | `order_price_updated` | `FareResponse` | Server-initiated live-fare push (recompute between batches, waiting-charge events). Carries the one-shot `events` list, which is **never repeated** — miss it and it is gone. |

**`data` for the order frames** — the `Order` model, top level (nested objects abbreviated; they are documented in full in the order-endpoints section):

| field | type | notes |
|---|---|---|
| `id` | `Int` | |
| `contact` | `{ id, name, phone, bonus: Long? }?` | client |
| `starting_price` | `Int` | so'm |
| `price_of_per_unit` | `Int` | in-city per-unit price, so'm |
| `address_category` / `address_category_finish` | `{ id, name }?` | |
| `address` / `address_finish` | `{ id, name, latitude: String, longitude: String }?` | **lat/lon are Strings here** |
| `price` | `Int` | so'm; already includes booked services |
| `latitude`, `longitude` | `String` | pickup, as strings |
| `info` | `String?` | client comment |
| `status` | `{ int: Int, string: String }` | order-history status (10/11/12/15) |
| `state` | `Int` | 2 accepted, 7 started, 8 arrived, 9 gone |
| `podacha` | `Int?` | approach fee, so'm |
| `order_items` | `[ { id, price: String, total: Int, service: { name, info?, icon? } } ]?` | note `price` is a **String**, `total` an Int |
| `branch` | object | incl. `accept_waiting: Int?` (seconds), `dispetcher_number: String`, `polygon.boundary: String?` (WKT `POLYGON((...))`) |
| `distance` | `Double` | km |
| `locations` | `[ { name, position: Int, lat: Double, lon: Double } ]` | multi-stop route points; **here lat/lon are Doubles** |
| `tariff` | object | mixed String/Int money fields (`price_of_out: String`, `price_of_waiting: String`, `min_wait_time: String`, `price_of_waiting_on_way: Int?`) |
| `driver_number` | `String` | |
| `car` | `{ car_number?, car_model?, car_color? }?` | |
| `from` | `Int` | originator: 25 client, 1 driver/taximeter, 3 manager, 4 dispatcher, 10 admin |
| `use_bonus` | `Boolean` | |
| `promo` | `{ usage: { code, amount: String }? }?` | |
| `is_card_payment` | `Boolean?` | |
| `created_at` | `{ int: Long?, datetime: String?, date: String?, time: String? }?` | **new in this range**; nested object, not a scalar. `int` is an epoch **seconds** value — *inferred from the history model's identical shape, confirm with backend*. |

```json
{
  "status": 200,
  "key": "order_new_for_nurse",
  "data": {
    "id": 78447,
    "contact": { "id": 40122, "name": "Aziz", "phone": "+998901234567", "bonus": 0 },
    "starting_price": 12000,
    "price_of_per_unit": 2500,
    "address_category": { "id": 3, "name": "Chilonzor" },
    "address": { "id": 811, "name": "Chilonzor 19-kvartal", "latitude": "41.275312", "longitude": "69.203441" },
    "address_category_finish": { "id": 7, "name": "Yunusobod" },
    "address_finish": { "id": 902, "name": "Yunusobod 4-kvartal", "latitude": "41.351020", "longitude": "69.289915" },
    "price": 27000,
    "latitude": "41.275312",
    "longitude": "69.203441",
    "info": "2-podyezd",
    "status": { "int": 11, "string": "accepted" },
    "state": 2,
    "podacha": 3000,
    "order_items": [
      { "id": 5512, "price": "5000", "total": 5000, "service": { "name": "Konditsioner", "info": null, "icon": null } }
    ],
    "branch": {
      "id": "1", "name": "Toshkent",
      "lat": "41.311081", "lon": "69.240562", "radius": "30000",
      "accept_waiting": 20,
      "dispetcher_number": "+998555161919",
      "clientBonusSettings": { "minimum_amount_to_use_bonus": 5000, "maximum_amount_to_use_bonus_per_order": "20000" },
      "polygon": { "boundary": "POLYGON((69.19 41.26,69.31 41.26,69.31 41.37,69.19 41.37))" }
    },
    "distance": 8.4,
    "locations": [
      { "name": "Chilonzor 19-kvartal", "position": 0, "lat": 41.275312, "lon": 69.203441 },
      { "name": "Yunusobod 4-kvartal", "position": 1, "lat": 41.351020, "lon": 69.289915 }
    ],
    "tariff": {
      "id": 12, "name": "Standart", "min_distance": 3,
      "distances": [ { "id": 1, "start": 0, "end": 3000, "price": 12000 } ],
      "price_of_out": "3000", "min_wait_time": "3", "price_of_waiting": "500",
      "min_wait_time_on_way": 2, "price_of_waiting_on_way": 700, "comission": "12"
    },
    "driver_number": "+998901112233",
    "car": { "car_number": "01A123BC", "car_model": { "id": 4, "name": "Cobalt", "second_name": "Cobalt" }, "car_color": { "id": 2, "name": "Oq", "second_name": "Белый" } },
    "from": 25,
    "use_bonus": false,
    "promo": null,
    "is_card_payment": false,
    "created_at": { "int": 1785312153, "datetime": "2026-07-28 11:02:33", "date": "2026-07-28", "time": "11:02" }
  }
}
```

**`data` for the fare frames** — `FareResponse`, identical to the REST fare payload:

| field | type | unit |
|---|---|---|
| `order_id` | `Long?` | |
| `distance_km` | `Double?` | km |
| `in_city_km` / `out_city_km` | `Double?` | km |
| `waiting_sec` / `traffic_sec` | `Long?` | **seconds** |
| `price` | `Long?` | so'm, rounded — display value |
| `fare` | `Double?` | so'm, unrounded (debug/dispute) |
| `breakdown`, `price_breakdown` | opaque JSON object | intentionally untyped |
| `live` | `FareLive?` | **preferred over the top-level fields when present** |
| `events` | `[String]?` | one-shot billing events: `waiting_charge_started`, `waiting_charge_step` |

`FareLive` (`data.live`) — **all money fields are Double**, the backend sends fractions (`"waiting_cost": 2613.33`); consumers round for display:

| field | type | unit |
|---|---|---|
| `distance_km` | `Double?` | km |
| `to_client_km` | `Double?` | km (approach leg) |
| `waiting_sec` | `Long?` | seconds, pickup wait |
| `on_way_sec` | `Long?` | seconds, on-route wait |
| `waiting_cost` | `Double?` | so'm |
| `services_price` | `Double?` | so'm |
| `podacha` | `Double?` | so'm |
| `extra_price` | `Double?` | so'm |
| `fare` | `Double?` | so'm |
| `price` | `Double?` | so'm — current TOTAL = round(fare + services + podacha + extra + waiting_cost) |
| `agreed_price` | `Double?` | so'm; **only on orders with a B point**, else null |
| `surcharge` | `Double?` | so'm; waiting surcharge on top of the agreed price |
| `kept_projection` | `Double?` | so'm; projected total if finished at B |
| `waiting_billing` | `Boolean?` | true = free waiting window is over, waiting is billing now |

```json
{
  "status": 200,
  "key": "order_price_updated",
  "data": {
    "order_id": 78447,
    "distance_km": 8.42,
    "in_city_km": 8.42,
    "out_city_km": 0,
    "waiting_sec": 320,
    "traffic_sec": 0,
    "price": 27000,
    "fare": 26890.55,
    "breakdown": { },
    "price_breakdown": { },
    "live": {
      "distance_km": 8.42,
      "to_client_km": 1.9,
      "waiting_sec": 320,
      "on_way_sec": 45,
      "waiting_cost": 2613.33,
      "services_price": 5000,
      "podacha": 3000,
      "extra_price": 0,
      "fare": 16276.67,
      "price": 26890,
      "agreed_price": 24000,
      "surcharge": 2613,
      "kept_projection": 26613,
      "waiting_billing": true
    },
    "events": ["waiting_charge_step"]
  }
}
```

**Outbound frames** (client → server) — only two:

1. Heartbeat: the **bare text** `ping` (not JSON, no envelope).
2. GPS batch (preferred transport; REST is the fallback):

```json
{
  "key": "order_gps_batch",
  "data": {
    "order_id": 78447,
    "points": [
      { "lat": 41.2753, "lon": 69.2034, "accuracy": 8.5, "speed": 11.2, "bearing": 145.0,
        "altitude": 452.1, "provider": "fused", "ts": 1785312153412, "cpid": 1041 }
    ],
    "waiting_time": 320000,
    "waiting_time_ontheway": 45000
  }
}
```

Units and semantics on the batch (from the model docs): `speed` is **m/s** (the server multiplies by 3.6 itself), `ts` is **ms epoch**, `waiting_time` / `waiting_time_ontheway` are **cumulative milliseconds** and are **omitted entirely when unchanged since the last send** (Gson drops nulls — iOS must do the same, sending `null` explicitly is not equivalent). `cpid` is a monotonically increasing per-order client point id used as the server's idempotency key (`ON CONFLICT DO NOTHING`), so re-sending the same `cpid` after a retry/reconnect is safe and will not inflate distance or fare. No `status` field on outbound frames.

**Errors:** the socket has no error frames. Failure modes are: connect failure / `onFailure` (client flips the "reconnecting" banner immediately but does **not** reconnect from there — only the paced heartbeat reconnects, so a down server cannot trigger a tight loop), and 3 consecutive missed pongs → cancel + reconnect. There is no server-side "auth failed" frame; a bad token presumably fails the handshake — **confirm**.

**Defensive parsing (required, not optional).** Every frame in this app is now parsed inside a `runCatching`/`try` that drops the frame on failure, at *three* layers: `MySocketListener.onMessage` (was catching `org.json.JSONException`, which Gson never throws — malformed frames escaped and killed the socket thread), the `MyTrackingService` broadcast receiver, and the `MapFragment` receiver (both re-parse the raw frame text). A malformed or partial frame must be logged and dropped; it must never propagate.

**iOS notes**
- Frames carry `status`/`key` at top level and `data` **nested** — do not flatten. Declare `data` optional on *every* frame type, including the order frames.
- The order payload mixes types for the same concept: `address.latitude` is a **String**, `locations[].lat` is a **Double**, `order_items[].price` is a **String** while `.total` is an Int. Decode leniently per field, not by convention.
- `order_price_updated`'s `events` array is one-shot — buffer it on arrival; it is never re-sent in a later frame or in `GET order-gps/fare`.
- Keep the app-level `ping` text frame and disable URLSession's own ping, mirroring Android — the server does not answer WebSocket control PINGs.
- `ORDER_CANCELLED` / `ORDER_CANCELLED_PRIVATE` fire for orders this driver never accepted: always compare `data.id` against the active order before tearing down trip state.

---

### Envelope, base URLs and auth (read once, applies to every endpoint below)

**Base URL (Mehrgo prod):** `https://prod.mehrgo.uz/api/v1/` — built in `Constants.kt` as `"https://$BASE/api/v1/"`, `BASE = "prod.mehrgo.uz"`. A second Retrofit instance points at the route server `https://route.teamwork.uz/` (UZ; `kgroute.` / `kzroute.` for KG/KZ) and is not used by any of the order endpoints below. Paylov routes escape v1 with a relative `../paylov/...` path; no order endpoint does.

**Socket:** `wss://prod.mehrgo.uz/socket`.

**Headers on every request** (`HeaderInterceptor`): `Authorization: Bearer <auth_key>`, `Accept-Language: <uz|kk|ky|ru>`, plus the device block `X-Platform`, `App-Version`, `OS-Version`, `Device-Brand`, `Device-Model`.

**Success envelope** — every endpoint in this appendix returns:

```json
{ "data": { } }
```

Kotlin: `class BaseResponse<T>(var data: T? = null)`. `data` is **nullable on every response** — the app treats a missing `data` as "no payload", never as an error. There is no `status`/`success` field on the success envelope. When the payload is a list, `data` is the array itself.

**Error envelope** — any non-2xx body deserializes to:

```json
{ "status": 422, "message": "order_not_found" }
```

Kotlin: `class ErrorResponse(var status: Int?, var message: String?)`. Both fields nullable. Every use case funnels errors identically:

- `HttpException` → parse body as `ErrorResponse`, surface `message`, fall back to a localized generic "server error" when absent.
- `IOException` → localized "connection error".
- HTTP 401 is special-cased by the map screen: it wipes the stored user and navigates to login.

`message` is sometimes a raw machine key (`pinfl_not_match`, `card_is_blocked`). Android added `Helper.humanizeServerError()`: known keys → localized sentence; any other bare snake_case token (contains `_`, no whitespace, all lowercase) → generic server error, so a key is never shown verbatim; real sentences pass through. **iOS must reimplement this filter** or drivers will see machine keys in toasts.

**Lenient number decoding (mandatory).** This fleet's backends intermittently send numeric fields as `""`, `null`, or the wrong JSON type (`"total": ""`, `"waiting_cost": null`, `"distance_km": ""`, numbers as strings `"5"`). Android had to install a lenient Gson (`common/AppGson.kt`) after production crashes: non-optional numerics coerce to `0`, optional ones to `nil`, numeric strings parse as numbers. On iOS, decode **every** numeric field through a permissive strategy (custom `KeyedDecodingContainer` helper that tries `Int`/`Double`/`String`/`null`), and make every money/distance field `Double?` rather than `Int`. A live capture of `"waiting_cost": 2613.33` against a `Long` field crashed the Android app outright.

**Money unit:** all money fields are **so'm**, not tiyin — but they are *not always integers* (`live.waiting_cost` arrives fractional). Never model money as `Int`.

---

#### `POST /order-gps/batch?order_id={id}` — GPS track upload + live server fare (existing endpoint, NEW usage: armed at ACCEPT)

**When it fires:**
`GpsBatchUploader` runs one instance per active order.

- **New in this range:** the uploader is armed as soon as the order reaches **state 2 (ACCEPTED)**, not at Boshlash. `MapFragment.commandArmGpsFromAccept()` sends `ACTION_START_TRACKING` with `with_timer=false`, which turns GPS capture + upload on but does **not** start the execution-time timer or the local distance meter (those still start at Boshlash / `order/start`). Previously the uploader only forwarded points once the driver pressed Go (`rideHasGone`), so the driver→pickup approach leg never reached the backend.
- Points are enqueued to a local (Room) FIFO the instant a GPS sample passes the client filter, so they survive process death and network loss.
- A drain fires when **≥ 20 points** are buffered, **or 15 s** since the last drain, whichever comes first; also on every 5 s wait tick, at Go, on socket reconnect and at trip end (`flushNow()`).
- Max **50 points per request**. Larger backlogs are sent as consecutive requests in one drain loop.
- If the socket is connected the same body is sent as a socket frame `{"key":"order_gps_batch","data":{order_id, points, waiting_time, waiting_time_ontheway}}` and the ack (same `FareResponse` shape) is awaited FIFO; REST is the fallback. iOS may implement REST only — the server supports both.
- **Timer-only batches:** when no GPS point passed the filter (stationary driver) but a wait timer moved, a batch with `"points": []` is still sent carrying only the timers. Without this the waiting bill never reaches the server.

**Client-side point filter (must be ported or the server sees a jagged/oversampled track):** drop `accuracy > 150 m` always; drop `accuracy > 50 m` unless a moving point is overdue; otherwise keep a point when any of: moved ≥ 50 m, bearing changed ≥ 15°, stopped↔moving transition crossed (speed threshold 1.94 m/s ≈ 7 km/h), or ≥ 6 s since the last kept point while moving.

**Request**

| field | in | type | required | unit / notes |
|---|---|---|---|---|
| `order_id` | query | Int | yes | the order being metered |
| `points` | body | array | yes | may be **empty** (timer-only batch); ≤ 50 entries |
| `points[].lat` | body | Double | yes | WGS84 degrees |
| `points[].lon` | body | Double | yes | WGS84 degrees |
| `points[].accuracy` | body | Float? | no | metres; null when the fix has no accuracy |
| `points[].speed` | body | Float? | no | **m/s** — the server multiplies by 3.6 itself. Do **not** send km/h |
| `points[].bearing` | body | Float? | no | degrees 0–360 |
| `points[].altitude` | body | Double? | no | metres |
| `points[].provider` | body | String? | no | e.g. `"gps"`, `"fused"`, `"go-seed"` |
| `points[].ts` | body | Long | yes | **epoch milliseconds** of the fix |
| `points[].cpid` | body | Long | yes | monotonic per-order client point id; **idempotency key** (server does `ON CONFLICT DO NOTHING`). Re-sending the same cpid after a retry must not grow distance/fare |
| `points[].state` | body | Int? | yes* | **ADDED 2026-07-30.** Order state at the moment the fix was **captured** (2 accepted / 7 started / 8 arrived / 9 gone) — NOT the state at upload time. The server buckets each point by ITS state: only `state = 9` points are the billable trip; earlier states are the driver→pickup approach. Stamp at capture/enqueue time, never at flush time — a batch may drain after a transition (order 79557: a post-arrival flush of the buffered approach leg was billed at the full trip rate). *null tolerated for legacy clients only |
| `waiting_time` | body | Long? | no | **cumulative pickup wait in MILLISECONDS**. Sent only when the value changed since the last successful send |
| `waiting_time_ontheway` | body | Long? | no | **cumulative on-route wait (after Go) in MILLISECONDS**, same "only when changed" rule |

`cpid` must survive process restart: Android seeds the counter from `max(lastCpidInDb, lastCpidInPrefs)`. iOS must persist the high-water mark before the point is uploaded, not after.

```json
{
  "points": [
    {
      "lat": 41.311081,
      "lon": 69.240562,
      "accuracy": 8.5,
      "speed": 11.4,
      "bearing": 173.2,
      "altitude": 455.0,
      "provider": "fused",
      "ts": 1753776012345,
      "cpid": 137,
      "state": 9
    },
    {
      "lat": 41.310204,
      "lon": 69.241377,
      "accuracy": 6.0,
      "speed": 12.9,
      "bearing": 168.0,
      "altitude": 454.0,
      "provider": "fused",
      "ts": 1753776018345,
      "cpid": 138,
      "state": 9
    }
  ],
  "waiting_time": 184000,
  "waiting_time_ontheway": 0
}
```

**Response** — `BaseResponse<FareResponse>`. The `live` block is the one the trip screen renders; the flat top-level fields are legacy and **incomplete** (the top-level `price` is known to omit services — backend bug 76967).

| field | type | notes |
|---|---|---|
| `order_id` | Long? | echo |
| `distance_km` | Double? | legacy flat total, km |
| `in_city_km` | Double? | km |
| `out_city_km` | Double? | km |
| `waiting_sec` | Long? | seconds |
| `traffic_sec` | Long? | seconds |
| `price` | Long? | legacy rounded total, so'm — **do not display**, lacks services |
| `fare` | Double? | legacy raw total before rounding, so'm |
| `breakdown` | object? | opaque; kept as raw JSON, never decoded into a type |
| `price_breakdown` | object? | opaque, same |
| `live` | object? | **preferred source of truth** when present (below) |
| `events` | [String]? | one-shot billing events, e.g. `waiting_charge_started`, `waiting_charge_step`. Fire a sound/UI cue once per delivery — they are never repeated |
| `live.distance_km` | Double? | **billed trip distance**, km |
| `live.to_client_km` | Double? | **approach-leg distance (driver→pickup), km — diagnostic, NOT billed** |
| `live.waiting_sec` | Long? | pickup wait, **seconds** (note: uploaded as ms, returned as s) |
| `live.on_way_sec` | Long? | on-route wait, **seconds** |
| `live.waiting_cost` | Double? | so'm, **fractional in practice** |
| `live.services_price` | Double? | so'm |
| `live.podacha` | Double? | pickup/dispatch surcharge, so'm |
| `live.extra_price` | Double? | so'm |
| `live.fare` | Double? | ride component only, so'm |
| `live.price` | Double? | **current TOTAL** = round(fare + services + podacha + extra + waiting_cost). This is the hero number |
| `live.agreed_price` | Double? | only on orders WITH a B point; null otherwise |
| `live.surcharge` | Double? | waiting surcharge above the agreed price (rounded estimate); B-point orders only |
| `live.kept_projection` | Double? | projected total if finished at B = agreed + waiting; B-point orders only |
| `live.waiting_billing` | Bool? | true = the free waiting window is over and waiting is billing right now |

```json
{
  "data": {
    "order_id": 78447,
    "distance_km": 6.42,
    "in_city_km": 6.42,
    "out_city_km": 0,
    "waiting_sec": 184,
    "traffic_sec": 0,
    "price": 24000,
    "fare": 23850.5,
    "breakdown": { "tariff_id": 12 },
    "price_breakdown": { "base": 12000, "per_km": 1900 },
    "live": {
      "distance_km": 6.42,
      "to_client_km": 3.11,
      "waiting_sec": 184,
      "on_way_sec": 0,
      "waiting_cost": 2613.33,
      "services_price": 2000,
      "podacha": 3000,
      "extra_price": 0,
      "fare": 18000,
      "price": 25613,
      "agreed_price": 24000,
      "surcharge": 1613,
      "kept_projection": 25613,
      "waiting_billing": true
    },
    "events": ["waiting_charge_step"]
  }
}
```

**Approach-leg vs trip bucketing — UNCONFIRMED.** The app now uploads *every* sample from `order/start` onward, including the driver→pickup approach leg. The working assumption, written into the Android source, is: *the server buckets by the order's state at upload time — points received while the order is at state 7/8 land in `to_client_km` (diagnostic only) and points received at state 9 land in `distance_km` (billed) — so shipping the approach leg does not over-bill.* **This was never confirmed in writing by the backend owner (Muhammad).** It was verbally agreed 2026-07-24 and it supersedes the earlier 2026-07-18 "send only the A→B leg" rule. If the assumption is wrong, every trip is over-billed by the approach distance. iOS should not ship the accept-time arming until this is confirmed, or should gate it behind a remote flag.

**Errors:**

| code | app behaviour |
|---|---|
| 400 | payload structurally bad → **drop that batch** (delete the points) and continue with newer points; treated as transient so the loop keeps going |
| 403 / 404 | treated as **transient**: the first flush after STARTED can beat the server's own state commit. Retry the *same* cpids (server de-dupes). Only after **5 consecutive** rejects give up and latch the uploader off until a lifecycle transition (`start` / `flushNow` / `reArm`, e.g. the driver pressing Go) re-arms it |
| 401 and other 4xx | terminal → stop the uploader, points stay queued, re-armed on the next lifecycle transition |
| 5xx / network | transient → exponential backoff 2 s → ×2 → cap 60 s, same cpids |

No error is shown to the driver; the uploader is entirely silent.

**iOS notes:**
- `speed` is m/s and `ts` is epoch **ms**; `CLLocation.speed` is already m/s (and is `-1` when unknown → send `nil`, not `-1`).
- `waiting_time*` go **up** in milliseconds but come **back** in seconds (`live.waiting_sec`). Do not round-trip one into the other.
- `live.*` money fields must be `Double?`. A `Int`/`Long` model here caused a hard crash on `"waiting_cost": 2613.33`.
- `breakdown` / `price_breakdown` must stay opaque (`[String: AnyCodable]` or raw `Data`) — the server's shape changes without notice.

---

#### `GET /order-gps/fare?order_id={id}` — fare snapshot for reconnect / screen reopen (existing endpoint, new call sites)

**When it fires:**
1. On **every order refresh** where `state >= 7`, debounced to at most once per 8 s (`FARE_REFRESH_MIN_INTERVAL_MS`), and **without** the debounce when the services total changed. Reason: `live` is otherwise only refreshed by a batch ack, and a stationary driver sends no batches — the fare froze while the rider added/removed a paid service.
2. At **finish**, started in parallel with the finish dialog's `/user/me` refresh; the receipt **waits** (bounded join) for it, because rendering the receipt from a stale snapshot bills the stale number.
3. On socket reconnect / screen reopen to resync.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | query | Int | yes | — |

No body.

**Response:** identical to `POST order-gps/batch` (same `FareResponse`, same `live` block). See the table above. `events` are typically absent here — they are one-shot and only ride the batch ack / socket push.

```json
{ "data": { "order_id": 78447, "live": { "distance_km": 6.42, "to_client_km": 3.11, "waiting_sec": 184, "on_way_sec": 0, "waiting_cost": 2613.33, "services_price": 2000, "podacha": 3000, "extra_price": 0, "fare": 18000, "price": 25613, "agreed_price": null, "surcharge": null, "kept_projection": null, "waiting_billing": true } } }
```

**Errors:** standard envelope. A failure is swallowed (the screen keeps the previous snapshot); only a `Resource.Success` is applied.

**iOS notes:**
- Accepts either the driver's or the passenger's bearer token — same payload.
- Before state 7 this endpoint has nothing to report (no track yet); Android skips calling it and re-fetches the *order* instead to pick up a server-side reprice.
- Treat the returned snapshot as authoritative for billing; any locally-derived number is display-only and must never be written into the value posted at finish.

---

#### `GET /user/me` — the active-order poll the map runs (existing endpoint, this IS the active-order fetch)

**When it fires:** this is the map's `getActiveMyOrders()`. It runs on: screen open / `onResume`, every socket order event (`ORDER_NEW`, `ORDER_ACCEPTED`, `ORDER_CANCELLED`, `ORDER_CANCELLED_PRIVATE`), after every state change (`start` / `arrive` / `go` / `cancel`), and on a **10 s poll while the order has not yet reached state 9** (`PRE_GO_REPRICE_POLL_MS`, only while the map is foregrounded). That poll exists purely as a workaround: the backend sends **no** signal at all when the rider toggles a paid service before Go — no socket frame, no FCM. Delete it once the backend pushes a reprice signal.

There is also `GET order/my-orders` (`BaseResponse<[Order]>`) with the same semantics, but the map does not use it — it reads `user/me`'s `orders` array so it gets the driver, today's totals and the work status in the same round-trip.

**Request:** no parameters.

**Response** — `BaseResponse<User>`:

| field | type | notes |
|---|---|---|
| `id` | Int? | driver id |
| `first_name`, `father_name`, `last_name` | String? | |
| `phone` | String? | |
| `status.int` | Int? | driver state: 1 deleted, 5 info completed, 7 code confirmed, 9 driver info completed, 10 active, 11 turned-not-active |
| `status.string` | String? | human label |
| `auth_key` | String? | bearer token |
| `balance` | Int? | so'm |
| `branch.id` | Int | |
| `branch.name` / `branch.city` | String? | label — use `name` else `city` |
| `branch.dispetcher_number` | String? | dispatcher phone, used by the "contact support" popup |
| `branch.blocked_aps` | String? | competitor-app blocklist (note the typo) |
| `today.count` | Int? | trips today |
| `today.price` | Long? | earnings today, so'm |
| `tariffs[].name` | String? | |
| `car` | object? | same shape as `Order.car` |
| `rating` | Double? | |
| `created_at` | String? | flat string here (unlike `Order.created_at`) |
| `orders` | [Order]? | **the active orders**. The map takes `orders[0]` |
| `device_token` | String? | |
| `workStatus.status` | String | `"completed"` = off shift. Non-null in the model — a missing value crashes Android |

**`Order` payload (the object the whole trip UI is built from):**

| field | type | notes |
|---|---|---|
| `id` | Int | |
| `state` | Int | **2** accepted, **7** started (driving to pickup), **8** arrived at pickup, **9** gone (client aboard, trip running). Mutable — every state POST returns the updated order |
| `status.int` / `status.string` | Int / String | order-history status: 10 cancelled, 11 accepted, 12 finished, 15 doing |
| `from` | Int | who created it: **25** client, **1** driver (taximeter), **3** manager, **4** dispatcher, **10** admin. *Legacy numbering existed where manager=10 / admin=… — check both when chasing old rows* |
| `price` | Int | so'm — **already includes the client's paid services**; never add `order_items` on top |
| `starting_price` | Int | so'm |
| `price_of_per_unit` | Int | in-city per-km rate, so'm |
| `podacha` | Int? | dispatch/pickup surcharge, so'm |
| `distance` | Double | **kilometres** (multiplied by 1000 wherever metres are needed) |
| `latitude`, `longitude` | String | **numbers as strings** — pickup point |
| `info` | String? | client comment |
| `contact.id/name/phone` | Int/String/String | client; `contact.bonus` Long? = client's bonus balance |
| `address.id/name/latitude/longitude` | Int/String/String/String | pickup landmark; lat/lon are strings |
| `address_category.id/name` | Int/String | |
| `address_finish` / `address_category_finish` | same shapes, nullable | destination landmark |
| `locations[]` | array | the real route points: `name` String, `position` Int (order), `lat` Double, `lon` Double. **Note:** here `lat`/`lon` are real Doubles, unlike `address.latitude` |
| `order_items[]` | array? | the paid services on this order: `id` Int, `price` String, `total` Int (so'm), `service.name` String, `service.info` String?, `service.icon` String? (**not on the wire yet** — always null on prod as of 2026-07-21; fall back to a local glyph keyed on `service.name`, not on id) |
| `branch.id` | String | **string here, Int on `user/me`** |
| `branch.name` | String | |
| `branch.lat` / `branch.lon` / `branch.radius` | String? | city centre + radius, numbers as strings |
| `branch.accept_waiting` | Int? | |
| `branch.dispetcher_number` | String | |
| `branch.clientBonusSettings.minimum_amount_to_use_bonus` | Long | so'm |
| `branch.clientBonusSettings.maximum_amount_to_use_bonus_per_order` | String | so'm as string |
| `branch.polygon.boundary` | String? | WKT `POLYGON((lon lat, …))` — parsed client-side for in-city/out-city split |
| `tariff.id` | Int | |
| `tariff.name` | String? | |
| `tariff.min_distance` | Int? | free distance, **metres** |
| `tariff.distances[]` | array? | `id` Int, `start` Long, `end` Long, `price` Long — banded pricing |
| `tariff.price_of_out` | String | out-of-city per-km rate, so'm as string |
| `tariff.min_wait_time` | String | free pickup wait, **seconds**, as string |
| `tariff.price_of_waiting` | String | pickup wait rate **per minute**, so'm as string |
| `tariff.min_wait_time_on_way` | Int? | free on-route wait, **seconds** |
| `tariff.price_of_waiting_on_way` | Int? | on-route wait rate per minute. **`null` OR `<= 0` means "not set" → fall back to `price_of_waiting`** (mirrors the backend) |
| `tariff.comission` | String? | note the spelling |
| `driver_number` | String | |
| `car.car_number` | String? | |
| `car.car_model.id/name/second_name` | Int?/String?/String? | |
| `car.car_color.id/name/second_name` | Int?/String?/String? | |
| `use_bonus` | Bool | client wants to spend bonus |
| `promo.usage.code` | String | |
| `promo.usage.amount` | String | so'm as string |
| `is_card_payment` | Bool? | true = card, false/null = cash. Drives the payment chip colour |
| `created_at` | object? | **newly parsed in this range** |
| `created_at.int` | Long? | epoch **seconds** on the wire per the history model's convention — treat as a timestamp, verify before doing date math |
| `created_at.datetime` | String? | e.g. `"Jul 24, 2026y 22:25"` — note the literal **`y` after the year**; Android strips `"y "` before display |
| `created_at.date` | String? | |
| `created_at.time` | String? | |

`created_at` is **nullable on purpose**: slim/older payloads omit it, and the pool card hides the "placed at" row when it is absent. It was added so a driver can spot a stale pooled order at a glance.

```json
{
  "data": {
    "id": 4021,
    "first_name": "Aziz",
    "last_name": "Karimov",
    "phone": "+998901234567",
    "status": { "int": 10, "string": "active" },
    "balance": 154000,
    "branch": { "id": 3, "name": "Toshkent", "dispetcher_number": "+998 55 516 19 19", "blocked_aps": "" },
    "today": { "count": 7, "price": 184000 },
    "rating": 4.8,
    "workStatus": { "status": "started" },
    "orders": [
      {
        "id": 78447,
        "state": 9,
        "status": { "int": 15, "string": "doing" },
        "from": 25,
        "price": 25000,
        "starting_price": 12000,
        "price_of_per_unit": 1900,
        "podacha": 3000,
        "distance": 6.42,
        "latitude": "41.311081",
        "longitude": "69.240562",
        "info": "2-podyezd",
        "is_card_payment": false,
        "use_bonus": false,
        "promo": null,
        "driver_number": "+998901234567",
        "contact": { "id": 9912, "name": "Dilnoza", "phone": "+998935556677", "bonus": 0 },
        "address": { "id": 55, "name": "Chorsu bozori", "latitude": "41.326000", "longitude": "69.234000" },
        "address_category": { "id": 2, "name": "Bozor" },
        "address_finish": { "id": 71, "name": "Oybek metro", "latitude": "41.293000", "longitude": "69.279000" },
        "address_category_finish": { "id": 4, "name": "Metro" },
        "locations": [
          { "name": "Navoiy ko'chasi 12", "position": 0, "lat": 41.326, "lon": 69.234 },
          { "name": "Oybek metro", "position": 1, "lat": 41.293, "lon": 69.279 }
        ],
        "order_items": [
          { "id": 811, "price": "2000", "total": 2000, "service": { "name": "Konditsioner", "info": null, "icon": null } }
        ],
        "branch": {
          "id": "3",
          "name": "Toshkent",
          "lat": "41.311081",
          "lon": "69.240562",
          "radius": "25000",
          "accept_waiting": 1,
          "dispetcher_number": "+998 55 516 19 19",
          "clientBonusSettings": { "minimum_amount_to_use_bonus": 5000, "maximum_amount_to_use_bonus_per_order": "20000" },
          "polygon": { "boundary": "POLYGON((69.1 41.2, 69.4 41.2, 69.4 41.4, 69.1 41.4, 69.1 41.2))" }
        },
        "tariff": {
          "id": 12,
          "name": "Standart",
          "min_distance": 2000,
          "distances": [ { "id": 1, "start": 0, "end": 5000, "price": 1900 } ],
          "price_of_out": "2400",
          "min_wait_time": "180",
          "price_of_waiting": "500",
          "min_wait_time_on_way": 120,
          "price_of_waiting_on_way": 600,
          "comission": "10"
        },
        "car": {
          "car_number": "01A123BC",
          "car_model": { "id": 4, "name": "Cobalt", "second_name": "Cobalt" },
          "car_color": { "id": 2, "name": "Oq", "second_name": "Белый" }
        },
        "created_at": { "int": 1753380300, "datetime": "Jul 24, 2026y 22:25", "date": "Jul 24, 2026", "time": "22:25" }
      }
    ]
  }
}
```
*(inferred values in the example: `status.string`, `workStatus.status: "started"`, `accept_waiting`, `comission`, `distances[]` contents, and the exact `created_at.int` epoch — the field names are from the models, these specific values were not captured from prod.)*

**What the app does with each state:**
- `orders` non-empty → show the trip sheet, set the stage label from `state` (2 → "accepted", 7 → "going to pickup", 8 → "waiting at pickup", 9 → "in trip"), hide the "end shift" button, and refresh the server fare.
- `state >= 7` → start full tracking (GPS + timer + local meter).
- `state == 2` → **arm GPS upload only** (`with_timer=false`).
- `state == 8` → re-arm the background auto-start.
- `orders` empty **and** `workStatus.status == "completed"` **and** the tracking service is running → tear down tracking and stop the service. The `workStatus` guard is load-bearing: a transient empty `orders` on a live trip must never kill tracking.
- Error `401` → wipe user, go to login. Any other error → toast the message, keep the map.

**Errors:** standard envelope; `401` is the only one with special handling.

**iOS notes:**
- `branch.id` is a **String** inside `Order` and an **Int** inside `User` — two different types for the same concept in one response. Decode each independently.
- `latitude`/`longitude` on `order` and `order.address*` are strings; on `order.locations[]` they are real doubles named `lat`/`lon`. Do not share a coordinate decoder between them.
- `workStatus` is required by the Android model; make it optional on iOS anyway and default to off-shift, since a null there was an Android crash class.
- `created_at.datetime` contains a literal `y` after the year — strip `"y "` before display, don't try to parse it with a standard formatter.

---

#### `GET /order/list` — the broadcast order pool (existing endpoint, new field rendered)

**When it fires:** the pool map/list screen on open, and on every `ORDER_NEW` / `ORDER_ACCEPTED` / `ORDER_CANCELLED` socket frame. Short-circuited client-side when the driver is off-shift (`isServiceRunning != true`) — the app renders an empty list with a "start your shift" hint and makes no request.

`GET /order/index/{category_id}` exists for a category-filtered pool (`BaseResponse<[Order]>`) but the current screen uses the unfiltered `order/list`.

**Request:** none.

**Response:** `BaseResponse<[Order]>` — same `Order` shape as above.

```json
{ "data": [ { "id": 78448, "state": 2, "price": 19000, "created_at": { "int": 1753380900, "datetime": "Jul 24, 2026y 22:35", "date": "Jul 24, 2026", "time": "22:35" } } ] }
```
*(truncated for brevity — a pool order carries the full `Order` payload.)*

**New usage in this range:** the pool card now shows `created_at.datetime` (with `"y "` stripped) so a driver can tell how stale a pooled order is. The row is hidden entirely when `created_at` is absent.

**Errors:** standard envelope; the message is toasted and the previous list is kept.

**iOS notes:** the pool refresh is event-driven, not polled — bad frames must be dropped, not crashed on (Android added a try/catch around the socket-frame parse for exactly this).

---

#### `POST /order/accept?id={id}` — driver accepts a pooled/offered order (existing endpoint, new side effect)

**When it fires:** driver taps Accept on the offer overlay (`AutoOfferService`), the in-app offer dialog (`MainActivity`), or the pool list (`OrdersMapFragment`).

**Request** — `application/x-www-form-urlencoded`. Note the split: **id in the query, location in the body**.

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `id` | query | Int | yes | order id |
| `lat` | body | Double? | no | last known fix; null when no fix yet |
| `long` | body | Double? | no | note the name — `long`, not `lon` |
| `accuracy` | body | Float? | no | metres |

```
id=78447            (query)
lat=41.311081&long=69.240562&accuracy=8.5     (body)
```

**Response:** `BaseResponse<Order>` — the accepted order at `state: 2`.

```json
{ "data": { "id": 78447, "state": 2, "status": { "int": 11, "string": "accepted" } } }
```
*(truncated — the full `Order` payload comes back.)*

**Side effect new in this range:** on success the app sets a "open the trip detail on next load" flag and, once the order shows up in `user/me`, immediately arms `order-gps/batch` (see the batch endpoint). Nothing extra goes on the wire at accept time.

**Errors:** standard envelope. The usual real-world case is another driver taking the order first; the server message is shown in an info popup.

**iOS notes:** `long`, not `lon` — this endpoint disagrees with `order-gps/batch` (`lon`) and `location/send` (`lon`). Every order state-change endpoint uses `long`.

---

#### `GET /order/order-skip?order_id={id}&lat=&long=&accuracy=` — driver declines an offer

**When it fires:** the driver dismisses/skips an offer, or the auto-offer countdown expires.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | query | Int | yes | |
| `lat` | query | Double? | no | GET, so location rides in the query here |
| `long` | query | Double? | no | |
| `accuracy` | query | Float? | no | metres |

**Response:** `BaseResponse<Any>` — payload ignored.

```json
{ "data": true }
```
*(payload shape inferred — the app never reads it.)*

**Errors:** silent; the offer UI closes regardless.

**iOS notes:** GET with side effects, and the parameter is `order_id` here but `id` on accept. Do not normalise.

---

#### `POST /order/start?order_id={id}` — Boshlash: driver sets off toward the pickup (state 7)

**When it fires:** the driver completes the "Boshlash" slider.

**Request** — form body; `order_id` in the **query**.

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | query | Int | yes | |
| `lat` | body | Double? | no | |
| `long` | body | Double? | no | |
| `accuracy` | body | Float? | no | metres |

**Response:** `BaseResponse<Order>` with `state: 7`.

**Side effects in the app:** full tracking starts (`with_timer=true`) — execution-time timer and the local distance meter begin here, not at accept. The GPS uploader was already running from accept.

**Errors:** standard envelope; message toasted, slider resets.

---

#### `POST /order-change/state?state=8&id={id}` — arrived at pickup

#### `POST /order-change/state?state=9&id={id}` — Kettik / Go: client aboard, trip metering begins

**When they fire:** the "Yetib keldim" and "Kettik" sliders; state 9 can also be triggered automatically by the tracking service when the car exceeds the auto-start speed while armed.

**Request** (identical for both; only the `state` query differs)

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `state` | query | Int | yes | literal `8` or `9`, baked into the path string |
| `id` | query | Int | yes | order id (`id`, not `order_id`) |
| `lat` | body | Double? | no | |
| `long` | body | Double? | no | |
| `accuracy` | body | Float? | no | metres |

**Response:** `BaseResponse<Order>` with the updated `state`.

```json
{ "data": { "id": 78447, "state": 9 } }
```
*(truncated — full `Order` returned.)*

**Side effects at state 9:** the uploader is re-armed (`reArm()`, clearing any 403/404 give-up latch) and a "go-seed" GPS point is injected at the exact Go position so metering starts at the pickup rather than at the next GPS callback. The pickup wait clock is frozen at this instant and everything after it counts as on-route wait.

**Errors:** standard envelope; toasted, slider resets.

**iOS notes:** the query key is `id` here but `order_id` on `order/start` — inconsistent by design of the backend, not a typo in this doc.

---

#### `GET /order-cancel-issue?type=1` — the cancel-reason list

**When it fires:** the driver taps "Bekor qilish" **while the order is still at state 2 (ACCEPTED)**. From state 7 onward the same button does **not** call this — it opens a "contact the dispatcher" popup instead, because the driver is not permitted to cancel a trip that is under way.

**Request:** no parameters beyond the fixed `type=1` baked into the path.

**Response:** `BaseResponse<[OrderCancelReason]>`

| field | type | notes |
|---|---|---|
| `id` | Int? | passed back as `issue_id` on cancel |
| `name` | String? | localized label (server honours `Accept-Language`) |

```json
{ "data": [
  { "id": 1, "name": "Mijoz javob bermadi" },
  { "id": 2, "name": "Mijoz bekor qildi" },
  { "id": 5, "name": "Manzilga yetib bo'lmadi" }
] }
```
*(ids and labels inferred — only the field names are from the model.)*

`isChecked` in the Android model is a local UI flag, **not** on the wire.

**Errors:** standard envelope; message toasted, the sheet does not open.

**iOS notes:** both fields are optional; a reason with a null `id` must not be selectable. Selection is single-select and **non-deselecting** — tapping the chosen row again keeps it selected. Submit stays disabled until a reason is picked.

---

#### `POST /order/cancel` — driver cancels the order with a reason

**When it fires:** the driver confirms a reason in the cancel sheet. Only reachable at state 2 (see above).

**Request** — form-urlencoded, **everything in the body** (no query params — unlike every other order action).

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | body | Int | yes | |
| `issue_id` | body | Int | yes | id from `order-cancel-issue` |
| `lat` | body | Double? | no | last fix at cancel time |
| `long` | body | Double? | no | |
| `accuracy` | body | Float? | no | metres |

```
order_id=78447&issue_id=2&lat=41.311081&long=69.240562&accuracy=8.5
```

**Response:** `BaseResponse<Any>` — payload not read.

```json
{ "data": true }
```
*(shape inferred; only success/failure is used.)*

**On success the app must:** dismiss the sheet, toast "order cancelled", **explicitly stop tracking** (otherwise the next order inherits a stale `isTracking`/orderId/flag set — this was a real bug), then re-fetch `user/me`. On error: keep the sheet open, restore the Confirm button, toast `humanizeServerError(message)`.

**Errors:** standard envelope. Messages here are frequently machine keys — run them through the humanizer.

**Do not use `POST /socket/order-cancel`.** It exists in the API interface but the repository deliberately routes to `order/cancel`: the `socket/*` routes reject the driver bearer token with 401 ("register to use the app") — they are a different auth surface. `POST /socket/order-cancel-and-renew` (cancel + re-offer to other drivers) is declared but wired to no UI.

**iOS notes:**
- Loading state belongs *in* the Confirm button; the sheet must stay up until the server answers.
- The cancel POST is the only order endpoint with `order_id` in the body rather than the query.

---

#### `POST /order/complete?order_id={id}` — finish the trip (existing endpoint — known gap: NO receipt in the response)

**When it fires:** the driver completes the "Yakunlash" slider, reviews the finish dialog (which shows the server-derived breakdown) and taps Submit.

**Hard precondition:** the order must be at **state 9**. Completing straight from state 8 leaves the backend with no stage-9 GPS track and the trip bills as ~0 distance (observed in production). Android gates this in two places, including a defensive check inside `prepareOrderFinish()`; iOS must do the same.

**Request** — `order_id` in the query, a **JSON body** (this is the only order endpoint with a JSON body; all sibling calls are form-encoded).

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | query | String | yes | sent as a string |
| `distance` | body | String | yes | **kilometres, 2 decimals, as a string** (e.g. `"6.42"`). Taken from the server snapshot (`live.distance_km`) when available, local meter only offline |
| `latitude_finish` | body | Double? | yes (nullable) | finish position |
| `longitude_finish` | body | Double? | yes (nullable) | |
| `total_price` | body | String | yes | gross total in **so'm as a string**, before bonus/promo |
| `waiting_time` | body | String | yes | **pickup wait only, MILLISECONDS as a string** (frozen at Go). Older clients sent the combined wait here; the backend still accepts that |
| `execution_time` | body | String | yes | trip duration, **MILLISECONDS as a string** |
| `finish_address_id` | body | Int? | yes (nullable) | currently always `null` from the map screen |
| `bonus_payment` | body | Long | yes | client bonus applied, so'm |
| `promo_code_payment` | body | Long | yes | promo applied, so'm |
| `accuracy` | body | Float? | yes (nullable) | metres, from the same fix as lat/long |
| `waiting_time_ontheway` | body | String | yes | **on-route wait (after Go), MILLISECONDS as a string**; `"0"` when none. Missing ⇒ backend treats as 0 and prices it with `price_of_waiting_on_way`, falling back to `price_of_waiting` |

```json
{
  "distance": "6.42",
  "latitude_finish": 41.293004,
  "longitude_finish": 69.279117,
  "total_price": "25613",
  "waiting_time": "184000",
  "execution_time": "1284000",
  "finish_address_id": null,
  "bonus_payment": 0,
  "promo_code_payment": 0,
  "accuracy": 6.0,
  "waiting_time_ontheway": "0"
}
```

**Response** — `BaseResponse<User>`. **This is the known gap:** the response is the refreshed **driver user object** (same shape as `GET user/me`), with **no `final` / receipt block at all**. `total_price` and `distance` that the app just posted are not echoed back and are believed to be ignored by the backend's own settlement. The app therefore builds the receipt screen **entirely from the values it just submitted** plus the wait/services rows the finish dialog rendered — no second network call — so the receipt cannot disagree with what the server was sent.

```json
{ "data": { "id": 4021, "balance": 179613, "today": { "count": 8, "price": 209613 }, "workStatus": { "status": "started" } } }
```
*(truncated to the fields the app reads; the full `User` payload is returned. Balance/today values inferred.)*

**Receipt arithmetic the app performs locally** (port as-is): `finalTotal = total_price − bonus_payment − promo_code_payment`, floored at 0; `rideCost = total_price − waitCost − servicesCost`, floored at 0; displayed wait = `waiting_time + waiting_time_ontheway` (since `waiting_time` is now pickup-only).

**Errors:** standard envelope, **plus** a special bonus-rejection body used by this endpoint:

```json
{ "message": "bonus_not_allowed", "data": { "client_total_bonus": 12000, "clientBonusSettings": { "minimum_amount_to_use_bonus": 5000, "maximum_amount_to_use_bonus_per_order": "20000" } } }
```
*(the `message` key is inferred; the `data` shape is from `ErrorOrderFinishResponse`.)*

Here the error body carries a **`data` object**, unlike the plain `ErrorResponse` — decode defensively. On any error the finish dialog stays open with its button restored so the driver can retry.

**iOS notes:**
- Numbers-as-strings everywhere in this body (`distance`, `total_price`, `waiting_time`, `execution_time`, `waiting_time_ontheway`) — encode them as `String`, not numbers, or the backend may reject.
- Freeze the *displayed* total into the Submit action. A late fare snapshot arriving after the dialog rendered must not silently change what gets posted.
- Do not expect a receipt from this call. If/when the backend adds a `final` block, the local-receipt path can be deleted — until then, mirroring Android's "receipt from submitted values" is the only way the two screens agree.
- The finish dialog **pauses** the local wait meter while it is open; if the driver backs out ("Davom etish"), the wait must be resumed or they wait for free.

---

#### `GET /order/history?expand=myOrder&page={n}` — trip history list (existing endpoint; detail screen fixed in this range)

**When it fires:** history screen open and on each infinite-scroll page. Paged 1 item per page in the Android `Pager` config; the server's own page size governs. The paged flow is built **once** per ViewModel so history → detail → back does not refetch.

There is **no separate detail endpoint** — the detail screen renders the already-fetched list item passed through navigation. The fix in this range is purely client-side (see below).

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `expand` | query | String | yes | fixed `myOrder` — without it the nested order is absent |
| `page` | query | Int | yes | 1-based |

**Response** — `BaseResponse<OrderHistory>`:

| field | type | notes |
|---|---|---|
| `items[]` | array | history rows |
| `items[].id` | Int | **history-row id — a DIFFERENT id space from the order id** |
| `items[].status.int` | Int | 10 cancelled, 11 accepted, 12 finished, 15 doing |
| `items[].status.string` | String | localized label, shown in the status pill |
| `items[].created_at.datetime` | String | e.g. `"Jul 24, 2026y 22:25"` — strip `"y "` |
| `items[].created_at.date` | String | |
| `items[].created_at.time` | String | |
| `items[].myOrder.id` | Int | **the real order id** |
| `items[].myOrder.price` | String | so'm as a string; **already includes services** |
| `items[].myOrder.distance` | String? | **kilometres, as a string** |
| `items[].myOrder.execution_time` | String? | **MILLISECONDS as a string** |
| `items[].myOrder.waiting_time` | String? | **SECONDS on newer rows, MILLISECONDS on rows written before ~2026-07-18** — see the trap below |
| `items[].myOrder.address.name` | String? | pickup landmark |
| `items[].myOrder.address_category.name` | String? | |
| `items[].myOrder.address_finish.name` | String? | destination landmark |
| `items[].myOrder.locations[].name` | String? | real street names |
| `items[].myOrder.locations[].position` | String? | **string here**, Int on the live `Order` model |
| `items[].myOrder.tariff.name` | String? | |
| `items[].myOrder.contact.name` / `.phone` | String? | present but deliberately **not shown** on a completed order (privacy) |
| `items[].myOrder.order_items[]` | array? | `service.name` String, `service.icon` String? (not on the wire yet), `total` Int (so'm) |
| `items[].myOrder.info` | String? | client comment |
| `_meta.totalCount` | Int | |
| `_meta.pageCount` | Int | |
| `_meta.currentPage` | Int | |
| `_meta.perPage` | Int | |

```json
{
  "data": {
    "items": [
      {
        "id": 33915,
        "status": { "int": 12, "string": "Yakunlangan" },
        "created_at": { "datetime": "Jul 24, 2026y 22:25", "date": "Jul 24, 2026", "time": "22:25" },
        "myOrder": {
          "id": 78447,
          "price": "25613",
          "distance": "6.42",
          "execution_time": "1284000",
          "waiting_time": "184",
          "address": { "name": "Chorsu bozori" },
          "address_category": { "name": "Bozor" },
          "address_finish": { "name": "Oybek metro" },
          "locations": [
            { "name": "Navoiy ko'chasi 12", "position": "0" },
            { "name": "Oybek metro", "position": "1" }
          ],
          "tariff": { "name": "Standart" },
          "contact": { "name": "Dilnoza", "phone": "+998935556677" },
          "order_items": [ { "service": { "name": "Konditsioner", "icon": null }, "total": 2000 } ],
          "info": "2-podyezd"
        }
      }
    ],
    "_meta": { "totalCount": 214, "pageCount": 214, "currentPage": 1, "perPage": 1 }
  }
}
```
*(`status.string` label and `_meta` values inferred; field names and types are from the model.)*

**The `waiting_time` / `execution_time` unit trap (must be ported).** `execution_time` is milliseconds. `waiting_time` is **seconds on rows written after the order-gps rewrite (~2026-07-18) and milliseconds on older rows** — the backend changed the stored unit without changing the field. Proof: order 77390 posted `waiting_time=2802813` (ms) on complete and the history row came back as `2803`. Reading that as ms shows "00:00:02" for a 47-minute wait. The app infers the unit using the invariant *waiting ≤ execution*:

```
value = Long(waiting_time); if value <= 0 -> 0
asSeconds = value * 1000
if execution_time <= 0 -> use asSeconds
else -> use asSeconds if asSeconds <= execution_time, otherwise use value as-is (ms)
```

Delete this once the backend reports one unit for both fields.

**Detail-screen id fix (this range):** the detail header previously printed `items[].id` (the history-row id). Support and dispatch look trips up by **`myOrder.id`**, so the screen now prints that. Two different id spaces, both present on the same object — do not conflate them on iOS.

**Errors:** standard envelope; the paging footer shows a retry.

**iOS notes:**
- `locations[].position` is a **String** in the history payload and an **Int** in the live order payload — sort with a string→int coercion.
- `distance` and `price` are numeric strings; `_meta.perPage` reflects the server's page size regardless of what the client requests.
- The history row and the order carry independent ids; surface `myOrder.id` to the driver.

---

#### `GET /service/order?id={id}` — the paid-service catalogue for an order (existing endpoint, touched path)

**When it fires:** the driver opens the "Услуги" sheet from the trip detail.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `id` | query | Int | yes | order id |

**Response:** `BaseResponse<[OrderService]>`

| field | type | notes |
|---|---|---|
| `id` | Int | service id, used as `service_id` on toggle |
| `name` | String | |
| `value` | Int | price, so'm |
| `info` | String? | description |
| `enabled` | Bool | whether it is currently on for this order (defaults false client-side) |

```json
{ "data": [
  { "id": 3, "name": "Konditsioner", "value": 2000, "info": "Salonda sovutgich", "enabled": true },
  { "id": 5, "name": "Bagaj", "value": 3000, "info": null, "enabled": false }
] }
```
*(ids/labels inferred.)*

**Errors:** standard envelope; toasted.

---

#### `POST /order/item` — driver adds/removes a paid service mid-order (existing endpoint)

**When it fires:** the driver toggles a service in that sheet. Mutating a service changes the order price server-side.

**Request** — form-urlencoded, all in the body.

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `order_id` | body | Int | yes | |
| `service_id` | body | Int | yes | from `service/order` |
| `action` | body | Int | yes | add vs remove flag — **the exact values are not documented anywhere in the app**; the app passes through whatever the toggle produces |
| `lat` | body | Double? | no | |
| `long` | body | Double? | no | |
| `accuracy` | body | Float? | no | metres |

**Response:** `BaseResponse<Any>` — not read; the app re-fetches the order.

**Errors:** standard envelope; toasted.

**iOS notes:** after a successful toggle the order price must be re-pulled — and note that **the backend sends no push at all when the RIDER changes services before Go**, which is why the map polls `user/me` every 10 s until state 9.

---

#### Socket frames that carry the same order/fare payloads

Not REST, but the iOS networking layer needs them because they replace polling.

| frame `key` | payload | app behaviour |
|---|---|---|
| `order_new`, `order_new_private` | `Order` | new offer → offer overlay / pool refresh |
| `order_accepted` | `Order` | pool refresh |
| `order_cancelled`, `order_cancelled_for_nurse` | `Order` | **compare `data.id` against the active order id before tearing anything down** — this frame fires for *any* order including pool orders |
| `order_gps_batch` | `FareResponse` | ack for an outgoing batch frame; acks are **not** id-matched, they are consumed FIFO |
| `order_price_updated` | `FareResponse` | server-initiated live-fare push between batches; the one-shot `events` array arrives **only** here or on a batch ack and is never repeated |
| `notification_new` | notification | in-app notification |
| `receive_pong` | — | heartbeat; tolerate 3 consecutive misses before showing "reconnecting" |

Envelope: `{ "status": …, "key": "…", "data": { … } }`. `data` **must be modelled as optional** even when the key implies a payload — a frame with a missing/null `data` is a real occurrence and caused a production NPE.

---

### Wallet / Paylov — cards, withdrawal, balance ledger

Android source of truth: `app/src/main/java/uz/teamwork/mehrgodriver/data/remote/ApiService.kt` (Paylov block), DTOs in `domain/model/paylov/`, use cases in `domain/use_case/paylov/`, UI in `presentation/main/ui/my_payment/`, `.../add_card/`, `.../add_card_confirm/`, `.../paylov_history/`.

---

#### Bases, envelope and headers (read once, referenced by every endpoint below)

**Two different bases.** The Retrofit base URL is `Constants.BASE_URL` = `https://prod.mehrgo.uz/api/v1/`. Android writes the Paylov paths as **relative dot-segment paths** — `@POST("../paylov/user-card/create")` — which OkHttp resolves per RFC 3986 against `.../api/v1/` to `https://prod.mehrgo.uz/api/paylov/user-card/create`. **iOS must not copy the `../` literal** (URLComponents does not normalise dot-segments; you would ship a literal `/api/v1/../paylov/…`). Add a second base:

| logical base | resolved value | used by |
|---|---|---|
| `main` (v1) | `https://prod.mehrgo.uz/api/v1/` | `balance/index` **only** (of this group) |
| `paylov` | `https://prod.mehrgo.uz/api/paylov/` | all `user-card/*`, `withdrawal/*`, `payment/*` |

Note the trailing slash on both; paths are appended without a leading slash. Host comes from `Constants.BASE` (`prod.mehrgo.uz` for Mehrgo prod; dev preset is `dashboard.ayoltaxi.uz`), so derive both bases from the same host constant and they follow the env switch together.

**Success envelope.** Every endpoint in this group returns `BaseResponse<T>`:

```kotlin
class BaseResponse<T>(var data: T? = null)
```

i.e. the wire shape is always `{ "data": <T or null> }`. `data` is **optional in every response** — Gson yields null when absent; Swift must model it as `T?` or decoding throws. Endpoints typed `BaseResponse<Any>` (delete card, cancel withdrawal, payment confirm) are treated as "HTTP 2xx = success"; the app never reads their `data`.

**Error envelope.** On a non-2xx the body deserialises to:

```kotlin
class ErrorResponse(var status: Int? = null, var message: String? = null)
```

```json
{ "status": 400, "message": "pinfl_not_match" }
```

Both fields optional. Every Paylov use case follows the same funnel: `HttpException` → parse `message`, fall back to a generic localized "server error"; `IOException` → localized "no connection". A non-2xx body that does **not** parse as `ErrorResponse` degrades to the generic server error, never a crash.

**Headers** added to every request by `HeaderInterceptor`: `Authorization: Bearer <token>`, `Accept-Language: <uz|kk|ky|ru>`, `X-Platform: android`, `X-App-Version: <name>+<code>`, `X-OS-Version`, `X-Device-Brand`, `X-Device-Model` (ASCII-filtered, truncated to 64 chars). All Paylov endpoints are authenticated — there is no anonymous path here.

**Form encoding.** Everything marked `@FormUrlEncoded` is `application/x-www-form-urlencoded` (OkHttp `FormBody`), **not JSON**. Only the query-param endpoints (`user-card/delete`, `withdrawal/index`, `balance/index`) put values in the URL.

---

#### `GET /api/paylov/user-card/cards` — list the driver's saved Paylov cards

**When it fires:** `MyPaymentFragment.loadPaylov()`, on every entry to the "Hisob" screen (`onViewCreated`), on the refresh button, and after a successful card-add / card-delete / withdrawal-create / withdrawal-cancel. Failure is deliberately swallowed: `is Resource.Error -> renderCards()` renders the empty state, so an older backend returning 404 leaves the section quiet instead of showing an error.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| — | — | — | — | no parameters; auth header only |

```http
GET https://prod.mehrgo.uz/api/paylov/user-card/cards
Authorization: Bearer <token>
```

**Response** — `BaseResponse<CardsResponse>`, `CardsResponse { cards: List<PaylovCard>? }`

| field | type | notes |
|---|---|---|
| `data.cards[]` | array, optional | may be absent → treat as empty |
| `data.cards[].cardId` | String? | Paylov card token; the id passed to `withdrawal/create` (`card_id`) and `user-card/delete` (`cardId`). **camelCase on the wire.** |
| `data.cards[].number` | String? | display PAN, already masked by the server. Rendered verbatim as the row title. |
| `data.cards[].balance` | Int64? | **TIYIN** per the in-code annotation on `PaylovCard.balance`. Android never renders it. Unconfirmed by the backend team — see Unresolved. |
| `data.cards[].bankName` | String? | row subtitle |
| `data.cards[].vendor` | String? | fallback subtitle when `bankName` is null; row hides the subtitle when both are null |

```json
{
  "data": {
    "cards": [
      {
        "cardId": "c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11",   // inferred format
        "number": "8600 13** **** 4751",
        "balance": 1250000,                                  // inferred: tiyin = 12 500 so'm
        "bankName": "Kapitalbank",
        "vendor": "uzcard"
      }
    ]
  }
}
```

**Errors:** no keys are special-cased. Any non-2xx is silently absorbed into the empty state.

**iOS notes:**
- `CardsResponse.kt` carries an explicit comment that the backend returns **either** `data.cards[]` **or** a bare `data[]` array depending on build. Gson tolerates the mismatch by yielding null; `Decodable` will **throw**. Write a custom `init(from:)` that tries the keyed `cards` container and falls back to an unkeyed `[PaylovCard]`, or the list is permanently empty on one of the two backends.
- These four card keys are genuinely camelCase — no `CodingKeys` needed here, unlike every withdrawal/ledger DTO.
- Every field optional; `balance` is tiyin, do not render it as so'm.

---

#### `POST /api/paylov/user-card/create` — register a card, step 1 (triggers the SMS OTP)  *(existing endpoint, new usage)*

**When it fires:** `AddCardFragment.submit()` — the "Davom etish" CTA on the first add-card screen, enabled only when PAN digits == 16 **and** expiry digits == 4, and after a client-side check that the month is `1..12`. Also fired again by `AddCardConfirmVM.resend()` behind a 60 s cooldown — resend re-registers the **same** card and the server returns a **brand-new `cid`**, which the app overwrites before confirming. *New usage in this range:* the error path now runs through `Helper.humanizeServerError` instead of toasting the raw message.

**Request** (`application/x-www-form-urlencoded`)

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `cardNumber` | body | String | yes | 16 raw digits, **no spaces/mask** (`etCardNumber.unMasked`) |
| `expireDate` | body | String | yes | **`YYMM`**, 4 digits. The field is typed MM/YY; Android converts `expiry.substring(2) + expiry.substring(0,2)` → 12/27 becomes `"2712"`. Swift: `expiry.suffix(2) + expiry.prefix(2)` on the 4 raw digits. |
| `phoneNumber` | body | String? | no | declared in `ApiService`; **Android never sends it** (defaults to null → omitted from the form body) |

```
cardNumber=8600131234564751&expireDate=2712
```

**Response** — `BaseResponse<CardCreateResult>`

| field | type | notes |
|---|---|---|
| `data.cid` | String? | the card-registration id. Passed to `user-card/confirm` as **`cardId`**. If null the app shows the generic error and does not navigate. |
| `data.otpSentPhone` | String? | phone the OTP was delivered to; displayed on step 2 when non-blank |

```json
{
  "data": {
    "cid": "8f3c11d0-77a2-4a0e-8d1c-2b9e5f6a0c34",  // inferred format
    "otpSentPhone": "+998 9* *** ** 51"              // inferred masking
  }
}
```

**Errors:** `message` is passed through `Helper.humanizeServerError`:
- `card_is_blocked` → "Bu karta bloklangan. Iltimos, boshqa karta kiriting yoki bankingizga murojaat qiling." (the card is blocked by the bank / Paylov).
- `pinfl_not_match` → "Bu karta boshqa shaxsga tegishli. Faqat o'zingizning kartangizni qo'sha olasiz." (the card owner's PINFL does not match the account holder's, i.e. the card belongs to someone else). More commonly returned by `confirm`, but the humanizer is applied on both screens.
- Any other value that **looks like a machine key** (contains `_`, no whitespace, all lowercase) is replaced by the generic localized server error so the raw key never reaches the driver. Anything else is shown verbatim.

**iOS notes:**
- Send `expireDate` as `YYMM`, not `MMYY` and not `MM/YY` — the single most likely integration bug here.
- `cid` (response) and `cardId` (confirm request) are the **same value under two names**; do not confuse `cid` with `PaylovCard.cardId` from the cards list.
- On resend you must replace the stored `cid` and clear the OTP field, or confirm submits against a dead registration.

---

#### `POST /api/paylov/user-card/confirm` — register a card, step 2 (OTP)  *(existing endpoint, new usage)*

**When it fires:** `AddCardConfirmFragment.confirm()` — the submit CTA on the OTP screen, enabled at **>= 4 characters** (the Paylov OTP length is *not* pinned to 4/6 by the backend; Android uses a free-length field). On success it toasts `card_added` and pops straight back to the Hisob screen, which reloads `user-card/cards`. *New usage in this range:* errors go through `Helper.humanizeServerError`.

**Request** (`application/x-www-form-urlencoded`)

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `cardId` | body | String | yes | the `cid` from `user-card/create` |
| `otp` | body | String | yes | digits as typed; sent as a **String**, not an Int |
| `cardName` | body | String? | no | declared; **Android never sends it** |
| `pinfl` | body | String? | no | declared; **Android never sends it** — the PINFL check happens server-side and surfaces as the `pinfl_not_match` error |

```
cardId=8f3c11d0-77a2-4a0e-8d1c-2b9e5f6a0c34&otp=1234
```

**Response** — `BaseResponse<PaylovCard>` (same shape as one element of the cards list). The Kotlin declaration carries an explicit caveat: "Confirmed card is **assumed** to come back as data. If the server returns a different shape, switch to `BaseResponse<Any>`." **The caller never reads the payload** — it only branches on success/failure.

| field | type | notes |
|---|---|---|
| `data.cardId` / `number` / `balance` / `bankName` / `vendor` | see cards list | assumed, not verified against a live response |

```json
{
  "data": {
    "cardId": "c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11",  // inferred — shape assumed, unverified
    "number": "8600 13** **** 4751",
    "balance": 0,
    "bankName": "Kapitalbank",
    "vendor": "uzcard"
  }
}
```

**Errors:** `pinfl_not_match` and `card_is_blocked` as above (this is the endpoint where `pinfl_not_match` actually fires — the PINFL comparison happens after OTP verification). Wrong/expired OTP returns some other `message`; **its exact key is not known from the client code** and currently degrades to the generic server error if it looks like a snake_case key.

**iOS notes:**
- Decode the confirm response defensively (`BaseResponse<PaylovCard?>` or ignore the body entirely) — the payload shape is an assumption in the Android code, not a verified contract.
- Do **not** reuse a fixed-length OTP box component: gate on `>= 4` characters or a 5–6 digit Paylov code gets truncated.
- The 60 s resend countdown is client-side only; there is no server-side "resend" endpoint.

---

#### `DELETE /api/paylov/user-card/delete` — remove a saved card  *(existing endpoint, new usage)*

**When it fires:** the trash icon on a card row in `MyPaymentFragment.renderCards()`, behind an `InfoPopup.confirm` dialog ("Kartani o'chirasizmi?" + the card number) with an in-button spinner on the "Ha". On success the dialog dismisses and `loadPaylov()` re-runs (cards + withdrawal info). *New usage in this range:* the error toast now runs through `Helper.humanizeServerError` and the dialog button is restored so a retry is possible.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `cardId` | **query** | String | yes | `PaylovCard.cardId`. Query string, **not a body** — the request has no body at all. |

```http
DELETE https://prod.mehrgo.uz/api/paylov/user-card/delete?cardId=c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11
```

**Response** — `BaseResponse<Any>`; only the HTTP status matters.

| field | type | notes |
|---|---|---|
| `data` | any/null | ignored by the app |

```json
{ "data": true }   // inferred — payload never read
```

**Errors:** generic — `message` humanized (no card-specific keys are special-cased for delete). A card that is referenced by a pending withdrawal may be refused; **that key is not known**.

**iOS notes:**
- `cardId` goes in the URL query on a `DELETE`; a body will be ignored/rejected.
- Reload both `user-card/cards` and `withdrawal/info` after a delete — the deleted card may be the one on the pending request.

---

#### `GET /api/paylov/withdrawal/info` — withdrawable balance, all limits, and the pending request

**When it fires:** `MyPaymentFragment.loadPaylov()`, in parallel with the cards call — screen entry, refresh button, and after every mutation (add/delete card, create/cancel withdrawal). Errors are swallowed entirely (`is Resource.Error -> {}`): the withdrawable line and limit notes simply never appear.

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| — | — | — | — | no parameters |

**Response** — `BaseResponse<WithdrawalInfo>`. **All snake_case, all optional, all Int64.**

| field | type | notes |
|---|---|---|
| `data.balance` | Int64? | full account balance, **so'm**. Not used by the withdraw sheet (the hero uses the cached `user/me` balance instead). |
| `data.withdrawable_balance` | Int64? | **so'm**. The amount actually withdrawable. Server formula, documented in `WithdrawalInfo.kt`: `withdrawable = balance − bonus_left − min_balance_left`. **Display the server value; never recompute it** — if client and server disagree the server rejects the request and the driver sees an unexplained failure. |
| `data.bonus_left` | Int64? | **so'm**. Non-withdrawable bonus portion. When `> 0` the screen shows "Shundan %s so'm — bonus, kartaga chiqarilmaydi". |
| `data.min_balance_left` | Int64? | **so'm**. Admin-set minimum that must REMAIN on the balance. When `> 0` shows "Balansda kamida %s so'm qolishi kerak". |
| `data.min_amount` | Int64? | **so'm**. Minimum per request. `0` (or null) = **no minimum**. |
| `data.max_amount` | Int64? | **so'm**. Maximum per single request. `0` (or null) = **no per-request cap**. |
| `data.daily_limit` | Int64? | **so'm**. Cap on the sum withdrawn per calendar day. `0` (or null) = **no daily cap**. |
| `data.today_withdrawn` | Int64? | **so'm**. Already withdrawn today; the remainder is `daily_limit − today_withdrawn`, clamped at 0. |
| `data.pending_request` | `WithdrawalRequest`? | the driver's single active request, or null. Full field list under `withdrawal/index` below. Non-null ⇒ the withdraw CTA dims to 0.6 alpha and opens an info popup instead of the form (the backend allows **one active request per driver**), and a pending card with a Cancel pill is shown. |

```json
{
  "data": {
    "balance": 350000,
    "withdrawable_balance": 280000,
    "bonus_left": 20000,
    "min_balance_left": 50000,
    "min_amount": 10000,
    "max_amount": 1000000,
    "daily_limit": 2000000,
    "today_withdrawn": 0,
    "pending_request": null
  }
}
```

**Where every UI limit comes from** (asked explicitly):

| UI limit | source | value |
|---|---|---|
| minimum amount | **server field** `min_amount` | `0`/null = no minimum. There is **no** client-side minimum constant. |
| withdrawable balance | **server field** `withdrawable_balance` | hard ceiling; never recomputed client-side |
| per-request maximum | **server field** `max_amount` | `0`/null = uncapped |
| daily cap | **server fields** `daily_limit` and `today_withdrawn` | remaining = `max(0, daily_limit − today_withdrawn)`; `daily_limit == 0`/null = uncapped |
| min balance that must remain | **server field** `min_balance_left` | informational only — already subtracted inside `withdrawable_balance` |
| "one active request at a time" | **server field** `pending_request` != null | enforced client-side by hiding the form; assumed enforced server-side too |
| page size for both histories | **client constant** `PaylovHistoryVM.PER_PAGE` | **20** |
| OTP resend cooldown (card add) | **client constant** `AddCardConfirmFragment.RESEND_WAIT_SECONDS` | **60 seconds** |
| top-up quick-pick chips | **client constants** in `MyPaymentFragment.showTopUpSheet()` | 50 000 / 100 000 / 200 000 / 500 000 so'm |

There are **no other client-side amount constants** — every withdrawal bound is server-driven.

The exact submit gate, verbatim from `MyPaymentFragment.showWithdrawSheet()`:

```
dailyRemainder = daily_limit > 0 ? max(0, daily_limit - today_withdrawn) : Int64.max
effectiveMax   = min(withdrawable_balance,
                     max_amount > 0 ? max_amount : Int64.max,
                     dailyRemainder)
enabled = amount > 0
          && (min_amount <= 0 || amount >= min_amount)
          && amount <= withdrawable_balance
          && (max_amount <= 0 || amount <= max_amount)
          && amount <= dailyRemainder
          && selectedCardId != nil
```

The inline error line (added in this range) reports the **first** bound broken, in that order: below minimum → over withdrawable → over per-request max → over daily remainder. An empty field shows no error (neutral).

**Errors:** none surfaced — any failure leaves the section blank.

**iOS notes:**
- `0 = no limit` is the highest-risk detail: treating `max_amount == 0` or `daily_limit == 0` as a real ceiling permanently disables the button on any backend that doesn't set limits. Use `Int64.max` as the neutral element.
- Every field must be `Int64?` with `?? 0` at the use site; older backends omit several of them and `Decodable` throws on a missing non-optional.
- Amounts here are **so'm**, not tiyin — unlike `PaylovCard.balance`. Do not multiply by 100.

---

#### `POST /api/paylov/withdrawal/create` — submit a withdrawal request  *(existing endpoint, new usage)*

**When it fires:** the "Pul chiqarish" CTA inside the withdraw bottom sheet, once the gate above passes. In-button spinner while in flight; on success the sheet dismisses, a toast shows "So'rov qabul qilindi. Administratsiya tasdiqlagach pul kartangizga o'tadi", and `loadPaylov()` re-runs so the new request appears as the pending card. *New usage in this range:* the sheet now surfaces `max_amount` in the limit lines and shows an inline reason instead of silently greying the button.

**Request** (`application/x-www-form-urlencoded`)

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `card_id` | body | String | yes | **snake_case here**, unlike `user-card/*` which uses `cardId`. Value is `PaylovCard.cardId`; the sheet preselects `cards.first`. |
| `amount` | body | Int64 | yes | **so'm** (not tiyin). Parsed from the space-grouped field by stripping non-digits. |
| `note` | body | String? | no | declared in `ApiService` and threaded through the repository/use case, but the ViewModel signature is `createWithdrawal(cardId, amount)` — **the app never sends a note today**. |

```
card_id=c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11&amount=150000
```

**Response** — `BaseResponse<WithdrawalRequest>` (same DTO as a history row; full field table below). The app **ignores the payload** and just refetches `withdrawal/info`.

```json
{
  "data": {
    "id": 4821,                                  // inferred
    "card_id": "c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11",
    "card_number": "860013******4751",
    "amount": 150000,
    "approved_amount": null,
    "status": 0,
    "status_name": "Kutilmoqda",                 // inferred wording
    "note": null,
    "admin_note": null,
    "paylov_transaction_id": null,
    "created_at": 1753689600,                    // epoch SECONDS (inferred unit — see Unresolved)
    "processed_at": null
  }
}
```

**Errors:** **not humanized** — `MyPaymentFragment` toasts `it.message!!` raw for both create and cancel. Any snake_case key the backend returns here (limit breach, stale balance, an already-pending request, a blocked card) is shown to the driver verbatim. Known keys for this endpoint were not determinable from the client code.

**iOS notes:**
- `card_id` is snake_case **String**, while `withdrawal/cancel` takes `id` as an **Int** — do not unify the types.
- Amount is so'm; a `× 100` here creates a 100× withdrawal request.
- Treat any error `message` defensively: run it through the same humanizer as the card flow rather than showing the raw key (Android's gap, worth fixing on iOS rather than porting).

---

#### `POST /api/paylov/withdrawal/cancel` — driver cancels their own pending request

**When it fires:** the "Bekor qilish" pill on the pending-request card on the Hisob screen (`MyPaymentFragment.cancelPendingWithdrawal()`), using `withdrawInfo.pending_request.id`. **No confirmation dialog** — cancelling is reversible. On success: toast "So'rov bekor qilindi", then `loadPaylov()`. Only one active request exists, so cancelling frees the slot.

**Request** (`application/x-www-form-urlencoded`)

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `id` | body | Int | yes | `WithdrawalRequest.id` — an **integer**, not the card token |

```
id=4821
```

**Response** — `BaseResponse<Any>`; only the status matters.

```json
{ "data": true }   // inferred — payload never read
```

**Errors:** raw `message` toasted (not humanized). A request already approved/processed presumably rejects; **the key is unknown**.

**iOS notes:** the cancelled request afterwards appears in `withdrawal/index` with `status = 3`, not deleted — expect it in the history list.

---

#### `GET /api/paylov/withdrawal/index` — paged withdrawal-request history

**When it fires:** `PaylovHistoryFragment`, "So'rovlar tarixi" tab (reached from the Hisob row, or opened directly with `tab=requests`). Page 1 on entry; the next page loads when the last visible row is within 4 of the end. Stops when accumulated `items.size >= total`, or when a page returns empty. On error the list stops silently and shows the quiet empty state (an older backend without the endpoint must not show an error).

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `page` | query | Int | yes | 1-based |
| `per-page` | query | Int | yes | **hyphenated key** (`per-page`, Yii2 convention) — not `per_page`, not `perPage`. Always **20** (client constant). |

```http
GET https://prod.mehrgo.uz/api/paylov/withdrawal/index?page=1&per-page=20
```

**Response** — `BaseResponse<WithdrawalList>`

| field | type | notes |
|---|---|---|
| `data.items[]` | array of `WithdrawalRequest`, optional | |
| `data.total` | Int? | **total row count** (not page count); drives the stop condition. Missing → treat as 0 and fall back to stopping on an empty page. |
| `data.page` | Int? | echoed page, unused by the app |
| `data.per_page` | Int? | echoed page size — note the **response** key is `per_page` (underscore) while the **request** param is `per-page` (hyphen) |

`WithdrawalRequest` — every field of a request row:

| field | type | notes |
|---|---|---|
| `id` | Int? | primary key; the value passed to `withdrawal/cancel` |
| `card_id` | String? | the Paylov card token the payout targets |
| `card_number` | String? | **masked PAN, digits only, unspaced** — e.g. `"860013******4751"`. The UI formats it as `chunked(4).joinToString(" ")` → `"8600 13** **** 4751"`. Row hides the card line when blank. |
| `amount` | Int64? | **so'm**. The amount the driver requested. |
| `approved_amount` | Int64? | **so'm**. What the admin actually approved; may differ from `amount`. When `status == 1` and `approved_amount != amount`, the row renders `amount` struck-through grey → `approved_amount` in green. Null for non-approved rows. |
| `status` | Int? | **`0` = pending, `1` = approved, `2` = rejected, `3` = driver-cancelled** (documented in `WithdrawalRequest.kt`). Row styling: 0 amber + clock glyph, 1 green + check, 2 red + x, 3 grey + minus (and the amount text greys out). Unknown/null falls into the pending branch. |
| `status_name` | String? | server-rendered label shown in the status pill; falls back to `status.toString()`. Assumed localized via `Accept-Language` — **unconfirmed**. |
| `note` | String? | the **driver's** own note (the app can send it via `withdrawal/create` but currently never does, so this is populated only from dispatcher/admin panel flows). Rendered in a grey box with a chat glyph. |
| `admin_note` | String? | the **admin's** note, typically the rejection reason. **Takes precedence over `note`.** When `status == 2` and `admin_note != null` the box renders red-soft with a warning glyph. |
| `paylov_transaction_id` | String? | Paylov-side payout reference. Parsed but **not displayed** anywhere in the Android UI. |
| `created_at` | Int64? | **epoch seconds** — the UI does `Date(ts * 1000)` and `Instant.ofEpochSecond(ts)`. Drives both the "HH:mm" row time and the day-section header ("Bugun" for today, else localized `d MMMM`). Unit is inferred from the client code, not confirmed by the backend. |
| `processed_at` | Int64? | epoch seconds, when the admin acted. Parsed but **not displayed**. |

```json
{
  "data": {
    "items": [
      {
        "id": 4821,
        "card_id": "c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11",
        "card_number": "860013******4751",
        "amount": 150000,
        "approved_amount": 140000,
        "status": 1,
        "status_name": "Tasdiqlandi",            // inferred wording
        "note": null,
        "admin_note": "Komissiya ushlab qolindi", // inferred wording
        "paylov_transaction_id": "PLV-99213847",  // inferred format
        "created_at": 1753689600,
        "processed_at": 1753693200
      },
      {
        "id": 4790,
        "card_id": "c1f8a0b2-4e35-4a1b-9a77-1f0b2d9c6e11",
        "card_number": "860013******4751",
        "amount": 50000,
        "approved_amount": null,
        "status": 2,
        "status_name": "Rad etildi",              // inferred wording
        "note": null,
        "admin_note": "Karta egasi mos emas",     // inferred wording
        "paylov_transaction_id": null,
        "created_at": 1753603200,
        "processed_at": 1753606800
      }
    ],
    "total": 2,
    "page": 1,
    "per_page": 20
  }
}
```

**Errors:** none surfaced. Any failure freezes pagination and renders the empty state.

**iOS notes:**
- All keys snake_case → explicit `CodingKeys` required (the iOS repo sets no `keyDecodingStrategy`).
- `created_at`/`processed_at` decode as `Int64`, **not** `Date` (no `dateDecodingStrategy` is configured); multiply by 1000 or use `Date(timeIntervalSince1970:)` directly on the seconds value.
- `total` drives the stop condition — if it is omitted you will paginate forever unless you also stop on an empty page.

---

#### `GET /api/v1/balance/index` — paged balance ledger (income / expense)

**This one is NOT on the Paylov base.** It stays under `/api/v1/` — the Kotlin comment calls this out explicitly ("the balance-history endpoint is the exception"). Putting it on `/api/paylov/` yields a 404 that looks like "the driver has no history".

**When it fires:** `PaylovHistoryFragment`, "Balans tarixi" tab (Hisob row, or `tab=balance`). Same pagination rules as the requests tab (page 1 on entry, next page within 4 rows of the end, stop on `>= total` or an empty page). Tapping the green "income" or red "expense" totals pill re-runs from page 1 with the `type` filter; tapping again clears it. **Unlike the requests tab, an error here is toasted** (`it.message`).

**Request**

| field | in | type | required | unit/notes |
|---|---|---|---|---|
| `page` | query | Int | yes | 1-based |
| `per-page` | query | Int | yes | hyphenated key; always **20** |
| `type` | query | Int? | no | ledger filter: omitted = all, `1` = income only, `2` = expense only. `BalanceHistoryItem.type == 1` is what the row renderer treats as income (green `+`), anything else as expense (red `−`). |
| `reason` | query | Int? | no | declared in `ApiService`/UC but **never sent** by the app; reason-code vocabulary unknown |
| `from` | query | String? | no | declared, **never sent**. Format assumed `YYYY-MM-DD` by analogy with `driver-earnings/summary` — unconfirmed. |
| `to` | query | String? | no | declared, **never sent**. Same. |

```http
GET https://prod.mehrgo.uz/api/v1/balance/index?page=1&per-page=20&type=1
```

**Response** — `BaseResponse<BalanceHistory>`

| field | type | notes |
|---|---|---|
| `data.items[]` | array of `BalanceHistoryItem`, optional | |
| `data.total` | Int? | total row count; stop condition |
| `data.total_income` | Int64? | **so'm**. All-time (or filter-scoped) income sum, shown in the green pill. |
| `data.total_expense` | Int64? | **so'm**. Shown in the red pill. |
| `data.page` | Int? | echoed |
| `data.per_page` | Int? | echoed (underscore in the response, hyphen in the request) |

`BalanceHistoryItem`:

| field | type | notes |
|---|---|---|
| `id` | Int? | ledger row id |
| `type` | Int? | `1` = income (rendered `+`, green); anything else = expense (rendered `−`, red). Matches the `type` query filter (1 income / 2 expense). |
| `type_name` | String? | server label; used as the row title only when `reason_name` is null |
| `value` | Int64? | **so'm**, always a positive magnitude — the sign is derived from `type`, not from the value |
| `total_after` | Int64? | **so'm**. Balance remaining after this entry; rendered as "Qoldiq: %s so'm". |
| `reason` | Int? | reason code; **vocabulary unknown**, not rendered |
| `reason_name` | String? | primary row title (falls back to `type_name`) |
| `info` | String? | free-text detail; **parsed but never rendered** by Android |
| `created_at` | Int64? | **epoch seconds** (`Date(ts * 1000)`); drives the "HH:mm" row time and the day-section header |
| `datetime` | String? | preformatted date-time string; used as the row time **only** when `created_at` is null. Format unknown. |

```json
{
  "data": {
    "items": [
      {
        "id": 99213,
        "type": 1,
        "type_name": "Kirim",                    // inferred wording
        "value": 24000,
        "total_after": 304000,
        "reason": 3,                             // inferred code
        "reason_name": "Buyurtma to'lovi",       // inferred wording
        "info": "Order #78447",                  // inferred
        "created_at": 1753689600,
        "datetime": "2026-07-28 12:00:00"        // inferred format
      },
      {
        "id": 99188,
        "type": 2,
        "type_name": "Chiqim",                   // inferred wording
        "value": 150000,
        "total_after": 280000,
        "reason": 7,                             // inferred code
        "reason_name": "Kartaga chiqarildi",     // inferred wording
        "info": null,
        "created_at": 1753603200,
        "datetime": "2026-07-27 12:00:00"        // inferred format
      }
    ],
    "total": 2,
    "total_income": 1840000,
    "total_expense": 620000,
    "page": 1,
    "per_page": 20
  }
}
```

**Errors:** `message` toasted raw (not humanized), pagination stops, empty state shown.

**iOS notes:**
- Different base from every other endpoint in this group (`/api/v1/`, not `/api/paylov/`).
- Filtered responses return **filtered totals** (the opposite side comes back 0) — Android only refreshes the two total pills from **unfiltered** loads so both always show real all-time sums. Port that guard or the pills flip to 0 as soon as a filter is applied.
- `created_at` (Int64 epoch seconds) and `datetime` (String) are two representations of the same instant; prefer `created_at` and use `datetime` only as a fallback.

---

#### Declared but not wired — do not implement for parity

These three exist in `ApiService.kt` on the Paylov base but no UI calls them; the driver tops up through external Click / PayMe web links instead (`https://my.click.uz/services/pay/?service_id=…&merchant_id=…&amount=<so'm>&transaction_param=<userId>` and `https://payme.uz/fallback/merchant/?id=…&userid=<userId>&amount=<so'm × 100 tiyin>`).

| endpoint | body | response |
|---|---|---|
| `POST /api/paylov/payment/fill-balance` | `cardId` (String), `amount` (Int64) | `BaseResponse<Any>` |
| `POST /api/paylov/payment/create` | `cardId` (String), `amount` (Int64) | `BaseResponse<PaymentCreateResult { transactionId: String? }>` |
| `POST /api/paylov/payment/confirm` | `transactionId` (String), `otp` (String) | `BaseResponse<Any>` |

#### Related push

An admin approving or rejecting a withdrawal triggers an FCM **data** message whose discriminator is `type`, **not** `key`: `{ "type": "paylov_withdrawal", "request_id": <id>, "status": "1" | "2" }` (`Constants.FCM_TYPE_PAYLOV_WITHDRAWAL`). Title and body arrive already localized — do not format client-side. Tap should open the withdrawal-requests history tab.

---

## Appendix B — open questions for the backend team

These were found while writing this brief by reading the Android client end-to-end. **None of them
is answerable from client code** — someone on the backend has to state the answer. They are listed
worst-first.

> **Do not "solve" these client-side.** Every one of them has already been guessed at least once by
> one of the two apps, and the guesses are why several of the bugs in this document existed.

### The five that block correctness

| # | Question | Why it matters | Who is affected |
|---|---|---|---|
| B1 | ~~Does `order-gps/batch` really bucket pre-boarding points into `to_client_km` by the order state at upload time?~~ **ANSWERED 2026-07-30 — NO, and it over-billed in production.** Order 79557: the buffered approach leg (captured at state 2) flushed after the state-8 change and the server billed the whole 1.38 km as `in_city_km` at the trip rate (`to_client_km` stayed 0). Riders were overcharged cumulatively by millions of so'm. The contract is now **per-point**: every point carries `state` (see the batch request table) and the server must bucket by the point's own state, not the order's state at upload time. Both apps must send it; backend must also **accept batches in every state 2–9** (during 79557's approach no batch was accepted at all until the state changed). | both apps |
| B2 | **`POST order/complete` returns the driver `User` object with no `final`/receipt block. Does the backend even read the `total_price` / `distance` the app posts?** | The apps cannot draw a server-authoritative receipt. Both currently render the receipt from what they posted plus the last `live` block, which can disagree with the driver's real settlement. | both apps |
| B3 | **`order/history`: `waiting_time` switched from milliseconds to SECONDS around 2026-07-18 with no announcement and no field rename, while `execution_time` stayed in milliseconds. Which unit is authoritative, for both fields, and will old rows be migrated?** | The client currently *infers* the unit per row. A wrong inference shows a 3-second wait as 50 minutes (or the reverse) on the driver's own earnings history. | both apps |
| B4 | **Withdrawal limits: does `0` (and `null`) in `min_amount` / `max_amount` / `daily_limit` mean "no limit"?** This is a client-side interpretation, not a documented contract. | If `0` actually means "zero allowed", the iOS withdraw button will be **permanently disabled** for every driver. | §2 |
| B5 | **Money units, field by field.** The withdrawal group is treated as so'm; `PaylovCard.balance` carries a `// TIYIN` comment but is never rendered, so nothing has ever verified it. Some fields are integers (`price`), others fractional (`waiting_cost` observed as `2613.33`). | A tiyin/so'm mix-up is a 100× error on a screen that moves real money. | §2, fare |

### iOS-specific traps (Swift is stricter than Kotlin here)

| # | Question | Why iOS cares more than Android |
|---|---|---|
| B6 | **What value should `X-Platform` carry from iOS?** Android hard-codes `android`; `ios` vs `iOS` is not defined anywhere and the panel may match on an enum. | Wrong value = the backend cannot tell the fleets apart in diagnostics. |
| B7 | **Does `user-card/cards` return `data.cards[]` or a bare `data[]`?** The Kotlin model says "either, depending on the backend build". | **Gson tolerates the mismatch; `Decodable` throws.** Pick one, or iOS must decode both shapes explicitly. |
| B8 | **What does `user-card/confirm` actually return?** The Kotlin payload type is annotated as an assumption and the caller never reads it. | Same reason as B7 — an assumed shape that is never read on Android will hard-fail on iOS the moment someone decodes it. |
| B9 | **Are `created_at` / `processed_at` epoch SECONDS?** Inferred from the client doing `× 1000`. Also: `Order.created_at.int` is assumed to be seconds by analogy. | If any is milliseconds, every date header lands in 1970 or the far future — the exact bug already hit `order/history` (B3). |
| B10 | **The full vocabulary of machine-key `message` values.** Only `pinfl_not_match` and `card_is_blocked` are mapped; every other snake_case key is swallowed into a generic error. | Drivers get "server error" instead of an actionable sentence — see §6. Unknown keys for: wrong/expired card OTP, withdrawal limit breach, cancelling an already-processed request, deleting a card tied to a pending request. |

### Product gaps (need a backend feature, not an answer)

| # | Gap |
|---|---|
| B11 | **No push/socket signal when the rider changes services before `state=9`.** Verified on live orders 2026-07-24 at both ACCEPTED and STARTED: no socket frame, no FCM. Android works around it with a **10-second poll**. A push would remove the poll from both apps. |
| B12 | **`order_gps_batch` socket acks carry no correlation id** — they are matched FIFO with a 5 s timeout, which is only safe because the uploader serialises sends (queue depth ≤ 1). Adding a message id would make this robust. |
| B13 | **`socket/order-cancel` and `socket/order-cancel-and-renew` reject the driver bearer token with 401** ("register to use the app"). Confirm whether these are internal-only routes. |
| B14 | **Is "one active withdrawal request per driver" enforced server-side**, or only by the client hiding the form when `pending_request != null`? If client-only, two devices (or a killed process) can create duplicates. |
| B15 | **Paylov OTP length is not pinned** — Android gates submit at `>= 4` chars because 4/5/6 are all possible. A fixed length lets iOS use a proper OTP field. |

The per-endpoint sections in Appendix A carry the remaining, narrower unknowns inline.

---

## Deliverable

For each of §1–§9: a short note of **what the iOS app did before, what you changed, and how you
verified it** — ideally against a real order or a real withdrawal capture, not a mock.

Call out explicitly:

- anything you could not verify on the wire,
- anything blocked by Appendix B (do not work around a B-item silently),
- any place where the iOS architecture made the Android approach a bad fit, and what you did
  instead.

**Two hard rules:**

1. **Ask before making a settlement-affecting change you cannot verify against a real payload.**
   Getting the fare wrong under-pays drivers.
2. **§2 (withdrawal) moves real money and does not exist on iOS yet.** Build it against the
   contracts in Appendix A §3, confirm B4 and B5 with the backend *before* shipping, and have the
   limit validation reviewed by someone else on the team.

