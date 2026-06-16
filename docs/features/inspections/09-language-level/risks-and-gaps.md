---
id: "INSP-09-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "done"
parent_id: "INSP-09"
folders:
  - "[[features/inspections/09-language-level/requirements|requirements]]"
---

# INSP-09: Risks & Gaps

## Critical Risks

### Risk 1.1: Annotator removal drops coverage
- **Impact**: Deleting `LuaLanguageLevelAnnotator` could silently lose a construct that the
  new inspection forgot to handle.
- **Likelihood**: low.
- **Mitigation**: The design §1 mapping table enumerates all six annotator branches and assigns
  each to an inspection visitor override; Phase 4 migrates the annotator's 51-test suite onto
  the inspection so any dropped construct fails a test. Full `./gradlew test` must stay green.

### Risk 1.2: Quick fixes are intentions, not `LocalQuickFix`
- **Impact**: `registerProblem` expects `LocalQuickFix`; the existing fixes extend
  `BaseIntentionAction`.
- **Likelihood**: low.
- **Mitigation**: Wrap with `com.intellij.codeInspection.IntentionWrapper` (design §2.2). The
  fix classes are unchanged; only the call site moves.

### Risk 1.3: Double-reporting during the transition
- **Impact**: If the `<annotator>` registration is not removed in the same change, every
  construct is reported twice.
- **Likelihood**: medium if split across commits.
- **Mitigation**: Phase 4 removes the registration, deletes the class, and migrates the test in
  one atomic change; checklist Scenario 3.2 verifies exactly one report.

## Design Gaps
_None open._ All prior design questions are resolved in design.md §1/§3.2/§2.2.

## Technical Debt & Future Work
- **TBD: `\z` line-continuation escapes (5.2+) and hexadecimal float literals.** Not detectable
  at the PSI level — string-escape sequences live inside a single `STRING` token, so there is no
  PSI node to flag without lexer changes. Out of scope (requirements §Out of Scope); revisit if
  the lexer is extended to tokenize escape sequences.

## Pre-Implementation De-risking Tasks
| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| INSP-00-DR-01 | Confirm `IntentionWrapper` makes `BaseIntentionAction` usable as an inspection `LocalQuickFix` in a one-line spike (or fall back to thin `LocalQuickFix` wrappers calling the same logic). | Risk 1.2 | todo |

## Test Case Gaps
- The migrated suite already covers all six constructs and false positives; the only net-new
  cases are the three quick-fix outcomes (requirements Test Cases 9–11) added in Phase 4.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
