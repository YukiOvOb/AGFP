# SERMS Agile Development Evidence Index

Sources for the Sprint 4 report's agile-development chapter. Member 1 maintains this index and adds an entry after each Sprint.

First report mention uses **Shared Equipment Reservation and Maintenance System (SERMS)**; subsequent mentions use **SERMS**.

## 1. Requirements and Planning

| Evidence | Location |
| --- | --- |
| Requirements, stories, acceptance | [backlog.md](backlog.md) |
| Goals, ownership, DoD, ceremonies | [sprint-plan.md](sprint-plan.md) |
| Weekly progress, risks, retrospectives | [sprint-status.md](sprint-status.md) |
| Board | GitHub Projects (To Do → In Progress → Review → Testing → Done); end-of-Sprint screenshots in `docs/evidence/board/` |

## 2. Sprint Outputs

Two repositories hold the history. Sprints 0-2 started in Shi Wenqi's repository `YukiOvOb/AGFP`, which was suspended in October 2026; its commits were carried into this repository, so PR numbers in commit subjects up to 2026-10-07 (for example "(#11)") refer to **AGFP**, not to this repository. From 2026-10-12 all PRs are in `cpg25ncu/shared-equipment-reservation-and-maintenance-system`.

| Sprint | Output | Evidence |
| --- | --- | --- |
| Sprint 0 | Stack/architecture, contracts, CI security gates, first deployment | AGFP #1-#3, #5-#7 (commits `3aedc8b`, `7e39485`, `9ddd40a`, `75e674a`, `a1fcf0b`, `553ea52`); [tech-stack.md](../tech-stack.md); [auth-and-approval.md](../contracts/auth-and-approval.md) |
| Sprint 1 | Data foundation, notifications, UC01 search/reservation with requirements and Sprint documents | AGFP #4, #10, #11 (commit `0bf5b3b`); [UC01](../uc01-search-and-reserve.md) |
| Sprint 2 | UC02-UC05 implementations, UC01 policies and approval port, PostgreSQL 18 runtime | This repository #1, #11, #13, #14; [UC01 contract §5-6](../contracts/equipment-and-reservation.md) |
| Sprint 3 | Completion tasks #15-#26 on `main`: frontend foundation (#31), reports (#33), UC01 rules/audit/limit (#32, #44), V005 (#42), admin bootstrap (#43), UC04 fields (#45), UC05 (#12), DevSecOps (#39); UC01 real-browser acceptance | [sprint-status.md](sprint-status.md#member-1-update-2026-10-15), [UC01 verification](../evidence/uc01/verification.md), [completion evidence](../evidence/completion-2026-10-15/verification.md), [demo-script.md](demo-script.md) |
| Sprint 4 | Pending: report, rehearsal (Fri 2026-10-23 to Sun 2026-10-25), presentation Mon 2026-10-26 | [Contribution records](#5-contribution-records) |

PR list: https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pulls?q=is%3Apr

## 3. Quality and Test Evidence

| Evidence | Location |
| --- | --- |
| Per-PR CI | GitHub Actions: `CI` (gitleaks, Trivy, Dependency Review), `Frontend and API contract`, `Notification foundation tests`, `Sprint 1 database`, `Notification PostgreSQL contract` |
| Tests/coverage | Actions artifacts `frontend-and-api-evidence`, `notification-test-evidence` (Surefire, JaCoCo, Playwright reports) |
| UC01 test bench | [UC01 Section 6](../uc01-search-and-reserve.md#6-test-bench-and-execution) |
| UC01 screenshots and real-browser record | [`docs/evidence/uc01/`](../evidence/uc01/), [verification.md](../evidence/uc01/verification.md) |

## 4. Design Evidence

| Evidence | Location |
| --- | --- |
| UC01 activity, sequence, reservation state, analysis and design classes | [`docs/diagrams/`](../diagrams/) (`uc01-*.mmd`, `reservation-state.mmd`); rendered in [UC01](../uc01-search-and-reserve.md) |
| Database ERD/states | [sprint1-domain.md](../sprint1-domain.md) |
| Patterns/locations | Technology stack §3.5; UC01 §5.2 |
| Development log | [`docs/devlog/`](../devlog/README.md) |

## 5. Contribution Records

Each member records their own contribution from PR and commit records; agents must not add hours, reviews or approvals.

| Member | Record |
| --- | --- |
| Member 1 · Shi Wenqi | [contribution-shi-wenqi.md](contribution-shi-wenqi.md) |
| Member 2 · Zhang Hanming | Pending |
| Member 3 · Zhou Fanhao | [Work summary](../zhoufanhao-work-summary.md) |
| Member 4 · Liu Tongyao | Pending |
| Member 5 · Wang Yuanmeng | [Sprint 1 record](../wang-yuanmeng-sprint1.md) |

## 6. Pre-report Checklist (Sprint 4)

- [x] Every UC01 requirement ID links to tests and evidence ([backlog §2.2](backlog.md#22-uc01-search-and-reserve-equipment-member-1))
- [ ] Every other requirement ID links to a PR and test
- [ ] Every Sprint has goals, completion, review screenshots, retrospective
- [ ] Counts, coverage, policy values match latest CI
- [ ] Figures numbered consistently; state/role terms match code
- [ ] Contributions based on PR/commit records
