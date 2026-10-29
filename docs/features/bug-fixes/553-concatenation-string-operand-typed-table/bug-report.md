---
id: "BUG-553"
title: "A string-valued operand typed `{ ... }` is reported by Suspicious Concatenation — 14 hits on lazy.nvim"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Sites

All 14 reproduce with each file opened **standalone in a light fixture, no library, Standard
target** — the neovim library and LuaJIT target are not involved. Grouped by what gives the
operand its `{ ... }`:

| Group | Site (`lua/lazy/`) | Operand | Table demands on the operand |
|-------|--------------------|---------|------------------------------|
| A — colon call before the `..` | `terminal.lua:63`, `:65` | `nextChar` (gsub callback param) | `nextChar:sub` |
| | `core/util.lua:80` | `home` (`vim.uv.os_homedir()`) | `home:sub` ×3 |
| | `core/util.lua:103` | `source` (`info.source:sub(2)`, then `vim.fn.fnamemodify(...) --[[@as string]]`) | `source:find`, `source:sub` |
| | `docs.lua:51`, `:53` | `content` (`M.fix_indent(...)`, `vim.trim(...)`) | `content:gsub` |
| | `health.lua:85` | `packs` (ipairs over `vim.fn.expand`) | `packs:find` |
| | `manage/task/plugin.lua:74` | `build` (ipairs over a `---@cast` array) | `build:sub`, `build:match` |
| | `minit.lua:60` | `name` (ipairs over a string-literal table) | `name:upper()` in the assignment target |
| | `view/render.lua:367`, `:370` | `icon` (`icon:gsub`), `key` (ipairs loop value) | `icon:gsub`; `key:sub` ×2 |
| B — two or more colon calls after the `..` | `core/cache.lua:451` | `name` (`uv.fs_scandir_next`) | `name:sub` ×4, all after line 451 |
| C — demand flows back from a callback | `core/util.lua:209` | `name` in `M.ls` (`vim.uv.fs_scandir_next`) | none in `M.ls`; `name:sub` ×2 in `M.walkmods`'s callback passed to `M.ls` |
| D — not reduced | `core/handler/keys.lua:57` | `keys.mode` (`---@param keys LazyKeys`, `@field mode? string`) | `keys.mode:sub(-1)` on another `keys` that is passed to `M.to_string` |

## 5. Root Cause

**One root cause, three triggers.** A value whose write type is `undefined` is typed by what is
demanded of it, and a colon call or field access demands a one-member structural `Table` that does
not admit `string` — although every string has a metatable whose `__index` is `string`, so
`s:sub()` is the normal way to call a string method.

- **The demand.** `methodSiteMember` emits `Table(localMembers = {method})` against the receiver
  (`LuaTypesVisitor.kt:74`); a dot access does the same (`LuaTypesVisitor.kt:1241`).
- **The pick.** `VariableElement.resolveRead` returns the merge of every `Table` demand when there
  are two or more, discarding every non-table demand — the `..` site's `Stringable` included —
  and otherwise the first demand in `downSet` order (`LuaTypeNodes.kt:251-253`).
- **The report.** `LuaTypesSnapshot.typeOf` returns `read` when `write` is `undefined`
  (`LuaTypes.kt:95-101`), so the inspection sees the demand as the operand's type.

Triggers, each executed in `LuaConcatMethodDemandOperandTest`:
- **A, first-wins:** one colon call before the `..` (`x:sub(1, 1)` then `"a" .. x`) is reported;
  the same call placed after the `..` is not (control).
- **B, merge-wins:** two colon calls after the `..` are reported regardless of order; one is not.
  Two field reads `x.a`, `x.b` after the `..` are reported too, one is not (probe, not committed).
- **C, back-flow:** `checkFunctionCompatibility` wires each call-site argument into the callee
  value's parameter (`LuaTypeGraph.kt:890`), so a callback parameter's colon call becomes a demand
  on the caller's argument. A single `n:sub(1)` in the callback reports the caller's operand,
  even though one direct colon call after the `..` does not; why one back-flowed demand outranks
  the `..` demand was not traced.

**Contributing, not causal:** the operand's write is `undefined` because the value is untyped:
`vim.uv = ...` in the neovim meta, `--[[@as string]]` on a call result (`util.lua:102`), an
ipairs value over `{ "config", ... }` (`minit.lua:60` — a `string[]` literal whose loop value is
still untyped), and over a `---@cast` array (`plugin.lua:74`). Each of these, once typed, would
mask the defect at its site rather than fix it.

**Not established:** group D. `keys.lua` reproduces when the whole file is opened standalone; two
reductions did not — the class declarations plus `M.to_string` alone, and a synthetic caller doing
`M.to_string(k)` with one and with two `k.mode:sub(...)` calls. It is likely the same cause reached
through the class member node, but that is unproven.

## 6. Fix Strategy

A demand on a value is a hypothesis about it, not evidence that it is a table.
- Make the colon-call demand admit `string` — a demand that a `string` (via its metatable) also
  satisfies, in the shape of the `Trait`s BUG-423 introduced — so `resolveRead` no longer reports
  `{ ... }` for a receiver only known to be colon-called.
- Stop `mergeTableDemands` from discarding non-table demands: when the demands disagree
  (`Stringable` plus `Table`), the read is not a table.
- Gate: flip the five `testCurrently…` tests in `LuaConcatMethodDemandOperandTest` to
  `assertNoWarning`; keep its controls (a real table still warns); re-record `lazynvim.baseline`
  (`inspection.LuaSuspiciousConcatenation` drops, ideally to 0) and check member completion
  inferred from usage (`x.` after `x:foo()`) for regressions.

One cause, so no split. If group D turns out to need a fix of its own, file it separately.
