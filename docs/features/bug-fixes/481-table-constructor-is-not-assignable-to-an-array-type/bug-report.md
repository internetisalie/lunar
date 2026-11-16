---
id: "BUG-481"
title: "A table constructor is reported as not assignable to an array type"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-481: A table constructor is reported as not assignable to an array type

`---@type string[]` followed by `local xs = {}` reports **`{ ... } is not assignable to string[]`**
on the unmodified engine. Every array-typed local initialised with a constructor — the idiomatic
way to declare one — carries a false error.

Found by [[TYPE-14]]'s DR-01 probe (2026-09-05, `ddaac805`), which was measuring generics and hit
this in its control fixtures. Filed separately because it has nothing to do with generics: the
value is a plain `LuaGraphType.Table` and the demand a plain `LuaGraphType.Array`.

## Reproduction

A light fixture (`IndexedBasePlatformTestCase`, `configureByText`):

```lua
---@type string[]
local xs = {}
```

`LuaTypesSnapshot.forFile(file).getErrors()` contains, verbatim from the probe report:

```
'local xs = {}': { ... } is not assignable to string[]
```

The same line appears twice in the probe (fixtures B and I), once per file that used the idiom.
A non-empty constructor (`{ "a", "b" }`) was not probed; whether it also reports is part of the
analysis, not of this report.

### Also reached through a parameter ([[MAINT-54]] DR-01e)

Measured again on `066057d5` by MAINT-54's probe, with the callee `---@param xs number[]`
`function pArray(xs) end` in another file. Each caller reports `ERROR` *{ ... } is not assignable to
number[]* (non-`HYPOTHESIS`):

| Caller | Where the error lands |
|---|---|
| `pArray({1})`, `pArray({})`, `pArray({1, 2})` | the literal |
| `---@type number[]` `local xs = {}` `pArray(xs)` | `{}` in the declaration, and `xs` |
| `local ys = {1}` `pArray(ys)` | `ys` |

So non-empty constructors report too, and the false positive reaches every array-typed parameter
that is passed a table. MAINT-54 reports its `arg.array` shape as unmeasurable for this reason.

## Expected

No diagnostic. An empty constructor is assignable to any array type, and a constructor whose
positional entries are all assignable to the element type is assignable to that array type.

## Actual

`LuaTypeGraph.isCompatible` has an arm for `Array` against `Array` and for `Table` against
`Table`, and none for a `Table` value against an `Array` demand
([LuaTypeGraph.kt:~690-707](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt));
the pair falls to `else -> false`. This is the symptom's location, not a root-cause claim — the
right representation for a constructor with positional entries (an `Array` of the entry types'
union, a `Table`, or both) is the analysis `plan-bug` owns.

## Impact

Medium. The idiom is universal in annotated Lua, and the diagnostic is an ERROR-tier message on a
declaration line, which BUG-417 showed can bury every other inspection's result on the file.

## Root Cause

`visitTableConstructor` ([LuaTypesVisitor.kt:797](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
built a `LuaGraphType.Table` holding only the *named* entries; positional entries were dropped, so
a constructor carried no information about its elements. Both assignability paths then had no
`Table` value against `Array` demand arm: `LuaTypeGraph.checkCompatibility` fell to its final
`reportIncompatible` (the `{ ... } is not assignable to X[]` message), and `isCompatible` fell to
`else -> false`, which also made every union demand with an array arm (`Foo|Foo[]`) fail.
Grep: `grep -n "Array" LuaTypeGraph.kt` shows only an `Array`/`Array` arm in each.

## Fix Strategy

- `LuaGraphType.Table` gains `positional` (one `VariableNode` per positional entry, fed from the
  entry's expression) and `arrayShaped` (a constructor literal with no `[expr] = v` key). Both sit
  outside the primary constructor, so equality is unchanged: as constructor parameters they split
  `{ ... }` into `{ ... } | { ... }` in `zerobrane-commands.golden` (`ide.session.projects[dir] or {}`).
  Because equal tables can now differ, the `Table`/`Array` pair is not memoised in `isCompatible`.
- `isCompatible` gets a `Table` against `Array` arm (`isArrayCompatible`): the table must be
  `arrayShaped` and every positional entry, other than a bare `nil` hole, must fit the element type.
  `checkCompatibility` returns before reporting when that holds.
- A bracket-keyed constructor is not judged, so it is reported exactly as before
  (`assign-type-mismatch-011`, `{ [1] = nil }` against `[boolean]`, which the parser reads as `any[]`).

## Test Strategy

`LuaTableConstructorArrayAssignabilityTest`: empty, matching, `nil` hole, held in a local then passed
to an array parameter, parameter argument, `Foo|Foo[]`; controls that stay reported: `{ 1 }` and
`{ 1, 2, 3 }` against `string[]`/`boolean[]`, `{{'a'}}` against `string[]`, a number against
`string[]`, and a bracket-keyed constructor.

## Not covered

`assign-type-mismatch-046` (`string|string[]|string[][]` with `{{'a'}}`) has a different cause:
`luacats.bnf` declares `arrayType ::= distinctType '[]'?`, so `string[][]` parses as `string[]`.
It still reports and is left out of this fix.
