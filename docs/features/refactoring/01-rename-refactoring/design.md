---
id: "REFACT-01-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "REFACT-01"
priority: "medium"
folders:
  - "[[features/refactoring/01-rename-refactoring/requirements|requirements]]"
---

# Technical Design: REFACT-01 — Rename Refactoring

## 1. Architecture Overview

### Current State

Rename is **not implemented** for anything but `::labels::`, and the platform does not refuse it —
it silently half-applies it.

| Fact | Evidence |
| :--- | :--- |
| No rename processor for identifiers | `LuaUnsupportedRenameProcessor` is the only `renamePsiElementProcessor` in `plugin.xml:389-390`, and it exists to **refuse** |
| `LuaNameReference.handleElementRename` does not exist | `LuaNameReference.kt` overrides `multiResolve`, `doMultiResolve`, `resolve`, `isReferenceTo`, `getVariants` only |
| The only `handleElementRename` is on labels | `LuaLabelReference.kt:45-48` |
| No `elementManipulator` is registered | `grep -n elementManipulator src/main/resources/META-INF/*.xml` → empty |
| The platform lets it through | `PsiElementRenameHandler.getRenameErrorMessage` admits any `PsiNamedElement`; `LuaNameRefElement : PsiNamedElement` (`lang/psi/LuaBaseElements.kt:75`) |

Measured live in a sandbox IDE on 2026-08-22 (recorded in `LuaUnsupportedRenameProcessor`'s KDoc):
caret on `local counter = 0`, Shift+F6 → `total` renamed the declaration, left **all four** usages
bound to `counter`, and reported success with no warning.

The reason usages are lost is a shape mismatch, not a missing search engine:

- `LuaNameReference` hangs off the **`LuaNameRef` composite**, not the IDENTIFIER leaf
  (`LuaBaseElements.kt:95-106`, `LuaNameRefBaseImpl.getReference`).
- `LuaNameReferenceSearcher.processQuery` returns early unless
  `target.elementType == LuaElementTypes.IDENTIFIER` (`LuaNameReferenceSearcher.kt:84-88`, `isNameDeclarationLeaf`,
  called from the guard at line 45).
- `TargetElementUtilBase.doFindTargetElement` hands rename the **composite** on a declaration site
  (`ELEMENT_NAME_ACCEPTED` → `getNamedElement` → nearest `PsiNamedElement` = `LuaNameRef`) and the
  **leaf** on a usage site (`REFERENCED_ELEMENT_ACCEPTED` → `LuaNameReference.resolve()`, whose
  Phase-1 result is `LuaScopeProcessor.result`, an IDENTIFIER leaf).

So the search substrate is correct and tested; rename is missing its **processor** and its
**normalisation** between those two shapes.

### The repo constraint that shapes the whole design

There is **no declaration PSI**. `LuaLabelName` is the only `LuaNameDeclElement`/
`PsiNameIdentifierOwner` in the plugin (`lua.bnf:251`, `LuaBaseElements.kt:51-71`). Every other name
— local, parameter, `for` variable, function name, method segment, global — is a `nameRef`
(`lua.bnf:169`) recognised as a *declaration* only by its parent container. The numeric-`for`
variable is not even that: `numericForStatement ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) gives a
bare IDENTIFIER leaf whose parent is the statement, with **no `LuaNameRef` and therefore no
`PsiNamedElement` anywhere**.

Consequence, and the single most important decision in this design:

> **The canonical rename target is the declaration IDENTIFIER leaf, not a `PsiNamedElement`.**
> That is the shape `LuaFindUsagesProvider.canFindUsagesFor`, `LuaNameReferenceSearcher` and
> `LuaSafeDeleteProcessor.findUsages` already key on, and it is the only shape every declaration
> kind in §3.5 shares. Because the leaf is not a `PsiNamedElement`, the default
> `RenameUtilBase.doRenameGenericNamedElement` would `LOG.error("Unknown element type")` — so
> `LuaRenameProcessor` **overrides `renameElement`** (§2.2, §3.3).

### Premises examined

Each constraint this design treats as fixed, and whether it actually is:

| Premise | Fixed? |
| :--- | :--- |
| There is no declaration PSI; a declaration is a `LuaNameRef` in a container | **Fixed for REFACT-01.** Changing it means editing `lua.bnf` and regenerating the parser, which moves every consumer of `LuaNameRef`. Recorded as the constraint the whole design is built around, not assumed away. |
| The rename target must be the IDENTIFIER **leaf** | **Chosen, not inherited.** The alternative (`LuaNameRef` composite) is examined and rejected in §9 Alternative A — it cannot express the numeric-`for` variable, which has no composite at all. |
| A `PsiReferenceBase` rename needs a registered `elementManipulator` | **NOT fixed — removed.** `getRangeInElement` is supplied by both contributors, so the manipulator is reachable only from `handleElementRename`/`bindToElement`, and overriding the former removes the need. §2.5, §2.7, §9 Alternative B, `risks-and-gaps.md` RD-1. |
| Conflict detection needs a nearest-declaration scope model | **NOT fixed — removed.** Restating the rules as "is any `newName` visible here" and "is the renamed declaration visible from this foreign reference" makes both a single existing `LuaScopeProcessor` crawl, and deletes a high-blast-radius refactor of core resolution from the plan. §9 Alternative C. |
| The platform persists the "search in comments" checkbox for us | **NOT true.** The base returns a hard `false` for non-file elements and there is no `…_FOR_VARIABLE` setting to delegate to. §2.9, `risks-and-gaps.md` RD-3. |
| `LuaSafeDeleteProcessor`'s normalisation helpers are private and should be copied | **Rejected on principle.** They are moved, not duplicated — copying a convention copies its defects, and both helpers already carry the `LuaFuncNameProperty` gap this feature has to fix. §1 Prior Art, §3.5. |
| Conflicts belong in `findExistingNameConflicts` (the hook the requirement names) | **NOT fixed.** That hook runs on the EDT; `findCollisions` runs inside the background read action. §9 Alternative D. |
| `LuaGlobalAssignmentIndex`'s coverage is a given, so Lua 5.5 `global` declarations are simply not cross-file resolvable | **NOT fixed — removed.** The index omits `LuaGlobalVarDecl`/`LuaGlobalFuncDecl` (`LuaGlobalAssignmentIndex.kt:95-107`), which would have made a `global` rename half-apply across files. Extending it is ~15 lines and a version bump; §2.10. Designing *around* the omission would have been machinery built to survive something not happening. |
| Renaming `obj:method()` call sites is in scope because the requirement lists it | **Deliberately narrowed.** Resolving them needs receiver-type inference; refusing loudly is correct and cheap. §3.1 step 4, `risks-and-gaps.md` RD-2 / DR-03. |

### Evidence class of the behavioural claims

Distinguishing what was *executed* from what was *read*, because grep settles existence and not
behaviour:

| Claim | Evidence |
| :--- | :--- |
| Rename on a local renames the declaration and leaves all usages bound to the old name | **Executed** — sandbox IDE, 2026-08-22, `local counter = 0` + 4 usages → 1 renamed, 4 left, success reported. Recorded in `LuaUnsupportedRenameProcessor`'s KDoc. |
| `LuaNameReferenceSearcher` returns nothing for a `LuaNameRef` composite | **Read** — the early `return` in `isNameDeclarationLeaf` is unconditional on element type. Low risk; TC-22/TC-23 will execute it. |
| `myFixture.renameElementAtCaret` drives `substituteElementToRename` then `RenameProcessor` | **Read** — `CodeInsightTestFixtureImpl.java:1092-1107`. Every TC depends on it, so **DR-01** executes it against the current tree before any test is written. |
| `RenameUtilBase.rename` calls `ref.handleElementRename(newName)` for non-bindable references | **Read** — `RenameUtilBase.java:44-50, 90-95`. Executed transitively by TC-01 the moment Phase 2 lands. |
| `RenamePsiFileProcessor` passes the new file name **with** its extension | **Read** — it does not override `renameElement`, so the base passes `newName` verbatim. TC-18a is the executing check, and it fails loudly (`require("helpers.lua")`) if this is wrong. |
| Caret-on-`self` resolves to the **method-name** leaf `m` of `function T:m()`, not to the class `T` | **Read, from three sources that agree** — `LuaScopeProcessor.kt:87-93` assigns `funcName.funcNameMethod!!.nameRef.identifier`; `lua.bnf:164` and `:166` put the method name (not the receiver) behind `funcNameMethod`; `LuaFuncNameMethod.java:8-11` exposes exactly one `getNameRef()`. **TC-19a executes it**: part (a) asserts the element the platform actually hands the processor is the `m` leaf under a `LuaFuncNameMethod`, and the plan's mutation-proof list requires flipping that expectation to `"Obj"` and seeing it go red. An earlier draft of this design asserted the opposite — "the receiver/class leaf" — and no artifact could have contradicted it, because no test looked at the resolved target. |
| `global x = 1` classified by the `LuaAttName` row alone becomes `LOCAL_VARIABLE` and its search is narrowed to one file | **Read** — `lua.bnf:217` + the `private attNameList` rule at `:242` put the `attName` directly under `LuaGlobalVarDecl` (`LuaGlobalVarDecl.java:10-13`), and §3.2 step 2 narrows on `isFileLocal`. **TC-28 executes it**: it asserts the cross-file usage is rewritten, which a narrowed scope cannot do. |
| `LeafElement.replaceWithText` is a legal edit inside a `LuaCatsLazyCommentImpl` | **Read only, and the one claim with no precedent in this repo** — every other AST edit here reuses `node.replaceChild`, which is used in production. **DR-06** executes it before Phase 6. |
| Safe Delete drops the delegate for an elevated node no delegate `handlesElement`, and searches no usages at all when that node is not a `PsiNamedElement` | **Read, from three sources that agree** — `SafeDeleteProcessor.java:138-166` (the delegate loop, then `if (!handled && element instanceof PsiNamedElement)`); `LuaStatement.java:8` + `LuaStatementImpl` → `LuaBaseElement` → `ASTWrapperPsiElement` (`LuaBaseElements.kt:29-31`), so none of the three new elevation nodes qualifies; and `LuaSafeDeleteProcessor.kt:46-52`, whose KDoc states the outcome verbatim from when REFACT-03 hit it. **TC-32 executes it** — with the enumerated `isElevatedDeclaration` restored it must fail on a *silent delete*, not on a text assertion (plan's mutation-proof list). |
| The non-code *replacement* string is derived from the renamed leaf and hits `LOG.error("Unknown element type")` unless `getQualifiedNameAfterRename` is overridden | **Read, and it is the defect the third Step 9 review found.** `RenameUtil.java:145-155` passes `element` (the leaf) to `getStringToReplace`; `:209-228` calls `getQualifiedNameAfterRename` first, whose base returns null (`RenamePsiElementProcessorBase.java:106-108`), then tests `instanceof PsiNamedElement`, then logs. The null reaches `document.replaceString(…, newText)` at `RenameUtil.java:377`. **EXECUTED 2026-08-23** — TC-13d, driven with `searchInComments = true`. With the override deleted the test fails with `TestLoggerFactory$TestLoggerAssertionError: Unknown element type : PsiElement(LuaTokenType.IDENTIFIER)` from `RenameUtil`, so the plan's open question is settled in the direction that made the override Phase-2 work: `stringToSearch.isEmpty()` does **not** short-circuit, and the hazard is reachable in Phase 2 rather than Phase 7. |
| Caret on the RECEIVER `M` of `function M.run() end`, with no other declaration of `M`, is classified `GLOBAL_FUNCTION`, is not redirected by resolution, and finds zero usages | **Read, from four sources that agree** — §3.5 row 9 matches the `LuaFuncName` grandparent (`lua.bnf:164`); `LuaBlock.processDeclarations` has no `LuaFuncDecl` branch (`LuaBlockExt.kt:38-77`), so Phase 1 resolution never sees the enclosing declaration from outside its body; `getQualifiedName` returns null for a `LuaFuncName` parent (`LuaNameReference.kt:173-176`) and `LuaGlobalDeclarationIndex` keys the declaration under `funcName.text` = `"M.run"`; `isReferenceTo` is false on a null resolve (`:239`). **TC-34a executes it**, and the plan's mutation-proof list requires deleting §3.1 step 4a and seeing it go red on a *silent half-rename*, not on a message assertion. |
| C1-C4 behave on real PSI as §3.4 specifies | **Executed 2026-08-23**, before the detector was written, because four claims in this design have already been measured false. A throwaway probe ran `scopeCrawlUp`, `LuaDeclarationSite.kindOf`, `StubIndex` and `LuaGlobalAssignmentNavigation.find` over TC-14/15/16/17/31's fixtures. **All four survived.** The load-bearing readings: C1 finds `local y = 2` from `print(x)` and nothing from the declaration leaf, so *both* site sets are needed; C2's candidate in `do local y = 3 end` classifies as `LOCAL_VARIABLE` and crawls up to the renamed `x` with `identity=true`, so step 3's skip is the difference between TC-16 and a false conflict; `LuaGlobalAssignmentNavigation.find` returns the caret file's own leaf as the *identical* instance, so C4's `!== dLeaf` filter fires. Two rows of §3.4 were corrected by the same run — C3 step 1's anchor and C4 step 1's `mapNotNull`. |
| A shadowing inner `local x` is not collected as a usage of the outer `x` | **Was READ and was WRONG. Executed 2026-08-23 and it failed.** §6's "handled by resolution" row asserted this without a test; measured, renaming the outer `x` rewrote the inner DECLARATION and left its own usage behind. `scopeCrawlUp` excludes a reference's own declaring statement, so the inner declaration's name resolves outward and `isReferenceTo` matches. Closed by `LuaNameReference.shadowsRatherThanUses`; TC-03 executes it and is the ONLY test of 69 across rename / Find Usages / Safe Delete / shadowing that catches it. |
| Caret on the receiver `M` of `function M.run()` is REDIRECTED when `M = {}` exists | **Was READ and was WRONG. Executed 2026-08-23.** It is refused by step 4a instead — `resolve()` on the funcName's `M` is null regardless of `M = {}`. Safe (a refusal, not a half-rename), but §6 has been rewritten to say what happens rather than what was expected. |
| `canProcessElement` claims every Lua `LuaNameRef`, including a usage that resolves to nothing | **Executed 2026-08-23, after a first attempt that could not have detected it.** TC-26 as specified called `substituteElementToRename` directly, which bypasses `canProcessElement` entirely: narrowing the predicate to `kindOf(element) != null` left the whole suite green. TC-26 now asserts `RenamePsiElementProcessor.forElement(...)` and drives `renameElementAtCaret`, and the narrowing mutant reddens it. |
| The index's assignment rule and §3.5 row 14 are the same rule | **Read, and it was wrong the first time** — the previous draft claimed `LuaGlobalAssignmentIndex.Indexer.map:92-104` "verbatim"; the index is top-down over `topLevel.filterIsInstance<LuaAssignmentStatement>()` and only its file-scope-locals clause was genuinely shared. §2.10 change 0 makes them one rule in code; **DR-09** executes `LuaCrossFileGlobalResolutionTest` against the delegated form before it lands. |
| Clause 3's O(1) form is equivalent to the index's membership test | **Read** — `LuaPsiImplUtil.kt:67-68` and `LuaBlockImpl.java:34-36` are both direct-children-only. **DR-08** executes both forms on the same two fixtures and requires them to agree. |
| `getElementToSearchInStringsAndComments` returning the leaf makes the non-code search inert | **Read** — `RenamePsiElementProcessorBase.java:264-266` (default returns `element`), `RenameUtil.java:145-155` (the search string comes from *that* element), `DefaultNonCodeSearchElementDescriptionProvider.java:37-40` (only `PsiNamedElement` has a general branch), `ElementDescriptionUtil.java:26` (falls back to `toString()`). **TC-13c executes it** by asserting the description is exactly `"counter"`. |

### Prior Art in This Repo

| Component | file:line | This design |
| :--- | :--- | :--- |
| `LuaUnsupportedRenameProcessor` | `refactoring/rename/LuaUnsupportedRenameProcessor.kt` | **REPLACED.** Deleted, with its test, in Phase 2; its `plugin.xml` line is rewritten to point at `LuaRenameProcessor`. Both must never be registered at once — the first extension whose `canProcessElement` matches wins (`RenamePsiElementProcessorBase.forPsiElement`), so leaving it registered would keep refusing every rename. |
| `LuaFindUsagesProvider.canFindUsagesFor` / `getType` | `lang/insight/LuaFindUsagesProvider.kt:55-88` | **EXTENDED.** The `when` chain becomes a delegation to `LuaDeclarationSite.kindOf` (§2.1); `LuaFuncNameProperty` (`function M.run()`) is added as an accepted grandparent, which it is not today. |
| `LuaSafeDeleteProcessor.declarationNodeFor` / `identifierLeafFor` | `refactoring/LuaSafeDeleteProcessor.kt:156-171` and `:178-191` | **EXTENDED.** Both private helpers move verbatim into `LuaDeclarationSite` (§2.1); `LuaSafeDeleteProcessor` delegates. Rename needs the same normalisation and must not duplicate it. |
| `LuaNameReferenceSearcher` | `lang/insight/LuaNameReferenceSearcher.kt` | **EXTENDED.** `isNameDeclarationLeaf` (`:84-88`) is deleted and replaced by a normalising gate so a `LuaNameRef` declaration composite is searched as its leaf — required by in-place rename (§2.6), which hands the platform a `PsiNamedElement`, and by `LuaSafeDeleteProcessor.findUsages`' `?: element` fallback (`:86`). Specified in full, guard order included, in **§3.8**. |
| `LuaNameReference` | `lang/LuaNameReference.kt` | **EXTENDED.** Gains `handleElementRename` (§2.4); `declarationIdentifier` gains the `LuaFuncNameProperty` case it is missing (§3.5). |
| `LuaRequireReference` | `lang/LuaRequireReference.kt` | **EXTENDED.** Gains `handleElementRename` (§2.7). |
| `LuaRefactoringSupportProvider` | `lang/insight/LuaRefactoringSupportProvider.kt` | **EXTENDED.** `isInplaceRenameAvailable` stops returning a hard `false` (§2.6); its stale KDoc attributing label rename to REFACT-01 is corrected to REFACT-04. |
| `LuaNamesValidator` | `refactoring/LuaNamesValidator.kt`, registered `plugin.xml:393-395` | **REUSED UNCHANGED.** Owns REFACT-01-10; specified by REFACT-05. |
| `LuaNameSuggestionProvider` | `refactoring/rename/LuaNameSuggestionProvider.kt`, registered `plugin.xml:391-392` | **REUSED UNCHANGED.** Already feeds the rename dialog. |
| `LuaGlobalAssignmentIndex` | `lang/indexing/LuaGlobalAssignmentIndex.kt` | **EXTENDED.** Gains the Lua 5.5 `LuaGlobalVarDecl` / `LuaGlobalFuncDecl` declaration forms and a `getVersion()` bump 3→4 (§2.10). Its `fileScopeLocalNames`/`boundName` helpers **move** to `LuaDeclarationSite` and are called back, not copied (§2.1). |
| `LuaGlobalAssignmentNavigation` | `lang/navigation/LuaGlobalAssignmentNavigation.kt` | **EXTENDED.** `find` gains the two matching collectors so the re-resolve step returns the same leaves the index recorded (§2.10). `collectTargets` (`:38-48`) is unchanged. Also **reused unchanged** by §3.4 C3/C4. |
| `LuaScopeProcessor` + `LuaResolveUtil.scopeCrawlUp` | `lang/LuaScopeProcessor.kt`, `lang/psi/LuaResolveUtil.kt` | **REUSED UNCHANGED.** Conflict detection (§3.4) is expressed entirely as two existing scope crawls. No new scope model is introduced — see §9 Alternative C. |
| `LuaLabelReference.handleElementRename` | `lang/LuaLabelReference.kt:45-48` | **UNTOUCHED.** Labels keep the platform default processor; `LuaRenameProcessor.canProcessElement` excludes `LuaLabelName` and `LuaLabelRef`. |

### Shared machinery — the REFACT-04 boundary

REFACT-04 (Label Refactoring) is planned separately and **must reference, not duplicate**, these:

| Owned by REFACT-01 | REFACT-04 layers on it |
| :--- | :--- |
| `LuaDeclarationSite` / `LuaDeclarationKind` (§2.1) — `LABEL` is already a member | Add label-specific kinds/behaviour here, do not fork the enum |
| `LuaRenameCollisionUsageInfo` (§2.4) — the `UnresolvableCollisionUsageInfo` carrier + its bundle keys | Reuse verbatim; only the *detector* differs |
| The C1/C2/C3 conflict **shape** (§3.4): "collect candidate sites → run a scope crawl → anchor a collision on the colliding declaration, never on a usage that must still be rewritten" | Substitute `LuaLabelScopeProcessor` + `LuaBlock.processLabelDeclarations` for `LuaScopeProcessor` + `LuaResolveUtil.scopeCrawlUp`; the per-function scope stop is `LuaLabelReference.walkLabelScopes` |
| `LuaRenameProcessor` (§2.2) | **Not shared.** Labels are `PsiNameIdentifierOwner` and work through the platform default processor; REFACT-01 deliberately does not claim them |

### Target State

```
Shift+F6
  │
  ├─ VariableInplaceRenameHandler ──> LuaRefactoringSupportProvider.isInplaceRenameAvailable   §2.6
  │      (file-local locals only; hands the platform a LuaNameRef, a PsiNamedElement)
  │
  └─ PsiElementRenameHandler
         └─ LuaRenameProcessor                                                                 §2.2
              ├─ canProcessElement(el)          every Lua LuaNameRef + declaration leaf,
              │                                  never a label or a file                       §3.0
              ├─ substituteElementToRename(el)  normalise to the declaration IDENTIFIER leaf   §3.1
              │                                  … or refuse loudly: unresolved, colon method,
              │                                    or a funcName receiver segment (§3.1 step 4a)
              ├─ findReferences(el, scope, …)   ReferencesSearch, narrowed to the file
              │                                  for file-local kinds                          §3.2
              │                                  → LuaNameReferenceSearcher normalising gate   §3.8
              ├─ findCollisions(…)              LuaRenameConflictDetector  → collisions        §3.4
              └─ renameElement(el, new, usages) usages via LuaNameReference.handleElementRename §3.3
                                                declaration via AST leaf swap
                                                @param tag via LuaCatsParamRenamer             §3.6
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.LuaDeclarationSite` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt`
- **Responsibility**: the single classifier and normaliser for "what kind of Lua declaration is this
  element, and where is its IDENTIFIER leaf / whole-declaration node".
- **Threading**: pure PSI reads; callers hold read access. No state, no cached `Project`.
- **Collaborators**: `LuaAttName`, `LuaNameRef`, `LuaNameList`, `LuaParList`, `LuaLocalFuncDecl`,
  `LuaFuncName` (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaFuncName.java:8-18` declares
  `getNameRef()`, `getFuncNamePropertyList()`, `getFuncNameMethod()`),
  `LuaFuncNameMethod`, `LuaFuncNameProperty`, `LuaNumericForStatement`,
  `LuaGenericForStatement`, `LuaLocalVarDecl`, `LuaFuncDecl`, `LuaLabelName`, `LuaElementTypes`,
  and — for the global forms of §3.5 — `LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaVar`,
  `LuaVarList`, `LuaAssignmentStatement`, `LuaBlock`, `LuaFile`
  (all in `lang/psi/`, generated or hand-written — every one verified present:
  `src/main/gen/net/internetisalie/lunar/lang/psi/LuaGlobalFuncDecl.java:10-16` declares
  `@Nullable LuaNameRef getNameRef()`, `LuaGlobalVarDecl.java:10-13` declares
  `@NotNull List<LuaAttName> getAttNameList()`, `LuaVar.java:8-19` declares `getNameRef()` /
  `getVarSuffixList()`).

```kotlin
enum class LuaDeclarationKind(
    val usageViewType: String,
    val isFileLocal: Boolean,
) {
    LOCAL_VARIABLE("local variable", true),
    PARAMETER("parameter", true),
    NUMERIC_FOR_VARIABLE("local variable", true),
    GENERIC_FOR_VARIABLE("local variable", true),
    LOCAL_FUNCTION("local function", true),
    GLOBAL_VARIABLE("global variable", false),
    GLOBAL_FUNCTION("global function", false),
    DOTTED_FUNCTION("global function", false),
    METHOD_FUNCTION("global function", false),
    LABEL("label", true),
}

object LuaDeclarationSite {
    fun kindOf(element: PsiElement): LuaDeclarationKind?

    fun identifierLeafOf(element: PsiElement): PsiElement?

    fun declarationNodeOf(element: PsiElement): PsiElement

    /**
     * The IDENTIFIER leaf that names the function whose name chain is [funcName] — its LAST
     * segment: the `funcNameMethod` if present, else the last `funcNameProperty`, else the bare
     * `nameRef`. One rule, three callers: [identifierLeafOf] row 9, §3.1 step 4a's
     * receiver-segment guard, and
     * [net.internetisalie.lunar.lang.LuaNameReference.declarationIdentifier].
     */
    fun functionNameLeafOf(funcName: LuaFuncName): PsiElement

    /** §3.5 row 14, clauses 1-3. Pure shape; O(1); reads no name set. */
    fun isBareAssignmentTarget(target: LuaVar): Boolean

    /** §3.5 row 14, clauses 1-4. Uses the cached [fileScopeLocalNames]. */
    fun isGlobalAssignmentTarget(target: LuaVar): Boolean

    /**
     * Names bound by a file-scope `local` / `local function`, cached per file. Shared with
     * [net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex], which owns the identical
     * rule today (`LuaGlobalAssignmentIndex.kt:133-146`) and delegates here after Phase 1.
     */
    internal fun fileScopeLocalNames(file: LuaFile): Set<String>

    /**
     * The uncached body of [fileScopeLocalNames]. Published because
     * [net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex.Indexer.map] must call it
     * instead of [fileScopeLocalNames]: an indexer runs on the indexing thread over a
     * non-physical `FileContent` PSI copy, where a `CachedValuesManager` round trip buys nothing
     * (the file is discarded after the run) and is an unexecuted claim about thread safety this
     * design will not make. `map` already computes the set exactly once per file, so it needs the
     * plain function, not the cache.
     */
    internal fun computeFileScopeLocalNames(file: LuaFile): Set<String>

    /**
     * The bound name of a declaration, read through the AST node rather than the generated
     * `@NotNull` getter (SYNTAX-18). Published — not private — because
     * [net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex.Indexer.declaredGlobalName]
     * still calls it (`LuaGlobalAssignmentIndex.kt:123`) after `fileScopeLocalNames` moves here.
     * Leaving it private would leave a second copy in the index, which is the exact outcome the
     * move exists to prevent.
     */
    internal fun boundName(declaration: PsiElement): String?
}
```

- `kindOf` implements §3.5 exactly. `usageViewType` strings are copied verbatim from
  `LuaFindUsagesProvider.getType` so that provider's existing assertions in `LuaFindUsagesTest`
  (`"local variable"`, `"global function"`, `"parameter"`, `"label"`) keep passing unchanged.
- `declarationNodeOf` is `LuaSafeDeleteProcessor.declarationNodeFor` moved verbatim
  (`LuaSafeDeleteProcessor.kt:156-171`), extended with the `LuaFuncNameProperty`, `LuaGlobalVarDecl` and
  `LuaGlobalFuncDecl` branches (§3.5).
- **Tripwire compliance**: `kindOf` is a five-line dispatcher (§3.5 rows 1-4) delegating to exactly
  three private helpers, each one line per branch and each taking **one** argument:
  - `kindFromLeafParent(parent: PsiElement): LuaDeclarationKind?` — §3.5 row 3.
  - `kindFromNameRefGrandParent(grandParent: PsiElement): LuaDeclarationKind?` — §3.5 rows 5-13.
  - `kindFromAssignmentTarget(grandParent: LuaVar): LuaDeclarationKind?` — §3.5 row 14.

  So no function exceeds 30 logic lines and none takes more than one argument.
- **Threading of `fileScopeLocalNames` vs `computeFileScopeLocalNames`.** The cached accessor is for
  read-action/EDT callers (`kindOf` row 14 reaches it from `canProcessElement`). The indexer calls
  the uncached body: `LuaGlobalAssignmentIndex.Indexer.map` already computes the set once per file
  and runs on the indexing thread, so it gains nothing from the cache and must not be the first
  place in this repo to assert that `CachedValuesManager` is safe there.
- **`fileScopeLocalNames` is cached per file**, not recomputed per call: row 14 is reached on the EDT
  from `canProcessElement` (§3.0), and a fresh top-level pass per keystroke-scale invocation is
  exactly what the engineering contract's `CachedValuesManager` rule exists to prevent. The idiom is
  the repo's own `ControlFlowCache.getControlFlow` (`analysis/controlflow/ControlFlowCache.kt:7-12`):
  ```kotlin
  internal fun fileScopeLocalNames(file: LuaFile): Set<String> =
      CachedValuesManager.getCachedValue(file) {
          CachedValueProvider.Result.create(computeFileScopeLocalNames(file), file)
      }
  ```
  `computeFileScopeLocalNames` is `LuaGlobalAssignmentIndex.Indexer.fileScopeLocalNames`
  (`LuaGlobalAssignmentIndex.kt:133-146`) moved with **one** change, which is the whole of the
  difference: its parameter becomes `file: LuaFile` and it opens with
  `val topLevel = file.getBlockList().flatMap { it.statementList }` — the exact line `Indexer.map`
  computes at `LuaGlobalAssignmentIndex.kt:92` and passes in today. The body is otherwise
  untouched, including its node-based `boundName` read, which must **not** be "simplified" to the
  generated getter: `LuaLocalFuncDecl.getNameRef()` is declared `@NotNull` but returns null for a
  partially parsed decl and the platform logs an error, surfacing as a `TestLoggerAssertionError`
  (SYNTAX-18; the reason is recorded at `LuaGlobalAssignmentIndex.kt:126-132`). `boundName`
  (`:148-152`) moves with it and stays reachable from the index — see the API surface above.

### 2.2 `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt`
- **Responsibility**: the whole rename contract for Lua identifiers. Replaces
  `LuaUnsupportedRenameProcessor`.
- **Threading**: `canProcessElement` / `findReferences` / `findCollisions` run inside the platform's
  background read action (`BaseRefactoringProcessor` line 303,
  `refUsages.set(ReadAction.computeBlocking(this::findUsages))`); every scan loop calls
  `ProgressManager.checkCanceled()`. `substituteElementToRename` runs on the EDT: it performs **no
  project-wide PSI scan**, but §3.1 step 3 does call `LuaNameReference.resolve()`, whose Phase 2
  reads the stub and file-based indexes (`LuaNameReference.kt:113-162`). That is bounded to a single
  name, is `ResolveCache`-backed (`LuaNameReference.kt:37-40`), and is the same lookup Go-to-Declaration
  already performs from the EDT on this element — it is stated here rather than denied, because the
  earlier "no I/O" claim was false. `canProcessElement` (§3.0) does **not** resolve and must not.
  `renameElement` is called by `BaseRefactoringProcessor` "in a
  command, on EDT, inside a Write Action" (its own javadoc, line 272) — so it must **not** open a
  second `WriteCommandAction`.
- **Collaborators**: `LuaDeclarationSite`, `LuaRenameConflictDetector`, `LuaCatsParamRenamer`,
  `LuaElementFactory.createIdentifier`, `com.intellij.refactoring.rename.RenameUtil.rename`,
  `CommonRefactoringUtil.showErrorHint`, `LuaBundle.message`, and — for §3.1 step 4a —
  `com.intellij.psi.util.PsiTreeUtil.getParentOfType` with `net.internetisalie.lunar.lang.psi.LuaFuncName`.

Each override's behaviour is specified in §3: `canProcessElement` in **§3.0**,
`substituteElementToRename` in §3.1, `findReferences` in §3.2, `renameElement` in §3.3,
`findCollisions` in §3.4, and the six non-code-search accessors in §2.9. **One of those six,
`getQualifiedNameAfterRename`, is not a Phase 7 nicety and ships with the processor in Phase 2** —
without it a single click on the rename dialog's "Search in comments and strings" checkbox drives a
platform `LOG.error`. §2.9 states the chain.

```kotlin
class LuaRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean
    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement?
    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference>
    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    )
    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?,
    )
    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement?
    override fun getQualifiedNameAfterRename(
        element: PsiElement,
        newName: String,
        nonJava: Boolean,
    ): String
    override fun isToSearchInComments(element: PsiElement): Boolean
    override fun setToSearchInComments(element: PsiElement, enabled: Boolean)
    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean
    override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean)
}
```

### 2.3 `net.internetisalie.lunar.refactoring.rename.LuaRenameConflictDetector` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt`
- **Responsibility**: produce the Lua-shaped rename collisions of §3.4. Stateless object.
- **Threading**: background read action (called only from `findCollisions`);
  `ProgressManager.checkCanceled()` at the top of every loop body.
- **Collaborators**: `LuaScopeProcessor`, `LuaResolveUtil.scopeCrawlUp`, `LuaDeclarationSite`,
  `LuaGlobalDeclarationIndex.KEY` + `StubIndex`, `LuaGlobalAssignmentNavigation.find`,
  `PsiTreeUtil.findChildrenOfType`, `LuaNameRef`, `LuaBundle`.

```kotlin
internal data class LuaRenameTarget(
    val identifier: PsiElement,
    val kind: LuaDeclarationKind,
    val newName: String,
)

internal object LuaRenameConflictDetector {
    fun collisions(
        target: LuaRenameTarget,
        usages: List<UsageInfo>,
    ): List<LuaRenameCollisionUsageInfo>
}
```

Two arguments (the third parameter of the rule set is folded into `LuaRenameTarget`), honouring the
≤3-argument cap. Private helpers `captures(...)`, `shadows(...)`, `globalNameTaken(...)` each take
one `LuaRenameTarget` plus at most one more argument.

### 2.4 `net.internetisalie.lunar.refactoring.rename.LuaRenameCollisionUsageInfo` (new)

- **File**: same file as §2.3.
- **Responsibility**: carry a conflict message to the platform without becoming a rename target.

```kotlin
internal class LuaRenameCollisionUsageInfo(
    anchor: PsiElement,
    renamedDeclaration: PsiElement,
    private val message: String,
) : UnresolvableCollisionUsageInfo(anchor, renamedDeclaration) {
    override fun getDescription(): String = message
}
```

**Why `UnresolvableCollisionUsageInfo` and why the anchor is never a usage.**
`RenameProcessor.preprocessUsages` calls `RenameUtil.addConflictDescriptions` (which turns each
`UnresolvableCollisionUsageInfo` into a conflicts-dialog entry keyed on `usage.getElement()`) and
then `RenameUtil.removeConflictUsages`, which **deletes those infos from the usage set**
(`RenameProcessor.java:248-252`). Anchoring a collision on a usage that still needs rewriting would
therefore skip rewriting it when the user presses Continue — reproducing the exact silent-partial
defect this feature exists to remove. Every anchor produced by §3.4 is a *colliding declaration or a
foreign reference named `newName`*, never a member of the renamed symbol's own usage set.

### 2.5 `net.internetisalie.lunar.lang.LuaNameReference` (edit)

Add:

```kotlin
override fun handleElementRename(newElementName: String): PsiElement {
    val host = myElement as? PsiNamedElement ?: return myElement ?: error("no element")
    return host.setName(newElementName)
}
```

- **Why an override rather than a registered `elementManipulator`.** `PsiReferenceBase.getRangeInElement`
  returns the range supplied to the constructor, so the manipulator is reached only from
  `handleElementRename` / `bindToElement` (`PsiReferenceBase.java:103-105, 129-137`). The host is
  always a `LuaNameRef`, whose `setName` already performs the correct AST swap
  (`LuaBaseElements.kt:83-92`, `LuaNameRefElementImpl.setName` → `node.replaceChild`). Overriding is
  one line, needs no new extension point, and mirrors `LuaLabelReference.handleElementRename`
  (`LuaLabelReference.kt:45-48`), which has worked in production since REFACT-04.
- Also fix `declarationIdentifier` per §3.5 so dotted declarations resolve their own name segment.
- **Add `shadowsRatherThanUses`, consulted from `isReferenceTo`** (Phase 2, ratified at that phase's
  review — §6's "two locals of the same name in nested blocks" row carries the measurement and the
  reasoning). A **file-local** declaration site's own name introduces a new lexical binding and is
  never a usage of the binding it shadows; without it `scopeCrawlUp`'s deliberate exclusion of a
  reference's own declaring statement makes an inner `local x` resolve outward and be rewritten as a
  usage of the outer one. `isFileLocal` is the predicate because shadowing is lexical: a second
  *global* declaration site is the same `_ENV` variable and stays in the rename set, to be reported
  by §3.4 C4. This is shared machinery — `isReferenceTo` is also the oracle for `ReferencesSearch`,
  Find Usages and Safe Delete (`risks-and-gaps.md` Gap 2.12).

### 2.6 `net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider` (edit)

```kotlin
override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement): Boolean =
    element is LuaNameRef &&
        LuaDeclarationSite.kindOf(element.identifier)?.isFileLocal == true
```

- **Why the `LuaNameRef` gate is load-bearing, not cosmetic.**
  `VariableInplaceRenameHandler.createRenamer` does an unchecked
  `new VariableInplaceRenamer((PsiNamedElement) elementToRename, editor)`
  (`VariableInplaceRenameHandler.java:149-151`). If in-place rename were offered for the IDENTIFIER
  leaf handed back by the usage-site path, that cast throws `ClassCastException` inside the IDE.
  Only the declaration-site path yields a `LuaNameRef`, which *is* a `PsiNamedElement`.
- `isMemberInplaceRenameAvailable` keeps returning `elementToRename is LuaLabelName` (REFACT-04).
- `isSafeDeleteAvailable` switches from `LuaFindUsagesProvider().canFindUsagesFor(element)` (which
  allocates a provider per call) to `LuaDeclarationSite.kindOf(element) != null`. **This widens Safe
  Delete**, and §2.6a below is the *whole* of what that widening requires. It is not optional and it
  is not a later phase: `isSafeDeleteAvailable` and `canFindUsagesFor` are the same predicate today
  (`LuaRefactoringSupportProvider.kt:30` delegates straight to it), so Phase 1 widens Safe Delete at
  the moment it widens Find Usages, whether or not anyone intends it to.

### 2.6a `net.internetisalie.lunar.refactoring.LuaSafeDeleteProcessor` (edit) — the Safe Delete half of Phase 1

**Decision: the two predicates keep sharing one rule; the *elevation set* stops being a second
list.** Find Usages and Safe Delete must agree on what a Lua declaration site is — a disagreement
between two hand-maintained lists is the shape of defect this feature exists to remove — so
`canFindUsagesFor` and `isSafeDeleteAvailable` both stay `LuaDeclarationSite.kindOf(element) != null`.
What must *not* be a hand-maintained list is `LuaSafeDeleteProcessor.isElevatedDeclaration`
(`LuaSafeDeleteProcessor.kt:53-57`), which today enumerates `LuaLocalVarDecl`, `LuaLocalFuncDecl`,
`LuaFuncDecl`, `LuaAttName` — and none of the three nodes §3.5's `declarationNodeOf` newly returns
(`LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaAssignmentStatement`).

**What goes wrong if it is left alone — the failure mode this section exists to prevent.**
`getElementsToSearch` (`LuaSafeDeleteProcessor.kt:66-70`) elevates the caret leaf to
`declarationNodeOf(leaf)`, and the platform then **re-dispatches `handlesElement` on the elevated
node** before searching (`SafeDeleteProcessor.findUsages`, `SafeDeleteProcessor.java:138-166`: the
delegate loop, then `if (!handled && element instanceof PsiNamedElement) findGenericElementUsages(...)`).
`LuaGlobalVarDecl`, `LuaGlobalFuncDecl` and `LuaAssignmentStatement` all extend `LuaStatement`, which
is `PsiElement` — **not** `PsiNamedElement` (`LuaStatement.java:8`; `LuaStatementImpl` →
`LuaBaseElement` → `ASTWrapperPsiElement`, `LuaBaseElements.kt:29-31`). So the delegate is dropped,
the `PsiNamedElement` fallback does not fire, and the declaration is deleted **with no usage search
at all**. The processor's own KDoc states this outcome verbatim and is treated here as the
specification it is: *"if this returned false the delegate would be dropped and the declaration
deleted with NO usage search (silently orphaning references)"* (`LuaSafeDeleteProcessor.kt:46-52`).
This would be **strictly worse than today**, where `global x = 1` elevates to a `LuaAttName` — which
*is* in the enumerated set — so usages are searched and only the deletion granularity is wrong
(§3.5, the `LuaGlobalVarDecl` note).

**Change 1 — `isElevatedDeclaration` becomes a round-trip test, not an enumeration:**

```kotlin
private fun isElevatedDeclaration(element: PsiElement): Boolean {
    val leaf = LuaDeclarationSite.identifierLeafOf(element) ?: return false
    return LuaDeclarationSite.declarationNodeOf(leaf) === element
}
```

An element is an elevated declaration **iff it is what `declarationNodeOf` elevates its own
identifier leaf to**. That admits exactly the nodes `getElementsToSearch` can produce — no more, no
less — and it cannot fall behind `declarationNodeOf`, because adding a `declarationNodeOf` row
without the matching `identifierLeafOf` row breaks the round trip and TC-33 goes red. Worked
through every row of §3.5's two tables:

| Fixture | `identifierLeafOf(node)` | `declarationNodeOf(leaf)` | round-trips |
| :--- | :--- | :--- | :-- |
| `local x = 1` → `LuaLocalVarDecl` | the `x` leaf | that `LuaLocalVarDecl` (one `attName`) | yes |
| `local a, b = 1, 2` → `LuaAttName` | the `a` leaf | that `LuaAttName` (two `attName`s) | yes |
| `global x = 1` → `LuaGlobalVarDecl` | the `x` leaf | that `LuaGlobalVarDecl` | yes |
| `global function f() end` → `LuaGlobalFuncDecl` | the `f` leaf | that `LuaGlobalFuncDecl` | yes |
| `function greet() end` / `function M.run() end` / `function Obj:m() end` → `LuaFuncDecl` | `greet` / `run` / `m` | that `LuaFuncDecl` | yes |
| `cfg = {}` → `LuaAssignmentStatement` | the `cfg` leaf | that `LuaAssignmentStatement` | yes |
| `a, b = 1, 2` → `LuaVar` | the `a` leaf | that `LuaVar` (two `var`s) | yes — but see the multi-target note |
| `print(x)` → the `LuaVar` around the read `x` | the `x` leaf | the `x` leaf itself (row 14's predicate is false) | **no** — correctly rejected |

**Multi-target assignments become Safe-Deletable, and the deletion is text-incorrect.** A file-scope
`a, b = 1, 2` satisfies `isGlobalAssignmentTarget` for both targets (§3.5 row 14), so each is a
declaration site, `isSafeDeleteAvailable` is true for it — **newly**, because `canFindUsagesFor` has
no `LuaVar` grandparent branch today (`LuaFindUsagesProvider.kt:60-66`) — and `declarationNodeOf`
elevates the `a` leaf to its `LuaVar`, not to the statement. The round trip holds, so the delegate is
kept and usages *are* searched (which is the property §2.6a exists to protect); what is wrong is the
**granularity**: deleting that `LuaVar` leaves `, b = 1, 2` in the file, because nothing removes the
separating comma. This is the exact shape the pre-existing `local a, b = 1, 2` case already has —
`declarationNodeFor` returns the `LuaAttName` for a multi-name `local`
(`LuaSafeDeleteProcessor.kt:160-163`) and deleting it leaves `local , b = 1, 2` — so it is not a new
*class* of defect; it is a newly *reachable* instance of an existing one, which is why it is recorded
here rather than left to be mistaken for a regression when `LuaSafeDeleteTest` grows the fixture.
Comma-aware deletion is REFACT-03's, not this feature's: it changes what Safe Delete *removes*, and
REFACT-01 only changes what it *finds*. Recorded as `risks-and-gaps.md` Gap 2.6; TC-33 asserts the
round trip **and** the residual text, so the outcome is pinned rather than assumed.

**Change 2 — `identifierLeafOf` gains the two rows the round trip (and `findUsages`) need.** They are
in §3.5's table as rows 10 and 11. Without them this is not merely a `handlesElement` question:
`LuaSafeDeleteProcessor.findUsages` normalises with `identifierLeafFor(element) ?: element`
(`LuaSafeDeleteProcessor.kt:86`), so an elevated `LuaAssignmentStatement` that `identifierLeafOf`
does not know would be handed to `ReferencesSearch.search` **as the statement**.
`LuaNameReferenceSearcher.isNameDeclarationLeaf` returns early unless
`elementType == LuaElementTypes.IDENTIFIER` (`LuaNameReferenceSearcher.kt:84-88`) and the statement
is not a `PsiNamedElement` for the default searcher either — zero usages, silent orphaning again,
reached through a second door with the delegate still installed.

**Change 3 — the regression gate.** TC-32 (`LuaSafeDeleteTest.testUsedGlobalRaisesConflict`) drives
the real `SafeDeleteHandler` over `config = {}` + `print(config)` and requires a
`ConflictsInTestsException`; TC-33 asserts the round trip directly for all four new shapes. Both are
Phase 1 exit criteria, and the plan's mutation-proof list requires demonstrating that reverting
`isElevatedDeclaration` to its enumerated form turns TC-32 red. `LuaSafeDeleteTest` has **no** global
or dotted fixture today (`grep -n 'global\|function M\.' src/test/kotlin/net/internetisalie/lunar/refactoring/LuaSafeDeleteTest.kt` → empty), and TC-30 asserts `declarationNodeOf` in
isolation, so neither would have caught this.

### 2.7 `net.internetisalie.lunar.lang.LuaRequireReference` (edit) — REFACT-01-18

```kotlin
override fun handleElementRename(newElementName: String): PsiElement
```

Implements §3.7. No `elementManipulator` is registered: the reference is constructed with an
explicit `TextRange` (`LuaRequireReferenceContributor.kt:44-50`), so `getRangeInElement` never
consults one, and `bindToElement` is never called because `LuaRequireReference` is not a
`BindablePsiReference` (`RenameUtilBase.doRenameGenericNamedElement` only takes the
`handleElementRename` branch for non-bindable references).

### 2.8 `net.internetisalie.lunar.refactoring.rename.LuaCatsParamRenamer` (new) — REFACT-01-16

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaCatsParamRenamer.kt`
- **Responsibility**: move a `---@param <old>` tag when its parameter is renamed.
- **Threading**: called from `renameElement`, i.e. already inside the platform's write action.
- **Collaborators**: `LuaPsiImplUtil.getCatsComment(owner: LuaCatsCommentOwner?): LuaCatsComment?`
  (`lang/psi/LuaPsiImplUtil.kt:14`), `LuaCatsComment.getParamTagList(): List<LuaCatsParamTag>`
  (`luacats/lang/psi/LuaCatsComment.java:62`), `LuaCatsParamTag.getArgName(): LuaCatsArgName?`,
  `com.intellij.psi.impl.source.tree.LeafElement.replaceWithText` (`LeafElement.java:137`).

```kotlin
object LuaCatsParamRenamer {
    fun rename(parameterIdentifier: PsiElement, oldName: String, newName: String)
}
```

`oldName` is a **declared parameter**, not something the object reads from its caller: by the time
`renameElement` calls this the declaration leaf has already been swapped (§3.3 step 4), so the old
spelling no longer exists anywhere in the tree and a stateless object cannot recover it. Three
parameters is within the engineering contract's ≤3 cap.

### 2.9 `net.internetisalie.lunar.settings.LuaRefactoringSettings` (new) — REFACT-01-15

- **File**: `src/main/kotlin/net/internetisalie/lunar/settings/LuaRefactoringSettings.kt`
- **Responsibility**: persist the rename dialog's two non-code-search checkboxes.
- **Why it is needed**: `RenamePsiElementProcessorBase.isToSearchInComments` returns
  `element instanceof PsiFileSystemItem && …` — i.e. a hard **`false`** for every non-file element,
  and `setToSearchInComments` is a no-op for them (`RenamePsiElementProcessorBase.java:195-212`).
  The platform has `RENAME_SEARCH_IN_COMMENTS_FOR_FILE` but **no** `…_FOR_VARIABLE`
  (`RefactoringSettings.java:22-26`), so there is nothing to delegate to. This corrects the
  requirement's assumption that the platform's persisted settings apply here (see
  `risks-and-gaps.md` §Requirement Defects, RD-3).

```kotlin
@Service(Service.Level.APP)
@State(
    name = "LuaRefactoringSettings",
    storages = [Storage("lunar.refactoring.xml")],
    category = SettingsCategory.CODE,
)
class LuaRefactoringSettings : PersistentStateComponent<LuaRefactoringSettings.State> {
    class State {
        var renameSearchInComments: Boolean = false
        var renameSearchForText: Boolean = false
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var renameSearchInComments: Boolean
        get() = myState.renameSearchInComments
        set(value) {
            myState.renameSearchInComments = value
        }

    var renameSearchForText: Boolean
        get() = myState.renameSearchForText
        set(value) {
            myState.renameSearchForText = value
        }

    companion object {
        val instance: LuaRefactoringSettings
            get() = ApplicationManager.getApplication().getService(LuaRefactoringSettings::class.java)
    }
}
```

This is `net.internetisalie.lunar.settings.LuaEditorOptions` (`settings/LuaEditorOptions.kt:19-47`)
with two properties substituted — the same `@Service`/`@State`/`PersistentStateComponent` shape, the
same `ApplicationManager.getApplication().getService(...)` accessor, and its own `Storage` file.
`SimplePersistentStateComponent`/`BaseState` is deliberately **not** used: no settings class in this
repo uses it, and the house pattern is what the existing `LuaApplicationSettings`, `LuaEditorOptions`
and `LuaToolchainRegistry` all follow.

Defaults are `false` because Lua's dynamic-access idioms (`_G["name"]`, `require`d module tables)
make a string match far more likely to be coincidental than in a statically-typed language —
REFACT-01-20 records that this is best-effort by construction.

**The six accessors, exactly:**

```kotlin
override fun isToSearchInComments(element: PsiElement): Boolean =
    LuaRefactoringSettings.instance.renameSearchInComments

override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
    LuaRefactoringSettings.instance.renameSearchInComments = enabled
}

override fun isToSearchForTextOccurrences(element: PsiElement): Boolean =
    LuaRefactoringSettings.instance.renameSearchForText

override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
    LuaRefactoringSettings.instance.renameSearchForText = enabled
}

override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? =
    element.parent as? LuaNameRef

override fun getQualifiedNameAfterRename(
    element: PsiElement,
    newName: String,
    nonJava: Boolean,
): String = newName
```

**Why `getElementToSearchInStringsAndComments` is overridden, and why returning the leaf would make
the whole checkbox inert.** `RenameUtil.processUsages` derives the *string it searches for* from this
element, not from the element being renamed:
`ElementDescriptionUtil.getElementDescription(searchForInComments, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS)`
(`RenameUtil.java:145-155`). That resolves through `DefaultNonCodeSearchElementDescriptionProvider`,
whose only general branch is `if (element instanceof PsiNamedElement) return namedElement.getName()`
(`DefaultNonCodeSearchElementDescriptionProvider.java:37-40`) — and the platform default for this
hook returns `element` unchanged (`RenamePsiElementProcessorBase.java:264-266`). The element
`substituteElementToRename` hands over is a bare IDENTIFIER **leaf**, which is not a
`PsiNamedElement`, so every provider returns null and `ElementDescriptionUtil` falls through to
`return element.toString()` (`ElementDescriptionUtil.java:26`) — a `LeafPsiElement`'s debug string,
not the identifier. The search would run against that literal, match nothing, and REFACT-01-15 would
be silently inert with the checkbox ticked. Returning the enclosing `LuaNameRef` fixes it: it *is* a
`PsiNamedElement` (`LuaNameRef extends LuaNameRefElement`, `LuaNameRef.java:8`;
`LuaNameRefElement : PsiNamedElement` and `LuaNameRefElementImpl.getName()` returns the IDENTIFIER
text, `LuaBaseElements.kt:75, 81`), so the searched string is the name.

`null` is the deliberate answer for the one declaration kind with no `LuaNameRef` parent — the
numeric-`for` variable, whose leaf hangs directly off `LuaNumericForStatement` (`lua.bnf:152`,
§3.5 row 3). `RenameUtil.processUsages` guards both non-code branches with
`searchForInComments != null` (`RenameUtil.java:147, 157`), so a null simply disables non-code search
for that kind rather than searching for garbage — and a `for` index is the kind least likely to be
meaningfully named in a comment. TC-13c is the input→output case for both halves.

**Why `getQualifiedNameAfterRename` must be overridden, and why it belongs in Phase 2 rather than
here.** The two hooks above fix the string the platform *searches for*. They do not touch the string
it *substitutes*, which `RenameUtil.processUsages` derives from the **renamed element** — the bare
IDENTIFIER leaf — not from `getElementToSearchInStringsAndComments`'s result:

```java
// RenameUtil.java:145-155 — `element` is the renamed leaf; `searchForInComments` is our LuaNameRef
final PsiElement searchForInComments = elementProcessor.getElementToSearchInStringsAndComments(element);
if (searchInStringsAndComments && searchForInComments != null) {
  String stringToSearch  = ElementDescriptionUtil.getElementDescription(searchForInComments, …);
  if (!stringToSearch.isEmpty()) {
    final String stringToReplace = getStringToReplace(element, newName, false, elementProcessor);
    … new NonCodeUsageInfoFactory(searchForInComments, stringToReplace) …

// RenameUtil.java:209-228 — the branch a LeafPsiElement falls into
String result = theProcessor.getQualifiedNameAfterRename(psiElement, newName, nonJava);
if (result != null) return result;
if (psiElement instanceof PsiNamedElement) return newName;
else { LOG.error("Unknown element type : " + psiElement); return null; }
```

`RenamePsiElementProcessorBase.getQualifiedNameAfterRename` returns **null** by default
(`RenamePsiElementProcessorBase.java:106-108`), and a `LeafPsiElement` is not a `PsiNamedElement` —
so the `else` branch fires. In production that is an IDE internal error; under
`BasePlatformTestCase` the test logger turns it into a `TestLoggerAssertionError`; and the null it
returns reaches `document.replaceString(startOffset, endOffset, usageOffset.newText)`
(`RenameUtil.java:377`) as the replacement text. Returning `newName` is exactly what the
`PsiNamedElement` branch would have returned for a named element, so the override restores the
platform's own intent for a leaf-keyed processor and adds no behaviour of its own.

**It is reachable from Phase 2, before this section's settings service exists.**
`RenameDialog.createCheckboxes` adds the "Search in comments and strings" checkbox
**unconditionally** (`RenameDialog.java:279-282`) and `createRenameProcessor` passes
`isSearchInComments()` straight into `RenameProcessor` (`RenameDialog.java:405`). With no
`isToSearchInComments` override the box merely starts *unticked* (`RenameDialog.java:93-94` seeds it
from the processor, whose base returns false for a non-file element) — one user click is enough. And
with no `getElementToSearchInStringsAndComments` override either, the default returns the leaf,
whose `toString()` is non-empty, so `stringToSearch.isEmpty()` does **not** short-circuit the call.
*That last clause is read, not executed* — it is the only part of this chain that decides whether the
hazard is reachable in Phase 2 or only in Phase 7, and the mutation half of TC-13d settles it either
way (see the plan's mutation-proof list). The override is required regardless.
The override therefore ships in Phase 2 with `LuaRenameProcessor`, and TC-13d is its Phase-2 gate;
TC-13e is the Phase-7 end-to-end case once the other five accessors land.

**No specified test could have caught this without a new mechanism.** TC-13c calls the accessor and
`ElementDescriptionUtil` directly and never reaches `processUsages`, and
`myFixture.renameElementAtCaret` hard-codes `searchInComments = false`
(`CodeInsightTestFixtureImpl.java:1092-1096`). TC-13d/TC-13e therefore drive the four-argument
`myFixture.renameElement(element, newName, searchInComments = true, searchTextOccurrences = true)`
(`CodeInsightTestFixture.java:779`, implemented at `CodeInsightTestFixtureImpl.java:1098-1107`),
which is the only fixture entry point that propagates the flag — and which still routes through
`substituteElementToRename` + `RenameProcessor.run()`.

### 2.10 `net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex` (edit) + `LuaGlobalAssignmentNavigation` (edit) — REFACT-01-07 for Lua 5.5

- **Files**: `src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaGlobalAssignmentIndex.kt`,
  `src/main/kotlin/net/internetisalie/lunar/lang/navigation/LuaGlobalAssignmentNavigation.kt`.
- **Why this is inside REFACT-01 and not a premise to work around.** Lua 5.5's `global x = 1` and
  `global function f() end` are first-class declarations here (SYNTAX-09), and §3.5 rows 5 and 7
  classify them as `GLOBAL_VARIABLE` / `GLOBAL_FUNCTION` — i.e. **not** file-local, so §3.2 does not
  narrow their search scope. But the project-wide half of resolution cannot see them:
  `LuaGlobalAssignmentIndex.Indexer.map` indexes only `LuaAssignmentStatement` targets and
  `LuaFuncDecl` names (`LuaGlobalAssignmentIndex.kt:95-107`), and `LuaGlobalAssignmentNavigation`
  re-collects only `LuaAssignmentStatement`s (`LuaGlobalAssignmentNavigation.kt:29-33`). Neither
  mentions `LuaGlobalVarDecl` or `LuaGlobalFuncDecl`. A cross-file usage of a `global` therefore
  resolves to nothing, `isReferenceTo` is false, and a rename would rewrite the declaring file and
  silently leave every other file bound to the old name — Risk 1.1 exactly. Leaving the index alone
  was the unexamined premise; extending it is ~15 lines and is what makes REFACT-01-07 true for 5.5.
- **Change**:
  0. **`Indexer.map` delegates its existing rules instead of restating them** — this is what keeps
     §3.5 row 14 a *reuse* rather than a second copy. Its `fileLocals` line becomes
     `LuaDeclarationSite.computeFileScopeLocalNames(psiFile)` (not the cached accessor — §2.1), its
     private `fileScopeLocalNames`/`boundName` are deleted, `declaredGlobalName` calls
     `LuaDeclarationSite.boundName`, and its assignment collector becomes:
     ```kotlin
     topLevel.filterIsInstance<LuaAssignmentStatement>().forEach { stmt ->
         stmt.varList.varList.forEach { target ->
             if (!LuaDeclarationSite.isBareAssignmentTarget(target)) return@forEach
             val name = target.nameRef?.text ?: return@forEach
             if (name !in fileLocals) result[name] = ""
         }
     }
     ```
     Clauses 1-3 now have exactly one implementation (`isBareAssignmentTarget`) and clause 4's name
     rule exactly one (`computeFileScopeLocalNames`); the only thing that differs between the two
     callers is *how the set is obtained*, which is a caching decision, not a rule. This is
     behaviour-preserving on paper — clauses 2 and 3 are unconditionally true for a target reached
     by this enumeration — and the existing `LuaCrossFileGlobalResolutionTest`
     (whose two negative fixtures are `local shadowed\nshadowed = 2\n` and
     `local function f()\n   nested = 1\nend\n` — `LuaCrossFileGlobalResolutionTest.kt:73` and
     `:81`; note the second is a **`local function`**, not a bare `function f()`) is the gate that
     says so in fact. `getVersion()` still bumps (change 2) because change 1 alters
     content regardless.
  1. `Indexer.map` gains two collectors over the same `topLevel` list it already computes, applying
     the same `!in fileLocals` guard as the existing two:
     `topLevel.filterIsInstance<LuaGlobalVarDecl>().flatMap { it.attNameList }` → each
     `attName.nameRef.identifier.text`; and
     `topLevel.filterIsInstance<LuaGlobalFuncDecl>()` → `it.nameRef?.identifier?.text`
     (`@Nullable`, so `?.let`).
  2. `getVersion()` **3 → 4**. Mandatory and non-negotiable: the index *content* changes, and the
     file's own comment records the precedent and the failure mode of skipping it —
     "without the bump a persisted index keeps its … entries and the fix is invisible on any machine
     that has indexed before it" (`LuaGlobalAssignmentIndex.kt:54-58`).
  3. `LuaGlobalAssignmentNavigation.find` gains the mirror-image collectors so the re-resolve step
     returns the same leaves: for `LuaGlobalVarDecl`, `attNameList` whose `nameRef.text == name` →
     `nameRef.identifier`; for `LuaGlobalFuncDecl`, `nameRef` whose `text == name` →
     `nameRef.identifier`. `collectTargets` (`LuaGlobalAssignmentNavigation.kt:38-48`) is unchanged
     and keeps owning the assignment form.
- **Threading**: indexing runs on the indexing thread with no resolution (the existing `Indexer` rule
  — "an indexer must not attempt scope resolution", `LuaGlobalAssignmentIndex.kt:90-91` — is
  preserved: both new collectors are pure PSI shape tests). `find` is called from read actions only.
- **Blast radius, stated rather than assumed**: this is **additive to resolution** — names that
  previously resolved to nothing now resolve to their `global` declaration. The visible consequences
  are that `LuaGlobalCreationInspection` / `LuaUndeclaredVariableInspection` stop reporting a use of
  a `global`-declared name as undeclared (a fix), and that hover/completion gain a target. It is
  still a resolution change, so **Phase 1's corpus gate covers it** and
  `LuaGlobalCreationInspectionTest`, `LuaUndeclaredVariableInspectionTest` and
  `LuaCrossFileGlobalResolutionTest` are named regression gates.

## 3. Algorithms

### 3.0 Claiming — `canProcessElement`

- **Input → Output**: `(element: PsiElement) → Boolean`. This is the decisive function of the
  feature: `RenamePsiElementProcessorBase.forPsiElement` returns the **first** extension whose
  `canProcessElement` matches and never consults another
  (`RenamePsiElementProcessorBase.java:153-161`), so anything claimed here is claimed *instead of*
  the platform default, and anything not claimed here reaches the platform default unmodified.
- **Exact predicate** — this is the whole implementation, in this order:

```kotlin
override fun canProcessElement(element: PsiElement): Boolean {
    if (element is LuaLabelName || element is LuaLabelRef) return false
    if (element is PsiFileSystemItem) return false
    if (!element.language.isKindOf(LuaLanguage.INSTANCE)) return false
    return element is LuaNameRef || LuaDeclarationSite.kindOf(element) != null
}
```

- **Rules — every clause is load-bearing**:
  1. **Labels are excluded first and unconditionally.** `LuaDeclarationSite.kindOf(LuaLabelName)`
     returns `LABEL`, *not* null (§3.5 row 1), so without this line the final clause would claim
     labels and `forPsiElement` would hand rename to `LuaRenameProcessor` — the platform default,
     which is what makes label rename work today, would never see them. What follows is worse than an
     abort: §3.1 step 1 normalises the `LuaLabelName` to its IDENTIFIER child (`identifierLeafOf`
     row 1), `kindOf` of that child is **null** (§3.5 row 4 — its parent is a `LuaLabelName`, not a
     `LuaNameRef`), so step 4 passes it through and the rename proceeds against a key that neither
     §3.2 nor §3.3 is specified for. `LuaRenameProcessor` would silently take over the **one
     refactoring that works today** (REFACT-01-17 / REFACT-04, `LuaLabelRenameTest`) with an
     unspecified result. `LuaLabelRef` is excluded with it because it
     is the `goto` side of the same pair and is a `LuaNameRefElement` but **not** a `LuaNameRef`
     (`lua.bnf:247-250` gives it its own rule and its own `LuaLabelRef` interface), so the third
     clause would not exclude it. TC-24 and TC-25 guard both halves.
  2. **File-system items are excluded** so `RenamePsiFileProcessor` keeps winning the file-rename
     path REFACT-01-18 depends on (§3.7). `kindOf` would return null for a `PsiFile` anyway (§3.5
     row 2), but the exclusion is *stated* rather than derived because -18's correctness rests on it.
  3. **Every Lua `LuaNameRef` is claimed — usage as well as declaration.** This is deliberate
     over-claiming and it is the point. If only declaration leaves were claimed, a *usage* whose
     resolution fails (an undeclared global, a `t.field` member access) would fall through to
     `RenamePsiElementProcessorBase`, whose `RenameUtilBase.doRenameGenericNamedElement` renames that
     `LuaNameRef` in place through `setName` (`LuaBaseElements.kt:83-92`) and collects no usages —
     which is BUG-457 reproduced under a different code path. Claiming it lets §3.1 steps 2-3 refuse
     it *with a reason*. This is the predicate `risks-and-gaps.md` Risk 1.4 is about, and it is why
     `LuaUnsupportedRenameProcessor` must be deleted in the same commit (§7).
  4. **A bare IDENTIFIER leaf is claimed only when it is a declaration site** (`kindOf != null`) —
     that is the shape `TargetElementUtilBase` hands back on the usage path
     (`REFERENCED_ELEMENT_ACCEPTED` → `LuaNameReference.resolve()` → `LuaScopeProcessor.result`,
     an IDENTIFIER leaf) and the shape `substituteElementToRename` normalises everything to.
  5. Consequently `...` (an ELLIPSIS token, neither a `LuaNameRef` nor a declaration leaf) and a
     `{ field = 1 }` constructor key (`field ::= … | IDENTIFIER '=' expr`, `lua.bnf:319`, a bare leaf
     with no `LuaNameRef`) are **not** claimed, and the platform reports
     `error.wrong.caret.position.symbol.to.rename` (§6). TC-19b locks the `...` case.
- **Threading**: EDT, called once per rename invocation and by `RenameHandler.isAvailableOnDataContext`.
  Pure PSI type tests plus `kindOf`; **no resolution and no index read** — §3.5 row 14's file-scope
  local-name set is `CachedValuesManager`-cached for exactly this reason (§2.1).

### 3.1 Target normalisation — `substituteElementToRename`

- **Input → Output**: `(element: PsiElement, editor: Editor?) → PsiElement?` (the declaration
  IDENTIFIER leaf, or `null` = abort with a hint).
- **Steps**:
  1. `LuaDeclarationSite.identifierLeafOf(element)` — if non-null, go to step 4 with it.
  2. Otherwise `element` is a usage. Take `element.reference ?: (element.parent as? LuaNameRef)?.reference`;
     if null, refuse with `refactoring.rename.unresolved` → `null`.
  3. `reference.resolve()` — if null, refuse with `refactoring.rename.unresolved` → `null`.
     Else `LuaDeclarationSite.identifierLeafOf(resolved)` — if null, refuse with
     `refactoring.rename.unsupportedTarget` → `null`.
  4. Let `leaf` be the result of step 1 or step 3, and `kind = LuaDeclarationSite.kindOf(leaf)`.
     Three refusals, in this order:
     - **(4a) Receiver-segment guard.**
       `val funcName = PsiTreeUtil.getParentOfType(leaf, LuaFuncName::class.java, /* strict = */ false)`;
       if `funcName != null && LuaDeclarationSite.functionNameLeafOf(funcName) !== leaf`, refuse with
       `LuaBundle.message("refactoring.rename.functionNameSegment", leaf.text)` → `null`.
       Rationale in the note below.
     - **(4b)** If `kind == LuaDeclarationKind.METHOD_FUNCTION`, refuse with
       `refactoring.rename.colonMethod` → `null`. Reached only for the method-name leaf itself: the
       `Obj` of `function Obj:m()` is already refused by 4a.
     - **(4c)** If `kind == LuaDeclarationKind.LABEL`, return `null` (unreachable —
       `canProcessElement` already excludes labels — but keeps the invariant local).
  5. Return `leaf`.

> **Why step 4a exists: a function-name RECEIVER segment is a declaration site by §3.5 row 9, and
> renaming it half-applies.** In `function M.run() end` the `M` leaf's parent is a `LuaNameRef` whose
> parent is the `LuaFuncName` (`funcName ::= nameRef funcNameProperty* funcNameMethod?`,
> `lua.bnf:164`), so §3.5 row 9 classifies it `GLOBAL_FUNCTION` — the same row that (correctly)
> classifies the `greet` of `function greet() end`. Without step 4a the consequence is BUG-457
> verbatim, on the one shape §3.5 row 9 newly makes reachable:
>
> 1. **The target is not redirected.** When `M` has no other declaration, `LuaNameReference.resolve()`
>    on that `LuaNameRef` is null — Phase 1's crawl never sees the enclosing `LuaFuncDecl`, because
>    `LuaBlock.processDeclarations` enumerates `LuaLocalVarDecl`, `LuaLocalFuncDecl`,
>    `LuaGlobalVarDecl`, `LuaGlobalFuncDecl` and `LuaAssignmentStatement` and has **no `LuaFuncDecl`
>    branch** (`LuaBlockExt.kt:38-77`); `LuaScopeProcessor`'s `is LuaFuncDecl` branch
>    (`LuaScopeProcessor.kt:79-84`) is reached only from `scopeCrawlUp`'s own `is LuaFuncDecl` arm
>    (`LuaResolveUtil.kt:22`), i.e. only from *inside* the body. Phase 2 misses too:
>    `getQualifiedName` returns null because the parent is a `LuaFuncName`, not a `LuaIndexExpr`
>    (`LuaNameReference.kt:173-176`), and `LuaGlobalDeclarationIndex` keys the declaration under
>    `funcName.text` = `"M.run"`, not `"M"`. So `TargetElementUtilBase` falls through to
>    `ELEMENT_NAME_ACCEPTED` and hands the processor the `M` `LuaNameRef`, which §3.1 step 1
>    normalises to the `M` leaf and step 4 passes through.
> 2. **The search then finds nothing.** `findReferences` searches for the name `M`;
>    `LuaNameReferenceSearcher` offers the `M` of every `M.run()` call site, and each one's
>    `isReferenceTo(M-leaf)` is false for exactly the reason above — `resolve()` is null, and
>    `isReferenceTo` returns false on a null resolve (`LuaNameReference.kt:239`).
> 3. **Result:** the declaration's `M` is rewritten and every `M.run()` call site is left on the old
>    name. That is `risks-and-gaps.md` Risk 1.1 shape 6.
>
> The guard is written as a **round trip against the function's own name leaf** rather than as a
> shape enumeration, for the same reason §2.6a's `isElevatedDeclaration` is: it admits exactly the
> leaf `identifierLeafOf(theFuncDecl)` returns and cannot fall behind it. It therefore also covers
> the intermediate segments of a longer chain — in `function A.B.run() end` both `A` (grandparent
> `LuaFuncName`, §3.5 row 9) and `B` (grandparent `LuaFuncNameProperty`, §3.5 row 10 →
> `DOTTED_FUNCTION`) are refused, while `run` renames normally. `LuaGlobalFuncDecl` is unaffected:
> `globalFuncDecl ::= <<globalKeyword>> FUNCTION nameRef funcBody` (`lua.bnf:229`) has **no
> `LuaFuncName` node at all** (`LuaGlobalFuncDecl.java:15-16`), so `getParentOfType` returns null and
> step 4a is inert for it. TC-34a/TC-34b are the gates.
>
> **Why refusal and not redirection.** When `M` *is* declared — `M = {}` in the same file, or a
> cross-file `M = {}` reachable through `LuaGlobalAssignmentNavigation` — the caret never reaches
> this branch: `TargetElementUtilBase` tries `REFERENCED_ELEMENT_ACCEPTED` first, `resolve()` lands
> on that declaration's leaf, and rename proceeds against it (row 14 / row 6) with the funcName's `M`
> collected as an ordinary usage. Step 3a bites only when the receiver has no declaration to redirect
> to, which is precisely the case where no correct rewrite exists.

> **There is no `self` guard, and there must not be one.** An earlier draft opened this algorithm
> with "if the element is `self`, refuse with `refactoring.rename.implicitSelf`", justified by the
> claim that `LuaScopeProcessor` resolves `self` to the receiver/class leaf. **That claim was false
> and the guard was dead code.** Three grounded facts, in the order that matters:
>
> 1. **`self` resolves to the METHOD-NAME leaf, not the class.** `LuaScopeProcessor`'s implicit-`self`
>    branch (`LuaScopeProcessor.kt:87-93`) sets
>    `result = element.funcName.funcNameMethod!!.nameRef.identifier`. Since
>    `funcName ::= nameRef funcNameProperty* funcNameMethod?` (`lua.bnf:164`) and
>    `funcNameMethod ::= ':' nameRef` (`lua.bnf:166`), and `LuaFuncNameMethod` exposes exactly one
>    `getNameRef()` (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaFuncNameMethod.java:8-11`),
>    that leaf is `m` in `function T:m()`. The class leaf is `funcName.nameRef.identifier` — the
>    expression §3.5's `identifierLeafOf` row 9 uses as its *last* fallback.
> 2. **The guard could never fire on the real path.** `TargetElementUtilBase` tries
>    `REFERENCED_ELEMENT_ACCEPTED` first, so Shift+F6 with the caret on `self` hands the processor the
>    **resolved** element — the `m` leaf, whose `text` is `"m"`. A predicate on `element.text ==
>    "self"` is never true there. (`myFixture.renameElementAtCaret` takes the same path,
>    `CodeInsightTestFixtureImpl.java:1092-1107`, which is why TC-19a could not have failed.)
> 3. **The guard would have caused a false refusal.** `function T.m(self, x)` is legal Lua and its
>    `self` is an ordinary `PARAMETER` that must rename normally. A text predicate refuses it.
>
> What actually stops `self` being renamed is step 4: the resolved `m` leaf is `METHOD_FUNCTION`, so
> the colon-method refusal fires. TC-19a asserts exactly that, and is falsifiable. The residual
> hazard — that once DR-03 makes the colon form renameable, caret-on-`self` would rename the method —
> is recorded as `risks-and-gaps.md` Gap 2.4 against DR-03, where the caret information needed to
> guard it correctly is actually available.

- **Rules / edge handling**:
  - Refusal is `CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(msg), RefactoringBundle.message("rename.title"), null)` followed by `return null`,
    exactly as `LuaUnsupportedRenameProcessor.substituteElementToRename` does today
    (`LuaUnsupportedRenameProcessor.kt:47-59`). Returning `null` aborts before the dialog opens
    (`PsiElementRenameHandler.rename` line 204-205).
  - **Refusal mechanics under test — every refusal TC asserts a throw, not a `null` return.**
    `CommonRefactoringUtil.showErrorHint` short-circuits in unit-test mode and throws
    `CommonRefactoringUtil.RefactoringErrorHintException(message)`
    (`CommonRefactoringUtil.java:79-86`), so `substituteElementToRename` **never reaches its
    `return null`** in a `BasePlatformTestCase`. TC-10, TC-19a, TC-26 and TC-34a/TC-34b therefore catch that
    exception and assert on its `message`, which is also what makes them able to distinguish *which*
    refusal branch fired. The repo already does exactly this in
    `LuaUnsupportedRenameProcessorTest.testRefusesWithAnExplanation`
    (`src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaUnsupportedRenameProcessorTest.kt:43-62`),
    whose KDoc states the reason; that test is deleted with its subject in Phase 2 and this is where
    its lesson is preserved.
  - **Why step 4 refuses the colon form.** `methodExpr ::= ':' nameRef` (`lua.bnf:300`), so a call
    `obj:m()` produces a `LuaNameRef` whose `LuaNameReference.getQualifiedName` returns `null`
    (its parent is `LuaMethodExpr`, not `LuaIndexExpr` — `LuaNameReference.kt:174-180`), and the
    bare-name fallbacks look up `"m"` while `LuaGlobalDeclarationIndex` keys the declaration under
    `funcName.text` = `"Obj:m"`. Zero call sites would be found, which is the silent-partial defect.
    Refusing is the only correct behaviour until receiver-type-based method resolution exists
    (tracked as DR-03 in `risks-and-gaps.md`).
- **Complexity**: O(1) plus one `LuaNameReference.resolve()`, which is `ResolveCache`-backed.

### 3.2 Search scope — `findReferences`

- **Input → Output**: `(leaf, searchScope, searchInCommentsAndStrings) → Collection<PsiReference>`.
- **Steps**:
  1. `kind = LuaDeclarationSite.kindOf(element) ?: return super.findReferences(...)`.
  2. `effective = if (kind.isFileLocal) LocalSearchScope(element.containingFile) else searchScope`.
  3. `return ReferencesSearch.search(element, effective).findAll()`.
- **Rules**: `LuaNameReferenceSearcher.candidateFiles` already handles `LocalSearchScope` by reading
  its scope elements' files (`LuaNameReferenceSearcher.kt:63-77`, `candidateFiles`), so step 2 changes cost,
  not results. A file-local
  kind can have no cross-file usage by definition of Lua scoping, so narrowing is sound, not merely
  an optimisation.
- **Note**: `RenameUtil.processUsages` passes
  `PsiSearchHelper.getUseScope(element)` (intersected with the refactoring scope) as `searchScope`;
  a `LocalSearchScope` returned there is used verbatim (`RenameUtil.java:127-131`), so this override
  is the only reliable place to narrow.

### 3.3 Applying the rename — `renameElement`

- **Input → Output**: `(leaf, newName, usages, listener) → Unit`. Runs inside the platform's write
  action; opens no `WriteCommandAction` of its own.
- **Steps**:
  1. Capture, **before** any mutation, the three values later steps read — the declaration leaf is
     replaced in step 4 and none of them can be recovered afterwards:
     `val kind = LuaDeclarationSite.kindOf(element)` (step 5's branch condition),
     `val oldName = element.text` (step 5's tag selector, §3.6), and
     `val catsOwner = PsiTreeUtil.getParentOfType(element, LuaCatsCommentOwner::class.java, false)`.
  2. **Resolve everything that can fail, before the first edit** — the ATOMICITY rule, corrected at
     the Phase-2 review after the original steps 2-4 shipped in the opposite order (see the
     edge-handling bullet, and `risks-and-gaps.md` Gap 2.13):
     `val replacement = LuaElementFactory.createIdentifier(element.project, newName)` and the
     declaration's AST swap, prepared but not applied — its parent node, the leaf's node and the
     replacement's node. If **any** of them is unavailable, refuse the whole rename by throwing
     `IncorrectOperationException(LuaBundle.message("refactoring.rename.rewriteUnavailable", newName))`,
     which `RenameProcessor.performRefactoring` catches and reports through
     `RenameUtil.showErrorMessage` (`RenameProcessor.java:432-435`, `RenameUtil.java:264-268`).
     (`LuaElementFactory.createIdentifier` returns null for a name that cannot be an identifier —
     `goto end` does not parse, `lua.bnf:125` — and is covered in both directions by
     `LuaElementFactoryTest`.)
  3. For each `usage in usages`: `ProgressManager.checkCanceled()`; `RenameUtil.rename(usage, newName)`.
     (`RenameUtilBase.rename` → `usage.reference?.handleElementRename(newName)`, which is §2.5.)
     Usages are rewritten before the declaration, matching
     `RenameUtilBase.doRenameGenericNamedElement`'s own order, so that no usage's reference is
     invalidated by the declaration edit.
  4. Apply the swap prepared in step 2: `parentNode.replaceChild(targetNode, replacementNode)`.
     This is the repo's proven leaf-swap idiom — the body of both
     `LuaNameDeclElementImpl.setName` and `LuaNameRefElementImpl.setName`
     (`LuaBaseElements.kt:61-70` and `:83-92`). It works uniformly for the `LuaNameRef` parent of
     every declaration kind in §3.5 except one **and** for the `LuaNumericForStatement` parent of
     that one — the numeric-`for` variable, which has no `LuaNameRef` and therefore no
     `PsiNamedElement` at all (`lua.bnf:152`).
  5. If `kind == LuaDeclarationKind.PARAMETER && catsOwner != null`:
     `LuaCatsParamRenamer.rename(replacement, oldName, newName)` (§3.6).
  6. `listener?.elementRenamed(replacement)`.
- **Edge handling — this bullet was WRONG as written, and the code that followed it was wrong with
  it.** It read: *"step 3 returning `null` (parse failure of the new name) aborts before any
  declaration edit; usages are already renamed at that point, which cannot happen in practice
  because `LuaNamesValidator.isIdentifier` gates the dialog first"*. The first clause states the
  defect and the second dismisses it with the wrong argument: the dialog is not the only caller of
  `renameElement`, and "the declaration keeps the old name while every usage carries the new one" is
  **BUG-457 inverted** — the failure class this whole feature exists to remove, produced inside the
  method that removes it. A refusal is the correct outcome and a half-rename in either direction is
  not, so the ordering above makes the half-state unrepresentable rather than unlikely.
  `LuaNamesValidator` remains the first defence (REFACT-01-10, TC-11); step 2 is the second, and
  TC-36 is its gate with the reviewed ordering as its proven mutant.

### 3.4 Conflict detection (REFACT-01-14)

Renaming in Lua does not *collide*, it silently **rebinds**: the file still compiles and the
behaviour changes. Three rules, all evaluated in `findCollisions` — i.e. inside the background read
action that `BaseRefactoringProcessor` wraps around `findUsages`, never on the EDT.

- **Input**: `LuaRenameTarget(identifier = dLeaf, kind, newName)` and the usage list already
  accumulated by `RenameUtil.findUsages` (it calls `findCollisions(element, newName, allRenames, result)`
  immediately after `processUsages` has filled `result` — `RenameUtil.java:97-101` then `:103`, so `result` is
  readable, and must be **snapshotted** before appending to it).
- **Output**: `List<LuaRenameCollisionUsageInfo>`, de-duplicated by anchor element identity.

**C1 — a usage of the renamed symbol would be captured.**
For each site `s` in `usages.mapNotNull { it.element } + listOf(dLeaf)`:
1. `ProgressManager.checkCanceled()`
2. `val processor = LuaScopeProcessor(newName); LuaResolveUtil.scopeCrawlUp(processor, s)`
3. If `processor.result != null` → emit a collision anchored on `processor.result`, message
   `refactoring.rename.conflict.capture` with parameters `(newName, oldName)`.

*Why this is exact enough:* `scopeCrawlUp` stops at the reference's own declaring statement
(`prev` is passed as `lastParent`), and `LuaBlock.processDeclarations` stops at `lastParent`'s text
offset (`LuaBlockExt.kt:32-36`), so only declarations *lexically visible at `s`* are seen. A visible
`newName` at a site that must be rewritten is precisely the rebinding hazard.

**C2 — an existing reference to `newName` would be captured by the renamed declaration.**
Only when `kind.isFileLocal`. For each `r` in
`PsiTreeUtil.findChildrenOfType(dLeaf.containingFile, LuaNameRef::class.java)`:
1. `ProgressManager.checkCanceled()`
2. Skip unless `r.identifier.text == newName`.
3. Skip if `LuaDeclarationSite.kindOf(r.identifier) != null` — `r` is itself a *declaration* of
   `newName`, not a reference, and a new declaration shadowing the renamed one changes no existing
   binding. *(Without this skip, `local x = 1; print(x); do local y = 3 end` renamed `x`→`y` would
   report a false conflict; TC-16 locks that it does not.)*
4. `val processor = LuaScopeProcessor(dLeaf.text); LuaResolveUtil.scopeCrawlUp(processor, r)`
5. If `processor.result === dLeaf` → emit a collision anchored on `r`, message
   `refactoring.rename.conflict.shadow`.

**C3 — the global name is already taken.**
Only when `!kind.isFileLocal`. With `scope = GlobalSearchScope.projectScope(project)`:
1. `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, newName, project, scope, LuaFuncDecl::class.java)`
   — one collision per element, **normalised to `LuaDeclarationSite.identifierLeafOf(it) ?: it`** and
   anchored on that.
2. `LuaGlobalAssignmentNavigation.find(project, newName, scope)` — one collision per element,
   anchored on it, excluding any element already emitted by step 1.
Message: `refactoring.rename.conflict.globalExists` with parameter `newName`.
Both lookups are index reads, so this is cheap enough to run project-wide.

*Why step 1 normalises* (corrected during Phase 3, which is when it was first executed): an earlier
draft anchored step 1 on the `LuaFuncDecl` itself. The two lookups then return disjoint PSI types —
a declaration node and an IDENTIFIER leaf — so step 2's exclusion clause could never match and was
dead. Normalising both to the leaf makes it live and makes C3 and C4 the *same* lookup against
different names, which is what their shared cost argument already assumed. The `?: it` fallback is
deliberate and applies to C4 step 1 as well, where the design previously wrote `mapNotNull`: a
declaration silently dropped for want of a leaf lowers C4's count and can turn a real ambiguity into
silence, which is the single outcome C4 exists to prevent.

**C4 — the global being renamed is declared in more than one place.**
Only when `!kind.isFileLocal`. This rule exists because of a specific, grounded property of
resolution: `LuaNameReference.resolve()` returns `null` whenever `multiResolve` yields more than one
result (`LuaNameReference.kt:228-231`), and `isReferenceTo` returns false on a null resolve
(`LuaNameReference.kt:239`). So if two files both declare the global `config`, **every read of
`config` project-wide stops being a findable reference** and the rename would rewrite the chosen
declaration and nothing else — the silent-partial defect, arriving through resolution rather than
through classification. Steps, with `oldName = dLeaf.text` and `scope = GlobalSearchScope.projectScope(project)`:
1. `val declarations = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, oldName, project, scope, LuaFuncDecl::class.java)`
   `.map { LuaDeclarationSite.identifierLeafOf(it) ?: it } + LuaGlobalAssignmentNavigation.find(project, oldName, scope)`,
   de-duplicated by element identity. (`map`/`?: it`, not `mapNotNull` — see C3's note.)
2. If `declarations.size > 1`, emit **one** collision per element other than `dLeaf`, anchored on
   that element, message `refactoring.rename.conflict.ambiguousGlobal` with parameters
   `(oldName, declarations.size)`.
These are the same two index reads as C3, against `oldName` instead of `newName`, so C4 adds no new
mechanism and no new cost class. It is a *reported* conflict rather than a refusal on purpose: the
platform's conflicts dialog lists every site, the preview pane (REFACT-01-13) shows exactly which
usages will change, and the user can cancel — whereas a refusal inside `substituteElementToRename`
would have to run these lookups on the EDT (§2.2), which §9 Alternative D rejects for
`findExistingNameConflicts` for the same reason.

**C3 and C4 search an index KEY, not the name the user sees** (corrected during the Phase-4 review,
which is when a dotted target was first put through them). The steps above write `newName` and
`dLeaf.text`; for a dotted function both are the **last segment**, and
`LuaFuncStubElementType.indexStub` files `function M.run() end` under the FUNC_NAME node's text
`"M.run"` and — via `substringBefore('.')` — under the receiver `"M"`, **never** under `"run"`. Both
rules therefore searched a key nothing writes and were inert for every dotted declaration: measured,
two files each declaring `function M.run()` give 0 hits for `"run"`, 2 for `"M.run"`, **0**
references from `ReferencesSearch` and **0** conflicts, so the rename rewrote the declaration and
left every call site behind — the defect this feature exists against, inside its own conflict
detector.

Both rules now search `searchKeyOf(target, segment)` = the target's own receiver prefix + `segment`,
the prefix taken **by text offset** within the enclosing `funcName` so that it reproduces that
node's text verbatim rather than re-deriving separators. `function greet() end`'s only segment is
its own last one, so its prefix is empty; `config = {}` and the Lua 5.5 `global` forms have no
`LuaFuncName` ancestor at all. **Bare globals are unchanged by construction**, and TC-17/TC-31 —
untouched — are the check. The messages carry the key, so C4 reports `'M.run' is declared in 2
places` rather than naming a `run` that is declared nowhere.

**What this does NOT cover, deliberately.** `LuaNameReference` resolves a dotted name through *two*
sources — the stub index and `LuaMemberFieldNavigation` — so a `function M.run()` beside an
`M.run = function() end` is ambiguous too, and unreported. `risks-and-gaps.md` Gap 2.15 / [[BUG-466]]
carry it: counting member-field hits would anchor a collision on an element that is itself a
**usage** (measured), which §2.4's anchoring invariant forbids, so closing it is an anchor-rule
change rather than a wider lookup.

**Complexity**: C1 is O(|usages| × scope depth). C2 is one PSI pass over one file. C3 and C4 are two
index lookups each. Nothing scans the project's PSI.

**Known residual gap** (tracked in `risks-and-gaps.md` Gap 2.2, not a blocker): `LuaScopeProcessor`
finds the *first-in-source-order* declaration in a block, not the last, because
`LuaBlockExt.processDeclarations` iterates `statementList` forward. Where one block declares the same
name twice (`local x = 1 … local x = 2`), C2's identity test in step 5 can compare against the wrong
`x`. That is a pre-existing resolution characteristic, not introduced here; the preview pane
(REFACT-01-13) is the backstop.

### 3.5 Declaration classification — `LuaDeclarationSite.kindOf`

Exhaustive, in this order. `elementTypes` are `net.internetisalie.lunar.lang.psi.LuaElementTypes`.

| # | Test on `element` | Kind |
| :-- | :--- | :--- |
| 1 | `element is LuaLabelName` | `LABEL` |
| 2 | `element.node?.elementType != LuaElementTypes.IDENTIFIER` | `null` (stop) |
| 3 | `element.parent is LuaNumericForStatement` | `NUMERIC_FOR_VARIABLE` |
| 4 | `element.parent !is LuaNameRef` | `null` (stop) |
| 5 | grandparent `is LuaAttName && grandparent.parent is LuaGlobalVarDecl` | `GLOBAL_VARIABLE` |
| 6 | grandparent `is LuaAttName` | `LOCAL_VARIABLE` |
| 7 | grandparent `is LuaGlobalFuncDecl` | `GLOBAL_FUNCTION` |
| 8 | grandparent `is LuaLocalFuncDecl` | `LOCAL_FUNCTION` |
| 9 | grandparent `is LuaFuncName` | `GLOBAL_FUNCTION` (see the receiver-segment note) |
| 10 | grandparent `is LuaFuncNameProperty` | `DOTTED_FUNCTION` |
| 11 | grandparent `is LuaFuncNameMethod` | `METHOD_FUNCTION` |
| 12 | grandparent `is LuaNameList && grandparent.parent is LuaParList` | `PARAMETER` |
| 13 | grandparent `is LuaNameList && grandparent.parent is LuaGenericForStatement` | `GENERIC_FOR_VARIABLE` |
| 14 | grandparent `is LuaVar && isGlobalAssignmentTarget(grandParent)` | `GLOBAL_VARIABLE` |
| 15 | otherwise | `null` |

Rows 1-4, 6, 8, 9 and 11-13 reproduce `LuaFindUsagesProvider.canFindUsagesFor`
(`LuaFindUsagesProvider.kt:54-68`) exactly. Rows 5, 7, 10 and 14 are new, and each closes a hole
that would otherwise be a refusal or — worse — a silent partial rewrite:

- **Row 10 (`DOTTED_FUNCTION`)**: `funcName ::= nameRef funcNameProperty* funcNameMethod?` and
  `funcNameProperty ::= '.' nameRef` (`lua.bnf:164-165`), so in `function M.run()` the `run` leaf's
  grandparent is `LuaFuncNameProperty`, which the existing provider does not accept — `function
  M.run()` is therefore not findable or safe-deletable today. REFACT-01-08 requires it.
- **Row 5 (`GLOBAL_VARIABLE` from a Lua 5.5 `global` declaration)**: `globalVarDecl ::=
  <<globalKeyword>> attNameList ['=' exprList]` (`lua.bnf:217`) and `attNameList` is a `private`
  rule (`lua.bnf:242`), so an `attName` hangs directly off `LuaGlobalVarDecl` exactly as it does off
  `LuaLocalVarDecl` (`LuaGlobalVarDecl.java:10-13`). **Row 6 alone would therefore classify
  `global x = 1` as `LOCAL_VARIABLE`**, whose `isFileLocal = true` makes §3.2 step 2 narrow the
  search to `LocalSearchScope(containingFile)` — so `global x = 1` renamed in one file would leave
  every cross-file usage bound to the old name. Row 5 must precede row 6.
- **Row 7 (`GLOBAL_FUNCTION` from a Lua 5.5 `global function`)**: `globalFuncDecl ::=
  <<globalKeyword>> FUNCTION nameRef funcBody` (`lua.bnf:229`) has **no `funcName` node** — the
  `nameRef` is a direct child, so the identifier's grandparent is `LuaGlobalFuncDecl`
  (`LuaGlobalFuncDecl.java:15-16` declares `@Nullable LuaNameRef getNameRef()`). Row 9 tests
  `LuaFuncName` and does not match it, so without row 7 `kindOf` is `null`, `canProcessElement`
  refuses, and `global function f() end` is not renameable, findable or safe-deletable at all.
- **Row 9 also matches a function-name RECEIVER, and that is deliberate — the refusal lives in
  §3.1 step 4a, not here.** `funcName ::= nameRef funcNameProperty* funcNameMethod?` (`lua.bnf:164`)
  puts the `M` of `function M.run() end` behind the same `LuaFuncName` grandparent as the `greet` of
  `function greet() end`, so row 9 classifies both. Narrowing row 9 instead would have been the wrong
  place: `kindOf` is what Phase 1 rewrites `LuaFindUsagesProvider.canFindUsagesFor` and
  `getType` to (§2.1), and `canFindUsagesFor` accepts *any* `LuaFuncName` grandparent today
  (`LuaFindUsagesProvider.kt:63`) — a receiver `M` referenced from inside the body *does* resolve to
  the receiver leaf, via `LuaScopeProcessor`'s `is LuaFuncDecl` recursion branch
  (`LuaScopeProcessor.kt:79-84`, reached from `LuaResolveUtil.kt:22`), so Find Usages on it is not
  vacuous and must not silently start refusing. Rename is the operation that cannot be correct here,
  so rename is where it is refused — exactly as `METHOD_FUNCTION` is classified by row 11 and refused
  by §3.1 step 4. TC-34a/TC-34b are the gates; TC-23 and `LuaFindUsagesTest` are the proof that Find
  Usages is unchanged.
- **Row 14 (`GLOBAL_VARIABLE` from a bare assignment)**: the canonical Lua global is `x = 1`, which
  produces `var ::= nameRef varSuffix*` (`lua.bnf:292`) — grandparent `LuaVar`, matching no other
  row. REFACT-01-07 ("rename a global across every file") is not satisfied by the function forms
  alone; see the predicate below.

**`isGlobalAssignmentTarget(target: LuaVar): Boolean`** — the discriminator for row 14. A `LuaVar`
is *also* produced by every ordinary read (`print(x)` parses as `funcCall ::= varOrExp nameAndArgs+`
with `x` inside another `var`, `lua.bnf:296-297`), so `LuaVar` alone is not a declaration test.

**This is not "the index's rule, verbatim", and calling it that would have shipped a second copy.**
`LuaGlobalAssignmentIndex.Indexer.map` (`LuaGlobalAssignmentIndex.kt:92-107`) applies the same
*meaning* from the opposite direction — top-down, `topLevel.filterIsInstance<LuaAssignmentStatement>()`
then `stmt.varList.varList`, with the file-scope and assignment-target clauses **implied by how it
enumerates** rather than tested. `isGlobalAssignmentTarget` is bottom-up from an arbitrary `LuaVar`
and must test them. Only clause 4 is literally shared today. So the two are made one rule *in code*
rather than one rule *in prose*, in two named pieces, and the index is rewritten to call both
(§2.10 change 0). This design condemns copying a convention because copying it copies its defects;
that applies to its own predicate first.

```kotlin
/** Clauses 1-3: pure shape, O(1), reads no name set. */
fun isBareAssignmentTarget(target: LuaVar): Boolean

/** Clauses 1-4. Adds the file-scope-shadowing test using the CachedValuesManager-backed set. */
fun isGlobalAssignmentTarget(target: LuaVar): Boolean
```

1. `target.varSuffixList.isEmpty()` — a dotted target `t.x = 1` belongs to `LuaMemberFieldIndex`,
   not here.
2. `(target.parent as? LuaVarList)?.parent` is a `LuaAssignmentStatement`, i.e. `target` is an
   assignment *target* and not a read.
3. That statement's `parent` is a `LuaBlock` whose `parent` is a `LuaFile` — **file scope only**.
   This is **exactly equivalent** to the index's `containingFile.blockList.flatMap { it.statementList }`
   membership test, and is stated in the O(1) form because `isBareAssignmentTarget` is called once
   per assignment target during indexing and the membership form is O(file) per call. The equivalence
   is not an assumption: `LuaFile.getBlockList()` is `LuaPsiImplUtil.getBlockList`, which is
   `PsiTreeUtil.getChildrenOfType(element, LuaBlock::class.java)` — **direct children only**
   (`LuaPsiImplUtil.kt:67-68`), and `LuaBlock.getStatementList()` is
   `PsiTreeUtil.getChildrenOfTypeAsList(this, LuaStatement.class)` — also direct children only
   (`LuaBlockImpl.java:34-36`). DR-08 executes both forms against real PSI and requires them to
   agree. The index's own comment gives the *reason* for the clause and applies unchanged: "a bare
   assignment nested inside a function may well be writing to an enclosing local, and an indexer must
   not attempt scope resolution" (`LuaGlobalAssignmentIndex.kt:90-91`).
4. `target.nameRef?.text !in LuaDeclarationSite.fileScopeLocalNames(containingFile)` — a name also
   declared `local` at file scope is a local write, not a global declaration. Clause 4 is the *only*
   clause split across the two functions, and deliberately: it is the one that needs a whole-file
   name set, and the interactive path wants that set cached while the indexer must not touch
   `CachedValuesManager` on the indexing thread (§2.1).

`identifierLeafOf(element)` — first matching row wins:

| # | `element` | Result |
| :-- | :--- | :--- |
| 1 | `is LuaLabelName` | `element.identifier` |
| 2 | `kindOf(element) != null` | `element` — it is already the declaration leaf |
| 3 | `is LuaNameRef` | `element.identifier` if `kindOf(element.identifier) != null`, else `null` |
| 4 | `is LuaAttName` | `element.nameRef.identifier` |
| 5 | `is LuaLocalVarDecl` | `element.attNameList.firstOrNull()?.nameRef?.identifier` |
| 6 | `is LuaGlobalVarDecl` | `element.attNameList.firstOrNull()?.nameRef?.identifier` |
| 7 | `is LuaLocalFuncDecl` | `element.nameRef.identifier` |
| 8 | `is LuaGlobalFuncDecl` | `element.nameRef?.identifier` (the getter is `@Nullable`) |
| 9 | `is LuaFuncDecl` | `functionNameLeafOf(element.funcName)` — i.e. `funcName.funcNameMethod?.nameRef?.identifier` ?: `funcName.funcNamePropertyList.lastOrNull()?.nameRef?.identifier` ?: `funcName.nameRef.identifier` |
| 10 | `is LuaAssignmentStatement` | `element.varList.varList.singleOrNull()?.nameRef?.identifier` |
| 11 | `is LuaVar` | `element.nameRef?.identifier` |
| 12 | otherwise | `null` |

Rows 10 and 11 exist for **Safe Delete, not rename**: they are the return leg of the elevation
round trip specified in §2.6a, and they are what `LuaSafeDeleteProcessor.findUsages` normalises with
before calling `ReferencesSearch` (`LuaSafeDeleteProcessor.kt:86`). Neither is reachable from
`substituteElementToRename`, whose input is always a leaf or a `LuaNameRef` (§3.1). Row 10 uses `singleOrNull()` deliberately, and **not** for the reason an earlier draft
gave. It is *not* needed to stop the round trip admitting the wrong node: with `firstOrNull()` a
multi-target `a, b = 1, 2` statement is still rejected, because `declarationNodeOf(a)` returns the
`LuaVar` — its `varList` has two `var`s — which is not the statement, so the identity test fails
either way. The real reason is `LuaSafeDeleteProcessor.findUsages`' normalisation
`identifierLeafFor(element) ?: element` (`LuaSafeDeleteProcessor.kt:86`): that is a *total* function
over whatever element the platform hands it, and with `firstOrNull()` a multi-target statement would
be searched as `a` alone — usages of `b` never looked for, while the deletion removes `b` too.
`singleOrNull()` makes the ambiguous case return null, so the normalisation falls through to
`?: element` and §3.8's gate rejects it outright: no answer rather than a *subset* answer. Neither
table makes that statement reachable today (see §2.6a's multi-target note); the choice is what keeps
a future caller from getting a wrong answer instead of none. Row 11 does **not** re-test row 14's
predicate — the round trip does that for it, which is why a read `print(x)` is rejected (§2.6a's
worked table).

Rows 1 and 2 were a single row in an earlier draft and gave two different answers for one condition;
they are split because `kindOf(LuaLabelName)` is `LABEL` — non-null — so row 2 would return the
`LuaLabelName` composite rather than its identifier if it came first.

Row 9 is `LuaSafeDeleteProcessor.identifierLeafFor` (`LuaSafeDeleteProcessor.kt:178-191`)
**plus** the `funcNamePropertyList` fallback, factored into `functionNameLeafOf(funcName)` (§2.1) so
that the three-way precedence has exactly one implementation. Its other two callers are §3.1 step 4a
— which refuses any funcName segment that is *not* what this function returns — and
`LuaNameReference.declarationIdentifier` (`LuaNameReference.kt:246-258`), which today returns the
*receiver* leaf (`M`) for `function M.run()` and therefore makes `isReferenceTo` false for every
`M.run()` call site; Phase 1 repoints it at `functionNameLeafOf` too.

`declarationNodeOf(element)` — `LuaSafeDeleteProcessor.declarationNodeFor`
(`LuaSafeDeleteProcessor.kt:156-171`) moved, plus the rows the new kinds make **mandatory**. This is
not scope creep into REFACT-03: `LuaRefactoringSupportProvider.isSafeDeleteAvailable` becomes
`LuaDeclarationSite.kindOf(element) != null` (§2.6), so every kind added above becomes
safe-deletable, and a kind with no `declarationNodeOf` row falls through to `else -> element` and
deletes the bare identifier — leaving `global  = 1`.

**Adding a row here is half a change.** Every node this table newly returns must also round-trip
back through `identifierLeafOf` (rows 10 and 11 above), because `LuaSafeDeleteProcessor` re-dispatches
`handlesElement` on the elevated node and searches usages from `identifierLeafFor(node)`. §2.6a
specifies both legs and TC-32/TC-33 gate them; read it before touching this table.

| grandparent of the leaf | Node returned |
| :--- | :--- |
| `LuaAttName` whose parent is `LuaLocalVarDecl` | that `LuaLocalVarDecl` if it has one `attName`, else the `LuaAttName` (unchanged) |
| `LuaAttName` whose parent is `LuaGlobalVarDecl` | that `LuaGlobalVarDecl` if it has one `attName`, else the `LuaAttName` (**new**) |
| `LuaGlobalFuncDecl` | that `LuaGlobalFuncDecl` (**new**) |
| `LuaLocalFuncDecl` | that `LuaLocalFuncDecl` (unchanged) |
| `LuaFuncName` / `LuaFuncNameMethod` | the enclosing `LuaFuncDecl`, else the grandparent (unchanged) |
| `LuaFuncNameProperty` | the enclosing `LuaFuncDecl`, else the grandparent (**new**) |
| `LuaVar` satisfying `isGlobalAssignmentTarget` | the enclosing `LuaAssignmentStatement` if its `varList` has one `var`, else the `LuaVar` (**new**; deleting that `LuaVar` leaves `, b = 1, 2` — see §2.6a's multi-target note) |
| anything else | `element` (unchanged) |

The `LuaGlobalVarDecl` row also **fixes a pre-existing defect**, which is worth naming so it is not
mistaken for a regression when `LuaSafeDeleteTest` output changes: today `canFindUsagesFor` accepts
any `LuaAttName` grandparent regardless of its parent (`LuaFindUsagesProvider.kt:61`), so
`isSafeDeleteAvailable` already returns true for `global x = 1`, while `declarationNodeFor`'s
`grandParent.parent as? LuaLocalVarDecl ?: return grandParent` (`LuaSafeDeleteProcessor.kt:161`)
returns the `LuaAttName` — so Safe Delete on a 5.5 global today leaves `global  = 1` behind.

### 3.6 `---@param` propagation (REFACT-01-16)

- **Input → Output**: `(parameterIdentifier: PsiElement, oldName: String, newName: String) → Unit`.
  All three are **declared parameters** — three is within the ≤3 cap, and `oldName` cannot be derived
  inside the object: §3.3 step 4 has already replaced the declaration leaf by the time this runs, so
  the old spelling exists nowhere in the tree. §3.3 step 1 captures it and passes it in.
- **Steps**:
  1. `val owner = PsiTreeUtil.getParentOfType(parameterIdentifier, LuaCatsCommentOwner::class.java, false) ?: return`
  2. `val comment = LuaPsiImplUtil.getCatsComment(owner) ?: return`
  3. `val tag = comment.paramTagList.firstOrNull { it.argName?.text == oldName } ?: return`
  4. `val leaf = tag.argName?.node?.firstChildNode as? LeafElement ?: return`
  5. `leaf.replaceWithText(newName)`
- **Rules / edge handling**: `paramTag ::= '@param' ((<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>) <<ArgType type>> description?`
  (`luacats.bnf:143`), so `argName` is null for the `...` variadic form — step 3's predicate skips
  it. A parameter with no annotation, an annotation on a `LuaFuncDef` assigned to a local (whose
  comment owner is the enclosing `LuaLocalVarDecl`, itself a `LuaCommentOwner` —
  `LuaLocalVarDecl.java:13`), and a mismatched tag name all fall through as no-ops. Only the
  **first** matching tag is rewritten; duplicate `@param x` tags are a LuaCATS error already.
- **Out of scope**: `@class` / `@alias` name propagation. Those names have no `PsiReference` anywhere
  under `luacats/` and reach navigation only through the file-based `LuaCatsTypeNameIndex`; moving
  them is a separate mechanism, deferred in `risks-and-gaps.md` (TBD-2, DR-04).

### 3.7 `require(...)` rewriting on file rename (REFACT-01-18)

- **Input → Output**: `LuaRequireReference.handleElementRename(newElementName: String) → PsiElement`.
  `newElementName` arrives as the **full new file name with extension** (e.g. `"newmod.lua"`) —
  `RenamePsiFileProcessor` does not override `renameElement`, so the base
  `RenameUtilBase.doRenameGenericNamedElement` passes the raw new name to every non-bindable
  reference.
- **Steps**:
  1. `val oldLiteral = element.text.substring(rangeInElement.startOffset, rangeInElement.endOffset)`
     — the module string **including** its delimiters (`"mod"`, `'mod'`, `[[mod]]`).
  2. `val openIndex = oldLiteral.indexOfFirst { it !in "\"'[=" }`; if none, return `element`.
     `val prefix = oldLiteral.take(openIndex)`; `val closeIndex = oldLiteral.length - prefix.length`
     mirrored from the end by the same predicate; `val suffix = oldLiteral.drop(closeIndex)`.
     `val oldModule = oldLiteral.substring(openIndex, closeIndex)`.
     This reproduces the delimiter-stripping already used by
     `LuaRequireReferenceContributor.moduleNameOf` (`text.trim('"', '\'', '[', ']', '=')`) while
     *retaining* the delimiters so they can be re-emitted unchanged.
  3. `val newBase = newElementName.removeSuffix(".lua")`.
  4. `val newModule = if (oldModule.contains('.')) oldModule.substringBeforeLast('.') + "." + newBase else newBase`.
     `.` is the only module separator this plugin understands:
     `LuaModuleFileResolver.resolveModuleCandidates` maps `moduleName.replace('.', '/') + ".lua"`
     and keys `FilenameIndex` on `moduleName.substringAfterLast('.') + ".lua"`
     (`LuaModuleFileResolver.kt:38-43`).
  5. `val replacementExpr = LuaElementFactory.createStringLiteral(element.project, prefix + newModule + suffix) ?: return element`
  6. Replace the string child of the host: for a `LuaTerminalExpr` host, `element.string`;
     for a `LuaArgs` host, `element.string` (both accessors exist —
     `LuaTerminalExpr.java:14`, `LuaArgs.java:17`). `stringElement.node.treeParent.replaceChild(stringElement.node, replacementExpr.node)`.
  7. Return `element`.
- **New factory method** (in the existing `LuaElementFactory`):
  ```kotlin
  fun createStringLiteral(project: Project, literalText: String): PsiElement? =
      (createExpression(project, literalText) as? LuaTerminalExpr)?.string
  ```
  `createExpression` builds `local _ = <literalText>` and returns the `LuaExpr`
  (`LuaElementFactory.kt`, covered by `LuaElementFactoryTest.testCreateExpressionProducesLuaExpr`).
- **Why the file is found at all**: `CachesBasedRefSearcher` searches the word
  `virtualFile.getNameWithoutExtension()` (= `mod`) and, because the target is a `PsiFileSystemItem`,
  `SearchRequestCollector.searchWord` adds `UsageSearchContext.IN_STRINGS`
  (`SearchRequestCollector.java:33-37`). `LuaFindUsagesProvider.getWordsScanner` is a
  `DefaultWordsScanner` configured with `LuaSyntax.StringLiteralTokens` (= `LuaElementTypes.STRING`),
  so string contents are word-indexed. `LowLevelSearchUtil.processTreeUp` then walks from the STRING
  leaf up through its parents, reaching the `LuaTerminalExpr`/`LuaArgs` host that carries the
  reference. No new index and no new contributor is required.

### 3.8 Reference-search normalisation — `LuaNameReferenceSearcher.processQuery`

The one algorithm this feature changes in an *existing* class rather than a new one, and the only
place where the order of two guards is load-bearing. `isNameDeclarationLeaf`
(`LuaNameReferenceSearcher.kt:84-88`) is **deleted**; `candidateFiles` (`:63-77`) is unchanged.

- **Input → Output**: `(parameters, consumer) → Unit`, feeding every `PsiReference` whose
  `isReferenceTo` holds for the normalised declaration leaf.
- **Threading**: a `QueryExecutorBase(true)` — the platform runs it inside a read action. The file
  loop gains a `ProgressManager.checkCanceled()`, which the current body lacks.
- **Exact body** (this is the whole implementation, in this order):

```kotlin
override fun processQuery(
    parameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
) {
    val requested = parameters.elementToSearch
    if (requested is LuaLabelName) return                                   // ① unreachable — keep
    val target = LuaDeclarationSite.identifierLeafOf(requested) ?: return   // ②
    if (LuaDeclarationSite.kindOf(target) == null) return                   // ③
    val name = target.text                                                  // ④
    if (name.isEmpty()) return

    for (file in candidateFiles(target, name, parameters.effectiveSearchScope)) {
        ProgressManager.checkCanceled()
        for (nameRef in PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)) {
            if (nameRef.identifier?.text != name) continue
            val reference = nameRef.reference ?: continue
            if (reference.isReferenceTo(target) && !consumer.process(reference)) return   // ⑤
        }
    }
}
```

- **① The label exclusion is defence-in-depth and is CURRENTLY UNREACHABLE. Keep it; do not
  reorder it; do not write a test for it.** An earlier draft of this section claimed the guard had
  to precede normalisation or labels would be searched by this searcher *as well as* by the default
  named-element searcher. **That claim is retracted — it is false**, and the two independent proofs
  are worth recording because they are what stops the next editor re-deriving it:
  1. **Guard ③ already rejects labels after normalisation.** `identifierLeafOf` row 1 maps a
     `LuaLabelName` to its IDENTIFIER child, and `kindOf` of *that child* is **null** — §3.5 row 4
     stops, because its parent is a `LuaLabelName`, not a `LuaNameRef`. So `③` returns on the very
     input `①` exists to catch. No input distinguishes the two orders: the reorder is
     **behaviour-preserving**, and there is therefore no mutation that can turn a test on it red.
  2. **This searcher can emit nothing for a label target under any fixture.** It only ever offers
     references attached to `LuaNameRef` composites, and `isReferenceTo` is identity against
     `resolve()` (`LuaNameReference.kt:233-244`). `LuaNameReference.resolve()` walks
     `LuaBlock.processDeclarations` (`LuaBlockExt.kt:25-81`), which has **no** `LuaLabel` branch —
     labels are a separate scope walk, `processLabelDeclarations` (`:83-93`), reached only from
     `LuaLabelReference`/`LuaLabelScopeProcessor`. So `isReferenceTo(labelLeaf)` is false for every
     candidate and the loop yields nothing even with the entire gate deleted.

  It is kept anyway because it is free and it is the guard that becomes load-bearing the moment
  §3.5 gains a row returning non-null for a label's IDENTIFIER *leaf* (e.g. a `parent is
  LuaLabelName → LABEL` row added to make `kindOf` total over label PSI). At that point `③` stops
  rejecting and `①` is the only exclusion left. **An editor who deletes it as dead code, or who
  "simplifies" by folding it after the normalisation, removes that protection silently.**

  Regression coverage for the label path is the existing
  `LuaFindUsagesTest.testLabelUsagesCount` (`:95-108` — asserts **exactly one** label reference
  through both `myFixture.findUsages` and `ReferencesSearch.search`, so a searcher that started
  contributing spurious label references, or that broke the label path outright, fails it) and
  `LuaLabelRenameTest` end to end. Both are already Phase 1 exit criteria. TC-24 and TC-25 remain the
  gates on the *`canProcessElement`* label exclusion (§3.0 rule 1), which — unlike this one — **is**
  reachable and load-bearing, because that predicate has no `③`-equivalent and
  `kindOf(LuaLabelName)` is `LABEL` (§3.5 row 1), not null.
- **② Normalisation replaces the old `elementType == IDENTIFIER` test.** The gate must now admit a
  declaration `LuaNameRef` **composite** as well as its leaf, because in-place rename (§2.6) hands
  the platform a `PsiNamedElement`, and because `LuaSafeDeleteProcessor.findUsages` passes
  `identifierLeafFor(element) ?: element` (`:86`) — an elevated declaration node when the fallback
  fires. `identifierLeafOf` covers every one of those shapes (§3.5 rows 1-12) and returns null for
  anything else, which is the correct "not searchable" answer.
- **③ is what keeps the gate meaning "a declaration site", the same predicate it means today.**
  `identifierLeafOf` row 11 maps *any* `LuaVar` to its `nameRef.identifier`, including the read `x`
  in `print(x)`; row 3 already applies `kindOf` for a `LuaNameRef` input, but rows 4-11 do not. A
  separate `elementType == IDENTIFIER` test is unnecessary — every row of `identifierLeafOf` returns
  an IDENTIFIER leaf, and row 2's pass-through is gated on `kindOf`, whose own row 2 requires it.
- **④ `name` is read from the NORMALISED leaf, never from `parameters.elementToSearch`.** Today
  `val name = target.text` (`LuaNameReferenceSearcher.kt:46`) is safe only because the gate
  guaranteed `target` was already a leaf. Once composites are admitted, `requested.text` would be
  the *whole declaration's* text — `"local x = 1"` for a `LuaLocalVarDecl` — so
  `nameRef.identifier?.text != name` would never match, `CacheManager.getFilesWithWord` would be
  asked for a word containing spaces, and the search would return zero usages while looking healthy.
- **⑤ `isReferenceTo` is called against the NORMALISED leaf, not `requested`** (today
  `LuaNameReferenceSearcher.kt:53` passes `target`, which was the leaf).
  `LuaNameReference.isReferenceTo` compares identity against `resolve()`'s result, which is always a
  leaf (`LuaNameReference.kt:233-244`), so passing a composite makes every candidate false — the
  widening would silently return zero usages, which is the same green as "no usages exist".

**Regression surface**: `LuaFindUsagesTest`, `LuaFindUsagesCrossFileTest`, `LuaSafeDeleteTest` and
`LuaLabelRenameTest` all consume this searcher and are Phase 1 exit criteria unchanged. There is
**no** direct unit guard on ①, and none is possible — see ① for the proof. ②, ④ and ⑤ are covered by
the widened-input cases (TC-32, TC-33) and by the cross-file suite; a regression in any of them
shows up as zero usages, which those tests assert against.

## 4. External Data & Parsing

The feature consumes **no** CLI output, network response, or file format produced outside this
plugin. The one non-PSI string it parses is the module literal inside a `require` call, whose format
and parse rule are pinned in §3.7 steps 1-4.

## 5. Data Flow

### Example 1 — rename a local from a usage (REFACT-01-01, -02)

`local counter = 0` … caret in `print(coun|ter)` … Shift+F6 → `total`.

1. `TargetElementUtilBase.doFindTargetElement` (`REFERENCED_ELEMENT_ACCEPTED`) →
   `LuaNameReference.resolve()` → `LuaScopeProcessor.result` = the `counter` IDENTIFIER leaf of the
   declaration.
2. `LuaRenameProcessor.canProcessElement(leaf)` → true (`kindOf` = `LOCAL_VARIABLE`).
3. `substituteElementToRename(leaf)` → `leaf` (§3.1 step 1).
4. `findReferences(leaf, …)` → `LocalSearchScope(file)` → `LuaNameReferenceSearcher` scans the file
   for `LuaNameRef`s named `counter` and keeps those whose `isReferenceTo(leaf)` holds → 4 refs.
5. `findCollisions` → C1 finds no visible `total`; C2 finds no `LuaNameRef` named `total`. Empty.
6. `renameElement` → 4 × `LuaNameReference.handleElementRename("total")` → 4 ×
   `LuaNameRef.setName`; then the declaration leaf is swapped.

### Example 2 — rename a global function across files (REFACT-01-07)

`a.lua: function greet() end` / `b.lua: greet()` — caret on `greet` in `a.lua`.

1. Target = `funcName.nameRef.identifier` (`ELEMENT_NAME_ACCEPTED` gives the `LuaNameRef`;
   §3.1 step 1 normalises it to the leaf).
2. `kind = GLOBAL_FUNCTION`, `isFileLocal = false` → `findReferences` keeps the platform's project
   scope; `LuaNameReferenceSearcher` narrows by `CacheManager.getFilesWithWord("greet", IN_CODE, …)`
   and matches through `LuaNameReference.isReferenceTo` → the `b.lua` call site.
3. `findCollisions` → C3 checks `LuaGlobalDeclarationIndex[newName]` and
   `LuaGlobalAssignmentNavigation.find` project-wide.
4. `renameElement` rewrites `b.lua`'s reference and `a.lua`'s declaration.

### Example 3 — rename `util.lua` to `helpers.lua` (REFACT-01-18)

1. `RenamePsiFileProcessor` finds usages via `CachesBasedRefSearcher` on the word `util`
   (`IN_STRINGS` included) → the `LuaTerminalExpr` in `require("app.util")`.
2. `RenameUtilBase.rename` → `LuaRequireReference.handleElementRename("helpers.lua")`.
3. §3.7: `oldLiteral = "\"app.util\""` → `prefix = "\""`, `oldModule = "app.util"`,
   `suffix = "\""`, `newBase = "helpers"`, `newModule = "app.helpers"` → the STRING child is
   replaced with `"app.helpers"`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Caret on `self` inside `function T:m()` | Resolves to the **method-name** leaf `m` (`LuaScopeProcessor.kt:87-93` + `lua.bnf:166`), **not** to the class `T`, so §3.1 step 4 refuses it with `refactoring.rename.colonMethod`. There is no `self`-specific guard — see the note under §3.1, which records why one would be dead code and would falsely refuse the legal `function T.m(self, x)`. TC-19a. |
| Caret on `self` in a `function T.m(self, x)` parameter list | Renamed normally: it is an ordinary `PARAMETER` (§3.5 row 12). Lua only makes `self` implicit for the colon form. TC-19c. |
| Caret on `self` outside any method (`local x = self`) | An ordinary undeclared name: `kindOf` is null and resolution finds nothing, so §3.1 step 3 refuses with `refactoring.rename.unresolved`. |
| Caret on `global x = 1` (Lua 5.5) | `GLOBAL_VARIABLE` (§3.5 row 5), `isFileLocal = false`, so §3.2 keeps the project scope. Classifying it by the `LuaAttName` row alone would make it `LOCAL_VARIABLE` and narrow the search to one file. |
| Caret on `global function f() end` (Lua 5.5) | `GLOBAL_FUNCTION` (§3.5 row 7). The grandparent is `LuaGlobalFuncDecl`, not `LuaFuncName` — `globalFuncDecl` has no `funcName` node (`lua.bnf:229`). |
| Caret on a bare global `config = {}` | `GLOBAL_VARIABLE` (§3.5 row 14) when `isGlobalAssignmentTarget` holds: no `varSuffix`, an assignment target, at file scope, and not shadowed by a file-scope `local`. Otherwise `null` — a write to an enclosing local is not a declaration. |
| A global declared in two files | Not refused, but **reported**: C4 (§3.4) anchors a collision on every other declaration site, because `LuaNameReference.resolve()` returns null on a multi-result `multiResolve` (`LuaNameReference.kt:228-231`) and reads of that global would otherwise be silently skipped. |
| Caret on `...` | `canProcessElement` is false — the ELLIPSIS token is neither an IDENTIFIER nor a `LuaNameRef` — so the platform shows `error.wrong.caret.position.symbol.to.rename`. |
| Caret on `{ field = 1 }` | `field ::= … \| IDENTIFIER '=' expr` (`lua.bnf:319`) is a bare leaf with no `LuaNameRef`; `kindOf` → `null`, `canProcessElement` false → platform refusal. |
| Caret on `t.field` (member access) | **Enumerated for all four receiver shapes by DR-05 — the table in §6.1 is the answer, and it is measured.** Every plain-data-field shape refuses; the only member access that reaches a rename target at all is a dotted *function* call whose receiver segment is spelled the same as the declaration's. No shape half-applies, and none is misdirected onto the receiver. |
| Caret on the `run` of an `M.run()` **call site** (a genuine dotted function) | **Not renameable — and the refusal comes from the PLATFORM, not from this processor.** Measured by DR-05. `TargetElementUtil` resolves the reference and hands back the whole enclosing `LuaFuncDecl`; §3.0 does not claim declaration NODES (deliberately — see the `M = {}` row above, whose rationale is the same one), so `RenamePsiElementProcessorBase.forPsiElement` selects no processor and the platform reports `error.cannot.be.renamed`. `substituteElementToRename` would normalise this correctly if it were ever asked — DR-05 called it directly and got the `run` leaf, `DOTTED_FUNCTION` — but it is not asked. Rename from the DECLARATION caret instead (TC-09), which works and does rewrite the call sites. |
| Caret on `function Obj:m()` | Refused with `refactoring.rename.colonMethod` (§3.1 step 4). |
| Caret on the **receiver** `M` of `function M.run()` (no other declaration of `M`) | Classified `GLOBAL_FUNCTION` by §3.5 row 9, then refused by §3.1 step 4a with `refactoring.rename.functionNameSegment`: `functionNameLeafOf(funcName)` is the `run` leaf, not this one. Without step 4a the declaration's `M` would be rewritten and every `M.run()` call site left on the old name — resolution cannot redirect, because `LuaBlock.processDeclarations` has no `LuaFuncDecl` branch (`LuaBlockExt.kt:38-77`). TC-34a. |
| Caret on the receiver `M` of `function M.run()` when `M = {}` also exists | **Refused by step 4a — this row's original claim was measured false in Phase 2.** `resolve()` on the funcName's `M` is still null (nothing redirects it to `M = {}`), so `TargetElementUtilBase` falls through to `ELEMENT_NAME_ACCEPTED`, hands over the `M` leaf, and step 4a refuses with `refactoring.rename.functionNameSegment`. A refusal, not a half-rename, so the outcome is safe — but it is not the redirection this row promised. |
| Caret on `M = {}` when `function M.run() end` also exists | **Not renameable**, measured. `TargetElementUtil` hands back the whole enclosing `LuaFuncDecl`, which §3.0 does not claim, so the platform reports `error.cannot.be.renamed`. That refusal is load-bearing: `identifierLeafOf` is total over declaration NODES (Safe Delete needs it so) and maps a `LuaFuncDecl` to its LAST name segment, so a `canProcessElement` widened to admit declaration nodes would answer a rename of `M` by renaming **`run`**. `LuaRenameTest.testCaretOnAGlobalShadowedByADottedDeclarationIsRefusedNotMisdirected`, mutation-proved. |
| Caret on the numeric-`for` variable `for <caret>i = 1, 3` | **No rename target at all**, measured. `numericForStatement ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) hangs the leaf directly off the statement, so it carries no reference and `LuaNumericForStatement` is no `PsiNamedElement`; `TargetElementUtilBase.getNamedElement` finds nothing and `findTargetElement` returns null. The leaf itself IS renameable — rename from a usage (`print(<caret>i)`) rewrites the `for` header correctly (TC-05). Closing the caret gap needs a `TargetElementEvaluatorEx2` for Lua, which is not in this feature's scope; `LuaRenameTest.testNumericForDeclarationCaretHasNoRenameTargetAtAll` pins it. |
| Caret on an intermediate segment `B` of `function A.B.run()` | Refused by §3.1 step 4a (`DOTTED_FUNCTION` by row 10, but not the funcName's last segment). TC-34b. |
| Two locals of the same name in nested blocks | **This row was WRONG until Phase 2 measured it, and it is the one that cost something.** Re-resolving is necessary but not sufficient: `LuaResolveUtil.scopeCrawlUp` excludes the reference's own declaring statement from scope (deliberately, so `local x = x`'s RHS reads the outer `x`), so an INNER `local x`'s own name resolves outward to the outer `x` and `isReferenceTo` reported it as a usage. Renaming the outer `x` of `local x = 1 / do local x = 2; print(x) end / print(x)` produced `local y = 1 / do local y = 2; print(x) end / print(y)` — the inner DECLARATION rewritten, its own usage left behind. Fixed by `LuaNameReference.shadowsRatherThanUses`: a **file-local** declaration site's own name is a new binding, never a use of the one it shadows. Restricted to file-local kinds because shadowing is lexical; a global declaration site is left alone, being §3.4 C4's ambiguity to report. TC-03, mutation-proved (it is the only test in 69 across rename/Find Usages/Safe Delete/shadowing that catches this). **RATIFIED at the Phase-2 review (2026-08-23):** the reviewer reproduced the defect and accepted `shadowsRatherThanUses` as this design's rule rather than an implementation workaround — the change is two logic lines, and `isFileLocal` is the *correct* predicate, not merely a convenient one: a Lua global is `_ENV.x`, so a second global declaration site is the SAME variable and must stay in the rename set (§3.4 C4 reports the ambiguity instead of dropping it), while the only forms that can introduce a shadowing binding — `local`, a parameter, a `for` variable and `local function` — are all file-local. This row is now the design; §2.5 carries the component. |
| `local function f` recursing into itself | `LuaLocalFuncDecl.processDeclarations` puts `f` in scope inside its own body (`LuaFunctionExt.kt:95-117`, whose step 3 at lines 115-116 executes the decl itself), so the recursive call is a real reference and is collected like any other. |
| Renaming to a name that is a Lua keyword | Blocked before the processor runs: `LuaNamesValidator.isIdentifier` (registered `plugin.xml:393-395`) gates the dialog's OK button. |
| Zero usages found for a claimed target | Not silently accepted: if the target is a `METHOD_FUNCTION` we refuse up front (§3.1 step 4); for every other kind zero usages is a legitimate outcome (an unused local). |
| Rename cancelled mid-search | **The invariant is stated over cost, not over a block count** — the count was restated wrongly three times and is now gone from the code (see `LuaRenameConflictDetector`'s KDoc and the block-audit note in `risks-and-gaps.md`). Every iteration block in §3.2/§3.4 whose body can **load** PSI, read VFS or query an index opens with `ProgressManager.checkCanceled()`; the rest do bounded work over lists a guarded block upstream already materialised, and are unguarded on purpose. `LuaRenameConflictTest`'s two `testCancellationIsChecked…` cases pin the property differentially — one over stub-hit count, one over usage count — so neither can be satisfied by an entry check alone. |
| `require "mod"` (paren-less) | The reference host is `LuaArgs` rather than `LuaTerminalExpr` (`LuaRequireReferenceContributor.kt:70-75`, `requireArgumentString`); §3.7 step 6 reads `element.string` on either. |

### 6.1 `t.field` for the four receiver shapes — DR-05

`canProcessElement` claims every `LuaNameRef`, so a member access reaches this processor and the
refusal has to be reliable. `risks-and-gaps.md` Gap 2.3 asked whether it is, or whether a member
access can resolve to something that merely *looks* like a declaration site. **Measured on
`4458a8b0` with a throwaway `BasePlatformTestCase` probe, one project per shape** (each shape in its
own `@Test` — a first run that shared one project across shapes leaked `function M.run()` from two
module fixtures into the control case and made it look broken; that reading was an artefact).

Every row records what `LuaNameReference.resolve()` returned and what
`substituteElementToRename` then did.

| # | Receiver shape | Fixture (caret on the member) | `resolve()` | Outcome |
| :-- | :--- | :--- | :--- | :--- |
| A | **local table** | `local t = {}` / `t.field = 1` / `print(t.fi<caret>eld)` | the `field` `LuaNameRef` of `t.field = 1`, via `LuaMemberFieldNavigation` | **Refused**, `refactoring.rename.unresolved` |
| B | **global table** | `t = {}` / `t.field = 1` / `print(t.fi<caret>eld)` | same, via `LuaMemberFieldNavigation` | **Refused**, `refactoring.rename.unresolved` |
| C1 | **`require`d module**, field, receiver renamed | `local m = require("mod")` / `print(m.fi<caret>eld)` | `null` — `multiResolve` empty | **Refused**, `refactoring.rename.unresolved` |
| C2 | **`require`d module**, dotted fn, receiver renamed | `local m = require("mod")` / `m.r<caret>un()` | `null` — `multiResolve` empty | **Refused**, `refactoring.rename.unresolved` |
| C3 | **`require`d module**, dotted fn, receiver same name | `local M = require("mod")` / `M.r<caret>un()` | `mod.lua`'s `LuaFuncDecl` | **Genuine `DOTTED_FUNCTION`** — `substituteElementToRename` returns `mod.lua`'s `run` leaf. But the caret cannot get there: see the call-site row in §6. |
| C4 | **`require`d module**, field, receiver same name | `local M = require("mod")` / `print(M.fi<caret>eld)` | `mod.lua`'s `field` `LuaNameRef` | **Refused**, `refactoring.rename.unresolved` |
| D1 | **`@class`**, `---@field` only | `---@class Config` / `---@field name string` / `local Config = {}` / `print(Config.na<caret>me)` | `null` | **Refused**, `refactoring.rename.unresolved` |
| D2 | **`@class`**, field also assigned | as D1 plus `Config.name = "x"` | the `name` `LuaNameRef` of the assignment | **Refused**, `refactoring.rename.unresolved` |

**Gap 2.3 is closed, and its worry did not materialise.** No shape resolves to something that looks
like a declaration site: `LuaMemberFieldNavigation.find` returns the member's `LuaNameRef`
(`memberFieldIdentifier`, `LuaMemberFieldNames.kt:24-28`), and `identifierLeafOf`'s `LuaNameRef`
branch is `element.identifier.takeIf { kindOf(it) != null }` — a member leaf's grandparent is a
`LuaIndexExpr`, which §3.5's table does not list, so `kindOf` is `null` and the branch yields `null`.
The dangerous neighbouring branch, `element is LuaVar -> element.nameRef?.identifier`, would have
returned the **receiver** leaf and renamed `t` when the user asked for `t.field`; it is never reached
from this path, because what resolution hands back is the `LuaNameRef`, not the `LuaVar`.

**One correction to Gap 2.3's predicted mechanism.** Gap 2.3 expected the general rule of §3.1
step 3 — "`identifierLeafOf(resolved)` or refuse" — i.e. the `refactoring.rename.unsupportedTarget`
branch. Measured, **every** refusal above is `refactoring.rename.unresolved` instead, and rows A, B,
C4 and D2 take a route the gap did not describe: `TargetElementUtil`'s `REFERENCED_ELEMENT_ACCEPTED`
succeeds and hands the processor the *declaration-site* `LuaNameRef` rather than the caret's usage,
so step 1's `identifierLeafOf` fails on that element, step 2 takes *its* reference, and step 3's
`resolve()` is what returns `null` (a member-field write target does not resolve to itself). The
outcome Gap 2.3 wanted holds; the branch that delivers it is one hop further along.


## 7. Integration Points

### plugin.xml

Exactly one line changes, inside the existing
`<extensions defaultExtensionNs="com.intellij">` block (currently `plugin.xml:389-390`):

```xml
<!-- REMOVE (Phase 2) -->
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaUnsupportedRenameProcessor"/>

<!-- ADD (Phase 2) — same extension point, same position, no order attribute -->
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor"/>
```

No other extension point is added. Specifically **no** `<lang.elementManipulator>` is registered
(§2.7), and **no** `<renameHandler>` — `PsiElementRenameHandler` and `VariableInplaceRenameHandler`
are the platform's own and need only `lang.refactoringSupport`, which is already registered at
`plugin.xml:384-386`.

The service of §2.9 is registered declaratively in the same file's `<extensions>` block:

```xml
<applicationService
        serviceImplementation="net.internetisalie.lunar.settings.LuaRefactoringSettings"/>
```

### LuaBundle.properties

Replace the interim key and add the rest
(`src/main/resources/net/internetisalie/lunar/LuaBundle.properties`):

```properties
# REMOVE with LuaUnsupportedRenameProcessor
# refactoring.rename.unsupported=…

refactoring.rename.unresolved=Cannot determine which declaration this name refers to, so its usages cannot be rewritten.
refactoring.rename.unsupportedTarget=Rename is not supported for this Lua element.
refactoring.rename.functionNameSegment=''{0}'' is the receiver part of a function name, not the function''s own name. Renaming it would change this declaration and leave every ''{0}.…'' call site bound to the old name.
refactoring.rename.colonMethod=Renaming a ''function Obj:method()'' declaration is not supported yet: calls written ''obj:method()'' are not resolved, so they would be left bound to the old name.
refactoring.rename.conflict.capture=Renaming to ''{0}'' would bind a usage of ''{1}'' to a different declaration that is already visible here.
refactoring.rename.conflict.shadow=The renamed declaration would shadow this existing reference, changing which value it reads.
refactoring.rename.conflict.globalExists=A global named ''{0}'' already exists in this project; renaming would merge the two.
refactoring.rename.conflict.ambiguousGlobal=''{0}'' is declared in {1} places; while more than one declaration exists its usages do not resolve, so they will not be rewritten.
```

### Existing subsystems touched

| Subsystem | Interaction |
| :--- | :--- |
| Find Usages (`LuaFindUsagesProvider`) | Delegates classification to `LuaDeclarationSite`; gains `DOTTED_FUNCTION`. `LuaFindUsagesTest` / `LuaFindUsagesCrossFileTest` are the regression gate. |
| Safe Delete (`LuaSafeDeleteProcessor`) | Delegates `declarationNodeFor`/`identifierLeafFor` to `LuaDeclarationSite`, **and replaces its enumerated `isElevatedDeclaration` with the §2.6a round trip**. This subsystem is widened by Phase 1 whether or not that is intended — `isSafeDeleteAvailable` and `canFindUsagesFor` are the same predicate (`LuaRefactoringSupportProvider.kt:30`) — so §2.6a is a Phase 1 `[Must]`, not a follow-up. `LuaSafeDeleteTest` is the regression gate and TC-32/TC-33 are the new cases; the class had no global or dotted fixture before them. |
| Reference search (`LuaNameReferenceSearcher`) | `isNameDeclarationLeaf` deleted; gate widened to accept a declaration `LuaNameRef` composite and an elevated declaration node, both normalised to the leaf, with the label exclusion kept **ahead** of normalisation as unreachable defence-in-depth (§3.8 ①). Specified in **§3.8**; `LuaLabelRenameTest` / `LuaFindUsagesTest` / `LuaFindUsagesCrossFileTest` / `LuaSafeDeleteTest` are the regression gates. |
| Resolution (`LuaNameReference`) | `declarationIdentifier` gains the dotted case. **This can move inferred types** — the corpus sweep (`test -PwithCorpus`) is a required gate for the phase that ships it. |
| Global indexing (`LuaGlobalAssignmentIndex`, `LuaGlobalAssignmentNavigation`) | Gain the Lua 5.5 `global` declaration forms; `getVersion()` 3→4 (§2.10). **This changes resolution** — additively — so `LuaGlobalCreationInspectionTest`, `LuaUndeclaredVariableInspectionTest` and `LuaCrossFileGlobalResolutionTest` join the corpus sweep as Phase 1 gates. |
| Name suggestion (`LuaNameSuggestionProvider`) | Unchanged; already keyed on `LuaAttName`/`LuaLocalVarDecl`, which the rename dialog passes as `nameSuggestionContext`. |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
| :--- | :---: | :--- |
| `REFACT-01-01` Rename a local + all references | M | §2.2, §3.1, §3.2, §3.3 |
| `REFACT-01-02` Invoked from a usage site | M | §3.1 steps 2-3 |
| `REFACT-01-03` Scope-exact under shadowing | M | §3.2 (search delegates isolation to `LuaNameReference.isReferenceTo`); §6 |
| `REFACT-01-04` Rename a parameter | M | §3.5 row 12, §3.3 |
| `REFACT-01-05` Rename a `for` control variable | S | §3.5 rows 3 and 13; §3.3 step 4 (leaf swap covers the wrapper-less numeric form) |
| `REFACT-01-06` `local function f` incl. recursive self-call | M | §3.5 row 8, §6 |
| `REFACT-01-07` Rename a global across files | M | All four ways Lua declares a global, each with its own §3.5 row and TC: `function greet()` (row 9, TC-08); bare assignment `config = {}` (row 14 + `isGlobalAssignmentTarget`, TC-27); Lua 5.5 `global x = 1` (row 5, TC-28); Lua 5.5 `global function f()` (row 7, TC-29). Project scope is kept by §3.2 step 2 (`isFileLocal = false`); §2.10 makes the 5.5 forms cross-file resolvable; C4 (§3.4) reports the multi-declaration case rather than half-applying it. §5 Example 2 |
| `REFACT-01-08` Method / dotted member declaration | S | §3.5 row 10 + `identifierLeafOf` row 9 / `declarationIdentifier` fix (dotted form, TC-09); §3.1 step 4 (colon form refused, DR-03); §3.1 step 4a (the receiver/intermediate segments of `function M.run()` / `function A.B.run()` refused rather than half-renamed — TC-34a/b) |
| `REFACT-01-09` Table field / constructor key | C | §6 — refused loudly by §3.1 step 3 / `canProcessElement`. Deferred: `risks-and-gaps.md` TBD-1 |
| `REFACT-01-10` Valid, non-reserved identifier | M | `LuaNamesValidator`, unchanged (§1 Prior Art) |
| `REFACT-01-11` Validity tracks the language level | C | Not delivered here — root cause is in `lua.flex`, owned by REFACT-05. `risks-and-gaps.md` TBD-3 |
| `REFACT-01-12` In-place rename | S | §2.6 |
| `REFACT-01-13` Dialog with preview and Find Conflicts | S | Platform-supplied once §2.2 lands; `showRenamePreviewButton` left at its `true` default; conflicts fed by §3.4 |
| `REFACT-01-14` Conflict detection before applying | S | §2.3, §2.4, §3.4 |
| `REFACT-01-15` Search in comments and strings | C | **Split across two phases, and only the Phase-7 half is the requirement.** §2.9 writes out all six accessors. Five of them (the persisted `isTo…`/`setTo…` pairs and `getElementToSearchInStringsAndComments`) deliver the requirement in Phase 7 — TC-13c is the accessor-level case, TC-13e the end-to-end one. The sixth, `getQualifiedNameAfterRename`, delivers **nothing** to this requirement and ships in **Phase 2** as a defect guard: the dialog's checkbox is reachable the moment `LuaRenameProcessor` is registered (`RenameDialog.java:279-282, 405`), and without the override one click drives `RenameUtil.java:226`'s `LOG.error`. TC-13d is that gate. |
| `REFACT-01-16` Propagates into LuaCATS annotations | S | §2.8, §3.6 (`@param` half). `@class`/`@alias` half deferred: `risks-and-gaps.md` TBD-2 |
| `REFACT-01-17` Rename a `::label::` | C | Delegated to REFACT-04; `canProcessElement` excludes `LuaLabelName`/`LuaLabelRef` so the working path is untouched |
| `REFACT-01-18` File rename updates `require(...)` | S | §2.7, §3.7 |
| `REFACT-01-19` Renaming `...` or `self` | W | `self` inside `function T:m()` refused by §3.1 step 4 — it resolves to the method-name leaf, which is `METHOD_FUNCTION` (TC-19a asserts the refusal **message**, the only assertion that distinguishes the branch); `...` never claimed by §3.0 (TC-19b); the legal explicit `self` parameter still renames (TC-19c). §6 and the note under §3.1 |
| `REFACT-01-20` Dynamic access out of reach | W | §2.9 default rationale; §6; mitigated by `-15` and the `-13` preview, as the requirement states |

## 9. Alternatives Considered

**A. Substitute to the `LuaNameRef` composite instead of the IDENTIFIER leaf.**
Then `RenameUtilBase.doRenameGenericNamedElement` works unmodified (`LuaNameRef.setName` exists) and
`renameElement` needs no override. Rejected: the numeric-`for` variable has **no** `LuaNameRef`
(`lua.bnf:152`), so this shape cannot express the numeric-`for` variable at all; and it would put
rename on a different key from Find Usages and Safe Delete, which both key on the leaf — the exact
mismatch that produced BUG-457.

**B. Register a `<lang.elementManipulator>` for `LuaNameRef` (and for the `require` hosts).**
Rejected for `LuaNameRef`: `LuaNameRefElementImpl.setName` already does the identical AST swap, so a
manipulator would be a second implementation of one operation. Rejected for the `require` hosts: the
new name arrives *with its `.lua` extension* and the reference range *includes the quotes*, so a
manipulator's `handleContentChange(element, range, newContent)` would splice `newmod.lua` in place
of `"app.util"` and produce `require(newmod.lua)`. The extension-stripping and delimiter-preserving
logic has to live in `handleElementRename` regardless, at which point the manipulator adds nothing.
This directly revises the assumption recorded in `REFACT-01-18` — see `risks-and-gaps.md` RD-1.

**C. Generalise `LuaScopeProcessor` into a multi-name "which declaration is nearest" processor.**
The first draft of §3.4 needed to know whether the renamed declaration or an existing `newName`
declaration was *nearer* to a site, which no existing processor can answer. That would have meant
extracting the declaration enumeration out of `LuaScopeProcessor` (a class on the hot resolution
path used by every reference, completion and inspection in the plugin) into a shared
`LuaDeclaredNames`. Rejected once the conflict rules were restated as **"is any `newName` visible
here at all"** (C1) and **"is the renamed declaration visible from this foreign reference"** (C2):
both are single existing `LuaScopeProcessor` crawls. The conservative form over-reports in benign
cases, which a confirmable conflicts dialog is designed for, and it removes a high-blast-radius
refactor of core resolution from the plan.

**D. Implement `findExistingNameConflicts` instead of `findCollisions`.**
Rejected: `RenameProcessor.preprocessUsages` calls `findExistingNameConflicts` on the **EDT**
(`RenameProcessor.java:171`, reached from `BaseRefactoringProcessor` line 354 after the modal
progress ends), while `findCollisions` runs inside `ReadAction.computeBlocking(this::findUsages)`
(line 303). A project-wide index lookup on the EDT is a `SlowOperationsException` risk and a direct
violation of the engineering contract §1.

**E. Resolve `obj:method()` calls by receiver type so REFACT-01-08's colon form could ship here.**
Rejected as scope: it requires `LuaTypeManager.resolveType` / `LuaClassType.resolveMember` plumbing
into `LuaNameReference`, which is type-engine work with its own corpus-sweep exposure. Refusing the
colon form loudly is correct and cheap; DR-03 tracks the follow-up.

## 10. Open Questions

_None — feature has cleared the planning bar._
