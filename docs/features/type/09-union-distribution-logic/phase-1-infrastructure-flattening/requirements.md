---
id: "TYPE-09-P1"
title: "Phase 1: Infrastructure & Flattening"
type: "feature"
status: "done"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# TYPE-09 P1: Infrastructure & Flattening

Provide a canonical union representation: flatten nested unions and simplify/dedupe members.

## Implementation Status
- **Done**: `LuaGraphType.Union(types: Set<LuaGraphType>)` (the union node) and the recursive
  `flatten` helper (`LuaTypeNodes.kt:79-93`); `getMembers()` merges union members
  (`LuaGraphType.kt:87`).
- **Remaining**: a `LuaTypeAlgebra` that **canonicalizes** — simplify (`T|any→any`, `T|T→T`),
  sort + dedupe, collapse a single-member union to that member — applied at union construction.

## Scope
- **In Scope**: canonicalization/simplification of unions; routing `Union` construction through
  the algebra.
- **Out of Scope**: compatibility checking (P2); error messages (P3).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `TYPE-09-P1-01` | **Union node** | **M** | Full | A `Union` type with a member set. (Implemented: `LuaGraphType.Union`.) |
| `TYPE-09-P1-02` | **Nested flattening** | **M** | Full | `Union(Union(A\|B)\|C)` flattens to `Union(A\|B\|C)`. (Implemented: `flatten`.) |
| `TYPE-09-P1-03` | **Simplification** | **S** | Full | `T\|any → any`, `T\|T → T`. |
| `TYPE-09-P1-04` | **Canonical form** | **S** | Full | Dedupe + sort members; collapse a 1-member union to the member. |

## Test Cases

### TC-TYPE-09-P1-01: Flatten (regression)
- **Input**: `Union(Union(number|string), boolean)`.
- **Output**: a single `Union{number, string, boolean}`.

### TC-TYPE-09-P1-02: Simplify any
- **Input**: `LuaTypeAlgebra.canonicalize(Union{string, Any})`.
- **Output**: `Any`.

### TC-TYPE-09-P1-03: Collapse single
- **Input**: `canonicalize(Union{number, number})`.
- **Output**: `number` (deduped to one member → collapsed).
