# Static-site stage: welcome page. Update this file when replacing it with the application;
# compose / deploy.sh can remain unchanged.
# nginx-unprivileged runs as non-root on port 8080 and satisfies Trivy's image security checks.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --chown=nginx:nginx web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx:nginx web/index.html /usr/share/nginx/html/index.html
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1
