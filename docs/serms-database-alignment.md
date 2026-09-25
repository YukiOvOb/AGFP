# SERMS Diagram Alignment and V002 Change Record

Date: 2026-09-25. Owner: Zhou Fanhao. Branch: feat/zhoufanhao-sprint1-data-foundation.

The user requested database adjustments based on diagrams and files in the SERMS directory. This revision builds on initial local commit 2b09c6d using the ER diagram, domain descriptions, equipment/loan state design and integration notes. It preserves V001 and existing Git history and upgrades forward through V002.

## Design Comparison and Changes

| Original Design Requirement | V001 Difference | V002 and Supporting Code |
| --- | --- | --- |
| Many-to-many User and Role relationship | Single role field, missing CUSTODIAN, uses TECHNICIAN | role/user_role; five roles; TECHNICIAN maps to MAINTAINER |
| Entity primary keys, requester_id, start_at/end_at | Generic id/user_id/starts_at/ends_at | Renamed to match the diagram while preserving UUIDs and references |
| account_status, purpose, version | Boolean active, no purpose or version | Migrates to ACTIVE/DISABLED; adds purpose and versions for equipment, reservations, loans and maintenance cases |
| PENDING_APPROVAL, UNDER_MAINTENANCE | PENDING, MAINTENANCE | Migrates existing states and updates constraints, Java enums and queries |
| FULFILLED retains the original slot | Exclusion constraint covers only pending and confirmed reservations | Includes FULFILLED; early return does not release the original slot |
| ON_LOAN allows future reservations after a non-overdue loan's due time | All rejected | Reads and writes share a database availability function using actual loans and active maintenance cases |
| Immutable approval decisions; no self-approval | No table | approval_decision, one per reservation, rejection reason and self-approval checks, immutable records |
| Loans and checkout/return custodians | Planned only | loan, return fields, time and damage details, one loan per reservation and one active loan per equipment |
| Maintenance cases | Planned only | maintenance_case, official states, assignment/closure fields and same-equipment Loan validation |
| Notification retry, read state and deduplication | Planned only | notification, exactly one business reference, unique dedup_key, delivery time and count constraints |
| Audit of important operations | No table or writes | Append-only audit_log; implemented booking/cancellation commits atomically with success audits |

The physical name app_user is retained to avoid the SQL USER name conflict. The original ER diagram does not enumerate account states; ACTIVE/DISABLED are used. Existing equipment.created_at and schema_version are retained even though absent from the diagram.

The Chinese glossary incorrectly uses OR for interval overlap, whereas the integration notes and English version use AND; this implementation follows the latter. Renewal/self-approval entries in attempt.md are discussion headings only, so the detailed design's no-renewal and no-self-approval rules remain in effect.

## Verification Results

Command: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1`.

- Sequential V001 and V002 application on an empty PostgreSQL 17 database: passed.
- V002 upgrade after inserting V1 states, roles, disabled users and reservations: passed; IDs, hashes and business records preserved.
- Java compilation with --release 17 and Maven clean verify: passed.
- Real database integration tests: 27 tests, 0 failures, 0 errors and 0 skipped.
- Concurrent reservations: only one conflicting booking commits for the same equipment. Concurrent loans: at most one ACTIVE Loan for the same equipment across different reservations.
- Additional coverage: multiple roles and duplicate grants, FULFILLED slot occupancy, future ON_LOAN reservations, overdue booking prevention, simultaneous late/damaged returns, approval and maintenance constraints, notification single-reference and deduplication rules, immutable audits and failure rollback, and version conditions rejecting stale updates.
- The one-command script removes its test containers and anonymous volumes on completion. Reports are in database/target/surefire-reports; CI uses the same migrations/fixtures and uploads reports.

Direct SQL fixtures test data constraints; they do not indicate complete checkout/return or approval services. Upper-layer services still handle role authorization, the actual recipient, checkout windows, cross-entity equipment state recomputation, independent audits of failed operations and notification jobs. State Pattern remains a candidate, not a claimed completed implementation.

At the time of this V002 verification, GitHub CI, peer review, test-environment deployment and end-to-end smoke tests had not run. Changes were committed locally; no production database operations or remote push were performed as part of that verification.

## Migration Notes

V001 remains unchanged. V002 preserves existing data and upgrades in one transaction. Existing data volumes require manual V002 application; new volumes initialize in order automatically. Java accessors and table column names have changed, requiring coordinated migration and release. If historical FULFILLED reservations overlap other occupying reservations, migration rejects and rolls back for manual review; it does not delete data automatically. See the [database README](../database/README.md) for commands and responsibility boundaries.

## Reference File Checksums

Original references are under C:/Users/haohao/Desktop/SERMS and were not modified. The following SHA-256 values identify the versions consulted.

| File | SHA-256 |
| --- | --- |
| SERMS_Domain_Glossary_and_ERD.md | 69509CB1EC4180D7E5478DD9C796A78607EC16EBDCDE99C1FC50328440D57FF0 |
| SERMS_Equipment_and_Loan_States.md | CB80860CDD1805A1AB672F5B1B0DFBD1493ADAE765D23B5F3B7AE96972D99387 |
| SERMS_System_Integration.md | 052B96F461C4E0491FFD2FCCD71CD0B51AB4C93A758776578C7C0BD995CC4FAA |
| English_Deliverables/Domain_Glossary_EN.md | AC6F90505C50A0A443783679EFBEAC5374ED70381FB181835FA41E26A97C8694 |
| English_Deliverables/State_Pattern_Candidate_Final.md | D0952824F5C71C797DE7808E3F2A1A930BBE3777C23BFAB206B7D83898F53329 |
| Diagrams/ER Diagram.png | 00922E060AF3C90C8FB9D12672FB51B72BE169B591BAD6FBA2C24110A7244A73 |
| Diagrams/Class Diagram.png | 09C015AB45BA2351D52641E2F87426B9002EB931982B2A2484156C9EEBA0FBD6 |
| Diagrams/Pickup Sequence Diagram.png | 5369AA28BFFB6CC737E4106E4132365045CF585D36CEDD231F15D892937D1895 |
| Diagrams/Return Sequence Diagram.png | 00E5CA9089CE0DA6401BABE477DBB7DE0F7F3C5ACA4B3A9B387308134BB46A83 |

AI usage: Codex assisted with diagram/document comparison, migration and test generation, and local verification. Manual sign-off awaits Zhou Fanhao and the team reviewer; actual hours, meetings and review records were not fabricated.
