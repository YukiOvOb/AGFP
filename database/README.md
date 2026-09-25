# SERMS 数据模块

负责人：Zhou Fanhao。Java 17/JDBC + PostgreSQL 17；当前 schema 为 V002。根据 SERMS 原始 ER 图已具备用户、多角色、设备、预约、审批决定、借用、维修工单、通知与审计十个业务表。现有 Java Repository 实现搜索、预约和本人取消；其他表为后续业务服务提供持久化基础。

设计与来源见 [领域模型](../docs/sprint1-domain.md)，调整与测试记录见 [V002 对齐记录](../docs/serms-database-alignment.md)。

## 一键验证

要求 Docker Desktop、JDK 17+、Maven。从仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1
```

脚本创建本机随机端口、随机密码的临时 PostgreSQL；验证空库 V001→V002 初始化，以及带旧角色、禁用账户、旧状态和预约记录的 V1→V2 升级；运行全部 27 项集成测试后自动清理容器/匿名卷并恢复环境变量。

报告位于 `database/target/surefire-reports/`，构建产物位于 `database/target/serms-data-0.1.0-SNAPSHOT.jar`。测试使用真实 PostgreSQL，不使用 H2；未配置专用测试数据库会失败而非跳过。

Linux/CI 的等价流程（PGHOST/PGUSER/PGPASSWORD 指向专用测试服务器）：

```sh
export PGDATABASE=serms_test
psql -v ON_ERROR_STOP=1 -f database/src/main/resources/db/migration/V001__sprint1.sql
psql -v ON_ERROR_STOP=1 -f database/src/test/resources/v1-upgrade-fixture.sql
psql -v ON_ERROR_STOP=1 -f database/src/main/resources/db/migration/V002__align_serms_model.sql
createdb serms_empty
for migration in database/src/main/resources/db/migration/V*.sql; do
  psql -d serms_empty -v ON_ERROR_STOP=1 -f "$migration"
done
export SERMS_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/serms_test
export SERMS_TEST_DB_USER="$PGUSER"
export SERMS_TEST_DB_PASSWORD="$PGPASSWORD"
mvn -B -f database/pom.xml clean verify
```

`v1-upgrade-fixture.sql` 仅限测试，不进入开发或生产初始化。JUnit 限定数据库名为 serms_test；测试仍应只在一次性实例执行，避免残留夹具被误当真实业务数据。

## 开发初始化与已有数据库升级

在当前 PowerShell 设置本地密码：

```powershell
$env:SERMS_DB_PASSWORD = '<your-local-password>'
docker compose -f database/compose.yaml up -d --wait
```

默认连接 `jdbc:postgresql://127.0.0.1:55432/serms`，用户名 serms，可设置 SERMS_DB_PORT 改端口。空卷第一次启动按文件名顺序应用 V001、V002。Compose 停止用 `docker compose -f database/compose.yaml down`，保留数据卷。

已有 V001 数据卷不会自动执行新 SQL。先备份并停止业务写入，再只应用 V002：

```powershell
docker compose -f database/compose.yaml cp database/src/main/resources/db/migration/V002__align_serms_model.sql db:/tmp/V002.sql
docker compose -f database/compose.yaml exec -T db psql -U serms -d serms -v ON_ERROR_STOP=1 -f /tmp/V002.sql
docker compose -f database/compose.yaml exec -T db psql -U serms -d serms -c "SELECT version, description FROM serms.schema_version ORDER BY version"
```

命令均从仓库根目录执行。不要重跑 V001、删卷或修改已应用的迁移。V002 是完整事务，任一步失败自动回滚；遇到旧 FULFILLED 重叠应人工核对历史。再次运行 V002 会拒绝已迁移结构，不覆盖数据。生产部署仍需迁移账号与最小权限应用账号分离。新表的历史数据不自动生成。

V002 重命名列并改变 Java record 的访问器，不兼容 V001 模块；应在同一维护窗口迁移数据库和发布对应 Java 代码。当前根目录站点仍为静态欢迎页，没有连接开发数据库。

## Java 接口

注入 `DataSource` 创建 `ReservationRepository`；新连接应处于默认自动提交模式，Repository 自行管理事务，不能嵌套到调用者未提交事务中。

| 方法 | 行为 |
| --- | --- |
| findAvailable(query,start,end) | 按设备名称、资产编号、分类进行字面量包含搜索；最多 100 条，按资产编号排序；检查活动借用、维修与区间冲突 |
| book(actor,equipment,start,end) | 生成请求关联 ID，不填用途 |
| book(actor,equipment,start,end,purpose,requestId) | 从设备决定 PENDING_APPROVAL/CONFIRMED，原子写预约与成功审计 |
| cancel(actor,reservation) | 仅本人有效预约；不存在、非本人或已结束返回 false |
| cancel(actor,reservation,requestId) | 同上；账户需 ACTIVE；成功取消与审计原子提交 |

参数 actor 必须来自可信认证会话，角色授权在 Service 校验。管理员代取消等能力尚未由本 Repository 暴露。失败操作的独立审计由上层在回滚后负责。requestId 是追踪标识，不是通用幂等键；不要在网络中断、提交结果未知时无条件重试。

Reservation 属性为 reservationId、requesterId、equipmentId、startAt、endAt、status、purpose、version。Equipment 使用 equipmentId 与 version；User 使用 accountStatus 和不可变 roles 集合，不包含密码哈希。

## 业务与错误约定

- 区间 [start,end)，最大微秒精度；支持尚未结束的当前时段。
- PENDING_APPROVAL、CONFIRMED、FULFILLED 均占位；提前归还仍保留原预约时段。
- ON_LOAN 只有当前借用未逾期，且新预约不早于到期、无活动工单和预约冲突时可预约。
- 数据库状态与多角色定义以 [领域模型](../docs/sprint1-domain.md) 为准。
- SQLSTATE 23P01：时间冲突（可映射 409）；23503：无效引用；23514：状态/字段/业务约束失败；23505：唯一性冲突。
- Java IllegalArgumentException/NullPointerException：输入非法。40P01/40001 可有限重试整个事务；其他 SQL 错误由服务记录 requestId 并返回通用错误，不暴露 SQL 或凭据。
- 所有相关写服务先锁 Equipment，再按序处理 Reservation、Loan、MaintenanceCase。version 更新需配合 WHERE version 与行数检查。
- 数据库约束不能完成 RBAC、现场交付核验、设备状态重算或完整领还事务。完整责任边界见领域文档。

CI 工作流：[database.yml](../.github/workflows/database.yml)。参考：[PostgreSQL 区间约束](https://www.postgresql.org/docs/17/rangetypes.html)、[pgJDBC 42.7.13](https://jdbc.postgresql.org/changelogs/2026-07-06-42.7.13-release/)。