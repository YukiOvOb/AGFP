# Hanming Quality Branch — UC05/Main Synchronization

Owner: Zhang Hanming (@Hanming1008). Refs #8 and #24. Codex is the sole writer in `SERMS-hanming-quality-20261015`, branch `codex/hanming-uc02-quality-20261015`. The user explicitly authorized synchronizing the existing PR, retaining both contributors' changes, extending Overdue acceptance and rerunning verification. No main merge, Issue closure, remote message, production change or human approval is authorized/claimed.

## Versions and rules

- Previous proposal: `64cf5e6603d14aecfe84f91fe9a444062ccc4176` (historically all ten workflows passed).
- Freshly fetched main: `f9596c14529fb67577877b0ac8ba25d758e8bf00`, including #47 evidence, #12 UC05 and #39 DevSecOps. No Dependabot dependency-upgrade PR is merged by this task.
- Normal merge: `852fc5f5f861e10f955c4f5cd0e6cc178968de4d`; parents are the two SHAs above. Local recovery branch `codex/hanming-before-uc05-sync-20261015` preserves the old proposal. No rebase/force push.
- While verification ran, main advanced to `0ca4c1439ae10201d7b72173f92567b6e201656a` (#70 Dependabot pause). Normal merge `0327b7ac5a1677a1b77dfe7d0afa175f2b81daf3` retains those three configuration/documentation files. It changes no application, API, migration or business-test inputs; no dependency upgrade is introduced. New matrix evidence records this final fetched base.
- Read: AGENTS, CONTRIBUTING, ownership/testing/contract index, current checkout-and-return UC05 projection confirmation and decision delta, and repository skills serms-task/verify/pr/integrate. Existing technology, test bench, language and authorization rules are retained.
- Scope: merge integration, route conflict resolution, browser/test infrastructure, verification/evidence. Admin #18 implementation, authentication/session contract changes, migrations, deployment and other authors' active branches are excluded from this PR.

## Design and conflict resolution

Only `frontend/src/app/App.tsx` and `RequireRole.tsx` conflicted. Retain main's `role: string | string[]` guard and use it for both BORROWER/CUSTODIAN loans and CUSTODIAN/ADMIN overdue. Remove the duplicate `roles` prop introduced by the older proposal. Keep explicit APPROVER checks; ADMIN has no implicit borrowing/approval privilege. Existing four loan-access regression cases stay.

Automatic merges retain main's UC05 profile/tests, notification producers, generated schema, overdue projection/page, CodeQL, Dependabot and Docker image preparation, as well as this PR's coverage gate and inventory test. No migration or REST/event contract is changed. The overdue operator/read-only contract is already confirmed on main; no new affected-owner approval is invented.

The explicit browser matrix now covers **13 routes / 23 read API families**, including Overdue's real menu, backend 401/403, direct URL, empty state (when genuinely empty) and the real ascending-sort request. Main's user-friendly notification text uses equipment names, so the workflow selects the unique confirmed-equipment message rather than an internal reservation UUID or unrelated submission/collection message. No fake data response/time is introduced. Old committed evidence is retained; new evidence goes in a separate dated sync directory.

## Problems and fixes

| Symptom | Root cause | Fix and verification |
| --- | --- | --- |
| Ordinary Git merge stops at two frontend files | Both branches independently extended the same guard and route list | Reuse main's multi-role API and retain both routes. Seven inventory tests, typecheck, 137 components and existing 12 mocked E2E passed |
| Native UC03+UC05 integration initially fails one global-overdue ordering assertion | Main's UC03 Maven profile now includes UC05. The old local runner only set the UC03 database; UC05's supported fallback reused it, importing UC03's older active loans | Provision a fourth throw-away database and all three UC05 connection variables; restore environment afterwards. Retain every original test/assertion and rerun the full bench. No table clearing or production fix |
| Real mobile custodian overview is 395.4px wide at a 390px viewport | Workspace and newly available Management navigation form a non-wrapping flex row | Allow mobile sidebar groups to wrap without hiding links, reducing font size or weakening the 2px tolerance. Actual DOM measurement then reports no overflowing elements; full real flow/matrix re-executed |
| A first matrix attempt ends with changed-data fingerprints despite passing page/API checks | It was started too early, overlapping this session's still-writing complete workflow and its temporary role cleanup | Discard that attempt as acceptance. Wait for workflow completion/cleanup, then rerun the matrix serially; keep the unchanged no-side-effect assertion and failed private record |

## Verification

Environment: Windows; Java 25.0.4 compiling release 17, Maven Wrapper 3.9.11, native PostgreSQL 18.6, Node 24.19.0, provided pnpm runtime, Playwright Chromium. Disposable regression databases are separate from the checkout's six-account demo cluster. The demo uses new dedicated loopback ports and retains previous history.

Frontend lint/typecheck/build, API schema drift check, **137 components**, **12 mocked E2E** and **7 explicit matrix/inventory tests** passed on the combined source. Existing chart/dependency annotation/bundle warnings remain nonfatal. Initial backend run passed 590 ordinary tests + 30 UC02, then 20 UC03 and 9/10 UC05 before the isolation finding above; that failed run is not called a pass. Full rerun, real browser runs, negative coverage exercise and latest-HEAD CI are recorded below only after actual execution.

After the four-database runner fix, the complete native PostgreSQL bench passed **590 ordinary + 30 UC02 + 20 UC03 + 10 UC05**, zero failures/errors/skips. Combined business-line coverage is **79.82%** (1,183 covered / 299 missed) with new UC05 service/domain classes included, not excluded to preserve the previous percentage. The real zero-execution negative exercise failed at the unchanged 70% threshold; original bytes restored and the actual check passed. Logs: ignored `.cache/uc05-sync-backend-isolated.log` and `.cache/uc05-sync-negative-coverage.log`. No production Java changed after those runs.

Final live workflow passed **37 checks**, run `52cadb7a47`, 15:34–15:38 SGT; temporary role removed and subsequent login is BORROWER-only. Final serialized matrix passed **66 grouped checks**, 15:40–15:41 SGT: **182 page visits, 322 read calls, 120 role-denied writes, four cross-user denials**, unchanged fingerprints and viewport fit. All four operator Overdue empty states and actual ascending-sort requests passed. The demo has no active overdue loans; do not claim a live nonempty overdue screenshot/scanner scenario from this run. The PostgreSQL UC05 suite and Wang's evidence cover those separately. [New reviewed evidence](../evidence/role-access-uc05-sync-20261015/README.md) preserves exact source hashes, actual times and safe summaries; old evidence remains historical.

After the responsive fix, frontend lint/typecheck/build and **137 component + 12 mocked E2E + 7 Node inventory tests** passed again. API-schema drift check passed; PowerShell and Node syntax, SQL wrong-database guards, source-hash/privacy checks and collaboration navigation passed. Latest published HEAD CI remains separate and must complete after push; no old ten-workflow result is substituted.

## Remaining acceptance

New main includes the Overdue page/scanners and CodeQL/Dependabot configuration; the old checklist's absent-UC05/security-config entries are historical, not today's status. Wang's #19/#25 acceptance/performance/backup/deployed follow-ups remain his responsibility. Hanming's #18 user/role administration, guard rails and audit viewer remain substantial unimplemented work. Final #24 equipment/admin-page acceptance follows #17/#18. Independent member checks, main integration, deployment and team Done are separate.

Authorship: original UC05/DevSecOps implementation remains Wang's, integration closeout on main remains Liu's; this synchronization/test adaptation is Codex-assisted work for Hanming. No hours, meetings, signatures or owner confirmations are invented.
