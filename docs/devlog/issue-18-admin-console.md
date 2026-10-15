# Administrator console foundation (#18)

Owner: Zhang Hanming (@Hanming1008) · Requirements: Proposal 5.1 / 5.7 ·
Branch: `codex/hanming-admin-console-20261015` ·
Baseline: `350085a` (fetched main on 2026-10-15, Asia/Singapore).

## 2026-10-15 First independently verifiable slice

### Scope, isolation and rules

The user's continuation authorizes implementation. Sole writer: this Codex session, in
`C:/Users/LENOVO/Documents/ChatGPT/Dev/SERMS-hanming-admin-20261015`. The desktop exposes no
worktree-creation capability, so the available Git worktree fallback was used. The quality/approval
worktree is retained unchanged. Main now contains PR #46 and the clean-build fix #30.

Read: `AGENTS.md`, `.github/CONTRIBUTING.md`, `docs/tech-stack.md`, contract index and
`auth-and-approval.md`, collaboration ownership/decisions/agent workflow, `docs/testing.md`,
this directory's README and `.agents/skills/serms-{worktree,task,verify,pr}/SKILL.md`.
The base's rules apply; Issue text is context, not authority to send messages or merge.

### What was built

- ADMIN-only user list/search and creation with explicitly selected initial roles.
- ADMIN-only, paginated, filtered, read-only audit query and React viewer.
- Shared navigation, route guards, form controls and tables. Extended the explicit browser matrix
  to the two new ADMIN-only pages and APIs, plus creation denials and user-table fingerprints.
- Real PostgreSQL tests for multi-role persistence, BCrypt, transactional audit failure, concurrent
  same-email creation, literal SQL search, paging/time boundaries and append-only audit protection.
- Unit/API/component/browser test benches. API type regeneration is part of verification.

No new migration or dependency, no duplicate user model for authentication, no bootstrap changes.
New response DTOs deliberately omit credentials. No notifications are emitted for account creation:
secure sharing of the initial password remains outside the app, with no public registration.

### Design notes

Use direct service orchestration and parameterized JdbcTemplate queries over the existing tables.
No variable rule chain is needed here. Both new services recheck active administrator membership
through the public Identity `AdminAccess` service; the audit module does not reach into Identity's
repository. Count and page use repeatable-read snapshots; create/role assignment/audit share one
transaction. The database email constraint is the concurrency arbiter. No pattern is added just
to increase the design-pattern count.

### Problems and fixes

| Symptom | Cause | Fix | Verification |
| --- | --- | --- | --- |
| Java rejected the roles element annotation | Type-use annotation preceded a qualified nested type | Import the enum and annotate the simple type | Subsequent compilation/API tests passed |
| Test configuration did not compile | Nested class named `Configuration` shadowed the annotation | Rename the class `Config` | Subsequent PostgreSQL bench passed |
| Risk of one generated `Page` schema overwriting another | Two nested records had the same simple name | Explicit `AdminUserPage` and `AuditEntryPage` OpenAPI names | Inspect generated backend specification and typecheck |
| Two old menu expectations failed after adding real routes | Tests still expected admin pages to be reserved | Update explicit expected management menu and leave only equipment pending | Rerun complete frontend tests; do not delete/skip tests |
| Component typecheck rejected `exact` options | React Testing Library already matches names exactly; `exact` is a Playwright option | Remove the unsupported options, retaining exact string names | Typecheck passed on rerun |
| Two new mock E2E cases signed in but did not reach the overview | Login correctly restored the previous ADMIN-only URL, which the new borrower cannot open | Await logout and explicitly start a fresh login-page navigation | Rerun the complete browser suite; no production auth change |
| First real browser probe navigated away before sign-in completed | A broad `Welcome` selector matched the login page's `Welcome back` heading | Wait for authenticated Sign out, explicitly open Overview and require the level-1 `Welcome,` heading | Repeat the desktop/mobile real workflow; initial failed evidence is retained locally |
| Existing approval fixture guard refused the admin-created database | The guard deliberately requires exactly the six provisioned demo accounts; administrator acceptance added real accounts | Preserve that cluster and its immutable audit history, run the older workflow in a fresh isolated cluster; do not weaken the guard | Follow-up real browser regression, separately recorded |
| Some ignored runtime files stayed locked during archival | Windows' Java launcher shim left the actual old JVM after stopping its wrapper | Identify the old JVM by PID and this worktree's JAR path, stop that process only; preserve files and start a new cluster on different ports | New demo started; no other worktree/service touched |

### Verification

Initial targeted unit/API run: 38 tests, 0 failures/errors/skips. Initial complete frontend run:
149 passed, 2 stale-menu assertions failed (above); these results are retained rather than called
passed. PostgreSQL/full regression, final frontend, browser evidence and delivered commit are
recorded below once executed. Source inputs are initially uncommitted relative to `350085a`.

Final local regression on these source inputs: 636 ordinary tests + 30 UC02 + 30 UC03/UC05,
all passed, zero failures/errors/skips. The new bench accounts for 46 of the ordinary tests,
including 8 real PostgreSQL cases. Coverage 1,243 covered / 299 missed business lines (80.61%);
negative gate and byte-for-byte restoration passed. Frontend typecheck/lint/build/API drift passed,
151 component tests and 14 mock E2E passed. Two desktop/mobile real admin workflow groups passed
at 16:37:38–16:37:47 SGT. Screenshots and source-bound JSON are in
[the administrator evidence directory](../evidence/admin-foundation-20261015/README.md).
The standard frontend chart-size/large-chunk warnings remain warnings, not ignored test failures.
An initial directory-mode gitleaks scan included ignored local runtime credentials and an unchanged
baseline false positive in `scripts/test-zhoufanhao.ps1:10` (environment-variable names, not a value).
The redacted report's file/rule fields were checked. Staged-diff scanning passed with no findings;
no secret rule or existing allowlist was changed and no runtime cache is published.

Follow-up real UC02/loan/maintenance flow and the expanded read-only role matrix both passed,
serially, in a fresh six-account PostgreSQL cluster. The matrix includes the two new pages/APIs
and account-creation denial; all user/business/audit/notification-read/role fingerprints were
unchanged. [Source-bound matrix and workflow evidence](../evidence/role-access-admin-foundation-20261015/README.md).
No synthetic accounts or immutable audit history were removed to satisfy the old fixture guard.

### Deviations and follow-ups (first slice)

This first slice is **partial #18**, not completion: display-name editing, existing-account role
changes, activation/deactivation, password reset, last-active-admin concurrency and global
next-request session revocation remained at that point. Existing login/session behavior was not
modified. The [contract proposal](../contracts/admin-console.md) gives concrete choices.
No Wang confirmation, member check, deployment or team Done was claimed; the delivery stayed draft.

### Authorship (first slice)

Implementation and verification are Codex-assisted for Zhang Hanming. No hours, meetings,
personal review or teammate approval are invented. No remote Issue comments, teammate messages,
repository settings or production changes were part of this implementation.

## 2026-10-15 Display-name editing while session confirmation is pending

### Scope and baseline

The user reports sending the session questions to Wang and explicitly authorizes the independent
work meanwhile. Sole writer and worktree remain the same. Start HEAD `f79be6b98528664bb90151d41066f1fe74b05768`;
fetched main `2d19ef9b4e7b7e38c7241e8b5f9e4dcf524db9cf` differs from `350085a` only by the
#71 coordination devlog. Previous #72 HEAD `f79be6b` passed all 11 hosted workflows (verified
through GitHub Actions metadata this turn); that historical result does not apply to new code.
Wang's reply is **not received or assumed**. Existing sessions/authorities remain untouched.

### What was built

- `PATCH /api/v1/admin/users/{userId}/display-name`, ADMIN-only in controller and service,
  with the existing current-database active-administrator check and actual CSRF cookie/header.
- A focused shared Dialog editor in the user table, validation, cancel/focus restoration, safe
  error handling and conflict recovery. API types regenerated from the actual backend specification.
- Fixed-text `USER_DISPLAY_NAME_CHANGED` audit, atomically committed with the name update.
- Unit/API, real PostgreSQL, component and mock/real browser extensions; the read-only role matrix
  now includes a valid-CSRF PATCH denial and still fingerprints accounts and roles.

### Design notes

Only the stored display name changes. Email, UUID, account state, roles, password hash and notification
identity do not. No migration or dependency is required. An exact `expectedDisplayName` from opening
the editor prevents silently overwriting a changed name; stale requests return a specific 409,
unknown targets 404, invalid input 400, role failures 403 and anonymous requests 401. A no-op has
no extra audit. This is a compare-current-name guard, not a new global account-version contract.

Row locking uses `FOR NO KEY UPDATE OF u`, followed by a conditional single-row update. Unlike a
stronger `FOR UPDATE`, it stays compatible with audit foreign-key key-share locks if two admins
rename each other. The lock compatibility was checked against the official
[PostgreSQL 18 documentation](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS),
and a barrier-based PostgreSQL concurrency test exercises both held target locks and audit inserts.
The existing session Principal/header deliberately retains its original display name until sign-in
again. The dialog states this; no `/auth` endpoint or shared security configuration changes.

### Problems and fixes

| Symptom / risk | Cause | Fix | Verification |
| --- | --- | --- | --- |
| New real-cookie MockMvc assertion initially found no cookie | Existing `csrf()` request processors switched the shared test filter's CSRF repository to a session-based simulator | Use actual `/auth/csrf` cookie/header requests throughout this test class, including invalid-token cases; leave production security unchanged | All 50 targeted unit/API tests passed after repair |
| Potential cross-admin audit FK deadlock identified during review | `FOR UPDATE` conflicts with the FK key-share lock on the other administrator | Use the non-key-update lock appropriate to this field and add a deterministic two-transaction test | Final PostgreSQL regression recorded below |
| A background refresh could otherwise replace the concurrency guard while a form stays open | Row props can change independently of an editor's input | Capture the expected name only on opening; block saving after conflict and require close/reopen | Component conflict test checks no implicit retry and the fresh expected value |
| Real browser creation probe matched seven elements after edit buttons were added | Playwright's partial `Display name` label selector also matched `Edit display name for ...` buttons in the seeded directory | Use exact form-field label matching in real/mock browser probes; preserve accessible action labels | Repeat the actual desktop/mobile create/rename/login flow; first failure retained locally |

### Verification and remaining acceptance

Source inputs initially uncommitted relative to `f79be6b`. Targeted unit/API: 50 passed. Administrator
components: 21 passed. Complete frontend components: 158 passed. Final full regression, real
browser source hashes and delivered revision are recorded with the new evidence/PR update.
No older evidence is relabeled as covering these inputs.

Final verification followed the main documentation-only sync at merge HEAD
`7afe031209cb6384d080b105e84974c6aff17a93`, with the uncommitted name-edit inputs. Complete
backend: 657 ordinary + 30 UC02 + 30 UC03/UC05 passed, including all 15 administrator PostgreSQL
tests; zero failures/errors/skips. Business coverage 1,256 covered / 299 missed (80.77%), original
70% check and negative/restoration exercise passed. Frontend lint/typecheck/build/API drift passed,
158 components and all 14 browser contract cases passed. The actual PostgreSQL role matrix
passed 66 groups / 210 page observations / 350 reads / 140 denied writes / 4 ownership denials,
then both real administrator create/rename/audit/login device flows passed. No write was run during
the matrix. [New evidence and exact source hashes](../evidence/admin-display-name-20261015/README.md).

Still pending: existing-role changes, activation/deactivation, password reset and next-request
global session revocation/last-active-admin guards. Those wait for the agreed Identity contract;
this slice is deliberately independent. Contract proposal remains pending affected-owner acceptance,
PR remains draft, and no member check, production deployment or team Done is claimed.

### Authorship

Codex-assisted implementation and checks for Zhang Hanming, at the user's explicit request. No
teammate message/Issue comment, approval, reallocation, hours or production change was made.

## 2026-10-15 Confirmed Identity/session continuation

### Task, authorization and versions

Owner: Zhang Hanming (`Hanming1008`), #18. Sole writer: this Codex session, resumed in
`SERMS-hanming-admin-20261015` on `codex/hanming-admin-console-20261015`; clean starting HEAD
`828f16407422c767f1dec8b27ca6f3523a4915b1`. The user pasted Wang Yuanmeng's explicit agreement to
the three session behaviours. This resolves that dependency, not whole-PR acceptance. No new
teammate message, Issue comment, main merge, protected setting or production operation is authorized.

Fetched main `0be0a29af011862824c6aaab642512241764bcd9`; only two CI/deployment trigger files
changed from the previous base. Normal merge `f189b5c79a7aa62d40b9d76de4165f884b0f8932` synchronized
the branch, preserving all main/source history and others' worktrees. Functional inputs below were
uncommitted against that merge HEAD during verification. No old migration was rewritten.

Rules read: `AGENTS.md`, `.github/CONTRIBUTING.md`, `docs/tech-stack.md`, `docs/testing.md`,
`docs/contracts/{README,auth-and-approval,admin-console}.md`, `docs/collaboration/{ownership,
agent-workflow,decisions}.md`, `docs/devlog/README.md`, and `.agents/skills/serms-{task,worktree,
verify,pr}/SKILL.md`. Scope: Identity/admin, narrow shared security integration, additive migration,
shared test projections, admin UI and corresponding evidence. Existing UC02/business models,
first-admin authorship, deployment and required checks are excluded from redesign.

### What was built

- PostgreSQL `session_version` (BIGINT, default 0, nonnegative), next currently free V007. Preserve
  V001–V006; renumber this unmerged migration if another migration takes that number first.
- `SermsUserPrincipal` stores the login version but no password hash. A credential-free Identity
  projection and `SessionVersionFilter` compare it on protected requests, clear old authentication
  and Session and return 401. Public CSRF/login/logout remain available. CurrentUser, UUID mapping,
  explicit-role semantics and ordinary 403 responses remain unchanged.
- ADMIN status/role/password-reset APIs, optimistic security versions and safe summaries. Actual
  status/role changes revoke old sessions; no-ops do not. Every explicit reset revokes sessions.
  All account/role/hash updates, version increments and safe audits share the business transaction.
- Self-deactivation is rejected. Role/status mutations use bootstrap's existing global PostgreSQL
  advisory transaction lock, then current-actor/target/version/count checks, to retain an active ADMIN
  even under concurrent independent transactions. This is direct service orchestration, not a forced
  additional design pattern or replacement for the UC02 responsibility chain.
- Shared React dialogs for explicit roles, disable/activate confirmation and password reset. Guard
  stale edits; explain/reopen after conflicts, disable self-deactivation, clear replacement passwords,
  keep them out of query/mutation caches, and reload identity after self role/password changes.
- Real PostgreSQL two-session/rollback/concurrency tests, every-role/CSRF/strict-version API tests,
  component tests, expanded denied-write inventory, and a repeatable real desktop/mobile script
  `scripts/check-admin-security-browser.mjs`.

### Problems and fixes

| Symptom / risk | Root cause | Fix and verification |
| --- | --- | --- |
| Initial sandbox Maven command stalled with an empty log | Local process/runtime restrictions | Stop only its owned execution; rerun with existing explicit JDK under approved local execution; no test/production bypass |
| New invalid-version API tests got 500 instead of 400 for coerced input | Default Jackson coercion admitted string/fraction values into service mocks | Reuse `StrictVersionDeserializer`; all integer-shape validation tests pass |
| Historical notification H2 cases lacked an Identity table | That test bench simulates cookie identities and never installed the real Identity schema | Explicit Identity projection mock for this historical bench; real revocation tested with actual PostgreSQL/Spring separately |
| Disabled UC05 session formerly expected 403 | Global revocation now precedes module permission checks under Wang's new agreement | Assert existing 401 ProblemDetail and invalidated session; retain the active-but-missing-role 403 test; no case skipped |
| New migration needed both Flyway and the existing schema inventory | Early draft added the column but not `serms.schema_version` | Follow V005/V006 bookkeeping and test V001–V007 at runtime; update exact UC03 inventory assertion to 7 |
| Restarting previous local browser database refused seeding | Prior admin UI tests intentionally retained extra created accounts | Preserve the complete stopped runtime as `.cache/admin-display-name-pre-session-20261015`; create a fresh isolated cluster, never clear histories or weaken the seed guard |

### Verification and follow-ups

Final backend source run: `scripts/test-uc02.ps1 -PostgresBin <native PostgreSQL18 bin>
-IncludeLoanIntegration`, Windows, Java25.0.4 targeting17, Boot4.0.8, Maven3.9.11/PostgreSQL18.6.
685 ordinary + 30 UC02 + 30 UC03/UC05 = **745 passed, zero failures/errors/skips**, ending
2026-10-15 21:09:16 SGT. Administrator/audit scope includes 38 API, 21 existing service unit,
8 new service unit, 15 existing PostgreSQL, 10 cross-session PostgreSQL, 1 isolated last-admin
concurrency and 2 audit-query unit cases. Coverage: 1,307 covered / 299 missed = 81.38%; unchanged
70% gate and deliberate-zero negative/byte-restoration exercise passed. Earlier failed candidate
logs remain ignored and are not presented as passes.

Frontend: 169 components passed, lint/typecheck/build passed. Fresh backend-exported types generated
and drift checked. Existing mocked desktop/mobile Playwright contract suite: 14 passed. These are
not substitutes for the actual-stack flows; current real-browser results and source hashes are recorded
in the accompanying evidence after execution. No real-browser success is assumed in advance.

The subsequent fresh actual-stack run passed the desktop/mobile UC02 handover/repair and dual-role
protection flows, then a no-write role matrix (66 groups, 210 pages, 350 reads, 170 denied mutations,
4 ownership denials and unchanged database fingerprints). Both desktop/mobile administrator security
flows passed actual UI role changes, disable/activate, password reset, two-session revocation,
new-password login and exactly one audit per committed action. The original create/name/audit/login
flows also passed. Reviewed synthetic screenshots and exact source hashes are in
[the current security evidence](../evidence/admin-session-20261015/README.md); private traces,
credentials and complete retained histories remain ignored. Earlier evidence stays historical.

Remaining: latest delivered-HEAD hosted CI, affected member review/check, migration-number recheck
at merge, release/deployed acceptance and final new-page role regression after the dependent feature
PRs merge. No Issue is closed or whole-team Done claimed here. First-admin bootstrap stays Liu's
#41/#43 contribution. Development records are factual, not manufactured ideal completion or hours.

### Authorship

Codex-assisted code and verification for Zhang Hanming, under the user's existing implementation
authorization and Wang's reply supplied by the user. No personally executed human test, GitHub
approval, public confirmation, effort hours or deployment is invented.

## 2026-10-15 Hosted notification-contract regression follow-up

### What happened and scope

The user authorized repairing the remaining PR #72 CI regression. Starting clean HEAD:
`d10f7e82ca2ca9ab63a97c50733b4721f495c142`; branch/worktree and sole writer unchanged.
Fetched main remains `0be0a29af011862824c6aaab642512241764bcd9`. Re-read the implementation,
verification, worktree and PR skills and their applicable collaboration/testing/contract sources.
Scope is one compatibility test plus this factual log/evidence; production, upstream contracts,
migrations, APIs, dependencies, workflow conditions and acceptance standards remain unchanged.

The old delivered HEAD passed 10 of 11 workflows; [Notification PostgreSQL contract run
38742301680](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/actions/runs/38742301680)
failed one of twelve cases: `existingV2NotificationsUpgradeWithoutLosingHistory`, expected 6 but
actual 7. The upgrade itself completed V007 successfully; its final schema-inventory assertion still
described the pre-session-version release. This was not a failed notification delivery or lost history.

### Fix and verification

Update the exact expected schema version to 7, also checking Flyway's current version is 7,
the added column is BIGINT and all pre-existing upgrade users received session version 0.
Retain every notification count, delivered/read/attempt state, canonical dedup-key, foreign-key and
legacy immutability assertion. No tests skipped, fixtures changed or new success threshold loosened.

Run the complete pinned compatibility module on a new disposable loopback PostgreSQL18.6 cluster
with separate `serms_test` / `serms_upgrade` databases; seed only the latter with V001/V002 and the
unchanged V2 notification fixture. The existing preparation script verifies all five public contract
sources byte-for-byte against `01b50778701ed8a729039790238581bb59e929b5`. Maven Wrapper3.9.11,
Java25.0.4/release17, unchanged historical adapter Boot3.5.16: **12 tests passed, zero failures,
errors or skips**, ending 2026-10-15 21:30:24 SGT. The adapter JAR duplicate-upstream-class check
passed. The disposable server was stopped, environment restored, data/logs retained under ignored
`.cache/notification-native-*`; no existing application/database touched. The historical Flyway
PostgreSQL support warning remains visible; production dependencies were not changed or suppressed.

The main application's earlier 745 checks and UI evidence remain bound to their original inputs;
this test-only follow-up did not rerun or relabel those local results. Current CI will exercise all
workflows on the new delivered HEAD. See the [follow-up evidence](../evidence/admin-session-20261015/notification-contract-followup.md).
Member review, main merge, migration-number coordination, deployment and team Done remain separate.

### Authorship

Codex-assisted repair/verification for Zhang Hanming at the user's request. No teammate comment,
human approval, working hours, Issue closure or production action was performed or invented.
