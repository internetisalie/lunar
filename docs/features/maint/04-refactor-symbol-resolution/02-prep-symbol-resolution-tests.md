---
folders:
  - "[[features/maint/04-refactor-symbol-resolution/03-requirements|requirements]]"
title: "02: Prep Symbol Resolution Tests"
---

# Specification: MAINT-04-Prep — Symbol Resolution Test Suite

## 1. Overview

This document outlines a comprehensive test suite designed to de-risk the MAINT-04 refactoring (Refactor Symbol Resolution). By implementing tests in advance, we establish a regression safety net that ensures the new `PsiScopeProcessor`-based implementation correctly handles all existing behavior before code is refactored.

This work serves as a prerequisite to MAINT-04, establishing clear behavioral specifications that the new implementation must satisfy.

## 2. Goals

- **Regression Safety:** Provide comprehensive test coverage for symbol resolution, reference navigation, and label handling
- **Specification Clarity:** Document expected behavior for scope resolution before refactoring
- **Phased Validation:** Enable testing of MAINT-04 changes incrementally (local resolution → global resolution → label handling)
- **Faster Iteration:** Reduce time spent debugging after refactoring by catching issues early

## 3. Test Modules to Implement

### 3.1 LuaSymbolResolutionTest

**Purpose:** Verify correct behavior of local variable scoping, resolution, and shadowing.

**Test Cases:**

#### Scope Chaining
- `testSimpleLocalVariable` — Single local variable declaration and usage
- `testNestedBlockScoping` — Variables visible in nested blocks but not outer
- `testShadowing` — Inner scope shadows outer scope variable
- `testMultipleDeclarationsInScope` — Multiple locals with distinct names in same scope

#### Function Parameters
- `testFunctionParameterResolution` — Function parameters are resolvable within function body
- `testMultipleParameters` — Multiple parameters with shadowing
- `testParameterShadowsLocalVariable` — Parameter shadows local from outer scope

#### Loop Variables
- `testForLoopVariableResolution` — For loop variables resolvable within loop body
- `testForLoopScopingBoundary` — For loop variable not visible after loop end
- `testNumericForLoopImplicitVariable` — Implicit loop counter (_) in numeric for loops
- `testGenericForLoopMultipleVariables` — Generic for loop with multiple iteration variables
- `testWhileLoopNoVariableScope` — While loops don't introduce new variables
- `testRepeatUntilLoopVariable` — Repeat-until variable scope

#### Control Flow Scope
- `testConditionalScopingIfBlock` — If/then block introduces new scope
- `testConditionalScopingElseBlock` — Else block has separate scope
- `testConditionalScopingElseIfChain` — Elseif chains maintain separate scopes
- `testGlobalVariableAfterAssignment` — Assignment to undefined variable creates global

#### Edge Cases
- `testForwardReferenceInInitializer` — Local referencing later-declared variable should fail
- `testLocalAfterUsage` — Usage before declaration should fail
- `testRedeclarationInSameScope` — Redeclaring local in same scope should resolve to latest
- `testEmptyBlock` — Empty blocks don't interfere with scoping
- `testNestedFunctions` — Nested function scope isolation

### 3.2 LuaGlobalResolutionTest

**Purpose:** Verify cross-file global resolution and late-binding via StubIndex.

**Test Cases:**

#### Global Variable Resolution
- `testGlobalVariableInSameFile` — Global in file resolvable from anywhere in file
- `testGlobalVariableAcrossFiles` — Global defined in one file resolvable in another
- `testPackageExports` — Module exports via `module.exports` or return statement
- `testRequireResolution` — `require("module")` returns module's exported table

#### Import/Require Resolution
- `testRequireStringLiteral` — `require("modulename")` resolves to module file
- `testRequireExpressionVariant` — `require` with different argument patterns
- `testCircularRequires` — Circular module dependencies don't crash
- `testRequireWithFileExtension` — `require("module")` finds `module.lua`
- `testRequireWithPath` — Relative and absolute require paths

#### Global vs Local Priority
- `testLocalShadowsGlobal` — Local variable shadows global in same scope
- `testGlobalResolvesWhenNoLocal` — Falls back to global when local not found
- `testFunctionParameterShadowsGlobal` — Parameter shadows cross-file global

### 3.3 LuaLabelResolutionTest

**Purpose:** Verify goto and label resolution behavior.

**Test Cases:**

#### Label Declaration and Usage
- `testSimpleLabelResolution` — Label declaration and goto target
- `testMultipleLabelsInFile` — Multiple labels with distinct names
- `testLabelVisibility` — Label visible from different code paths
- `testForwardGoto` — Goto to label defined later in file
- `testBackwardGoto` — Goto to label defined earlier in file

#### Scope Isolation
- `testLabelScopingInBlock` — Label scope relative to blocks
- `testLabelNotVisibleOutsideScope` — Label not accessible from outer scope (if applicable)
- `testNestedLabels` — Multiple labels in nested blocks
- `testLabelShadowing` — Inner label shadows outer label

#### Error Cases
- `testUndefinedLabelGoto` — Goto to non-existent label
- `testLabelInvalidContext` — Label in invalid context
- `testGotoIntoBlockUnsafe` — Goto into block with live variables

### 3.4 LuaReferenceNavigationTest

**Purpose:** Verify navigation features (goto definition, find usages) work correctly.

**Test Cases:**

#### Goto Definition
- `testGotoDefinitionLocal` — Goto definition of local variable
- `testGotoDefinitionGlobal` — Goto definition of global variable
- `testGotoDefinitionFunctionParameter` — Goto definition of function parameter
- `testGotoDefinitionFunctionDeclaration` — Goto function definition
- `testGotoDefinitionLabel` — Goto label definition
- `testGotoDefinitionCrossFile` — Goto definition in different file
- `testGotoDefinitionMultipleMatches` — Handle multiple definitions (shadowing)

#### Find Usages
- `testFindUsagesLocalVariable` — Find all usages of local variable
- `testFindUsagesGlobalVariable` — Find all usages of global variable
- `testFindUsagesExcludeDefinition` — Find usages excludes the definition itself
- `testFindUsagesAcrossFiles` — Find usages across multiple files

#### Reference Highlighting
- `testHighlightAllUsagesInScope` — Highlight all usages in current scope when name selected
- `testHighlightExcludesOtherScopes` — Don't highlight same name from different scope

### 3.5 LuaCompletionIntegrationTest

**Purpose:** Verify code completion works with symbol resolution (integration test).

**Test Cases:**

#### Local Completion
- `testCompleteLocalVariablesInScope` — Completion suggests locals in current scope
- `testCompleteParametersInFunction` — Completion suggests function parameters
- `testCompleteLoopVariables` — Completion suggests loop variables

#### Global Completion
- `testCompleteGlobalVariables` — Completion suggests globals from current file
- `testCompleteGlobalsCrossFile` — Completion suggests globals from other files
- `testCompleteAfterRequire` — Completion suggests fields from required module

#### Shadowing in Completion
- `testCompletionRespectsShadowing` — Completion respects local shadowing global
- `testCompletionShowsAllOverloads` — Show overloaded functions (if applicable)

## 4. Test Implementation Phases

### Phase 1: Foundation (Baseline) — ✅ COMPLETED
**Duration:** 3-4 days
**Deliverable:** LuaSymbolResolutionTest with local scope coverage

**Implementation:** Commit `c980e23`
- ✅ `test-local-scoping` — Implemented "Scope Chaining" test cases (4 tests)
- ✅ `test-function-params` — Implemented "Function Parameters" test cases (2 tests)
- ✅ `test-loop-variables` — Implemented "Loop Variables" test cases (3 tests)
- ✅ `test-control-flow-edge-cases` — Implemented redeclaration and nested functions (1 test)

**Validation:** ✅ All 10 tests pass with current LuaBindingsVisitor implementation

**Actual Test Coverage (Phase 1):**
1. ✅ `testSimpleLocalVariable` — Basic local variable resolution
2. ✅ `testNestedBlockScoping` — Nested block variable visibility
3. ✅ `testMultipleDeclarationsInScope` — Multiple locals in same scope
4. ✅ `testFunctionParameterResolution` — Function parameter resolution
5. ✅ `testMultipleParameters` — Multiple function parameters
6. ✅ `testForLoopVariableResolution` — For loop variable scoping
7. ✅ `testGenericForLoopMultipleVariables` — Generic for loop with multiple vars
8. ✅ `testWhileLoopNoVariableScope` — While loop scoping rules
9. ✅ `testRedeclarationInSameScope` — Variable redeclaration handling
10. ✅ `testNestedFunctions` — Nested function parameter visibility

**Deferred to Phase 2 (pending):**
- Shadowing test (advanced case)
- Forward reference test (validation case)
- Repeat-until loops
- Conditional scoping (if/else/elseif)
- Empty block handling

### Phase 2: Global & Cross-File (Pending)
**Duration:** 2-3 days
**Deliverable:** LuaGlobalResolutionTest and LuaReferenceNavigationTest (goto/find)

**Tasks:**
- `test-global-resolution` — Implement "Global Variable Resolution" test cases
- `test-import-require` — Implement "Import/Require Resolution" test cases
- `test-goto-definition` — Implement "Goto Definition" test cases

**Validation:** Run expanded test suite; document expected vs actual for failing tests

### Phase 3: Labels & Navigation (Pending)
**Duration:** 2-3 days
**Deliverable:** LuaLabelResolutionTest and LuaCompletionIntegrationTest

**Tasks:**
- `test-label-resolution` — Implement all label test cases
- `test-reference-navigation` — Implement remaining reference test cases (find usages, highlighting)
- `test-completion-integration` — Implement completion integration tests

**Validation:** Full test suite passes; document baseline for MAINT-04 refactoring

## 5. Test Execution & Maintenance Strategy

### Baseline Establishment
1. Run full test suite with current implementation
2. Document test results in `test-baseline.txt`:
   - Pass count and percentage by module
   - Known failures (with reasons)
   - Performance baselines (duration per test)

### Phased Rollout During MAINT-04
1. **Phase 1 (Local Scope Processor):** Run Phase 1 tests; must achieve 100% pass
2. **Phase 2 (Global Resolution):** Run Phase 1 + Phase 2 tests; must achieve 100% pass
3. **Phase 3 (Label Refactoring):** Run all tests; must achieve 100% pass

### Continuous Validation
- All tests must remain green in main branch
- New symbol resolution features must include corresponding tests
- Performance regression tests: completion latency in files >10KB

## 6. Success Criteria

✅ **Phase 1 Complete (Commit c980e23):**
- ✅ 10 local scope and parameter resolution tests implemented
- ✅ All tests pass with current LuaBindingsVisitor
- ✅ Baseline established for MAINT-04 validation
- ✅ Test file: `src/test/kotlin/net/internetisalie/lunar/lang/LuaSymbolResolutionTest.kt`

**Phase 2 & 3 (Pending):**
- ⏳ Full test suite implementation (50+ total tests across 3 modules)
- ⏳ 100% pass rate with current implementation (establishes regression baseline)
- ⏳ Documentation and performance baselines for MAINT-04

✅ **Full Test Suite Success Criteria (All Phases):**
- All test cases from sections 3.1–3.5 implemented
- Baseline established with current LuaBindingsVisitor
- Pass rate documented for each module

✅ **MAINT-04 Compatibility**
- New PsiScopeProcessor implementation achieves 100% test pass rate
- No regressions in navigation features (goto, find usages)
- No regressions in completion accuracy

✅ **Documentation**
- Each test includes docstring explaining the scenario
- Comments explain non-obvious assertions
- `test-baseline.txt` documents expected behaviors

✅ **Performance**
- Test execution <5 minutes for full suite
- No test causes >1 second delay in IDE (indicates hang)

## 7. Implementation Status by Phase

### Phase 1: Foundation (✅ Complete)

**Module:** `LuaSymbolResolutionTest` (10 tests)

| Test Case | Status | Notes |
|-----------|--------|-------|
| testSimpleLocalVariable | ✅ Implemented | Basic local variable resolution |
| testNestedBlockScoping | ✅ Implemented | Nested block variable visibility |
| testMultipleDeclarationsInScope | ✅ Implemented | Multiple locals with distinct names |
| testFunctionParameterResolution | ✅ Implemented | Function parameter resolution |
| testMultipleParameters | ✅ Implemented | Multiple function parameters |
| testForLoopVariableResolution | ✅ Implemented | For loop variable scoping |
| testGenericForLoopMultipleVariables | ✅ Implemented | Generic for loop with multiple iteration vars |
| testWhileLoopNoVariableScope | ✅ Implemented | While loop scoping behavior |
| testRedeclarationInSameScope | ✅ Implemented | Variable redeclaration handling |
| testNestedFunctions | ✅ Implemented | Nested function parameter visibility |

**Baseline Status:** ✅ All 10/10 tests pass with current LuaBindingsVisitor

### Phase 2: Global & Cross-File (⏳ Pending)

**Modules:** `LuaGlobalResolutionTest`, `LuaReferenceNavigationTest` (20+ tests)

**Status:** Not yet implemented. Blocked by Phase 1 completion (now satisfied).

### Phase 3: Labels & Navigation (⏳ Pending)

**Modules:** `LuaLabelResolutionTest`, `LuaCompletionIntegrationTest` (20+ tests)

**Status:** Not yet implemented. Blocked by Phase 2 completion.

## 8. Dependency on MAINT-04

This test suite is a **strict prerequisite** to MAINT-04:

1. ✅ Phase 1 baseline established (commit c980e23)
2. ⏳ MAINT-04 implementation MUST pass all tests from this suite
3. ⏳ If tests fail, MAINT-04 is considered incomplete
4. ⏳ New edge cases discovered during MAINT-04 MUST be added to this suite before merge

## 8. Risk Mitigation

**Risk:** Tests are too tightly coupled to current implementation (LuaBindingsVisitor)
**Mitigation:** Tests focus on behavioral contracts, not implementation details. Tests verify reference targets, not internal binding structures.

**Risk:** Tests take too long to run, slowing development
**Mitigation:** Tests are organized into modules; can run targeted test suites during development (e.g., `./gradlew test --tests "*LuaSymbolResolutionTest"`)

**Risk:** Edge cases not covered
**Mitigation:** MAINT-04 PR review includes "did we test this scenario?" checklist against edge cases found during refactoring.
