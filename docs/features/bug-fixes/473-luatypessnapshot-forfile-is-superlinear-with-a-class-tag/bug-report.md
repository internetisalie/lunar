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

## Measured — timings only; there is no profile

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

**There is no profiling data, and no way to obtain any — see [[MAINT-38]].** No flame graph, no
call-hierarchy trace, no per-method breakdown, no allocation counts, no cache hit/miss
instrumentation. The repo has three performance *tests* (`GlobalSymbolCompletionPerformanceTest`,
`GlobalSymbolPerformanceOptimizationTest`, `LuaUnionDistributionBenchmarkTest`) which assert
wall-clock budgets — none of them caught this — but nothing that attributes time to frames.
`LuaTypeSourceRecorder` is not profiling: its `SourceFrame` records which files and keys a
snapshot consumed, which is a cache-correctness question.

**So the first task is not to fix this bug — it is MAINT-38.** Diagnosing this one by reading
source is guesswork, and the same blindness applies to every performance question after it.

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

## Where to look

`LuaTypesSnapshot.forFile`, and what it recomputes per call site once a class is in scope. The
shape of the growth suggests work repeated per call site that should be computed once per file or
cached — see the engineering contract's `CachedValuesManager` rule for bindings.

## Evidence

`docs/features/refactoring/01-rename-refactoring/risks-and-gaps.md`, `REFACT-01-00-DR-03`, and
`.agents/handoffs/REFACT-01-00-DR-03-phase-1.md`.
