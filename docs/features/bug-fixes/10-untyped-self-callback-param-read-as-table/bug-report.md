---
id: "BUG-10"
title: "An untyped fun(self) parameter is read as a table type"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-10: An untyped `fun(self)` callback parameter is read as a table type

Found while triaging [[BUG-566]]'s corpus run. Observed only; root cause not investigated. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/view/float.lua:250-257`:

```lua
---@param fn fun(self?)
function M:on_key(key, fn, desc, mode)
  vim.keymap.set(mode or "n", key, function()
    fn(self)
```

Also `view/init.lua:297-319`, `---@param fallback? fun(self)` called as `fallback(self)`.

## 2. Expected vs Actual
- **Expected**: `fun(self)` declares one parameter named `self` with no type, so any argument is accepted.
- **Actual**: the argument is checked against a `{ ... }` table demand. 6 hits with [[BUG-566]]'s fix (float.lua:29, :255, :257; init.lua:23, :299, :319); hypotheses only on `main`.

## 3. Context
- Not established: whether `self` is parsed as a type name here, or the untyped parameter picks up the enclosing method's receiver.
