# GitHub Actions Security and Resource Audit

Task: conversation request, 2026-10-14 (Asia/Singapore). Human owner: requesting repository owner, Liu Tongyao / cpg25ncu as recorded in D-007. Sole writer: this Codex session. Branch: `codex/actions-security-audit`. Baseline/rule commit: `457a2a93f99ce1b195b1946d7d0f34cc6e63f9f5`, freshly fetched from origin/main.

## Scope and authorization

The user authorized reviewing latest code/history and directly removing unnecessary automation, preserving necessary builds, tests, security, deployment and human/Codex commits. Direct main changes, merging and removing business code are prohibited.

Worktree: `C:/Users/14824/.codex/worktrees/actions-security-audit/SWE5006_Group_Project`. Allowed paths: workflow configuration, CI/release verification scripts/tests and audit/testing/release documentation. Excluded: business code, dependencies, migrations, business contracts, production, secrets and protection settings. The original checkout (codex/local-repository-closeout / 1c613e3), its two untracked files and other worktrees are preserved.

Rules/skills actually read: AGENTS.md, .github/CONTRIBUTING.md, docs/tech-stack.md, docs/contracts/README.md, docs/collaboration/{ownership,agent-workflow,decisions}.md, docs/testing.md, docs/server-release.md, docs/devlog/README.md, .github/pull_request_template.md and .agents/skills/serms-{review,worktree,task,verify,pr}/SKILL.md. Review preceded writing in the isolated worktree. No subagents or human approval records were fabricated.

## Findings and account risk

**No observed github-actions[bot] commit storm, recursive workflow chain or rate-limit retry storm.** Confirmed issues concern redundant execution, unnecessary comment writes and resource bounds; bot identity alone is not a risk finding.

The [history evidence](../evidence/actions-audit/history.json) preserves all 174 available runs across both API pages. The observation interval is 2026-10-12 22:17:01 to 2026-10-14 01:56:31 Asia/Singapore; evidence timestamps are UTC.

| Observation | Evidence |
| --- | --- |
| Events | 113 pull_request, 61 push; no schedule, workflow_run or dispatch in the snapshot |
| Actors | cpg25ncu: 160; ezmoneysniper0701: 14; github-actions[bot]: zero |
| Results | 161 success, 13 failure; no pending/in-progress runs |
| Retries/duplicates | One run has attempt 2; no duplicate workflow-path/head-SHA run records |
| Bot commits | None by author/committer search across 106 reachable commits in fetched refs, or among all 43 main commits returned by the API |
| Inventory | 10 active remote workflows, matching baseline files; zero repository webhooks or self-hosted runners |
| Failure logs | All 14 failing jobs in the 13 failed runs inspected; no explicit rate-limit/abuse-detection error |

Five older deployment failures reported missing server host; the two newest failed the readiness assertion before SSH. Other failures concerned secret-scan inputs/detection, Dependency Review before dependency-graph setup, a missing upstream commit, application integration and an image vulnerability scan. A historical gitleaks comment warning was permission denial, not rate limiting. Existing .gitleaksignore excludes one specific historical test-script fingerprint; no ignore rule or threshold is broadened.

### Confirmed problems and changes

| Priority | Problem and impact | Remedy |
| --- | --- | --- |
| Medium | java-tests and checkout-return execute the same regression/UC03 PostgreSQL profile | Execute clean verify once in java-tests; retain checkout-return as a fail-closed dependent status. Remove zhoufanhao.yml |
| Medium | Documentation/ignore-only changes invoke Maven, PostgreSQL tests, Chromium, Docker builds and image smoke | Local Git scope detection avoids unrelated application steps only for proven Markdown/docs/root .gitignore changes; preserve security and named gates |
| Medium | Most workflows lack cancellation and job limits | Cancel superseded CI per workflow/PR or main ref; bound every job to 2-25 minutes. Serialize deployment without cancelling an active deployment |
| Medium | Dependency Review has pull-requests:write for routine comments; gitleaks attempts comments | Disable both PR-comment features; remove write permission. Keep blocking scans/job summaries; disable unnecessary gitleaks SARIF uploads |
| Medium | Mutable external Action tags | Pin every Action to a verified full SHA, with readable version comments |
| Low | Unready deployment allocates runners and repeatedly fails | Require main/readiness at job level before allocation; preserve script assertion. Narrow automatic push paths to runtime/configuration/release changes; retain manual current-main deployment |
| Low | Default artifact retention and duplicate evidence | Retain useful reports for seven days; remove duplicate UC03/gitleaks uploads. Do not delete existing evidence |
| Low | No explicit release pagination/request budget | Maximum 15 polls one minute apart, three check pages per poll, 15-minute deadline and 15-second request timeout. At most 60 GETs; stop on 403/429 without retry |

There is **no auto-commit facility to disable**. No workflow/deployment script commits or pushes code. Retained tokens lack contents:write; checkouts do not persist credentials. No PAT/App-token generation, recursive dispatch, automated merge or bot content-creation loop is introduced.

## Each workflow's purpose and disposition

Counts refer to baseline history.

| Workflow file / runs | Actual purpose | Decision |
| --- | --- | --- |
| ci.yml / 22 | gitleaks secret scan, Trivy filesystem/configuration vulnerabilities, PR Dependency Review | Keep all three on every PR and main revision. No documentation filter on security. Remove routine comments/write permission; pin/bound jobs |
| collaboration-docs.yml / 19 | Collaboration links, skills and navigation validation | Keep: cheap protection for shared operating rules. Add timeout/cancellation |
| database.yml / 23 | Real PostgreSQL 18 empty initialization, upgrade fixtures and immutable approval-history checks | Keep distinct migration coverage. Scope application steps; service initialization remains a small overhead |
| frontend-tests.yml / 23 | Actual backend OpenAPI, frontend API comparison, lint/typecheck/Vitest/build and Playwright | Keep complete checks on application/automation changes; proved documentation-only diffs end after scope determination |
| maintenance-tests.yml / 8 | UC04 real PostgreSQL rollback, concurrency, linked loans and recovery | Keep: distinct acceptance coverage from ordinary verify. Scope/bound execution and reports |
| notification-contract.yml / 23 | Transitional PostgreSQL adapter against pinned upstream contracts, upgrade fixtures and JAR boundary | Keep distinct contract coverage and upstream pin; existing transitional modules cannot be removed by this task |
| notification-tests.yml / 23 | Full app regression, default PostgreSQL integration and explicit LoanPostgresIT | Keep as sole UC03 profile executor; retain protected checkout-return dependent status |
| zhoufanhao.yml / 19 | Same app regression and UC03 profile as java-tests | Remove redundant workflow, retaining its check name inside notification-tests.yml |
| release-config.yml / 7 | Release-gate tests, deployment boundary tests, Compose validation, app/web builds, image scans and production-profile smoke | Keep both named release jobs. Always execute inexpensive configuration/script tests; scope image work only for proved docs/ignore-only diffs |
| deploy.yml / 7 (all failed) | Readiness/exact-current-main check gate, trusted SSH production deployment | Keep real release path. Readiness off means no runner/API/SSH; restrict push paths and preserve manual main execution |

Frontend still runs ordinary backend tests to generate actual OpenAPI while java-tests runs full regression/UC03. That overlap has a data dependency; sharing artifacts would require a larger pipeline refactor. Keep it to preserve contract checks. Migration and pinned-adapter benches cover different behavior and are not redundant.

PR checks validate a tested merge; main checks validate the actual main SHA used by deployment. Their coexistence is necessary, not evidence of a loop. One human rerun is also not an automated retry storm.

## Trigger, privilege and trust controls

- PR event types are opened/synchronize/reopened, matching prior defaults. Feature-branch pushes alone do not start main pipelines.
- Protected workflows retain their triggers; no workflow-level PR path filter leaves required checks Pending.
- Scope detection compares the tested PR merge to its target parent, or a push to its before SHA. It validates full SHA inputs, uses Git argument arrays and --no-renames to preserve deleted/renamed executable paths. No API calls or additional fetches.
- Unknown/mixed paths, workflow/scripts, empty diffs, mismatched checkout, non-merge PR checkout, unreadable payload or missing/shallow baseline run full application checks. A later docs commit on a PR containing code still runs full checks for that PR diff.
- checkout-return uses always() and explicitly requires java-tests.result == success; failure/cancellation/skipping cannot create a successful UC03 status.
- Settings were read, not changed: default workflow permissions read; Actions PR approval disabled; allowed actions all; no global SHA-pinning requirement.
- Actual main protection is strict, applies to administrators and requires eight GitHub Actions contexts: Secrets scan (gitleaks), Vulnerability scan (Trivy), Dependency review, database-test, java-tests, frontend, notification-contract, checkout-return. All remain. The four extra release-gate contexts also remain.
- All nine external Action references were resolved from upstream Git refs. Dependency Review's mutable v4 ref did not resolve at audit time; use verified v4.9.0 SHA with the same high-severity blocking policy.
- checks:read is limited to the release-gate job. No retained job requests any write permission; no pull_request_target, untrusted artifact execution or persistent self-hosted runner.
- Action pins do not freeze container bases, registries, vulnerability databases or tools downloaded by actions. Update pins deliberately when maintained versions change.

[GitHub's GITHUB_TOKEN documentation](https://docs.github.com/en/actions/concepts/security/github_token) explains that normal token-created push events do not recursively trigger workflows, with documented dispatch and approval-gated PR exceptions. PAT/App-token writes differ. This repo has no such write/dispatch chain; a bot name alone would not establish one.

## Resources and billing

This is a public repo and all jobs use standard ubuntu-latest. Under [GitHub billing rules](https://docs.github.com/en/billing/concepts/product-billing/github-actions), these runner minutes are free and do not consume private-repo included minutes. Account-wide billing and unrelated repositories are outside available evidence.

The initial 140 unexpired artifacts total 66,660,408 bytes (63.6 MiB); 27 caches total 2,303,022,873 bytes (2.14 GiB). These are point-in-time sizes, not accrued bills or account-wide remaining quota. Maven/Trivy caching is useful; blanket deletion would increase downloads. Future evidence uses seven-day retention.

At latest main, java-tests used 64 seconds and redundant checkout-return used 69 seconds. The change replaces that repeated 69-second suite with a minimal dependent status runner. release-images used 169 seconds; proved docs-only diffs can avoid those builds/scans/smoke. These are observed samples, not promised savings. Named gate runners and database service startup remain for compatibility.

[REST limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api) normally allow GITHUB_TOKEN 1,000 requests/hour/repository, with secondary limits too. Serialized release polling uses at most 60 GETs; normally 30 if checks stay pending. Scanner/action internals share quota and are outside the script's budget. Continuing requests while limited can restrict an integration; this gate stops immediately.

## Verification

Inputs: baseline HEAD plus the listed audit edits in the independent worktree. The delivered SHA and actual hosted checks are supplied with PR delivery.

| Check | Environment and actual result |
| --- | --- |
| node --test scripts/verify-release.test.mjs scripts/ci-scope.test.mjs | Windows, Node 24.19.0: 14 passed; real fixture Git diffs, finite API polling, pagination overflow and immediate 403/429 stop |
| actionlint -shellcheck= -pyflakes= | actionlint 1.7.12, official archive SHA256 verified: all nine workflows pass syntax/expression validation. Shellcheck/pyflakes unavailable and explicitly disabled |
| node scripts/check-collaboration.mjs | Passed: 19 documents, 7 skills |
| bash -n scripts/deploy.sh | Passed with Git for Windows Bash |
| bash scripts/test-deploy.sh | Seven mocked cases passed: lock, dirty checkout, volume identity, backup/build failures, unhealthy runtime, success. No SSH/deployment |
| git diff --check | Passed after final edits; UTF-8/LF maintained |
| Real PostgreSQL/frontend/Docker/images/live scans | Not repeated locally: no installed Docker. Audit PR changes automation/scripts and therefore requires full hosted checks, never the docs shortcut |
| Production/member check/merge/Done | Not performed; readiness variable absent; no human acceptance claimed; main unchanged |

This record captures local verification before publishing. Hosted CI is separate and bound to final PR HEAD; see the PR checks and delivery message. Baseline CI is not claimed as validation of new code.

## Residual risks and follow-up

No identified mechanism suggests account restrictions from bot spam, self-triggering loops or continued requests after rate limiting. This is an assessment of observed repo behavior, not a guarantee about GitHub enforcement, external apps, other repos, deleted history or future changes.

Legitimate PR updates still consume shared concurrency. Future pipelines exceeding pagination limits fail closed and need explicit review. Artifacts/caches remain finite; upstream services can impose independent limits. Production remains blocked pending the existing handover and acceptance. GitHub built-in secret-scanning/push-protection and Dependabot security updates were disabled at audit time; planned CodeQL/Dependabot work remains in Issue #25. This audit retains all existing gitleaks/Trivy/Dependency Review gates and does not claim those planned tools implemented.

After human review and merge under existing protection, verify one main run, protected names and readiness-off deployment skipping. Do not merge or enable production as part of this task.

## Authorship

Codex reviewed, implemented and verified at the user's request. No hours, teammate reviews, contract-owner approvals, production results or team Done evidence are invented.
