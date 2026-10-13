# Release Preparation Evidence

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
