---
id: "TYPE-09-P3-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-09-P3"
status: "in_progress"
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
- When `use is Union` and no member is compatible (the `:295` path), call
  `closestMember(value, use.types)` (§3.1) and emit the member-specific error from re-running
  `checkCompatibility(value, closest, …)` (which produces the precise field message), prefixed
  with "closest match '<member>':".

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaUnionDiagnostics` (new helper)
```kotlin
object LuaUnionDiagnostics {
    fun closestMember(value: LuaGraphType, members: Set<LuaGraphType>): LuaGraphType   // §3.1
}
```

## 3. Algorithms

### 3.1 `closestMember(value, members)` — TYPE-09-P3-03
- **Input → Output**: a value type + the union members → the best-match member.
- **Steps**:
  1. If `value` is a `Table`, score each `Table` member by `overlap = |value.getMembers().keys ∩
     member.getMembers().keys|`; pick the highest. Ties → the member with fewest *extra* required
     fields.
  2. If `value` is not a table, prefer a member of the same head kind (primitive/Function);
     else the first member.
- **Message**: re-invoke `checkCompatibility(value, closest)` to get the concrete reason (e.g.
  "missing field 'y'") and wrap it: `"'<value>' is not assignable to '<union>'; closest match
  '<closest>': <reason>"`.

## 4. External Data & Parsing
None.

## 5. Data Flow
`{x=1}` vs `{x,y}|{z}` → no member compatible → `closestMember` scores `{x,y}` (overlap 1) over
`{z}` (overlap 0) → re-check vs `{x,y}` yields "missing field 'y'" → emitted with the closest-match
prefix.

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
