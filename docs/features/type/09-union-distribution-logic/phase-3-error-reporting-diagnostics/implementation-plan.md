---
id: "TYPE-09-P3-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-09-P3"
status: "done"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-3-error-reporting-diagnostics/requirements|requirements]]"
---

# Implementation Plan: TYPE-09 P3 — Error Reporting & Diagnostics

Done: basic union error messages. Remaining: closest-match diagnostic.

## Phase 1: Closest-match [Should] — TYPE-09-P3-03
- [ ] `LuaUnionDiagnostics.closestMember` (§3.1, field-overlap scoring).
- [ ] In `checkCompatibility`'s union OR-failure branch, emit the closest member's specific
      reason with a "closest match" prefix (§2.1).
- [ ] Tests: TC-TYPE-09-P3-01 (regression message), TC-TYPE-09-P3-02 (closest-match points at
      `{x,y}` and reports missing `y`).

## Verification Tasks
- Unit: scoring picks the highest-overlap member; tie-break by fewest extra required fields;
  overlap-0 falls back to the generic message.
