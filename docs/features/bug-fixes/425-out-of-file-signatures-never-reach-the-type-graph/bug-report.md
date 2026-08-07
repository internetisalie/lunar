---
id: "BUG-425"
title: "A function signature declared outside the file under analysis never reaches the type graph"
type: "bug"
parent_id: "BUG"
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
