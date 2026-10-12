# SERMS 责任边界

本文件汇总已有分工并标明来源。它不是重新分配任务的决定，也不推断成员的 GitHub 账号。具体交付遵循相关合同或已约定任务；来源冲突时按[决策记录](decisions.md)询问用户。

## 已一致的用例分工

成员名称取自 [tech-stack 测试台范围](../tech-stack.md#52-每位成员的测试台范围)，Member 编号取自 [Sprint 计划](../agile/sprint-plan.md)。

| 成员 | 用例 | 主要业务路径 |
| --- | --- | --- |
| Member 1 · Shi Wenqi | UC01 搜索并预约设备 | `app/.../equipment/`、`app/.../reservation/`；`frontend/src/features/{equipment,reservation}/` |
| Member 2 · Zhang Hanming | UC02 审核和批准预约 | `app/.../approval/`；`frontend/src/features/approval/` |
| Member 3 · Zhou Fanhao | UC03 设备领用和归还 | `app/.../loan/`（目标路径）；已有 `database/` 与 `integration/notifications/` 数据基础 |
| Member 4 · Liu Tongyao | UC04 故障报告和维修处理 | `app/.../maintenance/`；`frontend/src/features/maintenance/` |
| Member 5 · Wang Yuanmeng | UC05 逾期检测和通知 | `app/.../notification/`；`frontend/src/features/notification/` |

`app/.../` 指 `app/src/main/java/sg/edu/nus/serms/`；目录表说明责任范围，不声称尚未出现的 `loan` 等模块已实现。每人继续为自己的功能提供完整 Test Bench。

## 公共职责与具体交付的来源

| 事项 | Sprint 计划的长期公共职责 | 技术栈 / 已确认合同的具体交付约定 |
| --- | --- | --- |
| 需求、任务、Sprint 与敏捷证据 | Member 1 · Shi Wenqi | [agile README](../agile/README.md) 指定 Member 1 维护 |
| 架构、页面结构、统一样式 | Member 2 · Zhang Hanming | [UC02 合同](../contracts/auth-and-approval.md#1-开发顺序) 的登录、ProblemDetail、OpenAPI、React 脚手架由 Wang Yuanmeng 交付 |
| 数据模型、数据库、迁移 | Member 3 · Zhou Fanhao | UC02 合同同样指定 Zhou Fanhao；沿用 |
| 测试策略、安全、质量 | Member 4 · Liu Tongyao | [tech-stack §5.2](../tech-stack.md#52-每位成员的测试台范围) 和 UC02 合同指定 Zhang Hanming 维护公共测试基础设施 |
| 仓库、CI/CD、发布和运行 | Member 5 · Wang Yuanmeng | UC02 合同第 5 项 CI/部署交付指定 Shi Wenqi；[Sprint 状态快照](../agile/sprint-status.md) 另列 Member 5 部署任务 |

“长期公共职责”和“某个 PR 的交付负责人”可以不同，但不能未经确认就把上述差异解释成已经达成的委托关系。确认状态见[分工决定](decisions.md#d-002-公共职责来源差异)。确认前，保留所有源文档及当前交付约定；跨职责的新任务先明确主负责人和受影响参与者。

## 容易发生并发修改的公共文件

| 区域 | 需要协调什么 |
| --- | --- |
| `app/pom.xml`、`frontend/package.json` 与 lockfile | 依赖版本、重复声明、扫描与组合测试 |
| `app/.../shared/`、`identity/`、`frontend/src/{app,api,components,lib}/` | 平台行为及全部消费者；避免各 UC 重造登录、权限、错误处理或公共 UI |
| Flyway、`database/`、通知 adapter | 迁移编号、schema、数据兼容、升级路径；按现有合同和数据职责协作 |
| 公共测试夹具、MSW、Playwright 登录流程 | 复用已有设施；不得为局部通过破坏别人的测试数据或行为 |
| 根 Docker/Compose、`.github/workflows/`、部署脚本 | 启动、CI、生产影响；本次不替换已有流程 |
| 规则、合同、Sprint 和证据索引 | 维护来源一致；未确认决定不能被模板或 skill 提前生效 |

这些路径不是禁止触碰的 CODEOWNERS 清单，也不会改变成员 Write 权限。任务涉及它们时，在 PR 中列出影响、依赖和实际确认记录，安排一个写入者整合，其他会话保持只读或在独立分支提出改动。
