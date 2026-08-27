---
id: "REFACT-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "REFACT-07"
priority: "high"
folders:
  - "[[features/refactoring/07-inplace-rename/requirements|requirements]]"
---

# Implementation Plan: REFACT-07 — In-place (Inline) Rename

## Ordering principle

**No code phase starts before its de-risking task has run.** Design §3.3's whole chain is a read
trace, and the prior attempts at `REFACT-01-12` each ended by discovering that a frame nobody
had executed did not behave as read. Phase 0 exists so that this attempt discovers it in a probe
rather than in a shipped commit.

**The document-level test lands before the PSI change ships.** `risks-and-gaps.md` Risk 1.2 records
that the suite is green with a corrupting override; a phase that adds the override before adding
the test that can see it repeats exactly that. Phase 2 is therefore ordered before Phase 3, and
Phase 2's first case is written against the *unchanged* tree, where it must **fail**.

## Phase 0 — De-risk [Must]

Run the de-risking tasks in `risks-and-gaps.md`. Nothing in this phase is committed to `main`;
edits are made with the `temporary-edits` skill and restored with it.

Run order is **DR-05, DR-01, DR-02, DR-04, DR-03**; the IDs are identities, not sequence.

| Task | Deliverable | Gates |
| :--- | :--- | :--- |
| DR-05 | `dr-05-evidence/` with the raw element record for every caret position in its table, plus the applied branch of its decision rule | DR-01, DR-02, Phases 1-5 |
| DR-01 | `dr-01-evidence/` with raw output for each of its probes (a)-(h), plus the recorded answers to the `getUseScope`-redundancy question and the collapse-into-REFACT-01 question | Phases 1-4 |
| DR-02 | `dr-02-evidence/` with the handler lists for both predicate states, raw, from the unit-test application **and** from a running GoLand | Phase 2's TC-02 |
| DR-03 | `dr-03-evidence/` with a per-consumer verdict for every row of design §4 that can be run against GoLand 2026.1.3, and an explicit outstanding list for the rest | Phase 3, Phase 1 task 1.4 |
| DR-04 | the observed conflict channel, type and message | Phase 2's TC-08 |

**Exit criteria**

- [x] DR-05 has run **first**, on the unchanged tree, and its decision rule has been applied to the
      plan — in particular, whether design §2.3, §2.4 and §3.5 are part of the delivery at all.
      They are: the applied branch, the answers it settles and where each one now lives are recorded
      in `risks-and-gaps.md` under DR-05.
- [x] Every de-risking task in `risks-and-gaps.md` has run and its decision rule has been applied.
      DR-03 is **partially** executed and its outstanding rows are named in its record — the two
      minimap rows, blocked by provenance, and the two completion-ranking rows, routed to Phase 5
      task 5.1a. Partial is the accepted state, not a pending action: nothing in Phase 1-4 waits on
      those rows.
- [x] Every probe's raw output is committed under this feature's evidence directories —
      `dr-01-evidence/` … `dr-05-evidence/`, all five present and populated.
- [x] The route decision (design §1) is either confirmed by DR-01 or replaced, in writing.
      Confirmed, and written into design §1.
- [x] `risks-and-gaps.md` is updated with what each task measured, replacing "Read, not run" labels
      in `design.md` §1's evidence table with executed ones, per claim. The labels that remain in
      `design.md` are §3.3 step 9's (a deliberate deferral no Phase 0 task covered, with Phase 5's
      settling recorded beside it), §3.6's two not-normalising consequences (which the design removes
      the need to settle) and §4's rule sentence — none of them a claim Phase 0 executed.
- [x] The tree is clean: `git status --porcelain` is empty and `git diff` shows no probe residue.

**Estimate**: 10-15 h.

## Phase 1 — The PSI primitive [Must]

**Precondition**: DR-05 has run and its decision rule is applied; DR-01 confirmed branch (a)-(h) as
designed.

| # | Task | File |
| :--- | :--- | :--- |
| 1.1 | Add `PsiNameIdentifierOwner` to `LuaNameRefBaseImpl`'s supertype list and implement `getNameIdentifier()` per design §3.1 | `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt` |
| 1.2 | Add the KDoc that states **why the mixin and not the interface** (design §3.1, "Why not the interface") and **why not the `.bnf`** (Alternative D), so the next reader does not "simplify" it onto `LuaNameRefElement` | same |
| 1.3 | Add the import `com.intellij.psi.PsiNameIdentifierOwner` — already present at `LuaBaseElements.kt:14` for `LuaNameDeclElement`, so verify rather than add | same |
| 1.4 | Add a one-line comment at `LuaSpellcheckingStrategy.getTokenizer` (`LuaSpellcheckingStrategy.kt:37`) recording that the **total override — no `super.` call — is load-bearing for REFACT-07**: `SpellcheckingStrategy.java:91` returns `PsiIdentifierOwnerTokenizer` for any `PsiNameIdentifierOwner`, which §3.1 makes every `LuaNameRef`, so delegating to `super` would newly spellcheck every Lua identifier. `risks-and-gaps.md` Risk 1.10 carries the reasoning. **The comment is the deliverable, not the risk entry** — this coupling is invisible at the one place it can be broken, and no REFACT-07 test fails if it is | `src/main/kotlin/net/internetisalie/lunar/lang/spellcheck/LuaSpellcheckingStrategy.kt` |

**Verification**

- [x] `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — green, and the
      result set matches the `f6148451` baseline.
- [x] `tooling/gce-builder/gce-builder.sh run ktlintCheck` — green (check only; to format, format on
      the VM and rsync back, per `AGENTS.md`).
- [x] `git diff src/main/gen` is empty — the change must not have touched generated code.
- [x] `git diff` on `LuaSpellcheckingStrategy.kt` shows **only** an added comment. Task 1.4 must not
      change the tokenizer's behaviour; DR-03 measured it as Δ none across both commits and that
      must stay true.

**Estimate**: 1-2 h.

## Phase 2 — The tests, written to fail [Must]

**Precondition**: DR-05, DR-02 and DR-04 have run. Phase 1 may be reverted or present; case 2.1 is
written so that it fails on the unchanged tree and passes after Phase 3.

**Two binding rules for every case written in this phase**, both from design §6:

- **No case puts `CommonDataKeys.PSI_ELEMENT` into a data context.** Contexts come from
  `DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)`. Injecting the
  element is what makes the shipped `LuaInplaceRenameTest` unable to see this feature's central
  defect, and repeating it would reproduce that.
- **Every document-layer case asserts that a template started**, not only what the document says.
  `CodeInsightTestUtil.tryInlineRename` returns `false` and asserts nothing when no template starts
  (`CodeInsightTestUtil.java:250-256`), and design §6's `renameInPlaceViaHandler` returns `false` in
  the same circumstance, so a case whose expected text is the *unchanged* file is otherwise satisfied
  by the feature being absent. Assert the driver's return value; for TC-06, which drives neither —
  it starts the template and then cancels it — assert
  `TemplateManagerImpl.getTemplateState(myFixture.editor) != null` before sending <kbd>Esc</kbd>.
  **That assertion is what makes TC-06 a gate rather than a guard**, and `requirements.md`'s TC-06
  row names the mutation it admits.

  **TC-08 is exempt from this rule, by measurement.** Its expected text is the unchanged fixture,
  so the rule would otherwise bind, but its THROWS assertion is that the driver raises
  `ConflictsInTestsException` — and with no template there is no commit, no `RenameProcessor` and
  therefore no exception, so that assertion cannot pass with the feature absent. DR-04 measured
  both halves: the throw on the real fixture, and a non-colliding new name committing normally as
  the positive control that a template does start there. This is an exemption earned by a run, not
  argued from the shape of the case; the exemptions from the **fail-first pass** are a different
  thing and are listed in this phase's Verification block, where TC-04 and TC-09 are the exempt
  pair. TC-08 is **not** exempt from the fail-first pass.

| # | Task | File |
| :--- | :--- | :--- |
| 2.1 | Rewrite `LuaInplaceRenameTest` per design §6: delete the class KDoc's Route A analysis, retarget the predicate cases from `VariableInplaceRenameHandler` to `MemberInplaceRenameHandler`, replace every injected data context with an editor-derived one, and add the registry-layer cases TC-02, TC-10, TC-11, TC-12. **`testInplaceRenameIsOfferedForAFileLocalDeclaration` (`:51-58`) must be inverted by name, not left to the rewrite by implication**: it asserts `isInplaceRenameAvailable` is `true`, §3.2 makes that `false`, and DR-01's probe (h) measured it as the single failure in an otherwise-green 2851-test suite. Its replacement asserts the same input through `MemberInplaceRenameHandler().isAvailableOnDataContext(context)`. The other three shipped cases each assert something is *withheld* and stayed green under the new predicate, so leaving them untouched proves nothing — design §6 says which they are | `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameTest.kt` |
| 2.2 | Add the document-layer cases TC-01, TC-03, TC-05, TC-06 and TC-15 driving `CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), …)` + `myFixture.checkResult` | same |
| 2.3 | Add the private helper `renameInPlaceViaHandler`, verbatim from design §6, and — **after** the fail-first pass has been recorded, per this phase's Verification — TC-04, driving `renameInPlaceViaHandler(LuaInplaceRenameHandler(), "total")`. `CodeInsightTestUtil.tryInlineRename` **cannot** be used for it: its parameter type is `VariableInplaceRenameHandler` (`CodeInsightTestUtil.java:236`) and `LuaInplaceRenameHandler` implements `RenameHandler` directly. Every case whose caret supplies an IDENTIFIER leaf uses this helper — read which those are off the driver column of `requirements.md`'s table, and do not restate the list here | same |
| 2.4 | Add TC-07 (invalid identifier) and TC-08 (conflict), asserting on the channels DR-04 measured. TC-07 asserts the document text, that a template started, **and** that no exception escapes — its mutation reddens by `IncorrectOperationException`, not by text | same |
| 2.5 | Add TC-09 (`---@param` parity) — like TC-04, **after** the fail-first pass has been recorded — using `LuaCatsParamRenameTest.kt:52-59`'s proven fixture shape, driven through `renameInPlaceViaHandler(LuaInplaceRenameHandler(), "count")` — **not** `CodeInsightTestUtil.tryInlineRename`. DR-05 probe `d` measured the data context supplying the parameter's IDENTIFIER leaf on that exact fixture, which the platform's handler refuses at `MemberInplaceRenameHandler.java:56` | same |
| 2.6 | Add TC-13 (Find Usages) and TC-14 (Safe Delete) as the `REFACT-07-12` regression guards, in the existing Find Usages and Safe Delete test classes rather than here | the existing classes |
| 2.7 | Confirm every case in `LuaLabelRenameTest` still passes unchanged — that class is `REFACT-07-13`'s document-layer half, and TC-11 is only its registry-layer half | `LuaLabelRenameTest.kt` — expected diff: none |

Each case's KDoc names the mutation from `requirements.md`'s table and the reason it is reachable
from that case's own fixture — **or**, for a case `requirements.md` labels a **guard**, carries the
argument for why no such mutation exists in code this repo can edit. Read the label off
`requirements.md`'s row; do not copy a list of guards here, because two statements of one list are
two things to keep in sync. A guard's KDoc must say so in its first line, so that a later reader
does not count it as coverage. No row's mutation is conditional: each names one mutation, or is a
guard, against the element DR-05 measured at that case's caret.

**Verification**

- [x] Run every document-layer case that compiles against the tree without Phase 1 or Phase 3 —
      TC-01, TC-03, TC-05, TC-06, TC-07, TC-08 and TC-15 — and every one must **fail**. The
      criterion is "a case that passes here is a case that cannot fail": on the unchanged tree no
      template starts for any of them, so a passing case is asserting something the absent feature
      already satisfies. **EXECUTED 2026-08-26; all seven failed. "Without Phase 1" is the
      load-bearing half of that sentence and was measured to be so** — see the record below.
- [x] **TC-04 and TC-09 are exempt from that pass, and the exemption is discharged in Phase 4.**
      They construct `LuaInplaceRenameHandler`, which task 3.3 creates, so the test source set does
      not compile with them present until Phase 3 — an implementer meeting compile errors from those
      two named cases here is meeting the plan, not a defect. Splitting task 3.3 to land the class early would **not**
      make them fail either: design §6's `renameInPlaceViaHandler` takes the handler as a parameter
      and calls `handler.invoke(...)` directly, so the registry — and therefore task 3.4's
      registration — is not on their path, and they pass the moment the class compiles with its
      logic. **Why this is not a weakening.** Fail-first proves one thing, cheaply: that a case
      detects the feature's *absence*. A mutation proof is the stronger form of the same evidence —
      it shows the case goes red when the feature is broken *and* isolates which part. So a case
      whose Phase-4 mutant removes `LuaInplaceRenameHandler`'s own contribution ends with better
      absence-evidence than this pass would have given it, not weaker. **The condition, which is
      binding per case**: that mutant must remove the handler's own contribution, not a peripheral
      step elsewhere on the path. `requirements.md` names it — TC-04's mutant, and TC-09's **M1** —
      and Phase 4 records it. A case that reaches Phase 4 without such a mutant has no
      absence-detecting evidence at all and must be re-designed, not recorded as passed.
- [x] Because of that compile constraint, the pass above is run and recorded **before** tasks 2.3
      and 2.5 add those two call sites; task 2.3's `renameInPlaceViaHandler` helper is not affected
      and may land earlier, since its parameter type is `RenameHandler`. **Honoured**: the helper
      landed with task 2.2, both fail-first runs and the full-suite run below were completed and
      recorded, and only then were TC-04 and TC-09 written.
- [x] TC-02, TC-10, TC-11 and TC-12 are **registry-layer** cases and are not in that list: TC-11 and
      TC-12 assert today's behaviour and legitimately pass before Phase 3, and TC-02 and TC-10 fail
      here for a different reason — the predicate, not the template. **TC-02 fails as stated. TC-10
      does not, and the claim about it was wrong**: the shipped `isInplaceRenameAvailable` already
      answers `false` for a global (`GLOBAL_VARIABLE.isFileLocal` is `false`), so no in-place handler
      is available and the registry already falls back to `PsiElementRenameHandler` — which is
      exactly TC-10's expectation. DR-05 probe `e` had measured that on the unchanged tree. TC-10 is
      green before Phase 3 alongside TC-11 and TC-12; its gate is its Phase-4 mutant.
- [x] Record which cases failed and with what error, including whether
      `MemberInplaceRenameHandler.doRename`'s fall-through to `performDialogRename` (`:87`) did
      anything observable in the fixture. That record is the evidence that the coverage hole is
      closed. **Recorded below.**

### Phase 2 record — the fail-first pass, executed 2026-08-26

Three runs, all `--rerun --no-build-cache` on the `gce-builder` host, from `319e8eb8` plus this
phase's test-only edits.

**Run A — the fail-first pass, on a tree without Phase 1 or Phase 3** (Phase 1's supertype and
`getNameIdentifier()` override temporarily reverted with the `temporary-edits` skill; restored, and
`git status --porcelain` shows only the three test files). `14 tests completed, 9 failed` in
`LuaInplaceRenameTest`, and `LuaFindUsagesTest`, `LuaSafeDeleteTest`, `LuaLabelRenameTest`,
`LuaCatsParamRenameTest` and `LuaRenameConflictTest` all green.

| Case | Verdict | Mechanism |
| :--- | :--- | :--- |
| TC-01, TC-03, TC-05, TC-06, TC-07, TC-08, TC-15 | **FAIL** (all seven) | `java.lang.RuntimeException` ← `com.intellij.util.IncorrectOperationException: Rename was not applied: '' cannot be written as a Lua identifier…`, raised at `RenameUtil.showErrorMessage(RenameUtil.java:267)` from `RenameProcessor.performRefactoring(:434)` |
| TC-02 | **FAIL** | `AssertionFailedError` — the single selected handler is a `VariableInplaceRenameHandler`, as the row predicts |
| the predicate inversion (`testMemberInplaceRenameIsOfferedForAFileLocalDeclaration`) | **FAIL** | `AssertionFailedError` — `isMemberInplaceRenameAvailable` is still `LuaLabelName`-only |
| TC-10, TC-11, TC-12, the two predicate guards, TC-13, TC-14 | pass | as measured by DR-05 on the same tree |

**The `performDialogRename` fall-through is loudly observable, which the phase asked to be
established.** Without Phase 1 a `LuaNameRef` is not a `PsiNameIdentifierOwner`, so
`MemberInplaceRenameHandler.doRename` skips its whole in-place branch and reaches
`performDialogRename` at `:87`. Headlessly that runs a real `RenameProcessor` with `DEFAULT_NAME`
absent — i.e. the **empty string** — and `LuaRenameProcessor`'s own `refuseRewrite` throws on it. So
the fall-through is not a silent no-op that leaves the document untouched: it throws out of the
driver before any edit. Every document-layer case is therefore absence-detecting *twice over* — by
its own assertion and by that escape.

**Run B — the row-named mutation for TC-01 and TC-06**, which is a different edit from Run A's:
Phase 1's supertype kept, `getNameIdentifier()` changed to `= null`. `14 tests completed, 9 failed`,
the same nine. All seven document-layer cases went red by **exactly the predicted mechanism** —
`TestLoggerFactory$TestLoggerAssertionError: null by …MemberInplaceRenamer`, thrown from
`Logger.error` inside `InplaceRefactoring.getSelectedInEditorElement`. One citation correction: the
shipped GoLand 2026.1.3 platform puts that `LOG.error` at **`InplaceRefactoring.java:859`**, not the
`:860` `requirements.md` and `design.md` cite from `intellij-community` master.

**Why Run A had to revert Phase 1, stated so the next reader does not undo it.** Design §6 is
explicit that a document-layer case cannot see a predicate: neither driver consults
`isInplaceRenameAvailable` or `isMemberInplaceRenameAvailable`, and both construct the handler
themselves, so Phase 3's absence — predicate *and* registration — is invisible to layer 3. Run C
below measures that directly: with Phase 1 present and Phase 3 absent, **all seven document-layer
cases pass**. The absence these cases detect is Phase 1's, and a fail-first pass run against
`319e8eb8` unmodified would have recorded seven green cases and proved nothing.

**Run C — the full suite in the as-committed state** (Phase 1 present, Phase 3 absent, TC-04 and
TC-09 not yet written). Result line `BUILD FAILED in 7m 45s`; JUnit XML aggregate over 458 files,
every one with this run's mtime: **`tests=2863 failures=2 errors=0 skipped=1`**. Against DR-03's
`f6148451` baseline of 2851 the test-NAME-set `diff` is exactly the intended edit and nothing else —
four shipped `LuaInplaceRenameTest` cases removed, sixteen added (fourteen here, TC-13 in
`LuaFindUsagesTest`, TC-14 in `LuaSafeDeleteTest`). The two failures are TC-02 and the predicate
inversion; **they are the whole of what the suite can see of Phase 3's absence**, which is design
§6's "why the document layer is not sufficient alone" in measured form.

**End state of the phase, and it does not compile — by the plan's own design.** After tasks 2.3 and
2.5, `compileTestKotlin` fails with exactly two errors and no others:
`LuaInplaceRenameTest.kt:541:65` and `:590:65`, `Unresolved reference 'LuaInplaceRenameHandler'`.
Phase 3 task 3.3 resolves both. `ktlintCheck` is green with those call sites present.

**Estimate**: 6-8 h.

## Phase 3 — Route selection [Must]

**Precondition**: Phase 1 and Phase 2 complete; DR-03 run and its verdict recorded per consumer,
with its outstanding rows named rather than left implied — **partial is sufficient here**, because
the rows it could not run are blocked by the platform build or measurable only live, and none of
them gates a code change in this phase.

| # | Task | File |
| :--- | :--- | :--- |
| 3.1 | Change `isInplaceRenameAvailable` to `false` and move its expression into `isMemberInplaceRenameAvailable` alongside the `LuaLabelName` clause, per design §3.2 | `src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaRefactoringSupportProvider.kt` |
| 3.2 | Rewrite the KDoc on `isInplaceRenameAvailable` and `isMemberInplaceRenameAvailable`: state Ground 3 (`RenameHandlerRegistry.java:114-119`) as the reason `isInplaceRenameAvailable` is `false`, so nobody "restores" it; state each clause of the member predicate and the test that gates it | same |
| 3.3 | Add `LuaInplaceRenameHandler` per design §2.3, verbatim — it implements `RenameHandler` **directly** and must not be changed into a `MemberInplaceRenameHandler` subclass (design Alternative I: the registry's removal loop deletes such a subclass, `RenameHandlerRegistry.java:111-119`). KDoc must state that, and state §3.5's availability invariant so nobody widens the gate to accept a `LuaNameRef` and puts two handlers in the registry | `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameHandler.kt` (new) |
| 3.4 | Register it: one `<renameHandler implementation="net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler"/>` element adjacent to `renamePsiElementProcessor` (`plugin.xml:389-390`), per design §2.4 | `src/main/resources/META-INF/plugin.xml` |
| 3.5 | Confirm no other `plugin.xml` change is needed, per design §2.4 | `src/main/resources/META-INF/plugin.xml` — expected diff: the one added element and nothing else |
| 3.6 | Normalise `renameElement`'s element per design §3.6: derive `declarationLeaf` once with `LuaDeclarationSite.identifierLeafOf(element) ?: element` and use it for the classification, `oldName`, `preparedDeclarationRewrite` and `LuaCatsParamRenamer.preparedRename`. Apply the same normalisation at `findReferences` (`:130`), which is the same latent defect at a second site. **The KDoc must say this is a `REFACT-01` defect that REFACT-07 is merely the first caller to expose** — a reader who records it as in-place-specific will delete it when the in-place path next changes, and `REFACT-07-09` will regress silently. Do **not** widen it to the `kindOf` sites design §3.6's audit marks safe or not-newly-affected | `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt` |

**Verification**

- [x] Every case Phase 2 recorded as failing now passes.
- [x] `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — green, result set
      matching the baseline plus the new cases.
- [x] `git diff src/main/resources/META-INF/plugin.xml` shows the single added `renameHandler`
      element and nothing else.
- [x] `verifyPlugin` is green, so the added registration resolves to a real class.
- [x] `LuaCatsParamRenameTest` is green and **unmodified** — `git diff` on it is empty. Task 3.6
      changes the element `renameElement` classifies, and that class is the dialog path's regression
      guard for the same behaviour; a change there would mean the fix moved the dialog path too.

**Estimate**: 3-5 h.

### Phase 3 record — executed 2026-08-26

**The suite is green and the count is the predicted one.** `run "test --rerun --no-build-cache"`,
result line `BUILD SUCCESSFUL in 7m 25s`, `:test` executed rather than `FROM-CACHE` or `UP-TO-DATE`.
JUnit XML aggregate over 458 files, every one carrying this run's mtime:
**`tests=2865 failures=0 errors=0 skipped=1`**. That is Run C's 2863 plus exactly two — TC-04 and
TC-09, which did not compile before task 3.3 — and Run C's two failures cleared.

**Both cases Phase 2 recorded as failing pass by name**, which is what the phase turns on:
`testExactlyOneHandlerClaimsAFileLocalDeclarationAndItIsTheMemberHandler` (TC-02) and
`testMemberInplaceRenameIsOfferedForAFileLocalDeclaration` (the predicate inversion). The seven
document-layer cases Runs A and B reddened — TC-01, TC-03, TC-05, TC-06, TC-07, TC-08, TC-15 — are
green, as are all sixteen `LuaInplaceRenameTest` cases, `LuaFindUsagesTest` (13),
`LuaSafeDeleteTest` (12), `LuaCatsParamRenameTest` (7, and `git diff` on it is empty),
`LuaLabelRenameTest` (4) and `LuaRenameConflictTest` (12).

**`verifyPlugin` green**, so the added registration resolves to a real class;
`git diff src/main/resources/META-INF/plugin.xml` is the one added `renameHandler` element and
nothing else; `git diff src/main/gen` empty; `ktlintCheck` green, run alone.

**Task 3.6 was applied at both sites named**, `renameElement` and `findReferences`, through one
private `declarationLeafOf` helper so there is a single place carrying the KDoc that says this is a
`REFACT-01` defect rather than an in-place workaround.

**Risk 1.3 bit, and the correction is not the line number.** The `getSelectedInEditorElement`
`LOG.error` is at `InplaceRefactoring.java:859` in the shipped GoLand 2026.1.3 and `:860` in the
`intellij-community` checkout design §1 Provenance names — both right for their own build.
`risks-and-gaps.md` Risk 1.3 records it with both build identities, and it **supersedes RD-2's**
first finding, which had recorded REFACT-01's `:859` as "off by one" after comparing it to the other
build. The citations for that statement now name the build they came from.

**Estimate**: 3-5 h. **Actual**: within it.

## Phase 4 — Mutation proof [Must]

For every case in `requirements.md`'s table, apply its named mutation, run that case, and record
the result. Use the `mutation-proof` skill; snapshot and restore with
the `temporary-edits` skill.

| # | Task |
| :--- | :--- |
| 4.1 | Execute each mutation named in the `requirements.md` table and record RED/GREEN per case, in the case's own KDoc |
| 4.2 | For any case that stays GREEN under its named mutation, either find a reachable mutation or delete the case and record why the requirement is untestable — do not invent an assertion to fill the row |
| 4.3 | Execute TC-15's mutation specifically — deleting `LuaRenameProcessor.canProcessElement`'s final `LuaDeclarationSite.kindOf(element) != null` disjunct (`LuaRenameProcessor.kt:90`) — and record that TC-15 goes RED **because `CommonRefactoringUtil.RefactoringErrorHintException` escaped the driver**, thrown by `showErrorHint` inside `PsiElementRenameHandler.canRename` before its `return false` (`PsiElementRenameHandler.java:139-141`; `CommonRefactoringUtil.java:84-85`). Record the exception's type and message. Do **not** record it as "no template started and the document is unchanged": `tryInlineRename` never returns under that mutation, so it asserts no document at all. Do **not** substitute the historical `getUseScope`-plus-no-`getNameIdentifier` state either: its document (`local counter = 0` / `print()` / ` =  + 1`) was measured on Route A, which this design does not take, and removing `getNameIdentifier()` does not compile once §3.1's supertype is present (`PsiNameIdentifierOwner.java:14-15`) |
| 4.4 | Record every case `requirements.md` labels a **guard** — read the labels off its rows — as a guard with its argument, not as a passed mutation. A guard with a GREEN verdict and no argument is indistinguishable from a mutation that failed to redden. Every label is now unconditional: read it off the row and record the argument the row states |
| 4.5 | Execute TC-09's **M3** — revert task 3.6's normalisation at `LuaRenameProcessor.kt:255-256` — and record two results, not one: TC-09 RED, **and `LuaCatsParamRenameTest.testParamTagFollowsParameter` still GREEN**. The second half is what makes M3 absence-detecting rather than a repeat of M2, and a run that records only the first half does not discharge it |
| 4.6 | Execute TC-04's mutant and TC-09's **M1** — each the deletion of the leaf→`LuaNameRef` normalisation from `LuaInplaceRenameHandler.invoke` — and record that each case went RED with no template started and the document unchanged. This is the evidence that stands in for Phase 2's fail-first pass for those two cases, so it is not optional and a GREEN verdict on either is a blocker, not a finding. Execute TC-09's **M2** separately and record it as what it is: proof the in-place commit reached `LuaRenameProcessor.renameElement`, shared with the dialog path and therefore not absence-detecting |
| 4.7 | Confirm every mutation was reverted: `git status --porcelain` empty, `git diff` empty |

**Verification**

- [x] Every case in the table has a recorded verdict: RED under its named mutation, or **guard**
      with the argument `requirements.md` states. A row naming more than one mutant — TC-09 — has a
      verdict and a recorded mechanism for each.
- [x] TC-04 and TC-09, which Phase 2 exempted from the fail-first pass, each went RED under a mutant
      that removes `LuaInplaceRenameHandler`'s own contribution. Without that, neither case has any
      absence-detecting evidence and the exemption was not discharged.
- [x] Every mutation that was executed compiled. A mutation that does not compile is recorded as a
      planning defect against `requirements.md`, not as a verdict.
- [x] Every case `requirements.md` labels a guard is recorded as a guard with its argument, and no
      case it labels a gate is recorded as a guard. Read the labels off the rows.
- [x] TC-08's RED verdict records that the **capture message** was missing from the exception.
      Its named mutation leaves the exception type and the document text intact — measured, DR-04
      run 2 — so a verdict citing either of those has recorded an assertion that stayed green as if
      it were the gate.
- [x] The recorded reason for each RED verdict is the mechanism its row names, not merely "the test
      failed" — in particular for every row that predicts a **thrown** failure rather than a
      text mismatch — TC-01, TC-06, TC-07 and TC-15. TC-06 shares TC-01's mutation and fixture and
      therefore its route: `LOG.error` (`InplaceRefactoring.java:859` in GoLand 2026.1.3, `:860` in the `intellij-community` checkout design §1 Provenance names) throws inside `doRename`,
      before the template-started assertion runs.
- [x] The full suite is green after every mutation is reverted.

**Estimate**: 4-6 h.

### Phase 4 record — the mutation sweep, executed 2026-08-26

**Fourteen mutants, one per named mutation, each applied alone and restored before the next.** Every
run is `test --rerun --no-build-cache` filtered to the six classes the rows touch —
`LuaInplaceRenameTest`, `LuaFindUsagesTest`, `LuaSafeDeleteTest`, `LuaCatsParamRenameTest`,
`LuaRenameConflictTest`, `LuaLabelRenameTest` — **64 tests, 0 failures on the unmutated tree**,
which is the control every row below is read against. Snapshot and restore went through the
`temporary-edits` helper; `git status --porcelain` was empty between mutants.

| Mutant | Cases RED | Cases GREEN that the row expects red | Mechanism observed | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| `LuaNameRefBaseImpl.getNameIdentifier()` → `= null` | **TC-01**, **TC-06**, and TC-03, TC-05, TC-07, TC-08, TC-09, TC-15 | — | `TestLoggerFactory$TestLoggerAssertionError: null by MemberInplaceRenamer`, from `Logger.error` in `InplaceRefactoring.getSelectedInEditorElement` | **CAUGHT** for both rows, by the named route |
| restore the shipped `isInplaceRenameAvailable` | **TC-02** | — | `AssertionFailedError` naming a `VariableInplaceRenameHandler` as the survivor of the registry's removal loop | **CAUGHT**, isolated |
| `reference.isReferenceTo(target)` → `false` (`LuaNameReferenceSearcher.kt:76`) | **TC-03**, plus 21 others | — | `FileComparisonFailedError` from `checkResult`; on the identical fixture TC-05 printed the document as `local total = 0` / `print(counter)` / `counter = counter + 1` | **CAUGHT**; wide blast radius recorded, not repaired |
| delete the leaf→`LuaNameRef` normalisation from `LuaInplaceRenameHandler.invoke` | **TC-04** and **TC-09 (M1)** — and nothing else | — | `RuntimeException` ← `IncorrectOperationException: … '' cannot be written as a Lua identifier`, via `MemberInplaceRenameHandler.doRename:88` → `performDialogRename` → `RenameProcessor` → `refuseRewrite` | **CAUGHT and absence-detecting**; both exemptions discharged |
| `renameElement` skips the usage loop | **TC-05**, plus 13 others | — | `junit.framework.ComparisonFailure` on the **hard-coded** text; the two-path equality stayed green, as the row predicts | **CAUGHT**, on the intended assertion |
| drop the reserved-word half of `LuaNamesValidator.isIdentifier` | **TC-07** | — | `RuntimeException` ← `IncorrectOperationException: … 'end' cannot be written as a Lua identifier` | **CAUGHT**, isolated |
| delete the capture rule from `LuaRenameConflictDetector.collisions` | **TC-08**, plus `LuaRenameConflictTest.testCaptureOfRenamedUsageIsReported` and `…testCancellationIsCheckedPerUsageNotPerCollisionsCall` | — | `AssertionFailedError` on the **MESSAGE** assertion only — the exception still threw and the document was still unchanged; the surviving message was the shadow rule's | **CAUGHT on the message**, as the row requires |
| replace the `---@param` hoist with `null` (**M2**) | **TC-09**, plus three `LuaCatsParamRenameTest` cases | — | `FileComparisonFailedError` | **CAUGHT, NOT absence-detecting** — recorded as the row states |
| revert §3.6's normalisation at `renameElement`'s call site (**M3**) | **TC-09**, and **nothing else** | — | `FileComparisonFailedError`; `LuaCatsParamRenameTest` **green**, all seven cases | **CAUGHT and absence-detecting for §3.6** |
| delete the `isFileLocal` clause from `isMemberInplaceRenameAvailable` | **TC-10**, plus the predicate guard `testMemberInplaceRenameIsWithheldFromAGlobalDeclaration` | — | `AssertionFailedError` naming a `MemberInplaceRenameHandler` where none may be offered | **CAUGHT** |
| delete the `LuaLabelName` clause | **TC-11** | — | `AssertionFailedError` naming `PsiElementRenameHandler` — the registry fallback at `RenameHandlerRegistry.java:121-123`; `LuaLabelRenameTest` stayed green | **CAUGHT**, isolated |
| — (none executed) | — | — | — | **TC-12 is a GUARD** — see task 4.4 below |
| delete the `identifierLeafOf` normalisation (`LuaNameReferenceSearcher.kt:57`) | **none** | **TC-13** | the whole 64-test set stayed green; `BUILD SUCCESSFUL` | **SURVIVED** — see task 4.2 below |
| `LuaSafeDeleteProcessor.getElementsToSearch` returns the bare leaf | **TC-14** | — | `ComparisonFailure … expected:<[]> but was:<[local = 0]>`, the parse error the row predicts | **CAUGHT**, isolated |
| delete `canProcessElement`'s `kindOf(element) != null` disjunct | **TC-15**, plus 23 others | — | `CommonRefactoringUtil$RefactoringErrorHintException` escaping the driver — see task 4.3 | **CAUGHT by the escaping exception** |

**Every mutant compiled.** No row produced the project's signature defect of a mutation that
measures nothing, and no row needed adjusting to make it build.

#### Task 4.3 — TC-15, recorded as the task specifies

TC-15 goes RED **because `CommonRefactoringUtil$RefactoringErrorHintException` escaped the driver**,
not because a document was unchanged — `tryInlineRename` never returned, so it asserted no document
at all. **Type**: `com.intellij.refactoring.util.CommonRefactoringUtil$RefactoringErrorHintException`.
**Message**: `Cannot perform refactoring.` / `Caret should be positioned at symbol to be renamed`.
The frames are the ones the task names, at the shipped build's line numbers:
`CommonRefactoringUtil.showErrorHint(:85)` ← `PsiElementRenameHandler.canRename(:139)` ←
`RenamePsiElementProcessorBase.substituteElementToRename(:244)` ←
`MemberInplaceRenameHandler.doRename(:64)`. The message is the wrong-caret-position string rather
than a "cannot be renamed" one, which is consistent with the row's own clause: the substituted
IDENTIFIER leaf is not a `PsiNamedElement` and has no rename processor once the disjunct is gone.

#### Task 4.4 — the guards

**TC-12** is the only case `requirements.md` labels a **Guard**, and it is recorded as one, not as a
passed mutation. Its argument is the row's own and is measured, not argued: at a numeric-`for`
control variable the data context supplies **null** and `RenameHandlerRegistry` returns an **empty**
handler list (DR-05 probes `f`/`f2`/`f3`, all three caret placements). No Lunar predicate is on the
path, and the only reddening mutation is on the grammar. Its green run in every sweep above is
evidence that nothing regressed, not that the feature works.

Two half-guards are recorded with it, because a reader counting rows would otherwise count them as
coverage. **TC-13's "the declaration is not among them" half** is a guard by its row's own text —
the identity check at `LuaNameReference.kt:264` is masked by `shadowsRatherThanUses` at `:265`.
**TC-06's Esc-restore itself** is out of any mutant's reach: the restoration is entirely
`InplaceRefactoring`'s own template listener and this feature adds no commit path, document write or
listener of its own. TC-06 gates "a template started and the document came back unchanged"; that the
*restoration* returned it is evidenced live, by `human-verification-checklists.md`.

#### Task 4.5 — TC-09's M3, both halves

**TC-09 RED** under M3 — `FileComparisonFailedError` — **and
`LuaCatsParamRenameTest.testParamTagFollowsParameter` still GREEN**, with the whole of
`LuaCatsParamRenameTest` green and TC-09 the *only* failure in the run. That second half is what
makes M3 absence-detecting for §3.6 rather than a repeat of M2. The mutation was applied at
`renameElement`'s call site — `val declarationLeaf = element` — and **not** to the shared
`declarationLeafOf` helper, exactly as Phase 3's implementer flagged: reverting the helper would
have mutated `findReferences` at the same time and stopped M3 isolating §3.6.

#### Task 4.6 — TC-04 and TC-09's M1, and the one place the plan was wrong

One mutant serves both rows, and **it reddened exactly TC-04 and TC-09 and nothing else in the six
classes** — which is the absence-detecting property the exemption turns on. Both exemptions from
Phase 2's fail-first pass are therefore discharged.

**The plan's predicted observable for this mutant is wrong, and the rows should be corrected rather
than the mutant.** Task 4.6 asks for the record to say "no template started and the document
unchanged"; `requirements.md`'s TC-04 row and TC-09's M1 say the same. What actually happens is that
the fall-through to `performDialogRename` **throws out of the driver before either assertion runs**:
headlessly it constructs a real `RenameProcessor` with `initialName = null`, i.e. the empty string,
and `LuaRenameProcessor.refuseRewrite` raises `IncorrectOperationException: Rename was not applied:
'' cannot be written as a Lua identifier…`. The stack is
`refuseRewrite(LuaRenameProcessor.kt:510)` ← `renameElement(:259)` ←
`RenameProcessor.performRefactoring(:420,:434)` ← `VariableInplaceRenameHandler.performDialogRename(:137)`
← `MemberInplaceRenameHandler.doRename(:88)` ← `LuaInplaceRenameHandler.invoke(:76)`. The predicted
**branch** is right and was observed; the predicted **observable** is not, because the case never
reaches its return-value or document assertion. Phase 2's own record already established this
behaviour for the fall-through — "it throws out of the driver before any edit" — so the defect is
that `requirements.md`'s two cells were not reconciled to it. **Fix**: replace "no template starts,
`renameInPlaceViaHandler` returns `false` and the document is still …" in TC-04's Mutation cell and
in TC-09's M1 with the escaping `IncorrectOperationException` and its stack.

**TC-09's M2 is recorded separately and as what it is**: proof that the in-place commit reached
`LuaRenameProcessor.renameElement` at all. It reddened `LuaCatsParamRenameTest`'s
`testParamTagFollowsParameter`, `testOnlyTheMatchingParamTagMoves` and
`testTagOnAFunctionExpressionAssignedToALocal` alongside TC-09 — the dialog path shares that branch,
so the mutant reddens with this feature entirely absent and is **not** absence-detecting.

#### Task 4.2 — TC-13, settled by running it

**Risk 1.11 was read-not-run and is now confirmed by a run: TC-13's named mutation SURVIVED.**
Deleting the `identifierLeafOf` normalisation at `LuaNameReferenceSearcher.kt:57` and searching
`requested` directly left the entire 64-test set green — `BUILD SUCCESSFUL`. The mechanism is the
one Risk 1.11 predicted, and the green run is itself the proof of it: had the `:58` `kindOf` gate
returned early as the row claims, TC-13 would have reddened. It did not, so `kindOf(requested)` was
non-null, so `requested` was already the IDENTIFIER leaf — which is what Find Usages passes.

**TC-13 is not deleted and `REFACT-07-12` is not untestable, because a reachable mutant exists and
was measured.** In the TC-03 sweep, `reference.isReferenceTo(target)` → `false`
(`LuaNameReferenceSearcher.kt:76`) reddened TC-13's gating half with
`AssertionFailedError: Expected the read, the write and the read inside it: [] expected:<3> but
was:<0>`. That is the row's own expected mechanism — "the searcher yields nothing, so the 'exactly
the reads and writes are reported' half fails" — reached through a different line.

**The correction, and its cost, stated rather than swapped in silently.** TC-13's Mutation cell
should name `:76` instead of `:57`. What is lost is the claim the cell currently makes that the
mutation is "this case's alone": at `:76` it is shared with TC-03 and with every other case that needs the searcher to find a usage — a set that grows with each new consumer, so TC-13 stops
being evidence that the **Find Usages path** specifically is intact and becomes evidence that the
**searcher** is intact. What TC-13 still guarantees either way is the thing DR-03 found nothing in
the 2851-name baseline asserted: that Find Usages over a file-local declaration reports exactly its
reads and writes and not itself, which is the `REFACT-07-12` regression guard §3.1's grant of
`PsiNameIdentifierOwner` needs. No assertion was invented to fill the row.

#### Task 4.7 — restoration and the closing gate

`git status --porcelain` empty and `git diff` empty after the last mutant; the only tree changes
this phase leaves are the KDoc verdicts task 4.1 requires and this record.

- **Full unit suite**: `BUILD SUCCESSFUL in 9m 39s`, `:test` executed rather than `FROM-CACHE`.
  JUnit XML aggregate over 458 files, every one carrying this run's mtime:
  **`tests=2865 failures=0 errors=0 skipped=1`** — exactly the `66da8b52` baseline.
- **`ktlintCheck`**: `BUILD SUCCESSFUL in 19s`, run alone.

**One environment note for the next reader.** The libvirt `gce-builder` host became unreachable
mid-sweep (its gateway dropped, not just the VM), and the GCE backend's **SPOT** instance was then
preempted during `:test` — the run died with `Canceling supervisor scopes: … SIGTERM or SIGKILL` and
no `BUILD` line, which is the one reliable preemption tell. The sweep completed on a recreated
non-preemptible instance, `GCE_BUILDER_BACKEND=gce GCE_BUILDER_PROVISIONING_MODEL=STANDARD` — an env
override, so `config.sh` is unmodified. `STANDARD` is the value that works; `ON_DEMAND`, which
`AGENTS.md` names, is not a valid provisioning model.

**Estimate**: 4-6 h. **Actual**: within it.

## Phase 5 — Live verification and documentation [Must]

| # | Task |
| :--- | :--- |
| 5.1 | Run `human-verification-checklists.md` through `.agents/skills/verify-in-ide`, attaching screenshots |
| 5.1a | Record the completion-ranking consumers DR-03 could not measure — `RecentPlacesFeatures.findDeclaration` and `VcsFeatureProvider` (design §4). Both are registered `<completion.ml.elementFeatures language="">`, so an empty `language` means every language and there is no gate making them unreachable; `findDeclaration` answers `null` on base and the enclosing `LuaNameRef` after §3.1. The effect is a ranking feature value, i.e. **completion item order**, which the unit-test container cannot show because the ranking plugin is not loaded there. In a live IDE, invoke completion at a Lua name and record the item order against the baseline build. A difference is a finding to record, not necessarily a defect |
| 5.2 | Set `requirements.md`'s Status column to `Full` for each requirement the evidence supports, and leave the others at their measured state with the reason |
| 5.3 | Apply RD-1's proposed `REFACT-01-12` row edit — **supervisor only**, after this feature's review gate passes |
| 5.4 | Update `CHANGELOG.md` under the current milestone: in-place rename is user-facing |
| 5.5 | Update `docs/roadmap.md`: `REFACT-07`'s row moves to `done` and is removed per the mint-and-close convention |
| 5.6 | Replace every "Read, not run" label in `design.md` §1's evidence table with the executed evidence Phase 0 produced — including every row DR-05 settles |

**Verification**

- [x] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs` — green.
      `lint_docs: 915 docs checked, 0 error(s)`; `lint_planning: 220 feature(s) checked, 0 error(s)`.
- [x] The `verify-in-ide` session shows: <kbd>Shift+F6</kbd> on a Lua local starts a template; typing
      updates the declaration and every usage together; <kbd>Enter</kbd> commits; <kbd>Esc</kbd>
      restores. All four observed live in GoLand 2026.1.3, with captures in
      `phase-5-live-evidence/`; <kbd>Esc</kbd> restore is evidenced byte-for-byte by an unchanged
      md5 after *Save All* (`05-esc-restores-byte-for-byte.png`).
- [x] **Task 5.1a is run, not deferred.** The completion-ranking A/B was taken 2026-08-27 against a
      baseline build (this branch with §3.1 reverted), and the two builds were told apart by
      **bytecode** rather than the version string they share: `javap` shows `LuaNameRefBaseImpl`
      without `PsiNameIdentifierOwner` on base (jar md5 `d1244987…`) and with it on treatment
      (`0429cf13…`). Navigation history was equalised structurally — `RecentPlacesStorage` has no
      `@State` so it starts empty every launch; both arms ran on a fresh IDE over a deleted sandbox
      `system/`; both were driven by the same unedited script (md5 `c6002f0a…`); and Recent
      Locations was captured in each arm and matched. **Order identical — in fact md5-identical
      frames.** Two executed reasons underwrite it: the value `findDeclaration` feeds downstream is
      `[]` on both arms, and no `com.intellij.completion.ml.model` provider matches Lua in GoLand
      2026.1.3 (registered set: SQL, Go, JavaScript, TypeScript, Shell). Closes the two DR-03 rows
      `risks-and-gaps.md` had left outstanding. `REFACT-07-12` nevertheless stays **`Partial`**: the
      two minimap rows are still unaudited because those classes are absent from the shipped
      platform.

**Estimate**: 4-5 h.

## Phase priorities and dependencies

| Phase | Priority | Depends on | Estimate |
| :--- | :---: | :--- | :--- |
| 0 — De-risk | **Must** | — | 10-15 h |
| 1 — PSI primitive | **Must** | DR-05, DR-01 | 1-2 h |
| 2 — Tests, written to fail | **Must** | DR-05, DR-02, DR-04 | 6-8 h |
| 3 — Route selection and the leaf-caret handler | **Must** | Phases 1, 2; DR-03 | 3-5 h |
| 4 — Mutation proof | **Must** | Phase 3 | 4-6 h |
| 5 — Live verification & docs | **Must** | Phase 4 | 4-5 h |

Every phase is a `Must`, and that is not priority inflation: Phase 0 decides whether the feature is
deliverable, Phases 1 and 3 are the feature, and Phases 2 and 4 are what make Phase 3's green mean
anything — which is the specific failure this feature exists to avoid repeating.

## Requirement coverage by phase

| Requirement | Delivered in | Gated by |
| :--- | :--- | :--- |
| `REFACT-07-01` | Phases 1, 3 — §3.2's predicate for the kinds whose context supplies the `LuaNameRef`, §3.5's handler for the parameter kind | TC-01 |
| `REFACT-07-02` | Phase 3 | TC-02, DR-02 (both halves: the availability invariant and the extension-list premise) |
| `REFACT-07-03` | Phase 1 | TC-01 |
| `REFACT-07-04` | Phase 3 | TC-03 |
| `REFACT-07-05` | Phase 3 | TC-05 |
| `REFACT-07-06` | Phase 3 | TC-06, `human-verification-checklists.md` (the live half — the mutation does not reach Esc-restore itself) |
| `REFACT-07-07` | Phase 3 | TC-07 |
| `REFACT-07-08` | Phase 3 | TC-08, DR-04 |
| `REFACT-07-09` | Phase 3 (tasks 3.3, 3.4 — a parameter caret is served by §3.5's handler) | TC-09, DR-01 (f) |
| `REFACT-07-10` | Phase 3 | TC-10 |
| `REFACT-07-11` | Phase 3 (tasks 3.3, 3.4) | TC-04, DR-05 |
| `REFACT-07-12` | Phase 1 | TC-14, TC-13 (gating half), DR-03 |
| `REFACT-07-13` | Phase 3 | TC-11, and `LuaLabelRenameTest` unchanged and green |
| `REFACT-07-14` | not delivered by Lunar code — the platform supplies no element at that caret (design §8) | TC-12, recorded as a **guard** with the argument its `requirements.md` row states |
| `REFACT-07-15` | Phase 2 | TC-15, and Phase 2's fail-first verification |
| `REFACT-07-16` | not delivered — `Won't` | — |

## Definition of done

- [x] Every `Must` requirement's acceptance check in `requirements.md` passes. Every box in that
      file's Acceptance Criteria is `[x]`. `REFACT-07-01`'s check now requires a document-layer case
      per declaration kind (TC-01, TC-09, TC-16, TC-17), which is what the review found TC-01 alone
      did not deliver.
- [x] Every test case has an executed verdict recorded (Phase 4): RED under its named mutation —
      under each of them, where a row names more than one — or **guard** with its argument. TC-01 to
      TC-15 carry Phase 4's verdicts; TC-16 and TC-17 carry verdicts executed 2026-08-26 in this
      remediation, and TC-09's M3 verdict was re-executed over the full suite rather than six
      classes.
- [x] The full unit suite is green against the `f6148451` baseline, run with `--rerun --no-build-cache`.
      `2867 tests completed, 0 failed, 1 skipped` — 2865 at review plus TC-16 and TC-17.
- [x] `ktlintCheck` is green.
- [x] `git diff src/main/gen` is empty, and `git diff src/main/resources/META-INF/plugin.xml` shows
      only the `renameHandler` element design §2.4 specifies. Verified against base `3f21e386`:
      `src/main/gen` diff empty, `plugin.xml` +2 lines, the one element.
- [x] `design.md` §1's evidence table carries no "Read, not run" label for a claim Phase 0 executed,
      and design §3.5's evidence note is likewise settled — §3.5 reads "Every claim in this section
      is now **Executed**".
- [x] `human-verification-checklists.md` is complete with attached evidence. **All 39 items are
      ticked and evidenced**; the last one closed 2026-08-27, when task 5.1a's completion-**ranking**
      measurement was taken rather than deferred a third time. It was ticked only after the run the
      earlier note demanded: **two** sandboxes told apart **by bytecode** (`javap` — both builds
      report `lunar (0.18.0)`, so the version string discriminates nothing), driven through an
      **identical** navigation sequence by one unedited script, with the recorded history captured
      in both arms rather than assumed equal. **Verdict: the order does not move**, and the frames
      are md5-identical. The result also generalises past the fixture: no `completion.ml.model`
      provider matches Lua in GoLand 2026.1.3, so no feature value could reorder a Lua lookup.
      Evidence: `phase-5-live-evidence/task-5-1a-ranking-measurement.txt`,
      `31-`–`34-ranking-ab-*.png`.
- [x] The doc linters are green. `lint_docs`: 0 errors over 915 docs; `lint_planning`: 0 errors over
      220 features.
