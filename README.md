# SERMS

Shared Equipment Reservation and Maintenance System（共享设备预约与维护系统）· SWE5006 Team 07

## 技术栈

前后端分离：React 单页应用 + Spring Boot REST API（单体，内部按业务模块划分）+ PostgreSQL 18，用 Docker Compose 运行，GitHub Actions 负责从编译到部署后验证的整条流水线。详细说明、需求覆盖表和与 Proposal 的差异见 [docs/tech-stack.md](docs/tech-stack.md)。

| 方面 | 选型 |
| --- | --- |
| 前端（`frontend/`） | React 19 · TypeScript · Vite 7 · React Router 7 · TanStack Query / Table · React Hook Form + Zod · shadcn/ui + Tailwind CSS 4 · Recharts |
| 后端（`app/`） | Java 17 · Spring Boot 3.5（Spring MVC REST、Spring Security、Spring Data JPA）· springdoc-openapi |
| 数据库 | PostgreSQL 18 · Flyway 管理表结构迁移 |
| 前后端接口 | `/api/v1` JSON；OpenAPI 文档自动生成前端 TypeScript 类型；错误统一用 Problem Details |
| 构建 | Maven（`./mvnw`）· pnpm（Node 24 LTS） |
| 测试 | 后端：JUnit 5 · Mockito · Spring Boot Test · Testcontainers · ArchUnit · JaCoCo（service/domain ≥ 70%）；前端：Vitest · React Testing Library · MSW · Playwright |
| 代码质量 / 安全 | Spotless · ESLint + Prettier · SpotBugs + FindSecBugs · CodeQL · gitleaks · Trivy · Dependency Review · Dependabot · OWASP ZAP |
| 交付 | Docker · Docker Compose · GHCR · GitHub Actions · nginx + Let's Encrypt |
| 协作 | GitHub Projects · PlantUML / Mermaid |

后端代码按业务模块组织在 `sg.edu.nus.serms` 下：`identity`、`equipment`、`reservation`、`approval`、`loan`、`maintenance`、`notification`、`report`、`audit`、`shared`；前端 `frontend/src/features/` 下的模块和后端一一对应。

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

Wang Yuanmeng's notification and shared-layout foundation lives in [`app/`](app/). Run with Java 17+ and the included Maven Wrapper: `cd app && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Open http://127.0.0.1:8081. See [scope, contracts and verification](docs/wang-yuanmeng-sprint1.md). The root static deployment is unchanged pending team integration.
