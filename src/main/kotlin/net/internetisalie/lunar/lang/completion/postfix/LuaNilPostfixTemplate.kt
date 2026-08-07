package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

class LuaNilPostfixTemplate(
    provider: PostfixTemplateProvider? = null,
) : StringBasedPostfixTemplate(
        "nil",
        "if expr == nil then ... end",
        LuaExprSelector(),
        provider,
    ) {
    override fun getTemplateString(element: PsiElement): String = "if \$expr\$ == nil then\n    \$END\$\nend"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}
