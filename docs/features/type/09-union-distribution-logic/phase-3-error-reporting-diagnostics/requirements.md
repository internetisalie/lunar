---
id: "TYPE-09-P3"
title: "Phase 3: Error Reporting & Diagnostics"
type: "feature"
status: "todo"
priority: "high"
parent_id: "TYPE-09"
folders: ["[[features/type/09-union-distribution-logic/requirements|requirements]]"]
---

# Phase 3: Error Reporting & Diagnostics

Stub (undefined — `todo`). Scope when planned: upgrade the existing union error messages
(`LuaTypeGraph.checkCompatibility:264,295`) into member-specific diagnostics with the
"closest-match" heuristic from the parent [design.md](../design.md) §3 (identify the union
member with the most overlapping fields and surface its specific failure).
