#!/usr/bin/env bash
# Server deployment script, called over SSH by .github/workflows/deploy.yml or run manually.
# Prerequisites: code synced to $APP_DIR (the workflow handles clone / reset), Docker Compose installed.
#
# Steps: read .env → docker compose up -d --build → health check.
# Preserves existing .env and data/ to protect secrets and database data.
#
# Independent of the technology stack: Node / Python / Go use the same procedure if the repository root
# contains compose.yaml (or docker-compose.yml) and a matching Dockerfile. Before first deployment:
#   1. Add compose.yaml + Dockerfile, with the container listening on $PORT.
#   2. Run cp .env.example /opt/agfp/.env on the server and fill in secrets (once).
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$APP_DIR"
echo "==> Application directory: $APP_DIR"

# 1) Require a Compose file and fail explicitly if it is missing.
COMPOSE=""
for f in compose.yaml compose.yml docker-compose.yaml docker-compose.yml; do
  [ -f "$f" ] && { COMPOSE="$f"; break; }
done
if [ -z "$COMPOSE" ]; then
  echo "❌ No compose.yaml at the repository root; there is no deployable application yet. Add compose.yaml + Dockerfile before merging."
  exit 1
fi

# 2) .env: keep secrets only on the server; never commit them to Git.
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    echo "⚠️ No .env found; using .env.example as a placeholder. Connect over SSH, fill in /opt/agfp/.env, and redeploy as soon as possible."
    cp .env.example .env && chmod 600 .env
  else
    echo "⚠️ Neither .env nor .env.example was found; starting without environment variables."
    : > .env; chmod 600 .env
  fi
fi

# 3) Build and replace the containers.
HOST_PORT="$(grep -E '^HOST_PORT=' .env 2>/dev/null | cut -d= -f2 | tr -d ' ' || true)"; HOST_PORT="${HOST_PORT:-8789}"
HEALTH_PATH="$(grep -E '^HEALTH_PATH=' .env 2>/dev/null | cut -d= -f2 | tr -d ' ' || true)"; HEALTH_PATH="${HEALTH_PATH:-/api/health}"
echo "==> docker compose -f $COMPOSE up -d --build"
docker compose -f "$COMPOSE" up -d --build --remove-orphans
docker image prune -f >/dev/null 2>&1 || true

# 4) Health check (local port only; public HTTPS is handled by nginx outside this script).
echo "==> Waiting for http://127.0.0.1:$HOST_PORT$HEALTH_PATH …"
ok=0
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$HOST_PORT$HEALTH_PATH" >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" -eq 1 ]; then
  echo "✅ Deployment succeeded: $(git log --oneline -1)"
else
  echo "❌ Health check failed. Recent logs:"
  docker compose -f "$COMPOSE" logs --tail=50
  exit 1
fi
