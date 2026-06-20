---
id: "TYPE-08-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "TYPE-08"
folders:
  - "[[features/type/08-flow-sensitive/requirements|requirements]]"
---

# TYPE-08: Implementation Plan

## Phases

### Phase 1: `type()` Guard Recognition [Must]

- **Goal**: `if type(x) == "string" then … end` narrows `x` to `string` in the `then` block.
- **Tasks**:
  - [ ] Add `subtractMember` to `LuaTypeAlgebra` companion — design §3.3.
    - File: `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeAlgebra.kt`
  - [ ] Add `tryParseTypeofGuard` private function in `LuaTypesVisitor` companion — design §3.1.
    - File: `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt`
    - Also add `TYPEOF_MAP`, `TypeGuard` data class, `subtractType`, and `injectNarrowedBinding`.
  - [ ] Modify `LuaTypesVisitor.visitIfStatement` — design §3.4.
    - Parse guards from condition expressions; inject narrowed scope bindings for each block.
  - [ ] Create `TestFlowSensitiveType` test class.
    - File: `src/test/kotlin/net/internetisalie/lunar/lang/types/TestFlowSensitiveType.kt`
    - Extends `BasePlatformTestCase`, uses `LuaTypesSnapshot.forFile(file)`, `LuaTypes.getValueType(element)`.
- **Exit criteria**: TCs 1, 2, 11, 12, 13 pass. `./gradlew test --tests "*FlowSensitive*"` green.

### Phase 2: `~=` and `nil` Guards [Must / Should]

- **Goal**: `if type(x) ~= "nil" then … end` and `if x == nil / x ~= nil then … end` narrow correctly.
- **Tasks**:
  - [ ] Add `tryParseNilGuard` private function in `LuaTypesVisitor` companion — design §3.2.
  - [ ] Ensure polarity model handles `~=` → "match branch gets original minus type" correctly.
  - [ ] Extend tests for `~=` and `nil` guards.
- **Exit criteria**: TCs 3–8 pass.

### Phase 3: `elseif` Chains [Should / Could]

- **Goal**: each `elseif` branch + the `else` branch get independently narrowed types.
- **Tasks**:
  - [ ] In `visitIfStatement`, after processing each match branch, do NOT re-inject the original
    binding into subsequent branches — scope creation already handles this (each branch gets its
    own `scope.child()`). The key change: ensure the "last block gets complement of all guards"
    logic (§3.4 step 2) correctly handles a chain of `elseif` + `else`.
  - [ ] Extend tests for `elseif` chains.
- **Exit criteria**: TCs 9, 10 pass.

### Phase 4: Regression & Edge Case Hardening

- **Goal**: No regressions in existing type-engine tests; edge cases from design §6 handled.
- **Tasks**:
  - [ ] Run full type-engine test suite: `./gradlew test --tests "*TypeEngine*"` — all existing
    tests must stay green.
  - [ ] Add edge-case tests: unknown variable, `type()` with wrong arg count, nested `if` blocks,
    unreachable branch.
- **Exit criteria**: full test suite green; edge cases verified.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TYPE-08-01 | M | Phase 1 |
| TYPE-08-02 | M | Phase 2 |
| TYPE-08-03 | S | Phase 2 |
| TYPE-08-04 | S | Phase 2 |
| TYPE-08-05 | S | Phase 3 |
| TYPE-08-06 | C | Phase 3 |

## Verification Tasks
- [ ] Create `TestFlowSensitiveType` — covers all 13 TCs from `requirements.md`.
- [ ] Verify no regressions: `./gradlew test` — existing type-engine tests (TestLuaTypeEnginePhase1, TestLuaTypeDomainModel, TestLuaTypeEngineSafety, etc.) must remain green.
- [ ] Human verification: run `human-verification-checklists.md` scenarios in sandbox IDE.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: `type()` guard recognition | todo | Must |
| Phase 2: `~=` and `nil` guards | todo | Must / Should |
| Phase 3: `elseif` chains | todo | Should / Could |
| Phase 4: Regression & hardening | todo | Must |