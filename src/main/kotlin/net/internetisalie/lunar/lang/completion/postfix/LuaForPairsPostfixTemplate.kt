package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement

class LuaForPairsPostfixTemplate(provider: PostfixTemplateProvider? = null) : StringBasedPostfixTemplate(
    "forp",
    "for k, v in pairs(expr) do ... end",
    LuaExprSelector(),
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "for k, v in pairs(\$expr\$) do\n    \$END\$\nend"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement {
        return expr
    }
}
