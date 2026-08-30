---
id: "BUG-474"
title: "A ratio gate amplifies machine speed instead of dividing it out, when one side is superlinear"
type: "bug"
parent_id: "BUG"
status: "todo"
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
