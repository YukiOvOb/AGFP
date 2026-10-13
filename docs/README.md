# SERMS Documentation Index

This index provides navigation without replacing existing standards; historical deliveries, snapshots, and evidence retain their locations.

| Topic | Entry point |
| --- | --- |
| Participation, branches, PRs, handover | [Collaboration guide](../.github/CONTRIBUTING.md) |
| Agent startup and skills | [AGENTS](../AGENTS.md) |
| Technology, layering, test benches, alignment targets | [Technology stack](tech-stack.md) |
| Member use cases and shared-responsibility differences | [Ownership](collaboration/ownership.md) |
| Tools, sole writers, delegation, resume | [Agent workflow](collaboration/agent-workflow.md) |
| Conflicts, reference patterns, user decisions | [Decisions](collaboration/decisions.md) |
| Commands, actual CI, evidence formats | [Testing](testing.md) |
| Current progress and remaining acceptance | [Integration update 2026-10-14](agile/sprint-status.md#integration-update-2026-10-14), [2026-10-13 snapshot](agile/sprint-status.md#current-local-progress-2026-10-13) |
| Development process, problems met, report material | [Development log](devlog/README.md), [2026-10-14 review and completion plan](devlog/2026-10-14-project-review-and-completion-plan.md) |
| Local startup and real-flow checks | [Local demo](local-demo.md), [Verification](evidence/local-demo/verification.md) |
| Local cleanup, retained history and next work | [Repository closeout](local-repository-closeout.md), [Repository preparation](repository-preparation.md) |
| Interface contracts | [Contracts](contracts/README.md) |
| Backlog, Sprints, task status, report evidence | [Agile](agile/README.md) |
| Diagram source files | [Diagrams](diagrams/) |
| Actual screenshots/acceptance evidence | [Evidence](evidence/) |
| Recorded architectural decisions | [ADR](adr/) |
| Platform/React integration | [Platform REST/React](platform-rest-react.md) |
| UC01 | [Search and reservation](uc01-search-and-reserve.md) |
| UC02 | [Approval implementation](uc02-approval-implementation.md) |
| Data foundation/historical delivery | [Database](../database/README.md), [Alignment](serms-database-alignment.md) |
| Notification adaptation/integration | [integration/notifications](../integration/notifications/README.md), [Alignment](notification-interface-alignment.md) |

## Project Structure

| Path | Responsibility and treatment in this work |
| --- | --- |
| `app/` | One Spring Boot application; retain `sg.edu.nus.serms.<business-module>.{api,service,domain,repository}` |
| `frontend/` | One React application; `src/features/` corresponds to business modules, reusing `app/`, `api/`, `components/`, `lib/` |
| `database/` | Retained data-foundation/regression module; production migrations now live in `app/` |
| `integration/notifications/` | PostgreSQL adapters are included by app's build; pinned historical contract testing remains separate |
| `web/`, root Docker/Compose | Existing deployment entry; distinguish current runtime and three-container target |
| `scripts/` | Existing test/deployment scripts retained; collaboration check entry added |
| `docs/collaboration/` | Added practices, ownership sources, agent workflow, decisions |
| `.agents/skills/` | Added SERMS operating guides; read as needed without duplicating rules |
| `.github/` | Existing workflows retained; collaboration guide, Issue/PR templates, document checks added |
| `.claude/CLAUDE.md` | Claude Code forwards to root AGENTS, avoiding duplicate rules |

Documents may describe earlier branches, database versions, or unmerged states. Check dates/commits before reading them as current acceptance; never delete historical evidence just to update navigation.
