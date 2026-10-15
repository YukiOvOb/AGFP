# Zhou Fanhao Local Verification Record

## Default PostgreSQL Finalization Verification

Clean rerun of `scripts/test-zhoufanhao.ps1 -PostgresBin 'C:/Program Files/PostgreSQL/18/bin'` succeeded: 27 database, 536 regression, 20 default PostgreSQL integration tests; separately 12 pinned notification tests. UC03 no longer sets ActiveProfiles and verifies default driver, unified Flyway, enabled approval, real authentication/handover.

Production JAR excludes MySQL Connector, Flyway MySQL, H2, test migrations; only V001–V004. Four migrated Git blobs match originals, unchanged by relocation. All database image configuration uses PostgreSQL 18/new PG18 development volume. OpenAPI matches existing frontend types.

Latest: [default-postgres-summary.json](evidence/uc03/default-postgres-summary.json). Frontend behavior unchanged; reuse 79 previously verified tests below. Remote review, CI, deployment not yet run.

## Previous Business Implementation Verification

Date: 2026-10-07, user's complete local AGFP backup. Isolated temporary PostgreSQL, no development/production connection. Actual environment: PostgreSQL 18.6, JDK 25 (Java release 17), Node 24.21.0, pnpm 11.19.0.

| Check | Result | Evidence |
| --- | --- | --- |
| V002 regression | 27; 0 failures/errors/skips | `database/target/surefire-reports` |
| Historical-decision V004 upgrade | Passed: IDs/reasons retained, four bigint versions, immutable triggers restored | `v4-upgrade-fixture.sql`, `v4-upgrade-assertions.sql` |
| Empty-database app Flyway | V001→V002→V003→V004 passed | Startup logs, schema/Identity tests |
| App regression summary | 536; 0 failures/errors/skips; full run plus targeted rechecks | `app/target/surefire-reports` |
| Full UC03 PostgreSQL integration | 20; 0 failures/errors/skips | `app/target/failsafe-reports` |
| React | 79, all 6 files passed | Vitest logs |
| TypeScript/Vite production | Passed | `pnpm build` |
| ESLint | Passed | `pnpm lint` |
| Generated OpenAPI consistency | Passed | `pnpm check:api`, `app/target/openapi.json` |
| Whitespace | Passed | `git diff --check` |

Compact committed summary: [test-summary.json](evidence/uc03/test-summary.json). Raw logs in ignored `.cache/`, JUnit in target. Summaries contain names/counts, not environment properties, full stdout, or local account details.

Real UC03 coverage: atomic checkout/normal return; damaged case/audit; invalid identity/windows/states/versions; disabled borrowers/revoked custodians; maintenance/retirement precedence; late-damaged; concurrent checkout/return; synchronous failure rollback; ownership; CSRF/anonymous; int64 rejects floats/strings/overflow; approval persistence/notifications into checkout; returned-loan Guard; real PostgreSQL login/UUID principal; actual OpenAPI.

Rechecks fixed write-lock misuse in read queues, PostgreSQL 18 RESTRICT error-code expectations, Integer version fixture data in existing maintenance tests. Full regression preceded targeted snapshot/JDBC/state rechecks; latest JUnit summary all passed. The 20 UC03 tests separately passed on the complete app/real PostgreSQL.

Reproduce with `scripts/test-zhoufanhao.ps1` (Docker or `-PostgresBin`). New workflow prepared but not executed during suspension. No claim that remote review/security/coverage/deployment/Smoke Test/Sprint Review/actual hours are complete; add them via [Member checklist](zhoufanhao-handoffs.md).
