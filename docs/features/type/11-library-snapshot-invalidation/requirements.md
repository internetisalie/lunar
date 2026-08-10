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

**What this feature does *not* fix** — stated so nobody expects it to:
- The **cold first build** of a session still pays the full library graph once (COMP-09's index is
  the answer to that half; see the last section).
- The **consumer-side rebuild scales with consumer file size** and is *correctly* invalidated per
  keystroke — the file did change. DR-20's own sideways data has `forFile` at 124 ms on a
  4 001-line file with the library already warm. If large project files matter, that is a separate
  incremental-inference problem, out of scope here.

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
| **index availability (dumb mode)** | `resolveGlobal` returns `null` while dumb (`LuaTypeManagerImpl:141`) — a snapshot built during indexing **bakes those nulls in**. Today any keystroke heals it; under a generation dependency it is sticky until the next roots/target tick | not handled anywhere; needs a don't-cache-while-dumb guard or `DumbService`'s modification tracker as a dependency |
| rock content changed **inside an existing root** | `luarocks install` writing into an already-registered `lua_modules/` — the root set does not change, so the roots tracker does **not** tick | `RockspecSourcePathProvider.forceRefreshTracker` exists; **whether it ticks on install is unverified**. **Deferred, not blocking:** v1 scope excludes rocks trees entirely (see Scope below) |

## Scope: platform libraries only, identified by provenance

v1 applies the generation dependency **only to plugin-provisioned immutable libraries** — the
bundled stdlib stubs and fetched definition libraries (LuaLS addons). Everything else, rocks trees
included, stays on the global tracker. Three reasons:

- Those files are genuinely immutable within a session; their content changes only via plugin
  update or addon re-fetch, both detectable. And DR-20's measured pain case (the 123 KiB `wx`
  library) **is** a definition library — this scope captures the demonstrated win.
- Rocks trees are exactly the risky remainder: mutable in place (the unverified
  `forceRefreshTracker` signal above) and ordinary Lua code that plausibly references project
  globals, maximizing the residual below. Excluding them converts that signal from a blocker into
  a follow-up.
- Identification is **by provenance** — the `RuntimeLibraryProvider` / definition-library registry
  knows its own files — **not** by `ProjectFileIndex.isInLibrary`, whose behavior for this
  plugin's `SyntheticLibrary` roots is unverified. Provenance sidesteps that question entirely.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-11-01 | **A platform-library snapshot survives an unrelated edit** | M | Editing a project file must not invalidate the cached snapshot of a file in a plugin-provisioned library (bundled stdlib stub, definition library). Rocks trees are out of scope for v1 and keep today's behavior. |
| TYPE-11-02 | **Every generation signal invalidates it** | M | Roots change and target/version change each discard the affected snapshots. A missed signal is a stale-type defect, which is worse than the cost being fixed. |
| TYPE-11-03 | **Library-file identification is by provenance** | M | The library set comes from what this plugin provisioned (`RuntimeLibraryProvider` / the definition-library registry), matched by `VirtualFile` identity against the file `resolveGlobal` actually resolved. Not `ProjectFileIndex.isInLibrary` — unverified for `SyntheticLibrary` roots, and unnecessary once provenance answers the question. |
| TYPE-11-04 | **No new stale-type defect** | M | The edit-then-reread fixtures (`TypeElevenDr01ResidualTest`, and for TYPE-11-06's two shapes `TypeElevenDr11LateDeclarationTest` / `TypeElevenDr12WarmInnerSnapshotTest`) stay green. **Measured (TYPE-11-DR-09): "all four corpus baselines unmoved and the full suite green" is not a test of this** — both hold under a build that serves stale types. They stay as "nothing else moved" checks. |
| TYPE-11-05 | **A dumb-mode build is never cached across the generation** | M | `resolveGlobal` answers `null` while indexing; a snapshot built then has those nulls baked in and must not outlive dumb mode. Either skip caching while `DumbService.isDumb`, or add the dumb-mode tracker as a dependency. Without this, TYPE-11 *creates* a staleness class that today's per-keystroke invalidation accidentally heals. |
| TYPE-11-06 | **An incomplete recording is never pinned** | M | The generalisation of TYPE-11-05, and the reason it is a requirement of its own: the recorder must distinguish **"no sources"** from **"sources unknown"**, and only the first may be pinned. A build during which a global resolution answered nothing, or during which a nested `forFile` was served from cache without contributing its own recorded sources, has not established that it is a pure function of provisioned content. Measured (design §1.8): both under-recordings ship a stale type under the rule as originally written, and closing them costs **zero** of the 11 files this feature pins. Gated by `TypeElevenDr11LateDeclarationTest` and `TypeElevenDr12WarmInnerSnapshotTest`. |

## The residual that may defeat the whole approach

A library file's snapshot is **not** purely a function of its own content, and the code makes the
dependency on *project* files likelier than "reaches other files" suggests. Two concrete paths:

- **Cross-file globals, project files winning.** `buildSnapshot` calls `resolveGlobal` for every
  unbound name (`freeGlobalSeed`, `LuaTypesVisitor:1322`), and `doResolveGlobal` searches
  **project scope first** (BUG-427, `LuaTypeManagerImpl:162`). So an unbound name inside a library
  file can resolve to — and embed the type of — a declaration in a project file.
- **Class member materialization is project-wide.** `materializeClass` → `collectMethodMembers`
  enumerates `LuaGlobalDeclarationIndex` with `getAllKeys` across the project
  (`LuaTypeManagerImpl:427`). A project file declaring `function LibClass:helper()` adds a member
  to a library-defined class; edit that file and a generation-pinned library snapshot is stale.
  This is not hypothetical — extending a stub-defined class from project code is an ordinary Lua
  idiom.

Library→library dependencies are safe under a *shared* generation tracker (any library change
ticks it for all). Library→project dependencies are the unsound case, and the platform-library
scoping above **shrinks this surface but does not eliminate it** — a project file can extend a
stub-defined class just as easily as a rock's. If measurement shows these paths are hot, a
per-file generation dependency is unsound regardless of how many signals are composited, and the
answer is something else (a scoped tracker per dependency set, or leaving cross-file-resolving
snapshots on the global tracker).

**Nothing above should be built until that is measured.**

## De-risking — do this first

| ID | Question | Resolves |
| :-- | :-- | :-- |
| TYPE-11-DR-01 | Swap `forFile`'s dependency to a generation-scoped one **for platform-library files only**, run the full suite and the corpus sweeps. What breaks tells you whether the cross-file residual is real. Include the two named residual paths explicitly: a project file shadowing a stub global (BUG-427 ordering) and a project file adding a method to a stub-defined class. | the residual above; TYPE-11-04 |
| TYPE-11-DR-02 | Can every file `resolveGlobal` resolves into be matched by `VirtualFile` identity against the provenance set (`RuntimeLibraryProvider` / definition-library registry)? Watch for identity traps — `originalFile`, light-fixture copies, event-system vs local file system. | TYPE-11-03 |
| TYPE-11-DR-03 | *(Deferred — rocks are out of v1 scope.)* Before ever extending this to rocks trees: does `RockspecSourcePathProvider.forceRefreshTracker` tick on `luarocks install` into an existing root? If not, what does — and if nothing does, that signal must be built. | follow-up scope only |
| TYPE-11-DR-04 | Re-measure DR-20's 9 ms / 334 ms pair after the change, medians of >=5. **Success is landing near the 9 ms no-library baseline, not the warm ~1 ms** — the old warm number was the consumer's own snapshot served from cache; after a keystroke that snapshot correctly rebuilds, and every free global re-runs `resolveGlobal` + `graphTypeToLuaType`, which builds a fresh `visited` map per call and walks the library table's full member set. If the number lands well above 9 ms, that conversion is the next cost to look at — not a failure of the invalidation change. | TYPE-11-01 |
| TYPE-11-DR-05 | Build a snapshot under `DumbService.isDumb` (index-rebuild test fixture), exit dumb mode, complete again. Do the baked-in nulls survive? Verifies the guard demanded by TYPE-11-05 actually engages. | TYPE-11-05 |

### De-risking outcomes (run 2026-08-09; full output in [design.md](design.md) §1)

DR-01, -02, -04 and -05 were run; DR-03 was not, as scoped above. Four statements in the sections
above did not survive being executed, and they are corrected here rather than left to be re-derived:

- **The residual is real, and the approach this document sketched is unsound.** Pinning every
  provenance-matched library snapshot to a generation tracker turned both named residual paths red
  (design §1.1). The plan therefore builds the alternative this document already allowed — leaving
  cross-file-resolving snapshots on the global tracker — decided per build from a recorded source set.
- **Nothing but TYPE-11's own new fixtures noticed.** Under the unsound build, 2543 pre-existing
  tests passed unchanged. TYPE-11-04's acceptance cannot rest on the existing suite. *(First round
  asserted the corpus half from a run that never executed the sweep classes; second round measured it
  — see below. The claim happens to be true, but it was not evidence when it was made.)*
- **Residual path 2 is real only in the hosted `---@class` form** (`---@class C` over `local C = {}`),
  and reaches the snapshot via `freeGlobalSeed` → `tableToLuaType` → `LuaGraphType.fromLuaType`, not
  via `materializeClass` as stated above. The bundled stdlib is 21/22 unhosted (design §1.2).
- **TYPE-11-03's "matched by `VirtualFile` identity" is not achievable as written.** `===` is false
  for a project file the index itself supplied; matching is by **URL containment**, read through
  `psiFile.originalFile.virtualFile` because a completion copy has no `virtualFile` at all
  (design §1.3).
- **TYPE-11-05's staleness class did not reproduce.** The nulls are baked in while dumb, but they do
  not survive — the file's own `modificationStamp` moves at dumb-mode exit, and `forFile`'s `psiFile`
  dependency rebuilds regardless of the churn tracker. Removing the guard left the harness green, as
  did an absolutely-never-ticking tracker. The guard is retained as insurance and TYPE-11-05 has **no
  automated protection**; tracked as `risks-and-gaps.md` DR-06 (design §1.6).
- **DR-04's success criterion is not met.** Arm B lands at 3–5× arm A in the same run, not "near the
  9 ms baseline" — which this document predicted, and named the reason for (design §1.5).

### Second round (2026-08-09, `main` @ `2e06bc86`) — the recorder's premise, and the sweep

- **The recorder's premise was tested and the recorder survives (TYPE-11-DR-10, design §1.7).** This
  document blames residual path 1 on `doResolveGlobal` searching project scope first (BUG-427). That
  ordering was *removed* — a provisioned file's globals were restricted to a provisioned-only
  candidate set — and blanket pinning stayed unsound: `2571 tests completed, 2 failed`, with residual
  path 2 still reporting `[afterEdit, beforeEdit]`. The reason is that path 2 never passes through
  global resolution at all: `collectMethodMembers` reads
  `StubIndex.getAllKeys(LuaGlobalDeclarationIndex.KEY, project)` **project-wide, with no scope
  argument** (`LuaTypeManagerImpl:427-432`). The restriction also deletes real behaviour — a library
  global typed from a project global stops resolving entirely (`[beforeEdit]` → `[]`).
- **Residual path 2's route is `materializeClass` after all, in the sense that matters.** This
  document's original wording named `materializeClass` → `collectMethodMembers`; design §1.2 corrected
  it to `freeGlobalSeed` → `tableToLuaType` → `fromLuaType`. Both are right about different halves:
  the *carrier* into the snapshot graph is `fromLuaType`, and the *source* of the project member is
  `collectMethodMembers`, reached from `tableToLuaType`'s nominal `resolveType(className)` hop
  (`LuaTypes.kt:156-172`). Only the second half explains why scoping global resolution cannot fix it.
- **The corpus sweep is blind to this too (TYPE-11-DR-09).** Re-run under the blanket-pin build:
  `2571 tests completed, 2 failed` — both TYPE-11's own — with `LuaCorpusSweepTest` 4/0,
  `LuaTortureCorpusTest` 1/0 and `LuaInspectionParityTest` 1/0. All four baselines unmoved.
  Structural, not incidental: `CorpusSweep.run` is a single pass over an unedited tree, and a stale
  cache needs an edit. TYPE-11-04's acceptance is `TypeElevenDr01ResidualTest` and nothing else.

### Third round (2026-08-10, `main` @ `07a8fa44`) — the recorder under-records in two directions

Step 9 raised two blockers against the conditional rule this document's second round adopted. Both
were reproduced and both are closed; full output in [design.md](design.md) §1.8.

- **A resolution that answers nothing reports no source (B1).** A library file whose free global
  nothing declares yet records an **empty** set, which the rule read as "depends on nothing" and
  pinned. Writing the declaration afterwards never reaches it:
  `expected:<[afterDeclared]> but was:<[]>`. The design's own sentence "which the global tracker
  guarantees happens" was false for a pinned file — `MODIFICATION_COUNT` is the dependency the pin
  removes, so **a pin must be correct when it is taken; there is no re-judgement**.
- **A nested `forFile` served warm contributes no sources (B4).** `resolveGlobal` got a replay
  (design §3.6) and `forFile` did not, though both are memoized on the same footing. Ordering alone,
  inside one modification epoch and with no roots tick, pins a library file that transitively embeds
  a project file's type: `expected:<[afterEdit]> but was:<[beforeEdit]>`.
- **The fix costs nothing.** Under the corrected rule all 11 provisioned files stay pinnable
  (`cond=11 … guarded=11`). The literal form of the rule — "any resolution that answered nothing" —
  costs `io.lua` its pin for five `resolveType` misses on unparsed type expressions
  (`boolean|nil`, `fun(): string`), so the absence rule is restricted to **global** resolution, which
  costs zero files. Warm inner snapshots are handled by replaying their recorded frame rather than by
  declaring them unknown, because a blanket rule there would strip every library→library chain's pin
  permanently.
- **The alternative was priced, not assumed.** `FileBasedIndex.getIndexModificationStamp` exists and
  behaves as hoped (`before=16 afterUnrelatedEdit=16 afterNewDeclaration=17`) but is not adopted; the
  rule it would optimise already costs nothing.

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
