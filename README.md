# Shared Equipment Reservation and Maintenance System (SERMS)

Shared Equipment Reservation and Maintenance System · SWE5006 Team 07

## Run the Complete Local Demo

The integrated React, Spring Boot and PostgreSQL 18 system can run locally before server handover. See the [local demo guide](docs/local-demo.md) for isolated startup/shutdown, six role accounts and real API smoke checks. Local acceptance and cloud publication are tracked separately in Issues #5-#10.

Use the full name **Shared Equipment Reservation and Maintenance System (SERMS)** on first mention in the project report, then use **SERMS** throughout the remaining text and everyday communication. The repository name is `shared-equipment-reservation-and-maintenance-system`.

## Collaboration and Agent Entry Points

- Team participation, task dependencies, branches, and handover: [Collaboration guide](.github/CONTRIBUTING.md).
- Coding agent startup and task-specific skills: [AGENTS.md](AGENTS.md).
- Ownership sources, tool boundaries, and confirmed/pending decisions: [Ownership boundaries](docs/collaboration/ownership.md), [Agent workflow](docs/collaboration/agent-workflow.md), [Decision record](docs/collaboration/decisions.md).
- Documentation, interface contracts, and verification evidence: [Documentation index](docs/README.md), [Contract index](docs/contracts/README.md), [Testing](docs/testing.md).

These entry points supplement collaboration practices; the technology stack, five-member use-case ownership, existing contracts, and Write collaborators' right to merge their own PRs remain in effect.

## Technology Stack

The frontend and backend are separate: a React single-page application, a Spring Boot REST API organized as a modular monolith, and PostgreSQL 18. Docker Compose runs the services, and GitHub Actions handles the pipeline from compilation through post-deployment verification. See [docs/tech-stack.md](docs/tech-stack.md) for requirement mappings, implementation status, and differences from the Project Proposal.

### Frontend (`frontend/`)

| Area | Technology |
| --- | --- |
| Language | TypeScript 5 (strict mode) |
| Framework | React 19 |
| Build / development server | Vite 7 |
| Routing | React Router 7 |
| Server data fetching and caching | TanStack Query 5 |
| API client | openapi-typescript + openapi-fetch (types generated from the backend API specification) |
| Forms and validation | React Hook Form + Zod |
| UI components / styling | shadcn/ui + Tailwind CSS 4 |
| Tables | TanStack Table 8 |
| Charts | Recharts 3 |
| Date and time | date-fns + date-fns-tz (displayed in Singapore time) |
| Package manager / runtime | pnpm · Node.js 24 LTS |
| Code style | ESLint + Prettier |
| Testing | Vitest · React Testing Library · MSW (API mocks) · Playwright (end-to-end) |

### Backend (`app/`)

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.0 (modular monolith; user-authorized migration in PR #13) |
| Web API | Spring MVC JSON endpoints under `/api/v1` + Jakarta Bean Validation |
| API documentation | springdoc-openapi (OpenAPI 3) |
| Error format | RFC 9457 Problem Details |
| Security | Spring Security: session cookie authentication, BCrypt password hashing, `@PreAuthorize` method authorization, and CSRF protection (cookie + request header) |
| Data access | Spring Data JPA / Hibernate; handwritten SQL through JdbcTemplate for locking or complex queries |
| Scheduled tasks | Spring `@Scheduled` |
| Operational endpoints | Spring Boot Actuator (only health and info exposed) |
| Build | Maven 3.9 (`./mvnw`) |
| Testing | JUnit Jupiter 6 · Mockito · AssertJ · Spring Boot Test · Testcontainers · ArchUnit (layering checks) |
| Coverage | JaCoCo (service + domain ≥ 70%) |
| Code style / static analysis | Spotless (google-java-format) · SpotBugs + FindSecBugs |

Backend code is organized into business modules under `sg.edu.nus.serms`: `identity`, `equipment`, `reservation`, `approval`, `loan`, `maintenance`, `notification`, `report`, `audit`, and `shared`. Modules under `frontend/src/features/` correspond to the backend modules.

### Database

| Area | Technology |
| --- | --- |
| Database | PostgreSQL 18 (`postgres:18-alpine` across local development, CI, and the server) |
| Schema migrations | Flyway |
| Reservation overlap prevention | PostgreSQL exclusion constraints (`EXCLUDE USING gist` + `tstzrange`) |
| Backups | Daily `pg_dump` backups retained for 7 days |

### DevSecOps and Deployment

| Area | Technology |
| --- | --- |
| CI/CD | GitHub Actions |
| Secret scanning | gitleaks |
| Vulnerability scanning | Trivy (filesystem + Docker images) |
| Dependency checks | Dependency Review (PR stage) + Dependabot (weekly automated upgrades) |
| Code security scanning | CodeQL (Java + TypeScript) |
| Post-deployment security scanning | OWASP ZAP Baseline |
| Load testing | k6 (50 concurrent users) |
| Containers | Docker (multi-stage builds, non-root execution) + Docker Compose: three containers, `web` + `app` + `db` |
| Image registry | GitHub Container Registry (GHCR) |
| Server | Hong Kong server + nginx + Let's Encrypt, https://serms.midas.cyou |

### Optional Features (If Time Permits)

| Feature | Technology |
| --- | --- |
| Email notifications | Spring Mail; Mailpit receives test emails during development |
| QR codes | Generated with `qrcode` and scanned with `@zxing/browser` in the frontend |

### Collaboration

| Area | Technology |
| --- | --- |
| Source control | Git + GitHub |
| Project management | GitHub Projects |
| Design diagrams | PlantUML / Mermaid (source files in `docs/diagrams/`), supplemented by draw.io |

> Currently implemented on main: Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, Maven, JUnit / Mockito, JaCoCo, GitHub Actions, gitleaks, Trivy, Dependency Review, Docker Compose, and server deployment. The PostgreSQL schema is on Zhou Fanhao's branch; the React frontend and remaining pipeline stages are still pending. See [Section 1 of docs/tech-stack.md](docs/tech-stack.md#1-complete-technology-stack) for individual status entries.

## Testing: Each Member Owns Their Test Bench

CI can only run tests that have been written. **Each member must provide a complete test bench for their use cases and submit it in the same PR as the feature code**, so every PR can verify its behavior in CI.

- Locations: backend `app/src/test/java/sg/edu/nus/serms/<module>/`; frontend `frontend/src/features/<module>/**/*.test.tsx`.
- Backend: unit tests (domain rules) + integration tests (real PostgreSQL 18 with Testcontainers) + API / authorization tests (MockMvc).
- Frontend: component tests (Vitest + React Testing Library with MSW API mocks) + at least one Playwright end-to-end test for the main flow of the member's use case.
- Cover the normal flow and all major exception flows.
- Requirements: after installing Docker and Node, `./mvnw verify` and `pnpm test` must run the tests; each test prepares its own data and is independent of execution order and other tests.
- CI: every PR runs all frontend and backend tests. Any failing test or backend service/domain coverage below 70% blocks merging; skipping or deleting tests to make CI pass is prohibited.

See [Section 5 of docs/tech-stack.md](docs/tech-stack.md#5-test-benches-each-member-owns-their-tests) for each member's test scope.

## Collaboration and Releases

Release preparation for Issue #10 is documented in [the server handover and release runbook](docs/server-release.md). The candidate builds the real React/backend/PostgreSQL system and gates deployment on the current main SHA, successful checks and verified handover. Production readiness remains disabled until the actual server inventory and acceptance are complete.

- Cross-module interface contracts live in [`docs/contracts/`](docs/contracts/); the repository version is authoritative. Changes require a PR and confirmation from the owners of affected modules. Available contracts: [Authentication and Approval (UC02)](docs/contracts/auth-and-approval.md), [Equipment Search and Reservation (UC01)](docs/contracts/equipment-and-reservation.md).
- All changes go through PRs. `main` is protected, and CI checks (gitleaks, Trivy, and Dependency Review) must pass before merging.
- Collaborators with Write permission may merge their own PRs without another reviewer's approval, provided all CI checks pass.
- Live site: https://serms.midas.cyou (Hong Kong server, automatically renewed Let's Encrypt certificate).
- Merging into `main` automatically deploys to `/opt/agfp` on the Hong Kong server (see `.github/workflows/deploy.yml` and `scripts/deploy.sh`).
- Deployment uses Docker Compose: `compose.yaml` and `Dockerfile` must exist at the repository root. The container port maps to the host's `127.0.0.1:8789`, with nginx providing the public reverse proxy.
- Secrets are stored only in `/opt/agfp/.env` on the server (template: `.env.example`) and must never be committed to Git.

## SERMS Sprint 1 application

The backend lives in `app/`, and React pages live in `frontend/`. The default configuration uses PostgreSQL 18 and loads the shared Flyway migrations, notification adapter, and Identity data mapping; see the [PostgreSQL guide](docs/serms-postgres-runtime.md) for startup instructions. The `local` development configuration also uses PostgreSQL; H2 exists only in test dependencies and resources. Real demo accounts and web/app/db deployment still require team integration.

## UC01 Search and Reserve Equipment (Member 1)

Backend `equipment` and `reservation` modules in `app/`, and frontend `frontend/src/features/{equipment,reservation}`: search by name / category / location / time interval, view occupied slots, submit and cancel reservations, and view personal reservations. Reservation rules use the Strategy pattern, and `ReservationApprovalService` implements the UC02 contract. The test bench includes concurrency conflict tests on PostgreSQL 18 (Testcontainers, requiring Docker). See the [UC01 documentation](docs/uc01-search-and-reserve.md) and [interface contract](docs/contracts/equipment-and-reservation.md).

Requirements, Sprint plans, progress and risks, demo scripts, and agile evidence are in [docs/agile/](docs/agile/README.md).

## SERMS Sprint 1 data foundation

Zhou Fanhao's Java/JDBC domain and reservation data module is in [database](database/README.md).
The database has been upgraded to V002 using the SERMS diagrams and documents, covering ten business entities; see the [alignment record](docs/serms-database-alignment.md). It includes PostgreSQL migrations, concurrency conflict protection, real database tests and CI, plus the [domain design](docs/sprint1-domain.md) and [delivery record](docs/sprint1-zhoufanhao.md).
Local verification: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-sprint1.ps1`.

## Notification Integration

The PostgreSQL adapter for Wang Yuanmeng's notification branch is in [integration/notifications](integration/notifications/README.md), including the V003 migration, a real LoanReminderGuard and a minimal integration patch.
Local tests: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-integration.ps1`; see the [interface alignment record](docs/notification-interface-alignment.md) for results.

## Zhou Fanhao: Checkout, Return, and Database Integration

UC03 backend, the React `/loans` page, the complete PostgreSQL profile, V004 data alignment, and the approval persistence adapter are provided. See the [delivery notes](docs/zhoufanhao-delivery.md), [interface contract](docs/contracts/checkout-and-return.md), [member integration checklist](docs/zhoufanhao-handoffs.md), and [verification record](docs/zhoufanhao-validation.md).

Full verification: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-zhoufanhao.ps1`; without Docker, add `-PostgresBin 'C:/Program Files/PostgreSQL/18/bin'`. The complete application uses PostgreSQL by default without selecting a database profile; see the [PostgreSQL runtime guide](docs/serms-postgres-runtime.md) for migration and startup instructions.
