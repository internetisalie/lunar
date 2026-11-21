---
id: "BUG-111"
title: "The else branch of a type(x) == \"table\" guard still holds the array members of x's type"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-111: The complement of a `table` type guard keeps `string[]`

Found while promoting HYPOTHESIS findings to ERROR (BUG-97 part 2): six `string | string[] is not
assignable to string` findings in lazy.nvim `lua/lazy/manage/process.lua` would have surfaced as wrong
ERRORs.

## 1. Reproduction
```lua
---@class LazyProcess
---@field cmd string
local Process = {}

---@param cmd string|string[]
function Process.new(cmd)
  local self = setmetatable({}, { __index = Process })
  if type(cmd) == "table" then
    self.cmd = cmd[1]
  else
    self.cmd = cmd -- cmd is a string here
  end
  return self
end
```

## 2. Expected vs Actual
- **Expected**: in the `else` branch `cmd` is `string`; no finding.
- **Actual**: `string | string[] is not assignable to string` on `self.cmd = cmd`. The same shape on a
  `---@type string` local reports it at ERROR severity.

## 3. Root Cause
Executed probe (`LuaTableGuardComplementTest`, red on base): the `else` branch's narrowed type is
`subtractType(originalNode.write, guard.narrowedType)` (`LuaTypesVisitor.injectNarrowedBinding`), which
reaches `LuaTypeAlgebra.subtractMember`, removing union members by `it == toRemove`. The guard's type is
the bare `LuaGraphType.Table()` (`TYPEOF_MAP["table"]`), and `string[]` is a distinct
`LuaGraphType.Array`, so it is never equal and stays in the complement. A class table or a table literal
member (`Table(className=...)`, non-empty `localMembers`) is likewise unequal to the bare table and
stays.

## 4. Fix Strategy
`LuaTypeAlgebra.isCoveredBy(member, guard)`: a member is covered when equal to the guard, or when the
guard is a `Table` and the member is a `Table` or an `Array`. `subtractMember` removes covered members.
A non-union original keeps the old equality rule, so a lone class is not narrowed to `undefined`.

## 5. Test Strategy
`LuaTableGuardComplementTest` (light fixture): the lazy.nvim shape; the same on a local; the `~=` form
(match branch gets the complement); an `elseif` chain; a class member and a table-literal member; and two
controls that must still report: a `number` guard leaves `string[]`, and a `table` guard leaves `number`.
Seven of eight are red on base.

## 6. Findings classification
Measured on the libvirt builder against `d07ba0af`.

| Artifact | Result | Class |
|----------|--------|-------|
| Type goldens (`types-golden`), shapes and member files | byte-identical | none |
| Conformance `results.json` | byte-identical | none |
| Type audit (`testLuacheckMember`) | identical to base at `d07ba0af` | none |
| Corpus baselines | unchanged (see suite below) | none |
| ERROR/WARNING lost or added | none | none |
