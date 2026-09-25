# 通知接口 PostgreSQL 适配

对齐目标：[feature/wang-yuanmeng-sprint1-notifications](https://github.com/YukiOvOb/AGFP/tree/feature/wang-yuanmeng-sprint1-notifications)，固定验证提交 `01b50778701ed8a729039790238581bb59e929b5`。

本模块可作为依赖加入该分支的 Spring Boot 应用。保持 `NotificationRequest`、`LoanReminderGuard.check(long,...)`、`NotificationRequestStore.requestOnce(...)` 原有签名，适配本仓库 PostgreSQL/UUID 数据模型。未合并整个通知分支，也未改变其 MySQL/H2 独立运行方式；选择 `serms-postgres` profile 后使用共享数据库。

## 对齐结果

| 接口差异 | 实现 |
| --- | --- |
| UUID Loan 与上游 long loanId | 新增唯一、不可变的 loan.reminder_id；UUID loan_id 仍为业务主键，禁止截断/哈希 UUID |
| 字符串 recipient | app_user.notification_principal，默认 user_id 字符串，大小写敏感且唯一；通知外键验证身份 |
| MySQL ON DUPLICATE KEY | Primary 的 PostgresNotificationRequestStore 使用 ON CONFLICT DO NOTHING，保留首条内容与 ID |
| LoanReminderGuard 缺少真实实现 | 同一事务按 Equipment → Loan 加锁，重查归还、归属、dueAt 和 ReminderPolicy 时间窗口 |
| 旧 notification 与请求/投递模型 | V003 创建 notification_request、notification_attempt，状态按上游 PENDING/RETRY/DELIVERED/CANCELLED/FAILED |
| 独立 Flyway V1/V2 与共享 V001/V002 冲突 | profile 只扫描 db/serms-shared、db/serms-notification，使用 public.serms_flyway_history |
| 成功事务与通知原子性 | 沿用同步 NotificationRequested 事件；JdbcTemplate 与 JPA 共用 DataSource 和事务管理器 |

默认提前提醒窗口是 PT24H，可设置 `serms.notifications.due-soon-lead`；它是可配置集成默认值，不代表团队已批准业务策略。dueAt 恰好等于 now 时是 DUE_SOON，now > dueAt 才是 OVERDUE，与上游 ReminderPolicy 一致。维护或报废中的设备仍可能有 ACTIVE Loan，不能因此假定已归还。

## 本地验证

从本仓库根目录执行：

```powershell
git fetch origin feature/wang-yuanmeng-sprint1-notifications
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1
```

脚本核对五个契约源文件与固定提交逐字一致，将上游通知实现只读提取到忽略的 .cache 目录作为测试源码；运行真实 PostgreSQL 测试和 Flyway/JPA 校验，最后清理随机测试容器/匿名卷。测试不使用 mock LoanGuard 或 H2。

验证包含：真实业务事件与业务写入一起提交/回滚、并发 requestOnce、真实 Guard 边界、收件人归属、并发投递、归还先提交则取消提醒、已投递历史保留、外键及旧通知迁移。报告位于 `integration/notifications/target/surefire-reports`。

`src/contract/java` 仅用于编译/测试，不随适配 JAR 发布。上游新增或改变接口时，先更新固定提交、复核契约，再运行测试；不要随意修改夹具掩盖接口不兼容。

## 接入目标分支

先在本仓库验证并安装适配 JAR：

```powershell
mvn -B -f integration/notifications/pom.xml install "-Dmaven.test.skip=true"
```

此安装命令复用已完成的测试验证；日常验证仍运行上一节脚本。在目标通知应用的 app/pom.xml 中加入以下依赖，或应用同目录 `upstream-app-dependency.patch`：

```xml
<dependency>
  <groupId>sg.edu.nus.serms</groupId>
  <artifactId>serms-notification-postgres</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

然后配置 PostgreSQL URL、用户名、密码，并只启用 serms-postgres（不同时使用 local profile）：

```powershell
$env:SERMS_DB_URL='jdbc:postgresql://127.0.0.1:55432/serms?currentSchema=serms,public'
$env:SERMS_DB_USER='serms'
$env:SERMS_DB_PASSWORD='<local-password>'
# 在已加入依赖的目标通知分支根目录执行
mvn -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=serms-postgres"
```

目标应用扫描 sg.edu.nus.serms，能够发现适配配置；Primary Bean 接替默认 request store 和不可用 Loan guard。适配 JAR 带 PostgreSQL 驱动及 Flyway PostgreSQL 扩展依赖，不包含上游的通知类副本。原应用的其他 Controller、页面、鉴权和工作线程继续使用原接口。

## 数据库迁移

空数据库由应用按 V001 → V002 → V003 初始化。V001/V002 保持原文件及 checksum。V003 属于本适配模块，不放进基础数据库的默认初始化目录；未使用通知适配的环境仍运行 V002。

对于已手动创建的 V002 数据库，先停写、备份，确认 `serms.schema_version` 最大值为 2、没有 Flyway 历史，再在本仓库执行显式 baseline：

```powershell
# SERMS_DB_URL / SERMS_DB_USER / SERMS_DB_PASSWORD 已指向待升级 PostgreSQL
mvn -f integration/notifications/pom.xml flyway:baseline
```

再启动已接入的通知应用，只执行 V003。baseline 仅登记版本，不代替结构核验；不要对不明或其他应用数据库执行。禁止启用自动 baseline 或同时扫描上游 db/migration、db/mysql。Flyway 的 [baseline 语义](https://documentation.red-gate.com/flyway/reference/commands/baseline) 是跳过 baselineVersion 及以前的迁移。

V003 保留所有旧通知为只读 notification_legacy_v2，同时复制到新模型；SENT 映射 DELIVERED，PENDING/FAILED 保留语义，阅读时间和尝试次数保留，不伪造历史 attempt 行。Loan 外键指向 reminder_id，旧 UUID 关系仍可从 legacy_notification_id 追溯。

旧借用提醒按 sourceId=loan:<UUID>、period=once 计算与上游相同的 SHA-256 去重键，验证其与 Java NotificationRequest.dedupKey 完全一致；旧业务通知使用 sourceId=legacy-notification:<UUID>。若旧数据的语义重复导致唯一冲突，或正文超过上游 2000 字符限制，升级整体失败，需先人工核对，不能截断/删除历史后静默继续。不要同时运行旧 notification 写入者和新 request 模型。

旧 notification_principal 默认 UUID 字符串。身份提供方必须让 Principal.getName() 与此字段完全一致；如使用其他稳定身份代码，应在生成请求前显式建立唯一映射，不按邮箱或显示名猜测。旧通知已引用的身份不能任意改名。上游的临时 user 登录账号不能自动代表现有数据库用户。

## 生产者接入示例

在将来的借用服务 Spring 事务内，从真实 Loan 构造请求，然后发布原有同步事件：

```java
@Transactional
public void enqueueReminder(UUID loanId) {
    var request = factory.forLoan(loanId, NotificationType.OVERDUE, "once", "Please return the equipment.");
    events.publishEvent(new NotificationRequested(request));
}
```

factory 生成 long 映射、收件人和真实 dueAt。不要使用随机扫描 ID 或当前时刻作为 period；扫描周期和提醒选择仍由业务服务负责，Guard 会在投递前重新判断。不允许将监听器改为异步或仅 after-commit；[Spring JPA/JDBC 同事务](https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)要求共用 DataSource。

现有 database/ReservationRepository 自行管理 JDBC 事务，不可直接嵌入上述 Spring 生产者事务后假定原子性。未来跨模块写服务应使用同一 JpaTransactionManager + JdbcTemplate，或先重构为调用者管理连接。本次已通过真实生产者事务证明接入协议，没有把自主提交的 Repository 包装成虚假的嵌套事务。

## 边界与交接

已交付真实 Guard、请求存储适配、请求工厂、共享迁移、配置和最小依赖补丁。全量 Loan 扫描器、领用/归还服务、身份提供方、通知业务审计和线上部署仍属于团队后续集成。本次未修改远端分支或推送。接入方必须应用依赖补丁和 PostgreSQL profile 后才能使用适配；它不是独立 Web 应用。

来源：五份编译契约与测试中的通知实现来自同仓库 Wang Yuanmeng 提交 01b5077，保留原作者来源；Codex 用于适配及验证，人工 Review 待团队完成。
