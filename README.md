# SERMS

Shared Equipment Reservation and Maintenance System（共享设备预约与维护系统）· SWE5006 Team 07

## 技术栈

前后端分离：React 单页应用 + Spring Boot REST API（单体，内部按业务模块划分）+ PostgreSQL 18，用 Docker Compose 运行，GitHub Actions 负责从编译到部署后验证的整条流水线。每项技术对应哪条需求、目前是否已落地、与 Proposal 的差异，见 [docs/tech-stack.md](docs/tech-stack.md)。

### 前端（`frontend/`）

| 方面 | 技术 |
| --- | --- |
| 语言 | TypeScript 5（strict 模式） |
| 框架 | React 19 |
| 构建 / 开发服务器 | Vite 7 |
| 路由 | React Router 7 |
| 请求后端数据与缓存 | TanStack Query 5 |
| 调用后端接口 | openapi-typescript + openapi-fetch（根据后端接口文档自动生成类型） |
| 表单与校验 | React Hook Form + Zod |
| UI 组件 / 样式 | shadcn/ui + Tailwind CSS 4 |
| 表格 | TanStack Table 8 |
| 图表 | Recharts 3 |
| 日期时间 | date-fns + date-fns-tz（按新加坡时间显示） |
| 包管理 / 运行时 | pnpm · Node.js 24 LTS |
| 代码规范 | ESLint + Prettier |
| 测试 | Vitest · React Testing Library · MSW（模拟接口）· Playwright（端到端） |

### 后端（`app/`）

| 方面 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5（按业务模块划分的单体应用） |
| Web 接口 | Spring MVC，`/api/v1` 下的 JSON 接口 + Jakarta Bean Validation |
| 接口文档 | springdoc-openapi（OpenAPI 3） |
| 错误格式 | RFC 9457 Problem Details |
| 安全 | Spring Security：Session Cookie 登录、BCrypt 密码哈希、`@PreAuthorize` 方法级权限、CSRF（Cookie + 请求头） |
| 数据访问 | Spring Data JPA / Hibernate；需要加锁或复杂查询的地方用 JdbcTemplate 手写 SQL |
| 定时任务 | Spring `@Scheduled` |
| 运维端点 | Spring Boot Actuator（只开放 health、info） |
| 构建 | Maven 3.9（`./mvnw`） |
| 测试 | JUnit 5 · Mockito · AssertJ · Spring Boot Test · Testcontainers · ArchUnit（分层规则检查） |
| 覆盖率 | JaCoCo（service + domain ≥ 70%） |
| 代码规范 / 静态分析 | Spotless（google-java-format）· SpotBugs + FindSecBugs |

后端代码按业务模块组织在 `sg.edu.nus.serms` 下：`identity`、`equipment`、`reservation`、`approval`、`loan`、`maintenance`、`notification`、`report`、`audit`、`shared`；前端 `frontend/src/features/` 下的模块和后端一一对应。

### 数据库

| 方面 | 技术 |
| --- | --- |
| 数据库 | PostgreSQL 18（本地、CI、服务器统一用 `postgres:18-alpine`） |
| 表结构迁移 | Flyway |
| 预约防重叠 | PostgreSQL 排他约束（`EXCLUDE USING gist` + `tstzrange`） |
| 备份 | `pg_dump` 每日备份，保留 7 天 |

### DevSecOps 与部署

| 方面 | 技术 |
| --- | --- |
| CI/CD | GitHub Actions |
| 密钥扫描 | gitleaks |
| 漏洞扫描 | Trivy（文件系统 + Docker 镜像） |
| 依赖检查 | Dependency Review（PR 阶段）+ Dependabot（每周自动升级） |
| 代码安全扫描 | CodeQL（Java + TypeScript） |
| 部署后安全扫描 | OWASP ZAP Baseline |
| 压力测试 | k6（50 并发） |
| 容器 | Docker（多阶段构建，非 root 运行）+ Docker Compose：`web` + `app` + `db` 三个容器 |
| 镜像仓库 | GitHub Container Registry（GHCR） |
| 服务器 | 香港服务器 + nginx + Let's Encrypt，https://serms.midas.cyou |

### 可选功能（时间允许再做）

| 功能 | 技术 |
| --- | --- |
| 邮件通知 | Spring Mail；开发时用 Mailpit 接收测试邮件 |
| 二维码 | 前端用 `qrcode` 生成，`@zxing/browser` 扫码 |

### 协作

| 方面 | 技术 |
| --- | --- |
| 代码托管 | Git + GitHub |
| 项目管理 | GitHub Projects |
| 设计图 | PlantUML / Mermaid（源文件放在 `docs/diagrams/`），draw.io 作为补充 |

> 目前已写进 main 的有：Java 17、Spring Boot 3.5、Spring Data JPA、Flyway、Maven、JUnit / Mockito、JaCoCo、GitHub Actions、gitleaks、Trivy、Dependency Review、Docker Compose 和服务器部署。PostgreSQL 表结构在周凡浩的分支上；React 前端和其余流水线环节还没落地。逐项状态见 [docs/tech-stack.md 第 1 节](docs/tech-stack.md#1-全部技术栈)。

## 测试：每人写好自己的 Test Bench

CI 只能验证已经写好的测试。**每位成员必须为自己负责的用例写好完整的测试台，并和功能代码放在同一个 PR 里提交**，这样每个 PR 在 CI 上都能自己验证自己。

- 位置：后端 `app/src/test/java/sg/edu/nus/serms/<模块>/`；前端 `frontend/src/features/<模块>/**/*.test.tsx`
- 后端：单元测试（领域规则）+ 集成测试（真实 PostgreSQL 18，Testcontainers）+ API / 权限测试（MockMvc）。
- 前端：组件测试（Vitest + React Testing Library，接口用 MSW 模拟）+ 至少一条自己用例主流程的 Playwright 端到端测试。
- 覆盖用例的正常流程和所有主要异常流程。
- 要求：装好 Docker 和 Node 后，`./mvnw verify` 和 `pnpm test` 就能跑完；每个测试自己准备数据，不依赖执行顺序和其他测试。
- CI：每个 PR 跑全部前后端测试，任何测试失败或后端 service/domain 覆盖率低于 70% 都不能合并；不允许靠跳过或删除测试让 CI 变绿。

每位成员的测试范围清单见 [docs/tech-stack.md 第 5 节](docs/tech-stack.md#5-测试台test-bench每人负责自己的)。

## 协作与发布

- 所有改动走 PR。`main` 受分支保护，必须通过 CI 检查（gitleaks、Trivy、依赖审查）才能合并。
- 所有有 Write 权限的协作者都可以自行合并自己的 PR，不需要他人审批，只要求 CI 全绿。
- 线上地址：https://serms.midas.cyou （香港服务器，Let's Encrypt 证书自动续期）
- 合并进 `main` 后自动部署到香港服务器 `/opt/agfp`（见 `.github/workflows/deploy.yml`、`scripts/deploy.sh`）。
- 部署走 Docker Compose：仓库根目录需有 `compose.yaml` + `Dockerfile`，容器监听的端口映射到宿主机 `127.0.0.1:8789`，由 nginx 反代对外。
- 机密只放服务器 `/opt/agfp/.env`（模板见 `.env.example`），不进 git。

## SERMS Sprint 1 application

通知后端位于 `app/`，React 收件箱与登录页面位于 `frontend/`。本分支新增的 REST、Identity 和前端开发方式见 [对齐说明](docs/platform-rest-react.md)。公共 PostgreSQL 18 迁移、真实账号和部署整合仍待团队依赖合入；旧 H2 local 配置仅可运行通知测试基线，不能用于完整登录验收。
