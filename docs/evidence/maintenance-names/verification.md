# Readable Names on Maintenance Pages — Verification (Issue #21, part 1)

Recorded on 2026-10-14 (Asia/Singapore) by an AI agent working for Liu Tongyao. Branch `feature/liu-tongyao-uc04-readable-names` from main `310dd50`; the checks ran on the uncommitted change set that the PR commits, with no other local changes. Environment: Windows 11, Java 25.0.4 building for Java 17, Node 24.20.0, pnpm 11.19.0, PostgreSQL 18.6, Playwright 1.63.0. Agent checks are supporting evidence, not a member check.

## Results

| Check | Command or method | Result |
| --- | --- | --- |
| Backend suite | `./mvnw -B test` in `app/`, external PostgreSQL 18.6 test database through `SERMS_APP_TEST_JDBC_URL` | Passed: 568 tests (563 before, 5 new) |
| UC04 PostgreSQL integration | `./mvnw -B -Puc04-postgres-it -Dit.test=MaintenancePostgresIT test-compile failsafe:integration-test failsafe:verify`, fresh `serms_uc04_test` migrated with V001-V004 by `psql` as in the CI workflow | Passed: 29 (1 new: the name directory reads the V002 `equipment.name`, `equipment.asset_tag` and `app_user.display_name` columns and omits unknown IDs) |
| Endpoint and authorization | `MaintenanceApiTest` (MockMvc with the real security configuration) | Case and history responses carry the names; a list resolves names in one equipment and one user lookup; unknown and unassigned names are `null`; denied reads (BORROWER, APPROVER, an unrelated MAINTAINER, non-operators on queues) never reach the name lookup |
| OpenAPI | `MaintenanceOpenApiTest`, then `OPENAPI_FILE=../app/target/openapi.json pnpm gen:api` and `pnpm check:api` | New fields are required and nullable strings; generated types match |
| Frontend | `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` in `frontend/` | Passed: 122 component tests (1 new for the ID fallback; name assertions added to the queue, case, history and lookup tests) |
| Mocked browser suite | `pnpm test:e2e` | Passed: 12 (the maintenance journey now asserts equipment, reporter and actor names) |
| Real demo | Playwright against the local demo (new backend jar, real PostgreSQL), custodian and maintainer accounts | Passed: see below |

## Real demo

The demo database had no `OPEN` case, so the live queue was empty; the queue card is covered by the component tests. The case page and the equipment lookup were checked on a resolved case linked to a loan.

| Observation | Value |
| --- | --- |
| Assigned cases (maintainer) | 15, each with equipment name and asset tag, reporter and technician |
| Case page fields still showing an identifier | Case ID and Loan ID only (neither has a name) |
| UUIDs in the history timeline | 0; actors shown as Alex Borrower, Morgan Custodian, Sam Maintainer |
| Mobile width 390 | No horizontal overflow |

## Screenshots

| File | Shows |
| --- | --- |
| `01-queue-custodian.png` | Custodian workspace; the queue is empty in the demo data. The report form and the lookup still take an equipment UUID as input (follow-up) |
| `02-case-custodian.png` | Case details with equipment, reporter and technician names and a named history |
| `03-equipment-lookup.png` | Equipment lookup headed by the equipment name, with cases and history |
| `04-assigned-maintainer.png` | Maintainer's assigned cases at 1440 wide |
| `05-assigned-mobile.png` | Same at 390 wide |

## Stale wording and history link

Recorded on 2026-10-14 on branch `feature/liu-tongyao-uc04-stale-wording` from main `76a38ea`, same environment as above, on the uncommitted change set that the PR commits.

| Check | Command or method | Result |
| --- | --- | --- |
| Frontend | `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` | Passed: 126 component tests (4 new) |
| Mocked browser suite | `pnpm test:e2e` | Passed: 12 on the final run. Of four runs, two failed: the first (1 test, while a Maven run competed for the machine; its trace was overwritten, cause unconfirmed) and the third (5 tests at the sign-in page, trace shows `net::ERR_NETWORK_CHANGED`, a Windows network change during the run). The second and fourth passed 12 |
| Backend | `./mvnw -B test -Dtest='Maintenance*Test,Jdbc*Test'` | Passed: 331 (the backend change is two comments) |
| Real demo | Playwright against the local demo; the frontend dev server serves this checkout | Passed: as custodian, the equipment name on a case opens `/maintenance?history=<id>`; the lookup is pre-filled, shows the item's 2 cases and its history, and is scrolled to the top of the view. As maintainer, the case page has no history link |

| File | Shows |
| --- | --- |
| `06-history-link-custodian.png` | The lookup after following the equipment link from a case |
