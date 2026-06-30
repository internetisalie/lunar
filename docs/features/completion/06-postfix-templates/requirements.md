---
id: COMP-06
title: Postfix Templates Requirements
type: feature
parent_id: COMP
status: done
vf_icon: ✅
folders:
  - "[[features/completion/requirements|requirements]]"
---

# Postfix Templates Requirements

Scope expanded 2026-06-15 from a competitor/platform survey (EmmyLua, Luanalysis, and the
platform's language-agnostic postfix set). EmmyLua/Luanalysis ship a 15-template set; Lunar shipped
only `.if`. All additions below reuse the existing `LuaExpr`-keyed selector
(`LuaIfPostfixTemplate.kt`) and confirmed PSI — no new PSI work. `null`/ternary/`val`/class-language
postfixes are intentionally excluded (no Lua equivalent).

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-06-01 | `.if` Template | Must | done | Convert `expr.if` into `if expr then … end`. Built (`LuaIfPostfixTemplate`). |
| COMP-06-02 | `.not` Template | Must | done | Convert `expr.not` into `not expr`. _[EmmyLua `LuaIfNotPostfixTemplate`/`not`]_ |
| COMP-06-03 | `.var` Template | Must | done | Convert `expr.var` into `local name = expr`, with `name` an editable tab stop (string template; delegating to `LuaIntroduceVariableHandler` was rejected — see design §9). _[EmmyLua `LuaLocalPostfixTemplate`; mechanism per JetBrains `CastVarPostfixTemplate`]_ |
| COMP-06-04 | `.for` Template | Must | done | Convert `expr.for` into `for i = 1, expr do … end`. _[EmmyLua `LuaForAPostfixTemplate`]_ |
| COMP-06-05 | `.forp` Template | Must | done | Convert `expr.forp` into `for k, v in pairs(expr) do … end`. _[EmmyLua `LuaForPPostfixTemplate`]_ |
| COMP-06-06 | `.fori` Template | Must | done | Convert `expr.fori` into `for i, v in ipairs(expr) do … end`. _[EmmyLua `LuaForIPostfixTemplate`]_ |
| COMP-06-07 | `.ifnot` Template | Should | done | Convert `expr.ifnot` into `if not expr then … end` (statement guard; distinct from `.not`). _[EmmyLua `LuaIfNotPostfixTemplate`]_ |
| COMP-06-08 | `.nil` Template | Should | done | Convert `expr.nil` into `if expr == nil then … end` (Lua analog of platform `.null`). _[EmmyLua `LuaCheckNilPostfixTemplate`]_ |
| COMP-06-09 | `.notnil` Template | Should | done | Convert `expr.notnil` into `if expr ~= nil then … end` (Lua analog of `.nn`/`.notnull`). _[EmmyLua `LuaCheckIfNotNilPostfixTemplate`]_ |
| COMP-06-10 | `.return` Template | Should | done | Convert `expr.return` into `return expr`. _[EmmyLua `LuaReturnPostfixTemplate`; platform-universal]_ |
| COMP-06-11 | `.print` Template | Should | done | Convert `expr.print` into `print(expr)` (Lua's primary debug idiom). _[EmmyLua `LuaPrintPostfixTemplate`]_ |

## Backlog (Could / Watch — parked, not yet prioritized into the plan)
Surveyed and viable, deferred per scope decision (2026-06-15):
- `.par` → `(expr)` (Could; note: parenthesizing a call truncates multi-returns — a footgun).
- `.tostring` → `tostring(expr)`, `.tonumber` → `tonumber(expr)` (Could). _[EmmyLua `QuickCallPostfix`]_
- `.inc` → `expr = expr + 1`, `.dec` → `expr = expr - 1` (Could; Lua has no `++`/`--`; editable `value`). _[EmmyLua `LuaIncrease/DecreasePostfixTemplate`]_
- `.while` → `while expr do … end` (Could; platform-canonical, not in EmmyLua).
- `.assert` → `assert(expr)` (Watch; no competitor precedent).

## Test Cases

### Test Case 1: `.if` template (COMP-06-01)
**Input:** `isValid.if` **Action:** Tab → **Output:** `if isValid then\n    <caret>\nend` (wraps the **outermost** expression, e.g. `x > 5.if` → `if x > 5 then …`).

### Test Case 2: `.not` template (COMP-06-02)
**Input:** `ready.not` **Action:** Tab → **Output:** `not ready`

### Test Case 3: `.var` template (COMP-06-03)
**Input:** `getUser().var` **Action:** Tab → **Output:** `local user = getUser()` (name is an editable tab stop).

### Test Case 4: `.for` template (COMP-06-04)
**Input:** `count.for` **Action:** Tab → **Output:** `for i = 1, count do\n    <caret>\nend`

### Test Case 5: `.forp` template (COMP-06-05)
**Input:** `tbl.forp` **Action:** Tab → **Output:** `for k, v in pairs(tbl) do\n    <caret>\nend`

### Test Case 6: `.fori` template (COMP-06-06)
**Input:** `list.fori` **Action:** Tab → **Output:** `for i, v in ipairs(list) do\n    <caret>\nend`

### Test Case 7: `.ifnot` template (COMP-06-07)
**Input:** `ok.ifnot` **Action:** Tab → **Output:** `if not ok then\n    <caret>\nend`

### Test Case 8: `.nil` template (COMP-06-08)
**Input:** `x.nil` **Action:** Tab → **Output:** `if x == nil then\n    <caret>\nend`

### Test Case 9: `.notnil` template (COMP-06-09)
**Input:** `x.notnil` **Action:** Tab → **Output:** `if x ~= nil then\n    <caret>\nend`

### Test Case 10: `.return` template (COMP-06-10)
**Input:** `result.return` **Action:** Tab → **Output:** `return result`

### Test Case 11: `.print` template (COMP-06-11)
**Input:** `value.print` **Action:** Tab → **Output:** `print(value)`
