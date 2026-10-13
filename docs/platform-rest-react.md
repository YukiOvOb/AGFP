# Wang Yuanmeng: REST, Identity, and Notification Frontend Alignment

## References and Scope

The technical baseline is main's `README.md` and `docs/tech-stack.md`. Identity conventions follow
`docs/contracts/auth-and-approval.md` from PR #7. The approval event contract is defined in Draft PR #8. This PR branches from the current main and covers platform authentication, REST APIs, and React notification pages. Approval event adaptation remains on a separate branch, pending the approval PR's merge.

Target technology stack: Java 17 / Spring Boot 3.5 / PostgreSQL 18 / a single Flyway migration sequence;
React 19 / TypeScript 5 strict / Vite 7 / Router 7 / TanStack Query 5 + Table 8 /
openapi-fetch + openapi-typescript / React Hook Form + Zod / Radix + Tailwind 4 / Node 24 + pnpm.

## Implemented on This Branch

- `/api/v1/auth/csrf`, `login`, `me`, and `logout`: JSON authentication, session fixation protection, and cookie-based CSRF protection.
- `SermsUserPrincipal` uses a UUID as its name; sessions store neither passwords nor password hashes.
- BCrypt verification; unknown users, incorrect passwords, DISABLED accounts, and inconsistent identity mappings all return the same 401 response.
- Paginated `/api/v1/notifications`, `/unread-count`, and `/{id}/read`. The recipient is derived solely from the authenticated identity.
- Consistent ProblemDetail responses, validation field errors, development OpenAPI documentation, and the public `/api/v1/health` endpoint.
- React login, protected routes, explicit role restrictions, shared layout, inbox, unread count, and marking notifications as read; UTC timestamps displayed in Asia/Singapore.
- An API wrapper for cookie-based CSRF, identity and query cache cleanup on 401, OpenAPI-generated types, and drift checks.
- React replaces the original Thymeleaf pages; notification events, transactions, workers, retries, and deduplication retain their existing implementation.

## Pending External Dependencies

**This branch does not complete the PostgreSQL 18 migration and is not ready for production acceptance.**
Zhou Fanhao owns the shared tables, PostgreSQL notification adapter, and Flyway coordination. This branch adds or changes no production migrations;
the existing MySQL/H2 configuration remains until his integration PR replaces it. The Identity repository queries the agreed `public.app_user`,
`public.user_role`, and `public.role` tables. The legacy database lacks these tables, so the old local configuration cannot authenticate real accounts.
Fields such as `role.code` and `role.role_id` must be checked against the final migration.

Zhang Hanming's shared PostgreSQL 18 Testcontainers test base is not yet included on this branch.
Current backend database tests use the legacy H2 notification compatibility baseline, and Identity SQL uses an isolated in-memory test database.
MockMvc authentication tests use a mock user service; frontend MSW and Playwright tests use mock APIs.
These checks do not replace integration acceptance with real PostgreSQL and the actual reservation → approval → notification flow.

The real Loan data source and overdue scanner still depend on merging Loan functionality and subsequent iterations. The placeholder Loan guard is not considered a complete overdue feature.
Deployment Docker Compose, the server `.env`, and main are unchanged; the deployment owner will integrate web/app/db before release.

## Development and Verification

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

`NotificationIntegrationTest` exports actual springdoc output to `app/target/openapi.json`. After API changes, regenerate and commit
`frontend/src/api/schema.d.ts`; alternatively, run `pnpm gen:api` against a running development backend.
Frontend `pnpm dev` listens on 127.0.0.1:5173 by default and proxies `/api` to 127.0.0.1:8081.
Start the development backend with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; real login requires merged shared-table migrations and existing accounts.
No default production password is provided. Do not persist credentials in the browser or disable CSRF to resolve integration issues.

## Conventions for Other Modules

- Backend controllers receive `@AuthenticationPrincipal SermsUserPrincipal` and pass the UUID to their services.
- Services use `@PreAuthorize` for role checks. ADMIN does not automatically have APPROVER permission.
- Frontend modules reuse `AuthProvider`, `RequireRole role="APPROVER"`, `api`, and shared UI components; route authorization only protects the presentation layer.
- Notification content is rendered as React text; `dangerouslySetInnerHTML` is prohibited.
- Approval events follow the transaction and deduplication contracts in the existing review documentation and never use a recipient supplied by the frontend.

## Verification Still Required Before Merge

1. After shared migrations / the PostgreSQL adapter merge, verify identity columns, UUIDs, role mappings, notification upserts, and locking behavior.
2. Reuse the shared Testcontainers base to run all transaction, concurrency, and identity query tests on PostgreSQL 18.
3. Use real accounts to run login → approval → worker → inbox → mark as read; verify that business rollbacks leave no notifications.
4. Merge through a PR after CI security checks, coverage thresholds, and deployment integration pass; do not push directly to main.

## Local Verification Record (2026-09-26)

- Maven ran locally on Java 21 with a Java 17 compilation target; CI is configured for Java 17, with remote execution pending the PR.
- Full backend `mvn verify`: 56 tests passed; authentication / notification API tests were rerun separately after subsequent OpenAPI parameter annotation changes.
- Combined JaCoCo line coverage for packages ending in `/service` or `/domain`: 92.3% (241/261); this does not claim an active CI coverage gate.
- Frontend ESLint, TypeScript strict checking, and production build passed; all 5 Vitest component tests passed.
- Playwright: both desktop 1440×1000 and mobile 390×844 login → inbox → mark as read → logout tests passed (mock APIs).
- CSRF initialization and unauthenticated pages were inspected in the browser using real local Spring Boot and the Vite proxy.
- PostgreSQL 18 / Testcontainers, real accounts, and the actual Loan / approval database flow have not passed acceptance; the three remote security CI checks were not run during this local development session.
- Production Flyway files and root deployment configuration were unchanged. Approval event adaptation and shared data migrations must be completed in their respective PRs.

# 2026-10-07 Complete PostgreSQL Integration Update

The complete application now provides the `serms-postgres` profile, shared migration packaging, and Identity data mapping. Historical pending database integration items are superseded by the [current delivery record](zhoufanhao-delivery.md) and [runtime migration guide](serms-postgres-runtime.md). Both default and local configurations now use PostgreSQL 18; H2 remains only in test dependencies and resources. The team still owns server web/app/db deployment integration.
