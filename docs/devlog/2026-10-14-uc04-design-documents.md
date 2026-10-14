# UC04 Analysis and Design Documents

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 9.1, UC04 / Proposal 5.6 · Branch: `docs/liu-tongyao-uc04-design` · Baseline: main `310dd505170ae783058fa7e1d38adb4a273a54f0`

## 2026-10-14 Documented the Implemented UC04

### What was built

Added [the UC04 document](../uc04-fault-and-maintenance.md) and six editable [Mermaid sources](../diagrams/): activity, analysis class, analysis sequence, design class, design sequence and states. Added one UC04 row to `docs/README.md`. The document covers actors/preconditions/flows, all 16 maintenance endpoints, policy predicates, service/domain/database enforcement, UC03 damaged returns, locks/versions, audit/history, events, report statistics, pattern choices, acceptance and every existing maintenance test/support class.

Delivery: [PR #35](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/35), targeting `main`; opened and left unmerged. Initial documentation commit: `3db67d662318611eddb594cfe40ca72714830cff`. The follow-up commit adds this delivery link only; the final PR HEAD and check statuses are reported in the handover.

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
| D9 | Case/queue/history UI shows equipment/user UUIDs as noted by #21; assignment dropdown already has names/email. `EquipmentHistoryLookup.tsx` says the shared catalogue API is unavailable although main has it and catalogue-selected fault reporting works | Original 310dd50 observation; resolved by #36 / 76a38ea for display names and #37 / ec57bbc for lookup wording and equipment-history links, including retired items |
| D10 | UC03 explicitly accepts expectedVersion, while UC04 controller/DTOs explicitly omit expectedVersion/If-Match even though responses expose version and repository updates compare it | Describe server-snapshot conflicts precisely; do not imply UC03's client concurrency contract applies to UC04 or invent a Proposal mandate for it |
| D11 | `MaintenanceCaseSnapshot` and service reject terminal work/mutations, require assignment for non-OPEN states and disallow all pre-terminal resolution data. V002 `check_maintenance`/CHECKs allow some same-status field edits, require assignment only for ASSIGNED/IN_PROGRESS, and allow a non-terminal resolution note when resolved_at is null. `check_loan` also does not independently enforce the no-maintenance issue rule | State which layer enforces each rule; no claim of complete database terminal immutability, role authorization or issue blocking |
| D12 | `TechnicianDirectoryPort.ensureTechnician` comment calls the Identity adapter future work, but `JdbcTechnicianDirectoryAdapter` already implements it on main | Resolved by #37 / ec57bbc: TechnicianDirectoryPort now describes implemented eligibility; MaintenanceHistoryRepository also identifies serms.audit_log storage |
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

## 2026-10-14 Synchronize design documents after PR #36

### Task, baseline and authorization

Liu Tongyao (@cpg25ncu) requested this conversation follow-up and confirmed Codex as the sole writer in the existing serms-uc04-design worktree on docs/liu-tongyao-uc04-design. Fetch confirmed [PR #36](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/36) was merged at 76a38eafabd74656cf90d032994339f29c970d8b and that origin/main contains functional commit 9ca0921bc5f32301cc0fa6722ff6733f211d8c2e. The fetched main baseline is 76a38eafabd74656cf90d032994339f29c970d8b.

The initial clean HEAD was 49ffa5cbf2cb7304da9b7a80e7ef64018f8453b7. A normal merge produced 149c0609e6f1be4b1c83f3a3a5e883b7c38ac478 with parents 49ffa5cbf2cb7304da9b7a80e7ef64018f8453b7 and 76a38eafabd74656cf90d032994339f29c970d8b, without conflicts. No rebase or force push was used. PR #35 remains a documentation delivery: eight new files plus the docs/README.md index row and one docs/testing.md workflow cell, with its baseline explanation. This follow-up edits only the UC04 document, design class/sequence sources, this log and the specifically authorized testing row/baseline explanation. UC04 application/frontend code, contracts, migrations and all other testing rows are excluded.

Rules and skills re-read: AGENTS.md, .github/CONTRIBUTING.md, .github/pull_request_template.md, .gitattributes, docs/collaboration/agent-workflow.md, ownership.md and decisions.md, docs/tech-stack.md, docs/testing.md, docs/devlog/README.md, relevant UC01/UC03 contract paths and docs/contracts/README.md, and .agents/skills/serms-worktree/SKILL.md, serms-verify/SKILL.md and serms-pr/SKILL.md. The user explicitly authorized the D8 correction in this follow-up. No subagents were used.

### Changes and discrepancy status

The description now covers the five required nullable name fields, ID fallbacks, the read-only MaintenanceNameDirectory / JdbcMaintenanceNameDirectory, immutable MaintenanceEquipmentLabel and MaintenanceNames, and the controller's batch projection only after secured service calls return authorized records. Each non-empty label kind uses one IN query. The design class and sequence include these dependencies and the secured queue read/name projection. The inventory includes JdbcMaintenanceNameDirectoryTest and the API, PostgreSQL, OpenAPI, wiring, component and E2E name assertions.

- **D9 display UUID portion: resolved by PR #36, merge commit 76a38ea** (full SHA above). Equipment, reporter, technician and history actor labels are displayed; Case ID and Loan ID remain explicit identifiers, with UUID fallback for unreadable labels. Unassigned cases have no technician name. The fault form and equipment lookup still take an equipment UUID.
- **D9 stale EquipmentHistoryLookup wording: resolved by PR #37 / ec57bbc.** Its equipment-history links and retired-item lookup are documented in the later dated update below; this PR authors no frontend correction.
- **D8: corrected in this PR.** The UC04 PostgreSQL integration workflow cell now describes Maintenance PostgreSQL acceptance, .github/workflows/maintenance-tests.yml, job maintenance-postgres, every PR and every main push, PostgreSQL 18 and -Puc04-postgres-it. Ordinary verify still does not enable that suite. Only that cell and the table's baseline explanation changed.
- **All other D items remain unchanged.** The preceding discrepancy table is retained as the original 310dd50 observation, with this dated section providing D8/D9 updates. D12 is subsequently resolved by PR #37 / ec57bbc, as recorded below.

### Verification

Checks used merge HEAD 149c0609e6f1be4b1c83f3a3a5e883b7c38ac478 with the scoped documentation changes, then collaboration/link/encoding/whitespace checks were repeated after this entry and at the delivered commit. The PR description identifies the final HEAD; the final commit only adds these documentation inputs. Windows PowerShell, Node v24.20.0, Mermaid CLI 12.0.0 and the existing Chromium headless shell were used. Temporary scripts/configuration and six SVG outputs are retained outside tracked files at D:/ChatGPT/SWE5006_Group_Project/.cache/sync-20261014/.

| Check | Result and evidence |
| --- | --- |
| All six UC04 Mermaid sources rendered again with mmdc and the temporary Puppeteer configuration | passed; six non-empty SVGs, including changed design class and sequence |
| node scripts/check-collaboration.mjs | passed; 19 documents and 7 skills |
| git diff --check and staged whitespace check | passed |
| Temporary verify-docs.mjs: local links/anchors, case, UTF-8 without BOM, LF, replacement characters, trailing whitespace and test inventory | passed; 10 delivery files, 87 local links before this entry; final counts are recorded in the PR description |
| Backend/frontend/PostgreSQL behavior suites for this documentation delivery | not run; merged main code is described, not newly altered or independently accepted here |
| Latest-HEAD hosted CI | separate from local checks; final SHA and actual GitHub results are recorded in the updated PR description |
| Member check, PR merge, deployment and team Done | not run; not claimed by agent documentation checks |

Codex updated these documents under Liu's instruction. No PR was merged, no other person's PR was commented on, and no shared runtime service or protected port was touched. Historical passes above remain tied to their original versions. PR #35 is for review, with remaining Issue #21 category/priority work separate from the now-resolved D9/D12 items.

## 2026-10-14 Refresh the baseline after PR #37

Liu's additional instruction supersedes the 76a38ea baseline for final delivery. Fetch confirmed [PR #37](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/37) MERGED at ec57bbccb8e4c512520e1f23fe2e724d294ec321; latest origin/main is that same SHA and contains #36 / 76a38ea. A normal conflict-free merge created 684ffaffac3bd4451750e3ce970b9e6e5399cc64, with parents 624ed6a78c16752d336f5e31192d108b21a0dde5 and ec57bbccb8e4c512520e1f23fe2e724d294ec321. The final PR description identifies the subsequent documentation HEAD.

**D9 is fully resolved:** #36 / 76a38ea supplies required nullable display labels and UUID fallbacks; #37 / ec57bbc removes the obsolete catalogue-unavailable wording and links operator case-card/detail equipment labels to /maintenance?history=<id>. The workspace validates the UUID, initializes and scrolls to the direct maintenance lookup, covering retired equipment without relying on catalogue inclusion. The form/manual lookup still takes UUIDs; MAINTAINER-only users gain no operator history access. There is no remaining D9 wording assignment to Issue #21.

**D12 is resolved by #37 / ec57bbc:** TechnicianDirectoryPort describes existing ACTIVE MAINTAINER validation, and MaintenanceHistoryRepository now describes logical activity backed by serms.audit_log. The D9/D12 table treatments above reflect these resolutions. D8 remains corrected in this PR, with only its authorized testing workflow cell and baseline explanation changed. All other D items remain unchanged.

The UC04 document and test inventory now describe these final behaviors. All six diagrams were rerendered again after this baseline refresh; collaboration, whitespace, local links/case/anchors, UTF-8 without BOM and LF checks passed on the refreshed documentation inputs and were repeated at final HEAD. Final link counts and latest-HEAD CI conclusions are recorded in #35's updated description. The earlier 624ed6a HEAD passed all 12 hosted checks, but that result is historical and does not establish CI for this refreshed HEAD.

Task owner, sole writer, worktree, allowed paths, exclusions and rules/skills are unchanged from the preceding session record. Application/frontend changes were received only through the authorized main merge; no UC04 source edits were authored by this writer. No PR merge, other-PR comment, member check, deployment or team Done is claimed. Codex wrote this update under Liu's instruction.
