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

## Initial delivery results

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

This delivers the coverage/UC02 regression slice only. Full #24 closeout still needs future admin users/audit (#18), equipment management (#17), overdue UI/scanner (#19), and the final combined live workflow after those features land. Dual-role browser self-decision acceptance was completed in the follow-up below. No Issue is closed and no team Done, merge or deployment is claimed.

## Publication and CI follow-up — 2026-10-15

The user subsequently authorized pushing this branch, creating a PR and inspecting/fixing CI. Draft [PR #46](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/46) targets main; no merge, deployment or separate Issue comments were authorized.

The GitHub connector could not create a PR (integration HTTP 403). The existing local Git login was verified as `Hanming1008` and used to create the draft; no credential was printed or committed.

Initial gitleaks 8.24.3 findings were `generic-api-key` matches on consecutive environment-variable **names**, not stored credentials. Construct the same nine names from prefix/suffix lists instead. Scanner rules, thresholds, workflow permissions and the existing ignore file are unchanged. The official matching scanner was downloaded only into ignored cache and its published checksum verified. The proposed feature commit is rescanned with the exact first-parent commit range used by CI, excluding ignored local runtime files.

The runner was reverified after the equivalent environment-list rewrite: 590 regression + 30 UC02 PostgreSQL + 20 UC03 PostgreSQL cases passed, zero failures/errors/skips (`.cache/uc02-verify-ci-fix.log`). Its original committed version remains in local Git recovery history/backup; only this newly created draft feature branch is updated using an explicit expected-old-SHA lease, never main. Final-HEAD hosted results are reported in the PR rather than inferred from the initial successful jobs.

## Dual-role/browser evidence follow-up — 2026-10-15

Starting HEAD `f7bf0f11b82a8d429c19e5c95f8b243767ac8d77` subsequently passed all ten hosted workflows; the PR links those actual runs. The user then authorized continuing the real dual-role self-decision acceptance. The new test/SQL/evidence-only changes are recorded in [the UC02 follow-up](issue-8-approval-postgres-browser.md#2026-10-15-dual-role-browser-self-decision-follow-up); main still matches `2e9eda35973f0929c19fa3e613688d902a361f9d`.

The expanded live command passed **37 checks**, including both forged decision choices at both viewport widths, zero persisted side effects, an independent approver's successful decision, and temporary-role restoration. A fresh real login confirmed BORROWER-only afterwards. [Reviewed public-safe evidence](../evidence/uc02-dual-role-20261015/README.md) binds the actual run to script/SQL hashes. Four fresh equipment items now isolate normal/self scenarios per viewport; no historical data or production role/API/schema is changed.

Frontend lint/typecheck/build, 130 components and 12 existing mocked E2E checks passed. SQL wrong-database/invalid-ID guards, Node syntax, diff checks and collaboration documentation passed. Application production code is unchanged. The new commit's hosted CI must run again; the previous green commit is not current-HEAD CI. Do not close #8/#24 or claim member/deployment acceptance.

### Docker initialization readiness race

The approval workflow for `62cd4bb` failed before Maven/tests: `createdb` reported “terminating connection due to administrator command”. The readiness loop used the default Unix socket, which can accept connections on the Docker image's temporary initialization server before its intentional shutdown/restart. This matches the official [PostgreSQL image entrypoint](https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh), whose temporary server uses an empty TCP listen-address list.

Change the readiness probe to explicit `127.0.0.1:5432` / `postgres`, so it waits for the final TCP listener. Keep fail-closed startup, database isolation, all tests and thresholds unchanged. This is a test-runtime fix, not an approval-product change. Local Windows has no Docker; the native PostgreSQL path and live browser results are separate evidence. The exact Docker path must be reverified in hosted CI rather than claiming a local Docker pass or merely rerunning an unchanged flaky probe.

## Current-page permission matrix and scope audit — 2026-10-15

### Scope and sources

Owner Hanming authorized continuing the remaining current-page acceptance and evidence checklist. Sole writer: Codex, same worktree/branch. Starting HEAD `6bad66922963369ecb28ba20959dc2a66dd27fe7`; freshly fetched main remains `2e9eda35973f0929c19fa3e613688d902a361f9d`. All ten hosted workflows on the starting HEAD passed, including the corrected Docker readiness, 590 regression + 30 UC02 + 20 UC03 and the actual negative coverage exercise; links are in PR #46. These historical runs do not certify the next commit.

Read AGENTS, CONTRIBUTING, the three task/verification/PR skills, contracts/auth-and-approval, contract index, technology/testing/ownership/decisions, development-log and agent-workflow rules, current Issues #8/#18/#24/#27/#41, actual App/navigation and service permissions. Allowed additions: explicit matrix, isolated read-only SQL/browser verification, minimal demonstrated route-guard fix/tests, frontend CI inventory check and evidence. Excluded: admin implementation, backend authentication/session or contract changes, migrations, external messages, main merge/deployment.

### Built and design

- An independent expected-policy manifest covers all 12 current application routes and 22 real read API families. It does not learn expected permission from the rendered menu. Node tests and the live runner reject route inventory drift; new pages require review/extension.
- Run every page/API with anonymous, all five single-role accounts and a second borrower, at desktop/mobile widths. Check actual content/deny/login states, valid-CSRF denied mutations, owner-scoped 404s, failed-CSRF logout retaining its session, and successful UI logout invalidating it.
- Guarded READ ONLY SQL obtains an already completed real UC02 handover/repair fixture. Before/after fingerprints prove business records, successful audits, notification read state and roles stay unchanged. No new schema, production accounts, role elevation or reset is needed.
- Reuse the existing RequireRole component with an explicit any-of role list for `/loans`; no new permission model/design pattern. ADMIN remains non-privileged without a separately assigned role.
- Added a [responsibility/evidence checklist](../evidence/hanming-delivery-status-20261015.md). Scope audit corrected the earlier conversational omission: #18 remains Hanming's admin/user/audit task; only bootstrap moved to Liu's #41. It is not counted as another member's remaining feature or declared complete.

### Problems and fixes

| Symptom | Root cause | Fix | Verification |
| --- | --- | --- | --- |
| Some local ports could not be bound (8097, later 8107) | Launcher bind check failed; process ownership was not established | Use other dedicated ports; do not stop or claim ownership of an unknown listener | First run used 55447/8107/5197; evidence-format rerun used 55457/8117/5207; isolation guards passed |
| Initial snapshot SQL fails before browser launch | Test used `approval_decision_id`, whereas the merged schema uses `approval_id` | Read existing DDL and correct only the test query | Read-only query and complete matrix pass; no schema rewrite |
| APPROVER opening `/loans` renders My loans and a 403 API banner | Menu and backend roles are correct, but the page had only a signed-in guard | Wrap loan route in explicit BORROWER-or-CUSTODIAN guard | Baseline live failure reproduced; corrected matrix passes; three negative component cases make zero loan reads; ADMIN+BORROWER remains allowed |
| Pre-commit Gitleaks flags a verification hash | The JSON used a filename containing `access` as a key immediately followed by a high-entropy SHA-256 value | Store provenance as explicit `{path, sha256}` records instead; do not suppress/relax scanning | Rerun the real matrix to bind final source hashes; rescan staged files and committed range |

### Verification and limitations

`node scripts/check-role-browser.mjs --publishable-evidence` passed **66 grouped checks** on 2026-10-15 11:03–11:04 SGT: **168 page observations, 308 read calls, 120 denied writes, four cross-user writes**, all role menu/CSRF/logout/viewport checks and unchanged database fingerprints. These are observations, not inflated JUnit counts. [Safe JSON and four visually inspected screenshots](../evidence/role-access-20261015/README.md) bind exact executed input hashes. Raw traces remain private.

Frontend lint/typecheck/build, **134 component tests**, **12 mocked E2E** and **6 matrix Node tests** passed; existing nonfatal chart/dependency/bundle warnings remain. Backend production sources and generated API contracts are unchanged. Current-HEAD hosted CI must be rerun after publication, separately from the initial all-green HEAD. Full real workflow rerun is recorded after execution rather than inferred.

The subsequent complete `check-uc02-browser.mjs` rerun passed **37 live checks**, run `ae79509918`, 11:05–11:10 SGT, with the changed frontend guard; [safe summary](../evidence/role-access-20261015/workflow-verification.json) records actual inputs. Fresh login after teardown confirms BORROWER-only; both new SQL guards reject the wrong database. A separate role probe initially ran before the full workflow's teardown and correctly still saw APPROVER; it is not recorded as restored until the later probe passed. No production/backend code changed. Runtime services are stopped at handover; database history/logs/private traces are retained.

Final evidence-format rerun: **11:12:46–11:13:38 SGT**, all **66 grouped checks** and the same detailed counts passed. Committed matrix JSON/screenshots now represent this rerun, after replacing filename-keyed hashes with explicit `{path, sha256}` records to avoid a Gitleaks false positive. All nine input hashes match, the new SQL wrong-database guards fail closed, BORROWER-only fresh login passes, and all four final screenshots were visually rechecked. Full-workflow production inputs did not change in this metadata-only correction.

## UC05/main combination follow-up

The user authorized normal main synchronization. [The dated integration log](2026-10-15-hanming-uc05-main-sync.md) records merge parents `852fc5f`/`0327b7a`, the current main base `0ca4c14`, retained source ownership, initial fixture-isolation/mobile-overflow failures and actual fixes. [New evidence](../evidence/role-access-uc05-sync-20261015/README.md) records **13-page / 23-API** live acceptance, **37 complete workflow checks**, **66 matrix groups** (182 page observations / 322 read calls / 120 denied writes / four cross-user denials), and unchanged fingerprints. Original 12-page evidence above remains historical.

Final native regression passed **590 + 30 UC02 + 20 UC03 + 10 UC05** with four isolated databases; no assertion/test was relaxed. New UC05 business logic is included in **79.82%** line coverage, and the negative 70% check still fails/restores/passes. Frontend **137 components / 12 mocked E2E / 7 Node tests** and quality checks passed. Main already contains CodeQL and a deliberately paused/grouped Dependabot configuration; do not describe them as absent. Actual latest-HEAD CI and member/deployed acceptance remain separate. #18 still belongs to Hanming and is not implemented by this synchronization.

Future equipment #17, Hanming's admin #18, overdue #19 and final combined-page acceptance remain open. CodeQL/Dependabot/backup/load/ZAP are Wang's #25, not silently assumed complete or reassigned. No effort, human check, merge or deployed acceptance is claimed. Authorship: Codex-assisted implementation/testing for Hanming.
