# UC03 Checkout and Return Interface Contract

Owner: Zhou Fanhao. Implementation: `app/.../loan` and `frontend/src/features/loan`. All business endpoints require login; writes require CSRF headers. CUSTODIAN performs physical handover; BORROWER reads personal records; no implicit ADMIN inheritance.

| Method and path | Purpose | Role |
| --- | --- | --- |
| GET `/api/v1/loans/checkouts?page=0&size=20&q=...` | Unended CONFIRMED reservations, ordered by start | CUSTODIAN |
| POST `/api/v1/loans` | Actual checkout; 201 and Location | CUSTODIAN |
| POST `/api/v1/loans/{loanId}/return` | Receive physical return; 200 | CUSTODIAN |
| GET `/api/v1/loans?activeOnly=true&page=0&size=20&q=...` | All records for custodians, own records for borrowers; latest checkout first | CUSTODIAN / BORROWER |
| GET `/api/v1/loans/{loanId}` | Details; another user's record returns 404 to ordinary borrowers | CUSTODIAN / BORROWER |

Pagination returns arrays; `size` 1–100, `page` 0–100000. No total count; stop after a short page. Queues include future reservations and do not establish checkout eligibility; the service revalidates under locks.

The optional `q` filters both lists by reservation ID, borrower display name or email, or equipment asset tag. Matching is case-insensitive and partial; `%`, `_` and backslash are literal characters. Leading and trailing whitespace is ignored; a blank value leaves the list unfiltered. The maximum non-blank query length is 100 characters. Borrowers remain limited to their own loans, and only custodians may read the checkout queue. The asset tag is included in every `CheckoutCandidate` and `LoanView` so the storekeeper can identify the physical equipment.

Checkout request:

```json
{"reservationId":"uuid","borrowerId":"uuid","expectedVersion":0,"identityConfirmed":true}
```

`borrowerId` identifies the person physically checked and must match saved requesterId; it is not the actor. expectedVersion comes from the queue. identityConfirmed records the custodian's on-site check; software cannot replace physical verification.

Return request:

```json
{"expectedVersion":0,"condition":"DAMAGED","note":"Lens cracked"}
```

condition is GOOD / DAMAGED only; damage requires a non-blank note, maximum 2000 characters. expectedVersion comes from Loan. The backend generates time, return actor, and overdue flag, never the client.

LoanView contains loanId, reservationId, equipmentId, equipmentName, borrowerId, borrowerName, checkoutBy, returnBy, checkedOutAt, dueAt, returnedAt, status, returnCondition, returnNote, version, overdue. ACTIVE overdue means now > dueAt; RETURNED overdue means returnedAt > dueAt. dueAt equals original reservation endAt; UTC Instant, microsecond precision. Backend versions are int64; the page currently uses JSON number like other modules.

| Status | Scenario |
| --- | --- |
| 400 | Missing fields, invalid version/condition, identity unconfirmed, missing damage note |
| 401 | Unauthenticated |
| 403 | CSRF, role, or database account authorization failure |
| 404 | Missing or inaccessible loan |
| 409 | State/window/version/borrower/equipment conflict; `/problems/loan-conflict` |
| 503 | Database temporarily unavailable; no SQL/credentials returned |

## Transactions and Cross-module Events

Lock order: Equipment → Reservation → Loan → MaintenanceCase. Handover, maintenance writes, states, success audits, and synchronous events share one Spring transaction. Updates use `WHERE version=?`; database triggers increment versions, not clients/services. Failures roll back without unbounded retries; if connection failure leaves commit uncertain, refresh/confirm rather than resend blindly.

`LoanEvent` contains action (CHECKED_OUT / RETURNED), loanId, reservationId, equipmentId, borrowerId, actorId, dueAt, occurredAt, late, returnCondition, maintenanceCaseId. Published synchronously before commit; database consumers join the transaction, while email/external effects require after-commit/outbox. UUID Loan is the business key; V003 bigint reminderId bridges notification interfaces. Use `LoanNotificationRequestFactory`, never truncate UUIDs to integers.

Wang Yuanmeng owns overdue scheduling/reminder periods outside this module. Return immediately persists RETURNED; `PostgresLoanReminderGuard` rechecks ACTIVE before delivery using the same lock order.

## UC05 Overdue Projection

UC05 exposes `GET /api/v1/overdue?page=0&size=20&order=desc` for `CUSTODIAN` and `ADMIN`. This read-only projection selects `ACTIVE` loans where `due_at < now`; it does not change the UC03 loan state or checkout/return endpoints. Each row has `loanId`, `reservationId`, `borrowerName`, `equipmentName`, `dueAt` (UTC instant) and `overdueSeconds`. `order=desc` lists longest overdue first; `order=asc` reverses that order. Both orders use `loanId` as a stable tie-breaker. Page size is 1–100. The response includes `content`, `totalElements`, `page` and `size`. The frontend formats `dueAt` in Singapore time.

Confirmed for PR #12 on 2026-10-15 under repository owner Liu Tongyao's direct instruction. The owner transferred this projection's contract-confirmation task from Zhou Fanhao to Liu Tongyao and delegated its technical review to Codex; see [the responsibility record](../collaboration/decisions.md#uc05-projection-contract-confirmation-2026-10-15). This confirmation covers the read-only endpoint, operator roles, overdue boundary, fields, paging/sorting and unchanged UC03 write/lock semantics. It does not claim a review by Zhou Fanhao or alter the ownership of the UC03 implementation.
