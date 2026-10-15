# Contribution Record: Member 1 · Shi Wenqi (@YukiOvOb)

Use case: UC01 Search and reserve equipment. Shared responsibility: requirements, task management and Sprint progress ([Sprint plan](sprint-plan.md)). This record lists what the commit history shows; effort and hours are added by Shi Wenqi personally if the report needs them.

## Commits Authored as YukiOvOb

Commits before 2026-10-12 were made in `YukiOvOb/AGFP`, so the PR numbers in their subjects are AGFP numbers (see [agile evidence §2](agile-evidence.md#2-sprint-outputs)).

| Date | Commit | Sprint | Content | Size |
| --- | --- | --- | --- | --- |
| 2026-09-09 | `3aedc8b` | 0 | CI: documentation-only PRs still trigger required checks; live URL in README (AGFP #1) | 2 files |
| 2026-09-09 | `7e39485` | 0 | Welcome page as the first deployable application (AGFP #2) | 4 files, +193 |
| 2026-09-09 | `9ddd40a` | 0 | Live domain changed to serms.midas.cyou (AGFP #3) | 3 files |
| 2026-09-25 | `75e674a` | 0 | Unified technology stack and per-member test bench requirements (AGFP #5) | 2 files, +321 |
| 2026-09-25 | `a1fcf0b` | 0 | React + TypeScript + Vite frontend, PostgreSQL 18, full stack list (AGFP #6) | 2 files, +322 / −170 |
| 2026-10-01 | `553ea52` | 1 | Authentication and approval interface contract, UC02 (AGFP #7) | 3 files, +264 |
| 2026-10-07 | `0bf5b3b` | 1-2 | UC01 full stack (search, availability, reservation, cancellation, Strategy rules, PostgreSQL test bench, React pages, E2E) plus backlog, Sprint plan, Sprint status, demo script and agile evidence index (AGFP #11) | 79 files, +6190 |
| 2026-10-15 | this branch | 3-4 | UC01 real-browser acceptance script and evidence, UC01 analysis/design class diagrams, UC01 document refresh, Sprint status, backlog traceability, demo script, agile evidence index, this record | see the branch's PR |

To confirm by Shi Wenqi: `e190cdf` (2026-09-09, "ci: PR security gate and automatic deployment to the Hong Kong server", 7 files, +228) is authored as "Yuki" with a different e-mail address. Two commits of 2026-10-07 that connect UC01 to the UC02 approval port (`e25a3cd`, `5e0059f`) carry another author identity and are not counted here.

## Work Done by Others on Member 1's Behalf

While the YukiOvOb account was unavailable, Liu Tongyao executed UC01 completion work under [D-004](../collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution): overdue-borrower rule and audit (#32), V005 read fields (#42) and the per-item limit (#44), plus UC01 integration fixes in #14. These remain Liu Tongyao's contributions.

## Where to Find the Results

| Area | Location |
| --- | --- |
| Use case, diagrams, design patterns | [UC01](../uc01-search-and-reserve.md), [`docs/diagrams/uc01-*.mmd`](../diagrams/) |
| Interface contract | [Equipment and reservation contract](../contracts/equipment-and-reservation.md) |
| Code | `app/src/main/java/sg/edu/nus/serms/{equipment,reservation}/`, `frontend/src/features/{equipment,reservation}/` |
| Tests | `app/src/test/java/sg/edu/nus/serms/{equipment,reservation}/`, `frontend/src/features/{equipment,reservation}/*.test.tsx`, `frontend/e2e/reservation.spec.ts`, `scripts/check-uc01-browser.mjs` |
| Evidence | [UC01 verification](../evidence/uc01/verification.md), [`docs/evidence/uc01/`](../evidence/uc01/) |
| Agile management | [backlog](backlog.md), [Sprint plan](sprint-plan.md), [Sprint status](sprint-status.md), [demo script](demo-script.md), [agile evidence](agile-evidence.md) |
