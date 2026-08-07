---
id: "BUG-425"
title: "A function signature declared outside the file under analysis never reaches the type graph"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-425: Out-of-file signatures never reach the type graph

Move a `---@param` one file away and the type engine stops checking it — not demoting it to the
BUG-419 hypothesis tier, **emitting nothing at all**.

## Measured (2026-08-07, `LuaDeclaredContractErrorTest` probe)

```
samefile bad-arg   [ERROR:string is not assignable to number]   ---@param n number; function count(n) end; count("s")
xfile    bad-arg   []                                           …with the declaration in defs.lua
xfile    zero-arg  []                                           count()
stdlib   bad-arg   []                                           string.rep("x", {})
stdlib   zero-arg  []                                           string.rep()
```

The same-file row is the control: a `@param` on a **global** function, checked exactly as a `local`
one is. Only the file boundary changes between it and the next row.

**The arity rows are what make this a wiring gap rather than a severity question.**
`checkFunctionCompatibility` emits `Too few arguments` before consulting any type, so its silence on
`count()` and `string.rep()` means the callee's `Function` type never reaches the call site at all.
A third probe agrees from the other direction: after `local r = string.rep("x", 2)`, calling `r()`
produces no hypothesis either, so `r` is not the known `string` the stub declares — the return type
is absent too, not just the parameters.

## Why it matters more than the emission count suggests

BUG-419 measured **3** declared-demand emissions across the whole of zerobrane and read that as
"real-world Lua is un-annotated". That is at most half the story: annotated code is not counted
either unless the annotation sits in the same file as the call. Every stdlib signature Lunar ships,
every `@class` in a library, every definition file — invisible to assignability checking.

It also corrects a premise in TARGET-10's sequencing. That plan expects its generated
`wx`/`wxstc`/`wxaui` definitions to put "~10 000 contracts through" the declared-demand ERROR path.
On today's engine they put **zero** through it. TARGET-10's own value — completion, navigation, and
~1 877 undeclared-variable hits — does not depend on this and is unaffected; only the claim about
loading the type engine is.

## Not the same bug as BUG-419

BUG-419 is about *tier*: which known contracts are diagnostics. This is about *reach*: which
contracts are known. They compose — fixing this one will hand the ERROR path its first real load,
which is why BUG-419's fixtures were mutation-proved before it (`M1`–`M6`, recorded in that report).

## Fix direction — not yet traced

Unknown whether the signature is missing because the call site never resolves the declaration into a
`LuaFunctionType`, or because it does and `LuaTypeGraphBridge` only injects for declarations in the
file being visited. `LuaTypesSnapshot` is per-file, so the second is the likelier shape, but that is
a hypothesis and this report has no business asserting it — three reports this month carried
premises measurement refuted.

Trace first: instrument the call-site path for `count("s")` with `defs.lua` present, and record
whether a `LuaGraphType.Function` with parameter nodes is built at all.

Note that a fix mints the demand tier question immediately: parameters materialized through
`LuaGraphType.fromLuaType` get their use nodes from `memberNodeFor`, which passes no
`declaredDemand` and therefore defaults to **false**. So out-of-file contracts would arrive in the
hypothesis tier unless that site is marked at the same time — the whole population, silently.

## Verification

- `LuaDeclaredContractErrorTest.testOutOfFileDeclaredContractsProduceNoDiagnosticToday` is written as
  a characterization test: **it goes red when this is fixed**. Flip it to `assertErrors` then.
- The arity probe must also come alive — `count()` against a one-parameter declaration in another
  file reports `Too few arguments`.
- A corpus re-baseline, expected to move `LuaTypeAssignability` upward: this is a fix that *adds*
  diagnostics, and the new population must be sampled for false positives before it is accepted.

## Fixed in part (2026-08-07) — and two claims in this report were wrong

### Root cause: a deliberate BUG-397 suppression, not an accident

`visitFuncCall` returns early for a callee in `declarationTypedNodes`, with the reason in the
comment: such a callee "contributes its declared return to the call results but raises no call
demand", because wiring one regressed `redis.register_function`'s `@overload` table form. Nobody
separated *the demand on the callee* (which really must stay off) from *the declared parameter
types* (which need not).

Fixed by raising a per-argument demand carrying the declared parameter type, marked
`declaredDemand = true` — a stub signature is a contract somebody wrote. Two guards, both measured
into existence:

- **Only node-free parameter types.** A demand built from a `Function` or `Table` does not merely
  compare — `checkFunctionCompatibility` and `checkTableCompatibility` wire edges into the type's
  own member and parameter nodes, which belong to the seed shared by every call in the file. One
  call site rewrote the signature everyone else reads;
  `ExpectedCallbackResolverTest.testTableSortComparatorSlotResolves` caught it. So class-typed and
  callback-typed parameters remain unchecked.
- **Only exact-arity, vararg-free calls.** A "the arity fits" rule put **244 false positives** on
  the corpus, 123 from `table.insert` alone: it declares `(list, pos?, value?)` plus an `@overload`,
  so `table.insert(stack, "block start")` matched `"block start"` against `pos: integer`. The caller
  omitted a *middle* parameter, which positional matching cannot see, and `@overload` never reaches
  the type engine at all. Any call that does not fill every slot is one the engine cannot align.

### Corpus impact: ZERO, on all four members

`LuaTypeAssignability` and `LuaReturnTypeMismatch` are unchanged everywhere; on luarocks the
per-site dump is byte-identical to the control, 0 new and 0 lost. The fix is unit-verified and
corpus-invisible, because real calls to out-of-file signatures almost never fill every slot exactly.

That is a third correction to TARGET-10's contract-load premise: even with this fixed, its generated
`wx` contracts are checked only on calls that supply every parameter.

### Claim 1 was wrong: the stdlib row was an artefact

This report's measurement table has `stdlib bad-arg []` as evidence. A light fixture has **no
bundled stdlib stubs in scope** — `resolveGlobal("string")` returns an empty table there, so
`string.rep` had no signature to check for reasons that have nothing to do with this bug. Forcing a
stub-index rebuild changes nothing. That row proves nothing and is withdrawn.

### Claim 2 was wrong: arity coming alive is not the criterion

The verification list said "the arity probe must also come alive". It must not:
`testDeclarationTypedCalleeIsNotArityChecked` pins arity as deliberately unreported, for the
`@overload` reason above. That criterion is withdrawn, and the reasoning behind it now guards the
type check too.

## Still open — filed as BUG-427

Two shapes remain invisible, and both are *resolution* gaps rather than demand gaps, which this
report conflated. Measured:

```
---@param n number
function count(n) end          (another file)  -> resolveGlobal = null            <- no type at all
---@param n number
count = function(n) end        (another file)  -> fun(n: unknown)                 <- @param lost
Lib = {}
---@param n number
function Lib.count(n) end      (another file)  -> checked, and errors correctly   <- fixed here
```

The library-member form — what a definitions file generates, and what TARGET-10 emits — works. A
bare global function declaration does not resolve cross-file at all, and the assignment form
resolves without its annotations. See **BUG-427**.
