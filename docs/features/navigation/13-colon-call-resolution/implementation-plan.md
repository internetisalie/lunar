---
id: NAVIGATION-13-PLAN
title: "13: Colon Call Site Resolution — Implementation Plan"
type: plan
parent_id: NAVIGATION-13
folders:
  - "[[features/navigation/13-colon-call-resolution/requirements|requirements]]"
---

# NAV-13: Implementation Plan

Each phase ends with the build green on the gce builder
(`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — `--rerun` is mandatory,
without it Gradle serves `:test` FROM-CACHE and the phase reports a pass having executed nothing).

**The branch and its guard ship together, in Phase 1.** The branch *without* the
`isSnapshotUnderConstruction` guard reddens the `LuaTypeGraphRootResolutionBudgetTest` methods that
predate this feature — measured, `WRITE` 812 and 814 against budgets of 600 and 620
(`risks-and-gaps.md` DR-02 Finding 4). Landing the branch first and the guard second would leave the
tree red between two commits, so Phase 1's exit criterion is the full suite green *including* those
methods at their existing budgets. Phase 3 is what *verifies* the guard, by executing its mutation;
it does not introduce it.

## Phases

### Phase 1: `LuaColonCallResolution` and the branch [Must]

- **Goal**: a colon call site resolves to its method declaration's IDENTIFIER leaf, and
  `ReferencesSearch` returns the usage set.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.psi.LuaColonCallResolution` — realizes design §2.1,
        with `isColonCallMemberName` (§3.1), `receiverOf` (§3.3), `declarationLeaves` (§3.4),
        `methodNameLeafOf` (§3.5) and `declarationLeafOf` (§3.6). Explicit imports, no wildcards; the
        object is stateless and retains no `Project`/`PsiFile`.
  - [x] Add the branch to `LuaNameReference.multiResolve` — realizes design §2.2. **Above** the
        `ResolveCache` call, not inside `doMultiResolve` (§3.6 decision 2). Nothing else in the class
        changes.
  - [x] Add `LuaTypesVisitor.isSnapshotUnderConstruction` — realizes design §2.3.
  - [x] Wire the guard into `declarationLeafOf` — design §3.6 decision 3. It is written in this phase
        because the phase is red without it; Phase 3 is what verifies it.
  - [x] Confirm `plugin.xml` is untouched (design §7). A diff to it in this phase is a defect.
- **Exit criteria**:
  - `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` is green, including
    `LuaTypeGraphRootResolutionBudgetTest`'s pre-existing methods **at their committed budgets**.
  - `ktlintCheck` is green — run it alone on the builder, never paired with `ktlintFormat` (BUG-445).

### Phase 2: The resolution and refusal tests [Must]

- **Goal**: every accepted and every refused shape is pinned by a test whose falsifying mutation was
  executed.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.LuaColonCallResolutionTest` (`BasePlatformTestCase`) —
        `requirements.md` cases 1–6 and 9–14. **One `configureByText` per test method**; the two
        cross-file rows (cases 6 and the cross-file global control) use `addFileToProject` plus one
        `configureByFile`.
  - [x] Create `net.internetisalie.lunar.lang.insight.LuaColonCallFindUsagesTest` — cases 7 and 8:
        `ReferencesSearch.search(<declaration leaf>, GlobalSearchScope.allScope(project))` returns
        the call site and `isReferenceTo` is true, for each accepted shape; and returns nothing for
        each refused one.
  - [x] Add the reach-pinning refusals of case 15 (`self`, factory, alias, parameter receiver,
        `require`d module) to `LuaColonCallResolutionTest`, each asserting `resolve() == null`, each
        carrying the comment that it has **no NAV-13-side falsifier** and why
        (`requirements.md`, the note under the test-case table).
  - [x] Create `net.internetisalie.lunar.lang.types.LuaColonCallInferenceWithdrawalTest`
        (`IndexedBasePlatformTestCase`) — `requirements.md` case 19. Two methods, one
        `configureByText` each: the colon fixture asserting the lambda parameter infers `unknown`, and
        the dot-call control asserting the same. Read the type through
        `LuaTypesSnapshot.forFile(file).graphTypeToLuaType(types.getValueType(paramRef)).name`; the
        parameter `LuaNameRef` comes from the `LuaFuncDef`'s `parList.nameList.nameRefList`.
        This is `NAV-13-07`'s exception, and the corpus ratchet structurally cannot cover it.
  - [x] Execute every mutation `requirements.md` names for cases 1, 4, 9, 10, 11, 12, 13, 14 and 19,
        one at a time, and confirm the named test reddens. Case 19's mutation is case 1's — deleting
        the colon branch — and it must be observed reddening **case 19's own test**, at `z` inferring
        `string`. Revert each with `git show HEAD:<path> > <path>` — never `git checkout --`.
- **Exit criteria**: every class this phase creates is green; every named mutation observed red on
  its own test and reverted; `git status --porcelain` clean in **both** the working tree and the
  builder tree.

### Phase 3: The cost gate and the fan-out bound [Must]

- **Goal**: the un-annotated resolution path has a committed budget, the guard has a falsifier, and
  the cross-file fan-out has a bound rather than a wall-clock.
- **Tasks**:
  - [x] Add `colonCallSiteResolutionStaysWithinItsRootResolutionBudget` and
        `unannotatedCallSiteFixture()` to `LuaTypeGraphRootResolutionBudgetTest` — design §2.4. The
        method must (a) assert every one of the fixture's 80 call sites resolves, so it cannot pass
        vacuously, and (b) resolve them **twice**, so a per-resolution cost is visible.
  - [x] Run it, read the measured `WRITE` / `READ`, and commit budgets with ~8% headroom over the
        measurement — the convention `CONVERSION_WRITE_BUDGET` / `CONVERSION_READ_BUDGET` already
        follow. The prototype measured `WRITE` = 165 and `READ` = 83 on this fixture and passed at
        `COLON_WRITE_BUDGET = 180L` / `COLON_READ_BUDGET = 92L`, executed green
        (`risks-and-gaps.md` DR-02 Finding 5). **Take the numbers from your own run**, and if they
        differ from 165/83 by more than the headroom, stop and record why before widening the
        budget.
  - [x] Execute the guard mutation (`requirements.md` case 16 / 17): delete the
        `isSnapshotUnderConstruction` test from `declarationLeafOf` and confirm the budget methods
        that **predate this feature** redden, at `WRITE` 812 and 814. Revert.
  - [x] Add `crossFileFanOutStaysLinearInCallSites` and `ringFixture(size: Int)` to
        `LuaTypeGraphRootResolutionBudgetTest` — `requirements.md` case 20, the shipped-code form of
        `risks-and-gaps.md` DR-04. The ring is `K` files (`K` = 2, 4, 8, 16); file *i* is
        `---@class Ci` / `local Ci = {}` / `function Ci:mi() end` / `return Ci` plus 10 blocks of
        `---@type C(i+1 mod K)` / `local bN` / `bN:m(i+1 mod K)()`. Resolve every site, assert all
        resolve, then sum `rootResolutionCount(WRITE)` and `(READ)` across **every** file's snapshot
        — per-file counters cannot see a fan-out
        ([LuaTypeGraph.kt:42](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt)).
        Assert the summed delta over the no-branch baseline is **one `WRITE` and one `READ` per
        resolved site at every `K`**; the prototype measured +20/+40/+80/+160 against baselines of
        198/396/792/1584 `WRITE` and 94/188/376/752 `READ`. Take the numbers from your own run and
        assert the per-site ratio, not the absolute totals, so the test survives an unrelated
        re-baselining of the engine.
- **Exit criteria**: every budget method green, including the ring method at every `K` it covers; the
  guard mutation observed reddening the methods that predate this feature; the committed budget
  values and the measurement they came from written into each new method's KDoc, pinned to the commit
  they were measured at.

**Executed** on top of `7cf880bf` (gce-builder, `test --rerun --no-build-cache`):

- **The un-annotated budget.** `WRITE` = 165, `READ` = 83 — the prototype's numbers reproduced
  exactly — committed as `COLON_WRITE_BUDGET = 180L` (9.1% headroom) and `COLON_READ_BUDGET = 92L`
  (10.8%), the pair `risks-and-gaps.md` DR-02 Finding 5 predicted.
- **The guard mutation.** Deleting the `isSnapshotUnderConstruction` test reddened both budget
  methods that predate this feature at exactly the transcribed values — `WRITE` **812** over 600 and
  **814** over 620 — and also reddened the new method at `WRITE` **485** over 180, which is DR-02
  Finding 4's un-annotated un-guarded "after 160 resolutions" cell. Reverted with
  `git show HEAD:<path> > <path>`; both production files verified byte-identical to `HEAD` by
  `md5sum`.
- **The ring.** Summed over every file's snapshot, baseline taken after all `K` snapshots are built
  and before any site is resolved: `WRITE` 198→218, 396→436, 792→872, 1584→1744 and `READ` 94→114,
  188→228, 376→456, 752→912 at `K` = 2/4/8/16 — DR-04's table reproduced value for value, and the
  assertion is the per-site ratio (delta = 1 `WRITE` + 1 `READ` per resolved site) rather than those
  totals.

**Two departures, both forced by execution and recorded in `risks-and-gaps.md` under
"Phase 3 execution":**

1. **`crossFileFanOutStaysLinearInCallSites` ships in its own class**,
   `net.internetisalie.lunar.lang.types.LuaColonCallCrossFileFanOutTest`
   (`BasePlatformTestCase`), rather than in `LuaTypeGraphRootResolutionBudgetTest` as this bullet
   said. That class's base, `BaseDocumentTest`, builds `LightTempDirTestFixtureImpl(false)`, whose
   added files live outside any source root, so `LuaTypeManagerImpl`'s `allScope` lookup cannot see a
   class declared in a sibling file — measured, **0 of 20** sites resolved at `K` = 2 there. Moving
   the class to a fixture that indexes would have perturbed the pre-existing budgets `NAV-13-06`
   requires to hold unchanged. `design.md` §2.4 adds only the budget method to that class, so this
   placement keeps the design's own statement true.
2. **The guard mutation does not redden the ring test**, and `requirements.md` case 20's mutation
   column is corrected accordingly. The un-guarded branch inflates the *baseline* (snapshot
   construction), which the shipped delta-only form cannot see by construction; cases 16 and 17 carry
   that falsifier. The ring's own executed falsifier is case 1's — deleting the colon branch from
   `LuaNameReference.multiResolve` — which reddens it at `expected:<20> but was:<0>`.

### Phase 4: The no-inference-change gate [Must]

- **Goal**: `NAV-13-07`'s no-change half. Its scoped exception is Phase 2's case 19; the ratchet
  cannot see that class of change (`risks-and-gaps.md` DR-03).
- **Tasks**:
  - [x] Run `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`.
        Confirm `LuaCorpusSweepTest`, `LuaTortureCorpusTest` and `LuaInspectionParityTest` appear in
        `build/test-results/test/` **and check their timestamps** — `--rerun` does not clear that
        directory, so a read after a skipped run serves the previous run's XML.
  - [x] Confirm no `Corpus regression:` line
        ([CorpusGuards.kt:53-54](../../../../src/test/kotlin/net/internetisalie/lunar/corpus/CorpusGuards.kt)).
        An `[corpus] IMPROVED (…)` line is printed rather than failed and means the baseline is
        re-recorded.
  - [x] Re-run `risks-and-gaps.md` DR-01's corpus reach measurement against the **shipped** code
        rather than the prototype, and record the number. Follow DR-01's Method exactly — in
        particular **one fixture project per corpus** (a combined project gives 73 declarations, not
        84) and the `LuaFuncCall`-parented call-site denominator. Apply DR-01's reproducibility
        protocol: measure the same copied tree twice in one method and require the two passes to
        agree, and check `files` = 734, `declLeaves` = 941 and `underFuncCall` = 14 116 **before**
        reading the numerator. Executed against the shipped code in Phase 4: **67 of 941** declarations and **315 of 14 116** sites (the prototype measured 84 / 372; the difference is zerobrane alone). A different
        number with the denominators intact means the shipped code differs from the transcription the
        design was measured on.
  - [x] Record the corpus lane's wall-clock beside the prototype's 20 min 5 s and [[TYPE-13]] Phase
        3's 24 min 49 s **as context only**. It bounds nothing — two uncontrolled runs on a shared
        builder — and Gap 2.3's actual bound is Phase 3's case-20 test, not this number.
- **Exit criteria**: the corpus lane green with no regression line; the shipped-code reach number
  recorded in `risks-and-gaps.md` DR-01 beside the prototype's, with its two passes agreeing and its
  three denominators reproduced.

**Executed** at `adc581f1` (gce-builder). Detail in `risks-and-gaps.md` under "Phase 4 execution":

- **The corpus lane.** `test -PwithCorpus --rerun --no-build-cache` — BUILD SUCCESSFUL in 20 min
  49 s, 483 classes / 3 032 tests / 0 failures / 0 errors / 1 skipped. All three opt-in classes
  present with XML `timestamp` attributes inside the run window; **no `Corpus regression:` line and
  no `[corpus] IMPROVED` line**. The results directory held two stale XMLs from an earlier filtered
  run when this one started, which is the trap the task bullet names.
- **The reach re-measurement — one departure from the expected value, reported rather than
  overwritten.** All five denominators reproduce exactly (`files` = 734, `declLeaves` = 941, raw
  `LuaMethodExpr` = 14 191, `underFuncCall` = 14 116, bare-name = 11 411), and luacheck, luarocks and
  penlight reproduce their numerators cell for cell. **Zerobrane returns 188 sites / 56 declarations
  against DR-01's 245 / 73**, taking the totals to **315 of 14 116 sites and 67 of 941 declarations**.
  The two passes agreed in every method, zerobrane alone returned the same figure, and 188 / 56 is
  DR-01's own recorded outlier (187 / 55) plus one — so the outlier is what reproduces and 245 / 73
  is what does not. Two hypotheses were executed: the §3.6 guard is **not** the cause (guard-off
  gives the identical 188 / 56), and a stage tally puts every loss upstream of §3.5 — 0 sites are
  refused at the leaf mapping and 0 are ambiguous, while 7 183 have a member whose
  `LuaMemberDeclarations.declarationOf` is null. The shortfall is in the type engine's answer, not in
  a clause NAV-13 ships. Risk 1.1's disposition is unaffected; DR-01 pre-committed to that.
- **Wall-clock, as context only.** 20 min 49 s beside the prototype's 20 min 5 s and [[TYPE-13]]
  Phase 3's 24 min 49 s. Three uncontrolled runs on a shared builder bound nothing; Gap 2.3's bound
  is Phase 3's case-20 test.

### Phase 5: The consumer-visible gate [Must]

- **Goal**: `NAV-13-08`. Every user-visible consumer change is re-enumerated against the shipped code
  by execution, and each is pinned by a fixture — because the corpus carries 0 `---@` tags and
  `NAV-13-07`'s ratchet can observe none of them.
- **Tasks**:
  - [x] Re-run `risks-and-gaps.md` DR-05's enumeration against the **shipped** code, not the
        prototype. Add `recordCaller()` to `LuaColonCallResolution.isColonCallMemberName` exactly as
        DR-05 specifies (capture `Thread.currentThread().stackTrace`, keep `net.internetisalie.lunar`
        frames excluding the recorder and `multiResolve`, take the first three), drive DR-05's five
        fixtures through every surface it lists, and diff the recorded route set against
        `design.md` §7's table. **A route present in the run and absent from §7 is a defect in the
        design, not in the run** — stop and record it. Revert the instrument with
        `git show HEAD:<path> > <path>`; never `git checkout --`.
  - [x] Re-run `risks-and-gaps.md` DR-06's **mirror sweep** against the shipped code: for each of its
        fixtures, drive `ReferencesSearch.search`, `LuaSafeDeleteProcessor.findUsages`,
        `LuaRenameProcessor.substituteElementToRename`, `myFixture.renameElementAtCaret`,
        `LuaFindUsagesProvider.canFindUsagesFor` and
        `LuaDocumentationTargetProvider.documentationTargets` at **every** `LuaNameRef` identifier
        leaf of the file — one `configureByText` name reused so the fixture is the project's only
        file — and diff the branch-off and branch-on runs field by field. Read the documentation
        target's **anchored element**, not its class: two declarations yield the same class and DR-05
        missed the retargeting because of it. Any off/on difference absent from `design.md` §7 is a
        defect in the design.
  - [x] Re-derive the `plugin.xml` surface set by **both halves** of `design.md` §7's rule, and
        recompute its residue. Take every `implementation=` / `implementationClass=` / `class=` /
        `factoryClass=` / `instance=` / `serviceImplementation=` attribute and every `<className>`
        naming a `net.internetisalie.lunar` class; resolve each to its declaring `.kt` file by path
        and, where the file is named after a different declaration, by searching
        `class|object|interface <Simple>`; then keep every class whose file names a **call**
        spelling (`.resolve()`, `multiResolve`, `ReferencesSearch`) **or** a **receive** spelling
        (`CommonDataKeys.PSI_ELEMENT`, `TargetElementUtil`, `PsiElementRenameHandler.getElement`).
        Separately list every `src/main/kotlin` file naming one of those six spellings that is
        **not** the declaring file of a registered class — §7's residue table. Confirm §7 accounts
        for every class in both halves and every residue file.
        **Deriving over the call half alone reproduces the omission this task exists to detect and
        certifies nothing**: `LuaInplaceRenameHandler`, `LuaTypeHierarchyProvider` and
        `LuaTargetElementEvaluator` name no call spelling, and the first two carry user-visible
        flips. Record the counts the derivation yields; §7 states what they were at `7a1dc387`, and
        a divergence is a consumer added or removed since, not a tolerance.
  - [x] Create `net.internetisalie.lunar.analysis.inspections.LuaColonCallDeprecationTest`
        (`BasePlatformTestCase`, `myFixture.enableInspections(LuaDeprecatedApiInspection())`) —
        `requirements.md` cases 21 and 22. Three methods, one `configureByText` each: the withdrawal
        fixture, the different-names fixture, and the same-names control. Assert on the exact
        `HighlightInfo` offsets the cases name, not on a count.
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaColonCallRenameRefusalTest` —
        `requirements.md` case 23. `substituteElementToRename` on the call site's leaf throws
        `RefactoringErrorHintException`; the same fixture with `LuaUnusedLocalInspection` enabled
        reports `Unused local variable 'm'` at the local's own offsets.
  - [x] Create `net.internetisalie.lunar.lang.insight.hint.LuaColonCallInlayHintsTest` —
        `requirements.md` case 24. The `t:print` fixture and the `t:emit` control, asserting the
        inline inlay offsets each yields.
  - [x] Extend `LuaColonCallRenameRefusalTest` with `requirements.md` case 26 — the full
        `renameElementAtCaret("RENAMED")` at the **same-named declaration's** caret, asserting the
        resulting file text on the local-variable fixture and on the receiver-shares-its-member-name
        fixture. This is the half case 23 does not cover: case 23 drives the call site.
  - [x] Create `net.internetisalie.lunar.lang.LuaColonCallUsageWithdrawalTest` —
        `requirements.md` cases 25, 27 and 29. `ReferencesSearch` on the same-named declaration leaf
        across the `LOCAL_VARIABLE` / `LOCAL_FUNCTION` / `GLOBAL_FUNCTION` fixtures;
        `LuaSafeDeleteProcessor().findUsages` on both ends of the case-23 fixture; and the
        unresolvable-member fixture, where the usage is withdrawn with no counterpart gained.
  - [x] Extend `LuaColonCallRenameRefusalTest`'s inspection half with `requirements.md` case 28 —
        the generic-`for` variable and, with `checkParameters = true`, the parameter kind.
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaColonCallRenameHandlerTest` —
        `requirements.md` case 31. Build the data context with
        `DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` and inject
        **nothing** into it — the whole point is that `PsiElementRenameHandler.getElement` returns
        what `TargetElementUtil` computes, and a `SimpleDataContext` carrying an injected
        `CommonDataKeys.PSI_ELEMENT` would assert the case away (`LuaInplaceRenameTest`'s class KDoc
        records that trap). Assert on the **handler type**, never on the platform's class name.
  - [x] Create `net.internetisalie.lunar.lang.hierarchy.LuaColonCallTypeHierarchyTest` —
        `requirements.md` case 32. One `configureByText`, `getTarget` on the same kind of context.
  - [x] Extend `LuaColonCallRenameRefusalTest` with `requirements.md` case 33 — the conflict
        withdrawal. Keep `print(m)` **outside** the `do` block; moving it in makes the mutation
        unreachable from the fixture (`design.md` §7, `distinctByAnchor`).
  - [x] Create `net.internetisalie.lunar.lang.doc.LuaColonCallDocumentationTest` —
        `requirements.md` case 30. Assert the documentation target's **anchored element**, so the
        retarget from a same-named global's doc to the method's is visible; asserting the target
        class cannot see it.
  - [x] Execute the mutations cases 21, 23 and 24 name — deleting the colon branch — and confirm each
        test reddens on its **own** fixture. For case 22 the mutation is
        `LuaDeprecatedApiInspection`'s `LuaFuncDecl` name-equality guard
        ([:81-86](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt)),
        which is production code this feature does **not** otherwise touch: delete it, observe the
        different-names method redden, and revert it with `git show HEAD:<path> > <path>`.
- **Exit criteria**: the re-enumeration names no route `design.md` §7 omits; the mirror sweep produces
  no off/on difference §7 omits; the surface set re-derived by **both** halves of §7's rule, and its
  recomputed residue, add no consumer §7 does not account for; every class this phase creates is green; every named mutation observed red on its own test and reverted; `git status
  --porcelain` clean in **both** the working tree and the builder tree, with no `recordCaller` left
  in `LuaColonCallResolution`.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|---|---|---|
| `NAV-13-01` | M | Phase 1, pinned by Phase 2 |
| `NAV-13-02` | M | Phase 1, pinned by Phase 2 |
| `NAV-13-03` | M | Phase 1, pinned by Phase 2 |
| `NAV-13-04` | M | Phase 1, pinned by Phase 2 |
| `NAV-13-05` | M | Phase 1, pinned by Phase 2 (cases 13, 14) and Phase 1's full-suite exit criterion |
| `NAV-13-06` | M | Phase 1 (the guard), gated by Phase 3 (cases 16, 17 and 20) |
| `NAV-13-07` | M | Phase 1 (the guard); the no-change half gated by Phase 4's ratchet (case 18), the scoped exception by Phase 2's case 19 |
| `NAV-13-08` | M | Phase 1 (the branch is what moves them); enumerated and pinned by Phase 5 — cases 21-24 and 31-32 on the call site's side, cases 25-30 and 33 on the same-named declaration's. Not gated by Phase 4 — the ratchet is structurally blind to every one of these |

## Verification Tasks

- [x] `LuaColonCallResolutionTest` — covers cases 1–6, 9–15.
- [x] `LuaColonCallFindUsagesTest` — covers cases 7, 8.
- [x] `LuaColonCallInferenceWithdrawalTest` — covers case 19.
- [x] `LuaTypeGraphRootResolutionBudgetTest` — covers cases 16, 17, 20.
- [x] `test -PwithCorpus` — covers case 18.
- [x] `LuaColonCallDeprecationTest` — covers cases 21, 22.
- [x] `LuaColonCallRenameRefusalTest` — covers case 23.
- [x] `LuaColonCallInlayHintsTest` — covers case 24.
- [x] `LuaColonCallUsageWithdrawalTest` — covers cases 25, 27, 29.
- [x] `LuaColonCallRenameRefusalTest` — also covers cases 26 and 28.
- [x] `LuaColonCallDocumentationTest` — covers case 30.
- [x] `LuaColonCallRenameHandlerTest` — covers case 31.
- [x] `LuaColonCallTypeHierarchyTest` — covers case 32.
- [x] `LuaColonCallRenameRefusalTest` — also covers case 33.
- [x] DR-05's caller enumeration re-run against the shipped code and diffed against `design.md` §7.
- [x] DR-06's mirror sweep re-run against the shipped code, and its `plugin.xml`-derived surface set
      re-derived by **both** halves of §7's rule — call spellings and receive spellings — with the
      residue list recomputed and reconciled with `design.md` §7.
- [x] Every mutation in `requirements.md`'s test-case table executed and observed, one at a time.
- [ ] `human-verification-checklists.md` run in the containerized IDE (`verify-in-ide`): the feature
      is a navigation action, and no unit fixture exercises the gutter, the Ctrl+Click target popup
      or the Find Usages tool window.

## Task Summary

| Phase | Status | Priority |
|---|---|---|
| Phase 1: `LuaColonCallResolution` and the branch | done | Must |
| Phase 2: The resolution and refusal tests | done | Must |
| Phase 3: The cost gate and the fan-out bound | done | Must |
| Phase 4: The no-inference-change gate | done | Must |
| Phase 5: The consumer-visible gate | done | Must |
