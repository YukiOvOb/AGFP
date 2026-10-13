# UC01/UC04 Integrated Acceptance

Issues #5 and #9. Human owner/executor: Liu Tongyao, with agent assistance; original Shi Wenqi UC01 authorship and all five team roles remain preserved. Prepared on 2026-10-13 (Asia/Singapore).

Base main: `940bf42cd1b6b507d24179e8aa4065af14f72a0e`. Dependency: draft PR #11, `d42e0a616affeebe41a3537396ccea960633b30d`. Sole writer: this session, isolated managed worktree `codex/serms-issue-acceptance`; validation copy under the original checkout's ignored `.cache/issue-acceptance/source`. Explicit file hashes are checked during transfer; original dirty maintenance checkout is preserved. Actual delivery SHA is in the PR and local delivery manifest.

Read: `AGENTS.md`, `.github/CONTRIBUTING.md`, ownership/agent-workflow/decisions, testing, UC01/UC03 and authentication/approval contracts; `serms-task`, `serms-worktree`, `serms-verify`, `serms-pr`. User-authorized scope: review and complete assigned Issues; then explicit local acceptance/release preparation pending server handover. No business-contract, migration, authorization/role or coverage-policy changes.

## Findings and Repairs

- The catalogue existed but reporting still required manually transcribing a UUID and claimed lookup was unavailable. Borrower/Custodian catalogue actions now link to the existing report page with equipment selected; other roles do not receive that action. Input is validated, server existence/authorization checks remain authoritative, and success clears the form.
- Zod's RFC UUID validation rejected PostgreSQL/Java-compatible identifiers already used by the demo database. The form now accepts canonical UUID syntax, including those identifiers. Regression includes the actual demo format and malformed-parameter rejection.
- Windows `Process.ExitCode` may be null until refreshed. The PostgreSQL test launcher could report a successful start as failed and omit cleanup. It now registers cleanup before waiting, refreshes the process and checks `pg_ctl status`.
- Existing cancellation/approval delegation, assigned-technician checks, invalid loan handling, rollback and equipment recomputation passed. An initial lock-order suspicion did not establish a defect; business code was not changed on that basis.

## Current Verification

Windows: PostgreSQL 18.6; JDK 25.0.4 compiling unchanged Java 17 target; Maven 3.9.7; Node 24.19.0; installed pnpm 11.25.0 and unchanged package/lockfile. Hosted CI uses Java 17 and package-declared pnpm independently.

| Check / command | Result |
| --- | --- |
| `scripts/test-zhoufanhao.ps1 -PostgresBin <18 bin>` | Passed: database 27, application 536, UC03 integration 20; zero failures/errors/skips |
| Existing `MaintenancePostgresIT` on isolated PostgreSQL 18 with V001–V004 | Passed: 28 tests, zero failures/errors/skips; transactions, concurrency, assigned technicians, invalid/matching loans and equipment recovery |
| Frontend `pnpm lint`, `pnpm test`, `pnpm build`, `pnpm check:api` | Passed; 87 components, TypeScript/Vite build and actual backend OpenAPI alignment |
| Existing `pnpm exec playwright test --workers=2` | Passed: 12 mocked desktop/mobile cases; API mocks are not real DB evidence |
| `node scripts/check-uc01-uc04.mjs` | Passed: 24 real same-origin API checks against the isolated live PostgreSQL application |
| `node scripts/check-uc01-uc04-browser.mjs` | Passed: 10 real-browser checks across desktop and 390px mobile; no routes/API interception |
| Visual evidence | Six screenshots: real fault forms, repair actions and restored catalogue; mobile document overflow assertion passed |

API acceptance verifies search/reservation/cancel, immediate slot reuse, other-borrower privacy, CSRF, overlap and retired/faulty equipment, existing UC02 approval, no cancellation after checkout, no writes for missing/mismatched linked loans, authenticated reporter, denied assignment/unassigned work, diagnosis/repair/resolve/history, terminal immutability and reservability after repair. Browser acceptance performs search/reserve/cancel, catalogue-selected fault, custodian assignment, assigned-maintainer work and availability restoration at both widths. Existing test bench also covers retirement and multiple open cases/active loans.

UC04 real-database acceptance is now repeatable in the added `maintenance-postgres` hosted job. It applies complete current migrations and invokes the existing suite; no tests/checks are removed or weakened.

## Reproduction, Initial Failures and Remaining Acceptance

Use PR #11's [local launcher/runbook](../../local-demo.md), then run the two new scripts above. Each requires this checkout's own ignored runtime/access state and only uses loopback. Sanitized summaries are under `.cache/local-demo`; credentials/cookies remain ignored. Reserve/repair transactions stay as demonstration history. Browser reruns use unique purpose/fault markers; selected demo equipment must be free.

Initial failures remain in ignored logs/traces: Windows process-state misreport; eight-way local Playwright initialization timeout (all 12 passed at two workers); a fixture accidentally choosing the repair item as its supposedly different loan equipment (corrected, its own transaction completed with history retained); browser UUID validation/repeated-label issues (fixed and rerun). UC04 initially ran on historical V002, where version columns are int; current V004 widens them to bigint. Final evidence uses all current migrations without changing SQL or assertions.

Generated credentials/runtime/data and diagnostic traces are not tracked. Stop this session's isolated runtime using `scripts/stop-local.ps1`; keep data for repeatability. Other checkouts/services remain untouched. Latest-HEAD hosted CI and independent member acceptance are distinct from local passes. PR #11 must merge before this dependent delivery is retargeted/merged into main; do not trigger production before Issue #10 handover. No remote comments, merge, deployment, invented member approval, Issue closure or team Done claim.
