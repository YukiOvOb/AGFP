# PR 8 notification contract review and adapter

Reviewed upstream: PR #8 at `1d3d7bedb9dea9e8a39c2716eda975f1a207d915`.
Local adapter branch: `feature/wang-yuanmeng-approval-notification-adapter`.
This is a dependent branch based on PR #8, not an independent replacement of its approval code.

## Review result

The actual `approval.service.event.ReservationDecided` record is usable for notification integration. It contains eventId, approvalDecisionId, reservationId, requesterId, approverId, decision and comment. ApprovalService synchronously publishes after the reservation transition and decision persistence, within a declared transaction. It must be invoked through the Spring proxy with transactional database adapters for the transaction guarantee to hold.

One contract difference remains: the original proposal included `Instant decidedAt`, but the current record does not. Current notification wording does not require it, so this is not an adapter blocker. The approval owner should confirm whether to add the persisted decision timestamp for audit/display; notification creation time must not be presented as approval time. This adapter does not modify the approval event.

The upstream unit tests use mocked ports and event publisher and do not demonstrate actual reservation/decision/audit database atomicity. That remains an integration test requirement once the shared PostgreSQL foundation is ready. Merely emitting an event before commit does not guarantee success: listeners participate in the transaction, and an audit/persistence failure must abort it.

## Adapter contract

- `ApprovalNotificationListener` consumes the exact upstream event synchronously with `Propagation.MANDATORY`.
- `ApprovalNotificationFactory` maps requesterId's standard UUID string to recipient; approverId is never the recipient.
- Type is BUSINESS_EVENT, sourceId is `reservation-decided:<reservation UUID>`, period is `once`, loanId and expectedDueAt are null.
- Approvals produce confirmation text. Rejections preserve the complete reason; malformed rejection reasons and overlong comments fail request creation rather than producing a misleading notification.
- Request-once identity is scoped to the recipient and reservation's one final decision, not the random eventId, decisionId or mutable content. Replays with a new eventId still resolve to the original request and content. Producers must treat an eventId as immutable; conflicting payloads reusing an eventId across different reservations are outside this contract.
- If decisions become revisable, introduce a separate event/identity version rather than silently reusing this key.
- Listener calls the existing NotificationRequestService; no cross-module repository access and no direct delivery occurs in the listener.
- NotificationRequest persistence joins the producer transaction. Worker delivery is a later transaction and cannot undo a previously committed approval.

## Verification scope

New factory and transaction tests cover approved/rejected text and identity, maximum reason length, malformed payloads, same-event and business-key replays, concurrent replays, request visibility before commit, rollback after publication, mandatory transaction rejection, rollback-only propagation when a listener fails, and delivered/read ownership.

The transaction tests temporarily use the already existing H2 test profile in a dedicated database and unique event/requester IDs. They do not clear shared tables. These are compatibility checks while Zhou Fanhao owns PostgreSQL 18 migration, not PostgreSQL acceptance evidence. The tests must move to the shared Testcontainers fixture before the migration is considered complete. No migration files, datasource configuration or shared schema were changed by this adapter.

No PR comment, review submission or message to teammates was sent by this local work.

## Local execution on 25 September 2026

Targeted run: 27 tests passed (14 upstream approval tests plus 13 adapter tests).
Full Maven verify: 46 tests passed, zero failures/errors/skips; executable packaging and JaCoCo report completed. Java 21 runtime with Java 17 release target. Existing H2 profile only; PostgreSQL 18 and actual approval/reservation/audit persistence remain unverified pending the shared database migration.
