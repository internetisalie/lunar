---
id: "TYPE-09-P0"
title: "Phase 0: De-risking & Spikes"
type: "feature"
status: "done"
vf_icon: ✅
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# TYPE-09 P0: De-risking & Spikes

Validate the safety/performance approach for the union solver before the Phase 2 hardening,
chiefly the combinatorial-explosion risk (`TYPE-DR-01`). The distribution core already exists in
`LuaTypeGraph`; this story de-risks the *limits and memoization* design before they are built.

## Scope
- **In Scope**: spikes to confirm the breadth/depth limits prevent blow-up, that memoization is
  sound when keyed on the resolution context, and that flattening terminates on recursive unions.
- **Out of Scope**: shipping the limits/memoization (that is P2).

## De-risking Actions

Each action is **done** only when its measurable criterion is met and the deliverable exists.

| ID | Action | Success Criterion (pass/fail) | Deliverable |
| :--- | :--- | :--- | :--- |
| `TYPE-09-P0-01` | Bound distribution cost | A synthetic `A1\|…\|A100` vs `B1\|…\|B100` check completes < 50 ms with the breadth-100 / depth-10 limits from parent design §2.3.1; without limits it is shown to blow up. | `results/union-perf.md` (with/without-limits timings) |
| `TYPE-09-P0-02` | Memoization soundness | A `(value, use)` pair under two different generic substitutions is **not** reused (distinct results); equal contexts hit the cache. Demonstrated by a failing-then-passing test. | `LuaUnionMemoSpikeTest` |
| `TYPE-09-P0-03` | Recursive-union termination | `flatten`/`fromLuaType` on a self-referential union (`type T = T \| number`) terminates via the `visited` guard (no SOE). | `results/recursive-union.md` |

## Test Cases

### TC-TYPE-09-P0-01: Limit prevents blow-up
- **Input**: the 100×100 union compatibility check.
- **Action**: run with limits (parent §2.3.1).
- **Output**: completes under the 50 ms budget; documented timing.

### TC-TYPE-09-P0-02: Context-keyed memo
- **Input**: the same node pair under `T=string` then `T=number`.
- **Action**: query compatibility twice.
- **Output**: the second call does **not** reuse the first's result.
