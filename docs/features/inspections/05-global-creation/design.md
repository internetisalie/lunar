---
id: INSPECTIONS-05-DESIGN
title: Global Creation Design
type: design
parent_id: INSPECTIONS-05
vf_icon: ✅
folders:
  - "[[features/inspections/05-global-creation/requirements|requirements]]"
---
# Technical Design: Global Creation

## 1. Core Components
- `LuaGlobalCreationInspection`.

## 2. Algorithms
1. Visit `LuaAssignStat`.
2. Try resolving the LHS target.
3. If it resolves to null, it is implicitly global.
