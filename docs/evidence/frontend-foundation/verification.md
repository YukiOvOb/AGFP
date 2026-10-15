# Frontend Component Foundation — Verification (Issue #16)

Recorded on 2026-10-14 (Asia/Singapore) by an AI agent working for Liu Tongyao. Base: main `457a2a9`. Environment: Windows 11, Node 24.20.0, pnpm 11.19.0, Java 25, PostgreSQL 18.6, Playwright Chromium 1243. The local demo ran from this branch's working tree (Vite on 5174, backend 8082, database 55433). Agent checks are supporting evidence, not a member check.

## Results

| Check | Command or method | Result |
| --- | --- | --- |
| Formatting, lint, types | `pnpm exec prettier --check src e2e`, `pnpm lint`, `pnpm typecheck` | Passed |
| Component tests | `pnpm test` | Passed: 112 tests in 9 files (87 before this change, 25 new) |
| Production build | `pnpm build` | Passed; the development gallery is not in the bundle (searched the built script for its heading) |
| Mocked browser suite | `pnpm test:e2e` (desktop 1440, mobile 390) | Passed 12 of 12 on five runs, including one from an emptied Vite cache. One earlier run failed 4 of 12 by timeout and could not be reproduced; see the note below |
| Real browser, borrower flow | `node scripts/check-local-browser.mjs` | Passed: 6 checks; document width 390 at a 390 viewport |
| Real browser, UC01/UC04 | `node scripts/check-uc01-uc04-browser.mjs` | Passed: 10 checks (desktop and mobile) |
| Real API smoke | `node scripts/smoke-local.mjs` | Passed 28 of 28 twice with the new frontend. A later run stopped at the booking step with "No free approval-required demo equipment": every run consumes a real booking and the demo data was used up. Not a code failure |
| Existing pages unchanged | Screenshots of 11 pages for five roles at 1440 and 820 wide, before and after, compared pixel by pixel outside the sidebar | 22 of 22 identical in the content area. Only the sidebar differs (new Overview entry, role names) |
| Overflow | Scroll width against viewport on the overview and the gallery at 1440, 820 and 390 | 0 at every width after the `Card` fix; the gallery overflowed by 22 px at 390 before it |
| Keyboard focus | Opened and closed the dialog and the confirmation in a real browser | Dialog starts in its first field; confirmation starts on Cancel; focus returns to the button that opened it |

## Notes

- **Pixel comparison.** The "before" set was taken from unmodified `main`; the "after" set right after the stylesheet restructuring and before any data-changing check. A second "after" set taken at the end differs on six list pages (notifications, reservations, maintenance queue) only in height, because the checks in between created reservations, cases and notifications.
- **The one failed browser-suite run.** It happened once, directly after the gallery and three new packages were added, and lasted 1.1 minutes against 7 seconds normally. The likely cause is the development server re-optimizing dependencies it had not seen and reloading pages in mid-test; that cannot happen on a fresh install, which is what CI uses, and the run from an emptied cache passed. No trace of the failed run was kept, so this remains an explanation, not a proven cause.
- **Not covered here.** The new components are used so far only by the overview page and the gallery; their behaviour inside real feature pages is verified by the tasks that adopt them. Contrast of the muted text colour was not changed and is left to the visual pass (#26).

## Screenshots

| File | Shows |
| --- | --- |
| `overview-desktop.png` | Overview for a storekeeper at 1440 wide |
| `overview-tablet.png` | Overview for a borrower at 820 wide |
| `components-desktop.png` | Component gallery at 1440 wide |
| `components-mobile.png` | Component gallery at 390 wide |
| `confirm-dialog-desktop.png` | Confirmation dialog over the gallery |
