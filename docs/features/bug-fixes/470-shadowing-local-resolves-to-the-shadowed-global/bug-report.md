---
id: "BUG-470"
title: "A `local` that shadows an earlier same-file global resolves to the global's declaration"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-470: planned as part of [[BUG-472]]

**Symptom.** In a file that assigns a global and then declares a `local` of the same name —
`config = 1` ⏎ `local con|fig = 2` ⏎ `print(config)` — a caret on the **`local`'s** name hands the
platform the **global's** declaration leaf at `(0,6)` instead of the `local`'s own. Rename then
refuses outright (`Cannot perform refactoring.`) and the document is unchanged; Go to Declaration
and Find Usages from that caret target the global, a different variable that the `local` shadows.
Measured at the data context by [[REFACT-07]]'s DR-05 probe `a2` and driven end to end as Gap
2.21's global control.

**This is one defect with [[BUG-472]], at the benign severity.** Same root cause — the declaring
statement is excluded from its own scope, so the scope walk resolves the declaration's own name
outward — and where the shadowed declaration is a `local` rather than a global the outcome is not a
refusal but a **wrong rewrite that silently changes what the program prints**. Planning them apart
would let the serious half be closed as covered when it is not.

**Reproduction, root cause, fix strategy, tests, blast radius and the de-risking tasks are in
[[BUG-472]]'s report** —
`docs/features/bug-fixes/472-renaming-a-shadowing-local-rewrites-the-shadowed-one/bug-report.md`.
Its §2 is this report's reproduction, §4.5 grounds why this half refuses rather than corrupts and
states that it needs nothing beyond BUG-472's fix, and §5's T3 is this half's regression case.

**Closing BUG-472 closes this.** Both roadmap rows retire in the same commit. This directory and
stub stay: `[[BUG-470]]` is linked from `docs/roadmap.md`, from BUG-472's family table and from
`docs/features/refactoring/07-inplace-rename/risks-and-gaps.md` (Gap 2.21 and DR-05 Observation 1),
and a dangling wikilink is worse than a stub.
