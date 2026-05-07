# Specification: MAINT-04 Refactor Symbol Resolution

## 1. Overview
This document outlines the architectural redesign of the symbol resolution system in Lunar. The current system relies on eager AST traversal (`LuaBindingsVisitor`), which suffers from performance bottlenecks, fragile caching, and incompatibility with advanced type inference (LuaCATS). The goal is to migrate to a standard, lazy-evaluated IntelliJ `PsiScopeProcessor` model.

## 2. Problem Statement
The current implementation of symbol resolution utilizes `LuaBindingsVisitor` to traverse the entire file's PSI tree upfront. 
- **Performance:** Caching the entire file's bindings (`CachedValuesManager`) means any file modification invalidates the entire cache, leading to severe latency during typing as the file is re-traversed.
- **Global vs Local:** The current visitor conflates local variable resolution with expensive cross-file global resolution (`getBindingsWithImports`), risking UI freezes.
- **Type System Limitations:** A naive lexical scope tree cannot accurately represent complex Lua control flow (e.g., conditional scoping, LuaCATS type narrowing) required by the upcoming Cubic Biunification type engine.

## 3. Architectural Requirements

### 3.1 Lazy Evaluation (PsiScopeProcessor)
- Replace `LuaBindingsVisitor` with a standard `PsiScopeProcessor`.
- Implement `PsiElement.processDeclarations(...)` on relevant block and structural PSI elements (e.g., `LuaBlock`, `LuaFuncDef`, `LuaForStatement`).
- `LuaNameReference.multiResolve()` must use `PsiTreeUtil.treeWalkUp()` to walk up the PSI tree from the usage site to find the declaration on-demand.

### 3.2 Decoupling Local and Global Resolution
- **Local Resolution:** The `PsiScopeProcessor` should exclusively handle local (early-bound) variables.
- **Global Resolution:** If the scope processor reaches the file root without a match, resolution must delegate to IntelliJ's `StubIndex` (late-bound) to find global declarations or package exports.
- Remove the `getBindingsWithImports` method and avoid performing cross-file resolution (`VirtualFilesQuery`) during AST traversal.

### 3.3 Robust PSI Pointers
- Eliminate the use of `PsiElement.textOffset` as keys for caching bindings, as they are fragile during typing and incomplete parses.
- Use `SmartPsiElementPointer` or direct `PsiElement` references where caching is strictly necessary.

### 3.4 Label Refactoring (MAINT-02 Dependency)
- The existing logic for Lua `goto` and `::label::` resolution is heavily coupled to the `LuaBindingsVisitor` (via delayed references). 
- As part of this redesign, label resolution must be migrated to the new scope processor architecture. This serves as the implementation path for **MAINT-02 - Label refactoring**.

## 4. Implementation Steps
1. **Scope Processor Creation:** Implement `LuaScopeProcessor` extending `PsiScopeProcessor`.
2. **PSI Integration:** Override `processDeclarations` in `LuaElementImpl` (or specific block implementations) to feed declarations into the processor.
3. **Reference Refactoring:** Update `LuaNameReference` and `LuaLabelReference` to use the new scope processor.
4. **Remove Legacy Code:** Deprecate and remove `LuaBindingsVisitor`, `LuaBindings`, and associated caching logic.
5. **Testing:** Ensure all existing navigation and completion tests pass with the new lazy evaluation model.

## 5. Success Criteria
- Typing latency in large Lua files is significantly reduced.
- Cache invalidation no longer requires a full file re-traversal for simple reference lookups.
- The foundation is laid for `TYPE-01` (Basic Type Inference) to utilize control-flow-aware resolution.