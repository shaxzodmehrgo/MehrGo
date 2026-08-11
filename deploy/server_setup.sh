#!/usr/bin/env bash
# MaktabGo — установка на сервере (Composer + миграции + демо-данные).
# БЕЗОПАСНО: работает только в своей папке и в своей БД maktabgo_dev.
# Таблицы такси, supervisor, nginx, git-дерево прода — НЕ трогаются.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"
echo ">>> App: $(pwd)"

export COMPOSER_ALLOW_SUPERUSER=1
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-maktabgo_dev}"
export DB_USER="${DB_USER:-maktabgo}"
export DB_PASS="${DB_PASS:-maktabgo_dev_pass}"
export ROUTE_PROVIDER="${ROUTE_PROVIDER:-haversine}"

if command -v composer >/dev/null 2>&1; then
  composer install --no-interaction --no-progress
else
  echo "!! composer не найден — установите зависимости вручную и повторите"; exit 1
fi

mkdir -p runtime && chmod -R 0777 runtime 2>/dev/null || true

php yii migrate/up --interactive=0
php yii seed/index
php yii seed/demo
echo ">>> Готово. Запуск сервера: bash deploy/server_start.sh"
