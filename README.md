# SERMS

Shared Equipment Reservation and Maintenance System（共享设备预约与维护系统）· SWE5006 Team 07

> 仓库名 AGFP 来自审批邮件里的项目名（Assignment Grading and Feedback Platform），与 Proposal 不一致，正在向老师确认。

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

## 协作与发布

- 所有改动走 PR。`main` 受分支保护，必须通过 CI 检查（gitleaks、Trivy、依赖审查）才能合并。
- 所有有 Write 权限的协作者都可以自行合并自己的 PR，不需要他人审批，只要求 CI 全绿。
- 线上地址：https://serms.midas.cyou （香港服务器，Let's Encrypt 证书自动续期）
- 合并进 `main` 后自动部署到香港服务器 `/opt/agfp`（见 `.github/workflows/deploy.yml`、`scripts/deploy.sh`）。
- 部署走 Docker Compose：仓库根目录需有 `compose.yaml` + `Dockerfile`，容器监听的端口映射到宿主机 `127.0.0.1:8789`，由 nginx 反代对外。
- 机密只放服务器 `/opt/agfp/.env`（模板见 `.env.example`），不进 git。
