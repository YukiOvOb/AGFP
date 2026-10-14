# PostgreSQL backup and isolated restore

This runbook applies to the inventoried PostgreSQL 18 Compose service. The release owner must first verify the host, active Compose project, database container, existing volume, and backup location in `docs/server-release.md`. Production execution is gated by issue #10.

## Daily backup

Set `SERMS_DB_NAME`, `SERMS_DB_USER`, and `SERMS_BACKUP_DIR` in the server-only `/opt/agfp/.env`. The backup directory must exist, be outside the checkout, and be writable only by the backup operator. Run `APP_DIR=/opt/agfp /opt/agfp/scripts/backup.sh` from a daily scheduler. The script uses `pg_dump -Fc`, verifies the archive with `pg_restore --list`, publishes it atomically, and removes only matching nightly dumps older than seven days after a successful new dump. Alert on any nonzero exit or missing daily dump. Keep backup access separate from the web service; do not commit dumps or credentials.

A cron example for the confirmed operator is `15 2 * * * APP_DIR=/opt/agfp /opt/agfp/scripts/backup.sh >> /var/log/serms-backup.log 2>&1`. Confirm timezone, log ownership and monitoring during handover before installing it. The script does not install a cron job itself.

## Scratch restore rehearsal

Do this without touching the live database or its volume:

1. Select a recent `.dump`; verify `pg_restore --list FILE` exits successfully.
2. Create an isolated PostgreSQL 18 container or scratch database with no application connection. Use a new database name and local-only credentials.
3. Run `pg_restore --no-owner --no-acl -U SCRATCH_USER -d SCRATCH_DB FILE` in that scratch environment. A custom archive can be streamed via stdin to `pg_restore` inside a container.
4. Query schema objects and representative row counts; compare with the source snapshot. Record date, archive identifier, PostgreSQL versions, restore command exit code and counts, excluding credentials and user data.
5. Remove only the scratch container/database after evidence is recorded. Never restore into the production database or delete its volume as a rehearsal.

A successful `pg_restore --list` is an archive check, not a restore rehearsal. The release recovery procedure and switchover remain in `docs/server-release.md`.

## 50-user load check

`SERMS_K6_BASE_URL` points to an isolated local deployment. `SERMS_K6_USERS_FILE` points to an untracked JSON array of at least 50 distinct `{email,password,equipmentId,startAt,endAt}` fixtures. Each VU logs in, searches availability, and submits a reservation. Use distinct equipment or disjoint intervals to avoid intentional booking conflicts. Run `k6 run --summary-export=/private/tmp/serms-k6-summary.json scripts/load-k6.js` from the repository root with those environment variables set. Capture checks, p95 latency and error rate in the issue log. Never run the reserve load against production or commit the fixture file.

OWASP ZAP baseline is a separate read-only check after deployment issue #10. Its target and findings must be reviewed with the release owner; no deployment is implied by this runbook.
