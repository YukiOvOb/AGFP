# Dependabot PR flood and pause until the code freeze

Owner: Liu Tongyao (@cpg25ncu) · Related: #25 (Dependabot is part of Wang Yuanmeng's DevSecOps task), #39 · Branch: `fix/liu-tongyao-dependabot-pause` · Baseline: `main` `f9596c1`

## 2026-10-15 What happened

PR #39 added `.github/dependabot.yml` with five weekly entries (`app`, `database`, `integration/notifications` Maven, `frontend` npm, GitHub Actions) and no limits. It merged at 13:07 SGT. Dependabot ran its first scan at once and opened 22 PRs (#48–#69) between 13:07 and 13:09, one per dependency. Each PR ran the full CI set, so more than 200 Actions runs followed and the PR list and Actions history were filled with bot entries.

Eight of the 22 were major-version bumps (Spring Boot 3.5 → 4.1 in `integration/notifications`, JUnit 5 → 6, vitest 3 → 5, `@tanstack/react-table` 8 → 9, `actions/setup-node` 4 → 7, `actions/setup-java` 4 → 5, `actions/upload-artifact` 4 → 7, `gitleaks/gitleaks-action` 2 → 3). Three PRs failed CI: #54 (`notification-contract`), #64 (`release-images`) and #67 (`frontend`, `release-images`).

The repository owner first had an agent ask Wang Yuanmeng on #39 to restrict the configuration, then decided to fix it directly.

## Change

Each of the five entries in `dependabot.yml` now has:

- `open-pull-requests-limit: 0`: no new version-update PRs until after the code freeze on Thu 2026-10-22 ([D-012](../collaboration/decisions.md#d-012-24-hour-completion-rule)). Resuming means deleting these five lines.
- An `ignore` rule for `version-update:semver-major` on every dependency, so majors are upgraded on purpose in their own task.
- A `groups` entry that bundles minor and patch updates, so after the pause each ecosystem produces one PR instead of one per dependency.

The 22 open Dependabot PRs were closed without merging, each with a comment pointing to this change. The dependency status row in `docs/tech-stack.md` was updated. Wang Yuanmeng keeps ownership of Dependabot under #25; this change only bounds it for the delivery period.

## Design notes

- Pausing with `open-pull-requests-limit: 0` keeps the configuration and the Proposal's Dependabot item visible, unlike deleting the file.
- In the repository settings, Dependabot alerts and security updates are both off (read on 2026-10-15), so the pause leaves no Dependabot PRs at all. Dependency risk during the freeze is still covered by Dependency Review and the Trivy filesystem scan on every PR. Changing those settings was not part of this task.

## Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| 22 bot PRs and 200+ Actions runs within minutes of merging #39 | First Dependabot run with no PR limit, no grouping and no major-version filter | Pause, ignore majors, group minor/patch | See the PR for this branch: CI on the change, and no new Dependabot PRs after merge |
