package net.internetisalie.lunar.lang

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.syntax.LuaSyntax

class LuaLabelFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            LuaLexer(),
            LuaSyntax.IdentifierTokens,
            LuaSyntax.CommentTokens,
            LuaSyntax.StringLiteralTokens,
        )
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        return psiElement is LuaLabelName
    }

    override fun getHelpId(psiElement: PsiElement): String? {
        return null
    }

    override fun getType(element: PsiElement): String {
        if (element is LuaLabelName) {
            return "label"
        }
        return ""
    }

    override fun getDescriptiveName(element: PsiElement): String {
        if (element is LuaLabelName) {
            return element.identifier.text
        }
        return ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        if (element is LuaLabelName) {
            return element.identifier.text
        }
        return ""
    }
}
