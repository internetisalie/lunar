---
id: "BUG-473"
title: "`LuaTypesSnapshot.forFile` is superlinear in call-site count once a `@class` tag is present"
type: "bug"
parent_id: "BUG"
status: "planned"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-473: one `---@class` turns snapshot cost superlinear

Found 2026-08-27 by [[REFACT-01]]'s `REFACT-01-00-DR-03` while sizing receiver-type method
resolution. **Not caused by that spike** — re-measured on the restored clean tree, with the
prototype reverted, and the numbers are unchanged.

## Reproduction

A single Lua file containing a `---@class` annotation and *n* colon-call sites against that class.

```lua
---@class Builder
local Builder = {}
function Builder:setName(n) end

local b = Builder
b:setName("a")   -- × n
```

Measure `LuaTypesSnapshot.forFile` on it.

## Measured — timings only (the profile is below)

Direct `forFile` calls on the clean tree at `a2a8758e`, prototype reverted.

| call sites *n* | with `@class` | same file, tag removed |
| ---: | ---: | ---: |
| 10 | 139 ms | 70 ms |
| 20 | 312 ms | 81 ms |
| 40 | 1 383 ms | 145 ms |
| 80 | **12 807 ms** | 190 ms |

**×2.2, ×4.4, ×9.3 per doubling** — worse than quadratic and accelerating. Flat without the tag.

**Two data points are deliberately excluded from the curve above, and should not be quoted as if
they belonged to it:**

- **n = 5 → 724 ms** is higher than n = 10 and n = 20. That is JIT warm-up, not the curve.
- **n = 200 → 344 736 ms** comes from an **earlier run**, not this controlled series, and its
  comparison figure (`~1229 ms for 200 full resolves`) measures something else entirely. It is
  suggestive of where the curve goes and is **not evidence**. Re-measure it before citing it.

**There was no profiling data when this was filed, and no way to obtain any — see [[MAINT-38]],
which has since shipped the capability and profiled this bug (section below).** No flame graph, no
call-hierarchy trace, no per-method breakdown, no allocation counts, no cache hit/miss
instrumentation. The repo has three performance *tests* (`GlobalSymbolCompletionPerformanceTest`,
`GlobalSymbolPerformanceOptimizationTest`, `LuaUnionDistributionBenchmarkTest`) which assert
wall-clock budgets — none of them caught this — but nothing that attributes time to frames.
`LuaTypeSourceRecorder` is not profiling: its `SourceFrame` records which files and keys a
snapshot consumed, which is a cache-correctness question.

**So the first task was not to fix this bug — it was MAINT-38.** Diagnosing this one by reading
source would have been guesswork, and the same blindness applied to every performance question after
it. MAINT-38 tier 1 is done and the recording's verdict is below; this bug is now a fix, not an
investigation.

## Expected

**Not linear — that target is unreachable and was wrong as first written.** `LuaTypeGraph`'s
transitive closure (`LuaTypeGraph.kt:9-10`) is O(n²) by construction, so no fix to this bug makes
snapshot cost linear in call-site count.

What is reachable, and what the fix is measured against: an annotated file is not *dramatically*
more expensive than an unannotated one of the same size. The measured annotated/control ratio at
n = 160 is **676×** today; the chosen Phase 1 brings it to **100×**, and the gated Phase 2 to
**20×**, moving the growth exponent from ×15.8 to ×4.0 per doubling.

Stating a linear target here would have made any real fix look like a failure.

## Why this is `high`

`LuaTypesSnapshot.forFile` is reachable from the resolve path. A user editing a well-annotated
module — precisely the code LuaCATS support exists to reward — can freeze the IDE for minutes.
The failure gets *worse* the more the user follows the conventions Lunar encourages.

It also blocks work: `DR-03` rejected the type route for `REFACT-01-08`'s colon form partly on
this, because putting `forFile` on `resolve()` would inherit the cost.

## Why the test suite never caught it

Measured during the same spike: across the **734** corpus `.lua` files, **zero** carry a
`---@class` (four carry any `---@` tag at all) — against 809 colon-method declarations and 16 336
colon-call tokens. The corpus exercises colon calls heavily and the annotated path not at all, so
the trigger condition is unsatisfiable on 100% of the corpus.

That is a coverage gap in its own right, and it is the reason a green corpus sweep says nothing
about annotated-file performance.

## Profiled — the answer is internal blowup, not cache thrash

Recorded 2026-08-29 with [[MAINT-38]] tier 1 (`-PjfrProfile`, `settings=profile`,
`stackdepth=1024`), against `LuaClassTagSnapshotPerformanceTest` at n = 80. Reproduced on the build
host at **9 387 ms annotated vs 177 ms for the tag-free control** — the same shape as the table
above, on a different machine. 2 335 `jdk.ExecutionSample` events over the run; 879 of them inside
`LuaTypesVisitor.buildSnapshot`. Recipe: [`docs/profiling.md`](../../../profiling.md).

**The fork the report left open is closed. It is one long invocation, not a thrashing cache.**

| Measurement over the 879 in-`buildSnapshot` samples | Value | What it rules out |
| :-- | ---: | :-- |
| stacks with exactly one `buildSnapshot` frame | 879 / 879 | re-entrant nesting |
| stacks with exactly one `CachedValuesManager.getCachedValue` frame | 879 / 879 | a nested cache compute |
| **distinct caller prefixes above `buildSnapshot`** (all 61 frames, to `EventDispatchThread.run`) | **1** | **cache thrash** — every sample is inside the *same* `getCachedValue` compute, reached from the single `forFile` call the harness makes |
| frames *below* `buildSnapshot` | 2 – 496, median 99 | — the variation is entirely on the callee side |

So `churnDependencyFor` / `dependenciesFor` (`LuaTypes.kt:281`) are **not** implicated in the
measured 9.4 s. The computed churn dependency is a legitimate thing to review on its own merits, but
it is not this defect: the cached value is computed once here and never invalidated. The `forFile`
call count is not the variable — the report's own series varies *call sites in the file*, not calls
to `forFile`.

### Where the time actually goes

**95 % of the in-`buildSnapshot` samples (835 / 879) are under `LuaTypeGraph.checkTypes`.** The AST
visit is nearly free by comparison: `LuaTypesVisitor.visitFile` / `visitBlock` appear in 44 samples
(5 %).

`checkTypes` (`LuaTypeGraph.kt:363-368`) walks the cross-product of `currentUpSet × currentDownSet`
and, for each admitted pair, reads three `VariableElement` properties:

| Property | Declared | Backing call | Samples containing it |
| :-- | :-- | :-- | ---: |
| `write` | `LuaTypeNodes.kt:125` | `resolveWrite` (`:171`) | 294 |
| `read` | `LuaTypeNodes.kt:126` | `resolveRead` (`:201`) | 376 |
| `declaredDemand` | `LuaTypeNodes.kt:140` | `resolveDeclaredDemand` (`:146`) | 123 |

Each is a `get()` that **starts a fresh `mutableSetOf()` and re-walks the graph from scratch on every
access**. Nothing memoizes the result. Within a *single* sampled stack the recursion reaches **82
nested `resolveRead` frames and 83 nested `resolveWrite` frames**, and the sampled stacks contain
15 891 repeated `resolveRead` and 12 404 repeated `resolveWrite` frames in aggregate.

The allocation profile agrees: the top leaf frame overall is `HashMap.putVal` (18 % of
in-`buildSnapshot` samples), and the top allocation sites are `Object[]` from `Arrays.copyOf`
(`toList()` on `resolveRead`'s demand sequence, `LuaTypeNodes.kt:190-201`) and `LinkedHashMap` from
`HashSet.<init>` — the per-call `visited` set. 10 437 `jdk.GCPhaseParallel` events over 29 s.

**Why the `---@class` tag is the trigger** — it is what makes the graph connected enough for those
walks to be deep. Remove it and the same 80 call sites finish in 177 ms, because the re-derivation
each pair triggers terminates almost immediately.

### What this does not say

- It does not say the fix is memoization. `resolveRead`/`resolveWrite` are cycle-guarded by a
  *caller-supplied* `visited` set, so a result is only valid relative to the walk that produced it;
  caching it naively would be a correctness change, and BUG-390 / BUG-419 / BUG-424 are all recorded
  in that code as defects caused by short-circuiting the walk. **Whoever fixes this needs to read
  those first.**
- It does not rule out cache invalidation being a *separate* problem in live IDE use, where `forFile`
  is called repeatedly from the resolve path rather than once from a harness. The profile scopes one
  `forFile` call, because that is what the measured 12 807 ms was.
- Absolute figures include Kover instrumentation (~2 % of samples). Relative attribution is unaffected.

## Where to look

**`LuaTypeGraph.checkTypes` and the three `VariableElement` accessors it drives**
(`LuaTypeNodes.kt:125`, `:126`, `:140`), per the profile above — not `forFile`'s caching, which the
profile ruled out.

An earlier version of this section pointed at `forFile` and suggested caching. That was written
before the recording existed and is wrong twice over: the cache is not thrashing, and caching the
walk is the specific hazard "What this does not say" warns about. The fix is **not** obvious and
this bug needs planning before implementation — start from BUG-390 (*type graph cycle guard
defeated by lazy nodes*), BUG-419 and BUG-424.

## Evidence

`docs/features/refactoring/01-rename-refactoring/risks-and-gaps.md`, `REFACT-01-00-DR-03`, and
`.agents/handoffs/REFACT-01-00-DR-03-phase-1.md`.

## Correction to the profiled diagnosis — it is `resolveRead`, and only `resolveRead`

Measured 2026-08-29 on the builder (`debian13`, `test -PwithPerf --tests
'*LuaClassTagSnapshotPerformanceTest*' --rerun --no-build-cache`) with throwaway counters compiled
into `LuaTypeNodes`/`LuaTypeGraph` and reverted afterwards. The profile's *location* is right; its
*weighting* is not.

**The decisive experiment**, all four numbers from the same single-`n` harness so they compare
directly. Clean engine at n = 80: **7 731 ms** and **8 087 ms** on two runs. Removing
`resolveWrite`'s recursion entirely — every interior `VariableElement` member of `upSet` skipped,
`92 064 935` interior calls reduced to **0**, verified by a counter: **8 044 ms** and **8 215 ms**.
**It did not help at all.** Adding the identical change to `resolveRead`: **2 518 ms**. Measured
again in the warmed-up sweep below, the same pair is 9 047 ms → 909 ms.

So the "three `VariableElement` accessors" framing above spreads the blame three ways when the
measurement puts effectively all of it on one:

| accessor | root calls | interior calls **per root** | productive node visits | effect of removing its recursion |
| :-- | ---: | ---: | ---: | :-- |
| `resolveWrite` (`LuaTypeNodes.kt:153`) | 14 336 | 6 422 | 1 123 795 | **none measurable** (7 731/8 087 → 8 044/8 215 ms) |
| `resolveRead` (`LuaTypeNodes.kt:182`) | 7 699 | 6 349 | 585 393 | **3.3× standalone, 9.9× warmed** (8 215 → 2 518 ms; 9 047 → 909 ms) |
| `resolveDeclaredDemand` (`LuaTypeNodes.kt:142`) | 7 296 | not counted | 578 508 | folded into the write experiment; none measurable |

Read the interior column carefully: `resolveWrite`'s 6 422 is a clean-engine measurement
(92 064 935 calls over 14 336 roots); `resolveRead`'s 6 349 is measured on the write-flattened spike
(92 065 177 over 14 501 roots), because the clean-engine read interior was never counted. The two
walks cost the same *per root* — the up- and down-closures are mirror images (`propagateBiEdge`,
`LuaTypeGraph.kt:853`) — and they still do not cost the same overall, because a *rejected* interior
call is one `LinkedHashSet.add` while a *productive* `resolveRead` visit materialises two collections
over the visited node's entire `downSet`
(`.asSequence().map{}.filter{}.toList()` at `LuaTypeNodes.kt:186-201`, then
`filterIsInstance<LuaGraphType.Table>()` at `:207`). The report already recorded that allocation
site and then weighted `write` alongside it anyway.

Three smaller corrections:

- **Depth is not the cost; breadth is.** "82 nested `resolveRead` frames" describes one stack. The
  quantity that scales is 585 393 productive `resolveRead` visits, each iterating a `downSet`
  averaging ~76 members and allocating two collections over it.
- **`resolveWrite (:171)` / `resolveRead (:201)` / `resolveDeclaredDemand (:146)` are sampled call
  sites inside those functions, not their declarations** (`:153`, `:182`, `:142`). Both readings are
  consistent with the source; the table reads as a declaration reference and is not one.
- **"Expected: no worse than linear" is not reachable by any strategy evaluated below.** The best
  measured result still grows ×4.0 per doubling, because the transitive closure the graph maintains
  by construction (`LuaTypeGraph.kt:9-10`) is itself O(n²) in a connected component. The target this
  report should be held to is *a fixed low polynomial degree and a bounded ratio against the
  tag-free control*, not linearity.

### Measured scaling, one harness, one machine

Same fixture generator, same JVM, `n` swept in a single test method after warm-up. `n = 10` is
JIT warm-up in every column and is excluded from the ratios.

| n | clean engine, annotated | clean engine, control | root-memo (§S1) | closure-flattened read (§S3) |
| ---: | ---: | ---: | ---: | ---: |
| 10 | 563 ms | 43 ms | 492 ms | 469 ms |
| 20 | 279 ms | 39 ms | 176 ms | 200 ms |
| 40 | 966 ms | 73 ms | 484 ms | 343 ms |
| 80 | 9 047 ms | 131 ms | 2 015 ms | 909 ms |
| 160 | **143 326 ms** | 212 ms | 27 276 ms | **3 649 ms** |
| growth per doubling | ×3.5, ×9.4, ×15.8 | flat | ×2.8, ×4.2, ×13.5 | ×1.7, ×2.6, ×4.0 |
| ratio to control at n = 160 | **676×** | 1× | 100× | **20×** |

### Graph shape at n = 80, annotated

`nodes = 1 067`, of which `817` are `VariableElement`. `checkTypes` runs **2** fixed-point
iterations and admits **14 508** pairs; **21 138** edge additions occur *inside* the first
iteration. Root accessor calls during `checkTypes`: `write` 14 336, `read` 7 699, `declaredDemand`
7 296. Distinct `(node, graph-revision)` keys among those same calls: **7 211 / 647 / 325** — i.e.
50 %, 92 % and 96 % of the calls re-derive a result that has not changed since the last one.

**The 5 s cutoff never fires.** `checkTypes(timeLimitMs = 5000)` (`LuaTypeGraph.kt:299`) is tested
once per fixed-point iteration (`:319-322`), and iteration 1 alone took 7 253 ms. The designed safety
break is structurally unable to bound a single iteration.

## What BUG-390, BUG-419 and BUG-424 teach, and how this plan answers each

**BUG-390 — the guard must survive every hop, and a special case is how it escapes.** The defect was
a `is VariableElement` branch threading `visited` sitting *above* a generic `is ValueNode -> it.write`
branch that did not; a lazy hop therefore restarted the guard with an empty set and recursed to a
`StackOverflowError` on 30–43 % of corpus files. The fix collapsed both branches into the single
`is ValueNode -> it.writeWith(visited)` at `LuaTypeNodes.kt:171` *precisely so the split cannot be
reintroduced*, and the comment at `:167-170` says so.
**How this plan answers it:** §S1 adds no branch to `resolveWrite`/`resolveRead`/
`resolveDeclaredDemand` and does not touch `writeWith` (`:38`, `:108`, `:128`). It intercepts only
the three public accessors (`:125`, `:126`, `:140`), each of which is by construction a walk *root*
— it allocates the `mutableSetOf()` itself. The interior entry points (`writeWith` at `:128`,
`resolveRead` at `:190`, `resolveDeclaredDemand` at `:146`) are never memo-consulted, so no path
that carries a caller's guard is altered. §S3, which *does* change a walk, is quarantined behind a
differential-equivalence gate (below) rather than shipped on reasoning.

**BUG-419 — a verdict requires knowing both sides, and a flag lost in the middle of a walk is a
silent behaviour change.** Its shipped fix turned on `UseNode.declaredDemand`, and the part worth
carrying here is `VariableElement.declaredDemand` (`:140`): inheriting the `false` default demoted
*every* declared-contract violation reached through a call, because the pair actually checked is
(value, *variable*) and not (value, `@param` use node). A change in the middle of a resolution that
looks like an optimisation can silently change which diagnostics are emitted.
**How this plan answers it:** the emission surface is asserted directly, not inferred. The gate
includes a `-PwithCorpus` sweep whose `LuaCorpusSweepTest.sweepAndRatchet` baselines count
`LuaTypeAssignability` emissions per member, and the acceptance condition is **byte-identical
baselines**, not "no test failed". §S1 is expected to be exactly identical; §S3 is measured *not* to
be (below), which is why it is a separate phase.

**BUG-424 — a fixture where nothing resolves cannot demonstrate the absence of a diagnostic.** The
first metamethod probe read zero false positives because both operands were imprecise; the class was
in fact the largest false-positive source, visible only once one side was precisely typed. Its second
lesson is in this file: the `is UseNode -> it.read` branch at `LuaTypeNodes.kt:191-197` deliberately
**preserves** the trait rather than projecting it, and the comment records that projecting at that hop
lost the metamethod arm for every value reaching an operator through a variable.
**How this plan answers it:** the regression fixtures are annotated and precisely typed on one side —
the `---@class` fixture this bug is about is exactly that shape — and no proposal below changes what
`resolveRead` *returns* for a `UseNode` member. §S3 changes the *order* in which those members are
consulted, which is why its risk register names `mergeTableDemands` (`:212`, BUG-395) and the trait
branch explicitly.

## Fix Strategy

Four strategies were implemented as throwaway spikes and measured; the tree was restored after each.

### S1 — memoize the three root accessors, keyed on a graph revision counter. **CHOSEN, Phase 1.**

`VariableElement` gains three memo fields, each an immutable `(revision, value)` reference:

- `write` (`:125`), `read` (`:126`) and `declaredDemand` (`:140`) return the memo when its revision
  equals the graph's current revision, else compute, store and return.
- `LuaTypeGraph` gains a `revision: Long`, incremented at exactly three places: the successful
  `to.upSet.add(from)` in `propagateDownward` (`:826`), the successful `from.downSet.add(to)` in
  `propagateUpward` (`:840`), and every `_nodes += node` in the four factories (`value`, `lazyValue`,
  `use`, `variable`). A fourth bump is required at `LuaGraphType.kt:393-394`, which mutates
  `memberNode.upSet` / `downSet` **directly, bypassing `addEdge`** — the one production site that
  does.
- Node creation must bump it because a node can join a `LuaGraphType.Table`'s `localMembers` *after*
  the `Table` was constructed (`LuaGraphType.kt:378-379`, `LuaTypesVisitor.kt:756`), which changes a
  demand without changing an edge.

**Measured:** ×4.5 at n = 80, ×5.3 at n = 160, ratio-to-control 676× → 100×. The spike used a
JVM-global counter, which *over*-invalidates (another file's graph bumps this one's revision) and is
therefore a **lower bound** on the per-graph variant's hit rate. Perf suite green (`failures="0"`).

**What it does not do:** the exponent is unchanged (×13.5 per doubling at the top). S1 is a constant
factor, not a fix for the growth curve.

### S2 — exploit the closure invariant for `resolveWrite` / `resolveDeclaredDemand`. **REJECTED.**

The graph maintains a transitive closure by construction (`LuaTypeGraph.kt:9-10`,
`propagateDownward`/`propagateUpward`), so every node reachable from `M` is *already a direct member*
of `M.upSet` and the recursion into `VariableElement` members re-derives a subset. Skipping those
members makes the walk one level deep.

Audited on the n = 80 annotated graph and on the tag-free control: **`upViol = 0`, `downViol = 0`**
over 1 632 variable nodes — the closure invariant holds exactly. Differential audit of flattened vs
recursive results: **`write` 0 mismatches / 1 632**, **`declaredDemand` 0 / 1 632**. The
transformation is sound for both, because `resolveWrite` folds with a union that drops `Undefined`
(`:158-164`) and `resolveDeclaredDemand` folds with `any` — both idempotent and order-insensitive.

**Rejected anyway, on measurement:** it buys nothing (8 044 → 8 215 ms, inside noise) while touching
the exact code BUG-390 was filed against. A correctness-neutral change with no measured benefit is
not worth the review surface.

### S3 — the same flattening for `resolveRead`. **Phase 2, gated.**

**Measured:** ×9.9 at n = 80 (warmed sweep; ×3.3 on the standalone harness), ×39 at n = 160, ratio-to-control 676× → 20×, and the growth curve
drops from ×15.8 to ×4.0 per doubling. It is the only strategy measured to change the exponent.

**And it is not equivalent.** `resolveRead` folds with **first-wins** (`demands.firstOrNull()`,
`:209`) plus a >1-table merge (`:207-208`, BUG-395), so it is order-dependent where the other two
are not, and the recursion collapses each variable child to *one* demand while the flattened form
sees all of that child's demands separately. Differential audit on the n = 80 annotated graph:
**1 mismatch / 817 nodes.** Not zero.

Phase 2 therefore ships only if a differential harness — the flattened result computed alongside the
recursive one, mismatches counted, over the **full unit suite and the `-PwithCorpus` sweep** — either
reports zero, or reports a set small enough to enumerate and adjudicate one by one. That harness is a
task in its own right; see DR-4.

### S4 — hoist `useNode.read` / `useNode.declaredDemand` out of the inner `valueNode` loop. **REJECTED.**

The natural reading of `LuaTypeGraph.kt:355-372` is that `useNode.read` and `useNode.declaredDemand`
are recomputed for every `valueNode`. They are — but only *inside* `if (checkedPairs.add(pair))`
(`:361`), so a `useNode` whose pairs are all already checked pays nothing. Hoisting them to the
`useNode` loop head makes them unconditional: **measured, root `read` calls went up, 7 699 → 14 501.**
The redundancy is real (92 % by `(node, revision)`) but it is captured by S1 without changing when
anything is evaluated. A *lazy* hoist would be a second mechanism for the same win and is not worth
the second mechanism.

### S5 — reduce the per-visit allocation inside `resolveRead`. **Adjunct, optional.**

`LuaTypeNodes.kt:185-209` builds a sequence, maps, filters, `toList()`s, then `filterIsInstance`s —
two collections per visit, 585 393 visits at n = 80. A single pass that tracks the first non-`Any` demand and
only materialises a table list when a second `Table` appears is result-identical. It is a constant
factor and **cannot change the exponent**, so it is worth doing only alongside S1/S3, never instead.

### S6 — shrink the cross-product or the closure itself. **Out of scope, file separately.**

`checkedPairs` (`:301`) already bounds each pair to one check for the life of the run, and the
measured pair count (14 508) tracks the closure size, not redundant checking. Making the closure
sparser — the actual O(n²) source — is an inference-engine redesign, not a bug fix.

### Recommended order

1. **Phase 1 = S1**, plus the call-count assertion below. Safe, measured 5×, ratio 676× → 100×.
2. **Phase 2 = S3 (+ S5)**, only after DR-4's differential harness reports a mismatch set that can be
   adjudicated. Measured 39×, ratio 676× → 20×.

**The safe fix is smaller than the fast one, and that is the honest outcome here.** Phase 1 alone does
not meet the "Expected" section as written; it makes an 80-call-site annotated file usable (2 s) and
leaves a 160-call-site one bad (27 s). Phase 2 is what makes the curve tolerable, and it is the phase
that can change what the engine infers.

## Correctness argument — what the `visited` set guarantees, and why S1 preserves it

**The invariant.** Within one resolution walk, each `VariableNode` contributes at most once, and a
re-entry returns the *neutral element of that walk's fold*:

| walk | guard line | re-entry returns | fold | neutral because |
| :-- | :-- | :-- | :-- | :-- |
| `resolveWrite` | `LuaTypeNodes.kt:154` | `Undefined` | union | `flatten` drops `Undefined` (`:161`) |
| `resolveRead` | `:183` | `Any` | intersection, first-wins | `.filter { it != LuaGraphType.Any }` (`:200`) |
| `resolveDeclaredDemand` | `:143` | `false` | `any` | `false` is the identity of `∨` |

So the guard is simultaneously the termination device and the fold's identity. A result computed
*under a non-empty guard* is therefore walk-relative: it is missing the contribution of every node
the enclosing walk had already entered.

**Why the memo is not walk-relative.** The three public accessors (`:125`, `:126`, `:140`) each open a
walk with a **freshly allocated, empty** `mutableSetOf()`. At that entry the guard contributes
nothing, so the value returned is a pure function of the graph's node and edge state — the same
function, for the same node, until that state changes. S1 memoizes exactly and only that function.

**Where the memo must never be consulted**, stated as an implementation constraint rather than an
expectation: `ValueNode.writeWith` (`:38`), `LazyValueElement.writeWith` (`:108`),
`VariableElement.writeWith` (`:128`), the `is VariableElement -> it.resolveRead(visited)` branch
(`:190`), and `it.resolveDeclaredDemand(visited)` (`:146`). Every one of these receives a caller's
guard. Consulting the memo from any of them reintroduces BUG-390 in reverse — instead of losing the
guard, it would *ignore* it, returning a full result where the guard requires the neutral element,
and a cycle would resolve to a type instead of `Undefined`. The regression test below asserts this
directly.

**Completeness of the invalidation key.** The memoized value depends on: the node's `upSet`/`downSet`
membership; `UseElement.read` / `ValueElement.write`, both `val`s fixed at construction; and the
contents of a `LuaGraphType.Table` reachable from either, whose `localMembers` map is populated after
the `Table` is constructed. Bumping the revision on every edge addition **and** every node creation
covers all three, because a member node is a node. Over-invalidation is always sound — it forces a
recomputation that returns the same answer — so an imprecise counter costs speed, never correctness.

**Threading.** Each memo is a single reference field holding an immutable `(revision, value)` pair, so
a concurrent reader sees either the previous pair or the new one, never a torn one (`kotlin.Pair`'s
components are `final`, so publication is safe). A stale read forces a recomputation and is harmless.
Do **not** split it into two fields. The snapshot outlives `checkTypes` and is read from the
annotator, completion, and inlay-hint paths, so this is a real concurrency surface and not a
formality.

**What cannot be argued, only measured.** That S1 changes no *diagnostic*. The argument above says
each accessor returns the same value; it does not by itself prove the emission set is unchanged,
because `checkTypes` interleaves reads with `addEdge` and a revision bump could in principle
reorder nothing but still be got wrong in implementation. The corpus baselines are the assertion.

## Test Strategy

### 1. A performance assertion that can actually fail

`LuaClassTagSnapshotPerformanceTest` is the right *fixture* and the wrong *budget*, and its own KDoc
(`:22-25`) says why: a wall-clock budget "would go red on the fix as readily as on a regression", and
the repo's existing budget suites did not catch this defect. Two assertions replace it, and only one
of them is a timing assertion.

**1a. Ratio against the in-JVM control — the timing assertion.** In one test method, one JVM, after
warm-up, measure `LuaTypesSnapshot.forFile` on the annotated fixture at n = 160 and on the identical
fixture with the tag removed, and assert `annotated / control ≤ 200`. The control absorbs machine
speed, CI contention and JIT state — the three things that make an absolute millisecond budget flaky
— so the threshold is a property of the engine, not of the host.

Defensibility of 200, from the table above: pre-fix **676**, post-S1 **100**, post-S3 **20**. It sits
2× above the Phase 1 result and 3.4× below the defect, so a regression restoring a third of the
defect trips it, and normal noise does not. Tighten to 40 when Phase 2 lands; do not tighten it
speculatively.

Stays behind the `*Performance*` / `-PwithPerf` filter (`build.gradle.kts:272-276`): at n = 160 the
pre-fix case is 143 s, which has no place in the routine loop. Note the recorded caveat at
`build.gradle.kts:267-271` that the perf suites have a warm-up/isolation dependency and are expected
to be run as part of a full `-PwithPerf` run.

**1b. A root-resolution call-count budget — the assertion that cannot be flaky.** The quantity that
actually regressed is not milliseconds, it is root accessor calls. Add `@TestOnly` counters to
`LuaTypeGraph`, in the same spirit as the existing `compatMemoSize()` (`LuaTypeGraph.kt:863`),
exposing the number of root `write` / `read` / `declaredDemand` resolutions performed during
`checkTypes`, and assert fixed bounds for the n = 80 fixture. Measured pre-fix: **14 336 / 7 699 /
7 296**; S1's distinct-key counts bound them at **7 211 / 647 / 325**. This is deterministic given
the fixture, independent of the machine, and fails on exactly the regression this report describes —
so it belongs in a normally-named test that the **routine loop runs**, not behind `-PwithPerf`.

**Both assertions get a mutation proof** (`mutation-proof` skill): revert the memo, confirm each goes
red; re-apply, confirm each goes green. An unproven perf assertion is the instrument that failed here
already.

### 2. Correctness regression tests

Home: `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaTypeGraphCycleGuardTest.kt`, which
already owns BUG-390 and BUG-427 and constructs graphs directly.

- **`guardedInteriorEntryIgnoresTheMemo`** — the test that fails if the fix breaks the cycle guard,
  drawn from BUG-390's shape. Build a variable `v` whose `write` resolves to a concrete type; read
  `v.write` so the memo is populated; then call `v.writeWith(mutableSetOf(v))` and assert it returns
  `LuaGraphType.Undefined`, **not** the memoized type. Mutation proof: make `writeWith` consult the
  memo — the test must go red. Repeat for `resolveRead`'s interior entry (expect `Any`).
- **`memoIsInvalidatedByALaterEdge`** — read `v.write`, then `graph.addEdge(graph.value(anchor,
  LuaGraphType.Number), v)`, then assert `v.write` reflects the new value. Mutation proof: delete the
  bump in `propagateDownward` — red.
- **`memoIsInvalidatedByALaterMemberNode`** — the `LuaGraphType.kt:393-394` path that bypasses
  `addEdge`. This is the bump a reviewer is most likely to miss, so it gets its own test.
- **`lazyNodeCycleTerminatesInsteadOfOverflowing`** and its two siblings already in the file must stay
  green unmodified. If any of them needs editing, the change is wrong.

### 3. Behavioural gates (not new tests — existing ones, run and compared)

- Full unit suite, `--rerun --no-build-cache` (a cached `:test` reports a pass having run nothing).
- `-PwithCorpus`, comparing `LuaCorpusSweepTest.sweepAndRatchet` baselines **byte-for-byte**, not
  merely "green". A `LuaTypeAssignability` count that moves is a Phase-1 failure.
- `ktlintCheck`.

## Blast radius

`VariableElement.write` / `.read` / `.declaredDemand` are read from, at minimum:

- **The engine itself** — `LuaTypeGraph.addEdge` (`:136-141`), `doInstantiateGeneric` (`:191`),
  `checkTypes` (`:346`, `:353-354`, `:362-368`), `checkTableCompatibility` (`:784-785`),
  `checkFunctionCompatibility`, `LuaTypeNodes.mergeTableDemands` (`:212`).
- **Snapshot query** — `LuaTypes.kt:76`, `:99` (`typeOf`), `LuaUnionDiagnostics.kt:65`.
- **The visitor, mid-traversal** — `LuaTypesVisitor.kt` at `:168`, `:194`, `:345`, `:471`, `:473`,
  `:657-658`, `:958-962`, `:985`, `:989`, `:1136`, `:1271`. These run **while edges are still being
  added**, which is why an invalidation key that covers only edges (and not nodes) is not enough.
- **User-visible surfaces** — `LuaCompletionContributor.kt:502`, `LuaInferredTypeAnnotator.kt:52`,
  `LuaTypeInlayHintProvider.kt:139`, `LuaMethodChainInlayHintProvider.kt:240`,
  `LuaOverrideLineMarkerProvider.kt:120`, and downstream of the emission set,
  `LuaTypeAssignabilityInspection`, `LuaReturnTypeMismatchInspection`, `LuaTypeHypothesisAnnotator`.

**The corpus cannot confirm this fix, and must still be run.** Zero of the 734 corpus files carry a
`---@class`, so a green sweep says nothing about the annotated path — but the sweep is what detects a
*general* inference regression, and it is exactly what caught BUG-390's 131-file highlight failure.
Both statements are true at once. Separately, **a `---@class`-bearing fixture must be added to the
corpus** or this coverage gap survives the fix; see DR-6.

## Phase 1 — delivered (S1)

Shipped on `fix-bug-473-phase1`. **Phase 2 (S3) remains open and this report stays `planned`
because of it** — S1 is a constant factor and does not touch the growth exponent, which is the part
the "Expected" section is held to.

### What was memoized, and what deliberately was not

`VariableElement` gains three one-slot `RootMemo`s, each holding an immutable `(revision, value)`
pair. They are read and written in exactly one place — the private `atRoot` helper reached from
`write` (`LuaTypeNodes.kt:130`), `read` (`:133`) and `declaredDemand` (`:151`), the three accessors
that allocate the guard themselves.

Not memoized, and asserted so by test: `ValueNode.writeWith`, `LazyValueElement.writeWith`,
`VariableElement.writeWith`, the `is VariableElement -> it.resolveRead(visited)` hop and
`it.resolveDeclaredDemand(visited)`. Each receives a caller's guard, so each owes its fold's neutral
element on re-entry, not a full result.

### The invalidation key — per-graph, and structural rather than enumerated

**DR-1 is settled as per-graph.** `VariableElement` now takes a `LuaTypeGraph` back-reference and
validates against `LuaTypeGraph.revision`. The reason is not only the hit rate the spike's global
counter under-measured: a JVM-global counter would make the root-resolution **counts** below
order-dependent across a shared-JVM test run, and the deterministic count assertion is the primary
gate. Retention is unchanged — the graph already holds every node in `_nodes`, and the snapshot
already holds the graph, so the back-reference closes a cycle inside one object group with one
lifetime. It holds no `Project`, `Editor`, `PsiFile` or `VirtualFile` that was not already reachable
from `TypeNode.element`.

**The bump moved into `OrderedSet` rather than being enumerated at call sites, and this is a
deliberate deviation from the plan.** S1 named the two `propagate*` sites plus a fourth bump at
`LuaGraphType.kt:393-394`. Instead every successful `upSet`/`downSet` insertion bumps, because
`OrderedSet` fires an `onAdd` hook that `VariableElement` wires to `LuaTypeGraph.bumpRevision`.
Node creation still bumps in all four factories.

The enumerated key was **measured insufficient**, not merely judged so — mutant M6 below implements
exactly the plan's three edge sites and leaves `memoIsInvalidatedByAnEdgeAddedOutsideAddEdge` red.
`LuaGraphType.memberNodeFor` is not the only bypasser: six graph-building tests mutate the sets
directly too, and any future one would silently inherit a stale memo. Making the set responsible for
its own invalidation removes the checklist.

### Measured, on the builder (`debian13`), same fixture and JVM in every column

| | pre-fix | post-S1 |
| :-- | ---: | ---: |
| annotated / control ratio at n = 160 | **678.8×** | **87.2×** |
| annotated at n = 160 | 145 269 ms | 21 284 ms |
| control at n = 160 | 214 ms | 244 ms |
| annotated at n = 80 | 9 387 ms (the profiled run) | 2 621 ms |
| root `write` resolutions at n = 80 | 14 417 | **7 292** (1.98×) |
| root `read` resolutions at n = 80 | 7 699 | **647** (11.9×) |
| root `declaredDemand` resolutions at n = 80 | 7 296 | **325** (22.4×) |

The ratio result is better than S1's predicted 100×, which is what DR-1 predicted would happen: the
spike's JVM-global counter over-invalidated and its numbers were a lower bound.

Every count above was re-measured on this host, pre-fix figures included. Five of the six land on
the report's predictions **exactly** — pre-fix `read` 7 699 and `declaredDemand` 7 296, post-fix
distinct keys 647 and 325. Both `write` figures are 81 over their prediction, the same 81 in each,
because the shipped counter is cumulative over the graph's life and so includes the snapshot's own
post-`checkTypes` reads (one per call site, plus the receiver) where the spike's counter was
`checkTypes`-scoped.

### The behavioural gate — compared, not just green

The `-PwithCorpus` sweep is **2 893 / 0 failures / 0 errors / 1 skipped** and reports three
`IMPROVED` lines, all `inspection.LuaUnusedLocal` (130 → 121, 77 → 55, 14 → 7). Those are **not
this change**: the identical three lines, with identical numbers, come out of a sweep run on
pristine `75957547` with every source edit reverted. They are pre-existing drift, from a commit
after the baselines were last recorded at `48eabe7d` — `f4b49c47` (BUG-472/470) changed how a
local's usages bind, which is what `LuaUnusedLocal` counts.

`LuaTypeAssignability` — the count BUG-419's lesson names as the one that must not move — did not
move in either run, and the five `.baseline` files are byte-identical before and after
(`sha256sum -c`, all OK). The comparison is treatment against control, which is the assertion the
plan asked for; "no test failed" would not have distinguished these three lines from a regression.

### Tests, each with the mutant that proved it

| test | mutant | result |
| :-- | :-- | :-- |
| `LuaTypeGraphRootResolutionBudgetTest` (routine loop) | M1 — `atRoot` never consults the memo | **red**: `write` 14 417 over the 8 500 budget |
| `…PerformanceTest.testAnnotatedSnapshotStaysWithinRatioOfTheUnannotatedControl` | M1 | **red**: ratio 678.8 over the 200 limit |
| `guardedWriteEntryIgnoresTheMemo` | M2 — `writeWith` serves `writeMemo` | **red**: `expected Undefined but was String` — the cycle resolved to a type |
| `guardedReadEntryIgnoresTheMemo` | M3 — the interior `is VariableElement` read hop serves `readMemo` | **red**: `expected Number but was String` |
| `memoIsInvalidatedByALaterEdge` | M4 — `OrderedSet.add` stops bumping | **red**: stale `String`, not the widened union |
| `memoIsInvalidatedByAnEdgeAddedOutsideAddEdge` | M4 | **red**: same |
| `memoIsInvalidatedByAnEdgeAddedOutsideAddEdge` | M6 — the plan's enumerated key (`propagate*` only) | **red**, while `memoIsInvalidatedByALaterEdge` stays green |

**One thing in the plan is not what it says it is, and no test covers it.** Mutant M5 removed the
node-creation bumps from all four factories and the whole type-engine suite stayed **green**. The
plan's stated reason for them — "a member node joins a `Table`'s `localMembers` *after* the `Table`
was constructed, which changes a demand without changing an edge" — does not produce staleness by
that route, because the memoized `LuaGraphType.Table` holds *the same mutable map instance* that is
later populated, so the memo observes the addition rather than missing it. The bumps are kept
anyway, on a different and narrower argument: `mergeTableDemands` (`LuaTypeNodes.kt:212`) builds a
**new** `Table` copying entries out of its inputs, and that copy does not alias them. Over-
invalidation is sound and the measured result above was obtained with the bumps in place, so they
cost nothing established. **Recorded as unproven rather than reported as covered.**

### What Phase 1 does not achieve

The exponent is untouched. An 80-call-site annotated file is usable at 2.6 s; a 160-call-site one is
still 21 s. Closing that is S3, and S3 is gated on DR-4.

## De-risking tasks — what was not run

| id | item | why it is open |
| :-- | :-- | :-- |
| DR-1 | Per-graph revision counter | **Settled — per-graph, shipped.** `VariableElement` carries a `LuaTypeGraph` back-reference and validates against `LuaTypeGraph.revision`. Decisive reason: a global counter makes the root-resolution counts order-dependent across a shared-JVM test run, and that count is the primary gate. The predicted lower-bound effect showed up — 87.2× against the spike's 100×. |
| DR-2 | Full unit suite + `-PwithCorpus` under S1 | **Run.** Ordinary suite `--rerun --no-build-cache`: 2 885 / 0 / 0 / 1 (2 880 before, +5 new). `-PwithCorpus`: 2 893 / 0 / 0 / 1. Baselines byte-identical; the three `LuaUnusedLocal` `IMPROVED` lines reproduce on pristine `75957547` and are pre-existing. |
| DR-3 | `ktlintCheck` | **Run, clean, no format pass needed.** `run ktlintCheck` alone (never paired with `ktlintFormat`, BUG-445). |
| DR-4 | S3 differential-equivalence harness | Flattened vs recursive `read`, mismatches counted over the full suite and the corpus. Measured 1/817 on the n = 80 fixture alone; the population-wide number is unknown and Phase 2 is blocked on it. |
| DR-5 | `timeLimitMs` granularity | `checkTypes(timeLimitMs = 5000)` (`:299`) is checked once per fixed-point iteration (`:319-322`); iteration 1 measured 7 253 ms without tripping. Decide whether the cutoff belongs inside the node loop, and whether `ProgressManager.checkCanceled()` belongs there too — this path runs on the EDT in the reproduction. |
| DR-6 | A `---@class` corpus fixture | The trigger condition is unsatisfiable on 100 % of the corpus. Until a fixture carries one, no sweep can regress-detect this bug. |
| DR-7 | Live IDE verification | The measured harness calls `forFile` directly. The user-visible symptom is a freeze while editing an annotated module; confirm via the `verify-in-ide` flow. |
