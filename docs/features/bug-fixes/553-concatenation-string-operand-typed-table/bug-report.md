---
id: "BUG-553"
title: "A string-valued operand typed `{ ... }` is reported by Suspicious Concatenation — 14 hits on lazy.nvim"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-553: `LuaSuspiciousConcatenation` fires on string operands the type engine infers as a table

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 design §3.2). A bug report: root
cause not investigated, and the sites are not triaged individually.

## 1. Reproduction

The corpus sweep on the MAINT-39 member, which is how the hits were observed:
`test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` after
`python3 tooling/corpus/fetch-corpus.py` (target LuaJIT 2.1, `neovim` library). The committed
`src/test/resources/corpus/lazynvim.baseline` records `inspection.LuaSuspiciousConcatenation=14`
and `symbol.LuaSuspiciousConcatenation.{ ... }=14`: every hit's operand type is `{ ... }`, identical
across MAINT-39 DR-12's arms with and without the library and target. Sampled sites
(`lua/lazy/`):

```lua
return "\r" .. nextChar           -- terminal.lua:63; nextChar is a gsub callback parameter
path = home .. path:sub(2)        -- core/util.lua:80; home = vim.uv.os_homedir()
local modpath = path .. "/lua/" .. name   -- core/cache.lua:451; name from uv.fs_scandir_next
```

**Not reproduced in isolation.** A light-fixture probe on `5dd675a4` (Standard 5.1 and LuaJIT
2.1, no library) gave no hit for a `gsub` callback parameter, an `os.getenv` result or a
`---@param name string` operand. A reproduction needs the inference path these sites take.

## 2. Expected vs Actual
- **Expected**: no hit. Each operand holds a string at run time.
- **Actual**: each operand is inferred as `{ ... }` (a table), so the concatenation is reported.

## 3. Context
- **Corpus effect**: a fix lowers `inspection.LuaSuspiciousConcatenation` in
  `lazynvim.baseline`, and the fixing change re-records it.
- **Not the same as [[BUG-541]]**, zerobrane's flickering `nil`-operand count.
