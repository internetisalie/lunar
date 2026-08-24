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
- **Likelihood**: medium. Five shapes can produce it, two of which the Step 9 review found in the
  first draft of this plan and which are now closed:
  1. a declaration kind whose usages are not resolvable (the colon-method form, `t.field`);
  2. a target the processor claims but whose usage search key is wrong;
  3. an in-place rename path that highlights fewer occurrences than the dialog path;
  4. **a non-file-local kind misclassified as file-local**, which makes §3.2 step 2 narrow the search
     to one file. The first draft did this to every Lua 5.5 `global x = 1`, by classifying it through
     the `LuaAttName` row (`lua.bnf:217` puts an `attName` directly under `LuaGlobalVarDecl` exactly
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
     the old name. **Found by the third Step 9 review, in material the second round added.** Closed
     by design §3.1 step 4a (a round trip against `functionNameLeafOf`, which also covers the
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
  §3.1 step 4. The first draft of this plan asserted the exclusion in three places and tested it with
  TC-24 but never wrote it into the predicate. Design **§3.0** now specifies `canProcessElement`
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
- **Likelihood**: certain if not designed for. No gate in the previous draft could have caught it:
  `LuaSafeDeleteTest` has no global or dotted fixture, and TC-30 asserts `declarationNodeOf` in
  isolation.
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
- **Why it matters to the requirement and not only to the design**: the first draft of `design.md`
  delivered only the first form and still marked `-07` delivered in its §8. The second form — a bare
  `x = 1` — is the *canonical* Lua global and had no classification row at all, so
  `substituteElementToRename` would have refused it outright. Design §3.5 rows 5, 7 and 14 and §2.10
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
- **Why it matters**: the first draft of `design.md` read the row as licence for a
  `element.text == "self"` refusal, which would have broken the dot form. The guard is now removed
  entirely (design §3.1, the note after step 5) and `self` in the colon form is refused by the
  general `METHOD_FUNCTION` rule instead.
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
- **Why the obvious guard does not work, and was removed rather than kept.** The first draft opened
  §3.1 with "if the element is `self`, refuse". It could never fire: `TargetElementUtilBase` tries
  `REFERENCED_ELEMENT_ACCEPTED` first, so the processor receives the *resolved* `m` leaf, whose text
  is `"m"`. Worse, on the paths where it *could* fire it was wrong — `function T.m(self, x)` is legal
  Lua whose `self` is an ordinary parameter that must rename normally (TC-19c). Dead in one direction
  and harmful in the other, so it is gone; §3.1 records the full derivation.
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
  nothing now resolve), and gated by Phase 1's corpus sweep plus three named resolution tests. This
  is the premise the first draft did not examine: the index's coverage looked like an input, and it
  was a choice.

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

### Gap 2.7: the searcher's label guard is NOT order-dependent — an earlier draft said it was, and was wrong

- **Question (as originally posed, and now retracted)**: design §3.8 replaces
  `LuaNameReferenceSearcher.isNameDeclarationLeaf` with `identifierLeafOf`-based normalisation.
  `identifierLeafOf` row 1 maps a `LuaLabelName` to its IDENTIFIER child — a **non-null** result —
  so normalising before excluding labels was said to make the guard dead code and to put REFACT-04's
  label rename at risk. **That framing is false**: it stops at `identifierLeafOf` and never asks
  what the *next* guard does with the leaf.
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
- **Resolved by**: design §3.8 ①; nothing deferred. Kept in this document rather than deleted
  because the false premise is an easy one to re-derive from `identifierLeafOf` row 1 alone.

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

### Gap 2.9: the numeric-`for` declaration has no caret-reachable rename target (OPEN)

`numericForStatement ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) hangs the control variable's leaf
directly off the statement — the one declaration kind with no `LuaNameRef`. The leaf therefore
carries no `PsiReference`, and `LuaNumericForStatement` is a plain `ASTWrapperPsiElement`, so
`TargetElementUtilBase.getNamedElement` finds no `PsiNamedElement` ancestor and
`TargetElementUtil.findTargetElement` returns **null**. Measured: Shift+F6 with the caret on
`for <caret>i` reports "cannot rename", where every other declaration kind renames.

**Impact is bounded and the feature still works**: `canProcessElement` claims the leaf, and rename
from a *usage* (`print(<caret>i)`) redirects to it and rewrites the `for` header correctly — TC-05 is
written that way and passes. Only the declaration-caret entry point is unavailable.

**Closing it needs a `TargetElementEvaluatorEx2` for Lua** (a new `targetElementEvaluator` extension
point), which is outside REFACT-01's design — §7 states that exactly one `plugin.xml` line changes.
`LuaRenameTest.testNumericForDeclarationCaretHasNoRenameTargetAtAll` pins the current behaviour so
that closing the gap goes red here and forces this section to be updated rather than silently
diverging. REFACT-01-05 is recorded **Partial** for this reason.

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
Gap 2.9 needs for the numeric-`for` declaration, and out of scope here for the same reason.

**User-visible consequence, stated plainly:** renaming a dotted function works from its declaration
(TC-09) and is refused from its call sites. That is a second reason `REFACT-01-08` is `Partial`
beyond the colon form, and it is recorded so the row's scope is not read as narrower than it is.

**Filed as [[BUG-465]] (2026-08-23), on the Phase-4 reviewer's judgement, and the reason is the
`TargetElementEvaluatorEx2`.** A gap recorded only inside this feature's own document closes when
the feature does; this one does not close with it. It is the **second** gap needing that one absent
extension — Gap 2.9's numeric-`for` declaration caret is the first — and rename-from-usage
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

**Why the fix first stopped short of it, and why that reason was wrong.** The recorded objection was
that adding member-field hits to C4's candidate set would anchor the collision on the field's
`LuaNameRef` — which the table above proves is in the renamed symbol's **usage set** — and that the
platform deletes collision anchors from that set, so Continue would skip rewriting it: a second
silent partial rename. **That is false, and it was the sole stated reason a measured data-loss path
was shipping.**

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
- **TBD-2: `REFACT-01-16` — `@class` / `@alias` name propagation.** Deferred, priority `S`. The
  `@param` half ships (design §3.6). The type-name half has no `PsiReference` anywhere under
  `src/main/kotlin/net/internetisalie/lunar/luacats/`; type names reach navigation only through the
  file-based `LuaCatsTypeNameIndex` + `LuaCatsTypeNavigation`, because LuaCATS tags are not stubbed
  and a bare `--- @class Name` has no host declaration. Renaming them needs either a reference
  implementation on the tag PSI or an index-driven rewrite — a feature-sized job. **DR-04** sizes it.
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
| `REFACT-01-00-DR-04` | Size `@class`/`@alias` rename: prototype rewriting a type name through `LuaCatsTypeNameIndex` and count the surfaces that would need it (docs, completion, inspections). | TBD-2 | todo |
| `REFACT-01-00-DR-05` | Enumerate what `t.field` resolves to for the four receiver shapes in Gap 2.3 and confirm each refuses or lands on a genuine `DOTTED_FUNCTION`. | Gap 2.3 | **done 2026-08-23 — all four shapes are safe; folded into design §6.1, see below** |
| `REFACT-01-00-DR-06` | Confirm `LeafElement.replaceWithText` is a legal edit on a `LuaCatsArgName` child inside a `LuaCatsLazyCommentImpl` (a lazy-parseable node) — the one AST operation in this design with no existing precedent in the repo. If it is not, fall back to rebuilding the comment text. | Design §3.6 | **done 2026-08-23** — executed in Phase 6 and re-verified by review: `replaceWithText` reaches `ASTFactory.leaf` via `ChangeUtil`, neither parsing nor validating, and `LuaCatsElementType` is a plain `IElementType` so the interning branch is taken. |
| `REFACT-01-00-DR-07` | Before writing §2.10's collectors, run one `BasePlatformTestCase` that puts `global count = 0` in `a.lua` and `print(count)` in `b.lua` and asserts what `LuaNameReference.resolve()` returns for `b.lua`'s `count` **on the current tree**. The design asserts it is null (nothing indexes `LuaGlobalVarDecl`) from reading `LuaGlobalAssignmentIndex.kt:95-107`, not from running it. If it already resolves, §2.10 is unnecessary and rows 5/7 alone finish the job. Paste the output into design §1's evidence table either way. | Design §2.10, Gap 2.5 | **done 2026-08-22 — it did NOT already resolve; §2.10 is load-bearing, measured** |
| `REFACT-01-00-DR-08` | Confirm the file-scope predicate of §3.5 row 14 against real PSI: for `cfg = {}` at file scope, assert `(target.parent as? LuaVarList)?.parent is LuaAssignmentStatement` and that the statement is in `containingFile.blockList.flatMap { it.statementList }`; for `function g() cfg = 1 end`, assert it is **not**. **Also assert the O(1) restatement agrees on both fixtures** — `stmt.parent is LuaBlock && stmt.parent.parent is LuaFile` — because §3.5 clause 3 ships in that form to keep `isBareAssignmentTarget` cheap enough for the indexer to call per target. The equivalence is derived from `LuaPsiImplUtil.kt:67-68` (`getChildrenOfType`, direct children) and `LuaBlockImpl.java:34-36` (`getChildrenOfTypeAsList`, direct children); this task is what makes it measured rather than derived. `LuaFile.getBlockList()` is `LuaPsiImplUtil.getBlockList` (`LuaFile.kt:31`) and the number of blocks a file exposes is read, not measured. | Design §3.5 row 14, §2.10 change 0 | **done 2026-08-22 — subsumed by DR-09's three-pass run, see below** |
| `REFACT-01-00-DR-09` | Before landing §2.10 change 0, run `LuaCrossFileGlobalResolutionTest` against a build in which `LuaGlobalAssignmentIndex.Indexer.map`'s assignment collector has been swapped for the `LuaDeclarationSite.isBareAssignmentTarget` form, and confirm its `local shadowed\nshadowed = 2` and `function f() nested = 1 end` fixtures still behave identically. The claim that the delegation is behaviour-preserving (clauses 2 and 3 are unconditionally true for a target reached by that enumeration) is currently **derived from reading**, and the index is the one component here whose defects are invisible until a user's persisted index is wrong. | Design §2.10 change 0, §3.5 row 14 | **done 2026-08-22 — delegation is behaviour-preserving, measured** |
| `REFACT-01-00-DR-10` | **Before writing any Phase-8 code**, establish whether a user can reach [[BUG-468]] at all. Drive a live IDE (`verify-in-ide`): rename a global used across enough files that `PotemkinProgress` paints its dialog, press **Stop** part-way, and record (a) whether the Stop button ever appears, (b) at what project size it appears — the dialog is gated on a 300 ms delay (`PotemkinProgress.updateUI:117-119`, `ProgressWindow.java:77`, `ProgressUIUtil.kt:8` = 300 ms) and steals input only once showing (`interact:84-85`) — and (c) whether the resulting file is split. **Outcome decides an ordering, not the fix**: if the Stop button is unreachable for realistic renames, Phase 8 is not the last open half of a live `Must` and Phase 7 (a `Could` that ships user-visible behaviour) should precede it; if it is reachable, the current Phase 8 → Phase 7 order stands and the recorded project size becomes the fixture for the post-implementation live check. Paste the observation into Gap 2.17 either way. | Gap 2.17, Gap 2.18, implementation-plan phase order | todo |

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

`LuaRenameConflictDetector`'s KDoc asserted a **count** of cancellation-checked iteration blocks, and
every attempt at that count was wrong: Phase 3 said six, Phase 4 corrected it to seven, the Phase-4
review chain-counted nine, and a strict count of every lambda-bodied operator at `4458a8b0` finds
**eleven** — four guarded, seven not. The revisions never disagreed about *compliance*; every block
omitted from every count was one of the unguarded pure ones. They disagreed only about a number that
has to be re-derived by hand after each edit.

The KDoc now states the **invariant** instead — every block that can load PSI, read VFS or query an
index is guarded; the rest are bounded work over already-materialised lists — plus the recipe for
checking it and the two counter-intuitive cases (`UsageInfo.getElement()` is a parse;
`identifierLeafOf` over a stub hit is a parse per hit). A reader can verify that without recounting,
and it does not go stale when a block is added.

The eleven figure above is a **dated audit at one commit**, not a maintained claim; it is here as the
evidence for dropping the count, and nothing depends on it staying accurate. It was derived
mechanically rather than by eye, so the next reader can reproduce it:

```bash
grep -n '\.\(map\|mapNotNull\|filter\|forEach\|flatMap\)\s*{' \
  src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt
```

That prints **ten lines** for eleven blocks — `ambiguousGlobal`'s `.filter { … }.map { … }` is a
single line carrying two lambda bodies. A hand-count reading the same output "correctly" still
lands on ten, which is the fifth wrong number and the last argument needed for stating the property
instead.

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
defect, not a Phase 2 risk** — see the correction below.

### Correction: row 9 was the same defect, on a second route (2026-08-22)

The paragraph above deferred `function repeat() end` to Phase 2 as a *possible* hazard reached
"through `funcName.nameRef`". Both halves were wrong, and the review that failed this phase measured
it rather than reading it:

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


### Gap 2.17 — cancelling a rename leaves the file inconsistent (OWNED BY PHASE 8)

**Filed as [[BUG-468]]. Owned by implementation-plan Phase 8, which realizes design §3.3 steps 3, 3a and 4.**
`renameElement` rewrites every usage before the declaration, so a `ProcessCanceledException` at usage
*k* leaves *k*-1 usages on the new name and the declaration on the old one — [[BUG-457]]'s shape,
reached by pressing Cancel.

**Nothing rolls it back, and the reason is not the one this paragraph used to give.** It read
*"Measured: the enclosing `WriteCommandAction` does **not** roll back"*, which [[BUG-468]] itself
retracted in the same commit that wrote it here — the retraction landed in the bug report and not in
this line. There is no `WriteCommandAction` on the path at all: the write action is
`ApplicationImpl.runEdtProgressWriteAction`'s `lock.runWriteActionBlocking` (`:1135-1154`) and the
command is opened by `CommandProcessor.executeCommand` at `BaseRefactoringProcessor.java:453-458`.
And **no IntelliJ write action rolls back**, so naming one as the thing that failed to implies a
mechanism that exists nowhere in the platform. What actually happens: the `ProcessCanceledException`
dies in `PotemkinProgress.runInSwingThread`'s bare `catch (ProcessCanceledException ignore) {}`
(`PotemkinProgress.java:151-162`), `doRefactoring` `return`s on `!indicator.isCanceled()`
(`BaseRefactoringProcessor.java:659-662`), and undo is **recorded** rather than deferred
(`DocumentUndoProvider.java:74-92, 126-129`) so nothing reverses the earlier edits. The exception
does not surface; that half was always right.

Pre-existing Phase 2/3 code, untouched by Phases 4-6. Phase 8 is now the phase that touches it, and
it precedes Phase 7 because this is the last open half of a `Must` while Phase 7 is a `Could`.

**The first version of this paragraph called the damage "bounded at one usage", called it "smaller
than Gap 2.13", and opened "Measured, not inferred".** All three were wrong: the bound is on latency
rather than damage, Gap 2.13's residue is a stale comment beside correct code while this is broken
code, and only the no-rollback half was measured. It is recorded here because a reassurance stated
with more confidence than its evidence is the defect class this feature spent seven review rounds
finding in its own artefacts — including, that time, in a supervisor's own writing.

**What Phase-8 planning added by measurement**, all executed on `5b7c6ca4` and recorded in design §1:

- **`checkCanceled()` there is reachable** — the Phase-6 review flagged this as unestablished, and it
  is now established the other way: three checks per three-usage rename, each under a live
  `PotemkinProgress` with `isInNonCancelableSection = false`. So option 2 had something real to fix
  and option 1 had a real contract cost to justify. (One earlier probe read `NonCancelableIndicator`
  and was sampling from a `DocumentListener`, which fires inside `PomModelImpl.runTransaction`'s own
  non-cancelable section — the wrong instant, not a different answer.)
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

- **That a user can reach this in a live IDE.** Every measurement drives cancellation
  programmatically. A real user cancels through `PotemkinProgress`'s **Stop** button, and the read
  is specific: the dialog is shown only once `now - myLastUiUpdate > delayInMillis`
  (`PotemkinProgress.updateUI:117-119`) where `delayInMillis` defaults to
  `ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS` = **300 ms** (`ProgressWindow.java:77`,
  `ProgressUIUtil.kt:8`), and input events are stolen only while
  `getDialog().getPanel().isShowing()` (`interact:84-85`), with the click turned into a cancel by
  `dispatchInputEvent` → `isCancellationEvent(e)` → `cancel()` (`:90-94`). For a rename that
  finishes inside 300 ms the dialog never paints and the user has no Stop button to press — which
  would make [[BUG-468]] unreachable for small renames while leaving it exactly as real for large
  ones. **Read, not run.**

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

### Gap 2.18 — a Cancel that arrives during the apply phase is IGNORED (RESIDUAL OF PHASE 8, ACCEPTED)

Design §3.3 step 4 runs every rewrite inside `ProgressManager.getInstance().executeNonCancelableSection`,
so a Cancel pressed after the preparation loop has finished does not stop the rename: the file is
fully renamed and the user's Cancel had no effect. **This is the residual the chosen option leaves,
and it is deliberate**, on three grounds:

1. An ignored Cancel with a correct file is strictly better than an honoured Cancel with a broken
   one, which is what ships today.
2. The window is bounded by a run of `ASTNode.replaceChild` calls plus at most one
   `LeafElement.replaceWithText`, with no parse, no index read and no VFS access on any reachable
   path — every expensive step has been moved into the cancellable preparation loop: the
   `SmartPsiElementPointer` deref and the per-usage `LuaElementFactory.createIdentifier` parse
   (design §3.3 step 3) and the `---@param` tag lookup (step 3a).

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
- **`global` declarations (Lua 5.5) — the "covered by construction" claim was false in both
  directions and is retracted.** An earlier version of this section said `LuaGlobalVarDecl` and
  `LuaGlobalFuncDecl` "reach `LuaDeclarationSite.kindOf` through the `LuaAttName` and `LuaFuncName`
  rows, so they are covered by construction". Neither half held:
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
