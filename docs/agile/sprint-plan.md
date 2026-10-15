# Sprint Plan

Core principle (team ownership description): each member owns one business feature and one shared responsibility; **agree rules first, develop separately, cross-check completed work, then integrate, test, and demonstrate together**.

## 1. Cadence and Ceremonies

| Activity | Timing | Chair | Output |
| --- | --- | --- | --- |
| Sprint planning | First day of each Sprint | Member 1 | Sprint goal, board tasks, owners, completion criteria |
| Weekly meeting / stand-up | Weekly | Member 1 | Each member reports completed work, next steps, problems; Member 1 updates [sprint-status.md](sprint-status.md) |
| PR review / team check | As needed | Designated by PR author | At least one member check before task Done; Write collaborators may self-merge after green CI, without a new mandatory GitHub approval gate |
| Sprint Review | Last day of each Sprint | Member 1 | Demonstrate completed features; save screenshots/reports in `docs/evidence/` |
| Retrospective | After review | Member 1 | What worked, improvements, next-Sprint actions in sprint-status.md |

GitHub Projects columns: To Do → In Progress → Review → Testing → Done. Each card records requirement ID ([backlog.md](backlog.md)), owner, and completion criteria; PR descriptions reference cards.

## 2. Definition of Done

A task moves to Done only when all items are satisfied:

1. Developed on a Feature Branch and merged through a PR, with at least one member check before team Done; this is not a new mandatory GitHub merge approval.
2. Green CI: compilation, all frontend/backend tests, service + domain coverage ≥ 70%, gitleaks, Trivy, Dependency Review (and CodeQL, Spotless, ESLint once implemented).
3. Test bench submitted with code, covering normal and major exception flows (technology stack §5).
4. Backend API changes include regenerated frontend types (`pnpm check:api` passes).
5. Cross-module interfaces have contracts (`docs/contracts/`); affected owners confirm changes.
6. Save diagrams, screenshots, and test results during development, not at the end.
7. Successful automatic deployment after merging and passed post-deployment checks.

## 3. Sprint Goals and Coordination

### Sprint 0 — Foundations and Rules

**Goal**: agree requirements, stack, database, testing, and pipeline rules; each member can explain their use case.

| Member | Task |
| --- | --- |
| Member 1 | Organize requirements/tasks; explain search/reservation |
| Member 2 | Define layering, frontend structure, styling; explain approval |
| Member 3 | Design database, business objects, states; explain checkout/return |
| Member 4 | Define testing, security, quality; explain faults/maintenance |
| Member 5 | Establish repository, branches, builds/deployment; explain overdue notifications |

Coordination: agree requirements as a team; each member diagrams their flow, Member 2 checks architecture, Member 3 checks database support.

### Sprint 1 — Reservation Main Flow

**Goal**: borrowers can log in, search, and reserve; CI builds and tests automatically.

| Member | Task |
| --- | --- |
| Member 1 | Search equipment, view availability, submit reservations |
| Member 2 | Authentication, authorization, shared page templates |
| Member 3 | User/equipment/reservation tables and data access |
| Member 4 | Login, search, authorization, reservation conflict tests |
| Member 5 | Automatic builds, tests, security, deployment after submission |

Coordination: Member 2 provides shared templates, Member 3 schema, Member 1 connects reservations, Member 4 tests, Member 5 ensures build/deployment.

### Sprint 2 — Other Core Features

**Goal**: approval, checkout/return, maintenance, and overdue notifications run independently and connect to reservations.

| Member | Task |
| --- | --- |
| Member 1 | Refine reservation rules; integrate main flows |
| Member 2 | Approve/reject reservations |
| Member 3 | Equipment checkout/return |
| Member 4 | Fault reports/maintenance |
| Member 5 | Overdue detection/in-app notifications |

Coordination: shared architecture, database rules, templates, and standards. Open PRs per feature; Write collaborators self-merge after green CI, retaining member-check evidence for team acceptance. Then test the full flow together (reservation → approval → checkout → return / overdue → notification; damage → maintenance).

### Sprint 3 — Integration Testing and Refinement

**Goal**: add no features; complete flows, gather screenshots/evidence, rehearse.

| Member | Task |
| --- | --- |
| Everyone | Fix their features; complete tests, diagrams, pattern explanations |
| Member 1 | Organize completion status/demo |
| Member 2 | Check all layouts, styles, interactions |
| Member 3 | Check relationships, business data, states |
| Member 4 | Full functionality/security (ZAP), load (k6), quality checks |
| Member 5 | Final deployment, release, fallback demo environment |

### Sprint 4 — Report and Submission

| Member | Task |
| --- | --- |
| Each member | Own use case, diagrams, code, tests, contribution record |
| Member 1 | Consolidate tasks, Sprints, agile evidence |
| Member 2 | Consolidate architecture, design transformations, UI evidence |
| Member 3 | Consolidate database, domain model, state design |
| Member 4 | Consolidate testing, security, quality |
| Member 5 | Consolidate CI/CD, deployment, releases, operation |

Coordination: finish individual sections, then cross-check; unify terminology, figure numbering, data, and formatting so the report matches the system.

## 4. Daily Collaboration Rules

- Own your feature while coordinating architecture, database, templates, and interfaces in advance.
- Every task has an explicit owner and completion criteria.
- Develop on Feature Branches and merge PRs; green CI permits Write collaborators to self-merge, while team Done requires at least one member check.
- See [Decision D-001](../collaboration/decisions.md#d-001-merge-permissions-and-member-checks) for merge/acceptance clarification; other DoD conditions remain.
- Report completed work, next plans, and problems weekly.
- Save diagrams, results, screenshots, and deployment records during development.
