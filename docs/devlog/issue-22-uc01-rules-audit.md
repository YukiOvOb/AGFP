# UC01: Overdue-Borrower Rule and Reservation Audit (#22)

Owner: Liu Tongyao (@cpg25ncu), executing UC01 for Shi Wenqi (D-004) · Requirement: Proposal 5.3, 5.7, business rules 9 and 11 · Branch: `feature/liu-tongyao-uc01-rules-audit` · Baseline: main `fa266a5`

## 2026-10-14 The parts that do not depend on #17

### What was built

- **`OverdueLoanPolicy`** (Strategy, next to the existing four policies): a borrower who has an `ACTIVE` loan past its `due_at` gets the violation `overdue-loan` and no reservation. It is reported together with the other violations in the existing 422 response, so the reservation page shows it without frontend changes. Setting `serms.reservation.block-overdue-borrowers` (default `true`) removes the policy from the rule set.
- **Audit records** for reservation submit (`RESERVATION_SUBMIT`) and cancel (`RESERVATION_CANCEL`) in `serms.audit_log`, written in the same transaction as the booking change, with the borrower as actor.
- `ReservationRepository.hasOverdueLoan` and `ReservationRepository.audit`; the rule context carries the overdue fact.
- Contract `docs/contracts/equipment-and-reservation.md`: new code, new rule row, processing steps and a section on audit records.

Not in this change: the per-equipment duration limit, which needs the `max_borrow_duration` column from #17.

### Design notes

- **Where the fact is read.** The overdue check runs inside the booking transaction after the requester row is locked, like the quota count, so the policy stays a pure function of the context and is unit-tested without a database.
- **Audit in the repository, not a listener.** The technology document plans an audit module that listens to domain events. No such module exists; loans, approvals and maintenance each write their audit row next to the change. Reservations follow the same pattern so a reader finds every audit write the same way. If an audit module is built later, the two calls move into a listener without changing the rows.
- **Summary text.** `change_summary` holds the status, equipment and slot (`from=` status on cancel) so an administrator reading the future audit viewer (#18) can tell what happened without joining tables.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| An overdue loan could not be created in a test | The database triggers require a reservation that has not ended and a loan due exactly at that end, so "already overdue" cannot be inserted | Create a loan due in one hour and evaluate the booking with a service whose clock is two hours ahead, inside a real transaction | The integration test sees `overdue-loan`, then a successful booking after the return |
| No local PostgreSQL integration tests: 30 errors in the full suite | No Docker on this machine, so Testcontainers cannot start | `SermsPostgres` already accepts an external database through `SERMS_APP_TEST_JDBC_URL`; started a separate throw-away PostgreSQL 18 cluster on port 55441 with database `serms_test_uc01` | Full suite: 544 tests, 0 failures, 0 errors |
| Second full run: all 33 integration tests errored | `SermsPostgres` runs V001 and V002 on the external database every time; on an already migrated database the scripts fail | Drop and recreate the test database before each run | Clean rerun passed |
| The shell command that started the test cluster never returned | `pg_ctl start` on Windows keeps the server attached to the command's output pipe | Ran the remaining steps separately; the launcher script uses `Start-Process` with redirected output for the same reason | Server listening on 55441 |
| Shell edits with an apostrophe in the text failed to parse | Quoting of the agent tool's shell | Wrote the edit scripts to files | Edits applied |

### Verification

- `./mvnw -B test` with `SERMS_APP_TEST_JDBC_URL` pointing at a fresh PostgreSQL 18.6 database: **544 tests, 0 failures, 0 errors** (536 before; 8 new: 2 policy, 3 service, 3 PostgreSQL).
- New PostgreSQL tests: overdue borrower refused and unblocked after return, another borrower unaffected; submit and cancel each write one audit row with the borrower as actor; a refused booking writes none.
- Not run locally: the failsafe `LoanPostgresIT` and the separate maintenance profile; hosted CI runs them.
- Not checked in the running demo: making a loan overdue there needs real time to pass.

### Deviations

The audit rows are written directly rather than by an event-driven audit module (see design notes).

### Follow-ups

- Per-equipment duration limit once #17 adds `max_borrow_duration`.
- The audit viewer in #18 will show these rows.

### Authorship

Implementation, tests and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. No teammate review is claimed.
