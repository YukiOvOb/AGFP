#!/usr/bin/env bash
# Daily custom-format dump of the inventoried PostgreSQL 18 service.
set -euo pipefail
umask 077

APP_DIR=${APP_DIR:-/opt/agfp}
[[ -d "$APP_DIR" ]] || { echo 'APP_DIR does not exist' >&2; exit 1; }
cd "$APP_DIR"
env_value() {
  if [[ -f .env ]]; then sed -n "s/^$1=//p" .env | tail -1 | tr -d '\r'; fi
}
DB_NAME=${SERMS_DB_NAME:-$(env_value SERMS_DB_NAME)}
DB_USER=${SERMS_DB_USER:-$(env_value SERMS_DB_USER)}
BACKUP_DIR=${SERMS_BACKUP_DIR:-$(env_value SERMS_BACKUP_DIR)}
[[ "$DB_NAME" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ && "$DB_USER" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || {
  echo 'Invalid database name or user' >&2; exit 1;
}
[[ "$BACKUP_DIR" = /* && -d "$BACKUP_DIR" && ! -L "$BACKUP_DIR" ]] || {
  echo 'Backup directory must be an existing absolute directory' >&2; exit 1;
}
[[ "$BACKUP_DIR" != "$APP_DIR" && "$BACKUP_DIR" != "$APP_DIR/"* ]] || {
  echo 'Backup directory must be outside the checkout' >&2; exit 1;
}
CONTAINER=${SERMS_DB_CONTAINER:-$(docker compose ps -q db)}
[[ -n "$CONTAINER" && "$CONTAINER" != *$'\n'* ]] || {
  echo 'Exactly one running db container is required' >&2; exit 1;
}
[[ "$(docker inspect --format '{{.Config.Image}}' "$CONTAINER")" = postgres:18-alpine ]] || {
  echo 'Expected the inventoried PostgreSQL 18 image' >&2; exit 1;
}
TEMP=$(mktemp "$BACKUP_DIR/.serms-nightly-XXXXXX")
trap 'rm -f "$TEMP"' EXIT
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$TEMP"
[[ -s "$TEMP" ]] || { echo 'Backup is empty' >&2; exit 1; }
docker exec -i "$CONTAINER" pg_restore --list < "$TEMP" >/dev/null
TARGET="$BACKUP_DIR/serms-nightly-$(date -u +%Y%m%dT%H%M%SZ).dump"
[[ ! -e "$TARGET" ]] || { echo 'Backup timestamp already exists' >&2; exit 1; }
mv "$TEMP" "$TARGET"
trap - EXIT
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'serms-nightly-*.dump' -mmin +10080 -delete
printf 'Backup verified: %s\n' "$TARGET"
