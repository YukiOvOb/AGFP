# 完整 SERMS PostgreSQL 运行与迁移

默认启动和 `local` 开发配置统一使用 PostgreSQL 18，审批默认开启。唯一的生产迁移源为 `app/src/main/resources/db/migration`，按 V001→V004 执行。生产依赖不再包含 MySQL 或 H2，旧通知快速回归的 H2 依赖和迁移仅位于测试作用域。`serms-postgres` 名称保留为兼容别名。

## 新数据库

本地可用 `docker compose -f compose.dev.yaml up -d` 启动 PostgreSQL 18。Compose 只启动数据库，Flyway 由后端执行，避免容器初始化 SQL 与 Flyway 重复管理历史。`.env` 文件由 Compose 读取；直接启动 Java 时仍需将数据库参数设置为进程环境变量。开发库使用新的 PG18 专用卷，不直接复用旧版本数据目录。

准备空数据库和独立迁移账号，设置以下环境变量，再启动应用。开发环境若使用 HTTP，应显式关闭 Secure Cookie；测试或正式 HTTPS 环境保持开启。

```powershell
$env:SERMS_DB_URL='jdbc:postgresql://127.0.0.1:55432/serms?currentSchema=serms,public'
$env:SERMS_DB_USER='serms'
$env:SERMS_DB_PASSWORD='<本地账号密码>'
mvn -B -f app/pom.xml package -DskipTests
java -jar app/target/serms-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Flyway 在 `public.serms_flyway_history` 记录迁移，在 `serms` 建立业务表。执行顺序 V001 基础、V002 领域模型、V003 通知桥、V004 审批和版本对齐。DDL 应由迁移账号执行，部署完成后运行应用应改用最小权限账号。测试 fixture 不能作为演示或生产种子。

## 已存在数据库

停止写入并备份，检查 `serms.schema_version`、公共 Flyway 历史、通知表结构及历史审批码。V004 保持旧迁移校验和、决策主键和时间，原子修改枚举和版本类型。Migration 失败应调查真实数据，不能删除历史规避约束。

如果已有 `serms_flyway_history` 与仓库迁移一致，直接升级，由 Flyway 验证校验和。不要重建该表。

如果 V001/V002 原来通过 psql 手工执行且没有 Flyway 历史，只有在备份和结构核验完成后才能显式 baseline 到 2，再由应用执行 V003/V004。仓库通知整合 POM 已配置相同的 public 历史表，可使用：

```powershell
mvn -B -f integration/notifications/pom.xml flyway:baseline -Dflyway.baselineVersion=2
```

若 V003 也已经手工应用，应核验 notification_principal、reminder_id、notification_request/attempt 及旧通知迁移结果，然后明确 baseline 到 3。不能仅因某个表存在就猜测版本。`spring.flyway.baseline-on-migrate=false` 保留为默认值，避免未知数据库被自动接管。

不同的 MySQL/H2 通知数据库不能直接按 baseline 当成 PostgreSQL 的 SERMS 数据库。迁移通知历史必须通过已实现 V003 的显式历史转换，不采用双写。需要整合已有业务数据时由数据库负责人逐项核对。

## 数据约定

| 约定 | 当前整合实现 |
| --- | --- |
| Identity | `serms.app_user`、多角色、ACTIVE/DISABLED；notification_principal 默认为 user_id 的字符串 |
| Approval | APPROVED / REJECTED；不可变最终决定及拒绝理由 |
| Version | PostgreSQL BIGINT，Java long，触发器递增；所有更新检查预期版本 |
| Loan | UUID 主键；独立 bigint reminder_id 仅作通知接口桥接 |
| Overdue | 依据 ACTIVE 的 now 或 RETURNED 的 returned_at 与 due_at 比较，不增加存储状态 |
| Reservation | [startAt,endAt)，FULFILLED 仍占原区间 |
| Equipment | RETIRED > 活动维护 > 活动借用 > AVAILABLE |

数据库的约束和触发器不能替代 Session、RBAC、现场核验及端到端事务。UC03 Service 负责这些业务检查；禁止其他模块仅插入 Loan 来假装完成交接。
