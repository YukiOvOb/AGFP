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

### Deviations and follow-ups

This is **partial #18**, not completion: display-name editing, existing-account role changes,
activation/deactivation, password reset, last-active-admin concurrency and global next-request
session revocation remain. Existing login/session behavior is not modified before Wang's affected-
owner confirmation. The [contract proposal](../contracts/admin-console.md) gives concrete choices.
No Wang confirmation, member check, deployment or team Done is claimed. The new contract itself
also remains a proposal until affected-owner confirmation; this delivery must stay draft.

### Authorship

Implementation and verification are Codex-assisted for Zhang Hanming. No hours, meetings,
personal review or teammate approval are invented. No remote Issue comments, teammate messages,
repository settings or production changes are part of this implementation.
