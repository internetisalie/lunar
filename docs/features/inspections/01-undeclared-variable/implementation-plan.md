---
id: "INSP-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "INSP-01"
status: "planned"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Implementation Plan: INSP-01 Undeclared Variable

This plan outlines the steps to implement real-time highlighting for undeclared variables.

## Phase 1: Core Logic & Local Resolution [Must]

Goal: Highlight variables that fail to resolve to a local scope.

- [ ] Create `LuaUndeclaredVariableAnnotator` and register it in `plugin.xml`.
- [ ] Implement basic check: call `resolve()` on `LuaNameRef`.
- [ ] Implement "Read Context" detection to avoid flagging assignments as undeclared.
- [ ] Add unit tests for local undeclared variables.

## Phase 2: Global & Library Integration [Must]

Goal: Ensure globals and standard libraries are correctly handled.

- [ ] Verify that `resolve()` correctly handles project-wide globals via stubs.
- [ ] Verify that `resolve()` correctly handles standard library symbols via `PlatformLibraryIndex`.
- [ ] Fix any "Used before declaration" false positives/negatives in local scope.
- [ ] Add unit tests for cross-file globals and standard library functions.

## Phase 3: Refinement & Configuration [Should]

Goal: Add suppression and user-defined globals.

- [ ] Add support for "Ignored Globals" in `LuaProjectSettings`.
- [ ] Implement suppression via comments (e.g., `-- luacheck: ignore`).
- [ ] Refine error messages and prioritize against other inspections.

## Verification Tasks

### Unit Tests
- `LuaUndeclaredVariableInspectionTest.kt`:
    - `testLocalUndeclared()`
    - `testGlobalUndeclared()`
    - `testStandardLibraryResolved()`
    - `testAssignmentIsExcluded()`

### Manual Verification
- Open a project with multiple files.
- Verify that valid globals from other files are NOT highlighted.
- Verify that `print`, `math`, etc., are NOT highlighted.
- Verify that a typo in a variable name IS highlighted.
