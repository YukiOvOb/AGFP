# SERMS PostgreSQL 18 与 UC03 更新审核

审核日期：2026-10-12（Asia/Singapore）。正式系统名称为 Shared Equipment Reservation and Maintenance System (SERMS)。

## 来源与结论

来源：周凡浩提供的 `AGFP.zip`，SHA-256 为 `EA7161D4EEF8D14EC6309EB929EDD1DD02F2C3630B878ACD40F22215AF56B1BF`。

审核了原始提交 `5559503`（UC03 与数据库整合）、`751ca0d`（默认 PostgreSQL 18）和 `b6d0db8`（交付说明），对照仓库主线 `6a57722`。原提交及作者署名完整保留，整合时保留主线已统一的仓库名称和 README 标题。缓存、第三方依赖、构建产物、打包工具和本地数据库不提交。

结论：未发现阻塞性的应用或迁移问题，可提交 PR。审核中修正了 Windows 测试脚本在输出重定向时停在 PostgreSQL 启动步骤的问题。托管 CI、安全扫描、团队验收和服务器部署仍需独立确认。

## 审核要点

- 默认运行配置、local 配置与开发容器已指向 PostgreSQL；镜像使用 `postgres:18-alpine`。生产依赖移除 MySQL Connector 和 Flyway MySQL，H2 仅保留在测试作用域。
- V001、V002、V003 移动到统一生产迁移目录后，Git blob 与原版本一致。V004 使用新增迁移处理审批码、BIGINT 版本和索引，升级测试核对旧审批主键、拒绝理由及不可变历史约束。
- 审批持久化要求现有事务和有效 APPROVER；默认开启审批前已接入真实持久化。真实登录、预约审批、通知和领用链路经过默认配置集成测试。
- UC03 写操作要求有效 CUSTODIAN；领用检查借用人、预约状态、时间窗、设备状态和版本。借用人仅能读取本人的借用记录；越权、CSRF 和无效版本输入有负面测试。
- 领用、归还、设备状态、损坏维修记录、审计和同步事件使用同一事务。并发领用/归还只有一个成功，事件失败会回滚，维修和报废状态优先级得到验证。
- Java/数据库的版本字段采用 long/BIGINT；前端与实际应用生成的 OpenAPI 类型一致。
- Windows 本地测试启动改用隐藏的 `Start-Process`，将启动输出写入独立文件，只等待 `pg_ctl` 进程退出，避免持续运行的 PostgreSQL 继承捕获输出管道。

## 独立验证

使用 PostgreSQL 18.6 的隔离临时实例、JDK 25.0.4（Java release 17 编译）和 Node.js 24.19.0。测试未连接开发或正式数据库，临时数据库实例在测试结束后停止。

| 检查 | 结果 |
| --- | --- |
| 数据库回归 | 27 项通过，失败/错误/跳过均为 0 |
| V004 旧审批历史升级 | 通过 |
| 后端完整回归 | 536 项通过，失败/错误/跳过均为 0 |
| 默认 PostgreSQL 配置及 UC03 集成 | 20 项通过，失败/错误/跳过均为 0 |
| UC04 PostgreSQL 集成 | 28 项通过，失败/错误/跳过均为 0 |
| 固定版本通知合同及历史兼容性 | 12 项通过，失败/错误/跳过均为 0 |
| 前端测试 | 79 项通过，6 个测试文件全部通过 |
| 前端 ESLint、TypeScript 与生产构建 | 通过 |
| OpenAPI 类型与实际应用接口 | 通过 |
| 生产 JAR | 无 MySQL/H2 依赖，仅包含 V001–V004 四份生产迁移 |
| Git 空白检查与 PowerShell 语法检查 | 通过 |
| 修正后的 Windows 测试脚本（重定向日志） | 完整执行通过，结束后停止临时数据库 |

以上主要测试共 702 项；最初的 107 项定向测试及后续脚本复测不重复计入。原始作者保存的测试摘要作为历史证据保留，本记录的结果来自本次独立执行。

## 交付边界

本更新解决仓库中后端仍默认使用 MySQL 的问题，包含周凡浩同时交付的 UC03 和跨模块整合。现有生产部署仍为欢迎页，不能据此认定网站已运行完整业务系统。已有 MySQL 数据也不会自动转换为 PostgreSQL 数据；真实历史数据切换应按运行说明备份、核对和迁移。

前端构建仍提示单个产物超过 500 kB，构建成功，可在后续优化代码拆分。本次没有宣称覆盖率门槛、端到端浏览器验收或云端安全检查已通过。

## 首次托管 CI 诊断

PR #1 首次运行的 Trivy 漏洞扫描通过。Dependency review 报告本仓库不支持该功能；私有仓库需满足 [GitHub 官方依赖审查的启用条件](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review)。此失败状态保留，没有跳过或降低依赖审查要求。

Gitleaks 8.24.3 的 `generic-api-key` 规则将原始提交 `5559503` 中 `scripts/test-zhoufanhao.ps1` 第 10 行的环境变量名称列表误判为密钥。该行只是 `SERMS_APP_TEST_*` 名称，不包含密码值；测试密码由运行时 GUID 生成。`.gitleaksignore` 仅登记这一个提交、文件、规则和行号组成的指纹，依据 [Gitleaks 官方指纹排除机制](https://github.com/gitleaks/gitleaks/blob/v8.24.3/README.md#gitleaksignore)，没有排除整个文件、提交或扫描规则。

已使用同版本本地 Gitleaks、相同的 PR 提交范围和日志选项复测：退出码 0，未发现泄漏。
