---
id: "BUG-424"
title: "Operator metamethods (`__add`, `__concat`, `__len`, `__call`) are unmodelled — latent false positives behind inference imprecision"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-424: Operator metamethods are unmodelled

The type engine models exactly one metamethod: `__index`, via `setmetatable` (COMP-04-08, which adds
the `__index` table as a supertype). It knows nothing about `__add`, `__sub`, `__mul`, `__concat`,
`__len`, `__call`, `__eq`, `__lt` or `__index` *as a function*.

So a table that defines an operator is, to the engine, just a table meeting a `Number` or `String`
demand.

## Measured

Lua accepts all three (identical on 5.4.7 and 5.5.0):

```lua
local V = {}; V.__index = V
V.__add    = function(a, b) return setmetatable({}, V) end
V.__concat = function(a, b) return "V" end
V.__len    = function(a) return 1 end
local function new(x) return setmetatable({x = x}, V) end
local a, b = new(1), new(2)
a + b     --> 3      (via __add)
a .. "s"  --> "V"    (via __concat)
#a        --> 1      (via __len)
```

```
t1 + t2  = 3
t .. "s" = V
#t       = 1
```

**Lunar reports 0 errors on the same source.** That is the important part, and it is *not* support.

## Zero errors is imprecision, not correctness

The engine is silent because `setmetatable({x = x}, V)` returned through a local function does not
resolve to a precise enough type to conflict with the operand demand — not because it consulted
`__add`. The gap is real and currently masked.

Evidence it is only masked: the BUG-419 survivor characterisation found a live `{ ... } -> number`
emission on luarocks. A table *does* conflict with an arithmetic demand once its type resolves. Every
improvement to inference precision converts more of this latent gap into visible false positives —
so this bug gets *worse* as the engine gets *better*, which is why it is worth recording now while it
costs nothing.

`#` is accidentally safe: `visitUnaryOpExpr` demands `String | Table | Array`, so any table passes
regardless of whether it defines `__len`. Permissive in the safe direction, and by accident rather
than design.

## Why low priority

Nothing is broken today — 0 false positives measured, 1 borderline emission corpus-wide. This is a
latent-risk record, not a live defect. It matters mainly as a *design input* to BUG-423: a trait
model (`Numberable`, `Stringable`, `Lengthable`) has a natural place to express "a table with
`__add` is Numberable", where a boolean `coercing` flag has none. If BUG-423 lands as traits, this
becomes an extension at a defined boundary; if it lands as a flag, this needs its own mechanism
later.

## Fix direction

Teach the operator demands about metamethods, most cheaply by resolving the operand's metatable
(the machinery exists — `LuaTypesVisitor` already walks `mt.__index` for COMP-04-08) and treating a
present `__add`/`__concat`/`__len` as satisfying the corresponding demand.

Order it **after** BUG-423, whose chosen shape decides where this belongs. Doing it first would
build a mechanism that the trait model may replace.

Not worth doing at all until inference is precise enough that the false positives actually appear —
at which point the corpus will say so, since `{ ... } -> <primitive>` pairs are exactly what the
BUG-419 survivor characterisation counts.
