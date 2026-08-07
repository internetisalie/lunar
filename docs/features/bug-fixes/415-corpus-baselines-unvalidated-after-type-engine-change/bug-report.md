---
id: "BUG-415"
title: "Corpus baselines went stale through a type-engine change, and a 3x jump was never evaluated"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-415: Corpus baselines went stale through a type-engine change, and a 3× jump was never evaluated

Two defects, one cause. The corpus gate did not run when a change moved every number it guards, and
nobody has since asked whether the new numbers are *right*.

## What was measured

Found 2026-08-06 while re-recording for BUG-409. Committed baselines against a current sweep:

| member | `LuaTypeAssignability` | `LuaUndeclaredVariable` | `LuaReturnTypeMismatch` |
| :-- | :-- | :-- | :-- |
| luacheck | 467 → 502 | 615 → 609 | — |
| luarocks | 1904 → 1778 | 954 → 953 | 233 → 182 |
| penlight | 570 → 506 | — | 95 → 72 |
| **zerobrane** | **846 → 2594 (+207%)** | **1812 → 791 (−56%)** | 113 → 204 |

## Defect 1 — the gate did not run

BUG-397 (`f08d7ca3`, "free globals are typed for the whole engine") changed the type engine
substantially and landed without the corpus baselines being re-recorded. Nothing caught it because
the sweep is opt-in — `-PwithCorpus`, excluded from the routine suite by `build.gradle.kts:270` and
absent from CI, which does not have the out-of-repo `test/` tree.

So MAINT-33's ratchet, whose entire purpose is to notice exactly this, was structurally unable to:
the one change most likely to move it was also the one nobody ran it against. **A gate that only runs
when someone remembers is not a gate.**

## Defect 2 — a 3× jump was never evaluated, and re-recording would freeze it

zerobrane's shape is the interesting part, and it is consistent with BUG-397 doing what it intended:
`LuaUndeclaredVariable` **halved** while `LuaTypeAssignability` **tripled**. ZeroBrane is a
wxWidgets IDE, and its baseline records 1 440 `wx` hits alone, so its free globals are exactly the
population BUG-397 changed — they stopped being *undeclared* and started being *type-checked*.

That explains the movement. It does **not** establish that the 2 594 assignability warnings are
correct. The alternative reading is 1 748 new false positives against `wx.*` members the engine now
types but types wrongly, and the ratchet cannot tell the difference: re-recording simply accepts
whatever it sees as the new floor. **If a defect count triples, the number needs evaluating before it
is baselined, not after.**

This report is filed *because* BUG-409 had to re-record to gate at all. Those recorded values are an
**unvalidated snapshot of post-BUG-397 behaviour**, not an endorsement of them.

## Reproduction

```bash
tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --tests '*LuaCorpusSweepTest*' --rerun --no-build-cache"
```
against `f08d7ca3`'s baselines fails with `inspection.LuaTypeAssignability: baseline 846 → observed 2594`.

## Also observed — an unexplained run-to-run drift

Two sweeps of identical code gave zerobrane **2549** and **2594** (`LuaUndeclaredVariable` 827 vs
791). The 2 594 reproduced twice, including in a zerobrane-only run, so 2 549 is the outlier. It is
**not** the documented BUG-390 non-determinism: `CorpusBaseline.compare` only downgrades the
per-inspection keys to advisory when `highlightFailures` is non-zero, and zerobrane's baseline has no
such key. Candidate cause: cross-test state, since the 2 549 run swept all four members plus the
torture corpus in one JVM. Unexplained drift in a gated metric is its own problem — a ratchet that
moves by 45 without a code change will eventually be re-recorded to silence it.

## Fix strategy

1. ~~**Evaluate before baselining.**~~ **Done 2026-08-06 — and the answer was "largely false".**
   Sampling all 2 491 zerobrane assignability warnings found 46 distinct shapes whose top three are
   a single defect: a possibly-nil value reaching a table slot, **1 801 of 2 491 (72%)**. Refiled as
   **BUG-416**. The 846 → 2594 movement is therefore **not** a floor to protect, and this baseline
   must not be treated as an expectation until BUG-416 lands.
2. ~~**Close the process gap.**~~ **Closed 2026-08-06 — the gate runs on the `lunar-ci` pool.**
   Every premise of "the corpus cannot run in CI" had dissolved by the time this was revisited: the
   fixtures are fully pinned and stamped (fetch-corpus/fetch-luac/fetch-torture, MAINT-35's
   pattern), the pool's node-local `/cache` persists them across jobs, and the `test` symlink is
   re-pointed at that mount in-job. A `corpus` job in `build-plugin.yml` now runs the full
   `'*Corpus*'` ratchet on **every push to `main`, every `v*` tag** (releases are corpus-gated),
   and **any PR touching the gate's own machinery** — so the lane is exercised pre-merge on
   exactly the changes most likely to break it. The job image gained `build-essential` + `python3`
   (0.2.0; the pool is tagless and consumes the newest semver automatically). The trigger is now
   "every merge", not "whenever someone remembers".
3. ~~**Explain the 45-count drift.**~~ **Mostly explained 2026-08-06 — the large component is
   BUG-417** (the contamination regime). The undeclared
   inspection's results depend on whether the type inspection ran in the same pass (measured: 1 954
   alone vs 843/1 563 with it, file-scoped). The drift and the regime flips are that coupling
   reacting to timing and error-profile changes. Until BUG-417 is fixed, the per-inspection keys of
   inspections that share a pass with the type engine are **not comparable across code changes** —
   treat them as advisory in review even though the ratchet still gates them mechanically.
   The residual ±3 was tracked as **BUG-418** and closed same-day by measurement: it was
   invocation-shape dependence, not randomness — identical invocations are byte-identical, and the
   gate shape reproduces the recorded baseline exactly. This item is now fully explained: the large
   component was BUG-417, the residue is deterministic cross-member JVM state, and the only hazard
   (member-alone runs vs full-run baselines) is documented at the point of use.

## Test strategy

- A regression test cannot assert a corpus count. What can be asserted: a small fixture of
  `wx`-style free globals produces the *intended* diagnostics after BUG-397, which is the sampling in
  step 1 turned into a fixture once the intended answer is known.
- Whatever trigger step 2 chooses must be demonstrated to fail on a stale baseline — the same
  standard MAINT-35 was held to.

## Outcome (2026-08-06)

All three items closed: (1) the un-evaluated jump was sampled and became BUG-416, fixed; (3) the
drift was BUG-417 (large component, fixed) plus BUG-418 (measured deterministic, no fix needed);
(2) the process gap is closed by the `corpus` CI lane above. The first CI run of the lane doubles
as the cross-machine determinism experiment BUG-418 deliberately left open: the builder-recorded
baselines either reproduce on the pool's n2-standard-4 or fail loudly on day one — both outcomes
are the gate working.
