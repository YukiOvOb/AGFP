# Real role matrix after administrator foundation (2026-10-15)

Baseline: main `350085a8adf4a2cf9d593347d37cacd02ff61547`, with the uncommitted administrator
foundation source hashes in [verification.json](verification.json). Earlier role-access directories
are historical and were not overwritten. This is local technical verification, not hosted CI,
contract-owner confirmation, a member check, deployment or team Done.

Executed serially against a fresh isolated PostgreSQL 18.6 local demo:

1. `node scripts/check-uc02-browser.mjs`: real desktop/mobile five-role and dual-role approval,
   rejection/slot release, notification/read, physical checkout/return, linked repair and reports.
   Temporary dual-role assignment restored. [Workflow result](workflow-verification.json).
2. `node scripts/check-role-browser.mjs --publishable-evidence`: anonymous + five roles + second
   borrower, desktop/mobile, all 15 current pages (including `/admin/users`, `/admin/audit`),
   25 read API families, valid-CSRF role-denied mutation probes, missing-CSRF/logout and cross-user
   ownership checks. The creation denial probe must leave the user table unchanged too.

Both passed. All user/business/audit/notification-read/role fingerprints remained unchanged across
the read/denied matrix. New-account creation is separately verified by the
[real administrator workflow](../admin-foundation-20261015/README.md), not mixed into this read-only matrix.
Equipment management (#17) is still the explicit unimplemented page dependency at this baseline.

Screenshots contain synthetic demo users only; raw traces, cookies and generated credentials stay
under ignored local caches. No production database, mocked APIs, fake clock or deleted audit history.
