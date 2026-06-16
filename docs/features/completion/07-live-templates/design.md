---
id: COMP-07-DESIGN
title: Live Templates Design
type: design
parent_id: COMP-07
status: done
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
A full built-in template set covering COMP-07-01…11:
- Four **insertion** contexts/templates already shipped (`fun`/`fori`/`forp`/`loc`).
- Nine **insertion** templates total when COMP-07-02…09 (`if`/`ifel`/`lfun`/`while`/`repeat`/
  `forip`/`req`/`mod`) are added — all pure XML in `lua.xml`.
- The single `LUA` context replaced by a code-aware `LUA_CODE` context (COMP-07-10) that suppresses
  expansion inside strings/comments/numbers, plus an optional in-`if` context (Could).
- Four **surround** templates (COMP-07-11) bound to a selection-aware context via `$SELECTION$`.

## 2. Core Components

### 2.1 Context types — `…lang/completion/templates/`

There are three context types after this feature; all extend
`com.intellij.codeInsight.template.TemplateContextType` and are registered via `<liveTemplateContext>`.

#### 2.1.1 `LuaTemplateContextType` (existing — RETAINED as the umbrella `LUA` context)
- **Id / name**: `("LUA", "Lua")`.
- **Predicate**: `templateActionContext.file is LuaFile`
  (`LuaFile` = `net.internetisalie.lunar.lang.psi.LuaFile`).
- **Role after COMP-07-10**: kept as the *base* (umbrella) context so existing user customizations
  keyed on `LUA` keep resolving, and so `LUA_CODE` can declare it as parent. No behaviour change.

#### 2.1.2 `LuaCodeContextType` (NEW — COMP-07-10) — the real-code context
- **Id / name**: `("LUA_CODE", "Lua (code)")`.
- **Parent context**: `LUA` — constructed via the 3-arg super
  `TemplateContextType("LUA_CODE", "Lua (code)", LuaTemplateContextType::class.java)`. Declaring the
  parent makes `LUA_CODE` show nested under `Lua` in the Settings tree and inherit its file gating.
  **Note:** this 3-arg constructor is `@deprecated` (the platform prefers `contextId`/`baseContextId`
  attributes on the `<liveTemplateContext>` registration), but it remains functional — the bean
  falls back to the constructor-set id (`LiveTemplateContextBean` `getContextId()`/`createInstance()`)
  and the already-shipped `LuaTemplateContextType` uses the deprecated 2-arg form. Either form is
  acceptable; prefer the plugin.xml-attribute form for the new contexts to avoid the warning.
- **Responsibility**: true only when the caret leaf is *not* inside a string, comment, or number —
  i.e. real Lua code. Fixes the defect where the `LUA`-only templates fire inside `"…"` and `--`.
- **Predicate**: see §3.1 (grounded against `LuaSyntax` token sets).
- **Threading**: platform-invoked during template lookup; PSI read only, no I/O, no write.

#### 2.1.3 `LuaIfContextType` (OPTIONAL — Could, COMP-07-10 partial / parked `elseif`)
- **Id / name**: `("LUA_IF", "Lua (inside if)")`, parent `LUA_CODE`.
- **Predicate**: §3.1 code check AND the caret leaf has a `LuaIfStatement` ancestor
  (`PsiTreeUtil.getParentOfType(leaf, LuaIfStatement::class.java) != null`). Used only to gate a
  future `elseif` template; ship only if/when `elseif` (parked backlog) is implemented. **Not on the
  Must/Should critical path** — documented here for completeness, deliverable deferred.

### 2.2 `src/main/resources/liveTemplates/lua.xml` (bundled data)
- **Format**: a `<templateSet group="Lua">` of `<template name=… value=… …>` entries; `&#10;` is the
  newline literal, `$END$` the final caret, `$NAME$`/`$COND$`/… the tab stops. Each template carries
  `<context><option name="LUA_CODE" value="true"/></context>` (insertion templates) or
  `name="LUA_SURROUND"` (surround templates, §2.3) to bind it to a context in §2.1 / the surround
  context.
- **Common attributes**: `description`, `toReformat="true"`, `toShortenFQNames="true"`. Tab-stop
  variables use `<variable name="X" expression="" defaultValue="…" alwaysStopAt="true"/>` (empty
  `expression` ⇒ no macro, plain editable stop).

#### 2.2.1 Shipped insertion templates (COMP-07-01) — migrate context `LUA` → `LUA_CODE`

  | Abbrev | Expansion (newlines shown as ⏎) | Tab stops | Req |
  |--------|----------------------------------|-----------|-----|
  | `fun`  | `function $NAME$($ARGS$)⏎    $END$⏎end` | NAME, ARGS | 01 |
  | `fori` | `for $VAR$ = $START$, $END_VAL$ do⏎    $END$⏎end` | VAR=i, START=1, END_VAL=10 | 01 |
  | `forp` | `for $K$, $V$ in pairs($TABLE$) do⏎    $END$⏎end` | K=k, V=v, TABLE=t | 01 |
  | `loc`  | `local $NAME$ = $VALUE$` | NAME, VALUE | 01 |

#### 2.2.2 New insertion templates (COMP-07-02…09)

  | Abbrev | `value` (literal — `&#10;` shown as ⏎) | Tab stops (`defaultValue`) | Context | Req |
  |--------|------------------------------------------|----------------------------|---------|-----|
  | `if`   | `if $COND$ then⏎    $END$⏎end` | COND | LUA_CODE | 02 |
  | `ifel` | `if $COND$ then⏎    $END$⏎else⏎    ⏎end` | COND | LUA_CODE | 03 |
  | `lfun` | `local function $NAME$($ARGS$)⏎    $END$⏎end` | NAME, ARGS | LUA_CODE | 04 |
  | `while`| `while $COND$ do⏎    $END$⏎end` | COND | LUA_CODE | 05 |
  | `repeat`| `repeat⏎    $END$⏎until $COND$` | COND | LUA_CODE | 06 |
  | `forip`| `for $I$, $V$ in ipairs($T$) do⏎    $END$⏎end` | I=i, V=v, T=t | LUA_CODE | 07 |
  | `req`  | `local $NAME$ = require("$MODULE$")` | NAME, MODULE | LUA_CODE | 08 |
  | `mod`  | `local $M$ = {}⏎⏎$END$⏎⏎return $M$` | M=M | LUA_CODE | 09 |

  Notes: `ifel` places `$END$` in the `then` branch and leaves an empty indented line in the `else`
  branch (no second stop, matching EmmyLua `ifelse`). `mod` exposes one stop `$M$` (the module
  table) defaulting to `M`; `req`/`mod` ship with literal default names rather than smart macros
  (see risks-and-gaps DR for the smart-default question). `toReformat="true"` lets the formatter
  normalise the indentation, so the literal 4-space indents above are advisory.

### 2.3 Surround templates (COMP-07-11)
Surround-with templates are ordinary `<template>` entries whose `value` contains the platform's
built-in `$SELECTION$` variable; the IDE offers them under **Code ▸ Surround With…** (Ctrl+Alt+T)
when there is a selection. They need a context that returns true *on a selection*.

- **Context**: a dedicated `LUA_SURROUND` option. Reuse `LuaCodeContextType` would also work (a
  selection's start leaf is in code), but a separate `LuaSurroundContextType`
  (`("LUA_SURROUND", "Lua (surround)")`, parent `LUA`) keeps the surround set out of the
  abbreviation-completion popup. Its predicate is the §3.1 code check evaluated at the selection
  start (the platform only consults surround contexts when a selection exists, so no extra
  `hasSelection` test is required).
- **Templates** (all `<context><option name="LUA_SURROUND" value="true"/></context>`):

  | Name | `value` (⏎ = `&#10;`) | Req |
  |------|------------------------|-----|
  | `if` (surround) | `if $COND$ then⏎    $SELECTION$⏎end` | 11 |
  | `for` (surround) | `for $K$, $V$ in pairs($T$) do⏎    $SELECTION$⏎end` | 11 |
  | `do` (surround) | `do⏎    $SELECTION$⏎end` | 11 |
  | `function` (surround) | `function $NAME$($ARGS$)⏎    $SELECTION$⏎end` | 11 |

  Because template *names* are unique within a group, the surround `if`/`for` use distinct `name`
  attributes from the insertion `if`/`forp` — e.g. `surr_if`, `surr_for`, `surr_do`, `surr_fn` — and
  carry a `description` so the Surround-With menu labels them clearly. `$SELECTION$` is a reserved
  platform variable, not declared with `<variable>`.

## 3. Algorithms

### 3.1 `LuaCodeContextType.isInContext` — string/comment/number exclusion (COMP-07-10)
Goal: return `false` when the caret sits inside a string, comment, or numeric literal; `true`
otherwise (when also inside a `LuaFile`).

**Grounded token sets** (all already defined — reuse, do not re-declare):
- Comments: `LuaSyntax.CommentTokens` (`src/main/kotlin/.../lang/syntax/LuaSyntax.kt:34`) =
  `{ LuaElementTypes.SHORTCOMMENT, LuaElementTypes.LONGCOMMENT, LuaElementTypes.SHEBANG,
  LuaLazyElementTypes.LUACATS_COMMENT }`.
- Strings: `LuaSyntax.StringLiteralTokens` (`LuaSyntax.kt:42`) = `{ LuaElementTypes.STRING }`.
- The lexer also emits long-string and number leaves named in
  `src/main/java/.../lang/lexer/LuaTokenTypes.java`: `LONGSTRING` (`:75`), `LONGSTRING_BEGIN`/`_END`
  (`:77-78`), `NUMBER` (`:68`); and the parser element `LuaElementTypes.NUMBER`
  (`src/main/gen/.../psi/LuaElementTypes.java:107`). Long-string content is **not** in
  `StringLiteralTokens`, so it is added explicitly below.

Define a private suppression set once (companion `val`, computed at class-init so the singleton
`IElementType`s are referenced after registration, per the CLAUDE.md registry-size lesson):

```kotlin
private val SUPPRESS: TokenSet = TokenSet.orSet(
    LuaSyntax.CommentTokens,
    LuaSyntax.StringLiteralTokens,
    TokenSet.create(
        LuaTokenTypes.LONGSTRING,
        LuaTokenTypes.LONGSTRING_BEGIN,
        LuaTokenTypes.LONGSTRING_END,
        LuaTokenTypes.NUMBER,
        LuaElementTypes.NUMBER,
    ),
)
```

Algorithm:
1. If `templateActionContext.file !is LuaFile` → return `false`. (Redundant with the `LUA` parent
   gate, but keeps the predicate self-contained.)
2. `val offset = templateActionContext.startOffset`.
3. `val leaf = file.findElementAt(offset) ?: file.findElementAt(offset - 1)` — at end-of-token the
   element at `offset` can be the *next* leaf or `null`; fall back one char left so a caret at the
   close quote of `"abc"` still resolves inside the string.
4. `if (leaf == null) return true` (empty file / pure whitespace ⇒ treat as code).
5. `val type = PsiUtilCore.getElementType(leaf)` (`com.intellij.psi.util.PsiUtilCore`).
6. Return `type !in SUPPRESS` **and** none of `leaf`'s ancestors is a comment/string/number element:
   walk `leaf.parents(false)` (or a manual `parent` loop) and if any ancestor's element type is in
   `SUPPRESS`, return `false`. (Handles long-string/long-comment *content* leaves whose own type may
   be whitespace/newline but whose parent is `LONGSTRING`/`LONGCOMMENT`.)
7. Otherwise return `true`.

`LuaIfContextType` (optional, §2.1.3) calls this same predicate first, then additionally requires a
`LuaIfStatement` ancestor.

### 3.2 Expansion
Expansion itself has no custom algorithm — it is data-driven by the platform live-template engine
from `lua.xml` (insertion) / the Surround-With action (`$SELECTION$`).

## 4. External Data & Parsing
The only "external data" is the bundled `lua.xml`, which is our own resource parsed by the platform
(`DefaultLiveTemplatesProvider`). Format is the IntelliJ live-template XML schema (§2.2). No
runtime CLI/text parsing.

## 5. Data Flow
### 5.1 Insertion: `if`⇥ in code
User types `if` + Tab in a `.lua` file, caret in real code → `LuaCodeContextType.isInContext` runs
§3.1 (leaf not in `SUPPRESS`) → returns true → platform expands the `if` template → caret lands on
`$COND$`, then `$END$` (the body).

### 5.2 Suppression: `forp`⇥ inside a string/comment
Caret inside `"forp"` or `-- forp` → `findElementAt` leaf type ∈ `SUPPRESS` (STRING / SHORTCOMMENT)
→ `isInContext` false → no Lua template offered (TC 6).

### 5.3 Surround: select N lines ▸ Ctrl+Alt+T ▸ `if`
Selection start leaf is code → `LuaSurroundContextType` true → Surround-With lists the four Lua
surround templates → choosing `if` wraps `$SELECTION$` in `if $COND$ then … end`, caret on `$COND$`.

## 6. Edge Cases
- Non-Lua file → all contexts false → templates inert.
- Caret at the boundary of a string token (the closing quote) → §3.1 step 3 left-fallback keeps it
  classified as string.
- Long string `[[ … ]]` / long comment `--[[ … ]]` content → §3.1 step 6 ancestor walk suppresses.
- Abbreviation collides with a real identifier prefix → standard platform behavior (Tab expands,
  other completions still available).
- Existing user-edited copies of the four shipped templates keyed on the old `LUA` option: because
  `LUA_CODE`'s parent is `LUA`, a template still bound to `LUA` continues to match; the bundled
  defaults move to `LUA_CODE` (see risks-and-gaps for the migration risk).

## 7. Integration Points
```xml
<!-- plugin.xml — extensions defaultExtensionNs="com.intellij"; currently lines 186-187 -->
<defaultLiveTemplates file="liveTemplates/lua.xml"/>
<liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType"/>
<!-- ADD: -->
<liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaCodeContextType"/>
<liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaSurroundContextType"/>
<!-- OPTIONAL (only with the parked elseif template): -->
<!-- <liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaIfContextType"/> -->
```
`<defaultLiveTemplates>` is unchanged — the same `lua.xml` file now carries the new `<template>`
blocks and the migrated `LUA_CODE`/`LUA_SURROUND` context options.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-07-01 Basic Templates (`fun`/`fori`/`forp`/`loc`) | M | §2.1.1, §2.2.1 | Built; context migrates `LUA`→`LUA_CODE` (§2.2.1). Reconciliation: `fun`≠`func`, `fori` is **numeric** (ipairs is `forip`, COMP-07-07) |
| COMP-07-02 `if` | M | §2.2.2 | Designed |
| COMP-07-03 `ifel` | M | §2.2.2 | Designed |
| COMP-07-04 `lfun` | M | §2.2.2 | Designed |
| COMP-07-05 `while` | M | §2.2.2 | Designed |
| COMP-07-06 `repeat` | S | §2.2.2 | Designed |
| COMP-07-07 `forip` | S | §2.2.2 | Designed |
| COMP-07-08 `req` | S | §2.2.2 | Designed |
| COMP-07-09 `mod` | S | §2.2.2 | Designed |
| COMP-07-10 Context refinement | S | §2.1.2, §3.1 (+ optional §2.1.3) | Designed; `LuaCodeContextType` is the string/comment defect fix; `LuaIfContextType` is optional/Could |
| COMP-07-11 Surround templates | S | §2.3, §5.3 | Designed via `$SELECTION$` + `LuaSurroundContextType` |

## 9. Alternatives Considered
- Programmatic templates vs. bundled XML: bundled `defaultLiveTemplates` is the platform-idiomatic
  way to ship read-only built-ins users can copy/edit; chosen over building `Template` objects in code.
- **Reuse `LUA_CODE` for surround** vs. a dedicated `LUA_SURROUND` context: a dedicated context keeps
  surround entries out of the abbreviation popup and lets the menu label them independently; chosen.
- **Drop the old `LUA` context** vs. keep it as parent: keeping it as the parent of `LUA_CODE`
  preserves existing user customizations and is the platform-standard nesting pattern; chosen.

## 10. Open Questions
_None — all open items are tracked as de-risking tasks in risks-and-gaps.md._
