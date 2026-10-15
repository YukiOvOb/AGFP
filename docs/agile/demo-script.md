# Demo Script

Used from Sprint 3 for Sprint Reviews and the final demo. Target: 10 minutes, connecting five use cases in business order; each owner presents their part.

## 1. Preparation

| Item | Content |
| --- | --- |
| Environment | Primary: https://serms.midas.cyou (automatic post-merge deployment); fallback: local `docker compose up` |
| Accounts | Two borrowers, one each approver/custodian/technician/admin (Member 3 seeds before demo; no passwords in repository) |
| Equipment | At least a camera without approval, an oscilloscope requiring approval, an item under maintenance, a retired item |
| Browser | Two windows (borrowers A/B) for conflicts |
| Fallback material | `docs/evidence/` screenshots, CI reports, Playwright recordings (`pnpm test:e2e` trace) |

## 2. Flow

| Step | Presenter | Action | Explanation |
| --- | --- | --- | --- |
| 0 | Member 1 | Open board/backlog | Requirement → task → PR → test traceability |
| 1 | Member 2 | Borrower A logs in | Session + CSRF; role-based menus, backend authorization |
| 2 | Member 1 | Find equipment: Camera, tomorrow 09:00–11:00 | Free / Not free, URL preserves criteria (share with B) |
| 3 | Member 1 | Select available-only, open camera reservation | Next 14 days' busy slots; no borrower identity |
| 4 | Member 1 | Submit 09:00–11:00 | Immediate confirmation; My reservations |
| 5 | Member 1 | B opens shared link, reserves 10:00–12:00 | 409 overlap; exclusion constraint ensures one concurrent winner (see tests) |
| 6 | Member 1 | B changes to 11:00–12:00 | Success: half-open intervals do not overlap at 11:00 |
| 7 | Member 1 | B reserves 8 days | 422, maximum 7 days; configurable Strategy rules |
| 8 | Member 1 | A reserves oscilloscope | Pending approval; `ReservationSubmitted` → notify approvers |
| 9 | Member 2 | Approver approves/rejects with reason | No self-approval; rejection releases slot |
| 10 | Member 5 | A opens inbox | Decision notification and unread count |
| 11 | Member 3 | Custodian checks out A's camera | Reservation cannot be cancelled (rule 4) |
| 12 | Member 3 | Record damaged return | Automatic maintenance case; equipment under maintenance |
| 13 | Member 4 | Technician resolves case | Not free during maintenance; available after repair |
| 14 | Member 5 | Demonstrate overdue reminder with seeded overdue loan | Scheduled scanning, deduplication, retry |
| 15 | Member 5 | Open latest main Actions run | Tests → scans → build → deployment → verification |
| 16 | Member 4 | Open test/coverage reports | Individual benches, coverage ≥ 70% |

## 3. If Problems Occur

- Site unavailable: switch to local Compose.
- Failed step: show screenshots and corresponding tests (UC01 exception flows all have tests; [Section 1.2](../uc01-search-and-reserve.md#12-alternative-and-exception-flows)).

## 4. Current UC01 Demonstration Readiness (2026-10-05)

Before risk 1 (migrations into `app/`) is resolved, demonstrate UC01 in either way:

1. `cd frontend && pnpm test:e2e`: login → search → reserve → cancel in a real browser (mock API data).
2. `cd app && ./mvnw verify`: 29 PostgreSQL 18 integration tests, including 8 concurrent requests for a slot.
