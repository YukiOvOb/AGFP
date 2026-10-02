# PostgreSQL Adapter for the Notification Interfaces

Alignment target: [feature/wang-yuanmeng-sprint1-notifications](https://github.com/YukiOvOb/AGFP/tree/feature/wang-yuanmeng-sprint1-notifications), pinned verification commit `01b50778701ed8a729039790238581bb59e929b5`.

Add this module as a dependency of that branch's Spring Boot application. It preserves the signatures of `NotificationRequest`, `LoanReminderGuard.check(long,...)` and `NotificationRequestStore.requestOnce(...)` while adapting this repository's PostgreSQL/UUID data model. The notification branch was not merged in full, and its standalone MySQL/H2 operation is unchanged. Select the `serms-postgres` profile to use the shared database.

## Interface Alignment

| Interface Difference | Implementation |
| --- | --- |
| UUID Loan versus upstream long loanId | Adds unique, immutable loan.reminder_id; UUID loan_id remains the business primary key; UUID truncation/hashing is prohibited |
| String recipient | app_user.notification_principal defaults to the user_id string; case-sensitive and unique; notification foreign keys validate identity |
| MySQL ON DUPLICATE KEY | Primary PostgresNotificationRequestStore uses ON CONFLICT DO NOTHING, preserving the first content and ID |
| LoanReminderGuard lacks a real implementation | Locks Equipment then Loan in the same transaction; rechecks return state, ownership, dueAt and ReminderPolicy windows |
| Legacy notification versus request/delivery model | V003 creates notification_request and notification_attempt, using upstream PENDING/RETRY/DELIVERED/CANCELLED/FAILED states |
| Standalone Flyway V1/V2 conflicts with shared V001/V002 | Profile scans only db/serms-shared and db/serms-notification and uses public.serms_flyway_history |
| Atomicity of successful business transactions and notifications | Retains synchronous NotificationRequested events; JdbcTemplate and JPA share a DataSource and transaction manager |

The default advance reminder window is PT24H, configurable through `serms.notifications.due-soon-lead`. This is an integration default, not a team-approved business policy. When dueAt equals now, the type is DUE_SOON; only now > dueAt is OVERDUE, matching upstream ReminderPolicy. Equipment under maintenance or retired may still have an ACTIVE Loan and must not be assumed returned.

## Local Verification

Run from this repository's root:

```powershell
git fetch origin feature/wang-yuanmeng-sprint1-notifications
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1
```

The script verifies that five contract source files match the pinned commit byte for byte and extracts upstream notification implementations read-only into the ignored .cache directory as test sources. It runs real PostgreSQL tests and Flyway/JPA validation, then removes the temporary test containers and anonymous volumes. Tests use neither a mock LoanGuard nor H2.

Coverage includes real business events committing/rolling back with business writes, concurrent requestOnce, real Guard boundaries, recipient ownership, concurrent delivery, reminder cancellation when return commits first, delivered history preservation, foreign keys and legacy notification migration. Reports are in `integration/notifications/target/surefire-reports`.

`src/contract/java` is used only for compilation/tests and is excluded from the adapter JAR. When upstream interfaces change, update the pinned commit, review the contracts and rerun tests; do not alter fixtures merely to hide incompatibility.

## Integrating into the Target Branch

First verify and install the adapter JAR in this repository:

```powershell
mvn -B -f integration/notifications/pom.xml install "-Dmaven.test.skip=true"
```

This installation command relies on the tests already completed; use the previous section's script for routine verification. Add the following dependency to the target notification application's app/pom.xml, or apply `upstream-app-dependency.patch` from this directory:

```xml
<dependency>
  <groupId>sg.edu.nus.serms</groupId>
  <artifactId>serms-notification-postgres</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Then configure the PostgreSQL URL, username and password, enabling only serms-postgres (do not combine it with the local profile):

```powershell
$env:SERMS_DB_URL='jdbc:postgresql://127.0.0.1:55432/serms?currentSchema=serms,public'
$env:SERMS_DB_USER='serms'
$env:SERMS_DB_PASSWORD='<local-password>'
# Run at the target notification branch root after adding the dependency
mvn -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=serms-postgres"
```

The target application scans sg.edu.nus.serms and discovers the adapter configuration. Primary beans replace the default request store and unavailable Loan guard. The adapter JAR includes dependencies on the PostgreSQL driver and Flyway PostgreSQL extension, without duplicate upstream notification classes. Existing controllers, pages, authentication and worker threads retain their interfaces.

## Database Migration

The application initializes an empty database through V001, V002 and V003. V001/V002 files and checksums remain unchanged. V003 belongs to this adapter module and is excluded from the core database's default initialization directory; environments without the notification adapter remain on V002.

For a manually created V002 database, stop writes, take a backup and confirm that the maximum `serms.schema_version` is 2 and no Flyway history exists. Then run an explicit baseline from this repository:

```powershell
# SERMS_DB_URL / SERMS_DB_USER / SERMS_DB_PASSWORD must point to the PostgreSQL database being upgraded
mvn -f integration/notifications/pom.xml flyway:baseline
```

Start the integrated notification application afterward to apply only V003. Baseline records a version but does not validate the schema; do not run it against unknown databases or another application's database. Do not enable automatic baseline or scan upstream db/migration or db/mysql alongside these locations. Flyway [baseline semantics](https://documentation.red-gate.com/flyway/reference/commands/baseline) skip migrations at or below baselineVersion.

V003 retains all legacy notifications in read-only notification_legacy_v2 and copies them into the new model. SENT maps to DELIVERED; PENDING/FAILED retain their semantics. Read times and attempt counts are preserved without fabricated historical attempt rows. Loan foreign keys reference reminder_id; legacy UUID relationships remain traceable through legacy_notification_id.

Legacy loan reminders use sourceId=loan:<UUID> and period=once to compute the same SHA-256 deduplication keys as upstream, verified against Java NotificationRequest.dedupKey. Legacy business notifications use sourceId=legacy-notification:<UUID>. Semantic duplicates that cause uniqueness conflicts or content exceeding the upstream 2000-character limit fail the entire upgrade and require manual review; history must not be silently truncated or deleted. Do not run legacy notification writers and the new request model simultaneously.

Existing notification_principal values default to UUID strings. The identity provider must make Principal.getName() match this field exactly. If another stable identity code is used, explicitly establish a unique mapping before generating requests; do not infer it from email or display name. Identities already referenced by legacy notifications cannot be renamed arbitrarily. Upstream temporary user login accounts do not automatically represent existing database users.

## Producer Integration Example

Inside a future loan service's Spring transaction, construct a request from the real Loan and publish the existing synchronous event:

```java
@Transactional
public void enqueueReminder(UUID loanId) {
    var request = factory.forLoan(loanId, NotificationType.OVERDUE, "once", "Please return the equipment.");
    events.publishEvent(new NotificationRequested(request));
}
```

The factory resolves the long mapping, recipient and actual dueAt. Do not use random scan IDs or the current time as period. Business services still choose scan periods and reminders; the Guard rechecks eligibility before delivery. The listener must not become asynchronous or after-commit-only. [Shared Spring JPA/JDBC transactions](https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html) require the same DataSource.

The existing database/ReservationRepository manages its own JDBC transactions and cannot be embedded directly in the Spring producer transaction with an assumption of atomicity. Future cross-module write services should share one JpaTransactionManager and JdbcTemplate, or first refactor connection ownership to the caller. Real producer transactions verify this integration contract; the self-committing repository is not wrapped as a purported nested transaction.

## Boundaries and Handover

Delivered: real Guard, request store adapter, request factory, shared migrations, configuration and minimal dependency patch. A full Loan scanner, checkout/return services, identity provider, notification business audits and production deployment remain team integration work. The initial adapter delivery did not modify or push the remote notification branch. Consumers must apply the dependency patch and PostgreSQL profile to use the adapter; it is not a standalone web application.

Sources: the five compilation contracts and notification implementations used in tests come from Wang Yuanmeng's commit 01b5077 in the same repository, retaining author attribution. Codex assisted with adaptation and verification; manual review remains with the team.
