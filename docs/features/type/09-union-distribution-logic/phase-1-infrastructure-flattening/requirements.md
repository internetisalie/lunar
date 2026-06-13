---
id: "TYPE-09-P1"
title: "Phase 1: Infrastructure & Flattening"
type: "feature"
status: "todo"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# Phase 1: Infrastructure & Flattening

Stub (undefined — `todo`). Scope when planned: the `LuaTypeAlgebra` canonicalization layer
(flatten — already partly in `LuaTypeNodes.kt:79`; simplify `T|any→any`, `T|T→T`; sort +
dedup + collapse-to-single) routed through `LuaGraphType.Union` construction. See the parent
[design.md](../design.md) "Current implementation status".
