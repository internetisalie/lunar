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

### This is not a new design question — traits were specified and never built

`docs/features/type/type-inference-engine-design.md` already calls for them, by name:

| where | what it says |
| :-- | :-- |
| §4.1 type kinds | `TRAIT_ORDERED`, `TRAIT_STRINGABLE`, listed as first-class "Trait constraints" |
| operator table | `..` → *"TODO: stringable trait"*; relational → *"TODO: ordered trait"* |
| known gaps | *"`..` operands / `STRING` constraint / Should be relaxed to a `STRINGABLE` trait"* |
| roadmap item 6 | *"**Trait system** — implement `ORDERED` and `STRINGABLE` traits (**as use-type heads**)"* |

`git grep -i trait src/main` returns nothing. Designed, recorded as a gap, never implemented.

"**as use-type heads**" is the demand-only property — the one that must never surface through
`resolveRead`. The design had it right from the start; the reverted attempt above rediscovered it by
breaking the inlay hints. That is the strongest argument for traits over a `coercing` flag: the flag
is a new idea, the trait is the plan.

**But the design has a hole that the measurement exposes.** It anticipated `STRINGABLE` (concat) and
`ORDERED` (relational), and specified arithmetic as plain `NUMBER` with *no* TODO — so it believed
`NUMBER` was correct there. It is not: `"10" + 5` is 15, and arithmetic accounts for **661 of the
~750** false positives, against 55 + 27 + 11 for concat. The single largest cost is the case the
original design did not flag.

So the trait set needs a fourth the design never named — `NUMBERABLE` — and it is the one that
matters most.

**Two of the four are moot or free today**, which shrinks the work:

- `ORDERED` — relational operators currently impose **no** operand demand at all
  (`"==", "~=", "<", ">", "<=", ">=" -> Boolean`, no use edge). The design's "typed as NUMBER" is
  stale. Permissive, therefore safe, and `"a" < "b"` correctly goes unflagged while `"10" < 5` — a
  real Lua error — also goes unflagged. Adding `ORDERED` would *tighten* this, so it is a separate
  decision, not part of fixing a false positive.
- `LENGTHABLE` — `#` already demands `String | Table | Array`, an unnamed trait. Naming it is free.

### Two candidate fixes — traits preferred, flag as the tactical fallback

Both keep the demand type at `Number`/`String` so inference and hints stay precise. That constraint
is non-negotiable: violating it is exactly what killed the reverted attempt.

**(a) A `coercing` flag on `UseNode`** — set at the five operator demand sites, consulted by
`checkCompatibility`/`isCompatible`. `UseNode` already grew `declaredDemand` for BUG-419, so this is
a second independent axis on an established shape. Smallest possible change.

**(b) Traits — `Numberable`, `Stringable`, `Lengthable`** — express what the *position* requires
rather than tagging the edge. Preferred, because a flag can only ever say "also accept string here",
while a trait says what the operand must be able to *do*:

| | flag | traits |
| :-- | :-- | :-- |
| fixes string↔number | yes | yes |
| keeps hints precise | yes | only if traits never reach `resolveRead` |
| the `#` operator | stays an ad-hoc union | **already is `Lengthable`** — unifies it |
| `__add` / `__concat` tables | inexpressible | natural: `Numberable` ⊇ table-with-`__add` |
| diagnostic wording | bare mismatch | "string is not Numberable" |

The `#` row is the tell: `visitUnaryOpExpr` already demands `String | Table | Array`, which is an
unnamed `Lengthable`. The codebase is halfway to traits by accident, and naming it would replace a
one-off union rather than add a concept.

**The trap is identical for both.** A trait must be a *demand-only* type that never surfaces through
`VariableElement.resolveRead`, or it reintroduces the `n : number | string` regression verbatim —
this time with a name users have never heard of. Whichever is chosen, the inlay-hint test and
`DuplicateNilAssignabilityTest` are the gates, because those are what caught the last attempt.

**Second trap, with precedent.** The flag (or trait) will likely need transitive resolution through
`downSet`, mirroring `VariableElement.resolveDeclaredDemand`. BUG-419 assumed the checked pair was
(value, use-node) when it is frequently (value, **variable**) — `VariableNode` *is* a `UseNode` and
inherited the `false` default, silently demoting every declared contract reached through a call. An
operand reaching an operator through a variable hop would lose a `coercing` flag the same way. Verify
with a fixture that has the hop, not just the direct form.

**Sequencing.** The full trait model is gated on metamethod awareness (BUG-424) for the
`__add`/`__concat` arm. Traits can land first covering only the primitive arms, with the metamethod
arm added when BUG-424 does — the trait boundary is where that extension belongs, which is the third
argument for (b) over (a).

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
