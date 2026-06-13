---
id: "TYPE-09-P2"
title: "Phase 2: Compatibility Logic"
type: "feature"
status: "done"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# TYPE-09 P2: Compatibility Logic

Distributive subtype checking for unions, plus the safety limits and memoization that make it
robust.

## Implementation Status
- **Done**: AND/OR distribution — `LuaTypeGraph.isCompatible` (`:341-342`):
  `value is Union → types.all { … }`, `use is Union → types.any { … }`; transitive checking via
  graph flow; the `visited` set handles cyclic types (parent §2.3.3).
- **Remaining**: max-breadth (100) and max-depth (10) limits (parent §2.3.1), and context-keyed
  memoization (parent §2.3.4).

## Scope
- **In Scope**: the distributive `isCompatible`/`checkCompatibility` behaviour and its
  safety/perf hardening.
- **Out of Scope**: canonicalization (P1); error message wording (P3).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `TYPE-09-P2-01` | **OR-distribution** | **M** | Full | `T <= A\|B` iff `T<=A` or `T<=B`. (Implemented `:342`.) |
| `TYPE-09-P2-02` | **AND-distribution** | **M** | Full | `A\|B <= T` iff `A<=T` and `B<=T`. (Implemented `:341`.) |
| `TYPE-09-P2-03` | **Transitive** | **M** | Full | Distribution works through variable assignments (graph flow). |
| `TYPE-09-P2-04` | **Breadth/depth limits** | **S** | Full | Unions >100 members fall back to head-matching; distribution depth >10 **assumes compatibility** (returns `true`) and logs a diagnostic — see TYPE-DR-04: returning incompatible would emit false-positive errors on valid deep types. |
| `TYPE-09-P2-05` | **Memoization** | **S** | Full | Cache `isCompatible` keyed on `(value, use)` (generics pre-instantiated to identity-distinct nodes; `subst` reserved); cleared per fixed-point iteration. |

## Test Cases

### TC-TYPE-09-P2-01: OR success (regression)
- **Input**: assign a `number` to a `string|number`-typed use.
- **Output**: compatible (no error).

### TC-TYPE-09-P2-02: AND failure (regression)
- **Input**: `string|number` flowing into a `string`-only use.
- **Output**: incompatible (`number` violates).

### TC-TYPE-09-P2-03: Depth limit
- **Input**: a pathological nesting exceeding depth 10.
- **Output**: the check **terminates without stack overflow** and **assumes compatibility**
  (returns `true` / emits no false-positive error) — per TYPE-DR-04 and parent design §2.3.1.
