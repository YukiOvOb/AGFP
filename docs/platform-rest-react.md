# Wang Yuanmeng: REST、Identity 与通知前端对齐

## 依据和边界

技术基准为 main 的 `README.md`、`docs/tech-stack.md`，身份约定参考 PR #7 的
`docs/contracts/auth-and-approval.md`。审批事件基于 Draft PR #8 的 `1d3d7be`。
本分支基于本地审批通知适配 `656dec9`；这些依赖尚未全部进入 main，因此提交 PR 时需要注明依赖。

目标技术栈：Java 17 / Spring Boot 3.5 / PostgreSQL 18 / 单一 Flyway 序列；
React 19 / TypeScript 5 strict / Vite 7 / Router 7 / TanStack Query 5 + Table 8 /
openapi-fetch + openapi-typescript / React Hook Form + Zod / Radix + Tailwind 4 / Node 24 + pnpm。

## 本分支实现

- `/api/v1/auth/csrf`、`login`、`me`、`logout`：JSON 登录、Session 固定攻击防护、Cookie CSRF。
- `SermsUserPrincipal` 的名称为 UUID；Session 不保存密码或密码哈希。
- BCrypt 校验，未知用户、错误密码、DISABLED 或身份映射不一致均返回统一 401。
- `/api/v1/notifications` 分页、`/unread-count`、`/{id}/read`。只从登录身份取收件人。
- 统一 ProblemDetail、校验字段 errors、开发环境 OpenAPI、公开健康端点 `/api/v1/health`。
- React 登录、受保护路由、显式角色限制、统一布局、收件箱、未读数、标记已读；UTC → Asia/Singapore 显示。
- Cookie CSRF 请求封装、401 清理身份和查询缓存、OpenAPI 生成类型及漂移检查。
- 原来的 Thymeleaf 页面已由 React 替代；通知事件、事务、worker、重试和去重沿用原有实现。

## 未落地的外部依赖

**这里不是 PostgreSQL 18 迁移完成声明，也不能直接上线验收。**
周凡浩负责公共表、通知 PostgreSQL 适配、Flyway 协调；本分支没有新增或修改生产 migration，
现有 MySQL/H2 配置暂留待他的整合 PR 替换。Identity repository 已按 `public.app_user`、
`public.user_role`、`public.role` 的约定查询，现有旧库没有这些表，因此旧 local 配置不能完成真实账号登录。
`role.code`、`role.role_id` 等字段需随最终 migration 再核对。

Zhang Hanming 的 PostgreSQL 18 Testcontainers 公共测试基类还未进入本分支。
目前后端数据库测试是旧通知 H2 兼容基线，Identity SQL 使用隔离的测试内存库；
MockMvc 认证测试使用模拟用户服务，前端 MSW 和 Playwright 使用模拟 API。
这些验证不代替真实 PostgreSQL、真实预约→审批→通知链路的集成验收。

真实 Loan 数据源与逾期扫描仍依赖 Loan 合入及后续迭代。本分支没有把占位 Loan guard 视作完整逾期功能。
部署 Docker Compose、服务器 `.env` 和 main 均未修改；由部署负责人整合 web/app/db 后再发布。

## 开发与验证

```sh
cd app
./mvnw verify
cd ../frontend
pnpm install --frozen-lockfile
OPENAPI_FILE=../app/target/openapi.json pnpm gen:api
pnpm lint
pnpm typecheck
pnpm test
pnpm build
OPENAPI_FILE=../app/target/openapi.json pnpm check:api
pnpm exec playwright install chromium
pnpm test:e2e
```

`NotificationIntegrationTest` 从真实 springdoc 输出 `app/target/openapi.json`，API 变更后重新生成并提交
`frontend/src/api/schema.d.ts`；也可在开发后端启动时直接 `pnpm gen:api`。
前端 `pnpm dev` 默认监听 127.0.0.1:5173，`/api` 转发到 127.0.0.1:8081。
开发后端可以 `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`；只有公共表迁移合入且账号存在后才能真实登录。
不提供生产默认密码，不在浏览器持久保存凭据，不通过关闭 CSRF 解决联调问题。

## 给其他模块的使用约定

- 后端 controller 接收 `@AuthenticationPrincipal SermsUserPrincipal`，传 UUID 给自己的 service。
- service 使用 `@PreAuthorize` 做角色校验。ADMIN 不自动具有 APPROVER 权限。
- 前端复用 `AuthProvider`、`RequireRole role="APPROVER"`、`api` 和通用 UI；路由权限只是显示层保护。
- 通知正文作为 React 文本渲染，不允许 `dangerouslySetInnerHTML`。
- 审批事件仍按已有 review 文档的事务与去重合同，不使用前端提交的 recipient。

## 合入前仍需验证

1. 公共 migration / PostgreSQL adapter 合入后，核对身份列、UUID、角色映射、通知 upsert 和锁行为。
2. 复用公共 Testcontainers 基类在 PostgreSQL 18 运行全部事务、并发和身份查询测试。
3. 使用真实账号执行登录→审批→worker→收件箱→标记已读；核对业务回滚无通知。
4. CI 安全检查、覆盖率门槛、部署集成通过后再经 PR 合入；不要直接 push main。

## 本地验证记录（2026-09-26）

- 本地使用 Java 21 运行 Maven，编译目标 Java 17；CI 配置使用 Java 17，远端执行尚待 PR。
- 后端完整 `mvn verify`：56 项测试通过；之后对 OpenAPI 参数标注的调整单独重跑认证/通知 API 测试。
- JaCoCo 中以 `/service`、`/domain` 结尾的包合计行覆盖率 92.3%（241/261）；这不是已上线的 CI 覆盖率门槛声明。
- 前端 ESLint、TypeScript strict、生产构建通过；Vitest 5 项组件测试通过。
- Playwright：desktop 1440×1000、mobile 390×844 两项登录→收件箱→已读→退出测试通过（模拟 API）。
- 本地真实 Spring Boot + Vite 代理的 CSRF 初始化和未登录页面已在浏览器检查。
- PostgreSQL 18 / Testcontainers、真实账号、真实 Loan/审批数据库链路尚未验收；远端三道安全 CI 未在本次本地开发中运行。
- 没有改动生产 Flyway 文件、根部署配置或 main；本分支依赖 PR #8 与公共数据迁移，提交 PR 时应说明这些依赖。
