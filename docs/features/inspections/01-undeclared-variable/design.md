---
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
title: Design
---

# Technical Design: INSP-01 Undeclared Variable

## Overview

The "Undeclared Variable" inspection identifies identifiers that do not resolve to any local or global declaration. It leverages the existing lazy resolution mechanism in `LuaNameReference`.

## Architecture

### 1. LuaUndeclaredVariableAnnotator
A new `Annotator` implementation that targets `LuaNameRef` elements.

### 2. Resolution Strategy
For each `LuaNameRef`:
- Call `resolve()`.
- If `resolve()` returns a non-null `PsiElement`, the variable is declared.
- If `resolve()` returns `null`:
    - Determine the context of the reference.
    - If it is a **Read Context** (e.g., in an expression, function argument, etc.), highlight as an **Undeclared Variable**.
    - If it is a **Write Context** (e.g., left side of an assignment), it is technically a global creation. Depending on settings, this may be highlighted as a **Global Creation Warning** (INSP-05) instead.

### 3. Standard Library Integration
`LuaNameReference` already includes `PlatformLibraryIndex` in its external resolution phase. This ensures that standard Lua globals (like `print`, `string`, `math`) are correctly resolved to their library stubs and not highlighted as undeclared.

### 4. Suppression & Configuration
- Support `@diagnostic` comments (e.g., `---@diagnostic disable-next-line: undeclared-variable`).
- Allow ignoring specific names via project settings (e.g., `_G`, `_VERSION`, or custom host-provided globals).

## Implementation Details

### Context Detection
We need to distinguish between `var = value` and `value = var`.
- `LuaNameRef` is used in both cases.
- If `LuaNameRef` is a child of `LuaVar`, and `LuaVar` is in the `varList` of a `LuaAssignmentStatement`, it's a write context.
- Otherwise, it's generally a read context.

### Severity
- Default severity: **Warning**.
- In strict mode (if enabled), it could be an **Error**.

## Integration Points

- **PsiReference**: Relies on `LuaNameReference.resolve()`.
- **Stubs/Index**: Uses `LuaGlobalDeclarationIndex` and `PlatformLibraryIndex`.
- **Settings**: Uses `LuaProjectSettings` to check for ignored globals.
