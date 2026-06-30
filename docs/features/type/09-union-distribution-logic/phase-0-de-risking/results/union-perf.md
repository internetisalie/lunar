---
id: "TYPE-09-P0-01-RESULTS"
title: "Distribution Cost Bound — Spike Results"
type: "design"
parent_id: "TYPE-09-P0"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-0-de-risking/requirements|requirements]]"
---

# TYPE-09-P0-01 — Distribution Cost Bound (Spike Results)

**Deliverable for:** `TYPE-09-P0-01` · **Test:** `LuaUnionDistributionSpikeTest`
(`src/test/kotlin/net/internetisalie/lunar/lang/types/LuaUnionDistributionSpikeTest.kt`)

## Question

Do the breadth-100 / depth-10 limits from parent `design.md` §2.3.1 keep union distribution
tractable, and does the existing engine blow up *without* them?

## Method

- Built disjoint single-field **exact** tables and unioned them into `A1|…|A100` vs `B1|…|B100`.
  Each table's single field is a **required** (non-nil) `Number`, so the structural compatibility
  check does real per-pair work instead of short-circuiting on empty tables.
- Drove the distribution through two THROWAWAY recursive copies of the real
  `LuaTypeGraph.isCompatible` rule (`value is Union → all`, `use is Union → any`, table →
  structural), one **unbounded** and one **bounded** (depth > 10 ⇒ `false`; breadth > 100 ⇒
  shallow head-match fallback). Both share the same `visited: Set<Pair<value,use>>` guard the
  production code uses.
- Also timed the **production path** `LuaTypeGraph.addEdge(value(unionA), use(unionB))`, which
  invokes `checkCompatibility → isCompatible` synchronously.
- 100 iterations each after a 20-iteration JIT warm-up; **median** reported via `System.nanoTime()`.
- Probed a **deep nested-union** shape (12 levels, exceeds depth 10) and a **pathological
  "wide tables nested deep"** shape (width 8 × depth 14) to try to defeat the visited guard, with
  a 5 s watchdog on the unbounded run.

## Measured numbers

JVM: Corretto 21.0.10. Single representative run (sub-millisecond, so absolute values jitter run
to run; the *relationships* are stable).

| Shape | Bounded (median ms) | Unbounded (median ms) | Production `addEdge` (median ms) |
| :--- | ---: | ---: | ---: |
| 100 × 100 disjoint unions | **0.125** | 0.133 | 0.250 |
| Deep nested union (12 levels) | 0.0017 | — | — |
| Pathological (width 8 × depth 14) | 0.049 | 0.060 | — |

- `ratio unbounded / bounded` (100×100): **≈ 1.07** (bounded and unbounded are effectively equal).
- Pathological ratio: **≈ 1.23** (unbounded terminated in < 5 s; never blew up).
- Deep nested result: terminated, returned `true`.

## Result vs success criterion

| Criterion (requirements.md / design.md §1) | Outcome |
| :--- | :--- |
| With limits, median **< 50 ms** for the 100×100 check | **PASS** — 0.125 ms (≈ 400× under budget). Asserted in-test. |
| Without limits, **demonstrably worse (≥10×) or non-terminating within 5 s** | **NOT REPRODUCED** — unbounded is within ~7–23 % of bounded and always terminated. |

**TC-TYPE-09-P0-01: PASS** (the limit keeps the check well under the 50 ms budget).

## Honest finding: the blow-up does not reproduce

The "without limits it blows up" half of the criterion **did not reproduce**, and this is the
correct, expected empirical result (the spike brief explicitly anticipated it). The reason is
structural, not luck:

1. **The `visited` guard already bounds a single distribution.** `isCompatible`
   (`LuaTypeGraph.kt:338`) and `checkCompatibility` (`:258`) add each `(value, use)` *type-pair*
   to a `visited` set and return early on a repeat. A `Union` is a `Set`, so distribution over
   distinct members visits each pair once; there is no exponential re-expansion of the same pairs.
2. **Structural sharing collapses nesting.** Nested/recursive unions that share sub-structure
   resolve to the same `LuaGraphType` instances, so the visited-pair set collapses them — which is
   why even the width-8 × depth-14 pathological shape stayed sub-millisecond.

To force a true combinatorial explosion you would need a graph that generates a fresh, distinct
type instance at every recursion node (no sharing, no pair repeats) — which is **not**
representative of how `LuaTypesVisitor` builds real graphs from Lua source.

## Go / No-Go on the breadth-100 / depth-10 limits

**GO — keep the limits, but reclassify them as defense-in-depth, not the primary perf mechanism.**

- The *primary* protection against combinatorial explosion (`TYPE-DR-01`) is the **existing
  `visited` guard**, which P2 must preserve.
- The breadth-100 / depth-10 limits remain worthwhile as **cheap, deterministic backstops** for
  adversarial or pathological inputs (e.g. machine-generated unions, future features that *do*
  mint fresh per-node instances such as generic instantiation or discriminant expansion). They
  cost nothing on normal code (breadth ≤ 100, depth ≤ 10 never trip).

### Recommended deltas to P2 design

1. **Re-frame §2.3.1** in parent `design.md`: state that the `visited`-pair guard is the load-
   bearing bound and the breadth/depth limits are secondary safety rails. The current wording
   ("To prevent Combinatorial Explosion … the check will implement the following limits") over-
   credits the limits.
2. **Depth-10 `return false` is a soundness hazard.** Returning *incompatible* on depth overflow
   can produce **false-positive type errors** on legitimately deep (but valid) types. Prefer
   returning **`true`/"assume compatible"** on the depth cutoff (consistent with how the `visited`
   guard already returns `true` on a cycle), and surface a diagnostic/log rather than a user-facing
   error. P2 should decide this explicitly.
3. **Keep the 50 ms budget as a regression gate**, not a live runtime check — there is ~400× of
   headroom, so a CI perf assertion (like this spike's) is sufficient; no need for a runtime timer
   on the hot path beyond the existing `checkTypes()` iteration/time guards.

## Status

`TYPE-09-P0-01` **DONE** — measurable criterion met (bounded < 50 ms) and deliverable produced.
