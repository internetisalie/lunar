---
id: INSPECTIONS-08-DESIGN
title: Deprecated API Design
type: design
parent_id: INSPECTIONS-08
status: done
---

# Technical Design: Deprecated API

## 1. Core Components
- `LuaDeprecatedApiInspection` extending `LocalInspectionTool`.

## 2. Algorithms
1. Visit `LuaNameReference`.
2. Resolve via `LuaResolveUtil`.
3. Check resolved PSI for LuaCATS `@deprecated`.
4. Register warning with `ProblemHighlightType.LIKE_DEPRECATED`.
