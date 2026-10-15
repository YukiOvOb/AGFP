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

## 2026-10-15 owner-directed integration closeout

Liu Tongyao directed Codex to complete Wang Yuanmeng's PR #12/#39 closeout and necessary repository comments. Wang retains DevSecOps authorship/ownership. Codex is the sole closeout writer in `C:/Users/14824/.codex/worktrees/yuanmeng-devsecops-closeout/SWE5006_Group_Project`, branch `codex/yuanmeng-devsecops-closeout`; source HEAD was `9e026912fc6d73e8ad3c5d9c9f0c711afcf11c4d`.

PR #12 merged as `0345a5054d2610a32adcc2c10cc8454b9ecd53b4`. This branch was synchronized with that actual main revision. The resulting application/frontend/database/integration/Compose files are identical to the UC05 closeout input, with no additional application changes from #39. Its native PostgreSQL 18.6 verification passed 589 ordinary tests, 20 UC03 and 10 UC05 integration tests; frontend API checks, lint/typecheck/build, 133 tests, 12 Playwright cases, real local smoke and real Chromium UC05 acceptance passed. See [the exact input and evidence record](2026-10-15-yuanmeng-uc05-closeout.md). Those local results are reused only for identical source; hosted checks must run for this PR's final HEAD.

`bash -n scripts/backup.sh` and `node --check scripts/load-k6.js` passed on Windows/Git Bash and Node 24.20.0. Git mode `100755` confirms the backup script is executable. The earlier scratch restore and 50-VU run above are Wang's recorded rehearsal evidence, not a new production run by Codex. The final integration adds only the original six DevSecOps files plus this closeout record. No migrations, API contracts, production cron entries or branch-protection settings are changed.

The final warm reservation p95 remains **2.26 s > 2 s**: functional success does not establish performance acceptance. The [durable follow-up](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/25#issuecomment-6184186165) retains the failed threshold, final-baseline rerun and sanitized-summary requirement. Production backup scheduling/timezone and the deployed ZAP baseline remain under #10/#25. Issue #25 stays open. Latest-HEAD CI, CodeQL findings, actual merge, deployment, member checks and team Done are recorded separately; no member approval, personal test or effort hours are invented.

Rules/skills: the UC05 closeout record lists shared rules read; `serms-integrate`, `serms-verify`, `serms-pr` and `serms-worktree` guide this phase. Codex authored this dated integration section under Liu's instruction.
