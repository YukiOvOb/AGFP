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
| Sprint 0 | Stack/architecture, database design, contracts, CI security gates, deployment | `YukiOvOb/AGFP` PR #1, #2, #3, #5, #6, #7, #10; [tech-stack.md](../tech-stack.md); [auth-and-approval.md](../contracts/auth-and-approval.md) |
| Sprint 1 | Notification/data foundations, UC01 search/reservation | `YukiOvOb/AGFP` PR #4, #10, #11; [UC01](../uc01-search-and-reserve.md) |
| Sprint 2 | UC02/UC04 foundations, UC01 policies and approval interface, platform REST/React | `YukiOvOb/AGFP` PR #9, #12; [UC01 contract §5–6](../contracts/equipment-and-reservation.md) |
| Repository move (2026-10-12) | Original remote suspended; repository recreated from local history in the team repository; history kept | [Project review](../devlog/2026-10-14-project-review-and-completion-plan.md); team repository branch `github-main-before-ban` |
| Sprint 3 | Integration line, Spring Boot 4 packaging, UC01–UC05 completion work, reports, completion status and demo flow | Team PR #1, #4, #11, #14, #28, #31–#33, #35–#38, #42–#45, #47, #12; [completion status 2026-10-15](sprint-status.md#member-1-completion-status-2026-10-15); [demo-script.md](demo-script.md); [development log](../devlog/README.md) |
| Sprint 4 | Report | This index; open items in the checklist below |

PR lists: Sprint 0–2 in https://github.com/YukiOvOb/AGFP/pulls?q=is%3Apr; from Sprint 3 in https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pulls?q=is%3Apr. On 2026-10-15 `YukiOvOb/AGFP` `main` was brought to the team repository's content (its PR #14), so both repositories hold the full history; PR numbers restart in the team repository, so always name the repository with the number.

Planning and re-planning records: [D-007 to D-012](../collaboration/decisions.md#d-007-completion-task-ownership) (ownership, state models, scope freeze, schedule, development log, 24-hour rule), [Issue #27](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27) (task list with owners and dates).

## 3. Quality and Test Evidence

| Evidence | Location |
| --- | --- |
| Per-PR CI | 12 required GitHub Actions checks: `Secrets scan (gitleaks)`, `Vulnerability scan (Trivy)`, `Dependency review`, `database-test`, `java-tests`, `frontend`, `notification-contract`, `checkout-return`, `collaboration-docs`, `maintenance-postgres`, `release-config`, `release-images` (list in `scripts/verify-release.mjs`) |
| Tests/coverage | Actions artifacts `frontend-and-api-evidence`, `notification-test-evidence` (Surefire, JaCoCo, Playwright reports) |
| UC01 test bench | [UC01 Section 6](../uc01-search-and-reserve.md#6-test-bench-and-execution) (2026-10-15: 86 backend + 22 component tests, 0 failures) |
| Local demo verification | [verification](../evidence/local-demo/verification.md), [testing guide](../testing.md) |
| UC01 screenshots | [`docs/evidence/uc01/`](../evidence/uc01/) |

## 4. Design Evidence

| Evidence | Location |
| --- | --- |
| UC01 activity, sequence, reservation state | [`docs/diagrams/`](../diagrams/) (Mermaid sources); rendered in UC01 |
| Database ERD/states | [sprint1-domain.md](../sprint1-domain.md) |
| Patterns/locations | Technology stack §3.5; UC01 §5.2; "Design notes" in the [development log](../devlog/README.md) |
| UC03 / UC04 analysis and design | [UC03](../uc03-checkout-and-return.md), [UC04](../uc04-fault-and-maintenance.md), diagram sources in [`docs/diagrams/`](../diagrams/) |

## 5. Pre-report Checklist (Sprint 4)

Status 2026-10-15 (Member 1). A tick means the material exists in the repository, not that the team has reviewed it.

- [x] Every UC01 requirement ID links to a PR and test ([backlog §2.2](backlog.md#22-uc01-search-and-reserve-equipment-member-1), [UC01 §1.2](../uc01-search-and-reserve.md#12-alternative-and-exception-flows))
- [ ] Every other use case's requirement IDs link to a PR and test (each owner)
- [ ] Every Sprint has goals, completion, review screenshots, retrospective (retrospective rows in [sprint-status.md](sprint-status.md) are still empty; meetings are not inferred)
- [x] UC01 counts and policy values match the latest run (2026-10-15)
- [ ] Coverage figure from the CI gate (#24)
- [ ] Figures numbered consistently; state/role terms match code
- [ ] Contributions based on PR/commit records (each member checks their row)
