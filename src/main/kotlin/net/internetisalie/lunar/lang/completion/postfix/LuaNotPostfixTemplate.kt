package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

class LuaNotPostfixTemplate(
    provider: PostfixTemplateProvider? = null,
) : StringBasedPostfixTemplate(
        "not",
        "not expr",
        LuaExprSelector(),
        provider,
    ) {
    override fun getTemplateString(element: PsiElement): String = "not \$expr\$\$END\$"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}
