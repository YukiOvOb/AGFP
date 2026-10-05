# Zhou Fanhao Sprint 1 Initial Delivery Record

This document preserves the historical state of initial commit 2b09c6d. V002 was subsequently aligned with the SERMS diagrams and documents and passed 27 tests. See the [V002 alignment record](serms-database-alignment.md) and [domain model](sprint1-domain.md) for the current design.

Based on sections 3, 4, 5.3, 7 and 8 of the local Team_07_SERMS_Project_Plan.docx. Sprint 1 runs from September 12 to September 25 and targets login, RBAC, search, availability and reservation conflict handling. The plan lists team Sprint 1 goals and long-term individual responsibilities, but no separate Zhou Fanhao Sprint 1 task list. This branch implements the database, domain model and reservation persistence integration foundation on that basis. Checkout and return business functionality is scheduled for Sprint 2; this delivery prepares its design.

Branch: feat/zhoufanhao-sprint1-data-foundation. Base: main at 9ddd40a. Status at initial delivery: local implementation and verification complete, awaiting team review and upper-layer integration; it cannot be marked Done under the project DoD.

## Backlog and Acceptance Tracking

The following local work items were derived from the project plan and have not been synchronized to the team task board. Sizes are relative estimates, not actual work hours.

| ID | User Story | Priority / Size | Acceptance Criteria | Implementation and Verification |
| --- | --- | --- | --- | --- |
| ZFH-S1-01 | As a developer, I need shared domain vocabulary and states so modules interpret them consistently | P0 / M | Define six domain objects, relationships, states and future integration boundaries | sprint1-domain.md: vocabulary, ERD, states and analysis/design diagrams |
| ZFH-S1-02 | As an integrator, I need reproducible data structures | P0 / M | Migrate an empty PostgreSQL database in one run; enforce unique identities, reference integrity and valid times | V001, transactional version marker and real database constraint tests |
| ZFH-S1-03 | As a borrower, I need reliable equipment reservations | P0 / L | Equipment must be available; pending approval occupies a slot; only one conflicting concurrent request succeeds; adjacent reservations are allowed | ReservationRepository, exclusion constraint and concurrency tests |
| ZFH-S1-04 | As a borrower, I need to cancel my own reservations securely | P1 / S | Other users cannot cancel; cancellation releases capacity; repeated cancellation does not change state | Identity-constrained UPDATE and tests |
| ZFH-S1-05 | As a team member, I need an executable integration handover | P0 / M | Provide Java/JDBC contracts, one-command local tests and CI configuration | database/README.md, test-sprint1.ps1 and database.yml |

Zhou Fanhao owns all items above. Estimates, board status and actual hours require his confirmation; this record does not invent meetings or time spent.

## Local Verification

Environment: Windows, JDK 25 (compiled with --release 17), Maven 3.9.10, Docker Desktop and a real PostgreSQL 17 container. CI specifies JDK 17.

Initial real database verification on 2026-09-25 found that a CASE expression in a PL/pgSQL condition required parentheses; the migration transaction rolled back completely. After the fix, migrations and tests passed: 14 tests, 0 failures, 0 errors and 0 skipped. Tests use neither H2 nor a mock database.

Coverage: search and literal escaping, reservation creation, approval slot occupancy, simultaneous bookings for different equipment, adjacent/contained/partially overlapping intervals, cancellation ownership, unavailable equipment, stale search snapshots, disabled users, invalid times/precision, direct SQL bypass attempts, irreversible states, foreign keys and uniqueness, two connections competing for a reservation, and maintenance updates racing with bookings. Each test uses random UUIDs.

The one-command script ran clean verify against a fresh database with a random password: all 14 tests passed, and automatic container cleanup was confirmed. Compose configuration validity, safe rejection of repeated migration and preservation of the version record were also verified.

Reproduction entry point: `scripts/test-sprint1.ps1`. Maven generates machine-readable reports in `database/target/surefire-reports/`; CI is configured to upload the same reports after each run. target is not committed to Git. This record preserves results and reproduction steps without inventing pipeline links.

## Outstanding Team DoD Requirements

- Code review by at least one other member and a PR link.
- Evidence that the three existing remote CI security checks and the new database-test job pass.
- Login/RBAC/HTTP/page integration, test-environment deployment, smoke tests and an acceptance demonstration.
- Team confirmation of the provisional database choice and rules; the team must clarify the project-name discrepancy with the instructor as planned.
- Actual Sprint Review, meeting records, individual work hours and member sign-offs.

At the time of this initial delivery, the branch had not been pushed or merged into main, and production had not been changed. The existing static-site deployment entry point remained unchanged.

## AI Usage Record

Source: OpenAI Codex, used to generate Java, SQL, tests, CI and Markdown design documents from the user-provided project plan and local repository for this Sprint 1 data-layer implementation and verification. Automated check results are recorded above; manual sign-off awaits Zhou Fanhao and the PR reviewer. No external business code was copied.

Third-party components: PostgreSQL uses the PostgreSQL License; pgJDBC uses BSD-2-Clause; JUnit uses EPL-2.0. New Maven dependencies are subject to the existing dependency-review and vulnerability checks. Database concurrency design references the official PostgreSQL Range Types documentation linked in the data module README. AI-generated content follows the repository's eventual licensing policy and does not replace human review.
