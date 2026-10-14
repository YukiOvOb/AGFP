# First-Admin Bootstrap (#41)

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 5.1 (accounts), deployment readiness · Branch: `feature/liu-tongyao-first-admin-bootstrap` · Baseline: main `1a29944` · Split from #18 (Zhang Hanming) on 2026-10-15

## 2026-10-15 Startup bootstrap

### What was built

- `FirstAdminBootstrap` (`app/.../identity/service/`): creates an `ACTIVE` user with the `ADMIN` role when no active administrator exists, in one transaction with a `USER_BOOTSTRAP_ADMIN` row in `serms.audit_log`. If an active administrator exists it changes nothing. The password is stored only as the hash from the application's existing encoder.
- `FirstAdminProperties` (`serms.bootstrap.admin.email`, `display-name`, `password`, read from `SERMS_BOOTSTRAP_ADMIN_EMAIL`, `_DISPLAY_NAME`, `_PASSWORD`) and `FirstAdminBootstrapRunner`, which calls the bootstrap once per start only when an email is set.
- `compose.yaml` passes the three variables through as blank by default; `.env.example` lists them commented out; `docs/server-release.md` has a "First Administrator" procedure: set for one start, then delete.

### Design notes

- **No endpoint.** A provisioning endpoint would be reachable by anyone before the first administrator exists. Startup configuration is only reachable by whoever controls the server's `.env`, which the release already requires.
- **Fail fast on wrong settings.** An invalid email, blank display name, a password under 12 characters or over 72 bytes (BCrypt's limit, which Spring refuses), or an email that belongs to another account stops the start. Messages name the setting, never its value.
- **Idempotent and safe to leave.** A second start finds the administrator and changes nothing; the procedure still says to delete the settings so the password does not stay in the file.
- **Concurrent starts.** A transaction-scoped PostgreSQL advisory lock serialises two instances starting at once, so only one administrator is created.
- **System actor.** The audit row has no actor (`actor_id` NULL): no user exists yet, and the row records that configuration created the account.
- Login and session behaviour are unchanged, so the `/auth` contract is unchanged.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The shared PostgreSQL test database already has administrators from other tests, so "create when none exists" could not be tested directly | Tests share one database and never truncate | Each case runs in one transaction that is rolled back; existing administrators are disabled only inside it | `FirstAdminBootstrapPostgresTest` (3 cases) |
| The first full test run for this branch overlapped a branch switch in the same checkout (to fix #42), which swapped source files under the running build | Two branches worked on in one working tree | Discarded that run and reran the suite after switching back; parallel branches belong in separate worktrees | Rerun below |

### Verification

Base `1a29944` plus this change set; Windows 11, Java 25.0.4 (target 17), PostgreSQL 18.6.

| Check | Result |
| --- | --- |
| `./mvnw -B test` with `SERMS_APP_TEST_JDBC_URL` (clean rerun) | Passed: 579 (568 before, 11 new: 8 in `FirstAdminBootstrapTest`, 3 in `FirstAdminBootstrapPostgresTest`) |
| Real start of the built jar on a fresh empty database with the three settings | First start created `first.admin@example.test` (address normalised) as `ACTIVE` with only `ADMIN`, a `{bcrypt}` hash and one `USER_BOOTSTRAP_ADMIN` audit row without actor; the second start logged "skipped: an active administrator already exists"; health `UP` both times; the generated password appeared in neither log. The database was dropped afterwards |

### Follow-ups

- #18 (Zhang Hanming): admin console, user API, deactivation and guard rails (including "cannot remove the last active ADMIN"), audit viewer.
- #10: use the procedure on the server once access is handed over.

### Authorship

Implementation, tests, documentation and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. No teammate review is claimed.
