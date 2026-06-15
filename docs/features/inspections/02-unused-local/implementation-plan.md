---
id: INSPECTIONS-02-PLAN
title: Unused Local Variable Implementation Plan
type: plan
parent_id: INSPECTIONS-02
status: planned
---

# Implementation Plan
## Phase 0: De-risking (CFG Integration) [Must]
- **Tasks**: Extend CFG infrastructure (from INSP-04) to support `ReadWriteInstruction`.
- **Verification**: `ControlFlowReadWriteTest`.

## Phase 1: Inspection Logic [Must]
- **Tasks**: Implement `LuaUnusedLocalInspection`.
- **Verification**: `UnusedLocalInspectionTest`.
