# UC02 approval API and React workspace

Owner: Zhang Hanming. This increment extends PR #8 on top of main's merged Identity/REST platform
and UC04 code. The shared business contract remains `docs/contracts/auth-and-approval.md`.

## Implemented

- `GET /api/v1/approvals/pending`: page 0, size 20, `startAt,asc` by default; includes expired items.
  Size is restricted to 1–100; other sort orders are rejected with a field validation error.
- `GET /api/v1/approvals/{reservationId}`: reservation fields at the top level, plus `decision`
  (null before a final decision).
- `POST /api/v1/approvals/{reservationId}/decision`: returns updated `reservation` and the recorded
  `decision`, including `approvalId` and `decidedAt`.
- APPROVER-only controller and service methods. ADMIN alone has no approval permission.
  Actor UUID comes from `SermsUserPrincipal`; requester UUID comes from the loaded reservation.
- Shared Cookie/Header CSRF and ProblemDetail handling. Rejections require a nonblank comment;
  all comments are limited to 1000 characters. Version must be a nonnegative JSON int64;
  fractional values, strings and overflowing numbers are rejected rather than coerced.
- Typed port conflicts map to 409 with `currentStatus` and `currentVersion`. Only known business
  conflicts use this mapping; arbitrary database failures remain safe shared errors.
- Policy/service classes use `approval.service`, consistent with the team package convention.
- `RecordedApprovalDecision` carries the persistence result. The same persisted `decidedAt`
  is used for the API response and `ReservationDecided` event. No timestamp is invented at publish time.
- `ApprovalReservationSnapshot` is an approval read projection, not a duplicate reservation entity.
  Its fields mirror the UC01 public view; `status` is the canonical reservation enum name as a string.
- React `/approvals` pending queue and `/approvals/:reservationId` detail/decision pages reuse the
  shared Layout, buttons, session, CSRF client, feedback components and generated OpenAPI types.
  Navigation and routes require APPROVER; ADMIN alone is not sufficient.
- Queue includes server pagination and Singapore times. The detail page requires an explicit
  confirmation, validates comments, blocks self-decisions and unsafe JavaScript version numbers,
  and makes final decisions read-only. Expired requests allow rejection only; expiration while
  confirmation is open also disables approval. The server remains authoritative for these rules.
- Failed writes are not automatically retried. A conflict requires manual refresh before another
  attempt; server field errors retain editable input. The command does not contain actor/recipient IDs.

## Explicit activation

`serms.approval.enabled` defaults to false (`SERMS_APPROVAL_ENABLED` can override it).
Enable it only after real `ReservationApprovalPort` and `ApprovalDecisionPort` beans are installed.
Enabling with an adapter missing fails application startup with the missing dependency named.
There are no production mock, in-memory, no-op or fallback persistence implementations.
The former `@ConditionalOnBean` scan-order condition has been removed.

## Integration with UC01 PR #11

UC01 was inspected at commit `dd90799e27ff50ec30cd9bb7633360d9a30fc9d3` without merging its feature
code into this approval PR. Its public `ReservationApprovalService` signatures remain compatible.
Before integrating the newer approval foundation, update its `ReservationApprovalAdapter`:

1. Import the ports and snapshot from `approval.service` instead of `approval.application`.
2. Implement `findPendingApproval(Pageable)` by delegating and mapping each `PendingReservation`.
3. Map the complete read projection: identifiers, requester name, equipment summary, start/end,
   purpose, `status().name()`, version, createdAt and expired. The old two-field constructor is gone.
4. Translate reservation state/version/expiry exceptions into `ApprovalConflictException` at the
   module boundary; preserve authoritative current state and version. `ReservationNotFoundException`
   already extends the shared `RecordNotFoundException` and retains the shared 404 response.
   Version/expiry exceptions in the inspected UC01 commit do not carry all required metadata;
   add that metadata at the guarded decision point rather than inventing it or querying inside
   an aborted database transaction.

The notification listener in PR #11 remains synchronous and MANDATORY. Existing event consumers
can still read the same identity/decision/comment accessors. Tests or callers constructing events
must supply the newly required persisted `decidedAt`.

## Decision persistence follow-up (after Zhou's database integration)

- Keep the port as the persistence boundary; implement `record` and `findByReservationId` against
  the final shared schema. Reuse the existing approval table, and do not rewrite applied V002.
- Join ApprovalService's existing transaction (writes should require it with MANDATORY).
- Return the actual stored decision ID, fields and TIMESTAMPTZ value from `record`.
- Translate only the identified reservation-final-decision unique constraint violation into a
  DUPLICATE conflict. Do not classify every DataIntegrityViolationException as duplicate approval.
- Resolve the existing schema, decision-enum, version type and version-increment differences with
  Zhou. No migration number or database name is assumed by this foundation.
- Include audit persistence and notification request persistence synchronously in the same transaction.
- Add real PostgreSQL tests for final-decision uniqueness, immutability, concurrent decisions,
  allocation release and rollback of reservation/decision/audit/notification rows.

## Verification and evidence

Run from `app/` in PowerShell (quote the Maven property):

```powershell
.\mvnw.cmd '-Dtest=Approval*Test' test
.\mvnw.cmd clean verify
```

ApprovalApiTest uses the real controller, service/policy and shared security with mocked ports.
ApprovalRuntimeTest uses the existing test profile (H2 locally, CI's selected DataSource otherwise)
to verify actual Spring transaction proxies,
synchronous event failures and OpenAPI; its ports are still mocks. These tests verify API and
transaction-boundary behavior, not PostgreSQL persistence or atomic rollback of real business rows.

Evidence locations: `app/target/surefire-reports/`, `app/target/site/jacoco/index.html`,
`app/target/approval-openapi.json`. Retain the run summary and associated commit/CI run.
Real adapters, PostgreSQL integration and live end-to-end acceptance remain pending.

Local verification on 2026-10-06 (Java 25 runtime, Java 17 compilation target):

- After merging main `6704171`, the unchanged baseline passed 400 tests.
- Final `mvn verify`: 445 tests, 0 failures, 0 errors, 0 skipped; 59 belong to approval
  (API 36, Spring runtime/OpenAPI 4, policy chain 7, configuration 3, service 9).
- JaCoCo approval service/domain packages, including service subpackages: 145/146 covered lines
  (99.32%). This is local coverage evidence, not a claim that a CI coverage gate is enforced.
- Existing frontend regression: 36 Vitest tests, lint, TypeScript/build and `pnpm check:api` passed.
  These are the existing notification/maintenance tests; approval UI is not implemented yet.
- No real PostgreSQL approval tests were run. The UC04 PostgresIT profile was not enabled.

Local verification of the React increment on 2026-10-07:

- `mvn verify`: 445 tests passed with no failures, errors or skips (59 approval tests).
- `pnpm test`: 60 tests passed, including 24 new approval component/API-contract tests using MSW.
- `pnpm test:e2e`: 10 browser tests passed across desktop (1440x1000) and mobile (390x844),
  including 6 approval runs. Approval scenarios cover rejection with validation/confirmation,
  conflict and refresh, and expiration during confirmation. Notification/maintenance regression
  flows also passed. Desktop/mobile screenshots were visually checked.
- Lint, TypeScript, production build and generated API drift check passed. Build warns about the
  current shared entry bundle exceeding 500 kB and upstream Zod comments; this is not a build failure.
- The existing OpenAPI-export test enables approval with mocked ports only in its Spring test
  context. Production activation is unchanged. The exported schema now includes approval endpoints
  and asserts that `expectedVersion` is an integer/int64, not a string.
- Browser requests are explicitly mocked in test files, not in the application. These runs do not
  establish real login/database connectivity, notification delivery, audit persistence or PostgreSQL
  approval transactions. No deployed approval slice is claimed.

## Running the frontend and inspecting UI evidence

From `frontend/` in PowerShell, after the backend tests have exported `app/target/openapi.json`:

```powershell
$env:OPENAPI_FILE = '../app/target/openapi.json'
pnpm check:api
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec playwright install chromium  # first browser-test run only
pnpm test:e2e
pnpm exec playwright show-report
```

The browser report is `frontend/playwright-report/index.html`; approval queue and confirmation
screenshots are in `frontend/test-results/approval-*/`. They show test fixtures, not live data.
These generated artifacts are ignored by Git; the existing frontend CI uploads them as evidence.

For actual application use, start the shared backend and `pnpm dev`, then open
`http://127.0.0.1:5173/approvals` and sign in with an actual APPROVER account. This will not yet
complete a live approval: the backend's real adapters and database integration are still pending.
Do not enable the approval property without its real ports, and do not replace them with mock
implementations for deployment. Once Zhou's database runtime and UC01 are integrated, wire the
ports and synchronous audit/notification listeners, enable approval and run the PostgreSQL
acceptance scenarios above.

## Shi's Review follow-up

| Comment | Status in this increment |
| --- | --- |
| Scan-order-dependent ConditionalOnBean | Replaced with explicit activation and startup tests |
| application vs service package | Aligned to service; UC01 imports must be updated at integration |
| Snapshot missing response fields | Full read projection added |
| Persisted decidedAt in result/event | Port result and event updated; real DB adapter pending |
| Negative version returned as server error | Bean Validation plus strict JSON int64 parsing, tested as 400 |
| Unique constraint conflict should be 409 | Typed conflict response tested with mocks; DB translation pending |
| Record failure should roll back reservation | Spring proxy rollback verified; real PostgreSQL row rollback pending |
