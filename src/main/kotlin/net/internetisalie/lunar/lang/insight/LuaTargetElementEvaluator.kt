package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations

/**
 * What a Lua caret targets, on the three declaration shapes the platform's own rules get wrong.
 *
 * [isAcceptableReferencedElement] keeps a caret that sits **on a file-local declaration** targeting
 * that declaration rather than the declaration it shadows (BUG-472 / BUG-470).
 * [getNamedElement] gives the numeric-`for` control variable a target at all (BUG-469), and gives a
 * `function t:m()` / `function M.run()` declaration caret the same IDENTIFIER leaf its call sites
 * already target, which is what Find Usages needs there (BUG-478).
 * [adjustTargetElement] turns a dotted function's resolved declaration NODE into the IDENTIFIER
 * leaf that declaration names, which is what a call-site caret needs to be renameable (BUG-465).
 *
 * `LuaResolveUtil.scopeCrawlUp` excludes a reference's own declaring statement from scope — that
 * is what makes the right-hand `x` of `local x = x` read the OUTER binding, as Lua requires — but
 * it also means an inner `local x`'s own name resolves outward to the declaration it shadows.
 * `TargetElementUtilBase.doFindTargetElement` tries the reference branch first, so without this
 * evaluator that shadowed declaration wins and rename rewrites the wrong one.
 *
 * Declining the foreign referenced element makes `doFindTargetElement` fall through to
 * `TargetElementUtilBase.getNamedElement`, whose `PsiNamedElement`-parent branch supplies the
 * caret's own declaring [net.internetisalie.lunar.lang.psi.LuaNameRef]. The
 * `referenceOrReferencedElement !== element` guard keeps every declaration kind that already offers
 * its own leaf — parameters, `local function` and global function names — untouched.
 */
class LuaTargetElementEvaluator : TargetElementEvaluatorEx2() {
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

    /**
     * Supplies the numeric-`for` control variable's own IDENTIFIER leaf, which nothing else at that
     * caret offers (BUG-469), and a LuaCATS `@class`/`@alias` declaration leaf, which has neither a
     * reference nor a `PsiNamedElement` parent either (`REFACT-08` design.md §2.8). Both registrations
     * share `language="Lua"`: a LuaCATS element reports `LuaLanguage` too
     * ([net.internetisalie.lunar.luacats.lang.lexer.LuaCatsElementType]), so the one evaluator
     * serves both.
     *
     * `numericForStatement ::= FOR IDENTIFIER '=' ...` (`lua.bnf:152`) wraps the control variable in
     * no [net.internetisalie.lunar.lang.psi.LuaNameRef], so it is the one Lua declaration whose leaf
     * has neither a reference for `doFindTargetElement`'s reference branch nor a `PsiNamedElement`
     * parent for `TargetElementUtilBase.getNamedElement`'s fallback. Measured on `for <caret>i`:
     * `findTargetElement` was null, `CommonDataKeys.PSI_ELEMENT` was null and
     * `RenameHandlerRegistry` returned an empty handler list, so <kbd>Shift+F6</kbd> reached no
     * handler and the Rename action was painted disabled.
     *
     * The leaf is the element the platform already renames correctly at this variable's **usage**
     * caret — `LuaNameReference` resolves there to this same leaf — so supplying it here makes the
     * declaration caret and the usage caret target one element rather than adding a second one.
     *
     * Narrow by construction: [LuaDeclarationKind.NUMERIC_FOR_VARIABLE] is reached only for an
     * `IDENTIFIER` whose direct parent is a `LuaNumericForStatement`. Every other declaration keeps
     * the `PsiNamedElement` parent branch that BUG-472 depends on, because a null return here is
     * what lets `TargetElementUtilBase.getNamedElement` continue to it.
     *
     * **[LuaDeclarationKind.METHOD_FUNCTION] and [LuaDeclarationKind.DOTTED_FUNCTION] join it for
     * BUG-478**, for the same "one element from both ends" reason and on a measured difference, not
     * a suspected one. Instrumented at the three carets of `local t = {}` / `function t:m() end` /
     * `t:m()` plus a `function gfun() end` control, on the editor's own data context:
     *
     * | caret | `findReference().resolve()` | `findTargetElement` | `USAGE_TARGETS_KEY` |
     * | :-- | :-- | :-- | :-- |
     * | `function gfun()` decl (control) | the `gfun` leaf — itself | the leaf | 1 |
     * | `t:m()` call site | the `m` declaration leaf | the leaf | 1 |
     * | `function t:m()` decl | **null** | **`LuaNameRefImpl`** | **null** |
     * | `function M.run()` decl | **null** | **`LuaNameRefImpl`** | **null** |
     *
     * A declaration name that resolves to nothing skips `doFindTargetElement`'s reference branch,
     * so the `PsiNamedElement`-parent fallback answers with the enclosing `LuaNameRef` composite —
     * and [LuaFindUsagesProvider.canFindUsagesFor] is `LuaDeclarationSite.kindOf(...) != null`,
     * which classifies IDENTIFIER **leaves** only. `DefaultUsageTargetProvider` therefore contributed
     * no `UsageTarget`, `targetVariants` came back empty and `FindUsagesAction` painted *"Cannot
     * search for usages from this location"* — over a usage set `ReferencesSearch` computes
     * correctly from the leaf ([net.internetisalie.lunar.lang.insight.LuaColonCallFindUsagesTest]).
     * A global function declaration escaped only because its own name resolves to itself.
     *
     * Returning the leaf makes these two kinds arrive the way the control already does. Rename is
     * unaffected in either direction: `LuaRenameProcessor.canProcessElement` admits a `LuaNameRef`
     * and a classified leaf alike, and `substituteElementToRename` normalises both through
     * `LuaDeclarationSite.identifierLeafOf` to this same leaf.
     */
    override fun getNamedElement(element: PsiElement): PsiElement? = element.takeIf { isOwnNameTarget(it) }

    /**
     * True when [element] is a declaration leaf that must be its own target rather than defer to the
     * `PsiNamedElement` parent branch — the three [LuaDeclarationKind]s above, plus a LuaCATS type
     * declaration leaf, which [LuaDeclarationSite] does not classify at all.
     */
    private fun isOwnNameTarget(element: PsiElement): Boolean =
        when (LuaDeclarationSite.kindOf(element)) {
            LuaDeclarationKind.NUMERIC_FOR_VARIABLE,
            LuaDeclarationKind.METHOD_FUNCTION,
            LuaDeclarationKind.DOTTED_FUNCTION,
            -> true

            else -> LuaCatsTypeDeclarations.isDeclarationLeaf(element)
        }

    /**
     * Maps a resolved [LuaFuncDecl] down to the IDENTIFIER leaf it declares, so a caret on a
     * `M.run()` **call site** targets the same element every other usage caret does (BUG-465,
     * `REFACT-01-08`; DR-05 Gap 2.14).
     *
     * Measured on `function M.run() end` / `M.ru<caret>n()` through the editor's own data context,
     * with nothing injected: `findTargetElement` returned `LuaFuncDeclImpl[function M.run() end]`,
     * `LuaRenameProcessor.canProcessElement` was **false** for it — a [LuaFuncDecl] is neither a
     * [net.internetisalie.lunar.lang.psi.LuaNameRef] nor a classified leaf — so
     * `RenamePsiElementProcessorBase.forPsiElement` fell through to the platform default. The one
     * claimant `RenameHandlerRegistry` returned — `PsiElementRenameHandler` — then refused with
     * *"Caret should be positioned at symbol to be renamed"*; BUG-465's report names
     * `error.cannot.be.renamed`, which is the neighbouring message, not the one that fires. The
     * declaration caret was never affected: it resolves to a leaf or to its own `LuaNameRef`
     * already.
     *
     * **Why this hook and not another.** `getElementByReference` carries no caret offset, and
     * without the offset the receiver segment cannot be told from the name segment — the reason
     * REFACT-01 left this gap open rather than widening `canProcessElement`. Of the two overrides
     * that do carry it, both were measured to be reached with the [LuaFuncDecl] at this caret
     * (`adjustReferenceOrReferencedElement(offset=25)` then `adjustTargetElement(offset=25)`), and
     * this one is chosen because it runs **last**: `TargetElementUtilBase.findTargetElement` calls
     * it after `doFindTargetElement` has finished, so [isAcceptableReferencedElement] and
     * [getNamedElement] have already had their say and neither BUG-472's nor BUG-469's guard can be
     * perturbed by what is returned here. Adjusting inside the reference branch would instead feed
     * a different element to BUG-472's guard.
     *
     * **The name check is load-bearing, not defensive — it is what keeps Gap 2.10 closed.**
     * [LuaDeclarationSite.identifierLeafOf] of a [LuaFuncDecl] is its **last** name segment, so an
     * unguarded map answers `run` for a caret the user put somewhere else entirely, which is the
     * silent misdirection REFACT-01 refused to ship. It is reachable: on
     * `<caret>M = {}` / `function M.run() end` the platform hands the caret on `M` that same
     * `LuaFuncDecl`, which `LuaRenameTest.testCaretOnAGlobalShadowedByADottedDeclarationIsRefusedNotMisdirected`
     * has asserted since REFACT-01 and which reddens if this check is dropped. Requiring the
     * reference at the caret to name the same segment the map would land on makes the redirect
     * segment-faithful by construction rather than by luck, and it costs one text comparison.
     * `TargetElementUtilBase.findReference` applies the platform's own offset adjustment and
     * `ReferenceRange` containment test, so a caret at either edge of the identifier is answered
     * the same way the target that arrived here was. The two carets on a function name's own
     * receiver were measured never to reach here at all — at a call site `M` resolves to nothing,
     * because the declaration is stub-indexed under `"M.run"`, and at the declaration `M` is
     * already its own leaf, where `LuaRenameProcessor`'s receiver-segment refusal takes it.
     *
     * A PSI shape test plus one `findReferenceAt` walk, no resolve and no index read: this runs on
     * the EDT for every Lua `findTargetElement`, including Go to Declaration and Quick Doc.
     *
     * The four parameters are the platform's ported signature, not a Lunar call shape; [flags] is
     * unused because this adjustment is correct for every accepted-target combination.
     */
    override fun adjustTargetElement(
        editor: Editor,
        offset: Int,
        flags: Int,
        targetElement: PsiElement,
    ): PsiElement {
        val declaration = targetElement as? LuaFuncDecl ?: return targetElement
        val declaredNameLeaf = LuaDeclarationSite.identifierLeafOf(declaration) ?: return targetElement
        val caretReference = TargetElementUtilBase.findReference(editor, offset) ?: return targetElement
        return if (caretReference.canonicalText == declaredNameLeaf.text) declaredNameLeaf else targetElement
    }
}
