# Development Log

The repository owner asked on 2026-10-14 that the development process and the problems met along the way be written down as the work happens, so the project report and individual contribution sections can be assembled from records instead of memory. See [D-011](../collaboration/decisions.md#d-011-development-log).

This log complements, and does not replace, the [decision record](../collaboration/decisions.md) (what was decided), the [contracts](../contracts/README.md) (what modules promise each other) and [evidence](../evidence/) (screenshots and reports).

## When to Write

- **Every completion task**: the PR that delivers an Issue adds or updates that Issue's entry. A PR without its entry is incomplete.
- **Cross-cutting events**: reviews, re-planning, incidents, integration merges, deployments.
- Write while the detail is fresh. A problem that took an hour to solve deserves five lines the same day.

## File Names

| Kind | Name | Example |
| --- | --- | --- |
| One Issue | `issue-<number>-<slug>.md` | `issue-17-equipment-management.md` |
| Cross-cutting event | `<YYYY-MM-DD>-<slug>.md` | `2026-10-14-project-review-and-completion-plan.md` |

One file per Issue keeps each file with a single writer, so entries do not conflict between branches. Append dated sections to the same file as the task progresses. There is no hand-maintained index; the directory listing is the index.

## Entry Template

```markdown
# <Issue title> (#<number>)

Owner: <name> (@<account>) · Requirement: <Proposal section / backlog ID> · Branch: <branch> · Baseline: <main SHA>

## <YYYY-MM-DD> <short heading>

### What was built
User-visible behaviour, endpoints, pages, migrations. Link the PR.

### Design notes
Options considered and why this one. Name the design pattern where one is applied
(Strategy, Chain of Responsibility, State, Observer, Adapter) and the class that carries it.

### Problems and fixes
| Symptom | Root cause | Fix | How it was verified |
| --- | --- | --- | --- |

### Verification
Commands, counts, the HEAD SHA they ran on, and paths of screenshots or reports under docs/evidence/.
State what is mock-based and what ran on real PostgreSQL.

### Deviations
Anything that differs from the Proposal, a contract or the technology document, with the reason.

### Follow-ups
What is left, and who owns it.

### Authorship
Who wrote what; which parts were agent-assisted. Effort or hours only if the member records them personally.
```

## Rules

- Record facts: what happened, on which commit, with which result. Failed attempts are worth keeping; they are often the most useful part of a report.
- Never record secrets, credentials, raw environment files or server addresses.
- Agents must not invent hours, meetings, reviews or approvals. An agent-written entry says so in its Authorship section.
- Repository language is English ([D-003](../collaboration/decisions.md#d-003-repository-language)).
- Local checks, CI, member checks, merge, deployment and team Done remain separate facts, as in the [testing guide](../testing.md).

## Using the Log for the Report

| Report topic | Where the material comes from |
| --- | --- |
| Agile practice | Cross-cutting entries (planning, re-planning, reviews), Issue history, [Agile documents](../agile/README.md) |
| Analysis and design | "Design notes" sections, [diagrams](../diagrams/), contracts |
| Design patterns | "Design notes" sections naming the pattern and class |
| DevSecOps | Verification sections, CI and release entries |
| Challenges and lessons learned | "Problems and fixes" tables |
| Individual contribution | "Authorship" sections and PR history |
