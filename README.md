# SERMS

Shared Equipment Reservation and Maintenance System（共享设备预约与维护系统）· SWE5006 Team 07

## 技术栈

一个 Spring Boot 单体应用（内部按业务模块划分）+ 一个 PostgreSQL 数据库，用 Docker Compose 运行，GitHub Actions 负责从编译到冒烟测试的整条流水线。详细说明、需求覆盖表和与 Proposal 的差异见 [docs/tech-stack.md](docs/tech-stack.md)。

| 方面 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 17 · Spring Boot 3.5（Spring MVC、Spring Security、Spring Data JPA） |
| 页面 | Thymeleaf · Bootstrap 5 · Chart.js（报表） |
| 数据库 | PostgreSQL 17 · Flyway 管理表结构迁移 |
| 构建 | Maven（`./mvnw`） |
| 测试 | JUnit 5 · Mockito · AssertJ · Spring Boot Test · Testcontainers · ArchUnit · Playwright · JaCoCo（service/domain ≥ 70%） |
| 代码质量 / 安全 | Spotless · SpotBugs + FindSecBugs · CodeQL · gitleaks · Trivy · Dependency Review · Dependabot · OWASP ZAP |
| 交付 | Docker · Docker Compose · GHCR · GitHub Actions · nginx + Let's Encrypt |
| 协作 | GitHub Projects · PlantUML / Mermaid |

代码按业务模块组织在 `sg.edu.nus.serms` 下：`identity`、`equipment`、`reservation`、`approval`、`loan`、`maintenance`、`notification`、`report`、`audit`、`shared`。

## 测试：每人写好自己的 Test Bench

CI 只能验证已经写好的测试。**每位成员必须为自己负责的用例写好完整的测试台，并和功能代码放在同一个 PR 里提交**，这样每个 PR 在 CI 上都能自己验证自己。

- 位置：`app/src/test/java/sg/edu/nus/serms/<模块>/`
- 内容：单元测试（领域规则）+ 集成测试（真实 PostgreSQL，Testcontainers）+ Web / 权限测试（MockMvc），覆盖用例的正常流程和所有主要异常流程。
- 要求：只装 Docker 就能用 `./mvnw verify` 跑完；每个测试自己准备数据，不依赖执行顺序和其他测试。
- CI：每个 PR 跑全部测试，任何测试失败或 service/domain 覆盖率低于 70% 都不能合并；不允许靠跳过或删除测试让 CI 变绿。

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
