---
id: "BUG-423"
title: "Arithmetic and concatenation operands ignore Lua's string↔number coercion"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-423: Operator operands ignore Lua's string↔number coercion

`visitBinOpExpr` demanded exactly `Number` at every arithmetic operator and exactly `String` at
`..`. Lua coerces between the two at both, so the engine rejected legal code — and after BUG-419
promoted operator demands to *language contracts* (correctly: an operand type violation really is a
Lua rule, not an inference), those rejections became firm ERRORs.

Found by characterising what survived BUG-419's emission rule. It was the single largest surviving
class.

## Measured against real interpreters

| expression | 5.0.3 | 5.4.7 | 5.5.0 |
| :-- | :-- | :-- | :-- |
| `"10" + 5` | 15 | 15 | 15 |
| `"3" * "4"` | 12 | 12 | 12 |
| `-"5"` | −5 | −5 | −5 |
| `1 .. "x"` | `1x` | `1x` | `1x` |
| `2.5 .. ""` | `2.5` | `2.5` | `2.5` |
| `"10" < 5` | ERROR | ERROR | ERROR |

Arithmetic accepts strings, concatenation accepts numbers, **comparison coerces nothing** — and that
last row is why the fix touches only the first two.

## Corpus impact

Survivors of BUG-419's rule, by value→use pair:

```
661  string  ->  number     zerobrane   <- this defect
 55  number  ->  string      "
 27  number  ->  string     penlight
 11  number  ->  string     luarocks
```

~750 emissions of ~2 300 graph-level survivors, all one cause.

## Fix — the obvious one was TRIED and REVERTED

Widening both operand demands to a union is the obvious move:

```kotlin
ARITHMETIC_OPERAND = Number | String
CONCAT_OPERAND     = String | Number
```

It works for checking — the four regression tests passed, including a `boolean` operand still being
rejected at both operators. **It was reverted anyway**, because the demand does double duty: it
constrains the operand *and* it feeds inference through `VariableElement.resolveRead`. Widening it
regressed every inferred hint:

```
local function double(n) return n * 2 end
   before:  n : number
   after:   n : number | string        <- LuaTypeInlayHintsTest.testInferredReturnArithmetic
```

`f(n) return n * 2 end` is about as common as Lua gets, so that lands on essentially every numeric
parameter in every file. `number | string` is *accurate* — `double("10")` really does work — but it
is a worse hint, and trading a false-positive class for a codebase-wide inference regression is not
a fix.

A second symptom of the same coupling: `nil .. s` changed its message from "nil value is not
assignable to string" to the union form, breaking `DuplicateNilAssignabilityTest`. That one is
cosmetic and would just need the expectation updating — recorded because it is evidence of the same
demand-is-also-inference entanglement, not an independent problem.

### The right fix: coercion belongs in the compatibility relation, not the demand

Keep the demand at `Number` (so inference and hints stay precise) and permit `String` where an
arithmetic operand is being *checked*. That needs the coercion to be context-scoped — a flag on the
`UseNode` marking "this demand coerces", consulted by `checkCompatibility`/`isCompatible` — because
a global `String ≤ Number` rule would wrongly accept `---@type number` + `local x = "s"`.

`UseNode` already grew `declaredDemand` for BUG-419, so the shape is established; this is a second
independent axis on the same node, not new machinery.

**Estimated shape**: one flag, set at the five operator demand sites, read at the two compatibility
entry points. Small, but it must not leak into the demand type itself — which is exactly what the
reverted attempt got wrong.

## Version sensitivity — one real exception, not in scope

Bitwise operators genuinely differ: Lua 5.4 removed string→number coercion for them from the core
(manual §8.1) while keeping it for arithmetic via the string metatable. Measured: `"10" | 1` errors
on 5.4.7 and 5.5.0.

**Lunar does not constrain bitwise operands at all**, so it cannot currently get this wrong. Anyone
adding them must gate on the language level rather than reuse `ARITHMETIC_OPERAND` — recorded in that
constant's KDoc, because reuse is the obvious wrong move.

## Gap: 5.1–5.3 unverified

lua.org was unreachable while this was measured (confirmed externally, not a local fault), so only
5.0.3, 5.4.7 and 5.5.0 could be run. The result is **bracketed** — identical before and after the
unmeasured range, so a change would have to have been introduced and reverted.

It does not block the fix regardless: **widening a demand can only remove diagnostics, never add a
false one.** If some intermediate version were stricter, Lunar would under-report there, which is the
posture BUG-419 chose deliberately. The current code is wrong in the *unsafe* direction on every
version that could be measured.

Closing the gap needs `lua` alongside `luac` from `tooling/corpus/fetch-luac.py`'s pinned tarballs —
the build already produces both, only `luac` is installed.

## Related: the tarball fetch is a single point of failure

`fetch-luac.py` and `fetch-torture.py` download from lua.org at provision time. Cached artefacts mean
existing builders are unaffected, but a builder or CI pod provisioned during an outage cannot build
the MAINT-35 parse oracle, so that gate silently becomes unavailable exactly when someone is setting
up. The pins are sha256-verified, so mirroring the tarballs would remove the dependency without
weakening the ratchet. Not filed separately yet.
