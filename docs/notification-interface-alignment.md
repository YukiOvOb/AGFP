# Interface Alignment with Wang Yuanmeng's Notification Branch

Date: 2026-09-25. Target repository: YukiOvOb/AGFP. Branch: feature/wang-yuanmeng-sprint1-notifications. Verified commit: 01b50778701ed8a729039790238581bb59e929b5.

An independent integration module implements the existing public interfaces while retaining PostgreSQL and UUID domain primary keys. It supplies long Loan IDs, stable recipient identifiers, real loan rechecks and PostgreSQL request deduplication for the upstream notification service. This branch does not include a full merge of the notification application; consumers add the adapter dependency and enable the serms-postgres profile.

## Local Deliverables

- integration/notifications: a consumable adapter JAR retaining the five upstream compilation contracts unchanged.
- PostgresLoanReminderGuard: requires the current transaction, locks Equipment then Loan, and rechecks state, identity, dueAt and the reminder window.
- PostgresNotificationRequestStore: preserves the upstream requestOnce signature, deduplicates atomically in PostgreSQL and retains the first request's content.
- LoanNotificationRequestFactory: resolves the actual reminder_id, identity mapping and due time from a Loan UUID.
- V003: adds stable mappings, notification_request and notification_attempt; preserves read-only V2 notifications and migrates history and deduplication identities.
- PostgreSQL profile: isolates Flyway migration locations and uses public.serms_flyway_history, without reusing the notification branch's standalone V1/V2/V3 versions.
- Minimal app/pom.xml dependency patch, operating instructions and a contract CI workflow.

V001, V002 and the original data module code were unchanged. Test readiness checks now use TCP to avoid mistaking PostgreSQL's temporary initialization process for the running service.

## Verification Evidence

Environment: Windows, Java 25 (release 17), Maven 3.9.10, PostgreSQL 17.11 and Spring Boot 3.5.16, matching upstream.

| Check | Result |
| --- | --- |
| Byte-for-byte comparison of five public contracts with the pinned commit | Passed |
| Flyway V001/V002/V003 initialization on an empty database and Hibernate validate | Passed |
| Explicit baseline 2 followed by V003 upgrade with V2 notification data | Passed |
| PostgreSQL notification integration tests | 12 tests, 0 failures, 0 errors, 0 skipped |
| Core database regression tests | 27 tests, 0 failures, 0 errors, 0 skipped |
| Adapter JAR excludes duplicate upstream notification classes | Passed |
| Minimal dependency patch applies to the target commit's POM | git apply --check passed |
| Adapter JAR installation in the local Maven repository | Passed |

The 12 tests directly use notification Entity, Service, Repository, EventListener and DeliveryStore implementations from the pinned commit, without mocking LoanReminderGuard. Coverage includes concurrent request deduplication, producer rollback, delivery/read state, concurrent delivery, real lock contention where return commits first, history preservation, identity case sensitivity and ownership, dueAt and time boundaries, foreign keys, and legacy reminder SHA-256 deduplication keys matching the Java contract.

Reports are in integration/notifications/target/surefire-reports and database/target/surefire-reports. Test logs and upstream test sources are in the ignored .cache directory; generated artifacts are not committed. The local pass claim does not cover upstream H2/MySQL/UI suites, remote CI, other members' reviews or production deployment.

## Integration and Remaining Responsibilities

See the [integration guide](../integration/notifications/README.md) for the dependency patch, environment variables, migration commands, transactional producer example and identity mapping.

The adapter is not a standalone web application. The notification branch must add the dependency and enable the profile; its temporary login accounts must also match app_user.notification_principal. Full overdue scanning, checkout/return business services, production identity/RBAC and notification auditing remain future integration work.

Sources and contributions: upstream contracts and test implementations come unchanged from Wang Yuanmeng's commit 01b5077. Codex assisted with the database adapter, tests and documentation; manual review awaits Zhou Fanhao and the interface owner. No remote pipeline, review or work-hour records were fabricated.
