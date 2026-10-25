---
id: "BUG-505"
title: "Members declared as `function a.b.c()` never complete under `a.b.` — the receiver-member index drops every function name with two separators"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-505: `function M.sub.deep() end` contributes nothing to `M.sub.`

Found by [[TARGET-11]]'s prototype (DR-04). A bug report: reproduction and cause executed; no
reviewed fix or test strategy yet. Does not block TARGET-11. `$S` = `~/.cache/claude-scratch/lunar/target-11`.

## 1. Reproduction (executed)

One project file:

```lua
M = {}
M.sub = {}
function M.sub.deep() end
```

Consumer `M.sub.<caret>`. Probe `Target11ShapeProbeTest` (`$S/probe-code/`), libvirt builder,
`test --rerun --no-build-cache --tests '…Target11ShapeProbeTest'`:

| Tree | `M.sub.` offers | `vim.api.` offers (Neovim shape `vim.api = {}` + `function vim.api.nvim_buf_get_lines(buffer) end`) | Evidence |
| :-- | :-- | :-- | :-- |
| `cb05af0f` + the catalog entry and BUG-503 probe patch (run 7 reuses run 5's compiled main tree: `compileKotlin UP-TO-DATE`) | `[]` | `[]` | `$S/results/run7-shapes/shapes.txt` |
| the same + the split patch below | `[deep]` | `[nvim_buf_get_lines]` | `$S/results/run8-split-shapes/shapes.txt` |

Same in a library root and in a project file. On the real Neovim library, the patch takes `vim.api.`
from 0 to 181 offered names and `vim.fn.` from 0 to 458, covering every `vim.api.*` (40) and
`vim.fn.*` (23) name lazy.nvim uses (`$S/results/run5-library/coverage.txt` against
`$S/results/run8-split/coverage.txt`).

## 2. Cause (read, then confirmed by the patch)

`LuaReceiverMemberIndex.Indexer.split` (`src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaReceiverMemberIndex.kt:546-562`)
splits a function name at its **first** separator and returns null when the remainder still holds
one, so `vim.api.nvim_buf_get_lines` is never indexed. Assignments were fixed for the same shape by
BUG-430 (`dottedTarget`, `:521-540`, keys `Foo.bar.baz = 1` under `Foo.bar`); function
declarations were not.

The probe patch (`$S/probe-code/probe.diff`) splits at the **last** separator (`lastIndexOf`,
`maxOf`) and bumps the index version from 5 to 6. It is a measurement, not a reviewed fix: the
method-on-nested-receiver form `function a.b:c()` and the existing callers of `split` are unexamined.

## 3. Expected vs actual

- **Expected:** `M.sub.` offers `deep`.
- **Actual:** it offers nothing.
