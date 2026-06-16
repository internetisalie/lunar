---
id: COMP-07
title: Live Templates Requirements
type: feature
parent_id: COMP
status: done
---

# Live Templates Requirements

Scope expanded 2026-06-15 from a competitor/platform survey. EmmyLua/Luanalysis ship ~11 templates
across **3 context types**; Lunar shipped 4 templates in a single `LUA` context that — a real defect
— also fires **inside strings and comments**. Additions below close that gap and add Lua-specific
wins (`repeat`, `pcall`, module skeleton) the competitors lack.

> **COMP-07-01 reconciliation:** the shipped abbreviations are `fun`/`fori`/`forp`/`loc`. The
> original requirement text said `func` (ship name is `fun`) and called `fori` an "ipairs loop" (it
> is **numeric**; pairs is `forp`). Treat the shipped names as canonical; ipairs iteration is now
> its own requirement (COMP-07-07).

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-07-01 | Basic Templates | Must | done | Shipped: `fun` (function), `fori` (numeric for), `forp` (pairs for), `loc` (local). _(see reconciliation note above)_ |
| COMP-07-02 | `if` template | Must | done | `if $COND$ then ⏎  $END$ ⏎ end`. _[EmmyLua `if`]_ |
| COMP-07-03 | `ifel` template | Must | done | `if $COND$ then ⏎  $END$ ⏎ else ⏎ end`. _[EmmyLua `ifelse`]_ |
| COMP-07-04 | `lfun` (local function) template | Must | done | `local function $NAME$($ARGS$) ⏎  $END$ ⏎ end`. _[EmmyLua/Luanalysis `lfunc`]_ |
| COMP-07-05 | `while` template | Must | done | `while $COND$ do ⏎  $END$ ⏎ end`. _[platform loop breadth; Lua core]_ |
| COMP-07-06 | `repeat` template | Should | done | `repeat ⏎  $END$ ⏎ until $COND$`. Lua-specific (no competitor analog). |
| COMP-07-07 | `forip` (ipairs for) template | Should | done | `for $I$, $V$ in ipairs($T$) do ⏎  $END$ ⏎ end`. Distinct from numeric `fori`. _[EmmyLua `fori`]_ |
| COMP-07-08 | `req` (require) template | Should | done | `local $NAME$ = require("$MODULE$")`. Standard import idiom. |
| COMP-07-09 | `mod` (module skeleton) template | Should | done | `local $M$ = {} ⏎⏎ $END$ ⏎⏎ return $M$`. Canonical post-`module()` pattern. |
| COMP-07-10 | Context-type refinement | Should | done | Replace the single `LUA` context with `LuaCodeContextType` (suppress inside string/comment/number) + a statement-aware `LuaIfContextType`. **Fixes templates firing inside strings/comments.** _[EmmyLua `LuaCodeContextType`/`LuaIfContextType`]_ |
| COMP-07-11 | Surround templates | Should | done | Surround-with `if` / `for` / `do…end` / `function` via `$SELECTION$` (Ctrl+Alt+T). _[platform `Java.xml`/`Kotlin.xml` surround pattern]_ |

## Backlog (Could / Watch — parked, not yet prioritized into the plan)
- `fn` anonymous closure → `function($ARGS$) $END$ end` (Could). _[EmmyLua `closure`]_
- `elseif` template gated to in-`if` context (Could; depends on COMP-07-10). _[EmmyLua `elseif`]_
- `pcall` wrapper → `local ok, err = pcall(function() ⏎  $END$ ⏎ end)` (Could; Lua error-handling idiom).
- Smart variable-name macros (`SuggestFirstLuaVarName`-style defaults) — needs a `Macro` subclass + `liveTemplateMacro` EP (Could).
- `class`/metatable boilerplate (Watch; framework-specific, no universal Lua convention).

## Test Cases

### Test Case 1: Basic templates (COMP-07-01)
**Input:** `fun`⇥ → `function $NAME$($ARGS$)\n    $END$\nend`. (`fori`/`forp`/`loc` likewise per `lua.xml`.)

### Test Case 2: `if` (COMP-07-02)
**Input:** `if`⇥ → `if $COND$ then\n    <caret>\nend`

### Test Case 3: `ifel` (COMP-07-03)
**Input:** `ifel`⇥ → `if $COND$ then\n    <caret>\nelse\nend`

### Test Case 4: `lfun` (COMP-07-04)
**Input:** `lfun`⇥ → `local function $NAME$($ARGS$)\n    <caret>\nend`

### Test Case 5: `while` (COMP-07-05)
**Input:** `while`⇥ → `while $COND$ do\n    <caret>\nend`

### Test Case 6: context suppression — string & line comment (COMP-07-10)
**Input:** caret **inside a string literal** `"forp"` or a `-- comment`. **Action:** type an abbrev + Tab.
**Expected:** no live-template expansion (templates inert in string/comment context).

### Test Case 7: context suppression — number literal (COMP-07-10)
**Input:** caret immediately after a number, e.g. `local x = 10<caret>` where the caret leaf is the `NUMBER` token. **Action:** type `if` + Tab.
**Expected:** no expansion (exercises the `NUMBER` member of the suppression `TokenSet`).

### Test Case 8: context suppression — long string / long comment (COMP-07-10)
**Input:** caret inside a `[[ long string ]]` or a `--[[ long comment ]]`. **Action:** type an abbrev + Tab.
**Expected:** no expansion (exercises the `LONGSTRING*`/`LONGCOMMENT` tokens + the ancestor-walk branch, since long-string content is not in `StringLiteralTokens`).
