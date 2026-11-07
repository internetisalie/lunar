---
id: "BUG-45"
title: "Routes that report a wrong argument or return at most sites stay silent at a few"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-45: Routes that report a wrong argument or return at most sites stay silent at a few

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction

Corpus sites only; each pair is one route, the left silent and the right reported:

| Silent | Reported |
|---|---|
| luarocks `src/luarocks/cmd.lua:219` `os.exit(exitcode or …)` → `os.exit("audit")` | luacheck `src/luacheck/main.lua:21` `os.exit(exit_codes.critical)` → `os.exit("audit")` |
| luarocks `src/luarocks/core/sysdetect.lua:490` `table.insert(files, 1, interp)` → `table.insert(files, true, interp)` | luacheck `spec/samples/argparse-0.2.0.lua:376` `table.insert(buf, 1, …)` → `table.insert(buf, true, …)` |
| lazynvim `lua/lazy/manage/lock.lua:14` `assert(io.open(Config.options.lockfile, "wb"))` → `io.open(true, "wb")` | luacheck `spec/cache_spec.lua:44` `os.remove(filename)` → `os.remove(true)` |
| lazynvim `lua/lazy/manage/git.lua:93` `return branch` in `---@return string?` → `return true` | lazynvim `lua/lazy/core/util.lua:70` `return ret` → `return true` |

## 2. Expected vs Actual Behavior
- **Expected**: the same diagnostic at every site of a route that is checked.
- **Actual**: silent at the sites in the left column. Audit: 9 silent of 54 measured sites across these
  four cells.

## 3. Context / Environment
- **Lua Version**: luarocks and luacheck pinned to their manifest targets; lazynvim LuaJIT 2.1.
- **Relevant Files**: not established. Design §3.4 of MAINT-54 notes one candidate it does not test:
  `LuaTypeGraph.addError` drops a repeated element and message, so an earlier `HYPOTHESIS` can shadow the
  injected `ERROR`.
- **Other Notes**: Cells: `arg.optional`, `arg.primitive`, `arg.union` × `LIBRARY` `MEMBER`;
  `return.mismatch` × `SAME_FILE` `MEMBER`.
