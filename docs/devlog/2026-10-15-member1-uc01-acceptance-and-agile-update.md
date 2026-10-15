# Member 1 Return: UC01 Acceptance and Agile Update (2026-10-15)

Owner: Shi Wenqi (@YukiOvOb) · Requirement: UC01 (Proposal 5.3, backlog R1.1-R1.12), Member 1 shared duty (requirements, tasks, Sprint progress) · Baseline: `main` `f9596c1`

## 2026-10-15 Picking up Member 1 after the repository suspension

### What was built

- `scripts/check-uc01-browser.mjs`: UC01 acceptance in a real browser against the local demo, with two borrowers on desktop and mobile. It covers filtered window search, retired-item hiding, reserving from a search result, a shared search link, an overlapping request refused with the conflict message, an adjacent slot accepted, the per-item limit stopping a four-day request, approval routing and cancellation releasing the slots. It cancels every reservation it makes.
- UC01 analysis and design class diagrams (`docs/diagrams/uc01-analysis-class.mmd`, `uc01-design-class.mmd`); the sequence diagram now shows the overdue check and the audit write.
- [UC01 document](../uc01-search-and-reserve.md): rules 9 and 11, the per-item limit, exception A12 and the audit row, five Strategy policies, class diagrams, current test counts and limitations.
- Agile documents: backlog traceability from every UC01 requirement to tests and evidence, Sprint status for 2026-10-15, a demo script based on the seeded local data, the agile evidence index with both repositories' PR numbering, and a [contribution record](../agile/contribution-shi-wenqi.md).
- [D-013](../collaboration/decisions.md#d-013-shi-wenqi-resumes-member-1-work) records the return.

### Design notes

The browser check reuses `scripts/lib/local-demo-client.mjs`, so it only runs against this checkout's isolated demo, like the existing UC01/UC04 checks. Times are chosen as a random hour-aligned window two to nine days ahead so reruns do not collide with earlier runs, and the desktop and mobile passes use windows six hours apart.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| 42 PostgreSQL test errors on the first backend run | The PostgreSQL 18 build used first ships without the `btree_gist` extension needed by the exclusion constraint | Used a PostgreSQL 18.4 build that includes it, via `SERMS_APP_TEST_JDBC_URL` | 589 tests, 0 errors |
| Playwright could not launch | The installed Playwright expects a newer browser build than the one available offline | `SERMS_CHROMIUM` option in the new script; a local launch option for the mocked suite | 16/16 real, 12/12 mocked |
| `scripts/start-local.ps1` cannot run on Linux | The script uses Windows executables (`postgres.exe`, `mvnw.cmd`) | Ran the same steps by hand (database, jar, seed, Vite) | Smoke 28/28 and UC01/UC04 checks 12/12 passed |
| Backlog ownership table disagreed with the Sprint plan and ownership boundaries | The 2026-10-07 edit swapped the shared responsibilities | Restored the team division of work | Compared with `sprint-plan.md` and `ownership.md` |

### Verification

All on `f9596c1` plus this branch, PostgreSQL 18.4: backend `./mvnw -B verify` 589 passed; UC01 service + domain line coverage 96.4%; frontend lint, typecheck, 133 component tests and `check:api` passed; mocked Playwright 12 passed; local smoke 28, UC01/UC04 real API 12, UC01 real browser 16 passed. Details: [UC01 verification](../evidence/uc01/verification.md).

### Deviations

None from the Proposal or contracts. Node.js 22 was used locally instead of 24.

### Follow-ups

- Member check of this PR; UC01 part of Issue #9 once the repository owner acknowledges D-013.
- Rehearse demo step 15 (overdue borrower refused) in the browser with Member 5's seeded overdue loan.
- Sprint 3 Review screenshots and Sprint 4 report chapter on agile development.

### Authorship

Shi Wenqi directed and reviews this work; it was prepared with agent assistance. No hours, meetings, reviews or approvals are recorded.
