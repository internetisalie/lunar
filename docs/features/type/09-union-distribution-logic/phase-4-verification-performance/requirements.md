---
id: "TYPE-09-P4"
title: "Phase 4: Verification & Performance"
type: "feature"
status: "done"
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
| `TYPE-09-P4-01` | **Discriminant pruning** | **S** | Future Work | For tagged unions (`{type:"A",…} \| {type:"B",…}`), prune by the literal discriminant before structural checks. **Deferred (TYPE-DR-05):** the type graph models no scalar string/number *literal* type — `{type="A"}` and `{type="B"}` both reduce to a `String` field — so literal-tag discrimination is not yet expressible. Blocked on a literal-type feature; P0 confirmed pruning is not needed for performance (~400× headroom). |
| `TYPE-09-P4-02` | **Benchmark gate** | **S** | Full | A benchmark asserts ≤5-member unions are near-constant and ≤20 members stay within budget. |
| `TYPE-09-P4-03` | **Comprehensive suite** | **M** | Full | The full TYPE-09 TC matrix (P1–P3 cases + the parent requirements §4 cases) runs green. |

## Test Cases

### TC-TYPE-09-P4-01: Discriminant prune — DEFERRED (TYPE-DR-05)
- **Input**: `{type="A", a=1}` vs `{type:"A",a:number} | {type:"B",b:number}`.
- **Output**: only the `type:"A"` branch is structurally checked (the `"B"` branch is pruned by
  the discriminant), and the value is compatible.
- **Status**: not yet testable — requires scalar literal types (see TYPE-DR-05). The value is
  still correctly type-checked today via the P2 structural path; only the *literal-tag pruning
  optimization* is deferred.

### TC-TYPE-09-P4-02: Benchmark budget
- **Input**: a 5-member union compatibility check, 1000 iterations.
- **Output**: median per-check time within the documented budget (e.g. < 100 µs).
