# Issue 8: actual approval persistence and browser acceptance

Owner: Zhang Hanming. Assistance: Codex. Refs #8; partial delivery, not Issue closure.

Worktree/branch/base and rule sources are recorded in [the quality log](issue-24-quality-gates.md). Scope is UC02 implementation verification and a directly demonstrated persistence conflict defect; no database migration, shared REST/event/auth contract, other member's branch, or production deployment is changed.

## Changes

- Port the existing unmerged UC02 real-PostgreSQL acceptance suite from local delivery `3f1e7f564ccb5c12b2d9dd9c18af15819a23f54f`, adapting Spring Boot 4 / Testcontainers 2 imports and the authoritative current 409 metadata contract. Reuse `SermsFixtures`, the app's real defaults and Flyway V001–V006. Only the clock is mocked; business services, persistence, audit and synchronous notification listeners are real.
- Verify approve/reject, self-decisions, wrong roles, anonymous/CSRF, invalid versions/comments, stale and expired requests, maintenance restriction, allocation release, final-decision uniqueness/immutability, concurrency, timestamp/event consistency and replay deduplication.
- Inject actual SQL insert failures at decision, audit and notification-request boundaries in a disposable test DB; verify reservation version/status, decision, audit and notification request all roll back.
- Demonstrated the old adapter leaking `DuplicateKeyException` for constraint `approval_decision_reservation_id_key`. Translate only SQLSTATE 23505 on that exact constraint into an internal typed exception; unrelated integrity failures stay unchanged.
- Let the business transaction roll back before the API handler asks the proxied public approval service for authoritative status/version in a new read-only transaction. Return the existing 409 `state-conflict` contract, with required metadata; do not query an aborted PostgreSQL transaction or invent nullable metadata.
- Add a real API/database regression using an inconsistent legacy decision fixture to exercise this safety net and prove both rollback and the post-rollback metadata read.
- Existing mocked browser tests navigate explicitly via the Approvals menu after login; the observed landing-page race no longer makes the assertion depend on a preserved guarded URL. API mocks remain clearly labelled as mocks.
- Add a separate live Playwright script using this checkout's existing isolated local-demo launcher. Guarded opt-in SQL provisions fresh equipment fixtures per run; it does not alter schema or existing transactions. Actual user-interface booking, reject/approve, notification reading, checkout/damaged return, assignment/repair and admin report viewing use real HTTP, sessions, CSRF and PostgreSQL. Desktop/mobile five-role menu assertions are independent expected policy lists; backend approval/report permissions are checked with those actual sessions.

## Verification

Local backend: 590 regression tests, 30 UC02 PostgreSQL cases, 20 existing UC03 PostgreSQL cases; zero failures/errors/skips after fixes. Commands, tool versions and report paths are in [the quality log](issue-24-quality-gates.md).

Initial failures are retained honestly: the imported duplicate-safety-net assertion exposed the missing mapping; a new API test initially used mock CSRF unlike the existing cookie fixture and was corrected to reuse `decide(approve())`; browser fixture iterations corrected current labels, sidebar-vs-overview scoping, explicit queue refresh and quota-safe reject-before-approve sequencing. None changes business rules or disables tests.

Live browser final result is recorded after execution in the quality log, not inferred from the implementation. The runner keeps a summary, screenshots and traces in ignored local runtime storage. Raw traces can contain authentication tokens; do not commit or publish them. Failed runs retain history and may cancel only their own unissued fixtures through the normal borrower cancellation API.

## Remaining acceptance

- Real dual-role browser self-approval scenario (the rule is already covered with actual roles and PostgreSQL through MockMvc).
- Admin users/audit UI (#18), future overdue page/scanner (#19), new equipment management pages when merged, and corresponding final combined browser run.
- Latest-HEAD hosted CI, independent member verification, main integration and deployment. No messages/Issue comments or merge were performed.
