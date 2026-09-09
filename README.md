# AGFP

Assignment Grading and Feedback Platform

## 协作与发布

- 所有改动走 PR。`main` 受分支保护，必须通过 CI 三道安全检查（gitleaks、Trivy、依赖审查）才能合并。
- 线上地址：https://agfp.midas.cyou （香港服务器，Let's Encrypt 证书自动续期）
- 合并进 `main` 后自动部署到香港服务器 `/opt/agfp`（见 `.github/workflows/deploy.yml`、`scripts/deploy.sh`）。
- 部署走 Docker Compose：仓库根目录需有 `compose.yaml` + `Dockerfile`，容器监听的端口映射到宿主机 `127.0.0.1:8789`，由 nginx 反代对外。
- 机密只放服务器 `/opt/agfp/.env`（模板见 `.env.example`），不进 git。
