---
name: serms-verify
description: Verify SERMS changes and bind local, CI, and acceptance evidence to real HEAD, base, and input versions.
---

# Verification

Sources: [Testing](../../../docs/testing.md), [Existing test bench requirements](../../../docs/tech-stack.md#5-test-benches-each-member-owns-their-tests).

1. Record OS, tool versions, HEAD, base and fetch time, and uncommitted inputs. Check that tests cover the delivery and current dependency combination.
2. Run the closest behavior checks, then complete task and existing PR verification scope. Use testing commands and actual workflows; do not apply Gateflow's Go/Python tests.
3. Isolate test resources; do not reuse others' containers, databases, ports, or unversioned reports.
4. Mark each result `passed / failed / not run / blocked` with evidence or reason. Keep gaps visible without real PostgreSQL 18, remote CI, or deployment evidence.
5. Rerun affected checks after HEAD/base changes. Exercise rule/template changes with a real task to confirm they preserve existing authorization, dependencies, and human confirmation boundaries.

Deliver: commands, versions, results, evidence locations, and remaining acceptance. Local checks do not replace CI, member checks, contract confirmation, or Done.
