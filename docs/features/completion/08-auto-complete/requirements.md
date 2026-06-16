---
id: COMP-08
title: Auto Complete Requirements
type: feature
parent_id: COMP
status: in_progress
---

# Auto Complete Requirements

Scope expanded 2026-06-15 from a competitor/platform survey (EmmyLua, EmmyLua2, lua-for-idea, and
platform EPs). The shipped `LuaEnterHandler` covers `then`/`do`/`function`/`repeat` but has a real
**correctness bug** (COMP-08-02) and lacks the smart-indent / reformat behaviors the reference
plugins provide. `LuaPairedBraceMatcher` already declares the keyword pairs as structural and can be
reused as the opener→terminator source of truth.

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-08-01 | Block Auto-close | Must | done | Insert `end` (or `until`) on Enter after a block starter (`then`/`do`/`function`/`repeat`). Built (`LuaEnterHandler`). |
| COMP-08-02 | Balance check before insert | Must | planned | **Bug fix.** Before inserting `end`/`until`, walk the enclosing block and **skip if a matching terminator already exists** — never insert a redundant `end`. Today the `LuaBlock` parent is looked up but unused. _[EmmyLua `LuaEnterAfterUnmatchedBraceHandler` `findChildByType(endType)==null`]_ |
| COMP-08-03 | Full opener coverage | Should | planned | Cover all Lua block openers — `if…then`, `while…do`, numeric/generic `for…do`, bare `do`, `function`, `repeat…until`, and **table-literal `{`→`}`** Enter-completion. _[EmmyLua `getEnd()` maps `TABLE_EXPR`→`RCURLY`]_ |
| COMP-08-04 | Between-pair smart indent | Should | planned | When Enter is pressed between an already-matched opener and its terminator, smart-indent the new blank line to the nested level **without** inserting a terminator. _[EmmyLua `LuaEnterBetweenRangeBlockHandler`]_ |
| COMP-08-05 | Reformat + caret placement | Should | planned | After inserting the terminator, reformat the new block range and place the caret on the correctly-indented body line (not rely on `DefaultForceIndent` alone). _[EmmyLua `…postProcessEnter` `adjustLineIndent`]_ |

## Backlog (Could / Watch — parked, not yet prioritized into the plan)
- **Smart-Enter / Complete-Statement** (Ctrl+Shift+Enter): complete a partial `if`/`while`/`for`/`function` (add missing `then`/`do`/`end`) and move caret into the body, via `com.intellij.lang.smartEnterProcessor` (`SmartEnterProcessorWithFixers`). _[lua-for-idea `LuaSmartEnterProcessor`]_ (Could — new wiring).
- **Typed-`end` reindent**: re-align a manually typed `end`/`until` to its matching opener. _[EmmyLua2 `LuaEnterHandlerDelegate`]_ (Could).
- **Settings toggle** (default on) to disable auto-close. _[EmmyLua `LuaSettings.isSmartCloseEnd`]_ (Could).

## Test Cases

### Test Case 1: Enter after `then` (COMP-08-01)
**Input:** `if true then<caret>` **Action:** Enter →
```lua
if true then
    <caret>
end
```

### Test Case 2: No redundant `end` (COMP-08-02)
**Input:**
```lua
if true then<caret>
end
```
**Action:** Enter → caret indents onto a new body line; **no second `end` is inserted** (the block is already balanced).

### Test Case 3: Table-literal completion (COMP-08-03)
**Input:** `local t = {<caret>` **Action:** Enter →
```lua
local t = {
    <caret>
}
```

### Test Case 4: Between-pair indent (COMP-08-04)
**Input:** caret between an existing `function f()` and its `end` (block already balanced). **Action:** Enter → new blank line indented to the body level; no terminator inserted.

### Test Case 5: Reformat + caret placement (COMP-08-05)
**Input:** `while x do<caret>` at indent level 1 (inside an outer block). **Action:** Enter →
```lua
    while x do
        <caret>
    end
```
Both the body line and the inserted `end` are indented to the correct nested level (not left at column 0), and the caret rests on the indented body line.

### Test Case 6: Other openers — while / for / do / repeat (COMP-08-03)
**Input/Action/Output:**
- `while x do<caret>` + Enter → `while x do⏎    <caret>⏎end`
- `for i = 1, 10 do<caret>` + Enter → `for i = 1, 10 do⏎    <caret>⏎end`
- `do<caret>` + Enter → `do⏎    <caret>⏎end`
- `repeat<caret>` + Enter → `repeat⏎    <caret>⏎until` (terminator is `until`, not `end`)
