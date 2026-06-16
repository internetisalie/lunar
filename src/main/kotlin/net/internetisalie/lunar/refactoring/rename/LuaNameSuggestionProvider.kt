package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.NameSuggestionProvider
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

/**
 * Feeds context-aware variable-name suggestions (INTENT-03) into the platform Rename popup,
 * derived from the RHS expression via the shared [LuaNameDeriver]. Returns
 * [SuggestedNameInfo.NULL_INFO] when a candidate is produced (we keep no name-usage statistics),
 * or `null` when not applicable.
 */
class LuaNameSuggestionProvider : NameSuggestionProvider {

    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>,
    ): SuggestedNameInfo? {
        val expr = resolveExpr(element) ?: resolveExpr(nameSuggestionContext) ?: return null
        val candidate = LuaNameDeriver.baseName(expr) ?: return null
        result.add(candidate)
        return SuggestedNameInfo.NULL_INFO
    }

    private fun resolveExpr(element: PsiElement?): LuaExpr? {
        if (element == null) return null
        // Rename target: the declared name (a LuaNameRef inside the decl's LuaAttName) — derive
        // from the initializer expression, not from the name being renamed.
        initializerForDeclaredName(element)?.let { return it }
        if (element is LuaExpr) return element
        return PsiTreeUtil.getParentOfType(element, LuaExpr::class.java, false)
    }

    private fun initializerForDeclaredName(element: PsiElement): LuaExpr? {
        val attName = PsiTreeUtil.getParentOfType(element, LuaAttName::class.java, false) ?: return null
        val decl = attName.parent as? LuaLocalVarDecl ?: return null
        return decl.exprList?.exprList?.firstOrNull()
    }
}
