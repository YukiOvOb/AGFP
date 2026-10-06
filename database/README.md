# SERMS Data Module

The notification application can integrate through optional V003; see the [notification adapter](../integration/notifications/README.md). This page describes the V002 foundation. After integration, notification_request / notification_attempt are authoritative, and the V2 table remains as read-only history.

Owner: Zhou Fanhao. Java 17/JDBC + PostgreSQL 17; current schema: V002. The original SERMS ER diagram is implemented as ten business tables covering users, multiple roles, equipment, reservations, approval decisions, loans, maintenance cases, notifications and audits. The Java repository implements search, booking and cancellation by the requester; the other tables provide persistence for future business services.

See the [domain model](../docs/sprint1-domain.md) for design and sources, and the [V002 alignment record](../docs/serms-database-alignment.md) for changes and test results.

## One-command Verification

Requires Docker Desktop, JDK 17+ and Maven. Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1
```

The script creates temporary PostgreSQL with a random local port and password. It checks empty-database V001-to-V002 initialization and V1-to-V2 upgrades containing legacy roles, disabled accounts, old states and reservations. After all 27 integration tests, it removes the container and anonymous volumes and restores environment variables.

Reports: `database/target/surefire-reports/`. Build artifact: `database/target/serms-data-0.1.0-SNAPSHOT.jar`. Tests use real PostgreSQL, not H2; missing dedicated test database configuration causes failure rather than a skip.

Equivalent Linux/CI procedure (PGHOST/PGUSER/PGPASSWORD must point to a dedicated test server):

```sh
export PGDATABASE=serms_test
psql -v ON_ERROR_STOP=1 -f database/src/main/resources/db/migration/V001__sprint1.sql
psql -v ON_ERROR_STOP=1 -f database/src/test/resources/v1-upgrade-fixture.sql
psql -v ON_ERROR_STOP=1 -f database/src/main/resources/db/migration/V002__align_serms_model.sql
createdb serms_empty
for migration in database/src/main/resources/db/migration/V*.sql; do
  psql -d serms_empty -v ON_ERROR_STOP=1 -f "$migration"
done
export SERMS_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/serms_test
export SERMS_TEST_DB_USER="$PGUSER"
export SERMS_TEST_DB_PASSWORD="$PGPASSWORD"
mvn -B -f database/pom.xml clean verify
```

`v1-upgrade-fixture.sql` is for tests only and must not be used for development or production initialization. JUnit requires the database name serms_test. Run tests only on disposable instances so leftover fixtures cannot be mistaken for business data.

## Development Initialization and Existing Database Upgrades

Set the local password in the current PowerShell session:

```powershell
$env:SERMS_DB_PASSWORD = '<your-local-password>'
docker compose -f database/compose.yaml up -d --wait
```

The default connection is `jdbc:postgresql://127.0.0.1:55432/serms`, with username serms; set SERMS_DB_PORT to change the port. On first startup, an empty volume applies V001 and V002 in filename order. Stop Compose with `docker compose -f database/compose.yaml down`, which preserves the data volume.

Existing V001 volumes do not automatically run new SQL. Back up the database and stop business writes, then apply only V002:

```powershell
docker compose -f database/compose.yaml cp database/src/main/resources/db/migration/V002__align_serms_model.sql db:/tmp/V002.sql
docker compose -f database/compose.yaml exec -T db psql -U serms -d serms -v ON_ERROR_STOP=1 -f /tmp/V002.sql
docker compose -f database/compose.yaml exec -T db psql -U serms -d serms -c "SELECT version, description FROM serms.schema_version ORDER BY version"
```

Run all commands from the repository root. Do not rerun V001, delete volumes or edit applied migrations. V002 runs in one transaction and rolls back on any failure; manually review historical FULFILLED overlaps. Reapplying V002 rejects the migrated structure without overwriting data. Production still requires separate migration and least-privilege application accounts. Historical data for new tables is not generated automatically.

V002 renames columns and changes Java record accessors, making it incompatible with the V001 module. Migrate the database and release the matching Java code in the same maintenance window. The root website remains a static welcome page without a development database connection.

## Java API

Inject a `DataSource` to construct `ReservationRepository`. New connections must use default auto-commit mode. The repository manages its own transactions and cannot be nested inside an uncommitted caller transaction.

| Method | Behavior |
| --- | --- |
| findAvailable(query,start,end) | Literal substring search over equipment name, asset tag and category; up to 100 rows ordered by asset tag; checks active loans, maintenance and interval conflicts |
| book(actor,equipment,start,end) | Generates a request correlation ID and leaves purpose unset |
| book(actor,equipment,start,end,purpose,requestId) | Chooses PENDING_APPROVAL/CONFIRMED from equipment settings; writes the reservation and success audit atomically |
| cancel(actor,reservation) | Only the requester's valid reservation; returns false if missing, owned by another user or already ended |
| cancel(actor,reservation,requestId) | Same behavior; requires an ACTIVE account; commits cancellation and its audit atomically |

The actor parameter must come from a trusted authenticated session; the service validates role authorization. This repository does not expose cancellation on behalf of another user by an administrator. The upper layer must independently audit failed operations after rollback. requestId is a tracing identifier, not a general idempotency key; do not retry unconditionally after a network failure when the commit outcome is unknown.

Reservation properties are reservationId, requesterId, equipmentId, startAt, endAt, status, purpose and version. Equipment uses equipmentId and version; User uses accountStatus and an immutable roles set, without a password hash.

## Business Rules and Error Conventions

- Intervals are [start,end), with at most microsecond precision; current intervals that have not ended are supported.
- PENDING_APPROVAL, CONFIRMED and FULFILLED occupy their original slots, including after an early return.
- ON_LOAN equipment can be reserved only when its current loan is not overdue, the new reservation starts at or after the due time, and there are no active maintenance cases or reservation conflicts.
- See the [domain model](../docs/sprint1-domain.md) for database states and multiple-role definitions.
- SQLSTATE 23P01: time conflict (can map to HTTP 409); 23503: invalid reference; 23514: state/field/business constraint violation; 23505: uniqueness conflict.
- Java IllegalArgumentException/NullPointerException: invalid input. Retry the entire transaction a bounded number of times for 40P01/40001. For other SQL errors, the service logs requestId and returns a generic error without exposing SQL or credentials.
- All related write services lock Equipment first, then process Reservation, Loan and MaintenanceCase in order. Version checks require a WHERE version condition and an affected-row count check.
- Database constraints do not implement RBAC, on-site handover checks, equipment state recomputation or complete checkout/return transactions. See the domain document for responsibility boundaries.

CI workflow: [database.yml](../.github/workflows/database.yml). References: [PostgreSQL range constraints](https://www.postgresql.org/docs/17/rangetypes.html), [pgJDBC 42.7.13](https://jdbc.postgresql.org/changelogs/2026-07-06-42.7.13-release/).
