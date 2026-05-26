---
id: "DOC-06-PLAT-DESIGN"
folders:
  - "[[features/documentation/06-documentation-indexing/platform-symbol-documentation/requirements|requirements]]"
title: "Technical Design"
type: "design"
parent_id: "DOC-06-PLAT"
status: "done"
---

# Technical Design: DOC-06-06 Platform Symbol Documentation

## 1. Overview

This document outlines the architectural changes necessary to fulfill the requirements of `DOC-06-06`. The goal is to ensure that the Quick Documentation feature (`Ctrl+Q`) correctly resolves symbols defined in platform libraries (such as the standard Lua `math`, `string`, and `global` libraries).

## 2. Architecture & Components

The primary component affected is `LuaDocumentationTargetProvider`. Currently, this provider intercepts requests for documentation and attempts to resolve identifiers to their underlying declaration (e.g., a function declaration, a local variable declaration representing a class).

### 2.1 Current Limitations
Currently, `LuaDocumentationTargetProvider` falls back to querying the stub index if local reference resolution fails:
```kotlin
val scope = GlobalSearchScope.projectScope(element.project)
val classDecl = StubIndex.getElements(LuaClassNameIndex.KEY, elementText, element.project, scope, LuaLocalVarDecl::class.java).firstOrNull()
```
- **Scope Restriction**: `ProjectScope` only includes files in the user's workspace. It excludes `SyntheticLibrary` roots (where the standard library `platform/` files live).
- **Index Restriction**: It only checks `LuaClassNameIndex`. It misses symbols that are aliases (`LuaAliasIndex`) or global functions (`LuaGlobalDeclarationIndex`).

## 3. Proposed Implementation

### 3.1 Scope Expansion
We will update the fallback query to use `GlobalSearchScope.allScope(project)` instead of `GlobalSearchScope.projectScope(project)`. `allScope` includes both project files and external libraries.

### 3.2 Multi-Index Fallback Logic
We will expand the fallback resolution in `LuaDocumentationTargetProvider.resolveDocumentationTarget()` to sequentially query the necessary indices. The logic will short-circuit upon finding the first valid match.

**Execution Flow**:
1. Try resolving via local `PsiReference`.
2. If unresolved, check `LuaClassNameIndex` across `allScope`.
3. If unresolved, check `LuaAliasIndex` across `allScope`.
4. If unresolved, check `LuaGlobalDeclarationIndex` across `allScope`.

**Code Outline**:
```kotlin
val scope = GlobalSearchScope.allScope(element.project)

// 1. Check Classes
val classDecl = StubIndex.getElements(LuaClassNameIndex.KEY, elementText, element.project, scope, LuaLocalVarDecl::class.java).firstOrNull()
if (classDecl != null) return classDecl

// 2. Check Aliases
val aliasDecl = StubIndex.getElements(LuaAliasIndex.KEY, elementText, element.project, scope, LuaLocalVarDecl::class.java).firstOrNull()
if (aliasDecl != null) return aliasDecl

// 3. Check Global Functions
val funcDecl = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, elementText, element.project, scope, LuaFuncDecl::class.java).firstOrNull()
if (funcDecl != null) return funcDecl
```

## 4. PSI and Stub Impacts

**No changes** are required to the PSI structure or the stub indexing mechanisms. 
- The IntelliJ Platform already indexes `SyntheticLibrary` files by default.
- The platform files (e.g., `builtin.lua`, `math.lua`) contain standard LuaCATS annotations (`@class`, `@param`, etc.).
- `LuaClassNameIndex`, `LuaAliasIndex`, and `LuaGlobalDeclarationIndex` are already being populated with these platform symbols. The only issue was that our documentation lookup was intentionally ignoring them via `projectScope`.

## 5. Dependencies & Risks

- **Performance**: Expanding from `projectScope` to `allScope` and checking up to three indices could theoretically increase lookup times. However, since `StubIndex` is highly optimized and standard library files are small and infrequently changed, the impact will be negligible. The sequential short-circuiting ensures we do the minimum amount of work necessary.
- **Ambiguity**: If a user defines a class with the same name as a standard library class (e.g., `@class string`), the `StubIndex` will return multiple results. Currently, `firstOrNull()` is used. We might return the user's class or the standard library class depending on index ordering. This is acceptable for now, as shadowing standard types is generally discouraged, but could be refined in the future to prefer project scope over library scope if conflicts arise.

## 6. Implementation Plan

### 6.1 Phase 1: Verification (Reproduce Failure)
1.  **Create Test Case**: Add a new test file `src/test/kotlin/net/internetisalie/lunar/lang/doc/LuaPlatformDocumentationTest.kt`.
2.  **Verify Failure**: Assert that triggering `documentationTargets` on standard library symbols currently returns an empty list.

### 6.2 Phase 2: Implementation
1.  **Modify `LuaDocumentationTargetProvider.kt`**:
    *   Update `resolveDocumentationTarget` to use `GlobalSearchScope.allScope(element.project)`.
    *   Add consecutive lookups for `LuaClassNameIndex`, `LuaAliasIndex`, and `LuaGlobalDeclarationIndex`.

### 6.3 Phase 3: Validation
1.  **Verify Fix**: Re-run the tests created in Phase 1 to ensure they now return correct documentation targets.
2.  **Add Regression Tests**: Ensure built-in types (e.g., `string`, `number`) also resolve through documentation lookup in tags.
3.  **Full Suite Check**: Run the full test suite to ensure no regressions in existing documentation resolution.
