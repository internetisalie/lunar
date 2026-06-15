---
id: INSP-04-PLAN
title: Unreachable Code Implementation Plan
type: plan
parent_id: INSP-04
status: planned
---

# Implementation Plan

## Phase 0: De-risking (CFG Infrastructure) [Must]
- **Tasks**:
  1. Implement `LuaControlFlowBuilder` and `Instruction` framework (modeled after `intellij-community/python`).
  2. Implement `ControlFlowCache`.
- **Verification**: `ControlFlowBuilderTest`.

## Phase 1: Inspection Logic [Must]
- **Tasks**:
  1. Implement `LuaUnreachableCodeInspection` and quick fixes.
- **Verification**: `UnreachableCodeInspectionTest`.
