# UC05 completion (#19)

Owner: Wang Yuanmeng (@ezmoneysniper0701) · Requirement: Proposal 5.5 and 5.7 · Branch: `feature/wang-yuanmeng-uc05-acceptance` · Baseline: latest `main` merged on 2026-10-14

## 2026-10-14 Spring Boot 4 and UC05 completion

### What was built
PR #12 merges the Spring Boot 4 baseline, updates the UC05 PostgreSQL test imports, adds reservation submitted/cancelled notifications, a configurable confirmed-reservation collection reminder, equipment-name/Singapore-time text, and a role-restricted overdue API and page. The page uses the shared `PageHeader`, `Card`, `DataTable` and `Select` components.

### Design notes
Synchronous event listeners join the business transaction and save idempotent `NotificationRequest` rows. The worker performs in-app delivery afterward. The collection scanner rechecks confirmed and uncollected state under a reservation row lock and uses a stable business key. Cancellation changes an unsent collection request to `CANCELLED`. The overdue view is a read-only projection from persisted Loan and Reservation data; it adds no Loan state. `NotificationChannel` is not implemented, so the report must not claim a channel Adapter pattern. Email and automatic expiry cancellation remain out of scope under D-009.

### Problems and fixes
| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| UC05 Testcontainers test did not compile on Boot 4 | MVC test and PostgreSQLContainer packages moved | Updated imports and container construction | Maven test compilation and PostgreSQL integration |
| H2 approval contract tests failed after display-name enrichment | The temporary H2 fixture has no SERMS business schema | Keep event-only text for the test profile; resolve persisted names in the PostgreSQL profile | Approval transaction tests and PostgreSQL integration |
| Frontend type check rejected overdue row values | Generated OpenAPI fields are optional in TypeScript | Handle optional values in the page | `pnpm typecheck` |
| Navigation test expected the overdue menu to remain hidden | The new route activates the reserved entry | Updated the expectation | Frontend test run |

### Verification
Local runs on macOS with Java 25 (`--release 17`), PostgreSQL 18.6 via Testcontainers, Spring Boot 4.0.8 and Node 24. `./app/mvnw -B -f app/pom.xml -Puc03-postgres-it -Dit.test=Uc05PostgresIT verify` passed: 536 existing tests and 7 PostgreSQL UC05 tests. `OPENAPI_FILE=../app/target/openapi.json pnpm check:api`, `pnpm typecheck`, `pnpm lint`, `pnpm test` (114 tests), and `pnpm build` passed. Results were recorded against the worktree before the UC05 completion commit; CI must confirm the pushed HEAD. The PostgreSQL test exercises real persistence; H2 approval tests are contract checks only.

### Deviations
The channel abstraction proposed in technology documentation is not delivered; in-app notification delivery remains concrete. The overdue API is under UC05 at `/api/v1/overdue`, leaving UC03 loan endpoints unchanged. This cross-module query contract needs the UC03 owner's review before merge.

### PR review follow-up (2026-10-14)
The latest `main` and the prepared PR #34 synchronization were merged locally into #12 without textual conflicts. Review exposed three defects: an old CUSTODIAN/ADMIN session could read global overdue data after its database role was removed or account disabled; an unsent collection reminder could survive checkout; and UC05 PostgreSQL IT always started Docker even when the repository's external test database was configured. The overdue service now rechecks active database membership, checkout cancels unsent collection requests in the same transaction, and the IT accepts a guarded external UC05/UC03 test database before falling back to Testcontainers. PostgreSQL integration rerun: 10 UC05 tests, zero failures; full `./app/mvnw -B -f app/pom.xml verify` on 2026-10-15 passed. The targeted integration includes real reservation submit/cancel/audit/notice rollback, stale-role denial, and checkout cancellation. The page test additionally checks server paging and sort reset. Frontend 129/129 tests, production build and generated API check passed on the revised baseline. A live PostgreSQL-to-Chromium inbox check verified delivery, unread 4→3 after marking read, and isolation between two borrower accounts; reproduction and findings are in `docs/evidence/uc05/local-postgres-browser-2026-10-15.md`. The pushed review commit passed all 12 hosted checks; final addendum CI remains separate.

### Follow-ups
Review the UC03 overdue projection contract, run latest-HEAD CI, obtain member review, merge, and separately verify deployment/acceptance. Issue #25 DevSecOps is a separate later task.

### Authorship
Code and this log were prepared with Codex assistance under Wang Yuanmeng's task request. No human review, effort hours or approval is claimed.
