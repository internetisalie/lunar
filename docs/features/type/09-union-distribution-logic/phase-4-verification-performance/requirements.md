---
id: "TYPE-09-P4"
title: "Phase 4: Verification & Performance"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# TYPE-09 P4: Verification & Performance

Discriminant-based pruning for tagged unions, a benchmark harness, and the comprehensive test
suite that locks the whole TYPE-09 behaviour.

## Scope
- **In Scope**: discriminant pruning (parent §2.6.1); a benchmark gate (≤5 members ~O(1),
  graceful degradation >20); a full union-distribution test suite (parent requirements §4).
- **Out of Scope**: the distribution/limits/errors themselves (P1–P3).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `TYPE-09-P4-01` | **Discriminant pruning** | **S** | Not Implemented | For tagged unions (`{type:"A",…} \| {type:"B",…}`), prune by the literal discriminant before structural checks. |
| `TYPE-09-P4-02` | **Benchmark gate** | **S** | Not Implemented | A benchmark asserts ≤5-member unions are near-constant and ≤20 members stay within budget. |
| `TYPE-09-P4-03` | **Comprehensive suite** | **M** | Not Implemented | The full TYPE-09 TC matrix (P1–P3 cases + the parent requirements §4 cases) runs green. |

## Test Cases

### TC-TYPE-09-P4-01: Discriminant prune
- **Input**: `{type="A", a=1}` vs `{type:"A",a:number} | {type:"B",b:number}`.
- **Output**: only the `type:"A"` branch is structurally checked (the `"B"` branch is pruned by
  the discriminant), and the value is compatible.

### TC-TYPE-09-P4-02: Benchmark budget
- **Input**: a 5-member union compatibility check, 1000 iterations.
- **Output**: median per-check time within the documented budget (e.g. < 100 µs).
