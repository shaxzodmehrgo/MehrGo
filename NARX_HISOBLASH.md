# Narx hisoblash — hozirgi holat (Driver ilova)

Manba: `MapFragment.kt` (hukm: 2583–2635, kutish: 3030–3075, masofa: 3095–3135), `Helper.kt` (`roundPrice`: 108, `calculatePriceWithDistanceIntervals`: 422). Boss spec: 2026-06-24.

---

## 1. Qayerda hisoblanadi

| Nima | Qayerda |
|---|---|
| Jonli taksometr (safar davomida ko'rinadigan raqam) | **Lokal** (ilovada) |
| Yakuniy hukm (yakunlash oynasidagi summa, `total_price` sifatida yuboriladi) | **Lokal** (ilovada) |
| **Haqiqiy to'lov** (yangi backend) | **Server** — o'z GPS treki + kutish vaqtlaridan o'zi hisoblaydi. App yuborgan `total_price` va `distance`ni **qabul qilmaydi**. Lokal raqam = taxmin |

---

## 2. Tarkibiy qismlar (lokal taksometr)

Barcha qiymatlar buyurtma **qabul qilingandagi** tarifdan olinadi.

Oxirgi ustun — yangi backend'da xuddi shu komponentni **kim/nimadan** hisoblashi:

| Komponent | Lokal formula | Server (yakuniy to'lovda) |
|---|---|---|
| Boshlang'ich | `order.startingPrice` — bir marta | SERVER — o'z tarif-snapshotidan |
| Ustama (surge) | `order.addPrice` | SERVER — o'z ma'lumotidan (`extra`) |
| Xizmatlar | `Σ service.total` | SERVER — o'z `order_items`idan |
| Masofa (shahar ichi) | `(distanceInCity − minDistance)` intervallar bo'yicha: har interval bo'lagi × `interval.price`/km, qoldiq × `priceInCity`/km. **Bepul `minDistance` faqat shahar-ichidan chegiriladi** | SERVER — **o'z GPS trekidan** (`GIS_fare`; app trekni faqat "Kettik"dan keyin yuboradi). App yuborgan `distance` **ishlatilmaydi** |
| Masofa (shahar tashqarisi) | `distanceOutCity / 1000 × priceOfOut` (intervalsiz, chegirmasiz) | — (yuqoridagi `GIS_fare` ichida) |
| Kutish (podacha) | ARRIVED'da avto boshlanadi. `(sekund − minWaitTime) / 60 × priceOfWaiting`. "Kettik"da muzlatiladi | **VAQTNI APP O'LCHAYDI** (`waiting_time`, ms) → **NARXNI SERVER** o'z tarifidan hisoblaydi (GPS'dan emas) |
| Kutish (yo'lda) | Haydovchi qo'lda yoqadi. `(sekund − minWaitTimeOnWay) / 60 × priceOfWaitingOnWay`; stavka berilmagan bo'lsa `priceOfWaiting` | **VAQTNI APP O'LCHAYDI** (`waiting_time_ontheway`, ms) → **NARXNI SERVER**; `price_of_waiting_on_way ≤ 0` bo'lsa server ham `price_of_waiting`ga fallback qiladi (app bilan bir xil qoida) |

**Masofa metrlanishi "Kettik" (GONE) dan boshlanadi** — haydovchi→mijoz (podacha) yo'li hisobga kirmaydi. Serverga GPS ham faqat "Kettik"dan keyin yuboriladi.

**Yaxlitlash:** yakuniy summa 100 so'mgacha (`roundPrice`: /100 → round → ×100).

Ikki tayyor summa:

```
taximeterTotal = round( boshlang'ich + ustama + xizmatlar + kutish
                        + masofaIchi + masofaTashqi )

agreedTotal    = round( order.price + kutish )
                 // xizmatlar va ustama order.price ICHIDA hisoblanadi
```

---

## 3. Yakuniy hukm — barcha hollar

> Bu hukm **ikkala rejimda bir xil**: navigator (MapFragment) va list (MyDirectionFragment).
> 2026-07-19'gacha list-rejimda eski TESKARI qoida qolgan edi (B'ga <200 m → taksometr!) —
> chuqur auditda topilib, boss-spec bilan tenglashtirildi.

Har bir qoida uchun **LOKAL** = ilova o'z `total_price`ini shunday tanlaydi; **SERVER** = yangi backend'da to'lovni belgilaydigan qoida.

### A) 2 nuqta (A → B)

Oxirgi GPS nuqtasi bilan **B** solishtiriladi:

| Shart | Natija | Kimda bor |
|---|---|---|
| B'dan **≤ 300 m** da tugatildi | `agreedTotal` (kelishilgan narx + kutish). Masofa **tekshirilmaydi** | **IKKALASIDA** — serverda `price_kept_near_b` |
| B'dan uzoq, lekin \|yurilgan − rejaviy (`order.distance`)\| **< 1000 m** | `agreedTotal` | **FAQAT LOKAL** — serverda bunday qoida yo'q (u to'g'ridan-to'g'ri `GIS_fare` hisoblaydi) |
| B'dan uzoq va farq **≥ 1000 m** | `taximeterTotal` | LOKAL; server har doim o'z `GIS_fare`i |
| GPS umuman yo'q (trek bo'sh) | `taximeterTotal` (xavfsiz fallback) | LOKAL |

### B) B nuqtasiz klient buyurtmasi (`locations.size < 2`)

→ **Har doim `taximeterTotal`.** `order.price` ishlatilmaydi — 300 m ham, rejaviy masofa ham tekshirib bo'lmaydi (B yo'q).
Server ham xuddi shunday: B yo'q → `price_kept_near_b` istisnosi yo'q → sof `GIS_fare` + kutish.

**Ekranda (2026-07-19 dan):** B'siz buyurtmada safar kartasi "Kelishilgan narx" o'rniga **"Joriy narx"** — jonli taksometr, har tick'da yangilanadi (server jonli narxi kelsa — o'sha, bo'lmasa lokal hisob).

### C) Taksometr (haydovchi yaratgan buyurtma)

Bitta nuqta bilan yaratiladi → B holatiga tushadi → **har doim `taximeterTotal`**. Serverda ham sof `GIS_fare`.

### D) Ko'p nuqtali (3+ nuqta)

Hukm **faqat OXIRGI nuqta (yakuniy B)** bo'yicha — A holatidagi 300 m / 1 km qoidalari o'shanga qo'llanadi. Oraliq nuqtalar narx hukmida qatnashmaydi, ular faqat yo'ldagi kutish qo'shishi mumkin.

---

## 4. Serverga nima ketadi (`POST order/complete`)

| Maydon | Qiymat |
|---|---|
| `total_price` | Lokal hukm natijasi (3-bo'lim) |
| `distance` | Server masofasi kelgan bo'lsa u, bo'lmasa lokal (ichi+tashqi), km |
| `waiting_time` | Faqat **podacha** kutishi, ms |
| `waiting_time_ontheway` | Faqat **yo'ldagi** kutish, ms |
| `execution_time` | Umumiy safar vaqti, ms |
| `bonus_payment`, `promo_code_payment`, finish koordinata, `accuracy` | — |

**Yangi backend:** `total_price` va `distance`ni e'tiborsiz qoldiradi — o'zi hisoblaydi (GPS trek + kutish maydonlari; B'ga ~300 m ichida tugatilsa kelishilgan narxni saqlaydi — `price_kept_near_b`). **To'lov = server raqami.**

**Eski backend:** app yuborgan `total_price`ni aynan qabul qilardi.

---

## 5. Real test-keys: buyurtma 76967 (2026-07-19, yangi build)

| Ko'rsatkich | Ilova | Server (admin) | Xulosa |
|---|---|---|---|
| Masofa | 5.89 km | 6.18 km | ✅ A→B gate ishlaydi (approach-leg inflatsiyasi yo'q) |
| Kutish | 10 s | Kutish: 10 s | ✅ `waiting_time` ms→s to'g'ri |
| Yo'ldagi kutish | 0 | Tirbandlikda: 0 | ✅ `waiting_time_ontheway` yetib boryapti |
| Yakuniy narx | 33 100 (23 100 yo'l + 10 000 xizmat) | **23 000** | ❌ pastdagi bug |

**BACKEND BUG №1 — xizmat jamlamaga qo'shilmagan:** admin'da `Xizmatlar: Ayol haydovchi: 1 × UZS 10 000 = UZS 10 000` biriktirilgan, lekin `Umumiy narx / Amalda to'langan = 23 000` — faqat GIS-fare. Doc §4 formulasi (`narx = GIS_fare + services + ...`) buzilgan. Mijozdan kam olindi, haydovchi 10 000 xizmat pulini yo'qotdi.

**BACKEND BUG №2 — baho saqlanmagan:** ilovada "Kelishilgan narx 16 000" bor edi; admin'da `Buyurtmadagi narx (baho): —` bo'sh. Baho saqlanmasa `price_kept_near_b` hech qachon ishlay olmaydi.

Ilova tomonида xato yo'q — 33 100 doc formulasiga mos. (Receipt lokal ekani — 6-bo'limdagi 1-ochiq nuqta.)

## 6. Ochiq nuqtalar

1. "Buyurtma yakunlandi" ekrani hali **lokal** qiymatlarni ko'rsatadi — server javobidagi yakuniy narxga o'tkazish rejalashtirilgan (backend `complete` javobida narx qaytara boshlagach).
2. Ko'p nuqtalida faqat oxirgi B tekshiriladi (spec shunday) — oraliq nuqtalar tekshirilmaydi.
3. Jonli server narxi (`ORDER_PRICE_UPDATED` socket) faqat ko'rsatish uchun — hukmda ishlatilmaydi.
