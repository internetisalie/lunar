package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement

class LuaForIpairsPostfixTemplate(provider: PostfixTemplateProvider? = null) : StringBasedPostfixTemplate(
    "fori",
    "for i, v in ipairs(expr) do ... end",
    LuaExprSelector(),
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "for i, v in ipairs(\$expr\$) do\n    \$END\$\nend"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement {
        return expr
    }
}
