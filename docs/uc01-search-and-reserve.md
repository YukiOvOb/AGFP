# UC01 Search and Reserve Equipment

Owner: Member 1 · Shi Wenqi (@YukiOvOb). While Shi Wenqi's account was unavailable, Liu Tongyao executed the UC01 completion work (Issue #22 through PRs #32 and #44; the V005 read fields of Issue #40 through PR #42) under [D-004](collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution); that work stays recorded as Liu Tongyao's. API details: [Equipment search/reservation contract](contracts/equipment-and-reservation.md); diagram sources in [`docs/diagrams/`](diagrams/).

## 1. Use Case

| Item | Content |
| --- | --- |
| Primary actor | Borrower (`BORROWER`) |
| Secondary actors | Approver (UC02 pending decisions), notifications (UC05) |
| Goal | Find suitable equipment and reserve a non-conflicting interval |
| Preconditions | Logged-in borrower, active account |
| Success | `CONFIRMED` without approval or `PENDING_APPROVAL` with approval, occupying the slot; `ReservationSubmitted` published |
| Requirements | Proposal 5.3 (search, availability, submit, cancel) and 5.7 (audit); rules 1/12 (no overlap), 2/8 (no maintenance booking), 4 (no cancellation after checkout), 9 (audit), 10 (duration, quota, item limit), 11 (overdue borrowers) |

### 1.1 Main Flow

1. Open Find equipment; search name/asset tag, category, location, optionally a Singapore-time interval.
2. List non-retired items; with a window show Free / Not free and available-only filtering. Criteria persist in URL for refresh/share.
3. Select an item; reservation page shows next 14 days' busy slots without occupant identities.
4. Enter start/end and optional purpose; submit.
5. One transaction: lock equipment → requester → policies → slots/state → insert → audit record → event.
6. Show result in My reservations as Confirmed or Pending approval.

### 1.2 Alternative and Exception Flows

| ID | Trigger | Behavior | Test |
| --- | --- | --- | --- |
| A1 | No search results | Empty state, suggest fewer filters | `search.test.tsx` › shows an empty state and a retry |
| A2 | One endpoint, end before start, >90 days, available-only without interval | Inline frontend error, no request; backend 400 explanation | `search.test.tsx` › validates the time window; `EquipmentSearchCriteriaTest`; `ReservationApiTest#invalidSearchExplainsWhatToFix` |
| A3 | Overlap, including pending | 409, choose another slot and refresh | `ReservationPostgresTest.Submit#overlappingSlotIsRefusedButAdjacentSlotIsAccepted`, `#pendingReservationsAlsoHoldTheSlot`; `reservation.test.tsx` › explains a slot conflict |
| A4 | Concurrent booking | One success, others 409 via exclusion | `ReservationPostgresTest.Submit#parallelRequestsForTheSameSlotBookItExactlyOnce` (8 threads) |
| A5 | Maintenance/open fault | Page blocks booking, API 409 | `#equipmentUnderMaintenanceOrWithAnOpenFaultCannotBeReserved`; `reservation.test.tsx` › blocks equipment with an open fault |
| A6 | Retired/missing | Excluded search, API 404 | `#retiredOrUnknownEquipmentIsNotFound`, `Search#retiredOrUnknownEquipmentHasNoAvailabilityPage` |
| A7 | Still on loan at start | 409; starts after due time permitted | `#equipmentStillOnLoanAtTheRequestedStartIsUnavailable` |
| A8 | <30 minutes, longer than the item's own limit (else 7 days), past start, >90-day horizon, already 3 open bookings | 422, **all violations together**; the page also stops an over-limit request before sending it | `ReservationPolicyTest`; `#bookingRulesAreReportedTogether`, `#anItemsMaximumBorrowingDurationIsEnforced`, `#quotaCountsOnlyOpenReservations`; `reservation.test.tsx` › lists every broken booking rule, applies an item's limit |
| A12 | Borrower holds an overdue, unreturned loan | 422 `overdue-loan` with the other violations; switchable by `serms.reservation.block-overdue-borrowers` | `ReservationPolicyTest#borrowerWithOverdueEquipmentIsRefused`, `#theOverdueRuleCanBeSwitchedOff`; `OverdueAndAudit#borrowerWithAnOverdueLoanCannotReserveUntilItIsReturned` |
| A9 | Disabled account | 403 | `#disabledAccountsCannotReserve` |
| A10 | No BORROWER / act for another | Service `@PreAuthorize`, 403 | `ReservationServiceSecurityTest` |
| A11 | Approval required | `PENDING_APPROVAL`, UC02 | `#equipmentThatNeedsApprovalStartsPending`; all `ApprovalContract` tests |
| C1 | Cancel pending/confirmed | `CANCELLED`, release slot, event | `Cancel#cancellingReleasesTheSlot`, `#pendingReservationCanBeCancelled`; `reservation.test.tsx` › cancels after confirmation |
| C2 | Cancel checked-out | 409, return rather than cancel; no Cancel button | `Cancel#checkedOutReservationCannotBeCancelled` |
| C3 | Cancel another user's | 404, no existence disclosure | `Cancel#anotherBorrowersReservationLooksMissing` |
| AU | Every successful submit and cancel | One `serms.audit_log` row in the same transaction; a refused request writes none | `OverdueAndAudit#submitAndCancelAreAuditedWithTheBorrowerAsActor`, `#aRefusedBookingLeavesNoAuditRecord`; `ReservationServiceTest#submitRecordsWhoBookedWhatAndWhen` |

The real-stack browser check `scripts/check-uc01-browser.mjs` repeats A3, A6, A8, A11, C1 and the shared search link against the local demo; see [the 2026-10-15 record](evidence/uc01/verification.md).

## 2. Activity Diagram

```mermaid
flowchart TD
  A([Log in]) --> B[Open Find equipment]
  B --> C[Enter name / category / location / interval]
  C --> D{Frontend valid?}
  D -- No --> C
  D -- Yes --> E[GET /equipment search]
  E --> F{Results?}
  F -- No --> C
  F -- Yes --> G[Select equipment → reservation page<br/>View next 14 days of busy slots]
  G --> H{Maintenance / open fault?}
  H -- Yes --> C
  H -- No --> I[Enter start / end / purpose]
  I --> J{Frontend rules valid?}
  J -- No --> I
  J -- Yes --> K[POST /reservations]
  K --> L{Backend result}
  L -- 201 CONFIRMED --> M([Success → My reservations])
  L -- 201 PENDING_APPROVAL --> N([Pending approval → UC02])
  L -- 409 slot conflict --> O[Refresh busy slots, choose another] --> I
  L -- 422 rules --> P[Show every violation] --> I
  L -- 409 equipment unavailable --> C
  M --> Q{Cancel?}
  N --> Q
  Q -- Yes, not checked out --> R["POST /reservations/{id}/cancel<br/>Release slot"]
  Q -- Checked out --> S([Cannot cancel → return UC03])
```

## 3. Sequence Diagram (Reservation Submission)

```mermaid
sequenceDiagram
  autonumber
  actor B as Borrower
  participant W as React reservation page
  participant C as ReservationController
  participant S as ReservationService
  participant P as ReservationRules (Strategy)
  participant R as ReservationRepository
  participant D as PostgreSQL 18
  participant E as Event listeners (notification)
  B->>W: Enter interval and submit
  W->>W: Zod validation (end>start, duration, future start)
  W->>C: POST /api/v1/reservations + X-XSRF-TOKEN
  C->>S: submit(userId from Session, request)
  Note over S: @PreAuthorize BORROWER and userId == authenticated identity
  S->>R: lockEquipment (FOR UPDATE)
  R->>D: SELECT … FROM equipment FOR UPDATE
  S->>R: lockRequesterAccountStatus (FOR UPDATE)
  S->>R: countOpen(requester, now)
  S->>R: hasOverdueLoan(requester, now)
  S->>P: evaluate(context)
  P-->>S: Violations
  alt Violations
    S-->>C: RulesBroken → 422 + violations
  else Slot occupied
    S-->>C: SlotTaken → 409
  else Valid
    S->>R: insert(…, CONFIRMED or PENDING_APPROVAL)
    R->>D: INSERT (triggers + exclusion constraint)
    alt Concurrent conflict 23P01
      D-->>S: exclusion violation
      S-->>C: SlotTaken → 409
    else Success
      S->>R: audit RESERVATION_SUBMIT (same transaction)
      S->>E: publish ReservationSubmitted (same transaction)
      S-->>C: Reservation
      C-->>W: 201 Created + ReservationView
      W-->>B: My reservations, show result
    end
  end
```

## 4. Reservation States

```mermaid
stateDiagram-v2
  [*] --> CONFIRMED: Submit (approval unnecessary)
  [*] --> PENDING_APPROVAL: Submit (approval required)
  PENDING_APPROVAL --> CONFIRMED: Approve (UC02, unexpired)
  PENDING_APPROVAL --> REJECTED: Reject (UC02, may be expired)
  PENDING_APPROVAL --> CANCELLED: Borrower cancellation (UC01)
  CONFIRMED --> CANCELLED: Borrower cancellation (UC01, not checked out)
  CONFIRMED --> FULFILLED: Checkout (UC03)
  REJECTED --> [*]
  CANCELLED --> [*]
  FULFILLED --> [*]
  note right of PENDING_APPROVAL
    PENDING_APPROVAL / CONFIRMED / FULFILLED
    Occupy slots (reservation_no_overlap exclusion)
  end note
```

## 5. Design

### 5.1 Layers (Technology Stack §2.2)

| Layer | equipment | reservation |
| --- | --- | --- |
| `api` | `EquipmentController`, `EquipmentProblemHandler` | `ReservationController`, `ReservationProblemHandler` |
| `service` | `EquipmentSearchService` | `ReservationService`, `ReservationApprovalService` (UC02), `ReservationApprovalAdapter`, events |
| `domain` | `EquipmentSearchCriteria`, `TimeWindow`, `EquipmentSummary` | `ReservationStatus`, `ReservationRequest`, `policy/*` |
| `repository` | `EquipmentSearchRepository` (JdbcTemplate) | `ReservationRepository` (JdbcTemplate) |

JdbcTemplate is used because queries depend on PostgreSQL ranges, exclusions, and `FOR UPDATE` (technology stack §1.2 allows JdbcTemplate for locking/complex queries).

### 5.2 Patterns (Proposal 9.3)

| Pattern | Location | Purpose |
| --- | --- | --- |
| Strategy | `ReservationPolicy` + 5 implementations (`StartInFuturePolicy`, `DurationPolicy`, `BookingHorizonPolicy`, `OpenReservationQuotaPolicy`, `OverdueLoanPolicy`), composed by `ReservationRules` from `ReservationLimits` | Independent rules; adding, removing or switching off a policy needs no service change |
| State | `ReservationStatus.isOpen / isCancellable` + triggers | Central transitions, database enforcement |
| Observer | `ReservationSubmitted`, `ReservationCancelled` | Notification subscribers without module coupling; the audit row is written directly by `ReservationRepository.audit` so it cannot be skipped by a missing listener |
| Adapter | `ReservationApprovalAdapter` | Connect UC02 port to public reservation service |

### 5.3 Consistency and Concurrency

- Check/write in one transaction; equipment → user/reservation lock order matches triggers and checkout/return.
- Application checks provide clear errors; **exclusion constraints guarantee no overlap**. Tests show one winner among 8 requests.
- Search Free and insert triggers use `serms.equipment_can_reserve`, avoiding inconsistent rules; another request winning first can still cause failure.

### 5.4 Analysis and Design Class Diagrams

Analysis classes (boundary, control, entity), source [`uc01-analysis-class.mmd`](diagrams/uc01-analysis-class.mmd):

```mermaid
%% UC01 analysis classes: boundary, control and entity objects (business view, no framework detail).
classDiagram
  direction LR
  class SearchEquipmentUI {
    <<boundary>>
    keyword
    category
    location
    window
    availableOnly
  }
  class ReserveEquipmentUI {
    <<boundary>>
    startAt
    endAt
    purpose
  }
  class MyReservationsUI {
    <<boundary>>
    statusFilter
  }
  class SearchController {
    <<control>>
    search(criteria)
    availability(equipment, window)
  }
  class ReservationController {
    <<control>>
    submit(request)
    cancel(reservation)
    listMine(status)
  }
  class BookingRules {
    <<control>>
    evaluate(request) violations
  }
  class Borrower {
    <<entity>>
    accountStatus
  }
  class Equipment {
    <<entity>>
    assetTag
    name
    category
    location
    status
    requiresApproval
    maxBorrowDuration
  }
  class Reservation {
    <<entity>>
    startAt
    endAt
    status
    purpose
  }
  class Loan {
    <<entity>>
    dueAt
    status
  }
  class MaintenanceCase {
    <<entity>>
    status
  }
  class AuditRecord {
    <<entity>>
    action
    actor
    occurredAt
  }
  SearchEquipmentUI --> SearchController
  ReserveEquipmentUI --> ReservationController
  MyReservationsUI --> ReservationController
  SearchController --> Equipment
  SearchController --> Reservation : busy slots
  ReservationController --> BookingRules
  ReservationController --> Reservation
  ReservationController --> AuditRecord
  BookingRules --> Loan : overdue check
  Borrower "1" --> "0..*" Reservation : requests
  Equipment "1" --> "0..*" Reservation : held by
  Equipment "1" --> "0..*" MaintenanceCase : open fault blocks booking
  Reservation "1" --> "0..1" Loan : fulfilled by (UC03)
```

Design classes as implemented, source [`uc01-design-class.mmd`](diagrams/uc01-design-class.mmd). The analysis control `BookingRules` became the Strategy composition `ReservationRules`; the entity `AuditRecord` is a row written through `ReservationRepository.audit`; the UC02 port is reached only through `ReservationApprovalAdapter`.

```mermaid
%% UC01 design classes as implemented in sg.edu.nus.serms.{equipment,reservation}.
classDiagram
  direction TB
  class EquipmentController {
    +search(q, category, location, from, to, availableOnly, page, size) EquipmentPage
    +filters() FilterOptionsView
    +availability(id, from, to) AvailabilityView
  }
  class EquipmentSearchService {
    +search(EquipmentSearchCriteria) SearchResult
    +availability(UUID, TimeWindow) Availability
    +filterOptions() FilterOptions
  }
  class EquipmentSearchRepository {
    +search(EquipmentSearchCriteria) SearchResult
    +findVisible(UUID, TimeWindow) Optional~EquipmentSummary~
    +busySlots(UUID, TimeWindow) List~BusySlot~
    +hasOpenMaintenance(UUID) boolean
  }
  class EquipmentSearchCriteria {
    <<record>>
    +window() Optional~TimeWindow~
  }
  class TimeWindow {
    <<record>>
    +overlaps(TimeWindow) boolean
  }
  class ReservationController {
    +create(CreateReservationRequest) ReservationView
    +mine(status, page, size) ReservationPage
    +get(UUID) ReservationView
    +cancel(UUID) ReservationView
  }
  class ReservationService {
    +submit(requesterId, ReservationRequest) Reservation
    +mine(requesterId, status, page, size) ReservationPage
    +get(requesterId, reservationId) Reservation
    +cancel(requesterId, reservationId) Reservation
  }
  class ReservationRepository {
    +lockEquipment(UUID) Optional~LockedEquipment~
    +lockRequesterAccountStatus(UUID) Optional~String~
    +overlapsHeldSlot(UUID, Instant, Instant) boolean
    +countOpen(UUID, Instant) int
    +hasOverdueLoan(UUID, Instant) boolean
    +insert(...)
    +changeStatus(...) boolean
    +audit(actorId, action, reservationId, summary, at)
  }
  class ReservationRules {
    -List~ReservationPolicy~ policies
    +evaluate(ReservationRuleContext) List~RuleViolation~
  }
  class ReservationPolicy {
    <<interface>>
    +evaluate(ReservationRuleContext) Optional~RuleViolation~
  }
  class StartInFuturePolicy
  class DurationPolicy
  class BookingHorizonPolicy
  class OpenReservationQuotaPolicy
  class OverdueLoanPolicy
  class ReservationRuleContext {
    <<record>>
    request
    now
    openReservations
    requiresApproval
    hasOverdueLoan
    equipmentMaxDuration
  }
  class ReservationStatus {
    <<enumeration>>
    PENDING_APPROVAL
    CONFIRMED
    REJECTED
    CANCELLED
    FULFILLED
    +isOpen() boolean
    +isCancellable() boolean
  }
  class ReservationLimits {
    <<record>>
    minDuration
    maxDuration
    bookingHorizon
    maxOpenReservations
    blockOverdueBorrowers
  }
  class ReservationSubmitted {
    <<event>>
  }
  class ReservationCancelled {
    <<event>>
  }
  class ReservationApprovalService {
    <<interface>>
    +findPendingApproval(Pageable) Page~PendingReservation~
    +confirm(UUID, long) PendingReservation
    +reject(UUID, long) PendingReservation
  }
  class DefaultReservationApprovalService
  %% ReservationApprovalPort is owned by UC02 (approval module).
  class ReservationApprovalPort {
    <<interface>>
  }
  class ReservationApprovalAdapter

  EquipmentController --> EquipmentSearchService
  EquipmentSearchService --> EquipmentSearchRepository
  EquipmentSearchService ..> EquipmentSearchCriteria
  EquipmentSearchCriteria ..> TimeWindow
  ReservationController --> ReservationService
  ReservationService --> ReservationRepository
  ReservationService --> ReservationRules
  ReservationService ..> ReservationSubmitted : publishes
  ReservationService ..> ReservationCancelled : publishes
  ReservationRules o-- "1..*" ReservationPolicy
  ReservationPolicy <|.. StartInFuturePolicy
  ReservationPolicy <|.. DurationPolicy
  ReservationPolicy <|.. BookingHorizonPolicy
  ReservationPolicy <|.. OpenReservationQuotaPolicy
  ReservationPolicy <|.. OverdueLoanPolicy
  ReservationPolicy ..> ReservationRuleContext
  ReservationLimits ..> ReservationRules : configures
  ReservationRepository ..> ReservationStatus
  ReservationApprovalService <|.. DefaultReservationApprovalService
  DefaultReservationApprovalService --> ReservationRepository
  ReservationApprovalPort <|.. ReservationApprovalAdapter
  ReservationApprovalAdapter --> ReservationApprovalService
```

## 6. Test Bench and Execution

| Layer | Files | Count |
| --- | --- | --- |
| Backend unit | `reservation/domain/ReservationPolicyTest` (12), `equipment/domain/EquipmentSearchCriteriaTest` (6), `reservation/service/ReservationServiceTest` (11), `reservation/service/ReservationApprovalAdapterTest` (6) | 35 |
| Backend integration (real PostgreSQL 18, all migrations V001-V006) | `reservation/ReservationPostgresTest` (34: Search, Submit, OverdueAndAudit, Cancel, MyReservations, ApprovalContract), `equipment/repository/EquipmentMasterDataPostgresTest` (5, V005 read fields) | 39 |
| Backend authorization/API | `reservation/service/ReservationServiceSecurityTest` (4), `reservation/api/ReservationApiTest` (13) | 17 |
| Frontend components (Vitest + MSW) | `features/equipment/search.test.tsx` (6), `features/reservation/reservation.test.tsx` (11) | 17 |
| E2E, mocked API (Playwright desktop/mobile) | `e2e/reservation.spec.ts` | 1 flow × 2 viewports |
| E2E, real local stack (React → Spring Boot → PostgreSQL 18) | `scripts/check-uc01-browser.mjs` | 8 checks × 2 viewports |

```sh
cd app && ./mvnw verify          # Docker, or SERMS_APP_TEST_JDBC_URL pointing at an isolated serms_test database
cd frontend && pnpm install --frozen-lockfile
OPENAPI_FILE=../app/target/openapi.json pnpm check:api
pnpm lint && pnpm typecheck && pnpm test && pnpm build
pnpm test:e2e
node scripts/check-uc01-browser.mjs   # from the repository root, with the local demo running
```

Latest local result (2026-10-15, main `f9596c1` plus this branch's script): all 589 backend tests passed on PostgreSQL 18.4; UC01 service + domain line coverage 96.4% (323 of 335 lines in `equipment.{domain,service}` and `reservation.{domain,domain.policy,service,service.event}`). Frontend lint, typecheck and all 133 component tests passed. The real-stack browser check passed 16 of 16. Details and screenshots: [UC01 verification record](evidence/uc01/verification.md); earlier mocked screenshots remain in [`docs/evidence/uc01/`](evidence/uc01/).

## 7. Known Limitations and Dependencies

- Automatic cancellation of expired pending reservations (R1.11) was dropped by [D-009](collaboration/decisions.md#d-009-scope-freeze); an expired request can only be rejected by an approver (UC02).
- Equipment keeps four states; there is no `RESERVED` equipment state ([D-008](collaboration/decisions.md#d-008-equipment-and-maintenance-state-models)). Whether an item is taken is derived from held reservation slots.
- Per-item limits come from equipment master data (V005). Editing them belongs to equipment management (#17, Zhou Fanhao); UC01 only reads them.
- Policy values (30 minutes to 7 days, 90-day horizon, 3 open reservations, overdue blocking on) are configuration defaults in `ReservationLimits`; changing them changes the contract in section 6 of the [UC01 contract](contracts/equipment-and-reservation.md#6-reservation-policies-configurable-strategy-pattern).
- Team member checks, cloud deployment and team Done are recorded separately from these local results.
