---
id: "BUG-426"
title: "`setmetatable(t, mt)` infers `Undefined` whenever the metatable is a named variable"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-426: `setmetatable` with a named metatable produces no type

COMP-04-08 models `setmetatable(t, mt)` by adding `mt.__index`'s table as a supertype of `t`. It
works only when the metatable is written **inline as a table literal** — the form its own test uses,
and close to the only form real Lua does not use.

## Measured (2026-08-07)

```
local plain = {}                                     -> Table       <- typed
local i = setmetatable({}, { __index = { x = 1 } })  -> Table       <- typed (TC-05, the test's form)

local V = {}; V.__index = V
local i = setmetatable({}, V)                        -> Undefined
local i = setmetatable(base, V)                      -> Undefined
local V = {}                                         -- no __index at all
local i = setmetatable({}, V)                        -> Undefined
```

The `---@class`-annotated variant is `Undefined` too. `V` itself resolves fine
(`Table(localMembers={__index=…, __add=…})`), so the metatable is known — it is the *result* of the
call that is lost.

`handleSetMetatable` returns false in these cases and the call falls through to normal handling,
which produces nothing, so the value is `Undefined`. The exact bail-out was not isolated; both the
`as? LuaGraphType.Table` casts and `indexTableOf` are candidates.

## Why it matters — `Undefined` absorbs, so this looks like support

Every check against an `Undefined` value passes. So the idiomatic constructor pattern

```lua
local Account = {}
Account.__index = Account
function Account.new(o) return setmetatable(o or {}, Account) end
```

yields instances the type engine knows nothing about: no member checking, no assignability, no
completion from the metatable, and — this is the trap — **no diagnostics either**, which reads
exactly like "supported".

BUG-424 hit this from the other side: its probe found "Lunar reports 0 errors" on a table with
`__add` and correctly called that *not support*. This is the mechanism behind that reading.

## Blocks BUG-424

BUG-424's fix direction assumes "`LuaTypesVisitor` already walks `mt.__index` for COMP-04-08, so
metatable resolution exists". It exists for the literal form only. Until this is fixed there is no
typed table for an operator metamethod check to consult, so the metamethod arm of the trait system
(BUG-423, shipped) cannot be built *or tested* — a fixture for it would pass whether the arm worked
or not. `LuaOperatorTraitTest.testMetamethodTablesAreUntypedToday` pins the current behaviour and
goes red when this is fixed, which is the signal to build the arm.

## Verification

- The four shapes above type as `Table`, with the `__index` members reachable.
- `LuaTypeInferredCompletionTest`'s `TC-05` (the literal form) stays green.
- **Corpus re-baseline expected to rise**: this makes a large population of values typed for the
  first time, so new diagnostics will appear. Sample them before accepting the movement — that is
  the same posture BUG-425 records, and for the same reason.
