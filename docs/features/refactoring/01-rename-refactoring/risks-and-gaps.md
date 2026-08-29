---
id: "REFACT-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-01"
priority: "medium"
folders:
  - "[[features/refactoring/01-rename-refactoring/requirements|requirements]]"
---

# REFACT-01: Risks & Gaps

## Critical Risks

### Risk 1.1: Shipping a rename that still half-applies

- **Impact**: the defect this feature exists to remove. A rename that reports success and leaves a
  usage bound to the old name is data-loss class — the file still compiles and the change is silent.
  BUG-457 measured exactly this: one occurrence renamed, four left behind, no warning.
- **Likelihood**: medium. **Every shape listed below** can produce it; each shape carrying a
  *Closed by* / *Reported by* clause is disposed of by the design section and tests it names, and
  the list is open — a new shape is added to it, never counted into a total here:
  1. a declaration kind whose usages are not resolvable (the colon-method form, `t.field`);
  2. a target the processor claims but whose usage search key is wrong;
  3. an in-place rename path that highlights fewer occurrences than the dialog path;
  4. **a non-file-local kind misclassified as file-local**, which makes §3.2 step 2 narrow the search
     to one file. Classifying a Lua 5.5 `global x = 1` through the `LuaAttName` row does exactly this
     to every one of them (`lua.bnf:217` puts an `attName` directly under `LuaGlobalVarDecl` exactly
     as under `LuaLocalVarDecl`). Closed by design §3.5 row 5, guarded by TC-28 and by the mutation
     check that deletes row 5 and requires TC-28 to go red;
  5. **a target whose usages stop resolving because the name has more than one declaration.**
     `LuaNameReference.resolve()` returns null whenever `multiResolve` yields more than one result
     (`LuaNameReference.kt:228-231`) and `isReferenceTo` is false on a null resolve (`:239`), so two
     files declaring the same global makes **every** read of it unfindable. Reported, not silently
     applied, by design §3.4 C4; guarded by TC-31.
  6. **a function-name RECEIVER segment, which §3.5 row 9 classifies as a declaration site.** In
     `function M.run() end` the `M` leaf's grandparent is the `LuaFuncName`
     (`funcName ::= nameRef funcNameProperty* funcNameMethod?`, `lua.bnf:164`) — the same grandparent
     as the `greet` of `function greet() end` — so row 9 gives `GLOBAL_FUNCTION` and rename claims it.
     When `M` has no other declaration nothing redirects the target: `LuaBlock.processDeclarations`
     enumerates local/global var and function declarations and bare assignments but has **no
     `LuaFuncDecl` branch** (`LuaBlockExt.kt:38-77`), so `LuaScopeProcessor`'s `is LuaFuncDecl` arm
     (`LuaScopeProcessor.kt:79-84`) is reachable only from inside the body via
     `LuaResolveUtil.kt:22`; and Phase 2 misses because `getQualifiedName` is null for a `LuaFuncName`
     parent (`LuaNameReference.kt:173-176`) while `LuaGlobalDeclarationIndex` keys the declaration
     under `"M.run"`. `resolve()` is therefore null at every `M.run()` call site and `isReferenceTo`
     is false on a null resolve (`:239`) — the declaration is rewritten, every call site is left on
     the old name. Closed by design §3.1 step 4a (a round trip against `functionNameLeafOf`, which also covers the
     intermediate `B` of `function A.B.run()`); guarded by TC-34a/TC-34b and by the mutation check
     that deletes step 4a and requires TC-34a to go red **on a silent half-rename**, driven end to
     end, not on a missing exception.
- **Mitigation**: the design refuses or reports rather than half-applying in every such case — design
  §3.1 steps 3-4, §3.4 C4 and §6. The invariant to hold during implementation:
  **`substituteElementToRename` returns non-null only for a declaration kind whose usages
  `LuaNameReferenceSearcher` can find, and any kind it returns whose usages may not resolve is
  reported by `findCollisions` before anything is written.** TC-10, TC-19a, TC-19b, TC-26, TC-28,
  TC-29, TC-31 and TC-34a/TC-34b are the automated guards; the live check in the Phase 7 verification list
  reproduces BUG-457's exact scenario.

### Risk 1.2: A conflict detector that reports on every rename

- **Impact**: worse than no detector. Users learn to click Continue without reading, and the one
  genuine rebind is lost in the noise — while the mechanism *looks* delivered.
- **Likelihood**: medium. The C1/C2 rules are deliberately conservative, and the natural failure mode
  of a conservative rule is over-reporting. C2's declaration-site skip (design §3.4 C2 step 3) is the
  single line separating "warns on real rebinds" from "warns whenever the target name appears
  anywhere in the file".
- **Mitigation**: TC-16 is the negative case, and the plan's mutation-proof task requires deleting
  C2 step 3 and confirming TC-16 goes red before the phase is accepted. Three tests asserting an
  exception *is* thrown cannot distinguish a working detector from a broken one.

### Risk 1.3: Regressing resolution while consolidating prior art

- **Impact**: Phase 1 edits `LuaNameReference.declarationIdentifier` and rewrites
  `LuaFindUsagesProvider.canFindUsagesFor` — both sit on the resolution/indexing path used by
  completion, navigation, inspections and the type engine. A regression here is far more expensive
  than the feature.
- **Likelihood**: low-medium. The change is additive (one new precedence step, one new accepted
  grandparent), but adding `LuaFuncNameProperty` means `function M.run()` becomes a *findable
  declaration* for the first time, which changes Find Usages and Safe Delete behaviour.
- **Mitigation**: Phase 1's exit criteria require `LuaFindUsagesTest`,
  `LuaFindUsagesCrossFileTest`, `LuaSafeDeleteTest`, `LuaReferenceTest`, `LuaNavigationTest` and
  `ShadowingVariableInspectionTest` to pass **unchanged**, plus a full corpus sweep
  (`test -PwithCorpus --rerun --no-build-cache`). A clean `git status` on
  `src/test/resources/corpus/` is not evidence the sweep ran — the baselines are only rewritten
  under `-PrecordCorpusBaseline`.

### Risk 1.4: The interim refusal and the real processor both registered

- **Impact**: `RenamePsiElementProcessorBase.forPsiElement` returns the **first** extension whose
  `canProcessElement` matches. `LuaUnsupportedRenameProcessor.canProcessElement` claims every Lua
  `PsiNamedElement` except labels and files, so if it survives the merge it will win over
  `LuaRenameProcessor` for most targets and every rename will keep being refused — while the tests
  for the new processor, which instantiate it directly, stay green.
- **Likelihood**: low, but the failure is silent and the test suite cannot see it.
- **Mitigation**: Phase 2 deletes the class, its test and its bundle key in the same commit as the
  `plugin.xml` swap. A post-Phase-2 grep must return nothing:
  `grep -rn "LuaUnsupportedRenameProcessor\|refactoring.rename.unsupported=" src/`.
- **The same `forPsiElement` mechanic cuts the other way, and that is now specified.** `LuaRenameProcessor`
  claiming too *much* is as damaging as the interim processor claiming everything: `kindOf(LuaLabelName)`
  returns `LABEL`, not null, so a predicate written as `kindOf(element) != null` would claim labels,
  the platform default would never see them, and REFACT-04's working rename would abort at design
  §3.1 step 4. **Asserting the exclusion in prose, and testing it with TC-24, is not enough — it has
  to be written into the predicate itself.** Design **§3.0** specifies `canProcessElement`
  literally and in order; TC-25 is the unit guard, and the plan's mutation-proof list requires
  deleting the exclusion line and confirming TC-24 **and** TC-25 both go red.

### Risk 1.5: In-place rename crashing the IDE

- **Impact**: `VariableInplaceRenameHandler.createRenamer` performs an unchecked
  `(PsiNamedElement) elementToRename` cast. Because REFACT-01's canonical target is an IDENTIFIER
  **leaf**, offering in-place rename on the usage-site path would throw `ClassCastException` inside
  the editor.
- **Likelihood**: low if design §2.6's `element is LuaNameRef` gate is implemented as written; high
  if an implementer "simplifies" it to `LuaDeclarationSite.kindOf(element)?.isFileLocal == true`,
  which accepts leaves.
- **Mitigation**: the gate's two clauses are both mandatory and the reason is stated inline in §2.6.
  TC-12 exercises the declaration-site path; the live check in Phase 7 exercises the usage-site path
  (Shift+F6 with the caret on a usage must fall back to the dialog, not crash).

### Risk 1.6: Phase 1 regressing Safe Delete into deleting declarations with no usage search

- **Impact**: **data loss, and strictly worse than the current tree.** Phase 1 rewrites
  `LuaFindUsagesProvider.canFindUsagesFor` to `LuaDeclarationSite.kindOf(element) != null`, and
  `LuaRefactoringSupportProvider.isSafeDeleteAvailable` delegates straight to it
  (`LuaRefactoringSupportProvider.kt:30`) — so Safe Delete widens in the same commit, whether or not
  anyone means it to, and before §2.6 or Phase 7 exists. `LuaSafeDeleteProcessor.getElementsToSearch`
  (`:66-70`) then elevates a leaf to one of the three nodes §3.5 newly returns (`LuaGlobalVarDecl`,
  `LuaGlobalFuncDecl`, `LuaAssignmentStatement`), the platform re-dispatches `handlesElement` on the
  elevated node, none of the three is in the enumerated `isElevatedDeclaration` set (`:53-57`) and
  none is a `PsiNamedElement` (`LuaStatement.java:8`; `LuaStatementImpl` → `LuaBaseElement` →
  `ASTWrapperPsiElement`) — so `SafeDeleteProcessor.findUsages`'s `if (!handled && element instanceof
  PsiNamedElement)` fallback does not fire either (`SafeDeleteProcessor.java:138-166`) and the
  declaration is deleted with **no usage search at all**. Today `global x = 1` elevates to a
  `LuaAttName`, which *is* admitted, so usages are searched and only the deletion granularity is
  wrong. `LuaSafeDeleteProcessor.kt:46-52` states this exact outcome in its own KDoc.
- **Second door, same room**: even with the delegate admitted, `findUsages` searches
  `identifierLeafFor(element) ?: element` (`:86`). An elevated `LuaAssignmentStatement` that
  `identifierLeafOf` does not know is searched **as the statement**, and
  `LuaNameReferenceSearcher.isNameDeclarationLeaf` returns early unless the element is an IDENTIFIER
  (`LuaNameReferenceSearcher.kt:84-88`) — zero usages, same silent orphaning.
- **Likelihood**: certain if not designed for, and nothing before Phase 1 could have caught it:
  TC-30 asserts `declarationNodeOf` in isolation, and Safe Delete gains its first global and dotted
  fixtures only with TC-32/TC-33 in this same phase.
- **Mitigation**: design §2.6a. The two predicates keep sharing one rule (Find Usages and Safe
  Delete *must* agree on what a declaration site is); the elevation set stops being a hand-maintained
  list and becomes the round trip `declarationNodeOf(identifierLeafOf(node)) === node`, which cannot
  fall behind `declarationNodeOf` by construction. `identifierLeafOf` gains the two return-leg rows.
  TC-32 (real `SafeDeleteHandler` over a used global, must raise `ConflictsInTestsException`) and
  TC-33 (round trip for all four new shapes) are Phase 1 exit criteria, and the plan requires
  mutation-proving TC-32 against the enumerated form.

### Risk 1.7: the rename dialog's "Search in comments and strings" checkbox driving a platform `LOG.error`

- **Impact**: an IDE internal error in production, a `TestLoggerAssertionError` under
  `BasePlatformTestCase`, and a **null** replacement string reaching
  `document.replaceString(startOffset, endOffset, newText)` (`RenameUtil.java:377`). Not a cosmetic
  log line: `getStringToReplace` *returns* that null and every non-code usage substitutes it.
- **Why it happens**: `RenameUtil.processUsages` derives the *searched* string from
  `getElementToSearchInStringsAndComments` but the *substituted* string from the **renamed element**
  — `getStringToReplace(element, newName, …)` (`RenameUtil.java:145-155`). REFACT-01's canonical
  target is an IDENTIFIER **leaf**, which is not a `PsiNamedElement`, and
  `RenamePsiElementProcessorBase.getQualifiedNameAfterRename` returns null by default
  (`RenamePsiElementProcessorBase.java:106-108`), so `RenameUtil.java:209-228` falls through to
  `LOG.error("Unknown element type : " + psiElement)` at `:226`.
- **Likelihood**: **certain, from Phase 2, on one user click.** `RenameDialog.createCheckboxes` adds
  the checkbox unconditionally (`RenameDialog.java:279-282`) and `createRenameProcessor` passes
  `isSearchInComments()` into `RenameProcessor` (`:405`). Having no `isToSearchInComments` override
  only makes the box start unticked (`:93-94` seeds it from the processor).
- **Why no gate in the second draft could have caught it**: TC-13c calls the accessor and
  `ElementDescriptionUtil` directly and never enters `processUsages`, and every rename-driving TC
  went through `myFixture.renameElementAtCaret`, which hard-codes `searchInComments = false`
  (`CodeInsightTestFixtureImpl.java:1092-1096`). This is the **twin** of a hazard the design already
  handled: `renameElement` is overridden precisely to avoid
  `RenameUtilBase.doRenameGenericNamedElement`'s `LOG.error("Unknown element type:")` on the *code*
  leg (design §1). Same message, non-code leg; one was closed and the other missed.
- **Mitigation**: design §2.9's `getQualifiedNameAfterRename(element, newName, nonJava) = newName`
  override, shipped in **Phase 2** with the processor rather than in Phase 7 with the rest of §2.9.
  TC-13d drives the four-argument `myFixture.renameElement(…, searchInComments = true, …)`
  (`CodeInsightTestFixture.java:779`) — the only fixture entry point that propagates the flag — and
  the plan requires mutation-proving it against the missing override. TC-13e is the Phase-7
  end-to-end case; the live checklist adds the real-logger check, because `TestLogger` and a
  production `Logger` are not the same oracle.

## Requirement Defects

Requirements the planner believes are wrong. **`requirements.md`'s rows were deliberately not
rewritten** — the table was derived from the platform contract and Lua's scoping rules rather than
from Lunar's code, and regenerating it from an implementation would restore the circularity
BUG-450 §4 removed. Two factually-wrong `file:line` citations *were* corrected in place (RD-6, RD-8);
a line number is not a requirement. Everything else below is recorded for the requirements owner to
adjudicate.

### RD-1: `REFACT-01-18` states an `elementManipulator` is required. It is not.

- **The row says**: "`LuaRequireReference` overrides `resolve()` and nothing else, and no
  `com.intellij.lang.elementManipulator` is registered anywhere in this plugin —
  `PsiReferenceBase.getManipulator()` throws … when there is none."
- **Both halves of the observation are correct**; the implied remedy is not. `getManipulator()` is
  reached only from `PsiReferenceBase.handleElementRename` and `bindToElement`
  (`PsiReferenceBase.java:103-105, 108-112, 129-137`). `LuaRequireReferenceContributor` constructs
  the reference with an explicit `TextRange` (`LuaRequireReferenceContributor.kt:44-50`), so
  `calculateDefaultRangeInElement` — the manipulator's other caller — never runs, and
  `LuaRequireReference` is not a `BindablePsiReference`, so `RenameUtilBase` takes only the
  `handleElementRename` branch. Overriding `handleElementRename` removes the need entirely.
- **Stronger reason**: a manipulator would be *wrong here*. The new name arrives as
  `"helpers.lua"` (extension included — `RenamePsiFileProcessor` does not override `renameElement`)
  and the reference range spans the **quotes**, so
  `manipulator.handleContentChange(element, range, "helpers.lua")` emits
  `require(helpers.lua)`. Extension stripping, dotted-prefix preservation and delimiter
  preservation have to live in `handleElementRename` regardless. Design §3.7 and §9 Alternative B.
- **Suggested amendment**: reword the row's remedy clause; the status (`Not Implemented`) and
  priority (`S`) are right.

### RD-2: `REFACT-01-08` bundles two jobs with very different costs.

- **The row says** "`function Obj:m()` / `function Obj.m()`" as one `Should`, noting the receiver-text
  caveat as a constraint on implementation.
- **The two halves are not comparable.** The dotted form is cheap and ships here: `Obj.m()` produces
  a qualified name through `LuaNameReference.getQualifiedName` and resolves through
  `LuaGlobalDeclarationIndex["Obj.m"]`; the only blockers are two one-line classification gaps
  (`LuaFindUsagesProvider.kt:60-67` does not accept a `LuaFuncNameProperty` grandparent, and
  `LuaNameReference.declarationIdentifier`, `:246-258`, returns the *receiver* leaf `Obj` instead of
  `m`). The colon form cannot
  ship at all: `methodExpr ::= ':' nameRef` (`lua.bnf:300`) leaves `getQualifiedName` returning null,
  the bare-name fallbacks look up `"m"`, and the declaration is indexed under `funcName.text` =
  `"Obj:m"` — so **zero** call sites are found. Renaming it would be the BUG-457 defect again.
- **Suggested amendment**: split into `-08a` (dotted, `S`, delivered) and `-08b` (colon, `S`,
  blocked on receiver-type method resolution — DR-03).

### RD-3: `REFACT-01-15` assumes the platform's persisted settings apply. They do not.

- **The row says** "`isToSearchInComments` / `isToSearchForTextOccurrences` default to the platform's
  persisted settings and are never overridden."
- **The base implementations return a hard `false` for any non-file element**, and their setters are
  no-ops for them:
  `return element instanceof PsiFileSystemItem && RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE;`
  (`RenamePsiElementProcessorBase.java:195-212`). `RefactoringSettings` has
  `RENAME_SEARCH_IN_COMMENTS_FOR_FILE` and `RENAME_SEARCH_FOR_TEXT_FOR_FILE` but **no** `…_FOR_VARIABLE`
  equivalents (`RefactoringSettings.java:22-26`). So today the checkboxes are not merely unchecked —
  their state has nowhere to persist. Design §2.9 adds `LuaRefactoringSettings` for that reason.
- **Second defect in the same row, and it is not about persistence at all.** The row frames -15 as
  two settings accessors. The feature actually needs **four more** hooks to be non-inert or
  non-crashing: `getElementToSearchInStringsAndComments` (without it the searched string is a
  `LeafPsiElement`'s `toString()`) and `getQualifiedNameAfterRename` (without it the *replacement*
  string derivation hits `LOG.error` — Risk 1.7), plus the two setters. Design §2.9 writes out all
  six, and one of them — `getQualifiedNameAfterRename` — is a **Phase 2** defect guard rather than
  part of this `C`-priority requirement at all.
- **Suggested amendment**: reword the row's mechanism clause. Status and priority (`C`) are right.

### RD-4: `REFACT-01-01`'s "(inferred)" claim is now executed, and it was right.

Not a defect — a status upgrade. The row marks the silent partial rewrite as **(inferred)** from
platform control flow. It was reproduced live on 2026-08-22 in a sandbox IDE (caret on
`local counter = 0`, Shift+F6 → `total`: declaration renamed, four usages left bound, success
reported), and the finding is recorded in `LuaUnsupportedRenameProcessor`'s KDoc. The evidence class
can be promoted from **(inferred)** to verified.

### RD-5: `REFACT-01-07` is written as one requirement but names four different declaration forms.

- **The row says** "Rename a global — across every file", justified by "the cross-file search
  substrate exists and is tested (`LuaFindUsagesCrossFileTest`, `LuaNameReferenceSearcher` narrowing
  by `CacheManager.getFilesWithWord`)". Both statements are true; the row is nevertheless the widest
  in the table, because Lua declares a global in **four** structurally unrelated ways and the row
  reads as though there were one:
  | Form | PSI shape | Reachable cross-file today? |
  | :--- | :--- | :--- |
  | `function greet() end` | `LuaFuncDecl` → `LuaFuncName` → `nameRef` | Yes — stub-indexed in `LuaGlobalDeclarationIndex`, and also in `LuaGlobalAssignmentIndex.kt:105-107` |
  | `config = {}` | `LuaVar` under `LuaVarList` under `LuaAssignmentStatement` | Yes — `LuaGlobalAssignmentIndex.kt:95-104` |
  | `global x = 1` (5.5) | `attName` directly under `LuaGlobalVarDecl` (`lua.bnf:217`, `attNameList` is `private` at `:242`) | **No** — no indexer mentions `LuaGlobalVarDecl` |
  | `global function f() end` (5.5) | `nameRef` directly under `LuaGlobalFuncDecl`, **no `funcName` node** (`lua.bnf:229`) | **No** |
- **Why it matters to the requirement and not only to the design**: delivering the first form
  alone would still let §8 mark `-07` delivered, and the second form — a bare `x = 1` — is the
  *canonical* Lua global. Without a classification row of its own,
  `substituteElementToRename` refuses it outright. Design §3.5 rows 5, 7 and 14 and §2.10
  now cover all four, with TC-08 / TC-27 / TC-28 / TC-29 one per form.
- **Suggested amendment**: split `-07` into `-07a` (function form, `M`), `-07b` (bare assignment,
  `M`) and `-07c` (Lua 5.5 `global` declarations, `M`), or add the four-form table to the row's
  description. Status (`Not Implemented`) and priority (`M`) are right for all of them.

### RD-6: `REFACT-01-10` cites the wrong `plugin.xml` line for `LuaNamesValidator`.

- **The row says** "`LuaNamesValidator` is registered at `plugin.xml:391`".
- **Line 391 is `<nameSuggestionProvider>`.** `LuaNamesValidator` is registered at
  `plugin.xml:393-395` (`<lang.namesValidator language="Lua" implementationClass="net.internetisalie.lunar.refactoring.LuaNamesValidator"/>`).
  `LuaNameSuggestionProvider` is the class at `:391-392`. The status (`Full`) and the delegation to
  REFACT-05 are both right; only the line number is off by one registration.
- **Amendment APPLIED** (2026-08-22): `requirements.md` now reads `plugin.xml:393-395`. Correcting a
  citation is not rewriting a requirement — the row's text, priority (`M`) and status (`Full`) are
  untouched.

### RD-8: `REFACT-01`'s preamble cites the wrong `lua.bnf` line for `labelName`.

- **The preamble says** "`labelName` (`lua.bnf:252`)".
- **`labelName ::= IDENTIFIER {` is at `lua.bnf:251`** (`labelRef` is at `:247`). The claim the
  citation supports — that `labelName` is the single grammar rule behind `LuaNameDeclElement` — is
  correct; only the line is off by one.
- **Amendment APPLIED** (2026-08-22): the preamble now reads `lua.bnf:251`. Recorded here because
  RD-6 recorded the sibling defect and this one was found in the same sweep; an unrecorded silent
  correction is how a citation set stops being auditable.

### RD-7: `REFACT-01-19` lists `self` as a single "Won't", but Lua has two `self`s.

- **The row says** "`self` is a plain IDENTIFIER … but is *implicit* in `function T:m()`: there is no
  declaration site to rename". That is exactly right for the colon form and **wrong for the dot
  form**: `function T.m(self, x)` is legal Lua in which `self` is an ordinary parameter with a real
  declaration site, and it must rename like any other parameter (TC-19c).
- **Why it matters**: read as licence for an `element.text == "self"` refusal, this row breaks the
  dot form. **There is no such guard and none is to be added** (design §3.1, the note after step 5);
  `self` in the colon form is refused by the general `METHOD_FUNCTION` rule instead.
- **Also worth correcting in the same row**: it says the caret-on-`self` hazard is that there is
  nothing to rename. The measured shape is different — `LuaScopeProcessor.kt:87-93` resolves `self`
  to the **method-name** leaf of the enclosing `function T:m()`, so the hazard is renaming the
  *method*, and it only becomes live once DR-03 makes the colon form renameable (Gap 2.4).
- **Suggested amendment**: scope the "Won't" to the implicit colon-form `self`; the explicit
  parameter form is covered by `REFACT-01-04`.

## Design Gaps

### Gap 2.1: The conflict rules are conservative by construction

- **Question**: C1 fires whenever *any* declaration named `newName` is lexically visible at a site
  that must be rewritten, without asking whether it would actually win the binding after the rename.
  Is over-reporting acceptable?
- **Options / leaning**: (a) conservative visibility test — **chosen**; (b) exact "which declaration
  binds after the rename" simulation, which needs a nearest-declaration model
  `LuaScopeProcessor` does not have and would require refactoring it (design §9 Alternative C);
  (c) no detection at all, i.e. leave `REFACT-01-14` unimplemented.
- **Resolved by**: decided at planning time, not deferred. (a) wins because the platform's conflicts
  dialog is a *confirmation*, not a block — an over-report costs the user one click, an under-report
  costs them a silently rebound program. The exactness of (b) is also illusory: `LuaScopeProcessor`
  returns the first-in-source-order declaration in a block, not the last, so a "precise" simulation
  built on it would be precisely wrong (Gap 2.2). Folded into design §3.4 and §9 Alternative C.

### Gap 2.2: `LuaScopeProcessor` resolves to the first same-named declaration in a block, not the last

- **Question**: `LuaBlockExt.processDeclarations` iterates `statementList` forward and stops at
  `lastParent` (`LuaBlockExt.kt:32-36`), so in `local x = 1 … local x = 2 … print(x)` the reference
  resolves to the **first** `x`. Lua binds the last. Does REFACT-01 have to fix this?
- **Options / leaning**: no. It is a pre-existing resolution characteristic, it affects
  Go-to-Declaration and Find Usages identically today, and changing block-scope resolution is a
  corpus-sweep-class change with no bearing on whether rename rewrites the references it *does*
  find. Its only consequence here is that C2's identity test (`processor.result === dLeaf`) can
  compare against the wrong `x` in a doubly-degenerate case: one block declaring the same name twice
  *and* an existing declaration of the new name.
- **Resolved by**: recorded in design §3.4 as a known residual gap with the preview pane
  (`REFACT-01-13`) as the backstop; **DR-02** measures whether the case occurs in the corpus before
  anyone spends effort on it.

### Gap 2.3: `t.field` member access is claimed by `canProcessElement` but must be refused

- **Question**: `t.field` is a `LuaNameRef`, so `canProcessElement` returns true, and it must then be
  refused inside `substituteElementToRename`. Is the refusal reliable, or can a member access
  resolve to something that *looks* like a declaration site?
- **Options / leaning**: the general rule in design §3.1 step 3 —
  "`LuaDeclarationSite.identifierLeafOf(resolved)` or refuse" — covers it, because
  `LuaMemberFieldNavigation` returns assignment targets that are not declaration sites by
  `kindOf`'s table. The residual worry is the dotted *function* case, where `M.run` legitimately
  resolves to a `LuaFuncDecl` and renaming is correct and wanted (TC-09).
- **Resolved by**: **DR-05**, executed 2026-08-23 and folded into **design §6.1**, which carries the
  eight-row measurement table. **The worry did not materialise, and the residual one is real but
  benign.** Every plain-data-field shape refuses with `refactoring.rename.unresolved`; nothing
  resolves to something that looks like a declaration site, because `LuaMemberFieldNavigation`
  returns the member's `LuaNameRef` and `identifierLeafOf`'s `LuaNameRef` branch is gated on
  `kindOf != null`, which is `null` for a `LuaIndexExpr` grandparent. The dotted *function* case is
  the only one that reaches a genuine `DOTTED_FUNCTION`, and only when the receiver segment is
  spelled the same as the declaration's (`local M = require("mod"); M.run()`); an aliased receiver
  (`local m = require("mod")`) resolves to nothing, because the qualified name `m.run` does not match
  the `LuaGlobalDeclarationIndex` key `"M.run"`. Two things DR-05 found that Gap 2.3 did not predict
  are recorded in §6.1 and in a new §6 row: the refusal branch is `unresolved`, not
  `unsupportedTarget`, and a caret on an `M.run()` **call site** is refused by the *platform*
  (`error.cannot.be.renamed`) before this processor is consulted at all, because
  `TargetElementUtil` hands back the whole `LuaFuncDecl`.

### Gap 2.4: once the colon form is renameable, caret-on-`self` will rename the method

- **Question**: the Step 9 review established that `self` resolves to the **method-name** leaf `m` of
  `function T:m()` — `LuaScopeProcessor.kt:87-93` returns `funcName.funcNameMethod!!.nameRef.identifier`,
  and `funcNameMethod ::= ':' nameRef` (`lua.bnf:166`) — not to the class `T`. Today that is harmless:
  design §3.1 step 4 refuses every `METHOD_FUNCTION`. But `DR-03` exists to make the colon form
  renameable, and on the day it succeeds, Shift+F6 with the caret on `self` will silently rename the
  method.
- **Why the obvious guard does not work, and must not be added.** Opening §3.1 with "if the element
  is `self`, refuse" could never fire: `TargetElementUtilBase` tries `REFERENCED_ELEMENT_ACCEPTED`
  first, so the processor receives the *resolved* `m` leaf, whose text is `"m"`. Worse, on the paths
  where it *could* fire it is wrong — `function T.m(self, x)` is legal Lua whose `self` is an
  ordinary parameter that must rename normally (TC-19c). Dead in one direction and harmful in the
  other; §3.1 records the full derivation.
- **Where the guard belongs when it is needed**: in `substituteElementToRename`, keyed on the
  **caret**, not on the resolved element — the `editor` parameter is in scope and
  `PsiUtilBase.getElementAtCaret(editor)` gives the token the user actually selected. That is
  DR-03's design decision to make, not this feature's, because it is only reachable once the colon
  form stops being refused.
- **Resolved by**: folded into **DR-03**'s scope below; nothing to build in REFACT-01.

### Gap 2.5: Lua 5.5 `global` declarations were invisible to the project-wide index

- **Question**: `LuaGlobalAssignmentIndex.Indexer.map` indexes `LuaAssignmentStatement` targets and
  `LuaFuncDecl` names (`LuaGlobalAssignmentIndex.kt:95-107`); `LuaGlobalAssignmentNavigation.find`
  re-collects only `LuaAssignmentStatement`s (`:29-33`). Neither knows about `LuaGlobalVarDecl` or
  `LuaGlobalFuncDecl`. Should REFACT-01 work around that, or fix it?
- **Options / leaning**: (a) classify the 5.5 forms as file-local — **rejected**, that is Risk 1.1
  shape 4 by construction; (b) classify them as global and accept that cross-file usages are never
  found — **rejected**, that is a silent partial rewrite wearing a correct classification;
  (c) extend the index — **chosen**.
- **Resolved by**: design §2.10 — two collectors, two mirror collectors in the navigation object, and
  `getVersion()` 3 → 4. Roughly 15 lines, strictly *additive* to resolution (names that resolved to
  nothing now resolve), and gated by Phase 1's corpus sweep plus three named resolution tests. The premise worth naming: the index's coverage
  *looks* like an input to this feature and is in fact a choice this feature makes.

### Gap 2.6: a multi-target file-scope assignment becomes Safe-Deletable, and the deletion leaves a stray comma

- **Question**: §3.5 row 14 classifies **both** targets of a file-scope `a, b = 1, 2` as
  `GLOBAL_VARIABLE`, so Phase 1's `isSafeDeleteAvailable = kindOf(element) != null` makes each one a
  Safe Delete target for the first time (`canFindUsagesFor` has no `LuaVar` grandparent branch today,
  `LuaFindUsagesProvider.kt:60-66`). `declarationNodeOf` elevates the `a` leaf to its `LuaVar`, not
  to the statement, so the round trip of §2.6a holds, the delegate is kept and usages *are* searched
  — but deleting that `LuaVar` leaves `, b = 1, 2` in the file. Does REFACT-01 have to fix it?
- **Options / leaning**: no. It is the same shape the pre-existing multi-name `local a, b = 1, 2`
  already has — `declarationNodeFor` returns the `LuaAttName` for a multi-name `local`
  (`LuaSafeDeleteProcessor.kt:160-163`) and deleting it leaves `local , b = 1, 2` — so this is a
  newly *reachable* instance of an existing defect, not a new class of one. Comma-aware deletion
  changes what Safe Delete **removes**; REFACT-01 only changes what it **finds**, and REFACT-03 owns
  the removal side. The property this feature must not lose — that usages are searched before the
  delete — is preserved, which is what §2.6a exists for.
- **Resolved by**: recorded in design §2.6a's multi-target note and in the `declarationNodeOf` table
  row, and **pinned by TC-33**, which asserts the residual text `, b = 1, 2` rather than leaving it
  to be met as a surprise. It is called out here so a future `LuaSafeDeleteTest` diff is not read as
  a regression introduced by Phase 1.

### Gap 2.7: the searcher's label guard is NOT order-dependent

- **The tempting framing, and it is false**: design §3.8 replaces
  `LuaNameReferenceSearcher.isNameDeclarationLeaf` with `identifierLeafOf`-based normalisation.
  `identifierLeafOf` row 1 maps a `LuaLabelName` to its IDENTIFIER child — a **non-null** result —
  so normalising before excluding labels looks like it makes the guard dead code and puts
  REFACT-04's label rename at risk. **Do not conclude that**: the framing stops at
  `identifierLeafOf` and never asks what the *next* guard does with the leaf.
- **Answer**: the two orders are **indistinguishable**. Guard ③ tests
  `kindOf(target) == null` and returns; `kindOf` of a `LuaLabelName`'s IDENTIFIER child is null
  (§3.5 row 4 stops — its parent is a `LuaLabelName`, not a `LuaNameRef`), so a label is rejected
  under either order. Independently, this searcher can emit nothing for a label under *any* fixture:
  it only offers references attached to `LuaNameRef` composites, and a label/`goto` subtree contains
  none (`labelName ::= IDENTIFIER`, `labelRef ::= IDENTIFIER` — `lua.bnf:251,247` — neither via
  `nameRef`), while `isReferenceTo` is identity against `resolve()`
  (`LuaNameReference.kt:233-244`), whose scope walk `LuaBlock.processDeclarations`
  (`LuaBlockExt.kt:25-81`) has no `LuaLabel` branch at all (labels use `processLabelDeclarations`,
  `:83-93`).
- **Consequence for the plan**: guard ① is kept as **unreachable defence-in-depth**, documented as
  such in design §3.8 ① together with the one future change that would make it load-bearing (a §3.5
  row returning non-null for a label's IDENTIFIER leaf). **TC-35 and its mutation check are dropped**
  — the first was tautological, the second unsatisfiable. Label coverage is the existing
  `LuaFindUsagesTest.testLabelUsagesCount` / `testCanFindUsagesForLabel` and `LuaLabelRenameTest`;
  the *reachable* label exclusion, `canProcessElement`'s (§3.0 rule 1), stays gated by TC-24/TC-25.
- **Resolved by**: design §3.8 ①; nothing deferred. Recorded here rather than omitted because the
  false premise is an easy one to re-derive from `identifierLeafOf` row 1 alone.

### Gap 2.8: a shadowing inner declaration was collected as a usage of the binding it shadows (CLOSED in Phase 2)

**Found by TC-03 failing, not by review.** Design §6 asserted that nested same-named locals were
"handled by resolution, not by rename … which is why REFACT-01-03 costs nothing extra". Measured on
the builder, renaming the outer `x` of

```lua
local x = 1
do
  local x = 2
  print(x)
end
print(x)
```

to `y` produced `local y = 1 / do local y = 2; print(x) end / print(y)` — the **inner declaration
rewritten and its own usage left behind**, which breaks the file silently. That is BUG-457's failure
class reintroduced by the rename built to remove it, on a **Must** requirement.

Mechanism: `LuaResolveUtil.scopeCrawlUp` passes the child ascended from as `lastParent`, excluding a
reference's own declaring statement from scope. That is deliberate and correct — it is what makes the
RHS of `local x = x` read the outer `x` — but it also means the inner declaration's **own name**
resolves outward to the outer `x`, and `LuaNameReference.isReferenceTo`'s identity test then matches.
The pre-existing self-exclusion guard (`self.identifier === element`) only covers a declaration being
its own usage, not a *different* declaration.

**Closed by `LuaNameReference.shadowsRatherThanUses`**: a **file-local** declaration site's own name
is a new lexical binding and is never a use of the one it shadows. Restricted to file-local kinds on
purpose — shadowing is lexical, and a *global* declaration site is deliberately untouched, because
multiple declarations of one global are the ambiguity design §3.4 C4 exists to report, not something
to drop silently from a search.

Measured blast radius: **zero**. The full suite is green at 2,808 tests, and with the guard removed
TC-03 is the only failure across 69 tests spanning `LuaRenameTest`, `LuaRenameCrossFileTest`,
`LuaFindUsagesTest`, `LuaFindUsagesCrossFileTest`, `LuaSafeDeleteTest` and
`ShadowingVariableInspectionTest`. This means the same over-report existed in **Find Usages** and
**Safe Delete** before Phase 2 and no test observed it; those subsystems now agree with rename.

### Gap 2.9: the numeric-`for` declaration has no caret-reachable rename target (CLOSED — [[BUG-469]] resolved)

`numericForStatement ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) hangs the control variable's leaf
directly off the statement — the one declaration kind with no `LuaNameRef`. The leaf therefore
carries no `PsiReference`, and `LuaNumericForStatement` is a plain `ASTWrapperPsiElement`, so
`TargetElementUtilBase.getNamedElement` found no `PsiNamedElement` ancestor and
`TargetElementUtil.findTargetElement` returned **null**.

**What Shift+F6 actually did, measured end-to-end** — which this section previously recorded as
"reports 'cannot rename'", and that was wrong. Driven through the editor's own data context with
nothing injected, on `for <caret>i = 1, 10 do print(i) end`: `findTargetElement` null,
`PsiElementRenameHandler.getElement` null, `RenameHandlerRegistry.getRenameHandlers` **empty**, and
the `RenameElement` action's `update` left the presentation **disabled**. No handler was reached, so
no refusal was reported either — the key did nothing at all, with no dialog and no message.
[[BUG-469]]'s Actual section was right and this one was not.

**Closed by `LuaTargetElementEvaluator.getNamedElement`**, which supplies the control variable's own
IDENTIFIER leaf — the same element `LuaNameReference` already resolved to from a *usage* caret, so
both carets now target one element rather than two. The `TargetElementEvaluatorEx2` this gap named
as the required instrument had since arrived for BUG-472/BUG-470, so closing this cost one override
on a registered component and no new `plugin.xml` line.

Measured after the fix, same driver: `findTargetElement` returns the leaf, the data context supplies
it, and `RenameHandlerRegistry` returns exactly one handler —
`com.intellij.refactoring.rename.PsiElementRenameHandler`, the dialog path. No in-place handler
claims it, because `LuaInplaceRenameHandler.declaringNameRefOf` ends at `leaf.parent as? LuaNameRef`
and there is none; that is `REFACT-07-14`, and it is unchanged.

`LuaRenameTest.testNumericForDeclarationCaretTargetsItsControlVariable`,
`…SelectsExactlyOneRenameHandler` and `…RewritesEveryUsage` replace the case that pinned the
limitation; `testRenamingANumericForVariableFromAUsageRewritesTheDeclaration` is the control. All
four go through the real data context. REFACT-01-05 is **Full**.

### Gap 2.10: `TargetElementUtil` can hand rename a declaration NODE, and `identifierLeafOf` would misdirect it (CONTAINED)

Measured while checking Gap 2.8's fix: with the caret on `M` of `M = {}` in a file that also contains
`function M.run() end`, `TargetElementUtil` hands back the whole enclosing **`LuaFuncDecl`**, not the
`M` leaf. `canProcessElement` does not claim declaration nodes (§3.0 admits a `LuaNameRef` or a
declaration *leaf*), so the platform reports `error.cannot.be.renamed` and nothing happens.

That refusal is load-bearing rather than incidental, which is why it is recorded here rather than
left implicit. `LuaDeclarationSite.identifierLeafOf` is deliberately **total over declaration nodes**
because Safe Delete passes them, and it maps a `LuaFuncDecl` to its **last** name segment — so a
`canProcessElement` widened to `identifierLeafOf(element) != null` would answer a rename of `M` by
renaming **`run`**. `LuaRenameTest.testCaretOnAGlobalShadowedByADottedDeclarationIsRefusedNotMisdirected`
is the guard and the widening is its proven mutant. Design §6 previously claimed this fixture
redirected to `M = {}` and renamed successfully; it does not.

### Gap 2.11: `goto` to a label that does not exist is renamed by the platform default (OPEN — REFACT-04 owns the closure)

**Opened by this commit, and it ships as-is by decision at the Phase-2 review.** §3.0 rule 1 excludes
`LuaLabelName` **and** `LuaLabelRef` so that REFACT-04's working label rename keeps the platform
default processor. A `LuaLabelRef` whose label does not exist is therefore not claimed by anything
Lua-specific: `goto miss<caret>ing` with no `::missing::` in the function reaches
`RenameUtilBase.doRenameGenericNamedElement`, which rewrites that single leaf and collects no usages
— measured: `goto missing` → `goto renamed`.

**Why it is not the BUG-457 shape, and why no guard is added here.** BUG-457 is a rename that leaves
*resolvable* occurrences bound to the old name. Here nothing resolves, so there is nothing to leave
behind, and the file is invalid Lua before and after. Claiming `LuaLabelRef` to refuse this case
would put `LuaRenameProcessor` first in `forPsiElement`'s extension order for the label pair — the
exact mutation TC-25 exists to redden — and cost the one refactoring the plugin ships today. The
exposure closes with **REFACT-04** (`planned`), which owns label rename end to end and can refuse an
unresolved `goto` from inside the processor that already claims labels.

### Gap 2.12: Find Usages and Safe Delete now depend on Gap 2.8's guard, and no test of THEIRS exercises it (OPEN)

`LuaNameReference.shadowsRatherThanUses` (Gap 2.8) sits in `isReferenceTo`, which is the shared
oracle for rename, `ReferencesSearch`, Find Usages and Safe Delete. Gap 2.8's measured blast radius
records the consequence precisely: with the guard removed, **TC-03 is the only failure** across 69
tests spanning `LuaFindUsagesTest`, `LuaFindUsagesCrossFileTest`, `LuaSafeDeleteTest` and
`ShadowingVariableInspectionTest`. Those subsystems over-reported a shadowing inner declaration as a
usage before Phase 2 and now do not — a **behaviour change in two shipped surfaces whose correctness
is pinned by a rename test alone**.

Nothing is known to be broken; the risk is that a future edit to the guard is gated only by a rename
case, so a Find Usages or Safe Delete regression would land green. Closing it is one fixture per
surface — a `LuaFindUsagesTest` case asserting the shadowed inner `local x` is **not** among the
outer `x`'s usages, and a `LuaSafeDeleteTest` case asserting an outer `local x` shadowed by an inner
one is still reported as used by its own reads only. Not written in this phase because it is
coverage for the *existing* surfaces rather than for the phase's deliverable; recorded so it is
scheduled rather than rediscovered.

### Gap 2.13: `LuaNameRef.setName` fails silently, which is why the atomicity invariant is pinned by a refusal and not by a text assertion (CONTAINED)

`renameElement` mutates twice — the usages, then the declaration — and the reviewed Phase-2 code
discovered the declaration half's two failure conditions *after* the usage loop had run
(`element.parent ?: return`, `createIdentifier(…) ?: return`). Had either fired, every usage would
carry the new name and the declaration the old one: **BUG-457 inverted**. Closed by resolving the
replacement and the declaration's AST swap before the first edit and refusing the whole rename with
`refactoring.rename.rewriteUnavailable` (an `IncorrectOperationException`, which
`RenameProcessor.performRefactoring` reports); TC-36 is the gate and the reviewed ordering is its
proven mutant.

**The limit, stated rather than glossed.** With the pre-check in place the two halves cannot
disagree, because both build their replacement from `LuaElementFactory.createIdentifier` with the
same name and project. That is also why the mutant does not produce a *visible* half-rename:
`LuaNameRefElementImpl.setName` (`LuaBaseElements.kt:83-92`) skips its `replaceChild` when the
factory returns null and returns `this` regardless, so under the old ordering an unbuildable name
made every usage a silent no-op too. TC-36 therefore distinguishes **refusal from silent success**,
not renamed-usages from an unrenamed declaration. Two consequences worth having in writing:

- That silent `setName` no-op is pre-existing and wider than rename (`LuaValkeyToRedisQuickFix` and
  any future caller share it). It is out of REFACT-01's scope and is **not** a licence to keep the
  ordering defect: a second rewrite path that does not funnel through this factory — §3.6's
  `LuaCatsParamRenamer` in Phase 6 is the first candidate, editing comment text rather than a
  `LuaNameRef` — restores the visible half-apply immediately.

  **Phase 6 shipped that candidate and it does not restore the half-apply (2026-08-23).** The
  prediction was right about the mechanism and wrong about the outcome, for a reason worth keeping:
  `LuaCatsParamRenamer` has **no failure outcome**. Every exit short of the rewrite means "there is
  no `---@param` tag spelled with the old name", which is a correct no-op the requirement asks for;
  and the rewrite itself is total, because `ArgName ::= <<child>>` gives an `ARG_NAME` node exactly
  one child — a `NAME` `LeafElement`, measured — so selecting the tag BY that leaf leaves no branch
  in which a matching tag is found and cannot be rewritten, and `LeafElement.replaceWithText`
  neither parses nor validates (`LeafElement.java:137-141`). What *would* have restored the
  half-apply is placing the call before §3.3 step 2's refusal, and that is now pinned rather than
  reasoned about: hoisting it was executed and reddens `LuaCatsParamRenameTest`'s TC-20d with a
  `FileComparisonFailedError` — `---@param end number` beside a parameter still spelled `a`.
- `LuaElementFactory.createIdentifier` was reachable only as a crash before this commit:
  `createGotoStatement` ended in `!!` (`LuaElementFactory.kt:33`), so an unbuildable name threw a
  `KotlinNullPointerException` from inside the write action instead of returning null. The `!!` is
  gone and the null path is now the contract, with
  `LuaElementFactoryTest.testCreateIdentifierIsNullForANameThatCannotBeAnIdentifier` pinning it.

### Gap 2.14: a caret on an `M.run()` CALL SITE cannot rename the dotted function — the same containment as Gap 2.10, and its cost (OPEN, filed as [[BUG-465]])

Measured by **DR-05** (2026-08-23), which found it while checking the dotted-function control case;
it is not a `t.field` shape, but it is the shape a user reaches by trying one.

With the caret on the `run` of `M.run()`, `TargetElementUtil.findTargetElement` resolves the
reference and hands back the whole enclosing **`LuaFuncDecl`** — the same handback Gap 2.10 records
for the receiver caret. `canProcessElement` does not claim declaration nodes, so
`RenamePsiElementProcessorBase.forPsiElement` selects no processor and the **platform** refuses with
`error.cannot.be.renamed`, before `LuaRenameProcessor` is consulted at all. DR-05 called
`substituteElementToRename` on that element directly and it returned the correct answer — `run`,
`DOTTED_FUNCTION` — so the machinery is right and simply never runs.

**Safe, and deliberately so: this is the price of Gap 2.10's containment, not a separate defect.**
Widening `canProcessElement` to admit declaration nodes would close this gap and simultaneously
reopen Gap 2.10's misdirection, because `identifierLeafOf(LuaFuncDecl)` is the *last* name segment
either way and cannot tell "the user pointed at `run`" from "the user pointed at `M`". Closing it
properly needs the caret offset, i.e. a `TargetElementEvaluatorEx2` for Lua — the same instrument
Gap 2.9 needed for the numeric-`for` declaration, and out of scope here for the same reason. That
evaluator now exists (`LuaTargetElementEvaluator`) and Gap 2.9 is closed through it, so this gap's
stated blocker is gone; what remains is [[BUG-465]]'s own work, not the missing extension point.

**User-visible consequence, stated plainly:** renaming a dotted function works from its declaration
(TC-09) and is refused from its call sites. That is a second reason `REFACT-01-08` is `Partial`
beyond the colon form, and it is recorded so the row's scope is not read as narrower than it is.

**Filed as [[BUG-465]] (2026-08-23), on the Phase-4 reviewer's judgement, and the reason is the
`TargetElementEvaluatorEx2`.** A gap recorded only inside this feature's own document closes when
the feature does; this one does not close with it. It is the **second** gap needing that one absent
extension — Gap 2.9's numeric-`for` declaration caret was the first, and is now closed through it
— and rename-from-usage
(`REFACT-01-02`) works for every other declaration kind, so the limitation is a defect against a
requirement this feature has already delivered elsewhere rather than an unbuilt piece of it. The
bug carries the mechanism, the containment argument and the fix sketch; a roadmap row carries the
priority.

### Gap 2.15: `function M.run()` beside `M.run = function() end` — the call sites unresolve and it went unreported (CLOSED in Phase 4; [[BUG-466]] resolved)

Found 2026-08-23 while executing the Phase-4 remediation of C3/C4's dotted blindness, with its own
fixture set per case. First recorded as deliberately left open, then **closed in the same phase**
once the reason for leaving it open turned out to be false (below).

`LuaNameReference.doMultiResolve`'s qualified branch reads **two** sources — the stub index under
`"M.run"` and `LuaMemberFieldNavigation.find(project, "M.run")`. A project carrying both a
`function M.run() end` and an `M.run = function() end` therefore has a two-result `multiResolve`,
a null `resolve()`, and no findable call sites. Measured, printing each reference rather than
counting them:

| Project | `ReferencesSearch` on the declaration leaf |
| :-- | :-- |
| decl + `M.run()` call site | **1** — the call site |
| decl + `M.run = function() end` + `M.run()` call site | **1** — the *assignment target*; the call site is gone |

The count is identical in both rows, which is why this needed the elements printed: renaming
rewrites the declaration and the assignment and silently leaves `M.run()` behind.
`LuaRenameConflictDetector.collisions` returns **0** for the second project, because C4 counts only
stub hits for the qualified key and finds one.

**The one objection to fixing it, and why it does not stand.** The objection is that adding
member-field hits to C4's candidate set anchors the collision on the field's `LuaNameRef` — which
the table above proves is in the renamed symbol's **usage set** — and that the platform deletes
collision anchors from that set, so Continue would skip rewriting it: a second silent partial
rename. **Do not defer this gap on that ground: it is false**, and it is the only ground ever
offered for leaving a measured data-loss path open.

`RenameUtil.removeConflictUsages` (`RenameUtil.java:297-307`) iterates the usage set and removes
only `usageInfo instanceof UnresolvableCollisionUsageInfo` — collision *objects*, not every info
that shares an anchor *element*. It could not do otherwise: `UsageInfo.equals`
(`UsageInfo.java:348-359`) opens with `!getClass().equals(o.getClass())`, so a real usage and a
collision on the same element are never equal and both survive the `LinkedHashSet` in
`RenameProcessor.preprocessUsages` (`:246-252`). Measured against that exact call — one info
removed, the real usage on the anchor still present — and now pinned end to end by
`LuaRenameConflictTest.testCollisionAnchoredOnAUsageIsStillRewritten`, which anchors a collision on
`b.lua`'s field, presses Continue via `withIgnoredConflicts`, and asserts `b.lua` is rewritten.

**How it was actually closed.** `globalDeclarationsNamed` gained a third candidate source,
`LuaMemberFieldNavigation.find`, so C3/C4's candidate set is exactly the set
`LuaNameReference.doMultiResolve` consults for the same key. No shape guard is needed: each
navigation lookup is inert for the other's key shape. `LuaMemberFieldNavigation.find` gained
`ProgressManager.checkCanceled()` at the two levels whose bodies can force work (parse a file, walk its statements) for the new rename-time caller, matching its
sibling `LuaGlobalAssignmentNavigation.find`. Pinned by
`LuaRenameConflictTest.testDottedFunctionBesideAFieldAssignmentIsReported`; mutation-proved by
dropping the new term, which makes both new cases fail — the first with "applied silently", the
second with zero anchors.

**What is still not repaired, and is not claimed to be.** The conflict is *reported*; `c.lua`'s call
site is still not rewritten, because it is not a findable reference while `multiResolve` is
ambiguous. Reporting is what C4 exists to do — the user sees the ambiguity and can cancel — and the
"reported, not repaired" boundary is asserted in the test rather than left implicit.

### Gap 2.16: design §3.7 step 5 builds a TRUNCATED literal for a file name that is not a Lua string body (CLOSED in Phase 5)

Design §3.7's step 5 is
`LuaElementFactory.createStringLiteral(project, prefix + newModule + suffix) ?: return element`,
and §3.7's own definition of the factory is
`(createExpression(project, literalText) as? LuaTerminalExpr)?.string`. Its `?:` reads as the
failure branch, but for the case that matters it never fires.

`createExpression` wraps the text in `local _ = <literalText>` and returns the **first** `LuaExpr`
in the tree. A file renamed to `he"lpers.lua` therefore builds `local _ = "he"lpers"`, whose first
expression is the perfectly well-formed `"he"` with the remainder left as an error element. Step 5
would take that as success and step 6 would replace a caller's `require("util")` with
`require("he")` — a silent, unrequested edit to a file the user did not ask to touch, which is
BUG-457's category in a smaller costume.

**Reachable, not theoretical.** A double quote is a legal character in a POSIX file name, and the
rename **does** reach `handleElementRename` carrying one: on the parent commit `a8424c14` a probe
renaming `util.lua` to `he"lpers.lua` failed at the missing `ElementManipulator`, i.e. inside the
reference, with no earlier name validation having rejected it.

**Closed by making the factory's null real.** `createStringLiteral` returns the STRING leaf only
when `it.text == literalText`, so text that merely *starts* with a literal yields null and
`handleElementRename` declines, leaving the literal untouched. TC-18d drives the whole file rename
and asserts both halves — the file **was** renamed, and the `require` was **not** rewritten. Two
mutants confirm it: dropping the round trip reddens TC-18d and the factory's null case; a factory
that normalises delimiters reddens those plus TC-18b and TC-18c.

**What is deliberately not asserted.** An *unterminated* literal is not a null case: `local _ =
"helpers` lexes to a STRING covering the remainder, so it round-trips and is returned. That costs
nothing here, because `renamedLiteral` re-emits the captured opening delimiter run as the closing
one and so cannot produce an unterminated literal. An `assertNull` on that input was written, ran
red, and was removed rather than inverted — it asserted a false claim about the lexer.

## Technical Debt & Future Work

- **TBD-1: `REFACT-01-09` — table field / constructor key rename.** Deferred, priority `C`. Two
  independent blockers: `field ::= '[' expr ']' '=' expr | IDENTIFIER '=' expr | expr`
  (`lua.bnf:319`) makes a constructor key a bare IDENTIFIER leaf with no wrapper, no reference and
  no `PsiNamedElement` — it cannot be a rename target without a grammar change; and correctness
  needs type inference to know *which* table, plus moving the `t["field"]` string form. REFACT-01
  ships the loud refusal (design §6) and nothing more. Revisit after TYPE work lands
  `LuaClassType.resolveMember` on the resolution path.
- **TBD-2: `REFACT-01-16` — `@class` / `@alias` name propagation.** Deferred, priority `S`, and
  **sized by DR-04 (2026-08-29): it is a feature of its own, not a REFACT-01 phase.** The `@param`
  half ships (design §3.6). The type-name half has no `PsiReference` anywhere under
  `src/main/kotlin/net/internetisalie/lunar/luacats/` — measured, not read: of 70 running PSI
  elements spelling a type name, zero answer `getReferences()` non-empty, and neither tree holds a
  `PsiNamedElement`. **The criterion applied:** a REFACT-01 phase is work its shipped components can
  perform given one more step; this has no element for `Shift+F6` to start from, so the work is to
  *create* the renameable symbol — upstream of everything REFACT-01 built, and unlike `-11`/`-12`/
  `-17` it has no existing feature to move to.
  **The index-driven alternative is not one.** `LuaCatsTypeNameIndex` maps *declaration sites* only,
  so the DR-04 prototype rewrote the `@class` and left the use file byte-identical — a guaranteed
  half-apply, whose damage is silent (`LuaTypeManager.resolveType` and `LuaCatsDeclaredType.isType`
  both flip to false, demoting a documented type to prose with nothing reported).
  **Two findings shrink it:** the use sites are already word-indexed
  (`LuaFindUsagesProvider`'s `DefaultWordsScanner` covers `CommentTokens`, so the default
  `ReferencesSearch` scan reaches every cats slot), and cats PSI reports `LuaLanguage`, so a
  `psi.referenceContributor language="Lua"` suffices. **Stubbing the comment PSI is therefore NOT on
  the critical path**, contrary to the standing "correct but heavy fix" note. **One widens it:**
  there are five name slots, not two — `LuaCatsArgType`/`LuaCatsArgName` declaring, and
  `LuaCatsNamedType` / `LuaCatsTypeParam` / `LuaCatsGenericType` using. Full evidence, the
  15-file surface table and the verdict are in "DR-04 result" below.
- **TBD-3: `REFACT-01-11` — validity tracking the language level.** Deferred, priority `C`, and it
  is not REFACT-01's to fix. `LuaNamesValidator` ignores its `project` argument and consults a fixed
  `LuaKeywords.RESERVED`. The exposure is one name wide: `global` is correctly absent from
  `RESERVED` (it is a soft keyword, `lua.bnf:212`), and `goto` is unconditionally present, which
  over-rejects for Lua 5.1 — but `lua.flex:74` returns `GOTO` at every level, so Lunar could not
  parse such a file anyway. The root cause is in the lexer and the validator belongs to REFACT-05.
- **TBD-4: automatic renaming of related symbols.** No `AutomaticRenamerFactory` is contributed.
  Java-style "also rename the parameter in overriding methods" has no Lua analogue worth building;
  recorded so a future reader does not read its absence as an oversight.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :--- | :--- | :--- |
| `REFACT-01-00-DR-01` | Write a throwaway `BasePlatformTestCase` that renames a local via `myFixture.renameElementAtCaret` **against the current tree** and asserts the four-usage BUG-457 outcome, to confirm the fixture drives `substituteElementToRename` + `RenameProcessor` as `CodeInsightTestFixtureImpl.java:1092-1107` says. Discard after. | Every TC in the plan assumes this harness works | todo |
| `REFACT-01-00-DR-02` | Grep the corpus (`tooling/corpus/`) for a block declaring the same local name twice, to size Gap 2.2 before anyone spends effort on it. | Gap 2.2 | todo |
| `REFACT-01-00-DR-03` | Size receiver-type method resolution: can `LuaNameReference` resolve `obj:m()` via `LuaTypeManager.resolveType` / `LuaClassType.resolveMember` (see the type-engine notes in `.agents/AGENTS.md`) without a corpus regression? Outcome decides whether `REFACT-01-08`'s colon form is a follow-up feature or a TYPE-epic dependency. **Also owns Gap 2.4**: whatever makes the colon form renameable must simultaneously add a caret-based `self` guard in `substituteElementToRename` (`PsiUtilBase.getElementAtCaret(editor)`), because `self` resolves to the method-name leaf and would otherwise rename the method. | Risk 1.1, RD-2, Gap 2.4 | todo |
| `REFACT-01-00-DR-04` | Size `@class`/`@alias` rename: prototype rewriting a type name through `LuaCatsTypeNameIndex` and count the surfaces that would need it (docs, completion, inspections). | TBD-2 | **done 2026-08-29 — the index answers navigation's question, not rename's: it maps declaration sites only, so the prototype's rewrite moved the `@class` and left every use byte-identical. 15 consuming files counted, 12 of which degrade SILENTLY. Verdict: a feature of its own, not a REFACT-01 phase. See below.** |
| `REFACT-01-00-DR-05` | Enumerate what `t.field` resolves to for the four receiver shapes in Gap 2.3 and confirm each refuses or lands on a genuine `DOTTED_FUNCTION`. | Gap 2.3 | **done 2026-08-23 — all four shapes are safe; folded into design §6.1, see below** |
| `REFACT-01-00-DR-06` | Confirm `LeafElement.replaceWithText` is a legal edit on a `LuaCatsArgName` child inside a `LuaCatsLazyCommentImpl` (a lazy-parseable node) — the one AST operation in this design with no existing precedent in the repo. If it is not, fall back to rebuilding the comment text. | Design §3.6 | **done 2026-08-23** — executed in Phase 6 and re-verified by review: `replaceWithText` reaches `ASTFactory.leaf` via `ChangeUtil`, neither parsing nor validating, and `LuaCatsElementType` is a plain `IElementType` so the interning branch is taken. |
| `REFACT-01-00-DR-07` | Before writing §2.10's collectors, run one `BasePlatformTestCase` that puts `global count = 0` in `a.lua` and `print(count)` in `b.lua` and asserts what `LuaNameReference.resolve()` returns for `b.lua`'s `count` **on the current tree**. The design asserts it is null (nothing indexes `LuaGlobalVarDecl`) from reading `LuaGlobalAssignmentIndex.kt:95-107`, not from running it. If it already resolves, §2.10 is unnecessary and rows 5/7 alone finish the job. Paste the output into design §1's evidence table either way. | Design §2.10, Gap 2.5 | **done 2026-08-22 — it did NOT already resolve; §2.10 is load-bearing, measured** |
| `REFACT-01-00-DR-08` | Confirm the file-scope predicate of §3.5 row 14 against real PSI: for `cfg = {}` at file scope, assert `(target.parent as? LuaVarList)?.parent is LuaAssignmentStatement` and that the statement is in `containingFile.blockList.flatMap { it.statementList }`; for `function g() cfg = 1 end`, assert it is **not**. **Also assert the O(1) restatement agrees on both fixtures** — `stmt.parent is LuaBlock && stmt.parent.parent is LuaFile` — because §3.5 clause 3 ships in that form to keep `isBareAssignmentTarget` cheap enough for the indexer to call per target. The equivalence is derived from `LuaPsiImplUtil.kt:67-68` (`getChildrenOfType`, direct children) and `LuaBlockImpl.java:34-36` (`getChildrenOfTypeAsList`, direct children); this task is what makes it measured rather than derived. `LuaFile.getBlockList()` is `LuaPsiImplUtil.getBlockList` (`LuaFile.kt:31`) and the number of blocks a file exposes is read, not measured. | Design §3.5 row 14, §2.10 change 0 | **done 2026-08-22 — subsumed by DR-09's three-pass run, see below** |
| `REFACT-01-00-DR-09` | Before landing §2.10 change 0, run `LuaCrossFileGlobalResolutionTest` against a build in which `LuaGlobalAssignmentIndex.Indexer.map`'s assignment collector has been swapped for the `LuaDeclarationSite.isBareAssignmentTarget` form, and confirm its `local shadowed\nshadowed = 2` and `function f() nested = 1 end` fixtures still behave identically. The claim that the delegation is behaviour-preserving (clauses 2 and 3 are unconditionally true for a target reached by that enumeration) is currently **derived from reading**, and the index is the one component here whose defects are invisible until a user's persisted index is wrong. | Design §2.10 change 0, §3.5 row 14 | **done 2026-08-22 — delegation is behaviour-preserving, measured** |
| `REFACT-01-00-DR-10` | **Before writing any Phase-8 code**, establish whether a user can reach [[BUG-468]] at all. Drive a live IDE (`verify-in-ide`) over a **fixed four-rung ladder** — one file declaring `config = {}` plus N consuming files of 10 usages each, at **N = 1, 10, 50, 200**, five attempts per rung — renaming `config` → `settings` via Shift+F6 and pressing **Stop** as early as the button allows. Record per rung: whether the Stop button paints (`scrot` evidence), the wall-clock, and whether any file ends holding a mixture of the two names. The full procedure, the two platform gates it must not presuppose (`PotemkinProgress.java:77-79` same-millisecond suppression; `:117` + `ProgressWindow.java:77` + `ProgressUIUtil.kt:8` = 300 ms paint delay; `:84` input stealing) and the **three-outcome decision rule stated before the run** are in `implementation-plan.md`'s Verification Tasks. **Outcome decides an ordering, not the fix, and every outcome moves something**: a mixture at any rung keeps Phase 8 → Phase 7 and fixes that rung as the post-implementation fixture; a painted button with no mixture anywhere, or no painted button at the N = 200 ceiling, flips the order to Phase 7 first — the first also requiring **which dialog was up when the click landed** to be recorded (a rename shows two: **Cancel** during the usage search, **Stop** during the apply loop, and only the second is `PotemkinProgress`; see the outcome-B rule), the second retracting this gap's *read* claim that a large rename exceeds 300 ms. Paste the per-rung table into Gap 2.17 either way. | Gap 2.17, Gap 2.18, implementation-plan phase order | **done (2026-08-25) — outcome A: the Stop button paints at N = 50 and N = 200 and a click on it left a mixture on 7 of the 9 attempts where it landed; the Phase 8 → Phase 7 order STANDS. Per-rung table, the two-label coordinate collision that made a coordinate-driven click a false negative, and the caveat that N = 50 is machine-reachable but not human-reachable, are in Gap 2.17 under "DR-10 result".** |

### DR-09 result (2026-08-22)

Run rather than read, in three `gce-builder` passes over
`LuaCrossFileGlobalResolutionTest` plus a throwaway index probe
(`LuaGlobalAssignmentIndexProbeTest`, deleted after the run) that asserts
`FileBasedIndex.getContainingFiles(LuaGlobalAssignmentIndex.KEY, …)` for 15 names across nine
fixture files — `config = {}`, `local shadowed / shadowed = 2`, a nested
`local function f() nested = 1 end`, `alpha, beta = 1, 2`, `t.dottedField = 1`,
`function greet() end` + `function M.run() end`, `local mixedName = 1 / mixedName = 2 / plainName = 3`,
`do inBlock = 1 end` and `if true then inIf = 1 end`:

| Pass | Index collector | Probe | `LuaCrossFileGlobalResolutionTest` |
| :-- | :--- | :-- | :-- |
| Baseline | the existing inline rule | 1 test, 0 failures | 7 tests, 0 failures |
| Swapped | `LuaDeclarationSite.isBareAssignmentTarget` | 1 test, 0 failures | 7 tests, 0 failures |
| Mutant | `isBareAssignmentTarget` with clause 3 broken (`block.parent is LuaFuncDecl`) | **FAILED** | **3 of 7 FAILED** |

The two forms produce byte-identical index contents on every probed shape, and the two fixtures the
DR names (`local shadowed` / `shadowed = 2`, and the nested `nested = 1`) stay unindexed under both.
The third pass is what makes the first two mean something: with clause 3 mutated, the probe and
three resolution cases go red, so the comparison was capable of detecting a difference and did not
merely fail to look. Note the *limit* of the evidence: clauses 2 and 3 are unreachable-false for a
target reached by `map`'s top-down enumeration, so the probe can only detect a predicate that
**rejects** something the old rule accepted — which is the direction that silently empties an index.


### DR-05 result and the iteration-block audit (2026-08-23)

DR-05's own findings live in **design §6.1**; only the by-product is recorded here.

`LuaRenameConflictDetector`'s KDoc states an **invariant**, not a count of cancellation-checked
iteration blocks (`LuaRenameConflictDetector.kt:79-84`): every block that can load PSI, read VFS or
query an index is guarded; the rest are bounded work over already-materialised lists. **Do not
restore a count.** A count has to be re-derived by hand after every edit, and the blocks that move it
are the unguarded pure ones — which the invariant already disposes of, so the number never disagreed
with *compliance*, only with itself. The KDoc carries the recipe for checking the invariant and the
two counter-intuitive cases (`UsageInfo.getElement()` is a parse; `identifierLeafOf` over a stub hit
is a parse per hit), so a reader can verify it without recounting, and it does not go stale when a
block is added.

A **dated audit at one commit** is recorded here as the evidence for dropping the count, not as a
maintained claim: at `4458a8b0`, a strict count of every lambda-bodied operator in the class finds
**eleven** blocks — four guarded, seven not — and every block a narrower count omitted was one of the
unguarded pure ones. Nothing depends on that figure staying accurate. It was derived mechanically
rather than by eye, so the next reader can reproduce it:

```bash
grep -n '\.\(map\|mapNotNull\|filter\|forEach\|flatMap\)\s*{' \
  src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt
```

That prints **ten lines** for those eleven blocks — `ambiguousGlobal`'s `.filter { … }.map { … }` is
a single line carrying two lambda bodies — so a hand-count reading the output "correctly" still lands
on ten. That trap is the last argument for stating the property instead of a number.

### Executed finding: design §3.5's `identifierLeafOf` rows read `@NotNull` getters that log (2026-08-22)

Rows 4-7 of the `identifierLeafOf` table are written with the generated getters
(`element.nameRef.identifier`, `attNameList.firstOrNull()?.nameRef?.identifier`). Those getters are
declared `@NotNull` and return null for a partially parsed declaration — the SYNTAX-18 case this
design already cites as the reason `boundName` reads through the AST node — and the platform
**logs an error** rather than returning null.

That was harmless while the rows were private to `LuaSafeDeleteProcessor`, because the old
`isElevatedDeclaration` was a four-way `is` enumeration that never touched a getter. §2.6a's round
trip changes that: `handlesElement` now calls `identifierLeafOf` on **whatever element the platform
offers**. Measured on the builder, one fixture, `local function repeat() end`:

| Build | `LuaSafeDeleteProcessor().handlesElement(theLocalFuncDecl)` |
| :--- | :--- |
| `28efcbe7` (pre-REFACT-01) | `true`, nothing logged |
| §3.5 rows as written | **`TestLoggerAssertionError: local function / parent=local function repeat`** — an IDE internal-error balloon in production |
| rows 4-7 through a node-based `nameRefLeafOf` | `false`, nothing logged |

Fixed in the Phase 1 follow-up commit by giving rows 4-7 a `nameRefLeafOf` helper alongside
`boundName`. **Row 9 was left dereferencing a getter and was a second live instance of the same
defect, not a Phase 2 risk** — see below.

### Row 9 was the same defect, on a second route (measured 2026-08-22)

`function repeat() end` is **not** a Phase-2 hazard reached "through `funcName.nameRef`". Both
halves of that framing are false, and the following is measured rather than read:

- **The throw is one call earlier, at `element.funcName` — the ARGUMENT.**
  `LuaFuncDecl.getFuncName()` is `@NotNull` (`LuaFuncDecl.java:20-21`) and `funcDecl ::= FUNCTION
  funcName funcBody` carries `pin = 1` (`lua.bnf:189-190`), so a `LuaFuncDecl` node exists with no
  `funcName` child at all. `functionNameLeafOf` is never entered, so the deferred action —
  re-examining `funcName.nameRef` *inside* it — would not have touched the failing line.
- **It was a regression against `28efcbe7`**, where `isElevatedDeclaration` was a pure type
  enumeration that touched no getter and answered `true` safely.

Fixed by making row 9 node-based (`funcDeclNameLeafOf`, `FUNC_NAME` via `node.findChildByType`).
`functionNameLeafOf` keeps its non-null return, which design §3.1 step 4a depends on, and needs no
change: `funcName ::= nameRef funcNameProperty* funcNameMethod?` (`lua.bnf:164-166`) is unpinned at
every level, so a failed leading `nameRef` rolls the section back and **no `FUNC_NAME` node is
produced to pass in**. The hazard is always at the caller that must obtain one.

Mutation-proved on the builder, not assumed. With row 9 restored to
`functionNameLeafOf(element.funcName)`, both new tests go red on the exact reviewer stack —
`TestLoggerAssertionError` → `PsiElementBase.notNullChild(PsiElementBase.java:287)` →
`LuaFuncDeclImpl.getFuncName(LuaFuncDeclImpl.java:49)` →
`LuaDeclarationSite.identifierLeafOf(LuaDeclarationSite.kt:67)`. With the fix, green.

| Gate | Fixture | Mutant (row 9 via the getter) | Fixed |
| :--- | :--- | :--- | :--- |
| `testPartiallyParsedFunctionDeclarationIsHandledWithoutLoggingAnError` | `function repeat() end` | **FAILED**, `TestLoggerAssertionError` | pass |
| `testNoElementOfAPartiallyParsedDeclarationMakesTheProcessorLog` | 5 broken fixtures × every element | **FAILED**, `TestLoggerAssertionError` | pass |
| `testPartiallyParsedLocalFunctionIsHandledWithoutLoggingAnError` | `local function repeat() end` | pass (different route) | pass |

The third row is why the sweep test exists. Two routes into one defect were found one at a time,
by two separate measurements, because each test named a single PSI type. The sweep asks the property
instead — *no element of a partially parsed file makes `identifierLeafOf` / `declarationNodeOf` /
`handlesElement` raise* — over every element of each broken declaration form that carries a `pin`,
so a future `identifierLeafOf` row that reaches a generated `@NotNull` getter fails without anyone
remembering to add a fixture for it.

**That claim was an overclaim when written, and Phase 2 made it true rather than softening it.**
The sweep's five fixtures covered `localFuncDecl`, `funcDecl` (bare and dotted), `localVarDecl` and a
bare assignment — but **not** `globalFuncDecl`, which carries `pin = 2` (`lua.bnf:229`) and is the
only other pinned declaration form. It was safe purely because `LuaGlobalFuncDecl.getNameRef()`
happens to be generated `@Nullable`, i.e. one generator annotation away from being uncaught, and a
sweep that misses a pinned form is exactly the kind of false coverage statement this feature exists
to remove. `"global function repeat() end\n"` was added as a sixth fixture and **mutation-proved**:
rewriting `identifierLeafOf`'s `globalFuncDecl` row as `requireNotNull(element.nameRef).identifier`
makes `testNoElementOfAPartiallyParsedDeclarationMakesTheProcessorLog` fail. The other two `global`
forms are **not** reachable this way and no fixture is claimed for them: `globalVarDecl` and
`globalModeDecl` are unpinned (`lua.bnf:217`, `:223`), and `global repeat = 1` does not even remap the
soft keyword — `repeat` is not in `LuaParserUtil.DECLARATION_FOLLOWERS`, so `global` stays an ordinary
identifier and no `GLOBAL_VAR_DECL` node is built.

**Design action for Phase 2 (restated):** §3.5's `identifierLeafOf` table should be written in terms
of the node read, not the generated getters. That is a documentation debt only — no row still
dereferences one.

### `LuaGlobalAssignmentNavigation`'s `attName.nameRef` — closed, not deferred (2026-08-22)

`collectGlobalVarNames` read the `@NotNull` `LuaAttName.getNameRef()`, the same class of hazard. It
is **not** reachable by any fixture found: `attName ::= nameRef attrib?` (`lua.bnf:243`) is unpinned,
`attNameList` (`:242`) is a private unpinned rule and `globalVarDecl` (`:217`) is unpinned, so a
failed leading `nameRef` rolls the whole section back and no `ATT_NAME` node is produced. It was
still changed, to `LuaDeclarationSite.identifierLeafOf(attName)` — the point of routing every
declaration-shape question through one object is that no caller has to re-derive that unreachability
argument, and `LuaGlobalAssignmentIndex` already reads this exact shape node-based. The unpinned
reasoning is recorded in the function's KDoc so a future grammar change that adds a `pin` to
`attName` has something to invalidate.

`LuaGlobalFuncDecl.getNameRef()`, checked at the same time, is `@Nullable` in the generated
interface (`LuaGlobalFuncDecl.java:15-16`) despite `globalFuncDecl` carrying `pin = 2` — the one
place the generator's nullability and the pin agree in the safe direction. No change needed.

### DR-07 result (2026-08-22) — §2.10 is load-bearing

DR-07 asked whether a Lua 5.5 `global` already resolved cross-file, because if it did, §2.10's
collectors were unnecessary. Answered by mutation rather than by a throwaway probe, which makes the
answer a permanent gate instead of a paragraph: `LuaLua55GlobalCrossFileResolutionTest` (5 cases)
was run against a build with §2.10's two collectors deleted from
`LuaGlobalAssignmentNavigation.find` — the exact pre-REFACT-01 resolution machinery, since with no
collector nothing consults the index rows either.

| Case | Without §2.10's collectors | With them |
| :--- | :--- | :--- |
| `global count = 0` read from another file | **FAILED** — `Undeclared variable 'count'` | pass |
| `global alpha, beta = 1, 2` read from another file | **FAILED** — `expected:<[<empty>]> but was:<[Undeclared variable 'alpha'; Undeclared variable 'beta']>` | pass |
| `global function greet() end` called from another file | **FAILED** — `Undeclared variable 'greet'` | pass |
| a name declared nowhere | pass (still reported) | pass |
| `local shadowed` + `global shadowed = 2` | pass (still reported) | pass |

So it did **not** already resolve, and rows 5/7 alone would have shipped the Risk 1.1 shape exactly:
a declaration classified project-wide whose usages are unresolvable in every other file. The two
negative cases staying green in both columns is what shows the collectors are not silencing the
inspection wholesale.

### DR-08 (2026-08-22) — subsumed by DR-09's run, with one limit named

DR-08 wanted the §3.5 row-14 file-scope predicate executed against real PSI, and the O(1)
restatement (`stmt.parent is LuaBlock && stmt.parent.parent is LuaFile`) shown to agree with the
`containingFile.blockList.flatMap { it.statementList }` membership test on both fixtures. DR-09's
three-pass run is that measurement, on a wider input set than DR-08 specified:

- the **baseline vs swapped** passes are precisely the two forms, run against the same nine fixture
  files and 15 names, and their index contents were identical — including DR-08's two named cases,
  file-scope `cfg = {}`-shaped assignments and a nested `function f() nested = 1 end`, plus
  `do inBlock = 1 end` and `if true then inIf = 1 end`, which DR-08 did not ask for and which are
  the shapes that separate "direct child of the file's block" from "anywhere in the file";
- the **mutant** pass broke clause 3 alone (`block.parent is LuaFuncDecl`) and turned the probe and
  3 of 7 resolution cases red, so the agreement was observed by a comparison capable of detecting
  disagreement.

**The limit, stated rather than glossed:** because both forms were exercised through the indexer's
top-down enumeration, the comparison can only detect a predicate that *rejects* something the old
rule accepted. A predicate that wrongly *accepted* a target the enumeration never reaches would be
invisible to it. That direction is covered by `LuaDeclarationSiteTest.testKindOfRejectsEveryNonDeclaration`, whose
row-14 clause-1/3/4 cases (`t.field = 1`, `function g() cfg = 1 end`, `local cfg` + `cfg = 2`) reach
the predicate through `kindOf` on a leaf rather than through the indexer's enumeration, and require
it to answer `null`.


### DR-04 result (2026-08-29) — the index answers navigation's question, not rename's

Run rather than read, in three `gce-builder` passes over a throwaway
`LuaCatsTypeRenameProbeTest` (nine probes, deleted after the run; `git diff -- src/` empty). The
fixture is `types.lua` declaring `--- @class Widget` + `local Widget = {}` + `--- @alias Handle
string`, and `uses.lua` spelling `Widget` in ten tag positions — `@type`, `@param`, `@return`,
`@class Panel : Widget`, `@field`, `Widget[]`, `table<string, Widget>`, `Widget|nil` in an `@alias`,
`@cast`, and `fun(a: Widget): Widget`.

| Probe | Question | Measured |
| :-- | :--- | :--- |
| P1 | does any type-name slot expose a `PsiReference`? | `slots=70 withReferences=0 withSingleReference=0` |
| P2 | what does `LuaCatsTypeNameIndex` return for `Widget`? | `indexedFiles=[types.lua] stubDecls=1 spelledInUseFile=68` |
| P3 | what does `ReferencesSearch` over the declaration slot find? | `classHits=0 aliasHits=0` |
| P4 | apply the index-driven rewrite — what does it leave? | `rewrittenFiles=[types.lua] declHasGadget=true staleSpellings=68 useFileUnchanged=true` |
| P5 | inventory of PSI shapes spelling the name | `NAME/LeafPsiElement=12 NAMED_TYPE=10 TYPE_PARAM=1` (+ the enclosing `ARG_TYPE`/`TYPE`/`UNION_TYPE`/… wrappers, and one Lua-side `ATT_NAME`/`NAME_REF`/`IDENTIFIER` for `local Widget = {}`) |
| P6 | containment chain of a use site | `NAMED_TYPE < DISTINCT_TYPE < ARRAY_TYPE < UNION_TYPE < TYPE < ARG_TYPE < TYPE_TAG < COMMENT < LAZY_COMMENT` |
| P7 | can the platform word index reach cats-comment use sites? | `files=[types.lua, uses.lua] … NAME=12 NAMED_TYPE=10 TYPE_PARAM=1 PARAMETERIZED_NAME=1 …` |
| P8 | the generic-head shape `Widget<string>` | `GENERIC_TYPE=1 NAMED_TYPE=1 NAME=2` |
| P9 | what a declaration-only rewrite does downstream | `before=resolveType=true isType=true` → `after=resolveType=false isType=false` |

**The premise of TBD-2 holds, and P1 is stronger than the grep behind it.** `grep -rn
'PsiReference\|ReferenceContributor\|getReferences\|PsiReferenceProvider\|ElementManipulator'
src/main/kotlin/net/internetisalie/lunar/luacats/` returns nothing over 12 files, `grep -rln
getReference src/main/gen/net/internetisalie/lunar/luacats/` returns nothing over the generated PSI,
and `grep -rn 'PsiNamedElement\|PsiNameIdentifierOwner'` over both trees returns nothing either — so
the tag name is not a *named* element, not only an unreferenced one. P1 then asks the running PSI:
of the 70 elements spelling `Widget`, **zero** answer `getReferences()` or `getReference()`
non-empty. Both registered `psi.referenceContributor`s (`plugin.xml:354-359`) are the label and
`require` contributors; neither patterns on a cats element.

**The index gives navigation's answer, not rename's — measured, and this is the crux DR-04 was
written to settle.** `LuaCatsTypeNameIndex.Indexer.map` reads `LuaCatsClassTag`'s direct
`LuaCatsArgType` child and `LuaCatsAliasTag`'s `LuaCatsArgName`; **nothing else is indexed**, so the
index is a map of *declaration sites*, which is exactly what `LuaCatsTypeNavigation` needs to jump
to a definition and exactly not what a rename needs. P2 proves the consequence rather than deriving
it: querying `Widget` returns `types.lua` **only**, while `uses.lua` — which spells `Widget` in ten
tags — is absent from the result. Note also that a `@class Panel : Widget` in `uses.lua` does not
put `uses.lua` under the `Widget` key: `getChildOfType(tag, LuaCatsArgType)` takes the first direct
child, and parent types hang off a `LuaCatsParentTypes` wrapper.

**So the index-driven rewrite was prototyped, and it is a half-apply by construction.** P4 walks
`getContainingFiles(KEY, "Widget")`, takes each `LuaCatsClassTag`'s `ARG_TYPE` leaf and applies
`LeafElement.replaceWithText("Gadget")` inside a `WriteCommandAction` — the DR-06 idiom
`LuaCatsParamRenamer` already ships. The declaration moves (`declHasGadget=true`) and **`uses.lua`
comes back byte-identical** (`useFileUnchanged=true`, 68 stale spellings). An index-driven rewrite
is therefore not a cheaper route to the same result; it is a different, wrong result, and it is
`REFACT-01-16`'s own `Partial` failure mode written at project scale.

**The damage from that half-apply is silent, and P9 measures it.** Against `--- @param target Widget
the thing to wrap`, before the rewrite `LuaTypeManager.resolveType("Widget", …)` is non-null and
`LuaCatsDeclaredType.isType` is `true`; after the declaration-only rewrite both are `false`. That
predicate decides whether the renderer treats the token as a type or as the first word of the
description (`LuaCatsDeclaredType.kt:50-61`), so the rendered doc and `LuaParameterInfoHandler`
**silently demote the type to prose**. No annotator fires, no inspection reports, nothing goes red —
the exposure is invisible until a user reads a doc popup.

**Two findings make the real implementation cheaper than TBD-2 assumed, and one makes it wider.**

- *Cheaper:* **the use sites are already word-indexed.** `LuaFindUsagesProvider.getWordsScanner`
  builds a `DefaultWordsScanner` over `LuaSyntax.CommentTokens`
  (`LuaFindUsagesProvider.kt:22-28`), and P7 confirms the consequence at runtime:
  `PsiSearchHelper.processElementsWithWord(…, UsageSearchContext.IN_COMMENTS, caseSensitive = true)`
  returns hits in **both** files and reaches all twelve cats `NAME` leaves. The chameleon is no
  obstacle — the scan expanded `LAZY_COMMENT` itself (it appears in the hit set). So the expensive
  half of an "index-driven rewrite", a new use-site index, **does not need building**: the platform's
  default `ReferencesSearch` already performs this scan and asks each candidate for its references.
  What is missing is only the references for it to ask about. Note the scan is deliberately
  over-broad — its hits include `local Widget = {}`'s Lua-side `IDENTIFIER` and every enclosing
  wrapper — so any consumer must filter to the cats slots itself.
- *Cheaper:* **cats PSI reports `LuaLanguage`.** `LuaCatsElementType`'s constructor passes
  `LuaLanguage` (`LuaCatsElementType.kt:23`), so a `psi.referenceContributor language="Lua"`
  patterned on `LuaCatsNamedType` is a legal registration; no new language registration is needed.
  *(Read from the constructor, not run.)*
- *Wider:* **there are five name slots, not two.** Declarations: `LuaCatsArgType` as the direct
  child of a `LuaCatsClassTag`, and `LuaCatsArgName` of a `LuaCatsAliasTag`. Uses: `LuaCatsNamedType`
  (P5: 10 of the 11 uses — it covers unions, arrays, parent types, `@cast`, and `fun(…)` argument
  and return slots alike), `LuaCatsTypeParam` (P5: the `Widget` in `table<string, Widget>`), and
  `LuaCatsGenericType` (P8: the head of `Widget<string>`). A reference implementation that patterns
  only on `LuaCatsNamedType` silently misses the last two.

#### Surfaces that consume a type name, counted

Reproduce with, from the repository root:

```bash
grep -rn "LuaCatsTypeNameIndex" --include=*.kt --include=*.java --include=*.xml src/
grep -rn "LuaCatsClassTag\|LuaCatsAliasTag" --include=*.kt src/main/kotlin
grep -rn "LuaClassNameIndex\|LuaAliasIndex" --include=*.kt src/main/kotlin
grep -rln "LuaCatsClassTag\|LuaCatsAliasTag\|LuaClassNameIndex\|LuaAliasIndex\|LuaCatsTypeNameIndex\|LuaCatsNamedType\|LuaCatsArgType" \
  src/main/kotlin/net/internetisalie/lunar/lang/completion/ src/main/kotlin/net/internetisalie/lunar/analysis/
```

**Fifteen production files** consume a LuaCATS type name. Classified by what a *declaration-only*
rewrite — the most an index-driven route can perform — does to each:

| # | Surface | File | Verdict |
| :-- | :--- | :--- | :--- |
| 1 | type resolution by name | `lang/psi/types/LuaTypeManagerImpl.kt` | **silently misses** — every stale use resolves to nothing (P9) |
| 2 | `@param` type-vs-prose predicate | `luacats/lang/doc/LuaCatsDeclaredType.kt` | **silently misses** — flips `true`→`false`, demoting the type to description text (P9) |
| 3 | LuaCATS doc rendering | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt` | **silently misses** — via #2, and its own parent-class lookup dangles |
| 4 | quick-doc target resolution | `lang/doc/LuaDocumentationTargetProvider.kt` | **silently misses** — a stale `@type Widget` has no target |
| 5 | doc hyperlink handling | `lang/doc/LuaDocumentationLinkHandler.kt` | **silently misses** — links keyed on the old name dangle |
| 6 | Lua-side doc rendering | `lang/doc/LuaDocumentationRenderer.kt` | **silently misses** — same lookup |
| 7 | parameter info popup | `lang/insight/hint/LuaParameterInfoHandler.kt` | **silently misses** — via #2 |
| 8 | method-chain inlay hints | `lang/insight/hint/LuaMethodChainInlayHintProvider.kt` | **silently misses** — `resolveType(receiverClass)` returns null |
| 9 | type hierarchy (parents/children) | `lang/hierarchy/LuaHierarchyUtil.kt` | **silently misses** — `@class Panel : Widget` edges sever |
| 10 | type hierarchy entry point | `lang/hierarchy/LuaTypeHierarchyProvider.kt` | **silently misses** — via #9 |
| 11 | receiver member index | `lang/indexing/LuaReceiverMemberIndex.kt` | **silently misses** — members stay keyed to the old class name for stale uses |
| 12 | name reference resolution | `lang/LuaNameReference.kt` | **silently misses** — class/alias lookups by the old name |
| 13 | Go to Class / Go to Symbol | `lang/navigation/LuaCatsTypeNavigation.kt` (+ `LuaGotoClassContributor.kt`, `LuaGotoSymbolContributor.kt`) | **untouched** — it only ever wanted the declaration, which is the one thing the rewrite gets right |
| 14 | global-symbol completion ranking | `lang/completion/GlobalSymbolRankingService.kt` | **untouched** — reads `getAllKeys(LuaClassNameIndex)`, recomputed from the stub |
| 15 | stub hoisting of tag data | `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt` | **untouched** — re-derives `className` from the rewritten comment on reindex |

**Nothing *breaks* — everything either silently misses or is untouched, and that is the finding.**
Twelve of the fifteen degrade invisibly; three are correct. There is no loud failure anywhere on the
list, which is why a half-applied type rename would ship unnoticed.

**Two of DR-04's own three named surfaces do not exist.** Its wording — "docs, completion,
inspections" — was a guess, and only *docs* survives contact (rows 3–7, five files, the largest
cluster). **Completion is one file and it is untouched** (row 14); there is **no completion of type
names inside a LuaCATS comment at all** — `grep -rn "Cats\|COMMENT"
src/main/kotlin/net/internetisalie/lunar/lang/completion/*.kt
src/main/kotlin/net/internetisalie/lunar/lang/LuaCompletionContributor.kt` returns one unrelated hit
(`LuaImportNameResolver.kt:52`). **Inspections are zero:** of the 16 classes registered as
`implementationClass="…Inspection"` in `plugin.xml`, `grep -rl luacats` over them matches exactly
one, `LuaDeprecatedApiInspection`, and its only cats import is `LuaCatsDeprecatedTag` — no inspection
anywhere consumes a type name. Conversely DR-04 named none of the three surfaces that matter most:
the **type engine** (row 1), **hierarchy** (rows 9–10), and **inlay hints** (row 8).

#### Verdict: a feature of its own, not a REFACT-01 phase

**The criterion, stated before the verdict: a REFACT-01 phase is work its shipped components can
perform given one more step; a feature of its own is work that requires a component REFACT-01's
design does not contain.** That is the line the three existing carve-outs already sit on
(`REFACT-01-11` → REFACT-05, `-12` → REFACT-07, `-17` → REFACT-04), and it is why the `@param` half
was a phase: `LuaCatsParamRenamer` starts from an element `LuaDeclarationSite` already classifies and
`RenameProcessor` already renames, and walks to one attached comment.

The type half fails that test at the first step. **There is no element for a rename to start from**
— P1 measures zero references on all 70 slots, and no `PsiNamedElement` or `PsiNameIdentifierOwner`
exists in either the hand-written or the generated cats PSI. `Shift+F6` on `--- @class Widget`
cannot begin, so there is no "one more step" to add: the work is to *create the renameable symbol*,
which is upstream of everything REFACT-01 built. The two carve-outs differ in one way, though —
`-11`, `-12` and `-17` each moved to an existing owner, and this has none, so it wants a **new**
feature rather than a transfer.

**What that feature contains, sized from the probes:**

| Component | Basis |
| :-- | :--- |
| `PsiNameIdentifierOwner` on `LuaCatsClassTag` / `LuaCatsAliasTag` | the two declaration slots (P5) |
| `LuaCatsTypeReference` + one `psi.referenceContributor language="Lua"` | cats PSI reports `LuaLanguage` (`LuaCatsElementType.kt:23`); resolves via `LuaCatsTypeNameIndex`, which already answers name → declaration (P2) |
| reference patterns for **three** use shapes | `LuaCatsNamedType`, `LuaCatsTypeParam` (P5), `LuaCatsGenericType` (P8) |
| `handleElementRename` per shape, via `LeafElement.replaceWithText` | the DR-06 idiom `LuaCatsParamRenamer` already ships |
| a `LuaFindUsagesProvider.canFindUsagesFor` branch | **no searcher needed** — the default `ReferencesSearch` word scan already reaches every cats use site (P7) |
| `LuaDeclarationSite` / `LuaRenameProcessor` entry + in-place parity | the `REFACT-07-09` pattern |
| regression coverage across 12 silently-degrading surfaces | the table above; none of them fails loudly (P9) |

**`AGENTS.md`'s "stubbing the comment is the correct but heavy fix" is not on this critical path,
and that is a correction rather than a nuance.** Stubbing buys fast cross-file lookup without
de-stubbing; a rename needs *declaration lookup* (which `LuaCatsTypeNameIndex` already gives, P2)
and *use-site enumeration* (which the platform word index already gives, P7). So the heavy fix can
stay deferred, and the half of it `AGENTS.md` says stubbing would unlock — Find Usages and Rename on
types via `PsiNameIdentifierOwner` — is exactly what this feature delivers without it.

**One design question this spike deliberately does not answer**, because it is the new feature's to
decide, not the sizing's: in the common idiom `--- @class Widget` above `local Widget = {}` the tag
name and the Lua local are two different symbols. P2 measures that both exist — `indexedFiles`
carries the tag, `stubDecls=1` carries the host `LuaLocalVarDecl` — and REFACT-01 today renames the
local while leaving the tag, which is the converse of the half-apply P4 produces. Whether one rename
must move both is a requirement, and it is unwritten.

#### What TBD-2 got wrong

Its premise and its "feature-sized job" conclusion both survive. Two of its framings do not:

- **"Renaming them needs either a reference implementation on the tag PSI **or** an index-driven
  rewrite."** The disjunction is false. P4 ran the index-driven rewrite and it rewrites the
  declaration and nothing else; it is not an alternative route to a correct rename but a
  guaranteed half-apply. The reference implementation is the only shape, and — per P7 — the index
  work TBD-2 imagined as its alternative is largely already done and reusable *inside* it.
- **"type names reach navigation only through the file-based `LuaCatsTypeNameIndex` +
  `LuaCatsTypeNavigation`."** True of navigation and misleading as a summary of the exposure: the
  same names reach the **type engine**, **hierarchy**, **inlay hints**, **parameter info** and five
  **documentation** surfaces (fifteen files, table above), and navigation is one of the only three
  that a declaration-only rewrite leaves correct.

### Gap 2.17 — cancelling a rename leaves the file inconsistent (CLOSED BY PHASE 8, 2026-08-25)

**CLOSED.** Phase 8 shipped design §3.3 steps 3, 3a and 4: `renameElement` resolves every rewrite in
a cancellable preparation phase and applies only prepared closures inside one
`ProgressManager.getInstance().executeNonCancelableSection`. A Cancel before the first edit now leaves
the file byte-identical (TC-43) instead of *k*-1 usages ahead of the declaration, and mutation-proving
that is what makes the claim more than a green run: restoring the Phase-2 apply loop reddens TC-43 on
`local counter = 0 / counter = counter + 1 / print(total)` — the half-applied shape described below,
observed a fourth time and on a fourth different occurrence. The residual — a Cancel arriving *after*
preparation is ignored — is Gap 2.18, which is accepted, not open. Everything below is the record of
the defect as it stood before Phase 8.


**Filed as [[BUG-468]]. Owned by implementation-plan Phase 8, which realizes design §3.3 steps 3, 3a and 4.**
`renameElement` rewrites every usage before the declaration, so a `ProcessCanceledException` at usage
*k* leaves *k*-1 usages on the new name and the declaration on the old one — [[BUG-457]]'s shape,
reached by pressing Cancel.

**Nothing rolls it back, and no write action ever would.** There is no `WriteCommandAction` on the
path at all: the write action is `ApplicationImpl.runEdtProgressWriteAction`'s
`lock.runWriteActionBlocking` (`:1135-1154`) and the command is opened by
`CommandProcessor.executeCommand` at `BaseRefactoringProcessor.java:453-458`. And **no IntelliJ
write action rolls back**, so do not describe this as a write action that "failed to" roll back —
that phrasing implies a mechanism which exists nowhere in the platform. What actually happens: the `ProcessCanceledException`
dies in `PotemkinProgress.runInSwingThread`'s bare `catch (ProcessCanceledException ignore) {}`
(`PotemkinProgress.java:151-162`), `doRefactoring` `return`s on `!indicator.isCanceled()`
(`BaseRefactoringProcessor.java:659-662`), and undo is **recorded** rather than deferred
(`DocumentUndoProvider.java:74-92, 126-129`) so nothing reverses the earlier edits. The exception
does not surface.

Pre-existing Phase 2/3 code, untouched by Phases 4-6. Phase 8 is now the phase that touches it, and
it precedes Phase 7 because this is the last open half of a `Must` while Phase 7 is a `Could`.

**The damage is not bounded at one usage, and it is not smaller than Gap 2.13's.** Cancelling at
usage *k* bounds the *latency* before the file breaks, not the damage: what breaks is the whole
file's declaration/usage agreement, at every *k*. And Gap 2.13's residue is a stale `---@param`
comment beside correct code, while this is broken code ([[BUG-468]] §2).

**What Phase-8 planning added by measurement**, all executed on `5b7c6ca4` and recorded in design §1:

- **`checkCanceled()` there is reachable** — the Phase-6 review flagged this as unestablished, and it
  is now established the other way: three checks per three-usage rename, each under a live
  `PotemkinProgress` with `isInNonCancelableSection = false`. So option 2 had something real to fix
  and option 1 had a real contract cost to justify. **Do not sample the indicator from a
  `DocumentListener`**: that fires inside `PomModelImpl.runTransaction`'s own non-cancelable section
  (`PomModelImpl.java:112`) and reads `NonCancelableIndicator` — the wrong instant, not a different
  answer.
- **The no-rollback mechanism is that nothing rolls back, ever.** A write action is not a
  transaction. `PotemkinProgress.runInSwingThread` swallows the `ProcessCanceledException`
  (`PotemkinProgress.java:151-162`), `ApplicationImpl.runEdtProgressWriteAction` reports it only as
  `!indicator.isCanceled()` (`:1135-1154`), `BaseRefactoringProcessor.doRefactoring` `return`s on
  that (`:659-662`), and undo is **recorded** rather than deferred —
  `DocumentUndoProvider.documentChanged` appends one `EditorChangeAction` per `DocumentEvent`
  (`:74-92, 126-129`). There is no compensating action anywhere on the path.
- **The damage does not begin at the first usage.** Cancelling at the processor's *first* check
  leaves the file untouched, because the parse inside `setName` throws before usage 1 is written.
  (The check itself does not throw: the hook cancels the indicator, and `doCheckCanceled` discards
  what `runCheckCanceledHooks` returns on its `ONLY_HOOKS` branch,
  `CoreProgressManager.java:220-222`. The next cancellation point does the throwing, and the first
  one reached is inside the rewrite of usage 1.) This matters twice: [[BUG-468]] §4's "every rename
  of a symbol with more than one usage is exposed" is right about exposure but the *first*
  interruption point is harmless, and a test that cancels at the first check is green on the defect
  — a second edge to §6's trap.
- **WHICH occurrence is left behind is not fixed, and no artifact may pin it.** Three runs on this
  fixture, each stopping after exactly one usage had been applied, each left a *different*
  occurrence carrying the new name — Phase-8 planning's cancel-at-check-2 probe the second, its
  prepared-edits probe the third, the Step-9 review's run the first. Each is "whatever `usages[0]`
  was in that run", so the three disagree about which occurrence `usages[0]` is. That is not three
  contradictory measurements; it is the array order. `RenameUtil.findUsages` appends usages in the
  iteration order of `elementProcessor.findReferences` (`RenameUtil.java:93-101, 133-142`), and
  `LuaRenameProcessor.findReferences` returns `ReferencesSearch.search(element, effectiveScope).findAll()`
  — a `Query`, whose contract specifies no ordering. **The invariant to assert is the property, not
  the string**: after a cancel at check *k*, exactly *k*-1 of the occurrences carry the new name,
  the declaration carries the old one, and the file is neither the input nor the fully-renamed
  output. An artifact that names a specific half-applied text tells an implementer to expect a
  shape their own run may not produce, and a correct reproduction then reads as a failure.
- **Draining the rewrites is necessary but not sufficient.** With every swap precomputed, an
  unwrapped apply loop still splits under a cancel arriving after the first `replaceChild`. The
  design closes it with `ProgressManager.getInstance().executeNonCancelableSection`, which is
  measured to neutralise exactly that. **The cancellation point that does the splitting is named**,
  and it is not `PomModelImpl`'s: `CompositeElement.replaceChild` (`:647`) calls
  `ChangeUtil.prepareAndRunChangeAction` (`:659`), whose `model.runTransaction(new PomTransactionBase(changedElement.getPsi())`
  (`ChangeUtil.java:148`) evaluates `getPsi()` as an **argument expression** — before
  `runTransaction` is entered and therefore before `PomModelImpl.java:112`'s own non-cancelable
  section — and `CompositeElement.getPsi()` opens with `ProgressIndicatorProvider.checkCanceled()`
  (`:719-720`). This is also why *deleting* the `checkCanceled()` would not have worked: with it
  gone the loop is still interruptible, at `RenameUtil.rename` → `LuaNameRefElementImpl.setName` →
  `LuaElementFactory.createIdentifier`, which parses — and, once that is drained too, at
  `getPsi()` above.

**What Phase-8 planning did NOT establish, stated so it is not read as established:**

- **That a user can reach this in a live IDE. — ANSWERED 2026-08-25 by `REFACT-01-00-DR-10`: they
  can. See "DR-10 result" below; the read reproduced below is what the run was built to test, and
  the ladder confirmed it at N = 50 and N = 200 and refuted nothing.** Every measurement drives cancellation
  programmatically. A real user cancels through `PotemkinProgress`'s **Stop** button, and the read
  is specific: the dialog is shown only once `now - myLastUiUpdate > delayInMillis`
  (`PotemkinProgress.updateUI:117-119`) where `delayInMillis` defaults to
  `ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS` = **300 ms** (`ProgressWindow.java:77`,
  `ProgressUIUtil.kt:8`), and input events are stolen only while
  `getDialog().getPanel().isShowing()` (`interact:84-85`), with the click turned into a cancel by
  `dispatchInputEvent` → `isCancellationEvent(e)` → `cancel()` (`:90-94`). A **second** gate sits in
  front of that one and bears on the small-rename branch specifically: `interact()` returns at once
  when it is called twice within the same millisecond — `long now = System.currentTimeMillis();`
  `if (now == myLastInteraction) return;` (`PotemkinProgress.java:77-79`) — so a rename that finishes
  inside one millisecond reaches `updateUI` at most once, and a single `updateUI` cannot satisfy
  `now - myLastUiUpdate > delayInMillis` on its own. For a rename that finishes inside 300 ms the
  dialog never paints and the user has no Stop button to press — which would make [[BUG-468]]
  unreachable for small renames while leaving it exactly as real for large ones. **Read, not run**;
  DR-10's four-rung ladder is what turns it into an observation.

  **This is now `REFACT-01-00-DR-10`, executed BEFORE Phase 8's implementation tasks rather than
  after them**, because its answer can still change a decision at that point and cannot after.
  Phase 8 was inserted ahead of Phase 7 on the ground that a `Must` outranks a `Could`; if the
  defect is not user-reachable at all, that ordering was decided on a premise that does not hold and
  Phase 7 should go first. Running the check after the code lands answers a question nobody can act
  on. **What the answer does NOT change is whether to fix it**: the failure mode is data loss, a
  mechanism that is hard to trigger is not a mechanism that is safe, and a rename over a large
  project exceeds 300 ms comfortably. Only the *ordering* is at stake.
- **How long the uncancellable window is.** See Gap 2.18.
- **Whether a multi-file rename undoes as one command.** See the Undo bullet under Test Case Gaps.

#### DR-10 result — [[BUG-468]] IS user-reachable. **Outcome A. The Phase 8 → Phase 7 order STANDS.**

Executed 2026-08-25 on `7ab48d97`, containerless live GoLand 2026.1.3 on the `lunar-builder` VM
(`Loaded custom plugins: lunar (0.18.0)`), Xvfb `:99`, per the `verify-in-ide` skill. Fixture exactly
as specified: one `decl.lua` declaring `config = {}` plus N files of ten `print(config)` each; rename
driven from the declaration by Shift+F6; five attempts per rung.

**The decision:** the Stop button paints at N = 50 and N = 200, and a click on it left a mixture on
**7 of the 9 attempts where the click landed on a dialog labelled Stop**. That is decision-rule
outcome **A**. Phase 8 (atomic application) keeps its place **ahead of** Phase 7. Under outcome A the
gap also has to record the *smallest rung that produced a mixture* as the fixture for the
post-implementation live check: that is **N = 50** — but read the caveat under the table before
using it, because N = 50 and N = 200 are not equally reachable by a human.

| Rung | Usages | Attempts | Button label when the click landed | Stop painted? | Wall-clock, Return → editor settled | Any file left holding a mixture | Backing screenshot |
| :--- | ---: | ---: | :--- | :--- | ---: | :--- | :--- |
| N = 1 | 10 | 5 | *no dialog painted — no click was possible* | **No** | 113 ms | No (5/5 renamed whole) | `dr-10-evidence/n1-recon-inflight-73ms-no-dialog.png` |
| N = 10 | 100 | 5 | *no dialog painted — no click was possible* | **No** | 157 ms | No (5/5 renamed whole) | `dr-10-evidence/n10-recon-inflight-78ms-no-dialog.png` |
| N = 50 | 500 | 5 | **Stop** ×4 (+744, +659, +688, +824 ms); attempt 5 the button was missed between polls | **Yes**, visible ≈ 64 ms | 805 ms | **Yes, 2 of 5** — a1: 44 usages on the new name, decl on the old, 30 files holding both; a4: 111 usages, 43 files | `dr-10-evidence/n50-a1-stop-click-744ms.png`, `…/n50-a2-stop-click-659ms.png`, `…/n50-a3-stop-click-688ms.png`, `…/n50-a4-stop-click-824ms.png` |
| N = 200 | 2000 | 5 | **Stop** ×5 (+2328, +2110, +2008, +1987, +1965 ms) | **Yes**, visible ≈ 1719 ms | 5013 ms (apply phase alone ends at 3419 ms) | **Yes, 5 of 5** — 374 / 418 / 420 / 373 / 401 usages on the new name, decl on the old, 170 / 174 / 186 / 170 / 179 files holding both | `dr-10-evidence/n200-a1-stop-click-2328ms.png` … `…/n200-a5-stop-click-1965ms.png` |

**The button LABEL is the observable, and it was made the gate rather than trusted.** A rename puts
up two modals and **they are drawn at the same screen coordinates** — measured, not assumed: the
search dialog's **Cancel** occupies (1149…1222, 535…563) and the apply dialog's **Stop** occupies
(1154…1227, 539…565), so a coordinate-driven click cannot tell them apart. The first design of this
run did exactly that and produced a textbook false negative: the click landed at +390 ms on the
still-unpainted search dialog, cancelled before any edit existed, and reported "no mixture"
(`dr-10-evidence/false-negative-click-on-blank-search-dialog-390ms.png`). The run was rebuilt so the
click is gated on an md5 of a 60×24 strip lying **over the button's label text** — that md5 *is* the
label — and fires only on the Stop signature. Every click in the table above therefore has a
screenshot, captured in the same loop iteration immediately before the click, showing "Renaming
global variable … " above a button reading **Stop**. Several of those frames are byte-identical
(md5 `4c5b8aad` covers n200-a2/a3/a4/a5 and n50-a1/a2/a3; `17b2ef54` covers n50-a4): the dialog is
deterministic, so this is one pixel-state recurring, not seven independent corroborations. It is
recorded rather than hidden.

**The shape on disk is [[BUG-468]]'s, and it is scattered rather than prefix-shaped.** A supplementary
capture (a sixth N = 200 run, not one of the five) with Stop clicked at +1731 ms left `decl.lua` still
reading `config = {}` while `src/use_1.lua` read `print(config)` on lines 1-3 and 5-8 and
`print(settings)` on lines 4, 9 and 10 — 689 of 2000 usages moved, 199 files holding both names. The
non-contiguity is the concrete form of this gap's own warning that **the invariant to assert is the
property, not the string**: the applied set follows `ReferencesSearch`'s unspecified `Query` order.

**Two things the data says that the plan did not expect, and they are not rounded away:**

1. **The 300 ms read is confirmed, and it bites much higher up the ladder than "small renames".**
   At N = 1 and N = 10 the *entire* rename settles in 113 ms and 157 ms — below
   `ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS`, with `myLastUiUpdate` initialised at construction
   (`PotemkinProgress.java:48`), so no `updateUI` can satisfy the delay and no button can exist.
   Those two rungs are unreachable **by anyone**, machine or human. Gap 2.17's *read* claim survives.
2. **N = 50 is reachable by this harness but NOT by a human, and the table must not be read as
   saying otherwise.** The Stop button is on screen for ≈ 64 ms at N = 50. The clicker polls at
   ≈ 80 Hz and clicked inside that window on 4 of 5 attempts; a person cannot. At N = 200 the window
   is ≈ 1719 ms, which is comfortably human. **So the honest reading of outcome A is: the defect is
   user-reachable, and the rung at which a *user* reaches it is N = 200, not N = 50.** The
   post-implementation live check under implementation-plan Phase 8 should therefore use **N = 200**
   — the smallest rung that is both mixture-producing and human-clickable — and N = 50 is recorded
   as the smallest rung a machine-speed click could break, which is the stricter regression fixture.

**Gap 2.18's residual was observed live, not only argued.** On N = 50 attempts 2 and 3 the click
landed on a genuine **Stop** button (+659 ms, +688 ms) and the project came out **fully renamed** —
the Cancel was honoured by nothing. That is today's code, before Phase 8, so it is not evidence about
the `executeNonCancelableSection` design; it is evidence that an ignored Cancel is already an
observable outcome of this dialog, which is the state Gap 2.18 proposes to make universal.

**Load bias, stated so it is not mistaken for precision.** The two N = 200 reconnaissance runs settled
at 9254 ms and 5013 ms; the difference is the screenshot loop (30 Hz full-screen capture in the first,
a 60×24 strip in the second). The wall-clock column reports the light-loop figures. Extra load
lengthens the apply phase and so makes the button *more* likely to paint — it biases toward outcome A,
which is the direction that leaves the current plan unchanged, so it is the bias worth naming. It
cannot manufacture the N = 1 / N = 10 result, where nothing painted at all.

**No IDE internal error appeared at any rung**: zero `ERROR`/`SEVERE` lines in `idea.log` across the
whole run window. (The one `net.internetisalie` `NoClassDefFoundError` in that log is dated
2026-08-15 and belongs to an earlier session.)

**What this does not change is whether to fix it** — unchanged from the pre-run statement above.

#### Phase 8 live verification — the invariant HOLDS, and the apply phase DOES repaint

Executed 2026-08-25 on `578e66a6`, containerless live GoLand 2026.1.3 on the `lunar-builder` VM,
Xvfb `:99`, per the `verify-in-ide` skill. This is the post-implementation counterpart of the DR-10
run recorded above, on the same fixture generator, and it is what closes
implementation-plan's "Live IDE verification of Phase 8".

**Which binary answered, and how that was established.** The plugin *version string is not a
discriminator* — DR-10's pre-Phase-8 sandbox and this one both log `Loaded custom plugins: lunar
(0.18.0)`, so a version check here would have proved nothing. What was checked instead is the
bytecode actually on the sandbox classpath: `javap -p` on
`net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.class` extracted from
`build/idea-sandbox/GO-2026.1.3/plugins/lunar/lib/lunar-0.18.0.jar` lists `preparedUsageRewrites`,
`preparedUsageRewrite` and `preparedDeclarationRewrite`, and `javap -c` shows
`invokevirtual ProgressManager.executeNonCancelableSection` inside `renameElement` — none of which
exist before Phase 8. That class is byte-identical (md5 `68855cd6`) to the one in the zip freshly
built from `578e66a6`, and the sandbox logged its load at 19:49:09, minutes before the first
attempt.

**The button LABEL was gated, again, and the gate was re-derived rather than inherited.** DR-10's
recorded Stop signature is `495263b9`; in this session's window geometry the same 60x24 strip over
the button label reads `7d41ab7b`. Inheriting the old md5 would have produced a gate that never
fires. A no-click pass attributed all three signatures against captured frames:
`efbce2b2` = **Cancel** (Looking for Usages), `7d41ab7b` = **Stop** (Renaming global variable …),
`21dcee76` = the **Save All Documents** dialog that follows a completed rename
(`phase-8-live-evidence/dialog-label-signature-attribution.png`). The coordinate collision DR-10
measured reproduces: Cancel sits at ~(1186,551) and Stop at ~(1190,553).

| # | Rung | Click at | Button label on the dialog when the click landed | Outcome on disk (all 201 / 801 files counted) | Mixture | Frame |
| :-- | :-- | --: | :-- | :-- | :-- | :-- |
| a1 | N = 200 | +2812 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a1-stop-click-2812ms-ignored.png` |
| a2 | N = 200 | +1504 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a2-stop-click-1504ms-ignored.png` |
| a3 | N = 200 | +2373 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a3-stop-click-2373ms-ignored.png` |
| a4 | N = 200 | +1805 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a4-stop-click-1805ms-ignored.png` |
| a5 | N = 200 | +2431 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a5-stop-click-2431ms-ignored.png` |
| a6 | N = 200 | +2602 ms | **Stop** | fully renamed — 2001 / 0 | **No** | `n200-a6-stop-click-2602ms-ignored.png` |
| b1 | N = 800 | +12614 ms | **Stop** | **wholly unchanged — 8001 / 0** | **No** | `n800-b1-stop-click-12614ms-honoured.png` |
| b2 | N = 800 | +12826 ms | **Stop** | fully renamed — 8001 / 0 | **No** | `n800-b2-stop-click-12826ms-ignored.png` |
| b3 | N = 800 | +15006 ms | **Stop** | fully renamed — 8001 / 0 | **No** | `n800-b3-stop-click-15006ms-ignored.png` |

Counts are `grep -o` over every `.lua` file in the fixture, not an eyeballed file: the checker also
reports how many files hold **both** names, and that figure was `0` on all nine attempts. DR-10 on
the pre-Phase-8 binary, same generator, same N = 200 rung, got a mixture on **5 of 5** with 170-186
files holding both names. Same fixture, same gate, opposite result.

**Six distinct pixel states cover the nine frames** (a3/a5, a2/b1 and a4/b2 are byte-identical) —
the dialog is deterministic apart from the progress bar's animation phase, so this is one pixel
state recurring rather than nine independent corroborations, exactly as DR-10 recorded of its own.
It is worth naming which pair: **a2 and b1 are the same bytes and had opposite outcomes** — a2 was
ignored and completed, b1 was honoured and changed nothing. The frame proves the *label*, which is
all it was ever gated on; it does not and cannot show which phase the rename was in.

**A positive control, because "the cancel was ignored" and "the click never landed" look identical
on disk.** Eight of nine attempts came out fully renamed, which is also what a click that missed
would produce. So one attempt deliberately gated on the **Cancel** signature instead: it clicked at
+370 ms, the search dialog closed 189 ms later, no Stop dialog ever appeared and the project was
untouched (`positive-control-cancel-click-370ms.png`). `xdotool` clicks at that coordinate do reach
the modal and are honoured — so the eight ignored Stop clicks are genuinely Gap 2.18's residual,
observed on Phase-8 code rather than argued. This is the same route that produced DR-10's false
negative, run on purpose and in the one direction where it is informative.

**What the run had to discover: at N = 200 a user's Stop can only land in the apply phase.** The
`PotemkinProgress` dialog appears no earlier than 300 ms after construction
(`ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS`), and at this rung the preparation loop finishes
inside that delay — so by the time the button exists there is nothing cancellable left, and all six
N = 200 attempts were ignored. The **preparation-phase Cancel is the branch Phase 8 was written
for**, and it is unreachable at the pinned fixture. Rung N = 800 (8000 usages, 801 files) makes
preparation outlast the paint delay, and attempt b1 is that branch executing live: Stop honoured,
dialog gone in 198 ms, **project byte-identical**. That is TC-43's live analogue, and it is the
outcome the pre-Phase-8 code could not produce — there, a Cancel once the button existed meant a
mixture. The N = 800 rung is supplementary, named as such, and does not replace N = 200.

**Claim 4 — the apply phase keeps repainting. This was READ from source in design §3.3; it is now
RUN.** The dialog region (600x110 px over the title, bar and button) was captured continuously at
~55 Hz. Because attempt b2's click was *ignored*, everything after it is at or past the start of
`executeNonCancelableSection`, which bounds the apply phase from outside without instrumenting the
plugin: in that 5153 ms post-click window the region changed at **86 points across 236 frames, with
a longest still gap of 186 ms** and no frozen block anywhere. The changes cycle through a repeating
sequence of md5s — the indeterminate progress bar's animation — which only advances if AWT events
are being pumped and `paintImmediately` is being called, i.e. exactly the
`ProgressManagerImpl.java:84-91` + `CoreProgressManager.java:184-196` path design §3.3 argued from.
The section removes the throw, not the paint. **The user-visible-freeze risk this check was written
to catch did not materialise.**

**No internal error.** No balloon and no red status-bar indicator in any post-attempt capture
(`no-error-balloon-status-bar.png`), and `idea.log` grew 179 lines across the whole verification
window with **0** new `ERROR`/`SEVERE` lines and 0 `net.internetisalie` mentions.

**Undo is one command.** After a completed N = 200 rename, a single Ctrl+Z raised the platform's
multi-file confirmation reading **"Undo Renaming global variable config to settings?"** — one
command, named for the whole refactoring (`undo-single-command-confirmation.png`) — and confirming
it returned all **2001** occurrences across all **201** files to the old name with 0 files holding
both. Stated precisely so it is not read as more than it is: this is one undo *step* plus the
platform's standard multi-file prompt, not a bare keystroke.

**Not run, and labelled so.** The exact instant preparation hands over to application was never
observed directly; it is bounded only by whether a click was honoured, which is why the two rungs
are reported separately rather than merged into one window figure. Timings carry the same
screenshot-loop load bias DR-10 named, and in the same direction — extra load lengthens the phases
and makes the button *more* likely to paint.


### Gap 2.18 — a Cancel that arrives during the apply phase is IGNORED (RESIDUAL OF PHASE 8, ACCEPTED)

Design §3.3 step 4 runs every rewrite inside `ProgressManager.getInstance().executeNonCancelableSection`,
so a Cancel pressed after the preparation loop has finished does not stop the rename: the file is
fully renamed and the user's Cancel had no effect. **This is the residual the chosen option leaves,
and it is deliberate**, on three grounds:

**It is now observed rather than only predicted.** The Phase 8 live verification recorded under Gap
2.17 clicked a dialog labelled **Stop** nine times on `578e66a6`; **eight** of those clicks were
ignored and the rename completed whole, which is this residual happening in front of a user. The
ninth (N = 800, attempt b1) arrived while the preparation loop was still running and was honoured,
leaving the project byte-identical — so both sides of the boundary are executed, not just argued.
A caveat that section carries and this one must not lose: at the N = 200 rung preparation finishes
before the 300 ms progress-dialog paint delay expires, so **every** Stop a user can physically press
there falls in this residual's window. At that size the residual is not an edge case; it is the
only outcome.

1. An ignored Cancel with a correct file is strictly better than an honoured Cancel with a broken
   one, which is what ships today.
2. The window is bounded by a run of `ASTNode.replaceChild` calls plus at most one
   `LeafElement.replaceWithText`, with no parse and no index read — every expensive step has been
   moved into the cancellable preparation loop: the
   `SmartPsiElementPointer` deref and the per-usage `LuaElementFactory.createIdentifier` parse
   (design §3.3 step 3) and the `---@param` tag lookup (step 3a).

   **The window is not VFS-free, and saying so absolutely would repeat the overreach this ground was
   already falsified for.** Every in-section `replaceChild` reaches
   `ChangeUtil.prepareAndRunChangeAction` → `PomModelImpl.startTransaction`, whose
   `FileDocumentManager.getInstance().getDocument(vFile)` (`PomModelImpl.java:310-311`) loads and
   decodes the file when no document is cached. It is a cache hit here rather than a read, because
   the preparation phase dereferences `usage.element` and `host.node` for every usage and so forces
   each file's document first — the exposure is in the source and absent from the execution. That is
   the claim this ground supports; "no VFS on any reachable path" is not.

   **This ground was false as first written and the design changed rather than the wording.** The
   Step-9 review found that §3.3 step 4 as specified called `LuaCatsParamRenamer.rename` *inside*
   the section, and that call parses: `LuaPsiImplUtil.getCatsComment` reaches `prev.firstChild`
   (`LuaPsiImplUtil.kt:29`) and `comment.paramTagList` reaches
   `LuaCatsLazyCommentImpl.getParamTagList` → `innerComment()` →
   `PsiTreeUtil.getChildOfType(this, …)` → `LazyParseablePsiElement.getFirstChild()` (`:88-89`) →
   `LazyParseableElement.getFirstChildNode()` (`:233-235`) → `ensureParsed()` (`:156`), which runs
   `parseContents` on a comment that nothing in the rename path has parsed yet. The input to that
   parse is the user's doc-comment block, whose length is unrelated to the size of the rename — so
   the section's duration would have been bounded by user input rather than by usage count, which
   is exactly the property engineering-contract §2 exists to protect. **The repair is to hoist the
   lookup, not to argue the parse is short**: design §3.3 step 3a resolves the `ARG_NAME`
   `LeafElement` during the cancellable preparation phase and the section applies only
   `LeafElement.replaceWithText`, which interns text and calls `replaceChild`
   (`LeafElement.java:137-141`, DR-06) and neither parses nor validates.

   One parse remains **inside** the section and is stated rather than glossed: step 3's delegating
   closure `{ RenameUtil.rename(usage, newName) }` goes through `setName` →
   `LuaElementFactory.createIdentifier`. It is unreachable today — only `LuaNameReference` can be a
   usage of a declaration IDENTIFIER leaf (§3.3 step 3.3) — and it exists because a *silent skip* is
   the half-apply class this phase removes. If it ever becomes reachable, this ground weakens with it.

   **What this ground is NOT.** It is not that the section is more cancellable than the platform's
   own rename. The earlier wording said `RenameUtilBase.doRenameGenericNamedElement` is *less*
   cancellable because it carries no `checkCanceled` (confirmed: zero occurrences in
   `RenameUtilBase.java`) and parses inside its usage loop (`:43-51` → `:90-95`). Both facts hold and
   the conclusion does not: the platform's apply phase stays **interruptible** — at that parse, and
   at `CompositeElement.getPsi()`'s `checkCanceled` (`:719-720`) reached from every `replaceChild` —
   while ours is deliberately **uninterruptible**. That incidental interruptibility is precisely
   [[BUG-468]]. Ours is *safer under cancellation*, and more responsive *before* the first edit
   (one cancellation point per usage against the platform's none); it is not more cancellable, and
   a favourable comparison in the wrong dimension is the retraction class this feature keeps
   producing.

   **The section does not stop the UI from repainting**, which is the concrete form of "does not
   lock the IDE": `PotemkinProgress` **is** the `CheckCanceledHook` for the duration of
   `executeProcessUnderProgress` (`ProgressManagerImpl.java:84-91`), and `doCheckCanceled` runs
   hooks even when `isInNonCancelableSection()` is true (`CoreProgressManager.java:184-196`). The
   `checkCanceled` inside `CompositeElement.getPsi()` therefore still pings `PotemkinProgress.interact()`
   once per edit; what the section removes is the *throw*, not the paint. **Read, not run** — DR-10
   is where it is observed.
3. The outcome is recoverable in one keystroke and that was measured, not assumed: after a rename,
   `UndoManager.isUndoAvailable(fileEditor)` is `true`, the command is named
   `Undo Renaming local variable counter`, and `UndoManager.undo` restores the file byte-for-byte.

**One escape from the section is unpinned by the suite, and that was measured rather than argued.**
Applying the `---@param` tag edit at step 3a — `preparedRename(...)?.invoke()` — instead of inside
step 4 puts one edit outside the section. The full suite is **green** on that mutant (executed
2026-08-25, 2844 tests): TC-20d cannot see it, because step 3a runs after step 2's refusal either
way, and TC-43/TC-44/TC-45 cannot either, because all three rename a `LOCAL_VARIABLE` so
`applyCatsTagRewrite` is null on their fixtures and there is no tag edit to escape. Pinning it needs a
new case — a `---@param` fixture whose indicator is cancelled between step 3a and step 4 — and none
was invented to fill the row. It is recorded here as unpinnable-without-a-new-case, the same
disposition design §3.3 sub-step 4's Elvis carries.

**Not mitigated by a notification**, deliberately: the user sees the file renamed, so a message
saying "your Cancel arrived too late" describes something already visible, and the platform issues no
such message for any refactoring. Design §9 Alternative F records why announcing a partial state was
rejected as a *fix*; this is the same argument applied to announcing a complete one.

**Two things this residual is NOT.** It is not a claim that the apply phase is instantaneous — for a
pathological usage count it is simply uninterruptible, and if that is ever measured to be a freeze,
the fix is to batch the section, not to reintroduce a cancellation point between two edits. And it is
not measured against a large project: every measurement behind Phase 8 used a three-usage
single-file fixture, so the *duration* of the uncancellable window is unmeasured. The
implementation-plan's live-IDE item for Phase 8 is where that gets a first look.

### Gap 2.19 — hoisting the IDENTIFIER lookup makes a DUPLICATE usage fatal (FOUND AND REPAIRED IN PHASE 8)

**A precondition design §3.3 step 3 did not state, found by the full suite rather than by reading.**
The usage array can hold two entries over the *same* host element. Measured on TC-42's fixture —
`function M.r<caret>un() end` in `a.lua`, `M.run = function() end` in `b.lua`, the collision
acknowledged via `withIgnoredConflicts` — the array is:

| # | `UsageInfo` class | host | file | reference |
| :-- | :-- | :-- | :-- | :-- |
| 1 | `MoveRenameUsageInfo` | `LuaNameRefImpl@784996764` | `a.lua` | `LuaNameReference` |
| 2 | `MoveRenameUsageInfo` | `LuaNameRefImpl@2114416945` | `b.lua` | `LuaNameReference` |
| 3 | `LuaRenameCollisionUsageInfo` | `LuaNameRefImpl@2114416945` | `b.lua` | `LuaNameReference` |

Entries 2 and 3 are the **same object**. The shipped `RenameUtil.rename` path absorbed that silently:
it re-read the host's current IDENTIFIER child inside `LuaNameRef.setName` on each call, so the second
rewrite of one occurrence swapped a fresh node for another fresh node with the same text — a no-op in
effect. **Hoisting that lookup out of the apply path is the whole point of step 3**, and it converts
the tolerated duplicate into a hard failure: the first closure detaches `identifierNode`, the second
calls `hostNode.replaceChild` on it, and `CompositeElement.replaceChild`'s
`LOG.assertTrue(((TreeElement)oldChild).getTreeParent() == this)` (`:648`) fires — a
`TestLoggerAssertionError` under `BasePlatformTestCase`, an internal-error notification in production.

**The repair is a claimed-node set threaded through step 3's loop**, so at most one rewrite is
prepared per IDENTIFIER node. That reproduces the pre-Phase-8 net effect exactly rather than changing
behaviour, and it sits in the preparation phase rather than in the closure because re-reading the
child at apply time would put a lookup back inside step 4's non-cancelable section — undoing the
property the phase exists to establish.

**It is gated by the EXISTING TC-42, not by a new case**, and the runnable mutant is deleting the
claim (executed: RED, on the `replaceChild` assertion itself rather than on a text comparison). What
this cost is worth recording: the defect was invisible to `test --tests *LuaRenameTest*
--tests *LuaCatsParamRenameTest*`, which was green on the broken code, and surfaced only on the full
suite. A targeted green is not a gate.

### Gap 2.20 — in-place rename is blocked by `PsiNameIdentifierOwner` on BOTH routes; the use scope was a symptom, and removing it makes the failure WORSE (BLOCKER, PHASE 7)

**Design §2.6 specifies exactly one edit for REFACT-01-12 — `isInplaceRenameAvailable` — and that
edit is necessary but not sufficient.** The predicate ships and is correct; the inline template does
not start. Every claim below was executed on the builder, not read.

**The use-scope gate is removable.** Overriding `getUseScope()` on `LuaNameRefElementImpl`, gated on
`LuaDeclarationSite.kindOf(identifier)?.isFileLocal == true` and returning
`LocalSearchScope(containingFile)`, does clear `InplaceRefactoring.checkLocalScope()`
(`InplaceRefactoring.java:283-290`). It also removes the Scratches union, because
`ScratchFileServiceImpl.UseScopeExtension` contributes nothing once the base scope is already local
(`ScratchFileServiceImpl.java:455-462`). Measured on the `LuaNameRef` of `local counter = 0`, at
`9c6d3b3d` and at `9c6d3b3d` plus that override:

| Probe | Without the override | With the override |
| :--- | :--- | :--- |
| `nameRef.useScope` | `ModuleWithDependentsScope` | `LocalSearchScope` |
| `PsiSearchHelper.getUseScope(nameRef)` | `UnionScope` (module ∪ Scratches) | `LocalSearchScope` |
| `… is LocalSearchScope` | `false` | **`true`** |
| same, for the `LuaNameRef` of a global `config = {}` | `UnionScope` | `UnionScope` (unchanged) |
| `PsiSearchHelper.getUseScope(declaration IDENTIFIER leaf)` | `ModuleWithDependentsScope` | `ModuleWithDependentsScope` (unchanged) |
| `VariableInplaceRenamer(nameRef, editor).performInplaceRename()` | `false` | **`true`** |

**`true` is the wrong answer.** With the scope gate cleared, `performInplaceRefactoring` reaches
`buildTemplateAndStart`, which needs `selectedElement` — the occurrence under the caret — and gets
`null`, from `getSelectedInEditorElement`'s `LOG.error` fallthrough
(`InplaceRefactoring.java:841-861`, error at `:859`). Both of that method's sources are empty for a
Lua declaration, and neither has anything to do with the search scope:

- `getNameIdentifier()` is `myElementToRename instanceof PsiNameIdentifierOwner ? … : null`
  (`InplaceRefactoring.java:596-598`). `LuaNameRef` is only a `PsiNamedElement`
  (`LuaNameRefElement : PsiNamedElement`, `LuaBaseElements.kt:79`), so this is `null` — measured.
- the reference at the caret is added only when `reference.isReferenceTo(myElementToRename)`
  (`addReferenceAtCaret`, `:678-696`). `LuaNameReference.isReferenceTo` requires an IDENTIFIER leaf
  and explicitly refuses a declaration's own name — *"a declaration's own name is not a usage of
  itself"* (`LuaNameReference.kt:260-272`), the rule Find Usages and Safe Delete depend on.
  Measured `false` against the `LuaNameRef` composite.
- `collectRefs` (`:319-332`) therefore holds only the three *usages*, at ranges `(24,31)`,
  `(33,40)`, `(43,50)`, none of which contains the caret at offset `10`.

**`LOG.error` is not a stop, so this fails OPEN.** Under `BasePlatformTestCase` it becomes a
`TestLoggerAssertionError`; in production `Logger.error` raises the internal-error notification and
*returns*, and `buildTemplateAndStart` carries on with `selectedElement == null`. With the logger
silenced (`LoggedErrorProcessor`), for the fixture
`local coun<caret>ter = 0` / `print(counter)` / `counter = counter + 1`:

- `performInplaceRename()` returns `true`, and the document is left as
  `local counter = 0` / `print()` / ` =  + 1` — `startTemplate` empties the three usage segments
  inside its own `WriteCommandAction`.
- driving the same fixture through `CodeInsightTestUtil.tryInlineRename(…, "total", …)` throws
  `AssertionError` at `CodeInsightTestUtil.java:257`, because `TemplateState.getCurrentVariableRange()`
  is `null` — **the template starts with no variable for the user to type into** — leaving
  `local counter = 0` / `print(total)` / ` =  + 1`. The declaration keeps its old name (it was never
  added to the builder: `:362` is guarded by `nameIdentifier != null`) and two usages are gone.

That last text is the **mid-template** state, because the harness threw before `gotoEnd`; it is what
the editor shows, not proof of what a committed template would write. It is enough: today's
`false` → `performDialogRename` (`VariableInplaceRenameHandler.java:118-124`) is a *graceful*
fallback, and this replaces it with an internal-error balloon over a template that has emptied three
occurrences and cannot be typed into. **Shipping the `getUseScope` override alone is a
document-corrupting regression on the primary Shift+F6 path for every Lua local**, and it is
reachable: `isInplaceRenameAvailable` already returns `true` for file-local declarations, so
`RenameHandlerRegistry` already selects `VariableInplaceRenameHandler` (`LuaInplaceRenameTest`'s
positive case asserts exactly that). The override is **not committed**.

**Both routes have the same root cause.** Route B (`MemberInplaceRenameHandler`, how REFACT-04's
labels work) is blocked by `PsiNameIdentifierOwner` in `isAvailable` (`:46`) and `doRename` (`:56`).
Route A is blocked by `PsiNameIdentifierOwner` too — at `InplaceRefactoring.java:596`, three call
frames later, and failing open instead of closed. The use scope was never the difference between
them. **`LuaNameRef` not being a `PsiNameIdentifierOwner` is the whole of Gap 2.20**, and design
§2.2's premise — *"Lunar models no declaration PSI: apart from `::labels::` every declared name is a
`LuaNameRef`"* — is what has to move for REFACT-01-12 to be deliverable.

**The narrowing is inert for every other consumer, including the one Risk 1.1 guards.** The full
suite is green with the override (`test --rerun --no-build-cache`: `BUILD SUCCESSFUL`, 2851 tests,
0 failures, 0 errors, 1 skipped — identical to the `9c6d3b3d` baseline), and *stays* green under the
mutation that drops the `isFileLocal` condition so every `LuaNameRef` narrows (same counts). That is
not missing coverage in `LuaRenameCrossFileTest`; it is the override sitting on an element no
non-in-place path ever asks:

- `RenameUtil.processUsages` reads `getUseScope(element)` (`RenameUtil.java:128`) for the
  **substituted** element, which `LuaRenameProcessor.substituteElementToRename` has normalised to the
  declaration IDENTIFIER **leaf**.
- `LuaSafeDeleteProcessor.findUsages` reads `searchTarget.useScope` (`:89`) after the same
  normalisation, and `LuaNameReferenceSearcher` normalises before searching (`:57`).
- a leaf is a platform `LeafPsiElement`; its `getUseScope` is untouched by an override on the
  composite, and stayed `ModuleWithDependentsScope` in every probe above.

Moving the identical mutation to where the rename path *does* read it — dropping `kind.isFileLocal`
in `LuaRenameProcessor.findReferences` (design §3.2) — turns **all four** `LuaRenameCrossFileTest`
cases red. REFACT-01-07 is genuinely guarded; it is guarded at the leaf, not at the composite.

**Containing file, not enclosing block, if a use scope is ever narrowed.** A Lua `local` is visible
to the rest of its enclosing block *including nested closures that capture it as an upvalue*, so a
block-scoped `LocalSearchScope` would have to model upvalue capture to be correct, and a miss there
loses a real reference silently. The file is the smallest scope that is right without that analysis,
and the platform collapses a whole-file scope to the containing file anyway
(`InplaceRefactoring.getElements`, `:292-300`). Nothing measured here argues for block scope.

**What ships instead.** The gate, with its three discriminating guards mutation-proved
(`LuaInplaceRenameTest`): reverting the predicate to `false` reddens the positive case; widening it
to accept the IDENTIFIER leaf — Risk 1.5's named "simplification" — reddens the leaf case; dropping
`isFileLocal` reddens the global case. It is a precondition for either route and is replaced by an
`isMemberInplaceRenameAvailable` predicate of the same shape under Route B, so it is not wasted work.
What the user sees is unchanged from before Phase 7: the ordinary rename dialog, fully delivered.
The cost is that REFACT-01-12 is `Partial`, not `Full`.

**What a replan has to decide, and what it may not re-price.** The remaining question is whether
Lunar introduces a declaration-owning PSI element (a `PsiNameIdentifierOwner` for the declaring
`LuaNameRef`, the change `.agents/AGENTS.md` calls the "correct but heavy" fix in the LuaCATS-tag
context) or leaves REFACT-01-12 at `Partial` permanently. Two things are settled and should not be
re-measured:

- **A `getUseScope()` override does not deliver REFACT-01-12 and must not be shipped on its own.**
  It converts a graceful dialog fallback into a corrupting template.
- **No existing test can see that regression.** The suite is green with the corrupting override in
  place, because nothing drives `VariableInplaceRenameHandler.doRename` or
  `InplaceRefactoring.performInplaceRefactoring` end to end. Any future attempt at this must land an
  end-to-end case (`CodeInsightTestUtil.tryInlineRename` plus a `checkResult`) *first*; the
  `isAvailableOnDataContext` gate cannot substitute for one, and neither can the full suite.

**No test asserts that the template fails.** A passing "in-place did not start" case would pin the
defect and go red on the fix, which is the shape this feature's bar rejects; the evidence lives here
and in `LuaInplaceRenameTest`'s KDoc instead.


## Test Case Gaps

- **Undo.** No test asserts that a rename is undoable as a single command. The platform provides it
  (`RenameUtil.registerUndoableRename`), but the design's `renameElement` performs raw AST surgery
  (`node.replaceChild`) outside any `WriteCommandAction` of its own — correct, because
  `BaseRefactoringProcessor` already supplies one, and *therefore* undo-safe. Worth one live check
  (Ctrl+Z after a multi-file rename) rather than a unit test. **The single-file half is no longer
  inferred**: Phase-8 planning measured `isUndoAvailable = true`, the command name
  `Undo Renaming local variable counter`, and a byte-for-byte restore, for a three-usage rename in
  one file — used as the evidence for design §9 Alternative G and Gap 2.18, not as coverage. The
  **multi-file** case is still unmeasured (`BaseRefactoringProcessor` only calls
  `markCurrentCommandAsGlobal` when `isGlobalUndoAction()`, `:456`), and the live check is still the
  right place for it.
- **The production logger is not the test logger.** TC-13d asserts the absence of a
  `TestLoggerAssertionError`, which is what a `LOG.error` becomes under `BasePlatformTestCase`. A
  production `Logger` does not throw — it raises the IDE's internal-error notification and lets the
  null replacement through to `document.replaceString`. The two oracles differ in what they *do*, not
  in whether the branch was taken, so the unit gate is sound for "the branch is not taken"; the live
  checklist item (tick the checkbox, look for the internal-error balloon) is what covers the rest.
- **Cancellation.** ~~No test asserts `ProgressManager.checkCanceled()` is actually reached in
  `findCollisions`/`findReferences`. Unit-testable only with a mock indicator; recorded rather than
  built.~~ **Withdrawn twice over.** Phase 4 built the `findCollisions` half
  (`testCancellationIsCheckedPerIndexHitNotPerCall` and its two siblings) after measuring that a
  mock *indicator* is inert here and that a `CheckCanceledHook` is the observation point. Phase 8
  builds the `renameElement` half (TC-43/44/45) on the same harness. `findReferences` remains
  uncovered, and stays that way on purpose: its loop is `ReferencesSearch.search(...).findAll()`,
  whose cancellation belongs to the platform's searcher, not to this processor.
- **Concurrent editing.** Renaming a global while another file is open and dirty is not covered.
  The platform handles document commit; recorded for completeness.
- **`global` declarations (Lua 5.5) are NOT covered by the `LuaAttName` and `LuaFuncName` rows.**
  They need rows of their own, and the two halves fail differently — one is not reached at all, the
  other is reached and misclassified:
  - `globalFuncDecl ::= <<globalKeyword>> FUNCTION nameRef funcBody` (`lua.bnf:229`) has **no
    `funcName` node** — the `nameRef` is a direct child, so the identifier's grandparent is
    `LuaGlobalFuncDecl` and the `LuaFuncName` row never matches. `kindOf` was `null`: not covered
    at all, and `global function f() end` was neither renameable nor findable.
  - `globalVarDecl ::= <<globalKeyword>> attNameList …` (`lua.bnf:217`) with `attNameList` a
    `private` rule (`:242`) **does** hit the `LuaAttName` row — and that was the defect, not the
    coverage: it yielded `LOCAL_VARIABLE`, `isFileLocal = true`, and design §3.2 step 2 narrowing the
    search to `LocalSearchScope(containingFile)`. `global x = 1` renamed in one file would have left
    every other file bound to the old name — Risk 1.1 exactly, produced by the very row that was
    supposed to cover it.

  Closed by design §3.5 rows 5 and 7 plus §2.10, and no longer left to a TC-21 fixture: TC-28 and
  TC-29 are cross-file rename cases, and the plan's mutation-proof list requires deleting each row
  and confirming the matching TC goes red. `kindOf` in isolation cannot see a narrowed search scope,
  which is why a TC-21 fixture alone would not have been enough.
- **Non-ASCII identifiers.** `LuaNamesValidator.IDENTIFIER_PATTERN` is `^[A-Za-z_][A-Za-z0-9_]*$`,
  which is correct for the reference Lua implementation but rejects names some Lua builds accept.
  Out of REFACT-01's scope (REFACT-05 owns the validator); recorded so it is not rediscovered here.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Interim refusal being replaced: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaUnsupportedRenameProcessor.kt`
