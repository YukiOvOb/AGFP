# Reports — Verification (Issue #23)

Recorded on 2026-10-14 (Asia/Singapore) by an AI agent working for Liu Tongyao. Branch `feature/liu-tongyao-reports` with main `aa86ef3` merged in. Environment: Windows 11, Java 25 building for Java 17, Node 24.20.0, pnpm 11.19.0, PostgreSQL 18.6, Playwright Chromium 1243. Agent checks are supporting evidence, not a member check.

## Results

| Check | Command or method | Result |
| --- | --- | --- |
| Backend suite | `./mvnw -B test`, external PostgreSQL 18.6 test database through `SERMS_APP_TEST_JDBC_URL` | Passed: 563 tests (544 before, 19 new) |
| Report SQL on real PostgreSQL | `ReportPostgresTest` | Reservations by category and status, loaned time inside a window (items still out, returned, idle, retired), overdue at a given time, late returns by category, maintenance ranking and limit, service combining every section |
| Endpoint | `ReportApiTest` (MockMvc with the real security configuration) | `ADMIN` 200 with every section; `BORROWER`, `APPROVER`, `CUSTODIAN`, `MAINTAINER` 403; anonymous 401; reversed, over-366-day, malformed and missing period 400 |
| Frontend | `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` | Passed: 121 component tests (9 new for the page and period logic); the page and the chart library are a separate 409 kB chunk, the main bundle is unchanged |
| Mocked browser suite | `pnpm test:e2e` | Passed: 12 |
| Real demo, administrator | Playwright against the local demo at 1440, 820 and 390 wide | 200 responses in 0.16-0.38 s including the chunk load; no horizontal overflow; bars, tooltip and table tab work |
| Real demo, borrower | Same | No menu entry; direct visit refused by the page; API returns 403 |
| Real UC01/UC04 checks on this branch | `scripts/check-uc01-uc04.mjs`, `scripts/check-uc01-uc04-browser.mjs` | Passed: 24 and 10 |
| Not run | `scripts/check-local-browser.mjs` | Its precondition is a passing API smoke run, and the demo database has no unbooked approval-required equipment left after earlier smoke runs; unrelated to this change |

## Load test (Proposal 7.2: basic reports within 5 seconds)

A separate backend instance (port 8085) ran against a throw-away database `serms_perf`, migrated by the application's own Flyway and then filled by a SQL script with triggers switched off for that session (constraints still applied):

| Data | Rows |
| --- | --- |
| Equipment (12 categories, 12 retired) | 600 |
| Reservations over the past year | 60,000 |
| Loans (about 15 % late, 521 overdue now) | 39,160 |
| Maintenance cases in every state | 8,000 |

`GET /api/v1/reports` as an administrator, five calls per period, measured with `curl` (`time_total`):

| Period | Times (s) |
| --- | --- |
| 30 days | 0.129, 0.056, 0.053, 0.051, 0.050 |
| 90 days | 0.056, 0.058, 0.059, 0.056, 0.066 |
| 366 days (the maximum) | 0.148, 0.148, 0.139, 0.138, 0.139 |

The slowest call is about 35 times inside the 5-second target. The 366-day response is 284 kB (600 items and 521 overdue loans). The load-test database, backend and frontend were removed afterwards.

The generated data contains loans of one item that overlap in time, which the schema's triggers would refuse in normal use; for those items utilisation reaches the 100 % cap. The screenshot `load-test-desktop.png` shows this.

## Screenshots

| File | Shows |
| --- | --- |
| `demo-desktop.png` | Reports for the local demo data, administrator, 1440 wide |
| `demo-mobile.png` | Same at 390 wide |
| `demo-tooltip.png` | Hover on a bar: value first, then the category |
| `load-test-desktop.png` | Twelve months of the load-test data |
