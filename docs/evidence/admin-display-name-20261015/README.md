# Administrator display-name editing: local evidence (2026-10-15)

Partial Issue #18, PR #72. This is the independent continuation while Wang's session-contract
reply is pending, not implementation or acceptance of that contract.

Input HEAD: `7afe031209cb6384d080b105e84974c6aff17a93`, after merging fetched main
`2d19ef9b4e7b7e38c7241e8b5f9e4dcf524db9cf` (coordination documentation only).
The feature changes were uncommitted inputs relative to that HEAD. Exact source hashes and real
browser start/end times are in [verification.json](verification.json). Earlier evidence directories
remain historical and were not overwritten.

## Passed

| Check | Actual result |
| --- | --- |
| Final backend `scripts/test-uc02.ps1 -PostgresBin '<18 bin>' -IncludeLoanIntegration` | 657 ordinary + 30 UC02 + 30 UC03/UC05 passed, no failures/errors/skips |
| Administrator backend bench (included above) | 67 total: 29 API, 21 service/unit, 15 real PostgreSQL, 2 audit-service tests. This continuation adds 21 backend tests |
| PostgreSQL name rules | Only name changes; credentials/roles/state retained; same-name no-op; unknown/stale account; audit rollback; concurrent same-old-name edits have one winner; two admins with both target locks held can rename each other without audit-FK deadlock |
| Coverage | 1,256 covered / 299 missed business lines = 80.77%; original 70% gate passes; negative exercise fails then restores coverage bytes exactly |
| Frontend quality | lint, typecheck, build and actual-backend `check:api` passed; 158 component tests passed (21 administrator tests) |
| Browser contract | All 14 desktop/mobile Playwright cases passed; simulated API path now includes creation, name edit and new-account login |
| Real browser | Desktop/mobile: create account, clear password, open shared edit dialog, rename, read USER_DISPLAY_NAME_CHANGED audit, sign out and sign in with the same password; new name visible and administrator access still denied |
| Role matrix | 66 groups, 210 page observations, 350 API reads, 140 role-denied mutations including valid-CSRF PATCH, 4 ownership denials; all account/business/audit/notification-read/role fingerprints unchanged |
| Documentation / inventory | Collaboration checker and all 7 Node route/role inventory tests passed |

Windows, PostgreSQL 18.6, Java 25.0.4 compiling release 17, Spring Boot 4.0.8, Maven 3.9.11,
Node 24.19.0, local pnpm 11.25.0 (manifest still pins 11.19.0; discrepancy is retained explicitly).
Full backend final verification finished at 18:21:08 SGT. The real admin flow passed at
18:24:57–18:25:05 SGT. The [read-only role matrix](../role-access-admin-display-name-20261015/README.md)
ran first, at 18:22:00–18:23:44 SGT, before account creation changed the six-account fixture.

Commands, initial failures and fixes are in [the development log](../../devlog/issue-18-admin-console.md).
Initial simulated-CSRF/real-cookie interference and a partial browser-label match were repaired;
failed runs are retained locally and not relabeled as passed. Standard existing chart/chunk warnings
remain warnings. No skipped tests, security suppression or new dependencies.

## Screenshots

Synthetic accounts only; password cleared before capture. Private credentials and raw traces stay
in ignored local caches. The local database, newly created accounts and immutable audits are retained.

- [Desktop editor](desktop-edit-name.png), [mobile editor](mobile-edit-name.png)
- [Desktop directory](desktop-users.png), [mobile directory](mobile-users.png)
- [Desktop audit](desktop-audit.png), [mobile audit](mobile-audit.png)

The dialog and table reuse shared UI. No page-level horizontal overflow was observed; mobile tables
scroll horizontally inside the table container.

## Deliberately unchanged / outstanding

Only display_name is updated, with a fixed-text audit in the same transaction. No role/status/password,
UUID, email or notification-recipient change. Existing Principals and session headers keep their old
name until a fresh sign-in, stated by the UI; `/auth` and security configuration remain unchanged.
Role mutation/deactivation/password reset/session revocation and last-active-admin acceptance still
wait for Identity agreement. PR remains draft; no owner confirmation, member check, deployment or
team Done is claimed. Previous HEAD `f79be6b` passed all 11 hosted workflows; new-HEAD CI must be
verified separately after pushing this continuation.
