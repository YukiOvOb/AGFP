# UC04: Fault Category and Priority, Readable Names on Maintenance Pages (#21)

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 5.6, backlog R4 · Branch: `feature/liu-tongyao-uc04-readable-names` · Baseline: main `310dd50`

## 2026-10-14 Readable names (part without a migration)

### What was built

- Maintenance case responses gain `equipmentName`, `equipmentAssetTag`, `reportedByName` and `assignedToName`; history responses gain `actorName`. The change is additive: every existing field stays, so older clients keep working.
- The case cards (open queue, my assigned cases, equipment lookup), the case page and the history timeline show "Professional tripod (DEMO-TRI-01)", "Alex Borrower" and "By Sam Maintainer" instead of UUIDs. Cards show the technician once a case is assigned. The equipment lookup is headed by the equipment name.
- Case ID and Loan ID stay on the case page: they are identifiers with no name.

The fault category and priority columns are not part of this step. Their migration V006 must follow #17's V005, which has not merged yet.

### Design notes

- **Port and Adapter.** `MaintenanceNameDirectory` (service port) is implemented by `JdbcMaintenanceNameDirectory`, like the existing `TechnicianDirectoryPort`. The domain model and the case and history repositories are unchanged; the names are a read concern only.
- **No N+1 queries.** `MaintenanceNames` collects the IDs of a whole response and asks the directory once for equipment labels and once for user names (`IN (:ids)` through `NamedParameterJdbcOperations`). Empty requests skip the database.
- **No new access path.** The controller looks names up only after the service call has passed method security, and only for IDs on the records it returns. `MaintenanceApiTest` checks that denied reads never reach the lookup.
- **Honest nulls.** The foreign keys make a missing name impossible in practice, but the schema marks the name fields nullable rather than promise what the lookup cannot guarantee. The frontend then shows the ID (`equipmentLabel`, `personLabel` in `format.ts`).
- **Why not join in the existing queries.** Joining names into the case repository SQL would put display data into `MaintenanceCase` and its snapshot tests; a separate read port keeps the write model and its concurrency tests untouched.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The controller had a public `history(UUID)` endpoint and a private `history(List)` helper | Overloads that differ only by parameter type read as one method | Helpers renamed `caseResponse`, `caseResponses`, `historyResponses` | Compiles; API tests |
| The real demo still served the old API after the build | The previous session's demo kept running from its own copy of the jar | Stopped it with `scripts/stop-local.ps1` (data and passwords kept) and restarted through the preview configuration | New fields present in live responses |
| The live queue could not be shown | The demo data has no `OPEN` case left | Checked the case page and the lookup on a resolved case instead; the queue card is covered by component tests | `docs/evidence/maintenance-names/` |
| `pg_isready` output was unreadable in the agent shell | Messages are localised for the Chinese Windows code page | Run with `LC_ALL=C` | Readable "accepting connections" |

### Verification

See [the verification record](../evidence/maintenance-names/verification.md): backend 568 tests and UC04 PostgreSQL integration 29 on real PostgreSQL 18.6; frontend 122 component and 12 mocked browser tests; generated API types in step; real demo checked as custodian and maintainer at 1440 and 390 wide.

### Deviations

None from the Proposal. The response change is additive and only the maintenance pages consume these endpoints, so no other module owner is affected.

### Follow-ups

- V006 `fault_category` and `priority` with CHECK constraints and defaults, the report-fault form, queue sorting and filtering by priority (after #17 merges V005).
- Defaults for the damaged-return path (UC03 calling `reportFault`), to be agreed with Zhou Fanhao (@zfh-usedname).
- The five-to-six state mapping note and contract section 9 marked "decided".
- The report form and the equipment lookup still take an equipment UUID as input. An equipment picker based on the catalogue API would remove the last UUID the user has to type; candidate for #26.

### Authorship

Implementation, tests, verification and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. No teammate review is claimed.

## 2026-10-14 Stale wording and a way into equipment history

Branch: `feature/liu-tongyao-uc04-stale-wording` · Baseline: main `76a38ea` (PR #36 merged)

### What was built

- **D9** (from the UC04 design review in PR #35): the equipment lookup said it was "interim" until the shared catalogue API existed. The catalogue API exists, so the text now says what the lookup does.
- Equipment names on open-queue cards and on the case page link to that item's maintenance history for custodians and administrators: `/maintenance?history=<equipmentId>` pre-fills the lookup, runs it and scrolls to it. Maintainers see the name without a link because the lookup is operator-only; cards inside the lookup are not linked to themselves. An invalid `history` value is ignored.
- **D12**: the `TechnicianDirectoryPort.ensureTechnician` comment called the adapter future work; it now states the contract that `JdbcTechnicianDirectoryAdapter` already implements. The `MaintenanceHistoryRepository` comment said audit_log was the "intended" source; it is the actual one.

### Design notes

- **Why a link and not a picker.** After #36 no page shows an equipment UUID any more, so a lookup that only accepts a typed UUID was effectively unusable. A catalogue-based picker would not do: the catalogue search excludes `RETIRED` equipment, and unrepairable cases are exactly the ones whose history matters. Every case already carries its equipment ID, so linking from the case reaches retired items too, with no backend change.
- **Remount instead of syncing state.** The home page passes the validated `history` value as the lookup's `key` and initial value, so a new value starts a fresh lookup without effects that copy URL state into component state.
- The comment `Shared request correlation integration remains pending` in `JdbcMaintenanceHistoryRepository` was checked and left: every module still generates its own `request_id`.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Following the link landed at the bottom of the history, not at the lookup | The scroll ran before the results loaded; the browser's scroll anchoring then kept the page bottom in view while results were inserted above it | Scroll once, after the cases have loaded | Real demo: `scrollY` 1818 before, 1029 after, lookup heading at the top (`06-history-link-custodian.png`) |
| Mocked browser suite failed 5 of 12 at the sign-in page (an earlier run had failed 1, cause unconfirmed because its trace was overwritten) | The browser logged `net::ERR_NETWORK_CHANGED`: Windows reported a network change mid-run and Chromium aborted module loads, so the app never rendered | No code change; reran | Runs before and after passed 12 of 12; the trace showed the network error |

### Verification

Frontend 126 component tests (4 new: link from a case into the lookup, queue link, no links for maintainers, invalid parameter), lint, typecheck, build and 12 mocked browser tests; backend maintenance and JDBC unit tests 331 (comment-only changes). Real demo: see [the verification record](../evidence/maintenance-names/verification.md#stale-wording-and-history-link).

### Follow-ups

- The report form still takes an equipment UUID; selecting from the catalogue already fills it. A picker remains a candidate for #26 once a search that includes retired items exists (for example #17's equipment management list).

### Authorship

Changes, tests and this section were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. The D9/D12 findings came from Codex's UC04 design review (PR #35). No teammate review is claimed.
