# Open issues — Mehrgo Driver (Android)

**Generated:** 2026-07-20 · **App:** 2.1.0 (61) · **Branch:** `mehrgo_driver_app`

**35 verified open items** — high: 4 · medium: 12 · low: 19

Owner split — android-client: 22 · backend: 9 · user-decision: 4

Every item below was produced by an agent reading the code, then **re-checked by a second agent whose only job was to refute it**. Refuted claims were dropped, so each surviving item has evidence someone actively failed to disprove. Evidence is `file:line` against the code as of the date above — verify before acting, code moves.

> **Already fixed on 2026-07-20 after this triage ran** (do not re-open): the socket `JSONException`/Gson mismatch + nullable `SocketFareResponse.data`, and the uncapped wait-resume gap (now `MAX_WAIT_RESUME_GAP_MS` = 10 min).

---

## HIGH (4)

### H1. Wait-resume credits the entire process-death downtime as billable waiting — and ratchets it into the server

`open` · owner: **android-client**

**Evidence.** CONFIRMED and WORSE than claimed. MyTrackingService.kt:788-805 — on restart with `was_waiting=true`, `gap = (System.currentTimeMillis() - lastTick).coerceAtLeast(0L)` is added wholesale to `timeWait` (:794) with NO upper bound; `wait_last_tick` is written only every 5s (:996-1002). Escalation found while trying to refute: that same `timeWait` is uploaded to the server in every GPS batch — `gpsBatchUploader.waitTimersProvider` reads `timeWaitInMillis` (MyTrackingService.kt:353-361) → GpsBatchUploader.kt:362-367 `FareGpsBatchRequest(waitingTime = waitMs, …)` — and the server stores the MAX of every timer value ever sent (documented at MyTrackingService.kt:932-941, `adoptServerWaits`). So the bogus gap inflates the SERVER's `waiting_cost`/`live.price` too, which is what the receipt then trusts (`waitLine = serverWaitCost ?: totalWaitingPrice`, MapFragment.kt:2661 / MyDirectionFragment.kt:1067). It is also POSTed raw at MapFragment.kt:2833-2856. No cap exists on any path.

**Next action.** Cap the credited gap in MyTrackingService.kt:790-793 (e.g. `gap.coerceAtMost(SOME_MAX)` sized to a plausible wait) BEFORE it reaches `timeWait`, since anything that lands there is uploaded and MAX-latched server-side and can no longer be walked back. An OEM battery-manager kill during pickup wait currently bills the passenger for the full downtime.

### H2. Finish receipt POSTs a price nobody has reconciled against a completed real order

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED, path corrected. app/src/main/java/uz/teamwork/mehrgodriver/presentation/maps/yandex_map/MapFragment.kt:2696-2706 `val taximeterTotal = serverPrice?.let { sp -> if (serverPriceIncludesExtras) Helper.roundPrice(sp).toLong() else Helper.roundPrice(sp + additionalPrice!! + totalServicePrice).toLong() }`. Source fields at :242-243 `serverPriceIncludesExtras = fare.live?.price != null` / `serverPrice = fare.live?.price?.roundToLong() ?: fare.price`. servicesRow rework at :2666-2671. Derived row at :2743-2745 `tvTrackPrice.text = ... Helper.roundPrice(totalPrice - (servicesRow + waitLine))`. It leaves the device: :2844-2848 `RequestOrderFinish(fDistance, ..., totalPrice.toString(), ...)` → domain/model/requests/RequestOrderFinish.kt:7 `var total_price: String`. Twin copy verified in presentation/main/ui/my_direction/MyDirectionFragment.kt:1099-1112 (POST at :1233).

**Next action.** Run one full taximeter order (NO B point) with ~3 min waiting. Screenshot the live hero price before swiping Yakunlash, then screenshot the receipt. Check (a) receipt Jami == live hero within rounding; (b) Masofa + Kutish + Xizmatlar sum exactly to Jami — any mismatch surfaces as a nonsensical or NEGATIVE 'yo'l narxi'; (c) the same total landed in the backend order record. Repeat in list mode (see item 9).

### H3. Agreed-price receipt bills live.waitingCost while the on-screen hint promises live.keptProjection

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED. Receipt arm: MapFragment.kt:2661 `val waitLine = serverWaitCost ?: totalWaitingPrice.toLong()` (serverWaitCost = `fare.live?.waitingCost?.roundToLong()` at :245), then :2708-2709 `val agreedTotal = Helper.roundPrice(order.price.toLong() + waitLine).toLong()`. Hint arm: MapFragment.kt:3377-3391 `updateAgreedSurchargeHint()` uses `live?.surcharge` and `live?.keptProjection?.roundToLong()` (:3386). The two quantities are documented as different things — domain/model/fare/FareLive.kt:31-34: surcharge = 'Waiting surcharge on top of the agreed price', kept_projection = 'Projected total if finished at B: agreed + waiting'. `grep -rn keptProjection app/src/main/java` returns exactly 2 non-comment hits: FareLive.kt:34 and MapFragment.kt:3386 — it is displayed and never billed.

**Next action.** Run a B-point order and wait 4-5 min at pickup (past the free window) so the hint flips to '+X kutish uchun · taxminan Y'. Record Y, finish, compare Y to receipt Jami. FAILURE = they differ, meaning waitingCost (raw waiting) and surcharge (billable beyond the free window) are not the same quantity and the receipt over-bills by the free window. If so, switch agreedTotal to `live.keptProjection` rather than recomputing locally.

### H4. Socket fare frame: Gson parse guarded by the wrong exception type, plus a non-null data param

`open` · owner: **android-client**

**Evidence.** CONFIRMED, and the crash is certain rather than speculative for one of the two triggers. common/socket/MySocketListener.kt:96-97 `val fareEnvelope = gson.fromJson(text, SocketFareResponse::class.java); fareChannel?.onSocketFare(fareEnvelope.key, fareEnvelope.data)`, wrapped only by `catch (e: JSONException)` at :100 with `import org.json.JSONException` at :11 — Gson throws JsonSyntaxException/JsonParseException (RuntimeException), never org.json.JSONException. PROVEN trigger: common/socket/SocketFareResponse.kt:14 declares `val data: FareResponse` non-null, Gson populates it reflectively without a null check, and common/fare/GpsBatchSocketChannel.kt:104 `fun onSocketFare(key: String, data: FareResponse)` is public → Intrinsics.checkNotNullParameter NPEs on a `"data": null` frame. `grep -rn setDefaultUncaughtExceptionHandler app/src/main/java` returns NOTHING, so this kills the process from the OkHttp reader thread. WEAKER than claimed: FareResponse.kt:23 `price: Long?` is a theoretical fractional-money trigger only — the live fractional capture (FareLive.kt:13-17, `waiting_cost: 2613.33`) was on a `live.*` field, and top-level `price` is documented at FareResponse.kt:8-9 as the already-rounded display amount.

**Next action.** Fix in code before the next shift test — no device needed. Wrap the ORDER_GPS_BATCH/ORDER_PRICE_UPDATED branch (MySocketListener.kt:95-98) in `runCatching`, or widen the outer catch to `Exception`; make `SocketFareResponse.data` nullable and early-return in the branch. Widening FareResponse.price/waitingSec/trafficSec to Double? is defensive hardening, not a proven fix — do it, but don't treat it as the root cause. Then confirm on device via `adb logcat -s FareLive` that pushes still parse during a trip.

---

## MEDIUM (12)

### M1. The one device-verified screen is the ONLY one that skips the changed growth branch

`needs-device-test` · owner: **android-client**

**Evidence.** Verified literally. `git diff` on MainActivity.kt confirms today's change replaced `height = actionBarSize + statusTop` (a theme-resolved framework android.R.attr.actionBarSize = 24dp) with per-toolbar `val baseHeight = toolbar.layoutParams?.height ?: 0` (MainActivity.kt:697) and `if (baseHeight > 0) { toolbar.updateLayoutParams { height = baseHeight + statusTop } }` (MainActivity.kt:720-722) — so every fixed-height toolbar grew by exactly 56dp-24dp = 32dp today. Enumeration re-confirmed independently: exactly 30 layouts under app/src/main/res/layout declare `@+id/clToolbar` or `@+id/llToolbar`; 29 declare `layout_height="?attr/actionBarSize"`, and `grep -rn actionBarSize app/src/main/res/values/` returns nothing, so with themes.xml:3 = Theme.MaterialComponents.Light.NoActionBar it resolves to the AppCompat 56dp. The single exception is app/src/main/res/layout/fragment_not_active_user.xml:17-20 (`@+id/clToolbar` + `layout_height="wrap_content"`, with the deliberate rationale comment at :11-16) — `git diff` on that file shows it was `?attr/actionBarSize` at HEAD and became `wrap_content` in this session. wrap_content = -2, so `baseHeight > 0` is false and the growth branch is skipped for the one screen looked at on device. The untested branch is taken by e.g. fragment_settings.xml:11-13, fragment_my_orders.xml:11-13, fragment_home.xml:14-16, fragment_my_profile.xml:10-12 (llToolbar). Refutation attempts that FAILED to clear it: (a) the callback does reach nav-graph destinations — `registerFragmentLifecycleCallbacks(..., true)` at MainActivity.kt:762 passes recursive=true, so child FragmentManagers are covered; (b) no height accumulation across the three applyStatusInset call sites (:744, :746-749, :756-759) because baseHeight/basePaddingTop are captured once from the declared params, making it idempotent.

**Next action.** Open one `?attr/actionBarSize` screen on device. fragment_my_orders is the sharpest test: its toolbar packs a `match_parent`-height logo ImageView (:19-23), tvMyPayment price text (:64-71) and the SwitchCompat (:85-91) inside a `gravity="center|end"` `match_parent` row, so any mis-sizing shows immediately. Confirm 56dp of content sits below the status bar with switch/price centered and unclipped. fragment_settings is the quick second check. Do not count the not_active_user check as coverage.

### M2. Receipt judge only verifies the FINAL stop — a skipped mid point still pays the full agreed price

`open` · owner: **user-decision**

**Evidence.** CONFIRMED, both copies read in full. C:/Users/Umar Inagamjanov/Desktop/teamwork_uz_ai/app/src/main/java/uz/teamwork/mehrgodriver/presentation/maps/yandex_map/MapFragment.kt:2711-2742 — `if (order.locations.size < 2)` … else takes `MyTrackingService.trackLocations.value?.lastOrNull()?.lastOrNull()` and compares it ONLY against `order.locations.last()` (:2725) with `endRadius = 300` (:2693); the sole escape hatch is the `abs(distanceTracked - distanceBetweenLocations) < 1000` fallback at :2733. Duplicated verbatim, minus the named constants, in .../presentation/main/ui/my_direction/MyDirectionFragment.kt:1114-1139 (`order.locations.last()` at :1125, literals 300 at :1127 and 1000 at :1132). Refutation attempts failed: `grep -rn 'reachedStop|stopReached|reachedFlags|locations\[i\]'` over app/src returns ZERO hits, and `grep -rn 'agreedTotal|taximeterTotal|endRadius'` finds exactly these two sites — there is no third path and no per-stop guard anywhere.

**Next action.** Ask the boss whether every mid stop must be radius-verified before the agreed price is honoured. If yes, set a per-stop reached-flag in-trip (within endRadius of each `order.locations[i]`) and require all flags before choosing agreedTotal — then apply it to BOTH MapFragment.kt:2711 and MyDirectionFragment.kt:1114, which are now duplicate copies of the same judge.

### M3. order/complete response `final` block still unconsumed — the app is still the billing authority

`backend-blocked` · owner: **backend**

**Evidence.** CONFIRMED. `grep -rn '"final"|SerializedName("final")|finalBlock' app/src --include=*.kt` returns ZERO hits. RequestOrderFinish.kt:7 still declares `var total_price: String`, computed app-side at MapFragment.kt:2711-2742 and POSTed to `@POST("order/complete")` (ApiService.kt:293-296). The bug-76967 client-side workaround is still live at MapFragment.kt:2701 / MyDirectionFragment.kt:1104 (`sp + additionalPrice!! + totalServicePrice` on the non-`serverPriceIncludesExtras` path).

**Next action.** Deliberately deferred receipt-from-server migration (Muhammad: 'skip for now'). Unblocking needs the complete-response JSON shape plus backend bug 76967 (services not summed). Leave the code as-is, but keep it on the list: the app — not the server — currently decides the billed amount.

### M4. OTP throttle must be keyed on (phone, channel) — Telegram window ≤20s

`backend-blocked` · owner: **backend**

**Evidence.** Client side VERIFIED complete. `@Field("channel")` present on all four auth endpoints: app/src/main/java/uz/teamwork/mehrgodriver/data/remote/ApiService.kt:68-79 (`POST user/register`), :81-86 (`POST user/refresh`), :103-108 (`POST user/recover`), :110-115 (`POST user/refresh?recover=1`). Both CTAs pass the right value: SignUpFragment.kt:151/154 and PasswordRecoveryFragment.kt:133/136 (`submit(Constants.CHANNEL_SMS)` / `submit(Constants.CHANNEL_TELEGRAM)`). Channel-aware cooldown confirmed: SignUpVerifyFragment.kt:64-66 and PasswordRecoveryVerifyFragment.kt:70-71 set waitTime=TELEGRAM_RESEND_WAITING_TIME on entry when channel==telegram; re-set per target channel after a resend at SignUpVerifyFragment.kt:285-289 and PasswordRecoveryVerifyFragment.kt:290-293. Constants.kt:343 TELEGRAM_RESEND_WAITING_TIME=20. TWO EVIDENCE CORRECTIONS: (a) the SMS branch uses Constants.kt:301 WAIT_TIME_VERIFY_CODE=60, NOT DEFAULT_SMS_WAITING_TIME — Constants.kt:342 DEFAULT_SMS_WAITING_TIME is declared and has ZERO usages repo-wide (dead constant). (b) the claim that this is 'purely a server problem' is only half true: the server DOES return a per-request `waiting_time` (SignUp.kt:9-10, SignUpResendCode.kt:9-10, PasswordRecoveryResendCode.kt:9-10) and the client deliberately IGNORES it in favour of the hardcoded 20/60 (resendVia Success block, SignUpVerifyFragment.kt:285-289) — that hardcode is the exact mechanism by which a client countdown can expire before the server window does. Severity lowered from high: nobody has observed a live rejection; the Telegram flow is coded and installed but device-untested, so this is an unverified contract requirement, not a reproduced failure.

**Next action.** Tell the backend dev verbatim: the OTP rate limiter on `user/register`, `user/refresh`, `user/recover` and `user/refresh?recover=1` must bucket by (phone, channel), not by phone alone — Telegram must allow a resend after 20s, and a switch Telegram→SMS at t=20s must not be rejected by an SMS 60s window still running for the same phone. Ask that the `waiting_time` field in the response reflect the cooldown for the channel actually used. Client-side hardening worth doing regardless: honor that returned `waiting_time` instead of the hardcoded 20/60 in SignUpVerifyFragment.kt:285-289 and PasswordRecoveryVerifyFragment.kt:290-293, so a server-side window change does not need an app release.

### M5. `events` array is repeated in every fare response instead of one-shot

`open` · owner: **backend**

**Evidence.** VERIFIED, all cited code is literally present. MyTrackingService.kt:184-190 carries the live-verified note ('LIVE-VERIFIED (2026-07-20): the backend repeats `waiting_charge_started` in EVERY response (not one-shot as the spec says)') and declares the two latches `chargeStartedBeeped` (:189) and `lastChargeStepBucket` (:190). The consuming collector is MyTrackingService.kt:367-391: `waiting_charge_started` beeps once per order (:370-373), `waiting_charge_step` beeps on a 5000-so'm bucket advance of `live.surcharge ?: live.waitingCost` (:375-381). Latches are reset in both lifecycle paths (:760-761 new-order, :825-826 clearTrackingState). Field parsed at FareResponse.kt:29-30, and it is logged at GpsBatchUploader.kt:497-500. The stale contradicting comment is CONFIRMED at GpsBatchSocketChannel.kt:114-115 ('the events only come in THIS payload, never repeated') — that comment is wrong; the MyTrackingService live capture is the later evidence and wins.

**Next action.** Ask backend to make `events` genuinely one-shot on `POST order-gps/batch` (ack), `GET order-gps/fare` and socket `order_price_updated`: emit `waiting_charge_started` in exactly ONE response when the free window ends, one `waiting_charge_step` per real charge step, then omit the key. Once fixed, delete the 5000-so'm bucket heuristic at MyTrackingService.kt:375-381 so step beeps align with the server's real step boundaries, and fix the now-known-wrong comment at GpsBatchSocketChannel.kt:114-115 either way.

### M6. `POST order/complete` returns no fare — receipt cannot move to server values

`backend-blocked` · owner: **backend**

**Evidence.** VERIFIED at ApiService.kt:293-297 — `@POST("order/complete") suspend fun orderFinish(@Query order_id, @Body RequestOrderFinish): BaseResponse<User>`, so the app parses nothing fare-related out of the response. Request body is the app-computed settlement: RequestOrderFinish.kt:3-19 (distance, total_price, waiting_time, waiting_time_ontheway, execution_time, bonus_payment, promo_code_payment). EVIDENCE CORRECTION: the claim that 'the receipt is rendered from local values' is overstated — the receipt is ALREADY server-first where a snapshot exists. MapFragment.kt (real path app/src/main/java/uz/teamwork/mehrgodriver/presentation/maps/yandex_map/MapFragment.kt):2660-2662 uses `serverWaitCost` with the local meter only as fallback, :2666-2670 builds the services row from `live.servicesPrice + podacha + extraPrice`, and :2695-2707 derives `taximeterTotal` from `serverPrice` (from `GET order-gps/fare`, ingested at :239-245) with the local meter as the `?:` offline fallback. Same in MyDirectionFragment.kt:1099-1106. So the real gap is narrower than stated: the values come from a PRE-complete `order-gps/fare` GET, and the server's own post-settlement numbers are never read back.

**Next action.** Get the exact `order/complete` RESPONSE JSON from Muhammad (field names + nesting for final price, server distance, waiting seconds, and the breakdown). He parked this migration ('skip for now'), so it stays blocked until he green-lights it. When the shape lands it is a small client change: widen the ApiService.kt:297 return type off `BaseResponse<User>` and bind the receipt to the settled values, keeping the existing order-gps/fare snapshot plus local meter as the fallback chain.

### M7. Order 76967 backend bugs: services not summed into the order total, agreed price ("baho") not saved

`backend-blocked` · owner: **backend**

**Evidence.** VERIFIED that the compensating client code still ships, with corrected line numbers: MapFragment.kt:233-236 ('The legacy top-level `price` lacks services (backend bug 76967) and still needs them added', with `serverPriceIncludesExtras` declared at :236 and set at :242), MapFragment.kt:2686-2687 ("app-side services/additional are added on top (backend doesn't sum them yet, bug 76967)") implemented at :2695-2707; mirrored in MyDirectionFragment.kt:143-145 / :154-155 and :1096-1106 (not :1097 alone). Original proof confirmed in repo-root NARX_HISOBLASH.md:111 (BACKEND BUG №1 — 10 000 service attached, Umumiy narx stayed 23 000) and :113 (BACKEND BUG №2 — 'Buyurtmadagi narx (baho): —' empty, so `price_kept_near_b` can never fire). CAVEAT KEPT AND REINFORCED: this is a client-side workaround still in place plus a 2026-07-19 doc entry, NOT fresh proof the server is still wrong today — I could not re-test the server. Note also that the `live` block DOES sum services (live.price = fare + services + podacha + extra + waiting, FareLive.kt:28-29), so the bug is scoped to the legacy top-level `price` and the stored order total, not to the live pipeline.

**Next action.** Ask backend to re-run order 76967's case and confirm two fixes: (a) attached `order_items` services are included in Umumiy narx / Amalda to'langan per doc §4, and (b) the agreed price is persisted on the order so `price_kept_near_b` can evaluate. Re-test before quoting 76967 to him — the last observation is a day old and the fix may have landed. No money is at risk right now because prod credits the app's `total_price`, but both are hard prerequisites before the server becomes authoritative (item: order/complete response shape).

### M8. Wait-timer resume after process kill credits the dead gap to BOTH the wait and the trip clock

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED (line numbers corrected). common/services/MyTrackingService.kt:788-804 — on `resuming && trackingPrefs.getBoolean("was_waiting", false)` it reads `wait_last_tick` (:789), computes `gap` (:790-792), then applies BOTH `timeWait += gap` (:794, persisted via updateWaitedTimeInLocale :796) AND `timeTrack += gap` (:799, updateTrackedTimeInLocale :801) before `startWaitingTimer()` (:803). Heartbeat written every 5 s at :999-1002. Both values are billed: RequestOrderFinish.kt:9 `waiting_time`, :10 `execution_time`. SEVERITY LOWERED from high: the over-credit is not a discovery — the in-code comment at :795-796 states the double-credit is deliberate ('Keep waits ≤ execution_time — the trip clock was equally dead for the same gap') and memory wait_timer_resume records it as knowingly left as-is. What is genuinely unverified is whether the resume fires at all.

**Next action.** On a real order at pickup: press Kutish, let it run 60 s, note the timer. `adb shell am force-stop uz.teamwork.mehrgodriver`, wait exactly 2 min, relaunch. Check `adb logcat -s FareLive` for 'resumed waiting after restart, gap=…ms'. FAILURE = timer still frozen (fix ineffective) or gap materially exceeds real downtime. Separately decide, as a product call, whether a driver who DROVE during the gap should be billed waiting — that is the accepted trade-off, not a bug to file.

### M9. adoptServerWaits (reconnect / second-device wait sync) has never run against a real server echo

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED. MyTrackingService.kt:942-971 lifts local timers to `maxOf(local, server)` from `live.waitingSec`/`live.onWaySec`; called on every fare publish at :398. Sole guard at :961 `if (newTotal <= curTotal + 2_000L) return`. Note an internal CONTRADICTION worth resolving on the device run: the KDoc at :937-940 says the server ROUNDS the echo ('sent 315939ms → waiting_sec=316', i.e. rounds UP), while the call-site comment at :396-397 asserts the opposite ('The echo of our own timer is floor(ms/1000)·1000 ≤ local'). Both cannot be true, and which one holds decides whether the 2 s threshold is sized correctly or is masking a self-ratchet. Pre-Go on-way deliberately not adopted (:944-951).

**Next action.** Mid-order, clear app data (or install on a second phone with the same driver account) and reopen so local timers are zero. Watch `adb logcat -s FareLive` for 'adopted server waits: pickup=… onWay=…'. FAILURE = timer restarts from 00:00 (adopt never fired, rider under-billed), or the local timer visibly ratchets upward on a single device with no gap (server rounds up, threshold too tight, rider over-billed). Capture one raw ack payload to settle the round-vs-floor contradiction.

### M10. Timer-only batch for a stationary driver: the only path that bills waiting when there is no GPS

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED, failure mode narrowed. common/fare/GpsBatchUploader.kt:330-357 — when `batch.isEmpty()` it still POSTs `FareGpsBatchRequest(points = emptyList(), waitingTime = waitMs, waitingTimeOntheway = onWayMs)` (:338-345), and on TERMINAL_FAIL latches `isBatchUploadEnabled = false` (:352-353), after which drainOnce returns immediately at :327 until reArm() (:193-196) or start() (:155). Timers attach only when changed (:390-396 `pendingTimers()`); the 5 s wait tick calls `nudge()` (MyTrackingService.kt:1006 → GpsBatchUploader.kt:258). CORRECTION to the claimed failure mode: a plain 400 is TRANSIENT_FAIL (:443-448, drops the batch and retries) and does NOT latch; the latch needs 401/402/405-499 via `in 401..499` (:472-476) or five consecutive 403/404 (:456-462). A 422 on an empty points array is exactly the latching shape, so the risk is real — just not triggered by every rejection. Second, quieter failure: on the socket path an ignored empty-points frame returns null after a 5 s ack timeout (GpsBatchSocketChannel.kt ACK_TIMEOUT_MS) before falling through to REST, which would stall the 5 s nudge cadence.

**Next action.** Park, start Kutish, sit completely still 5+ min with `adb logcat -s FareLive`. Expect a repeating 'batch sent: points=0 waiting_time=…' (logged at GpsBatchUploader.kt:407-411) every ~5 s and a matching 'live: … waitSec=' that keeps growing. FAILURE = 'batch sent' lines stop, or waitSec flatlines while the on-screen timer ticks. Note the HTTP status in the log — 400 means an endless silent retry, 4xx-other means the gate latched for the rest of the order; both under-bill the rider, but the fixes differ.

### M11. Socket reconnect rework (OkHttp pingInterval removed) unproven on a live shift

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED. MyTrackingService.kt:337-344 documents the deliberate removal and builds a bare `OkHttpClient.Builder().build()` (:343-344). `reconnectSocketNow()` at :693-698 does `isSocketConnect = false; MySocketListener.isSocketListener.postValue(false); webSocket?.cancel(); webSocket = client?.newWebSocket(request!!, mySocketListener!!)`, bypassing the stale-LiveData gate. Driven only from the 3-miss heartbeat at :887-892 (`MAX_MISSED_SOCKET_PONGS = 3` at common/Constants.kt:381, checked at :889). MySocketListener.kt:136-146 posts `isSocketListener.postValue(false)` on onFailure without reconnecting. All committed in HEAD (8ef635bf 'Majors'). Only runs while online and tracking, so it cannot be exercised from a desk.

**Next action.** Go online for a 30+ min shift including a tunnel/lift/dead-zone and watch the 'Qayta ulanmoqda' banner. PASS = banner appears only on genuine signal loss and clears within ~30 s. FAILURE MODE A = banner still flaps every ~20-30 s on good signal. FAILURE MODE B (worse, and the new risk introduced by removing protocol pings) = banner stays green but ORDER_NEW offers stop arriving on a half-open socket. Cross-check by having dispatch send a test order while parked in weak signal.

### M12. New drawable bg_button_soft.xml is untracked while 4 tracked layouts already reference it

`open` · owner: **android-client**

**Evidence.** CONFIRMED exactly as claimed. `git status --porcelain` → `?? app/src/main/res/drawable/bg_button_soft.xml`; `git ls-files app/src/main/res/drawable/bg_button_soft.xml` returns nothing (untracked). grep over app/src/main/res confirms 4 references, all in TRACKED and already-modified layouts: fragment_password_recovery_verify.xml:250 and :275, fragment_sign_up_verify.xml:253 and :278. `git grep bg_button_soft HEAD -- app/src/main/res/` exits 1 — zero references at HEAD, so all four are new in this diff and none can resolve without the untracked file. `git check-ignore -v` produces no output for it, so it is merely unstaged, not ignored.

**Next action.** Run `git add app/src/main/res/drawable/bg_button_soft.xml` before committing. A `git commit -a` stages only tracked modifications, so the four layout references would land without the drawable and AAPT would fail with 'resource drawable/bg_button_soft not found' on CI and every fresh clone.

---

## LOW (19)

### L1. Notification detail has no scroll container — the taller toolbar clips 32dp more of the body text

`open` · owner: **android-client**

**Evidence.** Verified literally, and confirmed unmitigated. app/src/main/res/layout/fragment_notification_detail.xml:2-8 root is a plain vertical LinearLayout (match_parent); the whole 121-line file contains no ScrollView/NestedScrollView/RecyclerView/ViewPager. Children: clToolbar :10-13 (`?attr/actionBarSize`, so it took today's +32dp), then the content LinearLayout at :54-119 holding a fixed 200dp CardView :60-66, tvTitle :81-87, tvDate :89-100, a divider :102-106, and tvText :108-115 (`wrap_content`, unbounded). Nothing is weighted or scrollable, so the toolbar growth pushes tvText down 1:1. Two refutation attempts failed: (1) the 200dp image card is never collapsed — NotificationDetailFragment.kt loads it unconditionally via Glide with a `.placeholder(R.drawable.ic_launcher_foreground)`, so the 200dp is always consumed even with no photo; (2) the body is server-supplied and unbounded — `tvText.text = Html.fromHtml(text ?: "")` in NotificationDetailFragment.onViewCreated, no maxLines, no ellipsize. The screen is reachable (mobile_navigation.xml:449-451). Note this is a pre-existing overflow that today's change worsens by 32dp; it also means the toolbar is now at its originally designed 56dp rather than the accidental 24dp.

**Next action.** Wrap the content LinearLayout at fragment_notification_detail.xml:54 in an `androidx.core.widget.NestedScrollView` (`layout_height="match_parent"`, `fillViewport="true"`) — the exact pattern already in fragment_access_permissions.xml:25-391 and fragment_add_card.xml:57-167.

### L2. Trip-time gap is credited only when the process died while WAITING, never while driving

`open` · owner: **android-client**

**Evidence.** CONFIRMED. MyTrackingService.kt:799-801 (`timeTrack += gap`) sits inside the `if (resuming && trackingPrefs.getBoolean("was_waiting", false))` block opened at :788. The non-waiting resume path (:771-783) only restores `timeTrack = savedCalculation.trackedTime`. `grep -rn 'track_last_tick' app/src` returns zero hits — no generic heartbeat exists; `startTrackingTimer` persists trackedTime only every 10s (:920-923), so the dead gap is simply lost. `execution_time` is fed from this value (MapFragment.kt:3138-3141 → :2850). Note the error direction is driver-unfavourable, not passenger-unfavourable — hence low.

**Next action.** Persist a generic `track_last_tick` heartbeat alongside `wait_last_tick` and credit the gap to `timeTrack` on ANY resume, not just the was_waiting one — otherwise `execution_time` under-reports after a mid-drive kill and the backend proportionally shrinks the waits (the reason cited in the comment at MyTrackingService.kt:797-798).

### L3. Go-online verification gate fails OPEN on a fetch error

`open` · owner: **user-decision**

**Evidence.** CONFIRMED, with corrected line numbers — the claim cited the condition lines, not the fail-open lines. `is Resource.Error -> startWork()` appears at HomeFragment.kt:602, MyOrdersFragment.kt:211, NotificationsFragment.kt:239, SettingsFragment.kt:396, MapFragment.kt:1579. The corresponding `if (status != null && status.canDriverGoOnline)` gates are at HomeFragment.kt:594, MyOrdersFragment.kt:204, NotificationsFragment.kt:231, SettingsFragment.kt:389, MapFragment.kt:1571. The in-code comment at HomeFragment.kt:601 states the fail-open is intentional ('don't block; the server still gates').

**Next action.** No code change unless the owner reverses the earlier decision — this is intentional anti-stranding behaviour backstopped by server enforcement. Worth one explicit re-confirmation, since if driver/verification-status ever 4xx/5xx's in prod, every driver goes online unchecked across all five entry points until it recovers.

### L4. Multi-point free-wait allowance is granted per LEG, not per stop

`open` · owner: **backend**

**Evidence.** CONFIRMED. MapFragment.kt:3181-3201 — the ORDER_STATE_CHANGED_GONE branch splits wait into exactly two buckets: `waitingPriceUntilGone` against `minWait` and `waitingPriceOnWay` against `minWaitOnWay` (one single `minWaitOnWay` deduction for the whole post-Go period). Every mid-stop wait collapses into that one on-way bucket and shares one free allowance. Impact is offline-only: the receipt line prefers the server value at MapFragment.kt:2661 and MyDirectionFragment.kt:1067 (`serverWaitCost ?: totalWaitingPrice`).

**Next action.** Confirm against the tariff spec whether `min_wait_time_on_way` is per-stop or per-trip for 3+ point orders. The server owns waiting_cost now, so it is the one to change; mirror it in the local offline fallback (MapFragment.kt:3181) only afterwards.

### L5. Live preview vs final receipt rule mismatch — superseded by the frozen-hero design

`likely-fixed` · owner: **android-client**

**Evidence.** CONFIRMED FIXED. MapFragment.kt:3316-3320 — `calculateTotalPrice` returns `order.price` immediately for any `locations.size >= 2 && price > 0`; the server-fare branch returns at :3324-3330. The old one-sided `(distanceTracked - distanceBetweenLocations) > 1000` rule at :3337 is reachable only for a B-order with `price <= 0` AND `serverPrice == null` — effectively dead. The freeze is documented as deliberate at :3311-3314, and the residual divergence is signposted in-trip by `updateAgreedSurchargeHint` (called at :3358).

**Next action.** Prune from the deferred list (memory note fare_receipt_deferred.md) — agreed-vs-taximeter divergence at the receipt is now by design.

### L6. Back-handler phantom resume-card flash — no longer possible, card removed

`likely-fixed` · owner: **android-client**

**Evidence.** CONFIRMED FIXED. MapFragment.kt:1417-1426 — `minimizeTrip()` only drops the sheet to STATE_COLLAPSED; the comment at :1420-1421 reads 'No resume card (the peek is always shown)'. `grep -rn cvResumeTrip` finds exactly three references: MapFragment.kt:1330 and :1782, both `View.GONE`, plus the layout itself, which declares `android:visibility="gone"` at fragment_map.xml:553. Nothing ever makes it visible.

**Next action.** Prune from the deferred list. The risky socket-handler change that was left for a deliberate decision is no longer needed. Optionally delete the dead cvResumeTrip block from fragment_map.xml:544-560.

### L7. Taximeter pickup-address fallback — implemented as decided, nothing open

`likely-fixed` · owner: **android-client**

**Evidence.** CONFIRMED FIXED. The locations[].name -> address.name -> placeholder chain is present at every display site: MapFragment.kt:1896, TripFinishFragment.kt:138, OrderHistoryDetailFragment.kt:66, OrderHistoryAdapter.kt:126, OrderOfferBottomSheet.kt:283. `grep -rniE 'reverseGeocode|geocod' app/src` returns zero hits, matching the decision to skip reverse-geocoding.

**Next action.** Prune. Separately, fix the MEMORY.md index line for taximeter_address_source.md — it says 'never show address.name — fall back to placeholder', which contradicts both the note body and all five code sites.

### L8. Woman-driver +10000 surcharge — no footprint in the driver repo

`likely-fixed` · owner: **android-client**

**Evidence.** CONFIRMED — not a driver-app item. `grep -rniE 'female|woman' app/src --include=*.kt` returns only the unrelated gender picker in CompleteDriverInfoFragment.kt:516-517 and CompleteDriverInfoVM.kt:55-57 (driver profile gender types). No surcharge, toggle, or +10000 constant exists anywhere in this repository.

**Next action.** Prune from the driver-app list — the note (woman_driver_surcharge.md) describes the CLIENT app. Nothing to do in this repo.

### L9. Duplicate waiting-charge beep from batch-ack + socket push — de-duped

`likely-fixed` · owner: **android-client**

**Evidence.** CONFIRMED FIXED. MyTrackingService.kt:369-381 — `waiting_charge_started` is gated by the once-per-order `chargeStartedBeeped` flag (:370-372) and `waiting_charge_step` by the 5000-so'm bucket marker `lastChargeStepBucket` (:375-380). Both are reset in the new-order branch (:760-761) and in `clearTrackingState()` (:825-826).

**Next action.** Prune. The residual sub-item (bucket floor not aligned to the server's step boundaries) is inaudible to the driver and backend-dependent — not worth tracking.

### L10. Unconfirmed: does the socket accept `order_gps_batch` frames and ack with the same key?

`backend-blocked` · owner: **backend**

**Evidence.** VERIFIED, and the socket transport is genuinely wired (I checked for the usual 'it is never actually enabled' escape hatch and there isn't one): GpsBatchUploader.kt:58 defaults `socketChannel` to null, but Hilt injects a real one — CommonModule.kt:38-42 `GpsBatchUploader(repository, pendingRepo, socketChannel)`; the channel is attached to the live websocket via MyTrackingService.kt:345 `MySocketListener(this, gpsBatchSocketChannel)` → MySocketListener.kt:47 `fareChannel?.attach(webSocket)` (GpsBatchSocketChannel.kt:40-42 sets connected=true). Socket-first preference at GpsBatchUploader.kt:416-429 (`if (ch.isConnected()) { val fare = ch.sendBatchAwaitAck(...) }`, REST only on a null result). Hard 5s wait at GpsBatchSocketChannel.kt:87-92 `withTimeoutOrNull(ACK_TIMEOUT_MS)`, constant at :135 = 5_000L. Frame shape at :66-79, expected ack key at :107-111. Severity lowered from medium to low: the failure mode is self-healing — the ack timeout falls through to REST (:427-428, and setConnected(false) drains pending acks at :44-53), the flush interval is 15s so a 5s stall is delay not data loss, and no live evidence exists either way (docs/ios-parity-plan.md:337 records that iOS sends batches over REST only, so the iOS client cannot corroborate server support).

**Next action.** Ask the backend dev to confirm the tracking socket handles an inbound `order_gps_batch` frame and replies `{key:"order_gps_batch", data:<same FareResponse>}`. Cheaper self-check first: run one trip with `adb logcat -s FareLive` — if every batch's live line lands ~5s after the flush, the socket is silently unsupported. If unsupported, disable the socket branch at GpsBatchUploader.kt:418 (or drop ACK_TIMEOUT_MS to ~1s) rather than waiting on backend work.

### L11. CLOSED — fractional money in the `live` block is no longer a defect

`likely-fixed` · owner: **backend**

**Evidence.** VERIFIED closed. FareLive.kt:13-17 documents the root cause (server sent `"waiting_cost": 2613.33`; a Long field made Gson throw NumberFormatException and killed the app on the getFare path) and every money field is `Double?` — FareLive.kt:24-31 (waitingCost, servicesPrice, podacha, extraPrice, fare, price) and :33-36 (agreedPrice, surcharge, keptProjection). Consumers round: MapFragment.kt:243, :245, :2669, :3380, :3386 and MyDirectionFragment.kt:155, :157, :1075. Rounding does not diverge from the server: Helper.kt:127-130 `roundPrice` rounds to the nearest 100, a no-op on 1000-rounded server values. One adjacent observation, NOT raised as an item: FareResponse.kt:23 still types the LEGACY top-level `price` as `Long?`, so the same crash class would return if the server ever sent a fractional top-level price — it is only safe because `live.price` is preferred whenever present.

**Next action.** No backend action — do NOT put this on the backend list. Only ask that money in `live` stays JSON numbers rather than quoted strings; fractional values are handled. If cheap, widen FareResponse.price to Double? for symmetry with FareLive.

### L12. Waiting-charge beep de-dup rebuilt after the live session, re-tested only in theory

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED, severity lowered. MyTrackingService.kt:367-391 — `waiting_charge_started` beeps once per order via `chargeStartedBeeped` (:370-373); `waiting_charge_step` beeps on a 5000-so'm bucket advance of `fare.live?.surcharge ?: fare.live?.waitingCost` (:375-381). Flags reset at :760 (new order) and :825 (clearTrackingState). The comment at :183-188 records the reason and is marked LIVE-VERIFIED (2026-07-20): the backend repeats `waiting_charge_started` in EVERY response, contradicting the spec. The bucket floor is `value / 5000.0` and is not aligned to the server's own step boundaries. SEVERITY LOWERED from medium: no money is computed from this path — a wrong or missing beep produces a driver-education/dispute problem, not a mis-bill, and the underlying de-dup rationale is already live-verified.

**Next action.** Wait 10+ min at pickup with volume up. Expect exactly ONE beep when the free window ends, then one per genuine server step. FAILURE = no beep at all, or a beep on every 15 s flush. If beeps land at odd amounts, the 5000 bucket is a guess — get the real step size from the tariff and align to it. Low priority relative to items 1-3.

### L13. List-mode drivers get no waiting-surcharge hint at all

`open` · owner: **user-decision**

**Evidence.** CONFIRMED by absence. `grep -rn tvPriceHint app/src/main/java app/src/main/res/layout` returns only MapFragment.kt:1944, MapFragment.kt:3381 and res/layout/dialog_bsh_order_state.xml:260 — the map-mode trip sheet only. Grepping MyDirectionFragment.kt for `tvPriceHint|updateAgreedSurchargeHint|includeDialog` returns ZERO hits, while that fragment consumes the identical live fare (MyDirectionFragment.kt:433-450 observes `GpsBatchUploader.latestFare` → applyServerFare) and builds the identical receipt (:1067-1141). AppTypeManager selects navigator vs list UI (referenced at MainActivity.kt:165, 225, 560, 932), so roughly half the fleet watches a B-order price diverge from the receipt with no explanation.

**Next action.** Decide whether list mode needs the hint; if yes, add a tvPriceHint to the MyDirection layout and reuse updateAgreedSurchargeHint's surcharge/kept_projection logic. Regardless of that decision, run every fare test in items 1-2 in BOTH UI modes — testing only map mode will not surface list-mode drift, and MyDirectionFragment carries its own copy of the receipt arithmetic that can diverge silently.

### L14. Waiting push (order_price_recalculated) rendering depends on backend payload keys

`backend-blocked` · owner: **backend**

**Evidence.** CONFIRMED — and the client is correct here, not at fault. `grep -rn order_price_recalculated app/src/main` returns NO matches; the app has no special-case handler. common/services/MyFirebaseMessagingService.kt:106-111 renders any push generically: `val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)` / `val body = message.notification?.body ?: message.data["body"] ?: ""`. Both notification-payload and data-payload pushes render fine; only a data-only push that omits `title`/`body` degrades to the app name with an empty body. The old worry in memory live_fare_protocol_v4 that 'data-only would be silent' is STALE — verified handled.

**Next action.** Wait past the free window with the app BACKGROUNDED and screen locked. PASS = a readable uz/ru waiting notification. FAILURE = a notification titled 'Mehrgo Driver' with no text, which is a backend payload defect (missing title/body data keys), not an app bug. Capture the `adb logcat -s FCM` line (logged at MyFirebaseMessagingService.kt:97-104 with key/data/notification) and hand it to Muhammad rather than filing against the client.

### L15. v61 crash-fix batch not yet confirmed in Play vitals

`needs-device-test` · owner: **android-client**

**Evidence.** CONFIRMED. app/build.gradle:21-22 `versionCode 61` / `versionName "2.1.0"`, in sync with common/Constants.kt:33-34. Fixes verified present today: exception-transparency reshape across the use-case tree — `grep -rln 'catch (e: Exception)'` over domain/use_case/ and data/ returns exactly 3 files (OrderHistoryDataSource.kt, SubscriptionsDataSource.kt, and the deliberate one in GetFareUC.kt); finish-flow guards at MapFragment.kt:2867 (`if (!isAdded || view == null) return` in orderFinish) and :2947 (`catch (parse: Exception)` around the raw error body), twinned at MyDirectionFragment.kt:1256 and :1286; overlay BadToken guards via `viewAdded` in WindowToAppService.kt:27,71,145 and AutoOfferService.kt:67,111,281. None of this is falsifiable on one device — it only shows up as a rate.

**Next action.** Ship v61 and read Play Console vitals after ~3 days of install base. PASS = crash rate below the 1.09% threshold with the SafeCollector.exceptionTransparencyViolated and BadTokenException signatures gone. The MapKit native ANR (main thread parked in libmaps-mobile.so) is Yandex-internal and will NOT clear from these fixes — do not read it as a regression.

### L16. SUPPORT_PHONE_NUMBER declared inside the active brand block — brand switch breaks compilation

`open` · owner: **android-client**

**Evidence.** Constants.kt:41 `const val SUPPORT_PHONE_NUMBER: String = "+998555161919"` sits between APP_VERSION_NAME (line 34) and ACTION_SEND_ORDER_DATA_BY_BROADCAST (line 42), i.e. physically interleaved in the active Mehrgo block (lines 24-52). grep confirms it appears exactly once in Constants.kt; no commented brand block (Ayol 8-22, Sevimli 63-77, Qulay 79-93, Yengil 95-109, Premium 111-120, ...) defines it. Helper.kt:36 references `Constants.SUPPORT_PHONE_NUMBER`, so commenting out the active block per CLAUDE.md's procedure yields an unresolved reference. SEVERITY DOWNGRADED medium→low: this is NOT a new breakage. `git show HEAD:...Constants.kt` shows `const val BASE` at line 24 (inside the block) and `IS_DEV_SERVER` at lines 53-54 (outside the block) already reading `BASE.contains("ayoltaxi")` — so the documented mechanical brand switch ALREADY fails to compile at HEAD, before this diff. The new constant adds a second instance of a pre-existing pattern, not a regression.

**Next action.** Move SUPPORT_PHONE_NUMBER out of the brand block into the shared section near IS_DEV_SERVER (Constants.kt:53-61), and while there also relocate `BASE` or make IS_DEV_SERVER independent of it — fixing only SUPPORT_PHONE_NUMBER still leaves the brand switch non-compiling. Add a per-brand support line to each commented block if white-labels should not inherit Mehrgo's number.

### L17. 57 MB app-release.aab is tracked in git with 15 revisions of history

`open` · owner: **user-decision**

**Evidence.** CONFIRMED, but the size figure in the original evidence was an unverified estimate. `git ls-files app/release/` → `app/release/app-release.aab` (tracked). `git diff HEAD --stat` → `app/release/app-release.aab | Bin 57640414 -> 57723588 bytes` (modified again in the current working tree). `git log --oneline --follow -- app/release/app-release.aab | wc -l` → 15. .gitignore (15 lines total) has `/build` at line 11 and no rule matching app/release or *.aab — `git check-ignore -v app/release/app-release.aab` produces no output. MEASURED repo size, replacing the claimed '~850 MB of blobs': `git count-objects -vH` → size 539.81 MiB loose + size-pack 491.70 MiB, i.e. roughly 1.0 GB total. No runtime impact — severity corrected medium→low as this is repo hygiene, not a defect.

**Next action.** Decide whether release artifacts belong in git. If not: `git rm --cached app/release/app-release.aab`, add `app/release/` and `*.aab` to .gitignore. A history rewrite (git-filter-repo / BFG) would reclaim most of the ~1.0 GB but invalidates every existing clone, so it needs team sign-off first.

### L18. Constants.kt comment documents a 'button hides' behavior that does not exist in the code

`open` · owner: **android-client**

**Evidence.** CONFIRMED. Constants.kt:40 reads "Blank = no support line for that brand → the call button hides instead of failing." grep across app/src/main/java for cvDispatcher|tvCommunicateOperator|cvDispatcherCall returns exactly 4 hits, all `setDebouncedClickListener` registrations and zero visibility/GONE logic: NotActiveUserFragment.kt:67, SettingsFragment.kt:208, MapSettingsFragment.kt:147, MapFragment.kt:1145. All four fall back to `showToast(getString(R.string.not_assigned_dispatcher))`. Secondary claim also holds: Helper.kt:34-36 ends in `?: Constants.SUPPORT_PHONE_NUMBER.takeIf { it.isNotBlank() }` against a non-blank compile-time literal, so dispatcherOrSupportNumber() can never return null for Mehrgo and the toast branch is unreachable in the current brand.

**Next action.** Correct the Constants.kt:40 comment to say the fallback is a toast, or implement the documented hide (set the dispatcher CTA to GONE when Helper.dispatcherOrSupportNumber() returns null) at all four call sites so the comment and the code agree.

### L19. Stray zero-byte ui_dump.xml at repo root, not covered by .gitignore

`open` · owner: **android-client**

**Evidence.** CONFIRMED. `git status --porcelain` → `?? ui_dump.xml`. `ls -l ui_dump.xml` → 0 bytes, dated Jul 20 05:50. `git check-ignore -v ui_dump.xml` produces no output and exits 1, so no .gitignore rule matches it (the 15-line .gitignore covers only *.iml, .gradle, several /.idea paths, .DS_Store, /build, /captures, .externalNativeBuild, .cxx, local.properties).

**Next action.** Delete ui_dump.xml and add `ui_dump.xml` (or `*_dump.xml`) to .gitignore so UIAutomator dumps are not swept into a future `git add .`.

---

## Per-topic state

### Regression risk from today's MainActivity.setupEdgeToEdge() per-toolbar height change

Both claimed items survive adversarial verification and neither needed a severity change; nothing was refuted, so nothing was dropped. I independently re-derived the core premise rather than trusting it: the 30-file toolbar enumeration is exact (29 `?attr/actionBarSize` + 1 `wrap_content`), there is no `actionBarSize` override anywhere in res/values against the NoActionBar Material theme, and `git diff` confirms the change is a straight swap from a theme-resolved 24dp base to each toolbar's own declared 56dp — i.e. a uniform +32dp on 29 screens today. I also confirmed the summary's "broad fix, not broad break" reading: the 4 layouts using a sibling `layout_marginTop=\"?attr/actionBarSize\"` (change_app_type/change_language/change_theme/choose_map) do have the toolbar as a direct child of a root FrameLayout — verified on fragment_change_theme.xml:2 (root FrameLayout) with the toolbar at :10-13 and the NestedScrollView sibling at :55-58 — so the marginSiblings loop at MainActivity.kt:704-713 shifts them and they land flush; under the old 24dp base they had a 32dp gap, so those are genuinely fixed. Three plausible refutations I chased and closed: the lifecycle callback does reach nav-graph destinations (recursive=true at MainActivity.kt:762, otherwise nothing would have applied at all); repeated applyStatusInset calls cannot accumulate height because baseHeight is captured once pre-mutation; and the notification-detail 200dp image card is never hidden, so its space is always consumed. Remaining risk is entirely visual — no crash, no money path.

### Previously-deferred code review findings — adversarial verification at commit 8ef635bf

I opened every cited location and tried to refute each claim; all 11 survived, so nothing was dropped. Corrections made: (a) MapFragment lives at presentation/maps/yandex_map/, not presentation/main/ui/maps/; (b) item 5 cited the gate-condition lines rather than the fail-open lines — the real `Resource.Error -> startWork()` sites are HomeFragment:602, MyOrdersFragment:211, NotificationsFragment:239, SettingsFragment:396, MapFragment:1579; (c) the MyDirectionFragment judge block runs to :1139, not :1135. One severity is raised: the wait-resume gap credit (item 2) goes from medium to HIGH, because refutation work turned up that the inflated `timeWait` is not merely POSTed at finish — it rides along in every GPS batch via `waitTimersProvider`, and the server stores the MAX of every timer value ever sent, so the phantom wait also inflates the server's own waiting_cost/live.price that the receipt then trusts, and it cannot be walked back once sent. Items 7-11 are confirmed already fixed or never applicable and are kept only so the corresponding memory notes get pruned; item 9 additionally exposes a MEMORY.md index line that contradicts both its own note body and the code.

### Driver app: open issues that require a BACKEND change (client side already correct)

All six items survived verification — nothing was refuted outright, but three evidence claims were wrong or overstated and two severities were inflated. Corrections: (1) MapFragment lives at presentation/maps/yandex_map/, not presentation/main/ui/maps/yandex_map/. (2) Item 4's \"receipt is rendered from local values\" is overstated — the receipt is already server-first off the pre-complete `GET order-gps/fare` snapshot (MapFragment.kt:2660-2707) with the local meter only as `?:` fallback; the real gap is that the post-settlement response is never read. (3) Item 1's \"purely a server rate-limit problem\" is half true — the server does return a per-request `waiting_time` (SignUp.kt:9-10 et al.) and the client deliberately ignores it for a hardcoded 20/60, which is the actual mechanism that would let the client countdown expire before the server window; also Constants.DEFAULT_SMS_WAITING_TIME (Constants.kt:342) has zero usages, the SMS path uses WAIT_TIME_VERIFY_CODE=60. Severities: item 1 high→medium (Telegram flow is coded and installed but device-untested, no live rejection observed), item 3 medium→low (I checked for the usual \"socket is never actually enabled\" escape hatch and there isn't one — Hilt really injects it, CommonModule.kt:38-42 — but the failure mode self-heals to REST within 5s on a 15s flush cadence). Items 2, 5 and 6 verified as claimed with line-number fixes; item 5's caveat is reinforced — it rests on a day-old doc entry plus a client workaround, not fresh server proof, so re-test before quoting order 76967. One latent risk noted inside item 6 rather than as a new item: FareResponse.kt:23 still types the legacy top-level `price` as `Long?`, the same shape that crashed on fractional money before `live` was preferred.

### Mehrgo Driver v61 (2.1.0) — built-but-unverified server-fare / timer subsystem, adversarially re-verified

All 11 claimed items survive verification — I could not refute any of them, though several file paths and line numbers in the incoming list were wrong and are corrected below. The single biggest path correction: MapFragment lives at `presentation/maps/yandex_map/MapFragment.kt`, NOT `presentation/main/ui/maps/yandex_map/` as cited; that path does not exist and would have sent someone hunting. Three claims were partially over-stated and are narrowed rather than dropped. (a) Item 3's crash argument rests on two triggers, only one of which is provable: the `data: FareResponse` non-null parameter NPE is certain (no global uncaught handler exists — `grep -rn setDefaultUncaughtExceptionHandler app/src/main/java` returns nothing, so an OkHttp-reader-thread throw kills the process), but the "fractional top-level price" trigger is speculative — the live fractional capture was `live.waiting_cost`, and top-level `price` is documented as the already-rounded display value. (b) Item 6's "gate latches" failure mode is real but narrower than claimed: a 400 returns TRANSIENT_FAIL (GpsBatchUploader.kt:443-448) and retries forever; only 401/402/405-499 (`in 401..499`, :472-476) or five consecutive 403/404 latch the gate — a 422 on an empty points array is exactly that shape, so the risk stands. (c) Item 8's failure mode is a UX/dispute issue, not a mis-bill, so I dropped it from medium to low. Item 4 I dropped high→medium: memory already records the double-credit as a knowingly accepted trade-off, so what is genuinely unverified there is only whether the resume fires at all. Everything else keeps its severity. Two items the incoming summary already proposed dropping (`fare_recalc_on_foreground`, the YouTube video) were not in the claimed list and I confirmed the reasoning — `grep -rn "ForegroundRefreshable\|recalculateFare" app/src/main/java` returns nothing in this repo, so that note is about the client app.

### Fresh bug sweep over the uncommitted working tree (mehrgo_driver_app branch) — verification pass

All five claimed items survive adversarial verification — every cited file:line is literally true in the current working tree, and I could not find a guard, caller, or elsewhere-fix that moots any of them. Two severities were overstated and are corrected. Item 1 drops medium→low: the brand switch does NOT newly break here, because `const val BASE` (Constants.kt:24, inside the active block) and `IS_DEV_SERVER` (lines 53-54, outside it, reading BASE) already make the documented comment-out procedure non-compiling at HEAD — SUPPORT_PHONE_NUMBER just adds a second instance of an existing pattern, which also means the suggested fix is incomplete unless BASE is handled too. Item 3 drops medium→low as repo hygiene rather than a defect, and its unverified "~850 MB" estimate is replaced with the measured `git count-objects -vH` figure of roughly 1.0 GB. I also independently confirmed the parent summary's "no runtime defects" conclusion: all four dispatcher call sites do use setDebouncedClickListener, and the refactor genuinely removes a TOCTOU NPE (the old code called UserManager.getUser() twice — null-check then `!!`). The only item with real build-breaking potential is the untracked bg_button_soft.xml.


