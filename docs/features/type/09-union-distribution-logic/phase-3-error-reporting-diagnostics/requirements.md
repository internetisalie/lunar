---
id: "TYPE-09-P3"
title: "Phase 3: Error Reporting & Diagnostics"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# TYPE-09 P3: Error Reporting & Diagnostics

Turn union compatibility failures into actionable, member-specific messages.

## Implementation Status
- **Done**: basic union error messages — `LuaTypeGraph.checkCompatibility` emits
  `"<T> is not assignable to union <A|B>"` (`:295`) and per-member errors (`:264`).
- **Remaining**: the parent §3 "closest-match" heuristic — when OR-distribution fails, identify
  the union member the value most nearly matches and surface *its* specific failure.

## Scope
- **In Scope**: message quality for union mismatches; closest-match member selection.
- **Out of Scope**: the distribution decision itself (P2); quick fixes (future).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `TYPE-09-P3-01` | **Union OR-failure message** | **M** | Full | "Value of type 'T' is not assignable to any of 'A\|B'." |
| `TYPE-09-P3-02` | **Union AND-failure message** | **M** | Full | "Union member 'A' is not assignable to type 'T'." |
| `TYPE-09-P3-03` | **Closest-match diagnostic** | **S** | Not Implemented | On OR failure, name the nearest member and its specific missing field/mismatch. |

## Test Cases

### TC-TYPE-09-P3-01: OR-failure message (regression)
- **Input**: a `boolean` assigned to a `string|number` use.
- **Output**: error mentions the value type and the full union.

### TC-TYPE-09-P3-02: Closest-match
- **Input**: a table `{x=1}` assigned to `{x:number, y:number} | {z:number}`.
- **Output**: the diagnostic points at the `{x,y}` member (most overlap) and reports the missing
  `y`, not the unrelated `{z}` member.
