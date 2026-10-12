# SERMS 验证与证据

测试台要求继续以 [tech-stack §5](tech-stack.md#5-测试台test-bench每人负责自己的) 为准。本页提供执行入口与证据格式，不降低覆盖率、真实 PostgreSQL 或前后端测试要求。

## 执行入口

| 检查 | 工作目录与命令 | 条件 / 当前 workflow |
| --- | --- | --- |
| 后端 | `app/`：Windows `mvnw.cmd -B verify`；macOS/Linux `./mvnw -B verify` | Java 17；PostgreSQL/Testcontainers 用例需要 Docker。`Frontend and API contract` 与 `Notification foundation tests` 会运行后端验证 |
| 前端 API 类型 | `frontend/`：先生成 `app/target/openapi.json`，再设 `OPENAPI_FILE=../app/target/openapi.json` 并运行 `pnpm check:api` | Java 后端 verify 的实际产物；`Frontend and API contract` |
| 前端质量与测试 | `frontend/`：分别运行 `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build` | Node 24，package.json 指定的 pnpm；先 `pnpm install --frozen-lockfile` |
| 前端 E2E | `frontend/`：`pnpm exec playwright install chromium`，再 `pnpm test:e2e` | Chromium 及对应平台依赖；Linux CI 使用 `--with-deps` |
| 数据基础 | 根目录：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1` | 现有脚本和 `Sprint 1 database`；实际使用 PostgreSQL 17，不能称为已完成 18 验收 |
| 通知适配 | 根目录：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1` | 现有脚本需要 Docker、Maven 和 pinned upstream 提交；`Notification PostgreSQL contract` |
| 安全 | GitHub `CI` workflow | 原有 gitleaks、Trivy、Dependency review；本地文档检查不能替代它们 |
| 协作导航与 skills | 根目录：`node scripts/check-collaboration.mjs` | 新增 `Collaboration documentation` workflow；不改变分支保护 required checks |

Windows 环境变量示例，工作目录 `frontend/`：

```powershell
$env:OPENAPI_FILE = '../app/target/openapi.json'
pnpm check:api
```

macOS/Linux 对应命令：

```sh
OPENAPI_FILE=../app/target/openapi.json pnpm check:api
```

表格是已配置的入口清单，不能证明它们已通过。`Notification foundation tests` 的真实库阶段仍用 MySQL；部分脚本仍用 PostgreSQL 17。PostgreSQL 18 与统一迁移的目标保持不变，实际差异记录在[决策记录](collaboration/decisions.md#保留的技术和业务差异)。不要在本次文档 PR 顺手修改数据库或扫描配置。

## 验证范围

- 实现者先跑最贴近改动的行为验证，再完成现有 PR 所需检查。纯文档改动本地检查链接、模板和适用规则；远端 CI 仍按已有 workflows 运行，不自行跳过。
- 后端/前端行为改动继续提交自己的完整 Test Bench。不能通过 H2 或 mock 的结果宣布已满足真实 PostgreSQL 18、并发或事务验收。
- 新 commit 或 base 更新后，判断哪些证据受影响并重新验证。旧 HEAD 的通过不能写成新 HEAD 的通过。
- 不修改 tests 的跳过状态或覆盖率阈值来掩盖失败；例外继续遵循 tech-stack 的既有确认要求。
- 端口、容器、测试库和生成报告属于各 worktree 自己的资源；不清空别人使用的库或结束别人的进程。

## 证据记录

每项检查记录：命令/场景，工作目录，OS 和工具版本，HEAD，base 分支及获取时的 SHA，未提交输入（如有），结果 `passed / failed / not run / blocked`，简短输出和报告位置。基线是否为远端最新状态须另行核对，不把旧的 `origin/main` 当实时远端。

本地未提交提案的例子：HEAD 仍为基线提交，注明新增/修改文件作为输入；提交后记录 delivered HEAD 与核对后的 base。不要在产物里写虚构的最终 SHA。

一份 PR 应分别说明：

| 状态 | 有效证据 |
| --- | --- |
| 本地验证 | 实际命令、输入版本、输出/报告 |
| CI | 最新 PR HEAD 对应的 Actions 运行及结论 |
| agent 审查 | 审查范围、HEAD/base 和具体发现 |
| 组员检查 | 实际人类检查记录；不能用 agent 审查替代 |
| 合同确认 | 受影响负责人的实际确认记录 |
| 合并/部署 | PR merge 状态、部署 workflow、部署后验证 |
| 团队 Done | [Sprint DoD](agile/sprint-plan.md#2-完成标准definition-of-done) 全部证据 |

既有 required checks、覆盖率及安全门槛保持不变。本文和新增检查 workflow 均不会自动修改 GitHub 分支保护。某项检查尚未落地或无法运行时如实记录，不能写 `passed`。

## 模板实用检查

修改规则、表单或 skill 后，用一项真实场景走一遍：例如 UC03 暂依赖数据迁移时，能否写清负责人、排除项、依赖、mock 与真实验收的区别，并交给唯一 writer；若任务已合并但缺部署/组员检查，不能移到 Done。

文档检查器覆盖新协作 Markdown 的本地文件/标题链接、skill 名称与来源入口，以及 Claude 引用。它不验证授权真伪、GitHub 实际保护设置、远端 URL 或业务合同一致性；这些仍需要事实核对。
