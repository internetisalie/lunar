---
id: "BUG-424"
title: "Operator metamethods (`__add`, `__mul`, `__pow`, `__concat`, `__len`) are unmodelled — the largest false-positive class"
type: "bug"
parent_id: "BUG"
priority: "high"
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

## Not latent — MEASURED as the dominant false-positive class

This report originally said "0 false positives, latent risk, low priority". **Sampling BUG-419's
survivors refutes that.** Of the 661 `string -> number` emissions on zerobrane, **655 are LPeg
pattern algebra**:

```
total=661   lpeg-context=655   other=6
```

```lua
local comment = lexer.token(lexer.COMMENT, '#' * lexer.nonnewline^0)
local word    = (lexer.alpha + '-') * (lexer.alnum + '-')^0
local n1b     = P('_') + '∆' + '⍙'
lex:add_rule('label', token(lexer.LABEL, word * ':'))
```

LPeg overloads `+` as ordered choice (`__add`), `*` as sequence (`__mul`), `^` as repetition
(`__pow`) and `-` as difference (`__sub`); each metamethod accepts a **string** operand and coerces
it to a pattern. zerobrane bundles Scintillua, so ~130 of its 325 Lua files are LPeg lexers and every
pattern definition in them trips this.

### Why the first probe read zero

The original probe built a table with `__add` and used it on **both** sides:

```lua
local a, b = new(1), new(2)
local sum = a + b        -- 0 errors
```

Both operands resolve to nothing precise, so no conflict is reachable — the silence was inference
imprecision, exactly as recorded. But that fixture is unrepresentative. Real metamethod code has a
**literal on one side** (`'#' * pattern`), and the literal is precisely typed, so *it* is the operand
that gets flagged. The defect was never latent; it was misfiled as string→number coercion because the
string is the side the message names.

The lesson generalises: a fixture where nothing resolves cannot demonstrate the absence of a
diagnostic that only appears when something does.

## Fix direction — this IS the trait work, not a follow-on

The dependency recorded when this was filed (`BUG-424 -> BUG-423`, "order it after") is **inverted**
by the sampling. BUG-423 is ~6 emissions; this is 655. Traits are the agreed direction (the type
engine design's own unbuilt roadmap item 6), so the metamethod arm is not an extension to add later —
it is the arm that carries the value:

```
NUMBERABLE  = number | numeric-string | <has __add/__sub/__mul/__div/__mod/__pow/__unm>
STRINGABLE  = string | number         | <has __concat>
LENGTHABLE  = string | table | array  | <has __len>
```

Without the metamethod arm, traits fix ~6 emissions on zerobrane. With it, ~661. Building the
primitive arms first and deferring metamethods would ship the version that does almost nothing.

**Do not "fix" this by widening the primitive demand.** Widening arithmetic to `number | string`
suppresses all 655 — because the operand that gets flagged happens to be a string — while modelling
nothing about LPeg and simultaneously blinding the engine to a genuine `"abc" * 2`. Right answer,
wrong reason, and it would have hidden this bug indefinitely. (That widening was tried under BUG-423
and reverted for an unrelated reason: it regressed every inferred hint.)

### Mechanism

`LuaTypesVisitor` already walks `mt.__index` for COMP-04-08, so metatable resolution exists. The work
is to consult it for the arithmetic/concat/length metamethods at the point a trait is checked, rather
than only for member lookup.

Two known hazards, both with precedent in this code:

- **The trait must never reach `resolveRead`.** The design says "as use-type heads" for exactly this
  reason; the reverted widening proved what happens otherwise (`n : number | string` on every
  numeric parameter).
- **It will likely need transitive resolution through `downSet`**, mirroring
  `VariableElement.resolveDeclaredDemand`. BUG-419 assumed the checked pair was (value, use-node)
  when it is frequently (value, **variable**), because `VariableNode` is itself a `UseNode`.

### Verification

The corpus is the gate: zerobrane's `LuaTypeAssignability` should fall by roughly the 655, and the
BUG-419 survivor characterisation re-run should show `string -> number` collapse without
`{ ... } -> <primitive>` rising. A unit fixture must use a **literal on one side**
(`'#' * pattern`) — the both-tables fixture that read zero above is not a valid regression test.

## Status (2026-08-07) — the trait system shipped; THIS ARM IS BLOCKED, and two claims above are wrong

BUG-423 landed the trait system — `Numberable`/`Stringable`/`Lengthable` as demand-only types, with
the metamethod arm left out. Attempting that arm produced two corrections to this report.

### 1. The metamethod arm cannot be built, because `setmetatable` produces no type

Filed as **BUG-426**. `setmetatable(t, mt)` types the result only when `mt` is an inline table
literal; with a named metatable — the form all real code uses, including this report's own measured
fixture — it infers `Undefined`:

```
local i = setmetatable({}, { __index = { x = 1 } })  -> Table       <- the COMP-04-08 test's form
local V = {}; V.__index = V
local i = setmetatable({}, V)                        -> Undefined
```

So the premise in *Mechanism* above — "`LuaTypesVisitor` already walks `mt.__index` for COMP-04-08,
so metatable resolution exists" — holds for the literal form only. There is no typed table for a
metamethod check to consult, and no fixture that could distinguish a working arm from a broken one:
every metamethod fixture written for this passed *before* any arm existed, because `Undefined`
absorbs every check. Building it now would be unreachable code whose silence would then be read as
the feature working — the exact error this report identifies in its own *Why the first probe read
zero* section, one level up.

`LuaOperatorTraitTest.testMetamethodTablesAreUntypedToday` records the measurement and **goes red
when BUG-426 is fixed**, which is the trigger to build this arm.

### 2. The "655" was never comparable to the baselines

This report is built on `total=661 lpeg-context=655`, taken from BUG-419's survivor
characterisation. Those are **graph-level `reportIncompatible` emissions**, and BUG-419 states in
the same table that they "are not comparable to the 997/478/376/317 baselines". Quoting them anyway
is what made this report high priority over BUG-423 and inverted the dependency between them.

Measured against the actual inspection counts, the trait system's primitive arms — the change this
report says would "fix ~6" — removed **57 assignability errors** corpus-wide (907 → 850) plus 17
return-type mismatches. zerobrane fell 358 → 338.

Whether any of those 20 zerobrane errors were the LPeg sites is not established, and cannot be from
these numbers. **What this arm is worth is therefore unmeasured**, and re-deriving it from the 655
would repeat the mistake. It should be re-characterised at the inspection level after BUG-426 lands,
when LPeg-shaped values are typed and the question becomes answerable.

### What stands

The defect is real: a table implementing an operator via a metamethod is not modelled, and once
BUG-426 makes such tables typed they will be reported as errors. The trait boundary is built and is
where the arm belongs — `LuaGraphType.Trait.admits`. Only the sizing and the sequencing were wrong.
