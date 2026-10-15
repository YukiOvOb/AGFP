# Member 1 Return, Server Release Re-split and Integration

Owner: Liu Tongyao (@cpg25ncu) · Event: re-planning and integration · Related: #27, #10, #9, #5 · Branch: `docs/liu-tongyao-coordination-devlog` · Baseline: main `350085a`

## 2026-10-15 Shi Wenqi returns; #10 and the UC01 part of #9 move to him

### What happened

Shi Wenqi's GitHub account (`YukiOvOb`) was restored, and on 2026-10-15 he joined the repository as a collaborator with the `write` role. Until then [D-004](../collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution) applied: no new Issues for him while the account was unavailable, and Liu Tongyao carried out his UC01 work (#14, #22, #40).

On the repository owner's instruction (Issue events, SGT):

| Time | Change |
| --- | --- |
| 13:34 | #10 (server handover and release) moved from @cpg25ncu to @YukiOvOb, with a handover comment |
| 13:41 | @YukiOvOb added to #9 as co-assignee for the UC01 acceptance only; UC04 and the full-workflow evidence stay with @cpg25ncu |
| 13:42-13:45 | Comments on #9 (how to check UC01) and #10 (his permissions; two deployment options) |
| 13:43 | #27 body: #10 row, new section "Re-split on 2026-10-15 (server release)", #9 sign-off split |

Deadlines written into #10 and #9: inventory, restore rehearsal and first release, and the UC01 acceptance, by Fri 2026-10-16 14:00 SGT; deployed acceptance on the code-freeze SHA by Thu 2026-10-22 at the latest.

Independently, at 13:34 SGT, Shi Wenqi committed `89705f9` on `feature/shi-wenqi-uc01-closeout` (no PR yet): Member 1 Sprint records, a devlog entry and a decision **D-013 Shi Wenqi Resumes Member 1 Work**, which says the new repository is not to be connected to the server yet and #10 keeps its D-005 position as the last step.

### Design notes

- **What `write` allows.** In a repository owned by a personal account, `write` is the highest collaborator role (Maintain and Admin exist only in organizations). It lets him push, merge his own PRs once the required checks pass, run and re-run the `Deploy` workflow, and edit Issues. Actions Secrets, variables and environments can be managed only by the owner, and that cannot be delegated. On 2026-10-15 none were configured.
- **Two deployment options.** Because the Secrets stay with the owner, #10 offers him a choice. Option A: he passes the five `DEPLOY_*` values to the owner privately, the owner sets them and `SERMS_PRODUCTION_READY`, and the gated workflow deploys every merge to `main` after `release-gate` confirms the required checks on that exact SHA. Option B: he runs `scripts/deploy.sh` himself over his own SSH access after checking the twelve required checks by hand, and posts SHA, time and result on #10 for each release. Both use the same script and server layout, so starting with B and switching to A later needs no server change. The choice is his, replied on #10.
- **Host key.** The fingerprint for `DEPLOY_HOST_FINGERPRINT` is to be read on the server itself, not trusted from a first network connection.
- **Server checkout origin.** The workflow requires `origin/main` in `/opt/agfp` to equal the released SHA. That checkout may still fetch from the original repository `YukiOvOb/AGFP`, so pointing `origin` at this repository is part of the inventory.
- **Authorship.** Release preparation already merged (#13, #41) stays Liu Tongyao's authored work, as D-004 requires; the release itself and the server inventory are his.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| D-013 on his branch says the repository is not to be connected to the server yet, while #10 now asks him to deploy by Fri 10-16 | D-013 was committed 45 seconds after #10 was reassigned, so it was drafted without the new assignment | Comment on #10 asking him to confirm the deployment and his option, to update D-013 in his PR (deployment plan, status line, drop the #9 follow-up) and to merge `main` first, because `docs/testing.md` changed with #46 | Open: no reply on #9 or #10 by 16:30 SGT. D-013 needs the owner's confirmation when the PR arrives |
| The #10 comment said the assignment came "a few minutes later (about 13:40 SGT)" than his commit | The time was estimated instead of read from the Issue events | Recorded here with the event times (13:34:14 assignment, 13:34:59 commit) | `gh api .../issues/10/events` |
| The deployment cannot be handed over completely | Secrets and variables are owner-only in a personal-account repository | Options A and B above; the owner keeps the Secrets | Collaborator permission API returns `write`; Secrets, variables and environments APIs return 0 entries |

## 2026-10-15 Integration of the day's PRs

### What happened

Ten PRs entered `main` on Thu 2026-10-15 (SGT). Each delivering PR has its own entry; this section records the order and the merge mechanics.

| PR | Author | Merge commit | Time (SGT) | Issue | Entry |
| --- | --- | --- | --- | --- | --- |
| #42 V005 equipment master data | @cpg25ncu | `b72ee5b` | 01:06 | #40 | [issue-40](issue-40-v005-equipment-schema.md) |
| #43 First-admin bootstrap | @cpg25ncu | `c8a4560` | 01:22 | #41 | [issue-41](issue-41-first-admin-bootstrap.md) |
| #44 Per-equipment maximum duration | @cpg25ncu | `314153c` | 01:26 | #22 | [issue-22](issue-22-uc01-rules-audit.md) |
| #45 Fault category and priority (V006) | @cpg25ncu | `2e9eda3` | 01:31 | #21 | [issue-21](issue-21-uc04-fault-fields.md) |
| #47 Completion evidence | @cpg25ncu | `74103d5` | 12:05 | #5 | - |
| #12 UC05 notifications and overdue view | @ezmoneysniper0701 | `0345a50` | 12:16 | #19 | [issue-19](issue-19-uc05-notifications.md) |
| #39 DevSecOps checks, backup, load scenario | @ezmoneysniper0701 | `f9596c1` | 13:07 | #25 | [issue-25](issue-25-devsecops.md) |
| #70 Dependabot version updates paused | @cpg25ncu | `0ca4c14` | 15:26 | - | [dependabot-pause](2026-10-15-dependabot-pause.md) |
| #30 Local launcher builds clean, fails fast | @cpg25ncu | `2050aed` | 15:49 | #5 | [local-demo-clean-build](2026-10-14-local-demo-clean-build.md) |
| #46 70% coverage gate, real UC02 checks | @Hanming1008 | `350085a` | 16:02 | #24, #8 | [issue-24](issue-24-quality-gates.md), [issue-8](issue-8-approval-postgres-browser.md) |

The progress update with the state of every task due tonight is on #27 ([comment of 16:30 SGT](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27#issuecomment-6187757981)).

### Design notes

- **Up-to-date branches.** Branch protection on `main` requires an up-to-date branch and applies to admins, so every merge made the next PR stale. Each was synced with a merge commit (no rebase or force-push) and merged only after the checks passed on the synced head: #30 as `beb4771` after #70; #46 by its author as `852fc5f` and `0327b7a`, then again by the owner's agent as `b58f53f` after #30 merged. Merges use merge commits, as on earlier PRs.
- **Review before merge.** An agent read-only review of #46 head `5bd746e` found no blocking defect and listed team-wide effects: the PostgreSQL driver moved to compile scope in `app/pom.xml`; `/loans` now requires `BORROWER` or `CUSTODIAN`, matching the menu and `LoanService`; the 70% service/domain line gate runs in every `verify`, so new service or domain code in #17, #18 and #20 needs tests in the same PR; `approval-postgres` is not a required status check, which is the owner's decision. This review is supporting evidence, not a member check.
- **Partial delivery stays open.** #46 delivered the first part of #24 and the UC02 evidence for #8; both Issues stay open because the #24 final pass waits for #17, #18 and #19.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| #46 could not merge after #30 merged | Strict up-to-date protection | Merged `main` `2050aed` into the branch (`b58f53f`), marked the PR ready, merged after the checks | 16 hosted checks passed on `b58f53f`; local run below |
| The author's branch gained a commit he did not push | Owner-side sync of #46 | Comment on #46 asking him to pull before pushing again | PR comment of 16:03 SGT |
| 22 Dependabot PRs and 200+ Actions runs right after #39 | No PR limit, grouping or major filter | #70 | See its entry |

### Verification

- `main` `0ca4c14`, fresh detached worktree, new PostgreSQL 18 cluster with demo data (Windows 11, JDK 25.0.4, Node.js 24.20.0, pnpm 11.19.0, PostgreSQL 18.6): health `UP`; `scripts/smoke-local.mjs` 28 checks, `scripts/check-uc01-uc04.mjs` 24, `scripts/check-local-browser.mjs` 6 and `scripts/check-uc01-uc04-browser.mjs` 10 passed. Posted on #5.
- #46 head `b58f53f`: `scripts/test-uc02.ps1 -IncludeLoanIntegration` under Windows PowerShell 5.1 passed 590 regression tests, 30 UC02 PostgreSQL tests and 30 UC03/UC05 PostgreSQL tests; the business coverage check met all limits. The real-browser matrix was not re-run.
- `main` `350085a`: 15 hosted checks passed; `release-gate` failed as designed because `SERMS_PRODUCTION_READY` is unset, and `deploy` was skipped.
- For this entry: `node scripts/check-collaboration.mjs` and `git diff --check`.

### Deviations

- [D-004](../collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution) says not to assign new Issues to Shi Wenqi while his account is unavailable. With the account restored, the owner assigned #10 and the UC01 part of #9. The decision record has not been updated yet; D-013 on his branch is meant to do that and waits for the owner's confirmation.
- #10's first release (Fri 10-16) comes before the feature freeze, while [D-005](../collaboration/decisions.md#d-005-local-closeout-and-cloud-last) places cloud deployment last. Deployed acceptance on the code-freeze SHA (by Thu 10-22) stays the last step. How D-013 words this is decided by the owner when the PR arrives.

### Follow-ups

| Item | Owner |
| --- | --- |
| Reply on #10 with option A or B; open the PR for `feature/shi-wenqi-uc01-closeout` with D-013 updated and `main` merged in; first release and UC01 acceptance by Fri 10-16 14:00 SGT | Shi Wenqi |
| Confirm or amend D-013; set the Secrets and `SERMS_PRODUCTION_READY` if he chooses option A; close #5 as the sign-off | Liu Tongyao |
| #18 and the #24/#8 boxes by tonight 23:00 SGT | Zhang Hanming |
| #20 by tonight 23:00 SGT; #17 and #6 by Fri 10-16 01:06 SGT | Zhou Fanhao |
| Check delivery after 23:00 SGT and bring re-split options to the owner (D-007, recorded on #27) | Liu Tongyao, agent-assisted |
| Draft PR #29 (Actions reduction): parked unchanged until after the presentation, on the owner's decision; 78 commits behind `main`, conflicts in two documents, and it touches `deploy.yml` during the #10 handover | Liu Tongyao |

Cleanup on the owner's decision: the agent worktree used for #30 and the merged remote branch `fix/start-local-clean-build` were removed.

### Authorship

The Issue and PR comments, the sync and merge of #46, the #27 updates, the cleanup and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. The re-split, the deadlines, the merges and parking #29 were Liu Tongyao's decisions. The content of Shi Wenqi's branch is his own. No teammate review, sign-off or acceptance is claimed.
