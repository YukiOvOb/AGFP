# 与 Wang Yuanmeng 通知分支的接口对齐记录

日期：2026-09-25。目标仓库 YukiOvOb/AGFP，分支 feature/wang-yuanmeng-sprint1-notifications，验证提交 01b50778701ed8a729039790238581bb59e929b5。

本次以独立集成模块对齐现有公开接口，在保留 PostgreSQL 和 UUID 领域主键的同时，为上游通知服务提供 long Loan ID、稳定收件人标识、真实借用复查和 PostgreSQL 请求去重实现。当前分支不包含通知应用全量合并，接入方只需增加适配依赖并启用 serms-postgres profile。

## 本地交付

- integration/notifications：可消费的适配 JAR，保持上游五份编译契约原样。
- PostgresLoanReminderGuard：强制参与当前事务，按 Equipment → Loan 加锁并复查状态、身份、dueAt 和提醒时间窗口。
- PostgresNotificationRequestStore：与上游 requestOnce 同签名，PostgreSQL 原子去重并保留第一条内容。
- LoanNotificationRequestFactory：从 Loan UUID 获取实际 reminder_id、身份映射和到期时间。
- V003：新增稳定映射、notification_request 和 notification_attempt；保留只读 V2 通知并迁移历史与去重身份。
- PostgreSQL profile：隔离 Flyway 迁移位置，使用 public.serms_flyway_history；不重用通知分支独立的 V1/V2/V3 版本。
- 最小 app/pom.xml 依赖补丁、操作说明和契约 CI 工作流。

V001、V002 及原数据模块代码未修改。测试脚本的就绪检查改用 TCP，避免把 PostgreSQL 初始化期间的临时进程误认为正式服务。

## 验证证据

环境：Windows、Java 25（release 17）、Maven 3.9.10、PostgreSQL 17.11、与上游一致的 Spring Boot 3.5.16。

| 检查 | 结果 |
| --- | --- |
| 与指定提交的五个公开契约逐字比较 | 通过 |
| Flyway 空库 V001/V002/V003 初始化及 Hibernate validate | 通过 |
| 带 V2 通知数据的显式 baseline 2 后升级 V003 | 通过 |
| PostgreSQL 通知集成测试 | 12 项，失败 0、错误 0、跳过 0 |
| 基础数据库回归测试 | 27 项，失败 0、错误 0、跳过 0 |
| 适配 JAR 不包含重复上游通知类 | 通过 |
| 最小依赖补丁针对目标提交的 POM 可应用 | git apply --check 通过 |
| 适配 JAR 本地 Maven 安装 | 通过 |

12 项测试直接使用固定提交中的通知 Entity、Service、Repository、EventListener 和 DeliveryStore，没有 mock LoanReminderGuard，覆盖并发请求去重、生产者回滚、投递/已读、并发投递、归还先提交的真实锁竞争、历史保留、身份大小写/归属、dueAt 与时间边界、外键、旧提醒 SHA-256 去重键与 Java 契约一致。

报告分别在 integration/notifications/target/surefire-reports 和 database/target/surefire-reports。测试日志及上游测试源码位于忽略的 .cache，不提交生成物。上游原有 H2/MySQL/UI 测试套件、远程 CI、其他成员 Review 和生产部署不在本地通过声明内。

## 接入及剩余职责

具体依赖补丁、环境变量、迁移命令、事务生产者示例和 identity 映射见 [接入文档](../integration/notifications/README.md)。

适配模块不是独立网页应用。通知分支需加入该依赖并启用 profile，原有临时登录账号也需与 app_user.notification_principal 映射一致。全量逾期扫描、领还业务服务、正式身份/RBAC 和通知审计依旧由后续集成完成。

来源与贡献：上游契约和测试用实现来自 Wang Yuanmeng 提交 01b5077，原代码未修改。Codex 辅助生成数据库适配、测试与文档；人工复核待 Zhou Fanhao 和接口负责人完成。没有伪造远程 Pipeline、Review 或工时记录。