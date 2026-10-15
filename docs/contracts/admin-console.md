# Administrator console contract (#18)

Status: **Identity/session behaviour confirmed by Wang Yuanmeng through the user's pasted reply
on 2026-10-15; administrator endpoint delivery remains under PR review.** Owner: Zhang Hanming.
Baseline: main `350085a8adf4a2cf9d593347d37cacd02ff61547`. This is an additive,
partial delivery, not a replacement for the confirmed [authentication contract](auth-and-approval.md).
The confirmation covers the revocation semantics below, not a GitHub review, test execution or approval of the whole PR.

## Administrator endpoints

Only explicit `ADMIN` may call the following endpoints. The service also checks the actor UUID
against the current database: the account must be `ACTIVE` and explicitly assigned `ADMIN`.
An `ADMIN` does not gain any other role. Use the existing session, UUID Principal,
`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header, BCrypt encoder and ProblemDetail handling.

| Endpoint | Behaviour |
| --- | --- |
| `GET /api/v1/admin/users?search=&page=0&size=20` | Literal, case-insensitive name/email substring. Email ascending then UUID. Includes active and disabled accounts, with all assigned roles; no credentials |
| `POST /api/v1/admin/users` | Creates one `ACTIVE` account with initial roles; returns 201 and the account summary |
| `PATCH /api/v1/admin/users/{userId}/display-name` | Changes only the display name; requires `{displayName, expectedDisplayName}`; returns the updated summary, 200 |
| `PATCH /api/v1/admin/users/{userId}/status` | `{active: boolean, expectedVersion: int64}`; returns summary, 200 |
| `PATCH /api/v1/admin/users/{userId}/roles` | `{roles: Role[], expectedVersion: int64}`; nonempty explicit role set; returns summary, 200 |
| `POST /api/v1/admin/users/{userId}/password-reset` | `{newPassword, expectedVersion: int64}`; returns summary with no credentials, 200 |
| `GET /api/v1/admin/audit?actorId=&action=&entityType=&from=&to=&page=0&size=20` | Read-only audit projection; exact actor UUID/action/entity type; UTC Instants in half-open `[from,to)`; newest first then audit UUID ascending |

Both lists return `{content, totalElements, page, size}`. `page` is 0–100000 and `size` 1–100.
Count and page are read in one repeatable-read snapshot. Search is limited to 200 characters;
action/entity-type filters to 100. SQL parameters are bound; `%`, `_` and `!` are literal in user search.

Create body: `{email, displayName, initialPassword, roles}`. Roles are a nonempty set chosen from
`BORROWER`, `APPROVER`, `CUSTODIAN`, `MAINTAINER`, `ADMIN`. Email is at most 254 characters,
valid and normalized to lower case; display name is nonblank, trimmed, at most 200 characters.
Password follows the existing bootstrap baseline: at least 12 characters after stripping padding,
at most 72 UTF-8 bytes, encoded **without trimming**. No plaintext/hash in response, audit summary
or request DTO `toString`. The frontend clears the password on success and does not cache mutations.

User, roles and `USER_CREATED` audit are in one Spring transaction. Audit actor is the Principal UUID,
target is the created user UUID, entity type `User`, outcome `SUCCESS`; time is database-generated.
Only the actual email unique constraint maps to 409 `/problems/email-in-use`; other SQL failures are
not mislabeled as duplicate email. The audit query exposes actor ID/name, action, target type/ID,
outcome, summary and time. It does not modify or delete audit rows.

Anonymous or revoked sessions return 401, authenticated non-admin actors 403, invalid inputs 400,
duplicate email 409. Mutations require CSRF (403 if absent/invalid). The existing `/auth` endpoints,
login error messages remain unchanged. The initial slice did not alter session lifecycle; the confirmed continuation below does.

### Additive display-name editing slice

`displayName` is trimmed and must be nonblank, at most 200 characters. `expectedDisplayName` is
the exact name displayed when the editor opened (required, nonblank, at most 200 characters).
It is a compare-current-value guard, **not** a general-purpose account version or role mutation API.
Authorization/current active administrator checks precede target lookup. An unknown target returns
404 `/problems/not-found`. A different stored name returns 409 `/problems/display-name-conflict`;
the client blocks another save until closing/reopening against the refreshed list. There is no
automatic overwrite or retry. An unchanged normalized name returns 200 without a manufactured audit.

The service locks the target account with `FOR NO KEY UPDATE OF u`, checks the expected name,
conditionally updates only `display_name`, and inserts `USER_DISPLAY_NAME_CHANGED` in the same
transaction. A failed audit rolls back the name. The lock serializes editors without blocking the
audit actor foreign-key key-share lock when two administrators rename each other. See
[PostgreSQL 18 row locks](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS).
Email, UUID, account status, roles, password hash and notification identity are never changed.
No migration is needed and no existing migration is edited. The audit summary uses fixed safe text,
not profile input or credentials. Editing one's own name is allowed; it is not self-deactivation.

This does **not** refresh or invalidate existing sessions or modify `/auth/me`: the current session
Principal and page header retain their existing name until sign-in again, while directory queries
read the persisted new name. The UI states this limitation. Login after the edit reads the new name
with the unchanged password and roles. Display-name editing never advances the security/session version.

UI: `/admin/users` and `/admin/audit`, both inside `RequireRole role="ADMIN"`, using shared
navigation/layout/table/form components. Times display in Singapore time. An empty audit actor is
shown as `System`; user-provided strings are escaped by React.

## Confirmed Identity/session continuation — 2026-10-15

Confirmation source: Zhang Hanming pasted Wang Yuanmeng's reply in the implementation conversation.
Wang explicitly accepted disabled-account revocation, revocation on actual role changes, and
revocation of every old session after administrator password reset. Wang recommended a database
monotonic session version, atomic changes/version/audit, and real cross-session PostgreSQL tests.
No public GitHub confirmation/comment or personally executed member check is inferred.

- Additive `V007__identity_session_version.sql` adds `serms.app_user.session_version`, BIGINT,
  nonnegative, not null, default 0. V007 was free on fetched main `0be0a29`; recheck the next free
  number when merging, and renumber an unmerged conflicting migration rather than editing V001–V006.
- Login reads roles and version in one database snapshot. The Session Principal retains UUID,
  email/name/explicit roles and that version, **never a password hash**. CurrentUser stays unchanged.
- Before CSRF/authorization on a protected request, read a credential-free database projection.
  Missing/disabled account, mismatching UUID notification identity or version invalidates that
  session, clears authentication and returns existing 401 `/problems/unauthenticated`, no redirect.
  Already in-flight requests are not retroactively cancelled. Database errors fail closed, not as a
  successful authenticated response. Ordinary authenticated missing-role access remains 403.
- `/auth/csrf` remains available anonymously and with an old session; `/auth/logout` remains
  idempotent with valid CSRF. Login remains available and uniformly rejects disabled accounts.
- Actual role/status changes advance the version. No-op role/status requests do not create
  audits or revoke sessions. Every explicit password reset advances it, even to the same password.
  Re-enabling an account never resurrects old versions. No ADMIN role inheritance is added.
- `AdminUser.version` exposes the current security version to the administrator editor.
  `expectedVersion` is required, nonnegative JSON int64 (no string/fraction coercion).
  Stale edits return 409 `/problems/account-version-conflict`; refresh/reopen, never auto-retry.
- Mutations acquire the same PostgreSQL advisory transaction lock as first-admin bootstrap,
  then recheck current operator, lock target, compare version and protect the last active ADMIN.
  Self-deactivation returns 409 `/problems/self-deactivation`; removing/disabling the final active
  ADMIN returns 409 `/problems/last-active-admin`. Independent transactions/instances serialize.
- Account data/roles/password, version and fixed secret-free `USER_ACTIVATED`, `USER_DEACTIVATED`,
  `USER_ROLES_CHANGED` or `USER_PASSWORD_RESET` audit commit or roll back together. Only server
  administrative services write these security changes; direct SQL must not bypass version/audit.
- React uses shared dialogs, explicit disable confirmation, server conflict explanations,
  CSRF/API wrapper and cache invalidation. Reset passwords are never put in query/mutation caches;
  inputs are cleared on completion, attempted-save errors and dismissal. A self role/password
  mutation reloads `/auth/me`, which applies the same revocation rule.

First-admin bootstrap remains Liu's completed #41/#43 work. This continuation does not claim member
acceptance, hosted CI for unpushed inputs, deployment or team Done.
