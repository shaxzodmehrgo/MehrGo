#!/usr/bin/env bash
# MaktabGo — запуск сервера (встроенный PHP-сервер, только на 127.0.0.1).
# Доступ с Windows — через SSH-тоннель, без правок nginx/firewall прода.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"

export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-maktabgo_dev}"
export DB_USER="${DB_USER:-maktabgo}"
export DB_PASS="${DB_PASS:-maktabgo_dev_pass}"
export ROUTE_PROVIDER="${ROUTE_PROVIDER:-haversine}"
export YANDEX_MAP_API_KEY="${YANDEX_MAP_API_KEY:-}"
PORT="${PORT:-8099}"

echo "MaktabGo → http://127.0.0.1:${PORT}   (родитель: /  · водитель: /driver.html)"
echo "Тоннель с Windows:  ssh -L ${PORT}:127.0.0.1:${PORT} mehrgo"
exec php -S 127.0.0.1:"${PORT}" -t web web/router.php
