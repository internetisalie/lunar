---
id: "TYPE-11-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# TYPE-11: Implementation Plan

The de-risking already built and measured this change once (`design.md` §1). The phases below rebuild
it in a reviewable order; the measurement scaffold itself was reverted and is **not** the starting
point — Phase 1 starts from `main`.

**Standing rule for every phase**: the harnesses in `src/test/kotlin/net/internetisalie/lunar/type/`
are already committed and green on `main`. They must stay green at the end of every phase, and
`TypeElevenDr01ResidualTest` in particular must never be made to pass by relaxing an assertion.

## Phases

### Phase 1: Provenance and the recorder, wired to nothing [Must]

- **Goal**: both new components exist, are tested, and change no behaviour. `forFile` is untouched, so
  the suite result is bit-identical to `main`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder` — realizes design §2.1
        and §3.1. `object`, `ThreadLocal<ArrayDeque<SourceFrame>>`, where `SourceFrame` carries **five**
        sets (`urls`, `absences`, `unreplayedWarm`, `inProgressHits`, `rescuedGlobals`) and an
        `absorb` over all five; plus `snapshotFrames`, a **`Collections.synchronizedMap(WeakHashMap())`**
        used by §3.7 — synchronized like its three siblings (`LuaTypeManagerImpl.kt:39`, `:51`, `:63`),
        **not** a bare `WeakHashMap`: a read action is shared, not exclusive, so pooled threads reach
        it concurrently (design §2.1). Functions: `recording`, `report`, `reportFile`, `reportAbsence`,
        `reportRescuedGlobal`, `reportInProgressHit`, `reportWarmSnapshot`, `replay`, `depth`.
        **Every `report*` writes to every open frame, not just the innermost** (§3.1 step 3) — that
        is the whole correctness of nesting. The four non-`urls` sets are not decoration: each exists
        for a shape that was **measured shipping a stale type** — `absences` and `unreplayedWarm` in
        design §1.8, `inProgressHits` and `rescuedGlobals` in §1.10.
        **Built 2026-08-11**, with `reportUnreplayableHit` (§2.1, §3.6) included — this task list
        omitted it from its "Functions:" enumeration while §2.1 specifies it, and §3.6's drift guard
        has no other entry point.
        **Gated by `LuaTypeSourceRecorderTest` — 12 tests**, one stated mutation each, all recorded in
        `risks-and-gaps.md`'s ledger. ⚠ **An earlier version of this bullet said the opposite** —
        "nothing calls the object yet, so it carries no Phase 1 test: its first assertion arrives with
        its first caller in Phase 2" — and that rationale is **overturned, by a defect it let through**:
        both conservative markers shipped a `?: return` that granted the pin they exist to deny
        (review of `1be7cc0d`, F1), caught afterwards by five-line assertions needing no caller at
        all. Nine of the object's members are a plain Kotlin `object` with no `Project` and no PSI;
        "untestable until Phase 2" was never true of them.
  - [x] Create `net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance` — realizes design §2.2
        and §3.2. Light `@Service(Service.Level.PROJECT)`; **no `plugin.xml` entry** (design §7).
        Root list memoized via `CachedValuesManager.getManager(project).getCachedValue(project) { … }`
        with `ProjectRootModificationTracker` + `targetModificationTracker` as dependencies.
        Match on `psiFile.originalFile.virtualFile?.url` with the `url == root || url.startsWith("$root/")`
        prefix test. **Built 2026-08-11.**
  - [x] Add `LuaLibraryProvenanceTest` — **this is TYPE-11-03's gate, and the only one.** The five
        DR-02 facts asserted against the production service. `TypeElevenDr02ProvenanceTest` defines
        its predicate *inside the test file* and matches by `VfsUtilCore.isAncestor` where §3.2
        specifies URL-prefix, so its five ledger mutations all mutated a **replica** — no defect in
        `LuaLibraryProvenance` can turn them red. **Re-run each of those five mutations against the
        real service** and append the observed reds to the ledger; a row that is not re-earned here
        is not evidence for this requirement. Include TC-6 in the `PsiFile`-overload form (§8.1) —
        there is no `VirtualFile` overload and `copy.virtualFile` is null.
        **Built 2026-08-11**: **9 tests** — six at first commit, all six mutations re-earned against
        the production service and moved into `risks-and-gaps.md`'s ledger, plus three added over two
        remediation rounds. A sixth assertion was added beyond the five DR-02 facts — the `"$root/"`
        separator in the prefix test, which §3.2 calls out as required rather than cosmetic and which
        no DR-02 row covered. The seventh, eighth and ninth cover §3.2 step 1's **memoization** and
        its **dependency set**, which the first six could not see at all: they read the root list only
        after `setUp`'s blanket roots tick, so `ModificationTracker.NEVER_CHANGED` left every one of
        them green (review of `1be7cc0d`, F3). Each dependency is gated by its own assertion under its
        own **single-member** mutation — `…AfterARootsTick` drops `:66`, `…AfterATargetTick` drops
        `:67` — because the conjunction "both dependencies replaced" that the first remediation used
        cannot attribute the red to either member, and measured, `:67` alone was ungated.
- **Exit criteria**: full suite green, with **every test that existed on `main` still passing** —
  the count necessarily rises by this phase's new class, so "same count as `main`" (the earlier
  wording) was unsatisfiable by a phase whose own task list adds one; `LuaLibraryProvenanceTest` green;
  each of its assertions shown red by the mutation named for it in `risks-and-gaps.md`'s ledger.

### Phase 2: Report from the type manager [Must]

- **Goal**: every cross-file consumption is recorded and replayed from cache. Still no behaviour
  change — nothing reads the recorded set yet.
- **Tasks**:
  - [x] ~~Add `sourceCache` to `LuaTypeManagerImpl`~~ / ~~add `recordUnder(key, body)` and
        `replaySources(key)`~~ — **superseded by the co-location task below**, which is the shape
        that shipped. The two bullets are struck rather than deleted because the drift rule they
        carried is what the co-located shape *removes the state for*, and that is the reason to
        prefer it. What shipped instead: a `private data class CachedAnswer(resolvedType,
        sourceFrame)`, the three existing caches re-typed to `MutableMap<String, CachedAnswer>`, and
        one helper `recordInto(cache, key, body)` (3 args, at the cap).
  - [x] **Co-locate the frame in the three existing caches** — `(LuaType?, SourceFrame)` as their
        value type — instead of adding `sourceCache` as a fourth (design §9). **Taken; it landed
        cleanly.** It deleted the fourth `CachedValue`, the map-key prefix scheme, `replaySources`,
        the drift check, `reportUnreplayableHit` (§2.1 — its only reachable input was the drift
        state, and §3.6's own instruction was "give it a fixture or delete it") and Risk 1.2. Cost:
        the three declared types, plus hoisting `doResolveType`'s four `typeCache.value[name] = …`
        writes into `recordInto` — each stored the value it was about to return under the same key,
        so the hoist is behaviour-preserving, and it aligns that door with the other two, which
        already wrote through the local map rather than re-reading `.value`.
  - [x] Wrap the three doors — `resolveType`, `resolveModule`, `resolveGlobal` — so a cache **miss**
        goes through `recordInto(cache, key)` and a cache **hit** calls
        `LuaTypeSourceRecorder.replay(entry.sourceFrame)` before returning. Do not move the early
        returns that precede each cache read. **Done, and none were moved**: the primitive check and
        dumb-mode guard in `resolveType`, the dumb-mode guard in `resolveGlobal` and both reentrancy
        guards return exactly where they did.
  - [x] In `resolveGlobal` **only**, call `LuaTypeSourceRecorder.reportAbsence("global:$name")` on
        every path that yields `null` — a computed null (inside the `recordInto` body, so it is
        stored and replays), ~~a cache hit on a stored null~~ and the reentrancy guard. **The
        cache-hit report was dropped**: co-location makes it strictly redundant with the replay (an
        entry cannot exist without its frame), and two sufficient mechanisms for one behaviour is
        precisely what the Phase 1 review rejected in `de60eb83` — with both present no mutation can
        attribute the absence to either. Do **not** do this for `resolveType`/`resolveModule`:
        measured, that costs `io.lua` its pin for `resolveType("boolean|nil")`-shaped misses and buys
        nothing (design §1.8).
  - [x] In `resolveModule`, call `LuaTypeSourceRecorder.reportAbsence("module:$moduleName")` on the
        two paths that yield `LuaPrimitiveType.ANY` — `doResolveModule` falling through when no
        candidate types and the reentrancy guard — §3.1 step 5d, §1.12. **Priced (DR-18)**:
        `REVIEW-COST TOTALS provisioned=11 withModuleRule=11 withoutModuleRule=11` — **zero** lost
        pins, now measured rather than expected. Both columns came from one run, because the
        `"module:"` key prefix lets the "without" verdict be computed by filtering the same frame.
  - [x] In `doResolveGlobal`, call `LuaTypeSourceRecorder.reportRescuedGlobal("global:$name")` when
        the **project-scope** pass returns null and the all-scope fallback then answers (§3.1 step 5b,
        §1.10 V2). Not when both answer null — step 5 already covers that, and the broader variant was
        measured to add nothing (`dr15broad` ties `dr15rescued` on every fixture tried).
  - [x] Insert the six `reportFile` calls listed in design §3.5, at the stated lines. `typeOfGlobalIn`
        gets `.onEach { LuaTypeSourceRecorder.reportFile(it) }` **after** the existing
        `.filter { it != exclude }` — reporting every file visited, not only the one that yields a
        type (§3.5's over-approximation rule).
  - [x] **Settle DR-19** — §3.1 step 4's undischarged premise, which `reportFile`'s `?: return` rests
        on and which `testReportFileWithNoUrlRecordsNothing` was cementing with a green test. Phase 2
        is the first point at which it can be run rather than reasoned, because it wires the six
        §3.5 sites. Outcome and the measured null rate: DR-19 in `risks-and-gaps.md`.
  - [x] **Add the Phase 2 assertions.** ⚠ This task list originally had none, on the goal statement's
        reasoning that the phase is inert. Inert means *no behaviour moves*, not *nothing is
        claimable*: a frame can be opened here exactly as `forFile` will open one, so "the doors
        record what they consume" is directly assertable, and every guard in this feature that
        shipped unasserted was later found unable to fail — four in Phase 1 alone.
        `LuaTypeManagerRecordingTest` — **6 tests**, one stated mutation each, observed reds in
        `risks-and-gaps.md`'s ledger: a `reportFile` site firing, the `resolveGlobal` absence path,
        the §1.10 V2 rescued-global path, the §1.12 module-absence path, and both halves of §3.6's
        replay (a memoized answer's sources, and a memoized *absence*).
- **Exit criteria**: full suite green, with **every test that existed at `7773984a` still passing**.
  ⚠ The earlier wording, "the same count as Phase 1", is the same unsatisfiable gate the review
  removed from Phase 1's criterion — this phase adds a test class, so the count necessarily rises.
  Recording is inert while no frame is open, so an unchanged *result* is the expected outcome and any
  movement is a defect. **The corpus sweep is a gate for this phase** (it was not for Phase 1):
  `LuaTypeManagerImpl`'s resolution paths are edited, so inferred types can move, and
  `test -PwithCorpus` is what would say so.

### Phase 3: Make `forFile` conditional [Must]

- **Goal**: the feature. `TypeElevenDr04LatencyTest` arm B drops by an order of magnitude and
  `TypeElevenDr01ResidualTest` stays green. **DONE (2026-08-11)** — arm A median 6 287 µs, arm B
  22 859 µs (unpinned arm B was 349 700 µs); `TYPE11-COST provisioned=11 pinnable=11`; full suite
  and corpus sweep green. Two tasks were added mid-phase by measurement, both recorded below.
- **Tasks**:
  - [x] Edit `LuaTypesSnapshot.forFile` (`LuaTypes.kt:212-224`) — realizes design §2.3, §3.3 and
        §3.7. Wrap `LuaTypesVisitor.buildSnapshot(psiFile)` in `LuaTypeSourceRecorder.recording { … }`,
        register `snapshotFrames[snapshot] = frame`, compute `pinnable` by the seven short-circuiting
        tests in §3.3 steps 1–7, and select the churn dependency in step 9. **Steps 1–8 go in
        `internal fun isPinnable(psiFile: PsiFile, frame: SourceFrame): Boolean`, not inline** — step 9
        is its only caller. That extraction is what TC-16 asserts against; inlined, the dumb-mode guard
        has no assertion that goes red when it is deleted (design §1.9 B5). Track whether the provider
        ran (`var computed`) and, when it did not and `depth() > 0`, call
        `reportWarmSnapshot(psiFile, served)` (§3.7 steps 2–4).
  - [x] **The `inProgressSnapshot` early return reports before it returns** — `reportInProgressHit(psiFile)`
        into every open frame when it answers non-null at `depth() > 0` (§3.1 step 5c, §3.7, §1.10 V1).
        Keep the early return: it is the cycle-breaker. Nothing can be replayed here — the served
        snapshot is mid-build — so §3.3 step 6 makes the outer file unpinnable instead. It must stay
        **before** the warm-hit reporting; `psiFile` and `targetModificationTracker` are unchanged.
  - [x] Add `TypeElevenDr11LateDeclarationTest` and `TypeElevenDr12WarmInnerSnapshotTest` to the
        standing-green set — covers TYPE-11-06. **Already committed and green on `main`**; each was
        measured red under the rule without its guard (design §1.8), which is what makes them gates
        rather than decoration.
  - [x] Add `TypeElevenPinSurvivesUnrelatedEditTest` — **TYPE-11-01's gate**, TC-1. Snapshot
        *instance identity* for a library file across an unrelated project edit. It is **red on `main`
        today**, which is the point: it is the only assertion that states what this feature does.
        `TypeElevenDr04LatencyTest` stays a printing probe with no assertions and does not gate
        anything (§1.5, TC-1b).
  - [x] Add `TypeElevenGenerationSignalTest` — covers TYPE-11-02, TC-2a/2b/3/4. Four cases, and the
        split between (a) and (b) is deliberate: one asserts a tick moves the pin, the other asserts
        the production chain produces a tick. Merged, the test fails for two unrelated reasons.
        **(a) TC-2a — assert the decision, not the outcome.** Build `forFile(wx.lua)` for an installed,
        enabled library, read its frame from `snapshotFrames`, assert `isPinnable` is `true`, and assert
        `churnDependencyFor(wx.lua, frame)` **is** `ProjectRootModificationTracker.getInstance(project)`
        by identity. Mutations, both red: `generationTracker()` → `NEVER_CHANGED`, and step 9 → always
        `MODIFICATION_COUNT`. ⚠ **Do not write the outcome form.** "Tick roots, assert the snapshot
        rebuilt" is green on `main`, under any rule, and with the pin deleted: `makeRootsChange` fires
        `propertyChanged(PROP_ROOTS)` and `canAffectPsi` admits it, so every roots tick is also a
        `MODIFICATION_COUNT` tick (design §1.11). Two earlier forms of this case died that way; holding
        PSI still does not help, because the roots tick *is* the PSI event.
        **(e) TC-2c — the wiring, and the case that must not be dropped again.** In the TC-2a fixture:
        take instance `A`, announce a roots change, take `B`, then edit an unrelated project file and
        take `C`. Assert `A !== B` **and** `B === C`. ⚠ `A !== B` alone is green on `main`; the pair is
        what gates. ⚠ **This is the only assertion that `forFile` passes the churn object into
        `Result.create` at all** — a pinnable branch that omits it, with `churnDependencyFor` correct,
        passes every other case in this plan and ships every library snapshot stale on any roots change
        (design §1.11).
        **(b) TC-2b — the production chain**, closing Gap 2.3: seed both trees with **L1 alone enabled**,
        call `LuaSettingsChangeListener.getInstance(project)` **first**, then
        `LuaDefinitionLibraryEnabler.apply(listOf(L1, L2))` — a list that **differs** from the stored
        one — pump the EDT, and assert `ProjectRootModificationTracker` advanced.
        ⚠ **Re-applying the same list publishes nothing**: `apply` delegates to
        `setEnabledDefinitionLibrariesAndNotify`, which early-returns when the normalized list equals
        the stored one (`LuaProjectSettings.kt:184-188`), and the enabler's second notify route only
        runs when a fetch occurred — which a seeded tree avoids. Getting this wrong makes the case red
        against a **correct** implementation. ⚠ **Without the explicit `getInstance` the publish reaches no subscriber**: the
        listener subscribes in its `init` and is normally created by the `LuaTargetSyncStartup`
        post-startup activity, which does not run under `BasePlatformTestCase` — so this case would be
        red against a *correct* implementation. `LuaSettingsNotificationTest.kt:47` forces it for the
        same reason. `apply()` on an unseeded tree would attempt a network fetch.
        **(c) TC-3** — pin a **definition-library** file (target-independent root; a
        `runtime/standard/lua-5.4/*` file stops being provisioned when the target moves), tick the
        target via **`LuaProjectSettings.getInstance(project).state.setTarget(...)`**, assert the
        instance changed. Mutation: drop `targetTracker` from the **pinnable** branch only.
        ⚠ **Not `setTargetAndNotify`**, despite its KDoc telling production callers to prefer it: it
        also publishes → `PlatformLibraryIndex.reload()` → `makeRootsChange`, invalidating through the
        **roots** tracker regardless of `targetTracker`, so the mutation could not fire.
        **(d) TC-4** — a project edit does not tick roots. Cheap regression check, **explicitly not a
        gate**: it is a platform fact no TYPE-11 defect can change and `rewriteAssertingRootsAreStill`
        already asserts it on every edit.
  - [x] Add `TypeElevenPinnableCostTest` — TC-15, and it is **its own live-fixture class**, not an
        assertion inside the text-reading coverage test. Enumerate the bundled stdlib via
        `RuntimeLibraryProvider(project).getLibraryFiles(target)` plus the installed definition
        library root, build `forFile` on each in one clean epoch, read each frame from
        `snapshotFrames`, and assert **all 11 pinnable**. Assert the enumerated count (`11`) first or
        a fixture that found zero files passes vacuously. This is the only thing separating "closed
        TYPE-11-06" from "pinned nothing".
  - [x] Add `TypeElevenDr14InProgressTest` and `TypeElevenDr15LateLibraryAnswerTest` to the
        standing-green set — TYPE-11-06's third and fourth channels, TC-18 and TC-19. **Already
        committed and green on `main`**; each was measured red under the post-B1/B4 rule without its
        guard (design §1.10), which is what makes them gates rather than decoration.
        ⚠ **Measure them one class at a time.** A combined run turned DR-14 green for an unrelated
        reason — an earlier class's teardown edit recomputing the chain — and that false green is
        recorded in `risks-and-gaps.md`. The full suite remains the commit gate.
  - [x] Add `TypeElevenDumbModeDecisionTest` — covers TYPE-11-05, TC-16. **Three** assertions on the
        TC-12 fixture: (a) inside dumb mode `isPinnable(delta.lua, SourceFrame())` is `false`; (b)
        inside dumb mode the frame a real `forFile(delta.lua)` registers is present (`assertNotNull`
        first) and empty; (c) a **different** library file `epsilon.lua` (`libSmart = sharedByLibrary`),
        built **only** in smart mode, whose frame is **non-empty**.
        ⚠ **(c) must not reuse `delta.lua`.** `snapshotFrames` is keyed on the snapshot instance and
        nothing between (b) and (c) invalidates it, so a same-file (c) reads (b)'s dumb, empty frame
        and is **red against a correct implementation** — its outcome decided by the unresolved
        dumb-exit `modificationStamp` question (design §1.6, Gap 2.1) rather than by the recorder.
        ⚠ **(c) is what makes (b) mean anything**: "every set empty" passes under a completely inert
        recorder — a Phase-1 recorder wired to nothing with all six §3.5 `reportFile` calls omitted
        satisfies it. (b) alone separates "the provider ran" from "it did not"; only (b)+(c) separate
        "dumb mode records nothing" from "nothing is ever recorded".
        ⚠ (b)'s emptiness does **not** depend on the target: `seedAmbientGlobals` has no `reportFile`
        site in §3.5 under any target, so `global.lua` contributes nothing anywhere. An earlier draft
        of this bullet demanded a default-target assertion, which guarded a mechanism that does not
        exist.
        **Stated mutation: delete §3.3 step 1** → the first assertion must go red (an empty frame on a
        provisioned file clears steps 2–7, so step 1 is the sole rejector). This is the gate §1.6
        said did not exist; `TypeElevenDr05DumbModeTest` stays a recorder and is not a gate.
  - [x] Add `LuaTypeSourceRecorderCoverageTest` — mitigates `risks-and-gaps.md` Risk 1.1, TC-17. Reads
        **`LuaTypeManagerImpl.kt` and `LuaTypesVisitor.kt`** as text, **strips comments, then removes
        all whitespace**, then counts **five** bare members — `.findFile(`, `.getElements(`,
        `.getContainingFiles(`, `.getAllKeys(`, `.getLibraryFiles(` — against the recorded
        **`LuaTypeManagerImpl` 2 / 3 / 2 / 2 / 0** and **`LuaTypesVisitor` 1 / 0 / 0 / 0 / 1**, with a
        message naming design §3.5. **Three members and one file is the pre-widening form and is not
        sufficient**: it cannot fire for `StubIndex.getAllKeys` (`LuaTypeManagerImpl.kt:432`, the route
        §1.7 designates load-bearing for residual path 2) nor for `seedAmbientGlobals`, which reads
        another file entirely. A new site fails the build and forces the
        author to decide whether it needs a `reportFile`.
        **Do not match the qualified chains** — measured against the real file (design §1.9 B3),
        `FileBasedIndex.getInstance().getContainingFiles` counts **0** because ktlint wraps both sites
        across lines, and `PsiManager.getInstance(project).findFile` counts **1** of 2 because `:358`
        calls through a `psiManager` local. Comment stripping changes nothing today (measured, same
        `2 / 3 / 2` either way) and is specified only to stop a future KDoc writing `.findFile(…)` in
        prose from failing a build that added no call site.
        **Stated mutation: inject one `PsiManager.getInstance(project).findFile(…)`** → `.findFile(`
        must go `2 → 3` and fail. Second check, to prove the guard is not fooled by formatting alone:
        re-wrapping an existing `StubIndex.getElements(` across lines must leave the count at `3`.
  - [x] Add `TypeElevenDr18ModuleAbsenceTest` — TC-20, TYPE-11-06's fifth channel. Library
        `mu.lua` = `muAlias = require("mymod")` with **nothing providing `mymod`** (assert
        `resolveModuleCandidates` is empty first), read it, then create the module and assert the type
        follows, roots tracker still. Red under §3 without step 5d (design §1.12).
  - [x] Add a library-`require`s-a-project-module case to `TypeElevenDr01ResidualTest` — closes the
        first item under `risks-and-gaps.md` "Test Case Gaps".
  - [x] **Added mid-phase — `internal fun dependenciesFor(psiFile, frame): Array<Any>` (§2.3) and
        `TypeElevenGenerationSignalTest` case (f), TC-2d.** TC-2c was run against a build whose
        pinnable branch omits the churn object from `Result.create` — the mutation §1.11 says only
        TC-2c can catch — and **passed**. A roots change moves the library `PsiFile`'s own
        `modificationStamp` (probed `0 -> 1`, same instance) and `psiFile` is a dependency in both
        branches, so the snapshot rebuilds either way. `forFile` now spreads one named dependency
        array and TC-2d asserts its three members by identity; red under that mutation and under
        TC-3's.
  - [x] **Added mid-phase — `TypeElevenIncompleteFrameDecisionTest` (7 tests) and
        `LuaTypeManagerRecordingTest.testResolvingAModuleRecordsTheFileItWasReadFrom`.** Re-earning
        the four TYPE-11-06 channels against the shipped build instead of the scaffold showed three
        of them green with their own guard deleted: **step 7 subsumes steps 5 and 6 and both module
        rules**, because every cross-library global reference is a rescued global and `require` is
        itself one. The guards are gated on the decision (one non-empty set at a time, one red per
        clause) and on the report (a warm hit really replays; a real cycle really marks
        `inProgressHits`; the module door really records its file).
  - [x] **Post-review remediation (2026-08-11)** — F1: `providerRan` deleted from `forFile`, §3.7
        step 2 rewritten (the platform discards every later call's provider, so the flag was the warm
        branch unconditionally); `TypeElevenWarmSignalMechanismTest` (2 tests) measures both the
        platform's provider identity and the cold-path replay being a set-wise no-op. F2: §2.3's
        "same edit" claim corrected to a convention, and closed anyway by **TC-2e**, which reads the
        stored `CachedValue`'s dependency items. F4: the DR-14 attribution was checked against the
        frame and **stands** — step 4 is not a second rejector there — now asserted by
        `TypeElevenDr14InProgressTest.testTheInProgressFixtureIsRejectedByTheRescuedGlobalAndNotByAnAbsence`.
- **Exit criteria**:
  - `tooling/gce-builder/gce-builder.sh run "ktlintCheck lintDocs test --rerun --no-build-cache"` —
    BUILD SUCCESSFUL, 0 failures.
  - the **corpus sweep run explicitly** — `run "test -PwithCorpus --rerun --no-build-cache"` — green,
    with **`LuaCorpusSweepTest`, `LuaTortureCorpusTest` and `LuaInspectionParityTest`** present in
    `build/test-results/test/` **and timestamped after the run started**. Those three are the only
    classes the `-PwithCorpus` filter gates (`design.md` §1.4 ⚠⚠); `BaselineRatchetTest`,
    `LexerInvariantsTest` and `ParseOracleTest` run in the routine loop and prove nothing about the
    sweep, and `--rerun` does **not** clear the results directory, so a stale XML from the previous
    run reads as a pass. Reference on `69ad6b57`: 2 571 tests, 0 failures. Do **not** substitute
    `git status --short src/test/resources/corpus/` — it is empty whether the sweep passed, regressed
    or never ran (`build.gradle.kts:286-288`).
  - `TypeElevenPinSurvivesUnrelatedEditTest` green — this is TYPE-11-01's actual exit condition.
  - `TypeElevenDr04LatencyTest`'s printed medians **read and recorded** in the phase report. ⚠ This
    was previously written as "arm B median at least 5× below the `main` figure measured in the same
    run as its own arm A", which is **unsatisfiable**: a `main` figure cannot be produced by a
    post-change run, and §1.5 forbids the cross-build ratio it asks for. It is a probe; the number is
    evidence of value, not a pass/fail gate, and no threshold on it belongs in an exit criterion.
  - **The pin still pays for itself**: all 10 bundled stdlib files and the 123 KiB definition library
    are judged pinnable in one clean epoch (design §1.8, `guarded=11`). A rule that closes
    TYPE-11-06 by pinning nothing is not a fix, and the only way to tell them apart is to count.
  - Every new assertion shown red under a stated mutation, appended to the `risks-and-gaps.md` ledger.

### Phase 4: Close the negative de-risking results [Should]

- **Goal**: the two things Phase 3 ships without proof stop being unproven.
- **Tasks**:
  - [ ] TYPE-11-DR-06 — decide whether the dumb-mode guard is dead code or unprotected production
        behaviour, and either delete it or build a fixture that reproduces the staleness.
  - [ ] TYPE-11-DR-07 — probe the lazy `LuaTypeReference` escape (Risk 1.3).
- **Exit criteria**: `risks-and-gaps.md` Gap 2.1 and Risk 1.3 both closed with pasted output, or
  re-filed as bugs with roadmap rows.

### Phase 5: The remaining arm-B cost [Could]

- **Goal**: understand, not necessarily fix, the 3–5× that survives Phase 3.
- **Tasks**:
  - [ ] TYPE-11-DR-08 — profile `resolveGlobal` + `graphTypeToLuaType` over a 3 600-member table and
        decide whether a `visited`-map reuse or a member-set cache is a separate feature.
- **Exit criteria**: a measured recommendation in `risks-and-gaps.md`, or a new feature filed.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-- | :-- |
| TYPE-11-01 — a platform-library snapshot survives an unrelated edit | M | Phase 3 — gated by `TypeElevenPinSurvivesUnrelatedEditTest` (TC-1, instance identity across an unrelated edit; red on `main`). `TypeElevenDr04LatencyTest` is a probe with no assertions and gates nothing. |
| TYPE-11-02 — every generation signal invalidates it | M | Phase 1 (the tracker composition) + Phase 3 (`TypeElevenGenerationSignalTest`) |
| TYPE-11-03 — identification is by provenance | M | Phase 1 — gated by `LuaLibraryProvenanceTest` against the production service, with all five DR-02 mutations **re-earned** there; `TypeElevenDr02ProvenanceTest` mutates a test-local replica and is de-risking, not acceptance. |
| TYPE-11-04 — no new stale-type defect | M | Phase 2 (recording) + Phase 3 (the condition); gated by **`TypeElevenDr01ResidualTest` alone** — measured (TYPE-11-DR-09): the full suite and all four corpus baselines pass unchanged under the rejected blanket-pin build, so neither is a gate for this requirement. They remain exit criteria for "nothing else moved". |
| TYPE-11-05 — a dumb-mode build is never cached across the generation | M | Phase 3 (the guard, design §3.4) — gated by `TypeElevenDumbModeDecisionTest` on the **decision** (TC-16), which is mutation-red when §3.3 step 1 is deleted. The **outcome** still does not reproduce (§1.6), so Phase 4 keeps DR-06: is the `modificationStamp` move platform behaviour or a `DumbModeTestUtils` artifact? |
| TYPE-11-06 — an incomplete recording is never pinned | M | Phase 1 (the `SourceFrame` shape, five sets) + Phase 2 (the absence and rescued-global reports, the whole-frame `sourceCache`) + Phase 3 (§3.3 steps 4–7, the §3.7 replay, the in-progress report); gated by `TypeElevenDr11LateDeclarationTest`, `TypeElevenDr12WarmInnerSnapshotTest`, `TypeElevenDr14InProgressTest` and `TypeElevenDr15LateLibraryAnswerTest` — all four measured red without their guard, all four fixes at zero lost pins |

## Verification Tasks

- [x] `TypeElevenDr01ResidualTest` — 3 tests, covers TYPE-11-04. **Already committed and green on
      `main`**; it must stay green through every phase.
- [x] `TypeElevenDr02ProvenanceTest` — 5 tests, covers TYPE-11-03. Already committed.
- [x] `TypeElevenDr11LateDeclarationTest` — 1 test, covers TYPE-11-06 (absence). Already committed
      and green on `main`; red under the rule without §3.3 step 4 (design §1.8).
- [x] `TypeElevenDr12WarmInnerSnapshotTest` — 1 test, covers TYPE-11-06 (warm inner snapshot).
      Already committed and green on `main`; red under the rule without §3.7 (design §1.8).
- [x] `TypeElevenDr05DumbModeTest` — 2 tests, TYPE-11-05. Already committed, and **explicitly not a
      gate** (`risks-and-gaps.md` Gap 2.1): it records the outcome, which two mutations could not move.
- [x] `TypeElevenDumbModeDecisionTest` (Phase 3) — the gate for TYPE-11-05, TC-16. Asserts the
      decision rather than the outcome, plus that a real dumb build's frame is empty.
- [x] `TypeElevenDr04LatencyTest` — printing probe, **no assertions**. Read its numbers; do not treat
      a green run as a pass, and do not cite it as TYPE-11-01's acceptance — `TypeElevenPinSurvivesUnrelatedEditTest`
      is that (TC-1).
- [x] `TypeElevenPinSurvivesUnrelatedEditTest` (Phase 3) — TYPE-11-01's gate, TC-1.
- [x] `TypeElevenPinnableCostTest` (Phase 3) — TC-15, the 11-of-11 pinnable count.
- [x] `LuaLibraryProvenanceTest` (Phase 1) — **9 tests**: the DR-02 assertions against the production
      service, the `"$root/"` separator, and one assertion per memoized dependency.
- [x] `LuaTypeSourceRecorderTest` (Phase 1) — **12 tests**, the recorder's own algebra asserted
      directly, covering 11 of its 12 members (`snapshotFrames` is exercised as a collaborator).
      Phase 2 deleted `reportUnreplayableHit` with the drift state it guarded, so the class now
      covers 10 of 11 members; no test method was removed, because the two that named it also
      asserted three other markers.
- [x] `LuaTypeManagerRecordingTest` (Phase 2) — **6 tests**, "the doors record what they consume":
      a §3.5 `reportFile` site firing, the `resolveGlobal` absence (§1.8 B1), the rescued global
      (§1.10 V2), the module absence (§1.12), and §3.6's replay for both a memoized answer's sources
      and a memoized absence.
- [x] `TypeElevenGenerationSignalTest` (Phase 3) — covers TYPE-11-02.
- [x] `TypeElevenDr14InProgressTest` — 2 tests, TYPE-11-06 (in-progress inner). Already committed;
      red under the post-B1/B4 rule without §3.3 step 6 (design §1.10 V1).
- [x] `TypeElevenDr15LateLibraryAnswerTest` — 1 test, TYPE-11-06 (rescued global). Already committed;
      red under the post-B1/B4 rule without §3.3 step 7 (design §1.10 V2).
- [x] `LuaTypeSourceRecorderCoverageTest` (Phase 3) — mitigates Risk 1.1, TC-17. **Two files, five
      members**: `LuaTypeManagerImpl` `2 / 3 / 2 / 2 / 0`, `LuaTypesVisitor` `1 / 0 / 0 / 0 / 1`, on
      whitespace-collapsed, comment-stripped text. The qualified-chain form counted `1 / 3 / 0` (§1.9 B3).
- [ ] Run `human-verification-checklists.md` — the whole feature is a *cache lifetime* change, and the
      symptom it fixes (typing latency with a large library loaded) is not observable in a light
      fixture at all.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Provenance and the recorder, wired to nothing | done | Must |
| Phase 2: Report from the type manager | done | Must |
| Phase 3: Make `forFile` conditional | done | Must |
| Phase 4: Close the negative de-risking results | todo | Should |
| Phase 5: The remaining arm-B cost | todo | Could |
