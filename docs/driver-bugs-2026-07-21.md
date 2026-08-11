# Driver app — bug handoff, 2026-07-21

**For:** the agent/dev working on the **MehrGo DRIVER** app (branch `mehrgo_driver_app`, package `uz.teamwork.mehrgodriver`).
**From:** the client-app session. These findings came out of a full CLIENT-app review plus a real end-to-end order run through both apps; this document is the driver-side half of it.

All paths are relative to the driver repo root (`app/src/main/java/uz/teamwork/mehrgodriver/…`).
Line numbers are from commit `7b956a3a` — re-grep before editing, they drift.

## How to read the confidence tags

| Tag | Meaning |
|---|---|
| ✅ **VERIFIED** | The reporting session opened the file and confirmed the code says this. Treat as fact. |
| 🟡 **REPORTED** | Found by an audit agent and survived adversarial refutation, but was not re-opened by hand. Confirm the cited lines before fixing. |
| ⚠️ **DECISION** | The code is confirmed, but the *right behaviour* is a product/business call, not a technical one. Do not "fix" unilaterally. |

Produced by a 180-agent audit: 56 candidates → 29 survived 2–3 independent refutation passes → **27 rejected** (listed at the bottom, do not re-chase them). After removing duplicate reports of the same defect, this document contains **20 distinct issues**.

---

## 0. The structural problem behind a third of this list

**`MyDirectionFragment` (list-mode UI) is a hand-copied twin of `MapFragment` that never received `MapFragment`'s fixes.**

`AppTypeManager` picks list mode vs navigator mode, so roughly half the fleet runs the un-patched copy. Seven issues below are literally "this guard exists in `MapFragment` and is missing in `MyDirectionFragment`". `docs/open-issues.md:255` already predicted this ("MyDirectionFragment carries its own copy of the receipt arithmetic that can diverge silently") — it has now diverged in at least five places.

**Recommendation:** fix the individual items first (they are shipping bugs), then extract `MapFragment.kt:2836-3042` and `MyDirectionFragment.kt:1065-1254` into **one shared finish/receipt function**. Otherwise the next fix drifts again.

---

## P0 — Security. These affect release builds. Fix first.

### S1. Release APK logs passwords, OTP codes and bearer tokens to logcat ✅ VERIFIED

**`data/di/NetworkModule.kt:41`**

```kotlin
fun getHttpLoggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
```

No `BuildConfig.DEBUG` gate, and it is added to the shared client at `:53`. `POST user/login` is `@FormUrlEncoded` with a `password` field, so the release build prints `phone=…&password=<plaintext>` under tag `OkHttp`. `POST user/confirm` prints the OTP. Every later request prints `Authorization: Bearer <token>` and every response body in full.

Anything with logcat access harvests live driver credentials: OEM log collectors, bug-report/dumpstate captures, adb during support sessions.

**Fix**

```kotlin
level = if (BuildConfig.DEBUG) Level.BODY else Level.NONE
```

plus `redactHeader("Authorization")`. Also check whether `ChuckerInterceptor` (`:28`, added at `:51`) uses the `library-no-op` artifact in release — if not, it is a second in-app copy of the same data.

**Must be fixed in the same commit as S2 below**, or the fix is incomplete.

### S2. Second ungated BODY logger in the order adapter ✅ VERIFIED (same class as S1)

**`presentation/main/adapter/OrderAdapter.kt:280`**

The order-pool adapter builds its **own private** `Retrofit`/`OkHttpClient` with `Level.BODY` and no debug gate, and creates a fresh never-shut-down client per adapter instance. The `NetworkModule` fix does not touch this file.

**Fix:** inject the shared Hilt-provided client (add a `@Named` Retrofit if the route base URL really differs). Gate the level regardless.

### S3. The driver's bearer token is written to disk and uploaded to the server ✅ VERIFIED

**`common/HeaderInterceptor.kt:65-89`**

```kotlin
val errorRequest = ErrorRequest(
    request.url.toString(),
    request.headers.value(0),   // <-- this IS "Bearer <authKey>"
    …
)
ErrorRequestManager.addItemToList(errorRequest, typeToken)
```

`request` here is the rebuilt request whose **first** `addHeader` was `Authorization` (`:54`), and `grep -rn "@Headers" app/src/main/java` returns **zero hits**, so index 0 is unambiguously the Authorization value. On every non-2xx response the literal token string is appended to plaintext SharedPreferences `myErrorPref` and later POSTed to `error/send`.

`UserManager.deleteUser()` clears only `myUserPref`, so **after logout the previous driver's token stays on the device** and is uploaded later — possibly while a different driver is signed in on the same phone.

**Fix:** drop the `token` field from `ErrorRequest` (send `UserManager.getUserId()` instead — that is what support actually needs to correlate), or mask it if the wire shape must stay. Separately call `ErrorRequestManager.clearAll()` everywhere `deleteUser()` is called.

### S4. Logout from the "not active" screen leaves the tracking service running under the old token ✅ VERIFIED

**`presentation/main/ui/not_active_user/NotActiveUserFragment.kt:169-173`**

```kotlin
dialogBinding.cvExit.setDebouncedClickListener {
    UserManager.deleteUser()                                   // no ACTION_STOP_SERVICE
    findNavController().navigate(R.id.action_notActiveUserFragment_to_loginFragment)
    exitDialog?.dismiss()
}
```

The other three logout sites do stop it (`MapSettingsFragment.kt:219`, and both 401 paths at `MapFragment.kt:1745` / `HomeFragment.kt:316`). `MyTrackingService` therefore keeps running with an authenticated socket and keeps uploading GPS as the logged-out driver, notification still showing.

It compounds: the socket `Request` is built **once** in `MyTrackingService.onCreate` (`:344-345`) as `"$BASE_URL_FOR_SOCKET?token=${UserManager.getToken()}"` and both reconnect paths reuse that same object (`:690`, `:706`) — so the stale token survives every reconnect, and a service that starts with no user stored connects permanently as `?token=null`.

**Fix:** add `sendCommandToService(Constants.ACTION_STOP_SERVICE)` before `deleteUser()`. Better: one `performLogout()` helper (stop service → clear UserManager → clear ErrorRequestManager → navigate) used by all four sites. Separately, rebuild the socket `Request` inside `reconnectSocketNow()` from the *current* token.

**Also check while here:** logout `navigate` actions need `popUpTo="@id/nav_graph"` inclusive, or the old screen flashes under the login screen on back-press.

---

## P1 — Money. These change what the driver charges.

### M1. List-mode posts an **uncapped bonus** — the final total renders negative ✅ VERIFIED

**`presentation/main/ui/my_direction/MyDirectionFragment.kt:1202-1220`** vs **`presentation/maps/yandex_map/MapFragment.kt:2989-3005`**

`MapFragment` has both guards, with comments explaining exactly this bug:

```kotlin
calculatedBonusFinal = calculatedBonusFinal
    .coerceIn(0L, (totalPrice - calculatedPromo).coerceAtLeast(0L))
…
val finalTotalPrice = (totalPrice - calculatedBonusFinal - calculatedPromo)
    .coerceAtLeast(0L)
```

`MyDirectionFragment` has **neither**:

```kotlin
calculatedBonusFinal = if (calculatedBonus < clientTotalBonus!!) calculatedBonus else clientTotalBonus!!
if (clientTotalBonus!! < minAmount!!) calculatedBonusFinal = 0
…
val finalTotalPrice = totalPrice - calculatedBonusFinal - calculatedPromo   // can go negative
```

**Repro:** `totalPrice = 12 000`, `useBonus = true`, flat `maxAmount = "20000"`, client bonus `20 000`.
→ `tvFinalTotalPrice` renders **`-8 000 so'm`** as the amount to collect, and the POST carries `total_price=12000` with `bonus_payment=20000`. The identical order finished in map mode posts `bonus_payment=12000`. Either the server credits 8 000 of phantom bonus against the driver's fare, or it rejects the finish and the list-mode driver **cannot close the order at all**.

**Fix:** port `MapFragment.kt:2995-2996` and `:3004-3005` verbatim.

### M2. List-mode bills a **stale fare** at finish — the `join()` fix was never twinned 🟡 REPORTED

**`MyDirectionFragment.kt:948-953` and `:1003`**

`getActiveMyOrders()` launches the finish-time `GET order-gps/fare` fire-and-forget, then shows the finish dialog immediately. The total is captured **by value** into the Submit lambda (`:1218`, `:1242`); a late `applyServerFare()` updates the fields but never re-renders the dialog.

`MapFragment` fixed exactly this with a bounded `withTimeoutOrNull(FINISH_FARE_WAIT_MS) { fareJob.join() }` (see `MapFragment.kt:2755`). `MyDirectionFragment` does not even keep a `Job` handle — and still carries the pre-fix comment at `:945-947`.

**Repro:** driver parked at the drop-off (stationary ⇒ `GpsBatchUploader.drainOnce()` posts nothing ⇒ `serverPrice` frozen for minutes). Rider removes a 2 000 so'm service. Driver taps Yakunlash 3 s later → the pre-removal total is what gets POSTed.

**Fix:** hold the Job from `:949`, `withTimeoutOrNull(2_500L) { it.join() }` immediately before `showDialogTrackingFinish()` at `:1003`. Add `FINISH_FARE_WAIT_MS` to the companion. Fix the stale comment.

### M3. List-mode never refreshes the order mid-trip — the rider's service toggle is invisible for the whole trip 🟡 REPORTED

**`MyDirectionFragment.kt:438`, `:834`, `:860-865`, `:944`**

There is no counterpart to `MapFragment.refreshServerFareOnOrderChange()` / `orderRepricedReceiver`. The only order refetch is wired to the **Finish button**, and there is no `onResume` refresh.

Two concrete cases:
- **Taximeter order:** the only fare input is the batch-ack-driven `GpsBatchUploader.latestFare`. Stationary driver ⇒ no acks ⇒ the on-screen total is frozen indefinitely.
- **Fixed-route order** (`locations.size >= 2`): `calculateTotalPrice()` routes to `calculateTotalPrice2()` = `totalWaitingPrice + order.price`, where `order` is the **navArgs snapshot captured at `onCreate`** (`:192`). `serverPrice` is not even consulted on this branch. The rider toggles AC, the server moves `order.price`, and the driver's screen *literally cannot change*.

The rider's app shows the new number immediately. The two people in the same car see different totals until the driver taps Finish.

**Fix:** port `refreshServerFareOnOrderChange()` (with its `lastSeenServicesTotal` optimistic repaint and the services-change throttle carve-out) and register the same id-guarded reprice receiver. Add an `onResume` refresh. For the fixed-route branch, stop reading the frozen navArgs `order.price`. Keep any optimistic value **display-only** — it must never be written into the field the receipt reads. See `docs/client-service-price-sync.md` §4.2 / §4.4 for the client-side shape of this.

### M4. List-mode finish dialog is cancelable and has no in-flight latch — double `order/complete` 🟡 REPORTED

**`MyDirectionFragment.kt:1019-1026`**

`MapFragment` blocks re-entry with `setCancelable(false)` plus a `finishPrepInFlight` latch (`MapFragment.kt:2677-2684`, released in a `finally` at `:2765-2771`). The list-mode dialog is a default cancelable `Dialog` whose only guard is `dialogTrackingFinish?.isShowing != true` — dismissing the receipt while the POST is in flight re-opens the flow and allows a second concurrent POST of the same order.

**Fix:** `setCancelable(false)` + `setCanceledOnTouchOutside(false)` + port the latch. **Also ask backend whether `POST order/complete` is idempotent per `order_id`** — neither app has client-side dedup.

### M5. The receipt screen shows a different breakdown from the dialog the driver just approved 🟡 REPORTED

**`MapFragment.kt:3075-3081`** (and the same shape in list mode)

The finish **dialog** derives its Kutish / Xizmatlar rows from the **server** fare snapshot; the `TripReceipt` handed to `TripFinishFragment` a second later re-derives the same three rows from the **local** meters (`totalWaitingPrice`, `totalServicePrice`) against the server-derived gross. Same total, two different breakdowns one screen apart, and the ride line is unclamped (can render negative).

**Fix:** hoist `waitLine` / `servicesRow` / `(totalPrice - servicesRow - waitLine)` into the scope that builds `RequestOrderFinish` and pass those exact values into `TripReceipt` instead of re-deriving. Add `.coerceAtLeast(0L)` on the ride line regardless.

### M6. `serverPriceIncludesExtras` is never reset on a null fare 🟡 REPORTED

**`MapFragment.kt:3309`** (list mode already does this correctly at `MyDirectionFragment.kt:443`)

The `latestFare == null` branch clears `serverPrice`, `serverDistanceKm`, `serverWaitCost` and `latestLive` but leaves `serverPriceIncludesExtras` **latched at `true`** from the previous order. The receipt then takes the server branch for the services row against a null `latestLive` and prints **Xizmatlar 0**, while the total falls back to the local meter which *does* include services — the rows stop footing and the derived ride line is inflated by exactly the services amount.

**Fix:** one line — add `serverPriceIncludesExtras = false` to that null branch.

### M7. Service totals are summed from the catalogue price, not the billed amount 🟡 REPORTED

**`MyDirectionFragment.kt:776`** (two sites)

These rebuild `totalServicePrice` by summing `OrderService.value` (the catalogue **list price**) while every other site in both fragments sums `Order.Service.total` (the **billed** amount). For any service whose total ≠ unit value (multi-count, percent-priced) the on-screen total — and on the legacy path the billed total — is wrong.

**Fix:** re-derive from the order payload after a successful toggle (refetch the order, as the client now does). Do not leave two differently-named fields feeding the same accumulator.

### M8. ⚠️ **DECISION** — the finish judge compares two different distance metrics

**`MapFragment.kt:2914-2916`** (same rule in `MyDirectionFragment.kt:1135`)

```kotlin
val distanceTracked = distanceInCity + distanceOutCity     // app's own GPS chord sum
val distanceBetweenLocations = order.distance * 1000       // server's PLANNED ROAD route
if (abs(distanceTracked - distanceBetweenLocations) < distanceTolerance) agreedTotal else taximeterTotal
```

`distanceTracked` is the sum of straight-line chords between accuracy-filtered 5 s GPS fixes (`Helper.filterTrackLocations` decimates to ~1 point per 5 samples once accuracy ≥ 10 m). `order.distance` is the planned road route. **`serverDistanceKm` — the value this same screen PRINTS on the receipt (`:2837-2841`) and POSTs as the trip distance (`:3028`) — is not used in the comparison.** The printed distance and the distance that chose the price are different numbers.

Consequence: a fixed-price order silently falls back to the meter whenever the driver finishes >300 m from the B pin (arm 2a misses) **and** the two metrics differ by ≥1 km — degraded GPS, a detour, roadworks, or simply a long trip where an honest route difference exceeds an *absolute* 1 km band.

**Why this is a DECISION, not a fix:** the audit was split. The structure is definitely wrong (verified by hand). But one verifier argued that with good GPS the chord sum tracks road distance to ~1–3%, so the >1 km divergence is not an everyday event; and `docs/open-issues.md` item **M2** already quotes this exact expression as "the sole escape hatch", owner = user-decision.

**Options, cheapest first:**
1. Compare like with like: `serverDistanceKm?.let { it * 1000 } ?: (distanceInCity + distanceOutCity)`. Minimal, makes the judge agree with the printed/POSTed distance.
2. Make the tolerance **relative** (e.g. `max(1000, 0.2 * planned)`) instead of a flat 1 km.
3. Drop the distance test from the agreed-price decision entirely and gate on `live.agreedPrice` / `kept_projection`.

⚠️ Any of these changes what riders are charged. **Get the product owner's sign-off before shipping.**

### M9. List-mode in-trip hero can show the local meter on a fixed-route order 🟡 REPORTED

**`MyDirectionFragment.kt:830`**

`MapFragment` freezes the hero at `order.price` for any B-point order (`MapFragment.kt:3498-3503`); list mode has no such early return and runs the one-sided distance rule, painting a purely local base+distance+wait meter that neither the rider nor the final receipt agrees with.

**Fix:** mirror the `locations.size >= 2 && price > 0` early return. Note `open-issues.md` **L5** was closed on `MapFragment` evidence alone — reopen it.

---

## P2 — Crashes

### C1. Socket frames are re-parsed in six receivers with no guard 🟡 REPORTED

**`common/services/MyTrackingService.kt:299` and `:308`; `SocketOrderResponse.kt:5-9`**

`MySocketListener.onMessage` was hardened with a broad catch (open-issues **H4**), but it **re-broadcasts the raw frame**, and BroadcastReceiver delivery is asynchronous — so the six receivers that decode it run **outside** that try/catch. `SocketOrderResponse.data: Order` is declared non-null while Gson populates it reflectively, so `"data": null` (or a shape change dropping `branch`) throws NPE on the main thread and kills the process. There is no `setDefaultUncaughtExceptionHandler` anywhere.

This is the **exact twin** of the `SocketFareResponse.data` hole that was already fixed — that one got `data: Order?` + a KDoc ("Kotlin's non-null is a compile-time promise Gson does not keep") + `runCatching`. `SocketOrderResponse` was left untouched. The client app hit and fixed the same class of bug today.

**Fix:** make `SocketOrderResponse.data` nullable (and `Order.branch`), `runCatching { gson.fromJson(...) }.getOrNull() ?: return` then `val order = response.data ?: return` in each receiver — same shape as `MySocketListener.kt:98-102`. **Cheapest single-point fix:** parse once in `MySocketListener` and broadcast a *validated* payload instead of raw text.

### C2. Promo-code amount is parsed with `[length-1]` and a bare `.toLong()` — on the finish path 🟡 REPORTED

**`MapFragment.kt:2942`** (and the list-mode copy)

`PromoCode.Usage.amount` is a non-null server `String` (`Order.kt:260-263`) that the receipt indexes at `[length-1]` and converts with bare `.toLong()` / `.toInt()`. Empty string → `StringIndexOutOfBoundsException`; a decimal or non-numeric value → `NumberFormatException`. This runs **while building the finish receipt**, i.e. at the moment the trip settles and money is on the line.

The client reads the same field and never converts it — it only does `amount.contains("%")` and takes the numeric value from the server's own `used_amount`.

**Fix:** `toDoubleOrNull()` with a fallback on both branches, or better, bill the server's `used_amount` like the client does.

### C3. Two concurrent 401s crash the app 🟡 REPORTED

**`presentation/main/ui/home/HomeFragment.kt:315-318`**

`getOrderAddress()` starts a fresh coroutine per invocation and is fired from both `onViewCreated` and a broadcast receiver on every socket order event. Each 401 unconditionally calls `navigate(…toLoginFragment)` — the second one throws.

**Fix:** guard with `if (findNavController().currentDestination?.id != R.id.loginFragment)` or a process-level `loggingOut` flag, and give `getOrderAddress()` the same single-flight `Job?.cancel()` treatment `loadVerification()` already has.

---

## P3 — UI / lifecycle

### U1. Every branch-wide socket frame blanks Home and the order pool to a spinner 🟡 REPORTED

**`presentation/maps/orders/OrdersMapFragment.kt:167` and `:177`; also `HomeFragment.kt:307`, `OrdersFragment.kt:219`**

Three screens do a **full screen reload** (`content` → GONE, spinner → VISIBLE) **plus a `RecyclerView.adapter` reassignment** on every `ORDER_NEW` / `ORDER_ACCEPTED` / `ORDER_CANCELLED` frame. Those keys are **branch-wide, not per-driver** — they fire for every order any client in the branch creates and every order any other driver accepts.

At even 1–2 branch events/second the screen strobes continuously. And `setAdapter()` clears the recycled-view pool and re-anchors the layout manager at position 0, so a driver who scrolled down to read order #7 is snapped to the top before he can tap it. Each rebuild also re-runs `OrderAdapter`'s per-bind routing HTTP call for every visible row and resets each row's distance back to "Tekshirilmoqda", so the distance column never settles.

This is the driver counterpart of the client's "`notifyDataSetChanged` on every socket frame" bug, but worse.

**Fix:** (1) give the refresh a `silent: Boolean = false` parameter and skip `loadingVisible()` on the socket path (or make the `Loading` branch a no-op after the first successful load, as `MapFragment.kt:1166` does). (2) **Delete the `rvOrders.adapter = orderAdapter` lines from the `Success` blocks** and set the adapter once in `onViewCreated` — then DiffUtil dispatches granular updates and scroll survives. (3) Debounce the socket refetch ~500 ms. (4) Memoize `getDistance` per order id instead of calling it in `onBind`.

### U2. Socket-triggered pool refreshes are never cancelled — a stale response can win 🟡 REPORTED

**`OrdersMapFragment.kt:77`**

Each frame launches a brand-new independent coroutine collecting `getAllOrders()`; nothing cancels the previous one. N concurrent requests complete in arbitrary order and **whichever finishes last wins** — so the pool can settle on a stale snapshot, and it silently undoes the optimistic row removal that Skip performs (a skipped order reappears).

**Fix:** hold the Job and `cancel()` before relaunching, or route the socket path through a `MutableSharedFlow` + `collectLatest`. Independently keep a `skippedOrderIds` set and filter it out of every submitted list.

### U3. Both My-Orders tabs register their load-state listener on a **null** adapter ✅ VERIFIED

**`presentation/main/ui/my_orders/fragments/CanceledOrdersFragment.kt:60`** (and the finished tab)

```kotlin
private fun loadData() {
    viewLifecycleOwner.lifecycleScope.launch {
        …
            orderHistoryAdapter = OrderHistoryAdapter()      // assigned HERE, inside the coroutine
        …
    }

    orderHistoryAdapter?.addLoadStateListener { … }          // runs NOW, adapter is still null
}
```

The safe-call swallows the registration, so the empty state, the spinner and the error state are **all dead code**. `tvOrdersEmpty` and `progressBar` are both `gone` in the layout and nothing ever flips them → a driver with zero cancelled orders sees a **blank white tab**. Same on "Yakunlangan".

Two more problems in the same block: the adapter is **re-created and re-attached on every `collectLatest` emission** (scroll reset), and `submitData` is fed a **client-side `.filter { status == … }`** over a paged stream, which can starve Paging's append (a page of 20 with no matching rows looks like an empty page).

**Fix:** create the adapter once in `onViewCreated`, attach it, register `addLoadStateListener` there, and only call `submitData()` inside `collectLatest` — exactly as `OrdersHistoryFragment.kt:29` and `:55-66` already do. Move the status filter server-side if possible.

### U4. History Pager is rebuilt on every `onViewCreated` 🟡 REPORTED

**`presentation/main/ui/orders_history/OrdersHistoryViewModel.kt:20`**

`getOrdersHistory()` constructs a brand-new `Pager` + `cachedIn(viewModelScope)` on each call and the fragment calls it from `onViewCreated`, so history → detail → back refetches the whole paged list from the network and strands the previous `PageFetcher` alive in `viewModelScope`.

**Fix:** make it a `val` property built once (both `OrdersHistoryViewModel` and `MyOrdersViewModel`), as the client did.

### U5. `MapFragment.infoPopup` is the one dialog left out of the dismiss-on-destroy sweep 🟡 REPORTED

**`MapFragment.kt:5414`** — member `Dialog?`, shown but never dismissed in `onDestroyView`/`onDestroy`, unlike the other ten dialogs in the same file which were all explicitly fixed. Leaks its window on Activity recreate (theme/locale change).

**Fix:** add `infoPopup?.dismiss(); infoPopup = null` next to the others (~`:804-806`). Better: route it through the existing `common/InfoPopup.show(..., lifecycle = viewLifecycleOwner.lifecycle)` helper, which already auto-dismisses on `ON_DESTROY` — the hand-rolled second implementation should not exist.

### U6. A new Activity-scoped observer is registered for every private-order offer 🟡 REPORTED

**`presentation/activity/main/MainActivity.kt:814`**

`showDialogInsideApp(order)` calls `MyTrackingService.timeAcceptingIndividualOrder.observe(this@MainActivity)` from inside a per-offer function, so **each incoming private order permanently attaches another observer** capturing that offer's dialog binding. None are ever removed. `MapFragment` guards this exact anti-pattern with `trackingObserversRegistered`.

**Fix:** hoist the observation into `onCreate` (next to `listenerNewPrivateOrder` at `:262`) reading `_dialogInsideAppBinding?.…` so it always targets the current dialog; or hold the `Observer` in a field and `removeObserver` in the dialog's `setOnDismissListener`.

### U7. A restored offer sheet's Accept button is dead 🟡 REPORTED

**`presentation/main/ui/order_offer/OrderOfferBottomSheet.kt:51`**

`onAccept`/`onSkip` are plain instance lambdas assigned by the host after `newInstance()`. A `DialogFragment` restored by the FragmentManager after a configuration change (rotation, theme/locale switch, process restore) has them **null** — the sheet renders fully and Accept silently does nothing while the offer timer runs out.

**Fix:** dismiss on restore (`if (savedInstanceState != null) { dismissAllowingStateLoss(); return }`), or move the result to a shared ViewModel / FragmentResult API, or re-wire in `onViewCreated` via `findFragmentByTag`.

---

## Do NOT chase these — investigated and refuted

These were plausible hypotheses that the audit **disproved**. Re-deriving them wastes a day each.

- ❌ **"The driver ignores `agreed_price` / `kept_projection` and always bills the meter."** False — the agreed-price arm exists (`MapFragment.kt:2912`). The real issue is M8 above (which *metric* the judge uses), not a missing feature. *(This was the reporting session's own initial hypothesis. It was wrong.)*
- ❌ Socket `notification_new` frames conflated by `LiveData.postValue` (the client's bug does **not** reproduce here).
- ❌ `order_cancelled_for_nurse` / the three cancel handlers missing an order-id guard — the guards are present.
- ❌ A socket reconnect blindly dismissing the open Start/Cancel sheet.
- ❌ Simultaneous private offers being conflated.
- ❌ `order_items[].service` / `.name` non-null NPE — the driver's guards hold (this one *was* real on the client).
- ❌ `order.locations[0]` indexed without an isEmpty check at 6 sites.
- ❌ Tariff money/time fields converted with bare `toInt()` on the tracking-start path.
- ❌ `SocketNotificationResponse.data` non-null killing the tracking service.
- ❌ Paging: shared `PagingSource` instance / `getRefreshKey` returning an item index / `PagingConfig(1)` being 20× off.
- ❌ `FinishedMyOrdersFragment` / `OrdersMapFragment` dereferencing `binding!!` after `onDestroyView`.
- ❌ `AutoOfferService` overlay Accept/Skip bypassing the double-tap debounce.
- ❌ The persisted error log growing unbounded / re-serialised on the network thread.
- ❌ App version having two hand-synced sources feeding two consumers.
- ❌ `live` block money fields typed `Double` in driver vs `Int` in client.

---

## Backend asks that came out of the same review

Not driver-app work, but the driver agent is likely to hit these:

1. `POST order/complete` — **is it idempotent per `order_id`?** Neither app dedups (see M4).
2. `order/history?expand=order` returns no per-order `waiting_cost` / `waiting_time`; and `waiting_time` is **seconds** on new rows but **milliseconds** on old ones while `execution_time` is always ms. Both apps now have to infer the unit — please normalise server-side.
3. History returns only ~11 records even with `per-page=100` (request fields verified correct client-side) — server-side truncation.
4. Decide agreed-price-vs-meter for fixed-route completion (M8) and make **one** side authoritative.
5. Admin panel: attach services (AC) to every tariff; upload per-tariff artwork (male-group photos are generic, Electro has none).

---

## Suggested order of work

1. **S1 + S2 in one commit** (credential logging), then **S3**, then **S4**. Ship as a hotfix — these are live in production.
2. **M1** (negative bonus — a list-mode driver may be unable to close an order).
3. **C1** (socket NPE process kill), **C2** (crash on the finish path).
4. **M2 + M3 + M4** together — same file, same root cause (list mode never got the map-mode fixes).
5. **U3** (blank tabs), **U1** (strobing pool).
6. **M8** — only after a product decision.
7. Then the refactor: **one shared finish/receipt function**, so this list does not regenerate itself.

Everything else as capacity allows.
