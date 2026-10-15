# Zhou Fanhao Database, Checkout, and Return Delivery

Based on Zhou Fanhao's long-term responsibilities and Section 5.3 in `Team_07_SERMS_Project_Plan.docx`, covering database, domain model, integration, UC03. Changes are saved in the backup/local Git; after repository recovery, the user pushes, requests review, and arranges deployment.

## Implemented Content

Unified work/integration entry: [Zhou work summary](zhoufanhao-work-summary.md).

Reuse the PostgreSQL model without duplicating User, Equipment, Reservation, Loan. Production migrations in `app/src/main/resources/db/migration` initialize V001–V004 by default; `database/` retains historical JDBC regression. V004 maps decisions to `APPROVED` / `REJECTED`, four aggregate versions to BIGINT, adds loan/equipment indexes. Old migrations remain unchanged; decision IDs/times/reasons survive and immutable-history triggers are restored.

Identity queries configurable `serms` tables and preserves UUID/principal mapping. A PostgreSQL `ApprovalDecisionPort` adapter makes state, immutable decision, audit, and existing approval notifications atomic. This completes persistence; Zhang Hanming still owns approval rules.

UC03 provides queue, actual checkout/return, lists/details. CUSTODIAN handles handover; BORROWER reads own records; ADMIN needs explicit CUSTODIAN. Writes check session and active database account/current roles; actor never comes from body.

Checkout requires CONFIRMED, matching borrower/on-site identity, `startAt <= now < endAt`, AVAILABLE, no active loans/cases, current version. Success sets ACTIVE Loan, FULFILLED Reservation, ON_LOAN Equipment, audit, transactional event.

Return requires ACTIVE/current version and supports normal/late/damaged/late-damaged. Required damage note creates Loan-linked OPEN MaintenanceCase/audit. Recompute RETIRED > active maintenance > active loan > AVAILABLE. Retired/maintenance returns are accepted without restoring availability. FULFILLED retains original interval despite early return.

React `/loans` provides custodian queue, identity confirmation, condition/damage input, active/history pagination. Borrowers see own records; 409 closes stale confirmation and refreshes for reselection. Reuse sessions, CSRF, ProblemDetail.

## Use-case and Design Delivery

- [UC03 use case/patterns](uc03-checkout-and-return.md): normal/exceptions, acceptance, direct versus State solutions.
- [UC03 contract](contracts/checkout-and-return.md): fields, roles, codes, transactions/events.
- `docs/diagrams/uc03-*.mmd`: editable Analysis Class/Sequence, Design Class/Sequence, Equipment/Loan States.
- [Domain dictionary/ERD](sprint1-domain.md): ten entities/relations/states/constraints; increment described here.
- [Member checklist](zhoufanhao-handoffs.md): four members' inputs/outputs/integration/pending team work.

## Local Verification

With Docker Desktop, from root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1
```

Without Docker but PostgreSQL 18 installed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1 -PostgresBin 'C:/Program Files/PostgreSQL/18/bin'
```

Create isolated databases, run V002 regression, V004 historical-decision upgrade, all app regression and full UC03 PostgreSQL integration. Stop instances afterward, retain logs/data in ignored `.cache/`. Reports: `database/target/surefire-reports`, `app/target/surefire-reports`, `app/target/failsafe-reports`.

Default runtime uses PostgreSQL 18 with `SERMS_DB_URL`, `SERMS_DB_USER`, `SERMS_DB_PASSWORD`, no extra database profile. HTTP development: `--spring.profiles.active=local`. Back up/check existing V002/V003 history; manual psql databases must not bypass checks through automatic baseline. See [Migration](serms-postgres-runtime.md).

## Delivery Status and Evidence Boundaries

Code/design/checks can finish locally; remote PR/member review, hosted CI/security, test deployment, Smoke Test, Sprint Review, actual hours remain team work. All applicable DoD must pass for Done; local delivery is insufficient. Team verifies approved project name/Presentation date, without invented confirmation.

Codex assisted generation/review from existing code, contracts, provided plan. No external business implementation copied. Team supplies human reviewers, hours, PR/deployment evidence; no fabricated meetings/reviews.
