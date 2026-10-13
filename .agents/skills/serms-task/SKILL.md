---
name: serms-task
description: Implement an agreed SERMS task within existing ownership, technology, and contract scope, with the corresponding test bench.
---

# Implementation

Sources: [Roles and authorization](../../../.github/CONTRIBUTING.md#roles-and-authorization), [Tasks and dependencies](../../../.github/CONTRIBUTING.md#tasks-and-dependencies), [Technology stack](../../../docs/tech-stack.md), [Contract index](../../../docs/contracts/README.md).

1. Read the task/conversation agreement, relevant contracts, allowed paths, and dependency versions; record rule baseline, HEAD/base, and authorized scope. Confirm you are the sole writer.
2. Implement agreed behavior in an isolated worktree. Preserve one backend, one frontend, existing module layering, and reuse platform/shared testing facilities.
3. Present concrete options and ask the user about conflicts with existing rules; continue independent work. Old implementation behavior is not permission to alter a contract.
4. Provide the full feature test bench. Regenerate frontend types for backend API changes; cover real authorization, transactions, concurrency, and exception flows.
5. Record actual results through [Verification](../serms-verify/SKILL.md) and organize delivery through [PRs](../serms-pr/SKILL.md). Delegate through the [Agent workflow](../../../docs/collaboration/agent-workflow.md) only when the user authorizes it.

Deliver: actual changes, branch/HEAD/base, test evidence, remaining acceptance, and pending decisions. Do not equate partial implementation or local passes with team Done.
