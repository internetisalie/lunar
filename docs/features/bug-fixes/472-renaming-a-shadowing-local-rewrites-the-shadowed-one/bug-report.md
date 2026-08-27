---
id: "BUG-472"
title: "A shadowing `local` targets the declaration it shadows — the dialog renames the wrong one and changes what the program prints"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-472: a shadowing `local` hands over the declaration it shadows

**This report covers [[BUG-470]] as well.** One defect, two severities, one fix. `BUG-470`'s
directory holds a pointer stub; reproduction, root cause, fix strategy and tests live here.
**Closing this closes both** — retire both roadmap rows together.

| | shadowed thing | outcome | filed as |
| :--- | :--- | :--- | :--- |
| `local config = 1` ⏎ `local con\|fig = 2` | a **`local`** | the **wrong declaration is renamed**; the program printed `2`, now prints `1` | **BUG-472** |
| `config = 1` ⏎ `local con\|fig = 2` | a **global** | the dialog **refuses**; the document is unchanged | **BUG-470** |

Found while driving [[REFACT-07]]'s Gap 2.21 (the `local` half, 2026-08-26) and by `REFACT-07-00-05`
/ DR-05's data-context probe (the global half, 2026-08-25). **Neither is caused by REFACT-07** — both
reproduce on the shipped tree, and the `local` half is on the **dialog** path, which that feature
does not touch.

---

## 1. Reproduction A — the wrong declaration is renamed (`high`)

```lua
local config = 1
local config = 2
print(config)
```

The second `local` shadows the first for the rest of the chunk, so `print(config)` binds to line 2
and the program prints `2`. Put the caret on **line 2's** `config` and rename it to `renamed`.

**Expected**

```lua
local config = 1
local renamed = 2
print(renamed)
```

The program still prints `2`.

**Actual** — line 1 is renamed instead, and `print(config)` is rewritten to follow it:

```lua
local renamed = 1
local config = 2
print(renamed)
```

The rename reports success and the file stays valid Lua. **The program printed `2` before and
prints `1` after.** Nothing warns the user.

**Reachability caveat.** With [[REFACT-07]] shipped the registry hands this caret to
`LuaInplaceRenameHandler`, which fails loudly and changes nothing (§4.4), so Shift+F6 does not
reach the corruption on the current tree. The corruption is on the **dialog** path, measured by
driving it directly; it is what this caret got before REFACT-07 and what it still gets anywhere the
dialog path is reached for this shape.

**Evidence.** `docs/features/refactoring/07-inplace-rename/risks-and-gaps.md`, Gap 2.21 — driven on
the shipped tree at `8913cf4b` with a real editor data context, nothing injected, with a dialog
control and a non-shadowing control both run. Raw rows in that feature's `gap-2-21-evidence/`.

## 2. Reproduction B — the rename is refused (`high`, [[BUG-470]])

```lua
config = 1
local config = 2
print(config)
```

Line 1 assigns the **global** `config`; line 2's `local` shadows it for the rest of the chunk, so
`print(config)` binds to line 2. Put the caret on **line 2's** `config` and rename it.

**Expected**

```lua
config = 1
local renamed = 2
print(renamed)
```

**Actual** — the refactoring is refused outright and the document is unchanged:

```
CommonRefactoringUtil$RefactoringErrorHintException: Cannot perform refactoring.
```

The message is `RefactoringBundle.getCannotRefactorMessage` (`RefactoringBundle.java:74-76`,
key `cannot.perform.refactoring`) wrapping
`error.wrong.caret.position.symbol.to.rename` — "Caret should be positioned at symbol to be
renamed" (`RefactoringBundle.properties:16`). §4.5 grounds why that branch is the one that fires.

**Evidence.** DR-05 probe `a2` measured the data context (the global's leaf at `(0,6)`, eleven
characters from the caret) on the unchanged tree at `f3a270fb`
(`docs/features/refactoring/07-inplace-rename/dr-05-evidence/measured-rows.txt`). Gap 2.21 then
**drove the refusal end to end** on the shipped tree as the global control. The end-to-end outcome
is measured, not reasoned.

---

## 3. Root cause

Two independent flaws in the same scope walk. **Both are required for the corruption, and fixing
either alone leaves a wrong document.**

### R1 — a declaration's own name resolves outward, to the declaration it shadows

`LuaBlockExt.kt:32-36`:

```kotlin
for (statement in statementList) {
    // Stop when reaching lastParent to enforce early-binding (prevent forward references)
    if (lastParent != null && statement.textOffset >= lastParent.textOffset) {
        break
    }
```

`LuaResolveUtil.scopeCrawlUp` (`LuaResolveUtil.kt:9-57`) passes `prev` — the child it ascended
from — as `lastParent`, so the walk stops **at** the declaring statement and the declaration is
excluded from its own scope. For line 2's `config` the loop therefore visits only line 1, matches
there, and `LuaScopeProcessor.execute` (`LuaScopeProcessor.kt:41-50`) returns line 1's IDENTIFIER
leaf.

The platform then prefers that resolve over the caret's own element. `TargetElementUtilBase`
`doFindTargetElement` tries the reference branch **first**:

```java
234:    PsiElement element = file.findElementAt(adjusted);
235:    if (BitUtil.isSet(flags, REFERENCED_ELEMENT_ACCEPTED)) {
236:      final PsiElement referencedElement = getReferencedElement(file, offset, flags, editor, element);
237:      if (referencedElement != null) {
238:        return referencedElement;
239:      }
240:    }
...
244:    if (BitUtil.isSet(flags, ELEMENT_NAME_ACCEPTED)) {
245:      if (element instanceof PsiNamedElement) return element;
246:      return getNamedElement(element, adjusted - element.getTextRange().getStartOffset());
247:    }
```

`getReferencedElement` → `isAcceptableReferencedElement` (`:263-274`) consults a per-language
`TargetElementEvaluatorEx2` and, with none registered, returns `true` unconditionally at `:273`.
So the wrong element wins at `:238` and the correct one — the caret's own declaring `LuaNameRef`,
which `getNamedElement` would have produced at `:246`, `LuaNameRef` being a `PsiNameIdentifierOwner`
via `LuaNameRefBaseImpl` (`LuaBaseElements.kt:111-114`) — is never reached.

This is the same rule `LuaNameReference.shadowsRatherThanUses` (`LuaNameReference.kt:168-192`)
already states in prose and already had to compensate for downstream, in `isReferenceTo` only:

> *"`LuaResolveUtil.scopeCrawlUp` excludes the reference's own declaring statement from scope —
> deliberately, so the RHS of `local x = x` reads the OUTER `x` — but that also means an inner
> `local x`'s own name resolves outward to the outer `x`."*

**R1 does not reach every declaration kind, and the exceptions are the proof that the fix is
narrow.** A declaration is immune exactly when its *own* `processDeclarations` re-offers it after
delegating to its body:

| kind | own name resolves to | why |
| :--- | :--- | :--- |
| `local` variable | the shadowed declaration | nothing re-offers it; only `LuaBlockExt.kt:32-36` is on the path |
| generic-`for` variable | the shadowed declaration | `LuaForStatementExt.kt:33-37` offers the variable only when `lastParent == block`, and here `lastParent` is the `LuaNameList` |
| `local function` name | **itself** | `LuaFunctionExt.kt:116` — `processor.execute(this, state)` after the body |
| parameter | **itself** | `LuaFunctionExt.kt:104` / `:68` — the `parList` is executed before the body |
| global function name | **itself** | `LuaFunctionExt.kt:80` |
| numeric-`for` variable | nothing at all | the leaf hangs off the statement (`lua.bnf:152`), carries no reference; separately tracked as REFACT-01 Gap 2.9 |

So R1's blast radius is exactly **`local` variables and generic-`for` variables**. The immune kinds
already supply the IDENTIFIER **leaf** at their declaration caret (DR-05 rows `d`/`d2`/`d3`; DR-01
probe (b) for `local function`), which is why the fix must not make them supply something else.

### R2 — within a block the *earliest* declaration wins, not the nearest

`LuaBlockExt.processDeclarations` iterates `statementList` in **source order** and
`LuaScopeProcessor` stops on the first match (`LuaScopeProcessor.kt:37`, `:46-48`), so for

```lua
local config = 1
local config = 2
print(config)
```

`print(config)` resolves to **line 1**. Lua binds it to line 2. `LuaFile.processDeclarations`
(`LuaFile.kt:41-65`) has the same forward-first-match shape over the file's children and its
`getBlockList()`.

**R2 is measured, not read.** Gap 2.21's dialog control produced
`local renamed = 1` ⏎ `local config = 2` ⏎ `print(renamed)`. `print(config)` was collected as a
usage of line 1, and the only route into that collection is
`LuaNameReference.isReferenceTo` → `resolve()` (`LuaNameReference.kt:267-271`), reached from
`LuaNameReferenceSearcher.processQuery` (`LuaNameReferenceSearcher.kt:76`). Had `print(config)`
resolved to line 2, it would not have moved.

**Why R2 matters even after R1 is fixed.** With only R1 fixed, the caret correctly targets line 2,
the rename rewrites line 2 — and `print(config)`, still bound to line 1, is left behind:

```lua
local config = 1
local renamed = 2
print(config)     -- now reads line 1
```

The program printed `2` and prints `1`. **Same semantic change, different route** — this is
`BUG-457`'s shape, the one `REFACT-01` exists to prevent. Reproduction A's *Expected* document is
unreachable without fixing R2.

R2 is not confined to rename. `LuaUnusedLocalInspection` (`:146-147`) resolves each usage and
matches it against the declaration, so today it reports line 2 unused and line 1 used — both
backwards.

### 3.1 The constraint the fix must not break

**Early binding is correct and must not regress.** In `local x = x` the right-hand `x` binds to the
**outer** declaration (Lua §3.3.3/§3.5). That is what the `break` at `LuaBlockExt.kt:34` exists for,
and `LuaNameReference.kt:48-52` records it as locked by REFACT-01's TC-02. The fix therefore cannot
simply include the declaring statement in its own scope.

The distinction that must be drawn is **structural, not positional**: within one statement, the
*declared name* is a `LuaNameRef` under an `LuaAttName` / `LuaNameList`, while a *reference in the
initialiser* is a `LuaNameRef` under an expression. `LuaDeclarationSite.kindOf`
(`LuaDeclarationSite.kt:43-50`) already separates them — it returns `LOCAL_VARIABLE` for the first
and `null` for the second — and `LuaDeclarationKind.isFileLocal` (`:19-28`) already carries the
lexical-vs-global split.

---

## 4. Fix strategy

Two production changes. **FIX-1** closes R1, **FIX-2** closes R2. Both are required.

### 4.1 FIX-1 — a Lua `TargetElementEvaluatorEx2` that declines a foreign referenced element at a file-local declaration caret

New class `net.internetisalie.lunar.lang.insight.LuaTargetElementEvaluator`, extending
`com.intellij.codeInsight.TargetElementEvaluatorEx2`
(`platform/analysis-impl/src/com/intellij/codeInsight/TargetElementEvaluatorEx2.java:20`, an
`abstract class` that supplies a body for every method including `TargetElementEvaluator`'s
`includeSelfInGotoImplementation` at `:70-73`, so nothing else need be overridden):

```kotlin
override fun isAcceptableReferencedElement(
    element: PsiElement,
    referenceOrReferencedElement: PsiElement?,
): ThreeState =
    if (referenceOrReferencedElement !== element &&
        LuaDeclarationSite.kindOf(element)?.isFileLocal == true
    ) {
        ThreeState.NO
    } else {
        ThreeState.UNSURE
    }
```

Registered in `src/main/resources/META-INF/plugin.xml` beside the other Lua language extensions:

```xml
<targetElementEvaluator
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.LuaTargetElementEvaluator"/>
```

The extension point is `com.intellij.targetElementEvaluator`, `beanClass`
`com.intellij.lang.LanguageExtensionPoint`, `implements`
`com.intellij.codeInsight.TargetElementEvaluator`
(`platform/platform-resources/src/META-INF/LangExtensionPoints.xml:581-583`). No
`targetElementEvaluator` is registered for Lua today (`grep targetElementEvaluator
src/main/resources/META-INF/plugin.xml` is empty), so this adds the first.

`ThreeState.NO` is consumed at `TargetElementUtilBase.java:268-270`; `getReferencedElement` then
returns null at `:260`, `doFindTargetElement` falls through the `if` at `:237`, and `:246`'s
`getNamedElement` supplies the caret's **own** declaring `LuaNameRef`. The fall-through needs
`ELEMENT_NAME_ACCEPTED` to be set, and it always is on both paths that matter:
`TargetElementUtil.getReferenceSearchFlags()` (`:76-82`) is a superset of `getAllAccepted()`
(`:60-66`), which is what `TextEditorPsiDataRule.getPsiElementIn` (`:183-192`) passes for
`CommonDataKeys.PSI_ELEMENT`; and `EditorTestFixture.getElementAtCaret` (`:308-311`) passes both
flags explicitly.

**Why this predicate and not "the caret leaf is a declaration".** The `referenceOrReferencedElement
!== element` clause is what keeps the fix to the two broken rows. Row by row against DR-05 Table 1:

| DR-05 row | caret | referenced element today | evaluator | supplied after FIX-1 |
| :--- | :--- | :--- | :--- | :--- |
| `a` | plain `local coun\|ter = 0` | **null** (resolve finds nothing) | never consulted — `:264` short-circuits on null | `LuaNameRefImpl` — **unchanged** |
| `a2` | `config = 1` ⏎ `local con\|fig = 2` | the global's leaf `(0,6)` | `NO` | the caret's own `LuaNameRefImpl` — **corrected** |
| Gap 2.21 `b6` | `local config = 1` ⏎ `local con\|fig = 2` | the earlier `local`'s leaf `(6,12)` | `NO` | the caret's own `LuaNameRefImpl` — **corrected** |
| `b` / `c` | usage read / usage write | the declaration's leaf | `kindOf(usage leaf)` is null → `UNSURE` | leaf — **unchanged** |
| `d` / `d2` / `d3` | parameter declaration | its **own** leaf (§3, immune) | `referenced === element` → `UNSURE` | leaf — **unchanged** |
| `e` | global decl `con\|fig = {}` | — | `GLOBAL_VARIABLE.isFileLocal` is `false` (`LuaDeclarationSite.kt:24`) → `UNSURE` | `LuaNameRefImpl` — **unchanged** |
| `f` / `f2` / `f3` | numeric-`for` | null | never consulted | null — **unchanged** |
| `g` / `g2` | label decl / `goto` | the label declaration | `kindOf(leaf inside a LuaLabelName)` is null — its parent is not a `LuaNameRef` (`LuaDeclarationSite.kt:48`) → `UNSURE` | `LuaLabelNameImpl` — **unchanged** |

Row `c` deserves its own note because it looks like a counterexample: `coun|ter = counter + 1` where
`counter` is a file-scope `local` classifies `null`, not `GLOBAL_VARIABLE`, because
`isGlobalAssignmentTarget` (`LuaDeclarationSite.kt:139-144`) excludes a name that is also in
`fileScopeLocalNames`. It stays `UNSURE`.

So FIX-1 moves exactly two caret shapes — a shadowing `local` variable and a shadowing generic-`for`
variable — and moves them onto the shape row `a` already has and which REFACT-07's TC-02 and
`testMemberInplaceRenameIsOfferedForAFileLocalDeclaration` already cover.

**It also creates the instrument [[BUG-465]] and REFACT-01 Gap 2.9 both name as their prerequisite**
(BUG-465, *Fix sketch*: "Register a `com.intellij.targetElementEvaluator` for `LuaLanguage`… That
one evaluator is what Gap 2.9 needs as well"). Those two need `getElementByReference` /
`adjustReferenceOrReferencedElement` and the caret offset; **do not add them here.** They are a
different cause (§7) and a different audit.

### 4.2 FIX-2 — the nearest preceding declaration wins

In `LuaBlockExt.processDeclarations`, iterate the block's statements **from the one nearest the
place backwards**, so `LuaScopeProcessor`'s first-match-wins picks the innermost binding:

```kotlin
val visible = statementList.takeWhile { lastParent == null || it.textOffset < lastParent.textOffset }
for (statement in visible.asReversed()) { … }
```

The `break` condition is unchanged in meaning — it is lifted out of the loop into `takeWhile`, so
early binding (§3.1) is preserved exactly: the set of visible statements is identical, only the
order in which they are offered changes.

Mirror the same change in `LuaFile.processDeclarations` (`LuaFile.kt:41-65`) for both its `children`
loop and its `getBlockList()` loop. `root ::= block*` (`lua.bnf:96`) permits more than one top-level
block, which is what makes those loops reachable; see DR-2 in §8.

`ProgressManager.checkCanceled()` is not currently called in either loop and this change does not
add a reason to; leave the cancellation surface as it is.

### 4.3 Alternatives considered and rejected

**ALT-A — fix `LuaNameReference.doMultiResolve`: short-circuit Phase 1 when the host is a file-local
declaration site, returning `host.identifier`.** This is the most *correct* placement: it makes
`resolve()` itself right, so quick documentation (`LuaDocumentationTargetProvider.kt:152`), the
inferred-type annotator (`LuaInferredTypeAnnotator.kt:67`) and the line-marker provider
(`LuaLineMarkerProvider.kt:45`) stop reading the shadowed declaration too. It reuses
`shadowsRatherThanUses` (`LuaNameReference.kt:189-192`), which already expresses this exact rule,
and it returns a leaf, honouring the invariant `LuaNameReferenceSearcher.kt:74-75` documents
("`isReferenceTo` compares identity against `resolve()`, which always returns a leaf").

**Rejected for this fix because it reopens a shipped `Must` requirement.** Making `resolve()`
non-null at a plain `local` declaration caret flips DR-05 row `a` from `LuaNameRefImpl` to the
IDENTIFIER **leaf** — the reference branch at `TargetElementUtilBase.java:235-239` starts winning
where it previously found nothing. Consequences, all in
`src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameTest.kt`:

- `testExactlyOneHandlerClaimsAFileLocalDeclarationAndItIsTheMemberHandler` (TC-02,
  `REFACT-07-02`) goes **red**: the sole claimant becomes `LuaInplaceRenameHandler`, which
  implements `RenameHandler` directly and is deliberately **not** a `MemberInplaceRenameHandler`
  (`LuaInplaceRenameHandler.kt:28-36`), so the `is MemberInplaceRenameHandler` assertion fails.
- `testMemberInplaceRenameIsOfferedForAFileLocalDeclaration` goes **red**:
  `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` (`:87-95`) requires
  `elementToRename is LuaNameRef`, and a leaf is not one.
- That clause's `true` branch would then be reachable only through `LuaLabelName`, which is a
  change to what `REFACT-07-02` asserts, not a test edit.

ALT-A is worth doing — **as tracked follow-up work**, alongside REFACT-07's own owner, not folded
into a bug fix on an open PR. Filed as DR-5 in §8.

**ALT-B — change `scopeCrawlUp` / `LuaBlockExt` to include the declaring statement when `place` is
that statement's own declared name** (the `place` parameter is threaded through every
`processDeclarations` overload and is currently unread in all of them). **Rejected**: it breaks
`LuaShadowingVariableInspection`. That inspection crawls up **from the declaration identifier**
(`LuaShadowingVariableInspection.kt:68-72`) and reports a problem when anything is found; including
the declaration in its own scope makes every local find itself and report "Shadowing variable 'x'".
`LuaRenameConflictDetector`'s C1 comment (`:145-152`) also states a dependence on the current stop
rule. Widening the contract of five `processDeclarations` overloads to repair two callers is a worse
trade than FIX-1.

**ALT-C — `TargetElementEvaluatorEx2.getNamedElement` or `adjustReferenceOrReferencedElement`
instead of `isAcceptableReferencedElement`.** Both are reachable hooks
(`TargetElementUtilBase.java:109-113` and `:216-220`). **Rejected**: `getNamedElement` runs *after*
the reference branch has already won and cannot suppress it; `adjustReferenceOrReferencedElement`
receives the caret offset but not the caret leaf, so the same predicate would have to re-derive
`file.findElementAt(offset)`. `isAcceptableReferencedElement` receives both operands the predicate
needs and is the hook the platform documents for exactly this.

### 4.4 Two approaches that are ruled out — do not propose them

1. **Do not guard `LuaInplaceRenameHandler.declaringNameRefOf` (`:130-135`) to decline the
   shadowing caret.** Declining routes the caret back to the dialog, i.e. into Reproduction A's
   corruption. Measured in Gap 2.21: on this fixture the in-place path's blast radius is *smaller*
   than the dialog's — `MemberInplaceRenamer` collects the refs of the element it was handed (line
   1's declaration), `getSelectedInEditorElement` finds none holding the caret at `(23,29)`, control
   reaches `LOG.error` + `return null`, and `InplaceRefactoring.java:363` (`:362` shipped)
   dereferences that null: `NullPointerException: Cannot invoke "PsiElement.getTextRange()" because
   "selectedElement" is null`. Ugly, protective, and it stops being the right question once the
   resolution is fixed. **Fix the resolution first.** With FIX-1 in place the template anchors on
   the caret's own declaration and that path is no longer entered for this fixture; whether the
   platform's unguarded dereference should be defended anyway remains REFACT-07's question, not
   this one's.
2. **"The supplied leaf must contain the caret" is not available as a universal guard.** A
   **usage** caret legitimately supplies a declaration leaf that is not at the caret — REFACT-07's
   TC-04, a `Must`-backed case, and DR-05 row `b` measures it. Verified by REFACT-07's reviewer.

### 4.5 Why the global half refuses rather than corrupts — and what it needs beyond FIX-1/FIX-2

**Nothing.** FIX-1 closes it, for a reason worth writing down because it is second-order.

In Reproduction B the data context supplies the **global's** IDENTIFIER leaf. That leaf then
classifies as **nothing**: `kindOf` → `kindFromNameRefGrandParent` → `grandParent is LuaVar` →
`kindFromAssignmentTarget` → `isGlobalAssignmentTarget` (`LuaDeclarationSite.kt:139-144`), which
fails because `config` **is** in `fileScopeLocalNames` — the very `local` on line 2 that is doing
the shadowing. So `LuaRenameProcessor.canProcessElement` (`:87-92`) — `element is LuaNameRef ||
kindOf(element) != null` — is false for a bare leaf with a null kind, no processor claims it, and
`PsiElementRenameHandler.getRenameErrorMessage` (`:149-158`) takes its
`!hasRenameProcessor && … && !(element instanceof PsiNamedElement)` branch. That is the message in
§2. `LuaGlobalAssignmentIndex` applies the same `fileLocals` exclusion (`:99`, `:105-107`), so
Phase 2 of `LuaNameReference` finds nothing to soften it either.

Once FIX-1 supplies the caret's own `LuaNameRef`, `canProcessElement` is true through its first
clause, `substituteElementToRename` normalises to the `local`'s leaf, and the rename proceeds. FIX-2
then binds `print(config)` to the `local` so it moves with it. Line 1's `config = 1` is not touched:
`isReferenceTo` resolves it to nothing (`isBareWriteTarget` at `LuaNameReference.kt:157` suppresses
the Phase-2 global lookup for a write target, and Phase 1 finds nothing before line 1).

**BUG-470's "scope note" is discharged.** Its report says the user-visible symptom was reasoned from
the measured data context rather than observed. Gap 2.21 has since driven the global variant end to
end: the dialog refuses with `RefactoringErrorHintException` and the document is unchanged. That is
the measured outcome recorded in §2; the old caveat does not survive into this document.

---

## 5. Test strategy

All cases are `BasePlatformTestCase`, in
`src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt` — the dialog-path
class, which is where both defects live. `myFixture.renameElementAtCaret` reaches the defect:
`CodeInsightTestFixtureImpl.renameElementAtCaret` (`:1073-1075`) → `getElementAtCaret()` →
`EditorTestFixture.getElementAtCaret` (`:308-311`) →
`TargetElementUtil.findTargetElement(editor, REFERENCED_ELEMENT_ACCEPTED | ELEMENT_NAME_ACCEPTED)`.

Every positive case asserts the **whole document** via `myFixture.checkResult`. Asserting "the
declaration changed" is green for the defect as well as for the fix — that is `LuaRenameTest`'s
standing rule (class KDoc) and BUG-457's lesson.

| # | name | fixture (caret `\|`) | asserts | named mutation that reddens it |
| :--- | :--- | :--- | :--- | :--- |
| T1 | `testRenamingALocalThatShadowsAnEarlierLocalRenamesTheOneUnderTheCaret` | `local config = 1` ⏎ `local con\|fig = 2` ⏎ `print(config)` | result is `local config = 1` ⏎ `local renamed = 2` ⏎ `print(renamed)` | **M1** — delete the `NO` branch of `LuaTargetElementEvaluator.isAcceptableReferencedElement` (return `UNSURE` unconditionally). Result becomes `local renamed = 1` ⏎ `local config = 2` ⏎ `print(renamed)`. |
| T2 | `testAUsageBindsToTheNearestPrecedingDeclarationNotTheEarliest` | same fixture as T1 | same result | **M2** — restore forward iteration in `LuaBlockExt.processDeclarations` (drop `.asReversed()`). `print(config)` resolves to line 1, is not collected, and the result becomes `local config = 1` ⏎ `local renamed = 2` ⏎ `print(config)`. |
| T3 | `testRenamingALocalThatShadowsAGlobalRenamesTheLocal` | `config = 1` ⏎ `local con\|fig = 2` ⏎ `print(config)` | result is `config = 1` ⏎ `local renamed = 2` ⏎ `print(renamed)` — **and does not throw** | **M1**. The global's leaf is supplied, `kindOf` is null (§4.5), no processor claims it, and `RefactoringErrorHintException` is thrown instead of a result. |
| T4 | `testSelfReferentialLocalInitialiserStillReadsTheOuterBinding` | `local \|x = 1` ⏎ `local x = x` ⏎ `print(x)` | result is `local outer = 1` ⏎ `local x = outer` ⏎ `print(x)` — the RHS moves, the second declaration and `print(x)` do **not** | **M3** — delete the `takeWhile`/`break` early-binding stop from `LuaBlockExt.processDeclarations`. The RHS `x` binds to its own declaration, is not collected, and the result becomes `local outer = 1` ⏎ `local x = x` ⏎ `print(x)`. |
| T5 | `testAnUpvalueBindsToTheDeclarationVisibleWhereTheClosureIsWritten` | `local \|x = 1` ⏎ `local function f()` ⏎ `  return x` ⏎ `end` ⏎ `local x = 2` ⏎ `print(f(), x)` | result is `local outer = 1` ⏎ `local function f()` ⏎ `  return outer` ⏎ `end` ⏎ `local x = 2` ⏎ `print(f(), x)` — the closure's `x` moves, the later declaration and `print(f(), x)`'s `x` do **not** | **M3** (`return x` re-binds to `local x = 2` and stops moving) **and M2** (`print(f(), x)`'s `x` binds to line 1 and starts moving). Both reddenings are reachable from this one fixture. |
| T6 | `testAShadowingDeclarationCaretTargetsItsOwnDeclaration` | `local config = 1` ⏎ `local con\|fig = 2` ⏎ `print(config)` | `TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS)` is a `LuaNameRef` whose `textRange` starts at the caret's own declaration, not at `(6,12)` | **M1** — the referenced element wins and the returned element is a `LeafPsiElement` at `(6,12)`. |

T4 and T5 are the **over-correction guard** the fix needs: they are green today and must stay green.
Without them a fix that simply drops the `break` at `LuaBlockExt.kt:34` turns T1–T3 green while
silently breaking Lua's early-binding semantics.

`CARET_TARGET_FLAGS` and `leafAtCaret()` already exist in `LuaRenameTest`
(`:843-846`, `:868-869`).

**Mutation discipline.** Each mutation must be applied on the builder and the case observed **red**,
per the house rule that a green run of a case never seen red is not evidence. Record the verdict in
this report's §9 when the fix lands.

---

## 6. Blast radius

**FIX-1** is confined to `TargetElementUtil` consumers, and within those to a shadowing file-local
declaration caret (§4.1's row table). Affected features: **Rename**, **Go to Declaration** (all
`GotoDeclarationAction` targets route through `TargetElementUtil.findTargetElement`), **Find Usages
invoked from a caret**, and anything else reading `CommonDataKeys.PSI_ELEMENT` from an editor
(`TextEditorPsiDataRule.kt:183-192`). In every one of them the change is from "the shadowed
declaration" to "the declaration under the caret". `LuaNameReference.resolve()` is **not** changed,
so the type engine, completion and the inspections are untouched by FIX-1.

**FIX-2** is the wider change: it alters which declaration a *usage* resolves to wherever a block
declares one name more than once. Named consumers, each via `LuaNameReference.multiResolve` unless
noted:

- `LuaNameReferenceSearcher` → `isReferenceTo` → **Find Usages, Safe Delete, rename usage
  collection**. This is the intended correction.
- `LuaUnusedLocalInspection` (`:146-147`) — today reports the *shadowing* declaration unused and the
  shadowed one used; both become right.
- `LuaUndeclaredVariableInspection` via `LuaUndeclaredNames.isUnresolvedNonGlobal` (`:22-23`) —
  existential (`multiResolve(false).isEmpty()`), so unchanged by ordering.
- `LuaDocumentationTargetProvider` (`:152`), `LuaInferredTypeAnnotator` (`:67`),
  `LuaLineMarkerProvider` (`:45`), `LuaParameterInlayHintsProvider`, `LuaParameterInfoHandler`,
  `LuaExpectedCallbackResolver` — all read `resolve()` on usages; each now sees the nearer binding.
- `LuaCompletionScopeProcessor` (`putIfAbsent`, `LuaScopeProcessor.kt:167`) — which declaration
  element a duplicated name records changes; the name set does not.
- `LuaRenameConflictDetector.visibleDeclarationOf` (`:352-356`) — C1 now reports the nearest
  capturing declaration rather than the earliest.
- `LuaShadowingVariableInspection` (`:68-72`) — *which* shadowed declaration is found can change;
  *whether* one is found cannot, so the reported problems are unchanged.
- `LuaRedisSandboxInspection` via `LocalBindingScopeProcessor` (`:38-59`) — purely existential
  ("does any local of this name exist in scope"), order-insensitive.

**Existing fixtures.** No test fixture in `src/test` re-declares a name at the same block depth; the
one near-miss, `LuaCompletionTest.kt:100`, re-declares inside a nested `if`, which FIX-2 does not
reach. Three shipped `.lua` files do (`src/main/lua/mobdebug/init.lua:403`,
`src/main/resources/lua/lunar/export.lua:20`,
`src/main/resources/codeStyle/preview/codeStyle.lua:26`) and none is asserted on.

**A resolution change of this shape is a corpus-sweep target**, per the engineering contract §5. The
routine loop excludes it. See DR-3 in §8.

---

## 7. Is [[BUG-465]] the same cause? — No

**Separate cause, shared instrument.** BUG-465 (rename from an `M.ru|n()` call site) has *correct*
resolution: the reference resolves to the right declaration. What is wrong is the **granularity** of
what `TargetElementUtil` returns — the whole `LuaFuncDecl`, because Phase 2 of `LuaNameReference`
returns stub-index declaration elements (`LuaNameReference.kt:95-104`) rather than leaves — and
`LuaRenameProcessor.canProcessElement` (`:87-92`) not admitting a declaration node. It involves no
shadowing, does not touch `LuaBlockExt.kt:32-36`, and is not affected by first-match ordering.
BUG-465's own report reaches the same conclusion by a different route, and this report does not
inherit that verdict — it re-derives it.

The overlap is the **instrument**: BUG-465 and REFACT-01 Gap 2.9 both name a Lua
`TargetElementEvaluatorEx2` as their prerequisite, and FIX-1 creates one. Closing BUG-472 makes
BUG-465 cheaper. It does not close it: BUG-465 needs `getElementByReference` (or
`adjustReferenceOrReferencedElement`) plus the caret offset, so that a caret inside a `funcName`
maps to the segment **under the caret** rather than to the last segment — the ambiguity
`LuaDeclarationSite.identifierLeafOf` cannot resolve and which is why REFACT-01 left it open.
**Do not widen this fix to cover it**; the two need different methods on the same class and
different audits.

---

## 8. De-risking tasks — what is read, not run

**Nothing in §3–§7 below the two Reproduction sections has been executed.** The reproductions
themselves are measured (Gap 2.21, DR-05); every mechanism, row prediction and consequence is read
from source at `a6c32346`. This bug family has already had one prediction inverted by actually
driving it — Gap 2.21's own predicted outcome was wrong — so the following are scheduled rather than
assumed.

| id | question | instrument | blocks |
| :--- | :--- | :--- | :--- |
| **DR-1** | Does registering a Lua `TargetElementEvaluatorEx2` disturb `LuaRenameTest.testNumericForDeclarationCaretHasNoRenameTargetAtAll`? Reasoned safe — a null referenced element short-circuits at `TargetElementUtilBase.java:264` *before* the evaluator is fetched at `:266`, and `getNamedElement`'s evaluator hook (`:109-113`) returns null by default — but not run. | `run "test --tests *LuaRenameTest* --rerun --no-build-cache"` | FIX-1 merge |
| **DR-2** | Does a Lua file ever produce more than one top-level `block`? `root ::= block*` (`lua.bnf:96`) permits it, and it is what makes `LuaFile.processDeclarations`'s loops (`:41-65`) reachable rather than dead. If it never does, the mirrored FIX-2 edit there is unreachable and should say so in a comment rather than ship unexplained. | a `BasePlatformTestCase` printing `(file as LuaFile).getBlockList().size` for a fixture with a mid-file `return` | FIX-2 shape in `LuaFile.kt` |
| **DR-3** | Does FIX-2 move any inferred type or index answer? A resolution change is exactly what the corpus sweep exists for, and it is excluded from the routine loop. | `run "test -PwithCorpus --rerun --no-build-cache"`; confirm `LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `LuaInspectionParityTest` appear in `build/test-results/test/` **with fresh timestamps** | merge |
| **DR-4** | Do all of `LuaInplaceRenameTest`'s handler-selection cases stay green? §4.1 predicts every DR-05 row except `a2` is untouched, per-row, from source — not measured. | `run "test --tests *LuaInplaceRenameTest* --tests *LuaRenameTest* --rerun --no-build-cache"` | merge |
| **DR-5** | Should `LuaNameReference.doMultiResolve` be corrected too (ALT-A), so `resolve()` stops returning the shadowed declaration to quick doc, the annotator and the line markers? It is the more correct placement and it reopens `REFACT-07-02` (§4.3). | file as a follow-up bug with REFACT-07's owner; enumerate the two `LuaInplaceRenameTest` cases and the `isMemberInplaceRenameAvailable` clause in the report | not this fix |

---

## 9. Verification — as run

Full suite `test --rerun --no-build-cache`: **BUILD SUCCESSFUL**, 2873 tests / 0 failures / 0 errors
/ 1 skipped, up from 2867 by exactly the six new cases. Corpus sweep and `ktlintCheck` recorded in
the commit's handoff.

**Mutation verdicts.** Each mutant applied singly, full suite each time.

| mutant | production change removed | observed red |
| :--- | :--- | :--- |
| **M1** | `LuaTargetElementEvaluator.isAcceptableReferencedElement` returns `UNSURE` unconditionally | T1, T2, T3, T6 — nothing else |
| **M2** | forward iteration in the declaration tier of `LuaBlock.processDeclarations` | T1, T2, T4, T5 — nothing else |
| **M3** | the early-binding stop dropped (`visibleBefore` returns the whole list) | T4, T5, plus 12 pre-existing cases in `LuaScopeResolveTest`, `LuaInplaceRenameTest`, `LuaRenameTest`, `LuaUndeclaredVariableInspectionTest`, `LuaRedisSandboxInspectionTest`, `LuaCreateLocalVariableIntentionTest` |

Two deviations from §5's predicted table, both wider coverage than predicted, neither a miss:

- **T2 also reddens under M1**, because it shares T1's fixture and whole-document assertion. It is
  therefore not a clean isolator of FIX-2 on its own; **M2 is**, and T4/T5 redden only under M2 and
  M3.
- **T4 also reddens under M2.** Its `print(x)` must bind to the *nearest* declaration, so it pins
  the FIX-2 ordering as well as early binding.

**M3 does not isolate.** Early binding is load-bearing across the resolver, so removing it reddens
twelve cases that predate this fix. That does not weaken T4/T5 — both went red for the reason §5
names — but M3 cannot be read as evidence that T4/T5 are the *only* guards on that invariant. They
are not, and that is the healthier outcome.

### 9.1 Live `verify-in-ide` — both reproductions, driven at a real caret

**Run.** GoLand 2026.1.3 sandbox (`runIde`) on the `lunar-builder` VM, `DISPLAY=:99`, project
`~/dr10/proj`, fixtures created before the IDE opened so they were indexed from disk rather than
injected. Every rename was invoked with the **Shift+F6 keystroke** through `xdotool`, never a test
API. Screenshots below are filenames under this directory's `live-evidence/`.

**Which build was loaded, proved by bytecode not by the version string.** Both builds report
`lunar (0.18.0)`, so the discriminator is the presence of `LuaTargetElementEvaluator`. The running
IDE's `idea.plugins.path` is `…/build/idea-sandbox/GO-2026.1.3/plugins`; the `lunar-0.18.0.jar` in
that path was checked each time with `unzip -l` and `/opt/jdk/bin/javap` (`javap` is not on the
builder's root `PATH`).

| build | jar md5 | `LuaTargetElementEvaluator.class` | `<targetElementEvaluator>` in the jar's `plugin.xml` |
| :--- | :--- | :--- | :--- |
| fixed (`a2adc0d2`) | `1630d729…` | present — `javap` prints `extends TargetElementEvaluatorEx2` and `isAcceptableReferencedElement` | 1 |
| pre-fix (`a2adc0d2^` sources, VM-only overlay) | `63147f8f…` | **absent** | 0 |
| fixed, restored | `27afc03e…` | present | 1 |

#### Reproduction A — `local config = 1` ⏎ `local con|fig = 2` ⏎ `print(config)`

Caret set to **2:9** with *Go to Line/Column* (`2:9` confirmed in the status bar,
`02b-repro-a-caret-statusbar.png`).

| observation | result |
| :--- | :--- |
| what Shift+F6 routed to | an **inline template**, not the dialog — the box lands on **line 2's** `config` and the status bar reads `2:9 (6 chars)`; line 1's `config` is not highlighted, line 3's is (`03-repro-a-shift-f6-invoked.png`, `03b-repro-a-template-anchored-line2.png`) |
| typing `renamed` | line 2 and line 3 update together, line 1 untouched (`04-repro-a-typing-updates-both.png`) |
| document after `Enter` + save (bytes, not pixels) | `local config = 1` ⏎ `local renamed = 2` ⏎ `print(renamed)` — §1's *Expected* exactly; the program still prints `2` (`05-repro-a-committed.png`) |
| error balloon | none |
| `idea.log` ERROR/SEVERE | **0**, and 0 `NullPointerException` |
| one `Ctrl+Z` | **fully restores**, byte-for-byte, in a single undo (`06-repro-a-after-one-undo.png`) |

#### Reproduction B ([[BUG-470]]) — `config = 1` ⏎ `local con|fig = 2` ⏎ `print(config)`

| observation | result |
| :--- | :--- |
| what Shift+F6 routed to | an **inline template** on line 2 — **no refusal**; the global on line 1 is not highlighted (`08-repro-b-shift-f6-invoked.png`) |
| document after commit + save | `config = 1` ⏎ `local renamed = 2` ⏎ `print(renamed)` — §2's *Expected* exactly; the global is untouched (`09-repro-b-typing.png`, `10-repro-b-committed.png`) |
| error balloon | none |
| `idea.log` ERROR/SEVERE | **0** |
| one `Ctrl+Z` | fully restores (`11-repro-b-after-one-undo.png`) |

#### Control — a plain non-shadowing `local config = 2` ⏎ `print(config)`

Inline template on line 1, both occurrences move, result `local renamed = 2` ⏎ `print(renamed)`,
0 log errors (`12-control-template.png`, `13-control-committed.png`). The ordinary case is intact.

#### The harness was shown to be able to see the failure

A green screenshot proves nothing unless the same rig goes red on a broken build, so the pre-fix
sources (`a2adc0d2^` for `LuaBlockExt.kt`, `LuaFile.kt`, `plugin.xml`, with
`LuaTargetElementEvaluator.kt` deleted) were staged **on the VM only** — the repository working tree
was never modified — and the identical keystroke sequence re-driven. The tree was then re-synced and
the fixed build re-driven, so the sequence is fixed → broken → fixed.

| fixture | pre-fix build (same harness, same keystrokes) | fixed build |
| :--- | :--- | :--- |
| A | red **"IDE error occurred"** balloon; `SEVERE … Plugin to blame: lunar … Last Action: RenameElement`; `NullPointerException: Cannot invoke "PsiElement.getTextRange()" because "selectedElement" is null` at `InplaceRefactoring.java:362` ← `MemberInplaceRenameHandler.doRename` ← `LuaInplaceRenameHandler.startTemplateOn(:109)`; **document unchanged** (`15-prefix-control-shift-f6.png`, `15b-prefix-ide-error-balloon.png`, `15c-prefix-npe-stack.txt`) | template, correct document, 0 errors (`17-refixed-repro-a-template.png`) |
| B | **"Cannot perform refactoring. Caret should be positioned at symbol to be renamed"**; document unchanged (`16-prefix-control-repro-b-refused.png`, `16b-prefix-cannot-perform-refactoring.png`) | template, correct document, 0 errors |

**This discharges §1's Reachability caveat and confirms §4.4's prediction as a measurement.** The
NPE §4.4 reasoned about is real and reachable by Shift+F6 on the pre-fix tree — it fired at
`InplaceRefactoring.java:362`, the exact line §4.4 names — and FIX-1 stops that path being entered:
the template now anchors on the caret's own declaration, `getSelectedInEditorElement` finds it, and
nothing is dereferenced null. §4.4 predicted this without measuring it; it is measured now.

**One deviation from expectation, in the fix's favour.** [[BUG-471]] records that undo after an
in-place rename may not restore. On both reproductions here a **single `Ctrl+Z` restored the
document byte-for-byte**, verified on disk after `Ctrl+S`, not just on screen. That does not close
BUG-471 — it is a different fixture, a different environment (BUG-471 was found in the
**containerized** GoLand; this ran the **VM-native** `runIde` sandbox), and this run does not survey
the cases BUG-471 covers — but on these two shapes undo behaved correctly, which is worth knowing
before BUG-471 is investigated.

**Read, not run.** The `verifyPlugin`/full-suite gate was not re-executed for this verification (it
is recorded above at `a2adc0d2` and the tree is unchanged since); no other rename shape, keymap or
IDE product was exercised; and the pre-fix A/B covers Reproductions A and B only, not the control.

## 10. What §4.2 got wrong — the assignment tier

**§4.2 as written reddens three shipped tests.** `LuaBlock.processDeclarations` does not offer only
declarations: its `LuaAssignmentStatement` branch also offers each `LuaVar` assignment target.
Reversing that single mixed loop makes a **write** out-rank the declaration it writes to. Measured
on `local counter = 0` ⏎ `counter = counter + 1` ⏎ `print(counter)` (TC-01's own fixture): after the
literal §4.2 edit, `print(counter)` resolved to line 2's assignment target rather than line 1's
declaration and was left behind — `local total = 0` ⏎ `total = total + 1` ⏎ `print(counter)`. That
reddened `testRenameLocalAndAllUsages`, `testSearchInCommentsDoesNotLogAnUnknownElementType` and
`testCancellingAfterTheFirstEditStillAppliesEveryEdit`.

The rule that resolves it is already in this report, at §4.5: an assignment target that is also
bound by a file-scope `local` is **not a declaration** — `LuaDeclarationSite.isGlobalAssignmentTarget`
excludes it. So the walk is split into two tiers over the *same* visible set: declaration statements
reversed (nearest first), then assignment targets in source order, exactly as before. The set of
offered elements is unchanged; an assignment can now only win where no declaration matched, which is
the only case it ever legitimately won. This is what makes §2's fixture right as well — the `local`
must beat the global assignment above it.

## 11. What DR-2 measured

Both halves, on the builder rather than read:

- **A file really does hold more than one top-level block.** `local a = 1` ⏎ `return a` ⏎
  `local b = 2` ⏎ `print(b)` yields `blocks=2`, ranges `(0,20)` and `(21,41)` — a mid-file
  `finalStatement` closes one block and opens the next. So `LuaFile.processDeclarations`'s
  `getBlockList()` loop is live and its nearest-first reversal is a real correction.
- **Its `children` loop is dead.** A file's direct children measured as
  `[LuaBlockImpl, PsiWhiteSpaceImpl, LuaBlockImpl, PsiWhiteSpaceImpl]` for a fixture holding a
  global function declaration, two global assignments and a `local` — under `root ::= block*` no
  `LuaFuncDecl` / `LuaGlobalFuncDecl` / `LuaGlobalVarDecl` / `LuaAssignmentStatement` is ever a
  direct child, so none of that `when`'s branches is reachable. It is left in source order with a
  comment saying so, rather than carrying an unexplained mirrored edit that nothing can execute.

## 12. Closing

`BUG-472` and `BUG-470` retire together. Set both `requirements`-equivalent front-matter statuses to
`done` and delete **both** roadmap rows (`docs/roadmap.md`) in the same commit — under the
mint-and-close convention a row is removed when the work closes, not marked done and left.
`BUG-470`'s directory and stub stay: `[[BUG-470]]` is linked from `docs/roadmap.md`, from this
report and from `docs/features/refactoring/07-inplace-rename/risks-and-gaps.md` Gap 2.21 and DR-05
Observation 1, and a dangling wikilink is worse than a stub.
