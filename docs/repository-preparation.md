# Repository Preparation and Deferred Release

Date: 2026-10-13, Asia/Singapore. Main baseline: `940bf42`; local-demo dependency: `d42e0a6`. This local-only closeout reconciles the preparation content from PR #3 with the later English repository and user-confirmed task takeover. It does not merge or update that remote PR.

## Current Scope and Execution

The owner requested local repository housekeeping and cloud deployment last. [D-004](collaboration/decisions.md#d-004-local-first-integration-and-wenqi-task-execution) records Liu Tongyao executing UC01/local integration and later release work, while Shi Wenqi remains Member 1 and the original contributor. [D-005](collaboration/decisions.md#d-005-local-closeout-and-cloud-last) places basic local acceptance and later user-defined changes before cloud publication. Earlier preparation notes naming Wenqi as the continuing execution owner are historical, not the current assignment.

Current Issues #5/#9/#10 belong to Liu Tongyao; #6/#7/#8 belong to Zhou Fanhao, Wang Yuanmeng and Zhang Hanming respectively. This closeout changes no member identity, account permissions, business contract or test requirement.

## Existing Protection and CI Evidence

Main is protected. The eight required checks returned by GitHub are `Secrets scan (gitleaks)`, `Vulnerability scan (Trivy)`, `Dependency review`, `database-test`, `java-tests`, `frontend`, `notification-contract`, and `checkout-return`. PR #11 passed all eight from GitHub Actions App 15368 plus `collaboration-docs` on `d42e0a6`.

The earlier preparation session recorded mandatory PRs, strict branch updates, administrator enforcement, no forced push/deletion and zero mandatory approving reviews. Those administrative values remain prior-session evidence; this closeout does not change GitHub settings. Write collaborators' self-merge policy and separate member/DoD acceptance remain as defined in [CONTRIBUTING](../.github/CONTRIBUTING.md).

## Backup and Development Copies

The local `.gitignore` now includes root ZIP/bundle archives, recovery, review, policy-audit and isolated hardening copies, matching PR #3's intended exclusions. Source, tests, templates and `.env.example` remain trackable. Existing archives/copies are retained in place; ignore rules do not remove already tracked files or replace secret scanning.

The original maintenance branch, three commits ahead of its remote, is retained. Its eleven uncommitted translation files are stored in a named stash and independently backed up with a binary patch, exact file hashes and a complete Git bundle. See [local closeout](local-repository-closeout.md).

## Remaining Release Work

After basic local acceptance and the user's subsequent changes, Issue #10 must verify server access/host identity, actual services/storage/backups, web/app/db packaging, allowed-main-SHA deployment gates, serialized release, rollback and real deployed login/workflow checks. Existing main deployment currently fails with `missing server host`; root packaging is still the static welcome page. This is a deferred release dependency, not a local startup blocker.

CI timeout/concurrency improvements, duplicate integration-job execution, trusted/pinned Actions sources, SSH deployment identity, environment/Secrets scope, artifact retention and post-deployment scans remain separate engineering work. Values and credentials stay outside source/public records.

PR #3 is still open/conflicted against the newer main; its intended ignore/document changes are prepared locally here. PR #11 remains draft. This closeout does not push branches, merge PRs, trigger deployment, stop other sessions' services or claim member/cloud acceptance.
