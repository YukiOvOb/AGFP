# Real role matrix including display-name PATCH (2026-10-15)

Input HEAD `7afe031209cb6384d080b105e84974c6aff17a93`, fetched main
`2d19ef9b4e7b7e38c7241e8b5f9e4dcf524db9cf`, with uncommitted display-name slice inputs identified
by the exact source hashes in [verification.json](verification.json).

`node scripts/check-role-browser.mjs --publishable-evidence` passed on actual React / Spring
Security / PostgreSQL 18.6, desktop and mobile, 18:22:00–18:23:44 SGT:

- 66 check groups; 210 observations over 15 pages and 350 reads over 25 API families.
- 140 valid-CSRF role-denied writes, including PATCH display-name updates; correct HTTP methods,
  syntactically valid bodies, no reliance on invalid-token rejection.
- 4 cross-user ownership denials; missing-CSRF and actual logout checks.
- Anonymous, five explicit roles and a second borrower; no implicit ADMIN inheritance.
- Identical before/after account, role, business, audit and notification-read fingerprints.

Run **before** `check-admin-browser.mjs`: the older read-only fixture guard correctly requires exactly
the six provisioned demo accounts. The [separate admin workflow](../admin-display-name-20261015/README.md)
then creates and renames real accounts. No account/audit cleanup or relaxed fixture guard is used.
Existing completed UC02/loan/repair history is reused from this worktree's retained isolated demo;
the full UC02 browser-writing workflow was not rerun for this name-only slice. Backend UC02/UC03/UC05
integration was rerun and passed. Earlier browser receipts remain historical.

Screenshots show synthetic demo data; raw traces/credentials remain ignored. Equipment management
#17 is still explicitly unimplemented at this baseline. This is local technical evidence, not new-HEAD
CI, owner confirmation, a member check, deployment or team Done.
