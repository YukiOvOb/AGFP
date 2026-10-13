# Sprint Status and Risks

Member 1 updates after weekly meetings. Preserve snapshots, newest first, as Sprint evidence.

---

## Local Delivery Update 2026-10-07

Database default configuration finalized: normal/local startup uses PostgreSQL 18, production migrations are in app's Flyway directory, approval is enabled; MySQL/H2 removed from production. Clean builds/default-runtime integration passed, pending team review and web/app/db deployment.

Zhou Fanhao implemented UC03 backend/React pages, State delegation, shared PostgreSQL profile, V004 decision/version alignment, ApprovalDecisionPort, and test scripts. See [Verification](../zhoufanhao-validation.md). Shared migrations and real Identity schema mapping address local gaps in snapshot risks 1–3.

The remote repository was suspended; this work changed only the backup and saved local Git. Review, hosted CI, security, web/app/db test deployment, Smoke Test, and Sprint Review remain team work. UC03 stays locally implemented, pending Review/Testing, not full Done. See [Member checklist](../zhoufanhao-handoffs.md). Long-term duties follow the provided plan; historical snapshots are retained.

## Snapshot: 2026-10-05 (Sprint 2 in Progress)

### 1. Use-case Progress

| Use case | Owner | Backend | Frontend | Test bench | Location |
| --- | --- | --- | --- | --- | --- |
| UC01 Search/reservation | Member 1 | ✅ | ✅ | ✅ Backend 69 + frontend 15 + E2E | UC01 branch (this PR) |
| UC02 Approval | Member 2 | 🔶 Domain/service, no REST | ⬜ | 🔶 Unit tests | [Draft PR #8](https://github.com/YukiOvOb/AGFP/pull/8) |
| UC03 Checkout/return | Member 3 | ⬜ (schema/constraints on main) | ⬜ | 🔶 Database tests (PR #10) | main `database/` |
| UC04 Maintenance | Member 4 | 🔶 Domain/service/JDBC/report reads | ⬜ | 🔶 PostgreSQL integration | [Draft PR #9](https://github.com/YukiOvOb/AGFP/pull/9) |
| UC05 Overdue/notifications | Member 5 | 🔶 Notifications main; REST branch | 🔶 Inbox branch | 🔶 | main + `feature/wang-yuanmeng-platform-rest-react` |
| Platform: authentication/templates/React | Member 2 / 5 | 🔶 | 🔶 | 🔶 | `feature/wang-yuanmeng-platform-rest-react` (no PR) |

### 2. Branch Dependencies

```mermaid
flowchart LR
  main[main<br/>V002 · Notification foundation · CI]
  pr8["PR #8 UC02 foundation"]
  plat[platform-rest-react<br/>Authentication · REST · React]
  uc01[UC01 this branch]
  pr9["PR #9 UC04 maintenance"]
  main --> pr8 --> plat --> uc01
  main --> uc01
  main --> pr9
```

Suggested merge order: **PR #8 → platform-rest-react → UC01**. The platform branch's older main causes two small conflicts in `README.md` / `.gitignore`, already resolved in UC01 as a reference; UC01 passes all tests against latest main. PR #9 business files do not overlap, but both change `app/pom.xml` (same PostgreSQL driver version); retain one declaration manually.

### 3. Integration Risks (Team Decisions Required)

| # | Problem | Impact | Suggestion | Owner |
| --- | --- | --- | --- | --- |
| 1 | `app/` uses H2/MySQL; PostgreSQL migrations in separate `database/` | UC01–UC04 connect to real DB only in tests; deployed APIs unusable | Move V001/V002 into app Flyway, use PostgreSQL 18, remove `db/mysql` (technology stack §7) | Member 3 |
| 2 | Login queries `public.app_user` / `notification_principal`; V002 uses `serms` without column | Real login fails | Unify `serms`; recipients use `user_id` | Member 3 + Member 5 |
| 3 | V002 APPROVE/REJECT and integer versions differ from APPROVED/REJECTED and bigint contract | UC02 decision inserts fail CHECK | Add V003 (merged V002 cannot be edited) | Member 3 + Member 2 |
| 4 | Root Docker/Compose only deploy welcome page | Site lacks system | `web` + `app` + `db` (technology stack §7) | Member 5 |
| 5 | Shared Testcontainers base missing | UC01 `testsupport/SermsPostgres` and UC04 `Uc04PostgresTestSupport` duplicate facilities | Consolidate from `SermsPostgres`; use Flyway after migrations into app | Member 4 |
| 6 | 30 minutes / 7 days / 90 days / 3 reservations are provisional | Demo/report need consistent values | Sprint 2 review, configuration only | Team |
| 7 | Equipment four/five, maintenance five/six states (UC02 §9) | Report/implementation mismatch | Use V002 and explain in report | Member 3 + Member 4 |

### 4. Next Steps (Remaining Sprint 2)

1. Member 2: add REST/approval pages to PR #8 using this branch's tested `ReservationApprovalService`.
2. Member 3: integrate migrations (risks 1–3), start UC03.
3. Member 5: open platform PR; deploy three containers (risk 4).
4. Member 4: mark PR #9 ready; consolidate test base (risk 5).
5. Member 1: open UC01 PR and request member review; run real-DB demo after risk 1 is resolved.

### 5. Retrospective

Add meeting notes afterward:

| What worked | Improvements | Actions (owner, deadline) |
| --- | --- | --- |
| | | |
