---
id: "BUG-507"
title: "A table reached through a field offers no members — a `@type`-annotated field, a field bound to `require` of an unannotated module, and a nested table constructor all complete empty"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-507: `M.typed.`, `M.req.` and `M.ctor.inner.` complete nothing

Found by [[TARGET-11]]'s prototype (DR-04). A bug report: reproduction executed, root cause not
investigated, no fix or test strategy yet. Does not block TARGET-11. `$S` =
`~/.cache/claude-scratch/lunar/target-11`.

## 1. Reproduction (executed)

Two files, registered once as a library root and once as project files:

```lua
-- ctl.lua
M = {}
---@class Thing
local Thing = {}
function Thing.go() end
---@type Thing
M.typed = nil
M.req = require('ctlmod')
M.ctor = { inner = { X = 1 } }
```

```lua
-- ctlmod.lua
local C = {}
function C.run() end
return C
```

Probe `Target11ShapeProbeTest` (`$S/probe-code/`), libvirt builder, `test --rerun --no-build-cache
--tests '…Target11ShapeProbeTest'` (`$S/results/run7-shapes/shapes.txt`), identical in both arms:

| Caret | Offered | Expected |
| :-- | :-- | :-- |
| `M.<caret>` (control) | `[ctor, req, sub, typed]` | the same |
| `M.typed.<caret>` | `[]` | `go` |
| `M.req.<caret>` | `[]` | `run` |
| `M.ctor.<caret>` | `[]` | `inner` |
| `M.ctor.inner.<caret>` | `[]` | `X` |
| `local t = require('ctlmod')` / `t.<caret>` | `[]` | `run` |

Not established: whether the last row is the same defect, or a property of this fixture's module
resolution. A library module that declares `---@class` does complete through `require`
(`LuaLibraryModuleResolutionTest.testMembersOfALibraryClassComplete`).

## 2. Why it matters

Neovim declares most of its namespaces in these shapes (`runtime/lua` at `779c3696`):
`---@type uv` / `vim.uv = ...` and the `vim.<name> = require('vim.<name>')` lines in
`vim/_meta.lua`, most modules an unannotated `local M = {}`; and `vim.log = { levels = … }` in
`vim/_core/editor.lua`. With the Neovim
library enabled, `vim.uv.`, `vim.fs.`, `vim.lsp.`, `vim.diagnostic.`, `vim.keymap.`,
`vim.treesitter.`, `vim.health.`, `vim.loader.` and `vim.log.levels.` offer nothing
(`$S/results/run8-split/complete.txt`).
