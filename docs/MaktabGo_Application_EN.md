# MaktabGo — Hackathon Application

> Product: **MaktabGo** (backend internal codename "Birga"). National Transport Hackathon — Track 1, Mehr Go. A standalone product built on top of the Mehr Go ride‑hailing platform, fully isolated (its own database, schema `shuttle`; taxi tables are never modified).

---

## 1. Describe the project in one sentence

MaktabGo is a subscription service for safe, on‑time school transport ("home ⇄ school") that automatically pools children from the same school into shared rides with a fair, split price.

---

## 2. Project description

MaktabGo turns the daily school run from dozens of separate private‑car trips into automatically assembled, cost‑efficient shared school routes.

**Team & founder–market fit.** We are not new to mobility. Our team operates one of the region's largest ride‑hailing businesses: intracity taxi aggregators launched in **20+ cities across Uzbekistan, Kazakhstan and Kyrgyzstan**, and **1313 — the leading intercity (city‑to‑city) taxi aggregator** in the region. This operating experience, together with our data, shows the demand is real and large. The idea also comes from first‑hand exposure to the problem: one of the founders' brother runs a **network of 13 private schools across Uzbekistan**, and his operational analysis surfaced a major, recurring pain — getting children to school and back home **safely and on time**. And it is not just intuition: independent open transport data for Tashkent shows **0 of 146 school‑area stops have good public‑transport access** (details in section 4). MaktabGo is built to solve exactly that, backed by **real market analysis and interviews with our target audience**, which we will publish in the project's GitHub repository.

**How it works.** A parent uses the web app to add a child, drop pins for home (point A) and school (point B), choose weekdays and acceptable vehicle types, and instantly sees a live monthly price and route status ("forming — N more children needed" / "active") before subscribing. A dispatching/matching engine groups requests by school and corridor, geo‑clusters homes, selects the smallest sufficient van capacity, sequences the pickup stops, and activates the route once it reaches the minimum viable number of children, then assigns a driver. The driver (Android app, adapted from the Mehr Go taxi driver app) goes online, accepts a multi‑stop route on a Yandex map, navigates, marks each child's pickup stop by stop, and completes the trip — while parents watch live tracking. The product is fully isolated from taxi operations (its own `shuttle` schema), so it adds zero risk to production ride‑hailing.

**Status.** Backend + three web interfaces (parent, driver, dispatcher) are built and tested end‑to‑end and deployed to production (domain **maktabgo.uz**, SSL, Telegram OTP bot). The driver Android app is in active development: restoring the full taxi‑style flow (background tracking service, Yandex map with navigation, deep‑link into Yandex Navigator, multi‑stop route) plus a screen where a driver can propose their own A→B route.

---

## 3. Main project tasks

- **Live subscription pricing** — transparent "distance × rate ÷ co‑riders" with a pooling discount; the parent sees exactly what they pay for.
- **Automatic route pooling** — grouping by school/corridor/schedule, geo‑clustering of homes, capacity selection, stop sequencing, and a `min_children` launch threshold (break‑even).
- **Subscriptions & day management** — monthly subscription, calendar, day‑off policy (private car 0% / shared ride 60%), balances and automatic per‑route charging.
- **Dispatching & driver app** — go online, accept a route, multi‑stop map, mark pickups, complete, continuous GPS tracking.
- **Pickup points** — large vans collect children at shared points (walk ≤ 600 m); small cars pick up door‑to‑door.
- **Demand Intelligence (data‑driven launch targeting)** — an offline pipeline overlays a school morning‑demand proxy × poor transit accessibility × school locations to rank zones (`demand_zones.geojson`) and answer the shuttle's most expensive question — *where to launch and which routes to assemble* — with data, not guesses.
- **Auth & directories** — OTP login (Eskiz SMS + Telegram), curated list of partner schools.
- **Safe isolation** — everything in schema `shuttle`; no changes to taxi tables or services.
- **(In progress)** Full in‑app map/navigation flow + driver‑proposed A→B routes.

---

## 4. Target users and expected value

**Parents** — safe, predictable delivery of their child; cheaper than a private driver thanks to shared rides; transparent pricing; control via live tracking and flexible day management.

**Drivers / van owners (7–20 seats)** — steady utilization from recurring routes instead of one‑off orders, predictable income, ready‑made routes with navigation.

**Partner schools** — organized student drop‑off, less chaos and parking pressure at the gates.

**City & Ministry of Transport** — fewer private cars around schools (congestion, emissions, parking), plus demand data (a separate "MaktabGo Data Intelligence" track).

**Core value — the network effect on price:** the more children in a ride, the cheaper it is per child while staying break‑even. Engine example (10‑seat van, 6 km, Mon–Fri): at 3 / 6 / 10 children ≈ **475,000 / 218,000 / 116,000 UZS per month** per child, vs ≈ 880,000 UZS for a private car.

### Social impact (city, families, child safety)

- **Less congestion.** One shared 10‑child van ride removes **up to ~9 private cars per trip** from the road (otherwise parents drive children individually). At full load: one school with 10 routes — **up to ~90 fewer cars** at the morning gate peak; 100 routes city‑wide — **up to ~900 fewer cars** per trip. Consolidating ten separate home→school trips into one multi‑stop route also cuts total vehicle‑kilometers. The scale is confirmed by global data: the school run is a major share of the morning peak — **up to ~30% of traffic in London** and **~13% (school buses) in Dubai**. *(Our estimate assumes full load; actual impact depends on the share of families who previously drove.)*
- **Access to quality education.** Reliable, on‑time transport raises **attendance and punctuality** (fewer late arrivals and absences) — especially for families without a car or with working parents. A child can attend a **strong school beyond walking distance** — equal opportunity regardless of neighborhood or whether parents can drive; parents are freed from two "school‑run" trips a day.
- **Accident prevention.** According to WHO, road traffic injuries are the **leading cause of death for children and young people aged 5–29**, and school zones at peak hours concentrate that risk. MaktabGo lowers it: fewer cars at the gate at the most dangerous moment (less reversing and double‑parking), **professional vetted drivers** instead of rushed parents, live tracking and route accountability, and children no longer walking along or crossing busy roads alone.

### Local evidence — Track 3 open data (Tashkent & Olmaliq)

We validated the problem on the hackathon's Track 3 open data (Ministry of Transport / ATTO fare data + Yandex accessibility isochrones, 30.09.2025):

- **Good public transport is almost absent.** Of 2,872 Tashkent stops, only **80 (2.8%) have good accessibility ("green")**, while **864 (30.1%) are "red."** A green stop reaches **58.6 km² in 30 minutes**, a red one only **12.9 km²** — a **4.5× accessibility gap.**
- **The clearest link — schools sit on the worst transit.** Of **146 stops named after schools, 0 are "green"** and 31.5% are "red," worse than the city average. Poor public transport is literally where the schools are.
- **Families already rely on cars.** In Olmaliq there are **1.74 car trips per public‑transport trip** (modeled OD demand), with underserved residential zones reaching ~4× — demand for "someone to drive the child" already exists, today met by the family car.
- **Demand peaks at 07:00** — the school/work hour (Olmaliq, full‑month fare data).

Poor transit + schools on the worst‑served stops + a 07:00 school peak + car‑dominant demand = precisely the gap a pooled school shuttle closes. A full written analysis and an interactive Yandex‑map dashboard ("MaktabGo — Data Intelligence") accompany this submission.

> Benchmarks & provenance: international — [London ~30% morning traffic](https://airqualitynews.com/local-government/school-run-accounts-for-nearly-30-of-morning-traffic-in-london/), [Dubai school buses ~13%](https://gulfnews.com/amp/story/uae%2Ftransport%2Fdubai-school-bus-trips-account-for-13-during-morning-rush-hour-1.2084158), [WHO: leading cause of death ages 5–29](https://www.who.int/health-topics/road-safety/children-and-young-people). Local — Track 3: Ministry of Transport (ATTO, Tashkent & Olmaliq) + Yandex accessibility (30.09.2025); accessibility and OD figures computed on full data, used within the hackathon track.

---

## 5. Technologies used

**Backend:** PHP 8.2 / Yii2 (API‑only), PostgreSQL (schema `shuttle`); Yandex Maps API — JS API (maps), Directions / Distance Matrix (distances & polylines), Geocoder, Suggest; OSRM (routing fallback, `route.teamwork.uz`); Eskiz SMS + Telegram Bot API (OTP, plus a request‑import bot); Yii2 queue (`yii\queue\db`); Nginx, Let's Encrypt / certbot, systemd; cURL.

**Android (driver):** Kotlin, Clean Architecture, Hilt (DI), Retrofit + OkHttp (incl. WebSocket) + Gson, Kotlin Coroutines/Flow, Room, Yandex MapKit SDK 4.25, Glide, Firebase (FCM, Crashlytics, Analytics), foreground services + LocalBroadcastManager, Jetpack Navigation, View/DataBinding; built with Gradle, JDK 21.

**Web (parent / driver / dispatcher):** HTML + CSS + vanilla JS with Yandex Maps JS API, served by the Yii2 backend.

**Data / analytics:** Python (pandas) offline pipeline over ATTO + Yandex data producing `demand_zones.geojson`; interactive HTML dashboard with a Yandex map.

**Underlying Mehr Go platform:** additionally Redis, PostGIS, Supervisor, WebSocket dispatch, GitLab.

**Infra / DevOps:** Ubuntu 22.04, SSH; domain maktabgo.uz with SSL; Telegram webhook.

> Note: geocoding/address suggestions in MaktabGo use Yandex Geocoder/Suggest (Nominatim is not currently used — it can be added later as a free fallback). Redis and PostGIS are platform‑level (available via Mehr Go); the MaktabGo MVP itself computes geometry in PHP (haversine) and does not use them yet.

---

## 6. What support do you need from the mentors?

- **Maps & quotas:** production Yandex Maps keys and limits (MapKit, JS API, Geosuggest, Distance+Route) and billing at scale.
- **Payments:** local PSP integration (Payme / Click / Paynet) with recurring subscription billing, replacing the current "subscribed / charge" stub.
- **Regulation & child safety:** licensing for child transport in Uzbekistan, driver vetting and medical checks, insurance, vehicle requirements — and who to align with.
- **School partnerships:** reaching schools for the curated point‑B list and a pilot in 1–2 schools (demand density → pooling quality).
- **Data access:** a tariff/`fare_id` reference from the Ministry of Transport / ATTO (to turn our school‑demand proxy into an exact figure), and compute access to the full ATTO dataset to finalize the Tashkent hourly profile.
- **Scaling the matching engine:** review of the pooling algorithm (currently greedy; forming routes are re‑assembled each run) — incremental insertion, moving to a queue/worker, quality metrics (detour, occupancy).
- **Real‑time architecture:** whether the shuttle needs WebSocket (currently polling).
- **Data for the city:** how to package the impact data (less congestion/emissions near schools) for the Ministry of Transport.
- **Unit economics & go‑to‑market:** validating the pricing model, break‑even threshold, and growth strategy.
- **Mobile release:** finalizing the app (package rename, dedicated Firebase project, MapKit key, Google Play release).

---

## Appendix — traction (what's already done)

- Backend (Yii2, API‑only) + three web UIs (parent, driver, dispatcher) built and tested end‑to‑end.
- Live in production: **https://maktabgo.uz** (reverse proxy, Let's Encrypt SSL, Telegram OTP bot via webhook).
- Implemented & verified: OTP login, children/schools, live `/calc` pricing, subscription + automatic pooling (clustering → capacity selection → sequencing → activation at `min_children`), driver dispatching (accept/start/pickup/complete/track), day‑off with retention policy, balances and automatic charging.
- Driver Android app: API layer, OTP login and the routes‑list screen are ready; full map flow (Yandex map, navigation, multi‑stop) in progress.
- **Data evidence (Track 3):** analysis of open Ministry‑of‑Transport / ATTO + Yandex data and an interactive dashboard ("MaktabGo — Data Intelligence") — e.g., 2.8% green vs 30.1% red stops, 0 of 146 school stops green, a 07:00 demand peak. *(Our written analysis and audience interviews will be published on GitHub; the raw Track 3 datasets stay within the hackathon track.)*
- Market analysis and audience interviews validate the demand.

---

## Sources

- School run accounts for nearly 30% of London morning traffic — AirQualityNews: https://airqualitynews.com/local-government/school-run-accounts-for-nearly-30-of-morning-traffic-in-london/
- Dubai school bus trips ~13% of the morning rush hour — Gulf News: https://gulfnews.com/amp/story/uae%2Ftransport%2Fdubai-school-bus-trips-account-for-13-during-morning-rush-hour-1.2084158
- Road traffic injuries — leading cause of death for children and young people (5–29) — WHO: https://www.who.int/health-topics/road-safety/children-and-young-people
- Local evidence — Track 3 open data: Ministry of Transport (ATTO, Tashkent & Olmaliq) + Yandex accessibility (30.09.2025); used within the hackathon track.
