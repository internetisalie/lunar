---
id: "BUG-474"
title: "A ratio gate amplifies machine speed instead of dividing it out, when one side is superlinear"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-474: `LuaClassTagSnapshotPerformanceTest`'s ratio assertion is not host-portable

Found 2026-08-29 by [[BUG-473]]'s DR-4, which measured the test failing on a host where nothing
was wrong with the code.

## Reproduction

Run `LuaClassTagSnapshotPerformanceTest.testAnnotatedSnapshotStaysWithinRatioOfTheUnannotatedControl`
at `df332c34` — Phase 1 merged, **no source edits** — on the **GCE** builder rather than libvirt.

## Expected

The assertion is a *ratio* against an in-JVM control precisely so it does not depend on host speed.
The same tree should pass on either builder.

## Actual

| host | annotated | control | ratio | limit |
| :--- | ---: | ---: | ---: | ---: |
| libvirt | — | — | **87.2×** | 200 |
| GCE | 39 761 ms | 117 ms | **339.84×** | 200 |

Same commit, same test, **fails on one host and passes on the other** by a factor of nearly four.

Confirmed not to be observer effect: instrumented 365.3×, reverted 339.8×, and the remote sources
were verified free of instrumentation.

## Root cause — the premise is wrong, not the threshold

**A ratio divides out machine speed only when both sides scale the same way.** Here they do not: the
annotated side is superlinear in call-site count and the control is linear. On a slower host the
superlinear side grows faster than the control, so the ratio **increases** with slowness. The gate
amplifies the very variable it was designed to cancel.

That is a defect in the assertion's design, not a threshold that needs raising. Raising it to 400
would hide a real regression on the fast host; the number is not portable at any value.

## Why it was believed

The design was endorsed in [[BUG-473]]'s plan and in the brief that commissioned it, on the reasoning
that "the control absorbs machine speed and JIT state". That is true for two linear quantities and
false here — and this bug exists *because* the annotated side is superlinear, so the one property
that breaks the gate is the property under test.

## Fix strategy — not settled, options only

1. **Drop the ratio assertion and rely on the deterministic gate.** BUG-473's root-resolution
   call-count budget is machine-independent by construction, already exists, and already sits in the
   routine loop. This is the smallest change and loses the least.
2. **Assert the growth exponent** rather than a level — fit across two or three sizes and assert the
   doubling factor. Portable in principle, noisier in practice, and needs a defensible tolerance.
3. **Calibrate per host** and store a baseline. Most faithful, most machinery, and it inherits every
   flakiness problem [[MAINT-38]] tier 3 was deferred for.

Option 1 is the one to beat: the deterministic counter caught everything the ratio did in Phase 1's
mutation matrix, without a host dependency.

## Scope note

Only the ratio assertion is affected. The call-count budget in the same coverage is deterministic and
host-independent, and it went red under the same mutants — so BUG-473's Phase 1 does not lose its
gate if the ratio assertion is removed.

## Resolution

Option 1, taken. `testAnnotatedSnapshotStaysWithinRatioOfTheUnannotatedControl` is deleted along
with its only helper (`snapshotMillis`), the `callSiteCount` parameter that existed for it, and the
three constants it used. `LuaClassTagSnapshotPerformanceTest` keeps its two n = 80 profiling cases,
which assert correctness only. BUG-473's regression gate is `LuaTypeGraphRootResolutionBudgetTest`
(routine loop), unchanged.

### The deterministic gate was proven before the ratio was deleted

The ratio could only ever catch a performance regression in root resolution. BUG-473's mutants
that bear on performance are M1 (the Phase 1 memo) and M-A (the Phase 2 hoist); the others (M2–M4,
M6, M-B, M-C) are correctness mutants guarded by their own tests and were never the ratio's job. M1
and M-A were re-run against `LuaTypeGraphRootResolutionBudgetTest` alone (`test --rerun -i`, libvirt
builder), plus one mutant (M-D) that BUG-473 did not run, to check a *partial* reversal. After each,
the file was restored with `git show HEAD:<path> > <path>` and `git diff --stat` was empty.

| mutant | edit | red tests | message (excerpt) |
| :-- | :-- | :-- | :-- |
| M1 | `atRoot` no longer consults the memo | all 3 methods | `WRITE was re-derived at a walk root 812 times, over the budget of 600` (annotated); 817 vs 620 (conversion); 804 vs 180 (colon) |
| M-A | `checkNodePairs` reads `valueNode.write` per pair, not per visit | 2 of 3 | `WRITE was re-derived at a walk root 7294 times, over the budget of 620` and `7292 ... over the budget of 600`; the colon-call method stays green, as it should (un-annotated, no hoist involvement) |
| M-D | only `VariableElement.read` bypasses the memo (still counted) | all 3 methods | `READ was re-derived at a walk root 7699 times, over the budget of 1000`; 7700 vs 700; 243 vs 92 |

No mutant was caught only by the ratio. M1 and M-A are the two the ratio caught in BUG-473; the
deterministic gate catches both, by a wider margin (M-A is 12× over budget).

**Not established.** The budget method asserts `WRITE` first, so under M1 and M-A the `READ` and
`DECLARED_DEMAND` assertions are not reached in the same method; M-D covers `READ` alone and
`DECLARED_DEMAND` alone has no dedicated mutant here (BUG-473's DR-6 proved all three against the
`annotated/builder.lua` lane under M1). A mutant that bypasses `atRoot` entirely also bypasses the
counter and would not be seen, but no such mutant is a plausible edit of the memo.

### Host independence

`LuaClassTagSnapshotPerformanceTest` is behind the `*Performance*` filter, so it runs only with
`-PwithPerf`; without it the filter matches nothing and `BUILD SUCCESSFUL` asserts nothing. Run with
`test -PwithPerf --rerun` on both builders, same tree:

| builder | result | annotated n = 80 | control n = 80 |
| :-- | :-- | ---: | ---: |
| libvirt (`debian13`) | `BUILD SUCCESSFUL in 34s`, 2 cases executed | 1 374 ms | 18 ms |
| GCE (`lunar-builder`) | `BUILD SUCCESSFUL in 31s`, 2 cases executed | 1 171 ms | 14 ms |
