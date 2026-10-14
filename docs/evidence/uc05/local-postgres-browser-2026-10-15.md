# UC05 real PostgreSQL and browser check — 2026-10-15 SGT

Environment: isolated local PostgreSQL 18-alpine container, Spring Boot 4 application with the `local` profile, Vite same-origin proxy, and Chromium via Playwright. The database and credentials were generated under `/private/tmp` and were never committed or sent to a shared server. This is local evidence, not a hosted deployment or member review.

## Reproduction

1. Start an empty PostgreSQL 18 database named `serms_local_demo` and the current application against it with `SPRING_PROFILES_ACTIVE=local`; Flyway applies V001–V004. Bind backend and Vite to `127.0.0.1` and point `SERMS_API_TARGET` at the backend.
2. Seed two distinct `BORROWER` accounts, each with its own available equipment and private password. Through the real JSON login, equipment search and reservation APIs, submit three nonconflicting reservations for each user at separate future time windows. Wait for the notification worker to deliver them; query `serms.notification_request` to confirm delivered rows.
3. In a real Chromium page, sign in as the first borrower, open `/notifications`, and mark one notification as read. The page showed four delivered rows (the submitted events plus a collection reminder), and the unread count changed from 4 to 3.
4. In a separate browser context, sign in as the second borrower. Its inbox showed its own equipment name (`Load camera 02`) and did not show the first borrower's equipment (`Load camera 01`). No mock API handlers were used.

The two-account browser result was `login=ok`, `inboxRows=4`, `unreadBefore=4`, `unreadAfter=3`, and `hasOtherEquipment=false`. A local screenshot is retained only in `/private/tmp/serms-k6-rehearsal.Yvmdgb/notifications.png`; it is not in Git because the generated test instance is ephemeral.

## Findings and regression coverage

The PR review found that a cached operator session could continue reading the global overdue list after database role revocation or account disablement. Reproduce on the old implementation: sign in as CUSTODIAN/ADMIN, revoke the role or disable the user in PostgreSQL, then request `GET /api/v1/overdue` with the existing session. The fix checks active database membership on each read; `Uc05PostgresIT.overdueSessionMustStillHaveActiveDatabaseRole` asserts 403 for both paths.

A committed but unsent collection reminder could still be delivered after physical checkout. Reproduce on the old implementation: scan a confirmed reservation into a pending collection request, perform checkout, then run the worker. The fix cancels the pending request in checkout's business transaction; `Uc05PostgresIT.checkoutCancelsCommittedButUnsentCollectionNotice` checks its final state and reason. `Uc05PostgresIT.realReservationSubmitCancelAndFailureKeepAuditAndNoticesAtomic` checks real reservation/audit/notice persistence and rollback; the frontend `OverduePage.test.tsx` checks paging and sort reset. Existing inbox tests cover mark-as-read, and the browser check above exercises the real stored data path.

The local PostgreSQL UC05 integration suite passed 10/10; the full backend Maven verification passed. The frontend suite passed 129/129 when run with two workers, followed by a successful production build and generated API type check. Latest-HEAD hosted CI is separate evidence. The Loan-owned overdue projection contract still needs Zhou Fanhao's confirmation before PR #12 is ready for merge.
