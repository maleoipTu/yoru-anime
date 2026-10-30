#!/usr/bin/env bash
# Проверка миграции Supabase на чистом PostgreSQL: схема применяется дважды (идемпотентность), затем — проверки правил.
# Нужны psql и переменные PGHOST/PGUSER (например, сервис postgres в GitHub Actions).
set -euo pipefail
cd "$(dirname "$0")/.."
db="yoru_test_$$"
createdb "$db"
trap 'dropdb --if-exists "$db"' EXIT
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/supabase-shim.sql
for _ in 1 2; do psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/migrations/20261030000000_accounts.sql >/dev/null 2>&1 || psql -v ON_ERROR_STOP=1 -d "$db" -f supabase/migrations/20261030000000_accounts.sql; done
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/accounts_test.sql
