# Local Integration Verification — 2026-10-13

Baseline main: `940bf42cd1b6b507d24179e8aa4065af14f72a0e`. Tests ran in an isolated writable source snapshot of that baseline plus this PR's local startup/data/check scripts, Vite proxy setting and hidden-label positioning fix. The tested files are copied byte-for-byte into the dedicated managed Git worktree before commit. Read the PR/Git history for the delivered commit; this baseline is not presented as a final delivery SHA.

## Environment and Results

Windows; PostgreSQL 18.6; Maven 3.9.7; installed JDK 25.0.4 compiling the existing Java 17 release target; Node 24.19.0; installed pnpm 11.25.0 with the unchanged frozen lockfile. Hosted frontend CI uses Java 17 and the package-declared pnpm version; its results remain separate from these local runs.

| Check | Actual result |
| --- | --- |
| Backend and frontend packaging | Passed; existing Rollup annotation/bundle-size warnings remain |
| `scripts/test-zhoufanhao.ps1 -PostgresBin <PG18 bin>` | Passed: database 27, application 536, UC03 PostgreSQL integration 20; zero failures/errors/skips |
| Service/domain line coverage | 97.04% (1115 covered / 34 missed), calculated from the generated JaCoCo report across service/domain packages; exceeds existing 70% requirement |
| `pnpm lint`, `pnpm typecheck`, `pnpm test` | Passed; 79 component tests |
| `CI=1 pnpm test:e2e` | Passed; 12 existing desktop/mobile browser tests using their mocked API setup |
| `node scripts/smoke-local.mjs` | Passed; 28 real same-origin checks against running Spring Boot/PostgreSQL, including UC01-UC05 normal flows and denied/overlap cases |
| `scripts/check-local-data.ps1` | Passed; idempotent provisioning/password preservation, wrong database refusal, unrelated-data refusal with existing records preserved |
| `node scripts/check-local-browser.mjs` | Passed; six real-browser checks, desktop/mobile screenshots, document/body width both 390 at a 390-pixel viewport, no uncaught browser exception |
| Stop/restart | Passed; only registered processes stopped, database/credentials and real reservation history retained; subsequent real browser login succeeded |
| GitHub CI / member review / cloud | Pending; no main merge or cloud change in this delivery |

The initial startup attempts exposed Windows PowerShell stderr/exit-code handling and interrupted-start recovery issues; those were fixed and successful startup was observed. An initial full regression attempt could not clean the running JAR; the launcher now runs a separate runtime copy and the complete regression passed afterward. Browser inspection found an absolutely positioned hidden table label expanding the mobile document; pinning that hidden label to its containing origin restored the document width. Test request pagination was corrected to the existing API limit without changing server behavior.

## UI Evidence and Remaining Acceptance

- [Equipment workspace, desktop](01-equipment-desktop.png)
- [Persisted reservation history, desktop](02-reservations-desktop.png)
- [Equipment workspace, mobile](03-equipment-mobile.png)

The real API smoke covers login/logout/CSRF/RBAC, search, booking conflict, separate approval, checkout/return, fault assignment/diagnosis/repair completion, equipment availability recomputation and persisted notification delivery. It does not claim that every business exception, timer or concurrency case was exercised through the browser. Existing regression results remain distinct from the smoke and mocked browser suites. Cross-member acceptance for Issues #6-#9 remains open, and cloud release is tracked separately by #10. No meeting, teammate sign-off, account reinstatement or deployment success is fabricated.

Local access values, cluster data, process state and raw reports remain in ignored `.cache`/`target` paths and are not included in the PR. The original dirty maintenance checkout and its 11 uncommitted files were preserved.
