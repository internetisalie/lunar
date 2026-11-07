---
id: "BUG-44"
title: "Assigning a wrong value to a `@field` of a class is reported only as a hypothesis"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-44: Assigning a wrong value to a `@field` of a class is reported only as a hypothesis

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
---@class Fa
---@field b boolean
local Fa = {}
---@type Fa
local f = Fa
f.b = 'audit'                          -- HYPOTHESIS only: string is not assignable to boolean
function Fa:x() self.b = 'audit' end   -- HYPOTHESIS only
```
Corpus sites (audit edit, silent): lazynvim `lua/lazy/async.lua:80` `self._suspended = true` →
`self._suspended = "audit"`; lazynvim `lua/lazy/manage/process.lua:120` `self.timedout = true` →
`self.timedout = "audit"`.

## 2. Expected vs Actual Behavior
- **Expected**: *string is not assignable to boolean* at the assigned value, as an `ERROR`/`WARNING`.
- **Actual**: the mismatch is computed but only at `HYPOTHESIS` tier, so neither inspection shows it.
  Audit: 0 of 4 measured `field.assign` sites reported (the 4 `PROJECT_FILE` sites were skipped on a pre-existing error).

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: not established.
- **Other Notes**: the probe also reports `ERROR '{}' Missing required field 'b'` on `local Fa = {}` and
  `ERROR 'local f = Fa' { ... } | Fa is not assignable to Fa`, both on the class declaration idiom; they are
  separate from this report. Cell: `field.assign` × `SAME_FILE` `FIELD`.
