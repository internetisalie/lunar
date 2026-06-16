package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement

class LuaVarPostfixTemplate(provider: PostfixTemplateProvider? = null) : StringBasedPostfixTemplate(
    "var",
    "local name = expr",
    LuaExprSelector(),
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "local \$name\$ = \$expr\$\$END\$"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement {
        return expr
    }

    override fun setVariables(template: Template, element: PsiElement) {
        super.setVariables(template, element)
        // Editable name tab stop seeded from a literal; the user Tabs to accept/edit it.
        template.addVariable("name", TextExpression("value"), TextExpression("value"), true)
    }
}
