---
id: "TYPE-09-P2-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-09-P2"
status: "in_progress"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-2-compatibility-logic/requirements|requirements]]"
---

# Implementation Plan: TYPE-09 P2 — Compatibility Logic

Done: OR/AND distribution + cycle handling. Remaining: limits + memoization.

## Phase 1: Safety limits [Should] — TYPE-09-P2-04
- [ ] Thread `depth` through `isCompatible`; `MAX_DISTRIBUTION_DEPTH = 10` guard;
      `MAX_UNION_BREADTH = 100` → `shallowHeadMatch` fallback (§2.1/§3.1).
- [ ] Tests: TC-TYPE-09-P2-03 (depth limit), a >100-member fallback test; regression
      TC-TYPE-09-P2-01/02.

## Phase 2: Memoization [Should] — TYPE-09-P2-05
- [ ] `CompatKey(value, use, subst)` memo on `LuaTypeGraph`, cleared per `checkTypes()`.
- [ ] Test: same pair under two substitutions yields distinct results (P0-02 graduates to a
      real test).

## Verification Tasks
- Unit: distribution regressions stay green; depth/breadth guards; memo correctness + a call-count
  cache-hit assertion.
- Perf: the P0-01 100×100 benchmark stays under budget with the limits in place.
