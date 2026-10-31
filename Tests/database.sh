#!/usr/bin/env bash
# Проверка миграций Supabase на чистом PostgreSQL:
#  1) первая миграция применяется дважды (идемпотентность) и проверяются правила аккаунтов;
#  2) поверх уже заполненной базы накатывается вторая миграция (путь обновления), тоже дважды;
#  3) проверяются списки, достижения, streak, друзья и отзывы.
# Нужны psql и переменные PGHOST/PGUSER (например, сервис postgres в GitHub Actions).
set -euo pipefail
cd "$(dirname "$0")/.."
db="yoru_test_$$"
createdb "$db"
trap 'dropdb --if-exists "$db"' EXIT
run() { psql -q -v ON_ERROR_STOP=1 -d "$db" -f "$1" >/dev/null; }
run supabase/tests/supabase-shim.sql
run supabase/migrations/20261030000000_accounts.sql; run supabase/migrations/20261030000000_accounts.sql
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/accounts_test.sql | tail -1
run supabase/migrations/20261030120000_social.sql; run supabase/migrations/20261030120000_social.sql
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/social_test.sql | tail -1
run supabase/migrations/20261031000000_admin_customization.sql; run supabase/migrations/20261031000000_admin_customization.sql
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/admin_test.sql | tail -1
run supabase/migrations/20261101000000_party_stats.sql; run supabase/migrations/20261101000000_party_stats.sql
psql -q -v ON_ERROR_STOP=1 -d "$db" -f supabase/tests/party_test.sql | tail -1
