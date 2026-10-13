# Zhou Fanhao Integration with Other Members

Checklist for review, merge, integration after repository recovery; delivered interfaces and required work/confirmation do not establish other members' approval.

| Member | Zhou provided | Integration and acceptance |
| --- | --- | --- |
| Shi Wenqi | Reservation use, CONFIRMED→FULFILLED, equipment/reservation lock order, complete PostgreSQL profile | Confirm time policy permits on-site checkout; one winner for cancel/checkout races; early return retains slot; verify Compose web/app/db, Vite proxy, HTTPS, Smoke Test |
| Zhang Hanming | V004 decision/BIGINT alignment, JDBC ApprovalDecisionPort, unit/integration/concurrency/authorization/rollback tests | Review adapter, self-approval/reasons/immutable decisions/audits; approve→checkout→return; coverage/security/PR gates/dependency scans |
| Liu Tongyao | Damaged return calls MaintenanceService.reportFault, OPEN Case linked to Loan/history, damaged Case retained for retired returns | Damage→assign→start→repair/retire; normal returns preserve other maintenance; faults during loans still allow return; MAINTAINER assignment/authorization; Backlog/Sprint/actual hours |
| Wang Yuanmeng | Unified serms, UUID principal, packaged PostgreSQL bridge, LoanEvent, factory ACTIVE/RETURNED/dueAt facts | Overdue producer/stable periods; factory reminderId, no UI recipient/dueAt; Guard suppresses after return, repeated scans deduplicate; styling/timezone/OpenAPI/after-commit boundaries |

## Cross-module Integration Order

1. Isolated PostgreSQL V001→V004, real test accounts and explicit roles.
2. BORROWER searches/reserves; another APPROVER decides restricted equipment.
3. CUSTODIAN checks identity in-window; FULFILLED, ON_LOAN, ACTIVE Loan.
4. Wang scans reminders, repeats for deduplication; after return Guard suppresses delivery.
5. Receive GOOD, late, DAMAGED, late-DAMAGED; verify priorities, links, audit.
6. After all active cases repaired, AVAILABLE; unrepairable stays RETIRED.
7. Concurrent custodians, unauthorized borrower, CSRF negatives; no partial writes/history rewriting.

## Team Completion or Confirmation Required

- Current deployment is static; Shi Wenqi integrates web/app/db; no direct server deployment here.
- Default/local are PostgreSQL 18, MySQL driver/production migrations removed, H2 isolated tests only. Wang/Zhang review compatibility/evidence.
- Manual histories need backup, version checks, migration account before Flyway; no automatic unknown-history baseline.
- Team supplies demo accounts, role grants, on-site identity policy; no credentials hardcoded in migrations.
- Owners record approved name, final Presentation date, actual hours, reviews, environment acceptance, Sprint meetings.

Local completion is not whole-team DoD. After repository recovery, the user pushes local commits, then follows this review/deployment checklist.
