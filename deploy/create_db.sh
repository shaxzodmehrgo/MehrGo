#!/usr/bin/env bash
# MaktabGo — создание ОТДЕЛЬНОЙ dev-БД и роли (запускать один раз, от root).
# Создаёт новую БД maktabgo_dev + схему shuttle. Существующие БД такси НЕ трогает.
set -e
DB="${DB_NAME:-maktabgo_dev}"
U="${DB_USER:-maktabgo}"
P="${DB_PASS:-maktabgo_dev_pass}"

psql_su() {
  if command -v sudo >/dev/null 2>&1; then
    sudo -u postgres psql "$@"
  else
    su -s /bin/bash postgres -c "psql $*"
  fi
}

psql_su -tc "SELECT 1 FROM pg_roles WHERE rolname='$U'" | grep -q 1 \
  || psql_su -c "CREATE USER $U WITH PASSWORD '$P';"
psql_su -tc "SELECT 1 FROM pg_database WHERE datname='$DB'" | grep -q 1 \
  || psql_su -c "CREATE DATABASE $DB OWNER $U;"
psql_su -d "$DB" -c "CREATE SCHEMA IF NOT EXISTS shuttle AUTHORIZATION $U;"
echo ">>> БД '$DB', роль '$U', схема 'shuttle' готовы."
