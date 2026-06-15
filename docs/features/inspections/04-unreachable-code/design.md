---
id: INSP-04-DESIGN
title: Unreachable Code Design
type: design
parent_id: INSP-04
status: planned
---

# Technical Design: Unreachable Code

## 1. Architecture Overview
- **Inspection Class**: `net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection` extending `LocalInspectionTool`.

## 2. Core Components
### 2.1 LuaUnreachableCodeInspection
- **Responsibility**: Checks if a statement is unreachable using the Control Flow Graph.
- **Key API**: `ControlFlowCache.getControlFlow(scopeOwner)`

## 3. Algorithms
1. Get the enclosing `ScopeOwner` (e.g., `LuaFunctionDecl` or `LuaFile`).
2. Retrieve its CFG via `ControlFlowCache`.
3. Check the `Reachability` of the current `LuaStatement`. If `UNREACHABLE`, register a `WEAK_WARNING`.

## 4. Integration Points
```xml
<localInspection language="Lua" shortName="LuaUnreachableCode" displayName="Unreachable code" groupName="Lua" enabledByDefault="true" level="WARNING" implementationClass="net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection"/>
```
