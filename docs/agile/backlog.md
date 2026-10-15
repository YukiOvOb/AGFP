# Requirements and Tasks (Product Backlog)

Sources: Project Proposal Chapter 5 (functional), Chapter 6 (business rules), Chapter 7 (non-functional), Section 8.3 (DevSecOps), team ownership, and [Technology stack](../tech-stack.md). Requirement IDs are stable and referenced by tasks, PRs, and tests.

Current tracking (2026-10-15): `main` `f9596c1` contains UC01-UC05, the notification platform and overdue view (#12), DevSecOps checks (#39), reports (#33), V005 equipment master data (#42), the first-admin bootstrap (#43), the per-item limit (#44) and UC04 fault fields (#45). Remaining tasks, owners and deadlines are on [Issue #27](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27) ([D-007](../collaboration/decisions.md#d-007-completion-task-ownership), [D-012](../collaboration/decisions.md#d-012-24-hour-completion-rule)). Shi Wenqi resumed Member 1 work on 2026-10-15 ([D-013](../collaboration/decisions.md#d-013-shi-wenqi-resumes-member-1-work)); work Liu Tongyao executed on Member 1's behalf under [D-004](../collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution) stays recorded as Liu Tongyao's. Cloud deployment remains last ([D-005](../collaboration/decisions.md#d-005-local-closeout-and-cloud-last)). Historical Sprint task tables below are kept as written; the Status columns of section 2 are current.

## 1. Ownership

Each member owns one full-stack feature (queries, backend, frontend, test bench) and a shared responsibility.

| Member | Business feature | Shared responsibility |
| --- | --- | --- |
| Member 1 Shi Wenqi | UC01 Search/reserve equipment | Requirements, task management, Sprint progress |
| Member 2 Zhang Hanming | UC02 Review/approve reservations | Architecture, frontend templates, UI consistency |
| Member 3 Zhou Fanhao | UC03 Equipment checkout/return | Database, domain model, system integration |
| Member 4 Liu Tongyao | UC04 Fault reports/maintenance | Testing, security, quality checks |
| Member 5 Wang Yuanmeng | UC05 Overdue detection/notifications | Git, CI/CD, deployment, release evidence |

2026-10-15: the shared-responsibility column again follows the team division of work, as do the [Sprint plan](sprint-plan.md) and [ownership boundaries](../collaboration/ownership.md); the 2026-10-07 edit of this table had swapped them. This restores consistency between documents and does not reassign completion tasks, which follow [D-007](../collaboration/decisions.md#d-007-completion-task-ownership). Historical task Member numbers/authorship below remain for traceability.

Shared authentication, roles, templates, and tables are prerequisites for all use cases and follow Sprint 1 order.

## 2. Requirements

Priority: **M** = Must (course acceptance), **S** = Should, **C** = Could (time permitting).

### 2.1 Shared / Platform

| ID | Requirement | Source | Priority | Owner |
| --- | --- | --- | --- | --- |
| R-P1 | Login/logout, session cookie, CSRF | 5.1, 7.1 | M | Member 5 (platform branch implementation) / Member 2 architecture review |
| R-P2 | Five roles, multiple roles per user, backend authorization | 3, 5.1 | M | Member 2 |
| R-P3 | Admin account/role management | 5.1 | S | Member 2 |
| R-P4 | Equipment records, unique tags/serials, retired history | 5.2 | M | Member 3 |
| R-P5 | Shared layouts, forms, tables, status badges | 7.4 | M | Member 2 |
| R-P6 | Append-only audits | 5.7, rule 9 | S | Member 3 |
| R-P7 | Reports | 5.7 | S | Member 4 |
| R-P8 | CI compilation, tests, coverage ≥ 70%, static/dependency/image scans, deployment/verification | 8.3 | M | Member 5 |

### 2.2 UC01 Search and Reserve Equipment (Member 1)

| ID | User story | Acceptance | Priority | Status | Tests / evidence |
| --- | --- | --- | --- | --- | --- |
| R1.1 | As a borrower, search by name/category/location | Case-insensitive substring search; exclude retired; pagination | M | ✅ main | `ReservationPostgresTest.Search`; `search.test.tsx`; real browser |
| R1.2 | View available equipment in an interval | Available/unavailable per result, available-only filter, same rules as submission | M | ✅ main | `Search#reportsAvailabilityForTheRequestedWindowAndCanShowOnlyFreeItems`; real browser |
| R1.3 | View occupied slots for an item | Next 14 days, no occupant identity | M | ✅ main | `Search#availabilityListsHeldSlotsWithoutRevealingWhoHoldsThem`; real browser |
| R1.4 | Reserve a slot | Confirm without approval, otherwise pending; visible in My reservations | M | ✅ main | `Submit#equipmentWithoutApprovalIsConfirmedImmediately`, `#equipmentThatNeedsApprovalStartsPending`; real browser |
| R1.5 | No overlapping slots for an item (rules 1, 12) | Includes pending approval; one concurrent winner | M | ✅ main | `Submit#parallelRequestsForTheSameSlotBookItExactlyOnce`; real browser (two borrowers) |
| R1.6 | No booking equipment under maintenance / with open faults / retired (rules 2, 8) | 409 / 404 with explanation | M | ✅ main | `Submit#equipmentUnderMaintenanceOrWithAnOpenFaultCannotBeReserved`, `#retiredOrUnknownEquipmentIsNotFound` |
| R1.7 | Duration/quota rules (10, 11), per-item limit, overdue borrowers | All violations returned together; configurable values; item limit replaces the global maximum | M | ✅ main (item limit #44, overdue rule #32) | `ReservationPolicyTest`; `#anItemsMaximumBorrowingDurationIsEnforced`; `OverdueAndAudit#borrowerWithAnOverdueLoanCannotReserveUntilItIsReturned`; real browser |
| R1.8 | Cancel reservations (rule 4) | Pending/confirmed may cancel and release slot; no cancellation after checkout | M | ✅ main | `ReservationPostgresTest.Cancel`; real browser |
| R1.9 | View personal reservations | Own records only, status filters | M | ✅ main | `MyReservations#listsOnlyMyReservationsNewestStartFirstAndFiltersByStatus` |
| R1.10 | Share search links | Criteria stored in URL | S | ✅ main | `search.test.tsx` › restores a shared search link; real browser |
| R1.11 | Automatically cancel expired pending reservations | Scheduler sets `CANCELLED` | C | Dropped ([D-009](../collaboration/decisions.md#d-009-scope-freeze)) | — |
| R1.12 | Audit submit and cancel (rule 9, Proposal 5.7) | One audit row per success in the same transaction; none for refusals | S | ✅ main (#32) | `OverdueAndAudit#submitAndCancelAreAuditedWithTheBorrowerAsActor`, `#aRefusedBookingLeavesNoAuditRecord` |

"Real browser" is `scripts/check-uc01-browser.mjs` on the local demo; results in the [UC01 verification record](../evidence/uc01/verification.md).

### 2.3 Other Use Cases (Owners Maintain Detailed Stories)

| ID | Use case | Key requirements | Owner | Progress |
| --- | --- | --- | --- | --- |
| R2 | UC02 Review/approve | Direct confirmation if unnecessary; approve; rejection reason/slot release; no self-approval; immutable decisions | Member 2 | On main; Issue #8 acceptance and draft #46 (coverage gate, real UC02 flows) open |
| R3 | UC03 Checkout/return | Validate checkout, record custodian, return condition, damage to maintenance | Zhou Fanhao | Backend/pages/V004 on main; Issue #6 validation and member acceptance open |
| R4 | UC04 Faults/maintenance | Fault blocks booking, assign technician, transitions, repaired availability/unrepairable retirement, history | Member 4 | On main with fault category/priority (#45); Issue #9 acceptance and #21 confirmation open |
| R5 | UC05 Overdue/notifications | Due-soon/overdue, retries/limit, deduplication, cancel after return, own inbox | Member 5 | Scanner, events and overdue view merged (#12); Issue #7 and #19 acceptance open |

### 2.4 UC03 Stories and Acceptance

Owner: Zhou Fanhao. He confirms effort estimates and actual hours; AI must not infer hours. See [Delivery](../zhoufanhao-delivery.md) for code/design/evidence.

| ID | User story | Acceptance | Priority | Status |
| --- | --- | --- | --- | --- |
| R3.1 | Custodian verifies borrower reservation and issues equipment | CONFIRMED, matching identity, valid window, AVAILABLE, no active loan/maintenance, current version; actual actor recorded | M | Local implementation; review/deployment pending |
| R3.2 | Reject invalid/duplicate checkout | No partial records for unapproved/cancelled/early/expired/disabled/fault/concurrent cases | M | Local implementation; review/deployment pending |
| R3.3 | Record normal/overdue return | ACTIVE→RETURNED, actual receipt/late facts, original slot retained on early return | M | Local implementation; review/deployment pending |
| R3.4 | Record damaged return | Required damage note, linked OPEN case, atomic audit/state | M | Local implementation; review/deployment pending |
| R3.5 | Borrower reads personal loans | Owner-filtered lists/details, no handover writes | M | Local implementation; review/deployment pending |
| R3.6 | Preserve trusted handover/state history | Equipment-first locking, version conditions, rollback, immutable success audit | M | Local implementation; review/deployment pending |
| R3.7 | Connect five use cases | Shared PostgreSQL profile, approval persistence, Identity, maintenance, reminder Guard | M | Local integration; team validation pending |

## 3. Tasks by Sprint

Status: ✅ complete, 🔶 in progress / on a branch, ⬜ not started. Owner means assigned responsibility, not authorship; PRs establish authorship. See [Sprint plan Section 2](sprint-plan.md#2-definition-of-done) for DoD.

### Sprint 0 — Foundations and Rules

| Task | Assigned owner | Status | Evidence |
| --- | --- | --- | --- |
| Requirements/tasks (this file) | Member 1 | ✅ | This file |
| UC01 flow/activity/sequence | Member 1 | ✅ | [UC01](../uc01-search-and-reserve.md) |
| Shared stack/layers/APIs | Member 2 | ✅ | [tech-stack.md](../tech-stack.md) (PR #5, #6 drafted/submitted by Member 1) |
| Database/domain/states | Member 3 | ✅ | `database/` V001 + V002 (PR #10) |
| Testing/security/quality | Member 4 | 🔶 | README / technology stack §5; shared Testcontainers base not submitted |
| Repository/protection/security CI/deployment | Member 5 | ✅ | `ci.yml`, `deploy.yml` (PR #1–#3 by Member 1) |
| Authentication/approval contract | Member 2 | ✅ | [auth-and-approval.md](../contracts/auth-and-approval.md) (PR #7 drafted/submitted by Member 1) |

### Sprint 1 — Reservation Main Flow

| Task | Owner | Status | Evidence |
| --- | --- | --- | --- |
| Search/availability/submission (backend/frontend/bench) | Member 1 | ✅ This branch | `equipment`, `reservation`; UC01 §6 |
| UC01 contract | Member 1 | ✅ This branch | [equipment-and-reservation.md](../contracts/equipment-and-reservation.md) |
| Authentication/authorization/shared React pages | Member 2 / Member 5 | 🔶 | `feature/wang-yuanmeng-platform-rest-react` (no PR) |
| User/equipment/reservation tables/access | Member 3 | ✅ | PR #10 |
| Login/search/authorization/conflict tests | Member 4 / feature owners | 🔶 | UC01 done here; login on platform branch |
| Builds/tests/security/deployment | Member 5 | 🔶 | Security gate ✅; frontend/backend workflows on branches |

### Sprint 2 — Other Core Features

| Task | Owner | Status | Evidence |
| --- | --- | --- | --- |
| Configurable Strategy policies | Member 1 | ✅ This branch | `reservation/domain/policy` |
| UC02 `ReservationApprovalService` and port integration | Member 1 | ✅ This branch | Contract §5; `ApprovalContract` tests |
| Approval/rejection REST/pages | Member 2 | 🔶 | Draft PR #8 (domain/service) |
| Checkout/return | Zhou Fanhao | 🔶 Local implementation; review/deployment pending | [UC03](../uc03-checkout-and-return.md), [Verification](../zhoufanhao-validation.md) |
| Faults/maintenance | Member 4 | 🔶 | Draft PR #9 |
| Overdue/in-app notifications | Member 5 | 🔶 | main + platform branch |
| Full team business-flow testing | Team | ⬜ | Shared migrations into `app/` required |

### Sprint 3 — Integration and Refinement (No New Features)

| Task | Owner | Status |
| --- | --- | --- |
| Completion/demo flow | Member 1 | ✅ Updated 2026-10-15: [sprint-status.md](sprint-status.md#member-1-update-2026-10-15), [demo-script.md](demo-script.md) |
| UC01 fixes, tests, diagrams, pattern explanations | Member 1 | ✅ 2026-10-15: real-browser check, analysis/design class diagrams, [UC01 verification](../evidence/uc01/verification.md) |
| Shared layout/style/interaction review | Member 2 | ⬜ |
| Database/data/state checks | Member 3 | ⬜ |
| Functional/security/quality (ZAP, k6) | Member 4 | ⬜ |
| Final deployment/release/fallback | Member 5 | ⬜ |

### Sprint 4 — Report and Submission

| Task | Owner | Status |
| --- | --- | --- |
| Tasks/Sprint/agile evidence | Member 1 | ✅ Index consolidated 2026-10-15: [agile-evidence.md](agile-evidence.md); Sprint 3 Review and Sprint 4 report sections remain |
| Individual use cases/design/code/tests/contributions | Each member | Member 1: [UC01](../uc01-search-and-reserve.md), [contribution record](contribution-shi-wenqi.md); others ⬜ |
