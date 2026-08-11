# Client app — the price must follow a mid-order service toggle

**Branch:** `mehrgo_client_app` · **Screen:** `OrderCreatedFragment`
**Written from:** the same defect found and fixed on `mehrgo_driver_app` (v2.1.1), verified against
prod on 2026-07-21.

---

## 1. What happened on the driver side

A client removed a service (Konditsioner, 5 000) in the middle of a trip. The driver's screen kept
showing the old total for ~2 minutes; the receipt at the end showed a different number. The money
was never wrong — the backend was right the whole time — but the app was showing a stale figure,
and one code path could have **billed** it.

Root cause: the app only refreshed its price when a GPS batch was uploaded. A stationary driver
uploads no points, so nothing refreshed. Nothing in the app reacted to "the services changed".

The client app has the same shape of bug, from the other end: **it is the side that changes the
services, and it does not refresh its own price afterwards.**

---

## 2. The defect, concretely

`presentation/screens/order_created/OrderCreatedFragment.kt` → `addRemoveService()` (~line 1978).
On `Resource.Success` it does exactly three things:

```kotlin
is Resource.Success -> {
    val updatedList = servicesOrderCreated.map { s ->
        if (s.id == service.id) s.copy(isEnabled = !s.isEnabled) else s
    }
    servicesOrderCreated = updatedList
    serviceOrderCreatedAdapter?.setPending(service.id, false)
    serviceOrderCreatedAdapter?.submitList(updatedList)
}
```

It flips the switch. **It never touches the price.** The user turns off a 5 000 service and the
total on screen does not move.

There is a second, independent gate. `applyLiveFare()` (line 1647) is the only thing that repaints
the live total, and it early-returns unless **all** of these hold:

```kotlin
if (order.state == ORDER_STATE_DONE) return
if (!isFreeMode(order)) return          // locations.size <= 1  → taximeter only
if (order.state != ORDER_STATE_WENT) return
```

So even when a socket frame does arrive, a **fixed-route** order (2+ points) never repaints — yet
its price genuinely changes when a service is toggled. Today the user sees the new number only
after leaving and re-entering the screen.

Everything needed is already wired: `getOrderGpsFare` (`ApiService.kt:159`), the socket event
`order_price_updated` (`MyService.kt:172` → collected at `OrderCreatedFragment.kt:429`). Nothing new
has to be built at the transport layer.

---

## 3. Server contract (verified, do not re-derive)

`GET order-gps/fare?order_id=<id>` and the socket frame `order_price_updated` return the same shape:

```json
"live": {
  "fare": 9000, "services_price": 5000, "waiting_cost": 33566.67,
  "podacha": 0, "extra_price": 0, "price": 48000
},
"price_breakdown": {
  "around_number": 1000,
  "estimate": { "price": 14000, "services_price": 5000, "podacha": 0, "extra_price": 0 }
},
"events": ["services_changed"]
```

Facts that matter:

- `live.price` = `fare + services_price + waiting_cost + podacha + extra_price`, rounded up by
  `around_number` (1000).
- `estimate.services_price` is the **frozen booking-time** services amount, and it is **already
  inside `order.price`**. Never add it on top — that is a separate bug we already closed.
- `events` contains `"services_changed"` on the frame that reflects a toggle. Use it; do not guess
  from a price delta.
- The server also sends an FCM **data** push:
  `type=order_price_recalculated`, `order_id`, `reason=services`, `price`, `surcharge`.
  Treat it as a **signal to refetch**, not as a value — the driver app deliberately ignores its
  `price` field.
- **The backend takes ~2.1 s** to reflect a services change. Anything that assumes an immediate
  answer will flicker.

---

## 4. What to implement

### 4.1 Repaint optimistically, immediately

On `Resource.Success` of the toggle, before any network call, recompute the displayed total by
swapping only the services component:

```
newTotal = currentLive.price - currentLive.services_price + newServicesTotal
```

This is exact for a services-only change and removes the dead 2-second window.

**Hard rule: the optimistic number is DISPLAY-ONLY.** Write it to the text view and nothing else.
Never store it into the field the receipt/complete flow reads. It can differ from the server's
recompute by up to one `around_number` step (1000), and on the driver side that difference was one
line away from being billed.

### 4.2 Then refetch the authoritative number

Which call depends on the order:

| Order shape | Where the price lives | What to call |
|---|---|---|
| Taximeter / free (`locations.size <= 1`) | `live.price` | `getOrderGpsFare(orderId)` |
| Fixed route (2+ points) | `order.price` | refetch the order |

The fixed-route case is the one currently missing entirely. `applyLiveFare` must stop early-returning
for it — either relax the gate for a services-driven refresh, or route fixed-route orders to the
order refetch and repaint from `order.price`.

### 4.3 React to the push and the socket event

- Socket `order_price_updated` where `events` contains `"services_changed"` → refresh.
- FCM `type == "order_price_recalculated"` → refresh.

**Guard both on the order id.** On the driver side `order_cancelled` fires for *any* order in the
pool, including ones the driver never accepted, and an unguarded handler wiped the wrong screen.
Assume the same about every socket frame: compare `data.order_id` with the screen's current order
and drop anything else.

### 4.4 Throttle, but carve out this case

If you add a refresh throttle (the driver app uses 8 s), a services change must **bypass** it.
Otherwise the one moment the user is actively looking at the price is the one moment you suppress
the update.

---

## 5. Traps we already hit

1. **`service.value` is not what gets billed.** It is the catalogue list price. The billed amount is
   `order_items[].total`. For percent-priced services (`price_mode`, `meta.percent_base`) they differ.
2. **Auto-added services are already inside the tariff price.** The client model already knows this
   (`Service.isAutoAdded`, `autoValue`, `autoCount` in `domain/model/Service.kt`). Excluding them
   from price math is existing behaviour — keep it when you touch this code.
3. **Do not key anything on the service id.** The same "Konditsioner" is `id: 1` inside
   `order_items[].service` and `id: 3` in the `service/order` catalogue. Two different id spaces.
4. **`order_items` can be empty in one payload and populated in the next** during a change; a
   handler that treats "empty" as "no services" will blink the total to zero.
5. Only the AC is toggleable mid-order today (`canToggle` in `ServiceOrderCreatedAdapter`, name-matched
   on "kondi"/"конди"). If that is a product rule it should be a server flag, not a string match in
   the adapter — worth raising separately.

---

## 6. Test checklist

- [ ] Toggle AC **off** mid-trip → total drops within one frame, and still matches the server after
      the refetch lands (~2 s).
- [ ] Toggle AC **on** again → total returns to the same figure as before, not a rounded-off variant.
- [ ] Same on a **fixed-route** order (2+ points) — this is the path that does nothing today.
- [ ] Toggle, then immediately background/foreground the app → number is the server's, not the
      optimistic one.
- [ ] Toggle, then let the trip finish → the receipt equals what the last screen showed.
- [ ] Toggle twice fast → one in-flight request per service is already enforced by `pendingServiceIds`;
      confirm the price does not end up one step behind.
- [ ] A socket frame for a **different** order arrives → this screen does not move.

---

## 7. For the backend (Muhammad)

- ~2.1 s latency before a services change is reflected in `order-gps/batch` / `order-gps/fare`.
  If that can come down, both apps can drop their optimistic repaint entirely.
- `GET service/order` does not return an `icon` for the driver token, while the client's
  `domain/model/Service.kt` declares one (`"@uploads/services/icons/x.svg"`) and has a full
  `SvgIconLoader`. Please confirm whether the field is populated/served conditionally — the driver
  app is wired for it and currently falls back to local glyphs.
