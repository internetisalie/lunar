---
id: "BUG-11"
title: "An optional union parameter type collapses to nil"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-11: An optional parameter whose type is a union with a table literal collapses to `nil`

Found while triaging [[BUG-566]]'s corpus run. Observed only; root cause not investigated. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/view/float.lua:222-225` declares

```lua
---@param opts? vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
function M:on(events, fn, opts)
```

and `view/init.lua:76-81` calls `self:on({ "User" }, function() … end, { pattern = pattern })`.

## 2. Expected vs Actual
- **Expected**: `{ pattern = pattern }` is checked against the union (or accepted if `vim.api.keyset.create_autocmd` does not resolve), never against `nil` alone.
- **Actual**: *{ ... } is not assignable to nil*. 5 hits with [[BUG-566]]'s fix: init.lua:81 confirmed from source; the other 4 were attributed to this family by their target type without tracing their call sites; hypotheses only on `main`.

## 3. Context
- Not established: which part collapses: the unresolved `vim.api.keyset.create_autocmd`, the `false` literal type in `{buffer: false}`, or the `?` combined with a union.
