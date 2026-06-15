package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaExpr

class LuaIfPostfixTemplate(provider: PostfixTemplateProvider? = null) : StringBasedPostfixTemplate(
    "if",
    "if expr then ... end",
    Selector(),
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "if \$expr\$ then\n    \$END\$\nend"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement {
        return expr
    }

    private class Selector : PostfixTemplateExpressionSelectorBase(Condition { it is LuaExpr }) {
        override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
            val exprs = mutableListOf<PsiElement>()
            var current: PsiElement? = com.intellij.psi.util.PsiTreeUtil.getNonStrictParentOfType(context, LuaExpr::class.java)
            while (current != null) {
                exprs.add(current)
                current = com.intellij.psi.util.PsiTreeUtil.getParentOfType(current, LuaExpr::class.java)
            }
            // Outermost-first: `.if` should wrap the whole boolean expression (e.g. `x > 5`),
            // not just the innermost operand (`5`). The framework defaults to the first entry.
            return exprs.asReversed()
        }
        
        override fun getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
            return getExpressions(context, document, offset)
        }
    }
}
