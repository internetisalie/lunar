---
id: REFACT-04
title: "04: Label Refactoring"
type: feature
status: "planned"
vf_icon: 📋
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
| `REFACT-04-02` | **Rename the declaration; every bound `goto` follows** | **M** | **Full** | `::myLabel::` + `goto myLabel` → both rewritten. The rewrite of each usage is `LuaLabelReference.handleElementRename`, which calls `setName` on the `LuaLabelRef` composite. Covered end to end by `LuaLabelRenameTest.testRenameFromDeclaration` — but **only for a single `goto`**; see Verification. |
| `REFACT-04-03` | **Rename invoked from a `goto` usage** | **M** | **Full** | Caret on `goto my<caret>Label` renames the declaration and all usages. Works because `LuaLabelRef`'s reference resolves to a `PsiNameIdentifierOwner`, so `TargetElementUtil`'s `REFERENCED_ELEMENT_ACCEPTED` branch yields a legal rename target — the branch that fails for every other Lua symbol ([[REFACT-01]] `-02`). `LuaLabelRenameTest.testRenameFromReference`. |
| `REFACT-04-04` | **Function-boundary isolation** | **M** | **Full** | Two functions may each hold `::L::`; renaming one must not touch the other. Enforced in `LuaLabelReference.walkLabelScopes`, which stops at `LuaFuncDef`/`LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaGlobalFuncDecl` — the PSI counterpart of the executed *"no visible label 'out'"* rule. `LuaLabelRenameTest.testScopeIsolatedRename`. |
| `REFACT-04-05` | **Shadowed labels renamed by binding, not by spelling** | **S** | **Partial** | On 5.2/5.3 an inner `::a::` may legally shadow an outer one (executed above); renaming the outer must rewrite only the `goto`s that bind to it. The substrate is right — `processLabelDeclarations` walks a block's **direct** statements only, so an inner label is invisible from outside, and `isReferenceTo` re-resolves each candidate rather than matching text. `Partial` because **no test renames under shadowing**; `LuaLabelResolutionTest.testSiblingBlockResolution` proves the resolver, not the rename. |
| `REFACT-04-06` | **New name is a valid, non-reserved identifier** | **M** | **Full** | Executed: `::end::` and `::goto::` are both `<name> expected` at every level, so rejecting reserved words is unconditionally correct for labels. Delegated to `LuaNamesValidator` / [[REFACT-05]], not duplicated. Unlike [[REFACT-01]] `-10`, here the validator is **not** inert: the rename UI that consults it is reachable. |
| `REFACT-04-07` | **Collision with a mutually visible label is caught** | **M** | **Not Implemented** | The one rule a rename can break, and nothing checks it. No `renamePsiElementProcessor` is registered, so `findExistingNameConflicts` is the platform's empty default. Executed cost, same rename: **5.3 → a silently different program (`n=2` becomes `n=1`); 5.4 → `label 'a' already defined`**. The rule to implement is exact and needs no invention: conflict iff another label of the new name is declared in a block that is an **ancestor-or-self, or descendant**, of this label's block **within the same function** — sibling blocks do not collide (executed: `do ::a:: end do ::a:: end` is OK on every version). |
| `REFACT-04-08` | **The collision rule tracks the configured language level** | **S** | **Not Implemented** | Follows from `-07` and from the executed matrix: shadowing is **legal on 5.2/5.3 and an error on 5.4+**, so the same rename is a hard block at one level and at most a warning at another. Any check must read `LuaProjectSettings.getInstance(project).state.languageLevel` — as `LuaLanguageLevelInspection` and `LuaCompletionContributor` (`level >= LuaLanguageLevel.LUA52`) already do — rather than assuming one dialect. **Lua 5.5 behaviour is unverified**: no 5.5 interpreter on this host, and 5.5 is a supported level. |
| `REFACT-04-09` | **In-place (inline) rename in the editor** | **S** | **Full (not verified live)** | `isMemberInplaceRenameAvailable` returns `elementToRename is LuaLabelName`; `MemberInplaceRenameHandler.isAvailable` additionally requires `PsiNameIdentifierOwner`, which `LuaLabelName` satisfies. Both halves are read and correct. **Not observable headlessly** — the handler also requires `editor.getSettings().isVariableInplaceRenameEnabled()` — and **no test asserts even the boolean**: `grep -rn isMemberInplaceRenameAvailable src/test/` is empty. Needs a live IDE session (the `verify-in-ide` loop). |
| `REFACT-04-10` | **The rename UI calls the target a "label"** | **C** | **Full** | `LuaFindUsagesProvider.getType` returns `"label"` and `getDescriptiveName` the identifier text, so the dialog and preview read *Rename label 'done'* rather than *Rename element*. Asserted by `LuaFindUsagesTest.testCanFindUsagesForLabel`. |
| `REFACT-04-11` | **The usage search stays inside the containing function** | **S** | **Not Implemented** | A label is invisible outside its function (executed) and can never be referenced from another file, so the search scope is knowable exactly. `LuaNameDeclElementImpl` does **not** override `getUseScope` (`grep -rn getUseScope src/main/kotlin` → one hit, in `LuaSafeDeleteProcessor`, and it only *reads* `.useScope`), so the platform falls back to a project-wide scope and every rename word-indexes the whole project to find at most a handful of in-function `goto`s. Correctness is preserved by `isReferenceTo`; cost is not. A `LocalSearchScope` over the enclosing function is the fix. |
| `REFACT-04-12` | **Find Usages on a label** | **S** | **Full** | Delegated to [[NAV-02]] `NAV-02-03`, not duplicated. Noted here only because rename consumes the same search, and because labels take a **different path** from every other symbol: `LuaNameReferenceSearcher` returns early for `LuaLabelName`, leaving the platform's default named-element searcher to drive `LuaLabelReference`. |
| `REFACT-04-13` | **Safe Delete of a label removes the whole `::name::`** | **S** | **Not Implemented** | `LuaSafeDeleteProcessor.handlesElement` accepts a `LuaLabelName` (asserted by `LuaSafeDeleteTest.testLabelDeclarationIsAvailable`), but `declarationNodeFor` elevates a leaf only when its parent is a `LuaNameRef`; a label's parent is a `LuaLabel`, so it returns the `LuaLabelName` unchanged and the surrounding `::` `::` survive. Executed: **`::::` is `<name> expected near '::'` on every version** — the refactoring would leave the file unparseable. **(inferred)** end to end; no test deletes a label, only checks availability. The fix is one `is LuaLabelName -> element.parent` branch. |
| `REFACT-04-14` | **Rename from the Structure View** | **C** | **Not Implemented** | Labels appear in the structure view (`LuaLabelStructureViewTreeElement`, `LuaStructureViewTest.testLabelNodeLeafPresentation`), but its `getValue()` returns `labelName.identifier` — the **IDENTIFIER leaf**. `StructureViewComponent` publishes exactly that as `CommonDataKeys.PSI_ELEMENT` (line 861), and a leaf is not a `PsiNamedElement`, so **(inferred)** F2 there reports *cannot be renamed* even though F2 in the editor works. Returning the `LuaLabelName` instead would fix it; navigation, the reason the leaf was chosen, works from either. |
| `REFACT-04-15` | **Rename is one undoable command** | **C** | **Full** | Platform-supplied — `RenameProcessor` runs inside a `WriteCommandAction`, so declaration and usages undo together. Recorded because the requirement is about the user, and *not* implemented by Lunar: an override here would replace working behaviour. |
| `REFACT-04-16` | **Availability under language level 5.1** | **C** | **Won't** | At 5.1 a label is a syntax error in real Lua (executed) but still parses in Lunar, and `LuaLanguageLevelInspection` already reports it with `RemoveLabelFix`. Rename stays offered on purpose: gating it would remove a way to clean the code up and duplicate the inspection. **This decision is recorded here for the first time** — nothing in the repo states it, and the current behaviour is a side effect of the grammar being level-agnostic rather than a choice anyone made. |
| `REFACT-04-17` | **Rename a label with no `goto`** | **C** | **Full** | A label with zero references renames the declaration and reports no usages. Legal Lua (a label is a void statement); the degenerate case of `-02`. |
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

### TC-REFACT-04-07: Collision is refused or warned (not yet satisfied)
- **Input**: `::a::` / `n = n + 1` / `do if n < 2 then goto a end ::<caret>b:: end`.
- **Action**: Rename `b` to `a`.
- **Expected**: at level 5.4/5.5, a blocking conflict — the result does not compile. At 5.2/5.3, a
  warning that `goto a` will rebind from the outer label to the inner one.
- **Actual**: the rename proceeds silently. Executed consequence: `n=2` becomes `n=1` on 5.3.6; on
  5.4.7 the file fails to load with `label 'a' already defined on line 2`.

### TC-REFACT-04-13: Safe Delete leaves valid syntax (not yet satisfied)
- **Input**: `::done::` with no `goto`.
- **Action**: Safe Delete on the label.
- **Expected**: the whole `::done::` statement is gone.
- **Actual (inferred)**: only the name is removed, leaving `::::`, which no Lua version parses.

## Verification

| Row | Covered by |
| :--- | :--- |
| `-01` | `LuaLabelRenameTest.testNameIdentifierOwner` |
| `-02` | `LuaLabelRenameTest.testRenameFromDeclaration` |
| `-03` | `LuaLabelRenameTest.testRenameFromReference` |
| `-04` | `LuaLabelRenameTest.testScopeIsolatedRename` |
| `-06` | `LuaNamesValidatorTest` (booleans only — see [[REFACT-05]]) |
| `-10`, `-12` | `LuaFindUsagesTest.testCanFindUsagesForLabel`, `.testLabelUsagesCount` |
| `-13` | `LuaSafeDeleteTest.testLabelDeclarationIsAvailable` — availability **only**; nothing deletes a label |
| `-14` | `LuaStructureViewTest.testLabelNodeLeafPresentation` — presentation only; nothing renames from the tree |
| `-16` | `LuaLanguageLevelInspectionTest.labelNotAllowedInLua51`, `.labelAllowedInLua52`, `.removeGotoQuickFixDeletesStatement` — the inspection, not the refactoring |
| `-05`, `-07`, `-08`, `-09`, `-11`, `-15`, `-17`, `-20` | **Nothing** |

Supporting coverage that the rename rows rest on but do not themselves exercise:
`LuaLabelResolutionTest` (`testBackwardLabelResolution`, `testForwardLabelResolution`,
`testEnclosingBlockResolution`, `testFunctionBoundaryResolution`, `testSiblingBlockResolution`) and
`LuaLabelCompletionTest` (`testVisibleLabelsCompletion`, `testEnclosingBlockCompletion`,
`testSiblingBlockCompletion`, `testFunctionBoundaryCompletion`). Label *binding* is well tested;
label *rename* is tested only in its three simplest shapes.

**Three coverage gaps worth naming, all found by writing this table:**

1. **No test renames a label with more than one `goto`.** All three rename tests use exactly one
   reference. The multi-reference path is the platform's `RenameProcessor` loop and is very likely
   fine, but `-02` claims "every bound `goto`" and one reference does not demonstrate "every".
2. **No test renames under shadowing** (`-05`), which is the case where "rewrite the right ones" has
   any content.
3. **Nothing at all exercises `-09` in-place rename**, not even the boolean, and it cannot be
   exercised headlessly.

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
`risks-and-gaps.md` landed on 2026-08-22, so the feature moves to `planned` — the unmet rows above
are specified down to their tests and have not been started. Read `planned` as "the remaining work is
planned and not yet begun", not "nothing exists", which was never true here.
