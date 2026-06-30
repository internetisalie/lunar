---
id: "INSP-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "INSP-03"
folders:
  - "[[features/inspections/03-type-mismatch/requirements|requirements]]"
---

# INSP-03: Risks & Gaps

## Critical Risks

### Risk 1.1: Doc-vs-code drift (the original design described a non-existent inspection)
- **Impact**: an implementer following the *old* design would build a third
  `LuaTypeMismatchInspection`, duplicating `LuaTypeAssignabilityInspection` +
  `LuaReturnTypeMismatchInspection` and producing **double-reported** problems for every mismatch.
- **Likelihood**: medium (the stale design read as greenfield).
- **Mitigation**: design §1 now records the extend-vs-replace verdict with file:line evidence and
  explicitly withdraws the new-inspection plan; requirements statuses are `Full`; the plan has no
  production-code phase. **Resolved.**

### Risk 1.2: Engine message format assumed by tests could change
- **Impact**: new tests assert on substrings (`not assignable`, `number`, `string`). If the engine
  reworded diagnostics, the assertions would break.
- **Likelihood**: low.
- **Mitigation**: assert on stable, generic substrings (mirroring the existing
  `looksLikeTypeMismatch` helper), not the full message.

## Design Gaps
_None._ The single former gap — "does `isAssignableTo`/`checkCompatibility` distribute unions?" —
is resolved: TYPE-09 is DONE and the union branch is present at `LuaTypeGraph.kt:290-332`, proven by
`testUnionClosestMatchDiagnosticOnRealCode`.

## Technical Debt & Future Work
- **TBD: explicit `shortName` on the two inspections** — neither `plugin.xml` entry declares one
  (platform derives it from the class name). Cosmetic; deferred. No requirement depends on it.
- **TBD: quick fixes** (e.g. "change declared `@type`", "wrap value") — out of scope for INSP-03;
  candidate follow-up feature.
- **TBD: anonymous-function (`LuaFuncDef`) return checking** — engine only models `@return` on host
  decls; deferred.

## Pre-Implementation De-risking Tasks
| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Confirm the scalar `---@type string` / `local x = 42` mismatch surfaces through `LuaTypeAssignabilityInspection` (not only the snapshot) by adding `testScalarTypeMismatchReported`. | Risk 1.1 (confirms no new inspection needed) | todo |
| DR-02 | Confirm the union *pass* case `---@type string\|number` / `local x = 42` yields no warning via the inspection. | INSP-03-02 coverage | todo |

## Test Case Gaps
- TC1 (scalar mismatch, real-flow) and TC2 (union member match, real-flow) are the only INSP-03
  test cases not yet automated; both are added in implementation-plan Phase 1.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
