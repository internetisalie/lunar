---
id: REFACT-04
title: "04: Label Refactoring"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: REFACT/INTENT
folders: ["[[features/refactoring/requirements|requirements]]"]
---

# 04: Label Refactoring

Rename a Lua `::label::` and rewrite exactly the `goto` statements that Lua binds to it — no more,
no fewer — without silently changing which label a `goto` jumps to, and without producing a file the
configured Lua version refuses to compile.

## Is this feature distinct from REFACT-01?

**Yes, and it is currently the only rename in the plugin that works.** The question is worth
settling because two things in the repo suggest otherwise, and both are stale:

- `LuaRefactoringSupportProvider`'s KDoc says it *"keeps in-place rename for labels (REFACT-01)"*.
  That attribution is wrong; [[REFACT-01]] disclaims labels in its own opening paragraph and carries
  a single delegation row (`REFACT-01-17`) pointing here.
- The epic's Detailed Implementation Status says REFACT-04 is *"Implemented
  (`LuaLabelFindUsagesProvider`, `LuaLabelRefactoringSupportProvider`)"*. **Neither class exists.**
  `grep -rn 'LuaLabelFindUsagesProvider\|LuaLabelRefactoringSupportProvider' src/` finds only two
  KDoc mentions and no definition. `3cafe73e` (NAV-02) folded the first into
  `LuaFindUsagesProvider`; `ab85c066` (REFACT-02) folded the second into
  `LuaRefactoringSupportProvider`.

So the **components** named for this feature are superseded, but the **capability** is not: it is
alive, it is the only working rename target in Lunar, and nothing else specifies the rules it must
obey. Those rules are Lua's, not the platform's, and no other feature has any reason to encode them:

- `isMemberInplaceRenameAvailable` returns `true` for exactly one type, `LuaLabelName`
  (`LuaRefactoringSupportProvider:23`). Labels are the only element in the plugin with in-place
  rename.
- Label binding runs through `LuaLabelReference` / `LuaLabelScopeProcessor` /
  `LuaBlock.processLabelDeclarations` — machinery entirely separate from `LuaNameReference`, because
  label scope is block-nested and function-bounded, not lexical-position-bounded.
- `LuaNameReferenceSearcher.isNameDeclarationLeaf` **excludes** `LuaLabelName` by name, deliberately
  routing labels to a different search path from every other symbol.

The correct outcome is therefore neither `superseded` nor a merge into REFACT-01. It is this
feature, re-pointed at the components that actually deliver it, with the rules it must respect
written down for the first time. The **class attribution** in the epic table is what is superseded,
and `REFACT-04-00` records that.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail — the defect
that left [[DEBUG-07]] marked shipped for months ([[BUG-450]] §4). This file has that same
provenance to answer for: it was one of the 16 placeholder `requirements.md` files created in bulk
and stamped ✅ in bulk. The rows below were derived from three sources outside this repo and only
then checked against it.

### 1. Lua's own label semantics — executed, not recalled

Every rule below was run against the real interpreters in
`~/Documents/src/lua/interpreters/*/src/lua` on 2026-08-22 rather than quoted from the manual,
because the one rule that matters most for renaming **changed between 5.3 and 5.4** and a
manual-derived spec would have got it wrong.

| Program | 5.1.5 | 5.2.4 | 5.3.6 | 5.4.7 |
| :--- | :--- | :--- | :--- | :--- |
| `::a::` / `goto a` | `unexpected symbol near ':'` | OK | OK | OK |
| `::a::` then `do ::a:: end` (nested shadowing) | error | **OK** | **OK** | **`label 'a' already defined on line 1`** |
| `do ::a:: end` / `do ::a:: end` (siblings) | error | OK | OK | OK |
| `::a::` / `::a::` (same block) | error | `label 'a' already defined` | `label 'a' already defined` | `label 'a' already defined` |
| `goto a` / `do ::a:: end` (jump into a block) | error | `no visible label 'a'` | `no visible label 'a'` | `no visible label 'a'` |
| `goto s` / `local x=1` / `::s::` / `print(x)` | error | `jumps into the scope of local 'x'` | same | same |
| `do local x=1 goto c ::c:: end` (label ends the block) | error | OK | OK | OK |
| `function f() goto out end` / `::out::` | error | `no visible label 'out'` | same | same |
| `::end::`, `::goto::` (reserved word as a label) | error | `<name> expected` | same | same |
| `::::` (label with the name deleted) | error | `<name> expected near '::'` | same | same |

Two consequences drive the table:

- **Renaming can violate exactly one of Lua's four label rules.** "No jumping into a block", "no
  jumping into the scope of a local", and "labels stop at function boundaries" are all invariant
  under renaming — a rename moves no code. Only **duplicate/shadowing** is reachable, which is why
  `REFACT-04-07` is a `Must` and `REFACT-04-20` can be `Full` by construction.
- **The duplicate rule is version-dependent, so conflict detection must be too.** Executed proof of
  what that costs, using the same rename (`b` → `a`) on the same file:

  ```lua
  local n = 0
  ::a::
  n = n + 1
  do
    if n < 2 then goto a end
    ::b::          -- rename this to `a`
  end
  print("n="..n)
  ```

  ```
  5.3.6 before rename (::b::)     n=2
  5.3.6 after  rename (::a::)     n=1        <- silently different program
  5.4.7 after  rename (::a::)     after.lua:7: label 'a' already defined on line 2
  ```

  One rename, no warning, and the outcome is a behaviour change on 5.2/5.3 and a compile error on
  5.4/5.5. That is the single most important row in this document.

  **Lua 5.5 is not in the executed matrix** — no 5.5 interpreter is installed on this host
  (`~/Documents/src/lua/interpreters` tops out at `lua-5.4.7`). 5.5 is assumed to follow 5.4 here;
  that assumption is **unverified** and is called out again in `REFACT-04-08`.

### 2. The IntelliJ refactoring contract

`RefactoringSupportProvider.isMemberInplaceRenameAvailable` / `isInplaceRenameAvailable`;
`RenamePsiElementProcessorBase` and its `substituteElementToRename`, `findReferences`,
`prepareRenaming`, **`findExistingNameConflicts`**, `isToSearchInComments`; `NamesValidator`;
`RenameInputValidator`; `PsiNameIdentifierOwner.setName`; `PsiReference.handleElementRename`;
`SafeDeleteProcessorDelegate`; `PsiElement.getUseScope`. Each is a question this feature must answer
either "we provide it" or "we deliberately do not".

Two platform facts, read in `~/Documents/src/lua/intellij-community`, are load-bearing below:

- `RenamePsiElementProcessorBase.findExistingNameConflicts` (both overloads, lines 129 and 142) has
  an **empty body**. Conflict detection is opt-in per language; a plugin that registers no
  `renamePsiElementProcessor` gets none. Lunar registers none —
  `grep -rn 'RenamePsiElementProcessor\|renamePsiElementProcessor' src/` is empty.
- `MemberInplaceRenameHandler.isAvailable` (line 44) requires **both**
  `element instanceof PsiNameIdentifierOwner` **and** `isMemberInplaceRenameAvailable`. That
  conjunction is why the next section matters.

### 3. The repo fact that is the whole reason this feature exists

Lunar models almost no declarations as named elements — `LuaNameDeclElement : PsiNameIdentifierOwner`
has exactly **one** grammar rule behind it, `labelName` (`lua.bnf:251-253` →
`LuaLabelName extends LuaNameDeclElement`, mixin `LuaNameDeclElementImpl` with a working
`setName`). Every other name is a `nameRef` whose mixin implements `PsiNamedElement` only, and a
table-constructor key is a bare IDENTIFIER leaf. Confirmed by
`grep -rn LuaNameDeclElement src/main/gen src/main/kotlin`: the only implementor is
`LuaLabelNameImpl`.

Labels therefore clear the platform's rename bar that nothing else in the plugin clears, which is
both why label rename works and why it is specified separately. It is also the ceiling on the
feature: everything below is what *one* real `PsiNameIdentifierOwner` buys, and what it does not.

### Evidence classes

- **Executed** — the Lua matrix above. Ran it; output pasted.
- **Verified by reading this repo** — that a class, override or `plugin.xml` registration exists or
  does not. Absences rest on `grep` over `src/main/` and `src/main/resources/META-INF/plugin.xml`.
- **(inferred)** — traced through platform control flow in `intellij-community`, not observed. No
  live IDE session was run for this table, and several rows cannot be settled without one; they say
  so.

## Scope

- **In scope**: renaming a `::label::` from its declaration or from a `goto`; which `goto`s a rename
  rewrites; rejecting or warning about a new name that breaks the file; the label-specific parts of
  in-place rename, Safe Delete and Find Usages.
- **Out of scope, delegated**: new-name syntax validation ([[REFACT-05]]); Find Usages mechanics
  ([[NAV-02]]); Safe Delete mechanics ([[REFACT-03]]); rename of every non-label identifier
  ([[REFACT-01]]); reporting that labels are invalid at 5.1 (`LuaLanguageLevelInspection`).
- **Out of scope, absent**: any label refactoring other than rename. No "extract label", no
  "convert `goto` to structured control flow" — see `REFACT-04-18`.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `REFACT-04-00` | **Feature attributed to components that exist** | **M** | **Superseded** | The epic's *"Implemented (`LuaLabelFindUsagesProvider`, `LuaLabelRefactoringSupportProvider`)"* names two deleted classes. The capability lives in `LuaRefactoringSupportProvider` (`plugin.xml:384`), `LuaFindUsagesProvider` (`:375`), `LuaSafeDeleteProcessor` (`:387`) and `LuaNamesValidator` (`:391`). Recorded as `Superseded` because the *attribution* was replaced by shipped work (`3cafe73e`, `ab85c066`), not because the feature was. |
| `REFACT-04-01` | **A label declaration is a renameable named element** | **M** | **Full** | `LuaLabelName : LuaNameDeclElement : PsiNameIdentifierOwner`, with `getNameIdentifier()` returning the IDENTIFIER child and `setName` replacing it through `LuaElementFactory.createIdentifier`. The single precondition the platform imposes, and the only element in the plugin that meets it. |
| `REFACT-04-02` | **Rename the declaration; every bound `goto` follows** | **M** | **Full** | `::myLabel::` + `goto myLabel` → both rewritten. The rewrite of each usage is `LuaLabelReference.handleElementRename`, which calls `setName` on the `LuaLabelRef` composite. Covered end to end by `LuaLabelRenameTest.testRenameFromDeclaration` and, for the multi-`goto` case the coverage-gap note below used to name, `.testRenameRewritesEveryBoundGoto` (TC-04-A, REFACT-04 Phase 1). |
| `REFACT-04-03` | **Rename invoked from a `goto` usage** | **M** | **Full** | Caret on `goto my<caret>Label` renames the declaration and all usages. Works because `LuaLabelRef`'s reference resolves to a `PsiNameIdentifierOwner`, so `TargetElementUtil`'s `REFERENCED_ELEMENT_ACCEPTED` branch yields a legal rename target — the branch that fails for every other Lua symbol ([[REFACT-01]] `-02`). `LuaLabelRenameTest.testRenameFromReference`. The dangling-`goto` half (a `goto` with no visible label) is closed by REFACT-04 Phase 3's `LuaLabelRenameProcessor.substituteElementToRename`, which refuses rather than renaming a `LuaLabelRef` in place — `LuaLabelConflictTest.testRenameFromAGoto` (TC-04-N). |
| `REFACT-04-04` | **Function-boundary isolation** | **M** | **Full** | Two functions may each hold `::L::`; renaming one must not touch the other. Enforced in `LuaLabelScopes.walkLabelScopes` (moved verbatim from `LuaLabelReference` in REFACT-04 Phase 2, boundary test delegated to `LuaLabelScopes.isFunctionBoundary`), which stops at `LuaFuncDef`/`LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaGlobalFuncDecl` — the PSI counterpart of the executed *"no visible label 'out'"* rule. `LuaLabelRenameTest.testScopeIsolatedRename`, and `LuaLabelResolutionTest`/`LuaLabelCompletionTest` as the regression gate for the move. |
| `REFACT-04-05` | **Shadowed labels renamed by binding, not by spelling** | **S** | **Full** | On 5.2/5.3 an inner `::a::` may legally shadow an outer one (executed above); renaming the outer must rewrite only the `goto`s that bind to it. The substrate is right — `processLabelDeclarations` walks a block's **direct** statements only, so an inner label is invisible from outside, and `isReferenceTo` re-resolves each candidate rather than matching text. Was `Partial` because no test renamed under shadowing; closed by REFACT-04 Phase 1's `LuaLabelRenameTest.testRenameUnderShadowingRewritesOnlyTheBoundGotos` (TC-04-C), mutation-confirmed against the `resolved === owner` identity test. `LuaLabelResolutionTest.testSiblingBlockResolution` remains the resolver-only regression guard. |
| `REFACT-04-06` | **New name is a valid, non-reserved identifier** | **M** | **Full** | Executed: `::end::` and `::goto::` are both `<name> expected` at every level, so rejecting reserved words is unconditionally correct for labels. Delegated to `LuaNamesValidator` / [[REFACT-05]], not duplicated. Unlike [[REFACT-01]] `-10`, here the validator is **not** inert: the rename UI that consults it is reachable — `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel` (TC-04-O, REFACT-04 Phase 1) asserts `RenameUtil.isValidName` reaches the validator for a `LuaLabelName`, not merely that the validator's own booleans are correct. |
| `REFACT-04-07` | **Collision with a mutually visible label is caught** | **M** | **Full** | Closed by REFACT-04 Phase 3: `LuaLabelRenameProcessor` (`refactoring/rename/LuaLabelRenameProcessor.kt`), registered as a `renamePsiElementProcessor`, delegates `findCollisions` to `LuaLabelConflictDetector`. **This requirement's own rule statement was wrong** — "ancestor-or-self, or descendant" reports on legal code; executed, `do ::a:: end` followed by `::a::` is legal on 5.2.4, 5.3.6 **and 5.4.7** (design §1 rows P-b/P-e). The implemented rule (design §3.2) is directional: conflict iff the two share a function and one is declared in an enclosing-or-same block of the other **and the outer one comes first in source order**. `risks-and-gaps.md` RD-1 records the defect. `LuaLabelConflictTest`: `testDuplicateLabelRenameRefusedAt54` (TC-04-D), `testSiblingBlocksDoNotCollide` (TC-04-F, negative), `testEarlierLabelInAClosedBlockDoesNotCollide` (TC-04-G, the RD-1 guard), `testLabelInANestedFunctionNeverCollides` (TC-04-H), `testLabelProcessorIsTheOneThePlatformSelects` (TC-04-L, wiring). All five mutation-confirmed. |
| `REFACT-04-08` | **The collision rule tracks the configured language level** | **S** | **Full** | Closed by REFACT-04 Phase 3: `LuaLabelConflictDetector`'s `messageFor` reads `LuaProjectSettings.getInstance(project).state.languageLevel` and selects `refactoring.rename.label.conflict.duplicate` at `>= LUA54` or `…conflict.rebind` below it (design §3.4) — one conflicts-dialog mechanism, tiered by message, not two severities (`risks-and-gaps.md` RD-2 records why a second, harder mechanism does not exist in the platform). `LuaLabelConflictTest.testDuplicateLabelRenameRefusedAt54` (TC-04-D) and `.testSameRenameReportedDifferentlyAt53` (TC-04-E), both mutation-confirmed against the tier comparison. **Lua 5.5 behaviour remains unverified** (DR-01, unresolved): no 5.5 interpreter on this host; the `>=` comparison fails safe by giving 5.5 the stricter 5.4+ wording. |
| `REFACT-04-09` | **In-place (inline) rename in the editor** | **S** | **Full (verified live)** | `isMemberInplaceRenameAvailable` returns `true` for a `LuaLabelName` (among others, since REFACT-07); `MemberInplaceRenameHandler.isAvailable` additionally requires `PsiNameIdentifierOwner`, which `LuaLabelName` satisfies. Both halves are asserted by `LuaLabelRenameTest.testInPlaceRenameAvailabilityConjuncts` (TC-04-M, REFACT-04 Phase 1). The one conjunct no unit test can reach — `editor.getSettings().isVariableInplaceRenameEnabled()` — was confirmed by **DR-02** (REFACT-04 Phase 3, live `runIde` session): `Shift+F6` on a label activated the in-place editable template, `Enter` committed through a real `RenameProcessor` (reaching `findCollisions`, raising the exact conflicts-dialog message for a colliding name), and a non-colliding rename applied instantly with no dialog. Outcome recorded in `risks-and-gaps.md`. |
| `REFACT-04-10` | **The rename UI calls the target a "label"** | **C** | **Full** | `LuaFindUsagesProvider.getType` returns `"label"` and `getDescriptiveName` the identifier text, so the dialog and preview read *Rename label 'done'* rather than *Rename element*. Asserted by `LuaFindUsagesTest.testCanFindUsagesForLabel`. |
| `REFACT-04-11` | **The usage search stays inside the containing function** | **S** | **Full** | A label is invisible outside its function (executed) and can never be referenced from another file, so the search scope is knowable exactly. REFACT-04 Phase 2 adds `LuaNameDeclElementImpl.getUseScope()` (`LuaBaseElements.kt`), gated on `this is LuaLabelName`, returning `LocalSearchScope(LuaLabelScopes.functionScopeOf(this))` — the enclosing function, or the containing `LuaFile` for a top-level label. Correctness was already preserved by `isReferenceTo`; this removes the cost. Covered by `LuaLabelRenameTest.testLabelUseScopeIsEnclosingFunction` (TC-04-I) and `.testTopLevelLabelUseScopeIsFile` (TC-04-J), both mutation-confirmed. `TC-04-A` (multi-`goto`) is the regression gate that the narrowing did not drop a usage. |
| `REFACT-04-12` | **Find Usages on a label** | **S** | **Full** | Delegated to [[NAV-02]] `NAV-02-03`, not duplicated. Noted here only because rename consumes the same search, and because labels take a **different path** from every other symbol: `LuaNameReferenceSearcher` returns early for `LuaLabelName`, leaving the platform's default named-element searcher to drive `LuaLabelReference`. |
| `REFACT-04-13` | **Safe Delete of a label removes the whole `::name::`** | **S** | **Not Implemented** | `LuaSafeDeleteProcessor.handlesElement` accepts a `LuaLabelName` (asserted by `LuaSafeDeleteTest.testLabelDeclarationIsAvailable`), but `declarationNodeFor` elevates a leaf only when its parent is a `LuaNameRef`; a label's parent is a `LuaLabel`, so it returns the `LuaLabelName` unchanged and the surrounding `::` `::` survive. Executed: **`::::` is `<name> expected near '::'` on every version** — the refactoring would leave the file unparseable. **(inferred)** end to end; no test deletes a label, only checks availability. The fix is one `is LuaLabelName -> element.parent` branch. |
| `REFACT-04-14` | **Rename from the Structure View** | **C** | **Full (verified live)** | Closed by REFACT-04 Phase 4: `LuaLabelStructureViewTreeElement.getValue()` now returns `myLabel.labelName` (a `LuaLabelName`, `PsiNameIdentifierOwner`) instead of `labelName.identifier` — the IDENTIFIER leaf `StructureViewComponent` published as `CommonDataKeys.PSI_ELEMENT` before this phase. Asserted by `LuaStructureViewTest.testLabelNodeValueIsRenameableLabelName` (TC-04-K), mutation-confirmed. `getPresentation()`'s null-tolerant read and `LuaStructureViewModel.SUITABLE_CLASSES` are untouched (design §2.6; the latter is Gap 2.2, out of scope). **DR-03 executed** (live `runIde` session): selecting the label node in the Structure View and invoking `Refactor ▸ Rename…` opened the rename dialog (not *"cannot be renamed"*), and completing it renamed both the label and its `goto` in one commit. Outcome recorded in `risks-and-gaps.md`. |
| `REFACT-04-15` | **Rename is one undoable command** | **C** | **Full** | Platform-supplied — `RenameProcessor` runs inside a `WriteCommandAction`, so declaration and usages undo together. Recorded because the requirement is about the user, and *not* implemented by Lunar: an override here would replace working behaviour. |
| `REFACT-04-16` | **Availability under language level 5.1** | **C** | **Won't** | At 5.1 a label is a syntax error in real Lua (executed) but still parses in Lunar, and `LuaLanguageLevelInspection` already reports it with `RemoveLabelFix`. Rename stays offered on purpose: gating it would remove a way to clean the code up and duplicate the inspection. **This decision is recorded here for the first time** — nothing in the repo states it, and the current behaviour is a side effect of the grammar being level-agnostic rather than a choice anyone made. |
| `REFACT-04-17` | **Rename a label with no `goto`** | **C** | **Full** | A label with zero references renames the declaration and reports no usages. Legal Lua (a label is a void statement); the degenerate case of `-02`. Covered by `LuaLabelRenameTest.testRenameWithNoGotoRenamesDeclarationAlone` (TC-04-B, REFACT-04 Phase 1). |
| `REFACT-04-18` | **Refactorings other than rename** | **W** | **Won't** | The epic row says *"renaming and refactoring of `goto` labels"*, which reads wider than what exists. Convert-`goto`-to-`break`, hoist-a-label, extract-loop: all require control-flow rewriting that changes semantics in ways Lua's jump rules make hard to prove safe (`LuaControlFlowBuilder` models labels but performs no rewriting). Out of scope by decision; an intention under the `INTENT` half of this epic would be the right home if it is ever wanted. |
| `REFACT-04-19` | **Cross-file rename** | **W** | **Won't** | A label cannot be referenced from another file — executed: it is not even visible from another *function*. Listed so the absence reads as a language fact rather than a gap, and so `-11` is understood as a scope that is too **wide**, not too narrow. |
| `REFACT-04-20` | **Rename never creates a jump-into-block or jump-into-local error** | **S** | **Full** | Guaranteed by construction, and the reason `-07` is the only conflict row: renaming relabels, it does not move code, so the two positional rules (executed: *"no visible label"* on a jump into a block, *"jumps into the scope of local 'x'"*) cannot be newly violated by a rename. Recorded explicitly so a future implementer of `-07` does not build machinery for rules a rename cannot break. |

## Test Cases

### TC-REFACT-04-02: Declaration rename propagates
- **Input**: `::<caret>myLabel::` / `goto myLabel`.
- **Action**: Rename to `newLabel`.
- **Output**: `::newLabel::` / `goto newLabel`.

### TC-REFACT-04-04: Same name in two functions
- **Input**: `function a() ::<caret>L:: goto L end` / `function b() ::L:: goto L end`.
- **Action**: Rename to `L2`.
- **Output**: only `a`'s label and `goto` change; `b` is untouched.

### TC-REFACT-04-07: Collision is refused or warned (satisfied, REFACT-04 Phase 3)
- **Input**: `::a::` / `n = n + 1` / `do if n < 2 then goto a end ::<caret>b:: end`.
- **Action**: Rename `b` to `a`.
- **Expected as originally written**: at level 5.4/5.5, a blocking conflict — the result does not
  compile. At 5.2/5.3, a warning that `goto a` will rebind from the outer label to the inner one.
  **This wording is imprecise** — the platform's rename pipeline has exactly one conflict surface
  (a conflicts dialog the user may Continue past), not a separate "blocking" mechanism at 5.4+;
  `risks-and-gaps.md` RD-2 records why a truthful hard block is not achievable and what was chosen
  instead (one mechanism, tiered by message).
- **Now**: `LuaLabelConflictTest.testDuplicateLabelRenameRefusedAt54` (TC-04-D) drives exactly this
  fixture at `LUA54` and asserts `BaseRefactoringProcessor.ConflictsInTestsException` is thrown with
  an "already defined" message; `.testSameRenameReportedDifferentlyAt53` (TC-04-E) drives it at
  `LUA53` and asserts a "jump to the nearer label" message instead. Both mutation-confirmed.

### TC-REFACT-04-13: Safe Delete leaves valid syntax (not yet satisfied)
- **Input**: `::done::` with no `goto`.
- **Action**: Safe Delete on the label.
- **Expected**: the whole `::done::` statement is gone.
- **Actual (inferred)**: only the name is removed, leaving `::::`, which no Lua version parses.

## Verification

| Row | Covered by |
| :--- | :--- |
| `-01` | `LuaLabelRenameTest.testNameIdentifierOwner` |
| `-02` | `LuaLabelRenameTest.testRenameFromDeclaration`, `.testRenameRewritesEveryBoundGoto` (TC-04-A) |
| `-03` | `LuaLabelRenameTest.testRenameFromReference` |
| `-04` | `LuaLabelRenameTest.testScopeIsolatedRename` |
| `-05` | `LuaLabelRenameTest.testRenameUnderShadowingRewritesOnlyTheBoundGotos` (TC-04-C) |
| `-06` | `LuaNamesValidatorTest` (booleans) plus `.testRenameUtilReachesValidatorForLabel` (TC-04-O — the *path*, see [[REFACT-05]]) |
| `-09` | `LuaLabelRenameTest.testInPlaceRenameAvailabilityConjuncts` (TC-04-M) — the two headlessly observable conjuncts only; the third needs a live IDE (DR-02) |
| `-10`, `-12` | `LuaFindUsagesTest.testCanFindUsagesForLabel`, `.testLabelUsagesCount` |
| `-13` | `LuaSafeDeleteTest.testLabelDeclarationIsAvailable` — availability **only**; nothing deletes a label |
| `-14` | `LuaStructureViewTest.testLabelNodeLeafPresentation` — presentation only; nothing renames from the tree |
| `-16` | `LuaLanguageLevelInspectionTest.labelNotAllowedInLua51`, `.labelAllowedInLua52`, `.removeGotoQuickFixDeletesStatement` — the inspection, not the refactoring |
| `-17` | `LuaLabelRenameTest.testRenameWithNoGotoRenamesDeclarationAlone` (TC-04-B) |
| `-11` | `LuaLabelRenameTest.testLabelUseScopeIsEnclosingFunction` (TC-04-I), `.testTopLevelLabelUseScopeIsFile` (TC-04-J) |
| `-07` | `LuaLabelConflictTest.testDuplicateLabelRenameRefusedAt54` (TC-04-D), `.testSiblingBlocksDoNotCollide` (TC-04-F), `.testEarlierLabelInAClosedBlockDoesNotCollide` (TC-04-G), `.testLabelInANestedFunctionNeverCollides` (TC-04-H), `.testLabelProcessorIsTheOneThePlatformSelects` (TC-04-L) |
| `-08` | `LuaLabelConflictTest.testDuplicateLabelRenameRefusedAt54` (TC-04-D), `.testSameRenameReportedDifferentlyAt53` (TC-04-E) |
| `-15`, `-20` | **Nothing** — both are true by construction/platform-supplied; `risks-and-gaps.md` "Test Case Gaps" records why no test can fail for either. |

Supporting coverage that the rename rows rest on but do not themselves exercise:
`LuaLabelResolutionTest` (`testBackwardLabelResolution`, `testForwardLabelResolution`,
`testEnclosingBlockResolution`, `testFunctionBoundaryResolution`, `testSiblingBlockResolution`) and
`LuaLabelCompletionTest` (`testVisibleLabelsCompletion`, `testEnclosingBlockCompletion`,
`testSiblingBlockCompletion`, `testFunctionBoundaryCompletion`). Label *binding* is well tested;
label *rename* is tested only in its three simplest shapes.

**Three coverage gaps were named here and are now closed by REFACT-04 Phase 1** (`implementation-plan.md`, no production code changed):

1. ~~No test renames a label with more than one `goto`.~~ Closed by TC-04-A
   (`testRenameRewritesEveryBoundGoto`), mutation-confirmed against `LuaLabelReference.isReferenceTo`.
2. ~~No test renames under shadowing~~ (`-05`). Closed by TC-04-C
   (`testRenameUnderShadowingRewritesOnlyTheBoundGotos`), mutation-confirmed against the
   `resolved === owner` identity test.
3. ~~Nothing exercises `-09` in-place rename, not even the boolean.~~ Partially closed by TC-04-M
   (`testInPlaceRenameAvailabilityConjuncts`), which asserts the two headlessly observable conjuncts
   — `PsiNameIdentifierOwner` and `isMemberInplaceRenameAvailable`. The third conjunct
   (`editor.getSettings().isVariableInplaceRenameEnabled()`) still needs a live IDE session (DR-02);
   see `risks-and-gaps.md`.

TC-04-M's negative fixture deviates from `implementation-plan.md`'s literal text — see
`risks-and-gaps.md` RD-5: REFACT-07 (landed after this design) broadened
`isMemberInplaceRenameAvailable` to also cover file-local declarations, so `local x = 1` is no longer
a valid negative case and a global declaration is used instead.

**Recorded nowhere else in the repo:** `-07`/`-08` (silent rebinding on 5.2/5.3, compile error on
5.4+, no conflict detection at all), `-11` (project-wide search scope for a function-local symbol),
`-13` (Safe Delete of a label yields the unparseable `::::`), `-14` (Structure View publishes a leaf,
so F2 there cannot work), and `-00` (the epic table names two deleted classes). None has a bug
report. `-07` is the one that deserves one first: a refactoring that reports success while changing
what the program does is the same defect class as [[REFACT-01]] `-01`.

**On the front-matter status.** It was corrected from `done` to `todo` on 2026-08-22, and neither
value is right. `done` was never earned — one `Must` row (`-07`) and four `Should` rows (`-05`,
`-08`, `-11`, `-13`) are unmet and `-09` is unverified. But `todo` understates a feature whose core
demonstrably works, and which is the only rename in the plugin that does. `scripts/lint_planning.py`
rejected anything above `todo` while this feature had no `design*.md`, and writing one was named here
as the first task. **That task is done**: `design.md`, `implementation-plan.md` and
`risks-and-gaps.md` landed on 2026-08-22, so the feature moved to `planned` — the unmet rows above
are specified down to their tests and had not been started. Read `planned` as "the remaining work is
planned and not yet begun", not "nothing exists", which was never true here.

**Phase 1 (`implementation-plan.md`) landed with no production code changed** — five tests
(TC-04-A/-B/-C/-M/-O) that close three of this section's named coverage gaps and mutation-confirm
`-02`, `-05`, `-06`, `-09` (partial), `-17` for the first time. `-05` moved `Partial` → `Full`. Status
moves to `in_progress`: real work has started, but `-07`/`-08`/`-11`/`-13` remain `Not Implemented`
and `-09`/`-14` remain unverified live.

**Phase 2 (`implementation-plan.md`) lands the label scope model and the use-scope narrowing.**
`LuaLabelScopes` (new, `lang/psi/LuaLabelScopes.kt`) centralizes the function-boundary rule that
`LuaLabelReference` used to own alone — `walkLabelScopes` moved verbatim, boundary test delegated to
the new `isFunctionBoundary`. `LuaNameDeclElementImpl.getUseScope()` (new override, gated on
`this is LuaLabelName`) narrows a label's use scope to its enclosing function, or the containing
`LuaFile` for a top-level label. Two new tests, TC-04-I and TC-04-J, both mutation-confirmed; the
move's regression gate (`LuaLabelResolutionTest`, `LuaLabelCompletionTest`) and the narrowing's
regression gate (`LuaFindUsagesTest.testLabelUsagesCount`, `LuaSafeDeleteTest.testLabelDeclarationIsAvailable`,
and Phase 1's TC-04-A) are all green. `-11` moves `Not Implemented` → `Full`; `-04` stays `Full`
(extraction only, no behaviour change). Status stays `in_progress`: `-07`/`-08`/`-13` remain
`Not Implemented` and `-09`/`-14` remain unverified live.

**Phase 3 (`implementation-plan.md`) lands the conflict check.** `LuaLabelConflictDetector` (new,
`refactoring/rename/LuaLabelConflictDetector.kt`) implements the corrected duplicate-label rule
(design §3.2, `risks-and-gaps.md` RD-1) and emits REFACT-01's `LuaRenameCollisionUsageInfo` — no new
carrier defined. `LuaLabelRenameProcessor` (new, same package) extends `RenamePsiElementProcessor`
and `DumbAware`, overrides exactly `canProcessElement`, `substituteElementToRename` and
`findCollisions`, and is registered as a `renamePsiElementProcessor` in `plugin.xml` with no `order`
attribute. Three bundle keys added (`refactoring.rename.label.conflict.duplicate`, `…conflict.rebind`,
`…unresolvedGoto`); nothing removed. Seven new tests in the new `LuaLabelConflictTest`
(TC-04-D/-E/-F/-G/-H/-L/-N), all mutation-confirmed — nine mutations run in total, since TC-04-H and
TC-04-L each have two named mutations and both were executed. `-07` and `-08` move `Not Implemented`
→ `Full`; `-03`'s dangling-`goto` half closes (TC-04-N). **DR-02 executed** (in-place rename, live
IDE) — outcome recorded in `risks-and-gaps.md`. Full suite green: 2913 tests / 0 failures / 0 errors /
1 skipped across 466 files (baseline 2906/465 + this phase's 7 tests in 1 new file — exact
reconciliation, no unexplained delta). `ktlintCheck` clean. Status stays `in_progress`: `-13` remains
`Not Implemented` (delegated to [[BUG-458]]) and `-09`/`-14` remain unverified live; Phase 4
(Structure View rename target) is the only remaining `todo` phase.

**Phase 4 (`implementation-plan.md`) lands the Structure View fix.** `LuaLabelStructureViewTreeElement.getValue()`
(`lang/structure/LuaLabelStructureViewTreeElement.kt`) now returns `myLabel.labelName` instead of
`labelName.identifier ?: labelName.firstChild ?: labelName` — design §2.6's one-line change.
`getPresentation()`'s null-tolerant read and `LuaStructureViewModel.SUITABLE_CLASSES` are untouched,
as instructed; the latter's disagreement with the new `getValue()` type is Gap 2.2, recorded as
out-of-scope future work. One new test, TC-04-K (`LuaStructureViewTest.testLabelNodeValueIsRenameableLabelName`),
mutation-confirmed: reverting `getValue()` to its old form reddened exactly that test (1 of 16) and no
other. `-14` moves `Not Implemented` → `Full (verified live)`. **DR-03 executed** (Structure View
rename, live IDE) — outcome recorded in `risks-and-gaps.md`; the one open item there (a raw `F2` not
firing directly on the tree under `xdotool`) is non-blocking and not a regression this phase
introduced. Full suite green: 2914 tests / 0 failures / 0 errors / 1 skipped across 466 files
(baseline 2913 + this phase's 1 new test — exact reconciliation, no unexplained delta). `ktlintCheck`
clean.

**All four phases of `implementation-plan.md` are now done.** Status moves to `done`. The one
remaining unmet row, `-13` (S, Safe Delete of a label), stays `Not Implemented` and delegated to
[[BUG-458]] (itself still `todo`) — it was never a phase of this plan (`implementation-plan.md`'s
Requirement → Phase Coverage table lists it as "Delegated to BUG-458", not assigned to Phase 1-4),
so it does not gate this feature's own completion, the same treatment `-00`, `-06` and `-12` already
get for work this feature depends on but does not itself deliver. `-16`/`-18`/`-19` are `Won't` by
recorded decision, not gaps.
