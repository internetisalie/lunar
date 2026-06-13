---
id: "NAV-09-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "NAV-09"
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/09-return-highlighter/requirements|requirements]]"
---

# Technical Design: NAV-09 Return Highlighter

## 1. Architecture Overview

### Current State
No exit-point highlighting. `return` is `LuaElementTypes.RETURN`; function bodies are
`LuaFuncBody`/`LuaBlock` under `LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaFuncDef`. The existing
`LuaLineMarkerProvider` already navigates `RETURN` tokens for tail calls, confirming the PSI
shape.

### Target State
A `HighlightUsagesHandlerFactory` so that placing the caret on a `return` highlights every
`return` of the **same** enclosing function (not nested ones), reusing the IDE's
identifier-highlight settings + Next/Previous navigation.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.LuaReturnHighlightUsagesHandlerFactory`
- **Key API**:
  ```kotlin
  class LuaReturnHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactoryBase() {
      override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement)
          : HighlightUsagesHandlerBase<PsiElement>? {
          if (target.elementType != LuaElementTypes.RETURN) return null
          return LuaReturnHighlightHandler(editor, file, target)   // §3.1
      }
  }
  ```
- `LuaReturnHighlightHandler : HighlightUsagesHandlerBase<PsiElement>` collects the targets
  (§3.1) and feeds them to the platform highlighter.

## 3. Algorithms

### 3.1 Same-scope `return` collection
- **Input**: the caret's `return` leaf `r`.
- **Steps**:
  1. `val fn = enclosingFunction(r)` = nearest ancestor among `LuaFuncDecl`/`LuaLocalFuncDecl`/
     `LuaFuncDef` (or the `LuaFile` for top-level returns).
  2. Collect every `RETURN` leaf `t` under `fn` whose `enclosingFunction(t) === fn` (this
     **excludes nested functions** — a `return` inside a nested `function … end` has a different
     `enclosingFunction`).
  3. Optionally (NAV-09-02) add the `FUNCTION` keyword leaf of `fn` to the highlight set.
- **Result**: the platform highlights all collected leaves; Next/Previous cycles them.

## 4. External Data & Parsing
None.

## 5. Data Flow
Caret on the outer `return` in `function f() if x then return 1 end; local g = function() return 2 end; return 3 end`
→ highlights the `return 1` and `return 3` (both enclosed by `f`), **not** `return 2` (enclosed
by the nested `g`).

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Nested functions | excluded by the `enclosingFunction(t) === fn` test. |
| Top-level `return` (module return) | `fn` = the `LuaFile`; highlights file-level returns only. |
| Single return | highlights just that one. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<highlightUsagesHandlerFactory
    implementation="net.internetisalie.lunar.lang.insight.LuaReturnHighlightUsagesHandlerFactory"/>
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-09-01 Highlight returns | C | §3.1 |
| NAV-09-02 Highlight function def | C | §3.1 step 3 |
| NAV-09-03 Exit Point Provider | C | §2.1 (`HighlightUsagesHandlerFactory`) |

## 9. Alternatives Considered
- **`HighlightUsagesHandlerFactory` vs a custom highlighting pass**: the factory reuses the
  IDE's highlight colors and Next/Previous navigation for free.

## 10. Open Questions

_None — feature has cleared the planning bar._
