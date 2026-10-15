# UC02 dual-role browser evidence — 2026-10-15

Human owner: Zhang Hanming; agent-assisted implementation/verification: Codex. Refs #8 and #24, supporting [PR #46](https://github.com/cpg25ncu/shared-equipment-reservation-and-maintenance-system/pull/46); not Issue closure or human approval.

## Actual run

`node scripts/check-uc02-browser.mjs --publishable-evidence` passed **37 checks** on 2026-10-15, approximately **10:37–10:42 SGT**, against the actual local React / Spring Boot 4.0.8 / PostgreSQL 18.6 stack. Five separate role sessions plus one actual BORROWER+APPROVER session were used at each of 1440×1000 and 390×844. No mocked API, fake clock or historical data reset was used.

[Verification JSON](verification.json) records the run ID, exact times, source/SQL SHA-256 hashes, starting HEAD, fetched main, checks and synthetic fixture reservation IDs. The run executed the starting HEAD plus those working-tree inputs; an old CI success is not presented as verification of a later commit.

## Self-decision assertions

1. The real login and `auth/me` confirm both BORROWER and APPROVER. The browser creates a real reservation; the approval list labels it “Your request”.
2. The detail page shows “You cannot approve or reject your own reservation.” and renders neither decision radio nor submission control.
3. The actual page sends APPROVED and REJECTED POSTs directly with its real session/CSRF cookie. Both return **403 /problems/self-approval**.
4. Read-only PostgreSQL snapshots after each attempt match the pre-attempt reservation status/version, decision count, successful approval-audit count and recipient notification-request count. A different approver's real API still returns the pending, undecided record.
5. That different approver then rejects the same record through the actual UI; exactly one decision, one successful audit and one additional notification request exist.
6. The temporary role added by this run is removed. A separate fresh login after cleanup confirms the original **BORROWER-only** role.

The current notification schema intentionally has no stored source-ID column. The fixture snapshot compares this actor's request count in the single-writer local demo; it does not invent a column or change the schema. Notifications may be delivered concurrently, but changing delivery status does not change request count.

## Screenshots

Both screenshots were visually inspected before publication. They show synthetic demo names and identifiers, not real accounts, cookies, login fields or credentials.

- [Desktop self-decision block](desktop-self-decision-blocked.png)
- [Mobile self-decision block](mobile-self-decision-blocked.png)

Raw browser traces are deliberately excluded: they can contain session tokens. They remain under ignored local runtime storage. The optional publication flag exports only screenshots; this reviewed JSON omits private paths, credentials and traces.

## Isolation and remaining work

SQL helpers fail closed outside the explicitly provisioned six-user local PostgreSQL 18 demo. All three entry points were checked against another database, and an invalid run identifier was rejected before fixture/role changes commit. Cleanup only removes a role inserted by the current run; a pre-existing assignment is preserved. Normal success and caught-error paths execute cleanup; force-killing the runner can interrupt cleanup and must not be confused with a recorded restoration pass.

Runtime services were stopped after verification, preserving all data/history/logs. Existing frontend lint/typecheck/build, 130 component tests and 12 mocked E2E cases also passed. Hosted CI, independent member acceptance, future admin/equipment/overdue-page integration and deployment remain separate evidence.
