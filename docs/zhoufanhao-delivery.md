# Zhou Fanhao 数据库与领用归还交付说明

本次工作依据 `Team_07_SERMS_Project_Plan.docx` 中 Zhou Fanhao 的长期职责和 5.3 分工完成，覆盖数据库、领域模型、系统集成和 UC03 设备领用归还。修改保存在当前备份目录及本地 Git。远程仓库恢复后，由用户自行推送、发起 Review 和安排部署。

## 已实现内容

全部工作和待团队对接事项的统一入口见 [Zhou 工作汇总](zhoufanhao-work-summary.md)。

数据库沿用原 PostgreSQL 领域模型，不重复建立 User、Equipment、Reservation 或 Loan。生产迁移统一放入 `app/src/main/resources/db/migration`，默认启动按 V001、V002、V003、V004 初始化完整应用；`database/` 保留历史 JDBC 回归模块。V004 将审批结果迁移为 `APPROVED` / `REJECTED`，将四类聚合的版本升级为 BIGINT，增加借用列表和设备查询索引。旧迁移未修改；原审批主键、审批时间和拒绝理由保留，迁移结束后恢复不可修改历史的触发器。

Identity 可按配置查询 `serms` 表，保持 UUID 与通知 principal 的映射。UC02 的 `ApprovalDecisionPort` 新增 PostgreSQL 适配器，使审批状态、不可变决定、审计及现有审批通知监听器进入同一事务。该适配器补齐数据库集成，审批规则仍由 Zhang Hanming 的模块负责。

UC03 提供待领用队列、实际领用、实际接收归还、借用列表和详情。CUSTODIAN 负责交接；BORROWER 只读本人借用。ADMIN 不自动获得交接权限，需要另行授予 CUSTODIAN。写操作同时核对 Session 权限及数据库中的有效账号、当前角色，操作人不从请求体获取。

领用要求 CONFIRMED、借用人匹配、现场身份核对、`startAt <= now < endAt`、设备 AVAILABLE、没有活动借用及维修单，并检查预约版本。成功后 Loan 为 ACTIVE、Reservation 为 FULFILLED、Equipment 为 ON_LOAN，附加审计和事务内事件。

归还要求 ACTIVE 及当前版本。正常、逾期、损坏和逾期且损坏均支持。损坏必须填写说明，并自动建立关联 Loan 的 OPEN MaintenanceCase 和维修审计。设备状态按 RETIRED > 活动维修 > 活动借用 > AVAILABLE 重算。报废或维修中的设备也能接收归还，不能因此自动恢复可用。FULFILLED 预约继续占用原时间段，提前归还不缩短预约。

React 的 `/loans` 页面提供库管队列、身份确认、归还条件与损坏说明、活动借用和历史分页。借用人看到自己的记录；409 冲突后关闭旧确认表单并刷新，必须重新选择记录。页面沿用 Session、CSRF 和 ProblemDetail 约定。

## 用例与设计交付

- [UC03 用例和设计模式](uc03-checkout-and-return.md)：正常与异常流程、验收条件、直接方案和 State Pattern 的比较。
- [UC03 接口合同](contracts/checkout-and-return.md)：字段、权限、状态码、事务和事件。
- `docs/diagrams/uc03-*.mmd`：可编辑的 Analysis Class、Analysis Sequence、Design Class、Design Sequence 和 Equipment/Loan State 图。
- [已有领域词典和 ERD](sprint1-domain.md)：十个领域实体、关系、状态和数据约束；当前增量说明见本文件。
- [成员对接清单](zhoufanhao-handoffs.md)：四位成员的输入、输出、联调场景和未完成团队事项。

## 本地验证方式

有 Docker Desktop 时，在根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1
```

无 Docker、已安装 PostgreSQL 18 时执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1 -PostgresBin 'C:/Program Files/PostgreSQL/18/bin'
```

脚本建立新的隔离数据库，先运行原 V002 数据库回归，再验证含历史决定的 V004 升级，随后运行全部应用回归和 UC03 完整 PostgreSQL 集成测试。测试实例在结束时停止，日志和临时数据保留在被 Git 忽略的 `.cache/`。测试报告位于 `database/target/surefire-reports`、`app/target/surefire-reports`、`app/target/failsafe-reports`。

完整应用默认使用 PostgreSQL 18，需要配置 `SERMS_DB_URL`、`SERMS_DB_USER` 和 `SERMS_DB_PASSWORD`；不需要额外的数据库 profile。HTTP 开发使用 `--spring.profiles.active=local`。已有 V002/V003 数据库应先备份并核对迁移历史；手工 psql 管理的数据库不能直接启用自动 baseline 来绕过检查，具体转换见 [迁移说明](serms-postgres-runtime.md)。

## 交付状态与证据边界

代码、设计源文件和自动化验证可以在本地完成；远程 PR、其他成员 Review、托管 CI、安全扫描、测试环境部署、Smoke Test、Sprint Review 和真实工时留痕仍需团队执行。项目计划要求全部适用 DoD 满足后才可标记 Done，因此本次本地交付不能替代这些证据。项目批准名称和 Presentation 日期仍由团队核实，不自行填写确认结果。

本次实现由 Codex 辅助生成和核查；依据是仓库现有代码、团队接口合同与提供的项目计划。没有复制外部业务实现。人类检查人、实际工时、PR 和部署证据由团队补录；不虚构会议或 Review 记录。
