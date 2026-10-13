FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build
COPY app/ app/
COPY integration/notifications/src/main/java/ integration/notifications/src/main/java/
RUN mvn -B -f app/pom.xml package -DskipTests

FROM node:24-alpine AS frontend-build
WORKDIR /build/frontend
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM eclipse-temurin:17-jre-alpine AS app
ARG RELEASE_SHA=local
LABEL org.opencontainers.image.revision=$RELEASE_SHA
RUN addgroup -S serms && adduser -S -G serms serms
WORKDIR /app
COPY --from=backend-build --chown=serms:serms /build/app/target/serms-0.1.0-SNAPSHOT.jar app.jar
USER serms
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -qO- http://127.0.0.1:8081/api/v1/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM nginxinc/nginx-unprivileged:stable-alpine AS web
ARG RELEASE_SHA=local
LABEL org.opencontainers.image.revision=$RELEASE_SHA
COPY --chown=nginx:nginx web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build --chown=nginx:nginx /build/frontend/dist/ /usr/share/nginx/html/
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
