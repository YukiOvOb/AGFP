# Wang Yuanmeng Sprint 1 implementation

> This document records the original notification foundation. The current REST/React and Identity implementation, run instructions, and remaining PostgreSQL dependencies are described in [platform-rest-react.md](platform-rest-react.md). The old Thymeleaf/default-login instructions below are historical and no longer apply.

This branch implements the UC-05 notification foundation and shared UI/architecture baseline described in Report 01 sections 3.5, 4.3 and 4.5. The starting repository contained only a static welcome page and deployment/security workflows. `app/` is an independently runnable Spring Boot application for integration with the team's future application skeleton. The root Docker/Compose deployment remains the existing static site until the team integrates the application.

## Run and verify

Java 17+; the committed Maven Wrapper downloads Maven on first use:

```sh
cd app
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://127.0.0.1:8081. The local profile uses a file-backed H2 database under `app/data/`; use the default generated Spring Security password printed on startup and username `user`. No shared password is committed. `SPRING_SECURITY_USER_NAME` and `SPRING_SECURITY_USER_PASSWORD` can configure a local account through the environment. This temporary authentication foundation must be replaced with the team's identity/RBAC provider. Notification recipient keys must equal the authenticated principal name; agree a stable principal identifier before integration.

The default profile requires MySQL and `SERMS_DB_URL`, `SERMS_DB_USER`, `SERMS_DB_PASSWORD`. Flyway owns schema creation; Hibernate validates it. Do not use `ddl-auto=update`. UTC is used for stored times and comparisons. The application binds to loopback by default; container integration must explicitly configure `SERVER_ADDRESS` and the required port.

## Implemented scope

- Shared Thymeleaf header, footer, navigation, buttons, forms, tables, empty and error states; login, overview and authenticated inbox pages.
- Durable notification requests and database-enforced request-once identity, including concurrent duplicate requests.
- In-app delivery as an atomic database visibility change; only DELIVERED records appear in the recipient's inbox.
- Durable delivery attempt history, fixed retry delay and terminal failure; cancelled stale requests remain traceable.
- Loan guard contract for return/due-date checks. The missing-adapter fallback defers delivery and reaches FAILED after the configured limit; it never assumes a loan is eligible.
- Scheduled bounded delivery recovery (100 requests per poll), business event integration, and pure due-soon/overdue boundary policy.
- Recipient-scoped inbox and read action, CSRF protection and escaped message text.

## Integration contract

A producer publishes `NotificationRequested(NotificationRequest)` **inside its successful business transaction** using Spring's default synchronous event multicaster. The listener requires an active transaction and persists the notification in that same transaction. If the producer rolls back, no request survives. Do not make this listener asynchronous or replace it with an after-commit-only callback: either would remove the atomic persistence guarantee. Delivery workers see only committed requests. A direct `NotificationRequestService.requestOnce` call also joins the producer's transaction.

For business notifications, use a persisted event ID as `sourceId`, `BUSINESS_EVENT` as type, and `once` as period. For loan reminders use a stable loan source, the expected due instant, and an explicit reminder policy period. The SHA-256 identity includes recipient, type, source, period, loan ID and due instant; content changes do not create duplicate reminders. Never use a random scan ID or current timestamp as the period. Identical requests retain the first content.

UC-03 supplies a `LoanReminderGuard` bean. It must obtain Equipment then Loan locks in the current transaction, verify recipient ownership and active possession, compare the expected due date and current reminder window, and retain locks until delivery commits. The notification worker locks the notification only after the business guard, so return/maintenance integrations must use the same order. Return does not delete delivered notifications. `UNAVAILABLE` produces a retry; `RETURNED`, `DUE_DATE_CHANGED`, and `NOT_APPLICABLE` cancel unsent requests. Unexpected infrastructure exceptions roll back and are retried by a later bounded poll; no false delivery success is recorded.

UC-04 supplies event type/content/recipient decisions in its service transaction. Identity supplies the authoritative principal identifier. Zhou Fanhao should reconcile these standalone Flyway migration versions with the shared migration sequence and add a Loan foreign key once the Loan table exists. Shi Wenqi can merge the shared resources and packages into the team's application entry point.

## Scope remaining for Sprint 2 integration

Real Loan repository adapter and overdue candidate scanner; production identity/RBAC mapping; UC-03/UC-04 event producers; shared audit integration; integrated MySQL concurrency and loan-return races; final deployment through the team's pipeline. Email transport is out of scope. In-app delivery has no remote transport; external delivery will need a separate idempotency/failure contract.

## Package and page rules

Feature packages contain `controller`, `service`, `domain`, and `repository`. Controllers derive identity from Principal, validate HTTP input and select views. Services own transactions and use-case orchestration; domain policies contain business rules; repositories own persistence. Shared configuration and reusable views live under `shared` and `templates/fragments`. Do not put state transitions or repository orchestration in controllers.

Use the shared CSS tokens and fragments, explicit labels, semantic tables, visible keyboard focus, escaped `th:text` for user content, and POST plus CSRF for mutations. Empty results and errors must explain the next available action. Screen visibility never substitutes for server-side authorisation.

## Evidence and next review

`./mvnw verify` writes Surefire test results and a JaCoCo report under `app/target/`. Tests cover database uniqueness, concurrent request/delivery, transaction rollback, reminder boundaries, cancellation, retry/final failure, history retention, authentication, ownership, CSRF and HTML escaping. H2 tests are a fast local baseline, not proof of MySQL lock behaviour. Record real execution results, review and actual hours in the Sprint Board before marking tasks Done.

Implementation assistance: Codex generated this branch's new code and documentation from the supplied project plan and progress report. Team review and applicable CI/security checks remain required before merge.

## Local verification 24 September 2026

Final `mvn verify` completed successfully on Java 21 with Java 17 compilation target: 19 tests, zero failures/errors/skips. JaCoCo line coverage: 183/204 (89.7%). Browser checks confirmed the shared overview, login, authenticated empty inbox and logout flow. `git diff --check` passed.

The local Docker daemon was not running, so MySQL execution was not performed. The new workflow defines that check, but has not run remotely because this branch has not been pushed. Existing remote security gates and team review remain pending.
