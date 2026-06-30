---
id: REFACT-03-DESIGN
title: "Technical Design"
type: design
parent_id: REFACT-03
priority: "medium"
folders:
  - "[[features/refactoring/03-safe-delete-refactoring/requirements|requirements]]"
---

# Technical Design: REFACT-03 Safe Delete

## 1. Architecture Overview

### Current State
`RefactoringSupportProvider` is implemented (label rename) but `isSafeDeleteAvailable` is not
overridden (defaults false). The references/find-usages engine works (NAV-02:
`LuaNameReference.isReferenceTo` + the declaration-site recognition). The platform's Safe Delete
runs through a `SafeDeleteProcessorDelegate` to discover usages and the elements to remove.

### Target State
Enable Safe Delete for name declarations and provide a `SafeDeleteProcessorDelegate` that
searches usages (reusing the references engine) and, when none remain, removes the declaration;
otherwise the platform shows the standard conflict dialog.

## 2. Core Components

### 2.1 `LuaRefactoringSupportProvider.isSafeDeleteAvailable` (add)
```kotlin
override fun isSafeDeleteAvailable(element: PsiElement): Boolean =
    LuaFindUsagesProvider().canFindUsagesFor(element)   // declaration-site identifiers (NAV-02 §3.1)
```

### 2.2 `net.internetisalie.lunar.refactoring.LuaSafeDeleteProcessor`
- **Key API** (`com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate`):
  ```kotlin
  class LuaSafeDeleteProcessor : SafeDeleteProcessorDelegate {
      override fun handlesElement(element: PsiElement): Boolean        // §2.1 set
      override fun findUsages(element: PsiElement, allToDelete: Array<PsiElement>,
                              result: MutableList<UsageInfo>): NonCodeUsageSearchInfo  // §3.1
      override fun getElementsToSearch(element, module, allToDelete): Collection<PsiElement>?
      override fun getAdditionalElementsToDelete(...) : Collection<PsiElement>? = null
      override fun isToSearchInComments(e: PsiElement) = false
      override fun isToSearchForTextOccurrences(e: PsiElement) = false
      // setters: no-op
  }
  ```
  Registered `<refactoring.safeDeleteProcessor implementation=…>`.

## 3. Algorithms

### 3.1 `findUsages` — REFACT-03-02/03
- **Steps**:
  1. `ReferencesSearch.search(target, target.useScope)` → each `PsiReference` whose
     `isReferenceTo(target)` holds (the same path Find Usages uses).
  2. For each, add a `SafeDeleteReferenceSimpleDeleteUsageInfo(ref.element, target,
     isSafeToDelete = false)` to `result` (so remaining usages become conflicts the platform
     reports — REFACT-03-03).
  3. Return `NonCodeUsageSearchInfo(condition, target)` — `condition` excludes the declaration
     itself from non-code search.
- The platform: if `result` has no "unsafe" usages → deletes the declaration's PSI (the whole
  `local`/parameter/global statement, via the default element delete) with no prompt
  (REFACT-03-01); otherwise shows the conflict dialog.

### 3.2 Element to delete
- For a local `local x = 1` with a single name, deleting the declaration removes the
  `LuaLocalVarDecl`; for a multi-name `local x, y`, remove only the `x` attName (platform
  handles the element passed). `getElementsToSearch` returns the declaration identifier.

## 4. External Data & Parsing
None — references engine + PSI delete.

## 5. Data Flow
Safe Delete on unused `local x = 1` → `findUsages` finds 0 references → platform deletes the
statement. On used `x` → 1 usage → conflict dialog.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Global used cross-file | `useScope` is project-wide; cross-file usages found via the stub index. |
| Parameter delete | removes the parameter; call sites become conflicts (the platform reports). |
| Keyword/literal target | `isSafeDeleteAvailable` false (§2.1). |
| Shadowed local | `isReferenceTo`'s `resolve()` scoping keeps only true usages. |

## 7. Integration Points
```xml
<!-- META-INF/plugin.xml -->
<refactoring.safeDeleteProcessor
    implementation="net.internetisalie.lunar.refactoring.LuaSafeDeleteProcessor"/>
```
- Reuses `LuaFindUsagesProvider.canFindUsagesFor` (NAV-02), `LuaNameReference.isReferenceTo`,
  `ReferencesSearch`. Depends on REFACT-02's consolidated `LuaRefactoringSupportProvider` for
  `isSafeDeleteAvailable`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| REFACT-03-01 Enable for declarations | M | §2.1, §2.2 |
| REFACT-03-02 Usage search | M | §3.1 |
| REFACT-03-03 Conflict prompt | S | §3.1 (unsafe usages → platform dialog) |

## 9. Alternatives Considered
- **`SafeDeleteProcessorDelegate` vs relying on the default**: a delegate is required to tell the
  platform how to search Lua references and which element to remove; the default handles only
  PSI types it recognises (Java/etc.).

## 10. Open Questions

_None — feature has cleared the planning bar._
