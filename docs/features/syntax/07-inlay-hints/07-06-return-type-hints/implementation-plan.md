---
id: "SYNTAX-07-06-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-07-06"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-06-return-type-hints/requirements|requirements]]"
---
# Implementation Plan: SYNTAX-07-06 Return Type Hints

## Overview
This document outlines the phased implementation plan for adding return type inlay hints to Lunar. The work is structured incrementally to ensure accurate type resolution, proper AST positioning, and adherence to performance constraints.

## Phases

### Phase 1: Core Scaffolding & AST Targeting
**Priority**: [Must]
**Focus**: Hooking into the existing provider and identifying the correct position for the hint.

1.  **Test Scaffolding**:
    *   Add placeholder test cases for function returns in `LuaTypeInlayHintsTest.kt`.
2.  **Collector Extension**:
    *   In `LuaTypeInlayHintProvider.kt`, update the visitor to handle `LuaFuncDecl`, `LuaLocalFuncDecl`, and `LuaFuncDef`.
    *   *Note: Consider extracting this to a separate visitor file if the class becomes too large, though keeping it in the same class is fine for now.*
3.  **Position Resolution**:
    *   Implement a helper function to find the `LuaParameters` child of the function definition.
    *   Locate the closing `)` token to determine the exact offset for the hint.

### Phase 2: Type Inference Integration
**Priority**: [Must]
**Focus**: Connecting the hint position to the actual type system.

1.  **Type Extraction**:
    *   Use `LuaTypesVisitor.getTypes(element)` on the function definition node to get the `LuaGraphType`.
    *   Extract the `returnTypes` from the resulting function signature.
2.  **Formatting**:
    *   Implement logic to convert the extracted types into a display string.
    *   Handle multiple return types by joining them with commas (e.g., `: string, number`).
3.  **Rendering**:
    *   Use the declarative API (`addPresentation`) to append the formatted type string at the calculated offset.

### Phase 3: Suppression & Optimization
**Priority**: [Should]
**Focus**: Reducing visual noise and ensuring performance.

1.  **Annotation Suppression**:
    *   Update `hasExplicitAnnotation` (or create a new helper) to check the function's `LuaCatsComment` for `---@return` tags.
    *   Suppress the hint if the tag exists.
2.  **Void/Unknown Filtering**:
    *   Add logic to suppress hints for functions that return `unknown`, `any`, or have no explicit return values (void).
3.  **Performance Verification**:
    *   Verify that the type resolution does not cause significant latency or block the EDT.
    *   Ensure type caching (`CachedValuesManager` within `LuaTypesVisitor`) is effectively utilized.

## Verification Checklist

- [ ] `local function f() return 1 end` shows `: integer`
- [ ] `local function f() return 1, "two" end` shows `: integer, string`
- [ ] `---@return integer` suppresses the hint.
- [ ] Functions with no return statements do not show a hint.
- [ ] The feature runs entirely on a background thread without UI stutter.
