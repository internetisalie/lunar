---
id: NAV-05-DESIGN
title: "Technical Design"
type: design
parent_id: NAV-05
priority: "medium"
folders:
  - "[[features/navigation/05-method-override-markers/requirements|requirements]]"
---

# Technical Design: NAV-05 Method Override Markers

## 1. Architecture Overview

### Current State
`net.internetisalie.lunar.lang.insight.LuaLineMarkerProvider` already adds gutter markers
(recursive call, tail call) via `LineMarkerInfo`. The type engine exposes class inheritance:
`LuaTypeManager.getInstance(project).resolveType(name, context)` → `LuaClassType` with
`superTypes`, and `LuaType.resolveMember(name): LuaTypeMember?` walks supertypes;
`LuaTypeMember` carries `sourceElement: PsiElement?` (the navigation target) and `type`.

### Target State
A `RelatedItemLineMarkerProvider` adds an *overriding/implementing* gutter icon to a method
definition (`function C:m()` / `function C.m()`) whose name also exists on a superclass of `C`,
navigating to the super definition.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.LuaOverrideLineMarkerProvider`
- **Responsibility**: gutter marker + navigation for overridden methods.
- **Threading**: marker collection runs in the platform's read-action highlighting pass.
- **Key API**:
  ```kotlin
  class LuaOverrideLineMarkerProvider : RelatedItemLineMarkerProvider() {
      override fun collectNavigationMarkers(
          element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
          val ident = methodNameIdentifier(element) ?: return        // §3.1
          val supers = findSuperMembers(ident) ?: return             // §3.2
          if (supers.isEmpty()) return
          val icon = if (supers.any { it.isAbstract }) AllIcons.Gutter.ImplementingMethod
                     else AllIcons.Gutter.OverridingMethod
          result.add(NavigationGutterIconBuilder.create(icon)
              .setTargets(supers.mapNotNull { it.sourceElement })
              .setTooltipText("Overrides method in superclass")
              .createLineMarkerInfo(ident))
      }
  }
  ```
  Markers are attached to the **leaf identifier** (per the platform rule that line markers
  target leaf elements), as the existing provider does.

## 3. Algorithms

### 3.1 `methodNameIdentifier(element)`
- Return the method-name `IDENTIFIER` leaf when `element` is that leaf of a `LuaFuncDecl` whose
  `funcName.funcNameMethod != null` (a `:` method) **or** whose `funcName` has a property chain
  (`function a.b.m()`); else null. (Leaf-targeting keeps one marker per method.)

### 3.2 `findSuperMembers(ident): List<LuaTypeMember>`
- **Steps**:
  1. From `ident`, get the enclosing `LuaFuncDecl`; `methodName = ident.text`; the receiver
     class name = the `funcName.nameRef` (+ `funcNamePropertyList`) text chain (e.g. `C`).
  2. `val classType = LuaTypeManager.getInstance(project).resolveType(receiverName, decl)
     as? LuaClassType ?: return null`.
  3. For each `s in classType.superTypes` (recursively, with a `visited` set on class name to
     stop cycles), collect `s.resolveMember(methodName)` where the member's `type` is a function
     type. Return the collected `LuaTypeMember`s (their `sourceElement` are the targets).
- **`isAbstract`** (implement vs override): a super member is "abstract" when its `sourceElement`
  is a `@field`-declared function signature with no body (a declaration), vs a concrete
  `function …() end`. If abstract → ImplementingMethod, else OverridingMethod.
- **Bounds**: `visited` on class names prevents infinite inheritance loops; depth is the class
  hierarchy depth.

## 4. External Data & Parsing
None — PSI + the type engine.

## 5. Data Flow

### Example: override (NAV-05-01/03)
```lua
---@class Base
function Base:greet() end
---@class Derived : Base
function Derived:greet() end   -- gutter: OverridingMethod → navigates to Base:greet
```
For `Derived:greet`, `findSuperMembers` resolves `Derived`, walks to `Base`, finds `greet` →
marker with target `Base:greet`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| No superclass / name not in any super | `findSuperMembers` empty → no marker. |
| Cyclic `@class A : B` / `B : A` | `visited` class-name set breaks the loop. |
| Multiple supers define the method | all collected; popup lists targets. |
| `function C.m()` (dot, static) | treated the same — name match against supers. |
| Go to Super Method action (NAV-05-04) | the gutter navigation covers click-through; the Ctrl+U action handler is `NAV-05-DR-01`. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<codeInsight.lineMarkerProvider language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.LuaOverrideLineMarkerProvider"/>
```
- Reuses `LuaTypeManager`, `LuaClassType.superTypes`, `LuaTypeMember.sourceElement`,
  `AllIcons.Gutter.OverridingMethod`/`ImplementingMethod`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-05-01 Override marker | S | §2.1, §3.2 |
| NAV-05-02 Implement marker | S | §3.2 (isAbstract) |
| NAV-05-03 Navigation | S | §2.1 (`setTargets`) |
| NAV-05-04 Go to Super Method | S | §6 (DR-01) |

## 9. Alternatives Considered
- **`RelatedItemLineMarkerProvider` vs plain `LineMarkerProvider`**: the related-item variant
  provides the navigation popup/targets for free (NAV-05-03), unlike the plain provider used for
  the recursive/tail markers.

## 10. Open Questions

_None — feature has cleared the planning bar._
