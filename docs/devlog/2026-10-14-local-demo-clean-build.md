# Local Demo Launcher: Clean Build and Fail-Fast Readiness

Owner: Liu Tongyao (@cpg25ncu) · Requirement: local demo tooling ([local demo](../local-demo.md)); no Issue, task agreed in conversation · Branch: `fix/start-local-clean-build` · Baseline: main `457a2a9`

## 2026-10-14 Stale build output broke the local demo

### What happened

`scripts/start-local.ps1` was run in a checkout whose `app/target/classes` still held resources from an older branch build: `db/migration/V1__notification_foundation.sql`, `db/migration/V2__notification_attempts.sql` and `db/mysql/V3__case_sensitive_notification_identity.sql`. None of them exist in the current source tree. The launcher built with `package -DskipTests` and no `clean`, so Maven packaged them into `serms-0.1.0-SNAPSHOT.jar` next to `V001__sprint1.sql` to `V004__loan_operations.sql`. Spring Boot stopped at startup with `FlywayException: Found more than one migration with version 001` (offenders `V001__sprint1.sql` and `V1__notification_foundation.sql`). The launcher kept polling the health endpoint for about four minutes before it reported a failure, and the message named only the URL. Running `mvnw clean package -DskipTests` by hand fixed the start.

### What was changed

- `scripts/start-local.ps1` builds the backend with `clean package -DskipTests`.
- The `Ready` loop receives the started process and its log path. When that process has exited, it throws at once and names the log, instead of running all 90 retries. Both readiness waits use it: the backend (`backend.log`) and Vite (`frontend.log`).
- `docs/local-demo.md` states that the launcher does a clean backend build and why.

Flyway migrations, `stop-local.ps1` and the other demo scripts are unchanged.

### Design notes

- `clean` is safe while a demo runs because the launcher already copies the JAR to `.cache/local-demo/serms-demo.jar` and starts that copy; nothing running reads `app/target`.
- `-SkipBuild` still uses whatever JAR is in `app/target`. The document already limits it to artifacts that match the desired source. The fail-fast check is what makes a wrong JAR visible quickly in that mode.
- The exit check comes after the health request in each iteration, so a healthy answer always wins and a normal start behaves as before.
- The message points to the standard-output log and mentions the `.err` file beside it, because Spring Boot logs to standard output while JVM launch errors go to standard error.
- Old duration: each of the 90 attempts waits up to 2 seconds for the request and then 0.5 seconds, which is where the roughly four minutes came from.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Backend never became healthy; `Found more than one migration with version 001` in `backend.log` | Build without `clean` packaged migration files left in `app/target/classes` by another branch's build | Launcher runs `clean package` | Planted the three files, ran the launcher, listed the JAR: only `V001` to `V004` remain |
| Launcher waited about four minutes and named only the health URL | `Ready` polled the URL without looking at the process it had started | `Ready` throws as soon as the process has exited and names its log | Poisoned JAR with `-SkipBuild`: launcher exited with code 1 after 11 seconds |

### Verification

Windows 11, Windows PowerShell 5.1.26100, JDK 25.0.4, Maven Wrapper (Maven 3.9.11), Node.js 24.20.0, pnpm 11.19.0, PostgreSQL 18.6. Run in a separate worktree on the working tree recorded by this commit, base `457a2a9`. Another demo instance from the main checkout was running on 55433/8082/5174, so this run used `-DatabasePort 55434 -BackendPort 8083 -WebPort 5175`; the other instance was not stopped and answered `UP` afterwards. Real PostgreSQL 18, no mocks.

1. **Reproduction (passed as a reproduction).** Copied the three stale files from commit `88edacf` into `app/target/classes`, then ran the old command `mvnw -B -f app/pom.xml package -DskipTests`. `jar tf` listed all seven SQL files in the JAR.
2. **Fail-fast (passed).** Ran the changed launcher with `-SkipBuild` on that JAR. It exited with code 1 after 11 seconds with `Process <pid> exited before http://127.0.0.1:8083/api/v1/health became ready. Inspect ...\.cache\local-demo\backend.log (and the .err file next to it).` `backend.log` contained `Found more than one migration with version 1` (this Flyway version prints `1`, the incident report quotes `001`). The launcher's existing cleanup stopped PostgreSQL, all three ports were free and no `state.json` was left.
3. **Clean build (passed).** Ran the changed launcher without `-SkipBuild` on the same poisoned `app/target`. `build-backend.log` shows `clean:3.5.0:clean` deleting `app/target` and `BUILD SUCCESS`; the runtime JAR holds only `V001` to `V004`; Flyway validated and applied 4 migrations; both health URLs returned `UP`.
4. **Smoke (passed).** `node scripts/smoke-local.mjs` against that instance: status `passed`, 28 checks.
5. **Shutdown (passed).** `scripts/stop-local.ps1` stopped this instance; ports 55434/8083/5175 were free.
6. `node scripts/check-collaboration.mjs`: passed, 19 documents and 7 skills.

Not run: the old launcher's four-minute wait was not measured again; the figure comes from the incident. The exit check for the Vite process uses the same code path as the backend but was not forced to fail. Backend and frontend test suites were not run locally because no application code changed; hosted CI on the pull request covers them.

### Deviations

None from the Proposal, contracts or the technology document. The task asked for the exit check on the backend; the same check also covers the Vite process because both waits share the `Ready` function.

### Follow-ups

- A member check of the launcher on another machine is still open; local results and CI do not replace it.
- `scripts/start-local.sh` does not exist; if a non-Windows launcher is added it should use `clean package` as well.

### Authorship

The change, the verification runs and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction, who observed and reported the incident. No teammate review is claimed.
