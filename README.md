# AGFP

Assignment Grading and Feedback Platform

## 协作与发布

- 所有改动走 PR。`main` 受分支保护，必须通过 CI 三道安全检查（gitleaks、Trivy、依赖审查）才能合并。
- 线上地址：https://serms.midas.cyou （香港服务器，Let's Encrypt 证书自动续期）
- 合并进 `main` 后自动部署到香港服务器 `/opt/agfp`（见 `.github/workflows/deploy.yml`、`scripts/deploy.sh`）。
- 部署走 Docker Compose：仓库根目录需有 `compose.yaml` + `Dockerfile`，容器监听的端口映射到宿主机 `127.0.0.1:8789`，由 nginx 反代对外。
- 机密只放服务器 `/opt/agfp/.env`（模板见 `.env.example`），不进 git。

## SERMS Sprint 1 Data Foundation

Zhou Fanhao's Java/JDBC domain and reservation data module is in [database](database/README.md).
The database has been upgraded to V002 using the SERMS diagrams and documents, covering ten business entities; see the [alignment record](docs/serms-database-alignment.md). It includes PostgreSQL migrations, concurrency conflict protection, real database tests and CI, plus the [domain design](docs/sprint1-domain.md) and [delivery record](docs/sprint1-zhoufanhao.md).
Local verification: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1`.

## Notification Integration

The PostgreSQL adapter for Wang Yuanmeng's notification branch is in [integration/notifications](integration/notifications/README.md), including the V003 migration, a real LoanReminderGuard and a minimal integration patch.
Local tests: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1`; see the [interface alignment record](docs/notification-interface-alignment.md) for results.
