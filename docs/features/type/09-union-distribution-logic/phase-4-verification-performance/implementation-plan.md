---
id: "TYPE-09-P4-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-09-P4"
status: "done"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-4-verification-performance/requirements|requirements]]"
---

# Implementation Plan: TYPE-09 P4 — Verification & Performance

## Phase 1: Discriminant pruning [Should] — TYPE-09-P4-01
- [ ] `LuaDiscriminantPruner.candidates` (§3.1); hook into `LuaTypeGraph.isCompatible`'s
      `use is Union` table-OR branch before the structural `any`.
- [ ] Test: TC-TYPE-09-P4-01 (only the matching tag branch checked).

## Phase 2: Benchmark + suite [Should/Must] — TYPE-09-P4-02/03
- [ ] `LuaUnionDistributionBenchmarkTest` (relative-to-baseline budget, §3.2).
- [ ] `LuaUnionDistributionTest` aggregating P1–P3 TCs + parent requirements §4.
- [ ] Tests: TC-TYPE-09-P4-02.

## Verification Tasks
- The full TYPE-09 suite runs green; the benchmark gate holds; pruning never changes a
  compatibility result (only cost).
