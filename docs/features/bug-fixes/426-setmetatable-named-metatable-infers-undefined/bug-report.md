---
id: "BUG-426"
title: "`setmetatable(t, mt)` infers `Undefined` whenever the metatable is a named variable"
type: "bug"
parent_id: "BUG"
status: "done"
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

## Fixed (2026-08-07)

The cause is **polarity**, not metatable resolution. `V.__index = V` goes through `visitIndexExpr`,
which records the member as a *demand* on `V` (`graph.use`) and never touches its `write`.
`handleSetMetatable` consulted only `write`, so a named metatable's type was the bare `{}` it was
declared with, `__index` was nowhere in it, and the call bailed. An inline table literal worked
because `visitTableConstructor` puts members in `localMembers` — on the write side.

Three changes:

- **`mergedTableOf`** — an argument's table type merges what is written to it with what is demanded
  of it. This is the view `LuaTypesSnapshot` already reports for a variable, so it is what the rest
  of the IDE was seeing; a genuine write wins over a demand on the same key.
- **`indexTableOf` resolves through the member's `upSet`**, not its `write`. `write` flattens to the
  source's write type, which for `V.__index = V` is again `V`'s bare `{}` — so members would have
  been dropped a second time, one level down.
- **A metatable with no resolvable `__index` no longer bails.** `setmetatable` returns its first
  argument regardless, so the table stays typed rather than collapsing to `Undefined`.

### It unblocked BUG-424, and that arm landed with it

`LuaOperatorTraitTest.testMetamethodTablesAreUntypedToday` went red exactly as designed. Leaving it
there would have shipped a *new* false-positive class — every metamethod-carrying table erroring at
its own operators — so the metamethod arm was built in the same change: `LuaGraphType.Table` gained
`metamethods`, populated from the metatable, and `Trait.metamethods` is consulted by
`implementsOperator`. BUG-424 is closed by this.

Implementing it surfaced the trap BUG-423 predicted verbatim: `VariableElement.resolveRead` was
projecting the trait to its primitive, so a value reaching an operator **through a variable hop**
was checked against `number` rather than `Numberable` and never reached the metamethod arm — the
same shape as BUG-419's `declaredDemand` defect, one axis over. The projection moved to the
presentation boundary (`LuaTypes.typeOf` and `graphTypeToLuaType`, which both inlay-hint providers
convert through). The graph now sees demands; only the IDE sees primitives.

### Corpus — and the prediction in this report was wrong

This report predicted the re-baseline would **rise**, on the reasoning that newly-typed values
produce new diagnostics, and said to sample them before accepting. It fell:

| member | `LuaTypeAssignability` | `LuaReturnTypeMismatch` |
| :-- | --: | --: |
| zerobrane | 338 → **323** | 62 → 59 |
| penlight | 103 → **91** | 21 → 17 |
| luarocks | 196 → 196 | unchanged |
| luacheck | 213 → 213 | unchanged |

Checked for a rise hidden under a larger fall: the per-symbol maps are **byte-identical** across all
four members, and `LuaUndeclaredVariable` did not move. So this is a net removal, not a churn.

The metamethod arm is why. Typing these values does create new checks, but the same change taught
the engine that a table implementing an operator satisfies it — and correctly typed values also stop
falling into the other error paths they used to.

## Known limitation

Metamethods are recorded only from `setmetatable`. A `---@class` declaring `__add` as a field does
not make its instances arithmetic-capable, and neither does assigning `__add` to a table that is
never installed as a metatable — which is correct for the second case and a gap for the first.
