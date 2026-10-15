# Zhang Hanming — Delivery Status and Remaining Work

As checked on 2026-10-15 against freshly fetched main `2e9eda35973f0929c19fa3e613688d902a361f9d` and proposal starting HEAD `6bad66922963369ecb28ba20959dc2a66dd27fe7`. Human owner: Zhang Hanming (@Hanming1008); factual preparation/verification assisted by Codex. This is a status checklist, not a sign-off, effort record or submitted course report.

## Scope correction

UC02 is substantially implemented, but the current task allocation includes more than UC02. [Issue #18](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/18), [plan #27](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27) and [D-007](../collaboration/decisions.md#d-007-completion-task-ownership) assign Hanming the **admin console, user API, guard rails and audit viewer**. Only the first-admin bootstrap was split to Liu Tongyao's #41 and completed in #43. Admin pages are not merely another owner's dependency.

## Historical pre-UC05-sync snapshot: requirement → evidence → remaining acceptance

| Work | Verified evidence | Status / next action |
| --- | --- | --- |
| UC02 rules, API, persistence and React UI | Existing implementation plus [UC02 log](../devlog/issue-8-approval-postgres-browser.md); 30 real PostgreSQL acceptance cases cover permission/CSRF, state/version/expiry, unique/immutable decision, concurrency, release and rollback | Implemented/tested on proposal; latest delivery still needs main integration and member/deployed acceptance |
| Real five-role workflow and dual-role self-decision | [Dual-role evidence](uc02-dual-role-20261015/README.md); 37 live checks, separate approve/reject, delivery/read, checkout/damaged return/repair, both self-decision choices blocked | Passed locally; no claim of deployed acceptance |
| Current-page menus / direct URL / API allow and deny | [Current-page matrix](role-access-20261015/README.md): 168 page observations, 308 read calls, 120 role-denied writes, 4 cross-user writes; real desktop/mobile; database fingerprints unchanged | Passed locally; future routes deliberately fail the inventory test until the matrix is extended |
| Approval normal/error/empty desktop/mobile states | Existing 24 approval component cases and 12 shared mocked E2E; actual live empty queue, rejection validation, separate/self decision flows | Passed for current scope; mocked error fixtures remain labelled mocks |
| 70% service/domain coverage | [Quality log](../devlog/issue-24-quality-gates.md); actual Maven check, nonempty scope, initial 87.47% line coverage; zero-execution proof fails and exact original coverage restores/passes | Configured, executed and passed on `6bad669` hosted acceptance; every later HEAD requires its own CI |
| Current frontend quality | 134 components, 12 existing mocked E2E, 6 matrix/inventory Node tests; lint/typecheck/build | Passed locally on recorded working-tree inputs; hosted result belongs to latest PR HEAD |
| User and role management #18 | No management routes/API/page at the fetched baseline; identity currently provides auth endpoints and bootstrap | **Not implemented**: list/search/create/update/reset password, activation/deactivation, multi-role assignment/removal |
| #18 safety/session behavior | Not established by approval's existing permission tests | **Not implemented/verified**: no self-deactivation, retain last active admin under concurrency, deactivated session denied on next request, transactional secret-free change audit |
| #18 audit viewer | Audit writes/append-only protections exist; no viewer API/page at the fetched baseline | **Not implemented**: ADMIN-only paginated/filtered read API + React page |
| #18 test bench | None claimed by this proposal | Needs unit/MockMvc/real PostgreSQL concurrency/session tests, components, real admin-creates-borrower login E2E; regenerate API types |
| #24 final combined-page acceptance | Management #17, admin #18 and overdue #19 absent at baseline | Pending those features; extend manifest, rerun real combined flows within the task's agreed schedule |
| Human check, merge, deployment, team Done | Separate from automated/local/CI results | Not claimed; do not close #8/#24 from this proposal |

## Quality/security ownership and evidence boundaries

- Existing GitHub CI includes Gitleaks, Trivy filesystem/image checks and Dependency Review; all ten workflows passed on `6bad669`. This is revision-bound executed evidence, not proof that every security-checklist checkbox passes or that a policy is required by branch protection.
- CodeQL, Dependabot and backup/load/ZAP work belongs to Wang's #25 under D-007. At this baseline the technology document still marks CodeQL/Dependabot pending; final evidence must come from actual configuration/runs. Do not silently take over that task or mark it complete.
- ArchUnit/Spotless/SpotBugs, email, QR codes and automatic expired-pending cancellation were explicitly dropped under D-009; do not resurrect old Sprint 0 tool lists as mandatory remaining work.
- New required-status-check policy remains the repository owner's decision. No protection/settings change is performed.

## Recommended execution order

1. Deliver/review the current #8/#24 proposal and retain its actual test/CI evidence; this is not a request to merge automatically.
2. Implement **#18 as a separate scoped delivery** from current main, checking for an existing owner branch first. Reuse the encoder, bootstrap, identity, UI and append-only audit infrastructure; do not create a second authentication system or rewrite migrations.
3. Before changes to login/session semantics, prepare the precise contract impact and obtain the affected-owner confirmation required by #18. This is one focused interface/security decision, not a need to reconfirm every old contract.
4. After #17/#18/#19 land, extend the explicit matrix and rerun real all-role workflows. Then obtain member checks and final deployed acceptance, and update the report from these records. Do not invent hours or a percentage-complete figure.

Authorship: Codex-assisted status audit for Hanming, based on actual repository/task evidence. No teammate approval or delegation beyond the published task assignment is implied.

## UC05/main synchronization update

The user subsequently authorized synchronizing PR #46 with main `f9596c1`; normal merge `852fc5f` retains both branches. [The synchronization log](../devlog/2026-10-15-hanming-uc05-main-sync.md) supersedes only the old snapshot's absent Overdue/CodeQL/Dependabot assumptions: these features/configurations are now present from #12/#39. Presence is not final security/performance/deployment acceptance. The explicit current-page matrix is extended to 13 routes/23 read families and must be re-executed on the combined source. Old screenshots/CI remain version-bound historical evidence, not proof of the new combination. #18 remains Hanming's next development task; only bootstrap #41 was completed by Liu.
