---
id: COMP-07-DESIGN
title: Live Templates Design
type: design
parent_id: COMP-07
status: planned
folders:
  - "[[features/completion/07-live-templates/requirements|requirements]]"
---

# Technical Design: COMP-07 — Live Templates

Ship a set of built-in live templates (abbreviation → expansion with tab stops) for common Lua
constructs, scoped to Lua files via a custom template context.

## 1. Architecture Overview

### Current State (what is actually built)
- Context: `net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType`
  (`src/main/kotlin/.../completion/templates/LuaTemplateContextType.kt:7`), id/presentable name
  `("LUA", "Lua")`.
- Bundled templates: `src/main/resources/liveTemplates/lua.xml` (`group="Lua"`), four templates:
  `fun`, `fori`, `forp`, `loc`.
- Registered at `plugin.xml:186-187` (`<defaultLiveTemplates>` + `<liveTemplateContext>`).
- Covered by `src/test/kotlin/.../completion/templates/LuaLiveTemplateTest.kt`.

**Requirement-vs-impl discrepancies to reconcile** (the impl is the shipped truth; the requirement
text is stale):
1. `COMP-07-01` names the function template **`func`**, but the shipped abbreviation is **`fun`**
   (`lua.xml`). Pick one and align both — recommend keeping `fun` (shorter) and updating the
   requirement, or adding `func` as an alias.
2. `COMP-07-01` calls `fori` an "ipairs loop", but the shipped `fori` is a **numeric** `for i = a, b`
   loop; the pairs loop is `forp`. There is no `ipairs` template. Update the requirement wording (or
   add an `forip` ipairs template if wanted — `Could`, not `Must`).
3. `loc` (local variable) ships but is not in the requirements table — add it as a documented `Should`.

### Prior Art in This Repo
Searched `src/main` for `TemplateContextType` / `liveTemplates` — only this feature's
`completion/templates/` package and `liveTemplates/lua.xml`. No overlap with the postfix templates
(COMP-06), which use a different platform EP. This design documents and reconciles the existing
feature; nothing to replace.

### Target State
The shipped four templates, each gated by the `LUA` context, with the requirement table reconciled
to match the actual abbreviations and semantics.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType`
- **Responsibility**: tell the platform that a live template applies inside a Lua file.
- **Threading**: platform-invoked during template lookup; no I/O.
- **Collaborators**: the `LUA` context option referenced by every `<template>` in `lua.xml`.
- **Key API** (as built):
  ```kotlin
  class LuaTemplateContextType : TemplateContextType("LUA", "Lua") {
      override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
          templateActionContext.file is LuaFile
  }
  ```

### 2.2 `src/main/resources/liveTemplates/lua.xml` (bundled data)
- **Format**: a `<templateSet group="Lua">` of `<template name=… value=… …>` entries; `&#10;` is the
  newline, `$END$` the final caret, `$NAME$`/`$VAR$`/… the tab stops; each carries
  `<context><option name="LUA" value="true"/></context>` to bind it to §2.1.
- **Shipped templates**:

  | Abbrev | Expansion (newlines shown as ⏎) | Tab stops |
  |--------|----------------------------------|-----------|
  | `fun`  | `function $NAME$($ARGS$)⏎    $END$⏎end` | NAME, ARGS |
  | `fori` | `for $VAR$ = $START$, $END_VAL$ do⏎    $END$⏎end` | VAR=i, START=1, END_VAL=10 |
  | `forp` | `for $K$, $V$ in pairs($TABLE$) do⏎    $END$⏎end` | K=k, V=v, TABLE=t |
  | `loc`  | `local $NAME$ = $VALUE$` | NAME, VALUE |

## 3. Algorithms
No non-trivial algorithm — context gating is a single `is LuaFile` check (§2.1); expansion is
data-driven by the platform live-template engine from `lua.xml`.

## 4. External Data & Parsing
The only "external data" is the bundled `lua.xml`, which is our own resource parsed by the platform
(`DefaultLiveTemplatesProvider`). Format is the IntelliJ live-template XML schema (§2.2). No
runtime CLI/text parsing.

## 5. Data Flow
### Example: `fun`⇥
User types `fun` and presses Tab in a `.lua` file → `LuaTemplateContextType.isInContext` returns
true (file is `LuaFile`) → platform expands the `fun` template → caret lands on `$NAME$`, then
`$ARGS$`, then `$END$` (the body).

## 6. Edge Cases
- Non-Lua file → `isInContext` false → templates inert.
- Abbreviation collides with a real identifier prefix → standard platform behavior (Tab expands,
  other completions still available).

## 7. Integration Points
```xml
<!-- plugin.xml:186-187 (extensions defaultExtensionNs="com.intellij") -->
<defaultLiveTemplates file="liveTemplates/lua.xml"/>
<liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType"/>
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-07-01 Basic Templates (`func`/`forp`/`fori`) | M | §2.1, §2.2 | **Built**, with naming reconciliations (see §1): shipped as `fun`/`forp`/`fori`(+`loc`); `fori` is numeric not ipairs |

## 9. Alternatives Considered
- Programmatic templates vs. bundled XML: bundled `defaultLiveTemplates` is the platform-idiomatic
  way to ship read-only built-ins users can copy/edit; chosen over building `Template` objects in code.

## 10. Open Questions
_None._ The discrepancies in §1 are documentation reconciliations (update the requirement text or
add optional templates), not unresolved design decisions.
