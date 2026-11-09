---
id: "BUG-74"
title: "A class method reads as fun() | fun(a), so a correct call is reported as too many arguments"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-74: A class method reads as fun() | fun(a), so a correct call is reported as too many arguments

Found by MAINT-55 DR-07 (2026-11-09), which switched arity checking against undeclared signatures on (LuaLS's `redundant-parameter` rule). 113 of the 129 new reports read "expected at most 0" because of this defect, not the rule. The shipped engine hides it, because arity against an undeclared signature is not reported.

## 1. Reproduction
```lua
---@class T
local t = {}
function t.f(a) end
t.f(1, 2)
```
Corpus site: `lazynvim/lua/lazy/core/handler/event.lua:82`, `M.get_state(ev.event, ev.buf, ev.data)` against `function M.get_state(event, buf, data)`.

## 2. Expected vs Actual Behavior
- **Expected**: `t.f` is `fun(a)`; `t.f(1, 2)` gets one too-many-arguments report against at most 1.
- **Actual**: `t.f` reads as `fun(a) | fun()`, and the call is reported twice: against `fun()` (at most 0) and against `fun(a)` (at most 1).
