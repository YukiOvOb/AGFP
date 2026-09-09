# 静态站阶段：welcome 页。后续换成真正的应用时改这里即可，compose / deploy.sh 不用动。
# 用 nginx-unprivileged：容器以非 root 运行、监听 8080，满足 Trivy 的镜像安全检查。
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --chown=nginx:nginx web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx:nginx web/index.html /usr/share/nginx/html/index.html
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1
