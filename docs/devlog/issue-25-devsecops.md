# DevSecOps completion (#25)

Owner: Wang Yuanmeng (@ezmoneysniper0701) · Baseline: `main` after #15 · Branch: `feature/wang-yuanmeng-devsecops`

## What was built
CodeQL analyzes Java and TypeScript on PRs and main; Java uses a manual Maven build. Dependabot checks Maven (`app`, `database`, `integration/notifications`), frontend npm, and GitHub Actions weekly. `scripts/backup.sh` creates PostgreSQL 18 custom archives, checks their catalog, publishes them atomically, and removes matching daily dumps older than seven days after a successful backup. `docs/runbook-backup.md` describes scheduling and isolated restore. `scripts/load-k6.js` exercises login, equipment search and reservation with 50 concurrent fixture accounts in an isolated environment.

## Design and scope
The backup script reads only the known plain `SERMS_DB_*` settings from the server-only `.env`; it never sources arbitrary shell content or prints secrets. It verifies the Compose database container image before a dump. The archive and scratch restores stay outside the checkout. No production scheduler or ZAP scan is claimed; both require the server handover/deployment under #10. The k6 scenario creates reservations, so it is restricted to scratch data. CodeQL is added as a workflow, not as a newly required branch-protection check.

## Problems, fixes and evidence
| Symptom | Root cause | Fix | Verification |
| --- | --- | --- | --- |
| Host `pg_restore` is version 14 while the target is PostgreSQL 18 | Host tooling is older than the archive format | Check archives with the same PostgreSQL 18 container used for `pg_dump` | Scratch backup/restore rehearsal |
| First scratch container briefly accepted readiness and then restarted during initialization | PostgreSQL image initialization has a transient server | Wait for a successful query against the final database before seeding | Repeated scratch rehearsal |

## Pending evidence and dependencies
On 2026-10-14 SGT, a scratch PostgreSQL 18-alpine container backed up `serms_test_backup` and restored the archive into a separate `serms_restore_check` database; both had one `rehearsal_marker` row and all commands exited zero. The archive was stored under `/private/tmp` and no production service was touched. CodeQL/Dependabot hosted checks require a pushed PR and GitHub Actions run. A local-only PostgreSQL 18/Spring Boot instance was seeded with 50 distinct borrower accounts and 50 distinct equipment rows. k6 ran 50 simultaneous VUs with one login, search, and reservation per VU. On the final warm run, 200/200 checks passed, 0/250 HTTP requests failed, and all 50 reservations persisted (150 across three separate time windows). Overall HTTP p95 was 1.95 s, login p95 1.46 s, search p95 1.71 s, and reserve p95 2.26 s. **The reserve step missed the project's 2 s response target**, so k6 exits nonzero on the per-operation threshold. Earlier cold/warm runs had overall p95 4.58 s and 2.38 s. These are local laptop results, not production capacity evidence; UC01 reservation performance needs triage. The sanitized raw summaries remain in `/private/tmp/serms-k6-rehearsal.Yvmdgb`; the scratch database, accounts fixture, and temporary passwords were removed after the run. No secrets are committed. Run the read-only ZAP baseline only after #10 has deployed the reviewed target. The release owner must confirm workflow coordination and production scheduler configuration. No review, effort hours, deployment, or security sign-off is claimed.

## Proposal deviations
None in the delivered local scope. Email notifications and other dropped features remain outside this issue.

## Authorship
Prepared with Codex assistance under Wang Yuanmeng's request; no human review or time claim is attributed.
