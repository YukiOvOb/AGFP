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

| Sprint | Output | Evidence |
| --- | --- | --- |
| Sprint 0 | Stack/architecture, database design, contracts, CI security gates, deployment | PR #1, #2, #3, #5, #6, #7, #10; [tech-stack.md](../tech-stack.md); [auth-and-approval.md](../contracts/auth-and-approval.md) |
| Sprint 1 | Notification/data foundations, UC01 search/reservation | PR #4, #10; UC01 branch; [UC01](../uc01-search-and-reserve.md) |
| Sprint 2 | UC02 / UC04 foundations (Draft PR #8, #9); UC01 policies/approval interface | PR links; [UC01 contract §5–6](../contracts/equipment-and-reservation.md) |
| Sprint 3 | Pending: integration, demo, screenshots | [demo-script.md](demo-script.md) |
| Sprint 4 | Pending: report | — |

PR list: https://github.com/YukiOvOb/AGFP/pulls?q=is%3Apr

## 3. Quality and Test Evidence

| Evidence | Location |
| --- | --- |
| Per-PR CI | GitHub Actions: `CI` (gitleaks, Trivy, Dependency Review), `Frontend and API contract`, `Notification foundation tests`, `Sprint 1 database`, `Notification PostgreSQL contract` |
| Tests/coverage | Actions artifacts `frontend-and-api-evidence`, `notification-test-evidence` (Surefire, JaCoCo, Playwright reports) |
| UC01 test bench | [UC01 Section 6](../uc01-search-and-reserve.md#6-test-bench-and-execution) |
| UC01 screenshots | [`docs/evidence/uc01/`](../evidence/uc01/) |

## 4. Design Evidence

| Evidence | Location |
| --- | --- |
| UC01 activity, sequence, reservation state | [`docs/diagrams/`](../diagrams/) (Mermaid sources); rendered in UC01 |
| Database ERD/states | [sprint1-domain.md](../sprint1-domain.md) |
| Patterns/locations | Technology stack §3.5; UC01 §5.2 |

## 5. Pre-report Checklist (Sprint 4)

- [ ] Every requirement ID links to a PR and test
- [ ] Every Sprint has goals, completion, review screenshots, retrospective
- [ ] Counts, coverage, policy values match latest CI
- [ ] Figures numbered consistently; state/role terms match code
- [ ] Contributions based on PR/commit records
