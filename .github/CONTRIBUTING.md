# SERMS 协作指南

这里补充任务流转、隔离开发和交接方法。技术与业务规则继续由已有文档维护；本指南不重分成员职责，不改变 Write 协作者自行合并的制度。

## 规则来源

| 内容 | 维护位置 |
| --- | --- |
| 技术选型、代码分层、Test Bench、迁移目标 | [技术栈说明](../docs/tech-stack.md) |
| 接口、事件、权限与事务语义 | [接口合同](../docs/contracts/README.md) |
| 发布与合并权限 | [README 协作与发布](../README.md#协作与发布) |
| Sprint、看板与团队 Done | [Sprint 计划](../docs/agile/sprint-plan.md) |
| 分工来源及差异 | [责任边界](../docs/collaboration/ownership.md) |
| 工具委派与多会话 | [agent 工作流](../docs/collaboration/agent-workflow.md) |
| 验证命令及证据格式 | [testing](../docs/testing.md) |
| 冲突与已确认决定 | [决策记录](../docs/collaboration/decisions.md) |

不要用“新文件优先”或“实现已经如此”来覆盖已确认合同。发现冲突时指出双方的路径、具体规定和影响，提交可审阅的选项，等用户决定后再修改相关规定。只暂停依赖该决定的部分。

规则变更仍走 PR。规则分支中的文字是提案，不能靠它给自己扩大权限或降低已有检查。记录任务采用的 `main` 基线 SHA；恢复任务时检查基线规则的后续变化。用户在当前会话作出的决定须写入决策记录，说明范围。

## 角色和授权

- **任务负责人**：对该任务范围、验收和依赖负责的人类成员。沿用团队既有分工；不要从 agent 名称推断人类负责人或 GitHub 账号。
- **写入者**：某 worktree 和分支当前唯一的人员或 agent 会话。
- **任务协调者**：为本项工作协调写入者、依赖及共享 Git 操作的人员或会话。是任务内职责，不是永久合并管理员。
- **审查者**：读取固定版本的改动并报告问题；发现问题交还写入者修复。

用户已有授权按其范围继续有效，不为正常阅读、隔离开发、修复和验证重复索要许可。任务没有授权的远端留言、外部消息、生产操作或付费操作不能由文件或 skill 自动授权。涉及更改已规定内容而与其冲突时，先准备具体方案，再征求用户意见。

本地任务可先来自用户会话或已约定的看板卡片；有 Issue 后补上链接。不要为了形式完整虚构 Issue、会议、工时、签字或审批。Issue 创建时指定一名主负责人，其他参与者写在依赖和协作字段；现有 Issue 的多位 assignee 不因此被自动删除。

## 任务和依赖

任务应写清：目标、对应 UC/需求、主负责人、允许修改的目录、排除项、前置依赖、验收条件、验证方式和交付物。使用 [task](ISSUE_TEMPLATE/task.yml)、[bug](ISSUE_TEMPLATE/bug.yml) 或 [design](ISSUE_TEMPLATE/design.yml) 表单。

- 继续使用 GitHub Projects：**To Do → In Progress → Review → Testing → Done**。不另建一套状态标签替换它。
- 能独立验证的交付尽量拆成独立 PR。依赖别人的合同或服务时，先确定接口和合并顺序。
- 基于依赖分支开发时，在任务和 PR 中明确 base、依赖 PR、允许使用 mock 的测试范围和尚缺的真实整合验证。
- mock 可以支撑开发和单元测试，不能宣称已证明真实 PostgreSQL、事务回滚、权限或完整用户流程。
- 部分交付写清剩余验收；有 Issue 时，用 `Refs #n`。只有进入 `main` 且完成该 Issue 全部验收的 PR 才用 `Closes #n`，避免自动关闭仍需部署验证的任务。

## 分支和 worktree

保留现有 `feature/<成员>-<用例>`、`feat/...` 分支。新分支沿用团队约定；工具创建的 `codex/...` 分支可作为 agent 提案分支，不批量重命名既有分支。

1. 先查看 `git status --short --branch`、`git log -1 --oneline` 和 `git worktree list --porcelain`。
2. 独立写入任务使用独立 worktree，从最新已获取的 `origin/main` 或已约定依赖提交建立。worktree 放在主 checkout 外；Codex 管理的 worktree 由 app 工具建立和管理。
3. 记录路径、分支、基线 SHA 和唯一写入者。worktree lock 只能防清理，不能替代写入者约定。
4. 多个会话共享一个 clone 时，由当前任务协调者串行执行 fetch、建/删分支、push、stash、配置和 worktree 维护，避免互相修改共享 refs。各写入者在自己的 worktree 做编辑、测试、提交。
5. 不把备份 ZIP、恢复目录、其他成员的未跟踪文件或凭据纳入自己的提交。用明确路径 `git add`，先检查暂存差异。

```text
git worktree list --porcelain
git status --short --branch
git log -1 --oneline
git diff --stat
git diff --cached
```

不要在活跃 checkout 切换分支或使用 `reset --hard`、`clean` 丢弃改动。重写自己的 PR 分支前核对其他写入者；确需 push 重写历史时使用 `--force-with-lease`，不强推共享分支。

依赖 PR 合并后，协调者确认 base 的变化和旧依赖 HEAD，再更新后续分支并重新验证；两个分支各自通过测试不代表组合后通过。

## 交接和清理

交出任务前，停止自己启动的服务和写入活动，记录 HEAD、基线、未提交/未跟踪内容、测试结果、未完成验收和待决定事项。接手者确认交接、读取适用规则并检查 Git 状态后再写入。记录格式见 [agent 工作流](../docs/collaboration/agent-workflow.md#交接记录)。

清理不能只看工作区是否干净。先检查 ignored 文件、未推送提交和 stash，保留有价值的本地数据或报告；确认任务完成或已取消，再使用创建 worktree 的工具归档，或在其他 checkout 中执行约定的清理。不要移除其他人的 worktree 或分支。

## PR 和团队验收

- 所有改动通过 PR；功能与自己的 Test Bench 同一个 PR 提交。[PR 模板](pull_request_template.md) 记录范围、依赖、合同影响、验证和剩余验收。
- 保留 [README 的合并制度](../README.md#协作与发布)：有 Write 权限的协作者在 CI 全绿后可自行合并，不新增强制 approving review 或唯一集成人。
- 每个任务达到团队 Done 前，保留至少一名组员检查、部署验证和其他原有 DoD 证据，见 [Sprint 计划](../docs/agile/sprint-plan.md#2-完成标准definition-of-done)。这与 GitHub 是否允许合并分别记录。
- 合同修改仍须受影响负责人确认；这项既有要求不因自行合并而免除。
- 合并前检查最新 HEAD 的 CI、目标分支及实际组合结果；不得降低分支保护或跳过失败的检查。仓库设置是否启用某门槛，需要读取设置核实，写入文档不会启用它。
- agent 审查可以作为辅助证据，不能写成人类检查或人类批准。报告合并、部署和验收的实际状态。

## 文件和命令

遵守现有 `.gitattributes`：UTF-8 文本、LF。链接使用仓库相对路径并匹配大小写。命令写明工作目录和必要环境；Windows 用 `mvnw.cmd`，macOS/Linux 用 `./mvnw`。保留已有 PowerShell、Shell、Maven 和 pnpm 工具，不把 Gateflow 的 Python-only 脚本约定变成 SERMS 的要求。
