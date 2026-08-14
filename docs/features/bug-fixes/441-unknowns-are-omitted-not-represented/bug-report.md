---
id: "BUG-441"
title: "An unknown write vanishes instead of widening, so the model lies about its own completeness"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
size: "L"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-441: unknowns are omitted, not represented

Carved out of [[BUG-419]] on 2026-08-14, unchanged in substance. BUG-419 shipped the two defects that
carried its measured value (defect 3, the declared-demand gate; defect 4, the same gate for arity) and
closed. These two did not ship, deliberately, and are tracked here so a measured-zero item stops
holding a report open.

**Priority is `low` on the numbers and `low` is not the same as wrong** — see "Why this is still
worth doing".

## The two defects, as BUG-419 stated them

### 1. Unknown writes are omitted, so the model lies about its own completeness

```lua
local v = wx.thing        -- unresolved stem → contributes NO write node at all
if cond then v = "s" end  -- contributes a string write
count(v)                  -- @param n number
```

`v`'s reaching definitions become `{string}` — the unknown write *vanished* rather than widening the
set. The certainty rule then counts one non-declared write, calls the flow **certain**, and errors,
when the honest answer is "v may be `wx.thing`, which is unknown". Node-lessness is stronger erasure
than `Undefined`: identical for direct absorption, but for union formation and certainty counting,
**absence ≠ unknown**.

### 2. A union carrying an `Undefined` arm is not gradual

`local v = wx.thing or "s"` → `Undefined | string`. Bare `Undefined` absorbs every check
(`checkCompatibility` early-returns), but as a *union arm* it does not: the informative-arms filter
removes only `Nil`, the string arm mismatches a `number` demand, and the engine errors. `Undefined`
means "could be anything" — in a union it must be gradual, exactly as `Any` already is (BUG-397
Phase 1).

## They are ONE change, and the order is forced

BUG-419's probe measured defect 2's exposure at **zero across all four corpus members**, and the
reason is structural rather than lucky: "union carrying an `Undefined` arm" is **unreachable**.

**So fixing (1) alone would CREATE the exposure that measures zero today**, converting silent erasure
into new false positives. (2) is dead code until (1) lands, and (1) must not land without it.

## Mechanism — MEASURED 2026-08-14, and it is not what this report said

The paragraph above originally attributed the unreachability to
`VariableElement.resolveWrite`'s `flatten` dropping `Undefined` (`else if (type != Undefined)`). That
came from reading `flatten`, and **running says otherwise**. A throwaway probe printing each
variable's `upSet` and inferred type:

```
var 'a' upSet.size=0                                  -- local a = wx.thing
'a' = undefined   [Undefined]
var 'b' upSet.size=1                                  -- local b = wx.thing or "s"
    upSet node ValueElement type=String
'b' = string      [String]
var 'c' upSet.size=1   'c' = string                   -- local c; if cond then c = "s" end
var 'd' upSet.size=1   'd' = string                   -- local d = wx.thing; if cond then d = "s" end
```

**`d` is byte-identical to `c`.** The unknown write does not merely get dropped downstream — it never
becomes a node, so nothing downstream could have preserved it. `flatten` is never consulted about an
`Undefined` it might keep, because no `Undefined` node exists to reach it.

Three distinct places drop or omit the unknown, and the report named the least important one:

| # | site | what it does |
| :-- | :-- | :-- |
| 1 | `LuaTypesVisitor.collectRhsNodes` | the **last** expression does `result.addAll(nodes)`, adding *nothing* when `nodes` is empty. The non-last branch already has the correct `?: graph.value(expr, Undefined)` fallback, with a comment stating exactly the right principle — it is simply not applied to the last position, which is where a single-expression RHS always lands |
| 2 | `LuaTypeAlgebra.simplify` | `members.filter { it != Undefined }` — this is what collapses `b`. `visitBinOpExpr` builds `Union.create(setOf(carried, rightType))` with `carried = Undefined`, and `Union.create` canonicalizes the arm away. `truthyPart`'s own KDoc already says so: *"`Union.create` drops those first"* |
| 3 | `VariableElement.resolveWrite`'s `flatten` | the site this report named. Real, but downstream of both — it can only matter once (1) and (2) stop erasing first |

So the order is forced for a different reason than recorded: **(1) is the root**, (2) is what would
re-erase the result the moment a union forms, and (3) is the third gate. The "one change" conclusion
survives; the mechanism behind it did not.

## Two designs, and they differ in blast radius

The emission rule says `Undefined` "must be gradual, exactly as **`Any` already is** (BUG-397
Phase 1)" — and that sentence points at a much smaller implementation than the one the report implies.

- **A — make `Undefined` viral and gradual.** Preserve it through (1), (2) and (3), and teach
  `checkCompatibility` to treat a union with an `Undefined` arm as gradual. Four coordinated edits to
  the type algebra, and `displayName()` starts producing `undefined | string` in inlays and quick doc
  unless it is projected away at the presentation boundary (the precedent is BUG-424's trait
  projection in `LuaTypes.typeOf` and the hint providers).
- **B — introduce the unknown as `Any`.** Fix only (1), contributing `graph.value(expr, Any)` when
  the last RHS expression yields no nodes. `Any` already survives `simplify`, already makes a union
  gradual (`checkCompatibility`'s `val gradual = Any in valueType.types`), and already carries the
  BUG-397 rule that structural arms survive beside it. (2) and (3) need no change because they never
  see an `Undefined` to drop.

B reuses shipped, corpus-tested machinery and is the smaller change; its cost is that
`local a = wx.thing` infers `any` rather than `undefined`, and `d` becomes `any | string` where it
reads `string` today — a visible inlay change on real code, which is the thing to measure before
committing to it.

## Why this is still worth doing

The corpus payoff is near zero — with the declared-demand gate in place only 3 emissions corpus-wide
were affected — and that is exactly why it was deferred twice. The case for it is correctness, and it
is the same case in both directions:

- **It is criterion 1 of BUG-419's own emission rule.** *Unknown-free provenance* is unimplemented, so
  the engine's stated licence to speak is still broader than what it can actually justify. What
  currently keeps that honest is the declared-demand gate, not the provenance rule.
- **An unknown write dropped can make a flow look certain when it is not.** That is a live wrong
  answer, independent of how often the corpus happens to hit it.

## Attempt 1 (2026-08-14) — REVERTED, and it moves the report's centre of gravity

Design B was implemented and abandoned after three failed iterations. Nothing shipped; the source is
back at `c4c958ce`. What it established is worth more than the code was.

**1. Representing the unknown works, and does not fix the diagnostic.** Adding
`graph.value(expr, Any)` to `collectRhsNodes`'s last branch made `d` infer `any | string` — a
regression test asserting exactly that passed. The error `string is not assignable to number` was
**unchanged**.

**2. Because the check never consults the merged type.** `checkTypes` iterates
`currentUpSet × currentDownSet` and calls `checkCompatibility(valueNode.write, useNode.read, …)` —
**each reaching definition against the demand on its own**. The union the variable resolves to is
never the thing being checked. Every "make the union gradual" design in this report — including the
`Undefined`-behaves-like-`Any` framing inherited from BUG-419 — is aimed at an object the diagnostic
path does not look at.

**3. `certain` does not gate this either.** The report's causal story — *"the certainty rule then
counts one non-declared write, calls the flow certain, and errors"* — is wrong. `certain` is read at
**exactly one place**, the `Nil` branch. Proven by running, not reading: after the fix `d` has two
reaching definitions, so `certain` is already `false`, and the error still fires.

**4. A provenance flag threaded through `checkTypes` never reaches the emitter.** Adding
`unknownProvenance` to the loop and gating `reportIncompatible` on it changed nothing. Instrumented,
the loop computes it correctly — `var='d' upSet=[any, string] unknownProvenance=true` — while every
emission prints `REPORT unknownProvenance=false`. **The diagnostics do not come from the fixpoint
loop.** They are emitted from `addEdge`'s value→use branch and the `propagate*` helpers, during graph
*construction*, which call `checkCompatibility` with the parameter defaults.

### What that means for the next attempt

The report's framing — "make unknowns viral through the type algebra" — is aimed at the wrong layer.
The type algebra (`simplify`, `flatten`, `Union.create`) governs what a variable *resolves to*; the
diagnostics are emitted per-edge at construction time and never see it. A fix must therefore either:

- carry provenance on the **value node** so `reportIncompatible` can consult it wherever it is
  reached from, rather than passing it down from one caller; or
- suppress at the edge — a variable with an unknown definition must not present its *other*
  definitions as checkable values — which is a propagation change, and BUG-416's rule that
  suppression "must not ENABLE anything" applies directly.

Either is engine surgery with the corpus as the only real gate. **This is not a bug fix; it wants
the `plan-feature` pass BUG-430 was given.** Sizing it as a small carve-out from BUG-419 was wrong,
and the `low` priority below is now doing double duty — small payoff, *large* change.

## What a fix has to reckon with

- **The corpus is the gate, and it will move things the probe cannot predict.** BUG-419's arity fix
  raised three other inspections' counts by un-burying findings hidden inside ERROR ranges (the
  BUG-417 effect). Making unknowns viral changes union shapes, which reaches inlays and completion,
  not only diagnostics. Re-baseline once and attribute each movement.
- **Do not size this from a graph-level probe.** Four separate incomparabilities between graph-level
  emission counts and inspection-level baselines are recorded in BUG-419 and BUG-424. Measure at the
  inspection level, by dumping the residual.
- **`Undefined` reaching `resolveRead` is a known trap.** BUG-423/424/426 all hit the shape where a
  demand-side type projected into a read position and changed every inferred hint. The projection
  belongs at the presentation boundary.

## Verification owed

BUG-419's original list, for these two only:

- A fixture for defect 1 showing the omitted write **defeating certainty**, and one for defect 2
  showing the `Undefined`-arm union erroring. Red before, green after, mutation-proved.
- Corpus re-baselined once, with each movement attributed.
- The BUG-417 parity criterion re-run — inspection independence must survive it.
- A declared-contract violation with certain evidence still errors.
