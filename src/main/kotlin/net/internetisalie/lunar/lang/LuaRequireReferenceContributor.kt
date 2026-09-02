package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.psi.LuaFuncCall

/**
 * Contributes a [LuaRequireReference] to the module-name string of a `require` call, in **both**
 * call shapes the grammar admits (BUG-389).
 *
 * `args ::= '(' [exprList] ')' | tableConstructor | STRING` (`lua.bnf`), so:
 * - `require("mod")` puts the string inside an `LuaTerminalExpr` under the parenthesized `exprList`;
 * - `require "mod"` / `require [[mod]]` put it as a **bare STRING leaf** directly under `LuaArgs`.
 *
 * Only the first was handled, so the paren-less form — idiomatic and widespread; luacheck uses it
 * at ~150 sites and `require(...)` at 3 — contributed no reference at all, leaving Go to Definition
 * and Find Usages silently inert on it.
 */
class LuaRequireReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val stringElement =
                        LuaRequireReference.moduleStringOf(element) ?: return PsiReference.EMPTY_ARRAY
                    if (!isRequireCall(element)) return PsiReference.EMPTY_ARRAY

                    val moduleName = moduleNameOf(stringElement) ?: return PsiReference.EMPTY_ARRAY
                    // Range is relative to the host element, which differs between the two shapes:
                    // the whole LuaTerminalExpr *is* the string, whereas under LuaArgs the string is
                    // one child among the call's argument syntax.
                    val textRange =
                        if (element === stringElement) {
                            TextRange(0, element.textLength)
                        } else {
                            TextRange.from(stringElement.startOffsetInParent, stringElement.textLength)
                        }
                    return arrayOf(LuaRequireReference(element, textRange, moduleName))
                }
            },
        )
    }

    private companion object {
        fun isRequireCall(element: PsiElement): Boolean {
            val funcCall = PsiTreeUtil.getParentOfType(element, LuaFuncCall::class.java) ?: return false
            return funcCall.varOrExp
                ?.`var`
                ?.nameRef
                ?.identifier
                ?.text == "require"
        }

        /**
         * Strips the quote or long-bracket delimiters; null when nothing is left.
         *
         * BUG-467: `trim` took a character SET, so it ate a module name's own leading or trailing
         * `"`, `'`, `[`, `]` or `=` — `require("=m6")` read as `m6`. The delimiters are parsed as a
         * grammar instead; the body is returned exactly as written.
         */
        fun moduleNameOf(stringElement: PsiElement): String? {
            val text = stringElement.text
            val (start, endExclusive) = LuaStringLiteralText.bodyRange(text) ?: return null
            return text.substring(start, endExclusive).ifEmpty { null }
        }
    }
}
