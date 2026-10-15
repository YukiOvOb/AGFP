# UC01 Verification Record (2026-10-15)

Member 1 · Shi Wenqi (@YukiOvOb). Baseline: `main` `f9596c1`. Everything below ran locally on Linux; hosted CI on the branch head is recorded separately on its PR.

## Environment

| Item | Value |
| --- | --- |
| Database | PostgreSQL 18.4 on loopback with `btree_gist`; isolated `serms_test` database for the backend suite, `serms_local_demo` for the running system |
| Backend | Java 17, `./mvnw -B verify`; external database through `SERMS_APP_TEST_JDBC_URL` because Docker was not available |
| Frontend | pnpm 11, Node.js 22 (the repository asks for Node.js 24; every command still passed) |
| Browser | Preinstalled Chromium through `SERMS_CHROMIUM` / a local launch option, headless |
| Local demo | The same steps as `scripts/start-local.ps1`, run by hand on Linux: Flyway V001-V006 on startup, `scripts/demo-data.sql`, Vite forwarding to the backend; accounts and password stay in `.cache/local-demo/access.json` (not committed) |

## Results

| Check | Command | Result |
| --- | --- | --- |
| Backend, all modules | `cd app && ./mvnw -B verify` | 589 tests, 0 failures, 0 errors, 0 skipped |
| UC01 backend share | Surefire reports for `equipment.*` and `reservation.*` | 91 tests: unit 35, PostgreSQL integration 39, authorization/API 17 |
| UC01 coverage | JaCoCo, `equipment.{domain,service}` and `reservation.{domain,domain.policy,service,service.event}` | 96.4% of lines (323 covered, 12 missed) |
| Frontend | `pnpm lint && pnpm typecheck && pnpm test` | Clean lint and typecheck; 133 component tests passed (UC01: 17) |
| API types | `OPENAPI_FILE=../app/target/openapi.json pnpm check:api` | Generated types match the backend specification |
| Mocked browser flows | `pnpm test:e2e` | 12 passed, including the UC01 flow on desktop and mobile |
| Local demo smoke | `node scripts/smoke-local.mjs` | 28 passed |
| UC01/UC04 real API checks | `node scripts/check-uc01-uc04.mjs` | 12 passed |
| UC01 real browser | `node scripts/check-uc01-browser.mjs` | 16 passed (8 checks × desktop and mobile), status `passed` |

## What the Real Browser Check Covers

Two borrowers in separate browser sessions, nothing mocked. All reservations it creates are cancelled at the end, so it can be rerun.

| Check | Requirement | Screenshot (desktop / mobile) |
| --- | --- | --- |
| Category + window search shows the tripod Free and hides the retired camera | R1.1, R1.2, R1.6 | — |
| Borrower A reserves from the search result; the window is prefilled; the reservation is Confirmed | R1.4, R1.9 | `real-desktop-01-confirmed.png`, `real-mobile-01-confirmed.png` |
| Borrower B opens A's shared search link and sees the tripod Not free | R1.2, R1.10 | `real-*-02-shared-link-not-free.png` |
| B's overlapping request is refused with "This equipment is already reserved for part of that time" and the taken slot is listed without A's identity | R1.3, R1.5 | `real-*-03-conflict.png` |
| B's adjacent slot starting when A's ends is Confirmed (half-open intervals) | R1.5 | — |
| A four-day request for the three-day camera is stopped in the browser with the item limit shown | R1.7 | `real-*-04-item-limit.png` |
| The approval-required camera request is Waiting for approval | R1.4, UC02 hand-off | `real-*-05-pending-approval.png` |
| Cancelling releases the slots; the shared search shows the tripod Free again | R1.8 | — |

## Not Established Here

- Hosted CI on the branch head, member checks, cloud deployment and team Done.
- Concurrency (eight parallel requests, one winner), checked-out cancellation, disabled accounts and overdue blocking are covered by `ReservationPostgresTest` on real PostgreSQL, not by the browser check.
- Results on Windows and Node.js 24 were not rerun in this session.
