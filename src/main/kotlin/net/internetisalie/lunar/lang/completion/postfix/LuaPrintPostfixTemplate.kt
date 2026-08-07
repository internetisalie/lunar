package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

class LuaPrintPostfixTemplate(
    provider: PostfixTemplateProvider? = null,
) : StringBasedPostfixTemplate(
        "print",
        "print(expr)",
        LuaExprSelector(),
        provider,
    ) {
    override fun getTemplateString(element: PsiElement): String = "print(\$expr\$)\$END\$"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}
