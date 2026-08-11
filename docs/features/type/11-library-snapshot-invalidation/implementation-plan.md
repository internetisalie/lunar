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
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder` — realizes design §2.1
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
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance` — realizes design §2.2
        and §3.2. Light `@Service(Service.Level.PROJECT)`; **no `plugin.xml` entry** (design §7).
        Root list memoized via `CachedValuesManager.getManager(project).getCachedValue(project) { … }`
        with `ProjectRootModificationTracker` + `targetModificationTracker` as dependencies.
        Match on `psiFile.originalFile.virtualFile?.url` with the `url == root || url.startsWith("$root/")`
        prefix test.
  - [ ] Add `LuaLibraryProvenanceTest` — **this is TYPE-11-03's gate, and the only one.** The five
        DR-02 facts asserted against the production service. `TypeElevenDr02ProvenanceTest` defines
        its predicate *inside the test file* and matches by `VfsUtilCore.isAncestor` where §3.2
        specifies URL-prefix, so its five ledger mutations all mutated a **replica** — no defect in
        `LuaLibraryProvenance` can turn them red. **Re-run each of those five mutations against the
        real service** and append the observed reds to the ledger; a row that is not re-earned here
        is not evidence for this requirement. Include TC-6 in the `PsiFile`-overload form (§8.1) —
        there is no `VirtualFile` overload and `copy.virtualFile` is null.
- **Exit criteria**: full suite green, with **every test that existed on `main` still passing** —
  the count necessarily rises by this phase's new class, so "same count as `main`" (the earlier
  wording) was unsatisfiable by a phase whose own task list adds one; `LuaLibraryProvenanceTest` green;
  each of its assertions shown red by the mutation named for it in `risks-and-gaps.md`'s ledger.

### Phase 2: Report from the type manager [Must]

- **Goal**: every cross-file consumption is recorded and replayed from cache. Still no behaviour
  change — nothing reads the recorded set yet.
- **Tasks**:
  - [ ] Add `sourceCache` to `LuaTypeManagerImpl` immediately after `globalCache` — realizes design
        §3.6 step 1. Same `createCachedValue(…, false)` shape, same
        `PsiModificationTracker.getInstance(project)` dependency, same `synchronizedMap`. The value
        type is `MutableMap<String, LuaTypeSourceRecorder.SourceFrame>` — the **whole frame**, not a
        URL set, or a memoized null replays as "no dependencies" (design §3.6).
  - [ ] Add the private helpers `recordUnder(key, body)` and `replaySources(key)` — design §3.6
        steps 3–4. Both are ≤ 4 lines; the ≤ 3-argument cap (engineering contract §3) is satisfied.
        `replaySources` must treat **"type cached but frame absent" as an incompleteness**, calling
        the string-keyed `reportUnreplayableHit(key)` (§2.1) — there is no `PsiFile` or served
        `LuaTypes` at a cache hit — which writes `unreplayedWarm` so §3.3 step 5 judges it, rather
        than passing over it in silence. **Only when the type was cached**: a cold first resolution
        has neither entry and must record normally, or every cold build becomes unpinnable — the four caches are separate
        `CachedValue`s read at different moments, and an absent frame otherwise replays as "no
        sources", which is §3.6's own unsound case re-created through the cache (design §3.6).
  - [ ] **Consider co-locating the frame in the three existing caches** — `(LuaType?, SourceFrame)` as
        their value type — instead of adding `sourceCache` as a fourth. It deletes the fourth
        `CachedValue`, the key-prefix scheme, the drift check above and Risk 1.2 (design §9). Take it
        if it lands cleanly; the parallel-map shape is precedent, not a reason.
  - [ ] Wrap the three doors — `resolveType`, `resolveModule`, `resolveGlobal` — so a cache **miss**
        goes through `recordUnder("type:$name" | "module:$moduleName" | "global:$name")` and a cache
        **hit** calls `replaySources(...)` before returning. Do not move the early returns that
        precede each cache read.
  - [ ] In `resolveGlobal` **only**, call `LuaTypeSourceRecorder.reportAbsence("global:$name")` on
        every path that yields `null` — a computed null (inside the `recordUnder` body, so it is
        stored and replays), a cache hit on a stored null, and the reentrancy guard. Do **not** do
        this for `resolveType`/`resolveModule`: measured, that costs `io.lua` its pin for
        `resolveType("boolean|nil")`-shaped misses and buys nothing (design §1.8).
  - [ ] In `resolveModule`, call `LuaTypeSourceRecorder.reportAbsence("module:$moduleName")` on the
        two paths that yield `LuaPrimitiveType.ANY` — `doResolveModule` falling through when no
        candidate types (`LuaTypeManagerImpl.kt:213-219`) and the reentrancy guard (`:116-118`) —
        §3.1 step 5d, §1.12. `ANY` is **non-null**, so without this a library file whose `require`
        resolves to nothing records an empty frame and is pinned. **Price it in the same run
        (DR-18)**: report the `REVIEW-COST` line with and without, as every other absence rule here
        was priced. Zero lost pins is expected, not established.
  - [ ] In `doResolveGlobal`, call `LuaTypeSourceRecorder.reportRescuedGlobal("global:$name")` when
        the **project-scope** pass returns null and the all-scope fallback then answers (§3.1 step 5b,
        §1.10 V2). Not when both answer null — step 5 already covers that, and the broader variant was
        measured to add nothing (`dr15broad` ties `dr15rescued` on every fixture tried).
  - [ ] Insert the six `reportFile` calls listed in design §3.5, at the stated lines. `typeOfGlobalIn`
        gets `.onEach { LuaTypeSourceRecorder.reportFile(it) }` **after** the existing
        `.filter { it != exclude }` — reporting every file visited, not only the one that yields a
        type (§3.5's over-approximation rule).
- **Exit criteria**: full suite green with the same count as Phase 1. Recording is inert while no
  frame is open, so an unchanged suite is the expected result and any movement is a defect.

### Phase 3: Make `forFile` conditional [Must]

- **Goal**: the feature. `TypeElevenDr04LatencyTest` arm B drops by an order of magnitude and
  `TypeElevenDr01ResidualTest` stays green.
- **Tasks**:
  - [ ] Edit `LuaTypesSnapshot.forFile` (`LuaTypes.kt:212-224`) — realizes design §2.3, §3.3 and
        §3.7. Wrap `LuaTypesVisitor.buildSnapshot(psiFile)` in `LuaTypeSourceRecorder.recording { … }`,
        register `snapshotFrames[snapshot] = frame`, compute `pinnable` by the seven short-circuiting
        tests in §3.3 steps 1–7, and select the churn dependency in step 9. **Steps 1–8 go in
        `internal fun isPinnable(psiFile: PsiFile, frame: SourceFrame): Boolean`, not inline** — step 9
        is its only caller. That extraction is what TC-16 asserts against; inlined, the dumb-mode guard
        has no assertion that goes red when it is deleted (design §1.9 B5). Track whether the provider
        ran (`var computed`) and, when it did not and `depth() > 0`, call
        `reportWarmSnapshot(psiFile, served)` (§3.7 steps 2–4).
  - [ ] **The `inProgressSnapshot` early return reports before it returns** — `reportInProgressHit(psiFile)`
        into every open frame when it answers non-null at `depth() > 0` (§3.1 step 5c, §3.7, §1.10 V1).
        Keep the early return: it is the cycle-breaker. Nothing can be replayed here — the served
        snapshot is mid-build — so §3.3 step 6 makes the outer file unpinnable instead. It must stay
        **before** the warm-hit reporting; `psiFile` and `targetModificationTracker` are unchanged.
  - [ ] Add `TypeElevenDr11LateDeclarationTest` and `TypeElevenDr12WarmInnerSnapshotTest` to the
        standing-green set — covers TYPE-11-06. **Already committed and green on `main`**; each was
        measured red under the rule without its guard (design §1.8), which is what makes them gates
        rather than decoration.
  - [ ] Add `TypeElevenPinSurvivesUnrelatedEditTest` — **TYPE-11-01's gate**, TC-1. Snapshot
        *instance identity* for a library file across an unrelated project edit. It is **red on `main`
        today**, which is the point: it is the only assertion that states what this feature does.
        `TypeElevenDr04LatencyTest` stays a printing probe with no assertions and does not gate
        anything (§1.5, TC-1b).
  - [ ] Add `TypeElevenGenerationSignalTest` — covers TYPE-11-02, TC-2a/2b/3/4. Four cases, and the
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
  - [ ] Add `TypeElevenPinnableCostTest` — TC-15, and it is **its own live-fixture class**, not an
        assertion inside the text-reading coverage test. Enumerate the bundled stdlib via
        `RuntimeLibraryProvider(project).getLibraryFiles(target)` plus the installed definition
        library root, build `forFile` on each in one clean epoch, read each frame from
        `snapshotFrames`, and assert **all 11 pinnable**. Assert the enumerated count (`11`) first or
        a fixture that found zero files passes vacuously. This is the only thing separating "closed
        TYPE-11-06" from "pinned nothing".
  - [ ] Add `TypeElevenDr14InProgressTest` and `TypeElevenDr15LateLibraryAnswerTest` to the
        standing-green set — TYPE-11-06's third and fourth channels, TC-18 and TC-19. **Already
        committed and green on `main`**; each was measured red under the post-B1/B4 rule without its
        guard (design §1.10), which is what makes them gates rather than decoration.
        ⚠ **Measure them one class at a time.** A combined run turned DR-14 green for an unrelated
        reason — an earlier class's teardown edit recomputing the chain — and that false green is
        recorded in `risks-and-gaps.md`. The full suite remains the commit gate.
  - [ ] Add `TypeElevenDumbModeDecisionTest` — covers TYPE-11-05, TC-16. **Three** assertions on the
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
  - [ ] Add `LuaTypeSourceRecorderCoverageTest` — mitigates `risks-and-gaps.md` Risk 1.1, TC-17. Reads
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
  - [ ] Add `TypeElevenDr18ModuleAbsenceTest` — TC-20, TYPE-11-06's fifth channel. Library
        `mu.lua` = `muAlias = require("mymod")` with **nothing providing `mymod`** (assert
        `resolveModuleCandidates` is empty first), read it, then create the module and assert the type
        follows, roots tracker still. Red under §3 without step 5d (design §1.12).
  - [ ] Add a library-`require`s-a-project-module case to `TypeElevenDr01ResidualTest` — closes the
        first item under `risks-and-gaps.md` "Test Case Gaps".
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

- [ ] `TypeElevenDr01ResidualTest` — 3 tests, covers TYPE-11-04. **Already committed and green on
      `main`**; it must stay green through every phase.
- [ ] `TypeElevenDr02ProvenanceTest` — 5 tests, covers TYPE-11-03. Already committed.
- [ ] `TypeElevenDr11LateDeclarationTest` — 1 test, covers TYPE-11-06 (absence). Already committed
      and green on `main`; red under the rule without §3.3 step 4 (design §1.8).
- [ ] `TypeElevenDr12WarmInnerSnapshotTest` — 1 test, covers TYPE-11-06 (warm inner snapshot).
      Already committed and green on `main`; red under the rule without §3.7 (design §1.8).
- [ ] `TypeElevenDr05DumbModeTest` — 2 tests, TYPE-11-05. Already committed, and **explicitly not a
      gate** (`risks-and-gaps.md` Gap 2.1): it records the outcome, which two mutations could not move.
- [ ] `TypeElevenDumbModeDecisionTest` (Phase 3) — the gate for TYPE-11-05, TC-16. Asserts the
      decision rather than the outcome, plus that a real dumb build's frame is empty.
- [ ] `TypeElevenDr04LatencyTest` — printing probe, **no assertions**. Read its numbers; do not treat
      a green run as a pass, and do not cite it as TYPE-11-01's acceptance — `TypeElevenPinSurvivesUnrelatedEditTest`
      is that (TC-1).
- [ ] `TypeElevenPinSurvivesUnrelatedEditTest` (Phase 3) — TYPE-11-01's gate, TC-1.
- [ ] `TypeElevenPinnableCostTest` (Phase 3) — TC-15, the 11-of-11 pinnable count.
- [ ] `LuaLibraryProvenanceTest` (Phase 1) — the DR-02 assertions against the production service.
- [ ] `TypeElevenGenerationSignalTest` (Phase 3) — covers TYPE-11-02.
- [ ] `TypeElevenDr14InProgressTest` — 2 tests, TYPE-11-06 (in-progress inner). Already committed;
      red under the post-B1/B4 rule without §3.3 step 6 (design §1.10 V1).
- [ ] `TypeElevenDr15LateLibraryAnswerTest` — 1 test, TYPE-11-06 (rescued global). Already committed;
      red under the post-B1/B4 rule without §3.3 step 7 (design §1.10 V2).
- [ ] `LuaTypeSourceRecorderCoverageTest` (Phase 3) — mitigates Risk 1.1, TC-17. **Two files, five
      members**: `LuaTypeManagerImpl` `2 / 3 / 2 / 2 / 0`, `LuaTypesVisitor` `1 / 0 / 0 / 0 / 1`, on
      whitespace-collapsed, comment-stripped text. The qualified-chain form counted `1 / 3 / 0` (§1.9 B3).
- [ ] Run `human-verification-checklists.md` — the whole feature is a *cache lifetime* change, and the
      symptom it fixes (typing latency with a large library loaded) is not observable in a light
      fixture at all.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Provenance and the recorder, wired to nothing | todo | Must |
| Phase 2: Report from the type manager | todo | Must |
| Phase 3: Make `forFile` conditional | todo | Must |
| Phase 4: Close the negative de-risking results | todo | Should |
| Phase 5: The remaining arm-B cost | todo | Could |
