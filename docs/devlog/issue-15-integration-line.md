# Land the Integration Line on main (#15)

Owner: Liu Tongyao (@cpg25ncu) · Requirement: shared integration, prerequisite of every completion task · Branch: `codex/local-repository-closeout` (documentation) · Baseline: main `940bf42`

## 2026-10-14 Preparation and first merge attempt

### What was done

- Committed the 2026-10-14 decisions and the development log locally (`a0e10c3`).
- Read the actual protection of `main` instead of relying on documents: pull requests are required with zero approving reviews, eight status checks are required, **the branch must be up to date with `main` before merging** (strict mode), and the rules apply to administrators.
- Confirmed the chain is linear: `940bf42` (main) is an ancestor of #11 `d42e0a6`, which is an ancestor of #14 `bbb1902`, which is an ancestor of #13 `15ec983`. All hosted checks were green on the three heads (9, 10 and 12 checks).
- Confirmed the repository has no Actions secrets and no variables, so a push to `main` cannot reach the server. The last `Deploy` run failed after about five seconds with `Error: missing server host`.
- Marked #11 ready for review.
- Pre-resolved the documentation conflicts between the local closeout commits and #13 by merging `15ec983` into the documentation branch locally (`a9fc9c4`). Three files conflicted: `README.md`, `docs/tech-stack.md`, `docs/collaboration/decisions.md`.

### Design notes

Merge order follows the stack (#11, #14, #13), each with a merge commit, so every PR is recorded as merged and branches already based on `15ec983` stay valid. Because of strict mode, #14 and #13 must each be retargeted to `main` and updated with it after the previous merge, which produces a new head and a new CI run (about three to four minutes each). Merging only #13 after retargeting would have been one step, but it would leave #14 closed rather than merged and was not what the task agreed.

Conflict resolution rule for documents: versions and code-facing statements come from #13 (Spring Boot 4.0.x, springdoc 3.0.x, JUnit Jupiter 6); implemented-status wording comes from the closeout, because #13's side of those table rows still carried older "pending" text; the decision record keeps D-005, D-006 and D-007 to D-011 in order. After the merge the tree differs from `15ec983` only in documents and `.gitignore`.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The agent's `gh pr merge 11` was refused before it ran | The session's permission classifier blocks an agent from merging a pull request that no human has reviewed | Not worked around. The merge commands are handed to the repository owner to run; the agent prepares everything around them | #11 still open and `main` still `940bf42` after the refusal |
| `git show <ref>:.github/...` failed in Git Bash with "ambiguous argument" | MSYS path conversion rewrites `ref:.path` arguments as Windows path lists | Run with `MSYS_NO_PATHCONV=1` | Command returned the workflow file |
| `node` was not on PATH, so `scripts/check-collaboration.mjs` could not run | Node.js is not installed system-wide on this machine; only a tool-bundled copy exists | Used the bundled Node 24.14.0 binary by full path | "Collaboration documentation passed: 19 documents, 7 skills" |
| #13's side of `docs/tech-stack.md` described REST, ProblemDetail and JSON login as pending | That branch edited the version columns on top of older status text | Kept #13's versions and the closeout's implemented statuses | Rows read back; no `3.5.x`, `JUnit 5 ·` or `springdoc 2.x` left in README or tech-stack |

### Verification

- `node scripts/check-collaboration.mjs` on `a9fc9c4`: passed, 19 documents and 7 skills.
- `git diff --stat origin/codex/serms-release-preparation` on the merged tree: 13 files, all documents plus `.gitignore`; no application, script, workflow or migration file differs.
- Not run: local demo smoke from the new `main` (there is no new `main` yet).

### Deviations

The repository still exposes the old server address, SSH port and login user in the comment header of `.github/workflows/deploy.yml` on `main`. #13 replaces that file, but the values stay in public history. Raised with the owner for the server handover.

### Follow-ups

- Owner runs the three merges; between them the PR is retargeted, updated and re-checked.
- Then: push this branch, open the documentation PR, close #3 as superseded, announce the new baseline on #27, notify the #12 author.
- Record merge SHAs and the `Deploy` results on `main` here.

### Authorship

Preparation, conflict resolution and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. Merges are performed by the repository owner. No teammate review is claimed.

## 2026-10-14 First merge landed; #14 prepared

### What was done

- The owner merged #11 at 2026-10-13T17:34:55Z (UTC). `main` moved from `940bf42` to merge commit `f077ce7`.
- Runs on `f077ce7`: CI, Collaboration documentation, Sprint 1 database, Notification PostgreSQL contract, Zhou Fanhao UC03 integration, and Notification and default PostgreSQL tests succeeded; `Deploy` failed as expected (no server secrets); Frontend and API contract was still running when read.
- #14 was retargeted from `codex/serms-local-demo` to `main` and updated with `main`. Its head moved from `bbb1902` to update-merge `962cc44`; the comparison `bbb1902...962cc44` shows zero changed files, so the tested content is unchanged.
- All 10 checks passed on `962cc44`. #14 was marked ready for review and is mergeable.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The agent's second merge attempt (#14) was refused, although the owner had asked for the agent to merge | Same permission classifier; a request in conversation does not lift it | The owner runs the merge, or adds an allow rule to their own Claude Code settings | #14 still open after the refusal |
| The agent could not add that allow rule when asked to | Changing its own permissions is blocked as self-modification | Left to the owner; the agent did not edit the settings file by another route | No settings file was created |
| `gh` is not on the agent shell's PATH | The CLI was installed after the desktop app started, so the shell kept the old PATH | Called by full path; a restart of the app picks up the new PATH | `which gh` fails; full-path calls work |

### Follow-ups

- Owner merges #14; then #13 is retargeted, updated, re-checked and merged the same way.

## 2026-10-14 Integration line on main

### What was done

- The owner merged #13 at 2026-10-13T17:45:38Z (UTC). Its base was still the branch of #14, so the merge (`db376ab`) landed #13 inside #14 rather than #14 in `main`. #14 then carried the whole remaining chain.
- `db376ab` already contained `main` (`f077ce7`), so no further update was needed. The comparison `15ec983...db376ab` shows zero changed files. All twelve checks passed on `db376ab`, including `release-config` and `release-images`.
- The owner added an allow rule for `gh pr merge` to their own Claude Code settings and restarted the app. The agent then merged #14 at 2026-10-13T17:48:54Z (UTC): `main` is `b3def12`.
- `main` `b3def12` and `15ec983` have identical content (zero changed files in the comparison).
- Runs on `b3def12`: CI, Collaboration documentation, Notification PostgreSQL contract, Sprint 1 database, Maintenance PostgreSQL acceptance, Notification and default PostgreSQL tests, and Zhou Fanhao UC03 integration succeeded; Release preparation and Frontend and API contract were still running when read. `Deploy` failed at the `release-gate` job (`SERMS_PRODUCTION_READY` was empty, `true` expected) and the `deploy` job was skipped.
- Merged the new `main` into the documentation branch without conflicts and updated the progress statements in README, tech-stack, testing, backlog, the documentation index and sprint-status (new "Integration Update 2026-10-14" section; the 2026-10-13 snapshot is kept).

### Design notes

The planned order was #11, #14, #13. The actual order was #11, #13 into #14, then #14. The result is the same content on `main` with one update-merge fewer; all three pull requests are recorded as merged. Because #13 was merged into a branch and not into `main`, its page shows `codex/serms-issue-acceptance` as the base; the commits reached `main` through #14.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| #14 was reported as merged but was still open, and `main` had not moved | #13 was merged instead; it targeted the branch of #14 because stacked pull requests keep their original base until retargeted | Checked the real state of both pull requests and the branch heads before acting; waited for the new head of #14 to pass, then merged #14 | `gh pr view` for #13 and #14, branch heads, and the file comparison against `15ec983` |
| A shell heredoc containing an unpaired apostrophe failed to parse in the agent tool | The tool parses the command text before the shell does | Wrote the script to a file and ran the file | Script ran and reported each updated document |

### Verification

- `gh api compare 15ec983...b3def12`: zero changed files.
- Hosted checks: twelve passed on `db376ab` before the merge.
- `node scripts/check-collaboration.mjs` on the documentation branch after the updates: passed, 19 documents and 7 skills.
- Not run: local demo start and `scripts/smoke-local.mjs` on `b3def12`. The content is the one that passed the production-profile Compose smoke in `release-images`; a local run is still owed and is listed on #15.

### Follow-ups

- Documentation pull request for this branch; close #3 once it is merged.
- Announce `b3def12` as the baseline on #27 and tell the author of #12.
- Local demo smoke on the new `main`.

## 2026-10-14 Local demo smoke on the new main

### What was done

The owner installed Node.js 24, pnpm 11.19.0 and PostgreSQL 18. The local demo was started from `main` at `457a2a9` (the documentation merge on top of `b3def12`; same application code) and every local check ran against it before any other change:

| Check | Result |
| --- | --- |
| `node scripts/smoke-local.mjs` | Passed, 28 checks |
| `scripts/check-local-data.ps1` | Passed, 3 checks |
| `node scripts/check-uc01-uc04.mjs` | Passed, 24 checks |
| `node scripts/check-local-browser.mjs` | Passed, 6 checks |
| `node scripts/check-uc01-uc04-browser.mjs` | Passed, 10 checks (desktop and mobile) |

Documentation reached `main` with #28 (`457a2a9`), and #3 is closed as superseded. This completes the acceptance items of #15.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| The demo backend would not start: "Found more than one migration with version 001" | Stale resources from an older branch build in `app/target/classes` were packaged because the build did not clean first | `mvnw clean package`; a separate task changes the launcher | Flyway applied V001-V004 and health returned `UP` |
| Newly installed tools were not found by the agent shell | The desktop app keeps the PATH it started with | Prefixed the Node directory per command and passed the PostgreSQL `bin` path to the launcher | Versions printed: Node 24.20.0, pnpm 11.19.0, PostgreSQL 18.6 |

### Authorship

Run and recorded by an AI agent (Claude Code) under Liu Tongyao's instruction. Member acceptance of #6-#9 remains separate.
