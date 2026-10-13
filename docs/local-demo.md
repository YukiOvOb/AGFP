# Complete Local SERMS Demo

This local-first delivery implements Issue #5 and supports #6-#9. Cloud publication is a separate P1 task (#10). The runtime uses the integrated React frontend, Spring Boot JSON APIs and a dedicated PostgreSQL 18 cluster; it does not use mocked APIs or a static welcome page.

## Prerequisites and Startup

On Windows, install Java 17+, Node.js 24, the pnpm version declared in `frontend/package.json`, and PostgreSQL 18 binaries. The launcher accepts an existing Maven executable; otherwise it uses the app's Maven Wrapper. Run from the repository root:

```powershell
$env:JAVA_HOME = '<installed JDK directory>'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -PostgresBin '<PostgreSQL 18 bin directory>'
```

Default ports are PostgreSQL 55433, backend 8082 and web 5174, bound to `127.0.0.1`. Change them with `-DatabasePort`, `-BackendPort` and `-WebPort`. The launcher refuses to stop unrelated processes on occupied ports. It can recover only its own registered PostgreSQL data directory after an interrupted startup.

Open http://127.0.0.1:5174. Six local-only accounts cover borrower, second borrower, approver, custodian, maintainer and administrator: `borrower@example.test`, `borrower2@example.test`, `approver@example.test`, `custodian@example.test`, `maintainer@example.test`, and `admin@example.test`. Their generated password is in `.cache/local-demo/access.json`; do not commit or publish that file. Seeded equipment includes approval-required and directly reservable items, plus a retired item.

Existing Flyway migrations run unchanged on the dedicated `serms_local_demo` database. Demo provisioning is an explicitly invoked SQL script, not an automatic production migration or a public endpoint. It rejects another database name or unrelated users/equipment, and uses BCrypt hashes with existing identity/role semantics. Repeated startup keeps existing passwords, reservations, loans, cases and notifications.

The backend uses the existing `local` profile for HTTP cookies. Production Secure cookie settings are unchanged. Vite forwards `/api` to the selected backend using `SERMS_API_TARGET`; ordinary development retains its existing 8081 default.

Build logs, runtime JAR, database, access file and process state live under the ignored `.cache/local-demo/`. The runtime JAR is copied outside `app/target` so a regression build can clean its own artifacts while the demo continues running. Use `-SkipBuild` only when the existing artifacts correspond to the desired source. Use `-KeepAlive` in a terminal/automation runner that cleans child processes when its job exits.

## Verification and Shutdown

```powershell
node scripts/smoke-local.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-local-data.ps1
node scripts/check-local-browser.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-local.ps1
```

The smoke command creates actual demonstration transactions through the same-origin frontend proxy: six-role login, denied roles/CSRF, equipment search, reservation/overlap rejection, approval, physical checkout/return, technician assignment/repair and persisted notification delivery. It writes `.cache/local-demo/smoke-summary.json` without passwords or cookies. It consumes a real booking window; reruns choose another available approval-required demo item and never delete booking history to force a pass.

The stop command validates the runtime's checkout/data paths and process command lines before stopping registered frontend/backend processes and its PostgreSQL cluster. It preserves data and credentials. Restart with the original command. Keep the access file with the local data; if it is missing, the launcher refuses to replace credentials for an existing cluster.

These smoke checks complement existing frontend/backend/PostgreSQL suites. They do not establish every concurrency/overdue rule, remote CI, independent team-member review or cloud acceptance. Evidence for this delivery is recorded in [the local verification record](evidence/local-demo/verification.md).
