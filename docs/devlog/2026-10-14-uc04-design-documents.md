# UC04 Analysis and Design Documents

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 9.1, UC04 / Proposal 5.6 · Branch: `docs/liu-tongyao-uc04-design` · Baseline: main `310dd505170ae783058fa7e1d38adb4a273a54f0`

## 2026-10-14 Documented the Implemented UC04

### What was built

Added [the UC04 document](../uc04-fault-and-maintenance.md) and six editable [Mermaid sources](../diagrams/): activity, analysis class, analysis sequence, design class, design sequence and states. Added one UC04 row to `docs/README.md`. The document covers actors/preconditions/flows, all 16 maintenance endpoints, policy predicates, service/domain/database enforcement, UC03 damaged returns, locks/versions, audit/history, events, report statistics, pattern choices, acceptance and every existing maintenance test/support class.

This is the user's conversation task, not delivery or closure of Issue #21. Only these eight new files and the one index row are authorized. Application/frontend/scripts/workflows/migrations/contracts are excluded. No runtime services were started or stopped, including the primary checkout's ports 5174, 8082, 55433 and 55441.

Sole writer: Codex in `C:/Users/14824/.codex/worktrees/serms-uc04-design/SWE5006_Group_Project`. The primary checkout stayed on `feature/liu-tongyao-uc04-readable-names` at the initial `310dd50`; its Issue #21 files and local Proposal copy were read without changes. `git fetch origin main` succeeded on 2026-10-14 before the managed worktree/new branch were created. Document checks initially use baseline HEAD plus these nine explicitly listed uncommitted inputs; delivery commit/PR identify the final version.

### Design notes

The analysis models use boundary/control/entity responsibilities and refine them into the actual REST controller, public services, aggregate/state machine, ports, JDBC adapters and PostgreSQL design. Fault report fields remain in MaintenanceCase. The design sequence records separate assign/start/resolve transactions and work entries; it does not introduce a client version contract or a post-resolution close operation.

- **State:** `MaintenanceCase` delegates to `MaintenanceStateMachine`; this is the implemented switch-based transition table, not a concrete State class hierarchy. D-008 maps the five states to the Proposal's six.
- **Observer:** `SpringMaintenanceEventPublisher` publishes `MaintenanceAssigned` / `MaintenanceCompleted` before commit. Draft PR #12's `MaintenanceNotificationListener` is labelled unmerged throughout; main has no production subscriber for these events.
- **Adapter:** `EquipmentMaintenancePort`, `TechnicianDirectoryPort`, `MaintenanceLoanPort` and their JDBC adapters isolate cross-module persistence access. Case/history repositories isolate aggregate/audit persistence.
- **Read model:** `MaintenanceReportService`, `MaintenanceReportRepository`, `JdbcMaintenanceReportRepository` and `MaintenanceReportSnapshot` calculate read-only snapshot statistics independently of mutation. The central report module on main already reuses the public service after PR #33.

Sources actually read:

- Rules/skills: `AGENTS.md`, `.github/CONTRIBUTING.md`, `.github/pull_request_template.md`, `.gitattributes`, `docs/devlog/README.md`, `docs/collaboration/{agent-workflow,ownership,decisions}.md`, `docs/{testing,tech-stack}.md`, `.agents/skills/{serms-worktree,serms-verify,serms-pr}/SKILL.md`. The installed `documents/SKILL.md` was consulted for reading the local DOCX; this task authors no DOCX and makes no layout claim about that reference.
- Contracts/context: `docs/contracts/{README,checkout-and-return,equipment-and-reservation,auth-and-approval}.md`, `docs/uc03-checkout-and-return.md`, existing `uc03-*.mmd` and `uc01-activity.mmd`, `docs/evidence/uc01-uc04/verification.md`, `docs/devlog/issue-23-reports.md`, the project review log, and database-alignment/Sprint-domain context.
- Implementation: every file under `app/src/main/java/sg/edu/nus/serms/maintenance/` at the baseline, the matching test inventory/assertions, all `frontend/src/features/maintenance/` files, `frontend/e2e/maintenance.spec.ts`, `frontend/playwright.config.ts`, `loan/service/LoanService.java`, `report/service/ReportService.java`, `shared/api/GlobalExceptionHandler.java`, V002/V004 migrations, `.github/workflows/maintenance-tests.yml`, `app/pom.xml` and `scripts/check-collaboration.mjs`.
- Local Proposal: `D:/ChatGPT/SWE5006_Group_Project/项目提案.docx`, read-only OOXML text extraction; SHA-256 `F4DAB58A166710309D1800CA5F117EDEFEE3460A410FC8E220927A3045E8EB3D`. Sections 5.6, 5.7, 6, 9.1-9.3 were checked. This untracked input is not copied into the worktree/commit.
- GitHub: Issues [#21](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/21) and [#17](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/17) were OPEN on 2026-10-14; [PR #12](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/12) was OPEN/Draft, HEAD `b93734b23398d065553b3942f8cfe1f09e1f6cd0`. Its `MaintenanceNotificationListener.java` was read at that fixed SHA to verify technician assignment notices and reporter completion notices.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Primary checkout already contained another writer's maintenance files | Issue #21 is active concurrently | Managed worktree and requested new branch from fetched main; exclude all code/contract/migration edits | Compare allowed file list and scoped Git diff before commit |
| Sandbox fetch could not write `.git/FETCH_HEAD`; initial GitHub reads could not connect | Filesystem/network restrictions | Retry required operations with explicit tool escalation | Fetch completed; `gh` returned Issue/PR metadata and fixed-SHA listener source |
| No Mermaid CLI command was initially installed; initial npx lookup failed ENOTFOUND | CLI absent and sandbox registry network blocked | Escalated temporary CLI download and skipped Puppeteer's browser download | CLI reported version 12.0.0 |
| All six initial renders failed with `spawn UNKNOWN`; an Edge retry exited before browser connection | Existing full Chromium launcher could not spawn in this execution environment; Edge did not expose a usable Puppeteer connection | Use the already installed Chromium headless-shell executable in temporary Puppeteer configuration | Headless-shell state-diagram retry passed, then the complete set was rendered |
| Design sequence failed parsing at its first multi-part note | Semicolons are sequence-statement separators in Mermaid, including in these note/message texts | Replace prose semicolons with commas in that source | Design sequence rerender passed; the other five sources had already rendered successfully |
| Existing documents overstate or lag parts of the implementation | Historical snapshots and pending integration work | Cite the fixed main baseline and enumerate differences below; keep excluded source files intact | Read code, SQL, Proposal text, current workflow and draft listener |

### Verification

Environment: Windows PowerShell, Node `v24.20.0`, GitHub CLI `2.102.0`, Mermaid CLI `12.0.0`; existing Playwright Chromium headless shell from `chromium_headless_shell-1243`. The npm toolchain is temporary and adds no repository dependency or lockfile. Render outputs/configuration and authoring/check helpers stay outside the Git worktree.

| Command or check | Inputs / result |
| --- | --- |
| `npx --yes --package @mermaid-js/mermaid-cli mmdc --version` | Initial sandbox registry lookup failed ENOTFOUND; escalated retry passed, version 12.0.0 |
| `npx --yes --package @mermaid-js/mermaid-cli mmdc -p <temporary puppeteer config> -i docs/diagrams/uc04-<model>.mmd -o <temporary output>.svg` for each of six models | Passed: all six sources rendered to non-empty SVGs. Initial browser failures and one sequence parse failure were repaired as above; final diagram inputs match the committed sources |
| `node scripts/check-collaboration.mjs` (worktree root) | Passed: 19 documents and 7 skills; baseline HEAD plus the authorized document inputs |
| `git diff --check` (worktree root) | Passed; staged new-file whitespace and the final delivery are additionally checked before/after commit |
| `node <temporary verify-docs.mjs> <worktree>` | Passed: all 9 delivery files UTF-8 without BOM/LF, no trailing whitespace, 76 local links with case/heading checks, all 21 backend test/support sources present in the inventory |
| Backend/frontend/real PostgreSQL/live browser behavior suites | Not run: documentation-only delivery; code/fixtures are unchanged and the primary services belong to another session. Existing suite coverage is described, not a new pass |
| Hosted CI, member check, merge, deployment, team Done | Not performed by this local document verification. Latest PR HEAD CI and any later acceptance are separate records |

Historical `docs/evidence/uc01-uc04/verification.md` passes belong to its original baseline/dependency inputs. They are linked as earlier evidence, not claimed for this documentation branch. No test, approval, hours or meeting evidence was invented.

### Deviations

These differences were observed, not resolved in source policies or implementation by this PR. Rows D1-D5 concern the Proposal; D6-D13 concern existing documents/comments or enforcement layers.

| ID | Sources and observed difference | Treatment / follow-up |
| --- | --- | --- |
| D1 | Proposal 5.6 has Reported/Assigned/In Progress/Resolved/Not Repairable/Closed; code/V002 have OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/UNREPAIRABLE. Proposal 5.2 has Reserved/Issued equipment labels; code uses time-window reservations and ON_LOAN. `docs/contracts/auth-and-approval.md` section 9 still says pending, and `docs/tech-stack.md` retains pending/six-step wording despite confirmed D-008 | Explain D-008's mappings without introducing CLOSED/RESERVED. #21/#17 own the source-wording follow-up |
| D2 | Proposal 5.6 requires fault category and priority; baseline aggregate, API/form and V002 lack both | Mark #21's V006 after #17's V005 as pending, including UC03 defaults; do not present the parallel writer's work as main |
| D3 | Proposal 5.6 says successful repair returns Available and unrepairable equipment may become Retired. Actual resolution preserves RETIRED/other active cases/ACTIVE loans; `markUnrepairable` always retires. Proposal rule 7's damaged-return maintenance statement also has UC03's already-RETIRED receipt exception | Document actual precedence and both receipt paths; no rule/migration change |
| D4 | Proposal 5.7 expects maintenance assignment/completion notices; main publishes those events but has no production maintenance listener. PR #12 adds one and remains Draft | Name actual draft consumer/recipients and fixed SHA; notification delivery is pending |
| D5 | Proposal 9.2 lists Fault Report and Maintenance Case separately; main stores fault information inside `maintenance_case` / `MaintenanceCase`, with no separate FaultReport model/table | Reflect the combined implementation in analysis/design models; do not add an entity |
| D6 | `docs/tech-stack.md` pattern table describes domain state classes, whereas maintenance uses one switch-based `MaintenanceStateMachine` plus aggregate/snapshot validation | Name the implemented variant and its limits; do not claim five concrete State classes |
| D7 | `docs/tech-stack.md` says notification/audit modules subscribe to domain events. UC04 audit is directly appended by `MaintenanceService` through its history repository, while maintenance notification subscription is still draft | Describe direct transactional history and draft Observer consumer separately |
| D8 | `docs/testing.md` says UC04 real PostgreSQL tests are not enabled by current CI; main `.github/workflows/maintenance-tests.yml` explicitly runs `MaintenancePostgresIT` in `maintenance-postgres` with PostgreSQL 18/profile `uc04-postgres-it`. Ordinary Maven verify still does not enable the suite by default | Cite the current workflow; preserve testing.md for its owner to update |
| D9 | Case/queue/history UI shows equipment/user UUIDs as noted by #21; assignment dropdown already has names/email. `EquipmentHistoryLookup.tsx` says the shared catalogue API is unavailable although main has it and catalogue-selected fault reporting works | Distinguish current directory labels from #21's pending case labels; record stale lookup prose without changing frontend |
| D10 | UC03 explicitly accepts expectedVersion, while UC04 controller/DTOs explicitly omit expectedVersion/If-Match even though responses expose version and repository updates compare it | Describe server-snapshot conflicts precisely; do not imply UC03's client concurrency contract applies to UC04 or invent a Proposal mandate for it |
| D11 | `MaintenanceCaseSnapshot` and service reject terminal work/mutations, require assignment for non-OPEN states and disallow all pre-terminal resolution data. V002 `check_maintenance`/CHECKs allow some same-status field edits, require assignment only for ASSIGNED/IN_PROGRESS, and allow a non-terminal resolution note when resolved_at is null. `check_loan` also does not independently enforce the no-maintenance issue rule | State which layer enforces each rule; no claim of complete database terminal immutability, role authorization or issue blocking |
| D12 | `TechnicianDirectoryPort.ensureTechnician` comment calls the Identity adapter future work, but `JdbcTechnicianDirectoryAdapter` already implements it on main | Use the actual adapter in the design model; leave code comment correction to code owners |
| D13 | `docs/tech-stack.md` section 2.4 says list endpoints share page/size/sort parameters, but UC04 maintenance list endpoints return unpaginated arrays and accept none of those parameters | Describe the actual UC04 APIs; no pagination contract/implementation is added by this documentation task |

The dated baseline in historical verification and database-alignment records is retained as history; it is not silently converted to a current pass. No substantive contract or permission conflict is changed by this explanatory task.

### Follow-ups

- Liu Tongyao: review the new analysis/design material for the course report; complete #21 category/priority/names and its contract wording in the separate writing session.
- Zhou Fanhao and Liu Tongyao: #17/V005 before #21/V006; agree defaults for both UC03 damaged-return paths.
- Wang Yuanmeng and affected owners: finish confirmation/member checks for PR #12; main notification behavior must be rechecked after that PR merges.
- Respective documentation/code owners: reconcile the stale wording and the identified layer/contract differences in separately scoped work.
- PR delivery is authorized; merging is explicitly excluded. No deployment/member acceptance or team Done is claimed.

### Authorship

The UC04 document, six diagram sources, index row and this entry were written by Codex under Liu Tongyao's instruction. Existing implementation/test authorship is unchanged. No teammate review, approval or personally recorded effort is claimed.
