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

- Real dual-role browser self-approval: now passed on desktop/mobile; see the dated follow-up and evidence below.
- Admin users/audit UI (#18), future overdue page/scanner (#19), new equipment management pages when merged, and corresponding final combined browser run.
- Latest-HEAD hosted CI, independent member verification, main integration and deployment. No messages/Issue comments or merge were performed.

## 2026-10-15: dual-role browser self-decision follow-up

Owner: Zhang Hanming; agent assistance: Codex. The user authorized continuing the remaining real-browser self-decision acceptance and updating the existing delivery; no merge/deployment/Issue comments are authorized. Starting branch HEAD `f7bf0f11b82a8d429c19e5c95f8b243767ac8d77`; fetched main remains `2e9eda35973f0929c19fa3e613688d902a361f9d`. Same sole-writer worktree and rule sources as the quality log. Allowed changes: browser runner/explicit local test fixtures, guard/read-only assertion SQL, evidence and task logs. Production APIs, identity/reservation/approval implementation, shared contracts and migrations are excluded.

### Design

- Reuse the second existing local-demo borrower, not a mock principal or a new production account. The guarded fixture temporarily adds APPROVER while retaining BORROWER. Record whether this run inserted the assignment; cleanup removes only a role added by this run and preserves any pre-existing assignment.
- Use fresh, per-run/self-test equipment for desktop and mobile. No booked/loan/decision history is reset. The same extracted guard requires the isolated PostgreSQL 18 demo database, exactly the six explicitly provisioned demo user IDs and the expected active second borrower.
- Actual UI login confirms both roles through the real `auth/me` response. The actor submits a reservation through the real browser and sees “Your request” in the approval queue; its detail page exposes neither decision choice nor submission control.
- Bypass the UI only through the actual page's `fetch`, using its real cookie session and CSRF cookie. Both APPROVED and REJECTED must return 403 `/problems/self-approval`, not a missing-role or missing-CSRF rejection.
- After each attempt, compare read-only PostgreSQL snapshots of reservation status/version, final decision rows, successful approval audits and recipient notification-request count. Also verify the independent approver's real API still sees an undecided pending reservation.
- A different real approver rejects the same request through the UI. Verify exactly one final decision, audit and new notification request; the dual-role borrower sees Rejected. Restore the temporary role after all contexts close, including on failures.

### Verification

`node scripts/check-uc02-browser.mjs --publishable-evidence` passed **37 actual checks** (original 26, ten dual-role scenario checks and role restoration), with no uncaught page errors or document overflow. Run `3aebf50674`: 2026-10-15 10:37–10:42 SGT. A separate fresh login after cleanup returned only BORROWER.

All three SQL entry points reject the wrong database; invalid run IDs reject before the fixture transaction commits. Node syntax/diff/documentation checks passed. Frontend lint/typecheck/build, 130 component tests and 12 separately labelled mocked browser tests passed. Existing nonfatal chart/dependency/bundle warnings remain. No production/backend code changed, so this step did not invent new backend-test counts or reuse earlier CI as latest-HEAD evidence.

[Reviewed screenshots and safe verification JSON](../evidence/uc02-dual-role-20261015/README.md) include exact source hashes and actual checks; both screenshots were visually inspected and contain only synthetic identities. Raw traces/cookies/passwords remain ignored/private. Services were stopped; fixture/history/logs preserved.

### Problems, decisions and follow-ups

- Schema inspection showed NotificationRequest has no persisted source ID. Compare this actor's request count in the single-writer demo and use fixture-specific reservation/decision/audit counts; do not add a speculative column.
- Creating another account would break the existing six-user provisioning guard. Temporarily extend the existing second borrower's roles instead, preserving the five primary role accounts and removing only an assignment inserted by this run.
- Extract the original guard for reuse by setup, read-only snapshot and cleanup; do not duplicate a weaker guard for a mutating helper.
- No product defect or API change was required: the existing UI and service already prohibit both self-approval and self-rejection. This delivery adds execution evidence, not a new business policy or design pattern.
- The previous commit's ten hosted workflows passed; latest-commit CI after publication is pending. Future-page integration, full all-page allow/deny matrix, independent member acceptance, main integration and deployment remain open. Abruptly killing the runner can interrupt cleanup; only a recorded normal teardown/fresh-login check establishes restoration.

## 2026-10-15: current-page allow/deny closeout

The user authorized continuing the current-page role matrix and responsibility/evidence checklist. Starting HEAD `6bad669`, fetched main `2e9eda3`; full revision and rule/ownership boundaries are in [the quality follow-up](issue-24-quality-gates.md#current-page-permission-matrix-and-scope-audit--2026-10-15).

The new [real role matrix](../evidence/role-access-20261015/README.md) passed for anonymous, all five roles and a second borrower on desktop/mobile: 168 page visits, 308 read API probes, 120 valid-CSRF role-denied writes and four cross-user write denials; actual session/CSRF/logout behavior and no side effects were checked. The route inventory is explicit and also checked in CI. This completes the current-page matrix gap, not future-page or deployed acceptance.

The test reproduced a `/loans` page-gating inconsistency (API 403 without hiding the page shell); the minimal shared guard extension fixes it without changing backend roles/contracts. All 134 frontend components and 12 existing mocked E2E cases passed. Details, original failed attempts, hashes, screenshots and excluded future work are retained in the linked records.

Scope correction: admin user management and audit viewer #18 are assigned to Hanming; bootstrap #41 alone was completed by Liu. [Remaining work](../evidence/hanming-delivery-status-20261015.md) makes this explicit. No unrequested admin/session implementation, migration, remote message, Issue closure, merge or cloud action was performed.

## 2026-10-15: synchronize the current UC05/main combination

User-authorized normal main synchronization is recorded in [the integration log](2026-10-15-hanming-uc05-main-sync.md). Main `0ca4c14` is included through normal merge commits, without rewriting others' branches. The shared guard retains both loans and Overdue; mobile menu overflow is fixed without hiding links. New [version-bound evidence](../evidence/role-access-uc05-sync-20261015/README.md) records 37 complete live workflow checks and 66 serialized role-matrix groups (13 pages, 23 read API families), with Overdue operator access/empty state/sort and other-role denial. Original failed fixture/viewport/concurrent-test attempts are retained honestly. Old CI/evidence does not certify this combination; latest published HEAD CI, member/deployed checks and #17/#18 final-page acceptance remain separate.
