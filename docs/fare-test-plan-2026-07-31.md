# Live-fare / Finish-flow — qo'lda test rejasi (2026-07-31)

**Build:** 2.7.0+70 debug (qurilmaga o'rnatilgan). **Backend:** prod.mehrgo.uz.
**Loglar:** `adb logcat -s FareLive MySocketListener` (narx oqimi + socket freymlar),
to'liq sim uchun `adb logcat | findstr okhttp`.

Har bir keys uchun: agar natija kutilgandan farq qilsa — **order id + logcat** ni saqlab
yuboring, simdan tekshiramiz.

Qamrov: (A) safar davomidagi jonli narx, (B) finish oynasi, (C) receipt = server
settlement, (D) guard/regressiyalar. YAKUNIY model (79711 sinovidan keyin):
- **Trip ekrandagi hero (preview):** kelishilgan narx + safar o'rtasidagi xizmat
  deltasi (server order.price'ni yangilamaydi — 79711 da isbotlangan); kutish
  surcharge hint sifatida. Klient hech qanday tarif hisoblamaydi.
- **Finish popup (Итоговый счёт):** serverning O'Z jonli jami (`live.price`) —
  klient matematikasisiz; oflaynda fallback = kelishilgan + delta + kutish.
- **Receipt:** `order_completed` freymidan serverning yakuniy hisobi (narx + masofa +
  bonus/promo) — server B da tugatilganda kelishilganga PASTGA ham hukm qilishi mumkin.
Qo'shimcha: finish'da darhol GPS-flush (`reArm+nudge`), popup live-refresh,
stale-freym guard, order-id pin.

---

## A. Safar davomida (jonli narx)

### A1. B-order: hero kelishilgan narxda turadi (+ xizmat deltasi)
1. Klient app'dan A→B buyurtma yarating (masalan, xizmatlar bilan), qabul qiling,
   Boshlash → Go, yura boshlang.
2. **Kutilgan:** hero safar davomida kelishilgan narxni ko'rsatadi (metr o'sishi
   ekranga chiqmaydi). `FareLive` loglarida `price=` o'sib boradi, lekin `agreed=` doimiy,
   `surcharge=0` (kutish bepul oynada bo'lsa).
3. Klient safar O'RTASIDA xizmat qo'shsa/olib tashlasa: hero kelishilgan ± xizmat
   farqiga o'tadi (masalan 21 000 + AC 2 000 = 23 000) — 79711 regressiyasi.

### A2. No-B (taximeter): hero jonli metr
1. B nuqtasiz buyurtma (taximeter) oching, Go, yuring.
2. **Kutilgan:** hero masofa bilan o'sadi = server `live.price` (xizmatlar ichida).
   Lokal hisob faqat birinchi server freymigacha ko'rinishi mumkin.

### A3. Kutish billing'i
1. Safar o'rtasida 2 daqiqadan ko'proq turing (Standart: 120 s bepul, keyin 800/min).
2. **Kutilgan:** bepul oynadan keyin ovozli signal; freymlarda `waiting_cost > 0`;
   B-orderda hero ostida surcharge hint ("+X kutish uchun…").
3. Kutish tugagach yurishda narx davom etadi, sekundlar yo'qolmaydi (server echo).

---

## B. Finish oynasi (preview)

### B1. Finish popup = serverning jonli jami
1. Oddiy safar: A→B (reja bo'yicha), B da tugatish slaydini oching.
2. **Kutilgan:** popup jami = oxirgi `FareLive` log satridagi `price=` qiymati
   (serverning jonli jami: metr + xizmatlar + kutish). Bu kelishilgandan bir oz
   farq qilishi mumkin — normal: server B da tugatilganda receipt'da kelishilganga
   hukm qilib PASTGA tushiradi (C1).
3. Qatorlar (server komponentlaridan): Xizmatlar = live `services + podacha + extra`;
   Yo'l = jami − (xizmatlar + kutish), 0 dan past emas. Yig'indi = jami bo'lishi shart.

### B2. 2c divergensiya → popup katta server raqamini ko'rsatadi
1. 79711 stsenariysini takrorlang: reja ~2 km, lekin 10+ km aylanib, B dan uzoqda
   tugatish slaydini oching.
2. **Kutilgan:** popup serverning jonli jamisini ko'rsatadi (masalan 55 000 — 79711
   holati). Receipt ham shu raqamni tasdiqlaydi (C2). Hech qanday klient-hisob yo'q —
   popup shunchaki server raqamini ko'rsatadi.

### B3. No-B → dialog = jonli metr
1. Taximeter orderda tugatish oynasini oching.
2. **Kutilgan:** jami = so'nggi server `live.price` (xizmatlar ichida); oxirgi
   `FareLive` log qiymatiga mos.
3. Qatorlar (no-B da server komponentlaridan): Xizmatlar = live `services + podacha +
   extra`; Yo'l = jami − (xizmatlar + kutish). 77704-regressiya: qatorlar yig'indisi
   jamidan farq qilmasligi shart.

### B4. Xizmat qo'shish/olib tashlash → HERO yangilanadi  ← 79711 REGRESSIYASI
1. Safar o'rtasida klient xizmat qo'shsin/olib tashlasin (AC kabi).
2. **Kutilgan:** trip ekrandagi KO'RINADIGAN hero (Согласованная цена raqami) bir
   necha soniya ichida kelishilgan + delta ga o'tadi: 21 000 + AC 2 000 = **23 000**.
   79711 da xato aynan shu edi (ko'rinadigan hero 21 000 da qotib qolgan). Xizmat
   OLIB TASHLANSA delta manfiy — narx pastga tushadi. Popup esa server jamisini
   ko'rsataveradi (B1).

### B5. Darhol flush (reArm + nudge)
1. Yurib kelib, to'xtagan ZAHOTI tugatish slaydini torting (15 s kadensiyani kutmasdan).
2. **Kutilgan:** slayddan so'ng ~0.5–1 s ichida `FareLive: live: price=… dist=…` ack
   satri keladi va dialog o'sha eng so'nggi qiymat bilan ochiladi. 79688 dagi "bir
   freym orqada" holati takrorlanmasligi kerak.
3. Eslatma: batch sog'lom socket orqali ketadi — okhttp logida `order-gps/batch` POST
   ko'rinMASligi normal (REST faqat fallback). `FareLive: batch sent` satri esa faqat
   kutish timeri o'zgarganda yoziladi — uni kutmang; ishonchli belgi — (2) dagi ack.
4. (Lab) 403-latch varianti: server safar o'rtasida batch'larni 5 marta 403 bilan
   qaytargan bo'lsa ham, finish'dagi `reArm()` tufayli oxirgi bo'lak complete'dan
   OLDIN yuklanishi kerak (dala sharoitida majburlab bo'lmaydi — logda 403 seriyasi
   ko'rinsa e'tibor bering).

### B6. Dialog ochiq — kech kelgan freym repaint qiladi
1. B5 ni tezlashtirilgan variantda: slayd + dialog ochilgach 1–2 s kuting.
2. **Kutilgan:** agar ack dialogdan keyin kelsa, ekrandagi jami O'ZGARIB yangi
   qiymatga o'tadi (B-orderda bu faqat kutish/xizmat farqi bo'ladi; no-B da metr).
   Repaint Submit closure'ni ham qayta bog'laydi — shuning uchun ekranda ko'ringan
   raqam == `order/complete` dagi `total_price` (simdan tekshiring). Dialog boshqa
   orderga almashgan bo'lsa repaint bosilmaydi (D3 ga qarang).

### B7. Dialog ochiq — kutish SOATI muzlaydi
1. Kutish yoqilgan holda dialogni oching, 1 daqiqa turing.
2. **Kutilgan:** kutish SOATI to'xtaydi (dialog ortida billing yurmaydi). Dialog
   ochilgandan keyin ~15 s ichida kutish qatori BIR MARTA muzlatilgan qiymatga
   tenglashishi normal (oxirgi timer batch'ining ack'i) — undan keyin o'smasligi
   shart. "Davom etish" bosilsa — kutish qayta boshlanadi (bepul emas).

### B8. Submit'dan keyin raqam qotadi
1. Yakunlash slaydini torting, spinner paytida kuzating.
2. **Kutilgan:** spinner ostida raqamlar o'zgarmaydi (kech freym repaint qilmaydi).

### B9. Complete xato → retry yangi raqam bilan
1. (Lab keys) Complete'ning O'ZI xato bergan holat kerak (server 4xx / proksi blok) —
   aviarejimda fare freymlari ham kelmaydi, shuning uchun bu keys'ni u bilan sinab
   bo'lmaydi.
2. **Kutilgan:** dialog qayta faollashadi; POST xato paytida kelgan fare freymi bo'lsa
   dialog YANGILANIB ko'rsatadi; qayta slayd yangi raqamni yuboradi. (Oddiy dalada:
   xatodan keyin dialog faollashib, retry ishlashini tekshirish kifoya.)

---

## C. Receipt (server settlement)

### C1. 2a: receipt = kelishilgan
1. B1 ni yakunlang (B da tugatish).
2. **Kutilgan:** receipt jami = dialogdagi kelishilgan narx (server ham shu bilan
   hisoblaydi). Log eslatmasi: `order_completed` uchun alohida tag yo'q — u
   `MySocketListener` ning xom `Received: {"…"key":"order_completed"…}` satrida
   ko'rinadi.

### C2. 2c: receipt = serverning metri  ← ASOSIY KEYS
1. B2 ni yakunlang (B dan o'tib tugatish).
2. **Kutilgan:** dialog kelishilgan (29 000 uslub) → **receipt esa serverning haqiqiy
   narxi** (40 000 uslub). Receipt masofasi = HAYDALGAN km (7.68 uslub, nuqta bilan —
   "7,68" vergul chiqsa bu ham xato), reja (4.17) emas. Yo'l qatori = gross − kutish −
   xizmatlar.
3. Sim: `order_completed` freymidagi `price`/`distance` == receipt'dagi qiymatlar.
   Analitika: Meta eventi ham endi settled gross bilan yozilishi kerak (log muhim emas,
   bilish uchun).

### C3. Kech kelgan freym receipt'ni joyida yangilaydi
1. C2 da receipt ochilgan zahoti diqqat bilan qarang.
2. **Kutilgan:** freym complete'dan keyin kelsa, receipt ~1 s ichida joyida yangilanadi
   (eski holatda qotib qolmaydi).

### C4. Bonus/promo — server qiymatlari
1. Bonusdan foydalanadigan klient bilan buyurtma; tugating.
2. **Kutilgan:** receipt'dagi bonus qatori = freymdagi `bonus_payment` (server yechgani),
   preview'da hisoblangan emas. `finalTotal = gross − bonus − promo` va hech qachon
   manfiy emas. Klient balansi/backend admin bilan solishtiring.
3. Teskari yo'nalish: freym `bonus_payment: 0` bilan kelsa, bonus qatori yashiriladi va
   jami to'liq bo'ladi — bu server haqiqatidir (preview'dagi taxminiy chegirma emas).
   Server ROSTDAN bonus qo'llagan-u freymda 0 kelsa — bu backend xatosi, xabar bering.

### C5. Pul rekonsiliatsiyasi (har finish'dan keyin)
`user/me` bo'yicha tekshiring:
- `today.count` +1, `today.price` +gross;
- balans: +gross − 14% komissiya (Standart);
- klient `contact.bonus`: +2% keshbek;
- alohida `DRIVER_BONUS_CREDITED` FCM (reason 10) bo'lsa — u safarga aloqasiz, balansga
  qo'shimcha.

### C6. Degradatsiya: socket o'chiq holda finish
1. (Lab keys) Socket uzilgan, lekin REST ishlayotgan holatda tugating (masalan, serverga
   qayta ulanish oynasida).
2. **Kutilgan:** receipt submitted preview (kelishilgan) bilan ochiladi — bu qabul
   qilingan fallback; crash yo'q. Freym keyin kelsa (C3) yangilanadi.

---

## D. Guard'lar / regressiyalar

### D1. Stale-freym guard
1. B5/B6 paytida logda ikki xil satr chiqishi MUMKIN — bu normal (GET-vs-ack poygasi
   ushlangani): `stale fare frame dropped: dist=…` (masofa orqaga) va
   `stale fare frame dropped: wait=…` (masofa teng, kutish orqaga).
2. **Kutilgan:** hero/dialogda masofa yoki kutish hech qachon ORQAGA sakramaydi.
   Guard bir order ichida ishlaydi (order id bo'yicha) — D2 aynan shuni tasdiqlaydi.

### D2. Keyingi order toza boshlanadi
1. Finish'dan keyin yangi order qabul qiling.
2. **Kutilgan:** hero 0 km / yangi narxdan boshlanadi; oldingi orderning narxi/masofasi
   sizib o'tmaydi; birinchi freym (0 km) guard tomonidan tashlanmaydi.

### D3. Navbatdagi ikkinchi order — dialog almashmaydi
1. (Agar dispetcher ruxsat bersa) A aktiv + B navbatda; A ning finish dialogi ochiq
   turganda socket'ni uzib-ulang (refetch trigger).
2. **Kutilgan:** dialog B ning narxiga ALMASHMAYDI — repaint umuman bosilmaydi, dialog
   ochilgan paytdagi qiymatlarida qoladi (id-pin); slayd A ni yakunlaydi.

### D4. Chetdagi cancel finish'ga ta'sir qilmaydi
1. Dialog ochiq turganda branch'dagi boshqa (pool) order bekor qilinsin.
2. **Kutilgan:** popup ham, dialog teardown ham yo'q (mavjud id-guard); finish davom etadi.

### D5. Cold reopen safar o'rtasida
1. Safar o'rtasida app'ni process'dan o'ldirib qayta oching.
2. **Kutilgan:** hero server qiymatlarini qayta oladi (masofa/kutish adopt), finish
   oqimi normal ishlaydi; kutish sekundlari yo'qolmaydi.

### D6. Taximeter trek yuklanadi (regressiya)
1. No-B orderda qisqa safar qiling, tugating.
2. **Kutilgan:** batch'lar tracking boshidan ketadi (cpid 2, 3, …), receipt haqiqiy
   masofa/narx bilan (≈0 so'm emas).

### D7. Buzuq freym crash qilmaydi (kod bilan qoplangan, kuzatuv)
`order_completed` freymi buzuq kelsa (null data / bo'sh price) — app yiqilmasligi va
receipt 0 so'mga tushmasligi kerak (narx/masofada musbatlik gate'lari submitted
qiymatni saqlaydi; bonus/promo'da null-gate — 0 qiymat server haqiqati sifatida
qabul qilinadi, C4.3). Maxsus trigger qilib bo'lmaydi — istalgan finish'da
crash/0-narx kuzatilmasligi kifoya.

### D8. Bonusli order — dialog ochiq paytdagi refetch crash qilmaydi
1. Bonusli klient buyurtmasida finish dialogini oching; ochiq turganda socket'ni
   uzib-ulang yoki klient xizmat toggle qilsin (order refetch trigger).
2. **Kutilgan:** crash yo'q. Agar yangilangan payload bonus maydonlarisiz kelsa,
   dialog bonus qatorini yashirib, jami = to'liq narx bilan qayta chiziladi
   (0 chegirma — xavfsiz yo'nalish; server o'z chegirmasini settlement'da qo'llaydi
   va receipt C4 bo'yicha uni oladi).

### D9. Boshqa orderning `order_completed` freymi bizga ta'sir qilmaydi
1. Finish dialogi yoki receipt ochiq turganda branch'dagi BOSHQA order yakunlansin
   (ikkinchi haydovchi/dispetcher orqali).
2. **Kutilgan:** bizning dialog/receipt raqamlari o'zgarmaydi — receipt faqat O'Z
   orderining freymidan yangilanadi (id-keyed store; begona freym biznikini
   o'chirib yubormaydi).

---

## Yakuniy checklist (minimal yugurish, ~30 daqiqa)

| # | Keys | O'tdi? |
|---|------|--------|
| 1 | A1 hero pinned + A3 kutish | ☐ |
| 2 | B1 dialog = kelishilgan (2a) → C1 receipt = kelishilgan | ☐ |
| 3 | B2 dialog = kelishilgan (2c) → **C2 receipt = metr + tracked km** | ☐ |
| 4 | B5 darhol flush (slayddan so'ng ~1 s da ack + dialog mos) | ☐ |
| 5 | B7 kutish soati muzlaydi / Davom etish qaytaradi | ☐ |
| 6 | C4 bonusli order: receipt bonus = server `bonus_payment` | ☐ |
| 7 | C5 pul rekonsiliatsiyasi (today/komissiya/keshbek) | ☐ |
| 8 | D2 keyingi order toza | ☐ |
| 9 | D8 bonusli order + refetch → crash yo'q | ☐ |
| 10 | A2/B3/D6 taximeter (no-B) oqimi | ☐ |
