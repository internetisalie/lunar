package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

class LuaRequireReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (element !is LuaTerminalExpr) return PsiReference.EMPTY_ARRAY
                    val terminal = element as LuaTerminalExpr
                    val stringElement = terminal.string ?: return PsiReference.EMPTY_ARRAY

                    val funcCall = PsiTreeUtil.getParentOfType(element, LuaFuncCall::class.java)
                        ?: return PsiReference.EMPTY_ARRAY

                    val callee = funcCall.varOrExp?.`var`?.nameRef?.identifier?.text
                    if (callee != "require") {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val text = stringElement.text
                    if (text.length < 2) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val moduleName = text.trim('\"', '\'', '[', ']', '=')
                    if (moduleName.isEmpty()) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val textRange = TextRange(0, element.textLength)
                    return arrayOf(LuaRequireReference(element, textRange, moduleName))
                }
            }
        )
    }
}
