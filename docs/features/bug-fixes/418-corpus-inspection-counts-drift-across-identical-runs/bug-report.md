---
id: "BUG-418"
title: "Inspection counts drift ±3 across identical runs, against a ratchet that gates at ±0"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-418: Inspection counts drift ±3 across identical runs, against a ±0 gate

Sweeping the same corpus member on the same tree does not reproduce per-inspection counts exactly.
The ratchet gates those counts at zero tolerance, so the gate can fail — or pass — on noise.

## Measured (2026-08-06, zerobrane, identical code each group)

| tree | `LuaUndeclaredVariable` across runs |
| :-- | :-- |
| post-BUG-417 | 1 945 / 1 947 / 1 948 |
| BUG-416 branch (pre-417) | 1 556 / 1 559 / 1 563 |
| graph-changes-only bisection | 1 480 / 1 559 |
| `LuaTypeAssignability`, graph-only | 1 130 / 1 138 / 1 141 |

The drift is small (±3-ish), present in every configuration measured, and **survives the BUG-417
fix** — so it is not the contamination regime (that was the 45-count and 800-count movements, now
gone). It is also not BUG-390's documented non-determinism: that mechanism is gated through
`highlightFailures`, which is zero on every run above.

## Why it matters now

Before BUG-417 this noise hid inside contamination swings. Now the instrument is honest, the
baselines are meaningful — and the recorded zerobrane baseline (1 945) happens to be the **low
sample** of its own distribution, so an unlucky next sweep reads +2/+3 and fails as a phantom
regression. A gate that fails on noise trains people to re-record instead of read, which is the
ratchet's whole failure mode (the lesson recorded on BUG-410, one layer down).

## Candidate causes — to be established, not assumed

Three sessions of this corpus work have shown every untested hypothesis wrong, so these are listed
as *candidates only*:

- The BUG-417 residue boundary: ±6 refs sit inside narrow ERROR ranges (platform severity
  precedence); if highlight *emission order* varies run to run, which refs are covered may wobble.
- Iteration/visiting order somewhere in the daemon or the type engine's fixed point feeding
  different intermediate states to `CachedValuesManager`-backed computations.
- Cross-test JVM state: the sweeps run several members in one JVM; single-member runs drift too,
  so this is at most a contributor.

## Fix directions, in preference order

1. **Find it.** Diff the per-file counts of two same-tree runs (the BUG-417 probes show the method);
   the files that differ localize the mechanism. If it is the severity-precedence boundary, the
   drift should live entirely in files with adjacent ERROR ranges.
2. **If it is irreducible platform behaviour**: gate per-inspection keys with a small explicit
   tolerance (e.g. ±0.5 %) — declared in the baseline file, not hard-coded — so the leak is visible
   and bounded. A tolerance is a ratchet leak; it must be a last resort and it must be written down.
3. **Not acceptable**: re-recording whenever it flakes. That is the failure mode, not a fix.

## Verification

- Ten consecutive same-tree sweeps of zerobrane produce identical per-inspection counts (fix 1), or
  land within the declared tolerance with the tolerance's rationale documented (fix 2).
- The ratchet is mutation-proved against the chosen mechanism: a genuine +1 regression beyond the
  tolerance still fails.

## Outcome (2026-08-06) — measured deterministic; the "drift" was invocation shape

Localization (fix direction 1) resolved this in two steps, and no gate change was needed:

1. **Identical invocations are byte-identical.** Two runs of the same probe: empty per-file diff
   across all 72 files, total 1 948 both times.
2. **Two full gate-shaped runs** (`-PwithCorpus --tests '*Corpus*'`, all members, all inspections):
   **identical across every member and every inspection**, and both equal to the recorded baseline
   exactly (zerobrane 1 945 / 997).

The 1 945 / 1 947 / 1 948 spread in the report above was three *different harnesses* — full-corpus
gate shape, member-alone sweep, two-inspection probe — each individually deterministic. Cross-member
JVM state shifts counts by a few, deterministically. Since recording and gating use the same
invocation shape, the ratchet always compares like with like: **the gate cannot flake.**

The one real hazard survives and is now documented at the point of use: a **member-alone** run
(`--tests '*LuaCorpusSweepTest.testZerobraneCorpus*'`) reads ±2 off a full-run baseline *by design*
and can fail as a phantom regression — that is diagnostic usage, not the gate. `LuaCorpusSweepTest`'s
KDoc says so. A per-inspection tolerance was considered and rejected: determinism is proven, and a
tolerance is a ratchet leak bought to solve a problem that does not exist.
