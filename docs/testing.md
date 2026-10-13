# SERMS Verification and Evidence

Test bench requirements remain defined in [Technology stack §5](tech-stack.md#5-test-benches-each-member-owns-their-tests). This page provides execution entry points and evidence formats without lowering coverage, real PostgreSQL, or frontend/backend testing requirements.

## Execution Entry Points

| Check | Working directory and command | Prerequisites / current workflow |
| --- | --- | --- |
| Backend | `app/`: Windows `mvnw.cmd -B verify`; macOS/Linux `./mvnw -B verify` | Java 17; PostgreSQL/Testcontainers cases require Docker. `Frontend and API contract` and `Notification foundation tests` run backend checks |
| Frontend API types | `frontend/`: generate `app/target/openapi.json`, set `OPENAPI_FILE=../app/target/openapi.json`, then `pnpm check:api` | Actual backend verify output; `Frontend and API contract` |
| Frontend quality/tests | `frontend/`: `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` separately | Node 24 and package.json's pnpm; first `pnpm install --frozen-lockfile` |
| Frontend E2E | `frontend/`: `pnpm exec playwright install chromium`, then `pnpm test:e2e` | Chromium/platform dependencies; Linux CI uses `--with-deps` |
| Data foundation | Root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1` | Existing script and `Sprint 1 database`; actually uses PostgreSQL 17, not completed 18 acceptance |
| Notification adapter | Root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1` | Docker, Maven, pinned upstream commit; `Notification PostgreSQL contract` |
| Security | GitHub `CI` workflow | Existing gitleaks, Trivy, Dependency Review; local document checks do not replace them |
| Collaboration navigation/skills | Root: `node scripts/check-collaboration.mjs` | Added `Collaboration documentation` workflow; required branch-protection checks unchanged |

Windows environment example, working directory `frontend/`:

```powershell
$env:OPENAPI_FILE = '../app/target/openapi.json'
pnpm check:api
```

macOS/Linux equivalent:

```sh
OPENAPI_FILE=../app/target/openapi.json pnpm check:api
```

This table lists configured entry points, not passed checks. The real-database stage of `Notification foundation tests` still uses MySQL; some scripts still use PostgreSQL 17. PostgreSQL 18/unified migrations remain the target; actual differences are in the [Decision record](collaboration/decisions.md#retained-technical-and-business-differences). Do not incidentally change database or scanner configuration in this documentation PR.

## Verification Scope

- Run the checks closest to the change, then existing PR requirements. For documentation-only changes, check links, templates, and applicable rules locally; remote CI follows existing workflows without skipping.
- Backend/frontend behavior changes include their complete test benches. H2/mock results do not establish real PostgreSQL 18, concurrency, or transaction acceptance.
- After commits/base updates, assess affected evidence and rerun verification; old-HEAD passes do not apply to new HEAD.
- Never alter skipped tests or coverage thresholds to hide failures; exceptions follow technology-stack confirmation rules.
- Ports, containers, databases, and reports belong to each worktree; do not clear others' databases or stop their processes.

## Evidence Records

For each check record command/scenario, working directory, OS/tool versions, HEAD, fetched base branch/SHA, uncommitted inputs if any, `passed / failed / not run / blocked`, brief output, and report location. Verify whether the baseline is remotely current; an old `origin/main` is not live remote state.

For a local uncommitted proposal, HEAD remains the baseline; identify added/modified files as inputs. After committing, record delivered HEAD and verified base. Do not invent a final SHA.

PRs distinguish:

| Status | Valid evidence |
| --- | --- |
| Local verification | Actual commands, input versions, output/reports |
| CI | Actions runs and conclusions for latest PR HEAD |
| Agent review | Scope, HEAD/base, specific findings |
| Member check | Actual human check; not agent review |
| Contract confirmation | Actual affected-owner confirmation |
| Merge/deployment | PR merge status, deployment workflow, post-deployment checks |
| Team Done | All [Sprint DoD](agile/sprint-plan.md#2-definition-of-done) evidence |

Existing required checks, coverage, and security thresholds remain unchanged. This document and the new check workflow do not automatically change GitHub protection. Record missing/unavailable checks accurately, never as `passed`.

## Practical Template Checks

After changing rules, forms, or skills, exercise a real scenario: for example, UC03 waiting for data migrations must identify owner, exclusions, dependencies, mock versus real acceptance, and a sole writer; merged tasks lacking deployment/member checks must not move to Done.

The documentation checker covers local file/heading links in collaboration Markdown, skill names/source entry points, and the Claude import. It does not verify authorization, actual GitHub protection, remote URLs, or business-contract consistency; these need factual checks.
