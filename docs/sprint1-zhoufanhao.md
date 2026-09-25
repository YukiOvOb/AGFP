# Zhou Fanhao Sprint 1 首次交付记录

本文保存首次提交 2b09c6d 的历史状态。随后已按 SERMS 图文完成 V002 调整并通过 27 项测试，当前内容以 [V002 对齐记录](serms-database-alignment.md) 和 [领域模型](sprint1-domain.md) 为准。

依据本地 Team_07_SERMS_Project_Plan.docx 第 3、4、5.3、7、8 节。Sprint 1 为 9 月 12 日至 9 月 25 日，目标是登录、RBAC、搜索、可用性和预约冲突处理。文档只列团队 Sprint 1 目标与个人长期职责，没有单独的 Zhou Fanhao Sprint 1 任务清单；本分支据此落实数据库、领域模型及预约数据层集成基础。领用归还业务排在 Sprint 2，本次提供其设计准备。

分支：feat/zhoufanhao-sprint1-data-foundation。基于 main 的 9ddd40a。状态：本地实现和验证完成，等待团队 Review 与上层集成；不能按项目 DoD 标记 Done。

## Backlog 与验收追踪

以下条目是从项目计划拆分的本地工作清单，尚未同步团队任务板。工作量为相对规模，不作为实际工时。

| ID | User Story | 优先级 / 规模 | Acceptance Criteria | 实现与验证 |
| --- | --- | --- | --- | --- |
| ZFH-S1-01 | 作为开发者，我需要统一领域词汇与状态，避免模块解释不一致 | P0 / M | 定义六个领域对象、关系、状态及后续集成边界 | sprint1-domain.md：词汇、ERD、状态、分析/设计图 |
| ZFH-S1-02 | 作为集成人员，我需要可重复创建的数据结构 | P0 / M | 空 PostgreSQL 可一次性迁移；身份唯一、引用完整、时间合法 | V001、事务版本标记、真实数据库约束测试 |
| ZFH-S1-03 | 作为借用者，我需要可靠预约设备 | P0 / L | 设备可用；待审批也占位；并发冲突仅一笔成功；相邻预约允许 | ReservationRepository、排斥约束、并发测试 |
| ZFH-S1-04 | 作为借用者，我需要安全取消本人预约 | P1 / S | 他人不能取消；取消释放容量；重复取消不改变状态 | 带身份条件的 UPDATE 与测试 |
| ZFH-S1-05 | 作为团队成员，我需要可执行的集成交接 | P0 / M | 提供 Java/JDBC 契约、本地一键测试和 CI 配置 | database/README.md、test-sprint1.ps1、database.yml |

以上条目负责人均为 Zhou Fanhao；估算、任务板状态和实际工时需其本人确认，本记录不虚构会议或投入时长。

## 本地验证

环境：Windows、JDK 25（以 --release 17 编译）、Maven 3.9.10、Docker Desktop，真实 PostgreSQL 17 容器。CI 指定 JDK 17。

2026-09-25 首次真实数据库验证发现 PL/pgSQL 条件中的 CASE 表达式需要括号，迁移事务已完整回滚；修复后迁移和测试通过。测试覆盖 14 个用例，失败 0、错误 0、跳过 0。测试不使用 H2，也不依赖 mock 数据库。

覆盖范围：搜索和字面量转义、预约创建、审批占位、不同设备同时间、相邻/包含/部分重叠、取消归属、不可用设备、过期查询快照、禁用用户、非法时间/精度、直接 SQL 绕过尝试、状态不可逆、外键与唯一约束、两个连接竞争预约、设备维护修改与预约竞争。每次用例使用随机 UUID。

一键脚本已在全新、带随机密码的数据库上执行 clean verify，14 项全部通过，并确认自动清理容器。另已验证 Compose 配置有效、重复迁移安全拒绝且版本记录保留。

可复现入口：`scripts/test-sprint1.ps1`。机器报告由 Maven 生成在 `database/target/surefire-reports/`，CI 配置在每次运行后上传相同报告。target 不提交 Git；本记录保存结果和复现步骤，不编造 Pipeline 链接。

## 未完成的团队 DoD 条件

- 至少一位其他成员的 Code Review，以及 PR 链接。
- 远程 CI 三项现有安全检查及新增 database-test 实际通过的证据。
- 登录/RBAC/HTTP/页面接入、测试环境部署、Smoke Test 与验收演示。
- 团队确认暂定数据库选型和规则；项目名称差异仍按原计划由团队向老师确认。
- 真实 Sprint Review、会议记录、个人实际工时及成员签核。

本分支没有推送、合并 main 或修改线上环境，现有静态站发布入口保持原状。

## AI 使用记录

来源：OpenAI Codex，根据用户提供的项目计划及本地仓库生成 Java、SQL、测试、CI 和 Markdown 设计文档；用途为本次 Sprint 1 数据层实现与验证。自动检查结果见上文；人工检查人待 Zhou Fanhao 及 PR Reviewer 签核。未复制外部业务代码。

第三方组件：PostgreSQL 使用 PostgreSQL License；pgJDBC 使用 BSD-2-Clause；JUnit 使用 EPL-2.0。新增 Maven 依赖交由现有 dependency-review 与漏洞检查复核。数据库并发设计参考 PostgreSQL 官方 Range Types 文档，链接见数据模块 README。AI 生成内容遵循仓库后续确定的许可政策，不代替人工 Review。