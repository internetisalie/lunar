---
id: "BUG-416"
title: "A possibly-nil value reaching a table slot reports an assignability error, 1801 times on one project"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-416: `nil | T` is reported as not assignable to `T`, pervasively

`LuaTypeAssignability` reports an error whenever a possibly-nil value flows into a slot typed as a
table. In Lua that is idiomatic and everywhere — there is no null-safety discipline to violate — so
the diagnostic fires at a rate that makes it noise rather than signal.

**Caused by BUG-397** (`f08d7ca3`, "free globals are typed for the whole engine"). Before it, free
globals were untyped and this comparison never happened; after it, they carry declared types and
every nil-union assignment against them is checked. Filed separately rather than reopening BUG-397
because that change is shipped, correct in its own terms, and the fix here is to the assignability
rule, not to the typing.

## Measured

Agent-run probe over the pinned zerobrane corpus member (ZeroBrane Studio, 72 indexed files),
`LuaTypeAssignabilityInspection` enabled alone, 2026-08-06:

**2 491 warnings, 46 distinct shapes.** The top three are one defect:

| shape | count |
| :-- | --: |
| `nil value is not assignable to { ... }` | 959 |
| `nil \| { ... } is not assignable to { ... }` | 421 |
| `{ ... } \| nil is not assignable to { ... }` | 421 |
| **subtotal** | **1 801 (72%)** |

Remaining shapes are a long tail of union-narrowing complaints of the same family —
`boolean | string is not assignable to string` (30), `boolean | number is not assignable to number`
(25), `File | nil is not assignable to { ... }` (25), `fun(list, i?, j?) | nil is not assignable to
fun(p)` (62).

Sites cluster in ordinary code: seven of the first eight samples are in `src/editor/gui.lua`.

## Why this is a false positive

Reading `t.foo` in Lua yields `nil` when absent, so *every* table field read is `T | nil` unless the
engine proves otherwise. Requiring `T` at the use site therefore flags idiomatic code:

```lua
local frame = ide.frame        -- inferred nil | { ... }
frame:SetStatusText("ready")   -- reported: nil | { ... } is not assignable to { ... }
```

ZeroBrane is a working, shipped IDE. 1 801 genuine type errors in 72 files is not a credible reading;
1 801 instances of one over-strict rule is.

The `nil value is not assignable to { ... }` variant (959) is the stronger signal still — that is a
value inferred as *exactly* `nil`, not a union, which suggests the engine is losing the non-nil arm
somewhere rather than merely being strict about optionality.

## Fix direction

Treat a `nil` arm as **gradual** at an assignment site, the way BUG-397's own Phase 1 already treats
an `any` arm: `any | <structural>` keeps its structural arms and only matching arms propagate
constraints. The same reasoning applies to `nil` — an optional value is not a type error in a
language whose absent-field read *is* `nil`.

Deliberately **not** proposed: suppressing the inspection, or special-casing free globals. The rule
is wrong for locals too; free globals are only what made it visible.

Investigate the 959 bare-`nil` cases first — if a non-nil arm is being dropped, that is a distinct
inference defect and fixing it may shrink the rest.

## Also observed, unverified

`Too few arguments: expected at least 2, got 1` fires 176 times. BUG-397's commit message states
that declaration-typed callees "raise no call demand, so stub calls stay un-arity-checked" — that
guarantee is specifically about *stub* calls, so these may be legitimate arity errors on ordinary
calls. **Not established either way**; check before treating it as a defect.

## Test strategy

- A fixture reproducing the pattern: a table field read passed to a function expecting a table must
  produce **no** assignability error. Red before the fix.
- The 959-case shape needs its own fixture once the cause is known — a value inferred as bare `nil`
  where a union was expected is a different assertion from an over-strict union rule.
- The corpus is the scale check, not the gate: zerobrane's `LuaTypeAssignability` should fall
  substantially. Its current baseline is un-validated (BUG-415) and must not be treated as a floor
  to protect.

## Outcome (2026-08-06)

The fix landed in a different place than planned, because the failing fixtures kept correcting the
plan: errors arrive at `checkCompatibility` **per reaching definition**, not as materialized unions,
so the union rule alone was nearly irrelevant. The delivered semantics:

- **Certainty by provenance and count.** A direct value→use edge (`nil .. "x"`) is the expression's
  own value — certain, still an error. A value reaching a use through a variable is one reaching
  definition: with several, a nil among them is optionality (no error); with exactly one
  *non-declared* write, it is certain (`local nothing = nil; count(nothing)` keeps its error).
  Declared bounds (`---@type` values, cross-file seeds) are excluded from the count — an annotation
  states what a variable should be, not what was written — which is what keeps
  `---@type string` + `local x = nil` an error (the `Nil → non-nil` engine contract).
- **The union informative-arms rule** for materialized unions (`and`/`or` builds one), with a gate
  so forgiveness never newly enables structural propagation.
- **"No information" is `Undefined`, never `Nil`**: the declared `frame = nil` placeholder seeds
  nothing, and six unmodeled-expression fallbacks stop manufacturing certain nils (BUG-359's
  mechanism, which the certainty rule would otherwise promote).
- **Incidental hardening**: `checkTypes` convergence no longer counts errors as progress —
  suppressing a diagnostic silently ended fixed-point iteration early.

Six fixtures, each mutation-proved: M1 (union rule), M2 (certainty), M3 (placeholder seed) each
killed exactly the tests claiming to cover them. The three `PrimitiveTypeCompatibilityTest`
`Nil → non-nil` contract tests went red on the first certainty design and drove the declared-origin
refinement.

Corpus (all four members re-recorded): `LuaTypeAssignability` luacheck 502 → 378, luarocks
1 778 → **419 (−76 %)**, penlight 506 → 257, zerobrane 2 594 → **993 (−62 %)**;
`LuaReturnTypeMismatch` roughly halved everywhere. `LuaUndeclaredVariable` is byte-identical on
three members — the fix provably does not touch resolution.

**Found while verifying: BUG-417.** zerobrane's undeclared count would not stabilise across
variants; six probe cycles established that `LuaUndeclaredVariableInspection`'s results depend on
whether the type inspection ran in the same pass (1 954 alone vs 843/1 563 with it), pre-existing,
file-scoped, user-visible. The 959 bare-`nil` shape's "investigate first" instinct in this report
was right for the wrong reason — the investigation's real yield was the contamination.

Full suite 2 411 / 0 (1 skipped); ktlint and doc linters clean.
