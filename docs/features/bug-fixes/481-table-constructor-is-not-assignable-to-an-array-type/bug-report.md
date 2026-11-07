---
id: "BUG-481"
title: "A table constructor is reported as not assignable to an array type"
type: "bug"
parent_id: "BUG"
status: "todo"
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
