---
id: "BUG-43"
title: "A method called on a possibly-nil stdlib value (`io.open`'s file) is not checked"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-43: A method called on a possibly-nil stdlib value (`io.open`'s file) is not checked

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
local fd = io.open('x')
fd:seek(true)          -- only HYPOTHESIS ':seek' File | nil is not assignable to { ... }
fd:seek('set', true)   -- nothing beyond that
local d = fd:read(true)
```
Corpus sites (audit edit, silent): luarocks `src/luarocks/core/sysdetect.lua:196`
`fd:seek("set", hdr.e_shoff + …)` → `fd:seek("set", true)`; luarocks `src/luarocks/tools/zip.lua:385`
`zh:read(file_name_length)` → `zh:read(true)`.

## 2. Expected vs Actual Behavior
- **Expected**: the argument is checked against `File:seek`/`File:read`'s `@param` (and a nil-receiver
  warning is a separate matter).
- **Actual**: the only error is the `HYPOTHESIS` about the receiver, which the inspections drop, so the
  call shows nothing. Audit: 0 of 12 measured `LIBRARY` `METHOD` sites reported.

## 3. Context / Environment
- **Lua Version**: any; `File` is declared in the bundled `io.lua` stubs.
- **Relevant Files**: not established.
- **Other Notes**: Cells: `arg.optional`, `arg.union`, `arity.tooMany` × `LIBRARY` `METHOD`.
