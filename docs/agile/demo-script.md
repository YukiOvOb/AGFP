# Demo Script

Used from Sprint 3 for Sprint Reviews and the final demo. Target: 10 minutes, connecting five use cases in business order; each owner presents their part.

## 1. Preparation

| Item | Content |
| --- | --- |
| Environment | Primary: https://serms.midas.cyou once the gated release (#10, [server release](../server-release.md)) has run; fallback: the [local demo](../local-demo.md) |
| Accounts | Two borrowers, one each approver/custodian/technician/admin (local demo seeds them; first production admin via the bootstrap in [server release](../server-release.md); no passwords in repository) |
| Equipment | [Demo data](../../scripts/demo-data.sql): tripod `DEMO-TRI-01` (no approval, no item limit), camera `DEMO-CAM-01` (approval, item limit 3 days), an item under maintenance, retired camera `DEMO-CAM-02` |
| Browser | Two windows (borrowers A/B) for conflicts |
| Fallback material | `docs/evidence/` screenshots, CI reports, Playwright recordings (`pnpm test:e2e` trace) |

## 2. Flow

| Step | Presenter | Action | Explanation |
| --- | --- | --- | --- |
| 0 | Member 1 | Open board/backlog | Requirement → task → PR → test traceability |
| 1 | Member 2 | Borrower A logs in | Session + CSRF; role-based menus, backend authorization |
| 2 | Member 1 | Find equipment: Tripod, tomorrow 09:00–11:00 | Free / Not free, URL preserves criteria (share with B) |
| 3 | Member 1 | Select available-only, open tripod reservation | Next 14 days' busy slots; no borrower identity |
| 4 | Member 1 | Submit 09:00–11:00 | Immediate confirmation; My reservations |
| 5 | Member 1 | B opens shared link, reserves 10:00–12:00 | 409 overlap; exclusion constraint ensures one concurrent winner (see tests) |
| 6 | Member 1 | B changes to 11:00–12:00 | Success: half-open intervals do not overlap at 11:00 |
| 7 | Member 1 | B opens the camera and asks for 4 days | Page states "at most 3 days" and refuses before calling the server; API answers 422 `too-long` (item limit, V005) |
| 7a | Member 1 | B reserves the tripod for 8 days | 422, global maximum 7 days; all violations listed together (Strategy rules) |
| 8 | Member 1 | A reserves the camera for 2 hours | Pending approval; `ReservationSubmitted` → notify approvers |
| 9 | Member 2 | Approver approves/rejects with reason | No self-approval; rejection releases slot |
| 10 | Member 5 | A opens inbox | Decision notification and unread count |
| 11 | Member 3 | Custodian checks out A's tripod | Reservation cannot be cancelled (rule 4) |
| 12 | Member 3 | Record damaged return | Automatic maintenance case; equipment under maintenance |
| 13 | Member 4 | Technician resolves case | Not free during maintenance; available after repair |
| 14 | Member 5 | Demonstrate overdue reminder with seeded overdue loan | Scheduled scanning, deduplication, retry; overdue view |
| 14a | Member 1 | The overdue borrower tries a new reservation | 422 `overdue-loan` until the item is returned (rule 11) |
| 15 | Member 5 | Open latest main Actions run | Tests → scans → build → deployment → verification |
| 16 | Member 4 | Open test/coverage reports | Individual benches, coverage ≥ 70% |

## 3. If Problems Occur

- Site unavailable: switch to local Compose.
- Failed step: show screenshots and corresponding tests (UC01 exception flows all have tests; [Section 1.2](../uc01-search-and-reserve.md#12-alternative-and-exception-flows)).

## 4. UC01 Demonstration Readiness (2026-10-15)

All UC01 steps run on the integrated system (PostgreSQL 18, real login) in the [local demo](../local-demo.md); the cloud site follows #10. If a step cannot be shown live:

1. `cd frontend && pnpm test:e2e`: login → search → reserve → cancel in a real browser (mock API data; also runs in the `frontend` check).
2. `cd app && ./mvnw test -Dtest='Reservation*Test,Equipment*Test'`: the UC01 bench, including 8 concurrent requests for one slot, the overdue rule and the item limit on PostgreSQL 18 ([UC01 §6](../uc01-search-and-reserve.md#6-test-bench-and-execution)).
3. Step 14a needs a loan that is already overdue, which the running demo only has after real time passes; show `ReservationPostgresTest#borrowerWithAnOverdueLoanCannotReserveUntilItIsReturned` instead.
