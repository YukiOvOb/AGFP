# Release Preparation Evidence

The final candidate includes the user-authorized Boot 4 migration and the PR #11/#14 integration dependency. See [security upgrade rationale and versions](../boot4-security-upgrade.md) and [D-006](../collaboration/decisions.md#d-006-spring-boot-4-security-migration). The original built-image failure on `795d68e` remains recorded; the final image scan and Compose smoke must pass on the updated delivery SHA before release.

Issue #10, owner/executor Liu Tongyao; agent-assisted preparation on 2026-10-13 (Asia/Singapore). Base main `940bf42cd1b6b507d24179e8aa4065af14f72a0e`. One writer on `codex/serms-release-preparation`; original dirty maintenance checkout preserved.

Rules/skills read: `AGENTS.md`, `.github/CONTRIBUTING.md`, `docs/collaboration/{ownership,agent-workflow,decisions}.md`, `docs/testing.md`, `docs/contracts/{README,equipment-and-reservation,checkout-and-return}.md`, and `.agents/skills/{serms-task,serms-worktree,serms-verify,serms-pr}/SKILL.md`. This session's user explicitly chose local acceptance and release preparation while server handover is unavailable.

| Check | Result / input |
| --- | --- |
| `node --test scripts/verify-release.test.mjs` | Passed, 6 tests; wrong refs/events, malformed/old SHA, wrong check producer, missing/running/skipped/failing checks, latest failed rerun |
| `bash -n scripts/deploy.sh` | Passed with Git Bash; no server execution |
| `bash scripts/test-deploy.sh` | Passed, 7 simulated cases: occupied lock, dirty checkout, wrong volume, backup failure, build failure, unhealthy response, success. Fake Git/Docker/curl/flock; **not** container/server acceptance |
| `node scripts/check-collaboration.mjs` | Passed; 19 documents / 7 skills |
| Production Compose parsing and image builds/scans | Added hosted `release-config` / `release-images` checks; no local Docker engine is installed |
| SSH, inventory, restore rehearsal, public HTTPS/login/UC flows | Blocked by actual handover; user confirmed this explicitly |

Local toolchain: Windows, Node 24.19.0 and Git Bash. Existing eight required branch checks and protections remain unchanged. The extra checks gate **release**, not a new human approving-review requirement. The draft must retain missing hosted image/runtime and member acceptance until verified. No merge, remote comments, server execution, volume/data deletion, secret updates, account provisioning or team Done claim.

Implementation replaces static-only packaging with existing React/backend/PostgreSQL 18; server-owned environment and external volume are required. The [handover/release runbook](../server-release.md) contains first-transition prerequisites, backup limitations, recovery and actual acceptance.

## Boot 4 Combined Regression

The candidate includes `bbb1902b705efd4e937abcfbfe0b12404fd683d4` (#14) and its #11 dependency. Boot 4.0.8, Framework 7.0.9, Springdoc 3.0.3, Jackson 3.1.7/2.21.7 and Tomcat 11.0.27 retain Java 17 and the business contracts. The MVC/security test starter and explicit web security configuration repair Boot 4 slice wiring; no security or assertion is removed.

Local combined regression: existing backend 536, database 27 and UC03 20 passed with no failures/errors/skips; frontend actual OpenAPI alignment passed. Final UC04/frontend/live API/browser checks and hosted image/Compose evidence are recorded separately in the PR on the final SHA. Earlier slice-wiring failures are retained in ignored logs. Boot 4 migration requires the final combined CI; prior Boot 3 results alone do not establish compatibility.

Final local [Boot 4 acceptance](boot4/verification.md): UC04 28, frontend components 87, mocked E2E 12, real cross-module API 28, UC01/UC04 API 24, real desktop/mobile browser 10 and demo-data guards 3 all passed. Latest-HEAD hosted scans/production-profile Compose smoke are separate gates.
