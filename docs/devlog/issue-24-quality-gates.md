# Issue 24: business coverage and real workflow evidence

## Scope and ownership

- Human owner: Zhang Hanming; implementation assistance: Codex.
- Authorization: proceed locally without first messaging teammates. No Issue comments, external messages, merge, deployment or human review is authorized by this record.
- Worktree: `C:/Users/LENOVO/Documents/ChatGPT/Dev/SERMS-hanming-quality-20261015`.
- Branch: `codex/hanming-uc02-quality-20261015`.
- Starting HEAD and fetched main: `2e9eda35973f0929c19fa3e613688d902a361f9d`.
- Allowed paths: coverage configuration in `app/pom.xml`, approval verification and directly demonstrated defect fixes, isolated test runners, browser verification, testing documentation and this evidence record.
- Excluded: schema migrations, shared auth/API contract changes, other owners' branches, admin implementation, deployment and production data.
- Refs #8 and #24. This is a partial delivery, not closure of either Issue.

## Rules read

`AGENTS.md`, `.github/CONTRIBUTING.md`, `docs/tech-stack.md`, `docs/contracts/README.md`, `docs/contracts/auth-and-approval.md`, `docs/collaboration/ownership.md`, `docs/collaboration/agent-workflow.md`, `docs/collaboration/decisions.md`, `docs/testing.md`, and repository skills `serms-task`, `serms-worktree`, `serms-verify`, `serms-pr`.

## Intended verification

Keep the full JaCoCo report and additionally report/check the combined `service` and `domain` production classes. Require at least 70% line coverage and a nonempty scope. Demonstrate both a passing normal build and a failing zero-coverage exercise without committing broken settings or deleting tests.

Approval PostgreSQL verification is independent of mocked frontend tests. Real browser acceptance, admin pages still absent from main, remote CI, member acceptance and deployment must be reported separately.

## Results

Verified inputs: starting HEAD above plus this branch's listed source/config/test changes; no production Java changed after the successful backend run. Java 25.0.4 compiled with release 17, Maven Wrapper 3.9.11, PostgreSQL 18.6 native binaries, Node 24.19.0, pnpm 11.25.0 (provided runtime; package declares 11.19.0), Chromium via Playwright 1.63.0, Windows PowerShell/pwsh.

| Check | Actual local result / evidence |
| --- | --- |
| `pwsh -File scripts/test-uc02.ps1 -PostgresBin '<PG18 bin>' -IncludeLoanIntegration` | Passed: 590 regression + 30 UC02 PostgreSQL + 20 UC03 PostgreSQL, zero failures/errors/skips. `app/target/surefire-reports/`, `app/target/failsafe-reports/`, ignored `.cache/uc02-verify.log` |
| Combined service/domain JaCoCo | Passed: 1,145 covered / 164 missed lines, 87.47%, 131 analyzed classes. `app/target/site/jacoco-business/`. Complete all-code report retained separately |
| `pwsh -File scripts/check-coverage-gate.ps1` | Passed: zero-execution data causes the actual Maven check to fail specifically at 0.00 vs 0.70; real coverage restored byte-for-byte and the same check passes. `.cache/coverage-gate/negative.log` |
| Frontend `pnpm lint`, `typecheck`, `test`, `build` | Passed: 130 component tests. Existing nonfatal chart-size, dependency annotation and bundle-size warnings remain. Lint/typecheck rerun after the E2E navigation edit |
| `OPENAPI_FILE=../app/target/openapi.json pnpm check:api` (PowerShell equivalent) | Passed; no generated frontend schema change needed |
| `pnpm test:e2e` | Passed: 12 desktop/mobile mocked browser contract cases, zero retries. Earlier run failed one landing-page-dependent assertion; explicit menu navigation fixes that fixture |
| Browser fixture guard checks | Passed: new fixture SQL rejects wrong database and invalid run identifier, with psql exit 3. No migration/table definition changes |
| `node scripts/check-collaboration.mjs`, `git diff --check`, browser script syntax | Passed. Documentation checker: 19 navigation documents / 7 skills |
| Initial hosted CI on PR #46, source `03bc718` | Approval PostgreSQL acceptance passed on Ubuntu/Java 17/Docker, including the negative coverage exercise. The other eight non-security workflows also passed. Trivy and Dependency Review passed; gitleaks rejected two environment-variable-name strings in the new runner. This initial run is not an all-green or final-HEAD claim |
| `node scripts/check-uc02-browser.mjs` | Passed: 26 actual desktop/mobile checks, five real role sessions per viewport. Separate-approver approve/reject, required rejection/damage notes, slot release, persisted delivered/read notification, custodian checkout/damaged return, linked maintenance assignment/diagnosis/repair/resolution, equipment restoration and admin reports. No uncaught page errors or document overflow. `.cache/local-demo/uc02-browser-summary.json` points to screenshots and local-only traces |

Real browser setup reuses `scripts/start-local.ps1` on worktree-only ports 55437/8097/5187. The new opt-in fixture script provisions two fresh, UUID-identified `DEMO-UC02-<run>-<device>` items per run in the explicitly checked local demo database. Existing history is not truncated or rewritten, failed unissued fixtures use ordinary authenticated cancellation, quota is respected, and waits use real time. Delivery polling allows for the existing worker's 30-second schedule. No production configuration is relaxed.

Main was rechecked with `git ls-remote origin refs/heads/main` after implementation and remained `2e9eda35973f0929c19fa3e613688d902a361f9d`. Hosted CI, branch protection and independent human acceptance are not established by any local result. Only this worktree's registered local services are stopped after verification; database/history/access files, coverage and test artifacts are preserved under ignored runtime/build directories.

## Remaining scope

This delivers the coverage/UC02 regression slice only. Full #24 closeout still needs future admin users/audit (#18), equipment management (#17), overdue UI/scanner (#19), and the final combined live workflow after those features land. Browser self-approval with a dual-role account remains separate from the passing real PostgreSQL/API self-decision tests. No Issue is closed and no team Done, merge or deployment is claimed.

## Publication and CI follow-up — 2026-10-15

The user subsequently authorized pushing this branch, creating a PR and inspecting/fixing CI. Draft [PR #46](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/46) targets main; no merge, deployment or separate Issue comments were authorized.

The GitHub connector could not create a PR (integration HTTP 403). The existing local Git login was verified as `Hanming1008` and used to create the draft; no credential was printed or committed.

Initial gitleaks 8.24.3 findings were `generic-api-key` matches on consecutive environment-variable **names**, not stored credentials. Construct the same nine names from prefix/suffix lists instead. Scanner rules, thresholds, workflow permissions and the existing ignore file are unchanged. The official matching scanner was downloaded only into ignored cache and its published checksum verified. The proposed feature commit is rescanned with the exact first-parent commit range used by CI, excluding ignored local runtime files.

The runner was reverified after the equivalent environment-list rewrite: 590 regression + 30 UC02 PostgreSQL + 20 UC03 PostgreSQL cases passed, zero failures/errors/skips (`.cache/uc02-verify-ci-fix.log`). Its original committed version remains in local Git recovery history/backup; only this newly created draft feature branch is updated using an explicit expected-old-SHA lease, never main. Final-HEAD hosted results are reported in the PR rather than inferred from the initial successful jobs.
