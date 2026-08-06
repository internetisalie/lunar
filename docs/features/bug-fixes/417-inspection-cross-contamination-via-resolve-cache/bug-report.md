---
id: "BUG-417"
title: "Whether a name reports as undeclared depends on whether the type inspection ran in the same pass"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-417: Undeclared-variable results depend on whether the type inspection ran

`LuaUndeclaredVariableInspection`'s results change — file by file, massively — depending on whether
`LuaTypeAssignabilityInspection` runs in the same highlighting pass. The two share no code and no
data by design; the coupling is a side effect, almost certainly cached resolution state populated
during type-snapshot computation.

This is user-visible product behaviour, not a test artefact: in the IDE, whether `wx` is flagged
undeclared depends on inspection scheduling.

## Measured (2026-08-06, zerobrane corpus member, 72 files)

| configuration | total `Undeclared variable` warnings |
| :-- | --: |
| undeclared inspection alone | **1 954** |
| \+ assignability inspection (current `main`) | 843 |
| \+ assignability inspection (BUG-416 branch) | 1 563 |

With assignability **off**, the two code versions agree **exactly — per-file diff is empty** across
all 72 files. With it on, `src/editor/gui.lua` reports 0 undeclared in one regime and 116 in the
other (265 standalone); `filetree.lua` 0 vs 126; `findreplace.lua` 0 vs 174. The suppression is
file-scoped and depends on the *content of the type errors produced* — changing which assignability
errors exist (BUG-416's suppression) shifted which files lost their undeclared warnings, which is how
this was found.

Reproduction is deterministic per tree (±2 across runs).

## What this explains

- **BUG-415's "unexplained drift" and regime flips.** zerobrane's committed baselines have carried
  `LuaUndeclaredVariable` values of 1 812, 791, 829, and 1 645 at various times — all of them
  contamination-distorted samples of a true count of ~1 954. The corpus's per-inspection numbers for
  any inspection downstream of the type engine's pass are unreliable in both directions.
- Part of BUG-397's apparent "improvement" in undeclared counts (1 812 → 791) was this suppression,
  not resolution getting better.

## Root-cause hypothesis (mechanism proven, exact call path not yet traced)

The empirical shape — suppression only when the snapshot computes, file-scoped, content-dependent —
fits `ResolveCache` poisoning: `LuaTypesSnapshot` computation (BUG-397's `resolveGlobal` /
free-global seeding) resolves references during the same daemon pass, and results computed in that
context are cached and then served to `LuaUndeclaredNames.isUnresolvedNonGlobal`
(`LuaNameReference.multiResolve` → `ResolveCache.resolveWithCaching`). A reference that resolves to
*something* in the snapshot's context reads as declared to the inspection.

The trace task: instrument `LuaNameReference.doMultiResolve` with the calling context for a `wx` ref
in `gui.lua`, run once with and once without the assignability inspection, and diff which phase
resolves it. Candidates inside the snapshot path: `LuaTypeManager.resolveGlobal`,
`LuaGlobalAssignmentNavigation`, `declaredMemberType`.

## Ruled out while finding it

- **A per-file highlight cap** — probed: 2 300 infos in one file, all counted.
- **The solve loop's time/iteration cutoffs** — zero occurrences in test logs.
- **Errors-as-progress convergence coupling** in `checkTypes` — real (fixed in the BUG-416 branch:
  progress now counts examined pairs, not errors), but measured to have no effect on these counts.
- **Structural propagation on newly-forgiven union pairs** — gated in the BUG-416 branch; no effect
  on these counts either.

## Fix direction

Whatever the exact path, the invariant to restore is: **an inspection's results must not depend on
which other inspections ran**. Either the snapshot computation must not write into caches that
reference resolution reads (compute its resolutions through a non-caching entry point), or the
results it caches must be correct for the inspection context too — established by the trace, not
assumed.

## Verification

- The zerobrane sweep reports the same `LuaUndeclaredVariable` total with and without
  `LuaTypeAssignabilityInspection` enabled (±0, not ±small).
- A regression test pinning one contaminated case: `gui.lua`-shaped fixture where a `wx` ref must
  report undeclared regardless of whether the type inspection is enabled alongside.
- Corpus baselines re-recorded once fixed; expect `LuaUndeclaredVariable` ≈ 1 954 on zerobrane and
  movements on every member.

## Outcome (2026-08-06) — the hypothesis above was wrong; the mechanism is simpler

The ResolveCache hypothesis was **refuted by instrumentation**: on a contaminated file the
inspection's own counters showed the visitor ran and would have registered all 126 problems
(`visited=2635 readUse=1505 unresolvedFiring=126`) while `doHighlighting` returned zero — the loss
is *after* registration, not in resolution. Ranged inspection of the emitted infos found it:

**Five assignability errors anchored at range `0–43946` — the entire file.** `fromLuaType` anchors
synthetic member/parameter nodes at `graph.firstNodeElement()`, which for a file's graph is the file
itself; a failed check between synthetic nodes therefore produced a *file-wide ERROR highlight*, and
the platform hides lower-severity infos under an ERROR range. One buried file = every other
inspection's results gone. All 124 `wx` refs in `filetree.lua` sat inside those five ranges.

### Fix

- `reportIncompatible` anchors a compatibility failure at the use element when it is a real
  element, else the value element, **else drops the error** — a diagnostic spanning the whole file
  has no user-actionable location and is strictly worse than silence.
- `addError` refuses any file-wide error as a safety net for every other emission site.
- `LuaTypeErrorAnchoringTest` pins all three behaviours (re-anchor, drop, leave-narrow-alone).

### Verification

zerobrane, 72 files, per-file undeclared counts with vs without the type inspection: **70 of 72
files at exact parity**; totals 1 948 vs 1 954. The residual 6 are refs inside *narrow,
correctly-anchored* ERROR ranges — the platform's by-design severity precedence, which applies to
any inspection pair in any IntelliJ plugin, and structurally cannot be the file-wide class (the
`addError` net forbids it). Whole-sweep: `LuaUndeclaredVariable` 843 → **1 947** with the inspection
enabled, converging on the uncontaminated value.

### Follow-up worth its own decision, not taken here

Inference-based `LuaTypeAssignability` reports at **ERROR** severity. Even correctly anchored, an
ERROR from a still-maturing inference engine takes precedence over other inspections' warnings and
carries more UI weight than its false-positive rate earns (BUG-415 sampling; BUG-416's 72%). LuaLS
presents these as warnings. Dropping the severity is a one-line policy change with corpus-visible
effects — decide it deliberately, separately.
