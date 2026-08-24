---
id: "REFACT-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "REFACT-01"
priority: "medium"
folders:
  - "[[features/refactoring/01-rename-refactoring/requirements|requirements]]"
---

# Implementation Plan: REFACT-01 — Rename Refactoring

Eight phases. Phases 1-2 together are the shippable core of every `Must` requirement's *uncancelled*
path; **Phase 8 closes the last open half of a `Must`** (`REFACT-01-01` under cancellation,
[[BUG-468]]) and therefore runs before Phase 7, which is a `Could`. Phases 3-7 are `Should`/`Could`
increments that each leave the build green on their own. Phase numbers are chronological, not
ordered: the execution order is 1, 2, 3, 4, 5, 6, **8**, 7.

**Standing rules for every phase**

- Build/test **only** through `tooling/gce-builder/gce-builder.sh run …`. Never `./gradlew` locally.
- Never run two `gce-builder run` invocations concurrently.
- Format on the VM and pull the result back before committing —
  `run ktlintFormat` then `rsync -az --include='*/' --include='*.kt' --exclude='*' builder:/home/builder/lunar/src/ src/`,
  then `run ktlintCheck` **alone**. Never `run "ktlintFormat ktlintCheck"` (BUG-445: that pairing
  cannot fail).
- The full-suite gate is `run "test --rerun --no-build-cache"`. A green `--tests *Rename*` proves
  nothing about the suite.
- Engineering-contract tripwires are hard: ≤30 logic lines per function, **≤3 arguments** per
  function (excluding `Project`/`Disposable`) — including private helpers. Self-audit each new file
  against both before opening the gate.
- **The ≤3-argument cap does not apply to a platform `override`, and no reviewer may fail an
  implementer for one.** `RenamePsiElementProcessor.findCollisions` (4 args),
  `renameElement` (4), `findReferences` (3) and `SafeDeleteProcessorDelegate.findUsages` /
  `getElementsToSearch` (3 each) have signatures fixed by the IntelliJ Platform: the arity is not a
  design choice, it is the contract being implemented, and changing it means not overriding the
  method. The contract's §3 cap governs functions *this* codebase declares, and every such function
  in this feature is designed to fit it — `LuaRenameConflictDetector.collisions` folds three values
  into `LuaRenameTarget` to stay at two (design §2.3), and `LuaCatsParamRenamer.rename` takes
  exactly three (§2.8). Implementers in this repo have repeatedly been failed on this cap; the
  distinction is written here so the next one is not.
- **There is deliberately no `human-verification-checklists.md` for this feature.** The
  `plan-feature` skill names one at Step 8 and the planning DoD does not require it; ~20% of feature
  directories in `docs/features/` carry one. The live-IDE checklist lives in "Verification Tasks"
  below instead, because every item in it is bound to a phase gate and a TC number that only mean
  something in this file's context — a separate copy would be a second list of the same steps, and
  this feature's whole subject is what happens when two lists of the same rule drift apart.

## Phases

### Phase 1: Declaration-site model + global indexing [Must]

- **Goal**: one classifier and normaliser for Lua declaration sites, with the existing consumers
  delegating to it. Two user-visible changes fall out: `function M.run()` becomes a findable
  declaration, and Lua 5.5 `global` declarations become cross-file resolvable (§2.10) — the second
  is what makes REFACT-01-07 true for 5.5 rather than a same-file half-measure.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.psi.LuaDeclarationKind` +
        `net.internetisalie.lunar.lang.psi.LuaDeclarationSite` in
        `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt` — realizes design
        §2.1 and **all three** tables in §3.5 (`kindOf` rows 1-15, `identifierLeafOf` rows 1-12,
        `declarationNodeOf`), **including `functionNameLeafOf(funcName)`** — the one-argument helper
        `identifierLeafOf` row 9, §3.1 step 4a and `LuaNameReference.declarationIdentifier` all call,
        so the funcName three-way precedence has exactly one implementation. Decompose `kindOf` into the three one-argument private helpers named in
        §2.1 (`kindFromLeafParent`, `kindFromNameRefGrandParent`, `kindFromAssignmentTarget`) to stay
        inside the tripwires.
  - [x] Implement `LuaDeclarationSite.computeFileScopeLocalNames(file)` +
        `fileScopeLocalNames(file)` (the latter behind `CachedValuesManager.getCachedValue`) and
        `boundName(declaration)`, moving `LuaGlobalAssignmentIndex.Indexer.fileScopeLocalNames` +
        `boundName` (`LuaGlobalAssignmentIndex.kt:133-152`) with the single signature change design
        §2.1 names (`topLevel: List<Any?>` → `file: LuaFile`), **including the node-based `boundName`
        read** (SYNTAX-18 — the generated `@NotNull` getter returns null on a partial parse and the
        platform logs an error). Both are `internal`, not private: the index calls
        `computeFileScopeLocalNames` (never the cached accessor — it runs on the indexing thread) and
        `declaredGlobalName` calls `boundName` (`LuaGlobalAssignmentIndex.kt:123`). Delete the index's
        private copies; do not leave two.
  - [x] Implement `LuaDeclarationSite.isBareAssignmentTarget(target)` (§3.5 clauses 1-3, in the O(1)
        `stmt.parent is LuaBlock && stmt.parent.parent is LuaFile` form) and
        `isGlobalAssignmentTarget(target)` (clauses 1-4), then **rewrite
        `LuaGlobalAssignmentIndex.Indexer.map`'s assignment collector to call
        `isBareAssignmentTarget`** — realizes design §2.10 change 0. This is what makes §3.5 row 14 a
        reuse instead of a second copy of the index's rule; the previous draft claimed the rule was
        shared "verbatim" when only the file-scope-locals clause actually was. Run DR-09 first.
  - [x] Move `declarationNodeFor` (`LuaSafeDeleteProcessor.kt:156-171`) and `identifierLeafFor`
        (`:178-191`) out of `net.internetisalie.lunar.refactoring.LuaSafeDeleteProcessor` into
        `LuaDeclarationSite`; have the processor call `LuaDeclarationSite.declarationNodeOf` /
        `identifierLeafOf`. Add the `LuaFuncNameProperty`, `LuaGlobalVarDecl`, `LuaGlobalFuncDecl` and
        global-assignment branches to `declarationNodeOf`, **and rows 10-11
        (`LuaAssignmentStatement`, `LuaVar`) to `identifierLeafOf`** — realizes design §3.5. Every one
        is **mandatory**, not optional, and the two tables must be extended together: the same phase
        widens `isSafeDeleteAvailable`, a `declarationNodeOf` row with no `identifierLeafOf` return
        leg breaks the §2.6a round trip, and a kind with neither deletes the bare identifier and
        leaves `global  = 1`.
  - [x] **Replace `LuaSafeDeleteProcessor.isElevatedDeclaration` (`:53-57`) with the round-trip test
        of design §2.6a** — `LuaDeclarationSite.declarationNodeOf(identifierLeafOf(element)) === element`
        — in the **same commit** as the two tasks above. This is not a Phase 7 nicety and not
        REFACT-03 scope creep: `isSafeDeleteAvailable` delegates to `canFindUsagesFor`
        (`LuaRefactoringSupportProvider.kt:30`), so rewriting `canFindUsagesFor` widens Safe Delete
        immediately, and the four enumerated types in `isElevatedDeclaration` do not include any node
        the new `declarationNodeOf` rows return. The platform then drops the delegate
        (`SafeDeleteProcessor.java:138-166`) and, because none of those nodes is a `PsiNamedElement`,
        deletes the declaration **with no usage search at all** — the outcome
        `LuaSafeDeleteProcessor.kt:46-52` describes verbatim, and strictly worse than the current
        tree. TC-32 and TC-33 are the gates; risks-and-gaps Risk 1.6 is the record.
  - [x] Extend `net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex` with the Lua 5.5
        `LuaGlobalVarDecl` / `LuaGlobalFuncDecl` declaration forms and bump `getVersion()` **3 → 4**;
        extend `LuaGlobalAssignmentNavigation.find` with the matching collectors — realizes design
        §2.10 changes 1-3. The version bump is not optional (`LuaGlobalAssignmentIndex.kt:54-58`
        records why). Without this, §3.5 rows 5 and 7 classify a `global` as project-wide while
        resolution cannot see it across files, and the rename half-applies silently.
  - [x] Rewrite `net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider.canFindUsagesFor` as
        `LuaDeclarationSite.kindOf(element) != null` and `getType` as
        `LuaDeclarationSite.kindOf(element)?.usageViewType ?: ""` — realizes design §2.1.
  - [x] Fix `LuaNameReference.declarationIdentifier` (`LuaNameReference.kt:246-258`) to call
        `LuaDeclarationSite.functionNameLeafOf(decl.funcName)` for its `LuaFuncDecl` branch — i.e.
        `funcNameMethod` → last `funcNameProperty` → `nameRef` — realizes design §3.5.
  - [x] Rewrite `LuaNameReferenceSearcher.processQuery` **exactly as design §3.8 writes it**,
        deleting `isNameDeclarationLeaf` (`:84-88`) and leaving `candidateFiles` (`:63-77`)
        untouched. Three things need care, each with a named rationale in §3.8 — (b) and (c) are
        load-bearing with real failure modes; (a) deliberately is **not**:
        (a) the `LuaLabelName` exclusion is kept **before** normalisation, as **unreachable
        defence-in-depth** — guard ③ rejects a normalised label on its own today, so the order is
        not observable and there is no test on it; copy §3.8 ①'s rationale into the KDoc so the
        next editor neither deletes the line as dead code nor reorders it;
        (b) `val name` is read from the **normalised leaf**, not from `parameters.elementToSearch` —
        the current `val name = target.text` (`:46`) is safe only because the old gate guaranteed a
        leaf, and a composite's `text` is the whole declaration (`"local x = 1"`);
        (c) `isReferenceTo` is called against the **normalised leaf**, not the original target
        (`:53`) — `LuaNameReference.isReferenceTo` compares identity against `resolve()`'s result,
        which is always a leaf, so a composite makes every candidate false and the widening silently
        returns zero usages.
  - [x] Point `LuaRefactoringSupportProvider.isSafeDeleteAvailable` at
        `LuaDeclarationSite.kindOf(element) != null` and correct its KDoc's REFACT-01→REFACT-04
        attribution for labels — realizes design §2.6.
- **Exit criteria**:
  - `LuaDeclarationSiteTest` passes TC-21, TC-22 and TC-30; `LuaFindUsagesTest` passes TC-23 **and
    its existing `testLabelUsagesCount` / `testCanFindUsagesForLabel`** — the latter two are the
    label path's coverage now that TC-35 is dropped (see its row above).
  - `LuaSafeDeleteTest` passes its existing **six** `@Test` cases (`testUnusedLocalIsDeleted`,
    `testUsedLocalReturnsUsages`, `testUnavailableOnKeyword`, `testHandlesElevatedDeclaration`,
    `testUsedLocalRaisesConflict`, `testLabelDeclarationIsAvailable`) **plus the new TC-32 and
    TC-33**. These are
    listed first among the exit criteria because Safe Delete is the subsystem this phase changes
    without meaning to (Risk 1.6), and its existing cases cover only file-local `local x = 1`.
  - `LuaFindUsagesTest`, `LuaFindUsagesCrossFileTest`, `LuaReferenceTest`,
    `LuaNavigationTest`, `ShadowingVariableInspectionTest` all still pass **unchanged**.
  - The index extension's resolution gates pass: `LuaGlobalCreationInspectionTest`,
    `LuaUndeclaredVariableInspectionTest`, `LuaCrossFileGlobalResolutionTest`. These are named
    separately from the list above because §2.10 changes what resolves, additively — a name that
    resolved to nothing may now resolve to its `global` declaration.
  - **Corpus gate**: `run "test -PwithCorpus --rerun --no-build-cache"` is green. This phase edits
    `LuaNameReference.declarationIdentifier`, which can move inferred types; per the engineering
    contract §5 the routine loop excludes `*Corpus*` and `git status` on
    `src/test/resources/corpus/` proves nothing. Verify `LuaCorpusSweepTest`,
    `LuaTortureCorpusTest` and `LuaInspectionParityTest` appear in `build/test-results/test/`
    **with fresh timestamps**.

### Phase 2: Core rename processor [Must]

- **Goal**: rename works end to end for locals, parameters, `for` variables, local functions and
  globals — from a declaration and from a usage — and the interim refusal is gone.
- **Tasks**:
  - [x] Add `LuaNameReference.handleElementRename` — realizes design §2.5.
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` with
        `canProcessElement`, `substituteElementToRename`, `findReferences`, `renameElement` —
        realizes design §2.2, **§3.0**, §3.1 (**steps 1-5, step 4a included**), §3.2, §3.3. Leave
        `findCollisions` unimplemented until Phase 3.
  - [x] Implement **§3.1 step 4a**, the funcName receiver-segment refusal:
        `PsiTreeUtil.getParentOfType(leaf, LuaFuncName::class.java, false)` and, when that is
        non-null and `LuaDeclarationSite.functionNameLeafOf(it) !== leaf`, refuse with
        `refactoring.rename.functionNameSegment`. This is **not** Phase 4 scope even though it reads
        like dotted-function work: §3.5 row 9 classifies the receiver `M` of `function M.run() end`
        as `GLOBAL_FUNCTION` from Phase 1, and rename lands here — so without step 4a, Phase 2 ships
        a rename that rewrites that declaration and leaves every `M.run()` call site on the old name
        (resolution cannot redirect: `LuaBlock.processDeclarations` has no `LuaFuncDecl` branch,
        `LuaBlockExt.kt:38-77`). TC-34a and TC-34b are the gates; risks-and-gaps Risk 1.1 shape 6 is
        the record.
  - [x] Implement **`getQualifiedNameAfterRename(element, newName, nonJava) = newName`** (design
        §2.9). This is **not** Phase 7 scope even though it is one of §2.9's six accessors:
        `RenameDialog.createCheckboxes` adds the "Search in comments and strings" checkbox
        unconditionally (`RenameDialog.java:279-282`) and `createRenameProcessor` passes
        `isSearchInComments()` into `RenameProcessor` (`:405`), so one user click reaches
        `RenameUtil.getStringToReplace` with the renamed **leaf**, which is not a `PsiNamedElement`
        — the default `getQualifiedNameAfterRename` returns null
        (`RenamePsiElementProcessorBase.java:106-108`) and the `else` branch logs
        `LOG.error("Unknown element type : …")` (`RenameUtil.java:226`) and returns null into
        `document.replaceString` (`:377`). TC-13d is the gate.
  - [x] Implement `canProcessElement` **exactly as design §3.0 writes it**, in that order. The
        `LuaLabelName`/`LuaLabelRef` exclusion must come first and must not be replaced by a
        `kindOf(...) != null` test: `kindOf(LuaLabelName)` is `LABEL`, not null, and
        `RenamePsiElementProcessorBase.forPsiElement` returns the first matching extension
        (`RenamePsiElementProcessorBase.java:153-161`), so claiming labels aborts the one refactoring
        that works today. TC-24 and TC-25 are the guards.
  - [x] Add the bundle keys `refactoring.rename.unresolved`, `.unsupportedTarget`, `.colonMethod`,
        `.functionNameSegment` to `LuaBundle.properties`; remove `refactoring.rename.unsupported`
        (currently `LuaBundle.properties:145`) — realizes design §7.
        There is **no** `refactoring.rename.implicitSelf` key: the `self` guard it belonged to was
        removed as dead and wrong (design §3.1, the note after step 5).
  - [x] **Correct the epic status table** in `docs/features/refactoring/requirements.md`. Line 33
        reads *"**Status**: **Implemented** (`LuaNameReference.handleElementRename`)"* — that method
        does not exist (`grep -n handleElementRename src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt`
        → empty), and the false claim is why this feature was believed shipped. Line 36 attributes
        REFACT-04 to `LuaLabelFindUsagesProvider` and `LuaLabelRefactoringSupportProvider`; neither
        class exists (`grep -rn LuaLabelRefactoringSupportProvider src/` → empty; the only hit for
        `LuaLabelFindUsagesProvider` is the historical note in `LuaFindUsagesProvider.kt:25`).
        Restate REFACT-01's line against what this phase actually lands (`LuaRenameProcessor`,
        `LuaNameReference.handleElementRename`) and REFACT-04's against
        `LuaFindUsagesProvider` + `LuaLabelReference.handleElementRename` +
        `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable`. Do this in the same commit as
        the `plugin.xml` swap, so the table is never describing a state that does not exist.
  - [x] **Delete** `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaUnsupportedRenameProcessor.kt`
        and `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaUnsupportedRenameProcessorTest.kt`,
        and repoint the single `<renamePsiElementProcessor>` line in `plugin.xml` (currently
        lines 389-390) at `LuaRenameProcessor` — realizes design §7. Both processors must never be
        registered at once.
- **Exit criteria**: `LuaRenameTest` passes TC-01…TC-07, TC-13d, TC-19a/b, TC-25, TC-26 and
  TC-34a/TC-34b; `LuaRenameCrossFileTest` passes TC-08, TC-27, TC-28 and TC-29; `LuaLabelRenameTest`
  (TC-24) still green; full suite green.
- **Met 2026-08-23** — full suite **2,808 tests / 454 classes / 0 failures** (baseline 2,792 / 453;
  +22 new cases in two classes, −6 with `LuaUnsupportedRenameProcessorTest`), `ktlintCheck` clean.
- **Phase-2 review (2026-08-23) — FAILED on first submission, then closed.** Recorded here because
  two of the four findings were defects in what shipped, not in how it was described:
  1. **The declaration rewrite ran after the usage rewrites, behind two unguarded `?: return`s**
     (`LuaRenameProcessor.kt:143-151` as committed in `84eefb25`). If either had fired, every usage
     would carry the new name and the declaration the old one — **BUG-457 inverted**, inside the
     method written to eliminate it. It was not an implementation slip: design §3.3's own steps 2-4
     specified that order and its edge-handling bullet dismissed the consequence as "cannot happen
     in practice because `LuaNamesValidator` gates the dialog". Fixed by resolving the replacement
     and the prepared AST swap before the first edit and refusing the whole rename otherwise
     (`refactoring.rename.rewriteUnavailable`); §3.3 and Gap 2.13 rewritten; TC-36 added with the
     reviewed ordering as its executed mutant.
  2. **`LuaElementFactory.createGotoStatement` ended in `!!`** (`LuaElementFactory.kt:33`), directly
     upstream of that rewrite — so the failure path it was supposed to have was a
     `KotlinNullPointerException` thrown from inside the platform's write action. The factory now
     returns null and `LuaElementFactoryTest` pins both directions.
  3. **Gate 3 (`!!` in a test):** `LuaRenameTest.kt:230,232` used `labelName!!`/`labelRef!!` where
     the same file already used `requireNotNull(...)`. Replaced.
  4. **Gate 8 (a table that renders a lie):** eleven rows of `requirements.md` gained a **6th cell
     against a 5-column header**, so GFM dropped every one of the Phase-2 evidence cells on render
     and `REFACT-01-01` displayed **Full** beside a description reading "no `renamePsiElementProcessor`
     … grep is empty". Rows repaired to five columns with the evidence inside the Description cell,
     and the pre-Phase-2 audit prose in those rows is now dated rather than present-tense.
  Also delivered at the review: **TC-11, TC-13b and TC-19c**, three plan test cases the phase had
  silently dropped (see Verification Tasks), and three new gaps — 2.11 (`goto` exposure, owned by
  REFACT-04), 2.12 (Find Usages / Safe Delete now depend on Gap 2.8's guard with no test of their
  own) and 2.13.
  **Re-gated after the fixes:** full suite **2,813 tests / 454 classes / 0 failures** (+5 on the
  2,808 the phase shipped: TC-11, TC-13b, TC-19c, TC-36 and the factory's null case), `ktlintCheck`
  clean, `lint_docs` and `lint_planning` 0 errors; corpus sweep **2,821 / 457 / 0** (baseline
  2,816 / 457, +5, none lost).
- **Four plan/design claims were measured false while executing this phase.** They are corrected in
  `design.md` §1/§6 and `risks-and-gaps.md` Gaps 2.8-2.10, and each is now a passing test:
  1. **TC-03 does not come for free.** Design §6 said nested same-named locals were "handled by
     resolution"; measured, the inner DECLARATION was rewritten and its own usage left behind. Fixed
     by `LuaNameReference.shadowsRatherThanUses` — see Gap 2.8, blast radius measured at zero.
  2. **TC-05 cannot use the declaration caret.** `TargetElementUtil.findTargetElement` returns null
     on `for <caret>i` — the leaf has no `LuaNameRef` and no `PsiNamedElement` ancestor. TC-05 is
     driven from a usage; the limitation is Gap 2.9 and has its own pinning test.
  3. **TC-26 as specified could not detect what it claimed to guard.** It called
     `substituteElementToRename` directly, bypassing `canProcessElement`; narrowing the predicate to
     `kindOf(element) != null` left the whole suite green. TC-26 now asserts
     `RenamePsiElementProcessor.forElement(...)` and drives `renameElementAtCaret`.
  4. **Design §6's `M = {}` row is false in both halves.** The funcName's `M` is refused by step 4a
     rather than redirected, and a caret on `M = {}` itself yields a `LuaFuncDecl` that
     `canProcessElement` (correctly) declines — Gap 2.10.
- **One deviation from the design's class shape, deliberate**: `LuaRenameProcessor` is `DumbAware`,
  which §2.2 does not list. `LuaUnsupportedRenameProcessor` carried the marker precisely so its
  refusal could not evaporate during indexing (`forPsiElement` skips a processor failing
  `isUsableInCurrentContext`, `:156`, while `RenameElementAction` is a `DumbAwareAction`), and
  dropping it here would have silently regressed that protection into a platform-default half-rename.
  `LuaRenameTest.testSurvivesDumbMode` asserts the selection inside
  `DumbModeTestUtils.runInDumbModeSynchronously`, not merely the marker.
- **The `---@param` propagation step of §3.3 is NOT wired here** (it was step 5 in the Phase-2
  numbering; Phase 8 splits it into step 3a's lookup and step 4's edit), and design §3.3 step 1 exists only
  to serve it: Phase 6's own task list owns "Wire step 5 of `LuaRenameProcessor.renameElement`", so
  capturing `kind`/`oldName`/`catsOwner` in Phase 2 would have produced three unused values.

### Phase 3: Conflict detection [Should]

- **Goal**: a rename that would silently rebind is reported before it is applied.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaRenameConflictDetector`,
        `LuaRenameTarget` and `LuaRenameCollisionUsageInfo` in
        `LuaRenameConflictDetector.kt` — realizes design §2.3, §2.4, §3.4 (rules C1/C2/**C3/C4**
        exactly as written, including C2 step 3's declaration-site skip).
  - [x] Implement `LuaRenameProcessor.findCollisions` to snapshot `result`, call the detector, and
        append — realizes design §2.2 and §3.4.
  - [x] Add the bundle keys `refactoring.rename.conflict.capture`, `.shadow`, `.globalExists`,
        `.ambiguousGlobal` — realizes design §7.
- **Exit criteria**: `LuaRenameConflictTest` passes TC-14, TC-15, TC-16, TC-17 and TC-31; full suite
  green.
- **Met 2026-08-23** — full suite **2,818 tests / 455 classes / 0 failures** (baseline 2,813 / 454;
  +5 in one new class, none lost), corpus sweep **2,826 / 458 / 0** (baseline 2,821 / 457, +5),
  `ktlintCheck` clean. `:integrationTest` still fails on the one pre-existing case it failed on
  through Phases 1 and 2 (`LuaCrossFileCompletionIntegrationTest > recursive cross-file completion
  offers transitively required globals()`), unchanged by this phase.
- **C1-C4 were executed against real PSI before a line of the detector was written**, because four
  claims in this feature's design have already been measured false. A throwaway probe drove
  `scopeCrawlUp`, `LuaDeclarationSite.kindOf`, `StubIndex` and `LuaGlobalAssignmentNavigation.find`
  over the five test fixtures and printed what each rule would see. **All four survived** — and the
  probe is what makes the C2-step-3 mutant a prediction rather than a hope: it showed the inner
  `do local y = 3 end` crawling up to the renamed `x` with `identity=true`, i.e. a false conflict,
  the moment the declaration-site skip is removed.
- **Two deliberate deviations from design §3.4, both of them narrowing a way to lose a conflict.**
  1. **C3 normalises its stub-index hits to the IDENTIFIER leaf, as C4 already does.** As written,
     C3 step 1 anchors on the `LuaFuncDecl` while step 2 anchors on a leaf, so step 2's "excluding
     any element already emitted by step 1" can never match — the two lookups return disjoint PSI
     types. Normalising both makes that exclusion live and lets C3 and C4 share one lookup helper.
  2. **A declaration whose leaf cannot be resolved falls back to the declaration node, where the
     design says `mapNotNull`.** Dropping it would lower C4's count and could turn a real ambiguity
     into silence — the one outcome the rule exists to prevent, and an Elvis fallback that silently
     drops a conflict is exactly what the engineering contract forbids.
- **C4's `declarations.size < 2` guard and its `!== target.identifier` filter are jointly pinned,
  not redundant — keep both.** Deleting *either* alone leaves the suite green; deleting *both*
  reddens all four `LuaRenameCrossFileTest` cases. That is the signature of two protections against
  two *different* failure modes, not of one protection written twice:
  - **The guard** is the only thing preventing a false `"'config' is declared in 1 places"` report
    if the filter's identity comparison ever fails to match — and that comparison was *measured*
    (`identity=true` on a probe), which makes it an assumption, not a guarantee.
  - **The filter** is the only thing preventing a collision anchored on the renamed declaration
    itself when `size >= 2` — i.e. the conflicts dialog telling the user that the very declaration
    they put the caret on is a rival of itself. **Not, as this bullet said through the Phase-4
    review, because anchoring on a usage would make the platform skip rewriting it:** that claim was
    false (see below), and the anchor is a legibility rule, not a correctness one.

  Removing either half to "pin" the other would therefore delete a distinct protection. **Do not
  treat the absent single-mutant as dead weight.** If the filter is wanted individually pinned, that
  is a *test* change and not a code change: TC-31 asserts membership, so an added count assertion
  would redden it. That addition is optional; this record of why both exist is not.

### Phase 4: Dotted method declarations [Should]

- **Goal**: `function M.run()` renames with its call sites; `function Obj:m()` is refused loudly
  instead of half-applied.
- **Tasks**:
  - [x] Confirm `LuaDeclarationKind.DOTTED_FUNCTION` flows through
        `substituteElementToRename`/`findReferences`; add the `METHOD_FUNCTION` refusal branch —
        realizes design §3.1 step 4.
- **Exit criteria**: `LuaRenameTest` passes TC-09 and TC-10; full suite green.
- **Met 2026-08-23** — full suite **2,822 tests / 455 classes / 0 failures** (Phase-3 baseline
  2,818 / 455; +4 in two existing classes, none lost, no new class), corpus sweep
  **2,830 / 458 / 0** (baseline 2,826 / 458, +4), `ktlintCheck` clean. `:integrationTest` is not run for this phase and the skip
  is deliberate rather than silent: that lane has four classes, none of which mentions rename, so
  it cannot reach this code, and its single pre-existing failure
  (`LuaCrossFileCompletionIntegrationTest`, `ExecTimeoutException`) is unchanged since Phase 1.
- **This phase wrote no production code for its own goal, and that is the finding** — *until its
  review, which found that the dotted form reached conflict detection blind; see the remediation
  section below, which does write production code.* Both halves of
  §3.1 step 4 were already in the tree: the `METHOD_FUNCTION` refusal branch shipped with Phase 2
  (`LuaRenameProcessor.substituteElementToRename`, needed there for TC-19a), and `DOTTED_FUNCTION`
  needs nothing to "flow through" — §3.5 row 10 classifies it, `isFileLocal = false` leaves the
  scope alone, and `LuaNameReference.declarationIdentifier`'s Phase-1 fix is what makes the call
  sites resolve. Phase 4 is therefore an **execution** of the claim, not an implementation of it,
  and the execution was the point: five of this feature's design claims have already been measured
  false.
- **Design §3.1 step 4 survived contact with real PSI — measured on the builder, not read.** A
  throwaway probe drove the two fixtures through `kindOf`, `substituteElementToRename` and
  `findReferences` before an assertion was written. `function M.<caret>run() end` classifies
  `DOTTED_FUNCTION`, substitutes to the `run` leaf and finds **1** reference (the `M.run()` call
  site) — with or without a `M = {}` declaration present, so §3.1 step 4a is inert for this caret
  in both worlds. `function Obj:<caret>m() end` classifies `METHOD_FUNCTION`, throws
  `RefactoringErrorHintException`, and — the fact that makes the refusal load-bearing rather than
  conservative — finds **0** references, so the branch is all that stands between this shape and a
  half-applied rename.
- **One measurement worth keeping, from a probe that was accidentally wrong — and the sentence it
  was originally written with was false.** The first probe reused one fixture project across three
  cases, so a second `function M.run()` reached the index and `findReferences` dropped to **0**.
  Discarding that *run* was right; concluding the observation was an artefact was not. It is C4's
  ambiguity arriving through resolution: two declarations of one global make `multiResolve` return
  two results, `resolve()` null and `isReferenceTo` false everywhere.
  **What this plan first wrote next — "the dotted form is subject to it exactly like the plain
  global form of TC-31" — was wrong, and the Phase-4 review caught it.** TC-31's form is *reported*;
  the dotted form was not, because C3 and C4 searched the target's bare last segment while
  `LuaFuncStubElementType.indexStub` files a dotted decl under its qualified `"M.run"` and its
  receiver `"M"` and never under `"run"`. Re-measured on the builder with a fixture set of its own:
  `"run"` → **0** stub hits, `"M.run"` → 2, `"M"` → 2, `ReferencesSearch` → **0** references, and
  `LuaRenameConflictDetector.collisions` → **0 conflicts**. The rename applied silently — BUG-457's
  shape inside the feature that closed BUG-457.

### Phase 4 remediation (2026-08-23) — the dotted key, and three owed items

- [x] **F1 — C3 and C4 search the qualified key.** `LuaRenameConflictDetector.searchKeyOf` prefixes
      the searched segment with whatever the target's own `funcName` carries in front of its last
      segment, taken **by text offset** so the key reproduces the FUNC_NAME node text the stub sinks
      verbatim. Measured prefixes: `"M."` for `function M.run()`, `"A.B."` for
      `function A.B.run()`, `""` for `function greet()`, and no `LuaFuncName` ancestor at all for
      `config = {}` or the Lua 5.5 `global` forms — so **bare globals are unchanged by
      construction**, which the untouched TC-17/TC-31 confirm. A deviation from design §3.4, whose
      C3/C4 steps write `newName` and `dLeaf.text`; recorded in §3.4 itself.
- [x] **TC-37 / TC-38 pin it**, and their mutant is the code as it stood: restoring the bare-segment
      lookup reddens both at `conflictsFromRenamingTo`'s "applied silently" assertion, with the
      other eight cases in the class green.
- [x] **TC-39 pins the fourth iteration block.** The two Phase-4 cancellation cases both use a
      `GLOBAL_FUNCTION` target, so `shadows` — which runs only for a file-local kind — was pinned by
      nothing. The third case is differential over file size: 3 name refs → 4 checks, 13 → 14.
      Deleting the check collapses both to 1.
- [x] **TC-40 restores DR-05's deleted probe as coverage.** DR-05's result rests on a two-part
      coupling — member-field navigation hands back a `LuaNameRef`, and `identifierLeafOf`'s
      `LuaNameRef` branch is guarded by `kindOf` — and nothing enforced either. Dropping the guard
      reddens it; a *guard* was rejected instead, being production code outside the plan and shared
      with Safe Delete.
- **Gate, counted from the builder's result XML rather than a Gradle summary line:** full suite
      **2,826 tests / 455 classes / 0 failures** (Phase-4 baseline 2,822 / 455 / 0; +4, all four new
      cases, no class added and none lost), corpus sweep **2,834 / 458 / 0** (baseline 2,830 / 458 /
      0), `ktlintCheck` clean, `lint_docs` and `lint_planning` 0 errors. The single `skipped` in both
      counts is `LuaCompletionTest`'s pre-existing ignored COMP-03 case.
- [x] **Gap 2.14 filed as [[BUG-465]]** with a roadmap row.
- [x] **Gap 2.15 ([[BUG-466]]) closed in this phase, after its deferral reason was disproved.** It
      was first filed as deferred on the grounds that reporting it would anchor a collision on an
      element the probe proves is in the usage set, which design §2.4 forbade. **That platform claim
      is false.** `RenameUtil.removeConflictUsages` (`RenameUtil.java:297-307`) removes only
      `UnresolvableCollisionUsageInfo` *instances*, not every usage sharing an anchor element, and
      `UsageInfo.equals` (`UsageInfo.java:348-359`) opens with a `getClass()` test so the two can
      never displace one another in the set. With the objection void the fix is one term:
      `globalDeclarationsNamed` adds `LuaMemberFieldNavigation.find`, making C3/C4's candidate set
      the one `LuaNameReference.doMultiResolve` already consults. Two cases added —
      `testDottedFunctionBesideAFieldAssignmentIsReported` (the conflict) and
      `testCollisionAnchoredOnAUsageIsStillRewritten` (Continue, via `withIgnoredConflicts`, still
      rewrites the anchor) — and dropping the new term reddens both. `LuaMemberFieldNavigation.find`
      gained `checkCanceled()` at the two levels whose bodies can force work (parse a file, walk its statements) for the new rename-time caller.
- [x] **The false claim removed everywhere it was written**: `LuaRenameConflictDetector.kt`'s two
      KDocs, `LuaRenameConflictTest`'s TC-14 KDoc, design §2.4 and §3.4, `risks-and-gaps.md`
      Gap 2.15, this plan, [[BUG-466]]'s report, the roadmap row, and REFACT-04 design §2.3, which
      had inherited it verbatim as "REFACT-01 §2.4's rule, applied unchanged".

### Phase 5: `require(...)` rewriting on file rename [Should]

- **Goal**: renaming `util.lua` rewrites `require("app.util")`.
- **Tasks**:
  - [x] Add `LuaElementFactory.createStringLiteral(project, literalText)` — realizes design §3.7
        step 5.
  - [x] Add `LuaRequireReference.handleElementRename` — realizes design §2.7 and §3.7 steps 1-7.
- **Exit criteria**: `LuaRequireRenameTest` passes TC-18a/b/c; `LuaElementFactoryTest` extended and
  green; full suite green.

#### Phase 5 record (2026-08-23)

- [x] **The requirement's `(inferred)` claim was executed, and it held.** On the parent commit
      `a8424c14`, `myFixture.renameElement(utilLuaFile, "helpers.lua")` throws
      `com.intellij.diagnostic.PluginException: No ElementManipulator instance registered for
      TERMINAL_EXPR [LuaTerminalExprImpl]` — and the `ARGS` variant for the two paren-less shapes —
      with the consuming file left untouched. So this phase does not merely add a rewrite: it
      repairs a file rename that **failed outright** whenever any `require` named the file.
      That is also the baseline red for TC-18a/b/c.
- [x] **Design §3.7's seven steps survived contact with real PSI, with one correction and one
      simplification.** Measured by probe before implementing: all three call shapes contribute
      exactly one `LuaRequireReference` and `ReferencesSearch.search(targetFile)` finds it —
      `TERMINAL_EXPR:(0,6)` for `require("util")`, `ARGS:(0,10)` for `require 'app.util'`,
      `ARGS:(0,8)` for `require [[util]]` — so §3.7's "why the file is found at all" is confirmed
      rather than assumed. `createExpression` yields a `LuaTerminalExpr` whose `getString()` is a
      single `STRING` token for `"x"`, `'x'`, `[[x]]` **and** `[==[x]==]`, so the merging lexer
      adapters need no special handling. **Correction:** step 5 as written returns a *truncated*
      literal for text that merely starts with a string — see the next item. **Simplification:**
      step 1 reads the literal off `rangeInElement`; the module string PSI is needed anyway for
      step 6, so its `text` is used directly and the two steps share one lookup.
- [x] **Step 5 gained a round trip, and TC-18d with it.** `createStringLiteral` returns the STRING
      leaf only when its text equals the requested text. Without that, a file renamed to
      `he"lpers.lua` builds `local _ = "he"lpers"`, whose first `LuaExpr` is the complete-looking
      `"he"` — and `require("util")` would be rewritten to `require("he")`. A double quote is a
      legal POSIX file-name character and the rename **does** reach `handleElementRename` with one
      (measured on the parent commit), so this is a reachable silent corruption, not a defensive
      branch. TC-18d drives the real file rename and asserts both that the file *was* renamed and
      that the `require` was left alone.
- [x] **TC-18b and TC-18c's fixtures were incomplete in the TC table above** and were corrected in
      the test: both name a rename of `util.lua` / `app/util.lua` but list only `main.lua` in the
      Input column. The target has to exist — there is nothing to rename otherwise, and
      `CachesBasedRefSearcher` only reports the usage because `LuaRequireReference.resolve()`
      reaches the file. Each case builds its own fixture set; no fixture is shared between cases.
- [x] **`LuaRequireReferenceContributor.requireArgumentString` moved to
      `LuaRequireReference.moduleStringOf`** rather than being duplicated. The host dispatch
      (`LuaTerminalExpr.string` vs `LuaArgs.string`) is now read by both the contributor that
      creates the reference and the rename that rewrites it, so the two cannot drift apart.
      `LuaRequireStringCallReferenceTest` (`exactlyOneReferenceIsContributedPerCall` included) and
      `LuaRequireTypeFlowTest` stay green.
- [x] **Five mutants, all executed, each reddening a different set.** M1 drop the round trip →
      TC-18d + `testCreateStringLiteralIsNullWhenTheTextIsNotOneLiteral` (2 of 14). M2 emit `"…"`
      instead of the captured delimiters → TC-18b + TC-18c. M3 drop the dotted prefix → TC-18b
      alone. M4 keep the `.lua` suffix in the module name → TC-18a + TC-18b + TC-18c. M5 normalise
      the delimiters inside the factory → `testCreateStringLiteralKeepsEveryDelimiterForm` +
      `testCreateStringLiteralIsNullWhen…` + TC-18b + TC-18c. The cannot-fail case searched for and
      found: TC-18d as first written asserted only that the consuming file was unchanged, which is
      green for a rename that never happened at all; the `target.name` assertion is what
      distinguishes "declined the literal" from "did nothing". A second was found and removed
      outright — an `assertNull` on the unterminated literal `"helpers`, which asserted a false
      claim about the lexer (it lexes to a STRING covering the remainder, round-trips, and is
      returned); `renamedLiteral` cannot produce one, so the case was dropped rather than inverted.

### Phase 6: LuaCATS `@param` propagation [Should]

- **Goal**: renaming a parameter moves its `---@param` tag.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaCatsParamRenamer` — realizes design
        §2.8 and §3.6.
  - [x] Wire the `---@param` propagation step of `LuaRenameProcessor.renameElement` (step 5 in the
        Phase-6 numbering; step 3a + step 4 after Phase 8) — realizes design §3.3.
- **Exit criteria**: `LuaCatsParamRenameTest` passes TC-20a/b/c; full suite green. **Met
  (2026-08-23)**, with four cases added beyond the plan's three: TC-20a/b/c alone leave the
  behaviour under-pinned, because only TC-20a can fail when the feature is absent. TC-20e (only the
  matching tag moves) and TC-20f (a `function` expression's comment owner is the enclosing `local`)
  are the other two gates; TC-20d (a refused rename does not move the tag — Gap 2.13's converse) and
  TC-20g (a tag naming a *different* parameter is untouched) are guards with executed mutants.

### Phase 8: Atomic application [Must]

- **Goal**: close [[BUG-468]] — make `renameElement` all-or-nothing under cancellation, so
  `REFACT-01-01` can be `Full`. Today a Cancel at usage *k* leaves *k*-1 usages on the new name and
  the declaration on the old one, silently. This phase realizes design **§3.3 steps 3, 3a and 4** and the
  scope correction under it.
- **Why it precedes Phase 7**: Phase 7 is a `Could`; this is the last open half of a `Must`, and it
  is the requirement's own "data-loss-class defect". Phase 7 also *adds* to the usage array (non-code
  usages), so it should land on an application path that is already atomic.
  **This ordering rests on one premise that is not yet established** — that a user can reach
  [[BUG-468]] at all, rather than only a test harness driving `cancel()` directly. `REFACT-01-00-DR-10`
  settles it and is the **first** task of this phase, ahead of any code: if the **Stop** button never
  paints for realistic renames, the `Must` is not live, and Phase 7 — which ships user-visible
  behaviour — should go first. Whichever way it lands, the fix itself still ships; only the order is
  at stake.
- **The decision this phase implements, and the measurements behind it.** [[BUG-468]] §5 offered two
  candidates. Candidate 1 ("drain the rewrites into one atomic step") is chosen, and the naive form
  of it is not enough — all four of these were executed on `5b7c6ca4` before the phase was written,
  and each one changes what the code must do:
  - `ProgressManager.checkCanceled()` in `renameElement` **is** reachable: a `CheckCanceledHook`
    filtered by `StackWalker` to the processor's own frames fires three times for a three-usage
    rename, each under a live `PotemkinProgress` with `isInNonCancelableSection = false`. The
    Phase-6 review's open question is answered: the contract cost of touching it is real.
  - Cancelling that indicator at the **2nd** processor check leaves **exactly one** of the three
    occurrences on the new name, the declaration on the old one, and `thrown = null`. At the
    **1st** check the file is untouched — so the trap [[BUG-468]] §6 names has a second edge: a test
    that cancels at the first check is green on the defect. **Do not expect a particular text.**
    Three runs on this fixture each moved a different occurrence; the usage array preserves
    `findReferences`' order (`RenameUtil.java:93-101, 133-142`) and ours is
    `ReferencesSearch…findAll()`, a `Query` with no specified ordering. Design §3.3's `d8e571e2`
    subsection carries the retraction and the assertable property.
  - Precomputing every swap is **not sufficient**. With all three usage swaps prepared and the live
    indicator cancelled after the first `replaceChild`, the unwrapped loop throws and leaves exactly
    one occurrence on the new name. The cancellation point is **not** `PomModelImpl`'s:
    `CompositeElement.replaceChild` (`:647`) → `ChangeUtil.prepareAndRunChangeAction` (`:659`), whose
    `new PomTransactionBase(changedElement.getPsi())` argument (`ChangeUtil.java:148`) is evaluated
    before `runTransaction` — and therefore before `PomModelImpl.java:112`'s own section — and
    `CompositeElement.getPsi()` opens with `ProgressIndicatorProvider.checkCanceled()` (`:719-720`).
  - The same run wrapped in `ProgressManager.getInstance().executeNonCancelableSection { … }`
    applies all three and throws nothing. That is why the section is in the design.
- **Tasks**:
  - [ ] Add `preparedUsageRewrites(usages, newName)` and `preparedUsageRewrite(usage, newName)` to
        `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` — realizes design §3.3
        step 3, **including its five sub-steps in that order**. `ProgressManager.checkCanceled()` is
        the first statement of every iteration of `preparedUsageRewrites`, and it is the **only**
        `checkCanceled` left anywhere in this method's write path.
  - [ ] Replace `LuaCatsParamRenamer.rename` with
        `fun preparedRename(parameterIdentifier: PsiElement, oldName: String, newName: String): (() -> Unit)?`
        — realizes design §2.8 and §3.6. Steps 1-3 are the existing body with `return` → `return null`;
        step 4 returns `{ taggedName.replaceWithText(newName) }` instead of calling it. **Delete
        `rename`; do not keep both.** It has one caller (`LuaRenameProcessor.kt:216`) and no test
        calls it. **This is the Step-9 review's F1 correction and it is load-bearing, not tidying**:
        the lookup expands a `LuaCatsLazyCommentImpl` chameleon (`getParamTagList` → `innerComment()`
        → `LazyParseablePsiElement.getFirstChild():88-89` → `LazyParseableElement.getFirstChildNode():233-235`
        → `ensureParsed():156`), i.e. it **parses**, on an input sized by the user's doc comment. It
        must not run inside the non-cancelable section.
  - [ ] Rewrite `LuaRenameProcessor.renameElement` to the design §3.3 body: capture, resolve the
        declaration rewrite, resolve the usage rewrites, resolve the `---@param` rewrite
        (**step 3a** — `LuaCatsParamRenamer.preparedRename(element, oldName, newName)`, note the
        argument is `element`, not `replacement`, because it runs before the swap), then apply all of
        them inside one `ProgressManager.getInstance().executeNonCancelableSection { … }`, then
        `listener?.elementRenamed(replacement)` outside it. The `usages.forEach { checkCanceled();
        RenameUtil.rename(...) }` loop is deleted, not amended. **Nothing that parses may be inside
        the section** — the one exception is step 3.3's delegating closure, which is unreachable
        today and documented as such in design §3.3.
  - [ ] New imports: `net.internetisalie.lunar.lang.LuaNameReference` and
        `net.internetisalie.lunar.lang.psi.LuaElementTypes`. `com.intellij.refactoring.rename.RenameUtil`
        stays — it is still called, from the delegating closure of §3.3 step 3.3.
  - [ ] Update `LuaRenameProcessor`'s class KDoc: the *"Everything that can fail is resolved BEFORE
        the first edit"* paragraph is about failures and stays; add what it does not cover today and
        now does — a user's Cancel between two edits — and drop nothing.
  - [ ] Correct `LuaCatsParamRenamer`'s KDoc, on three points. (a) Its cancellation paragraph says
        the upstream usage loop's *"exposure is bounded by one usage rather than by the whole
        declaration"*; [[BUG-468]] §2 retracted that (the bound is on latency, not damage) and this
        phase removes the exposure entirely — reference Gap 2.17 as **closed**, not as a standing
        hazard. (b) Its argument for carrying no `checkCanceled` in the tag scan **survives but for a
        different reason**: the scan now runs before the first edit, so it is no longer "after
        `renameElement` has already rewritten the declaration and every usage" — that clause is now
        false and must go. (c) The object-level KDoc claims the tag scan involves "no index or VFS
        read", which is still true, and must additionally say what the F1 review found: it **does**
        expand the LuaCATS chameleon, which is why the object now hands back a closure. (d) The paragraph
        *"[oldName] is a declared parameter because it cannot be recovered here"* is **false after the
        hoist** — the lookup now runs before the swap, so `parameterIdentifier.text` is the old name.
        Keep the parameter, restate the reason: it makes the selection independent of which leaf the
        caller passes (design §3.6).
  - [ ] Correct `LuaRenameProcessor.renameElement`'s KDoc paragraph that says
        *"[LuaCatsParamRenamer] re-derives the comment owner from [replacement] instead of from a
        value captured in step 1"*. After this phase the owner is derived from `element` **before**
        the swap, which removes the dependency on "the ancestor chain is unchanged by the swap"
        rather than restating it. Do not leave the old paragraph beside the new call.
  - [ ] **No `plugin.xml` change and no new `LuaBundle` key.** `renamePsiElementProcessor` at
        `plugin.xml:389-390` already points at this class, and this phase adds no user-visible
        message — a cancelled refactoring stays silent, which is the platform's own convention
        (`BaseRefactoringProcessor.java:659-662`). If an implementer finds themselves adding a
        bundle key, they have built Alternative F, which design §9 rejects.
- **Exit criteria**: `REFACT-01-00-DR-10` executed and its answer recorded in Gap 2.17 **before any
  code is written** (it can still change this phase's position in the order; see "Why it precedes
  Phase 7"); `LuaRenameTest` passes TC-43, TC-44 and TC-45; all existing rename tests stay green —
  in particular TC-36 (`testUnbuildableNewNameRefusesBeforeAnythingIsRewritten`), which pins the
  *failure*-atomicity `d8e571e2` delivered and which the rewritten `renameElement` must not regress,
  and TC-20a…TC-20g, which the `LuaCatsParamRenamer` signature change must not disturb; full suite
  green via `run "test --rerun --no-build-cache"`; and **every mutant listed in the mutation-proof
  task below** — there are three — executed and restored. (An earlier version of this line said
  "both mutants" against a task listing three. It is the seventh count error in this feature's
  artifacts; state the property, not a number, wherever a list can grow.)

### Phase 7: In-place rename and non-code search [Could]

- **Goal**: the inline editor template for file-local locals, and working "search in comments and
  strings" checkboxes.
- **Tasks**:
  - [ ] Implement `LuaRefactoringSupportProvider.isInplaceRenameAvailable` — realizes design §2.6.
  - [ ] Create `net.internetisalie.lunar.settings.LuaRefactoringSettings` and register the
        `<applicationService>` — realizes design §2.9 and §7.
  - [ ] Implement the remaining five non-code-search accessors **exactly as design §2.9 writes
        them** (the sixth, `getQualifiedNameAfterRename`, shipped in Phase 2) —
        `getElementToSearchInStringsAndComments` returns `element.parent as? LuaNameRef`, not
        `element`. Returning the leaf (or omitting the override, whose default *is* the leaf) makes
        the checkboxes inert: the leaf is not a `PsiNamedElement`, so
        `ElementDescriptionUtil.getElementDescription` falls through to `element.toString()`
        (`ElementDescriptionUtil.java:26`) and the search runs against a debug string. TC-13c is the
        guard. Realizes design §2.2 and §2.9.
- **Exit criteria**: `LuaInplaceRenameTest` passes TC-12; `LuaRenameTest` passes TC-13b, TC-13c and
  TC-13e; full suite green; the `verify-in-ide` checklist below is executed.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :--- | :---: | :--- |
| `REFACT-01-01` | M | Phase 2 (the uncancelled path) + **Phase 8** (the cancelled path — [[BUG-468]] / Gap 2.17). The row is `Partial` in `requirements.md` and becomes `Full` when Phase 8 ships, not before. |
| `REFACT-01-02` | M | Phase 2 |
| `REFACT-01-03` | M | Phase 1 (search key) + Phase 2 |
| `REFACT-01-04` | M | Phase 2 |
| `REFACT-01-05` | S | Phase 2 |
| `REFACT-01-06` | M | Phase 2 |
| `REFACT-01-07` | M | Phase 1 (§3.5 rows 5, 7, 14 + the §2.10 index extension) + Phase 2 (rename); C4's ambiguity report arrives in Phase 3. Covers all four global forms — `function greet()` (TC-08), bare `config = {}` (TC-27), Lua 5.5 `global x` (TC-28), Lua 5.5 `global function f` (TC-29). |
| `REFACT-01-08` | S | Phase 1 (classification) + Phase 2 (the funcName receiver-segment refusal, §3.1 step 4a — it must ship with the processor, not with Phase 4, because §3.5 row 9 makes the receiver a rename target from Phase 1) + Phase 4 |
| `REFACT-01-09` | C | **Deferred** — refusal path ships in Phase 2; see `risks-and-gaps.md` TBD-1 / DR-05 |
| `REFACT-01-10` | M | Already `Full` (`LuaNamesValidator`); regression-locked by TC-11 in Phase 2 |
| `REFACT-01-11` | C | **Deferred** — owned by REFACT-05; see `risks-and-gaps.md` TBD-3 |
| `REFACT-01-12` | S | Phase 7 |
| `REFACT-01-13` | S | Phase 2 (dialog + preview become reachable); conflicts arrive in Phase 3 |
| `REFACT-01-14` | S | Phase 3 |
| `REFACT-01-15` | C | Phase 7 (the five accessors that deliver the requirement) — **plus `getQualifiedNameAfterRename` in Phase 2**, which delivers none of it but stops the dialog's checkbox driving `RenameUtil.java:226`'s `LOG.error` the moment the processor is registered |
| `REFACT-01-16` | S | Phase 6 (`@param`); `@class`/`@alias` deferred — `risks-and-gaps.md` TBD-2 / DR-04 |
| `REFACT-01-17` | C | No work — REFACT-04 owns it; Phase 2 must not regress it (TC-24) |
| `REFACT-01-18` | S | Phase 5 |
| `REFACT-01-19` | W | Phase 2 (refusal + TC-19a/b/c) |
| `REFACT-01-20` | W | Phase 7 (default-off non-code search) + the Phase 2 preview path |

## Test Cases

Every case below is `BasePlatformTestCase` + `myFixture.configureByText`, per the engineering
contract §5. Rename is driven with `myFixture.renameElementAtCaret(newName)`, which calls
`RenamePsiElementProcessor.forElement(...).substituteElementToRename(...)` and then
`RenameProcessor(...).run()` (`CodeInsightTestFixtureImpl.java:1092-1107`) — so the substitution and
collision paths are genuinely exercised.

**Two cases must NOT use `renameElementAtCaret`, and the reason is mechanical, not stylistic:**

- **Non-code search (TC-13d, TC-13e).** `renameElementAtCaret` delegates to the two-argument
  `renameElement`, which **hard-codes** `searchInComments = false` and `searchTextOccurrences = false`
  (`CodeInsightTestFixtureImpl.java:1092-1096`), so no test written on it can ever enter
  `RenameUtil.processUsages`' non-code branches. Use the four-argument
  `myFixture.renameElement(element, newName, /* searchInComments = */ true, /* searchTextOccurrences = */ true)`
  (declared `CodeInsightTestFixture.java:779`, implemented `CodeInsightTestFixtureImpl.java:1098-1107`)
  — it still runs `substituteElementToRename` and `RenameProcessor.run()`, and it is the only fixture
  entry point that propagates the flags. Constructing
  `RenameProcessor(project, leaf, newName, /* isSearchInComments = */ true, /* isSearchTextOccurrences = */ true).run()`
  directly (`RenameProcessor.java:99-105`) is the equivalent lower-level form; it skips the
  substitution step, so pass the already-substituted leaf.
- **Anything caret-positioned on a token with no `PsiNamedElement` ancestor (TC-19b).**
  `myFixture.getElementAtCaret()` calls `fail("element not found in file …")` when
  `TargetElementUtil.findTargetElement` returns null (`EditorTestFixture.java:318-330`), and for
  `local function f(<caret>...)` it does: the ELLIPSIS token carries no reference, and
  `TargetElementUtilBase.getNamedElement` finds no non-`PsiFile` `PsiNamedElement` ancestor
  (`TargetElementUtilBase.java:106-126`) because `LuaParList` / `LuaFuncBody` / `LuaLocalFuncDecl`
  are all plain `ASTWrapperPsiElement`s. Such a case must take the leaf from
  `myFixture.file.findElementAt(myFixture.caretOffset)`.

**Every refusal case asserts a thrown exception and its message, never a `null` return.**
`CommonRefactoringUtil.showErrorHint` short-circuits in unit-test mode and throws
`CommonRefactoringUtil.RefactoringErrorHintException(message)` (`CommonRefactoringUtil.java:79-86`),
so `substituteElementToRename` never reaches its `return null` under `BasePlatformTestCase`.
Asserting the *message* is also the only way to tell which refusal branch fired — a test that merely
asserted "the text did not change" would pass for every branch, including the wrong one. The repo's
existing `LuaUnsupportedRenameProcessorTest.testRefusesWithAnExplanation`
(`LuaUnsupportedRenameProcessorTest.kt:43-62`) is the pattern to copy; it is deleted with its subject
in Phase 2, so the idiom lives here.

| TC | Req | Test class · method | Input (`test.lua`, `<caret>` shown) | Action | Expected output |
| :-- | :-- | :--- | :--- | :--- | :--- |
| TC-01 | -01 | `LuaRenameTest.testRenameLocalAndAllUsages` | `local coun<caret>ter = 0`<br>`counter = counter + 1`<br>`print(counter)` | rename → `total` | `local total = 0`<br>`total = total + 1`<br>`print(total)` |
| TC-02 | -02 | `LuaRenameTest.testRenameFromUsageSite` | `local counter = 0`<br>`print(coun<caret>ter)` | rename → `total` | `local total = 0`<br>`print(total)` |
| TC-03 | -03 | `LuaRenameTest.testShadowedLocalsStayIsolated` | `local <caret>x = 1`<br>`do`<br>`  local x = 2`<br>`  print(x)`<br>`end`<br>`print(x)` | rename → `y` | only the outer declaration and the trailing `print` change: `local y = 1` … `local x = 2` … `print(x)` … `print(y)` |
| TC-04 | -04 | `LuaRenameTest.testRenameParameter` | `local function f(<caret>a)`<br>`  return a + 1`<br>`end` | rename → `b` | `local function f(b)`<br>`  return b + 1`<br>`end` |
| TC-05 | -05 | `LuaRenameTest.testRenameNumericForVariable` | `for <caret>i = 1, 3 do print(i) end` | rename → `idx` | `for idx = 1, 3 do print(idx) end` |
| TC-06 | -05 | `LuaRenameTest.testRenameGenericForVariable` | `for <caret>k, v in pairs(t) do print(k, v) end` | rename → `key` | `for key, v in pairs(t) do print(key, v) end` |
| TC-07 | -06 | `LuaRenameTest.testRenameLocalFunctionWithRecursiveCall` | `local function <caret>fact(n)`<br>`  if n <= 1 then return 1 end`<br>`  return n * fact(n - 1)`<br>`end`<br>`print(fact(5))` | rename → `factorial` | all three occurrences become `factorial` |
| TC-08 | -07 | `LuaRenameCrossFileTest.testRenameGlobalFunctionAcrossFiles` | `a.lua`: `function <caret>greet() end`<br>`b.lua`: `greet()` | rename → `hello` | `a.lua`: `function hello() end`; `b.lua`: `hello()` |
| TC-09 | -08 | `LuaRenameTest.testRenameDottedFunctionDeclaration` | `M = {}`<br>`function M.<caret>run() end`<br>`M.run()` | rename → `start` | `function M.start() end`<br>`M.start()` — `M` unchanged |
| TC-10 | -08 | `LuaRenameTest.testColonMethodDeclarationIsRefused` | `Obj = {}`<br>`function Obj:<caret>m() end`<br>`local o = Obj`<br>`o:m()` | `LuaRenameProcessor().substituteElementToRename(elementAtCaret, null)` | throws `RefactoringErrorHintException` whose message contains the `refactoring.rename.colonMethod` text; file text unchanged |
| TC-11 | -10 | `LuaRenameTest.testKeywordIsNotAValidNewName` | n/a | `LuaNamesValidator().isIdentifier("end", project)` / `("2x", project)` / `("total", project)` | `false`, `false`, `true` |
| TC-12 | -12 | `LuaInplaceRenameTest.testInplaceRenameLocal` | `local coun<caret>ter = 0`<br>`print(counter)` | `CodeInsightTestUtil.doInlineRename(VariableInplaceRenameHandler(), "total", myFixture)` | `local total = 0`<br>`print(total)` |
| TC-13a | -13 | `LuaRenameCrossFileTest.testPreviewListsEveryUsage` | `a.lua`: `function <caret>greet() end`<br>`b.lua`: `greet()`<br>`c.lua`: `greet()` | `RenameProcessor(project, leaf, "hello", false, false).findUsages()` | 2 `UsageInfo`s, in `b.lua` and `c.lua` |
| TC-13b | -13 | `LuaRenameTest.testPreviewButtonIsOffered` | `local <caret>x = 1` | `LuaRenameProcessor().showRenamePreviewButton(leaf)` | `true` |
| TC-13c | -15 | `LuaRenameTest.testNonCodeSearchTargetsTheNamedComposite` | **(a)** `local <caret>counter = 0` **(b)** `for <caret>i = 1, 3 do end` | `LuaRenameProcessor().getElementToSearchInStringsAndComments(leaf)`, then `ElementDescriptionUtil.getElementDescription(result, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS)` when non-null | **(a)** the result is the enclosing `LuaNameRef` (`result is LuaNameRef`) and the description is exactly `"counter"` — **not** the leaf's `toString()`; **(b)** the result is `null` (a numeric-`for` variable has no `LuaNameRef` parent, `lua.bnf:152`), which `RenameUtil.java:147, 157` treats as "no non-code search". Part (a) is the assertion that fails if the override is dropped: the platform default returns the leaf, which is not a `PsiNamedElement`, so `ElementDescriptionUtil.java:26` falls back to `element.toString()` and the checkbox searches for a debug string. |
| TC-13d | -15 | `LuaRenameTest.testSearchInCommentsDoesNotLogAnUnknownElementType` | `local coun<caret>ter = 0`<br>`counter = counter + 1`<br>`print(counter)` | `myFixture.renameElement(myFixture.elementAtCaret, "total", /* searchInComments = */ true, /* searchTextOccurrences = */ true)` | **no exception**, and all four code occurrences become `total`. **Phase 2 gate.** Without `getQualifiedNameAfterRename` this throws a `TestLoggerAssertionError` carrying `"Unknown element type : "` — the renamed element is a `LeafPsiElement`, the base hook returns null (`RenamePsiElementProcessorBase.java:106-108`) and `RenameUtil.java:226` logs. The `stringToSearch.isEmpty()` guard does **not** save it: in Phase 2 `getElementToSearchInStringsAndComments` is still the default, which returns the leaf, whose `toString()` is a non-empty debug string. Mutation check: delete the override and this must go red on the logged error, not on a text assertion. |
| TC-13e | -15 | `LuaRenameTest.testSearchInCommentsRewritesTheComment` | `-- counter tracks the total`<br>`local coun<caret>ter = 0`<br>`print(counter)` | `myFixture.renameElement(myFixture.elementAtCaret, "total", /* searchInComments = */ true, /* searchTextOccurrences = */ false)` | `-- total tracks the total`<br>`local total = 0`<br>`print(total)`. **Phase 7 gate, and the only end-to-end case for REFACT-01-15.** It is red in two independent ways: drop `getElementToSearchInStringsAndComments` and the searched string becomes the leaf's `toString()` (`ElementDescriptionUtil.java:26`), so the comment is untouched; drop `getQualifiedNameAfterRename` and it throws before the comment is reached. |
| TC-14 | -14 | `LuaRenameConflictTest.testCaptureOfRenamedUsageIsReported` | `local <caret>x = 1`<br>`local y = 2`<br>`print(x)` | rename → `y` | `ConflictsInTestsException`; its messages contain the `conflict.capture` text |
| TC-15 | -14 | `LuaRenameConflictTest.testExistingReferenceShadowedIsReported` | `local y = 1`<br>`local <caret>x = 2`<br>`print(y)`<br>`print(x)` | rename → `y` | `ConflictsInTestsException`; messages contain the `conflict.shadow` text |
| TC-16 | -14 | `LuaRenameConflictTest.testUnrelatedInnerDeclarationIsNotAConflict` | `local <caret>x = 1`<br>`print(x)`<br>`do local y = 3 end` | rename → `y` | **no** exception; result is `local y = 1`<br>`print(y)`<br>`do local y = 3 end` |
| TC-17 | -14 | `LuaRenameConflictTest.testExistingGlobalIsReported` | `a.lua`: `function <caret>greet() end`<br>`b.lua`: `function hello() end` | rename → `hello` | `ConflictsInTestsException`; messages contain the `conflict.globalExists` text |
| TC-18a | -18 | `LuaRequireRenameTest.testRenameFileRewritesParenthesizedRequire` | `util.lua`: `return {}`<br>`main.lua`: `local u = require("util")` | rename `util.lua` → `helpers.lua` | `main.lua`: `local u = require("helpers")` |
| TC-18b | -18 | `LuaRequireRenameTest.testRenameFilePreservesDottedPrefixAndQuoteStyle` | `main.lua`: `local u = require 'app.util'` | rename `app/util.lua` → `helpers.lua` | `local u = require 'app.helpers'` |
| TC-18c | -18 | `LuaRequireRenameTest.testRenameFileRewritesLongBracketRequire` | `main.lua`: `local u = require [[util]]` | rename `util.lua` → `helpers.lua` | `local u = require [[helpers]]` |
| TC-19a | -19 | `LuaRenameTest.testSelfInsideAMethodIsRefusedAsTheMethod` | `Obj = {}`<br>`function Obj:m()`<br>`  return se<caret>lf`<br>`end` | resolve the caret with `TargetElementUtil.findTargetElement(editor, ELEMENT_NAME_ACCEPTED or REFERENCED_ELEMENT_ACCEPTED)`, then `LuaRenameProcessor().substituteElementToRename(target, null)` | **(a)** the resolved target is the `m` IDENTIFIER leaf of `function Obj:m()` — assert `target.text == "m"` and `target.parent.parent is LuaFuncNameMethod`, **not** `Obj`; **(b)** the call throws `RefactoringErrorHintException` whose message contains the `refactoring.rename.colonMethod` text. Part (a) is the assertion that would have caught the "resolves to the class leaf" error; part (b) is the assertion that fails if a `self` guard is reintroduced with a different message. |
| TC-19b | -19 | `LuaRenameTest.testVarargIsNotClaimed` | `local function f(<caret>...) end` | `val leaf = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset))`, then assert `leaf.elementType == LuaElementTypes.ELLIPSIS` (`LuaElementTypes.java:78`) and call `LuaRenameProcessor().canProcessElement(leaf)` | `false`. **`myFixture.elementAtCaret` must not be used here**: it calls `fail("element not found in file …")` when the caret resolves to nothing (`EditorTestFixture.java:318-330`), and an ELLIPSIS has no reference and no `PsiNamedElement` ancestor (`TargetElementUtilBase.java:106-126`), so the test would fail on the fixture rather than exercise the predicate. |
| TC-19c | -19 | `LuaRenameTest.testExplicitSelfParameterRenamesNormally` | `Obj = {}`<br>`function Obj.m(<caret>self, x)`<br>`  return self`<br>`end` | rename → `this` | `function Obj.m(this, x)` … `return this` — the dot form's `self` is an ordinary parameter and must **not** be refused. This is the case a text-based `self` guard would have broken. |
| TC-20a | -16 | `LuaCatsParamRenameTest.testParamTagFollowsParameter` | `---@param a number`<br>`local function f(<caret>a) return a end` | rename → `count` | `---@param count number`<br>`local function f(count) return count end`. The caret is on the **parameter**, which is the only position that works: with the caret inside the comment, `canProcessElement` is false (a `LuaCatsArgName` is neither a `LuaNameRef` nor a declaration leaf, §3.0) and `renameElementAtCaret` cannot drive the refactoring at all. |
| TC-20b | -16 | `LuaCatsParamRenameTest.testVariadicParamTagIsUntouched` | `---@param ... any`<br>`local function f(<caret>x, ...) return x end` | rename `x` → `first` | the `@param ...` line is unchanged |
| TC-20c | -16 | `LuaCatsParamRenameTest.testMissingTagIsANoOp` | `---@return number`<br>`local function f(<caret>a) return a end` | rename → `b` | comment unchanged; parameter and body renamed |
| TC-21 | — | `LuaDeclarationSiteTest.testKindOfEveryDeclarationShape` | one fixture per row of design §3.5 | `LuaDeclarationSite.kindOf(leaf)` | the kind named in that row; `null` for a table-constructor key and for a plain usage |
| TC-22 | — | `LuaDeclarationSiteTest.testIdentifierLeafOfNormalisesBothDirections` | `function M.run() end` | `identifierLeafOf(funcDecl)` and `identifierLeafOf(nameRef)` | both return the `run` leaf, not `M` |
| TC-23 | -08 | `LuaFindUsagesTest.testCanFindUsagesForDottedFunction` | `M = {}`<br>`function M.run() end`<br>`M.run()` | `LuaFindUsagesProvider().canFindUsagesFor(runLeaf)` + `ReferencesSearch.search(runLeaf)` | `true`; exactly 1 reference |
| TC-24 | -17 | `LuaLabelRenameTest` (existing, unchanged) | — | full class | still green — `LuaRenameProcessor` must not claim `LuaLabelName`/`LuaLabelRef` |
| TC-25 | -17 | `LuaRenameTest.testLabelsAreNotClaimed` | `::ret<caret>ry::`<br>`goto retry` | `LuaRenameProcessor().canProcessElement(x)` for the `LuaLabelName`, for its IDENTIFIER child, and for the `LuaLabelRef` in `goto retry` | `false` for all three. TC-24 proves label rename still works; **this** proves *why*, and it is the direct unit-level guard on design §3.0 rule 1. Mutation check: delete the `LuaLabelName`/`LuaLabelRef` line from `canProcessElement` and TC-25 must go red. |
| TC-26 | -01 | `LuaRenameTest.testUnresolvableUsageIsRefusedNotHalfApplied` | `print(unde<caret>fined_name)` | `LuaRenameProcessor().substituteElementToRename(elementAtCaret, null)` | throws `RefactoringErrorHintException` containing the `refactoring.rename.unresolved` text; file text unchanged. Guards design §3.0 rule 3: if `canProcessElement` claimed only declaration leaves, the platform default would rename this one occurrence silently. |
| TC-27 | -07 | `LuaRenameCrossFileTest.testRenameBareGlobalAssignmentAcrossFiles` | `a.lua`: `con<caret>fig = {}`<br>`b.lua`: `print(config)` | rename → `settings` | `a.lua`: `settings = {}`; `b.lua`: `print(settings)`. Exercises §3.5 row 14 + `isGlobalAssignmentTarget`; the canonical Lua global, which no other TC covers. |
| TC-28 | -07 | `LuaRenameCrossFileTest.testRenameLua55GlobalVariableAcrossFiles` | `a.lua`: `global c<caret>ount = 0`<br>`b.lua`: `print(count)` | rename → `total` | `a.lua`: `global total = 0`; `b.lua`: `print(total)`. Fails if §3.5 row 5 is missing (the `LuaAttName` row would make it `LOCAL_VARIABLE`, §3.2 would narrow to one file, and `b.lua` would keep `count`). |
| TC-29 | -07 | `LuaRenameCrossFileTest.testRenameLua55GlobalFunctionAcrossFiles` | `a.lua`: `global function gr<caret>eet() end`<br>`b.lua`: `greet()` | rename → `hello` | `a.lua`: `global function hello() end`; `b.lua`: `hello()`. Fails with "cannot refactor" if §3.5 row 7 is missing — `globalFuncDecl` has no `funcName` node, so row 9 does not match it. |
| TC-30 | — | `LuaDeclarationSiteTest.testDeclarationNodeOfCoversTheNewKinds` | `global x = 1`, `global function f() end`, `function M.run() end`, `cfg = {}` (one fixture each) | `LuaDeclarationSite.declarationNodeOf(leaf)` | the `LuaGlobalVarDecl`, the `LuaGlobalFuncDecl`, the `LuaFuncDecl`, the `LuaAssignmentStatement` — **not** the identifier leaf. Without this, Safe Delete (widened in the same phase) leaves `global  = 1`. |
| TC-31 | -14 | `LuaRenameConflictTest.testGlobalDeclaredTwiceIsReported` | `a.lua`: `con<caret>fig = {}`<br>`b.lua`: `config = {}`<br>`c.lua`: `print(config)` | rename → `settings` | `ConflictsInTestsException`; messages contain the `conflict.ambiguousGlobal` text. Guards §3.4 C4: with two declarations `LuaNameReference.resolve()` returns null for every read, so `c.lua` would be silently skipped. |
| TC-32 | — | `LuaSafeDeleteTest.testUsedGlobalRaisesConflict` | `test.lua`: `config = {}`<br>`print(config)` | `SafeDeleteHandler.invoke(project, arrayOf(configLeaf), true)` | throws `BaseRefactoringProcessor.ConflictsInTestsException` and the `LuaAssignmentStatement` **survives** — the exact shape of the existing `testUsedLocalRaisesConflict`, with the one fixture the class has never had. **This is the Risk 1.6 gate.** If `isElevatedDeclaration` falls behind `declarationNodeOf`, the platform drops the delegate, `LuaAssignmentStatement` is not a `PsiNamedElement` so no generic search runs, zero usages are found, no conflict is raised and `config = {}` is deleted with `print(config)` left orphaned — the test's `fail(...)` fires. Repeat with `global count = 0` + `print(count)` under language level 5.5 (`LuaGlobalVarDecl`) and `global function f() end` + `f()` (`LuaGlobalFuncDecl`). |
| TC-34a | -08 | `LuaRenameTest.testFunctionNameReceiverIsRefused` | `function <caret>M.run() end`<br>`M.run()`<br>`M.run()` — **no** `M = {}` anywhere | `LuaRenameProcessor().substituteElementToRename(myFixture.elementAtCaret, null)` | throws `RefactoringErrorHintException` containing the `refactoring.rename.functionNameSegment` text; file text unchanged. **This is the BUG-457 shape §3.5 row 9 newly makes reachable.** Mutation check: delete design §3.1 step 4a and the rename must be driven end to end (`myFixture.renameElementAtCaret("N")`) — it produces `function N.run() end` with **both** `M.run()` call sites left on `M`, i.e. a silent half-rename, which is what the assertion must distinguish from a refusal. Note the fixture deliberately omits `M = {}`: with it present, `TargetElementUtilBase` redirects to that declaration and the rename is correct (§6). |
| TC-34b | -08 | `LuaRenameTest.testIntermediateFunctionNameSegmentIsRefused` | `function A.<caret>B.run() end` | `LuaRenameProcessor().substituteElementToRename(myFixture.elementAtCaret, null)`; repeat with the caret on `A` | both throw `RefactoringErrorHintException` containing the `refactoring.rename.functionNameSegment` text. Then with the caret on `run`, `substituteElementToRename` returns the `run` leaf (**not** a throw) — `functionNameLeafOf` picks the last `funcNameProperty`. This is the case that proves step 4a is a round trip against `functionNameLeafOf` and not a "grandparent is `LuaFuncName`" enumeration. |
| ~~TC-35~~ | — | **DROPPED — do not write this test.** | — | — | An earlier draft specified `LuaFindUsagesTest.testSearcherStillSkipsLabels` as the guard on design §3.8 step ①. It was **tautological**: a `::retry:: / goto retry` fixture contains **no** `LuaNameRef` at all (`label ::= '::' labelName '::'` / `labelName ::= IDENTIFIER`, `lua.bnf:163,251`; `gotoStatement ::= GOTO labelRef` / `labelRef ::= IDENTIFIER`, `:125,247` — neither goes through `nameRef`), so its assertion held even with `processQuery`'s entire gate deleted. Its mutation check was **unsatisfiable**: guard ③ rejects a normalised label anyway (`kindOf` of a `LuaLabelName`'s IDENTIFIER child is null, §3.5 row 4), so the reorder is behaviour-preserving and no input distinguishes the orders. §3.8 ① now records guard ① as unreachable defence-in-depth. The label path's real coverage is the **existing** `LuaFindUsagesTest.testLabelUsagesCount` (`:95-108`, asserts exactly one label reference via both `myFixture.findUsages` and `ReferencesSearch.search`) and `LuaLabelRenameTest`, both already Phase 1 exit criteria; `canProcessElement`'s label exclusion — which *is* reachable — stays gated by TC-24/TC-25. |
| TC-33 | — | `LuaSafeDeleteTest.testEveryElevatedDeclarationNodeRoundTrips` | one fixture each: `global x = 1`, `global function f() end`, `function M.run() end`, `cfg = {}`, plus the multi-target shapes `local a, b = 1, 2` and file-scope `a, b = 1, 2`, plus the negative `print(x)` | for each, `node = LuaDeclarationSite.declarationNodeOf(leaf)`; assert `LuaDeclarationSite.identifierLeafOf(node) === leaf` **and** `LuaSafeDeleteProcessor().handlesElement(node)` | `true` for all four positives. For `local a, b = 1, 2` the node is the `LuaAttName` and the round trip still holds (pre-existing). For file-scope `a, b = 1, 2` — **newly Safe-Deletable via §3.5 row 14** — the node is the `LuaVar`, the round trip holds, and `handlesElement` is `true`; the test then drives `SafeDeleteHandler` on it and asserts the **residual text is `, b = 1, 2`**, pinning the known granularity gap (`risks-and-gaps.md` Gap 2.6) rather than leaving it to be discovered as a regression. For the read `x` in `print(x)` the node **is** the leaf and `handlesElement` on the enclosing `LuaVar` is `false`. This is the direct unit guard on design §2.6a — TC-32 proves the user-visible outcome, TC-33 proves why. |
| TC-36 | -01 | `LuaRenameTest.testUnbuildableNewNameRefusesBeforeAnythingIsRewritten` | `local coun<caret>ter = 0`<br>`counter = counter + 1`<br>`print(counter)` | `myFixture.renameElementAtCaret("end")` | the rename **throws** (an `IncorrectOperationException` carrying `refactoring.rename.rewriteUnavailable`, rethrown wrapped by `RenameUtil.showErrorMessage` under a test application) and the file text is **byte-identical**. **Added by the Phase-2 review**, which found `renameElement` discovering the declaration half's two failure conditions AFTER the usage loop had already run — every usage on the new name, the declaration on the old one, i.e. BUG-457 inverted. Mutation (executed): restore that ordering and this goes red at the refusal assertion — the rename returns normally having written nothing and reports success. Design §3.3 step 2 and risks-and-gaps Gap 2.13 are the record; TC-11 is the first defence, this is the second. |
| TC-43 | -01 | `LuaRenameTest.testCancellingMidRenameLeavesTheFileUntouched` | `local coun<caret>ter = 0`<br>`counter = counter + 1`<br>`print(counter)` | install a `CoreProgressManager.CheckCanceledHook` that calls `ProgressIndicatorProvider.getGlobalProgressIndicator()?.cancel()` on the **SECOND** check whose immediate caller is `LuaRenameProcessor`, then run `myFixture.renameElementAtCaret("total")` inside `(ProgressManager.getInstance() as ProgressManagerImpl).runWithHook(hook) { … }`, catching whatever it throws | the file is **byte-identical to the input**: `local counter = 0`<br>`counter = counter + 1`<br>`print(counter)`. Assert with `myFixture.checkResult`, i.e. the WHOLE file — "the declaration is unchanged" is green on the defect. Do **not** assert on the thrown value: `PotemkinProgress.runInSwingThread` swallows the `ProcessCanceledException` (`PotemkinProgress.java:151-162`), so `thrown` is `null` both before and after the fix, which is [[BUG-468]] §6's trap. **`SECOND` is load-bearing** — measured, cancelling at the FIRST check leaves the file untouched on the *defect* too (the parse inside `setName` throws before usage 1 is written), so a first-check variant of this case cannot fail. **Phase 8 gate.** |
| TC-44 | -01 | `LuaRenameTest.testCancellationIsCheckedPerUsageNotPerRename` | **(a)** `local <caret>x = 1` + 3 `print(x)` lines · **(b)** the same with 8 `print(x)` lines | count, with the same hook, the checks whose immediate caller is `LuaRenameProcessor` during `myFixture.renameElementAtCaret("y")`; compare the two counts | `checksForEight - checksForThree >= 5`. Pins that the cancellation point is inside the preparation loop rather than once per rename, which is what engineering-contract §2 asks for and what TC-43 alone does not force. **A GUARD, not a gate** — it is green on `5b7c6ca4`, where the check is already per usage (measured: 3 checks for 3 usages). Its executed mutant is hoisting `ProgressManager.checkCanceled()` out of `preparedUsageRewrites`' lambda to a single call before the `mapNotNull`: the delta collapses to 0 and this goes red while TC-43 and TC-45 stay green, which is the only reason it exists. Use `renameElementAtCaret`, not a direct call, so the count includes exactly the checks a real rename makes. |
| TC-45 | -01 | `LuaRenameTest.testCancellingAfterTheFirstEditStillAppliesEveryEdit` | `local coun<caret>ter = 0`<br>`counter = counter + 1`<br>`print(counter)` | capture the live indicator from the FIRST `LuaRenameProcessor` check via the same hook (measured to be a `PotemkinProgress`); register a `DocumentListener` on `myFixture.editor.document` that calls `capturedIndicator.cancel()` on the first `documentChanged`; run `myFixture.renameElementAtCaret("total")` | the rename **completes**: `local total = 0`<br>`total = total + 1`<br>`print(total)`, and nothing is thrown. **Phase 8 gate**, and the one that pins §3.3 step 4's `executeNonCancelableSection`. Executed mutant: drop the section and apply the prepared rewrites directly — the file splits, with exactly one of the three occurrences on the new name and a `ProcessCanceledException` escaping. **Assert the split, not a text**: which occurrence moves is order-dependent (see the mutation-proof task and design §3.3), so assert `myFixture.checkResult` differs from the fully-renamed output AND from the input, or count occurrences of `total`. It is **also red on `5b7c6ca4`**, by a different route: with no preparation phase the second usage's `LuaElementFactory.createIdentifier` parse throws under the cancelled indicator, so the rename splits there instead. Capture the indicator OUTSIDE the listener — inside `documentChanged` the thread indicator is `PomModelImpl`'s `NonCancelableIndicator` (`PomModelImpl.java:112`) and `cancel()` on it is a no-op, which is a measured way to write a test that cannot fail. |

## Verification Tasks

- [x] Add `src/test/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSiteTest.kt` — TC-21, TC-22, TC-30. TC-21's per-row fixtures must include one for **every** row of design §3.5, the four new ones included (`global x = 1`, `global function f() end`, `function M.run() end`, file-scope `cfg = {}`), plus the negatives: a nested `local` write `function g() cfg = 1 end` and a shadowed `local cfg` at file scope must both give `null` from row 14's predicate.
- [x] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt` — TC-01…TC-07, TC-09…TC-11, TC-13b, TC-13d (Phase 2), TC-13e (Phase 7), TC-19a/b/c, TC-25, TC-26, TC-34a, TC-34b.
      **Landed (Phase 2):** TC-01…TC-07, TC-11, TC-13b, TC-13d, TC-19a/b/c, TC-25, TC-26, TC-34a, TC-34b, plus TC-36 and the two pinning cases Gaps 2.9/2.10 required. **Landed (Phase 4):** TC-09 and TC-10. **Still owed by its own phase:** TC-13e (Phase 7 — non-code search). TC-11, TC-13b and TC-19c were **missing from the Phase-2 commit `84eefb25` and not disclosed**; they were added at the review and all three pass, TC-19c on the first run — the dot form's explicit `self` renames normally, as design §6 said it should.
- [x] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameCrossFileTest.kt` — TC-08, TC-13a, TC-27, TC-28, TC-29. Copy the harness from
      `src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaCrossFileGlobalResolutionTest.kt`
      (`BasePlatformTestCase` + `myFixture.addFileToProject("declarer.lua", …)` +
      `myFixture.configureByText`), which already proves a light fixture exercises
      `LuaGlobalAssignmentIndex` cross-file — and whose existing cases (`local shadowed\nshadowed = 2`,
      a nested `function f() nested = 1 end`) are the same negatives §3.5 row 14's predicate must
      reproduce. Do not invent a heavy fixture for this.
- [x] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictTest.kt` — TC-14…TC-17, TC-31.
- [x] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRequireRenameTest.kt` — TC-18a/b/c, plus TC-18d (the decline path, added in Phase 5).
- [x] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaCatsParamRenameTest.kt` — TC-20a/b/c, plus TC-20d/e/f/g.
- [ ] **Extend `LuaRenameTest` — TC-43, TC-44, TC-45 (Phase 8).** All three need one cancellation
      harness, and it already exists in this repo: copy the `CheckCanceledHook` + `StackWalker`
      idiom from `LuaRenameConflictTest.detectorCancellationChecks` (`:391-421`) and its
      `private companion object` (`:449-453`, `PROGRESS_PACKAGE`, `FRAME_SEARCH_DEPTH = 20L`),
      substituting `"net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor"` for its
      `DETECTOR` constant. Do **not** invent a second mechanism, and do **not** substitute a
      counting `ProgressIndicator`: the Phase-4 record below already measured that one to be inert
      here (`CoreProgressManager.doCheckCanceled` consults the indicator only when a *cancelled* one
      is on the thread, `CoreProgressManager.java:868-876`).
- [ ] **Mutation-proof the atomic application (Phase 8).** Three mutants, each reddening exactly the
      case named, all executed on the builder and restored:
      - Restore the Phase-2 body — `usages.forEach { ProgressManager.checkCanceled(); RenameUtil.rename(usage, newName) }`
        then `applyDeclarationRewrite()` — and confirm **TC-43 goes red on a half-applied file**, not
        on a missing exception. If it goes red on an exception assertion the test was written into
        [[BUG-468]] §6's trap and must be rewritten.
        **What "half-applied" looks like: exactly ONE of the three occurrences carries `total`, the
        declaration carries `counter`.** Do **not** expect a particular line to be the one that
        moved. An earlier version of this task named
        `local counter = 0` / `counter = total + 1` / `print(counter)` as "the measured shape"; that
        was one run's output read as a constant, and three runs of the same measurement each moved a
        different occurrence. The usage array preserves `findReferences`' iteration order
        (`RenameUtil.java:93-101, 133-142`) and `LuaRenameProcessor.findReferences` returns
        `ReferencesSearch.search(…).findAll()`, a `Query` with no specified ordering — so a run that
        produces a different line is a **correct** reproduction, not a failed one. TC-43 itself is
        unaffected: it asserts the *input*.
      - Delete `renameElement`'s `ProgressManager.getInstance().executeNonCancelableSection { … }`
        wrapper, applying the prepared rewrites directly, and confirm **TC-45 goes red** with the
        file split and a `ProcessCanceledException` escaping. **Run this mutant against the Phase-8
        code**: on the parent commit TC-45 is red for an unrelated reason (there is no preparation
        phase at all), so a pass measured there proves nothing about the wrapper.
      - Hoist `checkCanceled()` out of `preparedUsageRewrites`' lambda to a single pre-loop call and
        confirm **TC-44 goes red with delta 0 while TC-43 and TC-45 stay green**. That separation is
        the only thing that makes TC-44 worth its weight, since it is green on the parent commit.
      - Change step 3a's argument from `element` to `replacement` — the plausible wrong reading of
        the Phase-6 code it replaces — and confirm **TC-20a
        (`testParamTagFollowsParameter`) goes red**. `replacement` is a free-floating element from
        `LuaElementFactory.createIdentifier` at step 3a, so `PsiTreeUtil.getParentOfType` finds no
        `LuaCatsCommentOwner`, `preparedRename` returns null and the tag never moves. This is the
        only reachable mutant for the F1 hoist: moving the *lookup* back inside the section is not
        observable from any test — inside a non-cancelable section its parse cannot throw — so the
        hoist is justified by the argument in design §3.6, not by a test, and this mutant pins only
        the argument choice. Say so rather than inventing an assertion that cannot fail.
- [ ] Add `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameTest.kt` — TC-12.
- [x] Extend `LuaFindUsagesTest` — TC-23 only (Phase 1). TC-35 is dropped; the class's existing
      `testLabelUsagesCount` and `testCanFindUsagesForLabel` stay unchanged and must stay green.
- [x] Extend `src/test/kotlin/net/internetisalie/lunar/refactoring/LuaSafeDeleteTest.kt` — TC-32 and
      TC-33 (Phase 1). The class has **six** `@Test` methods today — `testUnusedLocalIsDeleted`
      (`:39`), `testUsedLocalReturnsUsages` (`:71`), `testUnavailableOnKeyword` (`:92`),
      `testHandlesElevatedDeclaration` (`:113`), `testUsedLocalRaisesConflict` (`:137`),
      `testLabelDeclarationIsAvailable` (`:156`) — and all six must stay green. Model TC-32 on
      `testUsedLocalRaisesConflict` (`LuaSafeDeleteTest.kt:136-153`), which is already the correct
      shape; the class simply has no global or dotted fixture
      (`grep -n 'global\|function M\.' …LuaSafeDeleteTest.kt` → empty).
- [x] **Mutation-proof the Safe Delete elevation set (Risk 1.6).** Revert
      `LuaSafeDeleteProcessor.isElevatedDeclaration` to its enumerated form
      (`element is LuaLocalVarDecl || element is LuaLocalFuncDecl || element is LuaFuncDecl ||
      element is LuaAttName`) and confirm **TC-32 goes red for the `config = {}` fixture** — it must
      fail on the missing `ConflictsInTestsException`, i.e. on a silent delete, not on an assertion
      about text. Then delete `identifierLeafOf` row 10 with the round-trip form restored and confirm
      **TC-32 goes red again** — and note the mechanism is **not** "the delegate is admitted but
      searches the statement", which an earlier draft of this task claimed. With the round-trip form,
      deleting row 10 makes `identifierLeafOf(LuaAssignmentStatement)` return null, so
      `isElevatedDeclaration` returns `false` at its `?: return false` and the delegate is **dropped**
      entirely (`SafeDeleteProcessor.java:138-166`); the statement is not a `PsiNamedElement`, so no
      generic search runs either, and the delete is silent for the same end reason by a different
      route. The "searches the statement" mechanism is what the *enumerated* form would produce, and
      it is the mechanism `LuaSafeDeleteProcessor.kt:86`'s `identifierLeafFor(element) ?: element`
      makes possible — which is why design §3.5 row 10 uses `singleOrNull()`. Restore both.
      Without this pass, nothing distinguishes "Safe Delete searched usages" from "Safe Delete found
      none", and those are the same green.
- [x] Delete `LuaUnsupportedRenameProcessorTest` with its subject (Phase 2).
- [x] **Mutation-proof the conflict tests.** TC-14/TC-15/TC-17 assert an exception is thrown; a
      detector that reports *everything* would pass all three. Delete C2's declaration-site skip
      (design §3.4 C2 step 3) and confirm **TC-16 goes red**; restore it. Without that pass, TC-16 is
      the only thing standing between this feature and a detector that cries wolf on every rename.
      **Executed (Phase 3, 2026-08-23) — and the required mutant was not sufficient on its own.**
      Deleting C2 step 3 reddens TC-16 as specified. But "an exception is thrown" was not the whole
      hazard: on TC-15's fixture **C1 and C2 both fire**, so a suite asserting only that a conflict
      was raised is green with C2 missing entirely. The tests therefore assert the *specific* rule's
      bundle message, and the discriminating mutant is the one that proves it: disabling C1, C3 and
      C4 together reddens TC-14, TC-17 and TC-31 while **TC-15 stays green**, so TC-15's assertion
      rides on C2 alone. Five further mutants, all executed and restored: C2 off → TC-15 red, TC-14
      green; C1 off → TC-14 red; C3 off → TC-17 red; C4 off → TC-31 red; C4's single-declaration
      protection removed → all four `LuaRenameCrossFileTest` cases red, which is what pins the
      detector against reporting an ambiguity on a global declared once.
- [x] **Mutation-proof `canProcessElement`'s label exclusion.** Delete the
      `element is LuaLabelName || element is LuaLabelRef` line from design §3.0's predicate and
      confirm **TC-25 goes red and TC-24 (`LuaLabelRenameTest`) goes red**; restore it. This is the
      one edit that silently breaks the only refactoring the plugin ships today, and
      `RenamePsiElementProcessorBase.forPsiElement` makes the breakage invisible to any test that
      instantiates `LuaRenameProcessor` directly.
- [x] **Mutation-proof the `self` resolution claim.** In TC-19a, change part (a)'s expectation to
      `target.text == "Obj"` and confirm it goes **red**. The original design asserted `self`
      resolved to the class leaf; nothing in the artifact set could have contradicted it, because no
      test looked at what the resolved target *was*.
- [x] **Mutation-proof the funcName receiver-segment refusal (Risk 1.1 shape 6).** Delete design
      §3.1 step 4a and confirm **TC-34a goes red**. It must be re-run in its end-to-end form for this
      pass — drive `myFixture.renameElementAtCaret("N")` over TC-34a's fixture and assert the two
      `M.run()` call sites are unchanged — so the mutation is shown to produce a **silent half-rename**,
      not merely a missing exception. Then restore step 4a and confirm the caret-on-`run` half of
      TC-34b still returns the leaf, proving the guard is a round trip against `functionNameLeafOf`
      and not a blanket refusal of every `LuaFuncName` grandparent (which would also kill TC-08's
      plain `function greet()`).
- [x] **Mutation-proof the non-code replacement hook.** (Phase-2 half only; the Phase-7 half stays open.) Delete
      `LuaRenameProcessor.getQualifiedNameAfterRename` and confirm **TC-13d goes red with a
      `TestLoggerAssertionError` carrying `"Unknown element type : "`** — not with a text assertion.
      Then, in Phase 7, delete `getElementToSearchInStringsAndComments` and confirm **TC-13e goes
      red** with the comment left unrewritten while TC-13c also goes red. The two overrides fix
      different halves of `RenameUtil.processUsages` (the searched string and the substituted string)
      and neither substitutes for the other; a pass that only checks TC-13c cannot tell them apart.
      **If the Phase-2 half does not go red**, the finding is that `ElementDescriptionUtil`'s
      `element.toString()` fallback produced an *empty* string for the leaf and
      `RenameUtil.java:149`'s `stringToSearch.isEmpty()` short-circuited before `getStringToReplace`
      — i.e. the hazard is Phase-7-only. Record that in design §1's evidence table and keep the
      override regardless; it is required either way once §2.9's other five accessors land.
- [ ] ~~**Mutation-proof the searcher's label guard.**~~ **REMOVED — do not attempt it, and do not
      substitute another assertion for it.** The mutation it named (moving §3.8's `is LuaLabelName`
      check after the `identifierLeafOf` normalisation) is **behaviour-preserving**: guard ③ rejects
      the normalised label on its own, because `kindOf` of a `LuaLabelName`'s IDENTIFIER child is
      null (§3.5 row 4 — its parent is a `LuaLabelName`, not a `LuaNameRef`). No input distinguishes
      the two orders, so no test can go red. Guard ① is kept as unreachable defence-in-depth with
      that stated in design §3.8 ① and in the implementation's KDoc; TC-35 is dropped. **If you
      believe you have found a mutation that does turn a label test red, you have found a defect in
      §3.5 or §3.8 — raise it rather than writing the test.**
- [ ] **Mutation-proof the global classification.** Delete design §3.5 row 5 (so `global x = 1`
      falls through to the `LuaAttName` row and becomes `LOCAL_VARIABLE`) and confirm **TC-28 goes
      red**; then delete row 7 and confirm **TC-29 goes red**; restore both. Without this pass the
      Lua 5.5 rows are asserted only by TC-21, which checks `kindOf` in isolation and would not
      notice that `isFileLocal` narrowed the search scope.
- [x] **Mutation-proof the dotted/colon pair (Phase 4).** Four mutants, all executed on the
      builder, each reddening exactly the case named:
      - Revert `LuaNameReference.declarationIdentifier`'s `LuaFuncDecl` branch to
        `decl.funcName.nameRef.identifier` → **TC-09 red**, and measured to be red *on a silent
        half-rename*: the file becomes `M = {}` / `function M.start() end` / `M.run()`. This is why
        TC-09 asserts the whole file — an assertion on the declaration alone is **green** under this
        mutant, and that is the cannot-fail shape that was gone looking for.
      - Delete §3.1 step 4b (the `METHOD_FUNCTION` branch) → **TC-10 red**, and TC-19a red with it,
        confirming the two cases share the branch while entering it from different directions
        (declaration caret vs. resolved `self`). **Correction (Phase-4 review):** this line first
        claimed TC-10 goes red "on both of its assertions". As executed it aborts at the **first** —
        the expected `RefactoringErrorHintException` is not thrown, so the case fails before it can
        reach the unchanged-file assertion. The mutant is still discriminating and the test still
        goes red for the stated reason; only the "both assertions" detail was wrong.
      - Delete `globalDeclarationsNamed`'s loop-body `checkCanceled` →
        **`testCancellationIsCheckedPerIndexHitNotPerCall` red**, with the counts collapsing from
        5-then-10 to 3-then-3, i.e. delta 0.
      - Delete `captures`' new usage-list `checkCanceled` →
        **`testCancellationIsCheckedPerUsageNotPerCollisionsCall` red**, delta 5 where 10 is
        required.
- [x] **Correct the Phase-3 cancellation audit (Phase 4).** The record said "the remaining **six**
      iteration blocks were audited"; there are **seven**. The omitted one —
      `captures`' `usages.mapNotNull { it.element }` — was the only omitted block able to reach PSI
      and VFS, through `UsageInfo.getElement()` → soft `SmartPsiElementPointer` →
      `SelfElementInfo.restoreElement` → `PsiManager.findFile`. **Decided on its merits and
      guarded**, not excused: the "bounded by the next statement's pass" argument is about latency
      only, the contract's rule is written without a latency exemption, and the fix is one line.
      The count and the three-guarded/four-deliberately-unguarded split are now stated in
      `LuaRenameConflictDetector`'s own KDoc, where the next reader will meet them.
- [x] **Restate the Phase-3 impossibility claim (Phase 4) — by building the test instead.** Phase 3
      recorded the cancellation fix as "not individually pinnable by a test". It is pinnable; the
      reviewer's differential construction works and is now
      `LuaRenameConflictTest.testCancellationIsCheckedPerIndexHitNotPerCall`, with a sibling for
      the block above. One deviation from the construction as sketched, forced by measurement: a
      counting `ProgressIndicator` is **inert** here, because `CoreProgressManager.doCheckCanceled`
      only consults the indicator when a *cancelled* indicator is on the thread
      (`updateShouldCheckCanceled`, `CoreProgressManager.java:870-874`) and otherwise takes its
      `ONLY_HOOKS` branch. The observation point is therefore `ProgressManagerImpl.runWithHook`.
      The raw hook count is also useless — measured 386 vs 1095 for the two fixtures, dominated by
      the platform's own checks running *underneath* the detector, which scale with file count and
      would have swamped the mutant — so the hook attributes each check to its **immediate caller**
      via `StackWalker`, giving the exact 5 vs 10 the construction predicted.
- [x] **Corpus sweep** after Phase 1: `run "test -PwithCorpus --rerun --no-build-cache"`, and verify
      the three corpus classes have fresh result XML.
- [ ] **`REFACT-01-00-DR-10` — live-IDE REACHABILITY, and it runs BEFORE Phase 8's code, not after**
      (`verify-in-ide` skill). The open question is whether a user can trigger [[BUG-468]] at all:
      TC-43/45 drive cancellation through a hook, while a user drives it through `PotemkinProgress`'s
      **Stop** button, which is shown only once `now - myLastUiUpdate > delayInMillis`
      (`PotemkinProgress.updateUI:117-119`; `delayInMillis` defaults to 300 ms —
      `ProgressWindow.java:77`, `ProgressUIUtil.kt:8`) and steals input only while showing
      (`interact:84-85`). On a project large enough for the dialog to paint, press **Stop** part-way
      through a global rename and record whether the button appeared, at what project size, and
      whether the file was split. **This was originally scheduled after implementation, which put it
      where its answer could not change anything.** It bears on the Phase 8 → Phase 7 order: that
      order was chosen because a `Must` outranks a `Could`, and if the defect is unreachable for
      realistic renames the `Must` is not live and Phase 7 should go first. It does **not** bear on
      whether to fix it — the failure mode is data loss. Record the answer in Gap 2.17 either way.
- [ ] **Live IDE verification of Phase 8** (`verify-in-ide` skill), **after** the code lands, on the
      project size DR-10 recorded: rename a global across those files, press **Stop** part-way, and
      confirm the project is either fully renamed or wholly unchanged — never a mixture — and that no
      internal-error balloon appears. Then press Ctrl+Z and confirm one undo step covers the whole
      rename. Also confirm the progress dialog keeps repainting during the apply phase — design §3.3
      argues from source that it must (`ProgressManagerImpl.java:84-91` +
      `CoreProgressManager.java:184-196`), and that argument is **read, not run**.
- [ ] **Live IDE verification** (`verify-in-ide` skill) after Phase 7 — unit tests cannot observe the
      conflicts dialog, the preview pane, or the inline-rename template:
  - [ ] Reproduce the BUG-457 scenario verbatim (`local counter = 0` + four usages, Shift+F6 →
        `total`) and confirm **all five** occurrences change.
  - [ ] Rename a local to a name already visible and confirm the conflicts dialog appears, names the
        colliding declaration, and that pressing **Continue** still rewrites every usage.
  - [ ] Shift+F6 on a local and confirm the inline template appears with every usage highlighted.
  - [ ] Rename a `.lua` file from the Project view and confirm the `require` string updates.
  - [ ] **Tick "Search in comments and strings" in the rename dialog and confirm no IDE internal
        error appears** (the red exclamation in the status bar / Event Log). This is the only check
        that the `getQualifiedNameAfterRename` override holds on a real `LOG.error`-configured
        logger rather than the test logger, and the checkbox is what a user reaches in one click.
  - [ ] Put the caret on the receiver `M` of `function M.run() end` in a file with no `M = {}` and
        confirm Shift+F6 **refuses with an explanation** rather than renaming the declaration alone.
  - [ ] Confirm Shift+F6 on a `::label::` still works (REFACT-04 regression).
  - [ ] **Safe Delete a used bare global (`config = {}` with `print(config)` in another file) and
        confirm the "usages found" dialog appears.** Phase 1 widened Safe Delete to reach this
        target; the unit gate (TC-32) runs in a single in-memory project, and this is the only check
        that the widened delegate still finds usages across files. Then Safe Delete an *unused*
        `global x = 1` at language level 5.5 and confirm the whole statement is removed, not
        `global  = 1`.
  - [ ] With the project language level set to **5.5**, rename a `global` variable declared in one
        file and used in another, and confirm both change — the unit fixtures share one in-memory
        project, so this is the only check that the `getVersion()` 3→4 bump actually took effect on a
        persisted index.

## Task Summary

| Phase | Status | Priority |
| :--- | :--- | :--- |
| Phase 1: Declaration-site model + global indexing | done | Must |
| Phase 2: Core rename processor | done | Must |
| Phase 3: Conflict detection | done | Should |
| Phase 4: Dotted method declarations | done | Should |
| Phase 5: `require(...)` rewriting on file rename | done | Should |
| Phase 6: LuaCATS `@param` propagation | done | Should |
| Phase 8: Atomic application | todo | Must |
| Phase 7: In-place rename and non-code search | todo | Could |
