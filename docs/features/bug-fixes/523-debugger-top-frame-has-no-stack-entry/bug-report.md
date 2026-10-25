---
id: "BUG-523"
title: "The debugger's initial top frame is built without its stack entry, so it has no variables"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-523: the top frame has no stack entry

Found in the third-pass review (`docs/review.md` §6, finding #109; #110 rides along). Verified against callers
and platform source; **not run**.

**Linked work.** BUG-463 (step pauses have no source position) and DEBUG-05 Phase 1 (`LuaFrameResolver`) change
the same lines in `LuaSuspendContext.kt`. **Fix them together**: BUG-463 §4 already warns that two fixes to this
path means one gets rewritten within the week.

## 1. Reproduction (read, not run)

Pause a debug session at a breakpoint inside a function with locals. Predicted: anything bound to the initial
frame before the Frames view loads sees no locals, and every pause computes Variables twice, the first pass
empty.

## 2. Expected vs actual

- **Expected:** `getTopFrame()` is the same object as the first frame listed by `computeStackFrames(0)`, with
  its stack entry, so its Variables are populated from the first pause.
- **Actual (by reading):** the top frame is a different object with `entry = null`.

## 3. Root cause (read, not executed)

`LuaSuspendContext.kt:62-65` (`initExecutionStack`) builds the top frame with `LuaStackFrame`'s 4-arg constructor,
so `entry = null` and `contextName = null`, and `LuaStackFrame.computeChildren` returns immediately
(`LuaStackFrame.kt:52`). The platform adopts exactly that frame as current on every pause:
`XDebugSessionImpl.updateSuspendContext` sets `currentStackFrame = currentExecutionStack?.topFrame`
(`XDebugSessionImpl.kt:1202-1205`). Variables appear only because `XFramesView`, once `computeStackFrames(0)`
arrives, selects index 0. `LuaStackFrame` does not override `getEqualityObject` (DEBUG-02-09), so index 0 is a
different object and `XFramesView` calls `setCurrentStackFrame` with it (`XFramesView.java:604-611,751-768`).

The top frame and frames[0] also take their positions from different sources (the breakpoint's position versus
the stack entry). That split is where BUG-463's step-pause defect lives.

Related P3 (#110): the `breakpoint` field (`:28`) is written and never read; both secondary constructors take 4
parameters, over the cap; the class holds a hard `Project` (`:26`) for the pause lifetime, unlike its twin
`redis/debug/LuaLdbSuspendContext.kt`.

## 4. Fix strategy

Not planned. Plan with `plan-bug`, together with BUG-463 and DEBUG-05 Phase 1. The report's direction: build the
frame list once in `LuaExecutionStack` from `stack.entries` and return frames[0] as `getTopFrame()`. Rewrite the
class to the `LuaLdbSuspendContext` shape: one primary constructor `(controller, stack, position)` holding no
`Project`.
