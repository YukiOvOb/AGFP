# Current-page Role Access Acceptance — 2026-10-15

Owner: Zhang Hanming (@Hanming1008). Codex-assisted implementation and execution. Refs #8 and #24; no Issue closure, member approval, merge or deployment is claimed.

## Actual run

`node scripts/check-role-browser.mjs --publishable-evidence` passed on 2026-10-15 **11:03:27–11:04:26 SGT** against this worktree's real React → Spring Boot → PostgreSQL 18 stack. Windows, Node 24.19.0, Chromium/Playwright 1.63.0. No API interception, fake clock, role elevation or business-fixture writes. Existing completed browser handover/repair history is read, not reset.

Starting HEAD: `6bad66922963369ecb28ba20959dc2a66dd27fe7`; freshly fetched main: `2e9eda35973f0929c19fa3e613688d902a361f9d`. The run included uncommitted runner/SQL/route-guard inputs. [Verification JSON](verification.json) binds nine executed inputs to SHA-256, rather than falsely assigning a future commit to the run. Backend production sources/JAR match the starting HEAD; Vite served the recorded frontend source hashes.

The initial corrected run above was followed by a final evidence-format rerun at **11:12:46–11:13:38 SGT**, with the same passing counts. The committed JSON/screenshots represent this final run. Provenance is stored as explicit `{path, sha256}` records: Gitleaks had misclassified a filename-keyed hash as an API credential. No real credential was present, no scanner suppression was added, and the complete matrix was re-executed after the format change. All final source hashes and screenshot layouts were rechecked.

| Check | Actual result |
| --- | --- |
| Actors/viewports | Anonymous + five single-role accounts + a second BORROWER; 1440×1000 and 390×844 |
| Explicit menu expectations | All 12 signed-in sessions matched the declared role menu; no implicit ADMIN privileges |
| Direct page routes | **168 passed**: 12 implemented pages × 7 actors × 2 viewports; actual allowed content, denied alerts or login redirects; no document overflow |
| Read APIs | **308 passed**: 22 endpoint families × 7 actors × 2 viewports; success, 401, role 403 and owner-scoped 404 distinguished |
| Unauthorized writes | **120 passed**, valid CSRF and valid request shapes; exact 403 `/problems/forbidden`, not validation or CSRF failures |
| Cross-user writes | **4 passed**: second borrower cannot cancel the first borrower's reservation or mark their notification read; 404 |
| CSRF/logout | All 12 signed-in sessions: missing CSRF rejects logout without invalidating the session; actual UI logout then makes `/auth/me` return 401 |
| Side effects | Before/after PostgreSQL fingerprints unchanged for equipment, reservations, decisions, loans, cases, audit, notification identities/read state and role memberships |
| Summary | **66 grouped checks passed**; counts above are detailed observations, not additional JUnit tests |

## Expected current page access

B = BORROWER, A = APPROVER, C = CUSTODIAN, M = MAINTAINER, D = ADMIN. Anonymous access redirects to login for every row. Backend permissions remain authoritative; page visibility does not grant a role.

| Page | Allowed roles / resource scope |
| --- | --- |
| Overview, equipment search, notifications | B, A, C, M, D; inbox scoped to the current user |
| Reserve equipment, My reservations | B; reservations scoped to the current user |
| Loans and returns | B own loans; C checkout/return and global loan view |
| Approval queue and request detail | A only; self-decision still forbidden even with explicit dual roles |
| Maintenance workspace | B reports faults; C queue/assignment/history; M assigned work; D queue/history/report |
| Maintenance case detail | C, D; M only when assigned to that case |
| Maintenance report, combined reports | D only |

The explicit manifest lives in [role-access.mjs](../../../scripts/lib/role-access.mjs). Its Node tests run in frontend CI and reject route-inventory drift, so a new route requires an explicit matrix update. The development-only component gallery and wildcard 404 are not business pages; login is exercised by real sign-in/logout.

## Defect reproduced and fixed

The initial live run failed when an APPROVER directly opened `/loans`: the hidden menu was correct and the API denied 403, but the page still rendered “My loans” and an API-error banner. Add an explicit BORROWER-or-CUSTODIAN route guard, retaining the existing single-role guard behavior and backend enforcement. Three negative component cases prove APPROVER/MAINTAINER/ADMIN never request loan data; one explicit ADMIN+BORROWER case retains legitimate multi-role access. The corrected full live matrix passed.

Related local checks: **134 component tests**, **12 mocked desktop/mobile E2E**, **6 route/matrix Node tests**, lint, typecheck and production build passed. The mocked E2E result is not substituted for this real run. Existing chart-size/annotation/bundle warnings remain nonfatal. Latest-head hosted CI is recorded separately in [PR #46](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/46).

After the guard fix, `node scripts/check-uc02-browser.mjs` was also rerun: **37 live cross-module/dual-role checks passed**, run `ae79509918`, **11:05:55–11:10:05 SGT**. [Safe workflow summary](workflow-verification.json) records the actual fixture IDs and executed frontend hashes. Its existing SQL inputs remain unchanged from the starting HEAD. Normal teardown removed only the role added by that run; a subsequent fresh login confirmed BORROWER-only. Both new read-only SQL entry points rejected another database with exit 3. An earlier role-restoration probe was run prematurely while the workflow intentionally still held its dual-role fixture; only the post-teardown probe is a restoration pass.

## Screenshots and private traces

All four PNGs were visually inspected; they show synthetic demo identities/data only:

- [Desktop ADMIN denied approval](desktop-admin-no-implicit-approval.png)
- [Mobile ADMIN denied approval](mobile-admin-no-implicit-approval.png)
- [Desktop assigned-maintenance read](desktop-assigned-maintenance.png)
- [Mobile assigned-maintenance read](mobile-assigned-maintenance.png)

Private traces are retained under ignored `.cache/local-demo/role-browser-*/`. Tracing starts after login, but traces can still contain session/CSRF tokens; never upload raw ZIPs or `access.json`. The committed summary has no password, cookie, token, runtime directory or server URL.

## Reproduce and remaining acceptance

1. Start **this checkout's** isolated demo using [the launcher](../../local-demo.md); never a shared/production database.
2. Ensure `node scripts/check-uc02-browser.mjs` has completed a real handover/damaged-return/repair and delivered a notification. The matrix fails closed without that history; it never invents IDs or silently skips a detail page.
3. Run `node --test scripts/role-access.test.mjs`, then `node scripts/check-role-browser.mjs`. Only add `--publishable-evidence` when intentionally replacing reviewed synthetic evidence.
4. Stop only this checkout's registered services with `scripts/stop-local.ps1`; preserve history and private artifacts.

Current unavailable pages are **not passed**: equipment management #17 (Zhou), users/roles and audit viewer #18 (**Hanming**), overdue #19 (Wang). Extend the matrix and rerun the combined workflow after those features merge. This current-page acceptance does not implement #18, establish role-change/deactivation session behavior, or provide independent member/cloud acceptance. See the [delivery status checklist](../hanming-delivery-status-20261015.md).
