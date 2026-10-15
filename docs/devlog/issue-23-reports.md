# Reports Module and Charts (#23)

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 5.7 (reports), 7.2 (reports within 5 s) · Branch: `feature/liu-tongyao-reports` · Baseline: main `fa266a5`, `aa86ef3` merged in

## 2026-10-14 Backend, page and load test

### What was built

- **`report` module** (`app/.../report/{api,domain,repository,service}`): `GET /api/v1/reports?from&to` for `ADMIN` only, at most 366 days. One read in a `REPEATABLE READ` snapshot returns: reservations by category and status, utilisation per item and per category, loans overdue now, late returns by category, current maintenance cases by status, cases reported and completed in the period with average repair time, and the ten items with most cases.
- **Reports page** at `/reports`: period presets (7, 30, 90 days, 12 months) and a custom range, kept in the address; six headline figures; three single-series bar charts, each with a table tab; sortable tables for overdue loans, late returns, utilisation and maintenance. The menu entry reserved by #16 is switched on.
- Missing required query parameters now return 400 instead of 500 for every endpoint.
- `EmptyState` accepts a `className` for compact use inside cards.

### Design notes

- **What "utilisation" means.** Time on loan inside the measured part of the period divided by the time the items existed in it. "Measured part" stops at the time of the report, so a period that runs into the future is not diluted by days that have not happened. Retired items count only if they were on loan in the window.
- **One definition of overdue.** An `ACTIVE` loan past its `due_at`, the same as the reservation rule in #22 and the overdue view planned in #19.
- **Late returns** are counted over loans that fell due in the measured part of the period: returned after `due_at`, or still out.
- **Reuse, not duplication.** Status counts and average repair time come from the existing UC04 `MaintenanceReportService` (a public service call across modules), so the two report pages cannot disagree.
- **Hours in the API.** Durations are sent as decimal hours, which charts and tables use directly; the domain keeps `Duration`.
- **Charts follow the data-viz method**: the form was chosen first (headline numbers as stat tiles; one measure across categories as single-series horizontal bars; lists as tables), then colour. The brand green failed the chroma floor of the palette validator (reads grey), so `--chart-1` is `#1b7f57`, the same hue stepped up, which passes the lightness band, chroma floor and 3:1 contrast on white. Bars are 18 px with a 4 px rounded end, hairline solid grid, value labels at the bar tip, hover tooltip with the value first, and every chart has a table tab. A single series has no legend; the title names it. The app has no dark mode, so no dark palette was selected.
- **Bundle.** The page is lazy-loaded; Recharts lives in its own chunk that only administrators download.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| An idle item showed two hours on loan in a two-hour window | `LEAST` and `GREATEST` skip NULLs, so for rows without a loan the expression became the window length | `FILTER (WHERE l.loan_id IS NOT NULL)` on the sum | `ReportPostgresTest` caught it; passes now |
| The API test context failed to start | The application class already defines a bean named `clock` | Replace it with `@MockitoBean(name = "clock")` | 7 API tests pass |
| A missing `from` or `to` returned 500 | The global handler had no case for `MissingServletRequestParameterException` and fell through to "unexpected" | Added it to the 400 list | API test |
| Type error passing Recharts tooltip props to the custom tooltip | Recharts 3 tooltip content types are generic over value and name types | The tooltip takes only `active` and `payload` | `pnpm typecheck` |
| Very short loans and repairs showed "0 %" and "0 h" | Rounding of real but tiny values (the demo's smoke runs return items within seconds) | Show "<1%" and "< 0.1 h" for non-zero values below the rounding step | Unit tests |
| Empty sections were 330 px tall | The shared empty state is sized for a whole page | Compact class on the reports page | Screenshot |
| The demo launcher refused to start: "A local demo state exists" | The desktop app restart had killed the demo processes; `stop-local.ps1` then failed because the database was already stopped, so it kept the state file | Confirmed the recorded processes were gone and moved the state file aside | Demo started; data and passwords kept |
| Load data: duplicate keys, then every loan identical | A `LATERAL` subquery that does not reference the outer row is evaluated once, so `gen_random_uuid()` and `random()` returned one value | Draw per row (select-list call, or a lateral subquery that references the outer row) | Row counts and a plausible late rate (15 %) |
| `curl` reported status 000 for every timed call | The period file written by Python on Windows had CRLF line ends | Strip `\r` before use | 200 responses |

### Verification

See [the verification record](../evidence/reports/verification.md): backend 563 tests, frontend 121 component and 12 mocked browser tests, real demo at three widths, borrower refused, UC01/UC04 checks 24 and 10, and the load test: 366 days of 60,000 reservations in about 0.14 s.

### Deviations

- CSV export was not built (the task listed it as optional).
- Utilisation is time on loan, not time reserved; reservations are reported separately.

### Follow-ups

- `scripts/stop-local.ps1` should succeed when the demo database is already stopped (for example after a restart); noted for the launcher work in #30.
- #19's overdue page should use the same overdue definition; this report already does.

### Authorship

Implementation, tests, load test and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. No teammate review is claimed.
