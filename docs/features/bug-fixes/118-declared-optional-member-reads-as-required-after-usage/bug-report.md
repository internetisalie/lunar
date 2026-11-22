---
id: "BUG-118"
title: "A declared optional field reads as required once the code calls a method on it, so `opts = opts or {}` is rejected"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-118: `ManagerOpts | { ... } is not assignable to ManagerOpts | nil` for a class whose fields are all optional

## Reproduction
Light fixture, reduced from lazy.nvim `lua/lazy/manage/init.lua` 18-27 (the finding is at 18, `function M.run`):

```lua
local M = {}

---@class ManagerOpts
---@field mode? string

---@param opts? ManagerOpts
function M.run(opts)
  opts = opts or {}
  local mode = opts.mode
  local event = mode and ("Lazy" .. mode:sub(1, 1):upper() .. mode:sub(2))
end
```

Rows on `bug-114-fix` (`883a3066`): `ERROR ManagerOpts | { ... } is not assignable to ManagerOpts | nil` at the
function, and HYPOTHESIS `.mode`. On main (`269b57cb`): the HYPOTHESIS `.mode` row only. Controls that give 0 rows:
`local event = "L" .. mode:sub(1, 1)`, `mode and mode:sub(1, 1):upper()`, and the read-only forms; the trigger is
the `..` chain with `:upper()` and a second `mode:sub(2)`. The full 68-line `M.run` gives the same row; it needs the
event block, the `plugins` block and the `show` block of the real file together (bisected by line ranges), and the
fixture above is the smallest form found.

## Expected vs Actual
- **Expected:** `{}` satisfies a class whose every field is optional, so `opts = opts or {}` reports nothing.
- **Actual:** the empty table is rejected for lacking `mode`. The use table's `mode` member is not optional even
  though `---@field mode? string` is.

## Root Cause
Printed from the union branch of `checkCompatibility` on the real file: the failing arm is the `{}` literal
(`keys=[]`), the use arm is `ManagerOpts` with members `[wait, clear, show, mode, plugins, concurrency, lockfile]`,
and the single missing member is `mode`. So the nil is again not the failing arm, and `mode` is read as required.
- A member's `read` is `LuaTypeNodes.VariableElement.resolveRead` (main `:307-320`): the uses reachable from the member
  node, in code order, first wins unless several are tables. A `Table` demand made by `mode:sub(...)` outranks and
  replaces what the declared `string?` would say, so `isOptional(read)` is false (`LuaTypeAlgebra.isOptional`).
  The same shape shows with a declared `---@field node? ObjR` read through `fin(node.node)`: the member prints as
  `node:{ ... }`, not optional, and the table path then reports `Missing required field 'node'` on the literal
  (ERROR, on main too).
- Confirmed by dumping the `down` set of the member node when the report is made. For `---@field node? ObjR` the
  member has two demands: the usage demand `node.node` (a `Table`, rank 13, the first the code makes) and the
  declaration's own converted demand (`LuaGraphType.Undefined` there, since `ObjR` is still being materialized
  when its own field is converted; `T|nil` for a field of another type), which `LuaDemandRanks` ranks last
  (rank 43). `resolveRead` (`LuaTypeNodes.kt`) picks the first-ranked demand, so the declared one is never
  consulted and the member reads `{ ... }`, which `isOptional` rejects. The required control reads `ObjR` and
  keeps reporting.
- Why the fix adds an ERROR: the pair judged against the final graph carries the declared `@param opts?` use
  (`ManagerOpts | nil`); main hid it (BUG-116).

## Fix Strategy
A declared member type must bound its member's `read`: when the member is declared `T?`, the read keeps the `nil`
arm whatever usage demands are merged in, as BUG-552 did for `?` parameters (`LuaTypeAlgebra.parameterDemand`).
BUG-115's rule would not cure this alone, because `ManagerOpts` is an exact declared class.

## Fix
`LuaTypeNodes.VariableElement.resolveRead` passes the picked demand through `keepDeclaredOptional`: when the
variable has a converted class-member demand (`LuaDemandRanks.isDeclaredMember`) that is optional, the read gains a
`nil` arm. Signature slots are left to BUG-552's `parameterDemand`.

## Test Strategy
`LuaOptionalFieldDemandTest`: the fixture above reports nothing; the `---@field node? ObjR` form read through a
helper reports no `Missing required field`; a required field (`---@field mode string`) still reports it.

Implemented in `LuaOptionalFieldDemandTest`: the `node? ObjR` form read through a helper, and the required-field
control. The `ManagerOpts` fixture reports nothing on main already (BUG-116 hides the pair) and is kept as a guard.
