package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite

/**
 * Keeps a caret that sits **on a file-local declaration** targeting that declaration, rather than
 * the declaration it shadows (BUG-472 / BUG-470).
 *
 * `LuaResolveUtil.scopeCrawlUp` excludes a reference's own declaring statement from scope — that
 * is what makes the right-hand `x` of `local x = x` read the OUTER binding, as Lua requires — but
 * it also means an inner `local x`'s own name resolves outward to the declaration it shadows.
 * `TargetElementUtilBase.doFindTargetElement` tries the reference branch first, so without this
 * evaluator that shadowed declaration wins and rename rewrites the wrong one.
 *
 * Declining the foreign referenced element makes `doFindTargetElement` fall through to
 * `getNamedElement`, which supplies the caret's own declaring [net.internetisalie.lunar.lang.psi.LuaNameRef].
 * The `referenceOrReferencedElement !== element` guard keeps every declaration kind that already
 * offers its own leaf — parameters, `local function` and global function names — untouched.
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
}
