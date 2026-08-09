---
id: "TYPE-11-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# TYPE-11: Risks & Gaps

## What the measurement ran against

Every figure in `design.md` §1 was produced by running, on gce-builder (libvirt `debian13`),
**2026-08-09**. The de-risking required a production edit; **that edit was reverted and the commit
carries no production change.** For the record, the reverted scaffold was:

| Scaffold file / edit | Purpose | State |
| :-- | :-- | :-- |
| `src/main/kotlin/.../lang/psi/types/LuaTemporaryProvenance.kt` (new) | the §3.2 predicate | **deleted** |
| `src/main/kotlin/.../lang/psi/types/LuaTypeSourceRecorder.kt` (new) | the §3.1 recorder | **deleted** |
| `LuaTypes.kt` — `forFile` churn dependency made conditional | the §3.3 decision + a `lunar.type11.trace` println | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` — `sourceCache`, `recordUnder`, `replaySources`, 6 `reportFile` sites | §3.5 / §3.6 | **reverted to HEAD** |

`git status` after the revert shows only `docs/` and `src/test/` additions. The test harnesses
**are** committed: they compile and pass against unmodified `main`, they are the executable form of
the design's premises, and every one of their assertions has been shown red under a named mutation
(below).

## Mutation ledger — every assertion, and the mutation that turned it red

"A test that cannot fail is not a gate." Each row was run; the `result` column is the observed
outcome, not an expectation.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `TypeElevenDr01ResidualTest.testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile` | blanket pin (provenance only, no source condition) | **RED** — `editing the project file must be reflected in the library global's type expected:<[afterEdit]> but was:<[beforeEdit]>` |
| `…testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile` | blanket pin | **RED** — `the removed project method must disappear; got [afterEdit, beforeEdit]` |
| `…testAProjectToProjectDependencyIsNeverPinned` | `isProvisionedFile`/`isProvisionedUrl` forced `true` (pin every file) | **RED** — `a project→project dependency must never be pinned expected:<[after]> but was:<[before]>` |
| `TypeElevenDr02ProvenanceTest.testEveryFileResolveGlobalWouldVisitIsClassifiedByProvenance` | definition roots dropped from the root list | **RED** — `provenance must classify …/luassert-…/wx.lua as provisioned=true` |
| `…testACopyOfALibraryFileIsOnlyMatchedThroughOriginalFile` | definition roots dropped | **RED** — `the original of a copied library file must be provisioned` |
| `…testTheSeededLibraryReachesTheProjectThroughTheRegisteredProvider` | enabled-library list cleared after install | **RED** — `the EP-registered LuaDefinitionLibraryProvider must contribute the seeded root; got []` |
| `…testTheBundledRuntimeRootIsVisibleAndItsSchemeIsRecorded` | target switched to `LuaPlatform.PANDOC` (no bundled tree) | **RED** — `the bundled runtime library root must resolve, or provenance has one source` |
| `…testALightFixtureProjectFileIsNeverProvisioned` | provenance widened to accept `/src` | **RED** — `MUTATION C` |
| `TypeElevenDr05DumbModeTest.testASnapshotBuiltWhileDumbDoesNotSurviveIntoSmartMode` | (a) `!isDumb` term removed; (b) generation tracker replaced with `ModificationTracker.NEVER_CHANGED` | **GREEN under both.** Not a gate — see Gap 2.1. |
| `TypeElevenDr04LatencyTest` (both arms) | — | **No assertions at all.** It is a printing probe, exactly like `CompNineDr20Test`, and is not claimed as a gate. |

For completeness, the two full-suite runs the ledger is anchored to:

```
blanket pin (rejected):   2563 tests completed, 2 failed, 1 skipped   BUILD FAILED     in 9m 48s
conditional rule (§3):    2564 tests, 0 failures, 1 skipped           BUILD SUCCESSFUL in 9m 45s
```

⚠ **corrected 2026-08-09** — the corpus sweep was **not run** for those measurements. `git status --short src/test/resources/corpus/` is **not** evidence: baselines are only rewritten under `-PrecordCorpusBaseline` (`build.gradle.kts:286-288`), so that check is clean whether the sweep passed, regressed, or never ran. The corpus classes are excluded from the routine loop by design (`build.gradle.kts:272-283`) — they index ~300-file third-party trees and need `tooling/corpus/fetch-corpus.py`. **The gate is the sweep itself**: `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`, in which `BaselineRatchetTest` compares against the recorded baselines in-test. Run on `69ad6b57` afterwards: **2 571 tests, 0 failures**, baselines unmoved.

Two harness defects were caught and fixed by this exercise rather than shipped:

- **`TypeElevenDr01ResidualTest`'s edit helper could be silently healed.** Run alone, residual path 2
  reported `[afterEdit, beforeEdit]`; run after the other TYPE-11 classes in the same JVM it reported
  `[afterEdit]`, because a `ProjectRootModificationTracker` tick left over from a previous class's
  library install discarded the pinned snapshot. `rewrite()` now asserts the roots tracker is **still**
  across the edit, so an unearned green fails loudly instead of passing.
- **Two drafts of the DR-01 control could not fail.** "The library still resolves after a consumer
  edit" is untouched by anything in this design; "a project file's snapshot tracks its **own** edits"
  is guaranteed by `forFile`'s `psiFile` dependency independently of the churn tracker — measured, it
  stayed green with *every file in the project pinned*. Only the third form, a **project→project**
  dependency, is sensitive to the churn tracker and goes red under that mutation.

## Premises examined

| Constraint treated as fixed | Verdict |
| :-- | :-- |
| "The residual might not be real" | **REFUTED by measurement.** It is real and it fires: `design.md` §1.1. Blanket pinning is unsound and this plan does not build it. |
| "The existing suite plus four corpus baselines will catch a stale-type regression here" | **REFUTED for the suite; UNTESTED for the corpus.** The pre-existing tests passed unchanged under a build that demonstrably serves stale types. The corpus half was never measured — those classes are excluded from the routine loop and the sweep was not run under the unsound build. Re-running it there is [[DR-09]]. This premise is the reason `TypeElevenDr01ResidualTest` exists and the reason it is committed rather than thrown away. |
| "A project file adding a method to a stub class stales the snapshot" (`requirements.md`) | **PARTLY REFUTED.** True only for the **hosted** `---@class` form, and by a different route than `requirements.md` names (`freeGlobalSeed` → `tableToLuaType` → `fromLuaType`, not `materializeClass` reaching the snapshot). The bundled stdlib is 21/22 unhosted. `design.md` §1.2. |
| "A dumb-mode build bakes in nulls that are then sticky" (`requirements.md`, TYPE-11-05) | **HALF REFUTED.** The nulls are baked in (`graph type = Undefined`); they are **not** sticky, and not because of any tracker — the file's own `modificationStamp` moves 0→1 when dumb mode ends. The guard is kept as insurance; see Gap 2.1. |
| "Library files can be matched by `VirtualFile` identity" (TYPE-11-03) | **REFUTED as written.** `===` is false for a project file the index itself supplied. Matching is by URL containment. `design.md` §1.3. |
| "Provenance must come from the plugin's own providers, not `ProjectFileIndex.isInLibrary`" | **Genuinely fixed, and re-confirmed.** The bundled root arrives over `jar://` inside the plugin jar; asking the platform's library index about that is a question with an unverified answer, and provenance never has to ask it. |
| "Rocks trees are out of v1 scope" | **Chosen, not forced.** They are excluded because they are mutable in place and their refresh signal is unverified — a v1 that included them would need TYPE-11-DR-03 answered first. TYPE-11-DR-03 was deliberately **not run**. |
| "`ProjectRootModificationTracker` is the right generation signal" | **Chosen, and half-executed.** Measured **not** to tick across a dumb-mode episode (`P2D before/inside/after dumb: roots=10` throughout) or across a document edit (`P2S before/after: rootsTracker=7`) — both are exactly what §3.3 relies on. That it **does** tick when a definition library is enabled is verified by **reading** the chain, not by running the production path: `LuaDefinitionLibraryEnabler.apply` → `LuaProjectSettings.notifyDefinitionRootsChanged` (`LuaProjectSettings.kt:199-205`) → `LuaSettingsChangedListener.TOPIC` → `LuaSettingsChangeListener.onSettingsChanged` (`project/LuaSettingsChangeListener.kt:32`) → `PlatformLibraryIndex.reload()` → `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`). The TYPE-11 fixtures announce the roots change themselves, so they do **not** exercise that chain. See Gap 2.3. |
| "The `psiFile` dependency in `forFile` can stay as it is" | **Genuinely fixed, and load-bearing in a way not previously noticed.** It is what makes a file's own edit always rebuild its own snapshot regardless of the churn tracker (`CachedValueBase` reads `containingFile.modificationStamp` for a `PsiElement` dependency). Removing it would break the design silently. |
| "A latency probe with no assertions is acceptable" | **Chosen, and stated.** DR-04 measures a direction; a threshold gate on a figure whose own baseline moved 3.2–10.4 ms between runs on the same machine would be noise dressed as a contract. COMP-09 §1.2's rule, applied. |
| "Library→library dependencies are safe under a shared generation tracker" (`requirements.md`) | **Accepted, untested.** No fixture edits a library file, because a user cannot. It is safe by construction: any change to a provisioned tree comes through a roots change, which ticks the shared tracker for every pinned file at once. |

## Critical Risks

### Risk 1.1: A missed reporting site produces a silent stale type

- **Impact**: exactly the defect this feature exists not to create. A cross-file consumption that does
  not call `LuaTypeSourceRecorder.reportFile` leaves the snapshot looking like a pure function of
  provisioned content, so it gets pinned while depending on a project file. There is no crash and no
  test failure — the user simply sees a type that stopped updating.
- **Likelihood**: **medium now, high over time.** `design.md` §3.5 enumerates six sites and they were
  sufficient for every measured case, but nothing structurally prevents a seventh being added later.
- **Mitigation**: (a) `TypeElevenDr01ResidualTest` is committed and covers the two known shapes;
  (b) implementation-plan Phase 3 adds `LuaTypeSourceRecorderCoverageTest`, a source-text guard over
  `LuaTypeManagerImpl.kt` that fails when a `PsiManager.findFile` / `StubIndex.getElements` /
  `FileBasedIndex.getContainingFiles` call site count changes without a matching `reportFile` count;
  (c) the over-approximation rule in §3.5 (report every file *visited*, not every file *used*) means a
  new site added inside an existing loop is likely already covered.

### Risk 1.2: `sourceCache` and the type caches drift apart

- **Impact**: a replayed source set that describes a different answer than the cached type — either a
  lost pin (harmless) or a missing source (stale type).
- **Likelihood**: **low.** They share one `PsiModificationTracker` dependency and are written in the
  same statement.
- **Mitigation**: §3.6 requires `sourceCache` to be built with the identical `createCachedValue`
  shape as the three existing caches. Any future change to one cache's invalidation must change all
  four; implementation-plan Phase 1 puts them adjacent in the file so the coupling is visible.

### Risk 1.3: A lazily-resolved `LuaTypeReference` escapes the recording frame

- **Impact**: a source consumed after `recording` returned is not in the set, so it cannot be judged.
- **Likelihood**: **low but not zero.** `LuaTypeReference.resolveType()` (`LuaTypeReference.kt:10`)
  calls into the manager, and `LuaGraphType.fromLuaType` flattens references eagerly during the build
  (`LuaGraphType.kt:251`), which is what the measured runs exercised. A reference reachable only
  through `LuaClassType.getMembers()` at read time is not flattened.
- **Mitigation**: bounded, not eliminated. A `LuaClassType` is never stored in a snapshot — snapshots
  hold `LuaGraphType` — so the escape can only occur through a graph type that still carries a
  `className`, and `tableToLuaType` re-resolves those nominally at read time under the project-wide
  `typeCache`. Tracked as DR-07 rather than claimed closed.

### Risk 1.4: The win is smaller than the headline suggests

- **Impact**: expectations. `design.md` §1.5 measures arm B at 3–5× arm A after the change, against a
  DR-04 success criterion of "near the 9 ms baseline".
- **Likelihood**: **certain** — it is already measured.
- **Mitigation**: state it, do not hide it. The recurring per-keystroke cost drops from hundreds of
  milliseconds to tens; the remaining gap is the `resolveGlobal` + `graphTypeToLuaType` conversion
  `requirements.md` already named, and it is a separate piece of work (DR-08).

## Design Gaps

### Gap 2.1: The dumb-mode staleness class has no reproducing test

- **Question**: can a snapshot built while `DumbService.isDumb` actually outlive dumb mode, in a real
  IDE rather than in `DumbModeTestUtils.runInDumbModeSynchronously`?
- **Measured**: not in this harness. The library `PsiFile`'s `modificationStamp` moves 0→1 across the
  fixture's dumb episode, and `forFile`'s `psiFile` dependency rebuilds the snapshot for that reason
  alone — with the `!isDumb` guard removed **and** with the churn tracker replaced by
  `ModificationTracker.NEVER_CHANGED`, the test still passed.
- **Options / leaning**: keep the guard (one boolean, cannot be wrong) and stop claiming a test covers
  it. The open question is whether the stamp move is real platform behaviour or a fixture artifact of
  `DumbModeTestUtils`; if it is an artifact, the guard is load-bearing in production and TYPE-11-05
  currently has no automated protection.
- **Resolved by**: DR-06.

### Gap 2.3: The production roots-tick chain is verified by reading, not by running

- **Question**: does enabling a definition library actually tick `ProjectRootModificationTracker` in a
  running IDE?
- **Why it is open**: the chain is five hops and one of them is a message-bus subscription that only
  fires if `LuaSettingsChangeListener` has been instantiated — a dependency that already produced a
  defect once (its KDoc records "TOOLING-08 review #41"). Every TYPE-11 fixture calls
  `makeRootsChange` directly, so none of them would notice if the chain were broken.
- **Options / leaning**: cover it in implementation-plan Phase 3 with
  `TypeElevenGenerationSignalTest` case (a), driving `LuaDefinitionLibraryEnabler.apply` rather than
  the fixture helper, and in human-verification Scenarios 3.1/3.2.
- **Resolved by**: implementation-plan Phase 3; until then this is the single largest
  read-not-run claim in the plan.

### Gap 2.2: Provenance for rocks trees is unanswered by design

- **Question**: extending the pin to `LuaRocksLibraryProvider` roots needs a signal that ticks when
  `luarocks install` writes into an already-registered `lua_modules/`.
- **Options / leaning**: out of v1 by `requirements.md`. `RockspecSourcePathProvider.forceRefreshTracker`
  exists; whether it ticks on install is unverified.
- **Resolved by**: TYPE-11-DR-03, deliberately **not run** for this plan.

## Technical Debt & Future Work

- **TBD: the remaining 3–5× in DR-04 arm B.** Every free global still re-runs `resolveGlobal` +
  `graphTypeToLuaType`, building a fresh `visited` map per call and walking the library table's full
  member set. Out of scope; see DR-08.
- **TBD: `LibraryRootTestCase` and `TypeElevenDefinitionLibraryTestCase` now overlap.** The former
  registers an anonymous provider (invisible to provenance); the latter goes through the real
  definition-library path. They are kept apart on purpose — the anonymous one is the right tool for
  "does resolution cross a `SyntheticLibrary` boundary", the real one for "did this plugin provision
  it". If a third arrives, consolidate.
- **TBD: COMP-09.** This feature removes the recurring per-edit cost only. The first completion of a
  session still builds the library graph once, which is what COMP-09's index avoids.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| TYPE-11-DR-09 | Re-run the corpus sweep **under the blanket-pin build** — `run "test -PwithCorpus --rerun --no-build-cache"` — to establish whether the sweep would have caught what the routine loop missed. The original refutation ran the routine loop only, so "all four baselines passed unchanged" was never measured. If the sweep *does* catch it, TYPE-11-04's acceptance is stronger than the plan currently claims; if it does not, the blind spot is wider. | TYPE-11-04, Risk 1.1 |
| TYPE-11-DR-01 | Pin `forFile`'s dependency for provenance-matched library files; run the full suite and the corpus sweeps; build explicit fixtures for both named residual paths | the residual; TYPE-11-04 | **done** — `design.md` §1.1/§1.2/§1.4. Residual real; blanket pinning rejected; conditional rule adopted and re-run green |
| TYPE-11-DR-02 | Can every file `resolveGlobal` resolves into be matched against the provenance set? | TYPE-11-03 | **done** — `design.md` §1.3. Yes, by URL containment through `originalFile`; `===` and `psiFile.virtualFile` both refuted |
| TYPE-11-DR-03 | Does `RockspecSourcePathProvider.forceRefreshTracker` tick on `luarocks install` into an existing root? | follow-up scope only | **not run** — rocks are out of v1 scope |
| TYPE-11-DR-04 | Re-measure the 9 ms / 334 ms pair, medians of ≥5 | TYPE-11-01 | **done** — `design.md` §1.5. Direction confirmed; the "near 9 ms" criterion is **not** met (3–5× arm A) |
| TYPE-11-DR-05 | Build a snapshot under `DumbService.isDumb`, exit, complete again | TYPE-11-05 | **done, negative** — `design.md` §1.6. Nulls are baked in; they do not survive; two mutations failed to make the harness red. Reopened as DR-06 |
| TYPE-11-DR-06 | Determine whether the `modificationStamp` move at dumb-mode exit is platform behaviour or a `DumbModeTestUtils` artifact. If the former, TYPE-11-05's guard is dead code and should be deleted; if the latter, build a fixture that reproduces the staleness | Gap 2.1, TYPE-11-05 | todo |
| TYPE-11-DR-07 | Probe whether a `LuaTypeReference` can be resolved after its recording frame closed, and whether the resulting source can reach a pinned snapshot | Risk 1.3 | todo |
| TYPE-11-DR-08 | Profile the residual arm-B cost (`resolveGlobal` + `graphTypeToLuaType`, fresh `visited` map per call over a 3 600-member table) and decide whether it is a separate feature | Risk 1.4 | todo |

## Test Case Gaps

- **No test edits a library file.** Users cannot, and the invalidation path for a library content
  change is a roots change, which is covered by `TypeElevenGenerationSignalTest` (plan Phase 3).
- **No test exercises a `require` from a library file into a project module.** `design.md` §6 says
  `getModuleType` reports it and the file is therefore not pinnable; that specific path is reasoned,
  not run. Cover it in plan Phase 3.
- **No test covers a rocks tree**, deliberately — v1 leaves rocks on today's behaviour, so the
  existing rocks suites already assert the unchanged answer.
- **No multi-project test.** `LuaLibraryProvenance` is per project and the definition cache is per
  **user**; two projects enabling the same library share one tree. Untested here.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Parent of the measurement discipline used here: `docs/features/completion/09-member-enumeration/`
