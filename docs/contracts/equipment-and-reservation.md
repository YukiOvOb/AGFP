# Equipment Search and Reservation Interface Contract (UC01)

Owner: Member 1 (search and reservation). The repository version is authoritative; changes require a PR and affected-owner confirmation (README Collaboration and Releases).

- Data model: merged V001 + V002 in `database/` (`serms` schema). This module **adds no migrations**.
- Implementation: backend `app/src/main/java/sg/edu/nus/serms/{equipment,reservation}`, frontend `frontend/src/features/{equipment,reservation}`.
- Approval-related reservation interfaces follow Sections 3–4 of the [Authentication and approval contract](auth-and-approval.md); implemented here (Section 5).

---

## 1. Shared Conventions

| Item | Convention |
| --- | --- |
| Prefix | `/api/v1`, JSON |
| Authentication | All endpoints require login (session cookie); otherwise 401 `/problems/unauthenticated` |
| CSRF | All `POST` requests require `X-XSRF-TOKEN`; otherwise 403 `/problems/csrf` |
| Identity | Requester **only from authentication** (`SermsUserPrincipal.userId`); no `requesterId` accepted in request bodies |
| Time | ISO-8601 UTC requests/responses (`2030-01-10T01:00:00Z`); input/display in `Asia/Singapore` |
| Interval | Half-open `[startAt, endAt)`, matching `tstzrange(start_at, end_at, '[)')`: 09:00–11:00 and 11:00–12:00 **do not overlap** |
| Pagination | `page` (zero-based), `size` (1–50, default 20); response `content`, `page`, `size`, `totalElements`, `totalPages` |
| Errors | RFC 9457 Problem Details: `type`, `title`, `status`, `detail`; additional `violations` or `errors` (Section 4) |

---

## 2. Equipment Search (Any Authenticated Role)

### 2.1 `GET /api/v1/equipment` — Search

| Parameter | Description |
| --- | --- |
| `q` | Case-insensitive substring of name/asset tag; literal `%` / `_`; maximum 100 |
| `category`, `location` | Exact values from 2.2 |
| `from`, `to` | Both required together, `from < to`, interval ≤ 90 days |
| `availableOnly` | `true` returns only reservable equipment in the supplied window; window required |
| `page`, `size` | Pagination |

- Exclude `RETIRED` equipment; sort by name, then asset tag.
- `availableInWindow` is `null` without a window. With a window, `true` means a reservation submitted **now** would be accepted. Criteria match the insert trigger: `serms.equipment_can_reserve(equipment, from)` (not retired/under maintenance, no open fault, existing loan due by `from`) **and** no overlapping `PENDING_APPROVAL` / `CONFIRMED` / `FULFILLED` reservation.
- Invalid parameters return 400 `/problems/validation`, with an explanatory `detail` (e.g. "Choose a time window to show only available equipment").

```json
{
  "content": [
    {
      "equipmentId": "…", "assetTag": "CAM-001", "name": "Canon EOS R6",
      "category": "Camera", "location": "COM1 Lab", "status": "AVAILABLE",
      "requiresApproval": false, "availableInWindow": true
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### 2.2 `GET /api/v1/equipment/filters` — Search Dropdowns

Returns `{ "categories": [...], "locations": [...] }` (distinct, sorted, excluding retired equipment).

### 2.3 `GET /api/v1/equipment/{id}/availability?from&to` — Occupied Slots in a Window

Returns equipment, `available` (same criteria as 2.1), `openMaintenance` (open fault exists), and `busySlots`.

- `busySlots[].reason`: `PENDING_APPROVAL` (also occupies slots), `RESERVED` (confirmed), `ON_LOAN` (checked out).
- **No occupant identity** is returned, protecting other borrowers.
- Retired or unknown equipment returns 404.

---

## 3. Reservations (`BORROWER` Only)

Services enforce `@PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")`: missing BORROWER gives 403; nobody acts for another borrower.

### 3.1 `POST /api/v1/reservations` — Submit → 201

```json
{ "equipmentId": "…", "startAt": "2030-01-10T01:00:00Z", "endAt": "2030-01-10T03:00:00Z", "purpose": "Project shoot" }
```

- Optional `purpose`, maximum 500. Success: 201, `Location: /api/v1/reservations/{id}`, and `ReservationView`.
- Initial status: equipment `requires_approval = false` → `CONFIRMED`; `true` → `PENDING_APPROVAL` (UC02 Section 8).

Processing order in one transaction:

1. Lock equipment with `SELECT … FOR UPDATE` (retired/missing → 404; maintenance/open fault → 409 `equipment-unavailable`).
2. Lock requester (`app_user`); disabled → 403 `not-eligible`. Equipment-before-user order matches triggers and avoids deadlocks; user locking prevents concurrent quota bypass.
3. Evaluate rules (Section 6), **return all violations together** → 422 `reservation-rule`.
4. Occupied interval → 409 `reservation-conflict`; existing loan at start → 409 `equipment-unavailable`.
5. Insert. Even if concurrent requests bypass step 4, `reservation_no_overlap` rejects them (SQLState `23P01`) → 409 `reservation-conflict`; trigger rejection (`23514`) → 409 `equipment-unavailable`.
6. Append an audit record `RESERVATION_SUBMIT` (actor = requester, entity `Reservation`) in the same transaction (Section 7.1).
7. Publish `ReservationSubmitted` in the same transaction (Section 7).

### 3.2 `GET /api/v1/reservations/mine?status&page&size` — My Reservations

Current user's records only, descending start time; optional `status` (`PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `FULFILLED`); invalid value → 400.

### 3.3 `GET /api/v1/reservations/{id}` — One Personal Reservation

Other users' and missing reservations **both return 404**, without revealing existence.

### 3.4 `POST /api/v1/reservations/{id}/cancel` — Cancel → 200

- Only `PENDING_APPROVAL` / `CONFIRMED` without a checkout record (Proposal rule 4). Checked out → 409 `not-cancellable` (return rather than cancel); cancelled/rejected → 409 `not-cancellable`.
- Conditional version update; concurrent changes → 409 `stale-reservation`.
- Cancellation releases the slot immediately (exclusion applies only to occupying states); append an audit record `RESERVATION_CANCEL` and publish `ReservationCancelled` in the same transaction.

### 3.5 `ReservationView`

| Field | Description |
| --- | --- |
| `reservationId`, `equipmentId` | UUID |
| `assetTag`, `equipmentName`, `location` | Display |
| `startAt`, `endAt`, `createdAt` | UTC |
| `status` | See 3.2 |
| `purpose` | Nullable |
| `version` | Optimistic locking version |
| `cancellable` | Backend-calculated; frontend uses it only to show/hide Cancel |

---

## 4. Error Reference

| HTTP | `type` | Scenario | Extra fields |
| --- | --- | --- | --- |
| 400 | `/problems/validation` | Missing/malformed fields, invalid search | `errors: [{field, message}]` |
| 401 | `/problems/unauthenticated` | Not logged in | |
| 403 | `/problems/csrf` | Missing CSRF header | |
| 403 | `/problems/forbidden` | Missing BORROWER role | |
| 403 | `/problems/not-eligible` | Disabled account | `violations: []` |
| 404 | `/problems/not-found` | Retired/missing equipment; other user's/missing reservation | |
| 409 | `/problems/reservation-conflict` | Overlap, including concurrency | `violations: []` |
| 409 | `/problems/equipment-unavailable` | Maintenance, open fault, loan at start | `violations: []` |
| 409 | `/problems/not-cancellable` | Checked out / ended | `violations: []` |
| 409 | `/problems/stale-reservation` | Version changed | `violations: []` |
| 422 | `/problems/reservation-rule` | Policy violations | `violations: [{code, message}]`, all listed |

Stable `violations[].code` values may be used by frontend/tests: `invalid-interval`, `start-in-past`, `too-short`, `too-long`, `too-far-ahead`, `quota-exceeded`, `overdue-loan`.

---

## 5. Public Service for Approval (UC02)

Implements [auth-and-approval.md](auth-and-approval.md) Sections 3–4 in `sg.edu.nus.serms.reservation.service`:

| Contract requirement | Implementation |
| --- | --- |
| `ReservationApprovalService`: `findPendingApproval(Pageable)`, `get`, `confirm`, `reject` | Interface + `DefaultReservationApprovalService` |
| `PendingReservation` with `requesterName`, `equipment`, `version`, `expired` | `reservation.domain.PendingReservation` |
| `confirm` / `reject` use `Propagation.MANDATORY` | Annotated; no transaction → `IllegalTransactionStateException` |
| Equipment before reservation locks | Implemented |
| Version mismatch → `ReservationVersionConflictException` | Current version included |
| Not pending → `ReservationStateConflictException` | Current status/version included |
| Expired `confirm` (`now >= startAt`) → `ReservationExpiredException`; expired may `reject` | Implemented |
| Maintenance/retired/open fault while waiting → no approval | `ReservationStateConflictException`; trigger fallback mapped likewise |
| Missing → `ReservationNotFoundException` | Extends `RecordNotFoundException`, globally mapped to 404 |

UC02 `ApprovalService` depends on `approval.service.ReservationApprovalPort`. `ReservationApprovalAdapter` implements this port and **delegates to the public service**, avoiding approval access to reservation tables. It maps state/version/expiry exceptions to `ApprovalConflictException` STATE / VERSION / EXPIRED, each with locked `currentStatus` / `currentVersion`; `ReservationNotFoundException` propagates as 404.

---

## 6. Reservation Policies (Configurable Strategy Pattern)

Each rule implements `ReservationPolicy`; `ReservationRules` runs them and collects every violation. Defaults are configured under `serms.reservation.*` (`ReservationLimits`):

| Rule | Class | Default | code |
| --- | --- | --- | --- |
| End after start; start not in past | `StartInFuturePolicy` | — | `invalid-interval` / `start-in-past` |
| Minimum/maximum duration (rule 10) | `DurationPolicy` | 30 minutes – 7 days | `too-short` / `too-long` |
| Booking horizon | `BookingHorizonPolicy` | 90 days | `too-far-ahead` |
| Concurrent unended reservations (rule 11) | `OpenReservationQuotaPolicy` | 3 (`PENDING_APPROVAL` + `CONFIRMED`, unended) | `quota-exceeded` |
| No new booking while holding overdue equipment (rule 11) | `OverdueLoanPolicy` | On (`block-overdue-borrowers`); an `ACTIVE` loan of the requester with `due_at` before now | `overdue-loan` |

> Provisional team defaults, pending Sprint 2 review; changes require configuration only. Frontend `features/reservation/rules.ts` mirrors duration/start rules for immediate feedback; the backend is authoritative.

---

## 7. Domain Events

Published inside the initiating transaction (Spring `ApplicationEventPublisher`); listener notification/audit writes roll back with it (technology stack §2.5).

| Event | Fields | Planned subscribers |
| --- | --- | --- |
| `reservation.service.event.ReservationSubmitted` | `eventId`, `reservationId`, `requesterId`, `equipmentId`, `startAt`, `endAt`, `status` | Notifications (approvers for `PENDING_APPROVAL`), audit |
| `reservation.service.event.ReservationCancelled` | `eventId`, `reservationId`, `requesterId`, `equipmentId` | Notifications/audit; UC05 may cancel unsent reminders |

### 7.1 Audit Records

Submit and cancel each append one row to `serms.audit_log` inside the booking transaction, so a refused or rolled-back request leaves no record (Proposal 5.7, business rule 9). Rows are written by `ReservationRepository.audit`, not by an event listener.

| Action | `actor_id` | `entity_type` / `entity_id` | `change_summary` |
| --- | --- | --- | --- |
| `RESERVATION_SUBMIT` | Requester | `Reservation` / reservation id | `status=…; equipment=…; slot=start/end` |
| `RESERVATION_CANCEL` | Requester | `Reservation` / reservation id | `from=…; equipment=…` |

`outcome` is `SUCCESS`; `request_id` is `reservation:<audit id>`. Approval decisions write their own `RESERVATION_DECIDE` record on the same reservation (UC02).

---

## 8. Items Pending Confirmation

| Item | Current status | Confirmation owner |
| --- | --- | --- |
| Section 6 defaults | Provisional 30 minutes / 7 days / 90 days / 3; Proposal rules 10/11 only require limits | Team Sprint 2 review |
| Category/borrower-specific rules | One global configuration; strategies separated, category values need a new configuration source | Team, when needed |
| Expired pending automatically `CANCELLED` | UC02 Section 8 future work | Member 1 + Member 2 |
| `version` integer → bigint | UC02 Section 6; Java reads `long`, so column change compatible | Member 3 (database) |
