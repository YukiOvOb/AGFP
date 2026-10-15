# Notification contract migration-inventory follow-up

Owner: Zhang Hanming, #18 / draft PR #72. Local test input HEAD
`d10f7e82ca2ca9ab63a97c50733b4721f495c142` plus the uncommitted test assertion repair;
fetched main `0be0a29af011862824c6aaab642512241764bcd9`. The original security/browser
[verification.json](verification.json) is preserved as its original input record, not overwritten.

## Observed failure

[Old-HEAD CI run 38742301680](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/actions/runs/38742301680),
job `notification-contract`, failed 1/12 cases in the historical compatibility module:
`NotificationContractTest.existingV2NotificationsUpgradeWithoutLosingHistory` expected schema
version 6 after successfully applying migrations through V007. The main app's 10 other workflows
on d10f7e8 passed; those old-HEAD results do not assert success for a later commit.

## Change

Only `integration/notifications/src/test/java/sg/edu/nus/serms/integration/notifications/NotificationContractTest.java`
changes code: expect schema version 7, independently verify Flyway current version 7,
BIGINT column mapping and zero/non-null session-version backfill on the existing V002 users.
All old notification history, delivered/read/attempt states, dedup-key, FK and legacy immutable-history
checks remain. Production sources/migrations, pinned upstream fixtures, dependencies, API types,
security policy and CI triggers are unchanged. No test is disabled or skipped.

## Actual local verification

- Windows; native PostgreSQL18.6; Maven Wrapper3.9.11; JDK25.0.4 targeting17;
  compatibility bench's existing Spring Boot3.5.16 (not the production app's Boot4 baseline).
- Existing `scripts/prepare-notification-contract.ps1` verified the five public contracts against
  pinned commit `01b50778701ed8a729039790238581bb59e929b5`, extracting only ignored test sources.
- A fresh, worktree-owned, loopback-only PostgreSQL cluster and two separate test databases were
  created. `serms_upgrade` was seeded with V001/V002 and the unchanged V2 notification fixture;
  the tests performed real Flyway baseline/migrate/validate, without mocks/H2.
- Command from root: `app/mvnw.cmd -B -f integration/notifications/pom.xml clean verify`, with
  the same four `SERMS_*` connection variables used by the existing CI script, targeting only those
  disposable databases. Native setup/cleanup helper and full logs remain ignored under `.cache/`.
- **12 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, 2026-10-15 21:30:24 SGT.
- Adapter JAR inspected: no compile-only `sg/edu/nus/serms/notification/` classes packaged.
- Dedicated PostgreSQL server stopped and environment restored; data/logs retained. Existing
  demo/other worktrees/production were not used. Historical Flyway support warnings were not hidden.

For the normal Docker reproduction used by hosted CI, fetch the pinned commit and run from root:

```powershell
git fetch --no-tags origin 01b50778701ed8a729039790238581bb59e929b5
pwsh -NoProfile -File scripts/test-notification-integration.ps1
```

This follow-up is test-only; API/frontend and main-app implementation inputs are unchanged, so the
previous local checks are retained with their original SHA/source hashes, not claimed as a rerun.
Latest delivered-HEAD CI, member acceptance, merge and deployment must be checked separately.
