# Complete SERMS PostgreSQL Runtime and Migration

Default/local configurations use PostgreSQL 18 with approval enabled. The sole production migration source is `app/src/main/resources/db/migration`, V001→V004. Production dependencies contain neither MySQL nor H2; legacy fast notification regression H2 dependencies/migrations are test-scoped. `serms-postgres` remains a compatibility alias.

## New Database

Start local PostgreSQL 18 with `docker compose -f compose.dev.yaml up -d`. Compose starts only the database; backend Flyway avoids duplicate initialization/history management. Compose reads `.env`; direct Java startup still needs process environment variables. A new PG18 volume avoids reusing older data directories.

Prepare an empty database and dedicated migration account, set variables, start the application. Disable Secure Cookie explicitly for development HTTP; retain it for test/production HTTPS.

```powershell
$env:SERMS_DB_URL='jdbc:postgresql://127.0.0.1:55432/serms?currentSchema=serms,public'
$env:SERMS_DB_USER='serms'
$env:SERMS_DB_PASSWORD='<local-account-password>'
mvn -B -f app/pom.xml package -DskipTests
java -jar app/target/serms-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Flyway records `public.serms_flyway_history` and creates business tables in `serms`: V001 foundation, V002 model, V003 notification bridge, V004 decisions/versions. Migration accounts execute DDL; use least-privilege runtime accounts afterward. Test fixtures are not demo/production seeds.

## Existing Database

Stop writes, back up, check `serms.schema_version`, public Flyway history, notifications, historical decision codes. V004 preserves old checksums, decision IDs/times and atomically changes enums/versions. Investigate real data when migration fails; never delete history to bypass constraints.

If `serms_flyway_history` matches repository migrations, upgrade directly with checksum validation. Do not recreate it.

For manually psql-applied V001/V002 without Flyway history, explicitly baseline to 2 only after backup/schema verification; the app then applies V003/V004. The notification integration POM uses the same public history table:

```powershell
mvn -B -f integration/notifications/pom.xml flyway:baseline -Dflyway.baselineVersion=2
```

If V003 was also manual, verify notification_principal, reminder_id, notification_request/attempt, and legacy migration, then explicitly baseline to 3. Table existence alone does not establish version. Keep `spring.flyway.baseline-on-migrate=false` to avoid adopting unknown databases automatically.

MySQL/H2 notification databases cannot be baselined as PostgreSQL SERMS. Migrate history through V003's explicit conversion, without dual writes. The database owner reviews existing business data item by item.

## Data Conventions

| Convention | Current integration |
| --- | --- |
| Identity | `serms.app_user`, multiple roles, ACTIVE/DISABLED; notification_principal defaults to user_id string |
| Approval | APPROVED / REJECTED, immutable final decisions, rejection reasons |
| Version | PostgreSQL BIGINT, Java long, trigger increments; expected-version checks on every update |
| Loan | UUID primary key; separate bigint reminder_id only for notifications |
| Overdue | ACTIVE now or RETURNED returned_at versus due_at; no stored extra state |
| Reservation | [startAt,endAt), FULFILLED retains original slot |
| Equipment | RETIRED > active maintenance > active loan > AVAILABLE |

Constraints/triggers do not replace sessions, RBAC, on-site checks, or end-to-end transactions. UC03 Service performs these checks; other modules must not merely insert Loan records and claim handover.
