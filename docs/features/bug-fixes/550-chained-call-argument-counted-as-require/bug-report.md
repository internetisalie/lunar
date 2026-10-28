---
id: "BUG-550"
title: "The string argument of a call chained on `require(…)` is recognised as a require: `require(\"lazy.stats\").track(\"LazyDone\")` requires `LazyDone`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-550: `require("a").f("text")` is counted as two requires, the second of `"text"`

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-06). A bug
report: root cause not investigated.

## 1. Reproduction (executed in MAINT-39 DR-06)

The corpus sweep's require tally, with a temporary dump of each recognised require and the file it
resolved to, over `lazy.nvim` `v11.17.5` (`85c7ff37`) `lua/`:

```lua
require("lazy.stats").track("LazyDone")                       -- lua/lazy/init.lua:116
require("lazy.core.util").warn("You need to set `vim.g.mapleader` **BEFORE** loading lazy")
                                                              -- lua/lazy/manage/reloader.lua:74 (and :81)
```

- `"LazyDone"` is recognised as a required module name and resolves to
  `lua/lazy/manage/init.lua`, the first `init.lua` in the fixture ([[BUG-500]]'s fallback).
- The two `warn(…)` messages are recognised as required module names and stay unresolved.

## 2. Expected vs Actual
- **Expected**: one require per statement — `lazy.stats`, `lazy.core.util`. The argument of the
  call chained on the `require(…)` result is an ordinary string.
- **Actual**: the chained call's string argument is also recognised as a require. Of the 202
  requires the sweep recognises, 5 are unresolved, two of them these `warn` messages.

## 3. Context
- **Committed evidence**: `src/test/resources/corpus/lazynvim.baseline` records `requires=202` and
  `unresolvedRequires=5`, the counts DR-06 tallied.
- **Not established**: whether the editor's require reference (Go to Declaration on the string)
  shares the recognition, or only the corpus tally does.
- **Corpus effect**: `requires` is identity-checked by the ratchet, so a fix re-records
  `lazynvim.baseline` in the fixing change.
