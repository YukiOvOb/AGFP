#!/usr/bin/env bash
# Execute only after the exact main SHA passes verify-release.mjs and server handover.
set -euo pipefail
umask 077
: "${APP_DIR:?Use the inventoried application path}"
: "${RELEASE_SHA:?Use the gate-verified main SHA}"
[[ "$RELEASE_SHA" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid release SHA'; exit 1; }
cd "$APP_DIR"
test -f .env
exec 9> .deploy.lock
flock -n 9 || { echo 'Another server release is running'; exit 1; }
test "$(git rev-parse origin/main)" = "$RELEASE_SHA"
git diff --quiet
git diff --cached --quiet
PREVIOUS_SHA=$(git rev-parse HEAD)
# Read simple KEY=value entries, never execute the server-owned environment file.
env_value() { sed -n "s/^$1=//p" .env | tail -1 | tr -d '\r'; }
BACKUP_DIR=$(env_value SERMS_BACKUP_DIR)
HOST_PORT=$(env_value HOST_PORT); HOST_PORT=${HOST_PORT:-8789}
DB_NAME=$(env_value SERMS_DB_NAME)
DB_USER=$(env_value SERMS_DB_USER)
DB_VOLUME=$(env_value SERMS_DB_VOLUME)
test -n "$BACKUP_DIR" && test -d "$BACKUP_DIR"
[[ "$BACKUP_DIR" = /* && "$BACKUP_DIR" != "$APP_DIR" && "$BACKUP_DIR" != "$APP_DIR/"* ]]
[[ "$HOST_PORT" =~ ^[0-9]+$ && "$DB_NAME" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ && "$DB_USER" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]
test -n "$DB_VOLUME"
docker volume inspect "$DB_VOLUME" >/dev/null
# Back up the confirmed running PostgreSQL 18 BEFORE checkout/build/migration.
DB_CONTAINER=$(docker ps -q --filter label=com.docker.compose.project=agfp --filter label=com.docker.compose.service=db)
[[ -n "$DB_CONTAINER" && "$DB_CONTAINER" != *$'\n'* ]]
test "$(docker inspect --format '{{.Config.Image}}' "$DB_CONTAINER")" = postgres:18-alpine
test "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$DB_CONTAINER")" = "$DB_VOLUME"
BACKUP="$BACKUP_DIR/pre-release-$RELEASE_SHA-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$BACKUP"
test -s "$BACKUP"
docker exec -i "$DB_CONTAINER" pg_restore --list < "$BACKUP" >/dev/null
printf 'previous=%s\nrelease=%s\nbackup=%s\n' "$PREVIOUS_SHA" "$RELEASE_SHA" "$BACKUP" > "$BACKUP.release"
git checkout --detach "$RELEASE_SHA"
export RELEASE_SHA
docker compose config --quiet
# Compilation failures leave currently running containers intact.
docker compose build
docker compose up -d --wait --wait-timeout 180
HEALTH=$(curl -fsS "http://127.0.0.1:$HOST_PORT/api/v1/health")
[[ "$HEALTH" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]
echo "Released $RELEASE_SHA. Verify real HTTPS login and workflows before acceptance."
# Retain previous revision/images and backup; never downgrade or delete database data automatically.
