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

## 2026-10-15 Per-equipment maximum borrowing duration

Branch: `feature/liu-tongyao-uc01-equipment-limit` · Baseline: main `b72ee5b` (V005 from #40 merged)

### What was built

- `DurationPolicy` uses an item's own `max_borrow_duration` (V005) in place of the global seven days when the item has one; the minimum still applies. The violation keeps the code `too-long` and says "This equipment may be reserved for at most 2 hours."
- `ReservationRepository.lockEquipment` reads the limit inside the same locked read that already provides status and approval; `ReservationRuleContext` carries it to the policies.
- Reservation page: when the item has a limit, it says "This item can be reserved for at most 3 days.", and the client-side rules in `rules.ts` apply the same limit with the server's wording, so a too-long request is refused before any call. React Hook Form reads the resolver on every render, so the limit applies as soon as availability has loaded.
- Contract section 6 (`DurationPolicy` row) describes the item limit.

### Design notes

- **Item limit replaces, not tightens.** The issue defines NULL as "use the global default", so a set value is the item's limit in either direction: a projector may allow 2 days, a laptop 10 days. A longer item limit is honoured; the database only requires a positive interval.
- **Strategy pattern unchanged.** The limit is a fact in the rule context, like `requiresApproval`; `DurationPolicy` stays the single place that decides duration, and no new policy class was needed.
- **Same words in both places.** The client formats durations the way the server's `DurationPolicy.human` does (whole days, else whole hours, else minutes), so the message does not change between the instant check and the server's answer.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The form schema is built at the first render, before the equipment (and its limit) is loaded | The resolver was created once from `reservationSchema()` | Pass the limit on each render; React Hook Form 7.88 re-reads `resolver` from its options on every render (checked in its source) | Component test types a too-long range after load and gets the item message without a request |

### Verification

Base `b72ee5b` plus this change set, in a separate worktree; Windows 11, Java 25.0.4 (target 17), Node 24.20.0, PostgreSQL 18.6.

| Check | Result |
| --- | --- |
| `./mvnw -B test` with `SERMS_APP_TEST_JDBC_URL` | Passed: 575 (573 before, 2 new: policy unit test for the item limit, PostgreSQL submit with a two-hour item) |
| `pnpm lint`, `typecheck`, `test`, `build` | Passed: 128 component tests (2 new) |
| `pnpm test:e2e` | Passed: 12 |

### Follow-ups

- #17 (Zhou Fanhao): the equipment management page sets `max_borrow_duration`; until then it is set in the demo data (camera 3 days, projector 2 days, laptop 5 days).

### Authorship

Implementation, tests and this section were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. No teammate review is claimed.
