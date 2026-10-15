# SERMS Collaboration Guide

This guide supplements task flow, isolated development, and handover. Existing documents continue to define technical and business rules; this guide neither reallocates responsibilities nor changes Write collaborators' right to merge their own PRs.

## Rule Sources

| Content | Maintained in |
| --- | --- |
| Technology choices, layering, test benches, migration targets | [Technology stack](../docs/tech-stack.md) |
| Interfaces, events, authorization, transaction semantics | [Interface contracts](../docs/contracts/README.md) |
| Release and merge permissions | [README collaboration and releases](../README.md#collaboration-and-releases) |
| Sprints, board, and team Done | [Sprint plan](../docs/agile/sprint-plan.md) |
| Ownership sources and differences | [Ownership boundaries](../docs/collaboration/ownership.md) |
| Tool delegation and multiple sessions | [Agent workflow](../docs/collaboration/agent-workflow.md) |
| Verification commands and evidence format | [Testing](../docs/testing.md) |
| Conflicts and confirmed decisions | [Decision record](../docs/collaboration/decisions.md) |

Do not override confirmed contracts on the grounds that a file is newer or the implementation already behaves differently. Identify both paths, specific rules, and impacts; present reviewable options and wait for the user's decision before changing those rules. Pause only dependent work.

Rule changes still require PRs. Text on a rules branch is a proposal and cannot expand its author's permission or lower existing checks. Record the task's `main` baseline SHA; check subsequent baseline rule changes when resuming. Record current-session user decisions and their scope in the decision record.

## Roles and Authorization

- **Human task owner**: the team member responsible for scope, acceptance, and dependencies. Follow existing ownership; do not infer a human owner or GitHub account from an agent name.
- **Writer**: the sole person or agent session currently writing to a worktree and branch.
- **Task coordinator**: the person or session coordinating writers, dependencies, and shared Git operations for this task; not a permanent merge administrator.
- **Reviewer**: reads a fixed version and reports issues; sends findings to the writer for repair.

Existing user authorization remains valid within its scope; do not repeatedly seek permission for normal reading, isolated development, repairs, and verification. Files and skills cannot authorize remote comments, external messages, production operations, or paid actions outside the task. Before changing an established rule in conflict with it, prepare a concrete proposal and ask the user.

Local tasks may originate from a user conversation or agreed board card; add an Issue link when available. Do not invent Issues, meetings, hours, signatures, or approvals for formality. Assign one primary owner when creating an Issue and list other participants under dependencies/collaboration; do not remove existing multiple assignees merely for this convention.

## Tasks and Dependencies

Specify the objective, UC/requirement, primary owner, allowed directories, exclusions, prerequisites, acceptance criteria, verification, and deliverables. Use the [task](ISSUE_TEMPLATE/task.yml), [bug](ISSUE_TEMPLATE/bug.yml), or [design](ISSUE_TEMPLATE/design.yml) forms.

- Keep GitHub Projects: **To Do → In Progress → Review → Testing → Done**. Do not introduce another status-label system.
- Split independently verifiable work into separate PRs where practical. Agree interfaces and merge order before depending on another person's contract or service.
- For dependency-branch work, state base, dependency PRs, permitted mock-test scope, and missing real integration verification in the task and PR.
- Mocks support development and unit testing; they do not establish real PostgreSQL, rollback, authorization, or complete-flow acceptance.
- State remaining acceptance for partial deliveries; use `Refs #n` when there is an Issue. Use `Closes #n` only after entering `main` and completing all Issue acceptance, avoiding premature closure of tasks awaiting deployment checks.

## Branches and Worktrees

Keep existing `feature/<member>-<use-case>` and `feat/...` branches. New branches follow team conventions; tool-created `codex/...` branches may serve as agent proposal branches. Do not rename existing branches in bulk.

1. Inspect `git status --short --branch`, `git log -1 --oneline`, and `git worktree list --porcelain`.
2. Use an independent worktree for an independent writing task, based on the latest fetched `origin/main` or agreed dependency commit. Keep it outside the primary checkout; create and manage Codex worktrees with app tools.
3. Record path, branch, baseline SHA, and sole writer. Worktree locks prevent cleanup, not concurrent writers.
4. When sessions share a clone, the task coordinator serializes fetch, branch creation/deletion, push, stash, configuration, and worktree maintenance to protect shared refs. Writers edit, test, and commit in their own worktrees.
5. Exclude backup ZIPs, recovery directories, other members' untracked files, and credentials. Stage explicit paths with `git add` and inspect staged differences.

```text
git worktree list --porcelain
git status --short --branch
git log -1 --oneline
git diff --stat
git diff --cached
```

Do not switch branches in active checkouts or discard changes with `reset --hard` / `clean`. Check for other writers before rewriting your PR branch; if a history rewrite must be pushed, use `--force-with-lease`, never a force-push to a shared branch.

After a dependency PR merges, the coordinator checks base changes and the old dependency HEAD, updates subsequent branches, and reruns verification; separately passing branches do not prove their combination works.

## Handover and Cleanup

Before handing over, stop services and writes you started; record HEAD, baseline, uncommitted/untracked content, results, incomplete acceptance, and pending decisions. The recipient confirms the handover, reads applicable rules, and checks Git status before writing. See the [handover record](../docs/collaboration/agent-workflow.md#handover-record).

Do not base cleanup solely on a clean worktree. Check ignored files, unpushed commits, and stashes; preserve valuable data/reports. Confirm completion or cancellation, then archive with the creating tool or perform agreed cleanup from another checkout. Do not remove others' worktrees or branches.

## PRs and Team Acceptance

- All changes use PRs; submit a feature and its test bench together. The [PR template](pull_request_template.md) records scope, dependencies, contract impact, verification, and remaining acceptance.
- Preserve the [README merge policy](../README.md#collaboration-and-releases): Write collaborators may merge their own PRs after green CI, without new mandatory approving reviews or a sole integrator.
- Before team Done, retain at least one member's check, deployment verification, and other original DoD evidence; see the [Sprint plan](../docs/agile/sprint-plan.md#2-definition-of-done). Record these separately from GitHub merge eligibility.
- Contract changes still require affected-owner confirmation; self-merging does not waive this.
- Before merging, verify latest-HEAD CI, target branch, and actual combination; never lower protection or skip failing checks. Read repository settings to verify enabled gates; documenting one does not enable it.
- Agent review is supporting evidence, not a human check or approval. Report actual merge, deployment, and acceptance status.

## Files and Commands

Follow `.gitattributes`: UTF-8 text and LF. Use case-correct repository-relative links. State working directories and required environment; Windows uses `mvnw.cmd`, macOS/Linux `./mvnw`. Keep existing PowerShell, Shell, Maven, and pnpm tools; do not impose Gateflow's Python-only scripting convention on SERMS.
