# AGFP

Assignment Grading and Feedback Platform

## 协作与发布

- 所有改动走 PR。`main` 受分支保护，必须通过 CI 三道安全检查（gitleaks、Trivy、依赖审查）才能合并。
- 线上地址：https://serms.midas.cyou （香港服务器，Let's Encrypt 证书自动续期）
- 合并进 `main` 后自动部署到香港服务器 `/opt/agfp`（见 `.github/workflows/deploy.yml`、`scripts/deploy.sh`）。
- 部署走 Docker Compose：仓库根目录需有 `compose.yaml` + `Dockerfile`，容器监听的端口映射到宿主机 `127.0.0.1:8789`，由 nginx 反代对外。
- 机密只放服务器 `/opt/agfp/.env`（模板见 `.env.example`），不进 git。

## SERMS Sprint 1 数据基础

Zhou Fanhao 的 Java/JDBC 领域与预约数据模块位于 [database](database/README.md)。
数据库现已按 SERMS 图文升级至 V002，覆盖十个业务实体，详见 [调整记录](docs/serms-database-alignment.md)。包含 PostgreSQL 迁移、并发冲突保护、真实数据库测试与 CI，以及 [领域设计](docs/sprint1-domain.md) 和 [交付记录](docs/sprint1-zhoufanhao.md)。
本地验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1`。

## 通知接口集成

与 Wang Yuanmeng 通知分支的 PostgreSQL 适配位于 [integration/notifications](integration/notifications/README.md)，包含 V003 迁移、真实 LoanReminderGuard 和最小接入补丁。
本地测试：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1`；结果见 [接口对齐记录](docs/notification-interface-alignment.md)。
