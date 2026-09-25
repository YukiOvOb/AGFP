# ADR 001 Durable notification requests and synchronous events

Status: proposed implementation for team review. Owner: Wang Yuanmeng.

The first repository revision has no Java application. Add an isolated `app/` package root with the planned Java 17/Spring Boot modular monolith, rather than changing the existing static deployment before the booking slice is integrated.

Notification generation must not lose requests after a business commit or deliver rolled-back changes. A synchronous Spring application event listener writes a durable request in the producer transaction. A separate bounded worker performs in-app delivery after commit. Database uniqueness enforces identity across simultaneous workers; a pessimistic notification lock serialises visibility changes. The loan adapter must take Equipment/Loan locks before the notification lock.

A direct application-service call is a valid simpler alternative and is supported. Observer events decouple producers from notification formatting/persistence, but add an implicit call path, so the transaction requirement is documented and tested. An after-commit callback alone was rejected because a process crash can lose the request. A separate broker/outbox relay is unnecessary for database-only in-app delivery; external delivery would require its own idempotent transport contract.

Costs: schema and retry lifecycle management; the shared migration sequence and stable recipient identity must be agreed with the database/identity owners. H2 is provided only for local development; MySQL is the intended integration database.
