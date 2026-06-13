---
id: "NAV-02-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "NAV-02"
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/02-find-usages/requirements|requirements]]"
---

# Technical Design: NAV-02 Find Usages

## 1. Architecture Overview

### Current State
`net.internetisalie.lunar.lang.insight.LuaLabelFindUsagesProvider` implements
`FindUsagesProvider` but `canFindUsagesFor` returns true **only** for `LuaLabelName`
(NAV-02-03). Symbol references already resolve through
`net.internetisalie.lunar.lang.LuaNameReference`, whose `isReferenceTo(element)` (`:237`)
compares the identifier text and `resolve() === element` — so IntelliJ's `ReferencesSearch`
can already find a declaration's references **once the declaration is recognised as a
find-usages target**. The `DefaultWordsScanner` over `LuaSyntax.IdentifierTokens` already feeds
the word index. Read/Write categorisation does not exist.

### Target State
Broaden the provider to recognise **name declaration identifiers** (locals, parameters,
loop vars, global function/assignment names, `@class` names) as targets, and add a
`UsageTypeProvider` that labels each usage Read vs Write. No new search algorithm — the
platform's `ReferencesSearch` + `LuaNameReference.isReferenceTo` does the finding.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider` (replaces the label-only one)
- **Responsibility**: declare which elements support Find Usages and how to label them.
- **Key API**:
  ```kotlin
  class LuaFindUsagesProvider : FindUsagesProvider {
      override fun getWordsScanner() = DefaultWordsScanner(LuaLexer(),
          LuaSyntax.IdentifierTokens, LuaSyntax.CommentTokens, LuaSyntax.StringLiteralTokens)
      override fun canFindUsagesFor(e: PsiElement): Boolean       // §3.1
      override fun getType(e: PsiElement): String                 // "local variable"|"parameter"|
                                                                  // "global function"|"global variable"|
                                                                  // "class"|"label"
      override fun getDescriptiveName(e: PsiElement): String       // the identifier text
      override fun getNodeText(e: PsiElement, useFullName: Boolean): String
      override fun getHelpId(e: PsiElement): String? = null
  }
  ```
  Registered by **changing** the existing `<lang.findUsagesProvider implementationClass=…>` to
  this class (it also keeps the `LuaLabelName` branch, so NAV-02-03 stays Full).

### 2.2 `net.internetisalie.lunar.lang.insight.LuaReadWriteUsageTypeProvider`
- **Responsibility**: classify a usage occurrence as Read or Write (NAV-02 §UI + NAV-10).
- **Key API** (platform `UsageTypeProvider`):
  ```kotlin
  class LuaReadWriteUsageTypeProvider : UsageTypeProvider {
      override fun getUsageType(element: PsiElement): UsageType?    // §3.2
  }
  ```
  `UsageType.WRITE` / `UsageType.READ` are platform constants.

## 3. Algorithms

### 3.1 `canFindUsagesFor(e)` — which elements are targets
- Return `true` when:
  - `e is LuaLabelName` (labels, existing), **or**
  - `e` is the declaration **identifier** of a name — i.e. an `IDENTIFIER` leaf whose enclosing
    `nameRef` parent is a declaration site: `LuaAttName` (local), `LuaNameList` under `LuaParList`
    (parameter) or under `LuaGenericForStatement` (loop var), a `LuaNumericForStatement`
    identifier, `LuaFuncName.nameRef` (global/declared function), the var of a top-level
    `LuaAssignmentStatement` (global assignment), or a `@class` name `LuaLocalVarDecl` indexed in
    `LuaClassNameIndex`.
- This is the same declaration-site set used by INSP-01 §3.1 and `LuaScopeProcessor` (whose
  `resolve()` returns exactly these identifier leaves) — so the target IntelliJ resolves a
  `LuaNameRef` to is always recognised here.

### 3.2 `getUsageType(element)` — Read vs Write
- Walk up from `element` to the nearest `LuaNameRef` `ref`.
- **Write** if `ref` is a simple assignment target: `ref.parent` is `LuaVar` with empty
  `varSuffixList` and that `LuaVar`'s parent is `LuaVarList` of a `LuaAssignmentStatement`
  (same write-target test as INSP-01 §3.1), **or** `ref` is the loop/`local`/parameter
  declaration identifier (a binding write). → `UsageType.WRITE`.
- Else → `UsageType.READ`. (Index-base reads like `a` in `a.b = 1` are Reads.)

### 3.3 Scope (NAV-02-01 local vs NAV-02-02 global)
- No custom scope needed. `ReferencesSearch` scans files whose word index contains the name,
  then keeps refs where `isReferenceTo(target)` holds. For a **local** target, a `LuaNameRef`
  in another file (or another scope) never `resolve()`s to that local identifier, so it is
  naturally excluded (→ usages stay within scope). For a **global**, `LuaNameReference`
  resolves cross-file via `LuaGlobalDeclarationIndex`, so project-wide usages match
  (NAV-02-02 uses the stub index — the implementation-constraint requirement).

## 4. External Data & Parsing
None — PSI + the platform word/stub indexes only.

## 5. Data Flow

### Example: Find Usages on a global `function Helper()` (NAV-02-02)
Target = the `Helper` identifier (a `LuaFuncName.nameRef`). `ReferencesSearch` finds files
containing `Helper` via the index; each `Helper` `LuaNameRef` resolves through the stub index
back to the declaration (`isReferenceTo` true) → listed. The call `Helper()` is a Read; an
assignment `Helper = …` is a Write (§3.2).

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Local shadowed in a nested block | `isReferenceTo` uses `resolve()`, which honours scope/early-binding → only true usages match. |
| `goto`/label | existing `LuaLabelName` branch (NAV-02-03). |
| Table field `t.x` usages (NAV-02-04, Could) | broad/text fallback only in v1 — fields are not uniquely resolvable; tracked `NAV-02-DR-01`. |
| `@type` → `@class` usages (NAV-02-05, Should) | depends on a reference from `@type` names to the `@class` decl; if absent, deferred to NAV-07 reference contributors (`NAV-02-DR-02`). |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml — change the existing provider, add the usage-type provider -->
<lang.findUsagesProvider language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider"/>
<usageTypeProvider
    implementation="net.internetisalie.lunar.lang.insight.LuaReadWriteUsageTypeProvider"/>
```
- Reuses `LuaNameReference.isReferenceTo`, `LuaGlobalDeclarationIndex`, `LuaClassNameIndex`,
  `LuaSyntax.IdentifierTokens`. No new index.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-02-01 Local usages | M | §3.1, §3.3 |
| NAV-02-02 Global usages | M | §3.1, §3.3 (stub index) |
| NAV-02-03 Label usages | S | §2.1 (existing label branch) |
| NAV-02-04 Table field usages | C | §6 (text fallback, DR-01) |
| NAV-02-05 LuaCATS type usages | S | §6 (DR-02) |
| Read/Write categorisation | — | §3.2 |

## 9. Alternatives Considered
- **Custom `UseScopeEnlarger`/`getUseScope` override vs default**: the default search +
  `isReferenceTo` already scopes correctly (locals excluded cross-scope by `resolve()`), so no
  custom scope is added.
- **Separate provider vs extending the label one**: one `LuaFindUsagesProvider` handles both
  names and labels — a single `<lang.findUsagesProvider>` registration.

## 10. Open Questions

_None — feature has cleared the planning bar._
