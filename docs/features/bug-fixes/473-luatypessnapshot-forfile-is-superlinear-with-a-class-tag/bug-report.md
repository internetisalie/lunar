---
id: "BUG-473"
title: "`LuaTypesSnapshot.forFile` is superlinear in call-site count once a `@class` tag is present"
type: "bug"
parent_id: "BUG"
status: "todo"
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

Snapshot cost grows no worse than linearly in call-site count, and an annotated file is not
dramatically more expensive than an unannotated one of the same size.

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

`LuaTypesSnapshot.forFile`, and what it recomputes per call site once a class is in scope. The
shape of the growth suggests work repeated per call site that should be computed once per file or
cached — see the engineering contract's `CachedValuesManager` rule for bindings.

## Evidence

`docs/features/refactoring/01-rename-refactoring/risks-and-gaps.md`, `REFACT-01-00-DR-03`, and
`.agents/handoffs/REFACT-01-00-DR-03-phase-1.md`.
