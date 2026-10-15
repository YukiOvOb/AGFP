# Real Role Access After UC05/Main Sync — 2026-10-15

Human owner: Zhang Hanming (@Hanming1008); Codex-assisted execution. Refs #8/#24, partial delivery. Previous evidence is retained in [the pre-UC05 directory](../role-access-20261015/README.md), not overwritten.

## Versions and execution

The quality proposal `64cf5e6` was normally merged with main `f9596c1` as `852fc5f`, retaining UC05/DevSecOps and the loans guard. A second normal merge `0327b7ac5a1677a1b77dfe7d0afa175f2b81daf3` retains main `0ca4c1439ae10201d7b72173f92567b6e201656a` (#70 Dependabot pause, configuration/docs only). No dependency upgrade, history rewrite, migration or business-rule change.

Windows; Java 25.0.4/release 17, Maven Wrapper 3.9.11, PostgreSQL 18.6, Node 24.19.0, Playwright Chromium. Actual React → Spring Boot → isolated six-account PostgreSQL demo; no API interception/fake clock. [Matrix JSON](verification.json) records starting HEAD `0327b7a`, final fetched main, and **13 executed source hashes**, including the Overdue UI/API and responsive CSS. The tested inputs include the documented uncommitted verification/style changes, not a fictitious future commit.

| Check / command | Actual result |
| --- | --- |
| `node scripts/check-uc02-browser.mjs` | **37 live checks passed**, run `52cadb7a47`, **15:34:16–15:38:10 SGT**. Actual booking, approve/reject, notification delivery/read, checkout/damaged return, repair, dual-role UI/forged-POST denial and role cleanup |
| `node scripts/check-role-browser.mjs --publishable-evidence` | **66 grouped checks passed**, **15:40:11–15:41:48 SGT**; final serialized run after business workflow teardown |
| Direct routes | **182 passed**: 13 pages × anonymous/five single roles/second borrower × desktop/mobile |
| Read APIs | **322 passed**: 23 families × 7 actors × 2 widths; exact 401/403 and owner-scoped 404, including real `/api/v1/overdue` |
| Denied writes | **120 passed** with valid CSRF and request shapes; four additional cross-user writes return 404 |
| Overdue | Four CUSTODIAN/ADMIN page reads, real empty states and actual ascending-sort responses passed; other roles denied and anonymous redirected/401 |
| No side effects | Equipment/reservation/decision/loan/case/audit/notification-read/role fingerprints unchanged; no uncaught page errors or document overflow |
| Native full regression | **590 ordinary + 30 UC02 + 20 UC03 + 10 UC05 passed**, zero failures/errors/skips, separate throw-away databases |
| Business coverage / negative proof | **79.82%**, 1,183 covered / 299 missed lines including new UC05 logic; actual unchanged 70% gate rejects zero coverage, original bytes restored and check passes |
| Frontend | Lint/typecheck/build/API-schema check passed; **137 components**, **12 mocked E2E**, **7 Node inventory/policy cases** passed. Existing nonfatal warnings retained |
| Guard/privacy recheck | Both SQL entry points reject another DB; fresh login confirms second borrower has BORROWER only; all 13 source hashes match, no session/credential fields in public JSON |

[Safe workflow summary](workflow-verification.json) records its actual starting application merge `852fc5f`, earlier fetched main and input hashes; later configuration-only main merge did not alter those product inputs. Neither summary contains raw trace paths, server URLs, passwords or cookies. Private traces/logs remain in ignored local cache; never upload them.

## Scope and retained failures

- Reuse main's `role: string | string[]` guard for loans and Overdue; no duplicate guard API or implicit ADMIN privilege. The independent expected policy manifest, not the menu implementation, defines allowed access. New routes still fail inventory checking until explicitly reviewed.
- Main now renders equipment names in notification text. The workflow selects the exact confirmed-equipment message, rather than an internal UUID or a separate submission/collection message.
- Initial native combined UC03/UC05 run failed because the old runner gave both suites the same database via UC05 fallback. Add a dedicated UC05 database; no original assertions, records or tests are deleted.
- Initial live workflow reproduced mobile custodian overflow: 395.4px document at 390px viewport. Allow sidebar groups to wrap; keep all links and the original 2px tolerance. The subsequent workflow/matrix passed.
- A premature first matrix overlapped the still-writing workflow and correctly failed its unchanged no-side-effect fingerprints. That run is retained privately and is not passed evidence. The final run is serialized after teardown and passed.

See [the synchronization log](../../devlog/2026-10-15-hanming-uc05-main-sync.md) for rules, commits, ownership, failed attempts and checks. Hosted results are recorded for the final published HEAD in [PR #46](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/46); local results do not imply CI, human approval, merge or deployment.

## Screenshots and remaining work

Four Overdue screenshots were visually inspected: [desktop custodian](desktop-custodian-overdue.png), [desktop admin](desktop-admin-overdue.png), [mobile custodian](mobile-custodian-overdue.png), [mobile admin](mobile-admin-overdue.png). Additional synthetic role-denial/assigned-maintenance reference frames are retained alongside them.

The live demo genuinely has **zero overdue active loans**, so this is permission/menu/empty-state/sort-request acceptance, **not a claim of a real-time nonempty overdue browser scenario**. Persisted overdue boundaries/projection/scanners are covered separately by the 10 real-PostgreSQL UC05 integration tests (test clock) and Wang's evidence. No history is backdated/reset to manufacture a screenshot.

To reproduce: start this checkout's [isolated demo](../../local-demo.md), finish `node scripts/check-uc02-browser.mjs`, then run the role matrix **serially**. The runner requires a real completed handover/repair fixture and fails closed without it. Stop only this checkout's registered services afterward; preserve history.

Remaining feature gaps are equipment management #17 and Hanming's **admin/users/roles/audit #18**. Extend the matrix after those pages land. Independent member checks, final main integration, cloud/deployed checks and team Done remain separate; neither #8 nor #24 is closed by this evidence.
