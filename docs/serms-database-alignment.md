# SERMS 图文对齐与 V002 调整记录

日期：2026-09-25。负责人：Zhou Fanhao。当前分支：feat/zhoufanhao-sprint1-data-foundation。

用户要求参考 SERMS 目录中的图和文件调整数据库。本次以 ER 图、领域说明、设备/借用状态设计和集成说明为依据，对首个本地提交 2b09c6d 进行后续修订；保留 V001 与既有 Git 历史，通过 V002 前向升级。

## 对照与调整

| 原始设计要求 | V001 差异 | V002 与配套代码 |
| --- | --- | --- |
| User 与 Role 多对多 | 单个 role 字段，缺少 CUSTODIAN，使用 TECHNICIAN | role/user_role；五种角色；TECHNICIAN 映射 MAINTAINER |
| 实体主键、requester_id、start_at/end_at | 通用 id/user_id/starts_at/ends_at | 按图重命名，保留原 UUID 与引用 |
| account_status、purpose、version | active 布尔值，无用途和版本 | 迁移 ACTIVE/DISABLED；增加 purpose；设备、预约、Loan、工单有 version |
| PENDING_APPROVAL、UNDER_MAINTENANCE | PENDING、MAINTENANCE | 迁移已有状态并更新约束、Java 枚举和查询 |
| FULFILLED 继续占原时段 | 排斥约束仅含待审批和已确认 | FULFILLED 纳入占位，提前归还不释放原预约 |
| ON_LOAN 可预约未逾期借用到期后的未来时段 | 全部拒绝 | 查询和写入共用数据库可用性函数，考虑实际 Loan 和活动工单 |
| 审批决定不可覆盖、禁止自批 | 无表 | approval_decision、预约唯一、理由与自批检查、不可变记录 |
| 借用与领还经办人 | 仅规划 | loan、归还字段、时间与损坏说明、单预约借用与单设备活动借用保护 |
| 维修工单 | 仅规划 | maintenance_case、正式状态、分派/结单字段及 Loan 同设备校验 |
| 通知重试、阅读和去重 | 仅规划 | notification、恰好一个业务关联、唯一 dedup_key、投递时间和计数约束 |
| 重要操作审计 | 无表和写入 | 追加式 audit_log；已实现的预约/取消与成功审计同事务提交 |

app_user 保留物理表名避免 SQL USER 名称冲突；账户状态具体代码在原 ER 图中未列出，采用 ACTIVE/DISABLED。图中未列出的既有 equipment.created_at 和 schema_version 保留。

中文词汇表把区间重叠公式误写为“或”，集成说明和英文版均为 AND，本次按后者实现。attempt.md 的续租/自批复仅为议题标题，继续采用详细设计中的无续借、禁止自批规则。

## 验证结果

执行：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1`。

- 空 PostgreSQL 17 数据库顺序应用 V001、V002：通过。
- 先写入 V1 旧状态、旧角色、禁用用户和预约，再应用 V002：通过，ID、hash 和业务记录保留。
- Java 使用 --release 17 编译，Maven clean verify：通过。
- 真实数据库集成测试：27 项，失败 0、错误 0、跳过 0。
- 并发预约：同设备冲突仅一笔提交；并发借用：不同预约对同设备最多一条 ACTIVE Loan。
- 测试还覆盖：多角色与重复授权、FULFILLED 占位、ON_LOAN 未来预约、逾期阻止预约、晚还和损坏同时记录、审批与维修约束、通知单关联与去重、审计不可改写及失败回滚、版本条件拒绝陈旧更新。
- 一键脚本在结束时清理本次测试容器和匿名卷。报告位于 database/target/surefire-reports，CI 使用相同迁移/夹具并上传报告。

测试中的直接 SQL 夹具专门验证数据约束，不代表已经实现完整领还/审批服务。外层服务仍负责角色授权、实际领取人、领用时间窗口、跨实体设备状态重算、失败操作独立审计和通知任务。State Pattern 仍为候选，未虚报为完成实现。

GitHub CI、同伴 Review、测试环境部署及全流程 Smoke Test 尚未执行。本次仅修改仓库并本地提交，没有操作线上数据库或推送远端。

## 迁移注意事项

V001 文件保持不变，V002 保留已有数据并在单个事务内完成升级。现有数据卷需手动应用 V002；新数据卷自动按顺序初始化。Java 字段访问器及表列名已变化，须配套迁移与发布。若历史 FULFILLED 已与其他有效预约重叠，迁移会拒绝并回滚，需要人工核对，不自动删除数据。运行命令与责任边界见 [数据库 README](../database/README.md)。

## 参考文件校验值

原始参考位于 C:/Users/haohao/Desktop/SERMS；未修改这些文件。下列 SHA-256 用于标识本次参考版本。

| 文件 | SHA-256 |
| --- | --- |
| SERMS_Domain_Glossary_and_ERD.md | 69509CB1EC4180D7E5478DD9C796A78607EC16EBDCDE99C1FC50328440D57FF0 |
| SERMS_Equipment_and_Loan_States.md | CB80860CDD1805A1AB672F5B1B0DFBD1493ADAE765D23B5F3B7AE96972D99387 |
| SERMS_System_Integration.md | 052B96F461C4E0491FFD2FCCD71CD0B51AB4C93A758776578C7C0BD995CC4FAA |
| English_Deliverables/Domain_Glossary_EN.md | AC6F90505C50A0A443783679EFBEAC5374ED70381FB181835FA41E26A97C8694 |
| English_Deliverables/State_Pattern_Candidate_Final.md | D0952824F5C71C797DE7808E3F2A1A930BBE3777C23BFAB206B7D83898F53329 |
| Diagrams/ER Diagram.png | 00922E060AF3C90C8FB9D12672FB51B72BE169B591BAD6FBA2C24110A7244A73 |
| Diagrams/Class Diagram.png | 09C015AB45BA2351D52641E2F87426B9002EB931982B2A2484156C9EEBA0FBD6 |
| Diagrams/Pickup Sequence Diagram.png | 5369AA28BFFB6CC737E4106E4132365045CF585D36CEDD231F15D892937D1895 |
| Diagrams/Return Sequence Diagram.png | 00E5CA9089CE0DA6401BABE477DBB7DE0F7F3C5ACA4B3A9B387308134BB46A83 |

AI 使用：Codex 用于对照图文、生成迁移与测试、执行本地验证。人工检查人仍待 Zhou Fanhao 和团队 Reviewer 签核；未虚构实际工时、会议或 Review 记录。