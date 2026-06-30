---
id: "TYPE-09-P0-DESIGN"
title: "Spike Methodology & Acceptance"
type: "design"
parent_id: "TYPE-09-P0"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-0-de-risking/requirements|requirements]]"
---

# TYPE-09 P0: Spike Methodology & Acceptance

The bar for a de-risking story is that each spike has a defined question, method, measurable
threshold, and a named deliverable (mirrors TOOL-00). All spikes run against the existing
`net.internetisalie.lunar.lang.psi.types.LuaTypeGraph` / `LuaGraphType`.

## 1. `TYPE-09-P0-01` — Distribution cost bound
- **Question**: do the breadth-100 / depth-10 limits (parent `design.md` §2.3.1) keep
  distribution tractable?
- **Method**: build `LuaGraphType.Union` nodes `A1|…|A100` and `B1|…|B100` (disjoint tables);
  call `isCompatible` with and without the limit guards; time both (JMH or a simple `nanoTime`
  loop, 100 iterations, report median).
- **Pass threshold**: with limits, median < 50 ms; without limits, demonstrably worse (≥10×) or
  non-terminating within 5 s.
- **Deliverable**: `results/union-perf.md`.

## 2. `TYPE-09-P0-02` — Memoization soundness
- **Question**: is caching `isCompatible` results sound across differing resolution contexts?
- **Method**: prototype a memo keyed only on `(value, use)` and show it returns a stale result
  when a generic param differs; then key on `(value, use, substitutions)` and show correctness.
- **Pass threshold**: the context-keyed memo yields distinct results for `T=string` vs
  `T=number`; equal contexts hit the cache (assert a call counter).
- **Deliverable**: `LuaUnionMemoSpikeTest` (red on the naive key, green on the context key).

## 3. `TYPE-09-P0-03` — Recursive-union termination
- **Question**: does flattening/conversion terminate on self-referential unions?
- **Method**: construct `type T = T | number` via `LuaGraphType.fromLuaType` and call
  `flatten`/`getMembers`; rely on the existing `visited` guard.
- **Pass threshold**: no `StackOverflowError`; the union resolves to `{ T-ref, number }`.
- **Deliverable**: `results/recursive-union.md`.

## 4. Integration Points
No `plugin.xml` change — throwaway spikes. Findings gate P2 (limits/memoization) and P1
(flatten/canonicalize). Unresolved items become P2/P4 design inputs.

## 5. Requirement Coverage

| Requirement | Priority | Acceptance |
|-------------|----------|-----------|
| TYPE-09-P0-01 | High | §1 |
| TYPE-09-P0-02 | High | §2 |
| TYPE-09-P0-03 | High | §3 |

## 6. Open Questions

_None — each spike has a defined method, threshold, and deliverable._
