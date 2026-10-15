# Administrator console contract proposal (#18)

Status: **Implementation proposal; affected-owner confirmation pending.** Owner: Zhang Hanming.
Baseline: main `350085a8adf4a2cf9d593347d37cacd02ff61547`. This is an additive,
partial delivery, not a replacement for the confirmed [authentication contract](auth-and-approval.md).
It must not be merged as an accepted contract without the relevant owner confirmation.

## Implemented first slice

Only explicit `ADMIN` may call the following endpoints. The service also checks the actor UUID
against the current database: the account must be `ACTIVE` and explicitly assigned `ADMIN`.
An `ADMIN` does not gain any other role. Use the existing session, UUID Principal,
`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header, BCrypt encoder and ProblemDetail handling.

| Endpoint | Behaviour |
| --- | --- |
| `GET /api/v1/admin/users?search=&page=0&size=20` | Literal, case-insensitive name/email substring. Email ascending then UUID. Includes active and disabled accounts, with all assigned roles; no credentials |
| `POST /api/v1/admin/users` | Creates one `ACTIVE` account with initial roles; returns 201 and the account summary |
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

Anonymous calls return 401, non-admin or no longer active/admin actors 403, invalid inputs 400,
duplicate email 409. Mutations require CSRF (403 if absent/invalid). The existing `/auth` endpoints,
session lifecycle and login error messages remain unchanged in this slice.

UI: `/admin/users` and `/admin/audit`, both inside `RequireRole role="ADMIN"`, using shared
navigation/layout/table/form components. Times display in Singapore time. An empty audit actor is
shown as `System`; user-provided strings are escaped by React.

## Next slice: concrete decisions for Identity owner Wang Yuanmeng

No existing-account role/state/password mutation endpoints are provided yet. Before adding them,
confirm the following proposal with Wang (and retain actual confirmation in the PR):

1. Deactivated/deleted accounts: on the next authenticated API request, clear and invalidate the
   session and return the existing 401 ProblemDetail (not a redirect). Existing login already rejects
   disabled accounts. Decide whether CSRF and logout remain available before this check.
2. Role changes: on the next request either reload authorities from the database, or invalidate the
   session and require sign-in. **Recommendation: invalidate on a changed role set** to avoid stale
   UI/menu/authority snapshots. Preserve explicit roles; do not add ADMIN inheritance.
3. Password reset: decide whether all existing sessions must end and how resets are tracked. A
   password hash must not be added to the session Principal. An additive credential/session version
   may require the next free migration; do not rewrite V001–V006.
4. Last-active-admin guard: serialize all admin-role/status mutations across application instances
   (including concurrent demotions) with the same PostgreSQL advisory transaction lock used by
   bootstrap. Recheck current operator, target and active-admin count under the lock. Refuse
   self-deactivation and removal of the final active administrator. Audit all committed changes in
   the same transaction; provide real concurrency and session-revocation tests.

These are **proposals, not owner confirmation**. First-admin bootstrap remains Liu's completed
#41/#43 scope. The current slice does not claim disabled-session or last-admin acceptance.
