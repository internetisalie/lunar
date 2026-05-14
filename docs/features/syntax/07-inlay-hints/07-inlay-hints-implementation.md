# SYNTAX-07 Inlay Hints — Implementation Status Report

**Date**: 2026-05-14  
**Audit Coverage**: Full codebase review (source, tests, configuration)

---

## Executive Summary

SYNTAX-07 inlay hints are **substantially implemented** with **8 of 11 requirements fully complete** (73% coverage). The core type inference, parameter name hints, and return type hints for function definitions are fully functional with comprehensive test coverage. Remaining work focuses on method chaining hints, per-category settings UI, and performance optimizations for large files.

**Status**: Implementation in progress; core features delivered.

---

## Detailed Requirements Analysis

### Implemented Requirements (8/11)

#### ✅ SYNTAX-07-01: Local Variable Type Hints
- **Status**: **FULL**
- **Evidence**: `LuaTypeInlayHintProvider.kt` (lines 22-42).
- **Implementation**: Shows `: <type>` after local variable names where type can be inferred.

#### ✅ SYNTAX-07-02: Literal Type Inference
- **Status**: **FULL**
- **Evidence**: `LuaTypesVisitor.getTypes(element)` correctly identifies literal types.
- **Supported Types**: `string`, `number`, `integer`, `boolean`, `nil`, `table`, `function`.

#### ✅ SYNTAX-07-03: LuaCATS Type Inference
- **Status**: **FULL**
- **Evidence**: Integration with `LuaTypesVisitor` which respects LuaCATS annotations for variable and return types.

#### ✅ SYNTAX-07-04: Parameter Name Hints at Call Sites
- **Status**: **FULL**
- **Evidence**: `LuaTypeInlayHintProvider.collectParameterHints` (lines 114-200).
- **Implementation**: Extracts parameter names from PSI or LuaCATS and renders `param:` before arguments. Supports cross-file resolution.

#### ✅ SYNTAX-07-05: Parameter Hint Suppression
- **Status**: **FULL**
- **Evidence**: `LuaTypeInlayHintProvider.shouldShowHint` (lines 202-210).
- **Implementation**: Suppresses hints when argument name matches parameter name, or parameter is single-char / `_`. Suppresses `self` in colon calls.

#### ✅ SYNTAX-07-06: Return Type Hints for Function Definitions
- **Status**: **FULL**
- **Evidence**: `LuaTypeInlayHintProvider.collectReturnTypeHints` (lines 56-95).
- **Implementation**: Shows inferred return types (including multiple returns) after function parameter lists.

#### ✅ SYNTAX-07-08: Annotation Suppression
- **Status**: **FULL**
- **Evidence**: `hasExplicitAnnotation` and `hasExplicitReturnAnnotation` helpers.
- **Implementation**: Suppresses hints when corresponding LuaCATS annotations are already visible.

#### ✅ SYNTAX-07-10: Background Execution
- **Status**: **FULL**
- **Evidence**: Uses `SharedBypassCollector` via Declarative Inlay Hints API.
- **Implementation**: Never blocks the EDT; computed asynchronously.

---

### In Progress Requirements (3/11)

#### 🚧 SYNTAX-07-07: Method Chaining Hints
- **Status**: **IN PROGRESS**
- **Saga Task**: Task 280
- **Documentation**: `docs/features/syntax/07-inlay-hints/07-07-method-chaining-hints/`
- **Missing**: Logic to detect multi-line chains and position hints at the end of each line.

#### 🚧 SYNTAX-07-09: Per-Category Settings UI
- **Status**: **IN PROGRESS**
- **Saga Task**: Task 282
- **Documentation**: `docs/features/syntax/07-inlay-hints/07-09-per-category-settings/`
- **Missing**: `LuaInlayHintsSettings` persistence model and UI Configurable.

#### 🚧 SYNTAX-07-11: Large File Threshold
- **Status**: **IN PROGRESS**
- **Saga Task**: Task 283
- **Documentation**: `docs/features/syntax/07-inlay-hints/07-11-large-file-threshold/`
- **Missing**: Integration with settings and early exit check in provider.

---

## Test Coverage

**Location**: `src/test/kotlin/net/internetisalie/lunar/lang/insight/hint/`

- **LuaTypeInlayHintsTest.kt**: 21 tests (literals, returns, annotations, suppression).
- **LuaParameterInlayHintsTest.kt**: ~10 tests (basic calls, method calls, cross-file, suppression).
- **LuaReturnTypeInlayHintsTest.kt**: Fully implemented and passing all tests.

---

## Technical Feedback
The implementation has matured significantly. The consolidation of Return Type Hints and Parameter Hints into the main provider is complete. The remaining tasks are primarily focused on "finishing touches" regarding user configuration and specialized formatting for method chains.
