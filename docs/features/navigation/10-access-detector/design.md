---
id: NAV-10-DESIGN
title: "Technical Design"
type: design
parent_id: NAV-10
priority: "medium"
folders:
  - "[[features/navigation/10-access-detector/requirements|requirements]]"
---

# Technical Design: NAV-10 Access Detector

## 1. Architecture Overview

### Current State
No read/write access analysis exists. Assignment PSI is well-defined:
`LuaAssignmentStatement.getVarList(): LuaVarList` (LHS) and `getExprList(): LuaExprList` (RHS);
a `LuaVar` has a `nameRef` and a `varSuffixList`. This is the same structure INSP-01 §3.1 uses
for write-target detection.

### Target State
A `ReadWriteAccessDetector` classifies each `LuaNameRef` occurrence as Read, Write, or
ReadWrite. The platform consumes it for (a) highlight colors on the selected symbol (NAV-10-03),
and (b) the Find Usages Read/Write grouping/filter (NAV-10-04, the same source NAV-02's
usage-type provider delegates to).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.LuaReadWriteAccessDetector`
- **Key API** (platform `ReadWriteAccessDetector`):
  ```kotlin
  class LuaReadWriteAccessDetector : ReadWriteAccessDetector() {
      override fun isReadWriteAccessible(element: PsiElement): Boolean =
          element is LuaNameRef || (element.elementType == LuaElementTypes.IDENTIFIER)
      override fun isDeclarationWriteAccess(element: PsiElement): Boolean   // §3.2
      override fun getReferenceAccess(referenced: PsiElement, reference: PsiReference): Access =
          getExpressionAccess(reference.element)
      override fun getExpressionAccess(expression: PsiElement): Access      // §3.1
  }
  ```
  `Access` is the platform enum `Read | Write | ReadWrite`.

## 3. Algorithms

### 3.1 `getExpressionAccess(expr)` — NAV-10-01/02
- Resolve `ref = expr as? LuaNameRef ?: expr.parent as? LuaNameRef ?: return Read`.
- **Write** iff `ref` is a simple assignment target: `ref.parent is LuaVar` with empty
  `varSuffixList`, and that `LuaVar`'s parent is `LuaVarList` of a `LuaAssignmentStatement`
  → `Access.Write`. (`name.field = …` base and compound assignments are Reads of the base.)
- **Else `Read`.** (Lua has no `++`/compound-assign operators, so `ReadWrite` does not arise
  from operators; reserved for future `x += 1` sugar if added.)

### 3.2 `isDeclarationWriteAccess(element)` — binding writes
- `true` when `element` (or its `nameRef`) is a declaration site: `LuaAttName` (local),
  parameter (`LuaNameList` under `LuaParList`), loop var (`LuaGenericForStatement`/
  `LuaNumericForStatement`) — the same declaration-site set as INSP-01 §3.1. A binding is a
  Write of the variable.

## 4. External Data & Parsing
None.

## 5. Data Flow

### Example (NAV-10-03): select `x` in `x = 1; print(x)`
The detector marks `x = 1` as Write (pink) and `x` in `print(x)` as Read (green) using the
user's *Identifier under caret* read/write colors.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `a, b = f()` | each LHS `LuaVar` (simple) → Write. |
| `t.k = 1` | `t` is Read (index base), `k` is the field (not a free var). |
| `local x = 1` | the `x` binding → `isDeclarationWriteAccess` true (Write). |
| Function parameter | binding Write at the parameter; Reads in the body. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<readWriteAccessDetector
    implementation="net.internetisalie.lunar.lang.insight.LuaReadWriteAccessDetector"/>
```
- NAV-02's `LuaReadWriteUsageTypeProvider` may delegate to this detector to avoid duplicating
  the write-target test.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-10-01 Write detection | S | §3.1 |
| NAV-10-02 Read detection | S | §3.1 |
| NAV-10-03 Highlight colors | S | §2.1 + platform highlight-usages |
| NAV-10-04 Find Usages filter | S | §7 (detector drives the Read/Write grouping) |

## 9. Alternatives Considered
- **Single shared write-target test**: NAV-10's detector is the canonical implementation;
  INSP-01 and NAV-02 reuse the same parent-PSI logic to stay consistent.

## 10. Open Questions

_None — feature has cleared the planning bar._
