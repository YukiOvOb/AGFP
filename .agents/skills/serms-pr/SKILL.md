---
name: serms-pr
description: Prepare scoped SERMS commits and PRs, explaining dependencies, contract impact, checks, and remaining acceptance.
---

# Commits and PRs

Sources: [PRs and team acceptance](../../../.github/CONTRIBUTING.md#prs-and-team-acceptance), [PR template](../../../.github/pull_request_template.md), [Evidence records](../../../docs/testing.md#evidence-records).

1. Inspect your diff, Git status, and staged changes; stage explicit task paths only. Preserve others' changes, backups, and recovery directories.
2. Titles describe observable changes using the repository's `type(scope): change` style. Fill the template and link real tasks/cards; without an Issue, state the conversation scope rather than invent a number.
3. State base/dependency PRs, contract impact and actual confirmation, full/partial delivery, verification HEAD/base, environment, and results. Keep missing deployment/member acceptance visible; use `Refs #n` when an Issue exists.
4. Follow existing user authorization for push and PR creation/update; drafts can be prepared for review. PR delivery does not automatically authorize comments or external messages.
5. Update evidence and affected review scope after new commits. Remain draft when readiness is insufficient; mark only actual results complete.

Deliver: commit/branch/HEAD/base, PR link or reviewable draft, checks, and pending acceptance. This skill does not approve rule proposals for the user or authorize automatic merging.
