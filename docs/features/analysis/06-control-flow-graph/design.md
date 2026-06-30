---
id: ANALYSIS-06-DESIGN
title: Control Flow Graph (CFG) Design
type: design
parent_id: ANALYSIS-06
---

# Technical Design: Control Flow Graph (CFG)

## 1. Architecture Overview
- **Core Abstractions**: Directly maps to IntelliJ's `com.intellij.codeInsight.controlflow` package (e.g., `Instruction`, `ControlFlowBuilder`).
- **Target Component**: `net.internetisalie.lunar.analysis.controlflow`.

## 2. Core Components

### 2.1 LuaControlFlowBuilder
- **Responsibility**: Walks the AST (`LuaRecursiveElementVisitor`) and connects `Instruction` instances.
- **Handling Branches**: When visiting `LuaIfStat`, creates branch instructions and merges paths at the end.
- **Handling Terminals**: When visiting `LuaReturnStat` or `LuaBreakStat`, connects the flow to an exit node instead of the subsequent statement.

### 2.2 LuaReadWriteInstruction
- **Responsibility**: Implements `Instruction` representing variable read or write.
- **Data**: Holds the `ACCESS` type (READ, WRITE) and the variable name/element.

### 2.3 ControlFlowCache
- **Responsibility**: Manages caching. Uses `CachedValuesManager` attached to the `LuaFunctionDecl` or `LuaFile` to cache the `ControlFlow` instance. It invalidates on PSI changes within the scope.

## 3. Integration Points
- This feature operates purely as a foundational utility. 
- It will be explicitly consumed by `LuaUnreachableCodeInspection` (INSP-04), which remains fully CFG-based.
- In the future, it will also be consumed by `LuaUnusedLocalInspection` (INSP-02) for advanced dead-store detection (while INSP-02 currently relies on `ReferencesSearch` for basic unused detection).
