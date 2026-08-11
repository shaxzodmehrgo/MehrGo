# MaktabGo — школьная подписочная перевозка (MVP)

**MaktabGo** («вместе») — подписочная перевозка детей: дом ⇄ школа и другие регулярные точки.
Совместные рейсы дешевле личного водителя (цена делится между семьями), диспетчерская
собирает рейсы из заявок автоматически (пулинг по гео-кластерам).

**Прод:** https://MaktabGo.uz · **Стек:** PHP 8.2 / Yii2 (API-only) + PostgreSQL + Yandex Maps.

> National Transport Hackathon — Track 1 — Mehr Go. Продукт поверх инфраструктуры такси Mehr Go,
> но полностью изолирован (своя БД `maktabgo_dev`, схема `shuttle`).

## Что внутри

- **Родитель** (`web/index.html`): заявка (дети/школа/формат/дни/адрес), Yandex-карта с точкой A,
  поиск адреса, расчёт стоимости (личный / совместный), подписки, трекинг машины, отмена дня.
- **Водитель** (`web/driver.html`): OTP-вход/регистрация, приём рейсов, старт, посадка по точкам,
  трекинг, симуляция движения.
- **Диспетчер** (`web/admin.html`): живая карта, заявки, рейсы, водители, школы, клиенты,
  биллинг; CRUD и балансы. Доступ по `ADMIN_TOKEN`.
- **Бэкенд** (Yii2 API): пулинг, ценообразование (fair-split), OTP (SMS Eskiz + Telegram),
  балансы/транзакции с авто-списанием за рейс, Yandex (карта/саджест/геокодер/расстояния).

## Быстрый старт (ноутбук)

```bash
composer install
# создать БД maktabgo_dev + схему shuttle (см. docs/HANDOFF_Birga_v4.md §1)
export DB_HOST=127.0.0.1 DB_PORT=5432 DB_NAME=maktabgo_dev DB_USER=maktabgo DB_PASS=maktabgo_dev_pass
export ROUTE_PROVIDER=haversine ADMIN_TOKEN=dev-admin
php yii migrate/up --interactive=0
php yii seed/index && php yii seed/demo && php yii import/yuksalish
php -S 127.0.0.1:8080 -t web web/router.php
```

Открыть `http://127.0.0.1:8080/` (родитель), `/driver.html`, `/admin.html`.

## Документация

📖 **[docs/HANDOFF_Birga_v4.md](docs/HANDOFF_Birga_v4.md)** — полная документация: архитектура, модель
данных, бизнес-логика (ценообразование, пулинг, OTP, биллинг), весь API, переменные окружения,
деплой, что дальше, грабли. **Начинать отсюда.**

🗺 **[docs/TAXI_CORE_MAP.md](docs/TAXI_CORE_MAP.md)** — где в бэкенде такси лежит переиспользуемый
CORE (OTP/Eskiz, балансы, платежи, Yandex) для дальнейшего переноса.

## Деплой

Скрипты в `deploy/`: `deploy_migrate.sh` (код + миграции), `deploy_code.sh` (только код),
`deploy_v3.sh` (полный с пересидом), `activate_yandex.sh` (Yandex-ключи). Подробно — HANDOFF §12.

## Статус

v3 + Yandex + ценообразование + админка + OTP + биллинг — на проде. Дальше: Android-приложение
водителя (Яндекс-навигация, открытие в Яндекс.Навигаторе, мультистоп) и платёжные провайдеры
(Payme/Paylov). Демо-авторизация: OTP работает в dev-режиме (код на экран), пока не заданы
`ESKIZ_*` / `TELEGRAM_BOT_TOKEN`.
