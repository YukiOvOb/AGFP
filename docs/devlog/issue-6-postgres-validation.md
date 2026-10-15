# PostgreSQL 18 and local demo validation (#6)

Owner: Zhou Fanhao (@zfh-usedname) · Branch: `test/zhoufanhao-issue6-postgres-validation` · Baseline: `0be0a29af011862824c6aaab642512241764bcd9`

## 2026-10-15: isolated database and demo checks

### What was validated and changed

The current V001–V006 Flyway sequence and database regression suite ran against disposable PostgreSQL 18.6 databases. Five `EquipmentMasterDataPostgresTest` cases include the V004-to-V005 upgrade with existing equipment, category and constraint behavior. The complete backend run passed 590 unit/database tests and 30 UC03/UC05 integration tests with zero failures or skips. The local demo started on dedicated ports from this worktree, applied Flyway using the real Spring Boot configuration, and passed 28 smoke checks for six account roles, equipment search, reservation, approval, checkout/return, maintenance, notifications and logout.

The demo seed now sets every V005 equipment field on initial insert and never updates an existing equipment row on a repeat run. The isolation checker compares all equipment rows, including versions and nullable fields, roles, categories, workflow counts and password fingerprints across reseeding and failed guard attempts. It also checks the six Identity role assignments and eight distinct equipment serial numbers. Four data checks passed.

### Problems and fixes

| Symptom | Root cause | Fix | Verification |
| --- | --- | --- | --- |
| A repeated demo seed changed equipment versions and could refill an intentionally cleared optional field. | Follow-up `UPDATE` statements ran after every `ON CONFLICT DO NOTHING` insertion. | Supply V005 fields in the original insert and remove the updates. | Full-row data snapshot remained identical across repeated seeding; `scripts/check-local-data.ps1` passed four checks. |
| A UC05 overdue assertion failed in the shared integration database. | It assumed its loan was the first row even when other tests had created overdue loans. | Match the target loan in the response without depending on row position. | The serial regression run passed all ten `Uc05PostgresIT` cases. |
| An initial verification run failed while the demo build used the same `app/target` directory. | Two Maven processes wrote to one worktree concurrently. | Finish the demo build, then rerun verification serially. | The final isolated run ended with `BUILD SUCCESS`. |

### Commands and evidence

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -DatabasePort 55463 -BackendPort 8086 -WebPort 5186` — real local Flyway startup; preserved logs in `.cache/local-demo/`.
- `node scripts/smoke-local.mjs` — 28 checks passed; `.cache/local-demo/smoke-summary.json`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-local-data.ps1` — four checks passed; `.cache/local-demo/data-check-summary.json`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-issue6-fresh-seed.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'` — clean V001–V006 Flyway startup, eight V005 equipment rows with unique serial numbers, unchanged full-row data after repeat seeding and backend restart; `.cache/issue6-fresh-dd12b45a2a/`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'` — 590 backend and 30 UC03/UC05 integration cases passed; `.cache/issue6-db-test-rerun.log`, `app/target/surefire-reports/`, and `app/target/failsafe-reports/`.

The commands used Windows 11, PostgreSQL 18.6, Java 25 targeting release 17, and an isolated local Node 24.22.0 runtime. Input was the baseline revision plus this branch's uncommitted seed, checker and UC05 test changes. The PostgreSQL clusters, ports and logs belong to this worktree. No hosted CI, cloud database or independent member review is claimed.

### Upgrade and recovery assumptions

Flyway migrations are append-only; this branch does not edit V001–V006. Apply them to a backed-up database with no manual schema drift and verify Flyway history and smoke tests after startup. Take a PostgreSQL backup before upgrading; on failure, restore the backup to a fresh database, fix the cause and rerun the application rather than changing a previously merged migration. The local demo seed is guarded for the database named `serms_local_demo` and refuses unrelated records. Existing demo databases seeded by an older script may retain their old nullable V005 values; use a fresh isolated demo database for a complete V005 fixture, rather than overwriting later local edits. No production or cloud upgrade was tested.

### Follow-up and authorship

The integration owner should run the combined branch after merge. Human member acceptance, latest-HEAD CI and deployment remain separate checks. This code and entry were prepared and verified by Codex under the user's instruction; no human review or effort hours are claimed.
