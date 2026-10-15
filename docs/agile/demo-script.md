# Demo Script

Used from Sprint 3 for Sprint Reviews and the final demo on Mon 2026-10-26. Target: 10 minutes, connecting five use cases in business order; each owner presents their part. Member 1 keeps this script in step with the system; last revised 2026-10-15 against `main` `f9596c1`.

## 1. Preparation

| Item | Content |
| --- | --- |
| Environment | Primary: the local demo ([docs/local-demo.md](../local-demo.md)) until the cloud release in #10 is accepted; then https://serms.midas.cyou with the local demo as fallback |
| Accounts | Local-only demo accounts: `borrower@example.test` (A), `borrower2@example.test` (B), `approver@example.test`, `custodian@example.test`, `maintainer@example.test`, `admin@example.test`. The password is generated into `.cache/local-demo/access.json`; never show it on screen or commit it |
| Equipment (seeded) | Professional tripod `DEMO-TRI-01` (no approval), Sony mirrorless camera `DEMO-CAM-01` (approval, 3-day limit), Studio lighting kit `DEMO-LIG-01` (for the fault), Retired camera `DEMO-CAM-02` (hidden) |
| Browser | Two windows side by side (borrowers A and B) |
| Rehearsal check | `node scripts/check-uc01-browser.mjs` runs steps 2-8 unattended in about 15 seconds; run it before presenting |
| Fallback material | [UC01 screenshots](../evidence/uc01/), [local demo evidence](../evidence/local-demo/verification.md), CI reports, Playwright traces |

## 2. Flow

| Step | Presenter | Action | Explanation |
| --- | --- | --- | --- |
| 0 | Member 1 | Open Issue #27 and the [backlog](backlog.md) | Requirement → Issue → PR → test traceability |
| 1 | Member 2 | Borrower A signs in | Session + CSRF; role-based menus, backend authorization |
| 2 | Member 1 | A: Find equipment, Category Photography, tomorrow 09:00–11:00 | Free / Not free per item; Retired camera is not listed; the URL keeps the criteria |
| 3 | Member 1 | A reserves the Professional tripod from the result | Window prefilled; next 14 days' taken slots without names; Confirmed immediately |
| 4 | Member 1 | B opens A's search link | Tripod now Not free |
| 5 | Member 1 | B asks for 10:00–12:00 | "Already reserved for part of that time" (409); PostgreSQL exclusion constraint keeps one winner even for parallel requests (see tests) |
| 6 | Member 1 | B asks for 11:00–12:00 | Confirmed: half-open intervals do not overlap at 11:00 |
| 7 | Member 1 | B asks for the camera for 4 days | Stopped before sending: this item allows at most 3 days (per-item limit, Strategy rules) |
| 8 | Member 1 | B requests the camera for 2 hours | Waiting for approval; `ReservationSubmitted` notifies approvers; audit row written |
| 9 | Member 2 | Approver approves or rejects with a reason | No self-approval; rejection releases the slot |
| 10 | Member 5 | B opens the inbox | Decision notification and unread count |
| 11 | Member 3 | Custodian checks out A's tripod | The reservation can no longer be cancelled (rule 4) |
| 12 | Member 3 | Record a damaged return | Automatic maintenance case; equipment under maintenance |
| 13 | Member 4 | Technician resolves the case | Not reservable during repair; available again afterwards |
| 14 | Member 5 | Custodian opens the overdue view | Overdue loans ordered by duration; scanner, deduplication, retry |
| 15 | Member 1 | A, holding an overdue loan, tries to reserve | Refused with the overdue-loan rule (rule 11) |
| 16 | Member 5 | Open the latest `main` Actions run | Tests → scans → build → gated deployment |
| 17 | Member 4 | Open test and coverage reports | Individual test benches, coverage ≥ 70% |

Step 15 needs a loan that is already overdue; prepare it before the demo with the same seeded data Member 5 uses for step 14.

## 3. If Problems Occur

- Server unavailable: switch to the local demo.
- A step fails: show its screenshot and the test that covers it. UC01 exception flows each have a test ([UC01 section 1.2](../uc01-search-and-reserve.md#12-alternative-and-exception-flows)).
- Leftover reservations from a rehearsal: cancel them in My reservations; `check-uc01-browser.mjs` cancels its own.

## 4. UC01 Demonstration Readiness (2026-10-15)

Steps 2-8 passed in a real browser against the local demo on desktop and mobile, 16 of 16 checks ([record](../evidence/uc01/verification.md)). Step 15 is covered by `ReservationPostgresTest` on PostgreSQL 18 and has not yet been rehearsed in the browser.
