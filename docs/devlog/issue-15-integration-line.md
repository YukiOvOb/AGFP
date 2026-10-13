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
