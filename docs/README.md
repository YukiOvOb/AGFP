# SERMS 文档索引

这里提供导航，不取代已有规范；历史交付、快照和证据保持原位置。

| 需要了解的内容 | 入口 |
| --- | --- |
| 如何参与项目、分支、PR、交接 | [协作指南](../.github/CONTRIBUTING.md) |
| agent 的最小启动入口和 skills | [AGENTS](../AGENTS.md) |
| 技术选型、业务分层、测试台、对齐目标 | [tech-stack](tech-stack.md) |
| 成员用例分工、公共职责差异 | [ownership](collaboration/ownership.md) |
| agent 工具、单写入者、委派和恢复 | [agent-workflow](collaboration/agent-workflow.md) |
| 冲突、参考模式与用户决定 | [decisions](collaboration/decisions.md) |
| 命令、实际 CI 和证据格式 | [testing](testing.md) |
| 接口合同 | [contracts](contracts/README.md) |
| Backlog、Sprint、任务状态和报告证据 | [agile](agile/README.md) |
| 设计图源文件 | [diagrams](diagrams/) |
| 实际截图及验收证据 | [evidence](evidence/) |
| 已记录的架构决定 | [ADR](adr/) |
| 平台与 React 整合 | [platform-rest-react](platform-rest-react.md) |
| UC01 | [搜索和预约](uc01-search-and-reserve.md) |
| UC02 | [审批实现](uc02-approval-implementation.md) |
| 数据基础及历史交付 | [database](../database/README.md)、[对齐记录](serms-database-alignment.md) |
| 通知适配与对接 | [integration/notifications](../integration/notifications/README.md)、[对齐记录](notification-interface-alignment.md) |

## 项目结构

| 路径 | 职责和本次处理 |
| --- | --- |
| `app/` | 单个 Spring Boot 应用；继续按 `sg.edu.nus.serms.<业务模块>.{api,service,domain,repository}` 组织 |
| `frontend/` | 单个 React 应用；`src/features/` 对应业务模块，复用 `app/`、`api/`、`components/`、`lib/` |
| `database/` | 已有数据基础与迁移测试；保留，整合另开任务 |
| `integration/notifications/` | 已有 PostgreSQL 适配模块；保留，整合另开任务 |
| `web/`、根目录 Docker/Compose | 已有部署入口；保留，具体运行现状与三容器目标区分记录 |
| `scripts/` | 保留现有测试、部署脚本；新增协作文档检查入口 |
| `docs/collaboration/` | 新增协作模式、分工来源、agent 流程与决策记录 |
| `.agents/skills/` | 新增 SERMS 任务操作指南；按需读取，不重复定义规则 |
| `.github/` | 保留全部已有 workflow；新增协作指南、Issue/PR 模板和文档检查 |
| `.claude/CLAUDE.md` | Claude Code 的入口转发到根 AGENTS，避免维护第二套规则 |

既有文档可能记录较早分支、数据库版本或未合并状态。阅读时先核对其日期与提交；不能仅凭旧快照宣布当前功能已验收，也不能为更新导航而删除历史证据。
