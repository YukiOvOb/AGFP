#!/usr/bin/env bash
# Exercise release failure boundaries with fake executables; never contact a server.
set -euo pipefail
SOURCE_DIR=$(cd "$(dirname "$0")" && pwd)
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/bin" "$TEST_DIR/app" "$TEST_DIR/backups"
export TEST_LOG="$TEST_DIR/commands" TEST_MODE=success
export TEST_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
cat > "$TEST_DIR/app/.env" <<EOF
HOST_PORT=8789
SERMS_DB_NAME=serms
SERMS_DB_USER=serms
SERMS_DB_VOLUME=confirmed-volume
SERMS_BACKUP_DIR=$TEST_DIR/backups
EOF
cat > "$TEST_DIR/bin/git" <<'EOF'
#!/usr/bin/env bash
echo "git $*" >> "$TEST_LOG"
case "$*" in
  'rev-parse origin/main') echo "$TEST_SHA" ;;
  'rev-parse HEAD') echo bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ;;
  'diff --quiet') test "$TEST_MODE" != dirty ;;
esac
EOF
cat > "$TEST_DIR/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$TEST_LOG"
case "$1 $2" in
  'ps -q') echo db-container ;;
  'inspect --format')
    if [[ "$3" = '{{.Config.Image}}' ]]; then echo postgres:18-alpine
    elif [[ "$TEST_MODE" = wrong-volume ]]; then echo other-volume
    else echo confirmed-volume; fi ;;
  'exec db-container')
    if [[ "$3" = pg_dump ]]; then
      test "$TEST_MODE" != backup-failure || exit 1
      echo archive
    fi ;;
  'exec -i') cat >/dev/null ;;
  'compose build') test "$TEST_MODE" != build-failure ;;
esac
EOF
cat > "$TEST_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
echo 'curl health' >> "$TEST_LOG"
if [[ "$TEST_MODE" = unhealthy ]]; then echo '{"status":"DOWN"}'; else echo '{"status":"UP"}'; fi
EOF
cat > "$TEST_DIR/bin/flock" <<'EOF'
#!/usr/bin/env bash
echo 'flock release' >> "$TEST_LOG"
test "$TEST_MODE" != busy-lock
EOF
chmod +x "$TEST_DIR/bin/"*
export PATH="$TEST_DIR/bin:$PATH"
for TEST_MODE in busy-lock dirty wrong-volume backup-failure build-failure unhealthy success; do
  export TEST_MODE
  : > "$TEST_LOG"
  if APP_DIR="$TEST_DIR/app" RELEASE_SHA="$TEST_SHA" bash "$SOURCE_DIR/deploy.sh" > "$TEST_DIR/result" 2>&1; then
    test "$TEST_MODE" = success
  else
    if [[ "$TEST_MODE" = success ]]; then cat "$TEST_DIR/result"; fi
    test "$TEST_MODE" != success
  fi
  case "$TEST_MODE" in
    busy-lock|dirty|wrong-volume|backup-failure) ! grep -q 'git checkout' "$TEST_LOG" ;;
    build-failure) ! grep -q 'compose up' "$TEST_LOG" ;;
    success) grep -q 'compose up -d --wait' "$TEST_LOG" ;;
  esac
  test -f "$TEST_DIR/app/.env"
  ! grep -Eq 'down|prune|remove-orphans|volume rm' "$TEST_LOG"
  echo "PASS deployment boundary: $TEST_MODE"
done
