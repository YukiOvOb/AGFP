# Project Review and Completion Plan

Date: 2026-10-14 (Asia/Singapore) · Requested by: Liu Tongyao (@cpg25ncu) · Type: cross-cutting review and re-planning

Inputs read: the Project Proposal (local copy), `main` at `940bf42`, draft PRs #11 (`d42e0a6`), #12 (`4a00f74`), #13 (`15ec983`), #14 (`bbb1902`), Issues #5-#10, and the repository documents. The review was read-only; nothing was merged, pushed or deployed.

## Repository State Found

| Item | State |
| --- | --- |
| `main` `940bf42` | UC01-UC04, JSON login and roles, notification inbox, PostgreSQL 18, Flyway V001-V004 |
| #11 -> `main` (draft) | Reproducible local demo: native startup/shutdown, demo accounts, real API and browser smoke |
| #14 -> #11 (draft) | UC01/UC04 acceptance fixes |
| #13 -> #14 (draft) | Spring Boot 4 upgrade, `web` + `app` + `db` Docker packaging, gated deployment workflow |
| #12 -> `main` (draft, Wang Yuanmeng) | UC05 loan reminder scanner, loan and maintenance notification listeners, `Uc05PostgresIT`; based on Boot 3 |
| #3 -> `main` | Open and conflicting; its content was reconciled in the local closeout commit |
| Local `codex/local-repository-closeout` `b9fa875` | One unpushed documentation commit on top of #11 |
| Issues #5-#10 | All open; evidence recorded by agents, no member sign-off |

All hosted checks were green on the latest HEAD of #11-#14 when read.

## Completion Against the Proposal

| Proposal | State | Gap |
| --- | --- | --- |
| 5.1 Login, logout, roles, server-side authorization | Done | - |
| 5.1 Administrator manages users and roles | Missing | Only `csrf`, `login`, `me`, `logout` exist. Accounts come only from `scripts/demo-data.sql`, which is prohibited in production |
| 5.2 Equipment and category management | Missing | Search endpoints only. No serial number, description, condition or maximum duration columns; category is free text |
| 5.3 Search and reservation | Done | Rule 11 (overdue borrower blocked) absent; duration limit is global, not per equipment |
| 5.4 Approval | Done | - |
| 5.5 Issue and return | Main flow done | No lookup by reservation number, borrower or equipment code; no overdue list with duration; `ADMIN` cannot read loans |
| 5.6 Fault reporting and maintenance | Main flow done | No fault category or priority; five states against the Proposal's six |
| 5.7 Notifications | Partial | `main`: approval decisions only. #12 adds checkout, return, maintenance assigned/completed, due-soon, overdue. Still missing: reservation submitted/cancelled, upcoming collection |
| 5.7 Reports | Partial | Maintenance report only. Missing: reservations by category, utilisation, current overdue, overdue frequency |
| 5.7 Audit records | Partial | Written for loans, approvals, maintenance. Not written for reservation submit/cancel. No read API or page |
| 7.x Quality and DevSecOps | Partial | Coverage is reported, not enforced. CodeQL, ZAP, k6 and the backup script are not present |

Design-pattern evidence found in code: Strategy (`reservation/domain/policy`), Chain of Responsibility (`approval/domain/ApprovalPolicyChain`), State (`MaintenanceStateMachine`, `EquipmentLoanState`), Observer (Spring events to notification listeners). The `NotificationChannel` adapter described in the technology document does not exist in code.

## Things to Change in Existing Code

| Finding | Where | Task |
| --- | --- | --- |
| Notification text concatenates raw UUIDs and UTC instants | `LoanNotificationListener`, `MaintenanceNotificationListener` in #12 | #19 |
| Maintenance pages show raw UUIDs for equipment, reporter, technician, actor | `frontend/src/features/maintenance/` | #21 |
| SQL lives inside the service; no repository layer | `loan/service/LoanService.java` | #20 (optional) |
| Reservation events published without audit or notification listeners | `ReservationSubmitted`, `ReservationCancelled` | #22, #19 |
| Only two shared UI components; 940 lines of hand-written CSS plus per-feature CSS; `/` redirects to notifications; roles shown as raw codes; chart library not installed | `frontend/src/` | #16, #26 |

## Problems Met So Far

These are recorded for the report's challenges section. Sources are the repository documents and GitHub state read during this review.

| Problem | Effect | How it was handled |
| --- | --- | --- |
| The original repository owner's GitHub account was banned | The original repository, its PR/Issue history and its configured deployment secrets became unavailable. Old documents still cite PR numbers of the previous repository | Repository recreated on 2026-10-12 under `cpg25ncu` from local history; Liu Tongyao executes Shi Wenqi's tasks (D-004); contribution provenance is kept |
| Server access stayed with the original owner | The recreated repository has no deployment secrets; every `Deploy` run on `main` fails within seconds at "missing server host"; the server keeps whatever was last deployed from the old repository (documented as the static welcome page, not checked in this review) | Local-first delivery (D-004, D-005); deployment gated in #13; handover tracked in #10 |
| Built-image scan failed with 10 HIGH/CRITICAL findings although the source scan passed | Release image could not pass the gate on Spring Boot 3.5 | Upgrade to Spring Boot 4.0.8 with pinned Jackson and Tomcat versions in #13 instead of suppressing findings |
| Integration work piled up as three stacked draft PRs | `main` no longer represents the system; #12 was built on the old baseline | #15 lands the chain first; all new work starts from the Boot 4 baseline |
| Effort went to verification, translation and process documents while Proposal scope stayed unbuilt | Admin, equipment management, reports and audit viewing had no owner or task | Gap review against the Proposal; tasks #17, #18, #23 created with owners and dates |
| The system cannot create its first account in production | Deployment would produce a system nobody can sign in to | First-admin bootstrap included in #18 and made a dependency of #10 |
| Shared responsibilities were recorded differently in three documents (D-002) | Platform tasks had no agreed owner | Settled for the completion work by D-007 |
| The local copy of the Proposal has no body text for chapters 8, 11 and 12 (headings appear only in the table of contents) | "Proposal 8.3" cited by the technology document could not be checked in this review | Open; confirm against the submitted Proposal when writing the report |

## Direction Changes

1. Build the missing Proposal scope before polishing or re-verifying what already works.
2. Land the integration line first (#15) so everyone develops against the shipped baseline.
3. Freeze scope (D-009) and keep the implemented state models (D-008), explaining both in the report.
4. Start the server handover inventory now, in parallel; execute the deployment last (D-005).
5. Frontend in two steps: a small shared-component foundation now (#16), visual polish after feature freeze (#26).

## Tasks Created

| Issue | Task | Owner | Target |
| --- | --- | --- | --- |
| #15 | Land the integration line on `main` | Liu Tongyao | Thu 2026-10-15 |
| #16 | Frontend component foundation | Liu Tongyao | Fri 2026-10-16 |
| #17 | Equipment and category management | Zhou Fanhao | Mon 2026-10-19 |
| #18 | Admin console, first-admin bootstrap, audit viewer | Zhang Hanming | Mon 2026-10-19 |
| #19 | UC05 scanner, notification events, overdue view | Wang Yuanmeng | Mon 2026-10-19 |
| #20 | UC03 checkout lookup | Zhou Fanhao | Tue 2026-10-20 |
| #21 | UC04 fault category/priority, readable names | Liu Tongyao | Tue 2026-10-20 |
| #22 | UC01 overdue rule, per-equipment duration, audit | Liu Tongyao | Tue 2026-10-20 |
| #23 | Reports module and charts | Liu Tongyao | Tue 2026-10-20 |
| #24 | Coverage gate and real all-role browser flows | Zhang Hanming | Wed 2026-10-21 |
| #25 | CodeQL, Dependabot, backup runbook, k6, ZAP | Wang Yuanmeng | Thu 2026-10-22 |
| #26 | Frontend visual polish | Liu Tongyao | Thu 2026-10-22 |
| #27 | Completion plan (tracking) | Liu Tongyao | - |
| #10 | Server handover and release (existing) | Liu Tongyao with Shi Wenqi | Thu 2026-10-22 |

Existing #6-#9 remain open as acceptance sign-off for their modules. Schedule and milestones are in [D-010](../collaboration/decisions.md#d-010-delivery-schedule).

## Open Risks

- Server access is not handed over. Inventory has the longest lead time and does not depend on code.
- The P0 feature window (2026-10-15 to 2026-10-19) includes a weekend; #17 and #18 are the largest items.
- Members were assigned by the repository owner; their acceptance of dates is shown by their own activity on the Issues and is not assumed here.

## Authorship

Review, gap analysis, Issue drafting and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction on 2026-10-14. Ownership, state-model, scope and development-log decisions were confirmed by Liu Tongyao in that conversation. Intermediate milestone dates were proposed by the agent. No teammate review or sign-off is claimed.
