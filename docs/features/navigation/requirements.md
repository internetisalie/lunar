---
id: "NAV"
title: "NAV: Code Navigation"
type: "epic"
status: "done"
priority: "medium"
folders:
  - "[[features]]"
---

# Code Navigation Requirements (`NAV`)

Lunar provides powerful tools to explore and navigate the Lua codebase.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| [`NAV-01`](01-go-to-definition/requirements.md) | **Go to Definition (Symbols)** | **M** | Resolve and navigate to the declaration of variables, functions, and table fields. |
| [`NAV-02`](02-find-usages/requirements.md) | **Find Usages (Symbols)** | **M** | Search for all references to a specific symbol across the project. |
| [`NAV-03`](03-go-to-class-file-symbol/requirements.md) | **Go to Class/File/Symbol** | **S** | Implement `ChooseByName` contributors for quick navigation. |
| [`NAV-04`](04-structure-view/requirements.md) | **Structure View** | **M** | Provide a hierarchical outline of the current file's functions, classes, and variables, including anonymous functions. |
| [`NAV-05`](05-method-override-markers/requirements.md) | **Method Override Markers** | **S** | Show gutter icons for methods that override or implement a parent class/interface method. |
| [`NAV-06`](06-hierarchy-view/requirements.md) | **Hierarchy View** | **C** | Show the inheritance hierarchy for classes and tables. |
| [`NAV-07`](07-reference-contributors/requirements.md) | **Reference Contributors** | **S** | Register custom reference providers to enable PSI-based reference resolution via `PsiReferenceContributor`. |
| [`NAV-08`](08-line-markers/requirements.md) | **Line Markers** | **S** | Display gutter markers for special call types: recursive calls and tail calls. |
| [`NAV-09`](09-return-highlighter/requirements.md) | **Return Highlighter** | **C** | Highlight `return` statements and their corresponding function definitions for visual clarity. |
| [`NAV-10`](10-access-detector/requirements.md) | **Access Detector** | **S** | Detect and highlight variable access patterns (read vs. write) for semantic analysis. |
| [`NAV-11`](11-bindings-caching/requirements.md) | ~~**Bindings Caching**~~ (Retired) | **—** | **Retired** — premise removed by MAINT-04 (lazy `PsiScopeProcessor`/`PsiReference` resolution replaced the cached `LuaBindingsVisitor`). See [`11-bindings-caching.md`](11-bindings-caching/requirements.md). |

---

## Detailed Implementation Status

### NAV-01: Go to Definition (Symbols)
- **Status**: **Implemented** (`LuaNameReference.resolve`)

### NAV-02: Find Usages (Symbols)
- **Status**: **Partial** (Implemented for labels and local symbols; global symbol usages rely on standard indexing)

### NAV-04: Structure View
- **Status**: **Implemented** (`LuaStructureViewFactory`)

### NAV-07: Reference Contributors
- **Status**: **Implemented** (`LuaLabelReferenceContributor`)

### NAV-11: Bindings Caching — Retired
- **Status**: **Retired (2026-06-13)** — `LuaBindingsVisitor.getBindings` was removed in MAINT-04 (lazy `PsiScopeProcessor`/`PsiReference` resolution replaced the cached visitor), so the feature's premise no longer applies. Spec retained for history: [`11-bindings-caching.md`](11-bindings-caching/requirements.md).

