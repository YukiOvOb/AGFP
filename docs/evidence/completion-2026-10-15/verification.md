# Completion Work of 2026-10-15 — Verification on the Local Demo

Recorded on 2026-10-15 at about 12:00 (Asia/Singapore) by an AI agent working for Liu Tongyao, after #42 (#40), #43 (#41), #44 (#22) and #45 (#21) merged. The local demo was restarted with a backend jar built from main `2e9eda3`, so Flyway applied V005 and V006 to the existing demo database, and the demo seed filled the new equipment columns. The check is read-only: it creates no reservation and no fault report. Agent checks are supporting evidence, not a member check.

Per-change verification (unit, API, real PostgreSQL, browser suites) is in each Issue's development log: [#40](../../devlog/issue-40-v005-equipment-schema.md), [#41](../../devlog/issue-41-first-admin-bootstrap.md), [#22](../../devlog/issue-22-uc01-rules-audit.md), [#21](../../devlog/issue-21-uc04-fault-fields.md).

## Results

Environment: Windows 11, Java 25.0.4, Node 24.20.0, PostgreSQL 18.6 (demo cluster), Playwright 1.63.0. The demo had stopped overnight; its stale state file was moved aside before the restart, with data and access file kept.

| Check | Result |
| --- | --- |
| Flyway on the existing demo database | Applied V005 and V006; `serms.schema_version` is 6; the 15 existing maintenance cases have `OTHER` / `MEDIUM` |
| Demo seed after migration | Serial numbers and descriptions filled for all 8 items; maximum durations camera 3 days, projector 2 days, laptop 5 days; retired camera condition `POOR` |
| #40 read fields (borrower, `GET /api/v1/equipment?q=camera`) | Camera has `maxBorrowDuration` `PT72H` and its description |
| #22 item limit (borrower, reservation page) | Page states "This item can be reserved for at most 3 days."; a four-day range shows "This equipment may be reserved for at most 3 days." and no reservation request is sent |
| #21 report form and queue (custodian) | Category select offers the six values with `Other` preset; priority preset `Medium`; queue shows the priority filter and sort |
| #21 existing case (custodian, case page) | Shows fault category and priority |
| Browser errors | None |
| Not checked here | #41 (the demo already has an administrator; the bootstrap was checked on an empty database, see its log) |

## Screenshots

| File | Shows |
| --- | --- |
| `01-reserve-item-limit.png` | Reservation page for the demo camera: the item's limit is stated and a four-day request is refused before any request is sent |
| `02-report-form-and-queue.png` | Custodian workspace: fault category and priority in the report form, priority filter and sort on the open queue |
| `03-case-fault-fields.png` | Case details with fault category and priority (an existing case, so `Other` / `Medium` from V006) |
