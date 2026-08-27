---
id: "REFACT-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "REFACT-07"
priority: "high"
folders:
  - "[[features/refactoring/07-inplace-rename/requirements|requirements]]"
---

# Technical Design: REFACT-07 — In-place (Inline) Rename

## 1. Architecture Overview

### Provenance of the platform citations

Every `platform/…` and bare `InplaceRefactoring.java:NNN` citation in this document was resolved
against the local `intellij-community` checkout at `~/Documents/src/lua/intellij-community`,
branch `master`, commit `5ba8ab1cfe37` (2026-05-04). **Lunar compiles and runs its unit tests
against GoLand `2026.1.3`** (`gradle.properties:18-19`), which is a different build of the same
sources.

Two consequences bind every reader of this design:

- **A line number may be off by a few lines in the compiled platform.** Cite-and-verify by symbol
  name, not by offset, when a citation does not land.
- **A behavioural claim resolved from that checkout is not evidence about the build Lunar runs.**
  This is why §3.3's chain is executed by DR-01 rather than accepted from the trace, alongside the
  "Read, not run" rule. `risks-and-gaps.md` Risk 1.3 carries it.

### Current state

`LuaRefactoringSupportProvider` (`plugin.xml:384-386`) answers `true` from
`isInplaceRenameAvailable` for a file-local declaring `LuaNameRef`
(`LuaRefactoringSupportProvider.kt:66-69`). The platform therefore selects
`VariableInplaceRenameHandler` for <kbd>Shift+F6</kbd>, that handler constructs a
`VariableInplaceRenamer`, and `performInplaceRefactoring` returns `false` at
`checkLocalScope()` — so `doRename` falls through to `performDialogRename`
(`VariableInplaceRenameHandler.java:112-122`) and the user gets the ordinary dialog.

`isMemberInplaceRenameAvailable` answers `true` only for `LuaLabelName`
(`LuaRefactoringSupportProvider.kt:71-74`), which is why REFACT-04's labels have a working
inline template today and nothing else does.

### What the platform actually hands a rename handler

Neither in-place gate is asked about the element under the caret. Both read
`PsiElementRenameHandler.getElement(dataContext)` — `VariableInplaceRenameHandler.java:34`, whose
result is the `element` argument of the `isAvailable` at `MemberInplaceRenameHandler.java:33-48`
and, via `invoke` (`VariableInplaceRenameHandler.java:55-79`), the `elementToRename` argument of
`doRename` at `MemberInplaceRenameHandler.java:51`. Both gates therefore run **before** any
`substituteElementToRename`.

In a real editor that key is lazy-computed as
`TargetElementUtil.findTargetElement(editor, getReferenceSearchFlags(), caret.offset)`
(`TextEditorPsiDataRule.kt:63-64` → `getPsiElementIn` at `:183-192`). `getReferenceSearchFlags()`
starts from `getAllAccepted()` (`TargetElementUtil.java:76-82`), so `REFERENCED_ELEMENT_ACCEPTED`
is set and `doFindTargetElement` takes the reference branch first (`TargetElementUtilBase.java:235-239`),
which resolves through `ref.resolve()` (`:173-183`). Only when that yields nothing does it fall
through to `ELEMENT_NAME_ACCEPTED` and `getNamedElement` (`:244-246` → `:105-126`). Lunar registers
no `targetElementEvaluator`, so no extension intervenes at either hook
(`getReferencedElement` `:176-181`, `findTargetElement` `:299-302`).

For Lua that makes the caret positions **structurally different elements**, and the split is not
declaration-versus-usage. It is whether a reference resolves at the offset:

| Caret | What the reference at the offset resolves to | `CommonDataKeys.PSI_ELEMENT` | Measured at |
| :--- | :--- | :--- | :--- |
| a `local` or global declaration whose name is not already in scope — `local coun\|ter = 0`, `con\|fig = {}` | nothing: `LuaResolveUtil.scopeCrawlUp` passes `prev`, the child it ascended from, as the block's `lastParent` (`LuaResolveUtil.kt:13-20`), and `LuaBlock.processDeclarations` breaks at `statement.textOffset >= lastParent.textOffset` (`LuaBlockExt.kt:32-36`) — so the declaring statement is not in its own scope | falls through to `getNamedElement`, which walks up to the nearest `PsiNamedElement` whose `getTextOffset()` is the leaf's start (`TargetElementUtilBase.java:116-122`) — the declaring **`LuaNameRef`** | DR-05 probes `a`, `e` |
| a `local` declaration whose name **is** already in scope — `config = 1` ⏎ `local con\|fig = 2` | the *earlier* declaration, which is a different declaration on a different line from the one under the caret | that earlier declaration's IDENTIFIER **leaf**, because the reference branch wins (`TargetElementUtilBase.java:235-239`) | DR-05 probe `a2` |
| a **parameter** declaration — `local function f(\|a)` | the parameter's own declaration | the parameter's IDENTIFIER **leaf** | DR-05 probes `d`, `d2`, `d3` |
| a usage — `print(coun\|ter)` | the declaration's IDENTIFIER **leaf**: every assignment to `LuaScopeProcessor.result` is an `.identifier` and none is a composite — enumerate them with `grep -nE '^ *result *=' src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt` and read each right-hand side, because the implicit-`self` assignment spans several lines and the single-line form `grep -n "result = "` misses it — and `LuaNameReference.kt:268-269` records it in words: *"Phase-1 (local) resolution returns the IDENTIFIER leaf"* | that **leaf** | DR-05 probes `b`, `c` |
| a numeric-`for` variable — `for i\| = 1, 10 do` | not established | **null** — the data context supplies no element, so `RenameHandlerRegistry` returns an empty handler list and <kbd>Shift+F6</kbd> reaches no handler at all | DR-05 probes `f`, `f2`, `f3` |

The right-hand column is **measured**, per caret, with nothing injected into the data context —
`risks-and-gaps.md` DR-05, "Table 1". The middle column is a **read** trace of how the platform got
there, and it is not established for the parameter and numeric-`for` rows; where the two disagree
the measured column governs.

Row 2 is shipped behaviour that predates this feature and that this feature does not change: it is
recorded as an observation in `risks-and-gaps.md` under DR-05 and, for the more serious
shadowed-`local` variant DR-01 measured, as Gap 2.21 — not designed for here.

This table is DR-05's measurement. **§3.2's split table is the complete per-kind mapping**, adding
the generic-`for` and `local function` kinds DR-01 probe (b) measured; read membership off that one,
so that there is a single list.

A leaf is a platform `LeafPsiElement`: neither a `PsiNameIdentifierOwner` nor a `LuaNameRef`, and
§3.1 cannot make it one. **So no platform in-place handler is available from a caret whose data
context supplies the leaf — a usage caret and a parameter declaration caret alike — and no widening
of `isMemberInplaceRenameAvailable` can change that**, because the `instanceof` gate at
`MemberInplaceRenameHandler.java:46` runs before the provider is consulted. That is measured too:
`MemberInplaceRenameHandler().isAvailableOnDataContext` is `false` at probes `b`, `c` and `d`. §3.5
is the mechanism that serves those carets, and `REFACT-07-11` is one of the requirements it
delivers.

The shipped `LuaInplaceRenameTest` cannot see any of this: it builds its context with
`SimpleDataContext…add(CommonDataKeys.PSI_ELEMENT, element)` where `element` is
`leafAtCaret().parent as LuaNameRef` (`LuaInplaceRenameTest.kt:106-116`, `:123-126`), so every
case — including `testInplaceRenameIsWithheldFromAUsageSite` at `:96-104` — asserts against an
element the platform would never have supplied. §6 replaces that.

### The single missing primitive

`LuaNameRefElement` is declared `PsiNamedElement` (`LuaBaseElements.kt:75`). `LuaLabelName`'s
`LuaNameDeclElement` is declared `PsiNameIdentifierOwner` (`LuaBaseElements.kt:51`). Both in-place
routes require the latter:

| Route | Requires `PsiNameIdentifierOwner` at | Failure mode today |
| :--- | :--- | :--- |
| `MemberInplaceRenameHandler` | `isAvailable` (`MemberInplaceRenameHandler.java:46`) and `doRename` (`:56`) | closed — the handler declines and the dialog opens |
| `VariableInplaceRenameHandler` | `InplaceRefactoring.getNameIdentifier` (`InplaceRefactoring.java:596-598`) and `InplaceRefactoring.getVariable` (`:646-653`) | open — `getSelectedInEditorElement` logs at `:860` and returns null, and `LOG.error` does not stop production |

The primitive is available for the cost of one method. `LuaNameRefElementImpl.getName()` already
computes it — `findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)?.text`
(`LuaBaseElements.kt:81`) — and the generated `LuaNameRefImpl.getIdentifier()` returns the same
node `@NotNull` (`LuaNameRefImpl.java:30-34`). `LuaNameDeclElementImpl.getNameIdentifier()` is
already exactly `findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)` (`LuaBaseElements.kt:59`).

### The route decision

**Route B — `MemberInplaceRenameHandler` / `MemberInplaceRenamer` — is chosen.** The grounds are
below, and each is a difference in what the user gets, not a preference.

**Ground 1 — Route A's commit path is not a rename.** `VariableInplaceRenamer.performRefactoringRename`
calls `renameSynthetic(newName)` (`VariableInplaceRenamer.java:467`), and `renameSynthetic` is an
empty method (`:447-448`). The only other work it does is run `AutomaticRenamerFactory` extensions
(`:469-506`), of which Lunar registers none. The file changes because the *template's* document
edits changed it. `LuaRenameProcessor.renameElement` is never called, so `---@param` propagation
(`REFACT-01-16`) and the non-cancelable write section REFACT-01 built for BUG-468 are both absent.
`MemberInplaceRenamer.performRefactoringRename` (`:250-307`) instead calls
`performRenameInner(substituted, newName)` at `MemberInplaceRenamer.java:283`, and that method
(`:309-317`) is `createRenameProcessor(element, newName)` followed by `renameProcessor.run()` — a real `RenameProcessor`, which routes to `LuaRenameProcessor`
through the registered `renamePsiElementProcessor` (`plugin.xml:389-390`).

**Ground 2 — Route A needs a second PSI change that Route B does not.**
`InplaceRefactoring.checkLocalScope()` reads `PsiSearchHelper.getInstance(project).getUseScope(element)`
and returns null unless it is a `LocalSearchScope` (`InplaceRefactoring.java:283-290`), and
`performInplaceRefactoring` aborts on null (`:236-240`). `MemberInplaceRenamer` overrides
`checkLocalScope()` to return the editor's own PSI file without consulting the use scope at all
(`MemberInplaceRenamer.java:105-111`). Grounded separately: `PsiSearchHelperImpl.getUseScope`
composes `element.getUseScope()`, the `UseScopeEnlarger` extensions and the scope optimizers
(`PsiSearchHelperImpl.java:143-166`) and never consults `PsiNameIdentifierOwner` — so under Route A
the two blockers are independent and **both** edits would be required.

**Ground 3 — enabling both routes silently selects Route A.**
`RenameHandlerRegistry.doGetRenameHandlers` removes `MemberInplaceRenameHandler` from the candidate
map whenever it holds more than one entry (`RenameHandlerRegistry.java:114-119`). Neither handler
implements `TitledHandler`, so `getHandlerTitle` falls back to `renameHandler.toString()`
(`:139-145`) — distinct per instance, so two available handlers really are two map entries. Route B
is only reachable if `isInplaceRenameAvailable` returns `false`.

**Ground 4 — Route B is the only route reachable from a usage caret.** A usage caret's element is
the declaration IDENTIFIER leaf, which fails `VariableInplaceRenameHandler.createRenamer`'s
unchecked cast to `PsiNamedElement` (`VariableInplaceRenameHandler.java:149-151`) as surely as it
fails `MemberInplaceRenameHandler`'s `instanceof`. Route B is the one that can be **delegated to**
after normalising: `MemberInplaceRenameHandler.doRename(elementToRename, editor, dataContext)`
(`MemberInplaceRenameHandler.java:51-89`) is `public`, takes the element as an argument rather than
re-reading the data context, and performs its own substitution and renamer construction —
`createMemberRenamer` keeps the owner and the substituted element as separate arguments (`:101-105`).
So a handler that hands it the declaring `LuaNameRef` gets the whole of Route B without subclassing
it. §3.5 depends on that shape, and §2.3 explains why it delegates rather than subclasses.

**Ground 5 — Route B is the route that already works in this plugin.** `LuaLabelName` reaches an
inline template through `isMemberInplaceRenameAvailable` today, against this platform build. The
shape is proven here, not inferred from another plugin.

**What Route B costs.** `MemberInplaceRenamer.findCollision()` returns null (`:113-117`), so
conflicts are not shown as an inline popup; they surface through `RenameProcessor`'s conflicts
dialog instead, driven by **`RenameProcessor.preprocessUsages`** reading
`LuaRenameProcessor.findCollisions` — `RenameUtil.addConflictDescriptions` builds the conflict map
at `RenameProcessor.java:170` and the refusal is raised at `:180`. **Measured, by stack trace
against the compiled build** (DR-04): the frame is `RenameProcessor`'s override, not
`BaseRefactoringProcessor.processConflicts` (`:540`) or `showConflicts` (`:797`), which a reading
of the reference checkout offers as plausible and which are **not on this path**. Anything asserting
on Lunar's conflicts through either of those seams asserts on a frame that does not run. That is the same rule set and the same wording, in a different
container. It is accepted, and `REFACT-07-08` is written against the rules rather than the
container.

### Prior art in this repo

| Component | file:line | This design |
| :--- | :--- | :--- |
| `LuaRefactoringSupportProvider.isInplaceRenameAvailable` | `LuaRefactoringSupportProvider.kt:66-69` | **REPLACED.** The predicate's *expression* is reused verbatim in `isMemberInplaceRenameAvailable`; the method itself must return `false`, per Ground 3. §3.2. |
| `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` | `LuaRefactoringSupportProvider.kt:71-74` | **EXTENDED.** The `LuaLabelName` clause stays and gains a second clause. §3.2. |
| `LuaNameRefBaseImpl` | `LuaBaseElements.kt:95-106` | **EXTENDED.** Gains the `PsiNameIdentifierOwner` supertype and one method. §3.1. |
| `LuaNameDeclElementImpl.getNameIdentifier` | `LuaBaseElements.kt:59` | **REUSED AS THE SPECIFICATION.** §3.1's body is the same expression; it is not shared through a common base because the two mixins have different supertypes and merging them would put `PsiNameIdentifierOwner` on `LuaLabelRefBaseImpl` too (§3.1, "Why not the interface"). |
| `LuaDeclarationSite.kindOf` / `LuaDeclarationKind.isFileLocal` | `LuaDeclarationSite.kt:15-49` | **REUSED UNCHANGED.** The one classifier; this feature adds no second predicate. |
| `LuaRenameProcessor` | `refactoring/rename/LuaRenameProcessor.kt` | **EXTENDED, by one normalisation.** Route B's commit runs it, and DR-01 measured it arriving with a `LuaNameRef` **composite** where the dialog path gives it a leaf, which silences its `---@param` clause. §2.5 and §3.6 specify the fix. The rename rules themselves are untouched. |
| `LuaRenameConflictDetector` | `refactoring/rename/LuaRenameConflictDetector.kt:120-131` | **REUSED UNCHANGED.** Reached via `LuaRenameProcessor.findCollisions`. |
| `LuaNamesValidator` | `refactoring/LuaNamesValidator.kt:12-25`, `plugin.xml:393-395` | **REUSED UNCHANGED.** Reached via `InplaceRefactoring.isIdentifier` → `LanguageNamesValidation` (`InplaceRefactoring.java:832-834`). |
| `LuaNameReferenceSearcher` | `lang/insight/LuaNameReferenceSearcher.kt:44-79` | **REUSED UNCHANGED.** Already normalises a `LuaNameRef` composite to its leaf at `:57`, which is what makes `InplaceRefactoring.collectRefs` (`:319-332`) find the usages. |
| `LuaInplaceRenameHandler` | — | **NEW**, and the only new production class. §2.3, §3.5. Nothing in this repo does its job: Lunar registers no `com.intellij.renameHandler` today (`grep -c renameHandler src/main/resources/META-INF/plugin.xml` is `0`). |
| `LuaInplaceRenameTest` | `refactoring/rename/LuaInplaceRenameTest.kt` | **REPLACED.** Every case is retargeted from `VariableInplaceRenameHandler` to the registry and to `MemberInplaceRenameHandler`, and its class KDoc — which documents the un-shipped Route A analysis — is rewritten. §6. |

### Evidence class of the behavioural claims

grep settles existence; it does not settle behaviour. Every behavioural claim in this design is
labelled here.

| Claim | Evidence |
| :--- | :--- |
| Which element `CommonDataKeys.PSI_ELEMENT` holds at each caret position | **Executed** — DR-05, 2026-08-26 on `f3a270fb`, from a real editor data context with nothing injected. The per-caret result is the "Measured at" column of the caret table above; the raw rows are in `dr-05-evidence/measured-rows.txt`. It is the premise the route decision and `REFACT-07-11` both rest on. |
| §3.5's handler and the platform's `MemberInplaceRenameHandler` can never both be available | **Executed.** DR-05 executed the structural premise — no caret supplies an element that is both a `LuaNameRef` and an IDENTIFIER leaf — across every caret in its table; **DR-02 executed the handler-availability half**, with §3.1's interface and §2.3's handler present, in the unit-test application *and* in a running GoLand. At no caret, in either application, in either predicate state, were both available. |
| No registered `renameHandler` other than the platform's two in-place handlers is available for a Lua editor caret — the premise `REFACT-07-02` needs and pairwise disjointness does not supply | **Executed by DR-02**, against the live `RenameHandler.EP_NAME` list in a running GoLand 2026.1.3 — **34** registrations, of which 31 answered `isRenaming` = `false` at all three carets. The three that ever answered `true` are the platform's two in-place handlers and Lunar's own. The premise **holds for this build**; it is not thereby invariant, and Risk 1.9 remains the residual for a different IDE or a user's third-party plugin. |
| `MemberInplaceRenameHandler` declines for a Lua `LuaNameRef` today, and the dialog opens | **Executed** — recorded in [[REFACT-01]] `risks-and-gaps.md` Gap 2.20, driven on the builder against `9c6d3b3d`. |
| A `getUseScope()` override flips `performInplaceRename()` to `true` and the template then empties the usage segments | **Executed** — Gap 2.20's probe table, same commit, fixture `local coun<caret>ter = 0` / `print(counter)` / `counter = counter + 1`. Consumed as an input; not re-derived. |
| `LuaNameRef` is not a `PsiNameIdentifierOwner` and `getNameIdentifier()` is therefore null | **Executed** — Gap 2.20, same run. |
| Adding `PsiNameIdentifierOwner` to `LuaNameRefBaseImpl` makes `getNameIdentifier()` non-null and lets `buildTemplateAndStart` create a primary variable | **Executed** — DR-01 probe (a): `supplied is PsiNameIdentifierOwner` = `true`, `nameIdentifier` = `LeafPsiElement(LuaTokenType.IDENTIFIER)` `'counter'` `(6,13)`, and `nameIdentifier === leafAtCaret` = `true`. Probe (d) then shows the template built from it: `variable[0]` = `PrimaryVariable range=(6,13)` with three `OtherVariable` segments. The read that predicted it — `buildTemplateAndStart` (`InplaceRefactoring.java:345-367`) into `addVariable` (`:805-818`), where `element == selectedElement` yields `PRIMARY_VARIABLE_NAME` — is kept because it is the reason. Raw rows in `dr-01-evidence/measured-rows.txt`. |
| `PsiElementRenameHandler.canRename` does not refuse Lunar's substituted IDENTIFIER leaf | **Executed** — DR-01 probe (g): `substituteElementToRename(2-arg)` returns `LeafPsiElement LuaTokenType.IDENTIFIER (6,13)`, the 3-arg form `returned-normally`, and the substitution callback **`FIRED`** with that same leaf — which is the observation a silent refusal would have withheld. `doRename` then yields a `TemplateState` with `currentVariableRange=(6,13)`. The `hasRenameProcessor` clause (`PsiElementRenameHandler.java:150-159`) is satisfied by `LuaRenameProcessor.canProcessElement` (`LuaRenameProcessor.kt:86-91`), which is why. |
| Route B is reachable only when `isInplaceRenameAvailable` is `false` | **Executed by DR-02**, in a running GoLand. With the shipped predicate, the declaration caret is claimed by `VariableInplaceRenameHandler` **and** `MemberInplaceRenameHandler`, and the removal loop (`RenameHandlerRegistry.java:114-119`) deletes the latter, so `getRenameHandlers` returns Route A alone. With the predicate at `false`, one handler remains and it is the `MemberInplaceRenameHandler`. |
| Route B's commit reaches `LuaRenameProcessor.renameElement` | **Executed** — DR-01 instrumented `renameElement` and recorded the call: `element=LeafPsiElement(LuaTokenType.IDENTIFIER) text='a' range=(36,37) kind=PARAMETER newName=count usages=1`, with the resulting document `---@param count number\nlocal function f(count) return count end\n` (probe f4) — the `---@param` movement only that path produces. The same probe recorded the composite arrival the dialog path does not produce, `element=LuaNameRefImpl(NAME_REF) … kind=null`, which is what §2.5 and §3.6 normalise. Read frames: `MemberInplaceRenamer.java:250-307` and `:309-317`. |
| The `PsiNameIdentifierOwner` change moves no behaviour Lunar ships a feature for, and every platform behaviour it does move is accepted | **Executed** — DR-03, base `f6148451` against treatment `8bbb7032` on identical fixtures. Not "inert": three consumers change, and `requirements.md`'s `REFACT-07-12` names them. §4 carries the per-consumer verdict, including the rows that are NOT RUN. |
| `IdentifierUtil.getNameIdentifier` returns the same leaf before and after the change | **Executed** — DR-03, identical leaf and range for every `LuaNameRef` measured on both commits. The read that predicted it, kept because it is the reason: before, the `PsiNamedElement` branch (`IdentifierUtil.java:17-25`) does `findElementAt(getTextOffset() - getTextRange().getStartOffset())`, which for a `LuaNameRef` whose only child is the IDENTIFIER is offset 0, and the text equals the name. After: the `PsiNameIdentifierOwner` branch (`:13-15`) returns the same node. |
| Lunar registers no `com.intellij.rename.inplace.resolveSnapshotProvider`, so `mySnapshot` is null | **Read** — `grep -c resolveSnapshotProvider src/main/resources/META-INF/plugin.xml` is `0`; the extension point name is `VariableInplaceRenamer.java:88-90`. Consequence: no automatic re-qualification of a reference the rename would capture — which is correct here, because `REFACT-07-08` refuses such a rename instead. |
| `LuaRenameCollisionUsageInfo` is discoverable as an `UnresolvableCollisionUsageInfo` | **Read** — `LuaRenameConflictDetector.kt:54-60` extends it, and `getShortDescription()` defaults to `StringUtil.stripHtml(getDescription(), false)` (`UnresolvableCollisionUsageInfo.java:22-24`), so Lunar's `getDescription()` override supplies both forms. |

## 2. Core Components

The existing files edited are those §2.1, §2.2 and §2.5 name; one production class is added
(§2.3); and one `plugin.xml` registration is added for it (§2.4). There is no new extension point,
no new ID, and no new group.

### 2.1 `net.internetisalie.lunar.lang.psi.LuaNameRefBaseImpl` (edit)

- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt`
- **Responsibility**: make the `LuaNameRef` composite satisfy the platform's
  `PsiNameIdentifierOwner` contract, which is the sole precondition both in-place routes impose.
- **Threading**: pure PSI reads; the caller holds read access. No I/O, no index, no resolution.
  `docs/engineering-contract.md` §1 binds here — *"Read PSI/VFS in `runReadAction { }`"* — and is
  satisfied by adding no work of our own.
- **Memory**: no field is added, so no `Project`/`Editor`/`PsiFile` reference is retained.
  `docs/engineering-contract.md` §4 binds and is satisfied trivially.
- **Signature after the edit**:

```kotlin
open class LuaNameRefBaseImpl(
    node: ASTNode,
) : LuaNameRefElementImpl(node),
    PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)

    override fun getReference(): PsiReference? { /* unchanged */ }
}
```

`getName()` and `setName(String)` are inherited unchanged from `LuaNameRefElementImpl`
(`LuaBaseElements.kt:81-92`) and already satisfy the rest of the interface.

### 2.2 `net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider` (edit)

- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaRefactoringSupportProvider.kt`
- **Responsibility**: select exactly one in-place route for a Lua caret.
- **Threading**: called on the EDT from `VariableInplaceRenameHandler.isAvailable`
  (`VariableInplaceRenameHandler.java:45-52`) and its `MemberInplaceRenameHandler` override
  (`MemberInplaceRenameHandler.java:33-48`), and from rename-action update. Must not resolve,
  index or touch VFS. `LuaDeclarationSite.kindOf` is a pure ancestor-shape test
  (`LuaDeclarationSite.kt:43-49`), so this holds.
- **Signatures after the edit**, in the shape `ktlintCheck` accepts. This is not a style
  preference: the single-expression form of the second predicate trips `argument-list-wrapping`
  and `max-line-length`, so **paste this, do not re-flow it by eye.** It is the shape a
  `ktlintFormat` pass produced on the builder and then verified with `ktlintCheck` at 0
  violations (`risks-and-gaps.md` DR-01, "Unexecutable as written"). Indentation is the real
  file's — these are class members.

```kotlin
    override fun isInplaceRenameAvailable(
        element: PsiElement,
        context: PsiElement?,
    ): Boolean = false

    override fun isMemberInplaceRenameAvailable(
        elementToRename: PsiElement,
        context: PsiElement?,
    ): Boolean =
        elementToRename is LuaLabelName ||
            (
                elementToRename is LuaNameRef &&
                    LuaDeclarationSite
                        .kindOf(
                            elementToRename.identifier,
                        )?.isFileLocal == true
            )
```

`getIntroduceVariableHandler()` and `isSafeDeleteAvailable()` are untouched.

### 2.3 `net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler` (new)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameHandler.kt`
- **Responsibility**: accept the one element the platform hands a rename handler that no platform
  in-place handler will accept — a Lua declaration IDENTIFIER **leaf** — and hand the declaring
  `LuaNameRef` to a `MemberInplaceRenameHandler`. This is the whole of `REFACT-07-11`; §3.5 is the
  algorithm.
- **It implements `com.intellij.refactoring.rename.RenameHandler` directly and does NOT extend
  `MemberInplaceRenameHandler`.** That is a decision, with a cost, and both are specified below
  under "Why not a `MemberInplaceRenameHandler` subclass". Alternative I records the rejected form.
- **Threading**: `isAvailableOnDataContext` is called on the EDT from
  `RenameHandlerRegistry.doGetRenameHandlers` (`RenameHandlerRegistry.java:106-110`, through
  `RenameHandler.isRenaming`'s default at `RenameHandler.java:23-25`) for **every** rename-action
  update and every <kbd>Shift+F6</kbd> in every language. It must be a pure PSI shape test: an
  element-type comparison plus `LuaDeclarationSite.kindOf`, which is the same work the shipped
  predicate already does per keystroke of the rename action's update. No resolution, no index, no
  VFS. `docs/engineering-contract.md` §1 binds and is satisfied.
- **Memory**: no field holds a `Project`, `Editor`, `PsiFile` or `VirtualFile`. The one field is a
  `ThreadLocal<Boolean>` on the companion, holding a flag and nothing else.
  `docs/engineering-contract.md` §4 binds and is satisfied.

```kotlin
package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaNameRef

class LuaInplaceRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (reentrancyGuard.get()) return false
        val currentEditor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        if (!currentEditor.settings.isVariableInplaceRenameEnabled) return false
        return declaringNameRefOf(PsiElementRenameHandler.getElement(dataContext)) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
        dataContext: DataContext?,
    ) {
        val context = dataContext ?: return
        val currentEditor = editor ?: return
        val nameRef = declaringNameRefOf(PsiElementRenameHandler.getElement(context)) ?: return
        startTemplateOn(nameRef, currentEditor, context)
    }

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext?,
    ) {
        // An inline template needs an editor; the non-editor entry point is not this handler's.
    }

    private fun startTemplateOn(
        nameRef: LuaNameRef,
        editor: Editor,
        dataContext: DataContext,
    ) {
        reentrancyGuard.set(true)
        try {
            MemberInplaceRenameHandler().doRename(nameRef, editor, dataContext)
        } finally {
            reentrancyGuard.remove()
        }
    }

    private fun declaringNameRefOf(element: PsiElement?): LuaNameRef? {
        val leaf = element ?: return null
        if (leaf.node?.elementType != LuaElementTypes.IDENTIFIER) return null
        if (LuaDeclarationSite.kindOf(leaf)?.isFileLocal != true) return null
        return leaf.parent as? LuaNameRef
    }

    private companion object {
        val reentrancyGuard: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    }
}
```

Every detail of that body listed here is load-bearing and must not be "simplified":

- **`val leaf = element ?: return null` is not redundant.** Testing `element?.node?.elementType`
  directly does not smart-cast `element` to non-null, so the following `kindOf(element)` call — whose
  parameter is a non-null `PsiElement` (`LuaDeclarationSite.kt:43`) — would not compile.
- **The explicit `IDENTIFIER` test is not subsumed by `kindOf`.** `kindOf` returns `LABEL` for a
  `LuaLabelName` *before* it tests the element type (`LuaDeclarationSite.kt:44-45`), and `LABEL` is
  `isFileLocal` (`:28`). Without the explicit test a label composite would pass both remaining
  checks and only the final safe cast would stop it — which works, but by accident, and it would put
  Lunar's handler in the map for the same context as the platform's for `REFACT-07-13`'s input.
- **`as?`, not `as`.** It asserts `LuaDeclarationSite.kindOf`'s own invariant — that every kind
  this feature is in scope for is classified through a `LuaNameRef` parent — and it is what would
  exclude a numeric-`for` leaf handed in directly. It is **not** what keeps the numeric-`for`
  variable out of the template: the data context supplies no element at that caret at all. See §3.5
  and §8's `REFACT-07-14` row.
- **`currentEditor.settings.isVariableInplaceRenameEnabled`** (`EditorSettings.java:176`) is the
  read `MemberInplaceRenameHandler.isAvailable` performs at `MemberInplaceRenameHandler.java:44`,
  restated here because a directly-implementing handler inherits no such gate. A user who has
  switched inline rename off must still get the dialog.
- **`reentrancyGuard` is not defensive programming; it replaces a platform guard this class no
  longer inherits.** See "Why not a `MemberInplaceRenameHandler` subclass", cost 1.
- **`invoke`'s four parameters are the interface's, not a design choice.**
  `RefactoringActionHandler.invoke(Project, Editor, PsiFile, DataContext)`
  (`RefactoringActionHandler.java:37`) fixes the arity, so
  `docs/engineering-contract.md` §5's ≤3-argument tripwire does not apply to it. It **does** apply
  to `startTemplateOn` and `declaringNameRefOf`, which take three and one.

**Kotlin nullability of the overrides.** `RefactoringActionHandler` annotates only `project`
(`RefactoringActionHandler.java:37`, `:48`), so `editor`, `file` and `dataContext` arrive as
platform types and the implementer chooses. They are declared **nullable** here, matching the
in-tree Kotlin implementation `PsiSourcedPolySymbolRenameHandler.kt:21` / `:31`; a non-null
declaration would put a Kotlin null-check intrinsic on a path the interface's own KDoc says may
pass null (*"can be `null` for some but not all of refactoring action handlers"*,
`RefactoringActionHandler.java:34-35`). `elements` is `PsiElement @NotNull []` (`:48`), hence
`Array<out PsiElement>`.

**Why not a `MemberInplaceRenameHandler` subclass.** Subclassing would inherit `isAvailable` /
`doRename` and cost less code. It is rejected because
`RenameHandlerRegistry.doGetRenameHandlers`'s removal loop deletes the first map entry that is
`instanceof MemberInplaceRenameHandler` and `break`s (`RenameHandlerRegistry.java:114-119`,
verified exact) whenever the map holds more than one entry (`:111-113`) — and **a subclass
satisfies that test.** Where the data context supplies the leaf — a usage caret or a parameter
declaration caret — the platform's own `MemberInplaceRenameHandler` is not
available (§3.5), so Lunar's would be the only member-inplace instance in the map and therefore the
one deleted: `REFACT-07-11` and `REFACT-07-09` would silently not work whenever anything else is
renaming at that caret. Implementing the interface directly puts Lunar's handler outside the loop's reach. Alternative I
records the rejection; §3.5 states the premise that remains, and the residual that remains with it.

**What implementing the interface directly forfeits, itemised.** Each cost is real and each is
paid explicitly above.

| # | Inherited behaviour forfeited | How it is paid |
| :--- | :--- | :--- |
| 1 | `VariableInplaceRenameHandler.isAvailableOnDataContext`'s re-entrancy guard: a `private static ThreadLocal` (`VariableInplaceRenameHandler.java:29`) read at `:39-41`, set by `performDialogRename` at `:130` and cleared at `:140`, which is what stops the registry re-selecting an in-place handler while that handler is falling back to the dialog | `reentrancyGuard`, set around the delegation. It **cannot** be delegated to the platform's flag: the only public reader, `getInitialName()` (`:144-147`), returns null when the flag holds `""` — which is exactly what `MemberInplaceRenameHandler.doRename`'s fall-through sets, because it passes `initialName = null` at `:87`. The re-entrant path is real: `doRename` calls `performDialogRename` at `:69` when the template does not start, and `performDialogRename` re-queries `RenameHandlerRegistry.getRenameHandler(dataContext)` at `:131` |
| 2 | `isAvailable`'s `element == null \|\| !element.isValid()` rejection (`VariableInplaceRenameHandler.java:46`) | `declaringNameRefOf` returns null for a null element; validity is re-established by `LuaDeclarationSite.kindOf`'s PSI reads, which is the same work the shipped predicate does |
| 3 | `editor.getSettings().isVariableInplaceRenameEnabled()` (`MemberInplaceRenameHandler.java:44`) | restated in `isAvailableOnDataContext` |
| 4 | `VariableInplaceRenameHandler.invoke`'s active-lookup fallback, which resolves an element when the data context supplies none (`:57-74`), and `MemberInplaceRenameHandler.isAvailable`'s equivalent at `:39-41` | **Deliberately not replicated.** Both look for a `PsiNamedElement` ancestor, which for Lua is the `LuaNameRef` a declaration caret already supplies; there is no element this handler wants that the data context does not give it, and `isAvailableOnDataContext` has already returned `false` when `getElement` is null |
| 5 | `checkAvailable`'s `LOG.error("Recursive invocation")` re-dispatch (`VariableInplaceRenameHandler.java:93-107`) | **Not replicated.** `invoke` re-derives the element from the data context and returns silently when the gate no longer holds, which is the same outcome without a logged error |
| 6 | the non-editor `invoke(Project, PsiElement[], DataContext)` (`VariableInplaceRenameHandler.java:82-91`) | **A no-op**, as in `PsiSourcedPolySymbolRenameHandler.kt:31-32`. `isAvailableOnDataContext` requires `CommonDataKeys.EDITOR`, so this handler is never the one the Project-view rename selects; that context reaches `PsiElementRenameHandler` and the dialog, which is `REFACT-07-11`'s scope boundary |
| 7 | `CodeInsightTestUtil.tryInlineRename`'s parameter type is `VariableInplaceRenameHandler` (`CodeInsightTestUtil.java:236`), so this class **cannot be passed to it** | §6 prints the replacement helper TC-04 uses. The helper drives `invoke` — the entry point `BaseRefactoringAction.performRefactoringAction` calls (`:172`) — which is *closer* to <kbd>Shift+F6</kbd> than `tryInlineRename`'s direct `doRename` call (`:246`) |
| 8 | `doRename`'s own `performDialogRename` fallback when the template does not start | **Retained.** The delegation calls `MemberInplaceRenameHandler.doRename`, which performs it at `:69` and `:87` |

Nothing about the **template** changes: the delegation reaches the same `MemberInplaceRenamer`
through the same `MemberInplaceRenameHandler.doRename` frames §3.3 steps 3-8 describe. What changes
is only which class the registry holds.

### 2.4 `plugin.xml` — one added registration

One registration is added and nothing else changes:

| Extension point | Implementation | Where |
| :--- | :--- | :--- |
| `com.intellij.renameHandler` | `net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler` | new element, adjacent to `renamePsiElementProcessor` at `plugin.xml:389-390` |

```xml
<renameHandler implementation="net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler"/>
```

The extension point is declared at
`platform/refactoring/resources/META-INF/RefactoringExtensionPoints.xml:11`, with interface
`com.intellij.refactoring.rename.RenameHandler` (`RenameHandler.java:13`). It takes no `language`
attribute and no `id`, and it declares no ordering: `doGetRenameHandlers` iterates
`RenameHandler.EP_NAME.getExtensionList()` and keys a `TreeMap` on `getHandlerTitle`
(`RenameHandlerRegistry.java:104-110`), so declaration order in `plugin.xml` is not the order the
registry sees. **Nothing in this design may rest on that order** — in particular the removal loop
at `:114-119` walks the map in title order, and `getHandlerTitle` falls back to
`renameHandler.toString()` for a handler that is not a `TitledHandler` (`:139-145`), which is an
identity hash. §3.5 states the availability invariant that replaces an ordering assumption, and the
premise it needs.

The remaining registrations already exist and are correct:

| Extension point | Implementation | Line |
| :--- | :--- | :--- |
| `com.intellij.lang.refactoringSupport` (`language="Lua"`) | `net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider` | `plugin.xml:384-386` |
| `com.intellij.renamePsiElementProcessor` | `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` | `plugin.xml:389-390` |
| `com.intellij.lang.namesValidator` (`language="Lua"`) | `net.internetisalie.lunar.refactoring.LuaNamesValidator` | `plugin.xml:393-395` |
| `com.intellij.referencesSearch` | `net.internetisalie.lunar.lang.insight.LuaNameReferenceSearcher` | `plugin.xml:382-383` |

Both **platform** rename handlers are registered by the platform itself
(`platform/platform-resources/src/META-INF/LangExtensions.xml:1108-1109`) and neither is
re-registered here.

**Deliberately not registered**: `com.intellij.rename.inplace.resolveSnapshotProvider`
(`VariableInplaceRenamer.java:88-90`). Its job is to re-qualify references a rename would capture,
so that the rename can proceed anyway. Lua has no qualification syntax for a local, and
`REFACT-07-08` requires such a rename to be *refused*. Registering one would contradict the
requirement.

### 2.5 `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` (edit)

- **File**: `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt`
- **This is a latent `REFACT-01` defect, not an in-place workaround, and the distinction is
  load-bearing.** `renameElement` classifies its `element` argument with
  `LuaDeclarationSite.kindOf` (`LuaRenameProcessor.kt:229` — pre-§3.6 line numbers, from DR-01's measurement on `bbed46c3`; the site is `:255-256` today and now normalises), which returns null for anything whose
  node type is not `IDENTIFIER` (`LuaDeclarationSite.kt:45`). **Any** caller that hands
  `renameElement` a `LuaNameRef` composite therefore loses the `---@param` clause. Nothing did
  until now only because the dialog path substitutes to the leaf first
  (`substituteElementToRename`, `:105-117`); REFACT-07 is the first caller that does not. A reader
  who files this as in-place-specific will remove the normalisation the next time the in-place path
  changes — it must survive REFACT-07 entirely.
- **Responsibility of the edit**: classify and rewrite from the declaration IDENTIFIER **leaf**,
  whatever shape the caller supplied, so that both routes reach identical rules. That is
  `requirements.md`'s "one source of truth for the rewrite" behaviour rule, and `REFACT-07-05`'s
  byte-identical requirement, applied to the processor's own entry point.
- **Threading**: none added. `renameElement` already runs inside the platform's write action, and
  `identifierLeafOf` is a pure PSI read (`LuaDeclarationSite.kt:57-75`).
- **Memory**: no field added; `LuaDeclarationSite` stays stateless.
- **Algorithm**: §3.6. **Scope of the change across the repo**: §3.6's call-site audit.

## 3. Algorithms

### 3.1 Granting `PsiNameIdentifierOwner` to `LuaNameRef`

**Site.** `nameRef ::= IDENTIFIER { mixin="net.internetisalie.lunar.lang.psi.LuaNameRefBaseImpl" implements="net.internetisalie.lunar.lang.psi.LuaNameRefElement" }`
(`lua.bnf:169-172`). The generated `LuaNameRefImpl extends LuaNameRefBaseImpl implements LuaNameRef`
(`LuaNameRefImpl.java:14`) is the only generated class that extends the mixin — verified by
`grep -rln "extends LuaNameRefBaseImpl" src/main/gen/`, which lists that file alone.

**Method body.** `findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)`. It is nullable by the
interface's own contract, and null is the honest answer for a malformed node with no IDENTIFIER
child. For a well-formed `LuaNameRef` it is the same node `LuaNameRefImpl.getIdentifier()` returns
`@NotNull` via `findNotNullChildByType(IDENTIFIER)` (`LuaNameRefImpl.java:30-34`).

**Why not gate the return on `LuaDeclarationSite.kindOf`.** Returning the leaf only for declaration
sites and null for reads would narrow the blast radius, and it is rejected: `getNameIdentifier()`
is a structural accessor, and a *usage* `LuaNameRef` genuinely does have a name identifier — the
leaf whose text is its name. Making a structural accessor depend on a semantic classification puts
a resolution-shaped question behind an accessor that the platform calls from paint-time paths, and
it makes `getName()` and `getNameIdentifier()` disagree for the same node. The narrowing that
matters is on the **availability predicate** (§3.2), where it is a decision about the feature
rather than a lie about the tree.

**Why not the interface.** Adding `PsiNameIdentifierOwner` to `interface LuaNameRefElement`
(`LuaBaseElements.kt:75`) would be the tidier Kotlin, and it is rejected: `labelRef` also declares
`implements="net.internetisalie.lunar.lang.psi.LuaNameRefElement"` (`lua.bnf:247-250`), so
`LuaLabelRef` — every `goto` target — would become a `PsiNameIdentifierOwner` too, widening the
audit surface in §4 for no gain. That is not a judgement about how many classes each currently
reaches: it is that `LuaNameRefElement` is declared as the `implements=` of both `nameRef`
(`lua.bnf:171`) and `labelRef` (`lua.bnf:249`), while `LuaNameRefBaseImpl` is the `mixin=` of
`nameRef` alone (`lua.bnf:170`), so putting the supertype on the interface necessarily reaches
`goto` targets and putting it on the mixin necessarily does not. Both sets are regenerated from
`lua.bnf`, so state the `.bnf` declarations, not a class count.

**Why no `.bnf` edit and no regeneration.** The platform tests the interface with `instanceof`
(`MemberInplaceRenameHandler.java:46`, `InplaceRefactoring.java:597`,
`SafeDeleteProcessor.java:117`, and every site in §4), which a mixin supertype satisfies. Adding
`implements="com.intellij.psi.PsiNameIdentifierOwner"` to `lua.bnf:169` would additionally put the
supertype on the generated `LuaNameRef` *interface*, buying Kotlin-side smart casts at the price of
a `src/main/gen` regeneration. Lunar's own call sites do not need the smart cast — §3.2's predicate
tests `LuaNameRef` and reads `.identifier`, not `.nameIdentifier` — so the regeneration buys
nothing this feature uses. If a later feature wants the static type, that is a separate, headless
change (`.claude/skills/generate-parser/scripts/generate.sh`).

### 3.2 Route selection

`isMemberInplaceRenameAvailable(elementToRename, context)` returns `true` iff **either**:

1. `elementToRename is LuaLabelName` — REFACT-04's clause, unchanged; **or**
2. `elementToRename is LuaNameRef` **and** `LuaDeclarationSite.kindOf(elementToRename.identifier)?.isFileLocal == true`.

`isInplaceRenameAvailable(element, context)` returns `false` unconditionally.

Each clause of (2) is load-bearing and each has its own test case:

- **`is LuaNameRef`** excludes the declaration IDENTIFIER **leaf**. `MemberInplaceRenameHandler.doRename`
  casts `elementToRename` to `PsiNameIdentifierOwner` (`MemberInplaceRenameHandler.java:65`), and
  `MemberInplaceRenamer`'s constructor takes a `PsiNamedElement` (`MemberInplaceRenamer.java:63`).
  A leaf is a platform `LeafPsiElement` and is neither. Gated by the retargeted leaf case in §6.
- **`kindOf(...) != null`** excludes a plain read, because `kindOf` of a usage leaf is null
  (`LuaDeclarationSite.kt:43-49`). **This predicate has nothing to do with `REFACT-07-11`.** A
  usage caret never reaches it: the element the data context supplies for such a caret is the
  declaration IDENTIFIER leaf (§1, "What the platform actually hands a rename handler"), which
  fails `MemberInplaceRenameHandler.isAvailable`'s `instanceof PsiNameIdentifierOwner` gate at
  `:46` before `isMemberInplaceRenameAvailable` is called at `:47`. `REFACT-07-11` is delivered by
  §3.5's handler instead. Gated by the retargeted read case in §6; TC-04 gates §3.5.

**Which declaration carets clause (2) actually serves.** Every `REFACT-07-01` kind has now been
measured, across DR-05 and DR-01 probe (b), and the split does not follow the kind's grammar — it
follows what the data context supplies:

| Kind | Context supplies | Served by |
| :--- | :--- | :--- |
| `local` variable declaration | the declaring `LuaNameRef` (DR-05 `a`) | **clause (2)** |
| global declaration | the declaring `LuaNameRef` (DR-05 `e`) | clause (2), and refused by its `isFileLocal` test — `REFACT-07-10` |
| generic-`for` variable | the declaring `LuaNameRef`, at all three placements measured (DR-01 `b4`, `b4b`, `b4c`) | **clause (2)** |
| **parameter** | the IDENTIFIER **leaf** (DR-05 `d`/`d2`/`d3`) | **§3.5's handler** |
| **`local function` name** | the IDENTIFIER **leaf** (DR-01 `b5`) | **§3.5's handler** |

So clause (2) serves `local`, global and generic-`for`; §3.5 serves parameter and `local function`.
`REFACT-07-01` is delivered by both mechanisms together, and §8 records that. Nothing here is Read,
not run: `b4b`/`b4c` exist because `b4`'s caret landed on the `,` leaf, and without a
multi-character fixture that row could not be told apart from a `findElementAt` off-by-one.
- **`isFileLocal == true`** excludes globals (`REFACT-07-10`). Gated by TC-10.
- **`?.` on `kindOf`** — `elementToRename.identifier` is `@NotNull` on the generated interface
  (`LuaNameRef.java:10-11`), so the safe call is on `kindOf`'s nullable result only.

**Null `context`.** The parameter stays nullable. `MemberInplaceRenameHandler.isAvailable` derives
it from `file.findElementAt(editor.getCaretModel().getOffset())` and then retries at `offset - 1`
(`MemberInplaceRenameHandler.java:32-38`), either of which can be null. It is unread by the
predicate; declaring it non-null would put a Kotlin null-check intrinsic on an availability path
the platform is entitled to call with null.

### 3.3 What the platform does with the two answers — the full path

Stated because a non-frontier implementer must be able to tell a working template from a template
that started and then corrupted, and the difference is which of these frames produced a non-null
value. Every frame is **read**; DR-01 executes the whole chain end to end.

0. `dataContext`'s `CommonDataKeys.PSI_ELEMENT` is the **declaring `LuaNameRef`**, per §1's table.
   This walkthrough is the path taken where the data context supplies the composite; §3.5 walks
   the path taken where it supplies the leaf, which enters at step 3 with the same substituted leaf.
1. <kbd>Shift+F6</kbd> is the `RenameElement` action (`keymaps/$default.xml:1005-1007` →
   `LangActions.xml:484` → `RenameElementAction`). Its `actionPerformed` does **not** call
   `RenameHandlerRegistry.getRenameHandler`; it collects `Renamer`s from the `RenamerFactory`
   extension point (`RenameElementAction.java:82-83`, `:126-128`), of which
   `RenameHandlerRenamerFactory` wraps **`getRenameHandlers(dataContext)`** — the list, after the
   removal loop — one `RenameHandler2Renamer` per handler
   (`RenameHandlerRenamerFactory.java:62-65`). One renamer runs directly
   (`RenameElementAction.java:111-113`); more than one raises a chooser popup (`:114-122`).
   `performRename` then reaches `BaseRefactoringAction.performRefactoringAction`, which calls
   `handler.invoke(project, editor, file, dataContext)` at `BaseRefactoringAction.java:172`.
   With §3.2's answers and §3.5's availability invariant **and its premise**, exactly one handler is
   in that list. `RenameHandlerRegistry.getRenameHandler` (`:71-92`) and its `HandlersChooser`
   dialog are reached only from `VariableInplaceRenameHandler.checkAvailable` (`:96`) and
   `performDialogRename` (`:131`), never from this action.
2. `MemberInplaceRenameHandler.isAvailable` (`:33-48`) has already passed: the element is a
   `PsiNameIdentifierOwner` after §3.1, and `isMemberInplaceRenameAvailable` is `true`.
3. `MemberInplaceRenameHandler.doRename` (`:51-89`) reads
   `RenamePsiElementProcessor.forElement(elementToRename)` — `LuaRenameProcessor`, because its
   `canProcessElement` claims Lua `LuaNameRef`s — checks `isInplaceRenameSupported()` (the base
   returns `true`, `RenamePsiElementProcessorBase.java:149-151`, and `LuaRenameProcessor` does not
   override it), then calls `substituteElementToRename(elementToRename, editor, pass)`. The
   three-argument overload is **not** overridden by `LuaRenameProcessor`; the base delegates to the
   two-argument one Lunar does override, and then gates the callback on
   `PsiElementRenameHandler.canRename(project, editor, psiElement)`
   (`RenamePsiElementProcessorBase.java:239-246`).

   **That gate is a frame that could block and does not.** `canRename` →
   `getRenameErrorMessage` refuses an element that is neither a `PsiNamedElement` nor claimed by a
   non-default rename processor (`PsiElementRenameHandler.java:150-159`). Lunar's substituted
   element is the IDENTIFIER **leaf**, which is not a `PsiNamedElement` — but
   `LuaRenameProcessor.canProcessElement` returns true for it (`LuaRenameProcessor.kt:86-91`, final
   clause `LuaDeclarationSite.kindOf(element) != null`), so `hasRenameProcessor` is true and the
   clause short-circuits. Had `canProcessElement` not claimed the leaf, Route B's callback would
   never fire and the template would silently never start.

   The pass therefore receives the declaration IDENTIFIER **leaf**.
4. `createMemberRenamer(element = leaf, elementToRename = LuaNameRef, editor)` builds
   `MemberInplaceRenamer(elementToRename, substituted = leaf, editor)`
   (`MemberInplaceRenameHandler.java:101-105`; ctor at `MemberInplaceRenamer.java:69-89` (`mySubstitutedRange` at `:76-84`)). The ctor
   records `mySubstitutedRange` as a greedy range marker over the leaf.
5. `performInplaceRename(names)` → `InplaceRefactoring.performInplaceRefactoring`
   (`InplaceRefactoring.java:205-262`):
   - `getReferencesSearchScope(file)` is overridden to `LocalSearchScope(currentFile)`
     (`MemberInplaceRenamer.java:200-204`).
   - `collectRefs` (`InplaceRefactoring.java:319-332`, extended at `MemberInplaceRenamer.java:173-183`)
     runs `ReferencesSearch.search(myElementToRename, …)`. `LuaNameReferenceSearcher` normalises the
     composite to its leaf (`LuaNameReferenceSearcher.kt:57`) and yields the file's usages.
   - `addReferenceAtCaret` (`:678-696`) adds nothing, because
     `LuaNameReference.isReferenceTo(myElementToRename)` is false against the composite: the
     reference compares identity against a leaf (`LuaNameReference.kt:266-271`) and additionally
     refuses a file-local declaration's own name (`:265`, `shadowsRatherThanUses` at `:189-192`).
     This is correct and is why step 6's `nameIdentifier` is the only source of the primary segment.
   - `checkLocalScope()` returns the editor's PSI file (`MemberInplaceRenamer.java:105-111`), so the
     `PsiSearchHelper` gate at `InplaceRefactoring.java:283-290` is never reached.
6. `buildTemplateAndStart` (`InplaceRefactoring.java:334-414`):
   - `getNameIdentifier()` (`MemberInplaceRenamer.java:120-154`, whose `super` call is at `:134`
     under the same-file guard at `:133`, → `InplaceRefactoring.java:596-598`) returns the
     IDENTIFIER leaf — **this is §3.1's whole
     contribution**.
   - `getSelectedInEditorElement(nameIdentifier, refs, stringUsages, offset)` (`:841-862`): the
     first loop misses (no ref contains the caret, per step 5), the `nameIdentifier` branch at
     `:851-854` hits because the caret is inside the declaration's identifier, and the `LOG.error`
     at `:860` is not reached.
   - the per-ref loop (`:351-361`) adds each usage as `OTHER_VARIABLE_NAME` depending on
     `PRIMARY_VARIABLE_NAME`.
   - `nameIdentifier != null` (`:362`) → `addVariable(nameIdentifier, selectedElement, builder)`
     (`:805-807` → `:809-818`), and because `element == selectedElement` the branch at `:810-812`
     creates `PRIMARY_VARIABLE_NAME`. A template with a current variable exists.
   - `startTemplate(builder)` runs inside the platform's own `WriteCommandAction` (`:409`).
7. On <kbd>Enter</kbd>: `VariableInplaceRenamer.performRefactoring` (`:549-577`) runs `findProblem()`
   in a non-blocking read action.
   - `isIdentifier(myInsertedName, myLanguage)` (`:342`) → `LanguageNamesValidation.isIdentifier`
     (`InplaceRefactoring.java:832-834`) → `LuaNamesValidator` — `REFACT-07-07`.
   - `findCollision()` is overridden to null on Route B (`MemberInplaceRenamer.java:113-117`), so
     conflicts are left to step 8.
   - a problem cancels the template and restores the document (`:569-572`).
8. `MemberInplaceRenamer.performRefactoringRename` (`:250-307`): `tryRollback()` (`:253`) restores
   the pre-template text, `getVariable()` and `getSubstituted()` re-derive the target, and
   `performRenameInner(substituted, newName)` — called at `:283`, defined at `:309-317` — runs a
   full `RenameProcessor` (`createRenameProcessor` at `:310`, `run()` at `:316`) — which reaches `LuaRenameProcessor.findCollisions` (`REFACT-07-08`) and
   `LuaRenameProcessor.renameElement`.

   **The whole of that chain is Executed, not read.** DR-04 captured the refusal's stack trace
   against the compiled build and it is this step verbatim, bottom-up:
   `performRefactoringRename` (`MemberInplaceRenamer.java:283`) → `performRenameInner` (`:316`) →
   `MyRenameProcessor.doRun` (`:417`) → `RenameProcessor.doRun` → `BaseRefactoringProcessor.doRun` →
   `RenameProcessor.preprocessUsages` (`RenameProcessor.java:180`), where the conflict is raised.
   `tryRollback()` is measured too: DR-04 probe (d) recorded the template's edit landing, the
   rollback removing it, and only then the conflict reaching the caller — so the ordering this step
   asserts holds, and the "document already changed when the conflict fires" failure does not
   occur.

   **`getSubstituted()` does not return the leaf step 3 produced, and that is measured, not read.**
   By the time `performRefactoringRename` calls it (`:264`) the original leaf has been invalidated
   by the template edit plus `tryRollback`, so control reaches the `mySubstitutedRange` branch
   (`MemberInplaceRenamer.java:367-372`), which re-derives the target with
   `PsiTreeUtil.findElementOfClassAtRange(psiFile, start, end, PsiNameIdentifierOwner.class)` —
   **and §3.1's interface grant is exactly what makes the Lua composite answer that query.**
   `renameElement` therefore receives a `LuaNameRefImpl` where the dialog path gives it a leaf.
   DR-01 measured it on the parameter fixture at range `(36,37)`: that query returns
   `LuaNameRefImpl`, whose `kindOf` is null, while `findElementAt(36)` is the leaf, whose `kindOf`
   is `PARAMETER`.

   **So `REFACT-07-09` does not follow from this step on its own.** It follows once §3.6's
   normalisation is in place. Without it the code renames correctly and the `---@param` tag does
   not move — the divergence `requirements.md` calls silent doc rot, and the one outcome it says is
   worse than having no in-place path at all. `risks-and-gaps.md` DR-01 carries the measurement and
   the platform line.
9. On <kbd>Esc</kbd>: `InplaceRefactoring`'s template listener reverts the document — `REFACT-07-06`.

   **This step is `Read, not run`, it stays that way, and no de-risking task covers it. That is a
   decision, not an omission.** `REFACT-07-06` is a `Must` and **TC-06 already tests it**
   (`requirements.md`, TC-06): it starts the template, asserts a template started, types into the
   current variable range, cancels with `gotoEnd(true)`, and asserts the document is the original.
   It is in Phase 2's fail-first list and carries a named mutation. A spike whose only job would be
   to predict what a required test will assert duplicates that test, so none is written.
   **If the platform does not in fact revert on <kbd>Esc</kbd>, TC-06 is where that surfaces** — at
   Phase 2's fail-first run or Phase 4's mutation — which is the cheap place for a wrong expectation
   to fail, and the reason this deferral is safe rather than an open gap. `risks-and-gaps.md` records
   the same disposition where DR-01 and DR-04 defer to each other.

   **Outcome, recorded in Phase 5: the platform does revert.** TC-06 passes, and the live driving
   the deferral was betting on confirms it end to end — <kbd>Shift+F6</kbd> on `local counter = 0`,
   `tot` typed into the template so all four segments read `tot`, then <kbd>Esc</kbd>: the document
   returned to `local counter = 0` / `print(counter)` / `counter = counter + 1`, md5 unchanged at
   `e13ea3dd80bb4182ba7494ef5b376db0` (`phase-5-live-evidence/04-esc-template-live-with-tot.png`,
   `05-esc-restores-byte-for-byte.png`). The `Read, not run` label above describes the decision
   taken **before** that run and is kept for that reason; the claim it deferred is now settled.

### 3.4 Failure ladder — what each outcome must look like

| Condition | Detected at | User-visible outcome | Document state |
| :--- | :--- | :--- | :--- |
| Caret is neither on an eligible declaration nor on a usage of one | §3.2's predicate, or §3.5's gate — whichever the caret's data-context element reaches | the ordinary rename dialog | unchanged |
| Caret is on a global, at its declaration or at a usage | the `isFileLocal` test — §3.2's clause or §3.5's step 2, per the row above | the ordinary rename dialog with preview | unchanged |
| Caret is on a numeric-`for` variable | nowhere in Lunar's code: the data context supplies **null**, so `RenameHandlerRegistry` returns an **empty** handler list and not even the platform's default `PsiElementRenameHandler` (DR-05 Table 2, probes `f`/`f2`/`f3`) | shipped behaviour, unchanged by this feature. What <kbd>Shift+F6</kbd> then does end to end was **not driven** — the measurement stops at the registry — so `human-verification-checklists.md` records it live rather than this table asserting it | unchanged |
| Template cannot start (`performInplaceRename` returns `false`) | `MemberInplaceRenameHandler.java:67-71` | the ordinary rename dialog | unchanged |
| New name is not a Lua identifier | step 7, `findProblem` | template cancelled, invalid-identifier UI | restored to the old name |
| New name collides | step 8, `RenameProcessor.preprocessUsages` (`RenameProcessor.java:180`) — **Executed**, DR-04 | conflicts dialog in the IDE; `ConflictsInTestsException` in unit-test mode. What the dialog *reads*, and whether its Continue button proceeds after the rollback, was **not driven** — `human-verification-checklists.md` covers it | restored to the old name **before the refusal reaches the caller** — measured, DR-04 probe (d), not inferred from the final text |
| `LuaRenameProcessor` refuses the target (`REFACT-01`'s reasons) | step 8, `renameElement` | the refusal message the dialog path shows | unchanged |
| User presses <kbd>Esc</kbd> | step 9 | template dismissed | restored to the old name |

There is no row in which the declaration and a usage disagree. That is the invariant this design
exists to preserve, and it is the one the un-shipped `getUseScope` override broke.

### 3.5 The carets whose data context supplies an IDENTIFIER leaf — `REFACT-07-11`, and the parameter declaration

**Scope of this section.** It carries every caret for which `CommonDataKeys.PSI_ELEMENT` is a Lua
declaration IDENTIFIER **leaf** rather than a `LuaNameRef` composite. §3.2's table is the
authoritative split; read the membership off it rather than off this paragraph, so that there is one
list and not two. Measured, the carets in this section's scope are:

- a **usage** caret — `REFACT-07-11` (DR-05 probes `b`, `c`);
- a **parameter declaration** caret (DR-05 `d`/`d2`/`d3`), and the answer is caused neither by the
  `---@param` tag — `d2` has none — nor by caret placement inside the token, which `d3` separates;
- a **`local function` name** caret (DR-01 `b5`), which supplies the leaf exactly as a parameter
  does.

The last two are `REFACT-07-01` kinds, so this section is not only `REFACT-07-11`'s mechanism. Every
test case and every probe on a caret in this list has to drive Lunar's handler; driving the
platform's measures its refusal of the leaf instead.

**The problem, restated as a fact about elements.** For `print(coun|ter)` and for
`local function f(|a)` alike the data context supplies a declaration's IDENTIFIER **leaf** (§1).
Every platform in-place handler refuses it: `isAvailable`
requires `element instanceof PsiNameIdentifierOwner` (`MemberInplaceRenameHandler.java:46`) and
`doRename` requires the same before it will do anything at all (`:56`, with the fall-through at
`:87` going straight to `performDialogRename`). §3.1 cannot help, because the leaf is a platform
`LeafPsiElement` and Lunar does not own its class. §3.2 cannot help, because the gate is upstream of
the provider call at `:47`.

**The mechanism.** `LuaInplaceRenameHandler` (§2.3) is registered as a
`com.intellij.renameHandler` (§2.4). It implements `RenameHandler` directly — §2.3 gives the reason
and the itemised cost — and it answers `true` for exactly the elements no platform in-place handler
will take, then normalises before **delegating** to a locally constructed
`MemberInplaceRenameHandler`:

```
declaringNameRefOf(element):
    leaf = element, or null when element is null
    if leaf.node?.elementType != LuaElementTypes.IDENTIFIER  -> null
    if LuaDeclarationSite.kindOf(leaf)?.isFileLocal != true   -> null
    return leaf.parent as? LuaNameRef        // null when the parent is not a nameRef
```

The Kotlin body is in §2.3, together with every detail of it that must not be simplified away.

Every step of `declaringNameRefOf` is load-bearing. The first two each exclude a specific
requirement's input; the third asserts an invariant that the data context never puts to the test:

| Step | Excludes | Requirement |
| :--- | :--- | :--- |
| `leaf.node?.elementType != IDENTIFIER` | the declaring `LuaNameRef` supplied at a `local` or global declaration caret (`NAME_REF`, DR-05 probes `a`/`e`), and a `LuaLabelName` composite — which `kindOf` would otherwise classify `LABEL` at `LuaDeclarationSite.kt:44`, before its own element-type test at `:45`, and `LABEL` is `isFileLocal` (`:28`) | `REFACT-07-02` (the availability invariant below), `REFACT-07-13` |
| `kindOf(...)?.isFileLocal != true` | a global's declaration leaf (`GLOBAL_VARIABLE.isFileLocal` is `false`, `LuaDeclarationSite.kt:24`), and every non-declaration leaf, whose `kindOf` is null | `REFACT-07-10` |
| `leaf.parent as? LuaNameRef` | the numeric-`for` variable: `numericForStatement ::= FOR IDENTIFIER '='` (`lua.bnf:152`) binds a bare leaf whose parent is the statement, so `kindOf` classifies it `NUMERIC_FOR_VARIABLE` — which **is** `isFileLocal` (`LuaDeclarationSite.kt:21`) and therefore passes step 2, so the safe cast is what would stop it | **none.** This step has no reachable input from the data context: at a numeric-`for` caret the platform supplies **null** (DR-05 probes `f`/`f2`/`f3`), so `declaringNameRefOf` returns at its first line and step 3 is never evaluated. `REFACT-07-14` is delivered by the platform supplying nothing — see §8. The cast stays as the invariant assertion argued below, and it would fire only for a caller that hands `declaringNameRefOf` such a leaf directly |

The cast is total for every in-scope kind: `LuaDeclarationSite.kindOf` reaches
`LOCAL_VARIABLE`, `PARAMETER`, `GENERIC_FOR_VARIABLE` and `LOCAL_FUNCTION` only through
`kindFromNameRefGrandParent`, which it calls after `if (parent !is LuaNameRef) return null`
(`LuaDeclarationSite.kt:48-49`). So for every kind this feature is in scope for, `leaf.parent`
**is** a `LuaNameRef`, by the classifier's own construction — the safe cast is an assertion of that
invariant, not a defensive branch, and `REFACT-07-14`'s input is the one case where it fires.

**The availability invariant, and what `REFACT-07-02` actually rests on.** Lunar's handler is
available only for an element whose node type is `IDENTIFIER`; the platform's
`MemberInplaceRenameHandler` is available only for a `PsiNameIdentifierOwner`. **No element is
both** — a Lua IDENTIFIER leaf is a `LeafPsiElement`, and §3.1 puts the interface on the
`LuaNameRef` composite, not on its child. So those two handlers are never both available: where the
data context supplies the `LuaNameRef` the platform handler takes it, and where it supplies the leaf
Lunar's handler takes it. Both shapes occur at declaration carets — probes `a`/`e` give the
composite, probe `d` gives the leaf — and **no measured caret supplied an element that was both**
(DR-05, "Table 1"), which is the invariant's structural premise, established by measurement rather
than by argument. That measurement was taken on the **unchanged** tree, so the consequence for
handler availability — with §3.1's interface and this handler present — was DR-02's to execute, and
**DR-02 executed it**: at a declaration caret, a usage caret and a parameter caret, in the unit-test
application *and* in a running GoLand, no caret in either predicate state had both handlers
available.
`VariableInplaceRenameHandler` stays out of the map because §3.2 makes `isInplaceRenameAvailable`
`false`.

**That pairwise fact is necessary and not sufficient, and the difference is `REFACT-07-02`.**
`RenameHandlerRegistry.doGetRenameHandlers` does not compare those two handlers. It iterates
**every** registered `RenameHandler` and puts each one whose `isRenaming(dataContext)` is true into
a `TreeMap` (`RenameHandlerRegistry.java:106-110`; `isRenaming` defaults to
`isAvailableOnDataContext`, `RenameHandler.java:23-25`). The single-entry early return at `:111-113`
therefore fires only when **no other registered handler at all** is renaming at that caret. So
`REFACT-07-02` rests on this premise, which `requirements.md`'s "Premises examined" table carries
and DR-02 executes:

> **No registered `renameHandler` other than the platform's two in-place handlers is available for
> a Lua editor caret.**

**How far this checkout grounds it, and where it stops.** `grep -rn "<renameHandler"
--include=*.xml platform/` returns the platform's own registrations
(`LangExtensions.xml:1106-1109` and `intellij.platform.polySymbols.backend.xml:62`), and each
declines a Lua editor caret for a stated reason:

| Registered handler | Why it is not available at a Lua editor caret |
| :--- | :--- |
| `PlainDirectoryRenameHandler` | `DirectoryRenameHandlerBase.isAvailableOnDataContext` requires the context element to adjust to a `PsiDirectory` (`DirectoryRenameHandlerBase.java:36-45`) |
| `FileDumbRenameHandler` | its `getElement` returns null as soon as `CommonDataKeys.EDITOR` is present (`FileDumbRenameHandler.kt:20-21`), and it additionally requires dumb mode and a registry key (`:43-48`) |
| `PsiSourcedPolySymbolRenameHandler` | requires a `PsiSourcedPolySymbol` in `CommonDataKeys.SYMBOLS` (`PsiSourcedPolySymbolRenameHandler.kt:34-39`); Lunar declares none — `grep -rl PolySymbol src/main/kotlin` returns nothing |
| `VariableInplaceRenameHandler` | §3.2 makes `isInplaceRenameAvailable` `false` |
| `MemberInplaceRenameHandler` | the pairwise disjointness above |

**This is `platform/` only, and Lunar does not run `platform/` alone.** Handlers registered by the
IDE's bundled plugins are not enumerable from this checkout — the same tree carries registrations
under `java/`, `plugins/kotlin/`, `plugins/groovy/` and others, and a GoLand build ships a
different subset again. **DR-02 enumerates the live list** — `RenameHandler.EP_NAME.getExtensionList()`
inside a running fixture — and records every handler whose `isRenaming` is true at each Lua caret.
Until it has, the premise is asserted, not established, and `REFACT-07-02` is conditional on it.

**What happens if the premise fails, and why Lunar's handler is not a `MemberInplaceRenameHandler`
subclass.** If a third handler is renaming at the same caret, the map holds more than one entry and
the removal loop at `:114-119` runs. It deletes the **first** entry that is
`instanceof MemberInplaceRenameHandler` and `break`s. The two caret groups of §1's table are not
symmetric under that loop:

- **Where the data context supplies the `LuaNameRef`** — a `local` or global declaration caret —
  the map entry is the *platform's* `MemberInplaceRenameHandler`, and the loop deletes it. The user
  gets the third handler. That is the platform's own behaviour for every language, it is Ground 3
  seen from the other side, and REFACT-07 neither causes it nor can prevent it.
- **Where the data context supplies the leaf** — a usage caret or a parameter declaration caret —
  the platform's handler is not available, so the only member-inplace instance
  in the map would be **Lunar's, if Lunar's were a subclass** — and the loop would delete it.
  `REFACT-07-11` would then be silently unreachable whenever anything else is renaming there. §2.3
  therefore implements `RenameHandler` **directly**, so no entry the loop can match belongs to
  Lunar, and Lunar's handler survives into the returned list. The remaining consequence is visible
  rather than silent: with two entries `RenameElementAction` shows its `Renamer` chooser popup
  (`RenameElementAction.java:111-122`) instead of starting the template, so `REFACT-07-02` is unmet
  and the user can still choose Lua's rename. `risks-and-gaps.md` Risk 1.9 carries that residual.

**What the template looks like.** `MemberInplaceRenameHandler().doRename(nameRef, …)` runs §3.3
steps 3-8 unchanged. Which segment is primary depends on whether a collected reference contains the
caret, at step 6's `getSelectedInEditorElement`:

- **From a usage caret**, its first loop matches (`InplaceRefactoring.java:846-849`), so the
  occurrence at the caret becomes `PRIMARY_VARIABLE_NAME` (`:351-357`) and the declaration's name
  identifier is added as a linked `OTHER_VARIABLE_NAME` (`:362-367`).
- **From a parameter declaration caret**, the caret sits in the declaration's own identifier, which
  is not a collected reference (§3.3 step 5), so the first loop misses and the `nameIdentifier`
  branch at `:851-854` answers — the same shape §3.3 step 6 describes for a `local` declaration
  caret. **Executed**: DR-01 probe (f) records exactly that shape — `variable[0]` =
  `PrimaryVariable range=(36,37)`, the parameter's own identifier, and `variable[1]` =
  `OtherVariable range=(46,47)`, its one use in `return a`.

The commit is identical in both — `performRenameInner` on the substituted declaration leaf — so
`REFACT-07-05` holds from either caret. `REFACT-07-03` is written against a caret whose primary
segment is the declaration's own identifier and is not contradicted by the usage case.

**Threading.** Nothing here is new work: the whole of `declaringNameRefOf` is a node-type test plus
`LuaDeclarationSite.kindOf`, and it runs on the EDT in the same frame the shipped predicate already
runs in.

**Evidence class.** Every claim in this section is now **Executed**. Which element the data context
supplies at each caret, and the pairwise availability invariant, are DR-05's, whose per-caret table
this section's scope is read off. The template shape from a parameter caret is DR-01 probe (f)'s.
The extension-list premise the invariant is not sufficient for is DR-02's, against the live
`RenameHandler.EP_NAME` list in a running GoLand. Both preceded Phase 3, which is where this handler
ships. Phase 5 then drove the usage-caret shape live: <kbd>Shift+F6</kbd> at a caret in
`print(counter)` opened the editable box on that occurrence with the declaration as a linked
segment, and committing rewrote the declaration too
(`phase-5-live-evidence/15-usage-caret-template.png`, `16-usage-caret-committed.png`).

### 3.6 Normalising `renameElement`'s element — the `kindOf` call-site audit

**The change.** `LuaRenameProcessor.renameElement` derives a declaration leaf once, at the top, and
uses it for the whole body:

```kotlin
    val declarationLeaf = LuaDeclarationSite.identifierLeafOf(element) ?: element
    val declarationKind = LuaDeclarationSite.kindOf(declarationLeaf)
    val oldName = declarationLeaf.text
    // …`preparedDeclarationRewrite(declarationLeaf, replacement)` and
    // …`LuaCatsParamRenamer.preparedRename(declarationLeaf, oldName, newName)`
```

`identifierLeafOf` is the repo's existing normaliser and is documented as total in both directions —
*"a leaf maps to itself, a declaration node or a `LuaNameRef` composite maps down to its leaf"*
(`LuaDeclarationSite.kt:52-56`). `LuaRenameProcessor` already routes through it at `:106` and
`:162`; `:229` is the one site in that class that calls `kindOf` on the raw argument.

Three details are load-bearing:

- **`?: element` is the fallback, not `?: return`.** `identifierLeafOf` answers null for an element
  that names no declaration — a *usage* `LuaNameRef`, whose `kindOf` is null at `:61`'s `takeIf`.
  Falling back to the raw element makes the change **behaviour-preserving for every caller that
  works today**: `kindOf` of that element was already null, and it stays null. A `return` would
  turn a rename that currently proceeds into a silent no-op.
- **The leaf is used for the rewrite too, not only for the classification.**
  `preparedDeclarationRewrite` does `element.parent.node.replaceChild(element.node, replacementNode)`
  (`LuaRenameProcessor.kt:447-455`) with an IDENTIFIER leaf as the replacement. Handed the
  composite, it replaces the whole `NAME_REF` node with a bare leaf — a tree shape the dialog path
  never produces, and one `nameRef ::= IDENTIFIER` (`lua.bnf:169`) says should not exist. DR-01
  asserted the resulting document **text**, not the tree, so the consequence of that shape is
  **Read, not run**; normalising first removes the question rather than answering it, and is what
  `REFACT-07-05`'s byte-identical requirement asks for anyway.
- **`LuaCatsParamRenamer.preparedRename` would tolerate either**, so this part is belt-and-braces
  rather than a second defect: it only does
  `PsiTreeUtil.getParentOfType(parameterIdentifier, LuaCatsCommentOwner::class.java, strict = false)`
  (`LuaCatsParamRenamer.kt:72-88`), and the composite's ancestor chain is the leaf's. **Read, not
  run.** Passing the leaf keeps one element flowing through the whole method.

**The call-site audit — every `LuaDeclarationSite.kindOf` caller in `src/main/kotlin`.** The
question each row answers is: *can this site receive a `LuaNameRef` composite where it previously
only saw a leaf, and does a null `kindOf` change what it does?* Reproduce the enumeration with
`grep -rn "kindOf(" src/main/kotlin/ | grep -v "fun kindOf"`.

| Call site | Argument | Verdict |
| :--- | :--- | :--- |
| `LuaRenameProcessor.kt:229` (pre-§3.6; `:255-256` today) `renameElement` | the raw `element` | **AFFECTED — this is the fix.** A composite silences the `---@param` clause at `:236`. Measured by DR-01. |
| `LuaRenameProcessor.kt:130` `findReferences` | the raw `element` | **AFFECTED, cost only.** A composite makes `kindOf` null, so the file-local narrowing is skipped and control falls to `super.findReferences` with the caller's scope. On Route B that caller is `MemberInplaceRenamer`, which supplies its own `LocalSearchScope` (`MemberInplaceRenamer.java:200-204`), so DR-01 measured identical *results*. Normalised for the same reason and in the same commit: it is one latent defect appearing at every row this table marks AFFECTED, and repairing some of them is the failure mode this repo has a rule about. **It gets no test case and no mutant**, because no measured path shows an observable difference; inventing an assertion to fill the row is what `requirements.md` forbids. |
| `LuaRenameProcessor.kt:90` `canProcessElement` | the raw `element` | **Safe.** `element is LuaNameRef || kindOf(element) != null` — the composite is admitted by the first disjunct. |
| `LuaRenameProcessor.kt:110` `substituteElementToRename` | `leaf`, from `identifierLeafOf` at `:106` | **Safe — already normalised.** |
| `LuaRenameProcessor.kt:163` `findCollisions` | `declarationLeaf`, from `identifierLeafOf` at `:162` | **Safe — already normalised.** This is the shape §3.6 copies. |
| `LuaRenameConflictDetector.kt:200` | `reference.identifier` | **Safe — normalised at the call.** |
| `LuaNameReference.kt:191` `shadowsRatherThanUses` | `host.identifier` | **Safe — normalised at the call.** |
| `LuaNameReferenceSearcher.kt:58` | `target`, from `identifierLeafOf` at `:57` | **Safe — already normalised.** |
| `LuaDeclarationSite.kt:60`, `:61` | inside `identifierLeafOf` itself | **Safe by construction** — `:61` is the branch that maps a composite down. |
| `LuaFindUsagesProvider.kt:35` `canFindUsagesFor`, `:37` `getType` | whatever the platform targets | **Not newly affected.** §3.1 does not change which element reaches them: `TargetElementUtil`'s `getNamedElement` keys on `PsiNamedElement`, which `LuaNameRef` already was (`LuaBaseElements.kt:75`), and DR-05 measured the composite being supplied at a declaration caret on the **unchanged** tree (probe `a`). Whether these sites are right *today* is a separate, pre-existing question that DR-03 owns; this feature does not move them. |
| `LuaRefactoringSupportProvider.kt:78` `isSafeDeleteAvailable` | the raw `element` | **Not newly affected**, on the same ground as the row above; §4's `SafeDeleteProcessor.isInside` row carries the Safe Delete side, and DR-03 executed it — Δ none, with the premise measured. |
| `LuaRefactoringSupportProvider.kt:69` | `element.identifier` | **Safe — normalised at the call.** |
| `LuaReceiverMemberIndex.kt:376`, `:409` | — | **Not this symbol.** A private `kindOf(assigned: LuaExpr?): LuaReceiverMember.Kind` declared in that file at `:517`; unrelated to `LuaDeclarationSite`. |

**Evidence class.** The divergence and the null `kindOf` are **Executed** (DR-01). The two
consequences of *not* normalising the rewrite — the replaced `NAME_REF` node, and
`preparedRename`'s tolerance of a composite — are **Read, not run**, and the design removes the
need to settle them rather than resting on them.

## 4. `PsiNameIdentifierOwner` consumer audit

Every site in the platform tree that mentions the interface, and what §3.1 does to Lunar's
behaviour there. **This table is `REFACT-07-12`'s exit criterion, so its enumeration must be
reproducible and must not depend on a syntactic form.**

**The search that produced it**, run in `~/Documents/src/lua/intellij-community` at
`5ba8ab1cfe37`:

```
grep -rl PsiNameIdentifierOwner --include=*.java --include=*.kt platform/ | LC_ALL=C sort
```

That is the **bare symbol name**, not `instanceof` / `is`. The narrower pair
(`grep -rn "instanceof PsiNameIdentifierOwner" --include=*.java platform/` plus
`grep -rn "is PsiNameIdentifierOwner" --include=*.kt platform/`) misses every `as?`, cast, `.class`
lookup, generic bound and typed parameter — the forms that supply half the rows below — so it must
not be used to establish this table. Re-running either form is how a reader reproduces or falsifies
it; the bare form is the one the criterion means. Two of the files it returns are the interface's own declaration
(`PsiNameIdentifierOwner.java:12`) and a `@see` in its supertype (`PsiNamedElement.java:14`), which
are declarations rather than consumers and carry no row.

**That search was scoped to `platform/` in a source checkout, and that is not the same set as the
consumers a GoLand build ships.** It excludes bundled plugins, and a source checkout carries classes
the shipped build does not. DR-03 re-enumerated against the compiled platform and found consumers
this table omitted and rows citing classes that are not there. **The corrected search, which is what
a reader should re-run**, is the grep above **plus** an enumeration of the shipped distribution:

```
# 1. the source checkout, as before — establishes the branch and the line
grep -rl PsiNameIdentifierOwner --include=*.java --include=*.kt platform/ | LC_ALL=C sort
# 2. the build Lunar actually runs — establishes which of them ship, and adds the bundled plugins
#    `platform/` cannot see. Over the IDE distribution's jars:
#      for each class file, javap -c and look for `instanceof`/`checkcast` on PsiNameIdentifierOwner
```

Step 2 is the one that matters for a verdict: a row citing a class absent from the build under test
is not a consumer, and a bundled plugin's consumer is one whether or not `platform/` mentions it.
**Rows below are annotated with which build they were verified against.**

**Every row is Read, not run until DR-03's column says otherwise; DR-03 executed the audit row by
row for every row this table carries — not for a subset chosen in prose — and its verdicts are in
`risks-and-gaps.md`. The rows it could not run are marked here, not only there.**

| Consumer | file:line | Effect on Lunar |
| :--- | :--- | :--- |
| `IdentifierUtil.getNameIdentifier` | `platform/analysis-impl/…/IdentifierUtil.java:13-15` | **Same answer, cheaper path.** The `PsiNamedElement` fallback at `:17-25` already returns the IDENTIFIER leaf for a `LuaNameRef` (its `getTextOffset()` is the leaf's start, so `findElementAt(0)` is the leaf, and its text equals `getName()`). Feeds identifier highlighting. |
| `SafeDeleteProcessor.isInside` | `platform/lang-impl/…/safeDelete/SafeDeleteProcessor.java:116-121` | **Newly taken; same verdict.** The branch only widens `isAncestor` when the owner is *not* an ancestor of `place` **and** `getNameIdentifier()` is not a descendant of the owner. For a `LuaNameRef` the identifier **is** its own child, so `PsiTreeUtil.isAncestor(ancestor, nameIdentifier, true)` is true and the `if` body at `:118-120` does not run. Lunar's Safe Delete target is the elevated statement node in any case (`LuaSafeDeleteProcessor`). |
| `PsiElement2Declaration.getIdentifyingElement` | `platform/lang-impl/…/model/psi/impl/PsiElement2Declaration.java:103-113` | **Newly taken; narrows the declaration range from the whole `LuaNameRef` to its identifier.** Those ranges are identical — `nameRef ::= IDENTIFIER` (`lua.bnf:169`) means the composite has exactly one child. Feeds Ctrl-hover and the symbol/target API. |
| `NamedElementDuplicateHandler` | `platform/lang-impl/…/editor/actions/NamedElementDuplicateHandler.java:74-80` | **CHANGED — measured.** Not "on a selection": the branch runs only when there is **no** selection, over the caret's whole line (`:44-56`). The document is byte-identical in every case; the **resting caret** moves to the duplicated line's first Lua name — `counter = helper(1, 2)` 151 → 129, `print(counter)` 158 → 144, and unchanged on `local counter = 0`, whose line starts with a keyword. Accepted, and `REFACT-07-12` names it; a `human-verification-checklists.md` item covers it because it is user-visible on a keystroke unrelated to rename. **Do not re-probe this with a selection**: `hasSelection()` short-circuits the branch, so a selection-based probe measures nothing and reports inert — DR-03's first attempt did exactly that, and a "no difference" from it would have been a false negative. |
| `RelatedItemLineMarkerProvider` | `platform/lang-api/…/RelatedItemLineMarkerProvider.java:36-40` | **CHANGED — measured.** This row was signed off on a premise that is **false**: it claimed "Lunar registers no `RelatedItemLineMarkerProvider`", and Lunar does — `LuaOverrideLineMarkerProvider : RelatedItemLineMarkerProvider()` (`LuaOverrideLineMarkerProvider.kt:26`, NAV-05), registered at `plugin.xml:729-731` and confirmed in a live EP enumeration. The row's *conclusion* was therefore unsupported, whatever its verdict. Measured: with a **composite** context element `getItems` yields one related item where base yielded none; with the IDENTIFIER leaf it yields one on both. **The editor action is unaffected** — `GotoRelatedSymbolAction.getContextElement` is `psiFile.findElementAt(caretOffset)`, always a leaf, and `getItems(file, editor)` agrees on both commits. Accepted; `REFACT-07-12` names it. |
| `BookmarkManager.getNameIdentifier` path | `platform/bookmarks/…/BookmarkManager.java:196` | **Newly taken.** A bookmark on a Lua name gains a name-identifier anchor. Behaviour change is limited to the bookmark's description and its resilience to edits; no rename or resolution consequence. |
| `NonAsciiCharactersInspection` | `platform/lang-impl/…/NonAsciiCharactersInspection.java:207-212` | **Newly taken.** A Lua identifier with non-ASCII characters can now be reported as a *declaration* rather than a plain token. Lunar's lexer restricts identifiers to `[A-Za-z_][A-Za-z0-9_]*` (`LuaNamesValidator.kt:24` mirrors the grammar), so the fixture that reaches this is not producible from valid Lua. |
| `RedundantSuppressInspectionBase` | `platform/analysis-impl/…/RedundantSuppressInspectionBase.java:204` | **Inert.** Requires a suppression comment scope; Lua has no `@SuppressWarnings` host and Lunar registers no `SuppressionUtil` provider over `LuaNameRef`. |
| `ChangeSignatureAction` | `platform/lang-impl/…/actions/ChangeSignatureAction.java:65-68` | **Newly taken at the availability check only.** The action still needs a `ChangeSignatureHandler` from `LanguageChangeSignature`; Lunar registers none, so the action stays disabled. |
| `MemberInplaceRenamer` (`:241`, `:358`) | `platform/lang-impl/…/inplace/MemberInplaceRenamer.java` | **Intended.** These are Route B's own frames; §3.3 steps 4 and 8 depend on them. |
| `MemberInplaceRenameHandler` (`:46`, `:56`) | `platform/lang-impl/…/inplace/MemberInplaceRenameHandler.java` | **Intended.** The gate this feature exists to satisfy. |
| `InplaceRefactoring.getNameIdentifier` | `platform/lang-impl/…/inplace/InplaceRefactoring.java:596-598` | **Intended.** §3.3 step 6. |
| `VariableInplaceRenamer` (`:433`) | `platform/lang-impl/…/inplace/VariableInplaceRenamer.java` | **Unreached on Route B**, because `MemberInplaceRenamer.findCollision()` returns null (`:113-117`) before it. |
| `ModCommandExecutorImpl` | `platform/lang-impl/…/modcommand/ModCommandExecutorImpl.java:453-455` | **UNREACHABLE, and what makes it unreachable is a missing Lunar registration** — `grep -rl ModCommand src/main/kotlin` returns nothing, so no Lunar quick fix is a `ModCommand`. If one is ever written, this row re-opens: the branch selects the name identifier as the post-action anchor instead of the whole element. Ranges are identical, per the `PsiElement2Declaration` row, so the expected effect is none — but that is an argument, not a measurement, and nothing has measured it. |
| `MinimapStructureMarkerCollector` | `platform/platform-impl/…/minimap/model/MinimapStructureMarkerCollector.kt:97` — **in the provenance checkout only** | **NOT APPLICABLE TO GOLAND 2026.1.3, and therefore NOT RUN.** The class is absent from the build Lunar runs: the provenance checkout (ic master `5ba8ab1cfe37`, 2026-05-04) carries a minimap rewrite with `model/` and `hover/` packages; GoLand 2026.1.3 is branch 261, cut earlier, and ships the older minimap with neither package. No fixture and no VNC session can observe it. This is Risk 1.3 — the provenance skew — in its strongest form: the row describes code that does not exist here. It re-opens if Lunar moves to a platform that ships the rewrite. |
| `ViewStructureCompletionCommandProvider` | moved package since the provenance checkout; resolve by symbol | **UNREACHABLE, and what makes it unreachable is a missing Lunar registration.** `codeInsight.completion.command.provider` is a `LanguageExtensionPoint`; the shipped registrations are `language="JAVA"` / `"kotlin"` and Lunar has none. Registering one re-opens this row. |
| The completion-command family — `ActionCommandProvider`, `ActionCompletionCommand`, `AbstractCopyFQNCompletionCommandProvider`, `AbstractRenameActionCommandProvider` | `platform/lang-impl/…/completion/command/commands/`. **The provenance checkout's `AbstractActionCompletionCommand` and `AbstractCopyFQNCompletionCommand` are FILE names, not class names**; resolve by the class names given here | **UNREACHABLE, and what makes it unreachable is a missing Lunar registration.** Same `LanguageExtensionPoint` with no Lua entry; every concrete subclass is language-specific and all the named types are abstract. **The claim that `AbstractRenameActionCommandProvider` "newly offering Rename at a Lua name is a desired consequence of this feature" is deleted, not softened: it cannot happen.** Nothing in REFACT-07 puts a Lua entry on that EP, so no amount of interface-granting reaches it. Registering a provider would re-open the row — and would be a separate feature, not a consequence of this one. |
| `CompletionPolicy` | `platform/testFramework/…/propertyBased/CompletionPolicy.java:123` — **not in the IDE distribution** | **Test framework only, ground re-confirmed.** Absent from the shipped build, and Lunar runs no property-based completion tests. |
| `VcsCodeVisionLanguageContext` | `platform/vcs-api/…/codeInsight/hints/VcsCodeVisionLanguageContext.kt:49` | **UNREACHABLE — ground re-confirmed at runtime, and the gate is a more precise EP than this row named.** `VcsCodeVisionProvider` **is** globally registered and does load, so "Lunar registers no code vision" was the wrong ground; the actual gate is `vcs.codeVisionLanguageContext`, a `LanguageExtensionPoint` carrying JAVA/kotlin/Python only. The provider returns `READY_EMPTY` before reaching `computeEffectiveRange`. Registering a Lua context re-opens the row; the expected effect is then none, because the ranges are identical per the `PsiElement2Declaration` row. |
| `MinimapHoverHitCheck` | `platform/platform-impl/…/ide/minimap/hover/MinimapHoverHitCheck.kt:147` — **in the provenance checkout only** | **NOT APPLICABLE TO GOLAND 2026.1.3, and therefore NOT RUN** — same ground as the row above, with which it is paired: the `hover/` package does not exist in the shipped minimap. |
| `ModPsiUpdater.rename` / `PsiUpdateImpl` | `platform/analysis-api/…/modcommand/ModPsiUpdater.java:94`; `platform/analysis-impl/…/lang/impl/modcommand/PsiUpdateImpl.java:610` | **Newly eligible, UNREACHABLE for want of a Lunar `ModCommand` — ground re-confirmed.** `void rename(PsiNameIdentifierOwner, List<String>)` becomes callable on a `LuaNameRef`, so a Lunar quick fix written as a `ModCommand` could use it; none exists (`grep -rl ModCommand src/main/kotlin` returns nothing). Writing one re-opens this row and the `ModCommandExecutorImpl` row together. |
| `NamingConvention` family | `platform/lang-impl/…/codeInspection/naming/NamingConvention.java:10`; `AbstractNamingConventionInspection.java:47`; `AbstractNamingConventionMerger.java:14` | **Inert.** These are generic bounds `<T extends PsiNameIdentifierOwner>`, not runtime branches. They matter only to an inspection that subclasses `AbstractNamingConventionInspection`; Lunar registers none (`grep -rl NamingConvention src/main/kotlin` returns nothing). |
| `AbstractInplaceIntroducer` | `platform/lang-impl/…/refactoring/introduce/inplace/AbstractInplaceIntroducer.java:69`, `:622-623` | **Inert.** The class bound is `<V extends PsiNameIdentifierOwner>` and `:622-623` walks up to the nearest one. Reached only from a subclass; REFACT-02's `LuaIntroduceVariableHandler` implements `RefactoringActionHandler` directly (`LuaIntroduceVariableHandler.kt:36`) and does not extend it. Recorded because REFACT-02 is the nearest neighbour and a later inplace-introduce would take this branch. |
| `RenameChangeInfo` | `platform/lang-impl/…/refactoring/changeSignature/inplace/RenameChangeInfo.java:79` | **Inert.** `PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiNameIdentifierOwner.class)` would newly find a `LuaNameRef`, but a `RenameChangeInfo` exists only inside the inplace change-signature flow, which needs a `LanguageChangeSignature` handler. Lunar registers none — the same ground as the `ChangeSignatureAction` row. |
| `InlineOptionsDialog` | `platform/lang-impl/…/refactoring/inline/InlineOptionsDialog.java:177`, `:181`, `:185`, `:190` | **Inert.** Occurrence counting for the Inline refactoring's dialog, reached only from an `InlineActionHandler`. Lunar registers none (`grep -c inlineActionHandler src/main/resources/META-INF/plugin.xml` is `0`). |
| `PolySymbolUsageSearcher` | the shipped reference is on **`PolySymbolUsageQueries`**; the provenance checkout's `PolySymbolUsageSearcher.kt:69` has drifted | **Inert, ground re-confirmed.** `(element as? PsiNameIdentifierOwner)?.nameIdentifier`, reached only for an element carrying a `PolySymbol` declaration. Lunar declares none (`grep -rl PolySymbol src/main/kotlin` returns nothing). |
| `CreateFromTemplateAction.moveCaretAfterNameIdentifier` | `platform/lang-impl/…/ide/actions/CreateFromTemplateAction.java:198` | **Inert.** A static helper whose caller passes an explicitly typed created element; it is not a branch on an arbitrary element. Lunar registers no `CreateFromTemplateAction`. |

**Consumers the `platform/`-scoped search did not return, added from the shipped-build enumeration.**
Each is a real consumer in the build Lunar runs; their absence above is what the corrected search
exists to prevent recurring.

| Consumer | Effect on Lunar |
| :--- | :--- |
| `SpellcheckingStrategy.getTokenizer` → `PsiIdentifierOwnerTokenizer` | **NOT TAKEN, Δ none — and only because Lunar overrides.** `SpellcheckingStrategy.java:91` returns `PsiIdentifierOwnerTokenizer` for **any** `PsiNameIdentifierOwner`, so without Lunar's own strategy every Lua name would newly have been spellchecked as an identifier. `LuaSpellcheckingStrategy.getTokenizer` (`LuaSpellcheckingStrategy.kt:37`) is a **total override with no `super.` call**, so line 91 is unreachable for Lua and all measured `LuaNameRef`s route to `LuaIdentifierTokenizer` on both commits. **This is a coupling to `EDITOR-02`, not an argument** — `risks-and-gaps.md` Risk 1.10 names it, and Phase 1 puts a comment at the override saying it is load-bearing. |
| `RenameTo` (spellchecker quick fix) | **Newly taken, Δ none.** Reachable because `LuaIdentifierTokenizer` passes `useRename=true`. The shipped `getNameRelativeRange(PsiNamedElement)` branches on the interface (verified by `javap -c`, because the shipped signature differs from the provenance checkout's). Measured `(0,6)` on both commits, from both the composite and the leaf. |
| `RecentPlacesFeatures.findDeclaration` | **Newly taken; effect NOT RUN.** Registered as `<completion.ml.elementFeatures language="">` — **an empty `language` means all languages**, so there is no per-language gate to make it unreachable. `findDeclaration` returns `null` on base for every Lua element and the enclosing `LuaNameRef` on treatment. The downstream effect is a completion-ranking feature value, i.e. **item order** — unmeasurable in the unit-test container, where the ranking plugin is not loaded. Routed to Phase 5 live verification. |
| `VcsFeatureProvider` | **Newly taken; effect NOT RUN** — same EP, same empty `language`, same ranking-order consequence, same Phase 5 item. |
| `PsiViewerDialog`, `mcpserver.util.Psi_utilKt`, IFT `SearchEverywhereLesson` | **Not user-facing for Lua**; listed so the enumeration is complete and a later reader does not re-derive them as findings. |

**Every row whose Effect column reports a change or a newly-taken branch** — not a subset named in
prose — is the reason `REFACT-07-12` is a `Must` with an executed check rather than an assertion,
and is what DR-03 step 2 exercised against a Lua fixture. DR-03 reads that column; it does not carry
its own copy of the list, because two statements of one list are two things to keep in sync.

**The rows here that report a measured change** — `NamedElementDuplicateHandler`,
`RelatedItemLineMarkerProvider` and `BookmarkManager` — are exactly the ones `requirements.md`'s
`REFACT-07-12` names as accepted; that requirement and this table are the two places the list
appears, and they must agree. **Rows marked NOT RUN or NOT APPLICABLE are not covered by any verdict**,
and `risks-and-gaps.md` DR-03 records DR-03 as **partially executed** for exactly that reason; a
reader must not read this table's completeness as coverage of those rows.

Rows marked "Newly eligible, unreachable" or "Inert" are audited by re-confirming the ground for
their unreachability — the missing registration, the missing subclass — not by exercising a
feature that cannot run.

## 5. Data Flow

### Example 1 — rename a local from its declaration (`REFACT-07-01`, `-03`, `-04`, `-05`)

```
local coun|ter = 0            (caret at |)
print(counter)
counter = counter + 1
```

1. `RenameHandlerRegistry` → one available handler: `MemberInplaceRenameHandler` (§3.2).
2. `LuaRenameProcessor.substituteElementToRename` → the `counter` IDENTIFIER leaf.
3. `MemberInplaceRenamer(elementToRename = LuaNameRef(counter), substituted = leaf)`.
4. `collectRefs` via `LuaNameReferenceSearcher` → the three usages in this file.
5. `getNameIdentifier()` → the declaration's IDENTIFIER leaf → `PRIMARY_VARIABLE_NAME`.
6. The user types `total`; the three usage segments mirror it live.
7. <kbd>Enter</kbd> → `findProblem()` passes → `tryRollback()` → `RenameProcessor` →
   `LuaRenameProcessor.renameElement`.

```
local total = 0
print(total)
total = total + 1
```

### Example 2 — rename a parameter carrying a `---@param` (`REFACT-07-09`)

```
---@param a number
local function f(|a) return a end
```

The data context supplies the parameter's IDENTIFIER **leaf** here, not a `LuaNameRef` (DR-05
probe `d`, on this exact fixture), so this caret takes §3.5's route, not Example 1's:

1. `RenameHandlerRegistry` → one available handler: `LuaInplaceRenameHandler` (§3.5). The
   platform's `MemberInplaceRenameHandler` is not available — its `instanceof` gate at
   `MemberInplaceRenameHandler.java:46` refuses a leaf.
2. `declaringNameRefOf(leaf)`: `IDENTIFIER` ✓, `kindOf` is `PARAMETER` (`LuaDeclarationSite.kt:243`,
   reached through `kindFromNameRefGrandParent`), `isFileLocal` ✓ (`:20`), `leaf.parent` **is** the
   `LuaNameRef` ✓.
3. `MemberInplaceRenameHandler().doRename(nameRef, editor, dataContext)` — §3.3 steps 3-8, with the
   primary segment as §3.5's "What the template looks like" states.

At the commit — §3.3 step 8 — `LuaRenameProcessor.renameElement` resolves
`LuaCatsParamRenamer.preparedRename` before the declaration swap and applies it inside its
non-cancelable section, so the tag moves with the code:

```
---@param count number
local function f(count) return count end
```

Under Route A the commit would be `renameSynthetic`, an empty method, and the tag would not move.
That divergence is Ground 1 of the route decision, and `REFACT-07-09` is its acceptance check —
driven, per this example, through `LuaInplaceRenameHandler` and §6's `renameInPlaceViaHandler`.

### Example 3 — an invalid name (`REFACT-07-07`)

```
local coun|ter = 0
print(counter)
```

The user types `end` into the template. At step 7 `isIdentifier("end", Lua)` is false —
`LuaNamesValidator.isIdentifier` requires `!LuaKeywords.isReserved(name)`
(`LuaNamesValidator.kt:21`) — so `findProblem()` returns an illegal-identifier problem, the
template is cancelled, and the document is restored to `local counter = 0` / `print(counter)`.

## 6. Test Design

`src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameTest.kt` is
**replaced**, not extended. Every case it currently carries drives
`VariableInplaceRenameHandler().isAvailableOnDataContext`, which after §3.2 answers `false` for
every input; its class KDoc documents an analysis this feature supersedes; and — the reason
replacement rather than retargeting is not enough — every case **injects** the element it wants
tested (`SimpleDataContext…add(CommonDataKeys.PSI_ELEMENT, leafAtCaret().parent as LuaNameRef)`,
`LuaInplaceRenameTest.kt:106-116`, `:123-126`) instead of letting the platform compute it. That is
the blind spot §1's data-context subsection describes: `testInplaceRenameIsWithheldFromAUsageSite`
(`:96-104`) hands in the *usage's* `LuaNameRef`, an element the platform would never have supplied
for that caret.

**Binding rule for the replacement: no case may put `CommonDataKeys.PSI_ELEMENT` into a data
context.** Registry-layer and predicate-layer cases build their context with
`DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` — the same source
`CodeInsightTestUtil.tryInlineRename` uses (`CodeInsightTestUtil.java:244`) — so that
`PsiElementRenameHandler.getElement(context)` returns what `TargetElementUtil` computes. A case
that injects the element measures the plan's assumption, not the platform.

The layers below are all required, because no one of them can fail on its own for the right
reasons:

1. **Registry layer** — `RenameHandlerRegistry.getInstance().getRenameHandlers(context)` returns
   exactly one handler, and it is the expected class. This is the only layer that sees Ground 3's
   silent-selection hazard and §3.5's availability invariant, and the only layer that can see a
   predicate mutation at all. TC-02, TC-10, TC-11, TC-12.
2. **Predicate layer** — the retargeted predicate cases, now driving
   `MemberInplaceRenameHandler().isAvailableOnDataContext(context)`. `isAvailableOnDataContext` is
   `final` on `VariableInplaceRenameHandler` (`:33`) and dispatches to the overridden
   `isAvailable`, so constructing a `MemberInplaceRenameHandler` is enough to exercise the member
   predicate. Covers the `LuaNameRef` clause, the leaf exclusion, the global exclusion and the
   usage-site case.

   **The shipped case that this replacement must invert, named rather than implied.**
   `testInplaceRenameIsOfferedForAFileLocalDeclaration` (`LuaInplaceRenameTest.kt:51-58`) asserts
   `isInplaceRenameAvailable` is `true` for a file-local declaration. §3.2 sets that method to
   `false` unconditionally, so the case **must** fail after Phase 3 — and it did: it is the single
   failure in DR-01's full-suite run (probe (h), `2851 tests completed, 1 failed`). Its replacement
   asserts the same input through `MemberInplaceRenameHandler().isAvailableOnDataContext(context)`
   instead, with the context built from the editor. The other shipped cases —
   `testInplaceRenameIsWithheldFromTheIdentifierLeaf`, `…FromAGlobal`, `…FromAUsageSite`
   (`:67`, `:82`, `:97`) — each assert that something is *withheld*, so they stayed green under the
   new predicate. That is design §6's "guard, not gate" point, and DR-01 confirmed it by
   measurement: a green run of those three is not evidence the retargeting happened.
3. **Document layer** — drive a handler to commit, then `myFixture.checkResult(...)`. This is the
   layer `REFACT-07-15` requires, and it is the layer the prior attempts lacked. TC-01, TC-03,
   TC-04, TC-05, TC-06, TC-07, TC-08, TC-09, TC-15.

   **Which driver each case uses is part of the case**, and it follows from **which element the
   data context supplies at that case's caret** — §1's caret table — not from whether the caret is
   on a declaration. The drivers are not interchangeable:

   - A case whose context supplies the declaring **`LuaNameRef`** uses
     `CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), newName, editor, elementAtCaret)`.
     That helper's parameter type is `VariableInplaceRenameHandler` (`CodeInsightTestUtil.java:236`)
     and `MemberInplaceRenameHandler extends VariableInplaceRenameHandler`
     (`MemberInplaceRenameHandler.java:31`), so it is accepted.
   - A case whose context supplies an IDENTIFIER **leaf** uses `renameInPlaceViaHandler` below on a
     `LuaInplaceRenameHandler`, because that class implements `RenameHandler` directly (§2.3) and
     **cannot** be passed to `tryInlineRename`. §2.3's forfeit table, row 7, is where that cost is
     booked. Every case on a **usage** caret and every case on a **parameter declaration** caret is
     in this group; `requirements.md`'s table names the driver on each such row, and that table is
     the single list — do not restate it here.

   Using the wrong driver measures the wrong route and the case's named mutation stops reaching it:
   on a parameter caret `MemberInplaceRenameHandler.doRename` refuses the leaf at
   `MemberInplaceRenameHandler.java:56`, falls through to `performDialogRename` at `:87`, and the
   driver returns `false` with the document untouched.

   **The replacement driver, to be written verbatim as a private helper in
   `LuaInplaceRenameTest`.** It is `CodeInsightTestUtil.tryInlineRename`'s body
   (`CodeInsightTestUtil.java:240-266`) with `handler.doRename(...)` replaced by
   `handler.invoke(...)` — the entry point `BaseRefactoringAction.performRefactoringAction` uses
   (`:172`) — and with the `renamer.finish(false)` branch dropped, which loses nothing:
   `MemberInplaceRenameHandler.doRename` returns `null` on every path (`:74`, `:88`), so
   `tryInlineRename` already skips that branch for every case in this suite.

   ```kotlin
   private fun renameInPlaceViaHandler(
       handler: RenameHandler,
       newName: String,
   ): Boolean {
       val disposable = Disposer.newDisposable()
       try {
           TemplateManagerImpl.setTemplateTesting(disposable)
           val context = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)
           handler.invoke(project, myFixture.editor, myFixture.file, context)
           val started = TemplateManagerImpl.getTemplateState(myFixture.editor) ?: return false
           val range = requireNotNull(started.currentVariableRange) { "template started with no current variable" }
           WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
               myFixture.editor.document.replaceString(range.startOffset, range.endOffset, newName)
           }
           requireNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor)).gotoEnd(false)
           NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
       } finally {
           Disposer.dispose(disposable)
       }
       return true
   }
   ```

   Imports, all taken from `CodeInsightTestUtil`'s own list (`CodeInsightTestUtil.java:20`, `:22`,
   `:24`, `:31`, `:37`, `:38`): `com.intellij.codeInsight.template.impl.TemplateManagerImpl`,
   `com.intellij.ide.DataManager`, `com.intellij.openapi.Disposable`,
   `com.intellij.openapi.application.impl.NonBlockingReadActionImpl`,
   `com.intellij.openapi.command.WriteCommandAction`, `com.intellij.openapi.util.Disposer`,
   `com.intellij.refactoring.rename.RenameHandler`. `TemplateManagerImpl.setTemplateTesting` is
   `TemplateManagerImpl.java:98` and `getTemplateState` is `:112` (`@Nullable`); `myFixture.file` is
   `CodeInsightTestFixture.getFile()` (`:105`).

   Its `false` return is the same signal `tryInlineRename`'s is: **no template started**. Phase 2's
   binding rule ("every document-layer case asserts that a template started") is satisfied by
   asserting this helper returned `true`, exactly as for `tryInlineRename`.

   **A document-layer case cannot see a predicate.** Neither driver consults
   `isInplaceRenameAvailable` or `isMemberInplaceRenameAvailable`: `tryInlineRename` calls `doRename`
   directly (`:246`), and `LuaInplaceRenameHandler.invoke` gates on its own `declaringNameRefOf`,
   not on the `RefactoringSupportProvider`. That is why TC-11 is a registry-layer case in layer 1
   rather than a document-layer one, and why no case in this layer names a predicate mutation.

**Why the document layer is not sufficient alone.** Neither driver consults
`isInplaceRenameAvailable` or `isMemberInplaceRenameAvailable` — see the preceding paragraph. A
suite built only on the document layer would stay green with both predicates returning `false`, i.e.
with the feature switched off, and would also stay green with the `renameHandler` registration
absent, because both drivers construct the handler themselves. The registry layer is what ties the
document layer to what <kbd>Shift+F6</kbd> actually does.

**Why the predicate layer is not sufficient alone.** It is the shipped state, and the shipped state
is green while a live editor blanks three usages.

**Test-framework facts that bind the implementation.**

- `tryInlineRename` builds its context from
  `DataManager.getInstance().getDataContext(editor.getComponent())` (`CodeInsightTestUtil.java:244`)
  and prefers `PsiElementRenameHandler.getElement(context)` over the passed `elementAtCaret`
  (`:246`). **So a document-layer case receives whatever `TargetElementUtil` computes, not what it
  passes in** — the `elementAtCaret` argument is a fallback for when the data context yields
  nothing. This is the one test-framework fact that makes §1's data-context subsection load-bearing
  for the suite and not only for the IDE.
- It asserts a non-null `state.getCurrentVariableRange()` — the range is read at `:257` and the
  assertion is at `:258`. That assertion **is** the "the template has a current variable" check;
  `renameInPlaceViaHandler` restates it as a `requireNotNull` with a message. It is not, however,
  where a null `getNameIdentifier()` is caught: `getSelectedInEditorElement` reaches its
  `LOG.error` at `InplaceRefactoring.java:859` in GoLand 2026.1.3, `:860` in the `intellij-community` checkout design §1 Provenance names first (Risk 1.3), and `LOG.error` throws
  `TestLoggerFactory.TestLoggerAssertionError` under the test logger
  (`TestLoggerFactory.java:550`, rethrow at `:578-580`, class at `:535-539`). TC-01's row says so.
- When **no** template starts it does not assert at all: `TemplateManagerImpl.getTemplateState`
  returns null, the helper calls `renamer.finish(false)` and returns `false` (`:250-256`), leaving
  the document untouched. A case whose expected document is the *unchanged* one therefore passes
  with the feature entirely absent — see `implementation-plan.md` Phase 2 for which cases that
  affects and what is done about it.
- Conflicts raise `BaseRefactoringProcessor.ConflictsInTestsException` in unit-test mode rather
  than showing a dialog — for Route B, from `RenameProcessor`'s conflict handling
  (`RenameProcessor.java:180`, under the `isUnitTestMode` branch). **Executed** — DR-04, on TC-08's
  own fixture, with the dialog path as a control producing the same class and the same messages.
  TC-08 asserts on that channel, and `requirements.md`'s row states exactly what it must assert.
- **The exception arrives on the asynchronous drain, not on `gotoEnd`.** Between `gotoEnd` returning
  and `NonBlockingReadActionImpl.waitForAsyncTaskCompletion()` completing, the document transiently
  holds the template's text and nothing has been thrown. `CodeInsightTestUtil.tryInlineRename` hides
  this by draining internally (`CodeInsightTestUtil.java:265`), and design §6's
  `renameInPlaceViaHandler` drains for the same reason. **A hand-driven case that reads the document
  between those two points reads an invalid intermediate state**, and would pass while asserting the
  opposite of the requirement. Any new hand-driven case must drain first.

**Mutation-proof obligation.** Every case in `requirements.md`'s table either names a mutation and
asserts it is reachable from that case's own fixture, or is labelled a **guard** and carries the
argument for why no such mutation exists in code this repo can edit. Each must be **executed**
during Phase 4 and the result recorded, per the `mutation-proof` skill. Every case's mutation is
stated unconditionally, against the element DR-05 measured the data context supplying at that
case's caret. Every `REFACT-07-01` caret kind is now measured — DR-05 for `local`, global,
parameter, usage and numeric-`for`, DR-01 probe (b) for generic-`for` and `local function` — so
§3.2's split table decides the driver and the mutation for any case, and no case need be deferred
for want of a measurement.

## 7. Integration Points

### plugin.xml

One added `renameHandler` element, specified in §2.4, which also lists the registrations this
feature depends on unchanged and the one it deliberately does not add.

### LuaBundle.properties

No new key. Every refusal message this feature can surface already exists
(`LuaBundle.properties:149-157`), because every refusal is `LuaRenameProcessor`'s.

### Existing subsystems touched

| Subsystem | Touched how |
| :--- | :--- |
| Rename (REFACT-01) | `LuaRenameProcessor` is the commit path on both routes after this feature. Its rules — conflicts, refusals, `---@param` propagation — are consumed unchanged; **one normalisation is added** to `renameElement` and `findReferences` so both routes classify the same element (§2.5, §3.6). That is a latent REFACT-01 defect, and it must outlive this feature. |
| Label rename (REFACT-04) | The `isMemberInplaceRenameAvailable` clause it depends on is preserved verbatim; `REFACT-07-13` pins it. |
| Find Usages | Not edited. `REFACT-07-12` requires no observable change; §4's `IdentifierUtil` row is the mechanism by which highlighting could have changed and does not. |
| Safe Delete (REFACT-03) | Not edited. §4's `SafeDeleteProcessor.isInside` row is the Safe-Delete branch that is newly taken, and it argues the verdict is unchanged. **Executed** — DR-03 measured the premise (`isAncestor(nameRef, identifier, strict=true)` is true for every `LuaNameRef`, so the widening body at `:118-120` cannot run) and drove end-to-end Safe Deletes identical on both commits. |
| Name validation (REFACT-05) | Consumed unchanged, through `LanguageNamesValidation`. |

## 8. Requirement Coverage

| Requirement | Design section |
| :--- | :--- |
| `REFACT-07-01` | §2.1, §2.2, §3.1, §3.3 steps 1-6; and, per §3.2's measured split table, §3.2's predicate for `local`, global and generic-`for`, §3.5's handler for **parameter** and **`local function`** |
| `REFACT-07-02` | §1 Ground 3, §2.3 ("Why not a `MemberInplaceRenameHandler` subclass"), §3.2, §3.3 step 1, §3.5 (availability invariant and its premise), §6 layer 1 |
| `REFACT-07-03` | §3.1, §3.3 step 6 |
| `REFACT-07-04` | §3.3 step 5 (`collectRefs`) and step 6 (per-ref loop) |
| `REFACT-07-05` | §1 Ground 1, §3.3 step 8, §3.6 (the normalisation is what makes both routes rewrite from the same element), §5 Example 1 |
| `REFACT-07-06` | §3.3 step 9, §3.4 |
| `REFACT-07-07` | §3.3 step 7, §3.4, §5 Example 3 |
| `REFACT-07-08` | §1 "What Route B costs", §3.3 step 8, §3.4 |
| `REFACT-07-09` | §1 Ground 1, §3.3 step 8, §3.5 (a parameter caret's context supplies the leaf, so Lunar's handler is the one that starts the template), §2.5 and **§3.6** (without the normalisation the commit reaches `renameElement` with a composite and the tag silently does not move — measured), §5 Example 2 |
| `REFACT-07-10` | §3.2 (`isFileLocal` clause), §3.5 (step 2 of `declaringNameRefOf`), §3.4 |
| `REFACT-07-11` | §1 "What the platform actually hands a rename handler", §2.3, §2.4, §3.5 |
| `REFACT-07-12` | §4 (whole section) |
| `REFACT-07-13` | §3.2 clause 1, §3.5 (step 1 of `declaringNameRefOf`), §7 |
| `REFACT-07-14` | **Delivered by the platform, not by Lunar code.** At a numeric-`for` caret the data context supplies **null** (DR-05 probes `f`/`f2`/`f3`), so `RenameHandlerRegistry` returns an empty handler list and neither `isMemberInplaceRenameAvailable` nor `declaringNameRefOf` is on the path. The grammatical ground is `numericForStatement ::= FOR IDENTIFIER '='` (`lua.bnf:152`) — no `nameRef`, so no `PsiNamedElement` to anchor on. §3.5's step 3 keeps the safe cast as the classifier-invariant assertion, and **no requirement rests on it**. §3.4 carries the user-visible outcome |
| `REFACT-07-15` | §6 layer 3 |
| `REFACT-07-16` | `InplaceRefactoring.java:228-234` — not designed for, by construction |

## 9. Alternatives Considered

**Alternative A — Route A (`VariableInplaceRenameHandler`) with both PSI edits.** Rejected on
Ground 1: its commit path is the template's own document edits, so `REFACT-07-09` and `REFACT-01-15`
would silently not hold on the in-place path. It also costs a second PSI edit (Ground 2) and is
the route the prior attempts at `REFACT-01-12` were pursuing.

**Alternative B — gate `getNameIdentifier()` on `LuaDeclarationSite.kindOf`.** Rejected in §3.1: it
makes a structural accessor answer a semantic question, and it makes `getName()` and
`getNameIdentifier()` disagree on the same node.

**Alternative C — add `PsiNameIdentifierOwner` to `interface LuaNameRefElement`.** Rejected in
§3.1: `labelRef` implements the same interface (`lua.bnf:247-250`), so it would widen §4's audit
surface to every `goto` target for no gain.

**Alternative D — add `implements="com.intellij.psi.PsiNameIdentifierOwner"` to `lua.bnf:169` and
regenerate.** Rejected in §3.1: the platform tests with `instanceof`, which the mixin already
satisfies, and Lunar's own call sites need no static type. It buys a `src/main/gen` regeneration
for nothing this feature uses.

**Alternative E — register a `resolveSnapshotProvider` so captured references are re-qualified
instead of refused.** Rejected in §2.4: Lua has no qualification syntax for a local, and
`REFACT-07-08` requires a refusal.

**Alternative F — keep `isInplaceRenameAvailable` true and rely on the platform to prefer the
member handler.** Rejected on Ground 3: the registry does the opposite
(`RenameHandlerRegistry.java:114-119`), silently.

**Alternative G — register a `com.intellij.targetElementEvaluator` for Lua so that
`CommonDataKeys.PSI_ELEMENT` is the declaring `LuaNameRef` at a usage caret, instead of adding a
rename handler (§2.3).** This would work: `TargetElementEvaluator.getElementByReference(ref, flags)`
is consulted before `ref.resolve()` (`TargetElementUtilBase.java:176-181`) and
`TargetElementEvaluatorEx2.adjustTargetElement` gets the last word (`:299-302`). It is **rejected**
because `findTargetElement` is the shared navigation primitive: **every** platform consumer of
`CommonDataKeys.PSI_ELEMENT` in an editor reads what it returns — Go to Declaration, Find Usages,
Quick Documentation and the symbol/target API among them — so the change would move Lua behaviour
across all of them to deliver one rename requirement. The set is enumerable but not fixed, which is
the point: the rejection is that the blast radius is *the whole navigation surface*, not that it is
some particular size. That is the exact opposite of `REFACT-07-12`, which requires the feature to be
inert outside rename. A `renameHandler` is
confined to rename by construction. Recorded rather than dismissed because it is the smaller edit,
and because it is the route to take if a later feature wants Lua's target element to be the
composite for its own reasons.

**Alternative H — widen `isMemberInplaceRenameAvailable` to accept the declaration IDENTIFIER leaf,
so a usage caret is served without a new handler.** Rejected as **impossible, not merely
undesirable**: `MemberInplaceRenameHandler.isAvailable` evaluates
`element instanceof PsiNameIdentifierOwner` at `:46` *before* calling the provider at `:47`, and
`doRename` repeats the gate at `:56`. No answer the provider can give is ever read for a leaf.
Recorded because it is the first thing a reader will reach for.

**Alternative I — register `LuaInplaceRenameHandler` as a `MemberInplaceRenameHandler` subclass,
overriding `isAvailable` and `doRename` instead of implementing `RenameHandler` directly.** It is
less code — `isAvailableOnDataContext`, the re-entrancy guard, both `invoke` overloads and the
delegation all come for free, and `CodeInsightTestUtil.tryInlineRename` would accept it directly
(`CodeInsightTestUtil.java:236`). **Rejected**, and this is the one alternative whose rejection is
about a defect rather than a preference: `RenameHandlerRegistry.doGetRenameHandlers` removes the
first map entry that is `instanceof MemberInplaceRenameHandler` whenever the map holds more than one
entry (`RenameHandlerRegistry.java:111-119`), and a subclass satisfies that test. At the carets
this handler serves — those whose data context supplies an IDENTIFIER leaf — the platform's own
`MemberInplaceRenameHandler` is not
available, so Lunar's subclass would be the map's only member-inplace entry and therefore the one
deleted, silently, whenever any other registered handler is renaming there. §2.3 itemises what
implementing the interface directly costs instead; §3.5 states the premise under which the two forms
behave identically and the residual under which they do not.

## 10. Open Questions

None — every de-risking task in [[features/refactoring/07-inplace-rename/risks-and-gaps|risks-and-gaps]] has now run and its decision rule is applied to this design: DR-05 (what the data context supplies at each caret), DR-01 (does §3.3's chain hold end to end — it does, to step 8, whose divergence §3.6 answers), DR-02 (the registry's selection and the extension-list premise), DR-04 (the conflicts channel), and DR-03 (§4's consumer audit, **partially** executed — the two minimap rows are blocked by provenance and the two completion-ranking rows are routed to `implementation-plan.md` Phase 5 task 5.1a, and `risks-and-gaps.md` DR-03 names all four as outstanding rather than leaving them looking audited).
