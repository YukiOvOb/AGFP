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

Status: **Settled for the completion work by [D-007](#d-007-completion-task-ownership) on 2026-10-14**. It was originally awaiting user confirmation: a decision was requested in that conversation, and this index did not reallocate responsibilities.

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

## D-005 Local Closeout and Cloud Last

Status: **Confirmed by the user in this closeout conversation on 2026-10-13 (Asia/Singapore)**. The user requested completing local repository housekeeping, with cloud deployment last. Basic functionality must be completed first; the user will specify additional changes later.

Scope: preserve the original maintenance checkout's changes/history, prepare a clean local branch from `d42e0a6`, update current progress/testing/navigation, reconcile local backup exclusions and earlier preparation notes. Existing use-case contracts, team membership, CI rules and production remain unchanged. Other sessions' active worktrees and local databases/services are retained. This conversation does not publish remote changes, merge PRs, invite accounts, or execute cloud work.

Execution order: local repository closeout → complete local UC01-UC05 and member acceptance → implement the user's later, separately defined changes → final release/cloud deployment and post-deployment acceptance. No later feature scope, deadline, hours or approval is inferred. Original DoD remains applicable to team Done even while cloud publication is deferred.

## D-006 Spring Boot 4 Security Migration

Status: **Confirmed by the user in this session on 2026-10-13 (Asia/Singapore)**. The built backend image revealed dependency vulnerabilities not detected by source scanning. Spring's corresponding 6.2 fixes require enterprise support, while the OSS fixes are in Framework 7. The existing technology stack selected Boot 3.5; options were to retain it with a blocked release or authorize Boot 4 and full regression. The user explicitly authorized **upgrading to Boot 4, updating the technical agreement and running complete regression**.

Scope: PR #13 migrates the existing single application to Boot 4.0.8/Framework 7.0.9, compatible Springdoc/Jackson/Testcontainers, patched dependency versions and relocated MVC test imports. Preserve Java 17, React/PostgreSQL, JSON business interfaces, strict integer versions, identity/session/CSRF/RBAC, immutable migrations, test coverage and ownership. Existing tests must continue passing, and generated API types and real integrated flows must be verified. No paid Spring artifacts, security suppression, production action or teammate sign-off is authorized or claimed. Server handover remains unavailable; release stays draft until actual readiness.

## D-007 Completion Task Ownership

Status: **Confirmed by the user (repository owner Liu Tongyao) in conversation on 2026-10-14 (Asia/Singapore)**, after a read-only review of main `940bf42`, draft PRs #11-#14 and the Project Proposal. The review is recorded in the [development log](../devlog/2026-10-14-project-review-and-completion-plan.md).

Sources: the differing shared-responsibility tables described in [D-002](#d-002-shared-responsibility-source-differences); requirement owners in the [backlog](../agile/backlog.md); task execution in [D-004](#d-004-local-first-integration-and-wenqi-task-execution).

Decision: for the remaining completion work, the primary owner of each task is its Issue assignee.

| Work | Owner | Issues |
| --- | --- | --- |
| Integration line, frontend foundation and polish, UC01 (on Shi Wenqi's behalf), UC04, reports, release | Liu Tongyao (`cpg25ncu`) | #15, #16, #21, #22, #23, #26, #10 |
| User/role administration, first-admin bootstrap, audit viewer, quality gates | Zhang Hanming (`Hanming1008`) | #18, #24 |
| Equipment and category management, UC03 lookup | Zhou Fanhao (`zfh-usedname`) | #17, #20 |
| UC05 completion, CodeQL/Dependabot/backup/load/ZAP | Wang Yuanmeng (`ezmoneysniper0701`) | #19, #25 |

The five-member use-case ownership is unchanged. Historical long-term responsibility tables stay as history; where they differ from this table for these tasks, this table applies. Shi Wenqi remains Member 1, receives no Issue while his account is unavailable, and takes part in the server handover.

Limits: contracts, merge permissions and checks are unchanged; contract-affecting tasks still need the affected owner's confirmation. The owner made this allocation; other members' acceptance is shown by their own activity on the Issues and is not assumed here. Re-splits are recorded on [the plan Issue](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27).

## D-008 Equipment and Maintenance State Models

Status: **Confirmed by the user on 2026-10-14 (Asia/Singapore)**. This settles the two items left pending in [UC02 contract section 9](../contracts/auth-and-approval.md#9-items-pending-confirmation).

Sources: Proposal 5.2 lists five equipment states (Available, Reserved, Issued, Under Maintenance, Retired) and 5.6 lists six maintenance states (Reported, Assigned, In Progress, Resolved, Not Repairable, Closed). Migration V002 implements four and five.

Decision: keep the implemented models. No migration changes either state set. The project report explains the difference.

| Proposal | Implemented | Reason |
| --- | --- | --- |
| Equipment: Reserved | Not an equipment state; derived per time window from `reservation` rows and shown as `RESERVED` busy slots | An item can hold several future reservations while being available now; one flag on the item cannot represent that, and the exclusion constraint already enforces the rule |
| Equipment: Issued | `ON_LOAN` | Naming only |
| Maintenance: Reported | `OPEN` | Naming only |
| Maintenance: Not Repairable | `UNREPAIRABLE` | Naming only |
| Maintenance: Closed | Folded into the terminal `RESOLVED` / `UNREPAIRABLE` | Both already require a resolution note and timestamp and accept no further transition; no post-resolution step exists that a separate state would guard |

Follow-up: the wording of contract section 9 and of the technology document is updated by the tasks that next touch them (#21 for maintenance, #17 for equipment). New columns for fault category/priority and equipment details are additive migrations and are not affected by this decision.

## D-009 Scope Freeze

Status: **Confirmed by the user on 2026-10-14 (Asia/Singapore)**.

Dropped for this delivery:

| Item | Source that mentioned it |
| --- | --- |
| Email notifications | Proposal 5.7, "may be added if it can be completed within the project schedule" |
| QR codes | Proposal 4.2, "may be generated if time permits" |
| ArchUnit layering checks, Spotless, SpotBugs + FindSecBugs | [Technology stack](../tech-stack.md) targets |
| Automatic cancellation of expired pending reservations (R1.11) | [Backlog](../agile/backlog.md), priority C |

Kept: everything in Proposal chapters 5-7 not listed above, including user/role administration, equipment and category management, all listed reports, audit records and their viewer, the 70% service/domain coverage requirement, CodeQL, Dependabot and the backup runbook. k6 load testing and the OWASP ZAP baseline are best effort inside #25.

The report states these as deviations from the technology document. Static analysis is covered by CodeQL and ESLint in place of SpotBugs.

## D-010 Delivery Schedule

Status: the presentation date **2026-10-26** was stated by the user on 2026-10-14, with the instruction to finish early enough to prepare the report and the demonstration. The intermediate dates below were proposed by the agent in that conversation and published on [the plan Issue](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27); the owner adjusts them there.

| Date | Milestone |
| --- | --- |
| Thu 2026-10-15 | Integration line merged (#15); all work moves to the Spring Boot 4 baseline |
| Fri 2026-10-16 | Component foundation merged (#16); #12 on the new baseline; server inventory requested |
| Mon 2026-10-19 | P0 features merged (#17, #18, #19) |
| Tue 2026-10-20 | P1 features merged (#20, #21, #22, #23); feature freeze |
| Wed 2026-10-21 | Quality gates (#24), cross-module testing, fixes, visual polish (#26) |
| Thu 2026-10-22 | Deployment and deployed acceptance (#10), #25; code freeze |
| Fri 2026-10-23 to Sun 2026-10-25 | Report, demo script, rehearsal; critical fixes only |
| Mon 2026-10-26 | Presentation |

Cloud deployment stays the last engineering step, as in [D-005](#d-005-local-closeout-and-cloud-last).

The intermediate dates in this table were replaced on 2026-10-14 by [D-012](#d-012-24-hour-completion-rule). The presentation date and the Thu 2026-10-22 latest deployment and code freeze still apply.

## D-011 Development Log

Status: **Requested by the user on 2026-10-14 (Asia/Singapore)**: record the development process and the problems met in detail, as documents, to support the later project report.

Decision: every completion task adds or updates its entry under [docs/devlog](../devlog/README.md) in the same PR; cross-cutting events (reviews, re-planning, integration, incidents, deployment) get dated entries. The template, naming and rules are in that README. The log records facts and does not replace this decision record, the contracts or the evidence folder.

## D-012 24-Hour Completion Rule

Status: **Confirmed by the user (repository owner Liu Tongyao) in conversation on 2026-10-14 at 23:34 (Asia/Singapore)**, after a progress review of main `bd6709d`. Replaces the intermediate dates of [D-010](#d-010-delivery-schedule).

Context: #17, #18, #20 and #24 had no branch, PR or Issue comment since they were assigned on 2026-10-14 at 00:34 SGT, and their assignees' latest commits in the repository date from 2026-10-07. The owner considered taking these tasks over and decided against it; the owners in [D-007](#d-007-completion-task-ownership) stay as they are.

Decision:

- Every task is completed within 24 hours of its assignment.
- For tasks already assigned when the rule was adopted, the 24 hours count from 2026-10-14 23:34 SGT, so unblocked work is due **Thu 2026-10-15 23:00 SGT**.
- Work that depends on another task is due 24 hours after that task merges (for example the #21 and #22 remainders after #17, the #24 final pass after #17, #18 and #19, deployment after #18's first-admin bootstrap and the server access).
- Each Issue states its deadline in its body; [the plan Issue](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/27) holds the full list. Each owner was notified by an agent-labelled comment on the Issue.

Unchanged: the presentation on Mon 2026-10-26, deployment and code freeze on Thu 2026-10-22 at the latest, report and rehearsal days from Fri 2026-10-23 to Sun 2026-10-25, cloud deployment last ([D-005](#d-005-local-closeout-and-cloud-last)), and task ownership ([D-007](#d-007-completion-task-ownership)).

Limits: a deadline does not change contracts, merge permissions, checks, test benches or the Definition of Done, and a missed deadline does not move a task to someone else automatically; re-splits are recorded on the plan Issue as D-007 describes. The owner set this rule; other members' acceptance is shown by their own activity on the Issues and is not assumed here.

## UC05 Projection Contract Confirmation (2026-10-15)

Status: **Directly authorized by repository owner Liu Tongyao in conversation on 2026-10-15 (Asia/Singapore)**. The owner instructed Codex to complete Wang Yuanmeng's PR #12/#39 closeout and send the necessary comments on the owner's behalf, then explicitly transferred the pending UC05 overdue-projection contract-confirmation task from Zhou Fanhao to Liu Tongyao for delegated completion.

Source: [checkout-and-return contract](../contracts/checkout-and-return.md#uc05-overdue-projection) was a proposal awaiting the UC03 owner's confirmation. The owner's current instruction reassigns this specific confirmation task; it is not a takeover of UC03 implementation or other Issues. Wang Yuanmeng retains UC05 and DevSecOps ownership.

Confirmed scope: UC05 owns GET /api/v1/overdue; ACTIVE CUSTODIAN/ADMIN operators may read its global Loan/Reservation/User/Equipment projection, while other roles cannot. Membership is ACTIVE with persisted due_at strictly before the server Instant, excluding returned loans and equality. Rows contain loanId, reservationId, borrowerName, equipmentName, dueAt (UTC Instant) and overdueSeconds (whole seconds). Defaults are page 0, size 20 and duration order desc; page is 0-100000, size 1-100, asc reverses duration order, and loanId is the stable ascending tie-breaker. The response is content/totalElements/page/size; the UI displays Singapore time. Existing UC03 endpoints, Loan states, migrations, events and write/reminder lock semantics remain as contracted.

Codex performs and records the technical contract review under this delegation, and comments identify that agency. No Zhou Fanhao sign-off, personally executed human test, new mandatory approval gate, deployment or team Done is implied. Local checks, CI, delegated owner acceptance and member/deployment evidence remain separate.
