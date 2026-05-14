# SYNTAX-07 Inlay Hints — Implementation Status Report

**Date**: 2026-05-13  
**Audit Coverage**: Full codebase review (source, tests, configuration)

---

## Executive Summary

SYNTAX-07 inlay hints are **partially implemented** with **5 of 11 requirements fully complete** (45% coverage). The core local variable type hints work well with 18 passing tests. However, critical features remain unimplemented: parameter name hints at call sites, return type hints for functions, method chaining hints, per-category settings UI, and large file threshold optimization.

**Status**: Requires additional implementation work before task can be marked complete.

---

## Detailed Requirements Analysis

### Implemented Requirements (5/11)

#### ✅ SYNTAX-07-01: Local Variable Type Hints
- **Status**: **FULL**
- **Evidence**: 
  - Main provider: `src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintProvider.kt`
  - Test coverage: `testLocalVariable`, `testArrayType`, `testFunctionReturnValue`, `testGenericFunction`, etc.
  - 18 tests passing in `LuaTypeInlayHintsTest`
- **Implementation**: Shows `: <type>` after local variable names where type can be inferred
- **Example**: `local x/*<# : number #>*/ = 42`

#### ✅ SYNTAX-07-02: Literal Type Inference
- **Status**: **FULL**
- **Evidence**:
  - Tests: `testBooleanLiteral`, `testArrayType`, `testMultipleAssignment`, `testFunctionReturnValue`
  - `LuaTypeInlayHintProvider` uses `LuaTypesVisitor.getTypes(element)` to infer types
- **Supported Types**: `string`, `number`, `integer`, `boolean`, `nil`, `table`, `function`
- **Example**: 
  ```lua
  local name/*<# : string #>*/ = "Alice"
  local flag/*<# : boolean #>*/ = true
  ```

#### ✅ SYNTAX-07-03: LuaCATS Type Inference
- **Status**: **FULL**
- **Evidence**:
  - Uses `LuaTypesVisitor.getTypes()` which respects LuaCATS annotations
  - Tests: `testFunctionReturnValue`, `testGenericFunction`, `testUnionTypePropagation`
  - Respects `@return`, `@type`, `@class`, `@alias`, `@generic` tags
- **Example**:
  ```lua
  ---@return string
  local function greet() return "hi" end
  local msg/*<# : string #>*/ = greet()
  ```

#### ✅ SYNTAX-07-08: Annotation Suppression
- **Status**: **FULL**
- **Evidence**:
  - Method: `hasExplicitAnnotation()` in `LuaTypeInlayHintProvider` (lines 44-69)
  - Checks for `@type`, `@class`, `@alias`, and `@param` tags
  - Tests: `testSuppressionWithExplicitType`, `testSuppressionWithClass`, `testSuppressionWithAlias`
- **Implementation**: Suppresses hints when corresponding annotations exist
- **Example**: No hint shown because `@type` already present:
  ```lua
  ---@type number
  local x = 42  -- no hint (annotation already visible)
  ```

#### ✅ SYNTAX-07-10: Background Execution
- **Status**: **FULL**
- **Evidence**:
  - Uses `SharedBypassCollector` (line 13 of `LuaTypeInlayHintProvider`)
  - Registered as `codeInsight.declarativeInlayProvider` in `plugin.xml`
  - IntelliJ's declarative inlay API runs collection off EDT automatically
- **Implementation**: Never blocks the Event Dispatch Thread

---

### Partially Implemented Requirements (1/11)

#### 🟡 SYNTAX-07-05: Parameter Hint Suppression
- **Status**: **PARTIAL**
- **Evidence**:
  - Annotation suppression works: `hasExplicitAnnotation()` prevents hints when `@param` tag exists
  - Tests: `testSuppressionWithExplicitType`
- **Missing**:
  - No logic to suppress when argument name matches parameter name
  - No logic to suppress single-letter parameters or `_`
  - No logic for method-style colon call suppression (implicit `self`)
- **Impact**: Parameter name hints not yet implemented (see 07-04 below)

---

### Not Implemented Requirements (5/11)

#### ❌ SYNTAX-07-04: Parameter Name Hints at Call Sites
- **Status**: **NOT IMPLEMENTED**
- **Evidence**: 
  - No parameter name hint logic in `LuaTypeInlayHintProvider`
  - `LuaParameterInfoHandler` exists but serves a different purpose (inline parameter info popup, not inlay hints)
  - No tests for parameter name inlay hints
- **Required Scope**:
  - Collect function call expressions in visitor
  - Resolve the called function
  - Extract parameter names from function definition or `@param` annotations
  - Generate hints before each argument (`param: value`)
  - Apply suppression rules (matching names, single-char, method calls)
- **Spec**: Section 2.2

#### ❌ SYNTAX-07-06: Return Type Hints for Function Definitions
- **Status**: **NOT IMPLEMENTED**
- **Evidence**:
  - No logic in `LuaTypeInlayHintProvider` for function definitions
  - No tests for return type inlay hints
  - `@return` annotations are used, but inferred return types are not shown
- **Required Scope**:
  - Collect function definition nodes (local, global, method)
  - Infer return types from return statements
  - Show `: <type>` after closing `)` of parameter list
  - Suppress if `@return` annotation exists
- **Spec**: Section 2.3
- **Example** (not currently shown):
  ```lua
  local function double(n)    -- should show: : number
      return n * 2
  end
  ```

#### ❌ SYNTAX-07-07: Method Chaining Return Type Hints
- **Status**: **NOT IMPLEMENTED**
- **Evidence**:
  - No method chaining detection logic in `LuaTypeInlayHintProvider`
  - No tests for method chaining hints
- **Required Scope**:
  - Detect method call chains (`:` operator)
  - For intermediate calls (not final), show return type after call
  - Suppress if `@return` annotation present
  - Avoid hints on single-line chains
- **Spec**: Section 2.4
- **Example** (not currently shown):
  ```lua
  local result = obj:method1()    -- should show: : MyClass
    :method2()                    -- should show: : MyClass
    :getValue()                   -- should show: : string
  ```

#### ❌ SYNTAX-07-09: Per-Category Settings UI
- **Status**: **NOT IMPLEMENTED**
- **Evidence**:
  - Only one inlay provider registered: `lua.type.hints`
  - Registered in group `TYPES_GROUP` with bundle key `lua.type.hints.name`
  - Bundle contains only one string: `lua.type.hints.name=Lua Inferred Types`
  - No settings configurables created for individual hint categories
  - No `LuaApplicationSettings` properties for hint toggles
- **Required Scope**:
  - Create settings properties for each category:
    - Show type hints for local variables (default: On)
    - Show parameter name hints at call sites (default: On)
    - Show return type hints for functions (default: Off)
    - Show method chaining return type hints (default: On)
  - Create settings UI under Settings → Editor → Inlay Hints → Types → Lua
  - Update inlay hint collectors to respect settings state
  - Store and persist per-IDE and per-project
- **Spec**: Section 3

#### ❌ SYNTAX-07-11: Large File Threshold
- **Status**: **NOT IMPLEMENTED**
- **Evidence**:
  - No file size checks in `LuaTypeInlayHintProvider`
  - No configuration property for threshold
  - No logic to skip hint computation for large files
- **Required Scope**:
  - Add configuration property (default: 10,000 lines)
  - In `collectFromElement()`, check file line count
  - Skip hint computation if threshold exceeded
  - Make threshold configurable in settings UI
- **Spec**: Section 6, Requirement 11
- **Rationale**: Prevent performance degradation on generated/minified files

---

## Architecture & Design Notes

### Current Implementation

**Provider Location**: `src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintProvider.kt`

**Registration**: `plugin.xml` declarative inlay provider:
```xml
<codeInsight.declarativeInlayProvider
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaTypeInlayHintProvider"
    isInternal="false"
    isEnabledByDefault="true"
    group="TYPES_GROUP"
    providerId="lua.type.hints"
    bundle="net.internetisalie.lunar.LuaBundle"
    nameKey="lua.type.hints.name"
/>
```

**Collection Method**: `SharedBypassCollector` (background execution off EDT)

**Type Resolution**: `LuaTypesVisitor.getTypes(element)` + `type.displayName()`

### Integration Points

1. **Type System**: Depends on `LuaGraphType` and `LuaTypesVisitor` from TYPE epic
2. **LuaCATS Support**: Uses `LuaCommentOwner.catsComment` for annotations
3. **PSI Tree**: Traverses `LuaNameRef` elements to find declaration sites
4. **Background API**: IntelliJ Platform's declarative inlay hints framework

---

## Test Coverage

**Location**: `src/test/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintsTest.kt`

**Tests Passing**: 18/18 (100% pass rate)

**Test Categories**:
- Literal type inference (6 tests)
- LuaCATS annotations (5 tests)
- Suppression logic (3 tests)
- Complex types (4 tests)

**Missing Test Cases**:
- Parameter name hints
- Return type hints
- Method chaining hints
- Settings enforcement
- File size threshold

---

## Related Work

### TYPE Epic Integration
The inlay hints implementation depends heavily on the TYPE epic's work:
- **Phase 1-5**: Type inference engine (`LuaGraphType`, `LuaTypesVisitor`)
- **Task 108**: "Phase 6 Implementation: Cross-file Inference & Inlay Hints" (DONE)
- **Task 269**: "Fix array type inlay hints" (DONE)

These provide the foundation for inferring types to display. SYNTAX-07 adds the UI layer.

### Related Features
- `LuaParameterInfoHandler`: Provides parameter info popup (separate from inlay hints)
- `LuaColorSettingsPage`: Semantic highlighting (separate from inlay hints)

---

## Technical Feedback & Preparatory Tasks

The following technical gaps must be addressed to unblock the remaining 55% of the feature scope:

### 1. Collector Architecture Expansion
The current `LuaTypeInlayHintProvider` only visits `LuaNameRef`. It must be refactored to use a more comprehensive visitor that handles:
- `LuaCallExpr` for parameter name hints (07-04).
- `LuaFuncDecl`, `LuaLocalFuncDecl`, and `LuaFuncDef` for return type hints (07-06).
- `LuaIndexExpr` / `LuaVarSuffix` for method chaining hints (07-07).

### 2. Resolution Robustness
Parameter hints (07-04) depend on resolving the function callee to its definition or LuaCATS annotation. The implementation must ensure:
- Reliable resolution of cross-file calls via `LuaTypeManager`.
- Extraction of parameter names from both PSI parameters and `---@param` tags in `LuaCatsComment`.

### 3. Suppression Logic Helper
A unified `LuaInlaySuppressionUtil` should be created to centralize:
- Name matching (arg == param).
- Triviality checks (single-char names, `_`).
- Implicit `self` detection for colon calls.
- Setting-based toggles once 07-09 is implemented.

### 4. Settings Infrastructure
`LuaApplicationSettings` needs properties for each hint category to support the per-category toggles required by SYNTAX-07-09.

---

## Implementation Plan: SYNTAX-07-04 (Parameter Name Hints)

| Phase | Task | Description |
| :--- | :--- | :--- |
| **1: Setup** | **Test Scaffolding** | Create `LuaParameterInlayHintsTest` with cases for basic calls, method calls, and suppression. |
| **2: Core** | **Call Visitor** | Update `LuaTypeInlayHintProvider` to visit `LuaFuncCall` and resolve the callee. |
| **3: Logic** | **Name Mapping** | Implement logic to pair arguments with parameter names from the resolved function. |
| **4: Rules** | **Suppression** | Implement the 5 suppression rules (matching names, single-char, etc.) defined in the spec. |
| **5: Verify** | **Integration** | Ensure hints respect the background thread and performance constraints. |

---

## Remaining Work

To complete SYNTAX-07, the following tasks are required:

1. **Parameter Name Hints** (SYNTAX-07-04)
   - Estimate: ~6-8 hours
   - Complexity: Medium (requires function call traversal + name resolution)

2. **Return Type Hints** (SYNTAX-07-06)
   - Estimate: ~4-6 hours
   - Complexity: Medium (requires return statement analysis)

3. **Method Chaining Hints** (SYNTAX-07-07)
   - Estimate: ~3-5 hours
   - Complexity: Medium (requires chain detection)

4. **Per-Category Settings** (SYNTAX-07-09)
   - Estimate: ~4-6 hours
   - Complexity: Low-Medium (IntelliJ settings API)

5. **Large File Threshold** (SYNTAX-07-11)
   - Estimate: ~2-3 hours
   - Complexity: Low

6. **Parameter Suppression Logic** (SYNTAX-07-05 completion)
   - Estimate: ~2-3 hours
   - Complexity: Low

**Total Estimated Effort**: ~21-31 hours

---

## Recommendations

1. **Priority**: Complete parameter name hints (07-04) first — highest user value and moderate complexity
2. **Testing**: Add comprehensive test suite for missing features before implementation
3. **Refactoring**: Consider extracting hint visibility logic into a separate `HintSettings` class
4. **Documentation**: Update user guide with inlay hints configuration section once settings UI is implemented

---

## References

- Specification: `docs/features/syntax/07-inlay-hints.md`
- Implementation: `src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintProvider.kt`
- Tests: `src/test/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintsTest.kt`
- TYPE Epic: Integration with type system (Tasks 108, 269)
