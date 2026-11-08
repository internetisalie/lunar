---
id: "MAINT-58"
title: "58: Bug-Fix Mining and Real-Bug-Rate Rule Weighting"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-58: Bug-Fix Mining and Real-Bug-Rate Rule Weighting

[[MAINT-56]] measures how often each type rule's situation occurs in representative Lua code and how
often Lunar stays silent there. That is exposure, not harm: a rule exercised a thousand times per kloc
may guard against a mistake nobody makes. This feature measures harm. It mines the fix commits of
MAINT-56's members, classifies which fixes repair a type-class mistake, and runs Lunar over each fix's
parent and child to learn whether Lunar would have reported the bug. The result is a real-bug rate
per rule that multiplies MAINT-56's `tooling/corpus/rule-weights.json`, and MAINT-54's `F`
re-weighted by it.

It was split from MAINT-56 because it shares nothing with it but the manifest: its harness walks
history rather than a pinned tree, it needs a classification codebook and a second rater, and its
Lunar run is per commit pair.

## Evidence

The feasibility probe is MAINT-56 risks-and-gaps DR-05 (commit-selection statistics, a classified
sample of small fixes, and its reading of how many classified fixes a per-rule rate needs).

## Scope

- **Inputs:** MAINT-56's `tooling/corpus/type-corpus.json` repositories with full history; DR-05's
  commit filter (`^fix(\([^)]*\))?!?:`, ≤ 20 Lua lines in ≤ 2 Lua files) and a stated fallback for
  repositories without conventional commits.
- **Codebook:** DR-05's classes written out as a codebook, with a double-coded 20 % subset and the
  agreement reported.
- **Lunar run:** each type-class fix's parent and child, limited to the touched files; a fix counts as
  caught when a type finding on the parent's changed lines is gone in the child.
- **Output:** a per-rule real-bug rate and its catch rate, joined to `rule-weights.json` by rule.

## Dependencies

- [[MAINT-56]] — the manifest, the domains and the rule-weights file this feature multiplies.

## Open Questions

- The selection fallback for repositories without conventional commits (DR-05).
- Whether a rule Lunar does not have (record-key, undefined-field and nil bugs in DR-05's sample) is
  reported as a gap row or omitted.
