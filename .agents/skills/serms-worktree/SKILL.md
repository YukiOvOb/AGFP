---
name: serms-worktree
description: Create or resume an isolated SERMS writing workspace, or prepare writer handover and worktree cleanup.
---

# Isolation and Handover

Sources: [Branches and worktrees](../../../.github/CONTRIBUTING.md#branches-and-worktrees), [Handover and cleanup](../../../.github/CONTRIBUTING.md#handover-and-cleanup), [Handover record](../../../docs/collaboration/agent-workflow.md#handover-record).

Inspect first:

```text
git worktree list --porcelain
git status --short --branch
git log -1 --oneline
```

- Create: confirm task, base, and writer. Reuse a suitable idle worktree for this task where possible; independent writing tasks get separate worktrees, without switching another person's active checkout. The coordinator handles shared refs; Codex worktrees use app tools.
- Resume: confirm the prior writer has handed over, branch/HEAD and uncommitted files, and current task/rule/contract changes.
- Hand over: stop your services and writes, provide evidence and pending decisions using the source's record format; the recipient confirms before writing.
- Clean up: check untracked/ignored files, unpushed commits, and stashes; preserve valuable content and confirm completion/cancellation scope. Archive with the creating tool; never delete merely because status is clean.

Deliver: worktree, branch, writer, HEAD/base, handover confirmation, retained content, and incomplete acceptance.
