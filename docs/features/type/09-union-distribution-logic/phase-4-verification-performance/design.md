---
id: "TYPE-09-P4-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-09-P4"
status: "planned"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-4-verification-performance/requirements|requirements]]"
---

# Technical Design: TYPE-09 P4 — Verification & Performance

## 1. Architecture Overview

### Current State
P1–P3 provide canonical unions, bounded/memoized distribution, and member-specific errors.
There is no discriminant-based pruning (so tagged unions are checked member-by-member
structurally), no benchmark gate, and no consolidated TYPE-09 test suite.

### Target State
Add discriminant pruning to the union OR path, a benchmark test that fails on regressions, and a
suite covering P1–P3 + the parent requirements §4 matrix.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaDiscriminantPruner` (new)
```kotlin
object LuaDiscriminantPruner {
    /** Given a value table and a union of table members, return the members whose discriminant
        field literal matches the value's (or all members if no common discriminant). */
    fun candidates(value: LuaGraphType.Table, members: List<LuaGraphType.Table>): List<LuaGraphType.Table>
}
```
- Consulted by `LuaTypeGraph.isCompatible` on the `use is Union` OR branch **before** the
  structural `any { … }`, when all members are `Table`s.

### 2.2 Benchmark + suite
- `LuaUnionDistributionBenchmarkTest` (a timed unit test, not JMH) asserts the P4-02 budgets.
- `LuaUnionDistributionTest` aggregates the P1–P3 TCs + the parent requirements §4 cases.

## 3. Algorithms

### 3.1 Discriminant pruning (`candidates`) — TYPE-09-P4-01
- **Steps**:
  1. Find **discriminant keys**: keys present in *every* member whose member-type is a
     **string/number literal** type (a "tag"), and present in `value` with a literal value.
  2. If a discriminant key `k` exists, keep only members whose `k` literal equals `value`'s `k`
     literal; if none match → return empty (incompatible, fast).
  3. If no discriminant key exists → return all members (no pruning; fall through to structural).
- **Result**: the OR branch then runs structural `isCompatible` only on the surviving candidates
  — O(1) for well-tagged unions instead of O(members).

### 3.2 Benchmark methodology (TYPE-09-P4-02)
- Warm up, then time N=1000 `isCompatible` calls on 5- and 20-member unions; assert the median
  is within a documented budget; on CI variance, compare relative to a 1-member baseline (ratio
  bound) rather than an absolute wall-clock.

## 4. External Data & Parsing
None.

## 5. Data Flow
`{type="A",a=1}` vs `{type:"A",…}|{type:"B",…}` → `candidates` keeps only the `"A"` member →
structural check runs once → compatible.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| No common discriminant | no pruning; structural over all members (correct, just not faster). |
| Discriminant present but value's tag matches none | empty candidates → incompatible (fast, with a clear P3 message). |
| Non-table members | pruning skipped (applies only to table unions). |

## 7. Integration Points
- No new EP — adds `LuaDiscriminantPruner`, a hook in `LuaTypeGraph.isCompatible`, and the two
  test classes.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|-------------|----------|----------------|
| TYPE-09-P4-01 Discriminant pruning | S | §2.1, §3.1 |
| TYPE-09-P4-02 Benchmark gate | S | §2.2, §3.2 |
| TYPE-09-P4-03 Comprehensive suite | M | §2.2 |

## 9. Alternatives Considered
- **Pruning before structural vs always structural**: pruning is a sound pre-filter (it only
  removes members that *cannot* match the discriminant), so it never changes the result, only the
  cost.

## 10. Open Questions

_None — feature has cleared the planning bar._
