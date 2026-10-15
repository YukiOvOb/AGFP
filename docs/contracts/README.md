# SERMS Interface Contract Index

Repository contracts remain authoritative; changes require a PR and affected-owner confirmation, per [README](../../README.md#collaboration-and-releases). This index does not change contract content.

| Contract / integration record | Modules | Reading purpose |
| --- | --- | --- |
| [Authentication and approval (UC02)](auth-and-approval.md) | identity, approval, reservation, shared platform/data | Authentication, roles, CSRF, public reservation service, approval API, transactions, pending states |
| [Administrator console (#18)](admin-console.md) | identity, audit, frontend admin | User/role/status/password operations and audit reader; Identity/session revocation confirmed by Wang via the user's reply; PR acceptance remains separate |
| [Equipment search and reservation (UC01)](equipment-and-reservation.md) | equipment, reservation, approval | APIs, states, rules, exceptions, approval interface |
| [Notification interface alignment](../notification-interface-alignment.md) | loan, notification, PostgreSQL adapter | Existing integration boundaries; this record does not override confirmed contracts |
| [Approval notification review](../reviews/pr-8-notification-contract.md) | approval, notification | Review context/gaps; verify covered commits |

Add new contracts here and to the index, specifying scope, provider/consumer, input/output, identity/authorization, transactions/concurrency, events/deduplication, exceptions, version impact, testing, and confirmation. Mark drafts pending confirmation; agent proposals are not member confirmation.

Track existing equipment/maintenance state and migration differences through the [Decision record](../collaboration/decisions.md#retained-technical-and-business-differences), rather than deciding them in navigation or collaboration-rule PRs.
