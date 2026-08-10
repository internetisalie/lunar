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

### Second measurement round (2026-08-09, `main` @ `2e06bc86`) — DR-09 and DR-10

Same discipline, same builder, same revert. This round tested the **premise** behind the recorder
(`design.md` §1.7 / DR-10) and ran the corpus sweep under the unsound build (DR-09). Its scaffold,
also reverted — `git diff -- src/main/` is empty at commit:

| Scaffold file / edit | Purpose | State |
| :-- | :-- | :-- |
| `src/main/kotlin/.../lang/psi/types/LuaTemporaryProvenance.kt` (new, 45 lines) | `object` with `rootUrls(project)` memoized on the **project** via `CachedValuesManager.getManager(project).getCachedValue(project)` with `ProjectRootModificationTracker` + `state.targetModificationTracker`; roots = `RuntimeLibraryProvider(project).getLibraryRoot(state.getTarget())` + EP-registered `LuaDefinitionLibraryProvider.getRootsToWatch(project)`; `isProvisionedUrl(project, url)` = `url == root \|\| url.startsWith("$root/")`; `isProvisioned(file)` reads `file.originalFile.virtualFile?.url` | **deleted** |
| `LuaTypes.kt` — `forFile` | added `private val BLANKET_PIN: Boolean = "true".toBoolean()` in the companion (a parsed string, not a `const`, so neither arm folds to unreachable code) and made the churn dependency `if (BLANKET_PIN && !DumbService.isDumb(project) && LuaTemporaryProvenance.isProvisioned(psiFile)) ProjectRootModificationTracker.getInstance(project) else PsiModificationTracker.MODIFICATION_COUNT`; imports `DumbService`, `ProjectRootModificationTracker` | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` — file-level `internal val RESTRICT_PROVISIONED_GLOBALS: Boolean` (same parsed-string form) | the DR-10 arm switch: `true` for the restriction run, `false` for the DR-09 run | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` — `doResolveGlobal` first statement | `if (RESTRICT_PROVISIONED_GLOBALS && here != null && LuaTemporaryProvenance.isProvisioned(here)) return typeOfProvisionedGlobal(name, here)` | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` — new `private fun typeOfProvisionedGlobal(name, exclude)` | `typeOfGlobalIn` over `GlobalSearchScope.allScope(project)` with `.filter { LuaTemporaryProvenance.isProvisionedUrl(project, it.url) }` inserted before the `PsiManager.findFile` hop | **reverted to HEAD** |

No `plugin.xml`, `build.gradle.kts` or test-source change was needed: both arms are selected by
editing the two `Boolean` initialisers and re-running, and every assertion consumed is one of the
committed TYPE-11 harnesses.

### Third measurement round (2026-08-10, `main` @ `07a8fa44`) — DR-11 and DR-12

Same discipline, same builder, same revert (`git diff -- src/main/` empty at commit). This round
reproduced the two Step 9 blockers and priced the rule that closes them (`design.md` §1.8). Unlike
the first two rounds the scaffold is the **whole** design of §3.1–§3.6, not a cut-down of it, plus a
mode switch, because a blocker about under-recording cannot be measured against a scaffold that does
not record.

| Scaffold file / edit | Purpose | State |
| :-- | :-- | :-- |
| `src/main/kotlin/.../lang/psi/types/LuaReviewScaffold.kt` (new, 186 lines) | three `object`s in one file: `LuaReviewRecorder` (frames of `urls` / `misses` / `warm` / `warmUnreplayed`, `recording`, `report`, `reportFile`, `reportMiss`, `reportWarmSnapshot`, `replay`, `depth`, and `snapshotFrames: WeakHashMap<LuaTypes, Frame>`); `LuaReviewProvenance` (§3.2 verbatim — target root + EP-registered `LuaDefinitionLibraryProvider.getRootsToWatch`, `url == root \|\| url.startsWith("$root/")`, read through `originalFile`); `LuaReviewMode` (mode from `System.getProperty("lunar.review.mode", …)` — a parsed string, so no arm folds away — plus a `Decision` record that computes `pinnableCond`, `pinnableB1`, `pinnableB1Globals`, `pinnableB4`, `pinnableConservative` and `pinnableGuarded` **simultaneously**, so one run prices every candidate rule, and a `decisions` map + `TYPE11-REVIEW` trace line) | **deleted** |
| `LuaTypes.kt` — `forFile` | `var computed` around `getCachedValue`; `recording { buildSnapshot }`; `snapshotFrames[snapshot] = frame`; churn from `LuaReviewMode.churnFor(psiFile, frame)`; warm hits reported via `reportWarmSnapshot(psiFile, result)` when `depth() > 0` | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` | `sourceCache: CachedValue<MutableMap<String, Frame>>` built exactly like the three existing caches; `recordUnder(key, body)` / `replaySources(key)`; the three doors (`resolveType`, `resolveModule`, `resolveGlobal`) wrapped so a miss records and a hit replays; a `reportMiss` on every null-returning path of all three; the six §3.5 `reportFile` sites | **reverted to HEAD** |

Modes, selected by editing one default string between runs: `off` (today's behaviour), `cond` (§3 as
written before this round), `guarded` (§3 plus the absence rule and the snapshot replay).
`conservative` (the literal "any incomplete recording is unpinnable") is not a separate run — its
verdict is one of the columns every run records.

**Reading test results on the builder: `--rerun` does not clear `build/test-results/test/`.** The
previous run's XML sits there for the whole of the next run and is only replaced when `:test`
finishes. Reading it mid-run reports the *previous* build's verdict. Check `ls -l` timestamps against
`date` on the builder before believing any XML — this round nearly recorded a false "Q1 is green"
from 2-hour-old files.

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
| `…testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile` (2026-08-09, second round) | provisioned-context globals restricted to provisioned scope (DR-10) **+** blanket pin | **RED at a different assertion — `:88`, not `:93`.** `the library global must take the project declaration's members expected:<[beforeEdit]> but was:<[]>`. The move from `:93` to `:88` is the DR-10 scaffold's liveness proof: the restriction is what turns `[beforeEdit]` into `[]`. |
| `…testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile` (second round) | same | **RED at `:139`, message unchanged** — `the removed project method must disappear; got [afterEdit, beforeEdit]`. Identical to blanket pinning: this path never enters `typeOfGlobalIn`. |
| Blanket pin alone, re-run in the second round for DR-09 | blanket pin (provenance + `!isDumb`), restriction off | **RED at `:93` and `:139`** — reproduces the first round's signature exactly, which is what makes the `:88` above attributable to the restriction and not to the pin. |
| `TypeElevenDr11LateDeclarationTest.testADeclarationWrittenAfterTheLibrarySnapshotWasBuiltStillReachesIt` (2026-08-10, third round) | the §3 conditional rule **as written** — i.e. without §3.3 step 4 (no absence recording) | **RED at `:83`** — `a project declaration written AFTER the library snapshot was built must still reach it expected:<[afterDeclared]> but was:<[]>`, with `TYPE11-REVIEW file=alpha.lua pinnable=true sources=0` on the line above and `roots 3 -> 3` proving no tick healed or condemned it. |
| `TypeElevenDr12WarmInnerSnapshotTest.testALibraryWhoseInnerLibrarySnapshotWasServedWarmStillTracksTheProjectFile` (third round) | the §3 conditional rule as written — i.e. without §3.7 (no `forFile` replay) | **RED at `:75`** — `expected:<[afterEdit]> but was:<[beforeEdit]>`, with `file=a.lua pinnable=true sources=1 outside=[] warm=[b.lua]` naming the cause and `file=b.lua pinnable=false outside=[p.lua]` showing the inner file was judged correctly. |
| The same two, under the corrected rule (`mode=guarded`) | — | **GREEN, and for the right reason**: `file=alpha.lua pinnable=false misses=[global:sharedByProject]` and `file=a.lua pinnable=false sources=2 outside=[p.lua] warm=[b.lua] warmUnreplayed=[]`. In the same run 11 of 11 provisioned files are still pinned, so the green is not "pinning switched off". |

For completeness, the two full-suite runs the ledger is anchored to:

```
blanket pin (rejected):   2563 tests completed, 2 failed, 1 skipped   BUILD FAILED     in 9m 48s
conditional rule (§3):    2564 tests, 0 failures, 1 skipped           BUILD SUCCESSFUL in 9m 45s
```

⚠ **corrected 2026-08-09** — the corpus sweep was **not run** for those measurements. `git status --short src/test/resources/corpus/` is **not** evidence: baselines are only rewritten under `-PrecordCorpusBaseline` (`build.gradle.kts:286-288`), so that check is clean whether the sweep passed, regressed, or never ran. **The gate is the sweep itself**: `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`. Run on `69ad6b57` afterwards: **2 571 tests, 0 failures**, baselines unmoved.

⚠⚠ **corrected again 2026-08-09 (second round)** — the correction above named the wrong comparator.
`BaselineRatchetTest` does **not** compare the recorded baselines: its 35 tests build synthetic
`CorpusMetrics` and ratchet them against throwaway files in a JUnit `TemporaryFolder`
(`BaselineRatchetTest.kt:28`, `:406`, `:416`). It is also **not** gated by `-PwithCorpus` —
`excludeTestsMatching("*Corpus*")` is case-sensitive and does not match the lowercase
`…lunar.corpus.` package segment, which the class's own KDoc states (`BaselineRatchetTest.kt:19-24`);
the same holds for `LexerInvariantsTest` and `ParseOracleTest`. The recorded baselines are compared
only in `LuaCorpusSweepTest.sweepAndRatchet` → `CorpusGuards.assertRatchet`
(`LuaCorpusSweepTest.kt:97-101`) and in `LuaTortureCorpusTest`. See `design.md` §1.4 ⚠⚠ and §1.7.

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
| "The existing suite plus four corpus baselines will catch a stale-type regression here" | **REFUTED for both halves, by measurement.** The pre-existing tests passed unchanged under a build that demonstrably serves stale types; DR-09 then re-ran the sweep on that same build and **all four baselines compared unchanged** (`2571 tests completed, 2 failed` — both TYPE-11's own). See "DR-09 measured" below: the sweep is a single pass over an unedited tree, so it cannot observe a stale-cache defect at any corpus size. This premise is the reason `TypeElevenDr01ResidualTest` exists and the reason it is committed rather than thrown away. |
| "A project file adding a method to a stub class stales the snapshot" (`requirements.md`) | **PARTLY REFUTED.** True only for the **hosted** `---@class` form, and by a different route than `requirements.md` names (`freeGlobalSeed` → `tableToLuaType` → `fromLuaType`, not `materializeClass` reaching the snapshot). The bundled stdlib is 21/22 unhosted. `design.md` §1.2. |
| "A dumb-mode build bakes in nulls that are then sticky" (`requirements.md`, TYPE-11-05) | **HALF REFUTED.** The nulls are baked in (`graph type = Undefined`); they are **not** sticky, and not because of any tracker — the file's own `modificationStamp` moves 0→1 when dumb mode ends. The guard is kept as insurance; see Gap 2.1. |
| "Library files can be matched by `VirtualFile` identity" (TYPE-11-03) | **REFUTED as written.** `===` is false for a project file the index itself supplied. Matching is by URL containment. `design.md` §1.3. |
| "Provenance must come from the plugin's own providers, not `ProjectFileIndex.isInLibrary`" | **Genuinely fixed, and re-confirmed.** The bundled root arrives over `jar://` inside the plugin jar; asking the platform's library index about that is a question with an unverified answer, and provenance never has to ask it. |
| "Rocks trees are out of v1 scope" | **Chosen, not forced.** They are excluded because they are mutable in place and their refresh signal is unverified — a v1 that included them would need TYPE-11-DR-03 answered first. TYPE-11-DR-03 was deliberately **not run**. |
| "`ProjectRootModificationTracker` is the right generation signal" | **Chosen, and half-executed.** Measured **not** to tick across a dumb-mode episode (`P2D before/inside/after dumb: roots=10` throughout) or across a document edit (`P2S before/after: rootsTracker=7`) — both are exactly what §3.3 relies on. That it **does** tick when a definition library is enabled is verified by **reading** the chain, not by running the production path: `LuaDefinitionLibraryEnabler.apply` → `LuaProjectSettings.notifyDefinitionRootsChanged` (`LuaProjectSettings.kt:199-205`) → `LuaSettingsChangedListener.TOPIC` → `LuaSettingsChangeListener.onSettingsChanged` (`project/LuaSettingsChangeListener.kt:32`) → `PlatformLibraryIndex.reload()` → `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`). The TYPE-11 fixtures announce the roots change themselves, so they do **not** exercise that chain. See Gap 2.3. |
| "The `psiFile` dependency in `forFile` can stay as it is" | **Genuinely fixed, and load-bearing in a way not previously noticed.** It is what makes a file's own edit always rebuild its own snapshot regardless of the churn tracker (`CachedValueBase` reads `containingFile.modificationStamp` for a `PsiElement` dependency). Removing it would break the design silently. |
| **"`doResolveGlobal` must keep searching project scope first (BUG-427), so the recorder has to over-approximate around it"** (`design.md` §3.5) | **REFUTED as the recorder's justification, by measurement — `design.md` §1.7.** The constraint *is* removable: a scaffold that sends a provisioned file's globals to a provisioned-only candidate set compiles and runs. Removing it does **not** make blanket pinning sound (`2571 tests completed, 2 failed`; residual path 2 still reports `[afterEdit, beforeEdit]`), because that path reaches the snapshot through `materializeClass` → `collectMethodMembers`, which queries `StubIndex.getAllKeys(LuaGlobalDeclarationIndex.KEY, project)` with **no scope argument at all** (`LuaTypeManagerImpl:427-432`). Removing it also costs real behaviour: residual path 1's library global stops resolving (`[beforeEdit]` → `[]`). The recorder stays; its stated *reason* was wrong, and §3.5 now says so. |
| "The corpus half of TYPE-11-04's acceptance is `BaselineRatchetTest` comparing recorded baselines under `-PwithCorpus`" (this document's own 2026-08-09 correction) | **REFUTED by reading the tests it names.** `BaselineRatchetTest` ratchets synthetic metrics against `TemporaryFolder` files and runs in the **routine** loop; the recorded baselines are compared only by `LuaCorpusSweepTest`/`LuaTortureCorpusTest`. A correction that names the wrong gate is the same defect as the claim it corrected. |
| **"An empty recorded source set means the file depends on nothing"** (§3.3 as written before this round) | **REFUTED by measurement — `design.md` §1.8.** It also means "the answer was unknown", which is where staleness lives. Two shapes, both red: a global nothing declares yet (`expected:<[afterDeclared]> but was:<[]>`) and a nested `forFile` served warm (`expected:<[afterEdit]> but was:<[beforeEdit]>`). The recorder must distinguish *no sources* from *sources unknown*. |
| **"A pinned file is re-judged on its next build, which the global tracker guarantees happens"** (§3.3 as written) | **REFUTED, and it was self-contradictory.** `PsiModificationTracker.MODIFICATION_COUNT` is exactly the dependency a pin removes, so a pinned file has no next build until a generation tick. A pin must be correct at the moment it is taken. |
| "Closing the absence case will cost most of the feature's value" (the reason to fear the fix) | **REFUTED by counting.** 11 of 11 provisioned files stay pinnable. The literal rule costs one file, `io.lua`, and only for `resolveType("boolean\|nil")`-shaped misses; restricting the absence rule to global resolution costs zero. Measured before any machinery was designed, which is what made the machinery unnecessary. |
| "A blanket 'warm nested `forFile` ⇒ unpinnable' is the natural fix for B4" | **Rejected on measured cost, not on taste.** An inner library file that is itself pinned stays warm across ticks, so every library→library chain would lose its pin permanently. Replaying the inner frame gives the identical verdict at zero cost (`guarded=11`). |
| "There is no platform tracker that ticks when a global declaration appears" | **REFUTED — one exists, and it is still not used.** `FileBasedIndex.getIndexModificationStamp(ID, Project)` (the sole `…ModificationStamp` member of `FileBasedIndex`, confirmed by `javap` on `lib/intellij.platform.indexing.jar`) measured `before=16 afterUnrelatedEdit=16 afterNewDeclaration=17`. Priced and declined in `design.md` §9: a second invalidation axis, a dumb-hostile index query inside a validity check, and no documented monotonicity, to optimise a rule that costs nothing. |
| "A latency probe with no assertions is acceptable" | **Chosen, and stated.** DR-04 measures a direction; a threshold gate on a figure whose own baseline moved 3.2–10.4 ms between runs on the same machine would be noise dressed as a contract. COMP-09 §1.2's rule, applied. |
| "Library→library dependencies are safe under a shared generation tracker" (`requirements.md`) | **Accepted, untested.** No fixture edits a library file, because a user cannot. It is safe by construction: any change to a provisioned tree comes through a roots change, which ticks the shared tracker for every pinned file at once. |

## DR-09 measured: the sweep does **not** catch it, and it structurally cannot

The blanket-pin build (provenance + `!isDumb`, no source condition — the configuration
`design.md` §1.1 rejected) re-run as `run "test -PwithCorpus --rerun --no-build-cache"`:

```
TypeElevenDr01ResidualTest > testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile FAILED
    junit.framework.AssertionFailedError at TypeElevenDr01ResidualTest.kt:139
TypeElevenDr01ResidualTest > testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile FAILED
    junit.framework.AssertionFailedError at TypeElevenDr01ResidualTest.kt:93

2571 tests completed, 2 failed, 1 skipped
> Task :test FAILED
BUILD FAILED in 18m 33s
```

```
DR-01 path1 before edit: libAlias  members = [beforeEdit]
DR-01 path1 after edit:  libAlias  members = [beforeEdit]   <- stale
DR-01 path2 before edit: libHandle members = [beforeEdit]
DR-01 path2 after edit:  libHandle members = [afterEdit, beforeEdit]   <- stale
```

Every corpus class green in that same run (XML timestamps checked against the run, not inherited):

```
BaselineRatchetTest      tests=35 failures=0      LuaCorpusSweepTest      tests=4 failures=0
LexerInvariantsTest      tests=8  failures=0      LuaInspectionParityTest tests=1 failures=0
ParseOracleTest          tests=14 failures=0      LuaTortureCorpusTest    tests=1 failures=0
```

**Answer: the blind spot is wider than the plan claimed.** All four recorded baselines — luacheck,
luarocks, penlight, zerobrane — compare unchanged under a build that serves stale types, so
TYPE-11-04's acceptance rests on `TypeElevenDr01ResidualTest` and on nothing else. `design.md` §1.1's
"the existing suite is not a gate for this" now extends to the corpus sweep.

**And this is structural, not a gap in corpus coverage.** `CorpusSweep.run`
(`CorpusSweep.kt:75-100`) is a **single pass**: it applies a module root, sweeps every file once for
parse/require tallies, then collects inspection hits. It never edits a file. This defect class is
"a snapshot built before an edit is still served after it" — it cannot exist without an edit, so no
single-pass sweep observes it however large the corpus grows. Two further reasons it could not have
fired here: the sweep installs no definition library, and every corpus file is a *project* file,
which the pin never touches.

**Consequence for the plan.** Do not widen the corpus to chase this. The gate for TYPE-11-04 is an
edit-then-reread fixture (`TypeElevenDr01ResidualTest`) plus the Risk 1.1 source-text guard; the
sweep's role in TYPE-11-04 is only to show that nothing *else* moved.

## Critical Risks

### Risk 1.1: A missed reporting site produces a silent stale type

- **Impact**: exactly the defect this feature exists not to create. A cross-file consumption that does
  not call `LuaTypeSourceRecorder.reportFile` leaves the snapshot looking like a pure function of
  provisioned content, so it gets pinned while depending on a project file. There is no crash and no
  test failure — the user simply sees a type that stopped updating.
- **Likelihood**: **medium now, high over time.** `design.md` §3.5 enumerates six sites and they were
  sufficient for every measured case, but nothing structurally prevents a seventh being added later.
- **Widened by the third round.** A site can also be missed by *not existing*: the two shapes in
  `design.md` §1.8 have all six sites correctly wired and still pin a stale snapshot, because the
  thing that needed recording was an **absence** and a **cache hit**. The Phase 3 source-text guard
  counts call sites and would not have caught either. Treat "the recorder is complete" as a claim
  about three sets, not one.
- **Escalated by DR-09.** The corpus sweep was the last remaining candidate for a broad safety net and
  it is measured blind to this class (below). `TypeElevenDr01ResidualTest` plus the Phase 3 source-text
  guard are the whole defence; there is no third line.
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
- **Incidental finding (third round), not a TYPE-11 blocker: `resolveType` is called with unparsed
  type expressions.** Building `io.lua`'s snapshot issues five `resolveType` calls whose `name`
  arguments are `boolean|nil`, `integer|nil`, `string|integer`, `string|number|nil` and
  `fun(): string` — union and function *type expressions*, not names any index can hold. Each answers
  null and each writes a permanent `null` entry into `typeCache` under that key. The route is
  `LuaTypeMember(…, LuaTypeReference(member.typeName, decl))` → `LuaTypeReference.resolveType()` →
  `LuaTypeManager.resolveType(rawString)`. Harmless today (the null falls back), but it is why the
  absence rule is scoped to *global* resolution in `design.md` §3.1 step 5: a "resolution answered
  nothing" signal that fires on `boolean|nil` measures nothing about declarations. Worth a separate
  look at whether these should be parsed by `TypeParser` before reaching the door.
- **TBD: COMP-09.** This feature removes the recurring per-edit cost only. The first completion of a
  session still builds the library graph once, which is what COMP-09's index avoids.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| TYPE-11-DR-09 | Re-run the corpus sweep **under the blanket-pin build** — `run "test -PwithCorpus --rerun --no-build-cache"` — to establish whether the sweep would have caught what the routine loop missed. | TYPE-11-04, Risk 1.1 | **done, negative — see "DR-09 measured" below.** |
| TYPE-11-DR-10 | Is the recorder's premise removable? Restrict a provisioned file's global resolution to provisioned scope, then blanket-pin, and see whether the residual still fires. | the recorder's existence; `design.md` §3.5, §9 | **done, negative** — `design.md` §1.7. `2571 tests completed, 2 failed`. The recorder survives; §3.5's stated justification was wrong and is corrected. |
| TYPE-11-DR-01 | Pin `forFile`'s dependency for provenance-matched library files; run the full suite and the corpus sweeps; build explicit fixtures for both named residual paths | the residual; TYPE-11-04 | **done** — `design.md` §1.1/§1.2/§1.4. Residual real; blanket pinning rejected; conditional rule adopted and re-run green |
| TYPE-11-DR-02 | Can every file `resolveGlobal` resolves into be matched against the provenance set? | TYPE-11-03 | **done** — `design.md` §1.3. Yes, by URL containment through `originalFile`; `===` and `psiFile.virtualFile` both refuted |
| TYPE-11-DR-03 | Does `RockspecSourcePathProvider.forceRefreshTracker` tick on `luarocks install` into an existing root? | follow-up scope only | **not run** — rocks are out of v1 scope |
| TYPE-11-DR-04 | Re-measure the 9 ms / 334 ms pair, medians of ≥5 | TYPE-11-01 | **done** — `design.md` §1.5. Direction confirmed; the "near 9 ms" criterion is **not** met (3–5× arm A) |
| TYPE-11-DR-05 | Build a snapshot under `DumbService.isDumb`, exit, complete again | TYPE-11-05 | **done, negative** — `design.md` §1.6. Nulls are baked in; they do not survive; two mutations failed to make the harness red. Reopened as DR-06 |
| TYPE-11-DR-06 | Determine whether the `modificationStamp` move at dumb-mode exit is platform behaviour or a `DumbModeTestUtils` artifact. If the former, TYPE-11-05's guard is dead code and should be deleted; if the latter, build a fixture that reproduces the staleness | Gap 2.1, TYPE-11-05 | todo |
| TYPE-11-DR-07 | Probe whether a `LuaTypeReference` can be resolved after its recording frame closed, and whether the resulting source can reach a pinned snapshot | Risk 1.3 | todo |
| TYPE-11-DR-08 | Profile the residual arm-B cost (`resolveGlobal` + `graphTypeToLuaType`, fresh `visited` map per call over a 3 600-member table) and decide whether it is a separate feature | Risk 1.4 | todo |
| TYPE-11-DR-11 | Step 9 blocker B1: does a build whose global resolution answered **nothing** get pinned, and does the declaration written afterwards fail to reach it? | TYPE-11-06, `design.md` §3.3/§3.4 | **done, positive (the defect is real)** — `design.md` §1.8. `expected:<[afterDeclared]> but was:<[]>`. Closed by §3.3 step 4; measured cost **zero** pinned files. |
| TYPE-11-DR-12 | Step 9 blocker B4: is the interleaving reachable in which a nested `forFile` is served warm, so the outer library file records an incomplete source set and is pinned? | TYPE-11-06, `design.md` §3.6 | **done, positive (the defect is real, and needs no roots tick)** — `design.md` §1.8. `expected:<[afterEdit]> but was:<[beforeEdit]>`. Closed by §3.7 (replay), not by blanket-unpinnable; measured cost **zero** pinned files. |
| TYPE-11-DR-13 | Price the alternative to blanket-unpinnable for B1: is there a tracker that ticks when a global declaration appears? | `design.md` §9 | **done, not adopted** — `FileBasedIndex.getIndexModificationStamp(LuaGlobalAssignmentIndex.KEY, project)` exists and behaves as hoped (`16 / 16 / 17`), but the rule it would optimise costs nothing, so the second invalidation axis buys nothing. |

## Test Case Gaps

- **No test edits a library file.** Users cannot, and the invalidation path for a library content
  change is a roots change, which is covered by `TypeElevenGenerationSignalTest` (plan Phase 3).
- **No test exercises a `require` from a library file into a project module.** `design.md` §6 says
  `getModuleType` reports it and the file is therefore not pinnable; that specific path is reasoned,
  not run. Cover it in plan Phase 3.
- **No test covers a rocks tree**, deliberately — v1 leaves rocks on today's behaviour, so the
  existing rocks suites already assert the unchanged answer.
- **Closed by the third round**: the absence shape and the warm-inner-snapshot shape now have
  fixtures (`TypeElevenDr11LateDeclarationTest`, `TypeElevenDr12WarmInnerSnapshotTest`), each shown
  red under the rule without its guard.
- **No fixture asserts the pinnable *count*.** The value of the feature and the correctness of the
  rule pull in opposite directions, and only counting distinguishes "closed the hole" from "pinned
  nothing". The third round counted with a scaffold (`guarded=11`); nothing committed does. Phase 3
  should add it alongside `LuaTypeSourceRecorderCoverageTest`.
- **No multi-project test.** `LuaLibraryProvenance` is per project and the definition cache is per
  **user**; two projects enabling the same library share one tree. Untested here.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Parent of the measurement discipline used here: `docs/features/completion/09-member-enumeration/`
