---
id: "TYPE-11"
title: "11: A library file's type snapshot must not be invalidated by an unrelated keystroke"
type: "feature"
status: "todo"
priority: "high"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-11: Library snapshot invalidation

## Overview

`LuaTypesSnapshot.forFile` caches a file's inferred type graph, and depends on
**`PsiModificationTracker.MODIFICATION_COUNT`** — which is **project-wide**. Any PSI change anywhere
discards every file's snapshot, including files that cannot change: bundled stdlib stubs, fetched
definition libraries, installed rocks.

So typing one character in a project file throws away the inferred graph of a 123 KiB library that
nobody touched, and the next completion rebuilds it.

## Measured (COMP-09 DR-20, 2026-08-09, `CompNineDr20Test`)

Identical two-line consumer file, medians of 5 cold samples, each sample using distinct consumer text
so the per-file-text memoization cannot serve a warm answer — i.e. each sample is "user typed a
character, snapshot rebuilt":

| | median |
| :-- | --: |
| no library registered | **9 ms** |
| one 123 KiB library registered | **334 ms** |

**37x, on the same two lines of Lua.** Confirmed sideways in the same run: `forFile` measured 980 ms
on a 3-line file and 124 ms on a 4 001-line file, because the small one ran first and paid the
library build. **Snapshot cost tracks library warmth, not file size.**

The chain is nested `forFile`: building the consumer's graph visits the name `wx`; `resolveGlobal`
runs "for *every* unbound name reference the type visitor meets" (its own KDoc); that resolves
cross-file and calls `forFile(wx.lua)`, building the library's entire graph. COMP-09 design §1.1
already measured that inner path at 9 568 ms on a larger tree.

## Why this is a capability, not a tuning knob

`MODIFICATION_COUNT` there is not a considered choice about library files — it is the safe
over-approximation applied uniformly. Library content is immutable **within a dependency
generation**, so the correct dependency is the generation, not every keystroke.

The complication, and the reason this needs planning rather than a one-line change: "generation" is a
composite this plugin defines, and each element is a way to be **silently** wrong — a missed tick
yields a stale snapshot, not a crash.

| signal | covers | status |
| :-- | :-- | :-- |
| `psiFile` | that file's own content | already a dependency of `forFile` |
| `ProjectRootModificationTracker` | roots added/removed — rock install/uninstall that changes the root set, definition-library enable/disable | platform API (`platform/core-api`), not yet used here |
| `targetModificationTracker` | **platform and language version together** — `Target` is `{platform, version}` and `setTarget` ticks it while deriving `languageLevel` (`LuaProjectSettings:134`) | already a dependency of `forFile` |
| rock content changed **inside an existing root** | `luarocks install` writing into an already-registered `lua_modules/` — the root set does not change, so the roots tracker does **not** tick | `RockspecSourcePathProvider.forceRefreshTracker` exists; **whether it ticks on install is unverified** |

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-11-01 | **A library snapshot survives an unrelated edit** | M | Editing a project file must not invalidate the cached snapshot of a file in a library root. |
| TYPE-11-02 | **Every generation signal invalidates it** | M | Roots change, target/version change, and in-place library content change each discard the affected snapshots. A missed signal is a stale-type defect, which is worse than the cost being fixed. |
| TYPE-11-03 | **Library-file identification is correct for synthetic roots** | M | This plugin's libraries arrive via `AdditionalLibraryRootsProvider`/`SyntheticLibrary`, not ordinary library roots. `ProjectFileIndex.isInLibrary` is used at `ProximityCalculator:23`, but not verified for synthetic roots. |
| TYPE-11-04 | **No new stale-type defect** | M | All four corpus baselines unmoved, and the full suite green. This changes when inference results are discarded; a wrong answer here is invisible until it is wrong in a user's editor. |

## The residual that may defeat the whole approach

A library file's snapshot is **not** purely a function of its own content. `buildSnapshot` calls
`resolveGlobal` for unbound names, which reaches other files — so library A's snapshot can depend on
library B, and on project files. If that is real here, a per-file generation dependency is unsound
regardless of how many signals are composited, and the answer is something else (a scoped tracker per
dependency set, or leaving cross-file-resolving snapshots on the global tracker).

**Nothing above should be built until that is measured.**

## De-risking — do this first

| ID | Question | Resolves |
| :-- | :-- | :-- |
| TYPE-11-DR-01 | Swap `forFile`'s dependency to a roots-scoped one **for library files only**, run the full suite and the corpus sweeps. What breaks tells you whether the cross-file residual is real. | the residual above; TYPE-11-04 |
| TYPE-11-DR-02 | Does `ProjectFileIndex.isInLibrary` return true for this plugin's `SyntheticLibrary` roots? `LibraryRootTestCase` exists precisely because that path behaves unlike ordinary roots. | TYPE-11-03 |
| TYPE-11-DR-03 | Does `RockspecSourcePathProvider.forceRefreshTracker` tick on `luarocks install` into an existing root? If not, what does — and if nothing does, that signal must be built. | TYPE-11-02 |
| TYPE-11-DR-04 | Re-measure DR-20's 9 ms / 334 ms pair after the change, medians of >=5. The claim is that subsequent completions land near the warm ~1 ms. | TYPE-11-01 |

## Relationship to COMP-09

COMP-09 is **parked at Phase 1** because of this. Its Phase 2 was executed to plan and aborted
(`ABORT_REPLAN`): the call site COMP-09 set out to replace is both nearly dead — the
`type == Undefined` guard opens only for receivers that have nothing to offer — and **downstream of
this cost**, which is 88 % of a cold completion. COMP-09's index is built, tested and consumed by
nothing; it is harmless where it stands.

This is not a replacement for COMP-09. Fixing invalidation removes the **recurring** per-edit cost;
the **first** completion of a session still builds the library graph once, which is what an index
avoids. COMP-09 DR-07 called narrowing "a complement, not an alternative" — that remains true, but
it undersold this half badly, because it was reasoning about the cold path alone.
