---
id: MAINT-13
title: "MAINT-13: Test Coverage - Run & Debugger"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-13: Test Coverage - Run & Debugger

## Overview
Increase test coverage for socket debugger payloads, breakpoint syncing, evaluator expression ranges, REPL console processes, and console multi-line code submission parsing.

## Scope
* **In Scope**:
  * Unit tests for socket command framing and response parsing in `LuaDebugConnection` and `LuaDebuggerController`.
  * Unit tests for breakpoint registration and lifecycle triggers in `LuaLineBreakpointHandler`.
  * Unit tests for expression range extraction in `LuaDebuggerEvaluator`.
  * Unit tests for REPL console commands execution and text submission checks in `LuaConsoleExecuteHandler` and `LuaChunkCompletion`.
* **Out of Scope**:
  * Testing graphical console tool window components.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-13-01 | **Debug Socket Communication** | Must | planned | Verify that the debugger controller correctly serializes payloads (step, run) and parses target client returns. |
| MAINT-13-02 | **Breakpoint Registration** | Must | planned | Verify that adding or removing editor breakpoints updates active target breakpoint states. |
| MAINT-13-03 | **Expression Range Evaluation** | Must | planned | Verify that the debugger evaluator correctly identifies expression targets under cursor offsets. |
| MAINT-13-04 | **Console Multi-line Parsing** | Must | planned | Verify that `LuaChunkCompletion` identifies incomplete statements (e.g., unmatched `do`, `function`, or table `{`) to support multi-line REPL input. |

## Acceptance Criteria
* **AC-13-01**: A test case asserts that a mock debugger socket receives valid DBGp XML command packets and responds without socket hangs.
* **AC-13-02**: A test case asserts that adding a breakpoint at line 5 fires a registration call on the debug process handler.
* **AC-13-03**: A test case asserts that evaluating cursor range over `localObj.field` yields the full index path string instead of just `field`.
* **AC-13-04**: A test case asserts that `LuaChunkCompletion.isIncomplete("function test()")` returns `true` (waiting for `end`), while `isIncomplete("print(1)")` returns `false`.
