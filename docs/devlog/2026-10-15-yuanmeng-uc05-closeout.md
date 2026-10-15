# UC05 integration closeout (#12, #19, #7)

Human closeout owner: Liu Tongyao (@cpg25ncu). Feature author/owner: Wang Yuanmeng (@ezmoneysniper0701). Codex is the sole writer in `C:/Users/14824/.codex/worktrees/yuanmeng-closeout/SWE5006_Group_Project`, branch `codex/yuanmeng-uc05-closeout`.

## Scope and inputs

The owner requested review and completion of Wang Yuanmeng's updated UC05 and DevSecOps deliveries, including necessary GitHub comments, and transferred the outstanding overdue-projection contract-confirmation task to Liu for delegated completion. This record covers UC05; it does not take over unrelated implementation tasks. The [responsibility decision](../collaboration/decisions.md#uc05-projection-contract-confirmation-2026-10-15) and [contract confirmation comment](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/12#issuecomment-6184064779) record that narrow confirmation.

Reviewed author HEAD: `0d12fc64b4306074ecb25930d9b1f30c99273d42`. Initial integration base: `b72ee5bd2c9d55ac6d9a526919ed69076b6f19d3`; subsequently fetched base: `main` `2e9eda35973f0929c19fa3e613688d902a361f9d`. Both are ancestors of the tested integration HEAD `b0a6c01c7f18b75763e879f4d47b917c7d7b03cd`. Tests used that committed source; subsequent closeout edits are documentation only. V001-V006 were applied without editing migration files. Regenerating API types from this combination produced no further difference.

## Review and changes

Rechecked current database authorization on the overdue projection, checkout cancellation of pending collection reminders, the UC03 native-database fallback, and real ReservationService/audit/notification rollback regression. The projection's read scope, strict overdue boundary, response fields, paging/sorting and unchanged UC03 write/lock semantics were confirmed under the owner's reassigned task. No Zhou Fanhao review or personal human test is claimed.

Merged current `main` into the delivery in an isolated worktree. Rules read: `AGENTS.md`, `.github/CONTRIBUTING.md`, `docs/collaboration/{ownership,agent-workflow,decisions}.md`, `docs/testing.md`, relevant `docs/tech-stack.md` sections, `docs/contracts/{README,checkout-and-return,auth-and-approval,equipment-and-reservation}.md`, the notification contract, `docs/devlog/README.md`, and the `serms-review`, `serms-integrate`, `serms-verify`, `serms-pr`, `serms-worktree` skills. The baseline rule changes were checked on resume; the new main range did not change the collaboration/contract-confirmation rules.

## Problems and verification

| Check | Result and input |
| --- | --- |
| Backend `app/mvnw.cmd -o -B -Puc03-postgres-it -Dit.test=LoanPostgresIT,Uc05PostgresIT verify` | Passed at `b0a6c01`: 589 ordinary tests, 20 UC03 and 10 UC05 PostgreSQL integration tests, zero failures/errors/skips |
| PostgreSQL isolation correction | First combined run shared the UC03 fallback database between the two suites. A pre-existing UC03 overdue fixture changed the global-list first row, causing one UC05 assertion failure. Rerun used separate fresh app/UC03/UC05 databases; both suites passed. The native UC03 fallback itself had already passed the standalone UC05 run at author HEAD |
| API/frontend | `pnpm gen:api`, `pnpm check:api`, lint, typecheck, 133 tests and production build passed; generated file unchanged |
| Existing Playwright suite | 12 desktop/mobile cases passed against a dedicated Vite port; these cases use mocked APIs |
| Existing real local smoke | `node scripts/smoke-local.mjs` passed: six role accounts, CSRF, reservation/approval/checkout/return, maintenance, delivered notices and logout against real PostgreSQL |
| Real Chromium UC05 acceptance | Passed without route mocks: 25 valid ACTIVE Loan fixtures became overdue naturally; 20/5 paging, duration asc/desc, reset to first page, persisted names/time, CUSTODIAN/ADMIN access, BORROWER 403, anonymous 401, delivered inbox, read count 29 to 28, second-borrower inbox isolation and foreign read mutation 404 |
| Documentation | `node scripts/check-collaboration.mjs` and `git diff --check` passed |

Environment: Windows, PostgreSQL 18.6, JDK 25.0.4 compiling release 17, Maven 3.9.11, Node 24.20.0, pnpm 11.19.0. The test cluster used a random loopback port and fresh guarded database names; its server was stopped in `finally`. Live acceptance used only this worktree's dedicated local demo (database/backend/web ports 55567/18067/15167), with randomly generated local credentials excluded from Git.

Private evidence retained under this worktree's `.cache/yuanmeng-closeout/`: `backend-verify.log`, the first failed shared-database attempt, `frontend-*.log`, `frontend-results.json`, `live-smoke.log`, `live-browser-summary.json`, `live-browser.log`, `live-overdue-fixture.sql`, `live-overdue.png`, `live-inbox.png`; existing smoke summary under `.cache/local-demo/`. The first browser fixture attempt had a PL/pgSQL variable/table-name ambiguity; its DO transaction rolled back, the fixture variable was renamed, and the complete run passed. Every fixture constraint and trigger remained enabled. To reproduce, start the isolated demo per `docs/local-demo.md`, run its smoke, create 25 separate equipment/reservation/Loan fixtures with due times 11-35 seconds ahead, let them expire naturally, and exercise the recorded API/UI cases. Do not run fixtures against a shared database.

## Acceptance boundaries and authorship

Latest-HEAD hosted CI and actual merge are recorded on PR #12 after this commit. This record is local verification and delegated technical contract confirmation. Independent member checks, deployment under #10 and team Done remain separate; no deployment, human-operated check, effort hours or approving review is invented. Wang retains feature authorship and UC05 responsibility; Codex performed this integration and verification under Liu's instruction.
