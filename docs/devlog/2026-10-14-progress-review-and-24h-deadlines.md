# Progress Review and 24-Hour Deadlines

Owner: Liu Tongyao (@cpg25ncu) · Event: re-planning · Branch: `docs/liu-tongyao-24h-deadline-rule` · Baseline: main `bd6709d`

## 2026-10-14 Review and new deadlines

### What happened

The repository owner asked for a review of project progress, at first with the aim of moving stalled Issues to himself. After the review he decided not to re-assign anything and instead introduced a 24-hour completion rule ([D-012](../collaboration/decisions.md#d-012-24-hour-completion-rule)). The deadline in every open task Issue (#5-#10, #17-#22, #24-#26) and the plan in #27 were rewritten, and each Issue received an agent-labelled comment so its owner is notified.

### Progress at main `bd6709d` (2026-10-14 23:34 SGT)

All eight workflows on `main` passed.

| State | Issues |
| --- | --- |
| Acceptance complete, ready to close | #15 integration line, #16 frontend foundation, #23 reports |
| Partly delivered, remainder waits for #17 | #21 (readable names, D9 and D12 done in #36 and #37; V006 fault fields left), #22 (overdue rule and audit done in #32; per-equipment limit left) |
| In progress | #19: draft #12 has must-fix review findings; sync proposal #34 waits for its owner |
| Not started since assignment on 2026-10-14 00:34 SGT | #17 and #20 (Zhou Fanhao), #18 and #24 (Zhang Hanming); no branch, PR or comment, latest commits from both on 2026-10-07 |
| Not started, not yet due under the old plan | #25 (after #19), #26 (after feature freeze), #10 (needs #18's first-admin bootstrap and the server access) |
| Member sign-offs | #6, #7, #8 can only be completed by their owners |

The critical paths were #17's migration V005 (unblocks #21 and #22) and #18's first-admin bootstrap (unblocks deployment, #10).

### Design notes

- **Counting from adoption.** Counting 24 hours from the original assignment would have made every task due about an hour after the rule was set. The owner chose to count from adoption: unblocked work is due Thu 2026-10-15 23:00 SGT.
- **Dependencies get their own clock.** Work that cannot start before another task merges is due 24 hours after that merge, so a late blocker does not make the dependent task overdue from the start.
- **Fixed end dates stay.** The presentation (10-26) and the latest deployment and code freeze (10-22) are unchanged; the rule only pulls work forward.
- **Notification.** Editing an Issue body sends no GitHub notification, so each Issue also received a comment that mentions its owner.

### Problems and fixes

| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |
| Re-assigning the stalled tasks to the owner was the first request | Considered with its effect on individual contribution and on contract confirmations that cannot move (#20 needs Zhou Fanhao, #18 may need Wang Yuanmeng) | The owner decided against re-assignment and set the 24-hour rule instead | Recorded in D-012 |
| A body comparison after the edits reported every Issue as different | `gh issue view --jq` appends a trailing newline | Compared ignoring blank lines | All 16 bodies matched the prepared text |

### Verification

- 16 Issue bodies edited with `gh issue edit --body-file` and read back; the only difference from the prepared text was the trailing newline.
- 15 comments posted (#5-#10, #17-#22, #24-#26); #27 carries the schedule itself.
- `node scripts/check-collaboration.mjs` and `git diff --check` for this record.

### Deviations

None from the Proposal. D-010's intermediate dates are replaced by D-012.

### Follow-ups

- Owners report on their Issue the same day if a deadline is at risk, so work can be re-split on #27 (D-007).
- #15, #16 and #23 are ready for the owner to close.

### Authorship

The review, Issue edits, comments and this entry were produced by an AI agent (Claude Code) under Liu Tongyao's instruction. The rule and its parameters were chosen by Liu Tongyao. No teammate review or acceptance is claimed.
