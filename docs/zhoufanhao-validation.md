# Zhou Fanhao 本地验证记录

## 默认 PostgreSQL 配置收尾验证

本次从干净构建重新执行 `scripts/test-zhoufanhao.ps1 -PostgresBin 'C:/Program Files/PostgreSQL/18/bin'`，完整命令成功。数据库 27 项、应用回归 536 项和默认运行配置的 PostgreSQL 集成 20 项均通过；另行验证固定版本通知合同 12 项通过。UC03 集成现在不设置 ActiveProfiles，检查默认 PostgreSQL driver、统一 Flyway 路径、默认开启审批和实际登录/交接链路。

生产 JAR 已检查：不包含 MySQL Connector、Flyway MySQL、H2 或测试迁移；只包含 V001–V004 四份生产迁移。四份迁移的新旧 Git blob 一致，移动未改写迁移内容。所有数据库镜像配置统一为 PostgreSQL 18，开发数据库使用新的 PG18 专用数据卷。新版 OpenAPI 与现有前端类型一致。

最新证据见 [default-postgres-summary.json](evidence/uc03/default-postgres-summary.json)。本次未修改前端功能，前端测试沿用下方已验证的 79 项结果。远端 Review、CI 和服务器部署仍未执行。

## 前次业务实现验证

验证日期为 2026-10-07，工作目录是用户提供的 AGFP 本地完整备份。使用独立临时 PostgreSQL 数据库，没有连接开发或正式业务数据库。环境实际为 PostgreSQL 18.6、JDK 25（Java release 17 编译）、Node 24.21.0 和 pnpm 11.19.0。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 原 V002 数据库回归 | 27 项，失败 0、错误 0、跳过 0 | `database/target/surefire-reports` |
| 含旧决定的 V004 升级 | 通过；主键及拒绝理由保留，四类 version 为 bigint，历史不可修改触发器恢复 | `v4-upgrade-fixture.sql`、`v4-upgrade-assertions.sql` |
| 完整应用空库 Flyway 初始化 | V001→V002→V003→V004 成功 | PostgreSQL 测试启动日志及 schema/Identity 测试 |
| 应用回归报告汇总 | 536 项，失败 0、错误 0、跳过 0；包含完整运行后定向复测 | `app/target/surefire-reports` |
| UC03 完整应用 PostgreSQL 集成 | 20 项，失败 0、错误 0、跳过 0 | `app/target/failsafe-reports` |
| React 测试 | 79 项，6 个测试文件全部通过 | 本地 Vitest 日志 |
| TypeScript 和 Vite 生产构建 | 通过 | `pnpm build` |
| ESLint | 通过 | `pnpm lint` |
| 生成的 OpenAPI 类型一致性 | 通过 | `pnpm check:api` 与 `app/target/openapi.json` |
| Git diff 空白检查 | 通过 | `git diff --check` |

可提交的紧凑摘要见 [test-summary.json](evidence/uc03/test-summary.json)。原始日志保存在被 Git 忽略的 `.cache/`，JUnit 报告保存在各模块 target 中。摘要只记录测试名和计数，不打包 JUnit 的环境属性、完整 stdout 或本地账号信息。

UC03 真实集成覆盖：领用与正常归还原子性；损坏维修关联与审计；非法身份、时间窗、预约状态和版本；停用借用人及撤销库管角色；维修和报废优先级；逾期且损坏；并发领用与归还；同步事件失败回滚；借用人数据归属；CSRF 和匿名访问；int64 输入拒绝浮点、字符串及溢出；审批持久化与通知进入领用链路；已归还借用的提醒 Guard；真实 PostgreSQL 登录与 UUID principal；实际 OpenAPI 操作。

复测过程中修正了读取队列误用写锁、PostgreSQL 18 的 RESTRICT 删除错误码预期，以及既有维修测试中的 Integer 版本辅助数据。应用回归先整体运行，再对修改过的维修快照、JDBC 映射和设备状态测试定向复测，最终最新 JUnit 汇总为全部通过。UC03 的 20 项集成在完整应用和真实 PostgreSQL 上单独全部通过。

一键重现脚本为 `scripts/test-zhoufanhao.ps1`，支持 Docker 或 `-PostgresBin`。新增 GitHub workflow 已准备，但仓库被封禁期间没有执行远程 CI。没有宣称远程 Review、安全扫描、覆盖率门槛、正式部署、Smoke Test、Sprint Review 或真实工时已经完成；这些证据按 [成员对接清单](zhoufanhao-handoffs.md) 补齐。
