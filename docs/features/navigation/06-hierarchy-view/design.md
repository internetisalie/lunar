---
id: NAV-06-DESIGN
title: "Technical Design"
type: design
parent_id: NAV-06
priority: "medium"
folders:
  - "[[features/navigation/06-hierarchy-view/requirements|requirements]]"
---

# Technical Design: NAV-06 Hierarchy View

## 1. Architecture Overview

### Current State
No hierarchy UI. Class inheritance is available: `@class A : B` is materialized by
`LuaTypeManager.resolveType` into a `LuaClassType` with `superTypes`; classes are indexed in
`LuaClassNameIndex` (`KEY: StubIndexKey<String, LuaLocalVarDecl>`). There is no *reverse*
(subclass) index yet.

### Target State
A `TypeHierarchyProvider` populates the standard Type Hierarchy tool window: supertypes (walk
`superTypes`) and subtypes (scan indexed classes whose `superTypes` include the target).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.hierarchy.LuaTypeHierarchyProvider`
- **Key API** (`com.intellij.ide.hierarchy.TypeHierarchyProvider`):
  ```kotlin
  class LuaTypeHierarchyProvider : TypeHierarchyProvider {
      override fun getTarget(dataContext: DataContext): PsiElement?    // the @class decl at caret
      override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser =
          LuaTypeHierarchyBrowser(target.project, target)
  }
  ```
- `LuaTypeHierarchyBrowser : TypeHierarchyBrowserBase` builds the node structure via two
  `HierarchyTreeStructure`s (super / sub), each yielding `HierarchyNodeDescriptor`s wrapping
  `LuaLocalVarDecl` class decls.

## 3. Algorithms

### 3.1 Supertypes (NAV-06-02)
- From the target class decl, `resolveType(name)` → `LuaClassType`; recursively emit each
  `superType` resolved back to its declaring `LuaLocalVarDecl` (via `LuaTypeMember.sourceElement`
  or `LuaClassNameIndex.getElements(superName)`), with a `visited` cycle guard.

### 3.2 Subtypes (NAV-06-01)
- Enumerate all class names (`StubIndex.processAllKeys(LuaClassNameIndex.KEY, …)`); for each,
  `resolveType(name)` and include it if any `superType`'s name equals the target's name. (Could
  priority → linear scan acceptable; a dedicated reverse index is `NAV-06-DR-01`.)

### 3.3 Method hierarchy (NAV-06-03)
- For a method, reuse NAV-05 `findSuperMembers` upward and the §3.2 subtype scan downward,
  filtering to classes that declare/override the same method name.

## 4. External Data & Parsing
None — type engine + `StubIndex`.

## 5. Data Flow
Caret on `@class Derived : Base` → *Navigate ▸ Type Hierarchy* → supertype tree shows `Base`;
subtype tree scans the class index for any `@class X : Derived`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Cyclic inheritance | `visited` class-name guard. |
| Multiple supertypes | all emitted. |
| Large project subtype scan | linear over class keys; cache per hierarchy session; reverse index deferred (`NAV-06-DR-01`). |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<typeHierarchyProvider language="Lua"
    implementation="net.internetisalie.lunar.lang.hierarchy.LuaTypeHierarchyProvider"/>
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-06-01 Subclasses | C | §3.2 |
| NAV-06-02 Supertypes | C | §3.1 |
| NAV-06-03 Method hierarchy | C | §3.3 |

## 9. Alternatives Considered
- **Linear subtype scan vs reverse index**: for a Could-priority feature the index scan is
  acceptable; a `superType → subclasses` stub index is the optimization (`NAV-06-DR-01`).

## 10. Open Questions

_None — feature has cleared the planning bar._
