# Zhou Fanhao 工作与业务对接汇总

截至 2026-10-07，Zhou Fanhao 负责的 UC03 设备领用归还，以及数据库、领域模型和系统集成的本地实现与验证已完成。默认启动现为 PostgreSQL 18，审批持久化的阻塞已解决。网站部署、其他成员 Review、托管 CI、安全检查和团队验收仍待对接，不能把本地实现等同于全部 Definition of Done 已满足。

本汇总依据提供的 Team 07 项目计划、当前本地 main 代码和保存的测试证据。既有领域与数据库基础被沿用并整合；UC01 的搜索预约功能属于 Shi Wenqi，UC02 的审批规则、接口与页面属于 Zhang Hanming，不计为 Zhou 新开发的业务。

## 已完成工作

### 数据库和运行配置

- 整合已有 User、Role/UserRole、Equipment、Reservation、ApprovalDecision、Loan、MaintenanceCase、Notification 和 AuditLog 等领域关系与约束。
- 默认启动和 local 开发配置统一使用 PostgreSQL 18，审批默认开启；serms-postgres 名称保留兼容。
- 唯一生产迁移目录为 `app/src/main/resources/db/migration`，包含 V001–V004。迁移移动后与原 Git blob 一致，没有改写已存在的迁移内容。
- 新增 V004，将审批决定统一为 APPROVED/REJECTED，四类聚合的版本升级为 BIGINT，并增加借用查询索引。升级保留审批主键、时间和拒绝理由，并恢复历史不可修改的触发器。
- 生产包移除 MySQL 驱动、Flyway MySQL 和旧生产迁移；H2 只在隔离测试依赖与测试资源中使用。
- 提供 PostgreSQL 18 开发 Compose、环境变量模板和已有数据库转入 Flyway 的说明。开发库使用新的 PG18 数据卷，后端负责执行迁移。

### UC03 领用归还后端与页面

- 提供待领用队列、实际领用、实际接收归还、借用列表及详情五项接口，以及 React `/loans` 页面。
- CUSTODIAN 执行现场交接；BORROWER 只能查看本人借用。ADMIN 不自动获得交接权限。
- 领用核对预约 CONFIRMED、借用人身份、有效账号和角色、时间窗、设备状态、活动借用/维修及预期版本。
- 领用成功后同一事务生成 ACTIVE Loan、将预约改为 FULFILLED、设备改为 ON_LOAN，并记录操作人与实际时间。
- 支持正常、逾期、损坏及逾期且损坏归还；损坏说明必填，自动创建关联 Loan 的 OPEN 维修单及维修审计。
- 设备状态按报废、活动维修、活动借用、可用的优先级重算。报废或维修设备可接收归还，不能因此恢复可用；提前归还不释放原预约时段。
- 页面包含身份确认、归还条件与说明、活动借用和历史分页、逾期提示及版本冲突后的刷新重选。

### 跨模块集成与审计

- Identity 对齐 serms schema，保持用户 UUID 与通知 principal 的映射。
- 补齐 UC02 的 ApprovalDecisionPort PostgreSQL 实现，使预约变更、不可变决定、成功审计和现有审批通知写入同一事务；审批本身的业务规则仍由 Hanming 负责。
- 损坏归还接入 Liu 的 MaintenanceService；已报废设备的损坏接收保留关联维修记录及历史，不恢复设备。
- 集成 Wang 的 PostgreSQL 通知适配器、LoanNotificationRequestFactory 与 LoanReminderGuard。已归还借用会在投递前重新检查并抑制提醒。
- 发布 CHECKED_OUT/RETURNED LoanEvent，提供借用、设备、借用人、操作人、到期时间、迟还及维修关联事实。
- 统一 Equipment→Reservation→Loan→MaintenanceCase 锁顺序，结合版本条件更新和数据库约束控制并发。业务、维修和成功审计一起提交，失败一起回滚。

### 领域设计与过程文档

- 沿用领域词典、ERD、状态定义和系统关系，补充 UC03 正常与主要异常流程、接口合同和验收条件。
- 提供可编辑的 Analysis Class、Analysis Sequence、Design Class、Design Sequence 和 Equipment/Loan State Mermaid 源文件。
- 比较直接 if/else、完整 State 类层次和 enum 状态对象，采用 EquipmentLoanState 将设备状态行为集中委托；没有额外构造 OverdueState。
- 更新 Backlog、Sprint 本地状态、运行迁移说明、测试脚本和 CI 配置，保存验证摘要及 AI 辅助来源记录。

## 已有验证证据

| 检查 | 最新结果 | 范围 |
| --- | --- | --- |
| 数据库回归 | 27 项通过 | V002 约束、历史升级和并发基础 |
| 应用回归 | 536 项通过 | 完整干净构建重新执行 |
| 默认 PostgreSQL 应用集成 | 20 项通过 | 不指定 ActiveProfiles，验证默认配置、真实登录、审批领用、归还、权限、并发、回滚和提醒 Guard |
| 固定版本通知合同 | 12 项通过 | PostgreSQL 通知及旧历史兼容性 |
| 前端测试 | 79 项通过 | 前次业务实现验证；数据库收尾未修改前端功能 |
| 前端构建、Lint 与 OpenAPI | 通过 | 生产构建、静态检查和类型一致性；数据库收尾后复查 OpenAPI |
| 生产 JAR 与迁移内容 | 通过 | 无 MySQL/H2 依赖及测试迁移，只含四份生产迁移，原迁移内容未变化 |

证据见 [验证记录](zhoufanhao-validation.md)、[默认 PostgreSQL 摘要](evidence/uc03/default-postgres-summary.json) 和 [前次业务测试摘要](evidence/uc03/test-summary.json)。本汇总没有重新运行测试，也不宣称安全扫描、覆盖率门槛或部署验收已通过。

## 需要其他成员对接的业务

| 对接成员 | Zhou 已交付 | 对方需要完成或确认 | 联调通过的条件 |
| --- | --- | --- | --- |
| Shi Wenqi，UC01 与 CI/CD | 默认 PostgreSQL 后端、共享迁移、领用接口和开发数据库配置 | 接入 web/app/db 部署、服务器数据库参数、前端代理、HTTPS 和 Smoke Test；核对取消与领用竞争、时间窗和预约占用 | 搜索→预约→领用→归还可在测试环境操作；取消与领用只能一个成功；提前归还不释放原时段 |
| Zhang Hanming，UC02 与质量控制 | ApprovalDecisionPort、审批码与 BIGINT 对齐、事务审计及集成测试 | Review 持久化适配器与测试；核对自审批、拒绝理由、不可变决定、版本冲突；安排质量与安全检查 | 另一个 APPROVER 批准后可领用；拒绝释放时段；决定、状态、审计及通知一致，无部分写入 |
| Liu Tongyao，UC04 与 Sprint 管理 | 损坏归还产生关联 OPEN Case、维修审计和设备状态重算 | 联调分配维修→开始→修复/报废；检查维修中或报废设备归还；更新 Sprint 与实际工时 | 损坏后阻止预约；全部维修完成才按持有事实恢复状态；报废设备接收归还后仍为 RETIRED |
| Wang Yuanmeng，UC05 与架构页面 | PostgreSQL 通知桥、UUID principal、LoanEvent、提醒工厂与 Guard | 接入或核验逾期扫描生产者、稳定提醒周期和去重；核对页面、时区、事件消费者及外部效果提交后处理 | 重复扫描不重复提醒；归还后待发提醒被抑制；recipient、reminderId 和 dueAt 来自保存的业务事实 |

逾期扫描、提醒周期、外部通知消费者和 UI 整体一致性仍由 Wang 对接。Zhou 已提供所需数据与接口，不等于这些上层业务已替对方完成。

## 尚待团队完成的事项

1. Shi 与 Hanming 商定“过期未审批预约自动取消”规则，包括截止时间、取消时机、通知、审计及与审批的竞争处理。R1.11 当前未实现；已有过期标记、禁止批准和人工拒绝不能代替自动取消。
2. Shi 完成网站 web/app/db 部署；根目录生产部署目前仍为欢迎页。数据库切换已解除后端配置阻塞，不代表网站已经上线。
3. 各业务负责人 Review 本地修改并完成端到端联调；Hanming 组织安全、依赖及覆盖率检查。
4. 团队确定演示账号、角色授予及现场身份核验方式；已有真实数据迁移前完成备份、版本核对和权限配置。
5. GitHub 恢复后由用户推送本地提交，完成 PR、托管 CI、部署与 Smoke Test，补充 Sprint Review、会议及真实工时证据。
6. 团队确认项目批准名称和最终 Presentation 日期，不将待确认事项写成已确认。

## 本地交付位置

| 本地提交 | 内容 |
| --- | --- |
| `5559503` | UC03 领用归还、数据库业务整合、设计源文件与测试 |
| `751ca0d` | 默认 PostgreSQL 18、生产依赖清理、统一迁移及完整验证 |

运行步骤见 [PostgreSQL 运行说明](serms-postgres-runtime.md)，接口字段与权限见 [UC03 合同](contracts/checkout-and-return.md)，详细联调顺序见 [成员对接清单](zhoufanhao-handoffs.md)。以上代码提交均保存在本地 main，远程状态未验证。
