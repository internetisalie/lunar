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
| `TypeElevenDr05DumbModeTest.testASnapshotBuiltWhileDumbDoesNotSurviveIntoSmartMode` | (a) `!isDumb` term removed; (b) generation tracker replaced with `ModificationTracker.NEVER_CHANGED` | **GREEN under both.** Not a gate — see Gap 2.1. |
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
**6 tests, 0 failures** when these six rows were produced (**8 tests, 0 failures** after the two
memoization rows below were added).

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
six pre-existing methods stayed **green**, and only the new one fell. Neither new row depends on
`setUp`'s blanket tick — each populates the cache inside its own body with an answer it asserts first.

| Assertion | Mutation applied | Result |
| :-- | :-- | :-- |
| `LuaLibraryProvenanceTest.testTheMemoizedRootListIsRecomputedAfterARootsTick` | both dependencies replaced by `ModificationTracker.NEVER_CHANGED` | **RED, alone (1 of 8)** — `a disabled library is no longer a root, so the memoized list must have been recomputed`. The other seven, including all six that shipped in `1be7cc0d`, stayed green — which is the F3 finding, measured. |
| `…testTheRootListIsNotRecomputedWhileNothingTicks` | the `CachedValuesManager` wrapper dropped (`rootUrls() = computeRootUrls()`) | **RED, alone (1 of 8)** — `with neither dependency ticked the root list must be served from the cache, unrecomputed`. Asserts both trackers still across the settings write, so a stray tick fails loudly instead of quietly deciding the outcome. |

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

## Premises examined

| Constraint treated as fixed | Verdict |
| :-- | :-- |
| "The residual might not be real" | **REFUTED by measurement.** It is real and it fires: `design.md` §1.1. Blanket pinning is unsound and this plan does not build it. |
| "The existing suite plus four corpus baselines will catch a stale-type regression here" | **REFUTED for both halves, by measurement.** The pre-existing tests passed unchanged under a build that demonstrably serves stale types; DR-09 then re-ran the sweep on that same build and **all four baselines compared unchanged** (`2571 tests completed, 2 failed` — both TYPE-11's own). See "DR-09 measured" below: the sweep is a single pass over an unedited tree, so it cannot observe a stale-cache defect at any corpus size. This premise is the reason `TypeElevenDr01ResidualTest` exists and the reason it is committed rather than thrown away. |
| "A project file adding a method to a stub class stales the snapshot" (`requirements.md`) | **PARTLY REFUTED.** True only for the **hosted** `---@class` form, and by a different route than `requirements.md` names (`freeGlobalSeed` → `tableToLuaType` → `fromLuaType`, not `materializeClass` reaching the snapshot). The bundled stdlib is 21/22 unhosted. `design.md` §1.2. |
| "A dumb-mode build bakes in nulls that are then sticky" (`requirements.md`, TYPE-11-05) | **HALF REFUTED.** The nulls are baked in (`graph type = Undefined`); they are **not** sticky, and not because of any tracker — the file's own `modificationStamp` moves 0→1 when dumb mode ends. The guard is kept, and — contrary to what this row said for three rounds — it **is** gated: on the decision rather than the outcome (design §1.9 B5, TC-16). See Gap 2.1. |
| "Library files can be matched by `VirtualFile` identity" (TYPE-11-03) | **REFUTED as written.** `===` is false for a project file the index itself supplied. Matching is by URL containment. `design.md` §1.3. |
| "Provenance must come from the plugin's own providers, not `ProjectFileIndex.isInLibrary`" | **Genuinely fixed, and re-confirmed.** The bundled root arrives over `jar://` inside the plugin jar; asking the platform's library index about that is a question with an unverified answer, and provenance never has to ask it. |
| "Rocks trees are out of v1 scope" | **Chosen, not forced.** They are excluded because they are mutable in place and their refresh signal is unverified — a v1 that included them would need TYPE-11-DR-03 answered first. TYPE-11-DR-03 was deliberately **not run**. |
| "`ProjectRootModificationTracker` is the right generation signal" | **Chosen, and half-executed.** Measured **not** to tick across a dumb-mode episode (`P2D before/inside/after dumb: roots=10` throughout) or across a document edit (`P2S before/after: rootsTracker=7`) — both are exactly what §3.3 relies on. That it **does** tick when a definition library is enabled is verified by **reading** the chain, not by running the production path: `LuaDefinitionLibraryEnabler.apply` → `setEnabledDefinitionLibrariesAndNotify` (**which early-returns if the enabled list is unchanged**, `LuaProjectSettings.kt:184-188`) → `notifyDefinitionRootsChanged` (`:199-205`) → `LuaSettingsChangedListener.TOPIC` → `LuaSettingsChangeListener.onSettingsChanged` (`project/LuaSettingsChangeListener.kt:36`) → `PlatformLibraryIndex.reload()` → `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`). The TYPE-11 fixtures announce the roots change themselves, so they do **not** exercise that chain. See Gap 2.3. |
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

### Risk 1.2: `sourceCache` and the type caches drift apart

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

### Gap 2.1: The dumb-mode *staleness* has no reproducing test (the *guard* is gated — §1.9 B5)

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
| TYPE-11-DR-05 | Build a snapshot under `DumbService.isDumb`, exit, complete again | TYPE-11-05 | **done, negative** — `design.md` §1.6. Nulls are baked in; they do not survive; two mutations failed to make the harness red. Reopened as DR-06. **Its trace nonetheless grounds the gate that replaced it** (§1.9 B5): `libDumb graph type = Undefined` inside the dumb block *is* `resolveGlobal` having returned null, i.e. the empty frame TC-16 asserts on |
| TYPE-11-DR-06 | Determine whether the `modificationStamp` move at dumb-mode exit is platform behaviour or a `DumbModeTestUtils` artifact. If the former, TYPE-11-05's guard is dead code and should be deleted; if the latter, build a fixture that reproduces the staleness | Gap 2.1, TYPE-11-05 | todo. **Narrowed, not closed, by B5**: the guard is now gated on the decision (TC-16), so "is it protected" is answered; this row is only the remaining question of whether the *outcome* can occur in a real IDE |
| TYPE-11-DR-07 | Probe whether a `LuaTypeReference` can be resolved after its recording frame closed, and whether the resulting source can reach a pinned snapshot | Risk 1.3 | todo |
| TYPE-11-DR-08 | Profile the residual arm-B cost (`resolveGlobal` + `graphTypeToLuaType`, fresh `visited` map per call over a 3 600-member table) and decide whether it is a separate feature | Risk 1.4 | todo |
| TYPE-11-DR-11 | Step 9 blocker B1: does a build whose global resolution answered **nothing** get pinned, and does the declaration written afterwards fail to reach it? | TYPE-11-06, `design.md` §3.3/§3.4 | **done, positive (the defect is real)** — `design.md` §1.8. `expected:<[afterDeclared]> but was:<[]>`. Closed by §3.3 step 4; measured cost **zero** pinned files. |
| TYPE-11-DR-12 | Step 9 blocker B4: is the interleaving reachable in which a nested `forFile` is served warm, so the outer library file records an incomplete source set and is pinned? | TYPE-11-06, `design.md` §3.6 | **done, positive (the defect is real, and needs no roots tick)** — `design.md` §1.8. `expected:<[afterEdit]> but was:<[beforeEdit]>`. Closed by §3.7 (replay), not by blanket-unpinnable; measured cost **zero** pinned files. |
| TYPE-11-DR-13 | Price the alternative to blanket-unpinnable for B1: is there a tracker that ticks when a global declaration appears? | `design.md` §9 | **done, not adopted** — `FileBasedIndex.getIndexModificationStamp(LuaGlobalAssignmentIndex.KEY, project)` exists and behaves as hoped (`16 / 16 / 17`), but the rule it would optimise costs nothing, so the second invalidation axis buys nothing. |
| TYPE-11-DR-14 | Step 9 blocker V1: is the `LuaTypesVisitor.inProgressSnapshot` early return ever served for a file **other** than the one that directly re-entered itself, and does that ship a stale type? | `design.md` §3.7, TYPE-11-06 | **done, positive (the defect is real)** — `design.md` §1.10. Reachable and measured: `TYPE11-DR14 inProgress hit file=…/outer.lua depth=5`; `expected:<[afterEdit]> but was:<[beforeEdit]>`. Closed by §3.1 step 5c + §3.3 step 6 (report, do not replay — the served snapshot is mid-build). Measured cost **zero** pinned files (`b14=11`). |
| TYPE-11-DR-15 | Step 9 blocker V2: does a global resolution that **succeeded** via the all-scope fallback get pinned, and is it then out-ranked by a project declaration it never re-judges? | `design.md` §3.1/§3.3, TYPE-11-06 | **done, positive (the defect is real)** — `design.md` §1.10. `expected:<[afterProject]> but was:<[beforeEdit]>`. Closed by §3.1 step 5b + §3.3 step 7. Two variants priced together; `dr15rescued` adopted over `dr15broad` — identical cost (`11`) on every fixture, and the broader one only duplicates the B1 absence rule. |
| TYPE-11-DR-16 | Is there a **sixth** under-recording channel? Every review round so far has found one more (absence, warm inner, in-progress inner, rescued global), which makes "the list is closed" the weaker prior. Enumerate the memoized doors and early returns systematically rather than waiting for the next review. **Two grounded candidates already**: (a) **`resolveModule` has V2's shape and V2's fix does not cover it** — `resolveModuleCandidates` (`lang/path/LuaModuleFileResolver.kt:26-49`) yields **project source-path** candidates before index-found ones and `doResolveModule` takes the first that types, so a module answered by a library today can be out-ranked by a project file created later, and `reportRescuedGlobal` is scoped to `resolveGlobal` only; (b) `resolveModule` has **no dumb-mode guard** at all (only `:84` and `:141` do), so §3.4's "a dumb build records zero sources" is false for any library file containing `require` — see §1.9 B5, where the general claim is already flagged as narrower than it reads | Risk 1.1, TYPE-11-06 | todo |
| TYPE-11-DR-18 | Price the module-absence rule (§3.1 step 5d, §1.12) in the same `REVIEW-COST` form every other absence rule was priced in: how many of the 11 provisioned files lose their pin when `resolveModule`'s `ANY` fall-through records an absence? Expected **zero** — nothing shipped `require`s — but expected is not measured, and this is the one rule adopted on correctness alone | §1.12, TYPE-11-06 | todo, Phase 2 |
| TYPE-11-DR-17 | ~~Is Risk 1.1b reachable through the shipped fetcher?~~ **Answered by tracing, not by a spike: it is not** — `isCached` short-circuits before `fetch`, `cacheDir` is `<id>-<version>`, and no delete-and-refetch path exists. Reduced to a standing note on Risk 1.1b: re-open if such a path is ever added. Kept as a row so the reasoning is not rediscovered. Was: is Risk 1.1b reachable through the shipped fetcher? Replace a definition library's content in place under its existing `<id>-<version>` root, with a pinned snapshot already built, and see whether anything ticks. If it is reachable, pick between accepting it as documented scope, a content-level signal, or a fetch stamp that always changes the root | Risk 1.1b, TYPE-11-02 | todo |

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
  should add it as its own live-fixture class `TypeElevenPinnableCostTest` (**not** alongside `LuaTypeSourceRecorderCoverageTest`, which reads text and cannot build fixtures — `design.md` TC-15).
- **No multi-project test.** `LuaLibraryProvenance` is per project and the definition cache is per
  **user**; two projects enabling the same library share one tree. Untested here.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Parent of the measurement discipline used here: `docs/features/completion/09-member-enumeration/`
