# 协作优化的来源与决策

本次只吸收 Gateflow 的团队合作方法。参考快照：[bibi45c/gateflow @ f914824](https://github.com/bibi45c/gateflow/tree/f91482416e4bd26c526e80b0674fdd9e8a5034d1)；SERMS 开始基线：[6a57722](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/tree/6a57722712ed23b7d6749ad184b75dcc368a73ae)。整理日期：2026-10-13，Asia/Singapore。

## 参考模式的取舍

| Gateflow 模式 | SERMS 处理 |
| --- | --- |
| 短 AGENTS + CONTRIBUTING + 按任务读取 skills | 采用；技术规则与合同继续引用 SERMS 原文 |
| 一项任务一个主负责人；一个 worktree/分支一个 writer | 采用；不删除已有 assignee，不重分五人 UC |
| task / bug / design、PR 的范围与依赖字段 | 采用模板；保留原看板，不另建 label 状态体系，不强制补虚构 Issue |
| 检查记录绑定 HEAD/base，合并与最终验收区分 | 采用；保留 Test Bench、CI 全绿和 70% 要求 |
| reviewer 只读，agent 结论不冒充人类批准 | 采用；配合任务协调和真实交接 |
| 唯一 integrator 才能合并 | 不采用；保留 SERMS Write 协作者自行合并 |
| Go 微服务、服务间 HTTP、Go internal、根 go.mod | 不采用；SERMS 继续 Spring Boot 模块化单体、React、PostgreSQL 18 |
| Python-only 工具与 Go 验证命令 | 不采用；保留 Maven、pnpm、已有 PowerShell/Shell |
| 删去过渡目录、强制 issue 标签与新远端权限层级 | 不直接套用；须有独立范围和必要确认 |

参考内容来自 [Gateflow AGENTS](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/AGENTS.md)、[CONTRIBUTING](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/.github/CONTRIBUTING.md)、[testing](https://github.com/bibi45c/gateflow/blob/f91482416e4bd26c526e80b0674fdd9e8a5034d1/docs/testing.md) 和其中链接的 skills。本次重新按 SERMS 语境编写，不执行 Gateflow 项目的权限或技术规则。

## D-001 合并权限与组员检查

来源：[README](../../README.md#协作与发布) 允许 Write 协作者 CI 全绿后自行合并，不需他人审批；[Sprint 计划](../agile/sprint-plan.md) 要求至少一名组员检查；[历史数据交付记录](../sprint1-zhoufanhao.md) 也保留过 review 要求。

状态：**本次会话用户已确认**。决定：保留自行合并，组员检查用于团队验收，不新增 GitHub 强制 approving review 门槛。

落实：仅澄清当前 Sprint 计划的 PR/DoD 描述。README 的合并权限及历史交付记录不重写。合同修改的受影响负责人确认、CI 全绿、团队 Done 的检查与部署证据仍保留。未修改 GitHub 分支保护。

## D-002 公共职责来源差异

来源与具体差异见 [ownership](ownership.md#公共职责与具体交付的来源)：Sprint 的长期职责，与 tech-stack 和 UC02 合同中平台、公共测试及 CI/部署的交付负责人不完全相同。

状态：**待用户确认**。本次会话已请求决定；未用本索引重分职责。

可选方案：保留来源并区分长期公共职责/具体交付；按 Sprint 计划统一；或按技术栈及已确认合同统一。后两项需先列出受影响合同和任务，再决定具体修改。确认前保留所有源文档，其他无冲突的协作补强继续进行。

## 保留的技术和业务差异

以下是已有整合问题，不是本次协作规则优化要实施的改动：

| 差异 | 现有来源 | 本次处理 |
| --- | --- | --- |
| PostgreSQL 18 目标与部分 17/MySQL/H2 配置 | [tech-stack §7](../tech-stack.md#7-已有代码如何对齐)、已有 workflows/脚本 | 原样保留；另开数据整合与升级验证任务 |
| 单应用单迁移序列目标与 `database/`、adapter、现有迁移路径 | [tech-stack §6](../tech-stack.md#6-团队约定)、[Sprint 风险](../agile/sprint-status.md) | 不移动、删除、改写已合并迁移；整合单独评审 |
| 设备四/五状态、维修五/六状态 | [UC02 合同 §9](../contracts/auth-and-approval.md#9-待确认事项)、Sprint 风险快照 | 不选择某个状态模型；仍需原负责人和团队确认 |
| 技术状态表/旧 Sprint 快照与当前 main 实际内容 | [tech-stack](../tech-stack.md)、[Sprint 状态](../agile/sprint-status.md) | 保留历史记录；新验收记录绑定日期和提交，不能自动改写历史 |

新增决定应记录双方原规定、明确选项、用户选择、影响范围及实际改动。agent 不能把“建议”或工具可执行等同于用户已确认。
