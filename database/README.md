# SERMS Sprint 1 数据模块

负责人：Zhou Fanhao。模块为 Java 17/JDBC 领域和数据层，供登录、设备搜索与预约服务集成。PostgreSQL 17 是本分支的初始选型，团队需在 Review 时确认。现有根目录 Docker 部署仍运行原欢迎页。

## 一键验证

Windows 安装 JDK 17+、Maven、Docker Desktop，在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1
```

脚本创建临时 PostgreSQL，使用随机密码和本机随机端口，应用迁移后执行真实数据库测试，最后删除本次创建的测试容器及匿名卷、恢复环境变量。不会使用已有数据库。结果位于 `database/target/surefire-reports/`，JAR 位于 `database/target/serms-data-0.1.0-SNAPSHOT.jar`。

Linux/CI 可对空的专用 `serms_test` 数据库执行：

```sh
psql -v ON_ERROR_STOP=1 -f database/src/main/resources/db/migration/V001__sprint1.sql
# 用实际测试环境设置上述 psql 的 PGHOST/PGUSER/PGDATABASE/PGPASSWORD。
# JDBC 同样通过环境变量读取连接信息：
export SERMS_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/serms_test
export SERMS_TEST_DB_USER=serms_test
export SERMS_TEST_DB_PASSWORD="$PGPASSWORD"
mvn -B -f database/pom.xml clean verify
```

未配置数据库会明确失败，不会跳过集成测试。测试只允许数据库名称为 `serms_test`；每个用例使用独立 UUID，避免共享固定数据。测试账户的占位 hash 不能用于登录。

## 开发数据库

在当前 PowerShell 会话设置 `$env:SERMS_DB_PASSWORD` 为本地开发密码，然后运行：

```powershell
docker compose -f database/compose.yaml up -d --wait
```

连接地址为 `jdbc:postgresql://127.0.0.1:55432/serms`，用户名为 `serms`。可用 `SERMS_DB_PORT` 覆盖端口。密码不写进仓库；根目录的服务器 .env 不受此配置影响。

初始化脚本仅在空数据卷第一次启动时运行。V001 在事务中执行，失败全部回滚；再次执行会拒绝已有 schema，不会清空数据。后续迁移使用新的 V002 等版本，不能修改已应用脚本；上线前备份、在测试副本验证并采用向前修复。停止容器用 `docker compose -f database/compose.yaml down`，保留数据卷。本地 compose 用户拥有迁移权限，生产需区分迁移用户和最小权限应用用户。

## Java 集成契约

通过连接池或 PostgreSQL DataSource 注入 `ReservationRepository(DataSource)`。每次操作借用并关闭一个连接。DataSource 返回的连接必须处于默认自动提交模式；book 在独立事务中提交或回滚，不能加入调用者的外层事务。当前接口：

| 方法 | 行为 |
| --- | --- |
| findAvailable(query, start, end) | 按名称、资产编号、分类进行大小写不敏感的字面量包含搜索；最多返回按资产编号排序的 100 项 |
| book(authenticatedUser, equipment, start, end) | 从数据库读取审批标记，自动写入 PENDING 或 CONFIRMED，返回 Reservation |
| cancel(authenticatedUser, reservation) | 仅取消本人仍有效的预约；返回 false 表示不存在、非本人或已结束 |

时间以 Instant 输入、timestamptz 保存，精度最多微秒，区间为左闭右开 [start,end)。显示时区由页面处理。不要将可用性查询结果当作锁或预约凭据。借出中的设备在 Sprint 1 暂不接受新的未来预约，这是保守规则，待团队确认是否放宽。

Service 必须从登录会话提供用户 ID，不得信任请求体中的 userId；登录、密码哈希算法、HTTP Controller、RBAC 授权属于上层集成。app_user 为其提供标准化唯一邮箱、密码哈希、角色及启用标记，User 投影不暴露 hash。只有管理服务可改角色和设备状态，数据库公共账号不是终端用户身份。

| SQLSTATE / Java 错误 | 上层建议 |
| --- | --- |
| 23P01 | HTTP 409：预约时段冲突，请重新查询 |
| 23503 | HTTP 404 或业务校验失败：引用不存在 |
| 23514 | HTTP 422：设备/用户不可用、时间或状态不合法 |
| 23505 | HTTP 409：唯一标识重复 |
| IllegalArgumentException / NullPointerException | HTTP 400：时间顺序、精度或必填项非法 |
| 40P01 / 40001 | 整个事务有限重试；不能把所有 SQL 异常当冲突 |
| 其他 SQLException | 内部记录关联 ID，返回通用服务错误，不将 SQL 信息返回客户端 |

取消返回 false 的统一响应可以避免向其他用户泄露预约是否存在。不要自动重试连接中断后的提交，提交结果可能未知；客户端幂等键是后续接口设计事项。

数据库通过设备行锁串行化预约与设备状态修改，并以 exclusion constraint 最终拒绝同一设备的有效预约重叠。PENDING 也占位，CANCELLED/REJECTED 释放容量；审批时再次检查设备与用户状态。已有预约遇到设备故障后的通知、取消或重新安排，由 Sprint 2 故障流程协调。锁顺序统一为设备后用户。触发器只保证数据规则，不代替身份认证或审批权限。

## 交接

- 领域词汇、ERD、状态与分析/设计图：[领域设计](../docs/sprint1-domain.md)。
- 任务、验收项和证据：[个人交付记录](../docs/sprint1-zhoufanhao.md)。
- CI 工作流：[database.yml](../.github/workflows/database.yml)，使用真实 PostgreSQL 并上传测试报告。
- PostgreSQL 区间排斥约束依据：[官方 Range Types 文档](https://www.postgresql.org/docs/17/rangetypes.html)。
- JDBC 依赖：[官方 42.7.13 发布记录](https://jdbc.postgresql.org/changelogs/2026-07-06-42.7.13-release/)。