---
id: "BUG-113"
title: "An io.open file is judged incompatible with the `{ seek = fun(p, p) }` demand its own :seek call creates"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-113: A `File` fails the `seek` demand of its own method call

## Reproduction
luarocks `src/luarocks/tools/zip.lua` (type corpus), around line 496:

```lua
local zh = io.open(zipfile, "rb")
if not zh then return end
-- ...
local okseek, errseek = zh:seek("set", cd_offset)
```

The `:seek` call demands `{ seek = fun(p, p) }` of `zh`, whose value is `File | nil`. In the final type
graph, `isCompatible(File, { seek = fun(p, p) })` returns false, both before and after BUG-97's cluster
collapse. A temporary trace recorded this in the BUG-97 report §8.

## Expected
`File` satisfies the demand: `file:seek([whence [, offset]])` is a method of every file handle, so
the call is valid Lua and produces no mismatch.

## Actual
The pair `File | nil` → `{ seek = fun(p, p) }` reports `File | nil is not assignable to { ... }`.
Today it is a HYPOTHESIS, so the inspection hides it, and it appears only in `luarocks-zip.golden`.

On main, this pair surfaces only when it is examined after the graph is final. Before BUG-97 part 2,
the examination order hid it. Any change that reorders pair examination can surface it, and if its
severity reaches ERROR, it becomes a wrong report.
