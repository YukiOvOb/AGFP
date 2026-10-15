# Administrator security and cross-session revocation — 2026-10-15

Owner: Zhang Hanming (`Hanming1008`), Issue #18 / draft PR #72. This records local agent-assisted
verification, not member approval, main merge, hosted CI for a future commit, deployment or team Done.

Input HEAD: `f189b5c79a7aa62d40b9d76de4165f884b0f8932` plus the uncommitted security continuation;
fetched main: `0be0a29af011862824c6aaab642512241764bcd9`. [verification.json](verification.json)
binds modified source/migration/test hashes and screenshot hashes to the inputs. V007 was still free
on that remote main when rechecked before delivery; renumber if necessary before integration.
Wang's behavioural agreement was supplied by the user in the conversation; see the
[contract record](../../contracts/admin-console.md) and [development log](../../devlog/issue-18-admin-console.md).

## Results

| Check | Actual result |
| --- | --- |
| Backend | 685 ordinary + 30 UC02 + 30 UC03/UC05 = 745 passed, no failures/errors/skips |
| Administrator/audit subset | 38 API, 29 service unit, 26 real PostgreSQL, 2 audit-query unit cases |
| Real security cases | Two independent sessions each for disable/re-enable, roles and password reset; strict 401 vs 403; old password denied; unchanged roles/status keep sessions; stale versions rejected; transaction rollback restores data/version/session validity; concurrent final-admin demotions leave one administrator |
| Coverage | 1307 covered / 299 missed business lines, 81.38%; unchanged 70% threshold; deliberate-zero negative check and byte-for-byte restoration passed |
| Frontend | 169 components; lint/typecheck/build and actual backend API type drift check passed |
| Mock browser contract | 14 desktop/mobile Playwright cases passed; these simulate APIs, not database acceptance |
| Real UC02/browser workflow | Actual desktop/mobile reservation → approval → checkout → damaged return → repair, rejection/slot release, notification and dual-role self-decision protection passed; real time/window, no fake clock |
| Real role matrix | 66 groups, 210 page observations / 15 routes, 350 API reads / 25 families, 170 denied writes / 18 mutation families, 4 cross-user denials; user/business/audit/notification-read/role fingerprints unchanged |
| Real admin security UI | Desktop/mobile create → explicit role change → disable → activate → password reset; two old cookie sessions expire; reactivation does not resurrect them; replacement password works; version 4 and exactly one audit for each of five committed actions |
| Original admin UI regression | Desktop/mobile create/name edit/audit/original-password login and admin permission denial passed |

Environment: Windows, JDK25.0.4 with Java17 target, Spring Boot4.0.8, Maven3.9.11, PostgreSQL18.6,
Node24.19.0, local pnpm11.25.0 (manifest11.19.0), Playwright Chromium. Disposable backend test
clusters are stopped by their script. Real browser data uses this worktree's fresh isolated
loopback demo. Prior data is preserved in ignored `.cache/admin-display-name-pre-session-20261015`;
no shared tables or immutable histories were cleared to obtain passes. All account/role/status/password
browser mutations use the actual REST UI and current CSRF/session contract, not direct SQL.
The separate UC02 dual-role fixture temporarily grants/restores its declared test role, as documented
by that harness. Role-matrix probes run before admin creation, with no concurrent writing test.

## Reproduction

From repository root, use PostgreSQL18 (native bin or the documented container alternative):

```powershell
pwsh -NoProfile -File scripts/test-uc02.ps1 -PostgresBin '<PostgreSQL18 bin>' -IncludeLoanIntegration
pwsh -NoProfile -File scripts/check-coverage-gate.ps1
node --test scripts/role-access.test.mjs
node scripts/check-collaboration.mjs
```

In `frontend/`, generate types from `../app/target/openapi.json`, then run `pnpm check:api`,
`pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` and `pnpm test:e2e`.
On a fresh dedicated local demo, run the real scripts **in this order**:

```powershell
node scripts/check-uc02-browser.mjs
node scripts/check-role-browser.mjs
node scripts/check-admin-security-browser.mjs
node scripts/check-admin-browser.mjs
pwsh -NoProfile -File scripts/stop-local.ps1
```

Follow `docs/local-demo.md` for startup, ports and prerequisites. The six-account guard in the
first two scripts deliberately rejects databases with extra users. Preserve previous data and use
a fresh isolated cluster instead of weakening the guard or deleting created accounts/audit rows.
The admin scripts generate random synthetic users, leave their histories intact, and write private
reports under `.cache/local-demo/`. Do not publish raw traces, access.json, cookies or credentials.

## Reviewed synthetic screenshots

- [Desktop roles](desktop-roles.png), [mobile roles](mobile-roles.png).
- [Desktop security result](desktop-security.png), [mobile security result](mobile-security.png).
- [Desktop audit](desktop-audit.png), [mobile audit](mobile-audit.png).

Screenshots contain synthetic local accounts only, after password fields were cleared. They do not
prove backend correctness on their own; the live requests and PostgreSQL tests above do.

## Remaining acceptance

The subsequent historical notification upgrade assertion repair and its independent 12-case
PostgreSQL result are recorded in the [notification-contract follow-up](notification-contract-followup.md).
The original source-hash JSON above remains unchanged.

Latest delivered-HEAD hosted CI and member review/check; merge-time migration coordination;
main integration, deployed acceptance and final new-page regression after all dependent feature PRs
merge. No Issue closure, branch-protection change, production deployment or human test is claimed.
