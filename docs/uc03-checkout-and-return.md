# UC03 Equipment Checkout and Return

The primary actor is Custodian; Borrower is the requester and actual borrower. Handover occurs on site; authenticated users record actual events. Custodians get confirmed reservations/active loans; borrowers get personal history.

## Normal Checkout Flow

1. Custodian logs in and opens Loans and returns.
2. List unended CONFIRMED reservations.
3. Select a record, match on-site identity to borrowerId, tick confirmation.
4. Under equipment lock, check active roles, version, window, equipment facts.
5. Atomically create ACTIVE Loan with checkoutBy/checkedOutAt/dueAt, set FULFILLED reservation and ON_LOAN equipment, audit and publish LoanEvent.
6. After commit, refresh and show success.

## Checkout Exception Flows

Unauthenticated → 401; missing handover role/disabled account → 403; unconfirmed identity/invalid version → 400; missing reservation → 404. Reject unapproved/cancelled/rejected/already issued/outside-window reservations, mismatched/disabled borrowers, faulty equipment, and active loans. Version conflict → 409, refresh/reselect. Concurrent checkout has one winner; failures create neither Loan nor success audit.

## Normal and Exceptional Returns

Custodian selects ACTIVE Loan, Receive return, verifies equipment, enters GOOD/DAMAGED. Version/locks validate and persist RETURNED, returnBy, returnedAt, condition, note. Damage requires a description and linked case/history. Preserve late facts when returned after dueAt; no OVERDUE state or automatic fines.

Early return retains original slot. Active maintenance retains UNDER_MAINTENANCE; RETIRED equipment may be received but stays RETIRED; otherwise no active maintenance/loan restores AVAILABLE. Duplicate/stale returns → 409; reject return before checkout. Maintenance, audit, or synchronous-consumer failures roll back the entire return.

## Design Problem and Pattern Choice

Allowed checkout and post-return states risk scattering across controllers/services/maintenance. Direct if/else on AVAILABLE, ON_LOAN, UNDER_MAINTENANCE, RETIRED is simple but can omit retirement precedence in new operations.

Options: direct conditions, full State class hierarchy, enum state objects. `EquipmentLoanState` uses enum singletons: AVAILABLE overrides `permitsCheckout()`, other states deny; `afterReturn()` centralizes retirement/maintenance/holding precedence. Services query facts, roles, windows, locks, transactions; state objects do not query databases. Loan remains ACTIVE/RETURNED with simple checks and no OverdueState.

Previously services compared strings and decided each handover; now they delegate equipment-state behavior, independently testable over all states/priorities. Enum centralizes finite states and requires editing the type for new ones; split classes if behavior grows. UC03 preserves maintenance's state machine and calls its public Service for faults.

## Acceptance Criteria

| Scenario | Expected |
| --- | --- |
| Valid checkout/normal return | Four sets of facts and audits agree; versions increment |
| Unapproved/cancelled/early/expiry boundary/fault | Rejected with no partial writes |
| Mismatched borrower/disabled/revoked custodian | Reject handover |
| Concurrent checkout/return | One success, no duplicate loans/cases |
| Late and damaged | RETURNED + late facts + DAMAGED + OPEN case |
| Retired/under maintenance | Receive without restoring AVAILABLE |
| Synchronous consumer failure | Loan/equipment/maintenance/audit roll back together |
| Borrower accessing others | No list disclosure, detail 404, no writes |
| Conflict UI | Error, refresh/reselect, no success |

Editable diagrams: `docs/diagrams/uc03-*.mmd`. Results: [Delivery/verification](zhoufanhao-validation.md); deployment acceptance, review, and meeting evidence remain team work.
