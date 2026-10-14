# UC04 Report a Fault and Process Maintenance

Owner: Liu Tongyao (@cpg25ncu). This document supplies the use case description, analysis models and design models required by Proposal 9.1. It describes `main` at `ec57bbccb8e4c512520e1f23fe2e724d294ec321`, fetched on 2026-10-14 (Asia/Singapore). Pending work is identified separately; the models do not add API fields, states or notification consumers to that baseline.

## Use Case Description

The objective is to record a fault, prevent unsafe reservation and physical issue, assign repair work, retain its history and record the outcome. The trigger is a borrower/storekeeper fault report or UC03's damaged physical return.

| Actor | Implemented role and responsibility |
| --- | --- |
| Borrower | `BORROWER`: report a fault; receive the new case ID. Reporting alone does not grant case/history reading |
| Storekeeper | `CUSTODIAN`: report faults, view the OPEN queue and equipment history, select an active technician and assign a case |
| Technician | `MAINTAINER`: view own assigned cases, start own work, record diagnosis/repair/notes and complete the case |
| Administrator | `ADMIN`: operator queue/assignment/history access and maintenance reports. Reporting and repair require separately held `BORROWER`/`CUSTODIAN` and `MAINTAINER` roles respectively |

Roles are additive; none implicitly inherits another. `MaintenanceAccessPolicy` calls the CUSTODIAN/ADMIN operator mapping provisional because the Proposal specifies only an "authorised user" for assignment. This document records that implementation without confirming a new permission rule.

Preconditions: the actor has an authenticated Session and the required explicit role; writes carry the shared CSRF header. The equipment exists and is not RETIRED for a direct report. An optional loan must concern the same equipment. Assignment requires an existing ACTIVE user with `MAINTAINER`. Start/work/completion require that user to be the case's assigned technician and the current state to permit the operation. Actor IDs and report/completion times come from the server, not request fields.

### Normal Flow

1. Borrower or storekeeper selects Report fault from the equipment catalogue, or opens the maintenance form and supplies an equipment UUID, optional loan UUID and non-blank fault description.
2. `MaintenanceController.reportFault` takes the reporter from `SermsUserPrincipal`. `MaintenanceService.reportFault` validates the aggregate, checks existence, locks Equipment, validates the optional loan, marks Equipment UNDER_MAINTENANCE and inserts an OPEN case and REPORT_FAULT history in one transaction. The response is 201 with Location and the saved case.
3. Storekeeper or administrator opens the OPEN queue (oldest report first) and selects an ACTIVE MAINTAINER from the directory. `assignTechnician` changes OPEN to ASSIGNED, saves history and publishes `MaintenanceAssigned`.
4. The assigned technician opens the case and starts maintenance. After acquiring the Equipment lock, the service reloads the case and checks assignment. ASSIGNED becomes IN_PROGRESS and START history is appended.
5. The assigned technician records diagnosis, repair actions and notes. Each non-blank entry appends history while the case remains IN_PROGRESS. These entries do not update the case row or its version. They are supported activities, not mandatory prerequisites for completion.
6. The assigned technician supplies a non-blank resolution note and either resolves the case or marks it unrepairable. The service persists RESOLVED or UNREPAIRABLE with `resolvedAt`, updates Equipment, appends completion history and publishes `MaintenanceCompleted` in the same transaction.
7. A resolved case causes Equipment to be recomputed: RETIRED stays RETIRED; another non-terminal case keeps UNDER_MAINTENANCE; otherwise an ACTIVE loan keeps ON_LOAN; otherwise Equipment becomes AVAILABLE. An unrepairable case always marks Equipment RETIRED in this implementation.
8. After commit, the page displays success and refreshes maintenance queries. Terminal cases have no further domain transition or work operation. History and report data remain readable by permitted actors.

Postconditions: a successful operation persists its case/equipment facts and success history atomically. A failure does not commit partial maintenance writes. Repair does not cancel reservations or release UC03's retained original reservation interval; AVAILABLE alone does not guarantee a requested slot is free.

Sources: [MaintenanceService.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceService.java), [MaintenanceAccessPolicy.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/security/MaintenanceAccessPolicy.java), [ReportFaultForm.tsx](../frontend/src/features/maintenance/ReportFaultForm.tsx), [MaintenanceActions.tsx](../frontend/src/features/maintenance/MaintenanceActions.tsx), and [queries.ts](../frontend/src/features/maintenance/queries.ts).

### Exception Flows

| Trigger | Implemented result |
| --- | --- |
| Missing equipment or case | 404 `not-found`; a maintainer denied access to an unassigned/missing case can receive 403 at the policy check before lookup |
| Direct report on RETIRED equipment | 409 `state-conflict`; no new direct case or equipment restoration |
| Missing loan or a loan for different equipment | 400 `validation`; reject before case/history writes and equipment status mutation. ACTIVE and RETURNED matching loans are both allowed |
| Missing, disabled or non-maintainer assignment target | 400 `validation`; no assignment/history/event |
| Another technician attempts start/work/completion | 403; identity is checked again against the post-lock case for these operations |
| Illegal transition, repeated assignment/completion or premature completion | 409 `state-conflict`; state machine rejects the action |
| Work on a non-terminal OPEN/ASSIGNED case, or on a terminal case | Only IN_PROGRESS accepts diagnosis/repair/note entries. An assigned caller in another state receives 409; an unassigned caller can receive 403 first |
| Stale repository snapshot during a case update | 409 `version-conflict` when `UPDATE ... WHERE version = ?` returns no row. Refresh before retrying; no success history/event is appended |
| Blank detail/description/resolution, malformed UUID or missing required field | 400 shared validation ProblemDetail; preserve form values for correction |
| No Session, wrong role or missing CSRF | 401 or 403 through shared security. ADMIN alone cannot report faults or perform technician work |
| Audit, equipment write or synchronous event-consumer failure | Roll back the service transaction. Known business errors use UC04 mappings; unrelated database errors use the shared 503 fallback |

The API exposes a server `version` but accepts neither `expectedVersion` nor If-Match. Assignment uses a snapshot loaded before the Equipment lock and can detect competing updates; start/completion reload under that lock. An old browser view is not itself a conditional-update token, so the documentation does not promise that every stale page submission produces a version conflict.

Sources: [MaintenanceController.java](../app/src/main/java/sg/edu/nus/serms/maintenance/api/MaintenanceController.java), [MaintenanceDtos.java](../app/src/main/java/sg/edu/nus/serms/maintenance/api/MaintenanceDtos.java), [MaintenanceExceptionHandler.java](../app/src/main/java/sg/edu/nus/serms/maintenance/api/MaintenanceExceptionHandler.java), [GlobalExceptionHandler.java](../app/src/main/java/sg/edu/nus/serms/shared/api/GlobalExceptionHandler.java), and [JdbcMaintenanceCaseRepository.java](../app/src/main/java/sg/edu/nus/serms/maintenance/repository/jdbc/JdbcMaintenanceCaseRepository.java).

## Business Rules and Enforcement

| Rule | Service/domain enforcement | Database enforcement or limit |
| --- | --- | --- |
| Equipment with an open maintenance case cannot be reserved or issued | Reporting immediately marks UNDER_MAINTENANCE; UC01 validates availability; `LoanService.checkout` tests equipment state and active maintenance (OPEN, ASSIGNED or IN_PROGRESS) | V002 `equipment_can_reserve` checks both equipment status and those case states; `check_reservation` invokes it under Equipment lock. `check_loan` guards active-loan uniqueness and due time, but does not independently reject maintenance equipment; issue safety also relies on the UC03 service |
| A case starts OPEN and follows only permitted transitions | `MaintenanceCase` delegates to `MaintenanceStateMachine` | V002 status CHECK and `check_maintenance` reject non-OPEN insertion and illegal status changes |
| A linked loan concerns the case's equipment | `JdbcMaintenanceLoanAdapter.ensureLoanMatchesEquipment` | Loan FK and `check_maintenance` verify the association through Reservation |
| Only an ACTIVE MAINTAINER can be assigned | `JdbcTechnicianDirectoryAdapter.ensureTechnician` checks Identity tables | `assigned_to` FK ensures existence, not account status or role |
| Only the assigned technician works on IN_PROGRESS | `MaintenanceAccessPolicy`, post-lock `requireAssignedTechnician`, `recordWork` state check | No SQL role/actor authorization or diagnosis prerequisite is provided by `check_maintenance` |
| Completion needs a note, valid timestamp and assignment | `MaintenanceCaseSnapshot` validates terminal note/time and requires assignment for every non-OPEN state | V002 requires terminal note/time and non-backdated resolution; its assignment CHECK covers ASSIGNED/IN_PROGRESS only |
| Non-terminal cases contain no resolution data | `MaintenanceCaseSnapshot` rejects both resolution note and time before terminal status | V002 requires non-terminal `resolved_at` to be null but does not require `resolution_note` to be null |
| Terminal case operations are read-only | State machine and service reject further transitions/work | `check_maintenance` rejects terminal status changes but allows some same-status field updates; it does not enforce full terminal-row immutability |
| Repair preserves other equipment facts; retirement cannot be reversed | `JdbcEquipmentMaintenanceAdapter` applies RETIRED > active case > ACTIVE loan > AVAILABLE, avoiding same-state equipment writes | V002 `keep_retired` rejects restoring a retired item. SQL triggers do not automatically recompute Equipment on case completion |
| History retains responsible actor/time for each successful operation | `MaintenanceService.record` appends a `MaintenanceHistoryEntry` in the mutation transaction | `audit_log` FKs/checks and UPDATE/DELETE/TRUNCATE rejection triggers protect append-only history |
| Case versions belong to the database | Repository compares the loaded version and reads the saved result; aggregate transitions do not increment it | V002 `maintenance_version` uses `bump_version`; V004 widens the version to bigint |

The first rule distinguishes reservation and issue guards; it does not imply every guard exists in both layers. Multiple non-terminal cases for one item are allowed; there is no unique-open-case constraint.

Primary SQL sources: [V002__align_serms_model.sql](../app/src/main/resources/db/migration/V002__align_serms_model.sql) (`maintenance_case`, `check_maintenance`, `equipment_can_reserve`, `check_reservation`, `check_loan`, `keep_retired`, audit and version triggers) and [V004__loan_operations.sql](../app/src/main/resources/db/migration/V004__loan_operations.sql). Cross-module sources: [UC01 contract](contracts/equipment-and-reservation.md), [LoanService.java](../app/src/main/java/sg/edu/nus/serms/loan/service/LoanService.java), [EquipmentMaintenancePort.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/EquipmentMaintenancePort.java), and [JdbcEquipmentMaintenanceAdapter.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/jdbc/JdbcEquipmentMaintenanceAdapter.java).

### UC03 Damaged Return

`LoanService.receive` locks Equipment, Reservation and Loan, persists the DAMAGED return, and calls `MaintenanceService.reportFault(equipmentId, actor, note, loanId)` for a non-retired item in the same transaction. The CUSTODIAN is the fault reporter; the return note supplies the description. Failure in maintenance, audit or synchronous event processing rolls back the physical-return record as well.

There is a specific retired-item exception: UC03 can receive equipment already RETIRED and directly insert a linked OPEN case and MAINTENANCE_REPORT_FAULT audit. It bypasses `reportFault` because that method rejects direct faults on retired items; retirement is preserved. This path is already documented in [UC03](uc03-checkout-and-return.md) and must not be modelled as restoring UNDER_MAINTENANCE. Issue #21's future category/priority defaults must cover both damaged-return paths.

## State Model

| Proposal 5.6 | Implemented state | Allowed next action |
| --- | --- | --- |
| Reported | OPEN | ASSIGN -> ASSIGNED |
| Assigned | ASSIGNED | START -> IN_PROGRESS |
| In Progress | IN_PROGRESS | RESOLVE -> RESOLVED; MARK_UNREPAIRABLE -> UNREPAIRABLE; history-only work entries remain IN_PROGRESS |
| Resolved | RESOLVED | None |
| Not Repairable | UNREPAIRABLE | None |
| Closed | Folded into RESOLVED / UNREPAIRABLE | No separate close/finalize endpoint or state |

[D-008](collaboration/decisions.md#d-008-equipment-and-maintenance-state-models) confirms retaining these five states. The four Equipment states are AVAILABLE, ON_LOAN, UNDER_MAINTENANCE and RETIRED; Proposal's Reserved is derived from time-window reservation rows, and Issued maps to ON_LOAN. Existing pending/six-step wording elsewhere is listed in the [development log](devlog/2026-10-14-uc04-design-documents.md#deviations), without changing its source.

Sources: [MaintenanceStatus.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceStatus.java), [MaintenanceStateMachine.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceStateMachine.java), [MaintenanceCase.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceCase.java), and [MaintenanceCaseSnapshot.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceCaseSnapshot.java).

## REST Endpoints and Access Policy

All paths below are relative to `/api/v1/maintenance`. GET returns 200; POST creation returns 201, other POSTs return 200. Every endpoint requires authentication; all POSTs require CSRF. Policy checks are on public service methods, so controller access and direct proxied calls share RBAC.

| Method | Path | Policy / permitted role |
| --- | --- | --- |
| POST | `/cases` | REPORTER: BORROWER or CUSTODIAN; reporter is self |
| GET | `/cases/open` | OPERATORS: CUSTODIAN or ADMIN; OPEN only |
| GET | `/technicians` | OPERATORS: CUSTODIAN or ADMIN |
| GET | `/cases/assigned` | OWN_CASES: MAINTAINER; authenticated user's assigned and terminal cases |
| GET | `/cases/{caseId}` | CASE_READER: CUSTODIAN/ADMIN, or assigned MAINTAINER |
| GET | `/cases/{caseId}/history` | CASE_READER: same case restriction |
| POST | `/cases/{caseId}/assignment` | ASSIGNER: CUSTODIAN/ADMIN; actor is self |
| POST | `/cases/{caseId}/start` | TECHNICIAN_START: MAINTAINER; actor is self and assigned |
| POST | `/cases/{caseId}/diagnosis` | TECHNICIAN_WORK: MAINTAINER; self, assigned, IN_PROGRESS |
| POST | `/cases/{caseId}/repair-actions` | TECHNICIAN_WORK: same restriction |
| POST | `/cases/{caseId}/notes` | TECHNICIAN_WORK: same restriction |
| POST | `/cases/{caseId}/resolve` | TECHNICIAN_WORK: same restriction |
| POST | `/cases/{caseId}/unrepairable` | TECHNICIAN_WORK: same restriction |
| GET | `/equipment/{equipmentId}/cases` | OPERATORS: CUSTODIAN or ADMIN; all states |
| GET | `/equipment/{equipmentId}/history` | OPERATORS: CUSTODIAN or ADMIN |
| GET | `/report?from={instant}&to={instant}` | REPORT_READER: ADMIN; explicit ordered Instant bounds |

Request DTOs contain equipment/optional loan/description, technicianId, detail or resolutionNote as appropriate. Actor IDs, client timestamps, fault category and priority are absent at this baseline. Case responses retain UUID references, five-state status, fault/resolution data, UTC Instants and a long server version. PR #36 adds `equipmentName`, `equipmentAssetTag`, `reportedByName` and `assignedToName`; history responses add `actorName`. All five display fields are required response properties and nullable: an existing referenced record supplies its label, and an unreadable/missing record supplies null. `assignedToName` is also null when the case is unassigned. The frontend falls back to the corresponding ID when a referenced label is null. List endpoints are unpaginated arrays. Technician directory responses already contain displayName/email, ordered by displayName then userId.

Sources: `MaintenanceController`, `MaintenanceDtos`, `MaintenanceAccessPolicy` linked above; [JdbcTechnicianDirectoryAdapter.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/jdbc/JdbcTechnicianDirectoryAdapter.java) and [MaintenanceLoanPort.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceLoanPort.java).

### Response Name Projection

`MaintenanceController` first calls the secured `MaintenanceService` method. Only after its role, identity and case-access checks succeed does `MaintenanceNames` collect distinct equipment, reporter, assigned-technician or history-actor IDs from the returned records. It never looks up labels for arbitrary request IDs or a rejected service call. This projection also decorates successful mutation responses after the service returns.

The read-only `MaintenanceNameDirectory` port is implemented by `JdbcMaintenanceNameDirectory`. Each non-empty label kind uses one batched `IN (:ids)` query: equipment name and asset tag together from Equipment, and display names from Identity. Reporter and technician IDs share the user batch; history only queries its returned actor IDs. Empty sets issue no SQL. Immutable `MaintenanceNames` maps and `MaintenanceEquipmentLabel` records feed response DTOs without changing aggregate state, write transactions or access rules.

Sources: [MaintenanceNameDirectory.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceNameDirectory.java), [JdbcMaintenanceNameDirectory.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/jdbc/JdbcMaintenanceNameDirectory.java), [MaintenanceEquipmentLabel.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceEquipmentLabel.java), and [MaintenanceNames.java](../app/src/main/java/sg/edu/nus/serms/maintenance/api/MaintenanceNames.java).

## Transactions, Locks, History and Events

`MaintenanceService` uses Spring `@Transactional` (default REQUIRED). Equipment/case/history mutation adapters require an existing transaction with MANDATORY. Equipment `SELECT ... FOR UPDATE` is the first write lock before any case write, matching UC03's Equipment -> Reservation -> Loan -> MaintenanceCase order. Case lookup is a non-locking snapshot; its UPDATE acquires the row lock and compares `version`. Linked-loan validation takes `FOR SHARE OF l` only after Equipment. UC04 does not add a Reservation write lock when validating that immutable relationship.

Assignment validates the technician and retains its initial case snapshot before Equipment locking. Start, work entries and completion reload the case after Equipment locking to avoid authorizing work against an obsolete assignment/state. Completion persists the terminal case before querying other cases/loans, so equipment recomputation sees the outcome. Reads use read-only service transactions; report reads use REPEATABLE_READ across all four reporting queries.

`JdbcMaintenanceHistoryRepository` stores history in `serms.audit_log`, not a separate maintenance-history table: `entity_type='MaintenanceCase'`, the case UUID as `entity_id`, `outcome='SUCCESS'`, `MAINTENANCE_` plus the action, actor, timestamp and detail in `change_summary`. Its requestId is a generated local `maintenance:<audit UUID>` correlation. Reads filter those eight maintenance actions and join the case to obtain equipmentId; from/to statuses are reconstructed from the action rather than stored as audit columns. Ordering is occurredAt then auditId. Diagnosis/repair/note details therefore belong to audit history rather than additional aggregate fields.

`SpringMaintenanceEventPublisher` forwards immutable `MaintenanceAssigned` and `MaintenanceCompleted` records to Spring's `ApplicationEventPublisher` before transaction commit. Both include case/equipment/technician/actor IDs and occurrence time; completion also carries RESOLVED/UNREPAIRABLE outcome. There is no event for report/start/note in UC04. The service itself records maintenance audit; it does not depend on a notification subscriber for history.

At this baseline there is **no production UC04 event consumer on main**. Draft [PR #12](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/12), checked at `b93734b23398d065553b3942f8cfe1f09e1f6cd0` on 2026-10-14, adds `notification/service/MaintenanceNotificationListener`: assignment requests a notice for the assigned technician, completion for the original fault reporter after validating the saved case. It calls `NotificationRequestService.requestOnce` with case-based deduplication keys and joins the business transaction via MANDATORY. These are draft consumers, not main behavior or proven delivered notices. A synchronous consumer's failure can roll back maintenance writes; external effects would need after-commit/outbox treatment, which UC04 does not implement here.

Sources: [JdbcMaintenanceHistoryRepository.java](../app/src/main/java/sg/edu/nus/serms/maintenance/repository/jdbc/JdbcMaintenanceHistoryRepository.java), [SpringMaintenanceEventPublisher.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/SpringMaintenanceEventPublisher.java), [MaintenanceAssigned.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceAssigned.java), [MaintenanceCompleted.java](../app/src/main/java/sg/edu/nus/serms/maintenance/domain/MaintenanceCompleted.java), [UC03 contract](contracts/checkout-and-return.md), and [draft listener source](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/blob/b93734b23398d065553b3942f8cfe1f09e1f6cd0/app/src/main/java/sg/edu/nus/serms/notification/service/MaintenanceNotificationListener.java).

## Design Problem and Pattern Choice

**State.** Controller conditionals would duplicate legal operations across endpoints. A full polymorphic State hierarchy is an alternative, but the implemented `MaintenanceStateMachine.transition(status, action)` centralizes the finite transition table in a switch over `MaintenanceStatus`. `MaintenanceCase` owns mutable lifecycle fields and delegates transitions; `MaintenanceCaseSnapshot` validates durable state. This is a State-oriented state machine, not five concrete GoF State classes. Diagnosis/repair/note actions are history-only service operations outside its four transition actions. Database status checks/triggers provide a second integrity boundary with the limits described above.

**Observer.** `MaintenanceAssigned` and `MaintenanceCompleted` separate business mutation from interested consumers. `MaintenanceEventPublisher` and `SpringMaintenanceEventPublisher` connect that public event seam to Spring. The draft `MaintenanceNotificationListener` illustrates its notification consumer; main currently publishes events without that production subscription. Synchronous publication keeps transactional database observers atomic but requires care before adding external side effects.

**Adapter and ports.** `MaintenanceService` depends on `EquipmentMaintenancePort`, `TechnicianDirectoryPort` and `MaintenanceLoanPort`, implemented by `JdbcEquipmentMaintenanceAdapter`, `JdbcTechnicianDirectoryAdapter` and `JdbcMaintenanceLoanAdapter`. These isolate equipment status/locking, Identity eligibility and Loan association queries from orchestration. `MaintenanceCaseRepository` / `JdbcMaintenanceCaseRepository` and `MaintenanceHistoryRepository` / `JdbcMaintenanceHistoryRepository` similarly isolate case and audit persistence. The adapters share the existing PostgreSQL schema/transaction, not separate applications or remote services. `MaintenanceConfiguration` supplies qualified UTC Clock and UUID generation.

**Report read model.** `MaintenanceReportService` calls `MaintenanceReportRepository` / `JdbcMaintenanceReportRepository` and returns an immutable `MaintenanceReportSnapshot`, avoiding loading/mutating aggregates to calculate statistics. It is a separate read projection, not an event-sourced system. Global counts include every canonical state with zero defaults; reported count uses reportedAt in `[from,to)`; completed count and mean `resolvedAt - reportedAt` use both terminal outcomes completed in that interval. Duration includes assignment/waiting time, not just IN_PROGRESS repair time; an empty completion set returns no average. `MaintenanceDtos.MaintenanceReportResponse` encodes duration as nullable ISO-8601 text. The ADMIN `MaintenanceReportPage` converts SGT form bounds to UTC; the central `report/service/ReportService` also consumes this public service for `/reports` on main after PR #33.

The stale `TechnicianDirectoryPort` and `MaintenanceHistoryRepository` comments were corrected by PR #37: technician eligibility is already implemented, and logical history entries use `serms.audit_log` (D12).

Sources: [EquipmentName.tsx](../frontend/src/features/maintenance/EquipmentName.tsx), [EquipmentHistoryLookup.tsx](../frontend/src/features/maintenance/EquipmentHistoryLookup.tsx), [MaintenanceHistoryRepository.java](../app/src/main/java/sg/edu/nus/serms/maintenance/repository/MaintenanceHistoryRepository.java), [TechnicianDirectoryPort.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/TechnicianDirectoryPort.java), [JdbcMaintenanceLoanAdapter.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/jdbc/JdbcMaintenanceLoanAdapter.java), [MaintenanceReportService.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceReportService.java), [MaintenanceReportSnapshot.java](../app/src/main/java/sg/edu/nus/serms/maintenance/service/MaintenanceReportSnapshot.java), [JdbcMaintenanceReportRepository.java](../app/src/main/java/sg/edu/nus/serms/maintenance/repository/jdbc/JdbcMaintenanceReportRepository.java), [MaintenanceReportPage.tsx](../frontend/src/features/maintenance/MaintenanceReportPage.tsx), and [ReportService.java](../app/src/main/java/sg/edu/nus/serms/report/service/ReportService.java).

## Analysis and Design Models

The analysis models express responsibility at boundary/control/entity level; their conceptual `MaintenanceControl` is refined into the implementation classes in the design models. Fault data is represented in MaintenanceCase because main has no separate FaultReport aggregate/table. The activity and analysis sequence include the normal flow and major rejection/outcome branches. The design sequence expands separate assignment, start and resolve transactions, including optimistic checks, audit and pre-commit publication; the optional draft subscriber is explicitly marked.

| Model | Editable Mermaid source |
| --- | --- |
| Activity | [uc04-activity.mmd](diagrams/uc04-activity.mmd) |
| Analysis class | [uc04-analysis-class.mmd](diagrams/uc04-analysis-class.mmd) |
| Analysis sequence | [uc04-analysis-sequence.mmd](diagrams/uc04-analysis-sequence.mmd) |
| Design class | [uc04-design-class.mmd](diagrams/uc04-design-class.mmd) |
| Design sequence | [uc04-design-sequence.mmd](diagrams/uc04-design-sequence.mmd) |
| Case states and equipment effects | [uc04-states.mmd](diagrams/uc04-states.mmd) |

## Pending Changes

[Issue #21](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/21) will add fault category/priority, API/form validation, queue priority sort/filter. Readable equipment/user names on case, queue and history pages are already delivered by [PR #36](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/36), merged at `76a38eafabd74656cf90d032994339f29c970d8b`. Planned migration V006 follows [Issue #17](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/17)'s V005. Both were OPEN when checked on 2026-10-14; main contains V001-V004 only. UC03 damaged-return defaults require agreement with Zhou Fanhao. Five states remain under D-008, and #21 includes updating the stale pending wording in contract section 9. The parallel Issue #21 writer's uncommitted files are outside this document's baseline.

The assignment dropdown shows technician displayName/email. Case cards, queues, equipment results and case details now show equipment names/asset tags and reporter/technician names; history shows actor names. Only Case ID and Loan ID remain as explicit identification fields, with UUID fallbacks for unavailable labels. Catalogue-selected report equipment is implemented, but the report-fault form and equipment-history lookup input still require an equipment UUID. [PR #37](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/37), merged at `ec57bbccb8e4c512520e1f23fe2e724d294ec321`, corrects the lookup wording. For CUSTODIAN/ADMIN, the equipment label on a case card or case detail links to `/maintenance?history=<id>`. The workspace validates that UUID, initializes the equipment lookup, loads all cases and history directly through the existing maintenance endpoints, and scrolls to the results. This includes RETIRED equipment and does not depend on the catalogue, which excludes retired items. MAINTAINER-only users retain plain labels and assigned-case access; the history link adds no role permission.

## Acceptance Criteria and Existing Test Bench

The following maps implemented acceptance to tests already on the baseline. It describes their assertions, **not a new execution result**. Backend paths in the inventory are relative to `app/src/test/java/sg/edu/nus/serms/maintenance/`.

| Criterion | Existing coverage and limit |
| --- | --- |
| Direct report creates OPEN, blocks reservation/issue and records history | MaintenanceServiceTest, JdbcEquipmentMaintenanceAdapterTest, MaintenancePostgresIT; UC01/UC03 guards and the linked historical real-flow evidence cover cross-module blocking |
| Assignment uses ACTIVE MAINTAINER and preserves actor | MaintenanceApiTest, MaintenanceMethodSecurityTest, JdbcTechnicianDirectoryAdapterTest, MaintenancePostgresIT |
| Only legal five-state transitions and assigned work are allowed | MaintenanceStateMachineTest, MaintenanceCaseTest, AssignedTechnicianEnforcementTest, MaintenanceServiceTest, MaintenanceApiTest |
| Wrong/missing loan leaves no partial report | JdbcMaintenanceLoanAdapterTest, MaintenanceServiceTest, MaintenancePostgresIT |
| Repair restores the correct equipment status; unrepairable retires | JdbcEquipmentMaintenanceAdapterTest, MaintenanceServiceTest, MaintenancePostgresIT; AVAILABLE requires no other active case/loan |
| Optimistic races and failed consumers retain atomicity | Repository/service tests and MaintenanceTransactionTest model conflicts; MaintenancePostgresIT exercises real optimistic concurrency and rollback |
| Ordered complete history and accurate reporting | History/report repository tests, report service/snapshot tests and MaintenancePostgresIT |
| UI validates fields and exposes only permitted actions | maintenance.test.tsx; maintenance.spec.ts covers a mocked signed-in maintainer start/diagnose/resolve flow |
| Authorized responses show names without per-row lookup or pre-authorization disclosure | JdbcMaintenanceNameDirectoryTest; MaintenanceApiTest; MaintenancePostgresIT; MaintenanceOpenApiTest; MaintenanceJdbcWiringTest; maintenance.test.tsx and maintenance.spec.ts name assertions |

| Test source | What it verifies |
| --- | --- |
| `domain/MaintenanceStateMachineTest.java` | Every permitted/rejected state/action pair, terminal rejection and null inputs |
| `domain/MaintenanceCaseTest.java` | OPEN creation, optional loan, both outcomes, invalid note/time without partial mutation, copies and database-owned version |
| `domain/MaintenanceCaseSnapshotTest.java` | Durable-state validation and round trips across all five states, assignment and resolution requirements |
| `service/MaintenanceServiceTest.java` | Fault/assignment/start/work/completion orchestration, rejected operations, history-only work, optimistic failures and post-lock reload |
| `service/AssignedTechnicianEnforcementTest.java` | Wrong/unassigned technician denied for all work operations; authorization uses reloaded assignment |
| `service/MaintenanceMethodSecurityTest.java` | Real method-security proxies, explicit role/self constraints, assigned-only reads/work and ADMIN-only report; persistence mocked |
| `service/MaintenanceTransactionTest.java` | Real Spring proxy and recording transaction manager demonstrate boundary/read-only/rollback signalling; no real database rollback claim |
| `service/MaintenanceJdbcWiringTest.java` | Production adapter/qualified dependency wiring, including MaintenanceNameDirectory, and MANDATORY write/lock guards without querying PostgreSQL |
| `service/SpringMaintenanceEventPublisherTest.java` | Assigned/completed events forwarded exactly once; completion rejects non-terminal outcomes |
| `service/MaintenanceReportServiceTest.java` | Exact four-query delegation, range validation and real proxy read-only REPEATABLE_READ boundary with mocked persistence |
| `service/MaintenanceReportSnapshotTest.java` | Immutable zero-filled status counts, range/count/duration invariants and absent empty-set average |
| `service/jdbc/JdbcEquipmentMaintenanceAdapterTest.java` | Equipment locks/existence, retired rejection, recomputation precedence and avoiding unnecessary version bumps; mocked JDBC |
| `service/jdbc/JdbcTechnicianDirectoryAdapterTest.java` | ACTIVE MAINTAINER filter, directory ordering and invalid targets; mocked JDBC |
| `service/jdbc/JdbcMaintenanceNameDirectoryTest.java` | H2 name/asset-tag and user-name IN projections, missing IDs, immutable maps, and no JDBC calls for empty sets; not a real PostgreSQL bench |
| `service/jdbc/JdbcMaintenanceLoanAdapterTest.java` | Same-equipment ACTIVE/RETURNED loans, missing/mismatched associations and propagated DB failures; mocked JDBC |
| `repository/jdbc/JdbcMaintenanceCaseRepositoryTest.java` | SQL mappings, server versions, stale update conflict, queue/status filters and deterministic ordering; mocked JDBC |
| `repository/jdbc/JdbcMaintenanceHistoryRepositoryTest.java` | Audit fields/correlation, eight actions, reconstructed transitions, details, filtering and ordering; mocked JDBC |
| `repository/jdbc/JdbcMaintenanceReportRepositoryTest.java` | Global status counts, half-open intervals, both terminal outcomes and numeric duration precision; mocked JDBC |
| `api/MaintenanceApiTest.java` | MockMvc with real method-security service and mocked repositories: Session/CSRF, actor binding, role/assignment denial, validation and safe ProblemDetail mappings; case/history names, null for missing labels, and no name-directory calls after denied access |
| `api/MaintenanceOpenApiTest.java` | All maintenance operations and schemas, absence of client actor fields; all five display properties required and nullable |
| `integration/MaintenancePostgresIT.java` | Real PostgreSQL case/equipment/audit commits, eligibility, assignment/version events, wrong loans/technicians, both outcomes and precedence, stale/concurrent assignment, consumer-failure rollback, ordered queries/history and report intervals/averages; real JDBC equipment/asset-tag, reporter, technician and history-actor name projection |
| `integration/Uc04PostgresTestSupport.java` | Fixture/support class rather than a test suite: explicit migrated named TEST DB, independent IDs, Spring/JDBC transaction setup and controlled event failures |
| `frontend/src/features/maintenance/maintenance.test.tsx` | MSW component assertions: catalogue UUID selection/reset, form validation/errors, role union, directory/queue, assigned state groups, start/work/terminal controls, unrepairable confirmation, history lookup, SGT report bounds and formatting; equipment/user labels, history actor names and UUID fallbacks; operator card/detail history links, retired-equipment lookup from the query parameter, invalid query rejection, and no operator history link for MAINTAINER-only users |
| `frontend/e2e/maintenance.spec.ts` | Playwright routes mock all APIs: login, CSRF header, own case start/diagnosis/resolve, refreshed history, equipment/asset-tag and technician/actor name assertions, hidden equipment UUID when labelled, terminal controls and overflow at configured desktop/mobile widths; no real backend/database |

`MaintenancePostgresIT` requires a dedicated migrated PostgreSQL test database; ordinary Maven verify does not enable it by default. On this baseline [maintenance-tests.yml](../.github/workflows/maintenance-tests.yml) does run it in the `maintenance-postgres` job with PostgreSQL 18 and profile `uc04-postgres-it`. The UC04 row in [testing.md](testing.md) is corrected in this PR (D8), while its other historical rows retain their original baseline. Its historical [UC01/UC04 verification](evidence/uc01-uc04/verification.md) records 28 real PostgreSQL tests, 24 real API checks and 10 real-browser checks on earlier delivery inputs; those results are evidence references, not this PR's results. Runtime scripts and behavioral suites were not rerun for this documentation-only task, and no member check, deployment or team Done is claimed.
