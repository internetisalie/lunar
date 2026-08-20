---
id: "BUG-441"
title: "An unknown write vanishes instead of widening, so the model lies about its own completeness"
type: "bug"
parent_id: "BUG"
status: "done"
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

### Emission path — PARTIALLY grounded, and the two runs disagree

Stack-probed on clean `main` (`c4c958ce`), the defect fixture emits **twelve times, all with an
identical stack**:

```
reportIncompatible  <-  LuaTypeGraph:514  <-  checkCompatibility  <-  LuaTypeGraph:333  <-  checkTypes
```

So on unmodified code the emissions come from **the fixpoint loop**, through
`checkCompatibility`'s *final unconditional* `reportIncompatible` — not from `addEdge`.

**That contradicts attempt 1's instrumentation**, where the loop printed
`unknownProvenance=true` for `d` and `n` while every emission printed
`REPORT unknownProvenance=false`. Both observations are real; they were taken against different code
states, and they cannot both describe one path. Candidate explanations, none yet tested:

- a second path exists via `addEdge`'s value→use branch (line 135, the one call site attempt 1 did
  **not** thread), reached from the `addEdge` calls *inside* `checkFunctionCompatibility`, and the two
  runs happened to be dominated by different ones;
- the `at '<element>'` in the attempt-1 log is the *use* element, not the variable being iterated, so
  those lines may not belong to the `var=` line they follow;
- attempt 1's flag was computed correctly but a pair was first checked — and so recorded in
  `checkedPairs` — during an iteration when the unknown had not yet propagated into that variable.

**Resolve this before writing any fix.** A stack probe *with the candidate fix applied* answers it in
one run, and everything below depends on the answer.

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

## Attempt 2 (2026-08-20) — FIXED. The emission path is pinned, and it settles the contradiction.

### The probe the report demanded, run first

A stack probe **with the candidate fix applied** (the report's step 1). Every emission, without
exception, had one identical stack:

```
checkTypes:276  ->  checkTypes:333  ->  checkCompatibility$default  ->  checkCompatibility:514  ->  reportIncompatible
```

**Nothing came from `addEdge`.** The suspected second path via `addEdge:135` does not exist for this
shape: that branch handles a direct value->use edge (`nil .. "x"`), which has no variable and so no
upSet to be unknown about. The `$default` frame is the likely explanation for attempt 1's
contradictory instrumentation — the call at 333 defaults `visited`, so it routes through Kotlin's
synthetic bridge and a parameter added there is easy to thread into the wrong arity.

### The fix

- **RC-1** — `collectRhsNodes` contributes `graph.value(expr, Any)` for a node-less last expression.
  `Any` and not `Undefined`, because `simplify` and `flatten` both drop an `Undefined` union arm and
  would restore today's behaviour exactly.
- **RC-2** — the loop checks each reaching definition **on its own**, so the merged union is never
  consulted; this is why every design aimed at the type algebra failed. Unknown-ness is a property of
  the **upSet as a whole** — the string write in `local d = wx.thing; if c then d = "s" end` is itself
  perfectly known, and what makes it unreportable is a *sibling*. Computed in `checkTypes`, threaded
  through `checkCompatibility`, gated in `reportIncompatible`.
- **Downgraded to `HYPOTHESIS`, not suppressed.** BUG-416 requires that suppression never ENABLE
  anything, and returning early would skip member-edge wiring the surrounding checks still perform.
  It is also the honest tier: the conflict is evidence the model is incomplete.
- **Defect 2** needed the same substitution one layer up: `visitBinOpExpr`'s carried arm was
  `Undefined`, which `Union.create` canonicalizes away, so `wx.thing or "s"` collapsed to bare
  `string`.

### Corpus — CORRECTED 2026-08-20. It moved a great deal, and the first landing note was wrong.

The landing note said "no movement to attribute". That was false, and the reason it was believed is
worth recording: **the ratchet is one-directional.** `CorpusGuards` asserts only
`regressions.isEmpty()`; improvements are `println`'d as `[corpus] IMPROVED (...) — re-record with
-PrecordCorpusBaseline` and fail nothing. A green ratchet means "nothing got worse", not "nothing
changed", and the seven IMPROVED lines went unread.

Measured properly, by reverting only this fix's two source files to `dc712238` and re-sweeping:

| corpus | `LuaTypeAssignability` | `LuaReturnTypeMismatch` | `LuaSuspiciousConcatenation` |
| :-- | --: | --: | --: |
| luacheck | 0 → 0 | — | — |
| luarocks | 7 → 5 | — | 116 → 115 |
| penlight | **46 → 5** | **6 → 1** | — |
| zerobrane | **32 → 0** | **9 → 0** | 25 → 21 |
| **total** | **85 → 10** | **15 → 1** | |

Pre-change matched the committed baseline exactly (zero IMPROVED lines), so the movement is cleanly
attributable here. **89 of 100 emission sites removed** — against this report's own prediction of
"only 3 emissions corpus-wide". A whole inspection zeroing on a 72-file corpus is the "stopped
checking" shape, so it was sampled rather than accepted: see [[BUG-428]], which the sample closes.
Every removed site read was a genuine unaccountable value; the 11 survivors are a *different* family
(operator metamethods on LPeg patterns and `__mod`), which is what shows checking still works.

Baselines re-recorded in the same commit. `LuaInspectionParityTest` 1/1 (BUG-417 parity holds).

### One test moved, and it pinned a spelling

`FreeGlobalMemberTypingTest.testChainedReadsStayIsolatedAndUntyped` asserted
`getValueType(v) == Undefined`. An unmodellable RHS used to contribute no node at all, leaving the
variable at its initial `Undefined`; it now contributes an explicit `Any`. The property that method
guards — a write through `Config.db` must not leak into `Config.sub`'s read — is untouched, since the
leak would make `v` a `string`. Rewritten to assert the property and **more strictly** on the half
that matters: the leaked `string` is now named outright with `assertNotSame`, rather than implied by
an equality that also happened to pin how "unknown" was written down.

### Mutation proof — 6/6 CAUGHT, including the control

| mutation | red |
| :-- | :-- |
| RC-1's `Any` node removed | `testAnUnknownWriteDefeatsCertainty` |
| tier gate -> `declaredDemand` alone | `testAnUnknownWriteDefeatsCertainty` |
| sibling computation -> `false` | `testAnUnknownWriteDefeatsCertainty` |
| `carried` arm -> `carriedPart` | `testAnUnknownOperandKeepsTheExpressionGradual` |
| **tier forced to `HYPOTHESIS` unconditionally** | **`testTheSameCodeWithoutTheUnknownWriteStillErrors`** |
| RC-1's `Any` node removed (presentation) | `testAVariableBoundToAnUnmodellableRhsDisplaysAsGradual` |

The last row is the one this report insisted on: widening the gate to fire unconditionally turns the
**control** red, so the fix represents the unknown rather than having stopped checking.

### `displayName()` — scoped out by the report, and it was NOT uncovered in the way first claimed

The landing note originally said no test covers the presentation change. That is wrong, and the
correction is the useful part: `displayName()` **is** asserted in several places —
`ArraySubscriptTypeTest`, `LambdaParamInferenceTest`, `StubGlobalSeedTypeTest` — and every one of
them asserts on a **sub-expression**: the subscript node, the lambda parameter, the `KEYS` reference
itself. RC-1 changes `collectRhsNodes`, which feeds the **variable on the left**, and nothing
asserted there. `StubGlobalSeedTypeTest` is the sharpest illustration: its fixture is
`local x = KEYS` — exactly a node-less-RHS binding — and it pins `KEYS`, never `x`.

So the surface was covered and the *position* was not, which is why a green suite said nothing about
it. Closed by `testAVariableBoundToAnUnmodellableRhsDisplaysAsGradual`, measured rather than assumed:
`local a = wx.thing` displays **`any`**. It is a characterization test, not a preference — if that
wording proves noisy in inlays and quick doc, the report's answer is a presentation-boundary
projection (BUG-424's precedent), and this is the test that would go red when it lands.
Mutation-proved: reverting RC-1 turns it red alongside `testAnUnknownWriteDefeatsCertainty`.

### Gates

`test --rerun --no-build-cache -PwithCorpus`: **2 694 tests, 1 failure** — `LuaInterpreterCommand-
LinesTest.testForProjectResolvesRuntimeAndAppliesEnvironment`, `expected the runtime dir prepended to
PATH`, which is [[BUG-422]] verbatim. Confirmed by measurement rather than by pattern-matching: it
passes in isolation and fails only under the full suite, the exact signature that report records.
`ktlintCheck` green.

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

## Root cause

Two defects, at two layers. Both are grounded by measurement; the line numbers are against
`c4c958ce`.

**RC-1 — the unknown is never represented.** `LuaTypesVisitor.collectRhsNodes:230`, the
last-expression branch, does `result.addAll(nodes)` and so adds **nothing** when the expression
produced no nodes. Measured: `local a = wx.thing` gives `upSet.size=0`. The non-last branch
immediately below already does the right thing (`?: result.add(graph.value(expr, Undefined))`) with a
comment stating the principle — it is simply not applied to the position a single-expression RHS
always occupies. Two further sites would re-erase an `Undefined` even if one were created:
`LuaTypeAlgebra.simplify:51` and `VariableElement.resolveWrite`'s `flatten` (`LuaTypeNodes.kt:161`).

**RC-2 — the diagnostic never consults the variable's merged type, so RC-1 is not sufficient.**
`LuaTypeGraph.checkTypes:326-341` iterates `currentUpSet × currentDownSet` and calls
`checkCompatibility(valueNode.write, useNode.read, …)` — **each reaching definition against the demand
on its own**. `checkCompatibility:514` then reports unconditionally. Proven: after RC-1 was fixed, `d`
inferred `any | string` (asserted, passing) and the error was unchanged.

`certain` is **not** the guard the report originally claimed — it is read at exactly one place, the
`Nil` branch (`LuaTypeGraph.kt:507`). With RC-1 fixed `d` has two reaching definitions, so `certain`
is already `false`, and the error still fires.

## Fix strategy

RC-1 alone is not a fix and must not ship alone: it changes inferred types corpus-wide (`local a =
wx.thing` becomes `any`, `d` becomes `any | string`) while removing no diagnostic. Ship both or
neither.

1. **Pin the emission path first** (see "Emission path" above) — one stack probe with the candidate
   fix applied. If a second path via `addEdge:135` exists, it needs the same gate, and attempt 1
   failed precisely by missing it.
2. **RC-1**: contribute a node for a node-less last expression. `Any` rather than `Undefined` —
   `Undefined` is dropped again by `simplify` and `flatten`, restoring the current behaviour and
   fixing nothing (this is why the report's original "make `Undefined` viral" framing costs three
   edits to achieve what one buys).
3. **RC-2**: carry unknown-ness where the emitter can see it. Attempt 1 threaded a parameter from
   the loop; prefer instead a property of the **value node** — `ValueNode.unknownProvenance`,
   alongside the existing `declaredOrigin` — so it is available wherever `reportIncompatible` is
   reached from rather than depending on one caller passing it down.
4. **Gate inside `reportIncompatible`, not at its call sites.** BUG-416 established that suppression
   must never *enable* anything; returning earlier would skip member-edge wiring the checks still
   perform.

**Deliberately out of scope**: `displayName()`. If `any | string` proves noisy in inlays and quick
doc, project it at the presentation boundary — `LuaTypes.typeOf` and the hint providers — exactly as
BUG-424 did for traits. Do not solve it by weakening the graph.

## Test strategy

Attempt 1's fixtures were correct and are worth re-creating verbatim; they are not in the tree because
committing a red test breaks the gate.

| test | asserts |
| :-- | :-- |
| `testAnUnknownWriteDefeatsCertainty` | `local d = wx.thing; if c then d = "s" end; count(d)` reports **no** `not assignable to number` |
| `testTheSameCodeWithoutTheUnknownWriteStillErrors` | **the control** — the identical fixture minus the unknown write **still errors** |
| `testTheUnknownSurvivesIntoTheInferredType` | `d` is a `Union` containing `Any` |
| `testAKnownOnlyVariableGainsNoGradualArm` | `c` is exactly `String` |
| `testAnUnknownOperandKeepsTheExpressionGradual` | `wx.thing or "s"` is not reported against `number` |

**The control carries the weight.** A fix that silences both fixtures has not represented the unknown,
it has stopped checking — and without the control every other test here still passes. Mutation-proof
it: the control must go red when the gate is widened to fire unconditionally.

The corpus is the real gate. Expect movement in **inlays and completion**, not only diagnostics, since
RC-1 changes inferred types; re-baseline once and attribute each movement. Re-run the BUG-417 parity
criterion — it currently reads exact parity (1954/1954, 72/72) and must not regress.

## Verification owed

BUG-419's original list, for these two only:

- A fixture for defect 1 showing the omitted write **defeating certainty**, and one for defect 2
  showing the `Undefined`-arm union erroring. Red before, green after, mutation-proved.
- Corpus re-baselined once, with each movement attributed.
- The BUG-417 parity criterion re-run — inspection independence must survive it.
- A declared-contract violation with certain evidence still errors.
