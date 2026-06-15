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

## 2. Algorithms
1. Rely on `ControlFlowCache`. Find all `ReadWriteInstruction` nodes.
2. If a local variable has write instructions but zero read instructions in the CFG, flag it as unused.
