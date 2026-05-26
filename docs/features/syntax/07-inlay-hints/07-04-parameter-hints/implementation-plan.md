---
id: "SYNTAX-07-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-07-04"
status: "done"
priority: "high"
folders:
  - "[[features/syntax/07-inlay-hints/07-04-parameter-hints/requirements|requirements]]"
---
# Implementation Plan: SYNTAX-07-04 Parameter Name Hints

## Phase 1: Foundation & Tests [Must]
Set up the test environment and basic plumbing.

- **Task 1**: Create `LuaParameterInlayHintsTest.kt` with initial test cases for simple function calls.
- **Task 2**: Update `LuaTypeInlayHintProvider` to visit `LuaFuncCall`.
- **Verification**: Tests fail with "no hints found".

## Phase 2: Core Implementation [Must]
Implement the mapping from arguments to parameters.

- **Task 3**: Resolve `LuaFuncCall` to a `LuaGraphType.Function`.
- **Task 4**: Extract parameter names from the resolved PSI/LuaCATS.
- **Task 5**: Handle the `self` parameter offset for colon calls.
- **Task 6**: Emit `<name>:` hints for each argument.
- **Verification**: `TC-01` (Basic calls) passes.

## Phase 3: Suppression Logic [Should]
Implement the rules to reduce visual noise.

- **Task 7**: Add `nameMatch` suppression (arg == param).
- **Task 8**: Add `singleParam` suppression.
- **Task 9**: Add `trivialName` suppression (`_` or single-char).
- **Verification**: `TC-02`, `TC-04`, `TC-05` pass.

## Phase 4: Refinement [Could]
- **Task 10**: Integrate with `LuaApplicationSettings` once SYNTAX-07-09 is implemented.
- **Task 11**: Add performance metrics and verify file size threshold enforcement.
- **Verification**: Manual audit on large files.
