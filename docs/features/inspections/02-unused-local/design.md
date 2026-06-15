---
id: INSPECTIONS-02-DESIGN
title: Unused Local Variable Design
type: design
parent_id: INSPECTIONS-02
status: planned
---

# Technical Design: Unused Local Variable

## 1. Core Components
- `LuaUnusedLocalInspection` extending `LocalInspectionTool`.
- `checkParameters` boolean flag, disabled by default, configurable via UI.

## 2. Algorithms
1. Find all local variable declarations (e.g. `LuaAttName` and `LuaNameList` inside `LuaParList` / `LuaGenericForStatement`).
2. Verify if it's named `_` (exactly `_` is the ignored idiom, suppress warnings for it).
3. Check `ReferencesSearch.search(element, LocalSearchScope(containingFile)).findFirst() != null`
4. If there are no references, flag as unused.

> [!NOTE]
> We use standard `ReferencesSearch` for basic unused detection as it is idiomatic and fast. In the future, once the Control Flow Graph (CFG) is mature, we can enhance this inspection to use the CFG for **dead-store detection** (e.g. `local x = 1; x = 2` where the first write is never read).
