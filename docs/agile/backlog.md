# Requirements and Tasks (Product Backlog)

Sources: Project Proposal Chapter 5 (functional), Chapter 6 (business rules), Chapter 7 (non-functional), Section 8.3 (DevSecOps), team ownership, and [Technology stack](../tech-stack.md). Requirement IDs are stable and referenced by tasks, PRs, and tests.

Current tracking (2026-10-14): use the [integration update](sprint-status.md#integration-update-2026-10-14) and [Issue #27](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27) (tasks #15-#26, with #5-#10 retained; owners per [D-007](../collaboration/decisions.md#d-007-completion-task-ownership)). Main `b3def12` includes UC01-UC04, the notification platform, the complete local demo and the Spring Boot 4 release packaging. Liu Tongyao executes UC01 takeover and local/release integration under [D-004](../collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution). Basic local acceptance comes first, then later user-defined changes, with cloud deployment last under [D-005](../collaboration/decisions.md#d-005-local-closeout-and-cloud-last). Historical Sprint task tables remain below and are not today's Done claims.

## 1. Ownership

Each member owns one full-stack feature (queries, backend, frontend, test bench) and a shared responsibility.

| Member | Business feature | Shared responsibility |
| --- | --- | --- |
| Member 1 Shi Wenqi | UC01 Search/reserve equipment | GitHub, CI/CD, deployment/release evidence |
| Member 2 Zhang Hanming | UC02 Review/approve reservations | Test strategy, security, quality |
| Member 3 Zhou Fanhao | UC03 Equipment checkout/return | Database, domain model, integration |
| Member 4 Liu Tongyao | UC04 Fault reports/maintenance | Product Backlog, Sprint management, progress consolidation |
| Member 5 Wang Yuanmeng | UC05 Overdue detection/notifications | Architecture, design rules, page consistency |

2026-10-07: long-term supporting duties corrected against the provided Team 07 plan. Historical task Member numbers/authorship below remain for traceability; subsequent supporting tasks follow this table.

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

| ID | User story | Acceptance | Priority | Status |
| --- | --- | --- | --- | --- |
| R1.1 | As a borrower, search by name/category/location | Case-insensitive substring search; exclude retired; pagination | M | ✅ This branch |
| R1.2 | View available equipment in an interval | Available/unavailable per result, available-only filter, same rules as submission | M | ✅ This branch |
| R1.3 | View occupied slots for an item | Next 14 days, no occupant identity | M | ✅ This branch |
| R1.4 | Reserve a slot | Confirm without approval, otherwise pending; visible in My reservations | M | ✅ This branch |
| R1.5 | No overlapping slots for an item (rules 1, 12) | Includes pending approval; one concurrent winner | M | ✅ This branch |
| R1.6 | No booking equipment under maintenance / with open faults / retired (rules 2, 8) | 409 / 404 with explanation | M | ✅ This branch |
| R1.7 | Duration/quota rules (10, 11) | All violations returned together; configurable values | M | ✅ This branch (values await Sprint 2 review) |
| R1.8 | Cancel reservations (rule 4) | Pending/confirmed may cancel and release slot; no cancellation after checkout | M | ✅ This branch |
| R1.9 | View personal reservations | Own records only, status filters | M | ✅ This branch |
| R1.10 | Share search links | Criteria stored in URL | S | ✅ This branch |
| R1.11 | Automatically cancel expired pending reservations | Scheduler sets `CANCELLED` | C | ⬜ Agree with Member 2 |

### 2.3 Other Use Cases (Owners Maintain Detailed Stories)

| ID | Use case | Key requirements | Owner | Progress |
| --- | --- | --- | --- | --- |
| R2 | UC02 Review/approve | Direct confirmation if unnecessary; approve; rejection reason/slot release; no self-approval; immutable decisions | Member 2 | API/pages/real adapter on main; Issue #8 member/browser acceptance open |
| R3 | UC03 Checkout/return | Validate checkout, record custodian, return condition, damage to maintenance | Zhou Fanhao | Backend/pages/V004 on main; Issue #6 validation and member acceptance open |
| R4 | UC04 Faults/maintenance | Fault blocks booking, assign technician, transitions, repaired availability/unrepairable retirement, history | Member 4 | Backend/API/pages on main; Issue #9 full integrated acceptance open |
| R5 | UC05 Overdue/notifications | Due-soon/overdue, retries/limit, deduplication, cancel after return, own inbox | Member 5 | APIs/inbox/worker/guard on main; Issue #7 overdue producer and full acceptance open |

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
| Completion/demo flow | Member 1 | ✅ Initial: [sprint-status.md](sprint-status.md), [demo-script.md](demo-script.md) |
| Shared layout/style/interaction review | Member 2 | ⬜ |
| Database/data/state checks | Member 3 | ⬜ |
| Functional/security/quality (ZAP, k6) | Member 4 | ⬜ |
| Final deployment/release/fallback | Member 5 | ⬜ |

### Sprint 4 — Report and Submission

| Task | Owner | Status |
| --- | --- | --- |
| Tasks/Sprint/agile evidence | Member 1 | 🔶 Index: [agile-evidence.md](agile-evidence.md) |
| Individual use cases/design/code/tests/contributions | Each member | ⬜ |
