# SERMS Verification and Evidence

Test bench requirements remain defined in [Technology stack §5](tech-stack.md#5-test-benches-each-member-owns-their-tests). This page provides execution entry points and evidence formats without lowering coverage, real PostgreSQL, or frontend/backend testing requirements.

## Execution Entry Points

| Check | Working directory and command | Prerequisites / current workflow |
| --- | --- | --- |
| Backend | `app/`: Windows `mvnw.cmd -B verify`; macOS/Linux `./mvnw -B verify` | Java 17; PostgreSQL/Testcontainers cases require Docker. `Frontend and API contract` and `Notification and default PostgreSQL tests` run backend checks |
| Complete PostgreSQL/UC03 regression | Root: `scripts/test-zhoufanhao.ps1`; without Docker supply `-PostgresBin '<PostgreSQL 18 bin>'` | Isolated PostgreSQL 18; database, app regression and UC03. CI also uses `-Puc03-postgres-it` |
| UC02 PostgreSQL and rollback acceptance | Root: `pwsh -File scripts/test-uc02.ps1 -IncludeLoanIntegration`; without Docker add `-PostgresBin '<PostgreSQL 18 bin>'` | Separate disposable app/UC02/UC03/UC05 databases. Runs full regression, `uc02-postgres-it`, then main's UC03+UC05 integration profile. Configured in `Approval PostgreSQL acceptance`; no production DB or merged migration is changed |
| Business coverage gate and negative exercise | `app/`: `mvnw.cmd -B verify`; root: `pwsh -File scripts/check-coverage-gate.ps1` after a successful build | Combined production `**/service/**` and `**/domain/**`, LINE/COVEREDRATIO ≥ 0.70 and nonempty line count. Negative exercise substitutes zero-execution data, checks the specific 70% failure, then restores coverage bytes and rechecks; do not run concurrently with Maven |
| UC04 PostgreSQL integration | `app/`: `mvnw.cmd -B -Duc04.postgres=true verify` (Linux: `./mvnw`) | Explicitly configured, already migrated TEST database; see `Uc04PostgresTestSupport`. On every PR and every push to main, `Maintenance PostgreSQL acceptance` ([maintenance-tests.yml](../.github/workflows/maintenance-tests.yml), job `maintenance-postgres`) runs `MaintenancePostgresIT` on PostgreSQL 18 with `-Puc04-postgres-it`. Ordinary `verify` still does not automatically enable the suite |
| Frontend API types | `frontend/`: generate `app/target/openapi.json`, set `OPENAPI_FILE=../app/target/openapi.json`, then `pnpm check:api` | Actual backend verify output; `Frontend and API contract` |
| Frontend quality/tests | `frontend/`: `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` separately | Node 24 and package.json's pnpm; first `pnpm install --frozen-lockfile` |
| Frontend E2E | `frontend/`: `pnpm exec playwright install chromium`, then `pnpm test:e2e` | Chromium/platform dependencies; Linux CI uses `--with-deps` |
| Data foundation | Root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1` | Docker, Java/Maven; script and `Sprint 1 database` use PostgreSQL 18 |
| Notification adapter | Root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1` | Docker, Maven, pinned upstream commit; `Notification PostgreSQL contract` |
| Security | GitHub `CI` workflow | Existing gitleaks, Trivy, Dependency Review; local document checks do not replace them |
| Collaboration navigation/skills | Root: `node scripts/check-collaboration.mjs` | Added `Collaboration documentation` workflow; required branch-protection checks unchanged |
| Real local demo | Root: startup, `node scripts/smoke-local.mjs`, data/browser checks and shutdown in [local demo](local-demo.md) | Dedicated local PostgreSQL cluster/accounts; creates actual demonstration transactions; not a full exception/overdue suite |
| Real administrator security browser workflow | Root: `node scripts/check-admin-security-browser.mjs` after this checkout's local startup and Chromium installation | Desktop/mobile actual create, role change, disable/activate, password reset; two independent cookie sessions per target; old-session 401 and new-login/audit checks. Creates synthetic accounts and retains history; private reports/screenshots in `.cache/local-demo/`, reviewed exports only |
| Real UC02 / five-role and dual-role browser flow | Root: `node scripts/check-uc02-browser.mjs` after this checkout's local-demo startup and Chromium installation; optional `--publishable-evidence` exports synthetic self-decision screenshots | Actual React → Spring Boot → PostgreSQL 18, desktop/mobile; no mocked APIs/clocks. Separate from `pnpm test:e2e` contract mocks. Includes temporary BORROWER+APPROVER, UI and actual browser-POST self-decision blocks, read-only no-side-effect DB assertions and role restoration. Creates isolated fixtures; respects quota and retains history. Private summary/traces: ignored `.cache/local-demo/`; never publish raw traces/session tokens. Reviewed evidence: [dual-role run](evidence/uc02-dual-role-20261015/README.md) |
| Real current-page role matrix | Root: `node --test scripts/role-access.test.mjs`, then `node scripts/check-role-browser.mjs` | Same checkout's running isolated local demo; run only after the UC02 browser handover/repair workflow finishes. Anonymous, five roles and second-borrower ownership; 13 pages, 23 read API families after UC05 sync, role-denied mutations with valid CSRF, CSRF/logout checks, unchanged database fingerprints. [Current sync evidence](evidence/role-access-uc05-sync-20261015/README.md), [earlier 12-page evidence](evidence/role-access-20261015/README.md). Node inventory tests run in frontend CI; full real matrix is local, not a hosted browser job |

Windows environment example, working directory `frontend/`:

```powershell
$env:OPENAPI_FILE = '../app/target/openapi.json'
pnpm check:api
```

macOS/Linux equivalent:

```sh
OPENAPI_FILE=../app/target/openapi.json pnpm check:api
```

The historical table baseline was main `b3def12`; UC04 acceptance was updated against `ec57bbccb8e4c512520e1f23fe2e724d294ec321`. UC02/coverage/browser entries were added against fetched main `2e9eda35973f0929c19fa3e613688d902a361f9d` on 2026-10-15. Default/local app configuration and current database CI/scripts use PostgreSQL 18; production Flyway V001-V006 is unified in `app/`. MySQL is absent from production dependencies and H2 is test-only. The historical [Decision record](collaboration/decisions.md#retained-technical-and-business-differences) explains earlier differences, not today's runtime. JaCoCo now generates both the complete report and `target/site/jacoco-business/`, and enforces the existing ≥ 70% business-line target during `verify`. Scope is aggregate service/domain packages, including the existing notification adapter sources compiled into `app`; not per-class or global-all-code coverage. Branch protection, hosted CI success and team acceptance are separate evidence. See the [quality delivery record](devlog/issue-24-quality-gates.md). Test and coverage requirements remain unchanged.

The [PR #11 verification record](evidence/local-demo/verification.md) preserves local results. GitHub subsequently passed all nine checks on `d42e0a6`; those results do not cover new closeout commits or replace member acceptance. The merged integration line passed all twelve checks on `db376ab`, whose content equals `main` `b3def12`. Current status and remaining scenarios are in [Sprint progress](agile/sprint-status.md#integration-update-2026-10-14).

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
