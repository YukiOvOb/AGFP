# SERMS Ownership Boundaries

This file summarizes existing responsibilities and their sources. It does not reallocate work or infer members' GitHub accounts. Deliveries follow the relevant contract or agreed task; source conflicts require asking the user through the [Decision record](decisions.md).

## Agreed Use-case Ownership

Member names come from the [technology stack test scope](../tech-stack.md#52-each-members-test-bench-scope), and Member numbers from the [Sprint plan](../agile/sprint-plan.md).

| Member | Use case | Main business paths |
| --- | --- | --- |
| Member 1 · Shi Wenqi | UC01 Search and reserve equipment | `app/.../equipment/`, `app/.../reservation/`; `frontend/src/features/{equipment,reservation}/` |
| Member 2 · Zhang Hanming | UC02 Review and approve reservations | `app/.../approval/`; `frontend/src/features/approval/` |
| Member 3 · Zhou Fanhao | UC03 Equipment checkout and return | `app/.../loan/` (target); existing `database/` and `integration/notifications/` foundations |
| Member 4 · Liu Tongyao | UC04 Fault reports and maintenance | `app/.../maintenance/`; `frontend/src/features/maintenance/` |
| Member 5 · Wang Yuanmeng | UC05 Overdue detection and notifications | `app/.../notification/`; `frontend/src/features/notification/` |

`app/.../` means `app/src/main/java/sg/edu/nus/serms/`. Paths describe responsibility, not implementation of then-absent modules such as `loan`. Each member still provides a complete test bench for their features.

## Sources of Shared Responsibilities and Specific Deliveries

| Item | Long-term shared responsibility in Sprint plan | Specific delivery in technology stack / confirmed contracts |
| --- | --- | --- |
| Requirements, tasks, Sprints, agile evidence | Member 1 · Shi Wenqi | [Agile README](../agile/README.md) assigns maintenance to Member 1 |
| Architecture, page structure, shared styling | Member 2 · Zhang Hanming | [UC02 contract](../contracts/auth-and-approval.md#1-development-sequence) assigns login, ProblemDetail, OpenAPI, and React scaffolding to Wang Yuanmeng |
| Data model, database, migrations | Member 3 · Zhou Fanhao | UC02 contract also assigns Zhou Fanhao; retained |
| Test strategy, security, quality | Member 4 · Liu Tongyao | [Technology stack §5.2](../tech-stack.md#52-each-members-test-bench-scope) and UC02 contract assign shared testing infrastructure to Zhang Hanming |
| Repository, CI/CD, releases, operation | Member 5 · Wang Yuanmeng | UC02 contract item 5 assigns CI/deployment delivery to Shi Wenqi; [Sprint snapshot](../agile/sprint-status.md) separately lists Member 5 deployment work |

Long-term responsibilities and a PR's delivery owner can differ, but do not treat these differences as confirmed delegation without agreement. See [Ownership decision](decisions.md#d-002-shared-responsibility-source-differences). Until confirmation, retain all sources and current delivery conventions; new cross-responsibility tasks need an explicit primary owner and affected participants.

## Shared Files at Risk of Concurrent Edits

| Area | Coordination needed |
| --- | --- |
| `app/pom.xml`, `frontend/package.json`, lockfile | Versions, duplicate dependencies, scans, combined testing |
| `app/.../shared/`, `identity/`, `frontend/src/{app,api,components,lib}/` | Platform behavior and all consumers; avoid rebuilding authentication, authorization, errors, or shared UI per UC |
| Flyway, `database/`, notification adapter | Migration numbers, schema, compatibility, upgrades; follow contracts and data ownership |
| Shared fixtures, MSW, Playwright login | Reuse facilities; never break other tests' data/behavior for local passes |
| Root Docker/Compose, `.github/workflows/`, deployment scripts | Startup, CI, production impact; this work does not replace existing flows |
| Rules, contracts, Sprints, evidence indexes | Consistent sources; templates/skills must not enact unconfirmed decisions |

These paths are not a prohibitive CODEOWNERS list and do not alter Write permissions. PRs touching them list impact, dependencies, and actual confirmations; one writer integrates, with other sessions read-only or proposing changes on separate branches.
