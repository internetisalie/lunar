---
id: ANALYSIS-06-PLAN
title: Control Flow Graph (CFG) Implementation Plan
type: plan
parent_id: ANALYSIS-06
status: planned
---

# Implementation Plan

## Phase 1: Core Framework [Must]
- **Tasks**:
  1. Define `LuaControlFlow` and `LuaInstruction` interfaces based on Platform equivalents.
  2. Implement `LuaReadWriteInstruction` and `LuaBranchInstruction`.
- **Verification**: Basic instantiation tests.

## Phase 2: CFG Builder [Must]
- **Tasks**:
  1. Implement `LuaControlFlowBuilder` visiting basic blocks, `if`, `while`, `for`, `repeat`.
  2. Implement handling of `return`, `break`, and `goto`.
  3. Ensure `ReadWriteInstruction` nodes are properly emitted for variable accesses.
- **Verification**: `ControlFlowBuilderTest` asserting the correct number of instructions and terminal paths for various AST structures.

## Phase 3: Caching & Reachability [Must]
- **Tasks**:
  1. Implement `ControlFlowCache.getControlFlow(ScopeOwner)`.
  2. Implement Reachability evaluation (forward traversal from entry).
- **Verification**: `ControlFlowReachabilityTest` validating `REACHABLE` vs `UNREACHABLE` on known edge cases.
