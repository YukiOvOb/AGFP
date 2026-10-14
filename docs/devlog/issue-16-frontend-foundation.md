# Frontend Component Foundation (#16)

Owner: Liu Tongyao (@cpg25ncu) · Requirement: Proposal 7.4, backlog R-P5 · Branch: `feature/liu-tongyao-frontend-foundation` · Baseline: main `457a2a9`

## 2026-10-14 Components, tokens, navigation and overview

### What was built

- **Shared components** in `frontend/src/components/ui/`: `DataTable` (TanStack Table: sortable headers, client or API paging, loading and empty states), `Dialog`, `ConfirmDialog`, `Tabs`, `Field` with `Input` / `Select` / `Textarea`, `Badge` / `StatusBadge`, `Card`, `PageHeader`, `Skeleton`, `toast`. `Button` gained `destructive` and `size="sm"`; `EmptyState` accepts a title, description and action.
- **Design tokens** in `frontend/src/styles.css`, named as shadcn/ui expects and exposed as Tailwind utilities. Badge colours now read the tone tokens.
- **Display maps**: `frontend/src/lib/roles.ts` (stored `CUSTODIAN` / `MAINTAINER` shown as Storekeeper / Technician) and loan and maintenance statuses added to `frontend/src/lib/status.ts`.
- **Navigation list** `frontend/src/app/navigation.ts` drives the sidebar and the overview. Five entries are reserved for #17, #18, #19 and #23 and stay hidden until their page exists.
- **Overview page** at `/` with one card per page the account can use; signing in without a return address lands there.
- **Developer gallery** at `/dev/components` (development builds only) and a usage note, `frontend/src/components/README.md`.
- New packages: `@radix-ui/react-dialog`, `@radix-ui/react-alert-dialog`, `@radix-ui/react-tabs`, `sonner`.

### Design notes

- **Cascade layers.** The existing shared CSS was unlayered, and in Tailwind 4 utilities live in a layer, so any utility on a `td`, `h1`, `p`, `a` or `button` lost to the old element rules whatever its specificity. The old styles (shared, approval, maintenance) now load into the `components` layer in their original order. They still override the Tailwind reset; utilities override them. This is the change that makes the agreed Tailwind/shadcn stack usable for new pages.
- **Moved instead of wrapped.** Wrapping 930 lines in `@layer components { }` would have re-indented every line of a file other members edit. The content moved to `frontend/src/styles/shared.css` and is imported with `layer(components)`; Git records it as a rename with small edits.
- **Token names.** `--muted` meant a text colour here but a background in shadcn/ui. The eleven uses became `--muted-foreground`, so generated shadcn components work unchanged.
- **Native select.** `Select` is the platform control styled like `Input`, not the Radix one: it works with touch, screen readers and `register()` and needs no test polyfills. A searchable dropdown can be added when a page needs one.
- **Reserved menu entries, hidden.** Agreeing paths and labels now avoids five pull requests editing `Layout.tsx`, but a link to a page that does not exist is worse than no link, so each entry has an `available` flag that its own pull request switches on. Reserved paths are flat (`/overdue`, `/manage/equipment`) so they do not light up the existing `/loans` and `/equipment` menu items.
- **Confirmation stays open.** `ConfirmDialog` does not close itself on confirm, so the caller can keep it open while the request runs and close it on success.
- **Display only.** No backend or API change; role and status codes are untouched, only how they are shown.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Backend failed to start: "Found more than one migration with version 001" | `app/target/classes` still held resources from an older branch build (`V1__notification_foundation.sql`, `V2__…`, `db/mysql/V3__…`); `package` without `clean` put them in the JAR. The launcher then polled health for about four minutes before reporting | `mvnw clean package`; a follow-up task changes `scripts/start-local.ps1` to build clean and fail fast | JAR lists only V001-V004; Flyway applied four migrations; health `UP` |
| Tailwind utilities had no effect on table cells, headings and links | Unlayered legacy element rules beat layered utilities | Legacy styles moved into the `components` layer | Gallery renders as designed; existing pages pixel-identical |
| `pnpm add` re-sorted every dependency in `package.json` | pnpm sorts keys when it writes the file | Restored the file and added the four lines by hand; lockfile verified with `--frozen-lockfile` | Four-line diff |
| Focus fell to the page body after a confirmation closed | Radix returns focus only to a `Dialog.Trigger`; a dialog opened from state has none | `useReturnFocus` remembers the focused element on open and restores it on close, ignoring the body so trigger-based dialogs keep Radix's behaviour | Component test and real-browser check: focus is on the opening button |
| A field next to a taller field grew a tall select and a gap under its label | The field is a grid, and its rows stretched to fill the outer grid row | `content-start` on `Field` | Screenshot |
| The gallery overflowed by 22 px at 390 wide | A wide table widened its card: grid items do not shrink below their content | `min-w-0` on `Card`, so the table scrolls inside | Scroll width equals viewport at 1440, 820 and 390 |
| Unit test saw the menu without the unread count | The count arrives after the first render | Wait for the count, then assert the menu | Test passes repeatedly |
| Real-browser scripts waited for `/notifications` after login and matched two "Find equipment" links | Landing page changed; the label is now also a card | Wait for the overview heading; select the menu link with `exact: true` | Both scripts pass |
| The browser checks rewrote tracked screenshots under `docs/evidence/` | The scripts save evidence into the repository | Restored them after each run; this task keeps its own evidence folder | `git status` |
| One mocked browser run failed 4 of 12 by timeout | Not proven. Probably the dev server re-optimizing newly added packages and reloading pages mid-test | None needed for CI, which installs fresh; recorded | Five later runs passed, one from an emptied cache |
| The API smoke stopped on its fourth run | Each run consumes a real booking; the demo ran out of free approval-required equipment | None; by design the script will not delete history | Three earlier runs passed 28 of 28 |

### Verification

See [the verification record](../evidence/frontend-foundation/verification.md): formatting, lint, types, 112 component tests, production build, 12 mocked browser tests, 6 + 10 real-browser checks and 28 real API checks, a pixel comparison of 22 existing page captures, overflow at three widths and keyboard focus.

### Deviations

- `Select` is a native control, not the Radix select listed in the task text.
- Reserved menu entries are present but hidden; the task text said "reserved (shown by role)".
- A development-only gallery page was added; it was not asked for and is excluded from production builds.
- The `scripts/` browser checks were edited although the task scope named only `frontend/`; they are test benches broken by the landing change.

### Follow-ups

- #26: move existing pages onto these components, fold `approval.css` and `maintenance.css` in, raise the contrast of the muted text colour, and replace the stacked mobile menu.
- Owners of #17, #18, #19, #23: switch on your menu entry with your route.
- `scripts/start-local.ps1`: clean build and fail-fast readiness (separate task).

### Authorship

Design, implementation, tests and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction, who owns the task. No teammate review is claimed.

## 2026-10-14 Merge

Merged with #31 as `fa266a5`. The first hosted run of the `frontend` check failed after 12 seconds at "Generate OpenAPI from backend tests": the Maven wrapper could not download Maven (`wget: Failed to fetch ... apache-maven-3.9.11-bin.zip`). A rerun of that job passed; all twelve checks were green before the merge. The issue checklist was updated; closing is left to the owner.
