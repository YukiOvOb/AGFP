---
name: serms-integrate
description: Check SERMS PR merge conditions and dependency updates, recording merge, deployment, and team Done evidence separately.
---

# Merge Checks and Completion

Sources: [README merge policy](../../../README.md#collaboration-and-releases), [PRs and team acceptance](../../../.github/CONTRIBUTING.md#prs-and-team-acceptance), [Sprint DoD](../../../docs/agile/sprint-plan.md#2-definition-of-done), [Testing](../../../docs/testing.md).

1. Read latest PR HEAD, target branch, and CI results; verify scope, dependencies, contract confirmation, and actual permissions. Preserve Write collaborators' self-merge policy without appointing a sole integrator.
2. State missing checks/acceptance. For dependency-branch tasks, verify merge order, retargeting, and the updated combination; old HEAD/base passes do not cover the new combination.
3. Merge according to existing user authorization and actual GitHub protection. This skill alone does not authorize merging; do not use rule proposals to approve themselves or bypass failures.
4. Read actual PR status and merge SHA afterward; record deployment and post-deployment checks, and return dependency tasks for revalidation.
5. Keep Testing/incomplete acceptance when member checks, real integration, or other DoD evidence is missing; do not close tasks automatically. Mark Done only after all acceptance; follow worktree preservation guidance for cleanup.

Deliver: actual merge/deployment status, merge SHA if available, task status, subsequent owner, and retained branch/worktree.
