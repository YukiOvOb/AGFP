# Boot 4 Combined Local Acceptance

Liu Tongyao, agent-assisted; 2026-10-13 (Asia/Singapore). Candidate PR #13 integrates PR #14 (`bbb1902b705efd4e937abcfbfe0b12404fd683d4`) and #11 (`d42e0a616affeebe41a3537396ccea960633b30d`), with the [user-authorized security migration](../../boot4-security-upgrade.md). Delivery HEAD and exact copied-file hashes are in the PR and local delivery manifest; no unreviewed old maintenance changes are included.

Boot 4.0.8 / Framework 7.0.9 / Springdoc 3.0.3 / Jackson 3.1.7 and compatibility 2.21.7 / Tomcat 11.0.27. JDK 25.0.4 compiles Java 17; native PostgreSQL 18.6, Maven 3.9.7, Node 24.19.0, installed pnpm 11.25.0 with unchanged lockfile. Hosted Java 17 and package-declared pnpm remain independent checks.

| Actual final local check | Result |
| --- | --- |
| `scripts/test-zhoufanhao.ps1 -PostgresBin <18 binaries>` | Database 27 + application 536 + UC03 20 passed; zero failures/errors/skips |
| Existing `MaintenancePostgresIT` on isolated V001–V004 PostgreSQL 18 | 28 passed; zero failures/errors/skips |
| `pnpm check:api`, `pnpm lint`, `pnpm test`, `pnpm build` | Passed; 87 components; generated API types remain aligned without regeneration |
| `pnpm exec playwright test --workers=1` | All 12 existing mocked desktop/mobile tests passed |
| `node scripts/smoke-local.mjs` | 28 real checks passed, including UC01–UC05, real approval/loan/repair/notifications |
| `node scripts/check-uc01-uc04.mjs` | 24 real API checks passed, including cancellation/privacy/blocked equipment/CSRF/technician/loan exceptions/history/recovery |
| `node scripts/check-uc01-uc04-browser.mjs --boot4` | 10 real desktop/mobile checks passed; six screenshots alongside this record; mobile overflow assertion passed |
| `scripts/check-local-data.ps1` | Three isolation/idempotency checks passed; passwords/history preserved |
| Release gate and simulated server boundaries | 6 + 7 tests passed; no server contact |

The live instance used its own generated accounts, database and ports 55435/8084/5176, only on loopback. Actual requests passed through the frontend proxy into the Boot 4 API and PostgreSQL. Browser checks used independent role contexts with no API interception. They create/cancel reservations and complete repair records while retaining history. Screenshots were visually inspected at mobile width; credentials/cookies are absent from tracked evidence.

Initial migration failures remain in ignored diagnostics: MVC slices lacked HttpSecurity before explicit security/test-module wiring; initial Vite cold-route and one mocked desktop initialization timeout. Subsequent complete runs passed; no assertions, skips, retries, coverage threshold or security scan severity were relaxed. The first Boot 3 built-image vulnerability failure remains documented in the security upgrade record.

Hosted build/image scans and the real production-profile Compose smoke must pass on final HEAD; they are recorded in the PR independently of these local results. The Compose job uses an explicitly created isolated CI volume, verifies real database health, six React routes/assets, API proxy/anonymous rejection and Secure CSRF cookies, then removes only its own CI containers/volume. No production volume is used.

Server access/fingerprint, actual storage/environment inventory, restore rehearsal, public HTTPS login/workflows and member acceptance remain pending. All three delivery PRs remain draft; no main merge, server action, fabricated review or team Done is claimed. Local runtime/data/reports remain ignored and are preserved after shutdown.

Workspace note: another authorized repository-closeout session moved the primary checkout to `codex/local-repository-closeout` at `b9fa875` during this task and preserved the initial eleven modifications in its named stash/backup. This session worked only in its isolated delivery worktrees and did not overwrite that checkout. The Boot security decision is D-006 to avoid colliding with closeout's D-005.
