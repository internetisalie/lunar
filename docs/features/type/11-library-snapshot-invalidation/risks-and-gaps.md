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

### Fourth measurement round (2026-08-11, `main` @ `1e9a91c1`) — DR-14 and DR-15

Same discipline, same builder, same revert (`git diff -- src/main/` empty at commit). This round
re-created the third round's scaffold and priced two new candidate rules against Step 9 blockers V1
and V2. Baseline established first, on unmodified `main`, before any edit:
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` →
**2565 tests, 1 skipped, 0 failures, BUILD SUCCESSFUL in 9m 45s** (XML timestamps checked against
`date` on the builder).

| Scaffold file / edit | Purpose | State |
| :-- | :-- | :-- |
| `src/main/kotlin/.../lang/psi/types/LuaReviewScaffold.kt` (new) | `LuaReviewRecorder` (frames of `urls`/`misses`/`warm`/`warmUnreplayed`, plus two new fields: `inProgressHits` for DR-14 and `projectEmptyBroad`/`projectEmptyRescued` for DR-15; `recording`, `report`, `reportFile`, `reportMiss`, `reportInProgressHit`, `reportProjectEmptyBroad`, `reportProjectEmptyRescued`, `reportWarmSnapshot`, `replay`, `depth`, `snapshotFrames: WeakHashMap<LuaTypes, Frame>`); `LuaReviewProvenance` (§3.2 verbatim); `LuaReviewMode` (mode from `System.getProperty("lunar.review.mode", …)`, a parsed string; a `Decision` class computing `cond`, `guarded`, `b14`, `dr15Broad`, `dr15Rescued` **simultaneously**; `churnFor` returns `Any` — not `ModificationTracker` — because `PsiModificationTracker.MODIFICATION_COUNT` is a `Key` sentinel the platform special-cases, not a `ModificationTracker` instance (`javap` on `intellij.platform.core.jar`: `public static final Key MODIFICATION_COUNT`), which the compiler caught (`Return type mismatch: expected 'ModificationTracker', actual '(Key<Any!>..Key<*>?)'`) when the first draft declared the narrower return type; a `TYPE11-REVIEW` trace line and a `reviewCostTotals()` dump) | **deleted** |
| `LuaTypes.kt` — `forFile` | the same `var computed` / `recording { buildSnapshot }` / `snapshotFrames[snapshot] = frame` / `churnFor` wiring as the third round, **plus** a one-line `println` at the `inProgressSnapshot` early return (Q14a's instrument) and a `reportInProgressHit` call when `depth() > 0` | **reverted to HEAD** |
| `LuaTypeManagerImpl.kt` | the third round's `sourceCache`/`recordUnder`/`replaySources` plus the six §3.5 `reportFile` sites and the B1 `reportMiss`, **plus** `doResolveGlobal` computing the DR-15 columns: `reportProjectEmptyBroad("global:$name")` whenever the project-scope pass alone answers null, `reportProjectEmptyRescued("global:$name")` only when that null is then rescued by the all-scope fallback | **reverted to HEAD** |
| `src/test/kotlin/.../type/TypeElevenDr14Dr15CostProbeTest.kt` (new) | the `REVIEW-COST TOTALS` probe: enumerates the 10 bundled stdlib files plus the definition library, builds each once in a clean epoch and dumps every candidate rule's verdict | **deleted with the scaffold** |

**Mode is a compile-time default string, not a runtime flag.** `-Dlunar.review.mode=…` on the
`gradlew` command line does **not** reach the forked test JVM — `build.gradle.kts` has no
`systemProperty` passthrough for it (confirmed by running with the flag: the trace kept printing
`mode=guarded` regardless). Every mode switch below was a source edit to the literal default,
matching the third round's own stated method ("Modes, selected by editing one default string between
runs").

**A same-JVM cross-test contamination trap, caught red-handed.** The very first combined run (all
three new classes + `TypeElevenDr14Dr15CostProbeTest` in one `:test` task, `mode=guarded`) reported
DR-14's staleness assertion **green** — the opposite of every isolated run since. Instrumenting
`forFile` with a `computed`/identity/`modificationStamp` trace showed why: `inner.lua`'s snapshot was
being *rebuilt* after the edit even though it had been judged pinnable, because an **earlier** test
class's teardown had already put a project-file edit in flight when `inner.lua`'s reentrant chain
reached back through `outer.lua` (itself unpinned) and recomputed everything fresh — a false green
for a reason that has nothing to do with the rule under test. Every DR-14/DR-15 verdict in this
section is therefore from a run of **that one test class alone**
(`test --tests net.internetisalie.lunar.type.TypeEleven<Class> --rerun --no-build-cache`), which is
also why the `REVIEW-COST TOTALS` figures below say `decisions=11 provisioned=11` rather than a
larger number carrying over other classes' fixtures.

#### Q14a — is the in-progress interleaving reachable?

**Yes, measured.** Fixture: two library files forming a genuine mutual-reference cycle seeded from a
project file — `outer.lua` (`OuterSeed = projectSeed` / `OuterGlobal = InnerSeed`) and `inner.lua`
(`InnerSeed = OuterSeed`). Resolving `OuterGlobal` starts `outer.lua`'s build; `outer.lua`'s second
statement nests into `inner.lua`'s build (a genuine cold `forFile`, not a re-entrant hit); `inner.lua`'s
own traversal then resolves `OuterSeed` and calls `forFile(outer.lua)` a second time — `outer.lua` is
still present in `LuaTypesVisitor.inProgressBuilds` (its own top-level build has not returned), so the
guard fires:

```
TYPE11-DR14 inProgress hit file=file:///…/luassert-…/outer.lua depth=5
```

`outer.lua` is the **outer** file — the one two frames further out on the stack, not the file whose
build directly re-entered itself. `design.md` §3.7's premise ("it is the same file's own in-flight
build, whose frame is the very frame currently open") is literally true of `outer.lua` from
`outer.lua`'s own point of view, but says nothing about `inner.lua`'s frame, which is what is actually
open when the hit fires — and `inner.lua`'s frame receives **no report at all** for this hit unless the
DR-14 candidate rule is added, because the early return happens before `CachedValuesManager.getCachedValue`
is even called (§3.7's own "returns before any of this" applies to itself here).

#### Q14b — does it ship a stale type?

**Yes, measured.** `TypeElevenDr14InProgressTest.testALibraryTransitivelyEmbeddingAProjectTypeThroughAReentrantCycleStillTracksIt`,
isolated run, `mode=guarded` (§3 with B1 and B4 already fixed, no DR-14 guard):

```
TypeElevenDr14InProgressTest > testALibraryTransitivelyEmbeddingAProjectTypeThroughAReentrantCycleStillTracksIt FAILED
    junit.framework.AssertionFailedError: editing the project file must be reflected in InnerSeed's
    type, even though InnerSeed was built while outer.lua's build was still in progress on the same
    thread expected:<[afterEdit]> but was:<[beforeEdit]>

2 tests completed, 1 failed
```

`inner.lua`'s own build is a **normal** cold `forFile(inner.lua)` call (nested inside `outer.lua`'s,
not itself a re-entrant hit), so it is recorded and cached exactly like any other library file:
`sources=1` (`outer.lua`, a provisioned file, reported by the normal `typeOfGlobalIn` site), `outside=[]`
→ pinned. What never reaches `inner.lua`'s frame is `outer.lua`'s own dependency on `p.lua`: that
dependency was reported into `outer.lua`'s frame **before** `inner.lua`'s frame was even pushed onto
the stack, so the "report into every open frame" rule (§3.1 step 3) cannot retroactively cover it, and
the re-entrant `forFile(outer.lua)` call `inner.lua` makes to read the now-complete value skips the
whole recording/replay path (§3.7's rule applied to itself). Same fixture, `mode=b14` (guarded plus
"an in-progress hit marks the outer frame unpinnable"):

```
BUILD SUCCESSFUL
TYPE11-REVIEW file=…/inner.lua provisioned=true pinnable=false sources=1 outside=[] …
    inProgressHits=[…/outer.lua] mode=b14
DR-14 after edit: InnerSeed = [afterEdit]
```

#### Q14c — the cost of the fix

```
REVIEW-COST TOTALS provisioned=11 cond=11 guarded=11 b14=11 dr15broad=11 dr15rescued=11
```

(Isolated `TypeElevenDr14Dr15CostProbeTest` run, `mode=b14`. ⚠ **That class is scaffold and was
deleted with it** — it reads `LuaReviewMode`, so it cannot compile against `main`. The figure is
therefore **not re-runnable from the committed tree**; reproducing it means re-creating the fourth-round
scaffold above. It is listed in the scaffold table for that reason. The shipped, re-runnable equivalent
is TC-15 / `TypeElevenPinnableCostTest`, which Phase 3 must build and which is the only thing that will
keep this number honest after the feature lands; the 10 bundled `lua-5.4` stdlib files plus
the 123 KiB `wx.lua` definition library, each built once in a clean epoch — the same shape and count
the third round used.) **`b14` costs zero of the 11 shipped files** — none of the bundled stdlib stubs
or the synthetic definition library reference *another* library file's global at all (`sources=0` for
10 of 11; `io.lua`'s one recorded source is itself, via `resolveType`, not a nested `forFile`), so none
of them can exercise the in-progress interleaving in the first place. **Recommended rule**: add a sixth
step to `isPinnable` — `if (frame.inProgressHits.isNotEmpty()) → not pinnable` — populated by reporting
into every open frame whenever `LuaTypesVisitor.inProgressSnapshot` answers non-null while
`LuaReviewRecorder.depth() > 0`, mirroring §3.7's `unreplayedWarm` treatment exactly (the in-progress
guard is, in effect, a *third* memoized door with no replay, alongside `resolveX`'s cache and `forFile`'s
warm-snapshot cache).

#### Q15a — reproducing the late-declaration-outranks-a-library defect

**Yes, real.** `TypeElevenDr15LateLibraryAnswerTest.testAProjectDeclarationWrittenAfterALibraryAnsweredStillOutranksIt`
— `TypeElevenDr11LateDeclarationTest`'s fixture with library `lib.lua` declaring the shared global
instead of nothing declaring it. Isolated run, `mode=guarded`:

```
TypeElevenDr15LateLibraryAnswerTest > testAProjectDeclarationWrittenAfterALibraryAnsweredStillOutranksIt FAILED
    junit.framework.AssertionFailedError: a project declaration written AFTER a library snapshot
    resolved via the all-scope fallback must out-rank that library's answer, exactly as it would for
    a fresh build expected:<[afterProject]> but was:<[beforeEdit]>
```

The roots-tracker-still assertion inside `rewriteAssertingRootsAreStill` passed in the same run — the
staleness is not an unearned green from a roots tick, exactly the base case's own guarantee. Same
fixture, `mode=dr15rescued` and separately `mode=dr15broad`: `BUILD SUCCESSFUL` under both.

#### Q15b — pricing the candidate rules

Three variants were computed simultaneously by the scaffold on every `doResolveGlobal` call:

- **`dr15broad`** — report `"global:$name"` whenever the project-scope pass alone returns null,
  regardless of whether the all-scope fallback then answers. This is the literal Q15b(i) rule and is
  the broadest: it also fires on the case §3.1 step 5 (B1) already covers (both scopes null).
- **`dr15rescued`** — the same trigger, but only counted when the all-scope fallback *does* then
  answer — i.e., excludes the case B1 already makes unpinnable, so it never double-reports.
- A third, cheaper alternative was **not** built as new machinery: `FileBasedIndex
  .getIndexModificationStamp(LuaGlobalAssignmentIndex.KEY, project)` is the same tracker DR-13 priced
  and rejected for B1 (`design.md` §9) — a global declaration is exactly a new entry in that index, so
  the same stamp would also close DR-15. It is not recommended here for the identical reasons DR-13
  gave (second invalidation axis, dumb-mode-hostile index query inside a validity check, undocumented
  monotonicity) **and** because the measured cost of the simple rule is already zero — there is nothing
  the stamp would buy.

```
REVIEW-COST TOTALS provisioned=11 cond=11 guarded=11 b14=11 dr15broad=11 dr15rescued=11
```

**Both `dr15broad` and `dr15rescued` cost zero of the 11 shipped files**, for the same structural
reason `b14` does: none of the bundled stdlib files or the synthetic definition library reference an
unbound global via `resolveGlobal` from inside their own build at all, so `doResolveGlobal` is never
even entered for any of the 11 files' own snapshot construction. On the adversarial fixture that *does*
exercise the rule (`alpha.lua` referencing `lib.lua`'s global with no project declaration yet), both
variants mark `alpha.lua` unpinnable identically — `dr15broad`'s extra trigger condition (both scopes
null) is redundant with the existing B1 absence rule wherever it fires, so it never removes a pin
`dr15rescued` would have kept. **Recommended rule: `dr15rescued`** — same measured cost as `dr15broad`
on every case tried, and it does not duplicate bookkeeping the absence rule already does.

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
| `TypeElevenDr05DumbModeTest.testASnapshotBuiltWhileDumbDoesNotSurviveIntoSmartMode` | (a) `!isDumb` term removed; (b) generation tracker replaced with `ModificationTracker.NEVER_CHANGED` | **GREEN under both.** Not a gate — see Gap 2.1. **Phase 4 explains why**: both dumb-mode edges fire `PROP_FILE_TYPES`, which invalidates all physical PSI, so the `psiFile` dependency alone rebuilds the snapshot and no mutation to the churn axis can show. |
| `TypeElevenDr04LatencyTest` (both arms) | — | **No assertions at all.** It is a printing probe, exactly like `CompNineDr20Test`, and is not claimed as a gate. |
| `…testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile` (2026-08-09, second round) | provisioned-context globals restricted to provisioned scope (DR-10) **+** blanket pin | **RED at a different assertion — `:88`, not `:93`.** `the library global must take the project declaration's members expected:<[beforeEdit]> but was:<[]>`. The move from `:93` to `:88` is the DR-10 scaffold's liveness proof: the restriction is what turns `[beforeEdit]` into `[]`. |
| `…testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile` (second round) | same | **RED at `:139`, message unchanged** — `the removed project method must disappear; got [afterEdit, beforeEdit]`. Identical to blanket pinning: this path never enters `typeOfGlobalIn`. |
| Blanket pin alone, re-run in the second round for DR-09 | blanket pin (provenance + `!isDumb`), restriction off | **RED at `:93` and `:139`** — reproduces the first round's signature exactly, which is what makes the `:88` above attributable to the restriction and not to the pin. |
| `TypeElevenDr11LateDeclarationTest.testADeclarationWrittenAfterTheLibrarySnapshotWasBuiltStillReachesIt` (2026-08-10, third round) | the §3 conditional rule **as written** — i.e. without §3.3 step 4 (no absence recording) | **RED at `:83`** — `a project declaration written AFTER the library snapshot was built must still reach it expected:<[afterDeclared]> but was:<[]>`, with `TYPE11-REVIEW file=alpha.lua pinnable=true sources=0` on the line above and `roots 3 -> 3` proving no tick healed or condemned it. |
| `TypeElevenDr12WarmInnerSnapshotTest.testALibraryWhoseInnerLibrarySnapshotWasServedWarmStillTracksTheProjectFile` (third round) | the §3 conditional rule as written — i.e. without §3.7 (no `forFile` replay) | **RED at `:75`** — `expected:<[afterEdit]> but was:<[beforeEdit]>`, with `file=a.lua pinnable=true sources=1 outside=[] warm=[b.lua]` naming the cause and `file=b.lua pinnable=false outside=[p.lua]` showing the inner file was judged correctly. |
| The same two, under the corrected rule (`mode=guarded`) | — | **GREEN, and for the right reason**: `file=alpha.lua pinnable=false misses=[global:sharedByProject]` and `file=a.lua pinnable=false sources=2 outside=[p.lua] warm=[b.lua] warmUnreplayed=[]`. In the same run 11 of 11 provisioned files are still pinned, so the green is not "pinning switched off". |
| `LuaTypeSourceRecorderCoverageTest` **as specified** (2026-08-11, fourth round) — qualified-chain matcher | none needed; run against `LuaTypeManagerImpl.kt` unmodified | **CANNOT FIRE.** `FileBasedIndex.getInstance().getContainingFiles` counts **0** against 2 real sites (ktlint wraps both), `PsiManager.getInstance(project).findFile` counts **1** against 2 (`:358` goes through a `psiManager` local). A recorded expectation of 0 that no new site can move. Step 9 blocker B3; design §1.9. |
| The replacement matcher — comments stripped, whitespace removed, bare members | inject one `PsiManager.getInstance(project).findFile(…)` | **RED** — `.findFile(` moves `2 → 3`. Baseline counts `.findFile(`=2, `.getElements(`=3, `.getContainingFiles(`=2. |
| The replacement matcher, formatting-only change | re-wrap an existing `StubIndex.getElements(` across lines | **GREEN, correctly** — stays `3`, where the chain literal falls `3 → 2` and would report a deletion that did not happen. |
| The replacement matcher, comment stripping | run it with comment stripping disabled | **NO CHANGE** — `2 / 3 / 2` either way. Stripping is prophylaxis against a future KDoc writing `.findFile(…)` in prose, not a measured requirement; §1.9 says so because an earlier draft claimed otherwise. |
| `TypeElevenDr14InProgressTest.testALibraryTransitivelyEmbeddingAProjectTypeThroughAReentrantCycleStillTracksIt` (2026-08-11, fourth round) | the §3 conditional rule **as accepted after B1+B4** — i.e. `mode=guarded`, no DR-14 guard | **RED** (isolated run) — `editing the project file must be reflected in InnerSeed's type, even though InnerSeed was built while outer.lua's build was still in progress on the same thread expected:<[afterEdit]> but was:<[beforeEdit]>`. `inner.lua`'s own build is a normal cold `forFile`, correctly pinned on `sources=1 outside=[]` — the missing dependency is `outer.lua`'s own `p.lua` source, reported into `outer.lua`'s frame before `inner.lua`'s frame existed. |
| The same, under `mode=b14` (guarded + "an in-progress hit marks the outer frame unpinnable") | — | **GREEN, and for the right reason** — `file=inner.lua pinnable=false … inProgressHits=[…outer.lua]`, `DR-14 after edit: InnerSeed = [afterEdit]`. |
| `TypeElevenDr14InProgressTest.testMutualReferenceCycleBetweenTwoLibraryFilesResolvesWithoutRecursing` (fourth round) | none needed — this is the Q14a reachability probe, not a guarded assertion | **Green on `main` and under every mode tried; not a gate.** Its purpose is the `TYPE11-DR14 inProgress hit file=…/outer.lua depth=5` trace it prints, which is the Q14a evidence. Listed here so a reader does not mistake it for an unproven guard. |
| `TypeElevenDr15LateLibraryAnswerTest.testAProjectDeclarationWrittenAfterALibraryAnsweredStillOutranksIt` (fourth round) | the §3 conditional rule **as accepted after B1+B4** — i.e. `mode=guarded`, no DR-15 guard | **RED** (isolated run) — `a project declaration written AFTER a library snapshot resolved via the all-scope fallback must out-rank that library's answer, exactly as it would for a fresh build expected:<[afterProject]> but was:<[beforeEdit]>`. |
| The same, under `mode=dr15rescued` | — | **GREEN.** |
| The same, under `mode=dr15broad` | — | **GREEN** (measured separately; ties `dr15rescued` on both pass/fail outcome and pinnable count on every fixture tried — see Q15b). |

### Phase 1 (2026-08-11) — the five DR-02 rows, re-earned against the production service

Every row above the `TypeElevenDr02ProvenanceTest` block mutated a **replica**: that class defines its
predicate inside its own file and matches by `VfsUtilCore.isAncestor`, where §3.2 specifies URL-prefix
containment, so no defect in the shipped `LuaLibraryProvenance` could turn any of them red. The rows
below are the same five facts re-run against the **production service** through
`LuaLibraryProvenanceTest`, plus a sixth the DR-02 set never covered. Each `Result` is pasted from the
JUnit XML of the run that produced it; the gate command is
`run "test --tests '*LuaLibraryProvenanceTest*' --rerun --no-build-cache"`, and the unmutated class was
**6 tests, 0 failures** when these six rows were produced (**9 tests, 0 failures** after the three
memoization/dependency rows below were added over two remediation rounds).

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `LuaLibraryProvenanceTest.testEveryFileResolveGlobalWouldVisitIsClassifiedByTheProductionService` (TC-5) | `definitionRoots` dropped from `LuaLibraryProvenance.computeRootUrls` | **RED** — `provenance must classify …/definitions/luassert-d3528bb6…/wx.lua as provisioned=true expected:<true> but was:<false>`. 3 of 6 failed: the copy and seeded-root cases fell with it, the sibling-prefix and project-file cases correctly stayed green. |
| `…testEveryBundledRuntimeStubFileIsProvisioned` | `runtimeRoot` dropped from `computeRootUrls` | **RED, alone (1 of 6)** — `the bundled stub jar:///…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua must be provisioned`. Replaces DR-02's fixture-level "switch the target to PANDOC": this mutates the service, not the fixture, and the `jar://` scheme in the observed message is the §1.3 cross-file-system fact re-measured. |
| `…testTheSeededLibraryIsProvisionedThroughTheRegisteredProvider` (TC-7) | enabled-library list cleared after install, roots ticked | **RED, alone (1 of 6)** — `the seeded library root itself must be provisioned`. ⚠ **Corrected 2026-08-11 (review of `1be7cc0d`, F3):** an earlier version of this row added "also the liveness proof for the memoized root list: it is only red if a roots tick actually invalidates the `CachedValue`". **That was unearned.** `setUp` ticks the roots before every method, so the value read in the body is always recomputed from current state and this red is fully explained by "the enabled list was empty when `computeRootUrls` ran" — no invalidation required. The mutation is also a *fixture* mutation, not a production one. The dependency set now has its own two rows below, and the claim is measured rather than asserted. |
| `…testACopyOfALibraryFileIsProvisionedOnlyThroughOriginalFile` (TC-6) | the `originalFile` hop dropped — `psiFile.virtualFile?.url` | **RED, alone (1 of 6)** — `a completion copy of a library file must be provisioned`. TC-6's own stated mutation, and the one DR-02 could not run because its replica took a `VirtualFile`. |
| `…testALightFixtureProjectFileIsNeverProvisioned` | root list widened with `"temp:///src"` | **RED (2 of 6)** — `a project file must not be provisioned`, and TC-5's negative half with it: `provenance must classify /src/projectGlobal.lua as provisioned=false expected:<false> but was:<true>`. |
| `…testASiblingRootSharingAPrefixIsNotProvisioned` | `url.startsWith(it)` — the `"$it/"` separator removed | **RED, alone (1 of 6)** — `a URL that merely extends a provisioned root's URL is a different root`. **Not a DR-02 row**: §3.2 names the separator as required rather than cosmetic, and nothing measured it until now. A definition cache directory is `<id>-<version>`, so prefix-sharing siblings are that tree's normal shape. |

### Phase 1 remediation (2026-08-11) — the review's two blocking defects, each with its own red

The Phase-1 review returned FAIL on Gate 7 with two defects, and both are the same shape as the ones
this ledger exists to catch: **a claim that was never executed**. The rows below are the executed
form. Gate command for every row: `run "test --rerun --no-build-cache --tests '<class>'"`; results
pasted from the JUnit XML of the run that produced them.

**F3 — the memoized dependency set (`LuaLibraryProvenance.kt:62-69`).** The review's charge was that
*no assertion in the class discriminates the real dependency set from `ModificationTracker
.NEVER_CHANGED`*. Measured, and the charge is **exactly right**: under the `NEVER_CHANGED` mutation the
six pre-existing methods stayed **green**, and only the new one fell. No new row depends on
`setUp`'s blanket tick — each populates the cache inside its own body with an answer it asserts first.

⚠ **Corrected 2026-08-11 (second remediation round).** The first version of the top row applied a
**conjunction** — "both dependencies replaced by `NEVER_CHANGED`" — which cannot attribute the red to
either member, the same over-claim as the row above it that this ledger had just replaced. Measured,
the over-claim was hiding a real hole: **deleting `LuaLibraryProvenance.kt:67` (the
`targetModificationTracker` dependency) alone left all eight methods green**, because no test in the
tree ticked the target at all (`grep -c setTarget` over `src/test/kotlin/.../type/` returned `0`),
while `computeRootUrls` reads `state.getTarget()` for the runtime root. The conjunction is now split
into two single-member mutations, each red on its own assertion and green on the other's, and a ninth
method exists to carry the target half. Unmutated: **9 tests, 0 failures**.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `LuaLibraryProvenanceTest.testTheMemoizedRootListIsRecomputedAfterARootsTick` | the `ProjectRootModificationTracker` dependency dropped **alone** (`LuaLibraryProvenance.kt:66`) | **RED, alone (1 of 9)** — `a disabled library is no longer a root, so the memoized list must have been recomputed`. The other eight stayed green, including the target-tick case, so the red is attributable to the roots dependency and nothing else. |
| `…testTheMemoizedRootListIsRecomputedAfterATargetTick` (added this round) | the `targetModificationTracker` dependency dropped **alone** (`LuaLibraryProvenance.kt:67`) | **RED, alone (1 of 9)** — `the previous target's runtime root is no longer a root, so the memoized list must have been recomputed on the target tick alone`. The other eight stayed green. Ticks the target with `state.setTarget` — **not** `setTargetAndNotify`, which reaches `makeRootsChange` and would invalidate through the *roots* tracker regardless, leaving this mutation unable to fire — and asserts the roots tracker **still** across the switch, which is what makes the red attributable. |
| `…testTheRootListIsNotRecomputedWhileNothingTicks` | the `CachedValuesManager` wrapper dropped (`rootUrls() = computeRootUrls()`) | **RED, alone (1 of 8 at the time; the method is unchanged)** — `with neither dependency ticked the root list must be served from the cache, unrecomputed`. Asserts both trackers still across the settings write, so a stray tick fails loudly instead of quietly deciding the outcome. |

**F1 — the two conservative markers returned instead of marking** (`LuaTypeSourceRecorder.kt:108`,
`:126` as shipped). §3.1 step 4's null-no-op grant is `reportFile`'s alone; for these two the
direction is inverted, because their job is to make the frame **non-empty** so §3.3 steps 5/6 reject
the pin — a `return` leaves a clean frame, and a clean frame on a provisioned file **is pinned**.
Fixed with a sentinel (§2.1 `UNIDENTIFIED_*`), and the design gap that permitted the reading is closed
in §3.1 step 4. The defect shipped because the object was untested; `LuaTypeSourceRecorderTest` now
covers 11 of its 12 members (`snapshotFrames` is exercised as a collaborator, not asserted on its own).
Unmutated: **12 tests, 0 failures**.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `LuaTypeSourceRecorderTest.testAWarmSnapshotWithNoUrlStillMarksTheFrame` | `?: UNIDENTIFIED_WARM` → `?: return` — **the code as shipped in `1be7cc0d`** | **RED (2 of 12, with the in-progress twin)** — `a file that cannot be identified is more unknown, not less — the frame must not be clean`. |
| `…testAnInProgressHitWithNoUrlStillMarksTheFrame` | `?: UNIDENTIFIED_IN_PROGRESS` → `?: return` — as shipped | **RED** — `an unidentifiable in-flight build must still cost the outer file its pin`. |
| `…testReportFileWithNoUrlRecordsNothing` (the *other* half of the asymmetry) | `reportFile` given the same sentinel treatment | **RED** — `a source that cannot be named is simply not a recorded source`. Pins the asymmetry so it cannot be "tidied" into symmetry: a sentinel in `urls` would be handed to `isProvisionedUrl` and cost every such file its pin. |
| `…testReportReachesEveryOpenFrameNotOnlyTheInnermost` (§3.1 step 3) | `report`: `openFrames.get().forEach` → `.last()` | **RED** — `the enclosing frame must record it too expected:<[file:///lib/a.lua]> but was:<[]>`. |
| `…testReportingOutsideAnyBuildIsANoOp` | the same `.last()` | **RED** — `java.util.NoSuchElementException: ArrayDeque is empty.` |
| `…testDepthCountsEveryOpenFrameAndIsZeroOutsideABuild` | `depth()` → `if (openFrames.get().isEmpty()) 0 else 1` | **RED** — `a nested build must be distinguishable from a top-level one expected:<2> but was:<1>`. |
| `…testEachConservativeMarkWritesItsOwnSet` | `reportRescuedGlobal` writes `absences` | **RED** — `expected:<[global:wx]> but was:<[global:wx, global:rescued]>`. |
| `…testAWarmSnapshotWithAStoredFrameIsReplayedRatherThanMarkedUnreplayable` (§3.7 step 4) | the `storedFrame != null` branch deleted | **RED** — `the stored frame's sources must propagate expected:<[file:///lib/a.lua]> but was:<[]>`. |
| `…testAbsorbUnionsAllFiveSets` | `absorb` drops `absences.addAll` | **RED (3 of 12)** — `expected:<[global:wx]> but was:<[]>`; `testReplayPropagates…` (same message) and `testAWarmSnapshot…Replayed` (`and so must its absences expected:<[global:wx]> but was:<[]>`) fell with it, both correctly, since both read `absorb`. |
| `…testReplayPropagatesAllFiveSetsIntoEveryOpenFrame` (§3.1 step 6) | `replay`: `forEach` → `.last()` | **RED** — `expected:<[file:///lib/a.lua]> but was:<[]>`, i.e. the outer of two open frames absorbed nothing. |
| `…testAWarmSnapshotWithNoStoredFrameMarksTheFileUnreplayable` | the not-found marking deleted | **RED** — `an unreplayable warm hit is recorded by URL expected:<[temp:///src/library.lua]> but was:<[]>`. The F1 case fell with it in the same run, correctly: the sentinel it asserts is written by the very line this mutation deletes. |
| `…testRecordingPopsItsFrameWhenTheBodyThrows` (§3.1 step 2) | the `finally` in `recording` replaced by a success-path pop | **RED (7 of 12)** — `a throwing build must leave no frame open expected:<0> but was:<1>`, plus six collateral `a previous test leaked an open frame onto this thread`. The collateral **is** the finding: one un-popped frame on a pooled thread silently poisons every later build, which is why the class asserts `depth() == 0` in `setUp` and `tearDown`. |

The **three coverage-matcher rows** — "as specified", "inject one `findFile`", and "re-wrap an existing
`getElements(`" — were produced by running the matcher logic over the real `LuaTypeManagerImpl.kt`
(553 lines, `main` @ `75707e78`), not by reading it — but by a **standalone replica** of the intended
Kotlin, because Phase 3 has not been implemented. They establish the counts and that the matcher moves
in both directions; they are not a substitute for the shipped assertion, which still owes this ledger
its own row.

**Several stated mutations are pending rather than observed**, and are listed separately for that
reason. A row here is a promise, not evidence; Phase 1/3 must run each and move it into the table
above. An earlier draft of this paragraph said "one", which under-counted the unearned rows — the
same over-claim this ledger exists to prevent:

| Assertion (not yet written) | Mutation to apply | Expected — **to be confirmed in Phase 3** |
| :-- | :-- | :-- |
| `TypeElevenDumbModeDecisionTest` — `isPinnable(delta.lua, SourceFrame()) == false` under `DumbModeTestUtils.runInDumbModeSynchronously` | delete §3.3 step 1 (`DumbService.isDumb`) | **RED expected.** An empty frame on a provisioned file clears steps 2–7, so step 1 is the sole rejector. This is the gate Gap 2.1 said did not exist (Step 9 blocker B5, design §1.9). Phase 3 must run it and move this row into the table above; a row that stays here is a guard that has not been shown red. |
| `TypeElevenPinSurvivesUnrelatedEditTest` — TC-1, snapshot instance identity across an unrelated project edit | revert §3.3 step 9 to always pick `MODIFICATION_COUNT` | **RED expected.** ⚠ The claim that it is **red on `main` today** is also unobserved — no committed fixture measures snapshot instance identity across an edit (`TypeElevenPath2ProbeTest` prints graph types and re-obtains the `PsiFile` each read, which is the very variable TC-1 depends on). Phase 3 must confirm both directions. |
| `TypeElevenGenerationSignalTest` (a) — TC-2a | `generationTracker()` → `ModificationTracker.NEVER_CHANGED` | **RED expected.** |
| `TypeElevenGenerationSignalTest` (c) — TC-3 | drop `targetTracker` from the **pinnable** branch of §3.3 step 9 only | **RED expected.** |
| `TypeElevenPinnableCostTest` — TC-15, all 11 pinnable | any rule that pins nothing | **Expected `11`.** The `guarded=11` figure it replaces came from deleted scaffold and is not re-runnable from the tree. |
| `TypeElevenGenerationSignalTest` (e) — TC-2c, the wiring | `generationTracker()` → `NEVER_CHANGED`, **or** the churn object omitted from `Result.create` | **RED expected on `A !== B`.** The second mutation is the one no other case catches. |
| `TypeElevenDr18ModuleAbsenceTest` — TC-20 | §3 without §3.1 step 5d | **RED expected** — the frame is empty, the file is pinned, and `require("mymod")` stays `ANY` after the module appears (§1.12). |
| `LuaTypeSourceRecorderCoverageTest` — TC-17, two files, five members | inject one `findFile(` call site | **RED expected.** The counts are measured; the shipped assertion is not yet written. |

### Phase 2 (2026-08-11) — the doors record what they consume

Phase 2's task list carried **no** test, on the goal statement's reasoning that the phase is inert.
Inert means *no behaviour moves*, not *nothing is claimable*: a frame can be opened here exactly as
`forFile` will open one in Phase 3, so every §3.1/§3.5/§3.6 claim about what the type manager reports
is assertable now. Every guard in this feature that shipped unasserted was later found unable to
fail — four in Phase 1 alone — so the exit criterion was changed rather than the reasoning accepted.

Gate command for every row: `run "test --tests '*LuaTypeManagerRecordingTest*' --rerun --no-build-cache"`
(and `'*LuaTypeSourceRecorderTest*'` for the last row); the unmutated classes were **6 tests, 0
failures** and **12 tests, 0 failures**. Results pasted from the JUnit XML of the run that produced
them.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `LuaTypeManagerRecordingTest.testResolvingAGlobalRecordsTheFileItWasReadFrom` (§3.5, `typeOfGlobalIn` row) | `.onEach { LuaTypeSourceRecorder.reportFile(it) }` deleted from `typeOfGlobalIn` | **RED, alone (1 of 6)** — `the declaring file must be a recorded source, but the frame holds []` |
| `…testAGlobalNothingDeclaresIsRecordedAsAnAbsence` (§3.1 step 5 / §1.8 B1) | the `.also { if (it == null) reportAbsence("global:$name") }` deleted from `resolveGlobal`'s `recordInto` body | **RED (2 of 6)** — `expected:<[global:nothingDeclaresThisName]> but was:<[]>`, and `testAMemoizedAbsenceReplaysAsAnAbsence` with it, correctly: an absence never recorded cannot be replayed. |
| `…testAGlobalOnlyALibraryAnswersIsRecordedAsRescued` (§3.1 step 5b / §1.10 V2) | the `?.also { reportRescuedGlobal("global:$name") }` deleted from `doResolveGlobal`'s all-scope fallback | **RED, alone (1 of 6)** — `expected:<[global:sharedByLibrary]> but was:<[]>`. The companion assertion (`absences` stays empty) is what makes this the V2 shape and not B1. |
| `…testAModuleThatResolvesToNothingIsRecordedAsAnAbsence` (§3.1 step 5d / §1.12) | the `.also { reportAbsence("module:$moduleName") }` deleted from `doResolveModule`'s `ANY` fall-through | **RED, alone (1 of 6)** — `expected:<[module:nosuchmodulename]> but was:<[]>`. The test also asserts the answer **is** `LuaPrimitiveType.ANY`, which is why the absence is needed at all. |
| `…testAMemoizedTypeReplaysItsSourcesIntoALaterBuild` (§3.6 step 4) | `LuaTypeSourceRecorder.replay(it.sourceFrame)` deleted from `resolveType`'s cache-hit branch | **RED, alone (1 of 6)** — `a cache hit must replay the sources of the answer it serves, but the frame holds []`. It failed *after* its `assertSame`, so the second call demonstrably was the memoized answer. |
| `…testAMemoizedAbsenceReplaysAsAnAbsence` (§3.6, "the stored value is the whole frame") | `LuaTypeSourceRecorder.replay(it.sourceFrame)` deleted from `resolveGlobal`'s cache-hit branch | **RED, alone (1 of 6)** — `expected:<[global:nothingDeclaresThisEither]> but was:<[]>`. This red is also the proof that the second call was a **hit**: a recompute would have re-recorded the absence and stayed green. |
| `LuaTypeSourceRecorderTest.testReportFileWithNoUrlMarksTheSourceUnidentified` (DR-19) | `reportFile`'s `?: return` restored — i.e. the shipped Phase 1 behaviour | **RED, alone (1 of 12)** — `a source that cannot be named is still a source, and one no provisioned root claims expected:<[unidentified:consumed-file]> but was:<[]>` |

#### DR-18 — pricing the module-absence rule

```
DR-18 file=io.lua        urls=1 outside=[] moduleAbsences=[] otherAbsences=[] warm=0 inProgress=0 rescued=0
DR-18 file=os.lua        urls=0 outside=[] moduleAbsences=[] otherAbsences=[] warm=0 inProgress=0 rescued=0
   (…math, utf8, debug, table, string, builtin, package, coroutine, wx — all urls=0, every set empty)
DR-18 REVIEW-COST TOTALS provisioned=11 withModuleRule=11 withoutModuleRule=11
```

**Zero of the 11 shipped files lose their pin.** Method, and it differs from the third and fourth
rounds' in a way worth keeping: both columns come from **one** run rather than a source edit between
runs, because absence keys carry the `"global:" / "module:"` prefix — the "without the rule" verdict
is the same frame judged with `module:`-prefixed absences filtered out, so no mode string and no
second build. The probe (`TypeElevenDr18ModuleAbsenceCostProbeTest`) enumerated the runtime root's 10
bundled `lua-5.4` stubs plus the seeded definition library, opened a frame around
`LuaTypesSnapshot.forFile` for each, and replicated §3.3 steps 2–7 locally because `isPinnable` does
not exist until Phase 3. Run **isolated** (`--tests '*TypeElevenDr18*'`), per the fourth round's
same-JVM contamination finding. **Scaffold: deleted after the measurement**, exactly as the third and
fourth rounds' scaffolds were.

⚠ Corroboration worth more than the number itself: `provisioned=11`, `io.lua urls=1`, all ten others
`urls=0` reproduces the fourth round's `REVIEW-COST TOTALS provisioned=11 cond=11 …` and its stated
detail ("`sources=0` for 10 of 11; `io.lua`'s one recorded source is itself, via `resolveType`")
**exactly** — on the shipped recorder rather than on `LuaReviewScaffold`. The fourth round's figures
were explicitly not re-runnable from the committed tree; they now are, in substance.

#### DR-19 — running the premise, and dropping the exemption anyway

Instrumentation: a counter on every `reportFile` call and on every null URL, plus the file's class,
name and `isPhysical`, dumped by a shutdown hook per test-worker JVM. Run under
`test -PwithCorpus --rerun --no-build-cache`, so the denominator is the full suite **plus** all three
corpus sweeps over the pinned third-party trees. Reverted before commit.

```
TYPE11-DR19 calls=47331 nulls=1 kinds=[LuaFile|unidentified.lua|physical=false]
```

**47 331 calls, one null — and that one is `LuaTypeSourceRecorderTest.fileWithNoUrl`'s own
non-physical `createFileFromText` fixture.** Zero from a real §3.5 call site, which is what the
premise claims.

The exemption was dropped anyway, and the reasoning is the part worth recording. The measurement
prices the drop at **zero**: if no real site ever produces a null, no sentinel is ever written, so
nothing loses a pin. What changes is the *failure mode* if a future site does produce one —
unconditional, that file loses its pin, which is exactly today's behaviour; exempt, it takes a
**wrong** pin, and a wrong pin is a stale type the user sees, with no second chance to revisit it
(§1.12). Trading a premise for nothing is a trade worth making. `reportFile` now records
`UNIDENTIFIED_CONSUMED` into `urls`, which §3.3 step 3 hands to `isProvisionedUrl` and which answers
`false` — reversing §2.1's earlier claim that no sentinel would ever reach the provenance predicate,
deliberately and with that claim corrected in place.

For completeness, the two full-suite runs the ledger is anchored to:

```
blanket pin (rejected):   2563 tests completed, 2 failed, 1 skipped   BUILD FAILED     in 9m 48s
conditional rule (§3):    2564 tests, 0 failures, 1 skipped           BUILD SUCCESSFUL in 9m 45s
```

⚠ **Line numbers in the rows above are as-of their measurement commit, not as-of HEAD.** The
`TypeElevenDr01ResidualTest.kt:88 / :93 / :139` citations were correct at `07a8fa44` / `2e06bc86`; the
file was refactored in `8dd4aeab` (the `rewrite` helper moved to the base case) and those assertions
now live at `:69`, `:74-78`, `:110` and `:119-120`. The pasted output remains valid evidence — it is
dated — but do not expect the line numbers to resolve against the current fixture.

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

### Phase 3 (2026-08-11) — the condition goes live, and three outcome gates stop attributing

Gate command for every row: `run "test --rerun --no-build-cache --tests '<class>'"`, one mutation per
run, restored between runs. The unmutated TYPE-11 package was **72 tests, 0 failures**.

**Three findings came out of this exercise, and all three are the same shape as §1.9 B3/B5: a guard
whose *outcome* test cannot go red.** They are listed after the ledger.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `TypeElevenPinSurvivesUnrelatedEditTest` (TC-1, TYPE-11-01) | §3.3 step 9 → always `MODIFICATION_COUNT` | **RED** — with `TypeElevenGenerationSignalTest`'s TC-2a and TC-2c in the same run (3 of 7) |
| `TypeElevenGenerationSignalTest.testAPinnedLibraryFileDependsOnTheProjectRootModificationTracker` (TC-2a) | `generationTracker()` → `ModificationTracker.NEVER_CHANGED` | **RED, alone (1 of 5)** |
| `…testTheProductionEnablePathTicksTheGenerationTracker` (TC-2b, Gap 2.3) | `notifyDefinitionRootsChanged()` deleted from `LuaProjectSettings.setEnabledDefinitionLibrariesAndNotify` | **RED, alone (1 of 6)** |
| `…testAPinnedSnapshotIsWiredToAllThreeOfItsDependencies` (TC-2d, **new**) | the churn object dropped from `dependenciesFor`'s pinnable branch | **RED, alone (1 of 7)** — and TC-1 + TC-2c stayed **green**, which is the finding below |
| `…testAPinnedSnapshotIsWiredToAllThreeOfItsDependencies` (TC-2d) | `targetModificationTracker` dropped from the pinnable branch | **RED (2 of 6)**, with TC-3 |
| `…testATargetSwitchInvalidatesAPinnedDefinitionLibrarySnapshot` (TC-3) | as above | **RED (2 of 6)** |
| `TypeElevenDumbModeDecisionTest.testTheDecisionIsNoWhileIndexing` (TC-16a, TYPE-11-05) | §3.3 step 1 (`DumbService.isDumb`) deleted | **RED, alone (1 of 3)** |
| `…testTheSameShapeOfFileBuiltSmartRecordsItsSource` (TC-16c) | `.onEach { reportFile(it) }` deleted from `typeOfGlobalIn` | **RED, alone (1 of 3)** |
| `TypeElevenPinnableCostTest` (TC-15) | `isPinnable` → `false` (a rule that pins nothing) | **RED, alone (1 of 1)** — and green under the step-9 mutation, which is the point: it measures the decision, not the wiring |
| `TypeElevenDr11LateDeclarationTest` (TC-13) | §3.3 step 4 (`absences`) deleted | **RED, alone (1 of 1)** — re-earned against the shipped build, not the scaffold |
| `TypeElevenDr15LateLibraryAnswerTest` (TC-19) | §3.3 step 7 (`rescuedGlobals`) deleted | **RED, alone (1 of 3)** — re-earned against the shipped build |
| `TypeElevenIncompleteFrameDecisionTest.testARecordedAbsenceDeniesThePin` (**new**) | §3.3 step 4 deleted | **RED, alone (1 of 7)** |
| `…testAWarmHitThatCouldNotBeReplayedDeniesThePin` (**new**) | §3.3 step 5 deleted | **RED, alone (1 of 7)** |
| `…testAnInProgressHitDeniesThePin` (**new**) | §3.3 step 6 deleted | **RED, alone (1 of 7)** |
| `…testAGlobalRescuedByTheAllScopeFallbackDeniesThePin` (**new**) | §3.3 step 7 deleted | **RED, alone (1 of 7)** |
| `…testAWarmNestedSnapshotReplaysItsFrameIntoTheBuildThatAskedForIt` (**new**) | `reportWarmSnapshot` call deleted from `forFile` (§3.7 steps 2–4) | **RED, alone (1 of 7)** |
| `…testAMutualLibraryCycleRecordsAnInProgressHit` (**new**) | `reportInProgressHit` call deleted from `forFile` (§3.1 step 5c) | **RED, alone (1 of 7)** |
| `LuaTypeSourceRecorderCoverageTest.every cross-file door…` (TC-17) | one `PsiManager.getInstance(project).findFile(…)` injected into `LuaTypeManagerImpl` | **RED, alone (1 of 3)** — `.findFile(` `2 → 3` |
| `LuaTypeSourceRecorderCoverageTest` (TC-17, false-positive check) | an existing `StubIndex.getElements(` re-wrapped across three lines | **GREEN** — formatting alone does not move the count, which is what the qualified-chain matcher could not manage |
| `…only one path stores an answer beside its frame` (**new**, DR-21) | a second `CachedAnswer(…)` construction added to `LuaTypeManagerImpl` | **RED, alone (1 of 3)** |
| `LuaTypeManagerRecordingTest.testAMemoizedAbsenceReplaysAsAnAbsence` (M6, **DR-22**) | a PSI tick (`addFileToProject`) inserted between the cold and warm calls | **RED, alone (1 of 7)** — the new `PsiModificationTracker` assertion is what fires, so the hit now has in-test evidence |
| `…testAModuleThatResolvesToNothingIsRecordedAsAnAbsence` (§3.1 step 5d) | the `.also { reportAbsence("module:…") }` deleted from `doResolveModule`'s `ANY` fall-through | **RED, alone (1 of 7)** |
| `…testResolvingAModuleRecordsTheFileItWasReadFrom` (**new**, §3.5 `getModuleType` row) | `reportFile(psiFile)` deleted from `getModuleType` | **RED, alone (1 of 7)** |

#### Finding 1 — TC-2c cannot catch the omission §1.11 built it for

§1.11 states, twice and in bold, that TC-2c is *"the only case that asserts `forFile` actually passes
the churn object into `Result.create`"*. Run against a build whose pinnable branch does exactly that
omission, **TC-2c passed** — as did TC-1, TC-2a, TC-3, TC-15 and every residual fixture:

```
mutation: pinnable branch emits Result.create(builtTypes, psiFile, targetTracker)
run "test --tests '*TypeElevenGenerationSignalTest' --tests '*TypeElevenPinSurvivesUnrelatedEditTest'"
BUILD SUCCESSFUL in 33s
```

Cause, probed rather than reasoned — the same `PsiFile` instance's `modificationStamp` moves across a
roots change, and `forFile` depends on `psiFile` in **both** branches:

```
PROBE before: psi=283239940 stamp=0 valid=true  snap=1237688767
PROBE after:  psi=283239940 stamp=1 valid=true  samePsi=true snap=914285686 sameSnap=false
```

§1.11 eliminated `MODIFICATION_COUNT` as the confound and left `psiFile` — which is the mechanism
§1.6 had **already recorded** for the dumb-mode exit ("the library `PsiFile`'s own
`modificationStamp` moves 0 -> 1"). No behavioural fixture can separate the two, because every route
to a `ProjectRootModificationTracker` tick runs through `makeRootsChange`, which is what moves the
stamp.

**Fix, in §1.9 B5's idiom**: the dependency set is now one named, assertable value —
`internal fun dependenciesFor(psiFile, frame): Array<Any>`, which `forFile` spreads. TC-2d asserts
all three members by identity and is **red** under the mutation TC-2c cannot see. TC-2c is kept as
the behavioural statement of the requirement.

##### Correction (Phase 3 review, F2) — the spread is a convention, and TC-2e is what enforces it

The paragraph above used to end "…and `forFile` spreads it, so 'omitted from `Result.create`' and
'omitted from `dependenciesFor`' are the same edit." **That is not an invariant.** Inlining the
arguments at the call site and leaving `dependenciesFor` untouched compiles and is green under TC-1,
TC-2c, TC-2d and TC-3 — the helper is the single source by convention, enforced by review, not by
the type system.

The review recorded this as uncatchable, on the ground that a test cannot observe a `CachedValue`'s
dependency list. **Measured, it can** — through public API, and without a seam in production code:

```
PROBE stored=com.intellij.psi.impl.PsiParameterizedCachedValue$Soft
PROBE provider=com.intellij.psi.util.CachedValuesManager$NonPhysicalPsiHandlerProvider
PROBE deps=[net.internetisalie.lunar.lang.psi.LuaFile,
            com.intellij.openapi.roots.ProjectRootModificationTrackerImpl,
            com.intellij.openapi.util.SimpleModificationTracker]
PROBE hasRoots=true hasTarget=true hasPsi=true size=3
```

`getCachedValue`'s `PsiElement` overload stores a `ParameterizedCachedValue` under
`LuaTypesVisitor.KEY`, and `ParameterizedCachedValue.getValueProvider()` is public, so a test can
recompute through the production lambda and read the `Result`'s `dependencyItems`. Two details make
it work: the key must be read as `Key<Any>`, because `ParameterizedCachedValue` does **not**
implement `CachedValue` and the checkcast the compiler inserts for `getUserData(KEY)` would fail on
the platform's own heap-polluting store; and the recompute is harmless, since it only rebuilds a
snapshot and re-registers its frame.

That is now **TC-2e**
(`TypeElevenGenerationSignalTest.testTheProviderHandsEveryDependencyToTheResultItCreates`), measured
**RED, alone (1 of 9)** under the inlining, with TC-2d green in the same run. Residual fragility, and
it is real: the case is coupled to two platform implementation choices (which `CachedValue`
implementation the overload stores, and that its provider stays reachable). Both fail *loudly* —
`checkNotNull` or a `ClassCastException`, never a silent pass — so the failure mode is a maintenance
red on a platform bump, not a false green.

#### Finding 2 — step 7 subsumes §3.3 steps 5 and 6 on every fixture that can exist

Re-earning the four TYPE-11-06 channels against the **shipped** build (the scaffold measured them one
guard at a time, in a build that had only the guard under test):

| mutation | DR-11 | DR-12 | DR-14 | DR-15 |
| :-- | :-- | :-- | :-- | :-- |
| step 4 (`absences`) deleted | **RED** | — | — | — |
| §3.7 warm reporting deleted | — | *green* | — | — |
| step 6 (`inProgressHits`) deleted | — | — | *green* | — |
| step 7 (`rescuedGlobals`) deleted | — | — | *green* | **RED** |

One cause for all three greens: a library global that only another library declares resolves through
`doResolveGlobal`'s all-scope fallback, so **every cross-library reference is a rescued global** —
and DR-12's and DR-14's fixtures are built entirely out of cross-library references. Two sufficient
rejectors for one outcome attribute redness to neither, which is the shape Phase 1's review rejected
in `de60eb83`. No fixture can separate them: an in-progress hit needs a library→library cycle (⇒
rescued), and routing the cycle through a project file puts an unprovisioned URL in `urls` (⇒ step
3).

##### Checked (Phase 3 review, F4) — the attribution above survives, and the proposed second rejector does not

The review read the DR-14 green as misattributed, on the ground that step 4 is a sufficient rejector
there too: "`resolveGlobal` records `absences=[global:OuterSeed]` off the in-progress snapshot".
**Refuted by inspecting the frame** rather than the mutation's colour — the re-entrant resolution
*answers*, through the all-scope fallback, so what it leaves is a rescued global and `absences` is
empty:

```
DR-14 inner.lua frame: urls=[…/luassert-…/outer.lua] absences=[]
                       inProgressHits=[…/luassert-…/outer.lua]
                       rescuedGlobals=[global:OuterSeed] unreplayedWarm=[]
```

So the table stands as written: step 7 is the sole cause of DR-14's step-6 green, and steps 6 and 7
are the only two guards that overlap on this fixture. The composition is now asserted rather than
argued —
`TypeElevenDr14InProgressTest.testTheInProgressFixtureIsRejectedByTheRescuedGlobalAndNotByAnAbsence`
pins all three facts, the empty `absences` being the load-bearing one.

**This is not a production defect** — the guards are correct and each is measured at zero lost pins.
It is a gap in what the plan claimed would catch one. Closed by `TypeElevenIncompleteFrameDecisionTest`:
the decision on a frame with exactly one non-empty set (four rows above, one red each), plus the two
*mechanism* assertions that stop those from being claims about states nothing reaches — a warm nested
hit really does replay, and a real mutual cycle really does mark `inProgressHits`.

#### Finding 3 — the module door's end-to-end cases do not attribute either, for the same reason

`TypeElevenDr18ModuleAbsenceTest` (TC-20) stayed **green** with §3.1 step 5d deleted, and
`TypeElevenDr01ResidualTest`'s new library-`require`s-a-project-module case stayed **green** with
`getModuleType`'s `reportFile` deleted. Probed frame for `mu.lua` = `muAlias = require("mymod")`:

```
PROBE-MOD frame urls=[package.lua] absences=[module:mymod] warm=[] inProgress=[] rescued=[global:require] pinnable=false
```

`require` is itself a global that only a library file declares, so **any** library file containing a
`require` is rescued-global-unpinnable before the module rules are consulted. Both module guards keep
their place (a pin must be correct when it is taken), and both now have an attributable gate in
`LuaTypeManagerRecordingTest` — the absence one already existed, the `getModuleType` one is new. The
two end-to-end cases stay as user-visible correctness checks with the non-attribution written into
their KDoc.

#### Finding 4 — `forFile`'s warm signal was a flag the platform cannot set (Phase 3 review, F1)

Shipped in `7c1f1862`, `forFile` opened with `var providerRan = false`, assigned it inside the
`getCachedValue` lambda, and reported to §3.7 only when it was still `false`. Design §3.7 step 2
described that as "a *conservative* warm signal, not an exact one". It is **neither**:

```java
// CachedValuesManager.java:216-224 (platform 261 sources)
ParameterizedCachedValue<T, PsiElement> value = (ParameterizedCachedValue<T, PsiElement>)context.getUserData((Key)key);
if (value != null) {
  return value.getValue(context);          // ← the provider argument is never looked at
}
```

The overload returns the stored value's `getValue` before touching the provider it was handed, so
every call after the first discards its own lambda. A **recompute** therefore runs the *first* call's
provider and sets the *first* call's flag — a dead local of a returned stack frame — leaving the
current call's flag `false`. The flag was `true` on exactly one call per file per session and `false`
on every other call, hit and recompute alike: the warm branch, unconditionally.

Run, not read — `TypeElevenWarmSignalMechanismTest.testAProviderPassedToALaterCallIsNeverTheOneThatRuns`
memoizes through the same overload with a `SimpleModificationTracker` dependency, ticks it, and calls
again with a fresh lambda: the value is recomputed (`computations` rises) and the second lambda's flag
is still `false`.

**Benign in the shipped build**, which is why the suite was green: a fresh build has just written its
own frame, so the warm branch it always took found that frame and replayed it idempotently. The cost
was a misdescribed mechanism plus one latent way to lose a pin for nothing — an evicted weak
`snapshotFrames` entry on a recompute would have marked `unreplayedWarm`.

**Fix**: delete the flag; call `reportWarmSnapshot` whenever `depth() > 0`. Behaviour-preserving in
all three reachable states, because every reporter writes to *every* open frame, so the replay of a
frame built under the current outer frame adds nothing. Measured rather than argued —
`…testAColdNestedBuildLeavesTheOuterFrameExactlyTheUnion` drives a **cold** nested build and compares
all five sets of the outer frame with the registered inner frame: `unreplayedWarm` empty, `urls`,
`absences`, `inProgressHits` and `rescuedGlobals` equal, with the project URL present so the
equalities are not over empty sets.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `TypeElevenGenerationSignalTest.testTheProviderHandsEveryDependencyToTheResultItCreates` (TC-2e, **new**) | `Result.create(builtTypes, psiFile, targetTracker)` inlined into the provider, `dependenciesFor` intact | **RED, alone (1 of 9)** — TC-2a, TC-2c, TC-2d, TC-3, TC-4 all green, which is Finding 1's correction |
| `TypeElevenWarmSignalMechanismTest.testAColdNestedBuildLeavesTheOuterFrameExactlyTheUnion` (**new**) | `snapshotFrames[builtTypes] = sourceFrame` deleted from `forFile` (§3.7 step 1) | **RED, alone (1 of 2)** — the unconditional report then finds no frame and marks `unreplayedWarm` |

### Phase 4 (2026-08-12, `main` @ `bf715eb2`) — DR-06 and DR-07, the two negative results closed

Same discipline as every earlier round: the measurement needed one production edit, and **that edit
was reverted — `git diff -- src/main/` is empty at this commit.**

| Scaffold file / edit | Purpose | State |
| :-- | :-- | :-- |
| `LuaTypeReference.kt` — `val resolved: LuaType by lazy { … }` → `val resolved: LuaType get() = …` | DR-07's attribution mutation: route every access through `resolveType` so the `typeCache` hit replays the frame | **reverted to HEAD** |
| `src/test/kotlin/.../type/TypeElevenDr06StampProbeTest.kt` (new) | DR-06's three cases | **kept** — they lock the platform premise the answer rests on |
| `src/test/kotlin/.../type/TypeElevenDr07LazyReferenceProbeTest.kt` (new) | DR-07's four arms | **kept, printing only** — it reproduces BUG-434 and must not assert today's behaviour |

**No production behaviour changed in Phase 4, so the corpus sweep was not re-run.** `-PwithCorpus`
gates `LuaCorpusSweepTest` / `LuaTortureCorpusTest` / `LuaInspectionParityTest`, none of which can
observe a docs-plus-tests change; the routine loop with `--rerun --no-build-cache` is the whole gate.

#### DR-06 — the guard is insurance, and here is the probe that shows why

Gap 2.1 asked whether the `modificationStamp` move at dumb-mode exit is real platform behaviour or a
`DumbModeTestUtils` artifact. **It is real platform behaviour**, it happens at **both** edges of the
episode, and it is not a roots change in disguise:

```
TYPE11-DR06 A0 library after install                        stamp=0 vfs=4 roots=3 psiTick=12 id=2074535251
TYPE11-DR06 A0 project after install                        stamp=0 vfs=15 roots=3 psiTick=12 id=1721201864
TYPE11-DR06 A1 library after a bare event pump, no dumb mode stamp=0 vfs=4 roots=3 psiTick=12 id=2074535251
TYPE11-DR06 A2 library inside dumb mode                     stamp=1 vfs=4 roots=3 psiTick=14 id=2074535251
TYPE11-DR06 A3 library after leaving dumb mode              stamp=2 vfs=4 roots=3 psiTick=16 id=2074535251
TYPE11-DR06 A3 project after leaving dumb mode              stamp=2 vfs=15 roots=3 psiTick=16 id=1721201864
TYPE11-DR06 A4 library refound                              stamp=2 ... sameInstance=true
TYPE11-DR06 A5 library after a SECOND dumb episode          stamp=4 vfs=4 roots=3 psiTick=20 id=2074535251
TYPE11-DR06 A5 project after a SECOND dumb episode          stamp=4 vfs=15 roots=3 psiTick=20 id=1721201864
```

Four things that together settle it. The stamp moves on **entry** as well as exit (`A1 → A2`), so
"leaving dumb mode" was never the whole mechanism. It moves **again** on a second episode
(`A3 → A5`, `2 → 4`), so it is not a one-off pending event from the fixture's install. A **bare event
pump moves nothing** (`A0 → A1`), which is the control that rules out "the harness pumped a queued
event at the exit". And the **project** file's stamp moves in lockstep with the library file's, so
this is not specific to a just-installed library tree. `roots` is still at `3` throughout, and
`vfs` never moves, so neither a roots change nor a VFS content event is responsible.

The event was then named rather than guessed:

```
TYPE11-DR06 C0 before                 stamp=0 vfs=19 roots=5 psiTick=29 id=485882996
TYPE11-DR06 C event=propFileTypes     stamp=1
TYPE11-DR06 C1 inside                 stamp=1 vfs=19 roots=5 psiTick=31 id=485882996
TYPE11-DR06 C event=propFileTypes     stamp=2
TYPE11-DR06 C2 after                  stamp=2 vfs=19 roots=5 psiTick=33 id=485882996
```

`propFileTypes` is `PsiTreeChangeEvent.PROP_FILE_TYPES`, and the source says why it fires here.
`PsiFileImpl.getModificationStamp()` is `myModificationStamp + contextStamp`, `myModificationStamp`
has one writer (`PsiFileImpl.clearCaches()`), its project-wide route is
`FileManagerImpl.clearPsiCaches` ← `possiblyInvalidatePhysicalPsi()` ← `processFileTypesChanged`, and
`FileManagerImpl`'s **constructor** subscribes that method to both dumb-mode edges
(`FileManagerImpl.java:93-103`):

```java
myConnection.subscribe(DumbModeListenerBackgroundable.TOPIC, new DumbModeListenerBackgroundable() {
  @Override public void enteredDumbMode() { processFileTypesChanged(false); }
  @Override public void exitDumbMode()    { processFileTypesChanged(false); }
});
```

Core platform, unconditional, with no test framework in the path. **Verdict: §3.3 step 1 is
insurance, not protection of a reachable defect.** Every dumb-mode transition invalidates all
physical PSI project-wide, `forFile` depends on `psiFile` in *both* branches of step 9, so a snapshot
built while dumb cannot survive the transition however it is pinned — which is exactly why design
§1.6's two mutations both stayed green and why neither could explain itself. The guard **stays**: it
costs one boolean, it cannot be wrong, and the alternative is depending on a platform subscription
that no test of ours pins. `TypeElevenDumbModeDecisionTest` continues to gate the *decision* (§1.9
B5), and `TypeElevenDr06StampProbeTest` now pins the platform premise the "insurance" verdict rests
on, so a platform that stops healing turns this red instead of being silently inherited.

⚠ This also **corrects design §1.6 and §1.11's shared attribution**. Both sighted the stamp move and
attributed it to their own event — §1.6 to "leaving dumb mode", §1.11 to `makeRootsChange`. Neither
is the mechanism; both are *routes to the same one*, `FileManagerImpl.clearPsiCaches`. §1.11's is
`PsiWsmListener.rootsChanged → possiblyInvalidatePhysicalPsi`, §1.6's is the dumb-mode subscription
above. The two independent sightings really were the same phenomenon, and it has a name.

#### DR-07 — Risk 1.3 is reachable, and it is a sixth under-recording channel (BUG-434)

Fixture: a provisioned library `lib.lua` declaring `---@class Widget` with `---@field part Gadget`
and `---@type Widget libWidget`, over a **project** file `gadget.lua` declaring
`---@class Gadget` / `---@field spin number`. The arms differ only in whether anything forced
`Widget.part`'s reference at `depth() == 0` before the snapshot was built.

```
TYPE11-DR07 pre-force depth=0 part=LuaTypeReference members=[spin]
TYPE11-DR07 arm1 cold       urls=[lib.lua, gadget.lua] absences=[] rescued=[] warm=[] inProgress=[] pinnable=false
TYPE11-DR07 arm2 pre-forced urls=[lib.lua]             absences=[] rescued=[] warm=[] inProgress=[] pinnable=true
TYPE11-DR07 arm3 pre-forced urls=[lib.lua]             … pinnable=true
TYPE11-DR07 arm3 before=[spin]
TYPE11-DR07 arm3 after=[spin] sameSnapshot=true
TYPE11-DR07 arm4 cold       urls=[lib.lua, gadget.lua] … pinnable=false
TYPE11-DR07 arm4 before=[spin]
TYPE11-DR07 arm4 after=[spun] sameSnapshot=false
```

Arms 3 and 4 rename the project field `spin → spun` through `rewriteAssertingRootsAreStill`, so the
roots tracker is asserted still and the only thing that changed is a **project** file. The pre-forced
arm keeps the same snapshot instance and keeps reporting `[spin]`. **That is a stale type shipped by
a pin this feature grants**, and it is the sixth under-recording channel after the absence, the warm
inner snapshot, the in-progress inner snapshot, the rescued global and the module absence.

The mechanism, **proven by mutation rather than by reading**: with `LuaTypeReference.resolved`'s
`by lazy` replaced by a plain `get()`, every arm reports the cold result —

```
mutation: val resolved: LuaType get() = LuaTypeManager…resolveType(name, context) ?: LuaPrimitiveType.UNKNOWN
TYPE11-DR07 arm2 pre-forced urls=[lib.lua, gadget.lua] … pinnable=false
TYPE11-DR07 arm3 pre-forced urls=[lib.lua, gadget.lua] … pinnable=false
TYPE11-DR07 arm3 before=[spin]
TYPE11-DR07 arm3 after=[spun] sameSnapshot=false
```

So the escape is **not** the one Risk 1.3 described. Risk 1.3 worried about a reference resolved
*after* the frame closed. What actually happens is the reverse: the reference is consumed **inside**
the frame, by `fromLuaType`, and the consumption is invisible because the `by lazy` short-circuits
before the manager is reached. `LuaTypeReference` is a **second memoization layer with no frame** —
`resolveType`'s own cache hit replays (`LuaTypeManagerImpl.kt:123-126`), and this one cannot, because
it never gets there. Risk 1.3's stated mitigation ("a `LuaClassType` is never stored in a snapshot")
is true and irrelevant: the reference does not need to reach the snapshot, only to be read while the
snapshot is being built.

**Reachability is not marginal.** The pre-force is any depth-0 read of a class member type —
completion (`getMembers`), a hover, `LuaOverrideLineMarkerProvider`, the hierarchy walk, an
assignability inspection. It must land in the same `PsiModificationTracker` epoch as the library
build, because `typeCache` is discarded on any PSI tick; but a wrong pin taken once survives until
the next roots or target tick, so the window only has to be hit once per session.

**Not fixed here, deliberately.** The obvious repair is the mutation above, and it is a production
behaviour change in the hot path of a *performance* feature: it replaces a field read with a
synchronized-map lookup plus a frame replay on every member access, and TYPE-11's own DR-04 arms,
`TypeElevenPinnableCostTest`'s `provisioned=11 pinnable=11` count and the corpus sweep would all have
to be re-measured. Filed as **BUG-434** with a roadmap row.

**Phase 5 (2026-08-12) fixed it, and not with that mutation.** The three re-measurements the
paragraph above demanded were run and all three are clean, but the shipped repair keeps the
memoization and adds the frame — `LuaTypeReference` memoizes `Pair<LuaType, SourceFrame>` through
`recording` and replays on every read of `resolved`, so no member access pays a map lookup and the
frame becomes the property of the layer that owns the answer. `LuaTypeSourceRecorder.replay` gained
an empty-frame short-circuit, which is an optimisation and provably nothing else (`absorb` is five
`addAll`s; five `addAll`s of empty collections change no receiver) and which matters because the fix
turns every read of a memoized reference into a replay while most references resolve names that
consumed nothing. Measurements: `TYPE11-COST provisioned=11 pinnable=11`; routine `ktlintCheck test
--rerun --no-build-cache` **2630 / 0 / 1**; `test -PwithCorpus --rerun --no-build-cache`
**2638 / 0 / 1**; DR-04 arm B `28 234 → 29 839 µs` (+5.7 %) on paired **isolated** runs whose arm A —
which has no library and no mechanism to be affected — moved −25 %…+17 % across the same runs, and
`15 147` / `10 906 µs` in-suite against Phase 3's in-suite reference of `22 859 µs`. Full write-up in
`docs/features/bug-fixes/434-lazy-type-reference-escapes-the-recording-frame/bug-report.md`.

## Premises examined

| Constraint treated as fixed | Verdict |
| :-- | :-- |
| "The residual might not be real" | **REFUTED by measurement.** It is real and it fires: `design.md` §1.1. Blanket pinning is unsound and this plan does not build it. |
| "The existing suite plus four corpus baselines will catch a stale-type regression here" | **REFUTED for both halves, by measurement.** The pre-existing tests passed unchanged under a build that demonstrably serves stale types; DR-09 then re-ran the sweep on that same build and **all four baselines compared unchanged** (`2571 tests completed, 2 failed` — both TYPE-11's own). See "DR-09 measured" below: the sweep is a single pass over an unedited tree, so it cannot observe a stale-cache defect at any corpus size. This premise is the reason `TypeElevenDr01ResidualTest` exists and the reason it is committed rather than thrown away. |
| "A project file adding a method to a stub class stales the snapshot" (`requirements.md`) | **PARTLY REFUTED.** True only for the **hosted** `---@class` form, and by a different route than `requirements.md` names (`freeGlobalSeed` → `tableToLuaType` → `fromLuaType`, not `materializeClass` reaching the snapshot). The bundled stdlib is 21/22 unhosted. `design.md` §1.2. |
| "A dumb-mode build bakes in nulls that are then sticky" (`requirements.md`, TYPE-11-05) | **REFUTED, and Phase 4 names the mechanism.** The nulls are baked in (`graph type = Undefined`); they are **not** sticky, and not because of any tracker — every dumb-mode transition fires `PROP_FILE_TYPES` from `FileManagerImpl`'s own subscription, invalidating all physical PSI and moving the file's `modificationStamp` on **entry and exit** alike (DR-06). The guard is kept, and — contrary to what this row said for three rounds — it **is** gated: on the decision rather than the outcome (design §1.9 B5, TC-16). See Gap 2.1. |
| "Library files can be matched by `VirtualFile` identity" (TYPE-11-03) | **REFUTED as written.** `===` is false for a project file the index itself supplied. Matching is by URL containment. `design.md` §1.3. |
| "Provenance must come from the plugin's own providers, not `ProjectFileIndex.isInLibrary`" | **Genuinely fixed, and re-confirmed.** The bundled root arrives over `jar://` inside the plugin jar; asking the platform's library index about that is a question with an unverified answer, and provenance never has to ask it. |
| "Rocks trees are out of v1 scope" | **Chosen, not forced.** They are excluded because they are mutable in place and their refresh signal is unverified — a v1 that included them would need TYPE-11-DR-03 answered first. TYPE-11-DR-03 was deliberately **not run**. |
| "`ProjectRootModificationTracker` is the right generation signal" | **Chosen, and half-executed.** Measured **not** to tick across a dumb-mode episode (`P2D before/inside/after dumb: roots=10` throughout) or across a document edit (`P2S before/after: rootsTracker=7`) — both are exactly what §3.3 relies on. That it **does** tick when a definition library is enabled is verified by **reading** the chain, not by running the production path: `LuaDefinitionLibraryEnabler.apply` → `setEnabledDefinitionLibrariesAndNotify` (**which early-returns if the enabled list is unchanged**, `LuaProjectSettings.kt:184-188`) → `notifyDefinitionRootsChanged` (`:199-205`) → `LuaSettingsChangedListener.TOPIC` → `LuaSettingsChangeListener.onSettingsChanged` (`project/LuaSettingsChangeListener.kt:36`) → `PlatformLibraryIndex.reload()` → `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`). The TYPE-11 fixtures announce the roots change themselves, so they do **not** exercise that chain. See Gap 2.3. |
| **"A `PsiFile` reached as a consumed source always has a non-null `originalFile.virtualFile`"** (`design.md` §3.1 step 4's named premise, DR-19) | **RUN, and true — then discarded as a dependency anyway, Phase 2.** With the six §3.5 sites wired, the full suite plus all three corpus sweeps made **47 331** `reportFile` calls and produced **one** null URL: `LuaTypeSourceRecorderTest.fileWithNoUrl`'s own non-physical `createFileFromText` fixture (`LuaFile\|unidentified.lua\|physical=false`). **Zero from a real call site.** So the premise holds as far as this project can observe. `reportFile`'s `?: return` was dropped regardless, because the same measurement prices the drop at **zero** — the sentinel is never written in practice — and the failure modes are not symmetric: unconditional, an unnameable source costs that file its pin (today's behaviour, no worse); exempt, it costs a *wrong* pin, which is a stale type the user sees, with no second chance (§1.12). A premise that costs nothing to stop relying on is one worth not relying on. |
| **"`reportFile`'s `?: return` is safe because a missing URL only weakens §3.3 step 3"** (`design.md` §3.1 step 4, as written before this round) | **REFUTED as an argument, though probably true as a conclusion — DR-19.** It restates the mechanism instead of proving safety, and it is the shape F1 overturned: losing the **last** URL leaves `urls` empty, an empty `urls` clears step 3 **vacuously**, and on a provisioned file with the other four sets empty the file **is pinned**. So the governing rule ("whenever the loss of a mark yields a pin, the mark is unconditional") does **not** exempt `reportFile`; only the named premise does — *a `PsiFile` reached as a consumed source always has a non-null `originalFile.virtualFile`* — and that premise is **undischarged**, reasoned rather than run. `testReportFileWithNoUrlRecordsNothing` now locks the no-op in with a green test, so it is cemented until Phase 2 gates it or drops the exemption. The behaviour was **not** changed on reasoning alone: a sentinel in `urls` reaches `isProvisionedUrl` and costs every affected file its pin. |
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
- **Widened by the third round, and again by the fifth.** A site can also be missed by *not existing*:
  **four** shapes now have all six sites correctly wired and still pin a stale snapshot, because the
  thing that needed recording was an **absence** (§1.8 B1), a **cache hit** (§1.8 B4), an
  **in-progress inner build** (§1.10 V1) or a **rescued global** (§1.10 V2). The Phase 3 source-text
  guard counts call sites and would have caught none of them. Treat "the recorder is complete" as a
  claim about **five** sets, not one — and note the trend: every review round so far has found one
  more channel, so the right prior is that a sixth exists rather than that the list is closed.
- **Escalated by DR-09.** The corpus sweep was the last remaining candidate for a broad safety net and
  it is measured blind to this class (below). `TypeElevenDr01ResidualTest` plus the Phase 3 source-text
  guard are the whole defence; there is no third line.
- **Mitigation**: (a) `TypeElevenDr01ResidualTest` is committed and covers the two known shapes;
  (b) implementation-plan Phase 3 adds `LuaTypeSourceRecorderCoverageTest`, a source-text guard over
  **`LuaTypeManagerImpl.kt` and `LuaTypesVisitor.kt`** that fails when the counts of
  `.findFile(` / `.getElements(` / `.getContainingFiles(` / `.getAllKeys(` / `.getLibraryFiles(` move
  off the recorded **2 / 3 / 2 / 2 / 0** and **1 / 0 / 0 / 0 / 1** without a matching `reportFile`
  count. **Three members over one file — the form this bullet carried until round six — is not
  sufficient**: it cannot fire for `StubIndex.getAllKeys` (`:342`, `:432`, the route §1.7 designates
  load-bearing for residual path 2) nor for `seedAmbientGlobals`, which reads another file entirely. ⚠ **The matcher had to be rewritten before it could fire at all** (Step 9
  blocker B3, design §1.9): specified as qualified chains it counted `1 / 3 / 0` against the real
  file — ktlint wraps both `FileBasedIndex` chains across lines, and one `findFile` goes through a
  `psiManager` local. It now matches bare members on comment-stripped, whitespace-collapsed text,
  which is also immune to a re-wrap silently dropping the count;
  (c) the over-approximation rule in §3.5 (report every file *visited*, not every file *used*) means a
  new site added inside an existing loop is likely already covered.

### Risk 1.1b: A definition library's content replaced **in place** ticks nothing

- **Impact**: a pinned library file keeps its old types indefinitely. Same silent-stale-type shape as
  Risk 1.1, reached without any missing `reportFile` — every site correctly wired and the file still
  goes stale.
- **How.** The roots tracker ticks on a change to the root **set**. A definition library re-fetched or
  edited into its existing `<id>-<version>` cache directory (`LuaDefinitionLibraryFetcher.cachedRoot`,
  consumed at `LuaDefinitionLibraryProvider.kt:55`) leaves that set unchanged. VFS/PSI events fire and
  tick `MODIFICATION_COUNT` — which is exactly the dependency a pinned file no longer has.
- **This is the rocks hazard, applied to a tree that v1 does *not* exclude.** `requirements.md` excludes
  LuaRocks partly because "the root set does not change, so the roots tracker does not tick". The same
  sentence is true of an in-place definition-library change, and the plan treated "any change to a
  provisioned tree comes through a roots change" as safe by construction without testing it. Definition
  libraries are the *demonstrated* win (the 123 KiB `wx` tree), so excluding them is not an option and
  the premise has to be tested instead.
- **Likelihood**: **very low, and now traced rather than guessed.** `LuaDefinitionLibraryFetcher.kt:79`
  short-circuits — `if (isCached(entry)) return cacheDir(entry)` — **before** `source.fetch`, so a
  same-version re-fetch writes nothing; `cacheDir` is `<id>-<version>` with the upstream SHA as the
  version (`:47`), so a re-pin *adds* a root and ticks; and `LuaDefinitionLibraryEnabler.kt:97` is the
  only caller of `ensureCached`, with no delete-and-refetch path anywhere in `src/main/`. **In-place
  replacement is unreachable through shipped code.** The residue is a user hand-editing the shared
  `<system>/lunar/definitions/` tree.
- **Mitigation**: **accepted and documented**, the same treatment rocks get — the trace above answers
  the reachability question a de-risking task was going to ask. DR-17 is reduced to a note: if a
  delete-and-refetch or in-place "update library" path is ever added, this risk becomes live and needs
  a content-level signal or a fetch stamp at that point.
- **Not covered by `TypeElevenGenerationSignalTest`**, contrary to an earlier claim here: none of
  TC-2a/2b/3/4 edits library *content*.

### Risk 1.2: `sourceCache` and the type caches drift apart — **CLOSED, Phase 2, by deletion**

**There is no `sourceCache`.** Phase 2 took design §9's deferred co-location option: the three
existing caches hold `CachedAnswer(resolvedType, sourceFrame)`, so the answer and its provenance are
one object, written together and read together. The drift state this row describes has no
representation, whichever map instance a door's local happens to point at. Deleted with it: the
fourth `CachedValue`, the map-key prefix scheme, `replaySources`, the §3.6 drift check, and
`reportUnreplayableHit` — whose only reachable input was the drift state, and which §3.6 itself said
Phase 2 must give a fixture or delete rather than ship unexercised. The row below is the record of
what was closed.

- **Impact**: a replayed source set that describes a different answer than the cached type — either a
  lost pin (harmless) or a missing source (stale type).
- **Likelihood**: **low, but not for the reason this row used to give.** "Written in the same
  statement" is false under the specified design: the frame is written by `recordUnder` and the type by
  `.also { cache[name] = it }` (`LuaTypeManagerImpl.kt:145`), and `design.md` §3.6's own drift paragraph
  describes them coming apart. What keeps it low is the shared `PsiModificationTracker` dependency plus
  the §3.6 drift check, which converts a drift into a lost pin rather than a stale type. Co-locating the
  frame in the three caches (§9) would make this risk structurally impossible and delete this row.
- **Mitigation**: §3.6 requires `sourceCache` to be built with the identical `createCachedValue`
  shape as the three existing caches. Any future change to one cache's invalidation must change all
  four; implementation-plan Phase 1 puts them adjacent in the file so the coupling is visible.

### Risk 1.3: A lazily-resolved `LuaTypeReference` escapes the recording frame — **REAL, not the shape written below, and CLOSED (BUG-434)**

- **Status (Phase 5, 2026-08-12)**: **CLOSED.** `LuaTypeReference` now memoizes
  `Pair<LuaType, SourceFrame>` through `LuaTypeSourceRecorder.recording` and replays the frame on
  every read of `resolved`, which is the `CachedAnswer` idiom Phase 2 gave the three manager doors
  (design §3.1 step 7). The invariant restored: **a reference read inside a frame contributes its
  consumed sources whether or not it was resolved earlier.** `isPinnable` is unchanged — the channel
  is a *missing report*, not a frame state, and no clause reading a clean frame can tell a wrongly
  clean one from a correct one, which is why it is fixed at the door rather than by a step 8.
  All four arms of `TypeElevenDr07LazyReferenceProbeTest` now assert; arms 2 and 3 are red under
  "drop the replay, change nothing else" and the cold controls 1 and 4 stay green.
  Priced, since it is the hot path of a performance feature: `TYPE11-COST provisioned=11
  pinnable=11`, routine suite **2630 / 0 / 1**, corpus suite **2638 / 0 / 1** (the `bf715eb2` baseline of 2631 plus Phase 4's 7 new tests), DR-04 arm B +5.7 % on
  paired isolated runs inside a ±20 % harness noise band and below Phase 3's in-suite reference in
  both in-suite runs. Full evidence:
  `docs/features/bug-fixes/434-lazy-type-reference-escapes-the-recording-frame/bug-report.md`.
  ⚠ The candidate the roadmap row proposed — `by lazy` → plain `get()` — was **not** taken. It is
  correct (it is what the Phase 4 attribution mutation used) but it deletes the memoization, paying a
  synchronized map lookup and a reentrancy round trip per member access, and it leaves the frame
  nobody's property so the next memoizing consumer re-opens the hole.
- **Status (Phase 4, 2026-08-12)**: **reproduced, attributed by mutation, filed as BUG-434.** Output
  and method in "Phase 4 … DR-07" above. It is a **sixth** under-recording channel and it ships a
  stale type: a provisioned library file is judged `pinnable=true` while its snapshot's content came
  from a project file, and a later edit to that project file leaves the pinned snapshot in place
  (`arm3 after=[spin] sameSnapshot=true`, against control `arm4 after=[spun] sameSnapshot=false`).
- **Impact**: a source consumed *during* the build is not in the frame, so it cannot be judged, and
  the file is pinned while depending on it. A pin must be correct when it is taken (§1.12).
- **Likelihood**: this entry rated it "low but not zero" on the reasoning below, and **the reasoning
  was wrong in its direction**. It worried about a reference resolved *after* `recording` returned.
  What happens is the opposite: the reference is consumed **inside** the frame by
  `LuaGraphType.fromLuaType` (`LuaGraphType.kt:251`), and the consumption is invisible because
  `LuaTypeReference.resolved`'s `by lazy` (`LuaTypeReference.kt:9-11`) short-circuits before
  `LuaTypeManager.resolveType` is reached — so neither the cold path's `recordInto` nor the warm
  path's `replay` runs. `LuaTypeReference` is a second memoization layer with **no frame**, unlike
  the three caches Phase 2 co-located frames into.
- **The stated mitigation was true and irrelevant.** "A `LuaClassType` is never stored in a snapshot"
  is correct; the reference does not need to reach the snapshot, only to be read while the snapshot
  is being built. Every `@field` member type, every `@class` supertype, every function parameter and
  return, and every alias target is such a reference (`LuaTypeManagerImpl.kt:356`, `:365`, `:412`,
  `:424`, `:592`, `:597-598`, `:617`).
- **Was not fixed inside Phase 4, and is fixed in Phase 5.** The repair is a production behaviour
  change in the hot path of a performance feature, so it was filed rather than rushed; the pricing it
  needed is the four measurements in the Status row above. See BUG-434 and the Phase 4 section.

### Risk 1.4: The win is smaller than the headline suggests

- **Impact**: expectations. `design.md` §1.5 measures arm B at 3–5× arm A after the change, against a
  DR-04 success criterion of "near the 9 ms baseline".
- **Likelihood**: **certain** — it is already measured.
- **Mitigation**: state it, do not hide it. The recurring per-keystroke cost drops from hundreds of
  milliseconds to tens; the remaining gap is the `resolveGlobal` + `graphTypeToLuaType` conversion
  `requirements.md` already named, and it is a separate piece of work (DR-08).

## Design Gaps

### Gap 2.1: The dumb-mode *staleness* has no reproducing test — **CLOSED, Phase 4: answered, the platform heals it**

- **Answer (2026-08-12, DR-06)**: the stamp move is **real platform behaviour**, not a
  `DumbModeTestUtils` artifact, so the outcome is unreproducible for a reason that holds in a running
  IDE. `FileManagerImpl`'s constructor subscribes `processFileTypesChanged` to **both** edges of dumb
  mode (`FileManagerImpl.java:93-103`), which runs `possiblyInvalidatePhysicalPsi` →
  `clearPsiCaches` → `PsiFileImpl.clearCaches()` → `myModificationStamp++` for every cached file, and
  fires `PROP_FILE_TYPES`. Measured: entry `0 → 1`, exit `1 → 2`, again `2 → 4` on a second episode,
  project and library files alike, `roots` and the VFS stamp still, and a bare event pump moving
  nothing. Full output in "Phase 4 … DR-06" above.
- **Consequence**: §3.3 step 1 is **insurance**, not protection of a reachable defect. It stays — one
  boolean, cannot be wrong — and the thing it insures against is now named: a platform that stops
  invalidating physical PSI across dumb transitions. `TypeElevenDr06StampProbeTest` asserts that
  premise, so such a platform turns the suite red rather than silently promoting the guard to
  load-bearing behind everyone's back. `TypeElevenDumbModeDecisionTest` continues to gate the
  *decision* (§1.9 B5, TC-16).
- **What follows for the record below**: the original framing of this gap ("if it is an artifact, the
  guard is load-bearing in production") is answered in the negative, and design §1.6's and §1.11's
  attributions of the same phenomenon to two different causes are both corrected — they are two
  routes to one mechanism.

The original entry, kept because the question it asked is the one that was answered:

- **Question**: can a snapshot built while `DumbService.isDumb` actually outlive dumb mode, in a real
  IDE rather than in `DumbModeTestUtils.runInDumbModeSynchronously`?
- **Measured**: not in this harness. The library `PsiFile`'s `modificationStamp` moves 0→1 across the
  fixture's dumb episode, and `forFile`'s `psiFile` dependency rebuilds the snapshot for that reason
  alone — with the `!isDumb` guard removed **and** with the churn tracker replaced by
  `ModificationTracker.NEVER_CHANGED`, the test still passed.
- **Options / leaning**: keep the guard (one boolean, cannot be wrong) and stop claiming *this* test
  covers it. The open question is whether the stamp move is real platform behaviour or a fixture
  artifact of `DumbModeTestUtils`; if it is an artifact, the guard is load-bearing in production.
- **Narrowed by Step 9 blocker B5 (2026-08-11).** "The outcome does not reproduce" was allowed to
  stand in for "the guard is untestable", and that inference was wrong. The decision is a pure
  predicate: `isPinnable(libraryFile, SourceFrame())` is `false` under dumb mode and `true` with
  §3.3 step 1 deleted, because an empty frame on a provisioned file clears steps 2–7. TYPE-11-05 is
  therefore gated by `TypeElevenDumbModeDecisionTest` (TC-16), and what remains open here is strictly
  the platform question — **not** "no automated protection", which is no longer true. See design §1.9.
- **Resolved by**: DR-06.

### Gap 2.3: The production roots-tick chain is verified by reading, not by running

- **Question**: does enabling a definition library actually tick `ProjectRootModificationTracker` in a
  running IDE?
- **Why it is open**: the chain is five hops and one of them is a message-bus subscription that only
  fires if `LuaSettingsChangeListener` has been instantiated — a dependency that already produced a
  defect once (its KDoc records "TOOLING-08 review #41"). Every TYPE-11 fixture calls
  `makeRootsChange` directly, so none of them would notice if the chain were broken.
- **Two things this gap did not say, both found by Step 9 (2026-08-11).** First, the case that was
  supposed to close it — TC-2 as originally written, "empty the enabled list and assert `resolveGlobal`
  no longer answers" — **could not go red**: emptying the list removes the file from `allScope`, so
  resolution finds no candidate and the pinned snapshot is never consulted. It passes on `main`, under
  any pinning rule, and with the roots tracker deleted. TC-2 is restated to keep the first library
  **enabled** and tick roots by enabling a **second** one, asserting snapshot instance identity.
  Second, the production chain has an **async hop**: `LuaProjectSettings.kt:199-205` publishes inside
  `ApplicationManager.getApplication().invokeLater { … }`, so a test driving
  `LuaDefinitionLibraryEnabler.apply` must pump the EDT or it observes nothing and passes for the
  wrong reason — the precise failure this gap exists to prevent.
- **Options / leaning**: cover it in implementation-plan Phase 3 with
  `TypeElevenGenerationSignalTest` case **(b)** — case (a) drives the decision and does not touch the enabler — driving `LuaDefinitionLibraryEnabler.apply` rather than
  the fixture helper, and in human-verification Scenarios 3.1/3.2.
- **CLOSED, Phase 3 (2026-08-11)** — `TypeElevenGenerationSignalTest.testTheProductionEnablePathTicksTheGenerationTracker`
  seeds both trees on disk, enables L1 alone, forces `LuaSettingsChangeListener.getInstance(project)`,
  calls `LuaDefinitionLibraryEnabler.apply(listOf(L1, L2))` and pumps the EDT with
  `PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()`. The tracker advances. Shown red by
  deleting `notifyDefinitionRootsChanged()` from `LuaProjectSettings.setEnabledDefinitionLibrariesAndNotify`,
  so it gates the chain rather than the fixture.

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
| TYPE-11-DR-05 | Build a snapshot under `DumbService.isDumb`, exit, complete again | TYPE-11-05 | **done, negative** — `design.md` §1.6. Nulls are baked in; they do not survive; two mutations failed to make the harness red. Reopened as DR-06. **Its trace nonetheless grounds the gate that replaced it** (§1.9 B5): `libDumb graph type = Undefined` inside the dumb block *is* `resolveGlobal` having returned null, i.e. the empty frame TC-16 asserts on |
| TYPE-11-DR-06 | Determine whether the `modificationStamp` move at dumb-mode exit is platform behaviour or a `DumbModeTestUtils` artifact. If the former, TYPE-11-05's guard is dead code and should be deleted; if the latter, build a fixture that reproduces the staleness | Gap 2.1, TYPE-11-05 | **DONE, Phase 4 (2026-08-12) — negative, and now explained.** Platform behaviour, not a `DumbModeTestUtils` artifact: `FileManagerImpl`'s constructor subscribes `processFileTypesChanged` to **both** dumb-mode edges, which invalidates all physical PSI and moves every cached file's `modificationStamp` (`0 → 1` on entry, `1 → 2` on exit, `2 → 4` on a second episode; `roots` and the VFS stamp still; a bare event pump moves nothing). The *outcome* therefore cannot occur in a real IDE either, so the guard is **insurance and stays**; Gap 2.1 closes. Method and full output above |
| TYPE-11-DR-07 | Probe whether a `LuaTypeReference` can be resolved after its recording frame closed, and whether the resulting source can reach a pinned snapshot | Risk 1.3 | **DONE, Phase 4 (2026-08-12) — positive: the defect is real, filed as BUG-434.** Reachable, and by the *opposite* mechanism to the one Risk 1.3 named: the reference is consumed **inside** the frame by `fromLuaType`, and `resolved`'s `by lazy` short-circuits before `resolveType`, so neither `recordInto` nor `replay` runs. `arm2 pre-forced urls=[lib.lua] pinnable=true` against `arm1 cold urls=[lib.lua, gadget.lua] pinnable=false`; end to end `arm3 after=[spin] sameSnapshot=true` against `arm4 after=[spun] sameSnapshot=false`. Attribution proven by replacing the `by lazy` with a `get()` — every arm then reports the cold result. **Sixth under-recording channel. Fixed in Phase 5 (2026-08-12)**: the memoized answer now carries its `SourceFrame` and replays it on every read (design §3.1 step 7), priced at zero pins (`provisioned=11 pinnable=11`), 2630/0/1 routine, 2638/0/1 corpus and DR-04 arm B inside the harness noise band. Risk 1.3 closed |
| TYPE-11-DR-08 | Profile the residual arm-B cost (`resolveGlobal` + `graphTypeToLuaType`, fresh `visited` map per call over a 3 600-member table) and decide whether it is a separate feature | Risk 1.4 | todo |
| TYPE-11-DR-11 | Step 9 blocker B1: does a build whose global resolution answered **nothing** get pinned, and does the declaration written afterwards fail to reach it? | TYPE-11-06, `design.md` §3.3/§3.4 | **done, positive (the defect is real)** — `design.md` §1.8. `expected:<[afterDeclared]> but was:<[]>`. Closed by §3.3 step 4; measured cost **zero** pinned files. |
| TYPE-11-DR-12 | Step 9 blocker B4: is the interleaving reachable in which a nested `forFile` is served warm, so the outer library file records an incomplete source set and is pinned? | TYPE-11-06, `design.md` §3.6 | **done, positive (the defect is real, and needs no roots tick)** — `design.md` §1.8. `expected:<[afterEdit]> but was:<[beforeEdit]>`. Closed by §3.7 (replay), not by blanket-unpinnable; measured cost **zero** pinned files. |
| TYPE-11-DR-13 | Price the alternative to blanket-unpinnable for B1: is there a tracker that ticks when a global declaration appears? | `design.md` §9 | **done, not adopted** — `FileBasedIndex.getIndexModificationStamp(LuaGlobalAssignmentIndex.KEY, project)` exists and behaves as hoped (`16 / 16 / 17`), but the rule it would optimise costs nothing, so the second invalidation axis buys nothing. |
| TYPE-11-DR-14 | Step 9 blocker V1: is the `LuaTypesVisitor.inProgressSnapshot` early return ever served for a file **other** than the one that directly re-entered itself, and does that ship a stale type? | `design.md` §3.7, TYPE-11-06 | **done, positive (the defect is real)** — `design.md` §1.10. Reachable and measured: `TYPE11-DR14 inProgress hit file=…/outer.lua depth=5`; `expected:<[afterEdit]> but was:<[beforeEdit]>`. Closed by §3.1 step 5c + §3.3 step 6 (report, do not replay — the served snapshot is mid-build). Measured cost **zero** pinned files (`b14=11`). |
| TYPE-11-DR-15 | Step 9 blocker V2: does a global resolution that **succeeded** via the all-scope fallback get pinned, and is it then out-ranked by a project declaration it never re-judges? | `design.md` §3.1/§3.3, TYPE-11-06 | **done, positive (the defect is real)** — `design.md` §1.10. `expected:<[afterProject]> but was:<[beforeEdit]>`. Closed by §3.1 step 5b + §3.3 step 7. Two variants priced together; `dr15rescued` adopted over `dr15broad` — identical cost (`11`) on every fixture, and the broader one only duplicates the B1 absence rule. |
| TYPE-11-DR-16 | Is there a **sixth** under-recording channel? Every review round so far has found one more (absence, warm inner, in-progress inner, rescued global), which makes "the list is closed" the weaker prior. Enumerate the memoized doors and early returns systematically rather than waiting for the next review. **Two grounded candidates already**: (a) **`resolveModule` has V2's shape and V2's fix does not cover it** — `resolveModuleCandidates` (`lang/path/LuaModuleFileResolver.kt:26-49`) yields **project source-path** candidates before index-found ones and `doResolveModule` takes the first that types, so a module answered by a library today can be out-ranked by a project file created later, and `reportRescuedGlobal` is scoped to `resolveGlobal` only; (b) `resolveModule` has **no dumb-mode guard** at all (only `:84` and `:141` do), so §3.4's "a dumb build records zero sources" is false for any library file containing `require` — see §1.9 B5, where the general claim is already flagged as narrower than it reads | Risk 1.1, TYPE-11-06 | todo |
| TYPE-11-DR-20 | `addMethodsOf` (`LuaTypeManagerImpl.kt:542`) iterates every global-declaration stub key with **no `ProgressManager.checkCanceled()`**, which engineering-contract §2 requires of "PSI lookup routines". Pre-existing — the whole package has zero `checkCanceled` calls — but Phase 2 added a `reportFile` line inside that loop, so it made an already-uncancellable long loop longer. Deferred out of Phase 2 deliberately: a new cancellation point is a behaviour change and Phase 2's exit criterion is that nothing moves. **Fix it as its own change, not inside a TYPE-11 phase** | engineering-contract §2, Phase 2 review D2 | todo |
| TYPE-11-DR-21 | Assert the invariant Phase 2 left implicit: **every path that stores a `null` answer reports the absence into the frame it stores.** Verified true today (`recordInto` is the only null-storing path and reports unconditionally), but nothing enforces it, and a future null-storing path that forgets re-creates §1.8 B1 through the cache — silently, since the suite stays green. Phase 3 owes a direct assertion | design §3.6, Phase 2 review | **DONE, Phase 3 (2026-08-11)** — a behavioural assertion cannot catch a *new* storing path, so the enforceable form is structural: `LuaTypeSourceRecorderCoverageTest.only one path stores an answer beside its frame` pins the `CachedAnswer(` construction count at 2 (the declaration + `recordInto`), with a message telling a future author what a new site owes. Red under an injected second site |
| TYPE-11-DR-22 | Strengthen or replace **M6** (`resolveGlobal` replay). Its red is proof-of-hit only in the ledger run, not in the assertion: the answer is `null`, so there is nothing to compare identity on, and if a PSI tick ever lands between the two calls it stays green while gating nothing. It also dies alongside M2. Give the hit its own in-test evidence, as M5 has via `assertSame` on an uninterned `LuaClassType` | Phase 2 review §4 | **DONE, Phase 3 (2026-08-11)** — M6 now asserts `PsiModificationTracker.modificationCount` is unchanged across the two calls. That is the hit's in-test evidence: the entry the cold call wrote cannot have been discarded, and `resolveGlobal` reads the cache before anything else. Red under a deliberate tick inserted between the calls |
| TYPE-11-DR-18 | Price the module-absence rule (§3.1 step 5d, §1.12) in the same `REVIEW-COST` form every other absence rule was priced in: how many of the 11 provisioned files lose their pin when `resolveModule`'s `ANY` fall-through records an absence? Expected **zero** — nothing shipped `require`s — but expected is not measured, and this is the one rule adopted on correctness alone | §1.12, TYPE-11-06 | **DONE, Phase 2 (2026-08-11)** — `DR-18 REVIEW-COST TOTALS provisioned=11 withModuleRule=11 withoutModuleRule=11`. **Zero lost pins, measured.** Method below |
| TYPE-11-DR-19 | **Discharge `reportFile`'s `?: return` premise, or drop the exemption.** §3.1 step 4 exempts `reportFile` from the governing rule ("whenever the loss of a mark yields a pin, the mark is unconditional") — but its loss *can* yield a pin: losing the **last** URL leaves `urls` empty, an empty `urls` clears §3.3 step 3 **vacuously**, and on a provisioned file with the other four sets empty every step clears and the file **is pinned** — the exact "empty because nothing was recorded, not because nothing was consumed" inversion §3.4 names. The mechanism argument the exemption previously rested on ("fewer URLs only weakens a test over the URLs that are present") is a **restatement, not a safety proof**, and is the same shape as the reasoning F1 overturned. What it actually needs is the named premise now written into §3.1 step 4: *a `PsiFile` reached as a consumed source always has a non-null `originalFile.virtualFile`* — every §3.5 site takes its file from `StubIndex` / `FileBasedIndex` / `PsiManager.findFile`, the `originalFile` hop already converts completion copies back, and a VFS-less PSI file (`DummyHolder`, non-physical `createFileFromText`) is neither index-reachable nor user-editable, so it cannot be a project dependency whose future content changes the answer. **Undischarged: reasoned, not run**, and `LuaTypeSourceRecorderTest.testReportFileWithNoUrlRecordsNothing` now *locks the no-op in with a green test*, so it is cemented until someone deliberately revisits it. **Phase 2 is the place**: it wires the six §3.5 `reportFile` sites, so it is the first point at which the premise can be gated (assert non-null at each site, or count nulls over the corpus) or the exemption dropped for a sixth `unidentifiedSources` set. ⚠ `reportFile`'s behaviour was deliberately **not** changed on this reasoning alone — flipping it unpriced repeats F1's error in the other direction, since a sentinel in `urls` reaches `isProvisionedUrl`, is classified unprovisioned, and costs every affected file its pin | `design.md` §3.1 step 4, §3.3 step 3, TYPE-11-06 | **DONE, Phase 2 (2026-08-11)** — premise **run** (47 331 calls, 1 null, and that one this suite's own non-physical fixture) and the exemption **dropped anyway**, at a measured cost of zero. Decision below |
| TYPE-11-DR-17 | ~~Is Risk 1.1b reachable through the shipped fetcher?~~ **Answered by tracing, not by a spike: it is not** — `isCached` short-circuits before `fetch`, `cacheDir` is `<id>-<version>`, and no delete-and-refetch path exists. Reduced to a standing note on Risk 1.1b: re-open if such a path is ever added. Kept as a row so the reasoning is not rediscovered. Was: is Risk 1.1b reachable through the shipped fetcher? Replace a definition library's content in place under its existing `<id>-<version>` root, with a pinned snapshot already built, and see whether anything ticks. If it is reachable, pick between accepting it as documented scope, a content-level signal, or a fetch stamp that always changes the root | Risk 1.1b, TYPE-11-02 | todo |

## Test Case Gaps

- **No test edits a library file.** Users cannot, and the invalidation path for a library content
  change is a roots change, which is covered by `TypeElevenGenerationSignalTest` (plan Phase 3).
- ~~**No test exercises a `require` from a library file into a project module.**~~ **Closed, Phase 3**
  — `TypeElevenDr01ResidualTest.testALibraryThatRequiresAProjectModuleTracksThatModule`. ⚠ Running it
  changed the claim: §6 says `getModuleType` reports the project file "and the file is therefore not
  pinnable", but the file is unpinnable *before* that report is consulted, because `require` itself is
  a rescued global (Finding 3 above). The reported URL is real and correct; it is simply not what
  decides this case.
- **No test covers a rocks tree**, deliberately — v1 leaves rocks on today's behaviour, so the
  existing rocks suites already assert the unchanged answer.
- **Closed by the third round**: the absence shape and the warm-inner-snapshot shape now have
  fixtures (`TypeElevenDr11LateDeclarationTest`, `TypeElevenDr12WarmInnerSnapshotTest`), each shown
  red under the rule without its guard.
- ~~**No fixture asserts the pinnable *count*.**~~ **Closed, Phase 3** — `TypeElevenPinnableCostTest`,
  which asserts the enumerated count (11) first and then that none is rejected: measured
  `TYPE11-COST provisioned=11 pinnable=11`. ⚠ It also caught a fixture leak the suite had no other
  way to see: the light project's `LuaProjectSettings` is shared across test classes, so TC-3's
  target switch left the next class enumerating the **5.1** tree (9 stubs). The base fixture now
  restores the entry target in `tearDown`. Original text:  The value of the feature and the correctness of the
  rule pull in opposite directions, and only counting distinguishes "closed the hole" from "pinned
  nothing". The third round counted with a scaffold (`guarded=11`); nothing committed does. Phase 3
  should add it as its own live-fixture class `TypeElevenPinnableCostTest` (**not** alongside `LuaTypeSourceRecorderCoverageTest`, which reads text and cannot build fixtures — `design.md` TC-15).
- **No multi-project test.** `LuaLibraryProvenance` is per project and the definition cache is per
  **user**; two projects enabling the same library share one tree. Untested here.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Parent of the measurement discipline used here: `docs/features/completion/09-member-enumeration/`
