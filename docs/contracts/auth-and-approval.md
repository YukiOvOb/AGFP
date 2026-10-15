# Authentication and Approval Interface Contract (UC02)

Default runtime configuration finalized: the backend uses PostgreSQL 18 with approval enabled by default. The sole production migration source is `app/src/main/resources/db/migration`. The MySQL production driver and migrations have been removed; H2 remains test-scoped only. Shi Wenqi still owns server web/app/db deployment.

Status: **Confirmed**. This repository file is authoritative. Changes require a PR and confirmation from the owners of affected modules.

2026-10-07 local integration record: the `serms-postgres` application packages shared migrations and enables real Identity, the reservation approval port, and ApprovalDecisionPort. Section 6 conventions are implemented through new V004, preserving the old V002 checksum; the earlier instruction to edit unmerged V002 directly no longer applies to this backup. MySQL/H2 remain only as the existing notification test baseline. Remote review and deployment await repository recovery and team completion; see the [Zhou handover checklist](../zhoufanhao-handoffs.md).

| Stakeholder | Responsibilities |
| --- | --- |
| Zhang Hanming | UC02 approval (`approval` module, `frontend/src/features/approval/`), test infrastructure |
| Shi Wenqi | UC01 reservations (`reservation` module, providing the public service in Section 3) |
| Zhou Fanhao | Database schema and migrations (`approval_decision`, `reservation`, etc.) |
| Wang Yuanmeng | Backend platform (authentication, ProblemDetail, OpenAPI) and frontend scaffolding |

---

## 1. Development Sequence

| Order | PR | Owner | Dependencies |
| --- | --- | --- | --- |
| 1 | PostgreSQL 18 + Flyway: move schema migrations into `app/`, remove MySQL / H2, and complete the database changes in Section 6 | Zhou Fanhao | — |
| 2 | Test infrastructure: Testcontainers bases, test data builders, ArchUnit rules | Zhang Hanming | 1 |
| 3 | Backend platform: authentication endpoints (Section 2), Principal mapping, global ProblemDetail (Section 5), springdoc OpenAPI; remove Thymeleaf | Wang Yuanmeng | 1 |
| 4 | Frontend scaffolding: Vite + React + TS, routing, `AuthProvider`, API client, OpenAPI type generation | Wang Yuanmeng | 3 |
| 5 | CI / deployment: backend and frontend CI jobs; change Compose to `web` + `app` + `db` | Shi Wenqi | 1; frontend job after 4 |
| 6 | UC01 reservations, including `ReservationApprovalService` from Section 3 | Shi Wenqi | 1, 3 |
| 7 | UC02 approval (API layer, integration tests, frontend pages) | Zhang Hanming | 2, 3, 4, 6 |

- Create the UC02 branch from main as `feature/zhang-hanming-uc02-approval`; merge the latest main before opening the PR.
- Work independent of the foundation PRs can start first: approval domain logic, Chain of Responsibility, and unit tests.
- Until PR 6 merges, write PR 7 tests with a mock `ReservationApprovalService`.

---

## 2. Authentication (Identity)

### 2.1 CSRF and Sessions

- The backend uses `CookieCsrfTokenRepository`: cookie `XSRF-TOKEN` (readable by the frontend), header `X-XSRF-TOKEN`.
- On startup, the frontend calls `GET /api/v1/auth/csrf` (204) to obtain the CSRF cookie.
- **All** `POST` / `PUT` / `PATCH` / `DELETE` requests must include `X-XSRF-TOKEN`, including login and logout.
- The session cookie is `JSESSIONID`, with `HttpOnly`, `Secure`, and `SameSite=Lax`; successful login rotates the session ID to prevent session fixation.

### 2.2 Endpoints

| Endpoint | Request body | Success | Failure |
| --- | --- | --- | --- |
| `GET /api/v1/auth/csrf` | — | 204, issues `XSRF-TOKEN` cookie | — |
| `POST /api/v1/auth/login` | `{ "email": string, "password": string }` | 200, returns `CurrentUser` | 400 malformed body; 401 unknown account, incorrect password, or disabled account (same message for all); 403 CSRF validation failure |
| `POST /api/v1/auth/logout` | — | 204, invalidates session | 403 CSRF validation failure |
| `GET /api/v1/auth/me` | — | 200, returns `CurrentUser` | 401 unauthenticated |

`CurrentUser`:

```json
{
  "userId": "7f1c…-uuid",
  "email": "someone@u.nus.edu",
  "displayName": "Someone",
  "roles": ["BORROWER", "APPROVER"]
}
```

### 2.3 Principal and User UUID Mapping

- A custom `SermsUserPrincipal implements UserDetails` contains `userId` (UUID), `email`, and `roles`.
- `Authentication.getName()` returns **the string representation of `userId`**, matching the notification recipient identifier (`notification_principal` defaults to `user_id::text`).
- Controllers obtain the current user through `@AuthenticationPrincipal SermsUserPrincipal me` and pass `UUID actorId` to services; the service layer does not depend on Spring Security types.

### 2.4 Roles

- Five roles: `BORROWER`, `APPROVER`, `CUSTODIAN` (equipment custodian), `MAINTAINER` (technician), and `ADMIN`, mapped to Spring Security authorities `ROLE_<role>`.
- Approval endpoints require `ROLE_APPROVER`.
- **`ADMIN` does not automatically grant approval permission**; assign `APPROVER` separately when needed.

### 2.5 401 and 403

| Scenario | Status code |
| --- | --- |
| Unauthenticated or expired session | 401 (API returns ProblemDetail without redirecting to login) |
| Authenticated but missing the required role | 403 |
| Missing or invalid CSRF header | 403 |

### 2.6 Administrator-triggered session revocation

Wang Yuanmeng confirmed the following through Zhang Hanming's pasted reply on 2026-10-15.
Administrator account deactivation, actual role changes and password resets revoke all older
sessions on each session's next protected API request: clear authentication/session and return
the existing 401 ProblemDetail. Disabled accounts cannot log in. A fresh authorized session
without the requested role still receives 403, and ADMIN gains no implicit roles.
Use a database monotonic session version compared with the login snapshot, not password hashes
in the Session. Security changes, version increments and audits use one transaction. `/auth/csrf`
remains available before login and `/auth/logout` remains idempotent with valid CSRF.
Implementation/endpoints, additive migration numbering and test scope are in the
[administrator console contract](admin-console.md#confirmed-identitysession-continuation--2026-10-15).
This records the communicated behaviour confirmation, not a whole-PR review or deployment acceptance.

---

## 3. Public Service Provided by the Reservation Module

The approval module **must not** access the `reservation` repository; it may only call the following public service.

```java
package sg.edu.nus.serms.reservation.service;

public interface ReservationApprovalService {
    /** Pending list, read-only without locking; includes expired pending reservations. */
    Page<PendingReservation> findPendingApproval(Pageable pageable);

    /** One reservation, read-only without locking; throws ReservationNotFoundException if absent. */
    PendingReservation get(UUID reservationId);

    /** Approve: PENDING_APPROVAL → CONFIRMED. Must run in the caller's transaction. */
    PendingReservation confirm(UUID reservationId, long expectedVersion);

    /** Reject: PENDING_APPROVAL → REJECTED, releasing the slot. Must run in the caller's transaction. */
    PendingReservation reject(UUID reservationId, long expectedVersion);
}
```

`PendingReservation` (record):

| Field | Type | Description |
| --- | --- | --- |
| `reservationId` | `UUID` | |
| `requesterId` | `UUID` | |
| `requesterName` | `String` | For display |
| `equipment` | `EquipmentSummary` | `equipmentId` (UUID), `assetTag`, `name`, `category` |
| `startAt` / `endAt` | `Instant` | |
| `purpose` | `String` | Nullable |
| `status` | `ReservationStatus` | `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `FULFILLED` |
| `version` | `long` | Optimistic locking version |
| `createdAt` | `Instant` | |
| `expired` | `boolean` | `true` when `now >= startAt` and status is `PENDING_APPROVAL` |

---

## 4. Responsibilities for confirm / reject

### 4.1 Reservation Module (Inside `confirm` / `reject`)

Both methods use `@Transactional(propagation = Propagation.MANDATORY)` and must run within the approval module's transaction, so the reservation state change and approval record commit or roll back together.

1. **Locking**: `SELECT … FOR UPDATE`, equipment first and reservation second, matching checkout / return to avoid deadlocks.
2. **Version check**: throw `ReservationVersionConflictException` when `version != expectedVersion`.
3. **State check**: throw `ReservationStateConflictException` if the status is not `PENDING_APPROVAL` (include current status and version).
4. **`confirm` only**:
   - Throw `ReservationExpiredException` when expired (`now >= startAt`). The database trigger only blocks confirmation after the end time, so the service must check the start time.
   - Throw `ReservationStateConflictException` if equipment enters maintenance or is retired while waiting; database triggers also enforce this restriction.
5. **`reject` only**: **expired pending reservations may still be rejected** to release the slot and preserve a decision record.
6. **Slot release**: the exclusion constraint applies only to `PENDING_APPROVAL`, `CONFIRMED`, and `FULFILLED`. Changing to `REJECTED` releases the slot automatically without extra code.

### 4.2 Approval Module

1. Authorization: `@PreAuthorize("hasRole('APPROVER')")`.
2. **No self-approval**: reject when `requesterId == actorId`, even if the user is also `ADMIN` (backed by the `check_approval` database trigger).
3. Rejection requires a reason (backed by a database `CHECK` constraint).
4. Insert `approval_decision`: unique `reservation_id` ensures **one decision per reservation**; a second concurrent insert violates uniqueness and maps to 409.
5. Publish `ReservationDecided` so notifications inform the requester and audits record the operation.

### 4.3 Transaction Order for a Decision

```
@Transactional  ApprovalService.decide(actorId, reservationId, decision, comment, expectedVersion)
  1. reservation = reservationApprovalService.get(reservationId)         // 404
  2. Check for self-approval                                            // 403
  3. APPROVED → reservationApprovalService.confirm(id, expectedVersion)  // 409
     REJECTED → reservationApprovalService.reject(id, expectedVersion)   // 409
  4. INSERT approval_decision                                           // Unique constraint conflict → 409
  5. publish ReservationDecided
```

---

## 5. Approval REST API

### 5.1 Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/approvals/pending?page=0&size=20&sort=startAt,asc` | Paginated `PendingReservation` list, ascending `startAt` by default; includes expired entries (`expired: true`) |
| `GET /api/v1/approvals/{reservationId}` | Path parameter is the **reservation ID**. Returns `PendingReservation`, with `decision` (`ApprovalDecision`) if already decided |
| `POST /api/v1/approvals/{reservationId}/decision` | Submit a decision; returns 200 on success |

Request body:

```json
{ "decision": "APPROVED", "comment": "Optional", "expectedVersion": 3 }
```

| Field | Rule |
| --- | --- |
| `decision` | Required; `APPROVED` or `REJECTED` |
| `comment` | Required and non-blank for `REJECTED`; optional for `APPROVED`; maximum 1000 characters |
| `expectedVersion` | Required integer (int64) |

Success response:

```json
{
  "reservation": { "…": "Updated PendingReservation" },
  "decision": {
    "approvalId": "uuid",
    "reservationId": "uuid",
    "approverId": "uuid",
    "decision": "REJECTED",
    "comment": "The equipment requires calibration during this time slot",
    "decidedAt": "2026-10-01T02:30:00Z"
  }
}
```

### 5.2 Errors: Consistent ProblemDetail (RFC 9457)

| Status code | Scenario | `type` |
| --- | --- | --- |
| 400 | Request validation failure, including a missing rejection reason; includes `errors: [{ "field", "message" }]` | `/problems/validation` |
| 401 | Unauthenticated | `/problems/unauthenticated` |
| 403 | Missing `APPROVER` role | `/problems/forbidden` |
| 403 | Deciding one's own reservation | `/problems/self-approval` |
| 403 | CSRF validation failure | `/problems/csrf` |
| 404 | Reservation not found | `/problems/not-found` |
| 409 | Status is not `PENDING_APPROVAL` (including already decided), or equipment unavailable | `/problems/state-conflict` |
| 409 | Version mismatch | `/problems/version-conflict` |
| 409 | Approving an expired reservation | `/problems/reservation-expired` |

- All 409 responses also include `currentStatus` and `currentVersion`; the frontend uses them to prompt, "This reservation has been updated by someone else. Please refresh."
- **Authorization precedes lookup**: users without `APPROVER` receive 403 for any ID, preventing discovery of reservations through 404 responses.
- ProblemDetail contains no stack traces, SQL, or internal class names.

---

## 6. Shared Data Conventions

| Item | Convention | Required changes |
| --- | --- | --- |
| Decision enum | API, Java, and database consistently use **`APPROVED` / `REJECTED`**, replacing `APPROVE` / `REJECT` | Zhou Fanhao's V002 currently uses `CHECK (decision IN ('APPROVE','REJECT'))` and a reason constraint with `decision <> 'REJECT'`; change these to `APPROVED` / `REJECTED`. V002 is not yet merged into main, so it can be edited on his branch without adding a migration |
| Version | Java `long`, database **`BIGINT`**, JSON integer | V002 `version` columns in `equipment`, `reservation`, `loan`, and `maintenance_case` currently use `integer`; change to `bigint` |
| Time | Database `timestamptz`, Java `Instant`, JSON ISO-8601 UTC strings; frontend display in `Asia/Singapore` | — |
| ID | UUID, represented as a JSON string | — |

---

## 7. Frontend

- Module directory: `frontend/src/features/approval/`.
- Routes: `/approvals` (pending list), `/approvals/:reservationId` (details and decision).
- **Provided by the frontend scaffolding PR (PR 4 in Section 1)**; the approval module reuses these rather than implementing its own:
  - OpenAPI type generation: `pnpm gen:api` generates `frontend/src/api/schema.d.ts` from backend `/api/v1/openapi.json`; CI checks for drift.
  - Global authentication: `AuthProvider`, `useAuth()` (current user and roles), and `<RequireRole role="APPROVER">` route guard.
  - API wrapper: automatically adds `X-XSRF-TOKEN`; redirects to login on 401; consistently parses ProblemDetail.
- Page behavior: expired pending entries display an "Expired" badge, hide "Approve", and retain "Reject"; the reason field is required when rejecting.

---

## 8. Business Rule Summary

1. UC01 sets the initial reservation status using equipment `requires_approval`: no approval needed → `CONFIRMED`; approval needed → `PENDING_APPROVAL`.
2. Only `APPROVER` can decide; self-approval is prohibited, including for `ADMIN`.
3. Each reservation can be decided only once, and the decision is immutable (enforced by the `approval_immutable` database trigger).
4. Approval: `PENDING_APPROVAL` → `CONFIRMED`.
5. Rejection: `PENDING_APPROVAL` → `REJECTED`, releasing the slot; a reason is required.
6. Expired (`now >= startAt`) pending reservations remain visible with an expired label; they **cannot be approved** (409) but **may be rejected** to release the slot and record the result.
7. Record approver, decision, comment, and time (Proposal 5.4).
8. Future work (outside this scope): a scheduled task automatically changes expired pending reservations to `CANCELLED`.

---

## 9. Items Pending Confirmation

| Item | Current situation | Confirmation owner |
| --- | --- | --- |
| **Four or five equipment states** | Proposal 5.2 lists five: Available, Reserved, Issued, Under Maintenance, Retired; this is the source of the technology document's five-state description. Zhou Fanhao's V002 follows his local `SERMS_Equipment_and_Loan_States.md` (not committed) and implements four: `AVAILABLE`, `ON_LOAN`, `UNDER_MAINTENANCE`, `RETIRED`. The difference is **Reserved**: V002 determines reservations for a time interval from the `reservation` table rather than using an equipment state. The final team model must be confirmed against the original state diagram; **this contract does not decide it independently** | Zhou Fanhao checks the original state document; after team confirmation, update the Proposal explanation or V002 |
| Five or six maintenance case states | Proposal 5.6 lists six: Reported, Assigned, In Progress, Resolved, Not Repairable, Closed. V002 has five: `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `UNREPAIRABLE`, without Closed | **Decided** by [D-008](../collaboration/decisions.md#d-008-equipment-and-maintenance-state-models): five states stay. Reported = `OPEN`, Not Repairable = `UNREPAIRABLE`, Closed is folded into the two terminal states; see the [UC04 state model](../uc04-fault-and-maintenance.md#state-model) |

Until these items are confirmed, corresponding descriptions in `docs/tech-stack.md` remain marked as pending confirmation. The approval module does not depend on specific equipment state values; it learns that equipment is unavailable through `ReservationApprovalService` exceptions, so these items do not block UC02 development.
