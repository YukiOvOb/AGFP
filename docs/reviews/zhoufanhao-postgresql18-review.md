# SERMS PostgreSQL 18 and UC03 Update Review

Review date: 2026-10-12 (Asia/Singapore). Official name: Shared Equipment Reservation and Maintenance System (SERMS).

## Sources and Conclusion

Source: Zhou Fanhao's `AGFP.zip`, SHA-256 `EA7161D4EEF8D14EC6309EB929EDD1DD02F2C3630B878ACD40F22215AF56B1BF`.

Reviewed original `5559503` (UC03/database), `751ca0d` (default PostgreSQL 18), `b6d0db8` (delivery notes), against main `6a57722`. Original commits/authorship retained; integration preserves unified repository name/README title. No caches, third-party dependencies, build output, packaging tools, local databases committed.

Conclusion: no blocking application/migration issues; ready for PR. Fixed redirected Windows test output stalling PostgreSQL startup. Hosted CI, security, team acceptance, server deployment require separate confirmation.

## Review Points

- Default/local/development container use PostgreSQL, `postgres:18-alpine`; production removes MySQL Connector/Flyway MySQL, H2 test-only.
- Relocated V001–V003 Git blobs unchanged. New V004 updates decisions/BIGINT/indexes; upgrades verify historical IDs/reasons/immutability.
- Approval persistence requires an existing transaction/active APPROVER; real persistence precedes enabled-by-default approval. Default-runtime tests cover login→reservation/approval→notification→checkout.
- UC03 writes require active CUSTODIAN; validate borrower/state/window/equipment/version. Borrowers read own records; negative tests cover authorization/CSRF/invalid versions.
- Checkout/return/equipment/damage cases/audits/synchronous events are atomic. Concurrency has one winner, event failures roll back, maintenance/retirement precedence tested.
- Java/database versions long/BIGINT; frontend matches app-generated OpenAPI.
- Windows startup uses hidden `Start-Process`, independent output files, waits only for `pg_ctl` exit so the running PostgreSQL does not inherit captured pipes.

## Independent Verification

Isolated temporary PostgreSQL 18.6, JDK 25.0.4 (Java release 17), Node.js 24.19.0. No development/production databases; stopped instances afterward.

| Check | Result |
| --- | --- |
| Database regression | 27 passed; 0 failures/errors/skips |
| V004 historical decisions | Passed |
| Full backend regression | 536 passed; 0 failures/errors/skips |
| Default PostgreSQL/UC03 | 20 passed; 0 failures/errors/skips |
| UC04 PostgreSQL | 28 passed; 0 failures/errors/skips |
| Pinned notification/history | 12 passed; 0 failures/errors/skips |
| Frontend | 79 passed, all 6 files |
| ESLint/TypeScript/production | Passed |
| OpenAPI versus actual app | Passed |
| Production JAR | No MySQL/H2; only V001–V004 |
| Git whitespace/PowerShell syntax | Passed |
| Fixed redirected Windows script | Completed and stopped temporary DB |

702 principal tests total; initial 107 targeted tests/subsequent script reruns not counted twice. Original author's summaries remain historical; this record comes from independent execution.

## Delivery Boundaries

Resolves the backend's MySQL default and includes Zhou's UC03/cross-module work. Production remains a welcome page, not proof of complete business deployment. Existing MySQL data is not automatically converted; follow backup/verification/migration instructions.

Frontend still warns of one output >500 kB; build succeeds, future splitting can improve it. No claim of passed coverage gates, E2E browser acceptance, or cloud security.

## Initial Hosted CI Diagnosis

PR #1's first Trivy scan passed. Dependency Review reports unsupported repository configuration; private repositories must meet [GitHub's enablement conditions](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review). Failure retained without skipping/lowering the requirement.

Gitleaks 8.24.3 `generic-api-key` misidentified the environment-name list at `scripts/test-zhoufanhao.ps1:10` in `5559503`. It contains only `SERMS_APP_TEST_*` names, no passwords; test passwords are runtime GUIDs. `.gitleaksignore` lists only that commit/file/rule/line fingerprint per [Gitleaks exclusions](https://github.com/gitleaks/gitleaks/blob/v8.24.3/README.md#gitleaksignore), without excluding whole files/commits/rules.

Same-version local Gitleaks, same PR commit range/log options: exit 0, no leaks.
