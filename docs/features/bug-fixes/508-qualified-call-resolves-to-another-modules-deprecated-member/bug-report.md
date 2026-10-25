---
id: "BUG-508"
title: "`M.clear()` resolves to every file's `function M.clear`, including a library module's file-local `M`, so a deprecated library member flags a project's own function"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-508: a project's `M.clear()` is reported deprecated because Neovim's `vim.lsp.codelens` declares a deprecated `M.clear`

Found by [[TARGET-11]]'s prototype (DR-10). A bug report: reproduction executed, root cause not
investigated, no fix or test strategy yet. Does not block TARGET-11. `$S` =
`~/.cache/claude-scratch/lunar/target-11`.

## 1. Reproduction (executed)

Library root, laid out like Neovim `779c3696` `runtime/lua/vim/lsp/codelens.lua:4,507-510,528-532`:

```lua
-- vim/lsp/codelens.lua
local M = {}

---@deprecated
function M.clear(client_id, bufnr) end

---@deprecated
function M.save(lenses, bufnr, client_id) end

return M
```

Project files, `LuaDeprecatedApiInspection` enabled (shapes of lazy.nvim `85c7ff37`
`lua/lazy/manage/init.lua:39,194` and `lua/lazy/docs.lua:147`):

```lua
-- lazy/manage.lua                         -- lazy/docs.lua
local M = {}                               local M = {}
function M.clear(plugins) end              function M.save(opts) end
function M.run()                           function M.update()
  M.clear()                                  M.save({})
end                                        end
return M                                   return M
```

Probe `Target11R2DeprecatedProbeTest`, libvirt builder (`$S/r2/results/r1-base/`, and unchanged
with [[BUG-504]]'s fix applied, `$S/r2/results/r2-fixes/`):

```
library lazy/manage.lua:5 'clear' targets=[vim/lsp/codelens.lua:LuaFuncDeclImpl('function M.clear(cli') decl=M.clear deprecated=true, lazy/manage.lua:LuaFuncDeclImpl('function M.clear(plu') decl=M.clear deprecated=false]
library lazy/docs.lua:4 'save' targets=[vim/lsp/codelens.lua:LuaFuncDeclImpl('function M.save(lens') decl=M.save deprecated=true, lazy/docs.lua:LuaFuncDeclImpl('function M.save(opts') decl=M.save deprecated=false]
```

Control: the same tree without `@deprecated` reports no hits.

On lazy.nvim with the real Neovim library this is three `LuaDeprecatedApi` hits (DR-10).

## 2. Expected vs actual

- **Expected:** `M.clear()` in `lazy/manage.lua` resolves to that file's `function M.clear`, which
  is not deprecated: no warning.
- **Actual:** it multi-resolves to both files' `function M.clear`, and the library's deprecated one
  raises the warning.

## 3. Root cause

Not investigated. The targets above are consistent with `LuaNameReference`
(`src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt`) resolving a qualified name by
the receiver's text, together with a library module's file-local `local M` counting as a global `M`
(TARGET-11 DR-05: `local vim = {}` + `function vim.x()` declares `vim`). Neither is confirmed. The
second is ruled out for these hits: with [[BUG-509]]'s fix, which stops a `local` root being indexed
as a global, both hits stay (`[probe:b508] lazy/manage.lua hits=[64..69 'clear']`,
`lazy/docs.lua hits=[63..67 'save']`; `$S/r3/results/g3-proto-full/probe.txt`).
