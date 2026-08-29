package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite

/**
 * What a Lua caret targets, on the two declaration shapes the platform's own rules get wrong.
 *
 * [isAcceptableReferencedElement] keeps a caret that sits **on a file-local declaration** targeting
 * that declaration rather than the declaration it shadows (BUG-472 / BUG-470).
 * [getNamedElement] gives the numeric-`for` control variable a target at all (BUG-469).
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
     * caret offers (BUG-469).
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
     */
    override fun getNamedElement(element: PsiElement): PsiElement? =
        element.takeIf { LuaDeclarationSite.kindOf(it) == LuaDeclarationKind.NUMERIC_FOR_VARIABLE }
}
