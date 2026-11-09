---
name: Spike
about: Timeboxed investigation to answer a question before committing to work
title: "Spike: <question>"
labels: spike
---

## Question(s)
What do we need to learn? 1–3 concrete questions.

## Why it matters
What decision or future work does this unblock?

## Timebox
e.g. ~6 hours of effort, or end of iteration N. When it runs out, stop and report.

## Scope
In:
- e.g., Trace where combat logic lives and what calls into it (abilities, spells, items, tick loop)

Out:
- e.g., Modify/prototype NPC behavior internals to prove understanding of the code

## Acceptance Criteria
- [ ] Each question answered, or marked unanswered with the reason
- [ ] Findings committed to `docs/spikes/`
- [ ] Recommendation made (ADR opened if it's a significant decision)
- [ ] Follow-up stories created and linked as sub-issues
- [ ] Any prototype code left on a `spike/` branch, not merged

## Findings
Link to the findings doc when complete.