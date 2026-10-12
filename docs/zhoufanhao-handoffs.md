# Zhou Fanhao 与其他成员的业务对接

本清单用于仓库恢复后的 Review、合并和联调，列出已经提供的接口与需要对方完成或确认的工作，不表示已经取得其他成员认可。

| 成员 | Zhou 已提供 | 需要对接的业务与验收 |
| --- | --- | --- |
| Shi Wenqi | UC03 使用其 Reservation 表；CONFIRMED→FULFILLED；设备、预约锁顺序；完整 PostgreSQL profile | 确认 UC01 的预约时间策略允许现场领用；取消与领用同时发生只允许一个结果；提前归还仍占用原预约区间；共同核对 compose 的 web/app/db、Vite API 代理、HTTPS 和部署 Smoke Test |
| Zhang Hanming | V004 审批码和 BIGINT 对齐；ApprovalDecisionPort 的 JDBC 实现；UC03 单元、集成、并发、权限和回滚测试 | Review 数据库适配器；确认无自审批、拒绝理由、不可变决定和审计；验证批准→实际领用→归还的完整链路；安排 coverage、安全检查、PR Quality Gate 和依赖扫描 |
| Liu Tongyao | 损坏归还调用 MaintenanceService.reportFault；OPEN Case 关联 Loan；维修历史可直接查询；报废设备归还时保留损坏 Case | 验证损坏归还→分配维修→开始→修复/报废；正常归还不清除其他维修；借用中报故障后仍可归还；核对 MAINTAINER 分配和维修权限；更新 Backlog、Sprint 状态与实际工时 |
| Wang Yuanmeng | 统一 serms schema、Identity UUID principal、打包现有 PostgreSQL 通知桥；LoanEvent；LoanNotificationRequestFactory 所需的 ACTIVE/RETURNED 和 dueAt | 接入逾期扫描生产者和稳定提醒周期；通过工厂使用 reminderId，不由页面提交 recipient/dueAt；检查归还后待发提醒被 Guard 抑制、重复扫描不重复通知；确认页面样式、时区、OpenAPI 类型生成和事件 after-commit 边界 |

## 跨模块联调顺序

1. 新隔离 PostgreSQL 上初始化 V001→V004，创建真实测试账号并赋予明确角色。
2. BORROWER 搜索并预约；受限设备由另一个 APPROVER 批准。
3. CUSTODIAN 在窗口内核对身份、领用；验证预约 FULFILLED、设备 ON_LOAN 和 ACTIVE Loan。
4. Wang 的扫描生产提醒；重复扫描验证去重。归还后再次发送验证 Guard 不投递已结束借用的提醒。
5. 分别接收 GOOD、迟还、DAMAGED、迟还且 DAMAGED；验证设备优先级、维修关联和审计。
6. MAINTAINER 修复全部活动 Case 后验证设备 AVAILABLE；无法修复后保持 RETIRED。
7. 双库管并发操作、借用人越权和 CSRF 负面测试；确认没有部分写入和历史改写。

## 必须由团队完成或确认

- 当前部署配置仍是静态站点，完整业务上线须由 Shi Wenqi 整合 web/app/db；本次不直接部署服务器。
- 默认数据库和 local 配置已统一为 PostgreSQL 18；MySQL 驱动及生产迁移已移除，H2 仅用于明确隔离的测试基线。Wang、Zhang 需 Review 通知兼容性和测试证据。
- 手工迁移历史切换 Flyway 需要数据备份、版本核对和迁移账号；不能对未知历史自动 baseline。
- 默认演示账号、角色授予、可接受的现场身份核验规则由团队提供，不在迁移中硬编码账号密码。
- 项目批准名称、Presentation 最终日期、真实工时、成员 Review、测试环境验收及 Sprint 会议由各负责人记录。

本地代码和测试完成不等于整组 Definition of Done 已满足。远程仓库恢复后用户自行推送本地提交，随后按以上业务进行 Review 和部署验收。
