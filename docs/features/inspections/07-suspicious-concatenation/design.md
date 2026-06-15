---
id: INSPECTIONS-07-DESIGN
title: Suspicious Concatenation Design
type: design
parent_id: INSPECTIONS-07
status: planned
---

# Technical Design: Suspicious Concatenation

## 1. Core Components
- `LuaSuspiciousConcatenationInspection` extending `LocalInspectionTool`.

## 2. Algorithms
1. Visit `LuaBinaryExpr` with operator `..`.
2. Use `LuaTypeManager` to resolve types.
3. If type is table or function, register warning.
