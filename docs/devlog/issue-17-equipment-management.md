# Equipment and category management (#17)

Owner: Zhou Fanhao (@zfh-usedname) · Branch: `feat/zhoufanhao-issue17-equipment-management` · Baseline: `0be0a29af011862824c6aaab642512241764bcd9`

## 2026-10-15: management API and page

### What was built

The CUSTODIAN and ADMIN management API provides equipment list, registration, update, retirement, and category list/create/rename operations under `/api/v1/equipment/manage`. It validates all editable V005 fields, checks the account's current active role, returns field-level 400/409 problems, and uses optimistic equipment versions for updates and retirement. The category rename follows V005's foreign key and retains existing equipment associations. Normal equipment status changes remain with loan and maintenance; retirement rejects an active loan. Register, update, retire and category writes create audit entries in the same transaction.

The management page exposes the same operations with a category selector, editable condition and maximum duration, field errors, role-gated navigation, and retirement confirmation. The OpenAPI TypeScript schema and equipment contract were updated with the API. The contract question is posted on [issue #17](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/issues/17#issuecomment-6193352204) for the UC01 executor; no confirmation is claimed.

### Design decisions

The API uses the existing V005 equipment columns and category table, with no migration change. Serial number and maximum duration remain optional; null duration uses the global reservation limit. A retired item stays in the management list and the existing UC01 search exclusion continues to apply. The service locks an equipment row while checking version and active-loan retirement rules, and database unique constraints settle concurrent duplicate registration. Category records do not have UUID keys, so audit references use a stable name-derived UUID. Management mutations invalidate both management and UC01 equipment query caches.

### Problems and fixes

| Symptom | Root cause | Fix | Verification |
| --- | --- | --- | --- |
| The first isolated integration run lacked the application test database. | Existing unit tests in the full Maven verify need `SERMS_APP_TEST_JDBC_URL` in addition to the new management database. | Create both databases and set both test connection groups in the runner. | Full unit suite and management PostgreSQL integration passed. |
| One MockMvc test returned 400 rather than the expected 403. | The forbidden POST sent an invalid body, so request validation ran before method authorization. | Send a valid body to test the authorization boundary. | All six `EquipmentManagementPostgresIT` cases passed. |
| Navigation tests expected management to remain unavailable. | The feature enables its route and menu item. | Update the reserved-page and ADMIN menu assertions. | Full frontend suite passed 140 tests. |
| The complete UC03/UC05 run failed in an overdue assertion. | The shared integration database had more than one overdue loan, so the test's first-row assumption was false. | Match the loan in the response without assuming its position. | Complete UC03/UC05 rerun passed 30 integration cases. |
| The first hosted frontend check failed its route inventory. | The independent role-access matrix still classified `/manage/equipment` as future work. | Add the implemented page, menu position, management reads and denied registration write to the explicit matrix. | `node --test scripts/role-access.test.mjs`: eight passed; latest-HEAD CI must rerun. |

### Verification

On Windows 11 with PostgreSQL 18.6, Java 25 targeting release 17, and Node 24.22.0, `scripts/test-issue17-equipment.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'` passed 590 unit/database tests and six management PostgreSQL integration cases, with zero failures or skips. Reports are under `app/target/surefire-reports/` and `app/target/failsafe-reports/`; the full command output is in ignored `.cache/issue17-db-test-final.log`. `pnpm check:api`, `pnpm lint`, `pnpm typecheck`, `pnpm build`, and `pnpm test` passed; the component run had 140 passing tests. `pnpm exec playwright test e2e/equipment-management.spec.ts` passed one mocked API browser path in desktop and mobile projects. These checks used this baseline plus the uncommitted files on this branch. Hosted CI, UC01 contract confirmation, member review, and deployment have not occurred.

The complete `scripts/test-zhoufanhao.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'` rerun also passed 590 backend and 30 UC03/UC05 PostgreSQL integration cases after the overdue assertion fix. Its output is in ignored `.cache/issue17-regression-final.log`.

Existing V004-to-V005 upgrade-with-data checks in `EquipmentMasterDataPostgresTest` ran as part of the full suite (five passing cases). The dedicated management cases cover duplicate fields, concurrent registration, role and input rejection, category rename, audit rollback, and retirement. V005/V006 migration files were not edited.

### Deviations and follow-up

The V005 migration, UC01 read fields and demo seed work were transferred to issue #40 and are already in the baseline. The UC01 executor should confirm the management contract on issue #17 before merge. The integration owner should run combined-branch checks after merge. This code and entry were prepared and verified by Codex under the user's instruction; no human review or effort hours are claimed.
