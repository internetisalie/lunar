---
id: "TYPE-11"
title: "11: A library file's type snapshot must not be invalidated by an unrelated keystroke"
type: "feature"
status: "in_progress"
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
| rock content changed **inside an existing root** | `luarocks install` writing into an already-registered `lua_modules/` — the root set does not change, so the roots tracker does **not** tick | `RockspecSourcePathProvider.forceRefreshTracker` exists (**`private`** — `rocks/RockspecSourcePathProvider.kt:25`, so exposing it is part of the deferred work, not a given); **whether it ticks on install is unverified**. **Deferred, not blocking:** v1 scope excludes rocks trees entirely (see Scope below) |

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
| TYPE-11-05 | **A dumb-mode build is never cached across the generation** | M | `resolveGlobal` answers `null` while indexing; a snapshot built then has those nulls baked in and must not outlive dumb mode. Either skip caching while `DumbService.isDumb`, or add the dumb-mode tracker as a dependency. Without this, TYPE-11 *creates* a staleness class that today's per-keystroke invalidation accidentally heals. **The staleness itself does not reproduce** (design §1.6) — so this is gated on the *decision*, `isPinnable(libraryFile, SourceFrame()) == false` under dumb mode, which goes red when the guard is deleted (`TypeElevenDumbModeDecisionTest`, design §1.9). |
| TYPE-11-06 | **An incomplete recording is never pinned** | M | The generalisation of TYPE-11-05, and the reason it is a requirement of its own: the recorder must distinguish **"no sources"** from **"sources unknown"**, and only the first may be pinned. A build during which a global resolution answered nothing, or during which a nested `forFile` was served from cache without contributing its own recorded sources, has not established that it is a pure function of provisioned content. Measured (design §1.8): both under-recordings ship a stale type under the rule as originally written, and closing them costs **zero** of the 11 files this feature pins. **Four channels, all measured**: an absence (B1), a warm inner snapshot (B4), an **in-progress** inner snapshot (V1) and a **rescued** global — one the project scope did not answer but the all-scope fallback did, which a later project declaration out-ranks (V2). Each was reproduced red and each fix costs **zero** of the 11 pinned files (§1.8, §1.10). A **sixth** channel is **open, not closed**: a `LuaTypeReference` already forced at `depth() == 0` is consumed inside a build frame by `fromLuaType` without reaching `resolveType`, so the file it resolved into never enters `urls` and the library file is pinned while depending on it — measured end to end, filed as **BUG-434** (Phase 4, DR-07). A **fifth** channel — `resolveModule`'s `ANY` fall-through, which records nothing when a `require` resolves to nothing (design §1.12) — is closed on correctness, and **its cost is now measured too** (DR-18, Phase 2): `REVIEW-COST TOTALS provisioned=11 withModuleRule=11 withoutModuleRule=11`, zero lost pins, so it is no longer the one rule adopted without a price. Gated by `TypeElevenDr11LateDeclarationTest`, `TypeElevenDr12WarmInnerSnapshotTest`, `TypeElevenDr14InProgressTest`, `TypeElevenDr15LateLibraryAnswerTest` and `TypeElevenDr18ModuleAbsenceTest`. |

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
  did an absolutely-never-ticking tracker. ⚠ **The clause that
  followed — "TYPE-11-05 has no automated protection" — was wrong, and is corrected by the fourth
  round below.** It confused the outcome with the decision. ⚠⚠ **And the open question — platform
  behaviour or `DumbModeTestUtils` artifact — is answered in Phase 4 (DR-06): platform behaviour, on
  *both* edges of the episode**, from `FileManagerImpl`'s own dumb-mode subscription to
  `processFileTypesChanged`, which invalidates all physical PSI project-wide. The guard is therefore
  **insurance** and is kept as such; Gap 2.1 is closed.
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

### Fourth round (2026-08-11, `main` @ `75707e78`) — two guards that could not fire

Step 9's remaining blockers were not about the rule but about the **tests promised to protect it** —
the same "vacuously satisfied" defect as the third round, one level out. Both are closed; full output
in [design.md](design.md) §1.9.

- **The coverage guard matched text that is not in the file (B3).** Phase 3's
  `LuaTypeSourceRecorderCoverageTest` was specified against qualified chains. Counted against the real
  `LuaTypeManagerImpl.kt`: `FileBasedIndex.getInstance().getContainingFiles` = **0** (2 real sites,
  both wrapped by ktlint), `PsiManager.getInstance(project).findFile` = **1** of 2 (one goes through a
  `psiManager` local — a second miss the review did not name), `StubIndex.getElements` = 3 of 3 but
  fragile: one re-wrap drops it to 2 and reports a deletion that never happened. Now matched on
  comment-stripped, whitespace-collapsed text with the recorded counts **2 / 3 / 2**, and shown to move
  `2 → 3` when a site is injected.
- **The dumb-mode guard is gateable after all (B5).** "The staleness does not reproduce" is a fact
  about the *outcome*; it was allowed to imply the guard is untestable, and that does not follow. A
  dumb build records an **empty** frame, and an empty frame on a provisioned file clears §3.3 steps
  2–7 — so step 1 is the sole rejector and `isPinnable(libraryFile, SourceFrame())` is `false`
  with it and `true` without. TYPE-11-05 gets a real gate on the decision; §3.3 steps 1–8 are extracted
  into a named predicate so it can be asked directly.
- **What was missing was an extraction, not evidence.** DR-05's own trace (`libDumb graph type =
  Undefined` inside the dumb block) already established the premise. Three rounds of review let
  "no reproducing test" stand unchallenged as "no possible test".

### Fifth round (2026-08-11, `main` @ `1e9a91c1`) — two more channels into the same defect

The second full Step 9 round raised two blockers against §3 *as accepted after B1 and B4*. Both were
reproduced before being fixed; output in [design.md](design.md) §1.10.

- **The in-progress nested snapshot (V1).** §3.7 dismissed `LuaTypesVisitor.inProgressSnapshot` as
  "the same file's own in-flight build". It is a map keyed on the *requested* file, and every file
  whose build is on the thread's stack has an entry — so the hit is served for a file two frames out,
  whose frame is not the one open. Measured (`inProgress hit file=…/outer.lua depth=5`), and it ships
  a stale type: `expected:<[afterEdit]> but was:<[beforeEdit]>`. It is a **third memoized door** with
  no possible replay — the served snapshot is still being built — so it gets the conservative
  treatment, not the replay treatment.
- **A resolution that succeeded, out-ranked later (V2).** `doResolveGlobal` searches project scope
  first (BUG-427). A library global only another *library* declares resolves through the all-scope
  fallback, so the call succeeds, no absence is recorded, and the file is pinned — then a project
  declaration out-ranks it and the pin never re-judges:
  `expected:<[afterProject]> but was:<[beforeEdit]>`. B1's shape with a **successful** resolution,
  which is precisely what step 4's "answered nothing" wording missed.
- **Both fixes cost nothing** (`b14=11 dr15rescued=11`), and of two priced variants the narrower one
  is adopted because the broader duplicates the absence rule without ever saving a pin.
- **The zero is structural and is not banked.** No shipped library file cross-references another
  library file's global, so none reaches either interleaving. Free today; a cross-referencing
  definition library would pay, correctly.

### Sixth round (2026-08-11) — the guards, not the rule

The same review round that produced V1/V2 found that several of this plan's *acceptance cases* could
not fail. None is a defect in the design; every one is a defect in what the plan promised would catch
a defect, which is the recurring shape here (see also §1.9 B3/B5).

- **TYPE-11-01 had no failing acceptance.** Its named test has **zero assertions**, and its threshold
  ("5× below the `main` figure measured in the same run as its own arm A") is unsatisfiable — a `main`
  figure cannot come from a post-change run, and §1.5 forbids the cross-build ratio anyway. Replaced
  by snapshot **instance identity** across an unrelated project edit, which is red on `main` today and
  is the plainest statement of what this feature does. The latency probe stays a probe.
- **TYPE-11-02's primary case could not go red.** "Empty the enabled list, assert `resolveGlobal` stops
  answering" removes the file from `allScope`, so resolution finds no candidate and the pinned snapshot
  is never consulted — green on `main`, under any rule, and with the roots tracker deleted. Restated to
  keep the library enabled and tick roots by enabling a second one. Its sibling case gates a dependency
  that is **unconditional today**, so its mutation is now stated as dropping `targetTracker` from the
  *pinnable* branch specifically.
- **TYPE-11-03 was gated against a replica.** The predicate under test is defined inside the de-risking
  test file and matches by `isAncestor` where §3.2 specifies URL-prefix; all five of its "mutation-proved"
  rows mutated that copy. Phase 1's `LuaLibraryProvenanceTest` is the gate, and must **re-earn** each
  mutation against the production service. Done 2026-08-11 — and the Phase-1 review then found the
  same shape one level down: the class re-earned the five *provenance* facts but gated nothing about
  the **memoized dependency set**, which `ModificationTracker.NEVER_CHANGED` left entirely green
  (measured). Two assertions were added for it; see the remediation table in `risks-and-gaps.md`.
  A second remediation round then found the same shape **one level down again**: that fix's mutation
  was a *conjunction* ("both dependencies replaced"), which attributes the red to neither member —
  and measured, dropping `targetModificationTracker` **alone** left all eight methods green, because
  no test in the tree ticked the target at all. A ninth assertion now gates the target dependency on
  its own single-member mutation. **`LuaLibraryProvenanceTest` is 9 tests; `LuaTypeSourceRecorderTest`
  is 12.** Phase 1 is complete.
- **The cost gate had no owner** (an exit criterion with no task, and assigned to a class that reads
  text rather than builds fixtures), and **the coverage guard was blind to three doors** — including
  `StubIndex.getAllKeys`, the route §1.7 designates load-bearing, and a cross-file read in
  `LuaTypesVisitor` that the guard's one-file scope could never see.
- **§3.5's "exhaustive" was exhaustive for one class**, not for the codebase; `seedAmbientGlobals` is
  the counter-example. Harmless today (it reads only the provisioned runtime root) and corrected
  because the premise, not the site, is what scoped the guard.

### Rounds five and six (2026-08-11) — the loop's own yield, and where it stopped

Five Step 9 rounds ran. The **rule** has not changed since V1/V2; every round after that found defects
in the machinery meant to prove the rule, and two of them found defects introduced while fixing the
previous round's. That is worth recording as a result about this process, not just about this feature.

The last round's three findings, all closed:

- **A fifth under-recording channel (§1.12).** `resolveModule` falls through to `LuaPrimitiveType.ANY`
  when nothing provides a module, consuming no file — so a library file whose `require` resolves to
  nothing records an empty frame and is pinned. B1 one door over. The step-5 absence restriction had
  been priced against `resolveType` only; `resolveModule` was never asked. Closed by step 5d, with its
  cost **owed** (DR-18) rather than assumed — it is the one rule here adopted on correctness alone.
- **§1.11's conclusion was wrong, and it had deleted a gate.** "A roots tick is also a PSI tick" is
  true and traced; "therefore the outcome cannot gate" does not follow, because a **pinned** value has
  no `MODIFICATION_COUNT` dependency — that is what the pin removes. Under the mutation the pinned
  snapshot genuinely does not rebuild, so the assertion is red. Removing it left TYPE-11-02 testing the
  ingredient and never the wiring: a build with a correct `churnDependencyFor` whose pinnable branch
  simply omits the churn object from `Result.create` passed **every** gate and would ship every library
  snapshot stale on any roots change. Restored as TC-2c.
- **Risk 1.1b shrank on tracing.** `isCached` short-circuits before `fetch`, `cacheDir` is
  `<id>-<version>`, and no delete-and-refetch path exists — in-place replacement is unreachable through
  shipped code. Accepted and documented rather than de-risked.

**Status moves to `planned` here.** The remaining open items are tracked de-risking tasks with owners
and phases (DR-06, DR-07, DR-08, DR-16, DR-17, DR-18) and a pending-mutation table that names every
assertion still owing an observed red. None of them blocks starting Phase 1, and the honest reason to
stop reviewing is that the last two rounds found no defect in the design — an implementer running code
will now find things faster than a sixth reading will.

### Phase 3 (2026-08-11) — the feature is live, and TC-2c's gate was measured false

`forFile` is conditional: a provisioned file whose recording is complete depends on
`ProjectRootModificationTracker` + `targetModificationTracker` + itself, and every other file keeps
today's `MODIFICATION_COUNT`. Measured on the shipped build, not on a scaffold:

- **TYPE-11-01** — `TypeElevenPinSurvivesUnrelatedEditTest` green; red under "step 9 → always
  `MODIFICATION_COUNT`". DR-04 probe in the same run: arm A median **6 287 µs**, arm B **22 859 µs**
  (unpinned arm B was 349 700 µs; the 3–5× residual over arm A is unchanged and out of scope, §1.5).
- **TYPE-11-06 pays for itself** — `TYPE11-COST provisioned=11 pinnable=11`, the count the de-risking
  measured (`guarded=11`), now asserted rather than quoted.
- **The one design correction.** §1.11 states that TC-2c is the only assertion that `forFile` passes
  the churn object into `Result.create`. Run against a build that omits it, TC-2c **passed**: a roots
  change moves the library `PsiFile`'s own `modificationStamp` (probed `0 -> 1` on the same instance)
  and `psiFile` is a dependency in both branches. §1.11 removed one confound and left another that
  §1.6 had already recorded. Fixed in the design's own idiom — the dependency set is now
  `dependenciesFor`, one assertable value that `forFile` spreads — and gated by TC-2d, which is red
  under exactly that mutation.
- **Two further guards stopped attributing**, for one shared cause: step 7 (rescued globals) rejects
  every cross-library fixture, so DR-12, DR-14, DR-18 and the new library-`require`s-a-project-module
  case are all green with their *own* guard deleted. No production defect and no lost coverage of the
  user-visible answer — but the guards now have decision-level and report-level gates
  (`TypeElevenIncompleteFrameDecisionTest`, plus two new rows in `LuaTypeManagerRecordingTest`), one
  red each.

### Phase 3 remediation (2026-08-11) — one shipped defect, one doc error, one claim that held

Three findings from the Phase 3 review, each closed by measurement rather than by agreement
(risks-and-gaps Findings 1, 2 and 4):

- **F1 — real, in shipped code.** `forFile`'s `providerRan` flag could not work:
  `CachedValuesManager.getCachedValue`'s `PsiElement` overload discards the lambda of every call
  after the first, so a recompute sets the *first* call's flag and the current call always reads
  `false`. Benign (the always-taken warm branch replayed a frame written moments earlier), but
  misdescribed and one weak-entry eviction away from a wasted pin. The flag is deleted, the §3.7
  report is unconditional at `depth() > 0`, and both halves are asserted in
  `TypeElevenWarmSignalMechanismTest` — the platform's provider identity, and the cold-path replay
  being a set-wise no-op.
- **F2 — a doc error, now closed by an assertion instead of a convention.** §2.3 claimed the
  `dependenciesFor` spread made an omission from `Result.create` "the same edit". It does not;
  inlining the arguments is green under TC-1/2c/2d/3. The review recorded it as uncatchable — it is
  not: **TC-2e** reads the dependency items off the `ParameterizedCachedValue` the platform stored
  and is red, alone, under exactly that inlining.
- **F4 — checked and refuted.** The review read DR-14's step-6 green as misattributed to step 7,
  naming step 4 as a second rejector. The frame says otherwise (`absences=[]`,
  `rescuedGlobals=[global:OuterSeed]`); the ledger's attribution stands and is now asserted.

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
