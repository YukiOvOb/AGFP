# SERMS 敏捷开发证据索引

Sprint 4 报告中 “敏捷开发过程” 一章的材料来源。Member 1 负责维护；每个 Sprint 结束时补充一行。

报告首次提及时使用 **Shared Equipment Reservation and Maintenance System (SERMS)**，后续统一使用 **SERMS**。

## 1. 需求与计划

| 证据 | 位置 |
| --- | --- |
| 需求清单、用户故事、验收标准 | [backlog.md](backlog.md) |
| Sprint 目标、分工、完成标准、仪式 | [sprint-plan.md](sprint-plan.md) |
| 每周进度、风险、回顾 | [sprint-status.md](sprint-status.md) |
| 看板 | GitHub Projects（To Do → In Progress → Review → Testing → Done）；Sprint 结束时截图存入 `docs/evidence/board/` |

## 2. 按 Sprint 的产出

| Sprint | 产出 | 证据 |
| --- | --- | --- |
| Sprint 0 | 技术栈与架构、数据库设计、接口合同、CI 安全闸门、自动部署 | PR #1、#2、#3、#5、#6、#7、#10；[tech-stack.md](../tech-stack.md)；[auth-and-approval.md](../contracts/auth-and-approval.md) |
| Sprint 1 | 通知基础、数据基础、UC01 搜索与预约 | PR #4、#10；UC01 分支；[UC01 文档](../uc01-search-and-reserve.md) |
| Sprint 2 | UC02 / UC04 基础（Draft PR #8、#9）；UC01 预约规则与审批接口 | PR 链接；[UC01 合同 §5–6](../contracts/equipment-and-reservation.md) |
| Sprint 3 | 待补：整合、演示、截图 | [demo-script.md](demo-script.md) |
| Sprint 4 | 待补：报告 | — |

PR 列表：https://github.com/YukiOvOb/AGFP/pulls?q=is%3Apr

## 3. 质量与测试证据

| 证据 | 位置 |
| --- | --- |
| 每个 PR 的 CI 结果 | GitHub Actions：`CI`（gitleaks、Trivy、依赖审查）、`Frontend and API contract`、`Notification foundation tests`、`Sprint 1 database`、`Notification PostgreSQL contract` |
| 测试报告与覆盖率 | Actions 产物 `frontend-and-api-evidence`、`notification-test-evidence`（surefire 报告、JaCoCo、Playwright 报告） |
| UC01 测试台说明 | [UC01 文档第 6 节](../uc01-search-and-reserve.md#6-测试台与运行方式) |
| UC01 截图 | [`docs/evidence/uc01/`](../evidence/uc01/) |

## 4. 设计证据

| 证据 | 位置 |
| --- | --- |
| UC01 活动图、时序图、预约状态图 | [`docs/diagrams/`](../diagrams/)（Mermaid 源文件），渲染版见 UC01 文档 |
| 数据库 ERD 与状态 | [sprint1-domain.md](../sprint1-domain.md) |
| 设计模式与位置 | tech-stack §3.5；UC01 文档 §5.2 |

## 5. 报告前检查清单（Sprint 4）

- [ ] 每个需求编号都能找到对应的 PR 和测试
- [ ] 每个 Sprint 都有：目标、完成情况、评审截图、回顾记录
- [ ] 报告中的数字（测试数量、覆盖率、规则数值）与最新 CI 结果一致
- [ ] 图片统一编号，术语与代码一致（状态名、角色名）
- [ ] 个人贡献以 PR / 提交记录为准
