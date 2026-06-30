---
id: "TYPE-09-P0-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-09-P0"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-0-de-risking/requirements|requirements]]"
---

# Implementation Plan: TYPE-09 P0 — De-risking & Spikes

## Phase 1: Run the spikes [Must]
- [ ] `TYPE-09-P0-01`: build the 100×100 union benchmark; time with/without limits; write
      `results/union-perf.md`.
- [ ] `TYPE-09-P0-02`: `LuaUnionMemoSpikeTest` demonstrating naive-key staleness vs
      context-key correctness.
- [ ] `TYPE-09-P0-03`: recursive-union termination check; write `results/recursive-union.md`.

## Verification Tasks
- All three deliverables exist and meet their thresholds (design §1–§3).
- Findings feed the P2 limits/memoization design and confirm P1's flatten guard.
