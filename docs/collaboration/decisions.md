# Collaboration Improvement Sources and Decisions

This work adopts only Gateflow's team collaboration practices. Reference snapshot: [bibi45c/gateflow @ f914824](https://github.com/bibi45c/gateflow/tree/f91482416e4bd26c526e80b0674fdd9e8a5034d1); SERMS starting baseline: [6a57722](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/tree/6a57722712ed23b7d6749ad184b75dcc368a73ae). Prepared on 2026-10-13, Asia/Singapore.

## Reference Pattern Choices

| Gateflow pattern | SERMS treatment |
| --- | --- |
| Short AGENTS + CONTRIBUTING + task-specific skills | Adopted; technical rules and contracts continue to reference SERMS sources |
| One primary task owner; one writer per worktree/branch | Adopted; existing assignees and five-member UC ownership retained |
| task / bug / design and PR scope/dependency fields | Templates adopted; existing board retained, no alternate label statuses or fabricated Issues |
| HEAD/base-bound checks; merge separate from acceptance | Adopted; retain test benches, green CI, and 70% requirement |
| Read-only reviewers; agent conclusions are not human approval | Adopted with coordination and actual handovers |
| Sole integrator may merge | Not adopted; retain Write collaborators' self-merging |
| Go microservices, inter-service HTTP, Go internal, root go.mod | Not adopted; retain Spring Boot modular monolith, React, PostgreSQL 18 |
| Python-only tools and Go verification commands | Not adopted; retain Maven, pnpm, existing PowerShell/Shell |
| Remove transitional directories, mandatory Issue labels, new remote permission hierarchy | Not applied directly; separate scope and necessary confirmation required |

References: [Gateflow AGENTS](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/AGENTS.md), [CONTRIBUTING](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/.github/CONTRIBUTING.md), [Testing](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/docs/testing.md), and linked skills. Rewritten for SERMS; Gateflow permissions and technology rules do not apply.

## D-001 Merge Permissions and Member Checks

Sources: [README](../../README.md#collaboration-and-releases) allows Write collaborators to merge after green CI without another approval; the [Sprint plan](../agile/sprint-plan.md) requires at least one member check; the [historical data delivery record](../sprint1-zhoufanhao.md) also retains review requirements.

Status: **Confirmed by the user in that conversation**. Decision: retain self-merging; member checks serve team acceptance without a new mandatory GitHub approving-review gate.

Implementation: clarify PR/DoD descriptions in the current Sprint plan only. README merge permissions and historical records are not rewritten. Affected-owner contract confirmation, green CI, and team Done check/deployment evidence remain required. GitHub branch protection was not changed.

## D-002 Shared Responsibility Source Differences

See [Ownership](ownership.md#sources-of-shared-responsibilities-and-specific-deliveries): Sprint long-term responsibilities differ from platform, shared-testing, and CI/deployment delivery assignments in the technology stack and UC02 contract.

Status: **Awaiting user confirmation**. A decision was requested in that conversation; this index did not reallocate responsibilities.

Options: retain sources while distinguishing long-term responsibility/specific delivery; unify according to the Sprint plan; or unify according to the technology stack and confirmed contracts. The latter two require identifying affected contracts/tasks before deciding changes. Until confirmed, keep all sources and continue independent collaboration improvements.

## Retained Technical and Business Differences

These are existing integration issues, outside this collaboration-rule improvement:

| Difference | Existing source | Treatment in this work |
| --- | --- | --- |
| PostgreSQL 18 target versus some 17/MySQL/H2 configurations | [Technology stack §7](../tech-stack.md#7-aligning-existing-code), workflows/scripts | Preserve; separate integration and upgrade verification task |
| Single-application/migration target versus `database/`, adapter, existing paths | [Technology stack §6](../tech-stack.md#6-team-conventions), [Sprint risks](../agile/sprint-status.md) | No moving, deleting, or rewriting merged migrations; integration reviewed separately |
| Four/five equipment states; five/six maintenance states | [UC02 contract §9](../contracts/auth-and-approval.md#9-items-pending-confirmation), Sprint risks | No state-model choice; original owners/team must confirm |
| Technology status/old Sprint snapshots versus current main | [Technology stack](../tech-stack.md), [Sprint status](../agile/sprint-status.md) | Preserve history; date/commit-bound new acceptance, no automatic historical rewriting |

New decisions record both original rules, explicit options, user choice, impact, and actual changes. Suggestions or tool capability are not user confirmation.

## D-003 Repository Language

Status: **Confirmed by the user in the current conversation on 2026-10-13 (Asia/Singapore)**. The user requested English throughout the repository, especially README, and then requested updating the GitHub repository.

Scope: translate documentation, collaboration guidance, skills, templates, diagram labels, comments, deployment messages, and welcome-page text; update translated heading links. Preserve business/API semantics, ownership, historical evidence, pending decisions, checks, migration contents, and deployment configuration. The translation PR is based on main commit `25036b161847b906cf09ec556407aa4e5d3dc583`; original local maintenance changes remain outside its scope.

## D-004 Local-First Integration and Wenqi Task Execution

Status: **Confirmed by the user in the current conversation on 2026-10-13 (Asia/Singapore)**. The user explicitly requested immediate Issue allocation, took over execution/upload of Shi Wenqi's tasks, and prioritized a completely working local system before cloud publication.

Shi Wenqi remains a team member and retains his original UC01/team responsibilities. Do not assign him new Issues while his account is unavailable. Liu Tongyao executes those tasks and coordinates integration; the other members retain their established modules. Preserve authentic authorship for existing or newly delivered Wenqi code; record Liu's newly authored takeover implementation as Liu's work, not Wenqi's contribution. Do not change the five-member team, business contracts, checks or merge permissions.

Tasks are Issues #5-#10. Database validation names Zhou Fanhao (`zfh-usedname`); the user will request acceptance of his collaborator invitation. Before GitHub permits assignment, record the intended owner without claiming an applied assignee. Wang Yuanmeng (`ezmoneysniper0701`) and Zhang Hanming (`Hanming1008`) receive their relevant platform/notification and approval/browser tasks.

Local startup and UC01-UC05 acceptance are P0. Server access is not yet handed over and must not block local progress; cloud handover/release is P1. Keep credential values outside Git/public Issues. Existing server configuration and production remain unchanged in the local-demo delivery. No new-account workaround or account-appeal result is assumed. PRs and evidence continue through the existing workflow; implementation/local verification does not imply CI, member review, cloud success or team Done.
