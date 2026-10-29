---
id: "BUG-564"
title: "A local declared without a value and assigned inside a `while` loop is reported as an unused local, though it is read"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-564: A local assigned inside a `while` loop is reported as unused, though it is read

Found while triaging the corpus movement of [[BUG-554]]: removing a false *Too few arguments* error exposed two hits that the error had been hiding. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`IndexedBasePlatformTestCase`), `myFixture.enableInspections(LuaUnusedLocalInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on the `bug-554` branch with a throwaway probe test (not committed).

```lua
local function f(line, col, pattern, handler)
  local from = 1
  local to, url
  while from do
    from, to, url = line:find(pattern, from)
    if from and col >= from and col <= to then
      return handler(url)
    end
    if from then
      from = to + 1
    end
  end
end
return f
```

## 2. Expected vs Actual
- **Expected**: no diagnostic. `to` is read at `col <= to` and `to + 1`, and `url` is read at `handler(url)`.
- **Actual**: `Unused local variable 'to'` and `Unused local variable 'url'`, both on line 3.
- **Control**: `local a, b` / `a, b = 1, 2` / `print(a, b)` reports nothing, so a multiple assignment alone does not cause it.

## 3. Context
- **Corpus effect**: 2 hits in `lazynvim.baseline` (`symbol.LuaUnusedLocal.to`, `symbol.LuaUnusedLocal.url`), at `lua/lazy/view/init.lua:307`, inside `M:on_pattern`.
- **Why they appeared only now**: before [[BUG-554]], the enclosing call `self:on_key(key, function() … end)` at `init.lua:300` had a false *Too few arguments* error. `LuaTypeAssignability` is registered at level `ERROR`, and the platform hides a lesser-severity highlight inside an ERROR's range (`UpdateHighlightersUtil.isWarningCoveredByError`). Once that error went away, these hits became visible. They reproduce in isolation, without any method call.
