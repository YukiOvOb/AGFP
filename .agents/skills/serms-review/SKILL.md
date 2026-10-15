---
name: serms-review
description: Review a fixed SERMS commit or PR read-only against behavior, contracts, team conventions, and evidence; report reproducible issues.
---

# Read-only Review

Sources: [PRs and team acceptance](../../../.github/CONTRIBUTING.md#prs-and-team-acceptance), [Review and human identity](../../../docs/collaboration/agent-workflow.md#review-and-human-identity), [Testing](../../../docs/testing.md).

1. Fix reviewed HEAD, base, and scope. Read the task, contracts, diff, and evidence, then compare other review opinions.
2. Remain read-only. Run authorized checks with isolated resources; send fixes to the writer rather than editing the reviewed branch.
3. Findings specify file/line, trigger, user/data impact, evidence, and severity; distinguish confirmed defects, possible issues, and missing evidence.
4. Verify test inputs, actual database version, API types, authorization/contract confirmation, and partial delivery. Agent analysis is not a human check.
5. Recheck affected scope after HEAD changes or explicitly limit conclusions to the original HEAD. Publish remotely only when authorized, identifying the agent; never submit an approving review as a human.

Deliver: versions, findings ordered by impact, actual checks, unresolved disagreements, and acceptance gaps.
