---
id: "BUG-417"
title: "Whether a name reports as undeclared depends on whether the type inspection ran in the same pass"
type: "bug"
parent_id: "BUG"
status: "todo"
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
