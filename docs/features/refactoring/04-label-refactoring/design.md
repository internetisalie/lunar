---
id: "REFACT-04-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "REFACT-04"
priority: "medium"
folders:
  - "[[features/refactoring/04-label-refactoring/requirements|requirements]]"
---

# Technical Design: REFACT-04 — Label Refactoring

## 1. Architecture Overview

### Current State

Label rename **works**, and it is the only rename in the plugin that does. This design does not
rebuild it; it adds the one thing Lua requires and the platform cannot supply — a conflict check —
and closes three named gaps around it.

| Fact | Evidence |
| :--- | :--- |
| `LuaLabelName` is the only `PsiNameIdentifierOwner` in the plugin | `lua.bnf:251-254` gives `labelName` the mixin `LuaNameDeclElementImpl` and the interface `LuaNameDeclElement`; `LuaBaseElements.kt:51` declares `interface LuaNameDeclElement : PsiNameIdentifierOwner`; `src/main/gen/.../impl/LuaLabelNameImpl.java:14` is its only implementor |
| `setName` on a label really swaps the AST child | `LuaBaseElements.kt:61-70`, `node.replaceChild(identifierNode, LuaElementFactory.createIdentifier(...).node)` |
| `LuaLabelReference` is the only reference in the plugin overriding `handleElementRename` | `LuaLabelReference.kt:45-48` |
| Label binding is a separate machine from name binding | `LuaLabelReference.resolveLabel` (`:36-43`) → `LuaLabelScopeProcessor` (`LuaLabelScopeProcessor.kt:10-39`) → `LuaBlock.processLabelDeclarations` (`LuaBlockExt.kt:83-93`), bounded by `walkLabelScopes` (`LuaLabelReference.kt:69-89`). `LuaBlock.processDeclarations` (`LuaBlockExt.kt:25-81`) has **no** `LuaLabel` branch |
| The name searcher deliberately steps aside for labels | `LuaNameReferenceSearcher.isNameDeclarationLeaf` returns false for `LuaLabelName` first (`LuaNameReferenceSearcher.kt:84-88`), so the platform's default named-element searcher drives `LuaLabelReference` |
| In-place rename is offered for exactly one type | `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` (`LuaRefactoringSupportProvider.kt:23-26`) returns `elementToRename is LuaLabelName` |
| Nothing detects a label collision | The only `renamePsiElementProcessor` today is `LuaUnsupportedRenameProcessor` (`plugin.xml:389-390`), and it excludes `LuaLabelName` (`LuaUnsupportedRenameProcessor.kt:37-41`), so labels reach `RenamePsiElementProcessorBase.DEFAULT`, whose `findCollisions` and both `findExistingNameConflicts` overloads have empty bodies (`RenamePsiElementProcessorBase.java:129-147`, `:248-253`) |
| A label's search scope is the whole module | `LuaNameDeclElementImpl` does not override `getUseScope`, so `PsiElementBase.getUseScope` (`PsiElementBase.java:184-186`) delegates to `ResolveScopeManagerImpl.getUseScope`, which ends at `GlobalSearchScope.moduleWithDependentsScope(module)` (`ResolveScopeManagerImpl.java:227-231`) |
| The Structure View publishes a leaf, not a named element | `LuaLabelStructureViewTreeElement.getValue()` returns `labelName.identifier` (`LuaLabelStructureViewTreeElement.kt:25-28`); `StructureViewComponent` publishes exactly that as `CommonDataKeys.PSI_ELEMENT` (`StructureViewComponent.java:861-864`) |

### What REFACT-01 owns and this feature consumes

[[REFACT-01]]'s design §1 carries a table headed *"Shared machinery — the REFACT-04 boundary"*. It is
authoritative and is **not restated here**. Read it there. This design's only additions to it are the
consumption points:

| Shared item (owned by REFACT-01) | Where this design consumes it |
| :--- | :--- |
| `LuaRenameCollisionUsageInfo` (REFACT-01 design §2.4) | §2.3 emits it; §2.3 defines **no** new carrier |
| The C1/C2/C3 conflict *shape* — collect candidates → run a scope walk → anchor the collision on the **colliding declaration**, never on a usage that must still be rewritten | §3.3, with `LuaLabelScopes` substituted for `LuaScopeProcessor`/`scopeCrawlUp` |
| `LuaDeclarationSite` / `LuaDeclarationKind.LABEL` (REFACT-01 §2.1) | Consumed **unchanged and unextended**. This design adds no kind and forks no enum; `LuaFindUsagesProvider.getType` keeps returning `"label"` through it, which is all `REFACT-04-10` needs |
| `LuaRenameProcessor` — explicitly **not** shared | §3.0 and §9 Alternative A: this design registers a *separate* processor for labels and never routes a label through `LuaRenameProcessor` |
| The epic-table correction at `docs/features/refactoring/requirements.md:36` | Already a REFACT-01 Phase 2 task (`implementation-plan.md:196-206`). `REFACT-04-00` is **delegated**, not re-done — see implementation-plan "Delegated work" |

### The repo constraint that shapes the whole design

Labels are the one Lua name with real declaration PSI. Everything the platform's rename pipeline
asks of a target — `PsiNameIdentifierOwner`, a working `setName`, a reference that can rewrite
itself — a label already satisfies, and nothing else in Lunar does. That is why this feature is not
a subset of REFACT-01 and why its shape is the opposite of REFACT-01's:

> **REFACT-01 must build a rename. REFACT-04 must not disturb one.**

The consequence, and the single most important decision in this design:

> **The label rename path stays on the platform's default behaviour.** The new
> `LuaLabelRenameProcessor` (§2.2) extends `RenamePsiElementProcessor` and overrides exactly two
> methods: `canProcessElement` and `findCollisions` — plus `substituteElementToRename` for the
> `LuaLabelRef` case, which is a *refusal*, not a rename. `findReferences`, `renameElement`,
> `prepareRenaming`, `isInplaceRenameSupported`, `createRenameDialog`, `getHelpID`,
> `isToSearchInComments` and every other hook are inherited from
> `RenamePsiElementProcessorBase`, which is the same code the label rename runs through today
> (`RenamePsiElementProcessorBase.DEFAULT` is a subclass that overrides only `canProcessElement`,
> `RenamePsiElementProcessorBase.java:275-282`). **One hook is inherited from a different place**:
> `createDialog`, which `RenamePsiElementProcessor` itself overrides (`RenamePsiElementProcessor.java:22-28`).
> §3.6's `createRenameDialog` row states that delta and why the dialog the user sees is unchanged.

So "a new code path" is precisely one method call: `RenameUtil.findUsages` already calls
`elementProcessor.findCollisions(element, newName, allRenames, result)` unconditionally
(`RenameUtil.java:103`), on the default processor, where it does nothing. This design makes that
call do something. Nothing else about the label rename moves. §9 Alternative D records what was
rejected instead.

### Premises examined

| Premise | Fixed? |
| :--- | :--- |
| Label rename works today and must not regress | **Fixed, and it is the constraint the design is built around.** Everything below is additive; the only edits to live rename behaviour are `getUseScope` (§3.5, narrows a search that was already correct) and `findCollisions` (§3.3, previously a no-op). |
| Conflict detection must live behind a registered `renamePsiElementProcessor` | **Chosen, not inherited.** `renameInputValidator` and `findExistingNameConflicts` were both examined; §9 Alternatives B and C say why each loses. |
| `REFACT-04-07`'s stated rule ("ancestor-or-self **or descendant**") is the rule to implement | **NOT fixed — corrected.** Executed below: a label in a *descendant* block that precedes the renamed one is legal on 5.4.7. The implemented rule is §3.2, and `risks-and-gaps.md` RD-1 records the requirement defect. Implementing the requirement verbatim would have produced a conflict report on legal code. |
| `REFACT-04-07`'s "blocking conflict at 5.4/5.5 vs warning at 5.2/5.3" is two mechanisms | **NOT fixed — collapsed to one.** The platform's rename pipeline has exactly one conflict surface (the conflicts dialog) and no severity axis; the only hard block available (`renameInputValidator`) can state its reason only as *"'a' is not a valid identifier"* (`LangBundle.properties:235`), which is false. §3.4 tiers the **message**, not the mechanism. `risks-and-gaps.md` RD-2. |
| A label's use scope is a given and the cost is unavoidable | **NOT fixed — removed.** `getUseScope` is a one-method override on a mixin used by exactly one grammar rule (§3.5). The scope is knowable exactly, because a label is invisible outside its function (executed, `REFACT-04-04`). |
| Safe Delete of a label belongs to this feature | **NOT fixed — delegated.** [[BUG-458]] already owns it, with a fix strategy and a test strategy. This design adds a cross-dependency note (`risks-and-gaps.md` Gap 2.3) and no code. |
| `LuaLabelRef` (the `goto` side) can be left to the platform default | **NOT fixed — claimed.** It can be left alone *today* only because `LuaUnsupportedRenameProcessor` refuses it (`LuaUnsupportedRenameProcessor.kt:37-41`); REFACT-01 Phase 2 deletes that class and excludes `LuaLabelRef` from `LuaRenameProcessor` (REFACT-01 design §3.0 rule 1), which leaves an unresolved `goto` renameable by the platform default. §3.1 closes it. |
| Lua 5.5 follows Lua 5.4 | **Not verifiable here.** No 5.5 interpreter on this host (`~/Documents/src/lua/interpreters` tops out at `lua-5.4.7`). Read-derived corroboration only (see the evidence table); tracked as **DR-01**. The design fails safe: `>= LUA54` selects the blocking-tier message, so 5.5 gets the stricter wording. |

### Evidence class of the behavioural claims

**Executed** — run on this host on 2026-08-22, output pasted. Each program was run under
`~/Documents/src/lua/interpreters/lua-<v>/src/lua`.

| # | Program | 5.2.4 | 5.3.6 | 5.4.7 |
| :-- | :--- | :--- | :--- | :--- |
| P-a | `::a::` ⏎ `do ::a:: end` | ok | ok | `p_a.lua:2: label 'a' already defined on line 1` |
| P-b | `do ::a:: end` ⏎ `::a::` | ok | ok | **ok** |
| P-c | `do ::a:: end` ⏎ `do ::a:: end` | ok | ok | ok |
| P-d | `::a::` ⏎ `do` ⏎ `do ::a:: end` ⏎ `end` | ok | ok | `p_d.lua:3: label 'a' already defined on line 1` |
| P-e | `do` ⏎ `do ::a:: end` ⏎ `end` ⏎ `::a::` | ok | ok | **ok** |
| P-f | `goto a` (no label anywhere) | `no visible label 'a' for <goto> at line 1` | same | same |
| P-g | `function f()` ⏎ `::a::` ⏎ `local g = function()` ⏎ `::a::` ⏎ `end` ⏎ `end` | ok | ok | **ok** |

**P-b and P-e are the rows that matter.** They are the "descendant" direction of the rule
`REFACT-04-07` states, and they are legal on every version tested. The rule is therefore
*directional*, and §3.2 encodes the direction. The rest of the executed matrix — the same-block
duplicate, the sibling blocks, the function boundary, the reserved-word rejection, `::::` — is in
`requirements.md` and is not re-run here.

**P-g is TC-04-H's post-rename text** and was run through `<v>/src/luac -p` rather than `lua` (same
binaries; `luac -p` compiles without executing, and the same harness reports P-a's
`label 'a' already defined on line 1` on 5.4.7, so an error would not be swallowed). It is the
executed form of `REFACT-04-04` for *duplicate* labels rather than for `goto` visibility: a nested
function is its own label scope, so the same name in both is legal at every level. The pre-rename
text (`::b::` in place of the outer `::a::`) is `rc=0` on all three as well.

**Read-derived, from `~/Documents/src/lua/intellij-community` and the Lua 5.4.7 sources.** grep
settles existence; these are behaviour and are marked as such.

| Claim | Evidence | Executed by |
| :--- | :--- | :--- |
| `findlabel` scans only labels declared so far in the current function's still-open blocks | `lua-5.4.7/src/lparser.c:544-554` (`for (i = ls->fs->firstlabel; i < dyd->label.n; i++)`), used by `checkrepeated` (`:1448-1455`) from `labelstat` (`:1463`). This is the *mechanism* behind P-a…P-e, and the reason 5.5 is expected to behave like 5.4 | TC-04-D/F/G (they assert the *predictions*, not the C code) |
| `RenameUtil.findUsages` calls `findCollisions` on the processor `forPsiElement` selected | `RenameUtil.java:87-106` | TC-04-D — it can only throw if this call reaches `LuaLabelConflictDetector` |
| `findCollisions` runs inside a background read action, never on the EDT | `BaseRefactoringProcessor.java:303` — `refUsages.set(ReadAction.computeBlocking(this::findUsages))` | Not directly asserted; a violation surfaces as a `SlowOperationsException`/read-access assertion in any TC that renames |
| An `UnresolvableCollisionUsageInfo` becomes a conflicts-dialog entry keyed on `usage.getElement()` and is then **removed from the usage set** | `RenameUtil.addConflictDescriptions` (`RenameUtil.java:309-315`), `RenameUtil.removeConflictUsages` (`:297-307`), called from `RenameProcessor.preprocessUsages` (`RenameProcessor.java:166-171`, `:248`) | TC-04-D (throws) and TC-04-F/G (do not) |
| In unit-test mode a non-empty conflicts map throws `BaseRefactoringProcessor.ConflictsInTestsException`, whose `getMessages()` strips HTML | `RenameProcessor.java:179-182`, `BaseRefactoringProcessor.java:755-786` | TC-04-D, TC-04-E — and the repo already relies on it (`LuaSafeDeleteTest.kt:142-148`) |
| **In-place rename does reach `findCollisions`** — `MemberInplaceRenamer` runs a real `RenameProcessor`, unlike its `VariableInplaceRenamer` base | `MemberInplaceRenamer.performRefactoringRename` (`MemberInplaceRenamer.java:250-297`) → `performRenameInner` (`:309-317`) → `MyRenameProcessor extends RenameProcessor` (`:399-408`). The base's `performRefactoringRename` (`VariableInplaceRenamer.java:459-525`) never calls `RenameUtil.findUsages` for the primary element | **Not executed** — in-place rename is not observable headlessly (`REFACT-04-09`). **DR-02** |
| The EP is typed `RenamePsiElementProcessorBase` but the in-place path **casts to `RenamePsiElementProcessor`** | `RenamePsiElementProcessor.forElement` (`RenamePsiElementProcessor.java:30-38`) does an unchecked `(RenamePsiElementProcessor)` cast, and `MemberInplaceRenamer.MyRenameProcessor` calls it (`MemberInplaceRenamer.java:401`). Extending only the `…Base` class would throw `ClassCastException` inside the IDE | Not executed; §2.2 fixes the superclass, and TC-04-L asserts the registered instance's type |
| `RenameUtil.isValidName` reaches `LuaNamesValidator` through `LanguageNamesValidation` on **both** the dialog and the in-place paths | `RenameUtil.java:383-406`, `RenameDialog.areButtonsValid` (`RenameDialog.java:420-423`), `MemberInplaceRenamer.isIdentifier` (`MemberInplaceRenamer.java:166-169`) | TC-04-O |
| `CommonRefactoringUtil.showErrorHint` throws `RefactoringErrorHintException` in unit-test mode | `CommonRefactoringUtil.java:79-99` | TC-04-N |
| A default `getUseScope` for a file in a module source root is `moduleWithDependentsScope` | `ResolveScopeManagerImpl.java:227-231` | TC-04-I — it asserts the *replacement* is a `LocalSearchScope`, which is red without the override |
| A `LocalSearchScope` from `getUseScope` **replaces** the rename's refactoring scope instead of narrowing it | `RenameUtil.processUsages` (`RenameUtil.java:118-134`), whose `if (!(useScope instanceof LocalSearchScope)) useScope = searchScope.intersectWith(useScope)` at `:129-131` skips the intersection; `PsiSearchHelperImpl.java:143-146` delegates to `element.getUseScope()`; the scope it would otherwise intersect with is `GlobalSearchScope.projectScope` (`RenameProcessor.java:104`) | TC-04-A after Phase 2 — its named mutation (`getUseScope` → `LocalSearchScope(this)`) reddens it only because this is true |
| `StructureViewComponent` publishes the selected node's `getValue()` as `CommonDataKeys.PSI_ELEMENT`, and `PsiElementRenameHandler` refuses anything that is neither a `PsiNamedElement` nor claimed by a processor | `StructureViewComponent.java:861-864`; `PsiElementRenameHandler.getRenameErrorMessage` (`PsiElementRenameHandler.java:150-159`) | TC-04-K asserts the precondition (`getValue()` is a `PsiNameIdentifierOwner`); the end result needs a live IDE — **DR-03** |

### Prior Art in This Repo

| Component | file:line | This design |
| :--- | :--- | :--- |
| `LuaLabelReference` | `lang/LuaLabelReference.kt` | **EXTENDED.** `walkLabelScopes` (`:69-89`) **moves** into `LuaLabelScopes` (§2.1) and is called back. `resolve`, `multiResolve`, `isReferenceTo`, `getVariants`, `handleElementRename` are **untouched** — `isReferenceTo` (`:50-56`) is what makes `REFACT-04-05` correct and must not be simplified. |
| `LuaLabelScopeProcessor` / `LuaLabelCompletionScopeProcessor` | `lang/LuaLabelScopeProcessor.kt:10-61` | **REUSED UNCHANGED.** The conflict detector does not resolve; it compares declaration positions (§3.3), so it needs no processor. |
| `LuaBlock.processLabelDeclarations` | `lang/psi/LuaBlockExt.kt:83-93` | **REUSED UNCHANGED.** Named here because §3.3's block model must agree with it — see §6 E-4. |
| `LuaNameDeclElementImpl` | `lang/psi/LuaBaseElements.kt:53-71` | **EXTENDED.** Gains `getUseScope()` (§2.4, §3.5), gated on `this is LuaLabelName`. |
| `LuaLabelStructureViewTreeElement` | `lang/structure/LuaLabelStructureViewTreeElement.kt:25-28` | **EXTENDED.** `getValue()` returns the `LuaLabelName` instead of its IDENTIFIER child (§2.6). `getPresentation`/`getChildren` untouched. |
| `LuaRefactoringSupportProvider` | `lang/insight/LuaRefactoringSupportProvider.kt:23-26` | **REUSED UNCHANGED.** `isMemberInplaceRenameAvailable` is already correct for `REFACT-04-09`. Its stale KDoc (`:12`) is corrected by **REFACT-01** (REFACT-01 design §1 Prior Art); this design does not touch it. |
| `LuaNameReferenceSearcher` | `lang/insight/LuaNameReferenceSearcher.kt:84-88` | **REUSED UNCHANGED.** Its label exclusion is what routes labels to the default searcher; REFACT-01 §3.8 keeps it. |
| `LuaSafeDeleteProcessor` | `refactoring/LuaSafeDeleteProcessor.kt:156-171` | **NOT TOUCHED HERE.** `REFACT-04-13` is delegated to [[BUG-458]]; the interaction with REFACT-01's `LuaDeclarationSite.declarationNodeOf` move is `risks-and-gaps.md` Gap 2.3. |
| `LuaNamesValidator` | `refactoring/LuaNamesValidator.kt:12-25`, registered `plugin.xml:393-395` | **REUSED UNCHANGED.** Owns `REFACT-04-06`; specified by [[REFACT-05]]. |
| `LuaPsiUtils.getElementLineNumber` | `lang/psi/LuaPsiUtils.kt:100-104` | **REUSED UNCHANGED.** Supplies the `{1}` parameter of both conflict messages (§7). |
| `LuaProjectSettings` | `settings/LuaProjectSettings.kt:52` (`state.languageLevel`), read the way `LuaLanguageLevelInspection.level` reads it (`analysis/inspections/LuaLanguageLevelInspection.kt:155-156`) | **REUSED UNCHANGED.** Supplies §3.4's tier. |
| `LuaUnsupportedRenameProcessor` | `refactoring/rename/LuaUnsupportedRenameProcessor.kt`, `plugin.xml:389-390` | **NOT TOUCHED HERE.** REFACT-01 Phase 2 deletes it. §3.0 is written so that both orderings are correct — see §6 E-1. |

### Target State

```
Shift+F6 on ::label:: or on goto label
  │
  ├─ MemberInplaceRenameHandler   (editor, in-place)                             REFACT-04-09
  │    isAvailable = isVariableInplaceRenameEnabled
  │                && element is PsiNameIdentifierOwner        ← LuaLabelName
  │                && LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable
  │    on commit → MemberInplaceRenamer.performRenameInner → RenameProcessor ────┐
  │                                                                              │
  └─ PsiElementRenameHandler      (dialog; also the Structure View path)  ───────┤
                                                                                 │
                                        RenameProcessor ─────────────────────────┘
                                          │
                                          ├─ RenameUtil.findUsages
                                          │    ├─ processUsages → findReferences   INHERITED
                                          │    │    → ReferencesSearch over getUseScope   §3.5
                                          │    │    → default searcher → LuaLabelReference.isReferenceTo
                                          │    └─ findCollisions ──► LuaLabelConflictDetector  §3.3
                                          ├─ preprocessUsages → conflicts dialog          §3.4
                                          └─ renameElement                        INHERITED
                                               → LuaLabelName.setName + LuaLabelReference.handleElementRename
```

Everything marked `INHERITED` is the code that runs today. The two new boxes are
`LuaLabelConflictDetector` and the narrowed `getUseScope`.

## 2. Core Components

**Symbol provenance — read this before grepping.** Three groups appear below and a reviewer checking
grounding needs to tell them apart:

| Group | Symbols | Expected `grep src/` result |
| :--- | :--- | :--- |
| **(new)** — introduced by this feature | `LuaLabelScopes` (§2.1), `LuaLabelRenameProcessor` (§2.2), `LuaLabelConflictDetector` and `LuaLabelRenameTarget` (§2.3) | **No hit.** These are the four types Phases 2-3 create. |
| **(REFACT-01)** — planned elsewhere, consumed here | `LuaRenameCollisionUsageInfo`, `LuaDeclarationSite`, `LuaDeclarationKind.LABEL`, `LuaRenameProcessor` | **No hit yet.** Owned by [[REFACT-01]] design §2.1/§2.2/§2.4, which is `status: planned`. Every mention below names REFACT-01 as the owner; `implementation-plan.md` makes `LuaRenameCollisionUsageInfo` a blocking precondition on Phase 3 only. |
| **(existing)** — everything else | `LuaLabelName`, `LuaLabelRef`, `LuaLabelReference`, `LuaLabelScopeProcessor`, `LuaNameDeclElementImpl`, `LuaBlock`, `LuaFuncDef`/`LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaGlobalFuncDecl`, `LuaFindUsagesProvider`, `LuaRefactoringSupportProvider`, `LuaSafeDeleteProcessor`, `LuaNamesValidator`, `LuaPsiUtils`, `LuaProjectSettings`, `LuaLanguageLevel`, `LuaBundle`, `LuaElementFactory`, `LuaLabelStructureViewTreeElement`, `LuaStructureViewModel` | **Hit, with the `file:line` cited at each use.** |


### 2.1 `net.internetisalie.lunar.lang.psi.LuaLabelScopes` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaLabelScopes.kt`
- **Responsibility**: the single answer to "which function bounds this label's scope, which block
  declares it, and which labels share its function". One rule, three callers.
- **Threading**: pure PSI reads; the caller holds read access. Stateless `object`; retains nothing.
- **Collaborators**: `LuaBlock` (`src/main/gen/.../LuaBlock.java:10`), `LuaLabel`, `LuaLabelName`,
  `LuaFuncDef` (`…/LuaFuncDef.java:10`), `LuaFuncDecl` (`…/LuaFuncDecl.java:15`),
  `LuaLocalFuncDecl` (`…/LuaLocalFuncDecl.java:15`), `LuaGlobalFuncDecl` (`…/LuaGlobalFuncDecl.java:10`),
  `com.intellij.psi.util.PsiTreeUtil`, `com.intellij.openapi.progress.ProgressManager`.

```kotlin
object LuaLabelScopes {
    /**
     * True for the four PSI types at which label visibility stops. The single source of the
     * function-boundary rule; [walkLabelScopes] and [functionScopeOf] must both use it and
     * nothing else may re-enumerate it.
     */
    fun isFunctionBoundary(element: PsiElement): Boolean

    /**
     * Visits every [LuaBlock] between [start] and its function boundary, innermost first,
     * stopping early when [visit] returns false. Moved verbatim from
     * `LuaLabelReference.walkLabelScopes` (`LuaLabelReference.kt:69-89`) with the boundary
     * test delegated to [isFunctionBoundary].
     */
    fun walkLabelScopes(start: PsiElement, visit: (LuaBlock) -> Boolean)

    /**
     * The element that bounds [element]'s label scope: the nearest ancestor for which
     * [isFunctionBoundary] holds, or the containing [LuaFile] when there is none.
     * Null only when [element] has no containing file.
     */
    fun functionScopeOf(element: PsiElement): PsiElement?

    /** The [LuaBlock] that declares [label], i.e. the block whose `statementList` holds its `LuaLabel`. */
    fun blockOf(label: LuaLabelName): LuaBlock?

    /**
     * Every [LuaLabelName] declared directly in [scope]'s own function — descendants inside a
     * nested function are excluded. See §3.3 step 3.
     */
    fun labelsInFunctionScope(scope: PsiElement): List<LuaLabelName>
}
```

- **Tripwire compliance**: five functions, each ≤ 12 logic lines, each ≤ 2 arguments.
- `walkLabelScopes` keeps its exact present semantics, including that it visits a `LuaBlock` *before*
  testing the boundary at the same element — no `LuaBlock` is also a function boundary, so the order
  is unobservable, but the move must not reorder it. `LuaLabelResolutionTest` and
  `LuaLabelCompletionTest` are the regression gate for the move.

### 2.2 `net.internetisalie.lunar.refactoring.rename.LuaLabelRenameProcessor` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelRenameProcessor.kt`
- **Responsibility**: attach Lua's label rules to the rename the platform already performs. It adds
  a conflict check and a refusal; it performs no rename of its own.
- **Superclass**: `com.intellij.refactoring.rename.RenamePsiElementProcessor` — **not**
  `RenamePsiElementProcessorBase`. `MemberInplaceRenamer.MyRenameProcessor` calls
  `RenamePsiElementProcessor.forElement(element)` (`MemberInplaceRenamer.java:401`), which casts the
  EP instance unconditionally (`RenamePsiElementProcessor.java:34`); the wrong superclass is a
  `ClassCastException` in the IDE on every in-place label rename.
- **Threading**: `canProcessElement` and `substituteElementToRename` run on the EDT (`canProcessElement`
  is also reached from `RenameHandler.isAvailableOnDataContext`) and do pure PSI type tests plus, for
  `LuaLabelRef`, one `LuaLabelReference.resolve()` over a single function's blocks — no index read,
  no file scan. `findCollisions` runs inside the background read action of
  `BaseRefactoringProcessor.java:303`.
- **Dumb mode**: the class also implements `com.intellij.openapi.project.DumbAware`, and the marker is
  part of the specification, not decoration. Both selection functions skip any processor for which
  `DumbService.isUsableInCurrentContext` is false — `RenamePsiElementProcessorBase.forPsiElement`
  (`RenamePsiElementProcessorBase.java:153-161`, the test at `:156`) and
  `RenamePsiElementProcessor.forElement` (`RenamePsiElementProcessor.java:30-38`, the test at `:33`).
  Without the marker a label falls back to `RenamePsiElementProcessorBase.DEFAULT` during indexing,
  and the conflict check — a `Must` requirement (`REFACT-04-07`) — silently disappears in a state the
  user cannot see, while `Shift+F6` itself stays enabled (`RenameElementAction extends DumbAwareAction`,
  `RenameElementAction.java:35`). The marker is safe because all three overrides are index-free:
  two `is` tests (§3.0); one `LuaLabelReference.resolve()`, which is a `LuaLabelScopeProcessor` walk
  over the enclosing function's blocks with no index or stub read (`LuaLabelReference.kt:36-43`,
  `LuaLabelScopeProcessor.kt:10-39`) (§3.1); and a `PsiTreeUtil` pass plus a `LuaProjectSettings` read
  and a `document.getLineNumber` call (`LuaPsiUtils.kt:100-104`) (§3.3). The idiom has platform
  precedent: `class RenamePsiFileDumbProcessor : RenamePsiElementProcessor(), DumbAware`
  (`RenamePsiFileDumbProcessor.kt:34`). Nothing is claimed here about the dumb-mode behaviour of the
  inherited pipeline — only that these three overrides remain reachable and correct.
- **Collaborators**: `LuaLabelName`, `LuaLabelRef`, `LuaLabelReference`, `LuaLabelConflictDetector`,
  `LuaBundle` (`LuaBundle.kt:15-26`), `com.intellij.refactoring.util.CommonRefactoringUtil`,
  `com.intellij.refactoring.RefactoringBundle`, `com.intellij.openapi.project.DumbAware`.

```kotlin
class LuaLabelRenameProcessor :
    RenamePsiElementProcessor(),
    DumbAware {
    override fun canProcessElement(element: PsiElement): Boolean          // §3.0

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement?  // §3.1

    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    )                                                                     // §3.3
}
```

- **Nothing else is overridden**, and §3.6 lists what that decision buys and why each omission is
  deliberate. `findCollisions`'s four parameters are the platform's signature
  (`RenamePsiElementProcessorBase.java:248-252`); the engineering contract's ≤3-argument cap governs
  functions this codebase declares, not a platform `override` — the same carve-out REFACT-01's
  implementation plan states verbatim.

### 2.3 `net.internetisalie.lunar.refactoring.rename.LuaLabelConflictDetector` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelConflictDetector.kt`
- **Responsibility**: given a label and a proposed new name, produce the collisions of §3.3.
- **Threading**: background read action only. `ProgressManager.checkCanceled()` at the top of the
  candidate loop.
- **Collaborators**: `LuaLabelScopes` (§2.1), `LuaLabelName`, `PsiTreeUtil`, `LuaPsiUtils.getElementLineNumber`
  (`lang/psi/LuaPsiUtils.kt:100-104`), `LuaProjectSettings` (`settings/LuaProjectSettings.kt:52`),
  `LuaLanguageLevel` (`lang/LuaLanguageLevel.kt:19-35`), `LuaBundle`, and **`LuaRenameCollisionUsageInfo`
  owned by REFACT-01 design §2.4** — reused verbatim, not redefined.

```kotlin
internal data class LuaLabelRenameTarget(
    val label: LuaLabelName,
    val newName: String,
)

internal object LuaLabelConflictDetector {
    /** §3.3. Empty list when the rename is safe. Never returns a collision anchored on a `goto`. */
    fun collisions(target: LuaLabelRenameTarget): List<LuaRenameCollisionUsageInfo>
}
```

- **`internal` is required, not stylistic.** `collisions` takes an `internal` parameter type
  (`LuaLabelRenameTarget`, declared `internal` two lines above) and returns an `internal` one
  (REFACT-01 design §2.4 declares `internal class LuaRenameCollisionUsageInfo`). A `public` object
  exposing them does not compile — Kotlin rejects it with `EXPOSED_PARAMETER_TYPE` /
  `EXPOSED_FUNCTION_RETURN_TYPE`. `internal` is module-wide, so the detector stays visible to
  `LuaLabelRenameProcessor` (same module, same package) and to tests — the test compilation is
  associated with `main`, as this repo already relies on (`internal class LaunchSeams`,
  `redis/connection/LuaRedisServerLauncher.kt:47`, is referenced from
  `src/test/kotlin/net/internetisalie/lunar/redis/connection/TestLuaRedisServerLauncher.kt`).
  `LuaLabelRenameProcessor` itself stays **public**: it is instantiated by the platform from
  `plugin.xml`, and it names neither type in a signature — the detector appears only in the body of
  its `findCollisions` override.
- Two values folded into `LuaLabelRenameTarget` so `collisions` takes one argument. Private helpers
  `collides(target, other)` and `messageFor(target, other)` take two each.
- **Why the anchor is a `LuaLabelName` and not a `goto`**: it is the element the user must look at —
  the rival declaration, not one of the jumps that would be rebound. Every anchor produced by §3.3 is
  a **colliding `LuaLabelName`**, which also happens never to be a member of the renamed label's
  usage set (a label declaration is not a reference to another label).

  **This bullet previously gave a different, false reason** — that
  `RenameUtil.removeConflictUsages` deletes collision *anchors* from the usage set, so anchoring on
  a usage would skip rewriting it on Continue — inherited verbatim from REFACT-01 §2.4 as "REFACT-01
  §2.4's rule, applied unchanged". `removeConflictUsages` (`RenameUtil.java:297-307`) removes only
  `usageInfo instanceof UnresolvableCollisionUsageInfo`, i.e. the collision objects themselves, and
  `UsageInfo.equals` (`UsageInfo.java:348-359`) opens with a `getClass()` test so a real usage and a
  collision on the same element never displace one another. Corrected in REFACT-01 alongside
  [[BUG-466]], where the false claim was the sole stated reason a measured data-loss path shipped.
  **Nothing in §3.3 changes** — the anchors were already right — but a future implementer must not
  read this as a correctness constraint on where a collision may be anchored.

### 2.4 `net.internetisalie.lunar.lang.psi.LuaNameDeclElementImpl` (edit) — REFACT-04-11

Add one override to the existing mixin (`LuaBaseElements.kt:53-71`):

```kotlin
override fun getUseScope(): SearchScope {
    if (this !is LuaLabelName) return super.getUseScope()
    val boundary = LuaLabelScopes.functionScopeOf(this) ?: return super.getUseScope()
    return LocalSearchScope(boundary)
}
```

- **Why the `LuaLabelName` gate is not redundant.** `labelName` is today the only grammar rule using
  this mixin (`lua.bnf:251-254`; the only implementor is
  `src/main/gen/.../impl/LuaLabelNameImpl.java:14`), so the gate is unreachable now. It is written
  anyway because the *reason* the scope is a single function is Lua's label rule, not a property of
  `LuaNameDeclElement`: a future rule given this mixin would silently inherit a wrong scope. The gate
  makes the wrongness a compile-time-visible `super` call instead of a silent search miss.
- **Threading**: `getUseScope` is called from search/refactoring code under a read action.
- Returns `com.intellij.psi.search.LocalSearchScope`; `super.getUseScope()` is
  `PsiElementBase.getUseScope` (`PsiElementBase.java:184-186`).

### 2.5 `net.internetisalie.lunar.lang.LuaLabelReference` (edit)

`walkLabelScopes` (`LuaLabelReference.kt:69-89`) is deleted and its two call sites (`:39`, `:61`)
call `LuaLabelScopes.walkLabelScopes` instead. **No other change.** This is a move so that §3.3 and
§3.5 use the same function-boundary rule as resolution rather than a second copy of it — copying a
convention copies its defects, and this one is the definition of `REFACT-04-04`.

### 2.6 `net.internetisalie.lunar.lang.structure.LuaLabelStructureViewTreeElement` (edit) — REFACT-04-14

```kotlin
override fun getValue(): Any = myLabel.labelName
```

- Replaces `labelName.identifier ?: labelName.firstChild ?: labelName`
  (`LuaLabelStructureViewTreeElement.kt:25-28`). `LuaLabel.getLabelName()` is `@NotNull`
  (`src/main/gen/.../LuaLabel.java:8-11`), and `label ::= '::' labelName '::'` carries **no pin**
  (`lua.bnf:163`), so a partial parse rolls the whole rule back rather than materialising a
  `LuaLabel` without its `labelName`. The same class already relies on this: `getPresentation()`
  reads `myLabel.labelName` unguarded and guards only `identifier`
  (`LuaLabelStructureViewTreeElement.kt:15-16`).
- `getPresentation()` keeps its own null-tolerant read of `identifier` (`:14-17`) — that one is real
  (SYNTAX-18: a generated `@NotNull` getter can return null on a partially parsed decl) and is not
  touched.
- **Navigation is unaffected**: `LuaStructureViewTreeElement.navigate` uses the constructor's
  `myElement` (the `LuaLabel`), not `getValue()` (`LuaStructureViewTreeElement.kt:10-17`).
- **Out of scope, deliberately**: `LuaStructureViewModel.SUITABLE_CLASSES` lists `LuaLabel` and not
  `LuaLabelName` (`LuaStructureViewModel.kt:14-21`), so "autoscroll from source" already never
  matches a label node's value and still will not. Changing it is not required by any requirement and
  is not headlessly assertable — recorded as future work in `risks-and-gaps.md`.

## 3. Algorithms

### 3.0 Claiming — `canProcessElement`

- **Input → Output**: `(element: PsiElement) → Boolean`.
- **Exact predicate — this is the whole implementation:**

```kotlin
override fun canProcessElement(element: PsiElement): Boolean =
    element is LuaLabelName || element is LuaLabelRef
```

- **Rules**:
  1. `RenamePsiElementProcessorBase.forPsiElement` returns the **first** extension whose
     `canProcessElement` matches (`RenamePsiElementProcessorBase.java:153-161`). The predicate is
     therefore two disjoint type tests and nothing else — no `LuaDeclarationSite.kindOf` call, no
     language test. `LuaLabelName` and `LuaLabelRef` are Lua-only generated types
     (`lua.bnf:247-254`), so a language test would be dead code.
  2. **Registration order is irrelevant** and must not be relied on. This predicate and
     `LuaRenameProcessor`'s (REFACT-01 design §3.0, whose rule 1 excludes both types first and
     unconditionally) are disjoint by construction; `LuaUnsupportedRenameProcessor`'s
     (`LuaUnsupportedRenameProcessor.kt:37-41`) excludes `LuaLabelName` but **not** `LuaLabelRef`, so
     during the window in which both are registered the `LuaLabelRef` claim is contested — see §6 E-1.
  3. Claiming these two types makes `PsiElementRenameHandler.getRenameErrorMessage`'s
     `hasRenameProcessor` true for them (`PsiElementRenameHandler.java:150-152`). Both are already
     `PsiNamedElement` (`LuaLabelName` via `PsiNameIdentifierOwner`; `LuaLabelRef` via
     `LuaNameRefElement`, `LuaBaseElements.kt:75`), so the branch outcome is unchanged. Stated
     because `REFACT-04-01`'s correctness rests on it.
  4. Nothing else is claimed. A `LuaGotoStatement`, a `LuaLabel` and a bare IDENTIFIER leaf all fall
     through to whatever else is registered, exactly as today.

### 3.1 Target normalisation — `substituteElementToRename`

- **Input → Output**: `(element: PsiElement, editor: Editor?) → PsiElement?`
  (`null` = abort the rename).
- **Steps**:
  1. If `element is LuaLabelName` → **return `element`**. This is the base class's behaviour
     (`RenamePsiElementProcessorBase.java:227-229`) restated so that the label path is provably
     unchanged.
  2. Otherwise `element is LuaLabelRef`. Take `(element.reference as? LuaLabelReference)?.resolve()`.
  3. If it resolved to a `LuaLabelName` → return that `LuaLabelName`. (This is the same element
     `TargetElementUtil`'s `REFERENCED_ELEMENT_ACCEPTED` branch hands the platform for a resolvable
     `goto`, so step 3 is a no-op in the common case and a repair in the uncommon one.)
  4. Otherwise the `goto` refers to no visible label. Call
     `CommonRefactoringUtil.showErrorHint(element.project, editor,
     RefactoringBundle.getCannotRefactorMessage(LuaBundle.message("refactoring.rename.label.unresolvedGoto", element.text)),
     RefactoringBundle.message("rename.title"), null)` and **return null**.
- **Rules / edge handling**: `showErrorHint` throws `RefactoringErrorHintException` in unit-test mode
  (`CommonRefactoringUtil.java:84-86`), so TC-04-N expects the exception, not a null return.
  `element.text` for a `LuaLabelRef` is the identifier text (`labelRef ::= IDENTIFIER`, `lua.bnf:247`).
- **Why this exists at all**: renaming an unresolved `goto` in place would bind it to a label it did
  not previously refer to, or leave it dangling under a second name. Today
  `LuaUnsupportedRenameProcessor` refuses it as a side effect of BUG-457's blanket refusal; after
  REFACT-01 Phase 2 nothing would. Declining loudly is the same policy BUG-457 chose.
- **Complexity**: one `LuaLabelReference.resolve()` — a walk over the enclosing function's blocks.

### 3.2 The duplicate-label rule, formalised

This is the rule `REFACT-04-07` needs and the one place `REFACT-04-07`'s own wording is wrong.

**Definitions.** For a label `L`, `block(L)` is `LuaLabelScopes.blockOf(L)` and `fn(L)` is
`LuaLabelScopes.functionScopeOf(L)`. `before(X, Y)` is
`X.textRange.startOffset < Y.textRange.startOffset`. "`A` encloses `B`" is
`PsiTreeUtil.isAncestor(A, B, /* strict = */ false)` — **false** so that a block encloses itself.

**Rule.** Two labels `L` and `M` named the same collide iff all of:

1. `fn(L) === fn(M)` — same function (`REFACT-04-04`; executed: a label is invisible from another
   function), **and**
2. one of:
   - `block(M)` encloses `block(L)` **and** `before(M, L)`, or
   - `block(L)` encloses `block(M)` **and** `before(L, M)`.

In words: **they collide iff one is declared in an enclosing-or-same block of the other *and the
outer one comes first*.** When they share a block, "encloses" holds both ways and whichever comes
first satisfies the rule, so a same-block duplicate always collides.

**Why the direction.** Lua's check is `checkrepeated` → `findlabel`
(`lua-5.4.7/src/lparser.c:1448-1455`, `:544-554`), which scans only labels already declared in the
current function's **still-open** blocks. A block closes at its `end`, so a label in a block that has
already closed is not in the list. Executed confirmation: P-b and P-e are legal on 5.4.7, P-a and P-d
are not, and P-c (siblings, neither encloses the other) is legal everywhere.

**Why nothing else can be broken by a rename.** `REFACT-04-20`: "no jumping into a block", "no
jumping into the scope of a local" and "labels stop at function boundaries" are positional rules, and
a rename moves no code. Duplicate/shadowing is the only reachable violation, so this is the only rule
implemented.

### 3.3 Conflict detection — `findCollisions`

- **Input**: `element` (the substituted rename target — a `LuaLabelName`; §3.1 guarantees it),
  `newName`, `allRenames` (**ignored**: a label rename never adds elements to it, because
  `prepareRenaming` is not overridden), `result` (the usage list `RenameUtil.processUsages` has
  already filled — appended to, never read).
- **Output**: zero or more `LuaRenameCollisionUsageInfo` appended to `result`.
- **Steps** (`LuaLabelRenameProcessor.findCollisions` delegates the whole body to
  `LuaLabelConflictDetector.collisions(LuaLabelRenameTarget(label, newName))` and adds the result):
  1. `val label = element as? LuaLabelName ?: return` — nothing to check for a non-label.
  2. `if (label.name == newName) return` — a no-op rename collides with nothing.
     (`LuaNameDeclElementImpl.getName()`, `LuaBaseElements.kt:57`, reads the IDENTIFIER child through
     `findChildByType` and is null-safe; do **not** use `label.identifier.text`, whose generated
     getter is `@NotNull` but can return null on a partial parse — SYNTAX-18.)
  3. `val scope = LuaLabelScopes.functionScopeOf(label) ?: return`
  4. `val renamedBlock = LuaLabelScopes.blockOf(label) ?: return`
  5. For each `other` in `LuaLabelScopes.labelsInFunctionScope(scope)`:
     1. `ProgressManager.checkCanceled()`
     2. `if (other === label) continue`
     3. `if (other.name != newName) continue`
     4. `val otherBlock = LuaLabelScopes.blockOf(other) ?: continue`
     5. Apply §3.2 clause 2 with `renamedBlock`/`otherBlock` and the two start offsets. `continue`
        if it does not hold.
     6. Append `LuaRenameCollisionUsageInfo(anchor = other, renamedDeclaration = label, message = §3.4)`.
  6. Return the list, de-duplicated by anchor identity (two labels cannot be the same element, so the
     de-duplication is a defensive `distinctBy { it.element }`).
- **`labelsInFunctionScope(scope)`** is
  `PsiTreeUtil.findChildrenOfType(scope, LuaLabelName::class.java).filter { LuaLabelScopes.functionScopeOf(it) === scope }`.
  The filter is load-bearing: `findChildrenOfType` descends into nested `LuaFuncDef`s, whose labels
  are in a different function and can never collide (`REFACT-04-04`). TC-04-H is its gate, and its
  fixture must keep the second function **nested inside** the first: `findChildrenOfType(scope, …)`
  returns only descendants of `scope`, so a label in a *sibling* function is out of reach whether the
  filter is present or not, and a sibling fixture would leave the filter untested.
- **Rules / edge handling**:
  - `scope` is the containing `LuaFile` for a top-level label, so a file-level rename scans the whole
    file's labels and no more.
  - A label whose enclosing block cannot be determined (step 4/5.4 returning null) is skipped rather
    than guessed at — see §6 E-4.
  - The detector never resolves and never reads an index, and does no I/O — which is what makes
    §2.2's `DumbAware` marker safe rather than optimistic. The marker is what keeps the check
    *reachable* during indexing; this bullet is only about the detector being correct once reached.
- **Complexity**: one `PsiTreeUtil` pass over one function's subtree, plus one `functionScopeOf` walk
  per label found. Bounded by the size of a single Lua function; no file, index or project scan.

### 3.4 Severity tiering by language level — REFACT-04-08

- **Input → Output**: `(renamed: LuaLabelName, other: LuaLabelName) → String` (a bundle message).
- **Steps**:
  1. `val level = LuaProjectSettings.getInstance(renamed.project).state.languageLevel` — the same read
     `LuaLanguageLevelInspection.level` performs (`analysis/inspections/LuaLanguageLevelInspection.kt:155-156`).
  2. `val line = LuaPsiUtils.getElementLineNumber(other)` (`lang/psi/LuaPsiUtils.kt:100-104`, 1-based).
  3. `val key = if (level >= LuaLanguageLevel.LUA54) "refactoring.rename.label.conflict.duplicate"
     else "refactoring.rename.label.conflict.rebind"`
  4. `return LuaBundle.message(key, newName, line, level.toString())`.
- **Rules**:
  - `LuaLanguageLevel` is an `enum class` with declaration order `LUA50 … LUA55`
    (`lang/LuaLanguageLevel.kt:19-29`), so `>=` is the natural ordinal comparison and `LUA55` selects
    the blocking-tier message. That is the fail-safe direction for the unverified 5.5 behaviour
    (**DR-01**): a stricter message on a version that turns out to be permissive is a false alarm; the
    reverse is a silently broken file.
  - `LUA50` and `LUA51` take the `rebind` message. At 5.1 a label is not valid Lua at all and
    `LuaLanguageLevelInspection.visitLabel` already reports it with `RemoveLabelFix`
    (`analysis/inspections/LuaLanguageLevelInspection.kt:61-70`); `REFACT-04-16` keeps rename offered
    there on purpose, so the conflict check must not become a second, differently-worded report of a
    file that is already flagged. The weaker message is the correct one.
  - **Both tiers use the same mechanism** — a conflicts-dialog entry the user may Continue past. The
    platform's rename pipeline has no second, harder surface that also covers in-place rename; §9
    Alternative B records the one that exists and why it loses. `risks-and-gaps.md` RD-2 records the
    gap against `REFACT-04-07`'s wording.

### 3.5 Label use scope — REFACT-04-11

- **Input → Output**: `(label: LuaLabelName) → SearchScope`.
- **Steps**: §2.4's four lines. `functionScopeOf` walks parents until `isFunctionBoundary` holds,
  returning the containing `LuaFile` if it never does.
- **Rules**:
  - `LocalSearchScope(boundary)` where `boundary` is the enclosing `LuaFuncDef`/`LuaFuncDecl`/
    `LuaLocalFuncDecl`/`LuaGlobalFuncDecl`, or the `LuaFile`.
  - Correctness is not *created* here — `LuaLabelReference.isReferenceTo` (`LuaLabelReference.kt:50-56`)
    already re-resolves every candidate, so a wider scope was correct and merely expensive. The
    override removes work; it must not be relied on to remove wrong answers.
  - **This reaches the rename search, not only Find Usages.** `RenameUtil.processUsages`
    (`RenameUtil.java:118-134`) opens with
    `SearchScope useScope = PsiSearchHelper.getInstance(project).getUseScope(element)` and then —
    `RenameUtil.java:129-131` — intersects it with the refactoring scope **only when it is not a
    `LocalSearchScope`**, before passing it to `findReferences`. `PsiSearchHelperImpl.getUseScope`
    delegates straight to `element.getUseScope()` (`PsiSearchHelperImpl.java:143-146`). So a
    `LocalSearchScope` returned here **replaces** `RenameProcessor`'s default
    `GlobalSearchScope.projectScope` (`RenameProcessor.java:104`, `:324-325`) outright rather than
    narrowing it — which is why Risk 1.4 in `risks-and-gaps.md` is about a scope that is too *narrow*,
    with no intersection to fall back on.
  - Two further consumers pick it up for free: `ReferencesSearch.search(labelName)` with no explicit
    scope (`LuaFindUsagesTest.kt:107`) and `LuaSafeDeleteProcessor.findUsages`, which searches
    `searchTarget.useScope` (`refactoring/LuaSafeDeleteProcessor.kt:86-89`).
- **Complexity**: a parent walk bounded by nesting depth, on every `getUseScope` call. Not cached:
  the walk is a handful of `getParent` hops, and `CachedValuesManager` on a per-element scope would
  retain a `SearchScope` holding a hard `PsiElement` reference for the life of the cache — the
  opposite of what the engineering contract's retention rule wants.

### 3.6 What is deliberately not overridden

Each row is a hook `LuaLabelRenameProcessor` could implement and does not. The omission is the
design.

| Hook | Why not |
| :--- | :--- |
| `findReferences` | The base is `ReferencesSearch.search(element, searchScope)` (`RenamePsiElementProcessorBase.java:96-100`). With §3.5's scope it already searches exactly the right region, and `LuaLabelReference.isReferenceTo` already filters by binding. `REFACT-04-02`, `-05`, `-17` are all satisfied by it. |
| `renameElement` | The base is `RenameUtil.doRenameGenericNamedElement` (`:77-82`), which calls `setName` on the `PsiNameIdentifierOwner` and `handleElementRename` on each reference — both of which Lunar implements correctly today (`LuaBaseElements.kt:61-70`, `LuaLabelReference.kt:45-48`). Overriding it would replace the one working rename in the plugin with new code. |
| `isInplaceRenameSupported` | Base returns `true` (`:149-151`), which is the precondition `MemberInplaceRenameHandler.doRename` checks (`MemberInplaceRenameHandler.java:57-58`). Overriding it would disable `REFACT-04-09`. |
| `prepareRenaming` | A label rename never drags a second element along. Leaving it empty keeps `allRenames` a singleton, which §3.3 relies on. |
| `findExistingNameConflicts` | Runs on the EDT from `RenameProcessor.preprocessUsages` (`RenameProcessor.java:166-171`). §3.3's work belongs in the background read action. §9 Alternative C. |
| `getQualifiedNameAfterRename` / `getElementToSearchInStringsAndComments` | Two independent reasons, and the second is the load-bearing one. (a) The checkbox starts **off**: `RenameDialog` sets it from `RenamePsiElementProcessor.forElement(element).isToSearchInComments(element)` (`RenameDialog.java:93-94`, `:139-140`), whose base returns false for a non-`PsiFileSystemItem` (`RenamePsiElementProcessorBase.java:195-197`) — the `setSelected(true)` at `RenameDialog.java:281` is the widget's construction default and is overwritten at `:94`. (b) Even with the user turning it on, a label is safe where REFACT-01's target is not: `RenameUtil.getStringToReplace` falls through the null `getQualifiedNameAfterRename` to `psiElement instanceof PsiNamedElement → return newName` (`RenameUtil.java:209-228`, the branch at `:222-223`). `LuaLabelName` **is** a `PsiNamedElement`; REFACT-01's IDENTIFIER leaf is not, which is why it must override the hook (REFACT-01 §2.9) and this feature must not. A label spelled in a comment is not a reference to it, so opting in would only offer to rewrite prose. |
| `createRenameDialog` | The default `RenameDialog` already titles itself *Rename label 'done'* because `LuaFindUsagesProvider.getType` returns `"label"` and `getDescriptiveName` the identifier text (`lang/insight/LuaFindUsagesProvider.kt:70-75`, `:89`) — that is `REFACT-04-10`, already `Full`. **One behavioural delta rides in with §2.2's superclass and is stated here so the whitelist is not read as wrong**: today a label reaches `RenamePsiElementProcessorBase.DEFAULT`, whose `createDialog` (`RenamePsiElementProcessorBase.java:64-75`) first walks the `renameRefactoringDialogProvider` EP chain and only then falls back to `RefactoringUiService.createRenameRefactoringDialog`; `RenamePsiElementProcessor` **overrides** `createDialog` (`RenamePsiElementProcessor.java:22-28`) to call its own `createRenameDialog` (`:15-20`) = `new RenameDialog(...)` directly, so adopting that superclass skips the chain. The dialog the user sees is unchanged: `RenameRefactoringDialogProvider` has **no implementation** — `grep -rn RenameRefactoringDialogProvider ~/Documents/src/lua/intellij-community` returns exactly three hits, the abstract class, its EP declaration (`platform/refactoring/resources/META-INF/RefactoringExtensionPoints.xml:13`) and the one consumer above — and the fallback `RefactoringUiServiceImpl.createRenameRefactoringDialog` (`RefactoringUiServiceImpl.java:42-47`) returns the same `new RenameDialog(project, element, context, editor)`. This is not asserted headlessly; it is a *bypass of an empty chain*, and if a third-party plugin ever contributes to that EP for Lua, this row is where the divergence is recorded. |

## 4. External Data & Parsing

**None.** This feature consumes no CLI output, no file formats, no network responses and no text
Lunar did not produce. Its only inputs are PSI and one enum from `LuaProjectSettings`. The Lua
interpreter output quoted in §1 and in `requirements.md` is *evidence gathered while planning*, not
data the feature parses at runtime.

## 5. Data Flow

### Example 1 — the rename that works today, unchanged (REFACT-04-02)

```
::myLabel::            caret here, Shift+F6 → "newLabel"
goto myLabel
do goto myLabel end
```

1. `MemberInplaceRenameHandler.isAvailable` → true (`LuaLabelName` is a `PsiNameIdentifierOwner`;
   `isMemberInplaceRenameAvailable` returns true) → `MemberInplaceRenamer`.
2. On commit, `performRenameInner` runs a `RenameProcessor` (`MemberInplaceRenamer.java:309-317`).
3. `RenameUtil.findUsages` → `findReferences` → `ReferencesSearch` over the new
   `LocalSearchScope(luaFile)` (§3.5) → default searcher → two `LuaLabelReference`s, both
   `isReferenceTo` true.
4. `findCollisions` → `LuaLabelConflictDetector.collisions` → no other label named `newLabel` → empty.
5. `renameElement` → `setName` on the declaration, `handleElementRename` on both `goto`s.

Net change versus today: step 3's scope is a file instead of a module; step 4 does work and finds
nothing.

### Example 2 — the collision that is silently applied today (REFACT-04-07, -08)

```lua
local n = 0
::a::
n = n + 1
do
  if n < 2 then goto a end
  ::b::                      -- caret here, rename to "a"
end
print("n="..n)
```

1. Steps 1-3 as above; one usage found (`goto a` is a reference to the *outer* `a`, not to `b`, so it
   is not in `b`'s usage set at all).
2. `findCollisions`: `fn(b) === fn(a)` (both the file); `block(a)` is the file-level block, which
   encloses `block(b)` (the `do` block); `before(a, b)` holds. §3.2 clause 2 first bullet → collide.
3. At `LUA54`/`LUA55`: message `refactoring.rename.label.conflict.duplicate` naming line 2.
   At `LUA52`/`LUA53`: `…conflict.rebind`, naming the same line.
4. `RenameProcessor.preprocessUsages` turns it into a conflicts-dialog entry anchored on the *outer*
   `::a::` and removes it from the usage set (`RenameUtil.java:309-315`, `:297-307`). In a test it
   throws `ConflictsInTestsException`.

Executed cost of the current behaviour, from `requirements.md`: on 5.3.6 the program's output changes
from `n=2` to `n=1`; on 5.4.7 the file no longer loads.

### Example 3 — the legal rename the requirement's rule would have rejected (REFACT-04-07 negative)

```lua
do ::a:: end
::b::                        -- caret here, rename to "a"
```

`fn` matches. `block(a)` is the `do` block, `block(b)` the file block. `block(b)` encloses `block(a)`,
but `before(b, a)` is false; `block(a)` does not enclose `block(b)`. Neither bullet holds → **no
conflict**, and the rename proceeds. Executed (P-b): the result is legal on 5.2.4, 5.3.6 and 5.4.7.

## 6. Edge Cases

- **E-1 — the overlap window with `LuaUnsupportedRenameProcessor`.** Until REFACT-01 Phase 2 deletes
  it, `LuaUnsupportedRenameProcessor.canProcessElement` claims `LuaLabelRef` (it excludes
  `LuaLabelName` but not `LuaLabelRef`, `LuaUnsupportedRenameProcessor.kt:37-41`), so whichever of the
  two is registered first wins for an unresolved `goto`. Both outcomes are a refusal with a message,
  so the window is benign; it is named because a test asserting *which* message appears would be
  order-dependent and must not be written. TC-04-N asserts the refusal by calling
  `LuaLabelRenameProcessor` directly, which is order-independent.
- **E-2 — renaming to the same name.** §3.3 step 2 returns early. Without it a label would collide
  with itself through the same-block branch of §3.2.
- **E-3 — two labels of the new name, both colliding.** One `LuaRenameCollisionUsageInfo` per
  colliding label; the conflicts dialog lists them all. No de-duplication by name.
- **E-4 — a label that is not a direct statement of a `LuaBlock`.** `blockOf` uses
  `PsiTreeUtil.getParentOfType(label, LuaBlock::class.java)`, which finds the nearest block ancestor
  even across an `ERROR_ELEMENT`, whereas `LuaBlock.processLabelDeclarations` iterates
  `statementList` and would not see it (`LuaBlockExt.kt:87-91`). In that case the detector can report
  a conflict the resolver does not model. Accepted: a false conflict on unparseable code is a dialog
  the user dismisses, and the alternative (skipping the check whenever the file has an error) would
  disable the feature exactly when the file is most likely to be broken.
- **E-5 — `goto` inside a nested function referring to an outer label.** Not resolvable
  (`REFACT-04-04`, executed: *"no visible label 'out'"*), so it is not in the usage set and is never
  rewritten. §3.3's `functionScopeOf` filter keeps it out of the conflict scan too.
- **E-6 — a label with no `goto`** (`REFACT-04-17`). Zero usages; the rename applies to the
  declaration alone. The conflict scan is unaffected — it looks at declarations, not usages.
- **E-7 — the new name is a reserved word.** Rejected before `findCollisions` ever runs, by
  `LuaNamesValidator` through `RenameUtil.isValidName` (`RenameUtil.java:383-406`), on both the dialog
  and in-place paths (`RenameDialog.java:420-423`, `MemberInplaceRenamer.java:166-169`).
  `REFACT-04-06`; owned by [[REFACT-05]].
- **E-8 — rename invoked at 5.1.** Offered (`REFACT-04-16`), conflict-checked with the `rebind`
  message (§3.4). The file is already flagged by `LuaLanguageLevelInspection`; rename is a cleanup
  path and is not gated.
- **E-9 — renaming a label onto the name of a *dangling* `goto`.** `::b::` renamed to `a` while a
  `goto a` with no visible label exists in the same function: after the rename that `goto` binds,
  where before it did not. **Not reported**, deliberately. The prior state does not compile on any
  version — executed here on 2026-08-22, `goto a` alone in a file gives
  `no visible label 'a' for <goto> at line 1` on 5.2.4, 5.3.6 and 5.4.7 (row **P-f**) — so there is no
  working behaviour to
  preserve and no rebinding to warn about — the dangling `goto` is not in `b`'s usage set and never
  was. §3.3 compares declarations to declarations for exactly this reason: a rule keyed on `goto`
  text would fire here and would be wrong.
- **E-10 — `allRenames` is ignored.** `prepareRenaming` is not overridden (§3.6), so the map is always
  the singleton `{label → newName}`. §3.3 reads `element` and `newName` only; a future override of
  `prepareRenaming` would have to revisit this.

## 7. Integration Points

### plugin.xml

One extension is added, inside the existing `<extensions defaultExtensionNs="com.intellij">` block,
immediately after the existing `renamePsiElementProcessor` line (currently `plugin.xml:389-390`):

```xml
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaLabelRenameProcessor"/>
```

- **No `order` attribute.** §3.0 rule 2: the predicate is disjoint from every other registered
  processor's, so ordering is not part of the contract and must not become part of it.
- **No other extension point.** No `renameInputValidator` (§9 Alternative B), no `renameHandler`, no
  `elementManipulator`, no `lang.refactoringSupport` change — `LuaRefactoringSupportProvider` is
  already registered (`plugin.xml:384-386`) and already correct for `REFACT-04-09`.
- `lang.namesValidator` (`plugin.xml:393-395`) and `lang.findUsagesProvider` (`plugin.xml:375-377`)
  are unchanged and unmoved.

### LuaBundle.properties

Add to `src/main/resources/net/internetisalie/lunar/LuaBundle.properties` (the file `LuaBundle.kt:18`
names):

```properties
refactoring.rename.label.conflict.duplicate=Renaming this label to ''{0}'' duplicates the label declared on line {1}. Lua 5.4 and later reject the file with "label ''{0}'' already defined"; this project is configured for {2}.
refactoring.rename.label.conflict.rebind=Renaming this label to ''{0}'' makes it shadow the label declared on line {1}: any ''goto {0}'' between them will jump to the nearer label instead, changing what the program does. This project is configured for {2}, which allows it.
refactoring.rename.label.unresolvedGoto=''goto {0}'' does not refer to any label visible from here, so there is nothing to rename.
```

- Three keys, no key removed. `refactoring.rename.unsupported` (`LuaBundle.properties:145`) belongs
  to REFACT-01 Phase 2 and is not touched here.
- `''` is the `MessageFormat` escape for a literal apostrophe and is required in every message that
  takes parameters — the existing keys in this file use the same convention.

### Existing subsystems touched

| Subsystem | Interaction | Regression gate |
| :--- | :--- | :--- |
| Label resolution & completion | `walkLabelScopes` moves to `LuaLabelScopes` (§2.5). Pure move. | `LuaLabelResolutionTest` (5 tests), `LuaLabelCompletionTest` (4 tests) |
| Find Usages on labels | `getUseScope` narrows the default scope of `ReferencesSearch.search(labelName)` | `LuaFindUsagesTest.testLabelUsagesCount` (`:99-109`), `.testCanFindUsagesForLabel` (`:54-59`) |
| Safe Delete on labels | Same `useScope` narrowing reaches `LuaSafeDeleteProcessor.findUsages` (`:86-89`) | `LuaSafeDeleteTest.testLabelDeclarationIsAvailable` (`:156-170`) |
| Structure View | `getValue()` changes type from a leaf to a `LuaLabelName` (§2.6) | `LuaStructureViewTest.testLabelNodeLeafPresentation` (`:139-148`) and the `isAlwaysLeaf` test (`:186-192`), neither of which reads `value` for a label |
| Rename (all other symbols) | None. `canProcessElement` claims two label-only types. | `LuaUnsupportedRenameProcessorTest` while it exists; `LuaRenameTest` after REFACT-01 |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
| :--- | :---: | :--- |
| `REFACT-04-00` | M | **Delegated** — REFACT-01 implementation-plan Phase 2 (`implementation-plan.md:196-206`) already corrects `docs/features/refactoring/requirements.md:36`. §1 "What REFACT-01 owns"; see implementation-plan "Delegated work". |
| `REFACT-04-01` | M | Already `Full` — `LuaBaseElements.kt:51-71` + `lua.bnf:251-254`. Locked by TC-04-M and the existing `LuaLabelRenameTest.testNameIdentifierOwner`. |
| `REFACT-04-02` | M | Already `Full`, inherited (§3.6 row 1-2); scope from §3.5. New coverage: TC-04-A. |
| `REFACT-04-03` | M | Already `Full`; §3.1 makes the usage-site path explicit rather than incidental: step 3 for the resolvable `goto` (TC-04-N part **a**), step 4 for the dangling one (part **b**). |
| `REFACT-04-04` | M | §2.1 `isFunctionBoundary` / `functionScopeOf`; §3.3's scope filter. TC-04-H, plus existing `LuaLabelRenameTest.testScopeIsolatedRename`. |
| `REFACT-04-05` | S | Inherited `findReferences` + `LuaLabelReference.isReferenceTo` (§3.6 row 1). TC-04-C. |
| `REFACT-04-06` | M | `LuaNamesValidator`, reached per §6 E-7. TC-04-O. |
| `REFACT-04-07` | M | §2.2, §2.3, §3.2, §3.3. TC-04-D, TC-04-F, TC-04-G, TC-04-H, TC-04-L. |
| `REFACT-04-08` | S | §3.4. TC-04-D (5.4 tier), TC-04-E (5.3 tier). |
| `REFACT-04-09` | S | No code change — `LuaRefactoringSupportProvider.kt:23-26` is already correct. TC-04-M asserts both headlessly observable conjuncts; **DR-02** is the live check. |
| `REFACT-04-10` | C | Already `Full` — `LuaFindUsagesProvider.getType` (`:70-75`). Unchanged; `LuaFindUsagesTest.testCanFindUsagesForLabel` is the gate. |
| `REFACT-04-11` | S | §2.4, §3.5. TC-04-I, TC-04-J. |
| `REFACT-04-12` | S | Delegated to [[NAV-02]]. Unchanged except for the scope narrowing of §3.5, gated by `LuaFindUsagesTest.testLabelUsagesCount`. |
| `REFACT-04-13` | S | **Delegated to [[BUG-458]].** No code here. Cross-dependency: `risks-and-gaps.md` Gap 2.3. |
| `REFACT-04-14` | C | §2.6. TC-04-K; **DR-03** for the live F2. |
| `REFACT-04-15` | C | Platform-supplied; §3.6 row 2 records that `renameElement` is not overridden, which is what preserves it. No test — see `risks-and-gaps.md` "Test Case Gaps". |
| `REFACT-04-16` | C | Decision, not code: §3.4 rule 2 and §6 E-8 record why rename stays offered at 5.1 and which message it uses. |
| `REFACT-04-17` | C | Inherited. TC-04-B. |
| `REFACT-04-18` | W | Out of scope by decision; nothing implemented. Recorded in `risks-and-gaps.md` "Technical Debt & Future Work". |
| `REFACT-04-19` | W | Out of scope by language fact. §3.5 is the operational consequence: the scope is a *function*, so cross-file is unreachable by construction. |
| `REFACT-04-20` | S | Guaranteed by construction; §3.2 "Why nothing else can be broken by a rename" states the argument so no implementer builds machinery for it. |

## 9. Alternatives Considered

**A. Route labels through REFACT-01's `LuaRenameProcessor`.** Rejected, and REFACT-01 rejects it from
its side too (design §3.0 rule 1). `LuaRenameProcessor` normalises its target to a declaration
IDENTIFIER *leaf* and overrides `renameElement` to do an AST swap, because for every non-label Lua
name there is no `PsiNamedElement` to hand the platform. A label has one. Sending it down that path
replaces `setName` + `handleElementRename` — the only rename code in this plugin with a passing
end-to-end test — with code written for the opposite shape, and REFACT-01 §3.0 shows the failure is
silent rather than loud: `kindOf` of a label's IDENTIFIER child is null, so the rename would proceed
against an unspecified key.

**B. `renameInputValidator` for the 5.4+ blocking tier.** Rejected. It is the only hook in the
pipeline that can *prevent* a rename rather than warn about it, and it reaches both surfaces
(`RenameDialog.areButtonsValid` → `RenameUtil.isValidName`, `RenameDialog.java:420-423`;
`MemberInplaceRenamer.isIdentifier`, `MemberInplaceRenamer.java:166-169`). It loses on the message:
`RenameInputValidator.isInputValid` returning false produces
`LangBundle.message("dialog.message.valid.identifier", newName)` = *"'a' is not a valid identifier"*
(`LangBundle.properties:235`), which is false — `a` is a perfectly good identifier — and the only way
to supply a real message, `RenameInputValidatorEx.getErrorMessage(newName, project)`
(`RenameInputValidatorEx.java:23-24`), is handed **no element**, so it cannot name the colliding
label, its line, or the language level. Making it work would mean caching the element on an
application-level extension instance between `getPattern` and `getErrorMessage`
(`RenameInputValidatorRegistry.java:35-46`) — a hard PSI reference on a singleton, which the
engineering contract's retention rule forbids outright. A truthful warning beats a lying block.

**C. `findExistingNameConflicts` instead of `findCollisions`.** Rejected for the reason REFACT-01
rejects it (its §9 Alternative D): `findExistingNameConflicts` is called from
`RenameProcessor.preprocessUsages` (`RenameProcessor.java:166-171`) on the EDT, while `findCollisions`
is called from `RenameUtil.findUsages` (`RenameUtil.java:103`) inside
`ReadAction.computeBlocking` (`BaseRefactoringProcessor.java:303`). Both end in the same conflicts
dialog, so there is nothing to gain and a threading rule to break.

**D. Add the check to `LuaLanguageLevelInspection` instead of the rename.** Rejected. An inspection
reports a duplicate label *after* the file has been broken; the requirement is that the refactoring
does not break it. The two are complementary and an inspection for duplicate labels is a reasonable
separate feature — recorded as future work in `risks-and-gaps.md`, not folded in here.

**E. Keep `walkLabelScopes` private to `LuaLabelReference` and re-derive the boundary in the
detector.** Rejected. Two copies of `REFACT-04-04` would drift, and the drift is invisible: a
detector that stops at a different boundary than the resolver reports conflicts for labels that
cannot collide, or misses ones that can. §2.1 makes it one function with one test surface.

## 10. Open Questions

_None — feature has cleared the planning bar._
