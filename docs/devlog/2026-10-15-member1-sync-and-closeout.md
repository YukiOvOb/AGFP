# Repository Sync and Member 1 Sprint 3–4 Closeout

Owner: Member 1 (@YukiOvOb) · Requirement: team plan Sprint 3 "organize completion status and demo flow", Sprint 4 "consolidate tasks, Sprints and agile evidence" · Baseline: team `main` `0345a50`

## 2026-10-15 Bring `YukiOvOb/AGFP` up to the team repository

### What was done

- `YukiOvOb/AGFP` `main` (`0bf5b3b`) is an ancestor of the team repository's `main` (`0345a50`), so no history had to be rewritten. `main` there is protected, so the team history went in through its PR #14 (merge `b5b762a`); the resulting tree is identical to team `main`.
- 37 team branches were copied to `YukiOvOb/AGFP` without force. `feature/liu-tongyao-uc04-maintenance` exists in both with different histories and was left as it was.
- `YukiOvOb/AGFP` holds the server's deployment secrets, so keeping it in step with the team repository keeps the [gated release](../server-release.md) usable for #10.

### Verification

| Check | Result |
| --- | --- |
| `git diff origin/main team/main` after the merge | Empty |
| Required checks on `YukiOvOb/AGFP` PR #14 (head `0345a50`) | 12 of 12 passed |
| `Deploy` run 10 after the merge | Stopped in `release-gate` as designed (`SERMS_PRODUCTION_READY` is not set); the SSH job did not run, so the server is unchanged |

## 2026-10-15 Member 1 closeout documents

### What was built

- [UC01](../uc01-search-and-reserve.md): exception flows A12 (overdue borrower), A13 (item limit) and AU1 (audit) with their tests; sequence diagram (also [`uc01-sequence.mmd`](../diagrams/uc01-sequence.mmd)) shows the overdue check and the audit write; five Strategy policies; test bench counts from today's run; resolved 2026-10-05 limitations replaced by current ones.
- [Backlog](../agile/backlog.md): R1.1–R1.10 marked on `main`, R1.11 marked dropped by D-009, R1.12–R1.14 added; Member 1 Sprint 3/4 tasks updated.
- [Sprint status](../agile/sprint-status.md#member-1-completion-status-2026-10-15): merged PRs since 2026-10-14, use-case status, risks and next steps.
- [Demo script](../agile/demo-script.md): items match the demo data (tripod without approval, camera with approval and a 3-day limit); item-limit and overdue-rule steps added.
- [Agile evidence index](../agile/agile-evidence.md): Sprint 3 and the repository move, both PR lists, the 12 required checks, report checklist status.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Direct push of team `main` to `YukiOvOb/AGFP` refused | Branch protection requires a PR and checks | Opened PR #14 from a branch at `0345a50` and merged it after the checks | Tree diff empty |
| The demo script's camera step showed immediate confirmation | Demo data marks the camera as needing approval | Use the tripod for the confirmation/conflict steps and the camera for approval and the item limit | Checked against `scripts/demo-data.sql` |
| Playwright did not start locally | Installed browser build differs from the pinned Playwright | Relied on the hosted `frontend` check, which runs the same flow | `frontend` passed on PR #14 |

### Verification

On `0345a50`, Linux, Java 21, Node 22, PostgreSQL 18 (Testcontainers):

| Command | Result |
| --- | --- |
| `./mvnw test -Dtest='Reservation*Test,Equipment*Test'` | 86 UC01 tests (plus 7 matched tests of other modules), 0 failures/errors/skips |
| `vitest run src/features/equipment src/features/reservation` | 22 of 22 passed |
| `node scripts/check-collaboration.mjs` | Passed |

### Deviations

None in code. R1.11 stays out of scope (D-009).

### Follow-ups

- #10: server preparation and first gated release; then a deployed run of the demo script.
- Retrospective rows and member acceptance are recorded by the members themselves.

### Authorship

Documents prepared for Member 1 (@YukiOvOb) with an AI coding agent; no teammate review is claimed.
