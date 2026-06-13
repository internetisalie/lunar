---
id: "TYPE-09-P3-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-09-P3"
status: "done"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-3-error-reporting-diagnostics/requirements|requirements]]"
---

# Technical Design: TYPE-09 P3 — Error Reporting & Diagnostics

## 1. Architecture Overview

### Current State
`LuaTypeGraph.checkCompatibility` already emits `ElementError`s for union failures: the
generic union message (`:295`) and per-member messages (`:264`). Errors surface through the
existing `LuaTypesAnnotator`. There is no closest-match heuristic — a `{x=1}` failing against
`{x,y} | {z}` does not say *which* member it was closest to.

### Target State
On OR-distribution failure, pick the union member with the greatest structural overlap with the
value and emit *its* specific failure (missing field / mismatched field), per parent §3.

## 2. Core Components

### 2.1 `LuaTypeGraph.checkCompatibility` (modify, union OR branch)
- When `use is Union` and no member is compatible (the OR-failure path), call
  `closestMatch(value, use.types)` (§3.1). It returns a `ClosestMatch(member, reason)` — the
  member plus a precomputed reason string — or `null`. On a non-null result, emit
  `"<value> is not assignable to <union>; closest match '<member>': <reason>"`; on `null`, keep the
  existing generic `"<value> is not assignable to union <union>"`.
- The reason is produced **directly** by `LuaUnionDiagnostics` (missing required fields), NOT by
  re-invoking `checkCompatibility(value, closest)`. Re-invoking would be side-effecting:
  `checkCompatibility` *emits* `ElementError`s to a list rather than *returning* a reason, so
  re-running it against the closest member would add stray errors. The helper instead computes the
  reason as a pure function of the two table shapes, keeping diagnosis side-effect-free.

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaUnionDiagnostics` (new helper)
```kotlin
object LuaUnionDiagnostics {
    data class ClosestMatch(val member: LuaGraphType.Table, val reason: String)
    fun closestMatch(value: LuaGraphType, members: Collection<LuaGraphType>): ClosestMatch?   // §3.1
}
```

## 3. Algorithms

### 3.1 `closestMatch(value, members)` — TYPE-09-P3-03
- **Input → Output**: a value type + the union members → a `ClosestMatch(member, reason)`, or
  `null` when no member is meaningfully close.
- **Steps**:
  1. Return `null` unless `value` is a `Table` *and* the union has ≤100 members (the large-union
     guard — wider unions skip overlap scoring and keep the generic message).
  2. Score each `Table` member by `overlap = |value.getMembers().keys ∩ member.getMembers().keys|`.
     Keep only members with `overlap > 0`; if none, return `null` (a non-table value or an
     all-overlap-0 union falls back to the generic message — this is what keeps the boolean case on
     the generic message).
  3. Pick the highest-overlap member; tie-break by **fewest extra required fields** (member required
     keys absent from `value`).
- **Reason (computed in the helper, no `checkCompatibility` re-invocation)**: the missing required
  fields of the chosen member — a required field is one whose key is absent in `value` and whose
  read type is not optional. `"missing field 'y'"` for one, `"missing fields 'y', 'z'"` for several,
  `"incompatible fields"` as a generic fallback if (unexpectedly) none is missing. The caller wraps
  it: `"<value> is not assignable to <union>; closest match '<member>': <reason>"`.

## 4. External Data & Parsing
None.

## 5. Data Flow
`{x=1}` vs `{x,y}|{z}` → no member compatible → `closestMatch` scores `{x,y}` (overlap 1) over
`{z}` (overlap 0) → computes its missing required field directly → returns
`ClosestMatch({x,y}, "missing field 'y'")` → emitted with the closest-match prefix.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| All members equally bad (overlap 0) | fall back to the generic union message (current behaviour). |
| Non-table value | head-kind preference; otherwise first member. |
| Large union (>100) | reuse the P2 head-match path; skip the expensive overlap scoring. |

## 7. Integration Points
- No new EP — edits `LuaTypeGraph` error emission + adds `LuaUnionDiagnostics`. Errors render via
  the existing `LuaTypesAnnotator`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|-------------|----------|----------------|
| TYPE-09-P3-01 OR message | M | existing `:295` |
| TYPE-09-P3-02 AND message | M | existing `:264` |
| TYPE-09-P3-03 Closest-match | S | §2.1, §3.1 |

## 9. Alternatives Considered
- **Field-overlap scoring vs first-member**: overlap scoring makes the message point at the
  member the user most likely intended, which is the whole value of the heuristic.

## 10. Open Questions

_None — feature has cleared the planning bar._
