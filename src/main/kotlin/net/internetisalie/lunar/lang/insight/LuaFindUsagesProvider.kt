package net.internetisalie.lunar.lang.insight

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.syntax.LuaSyntax

/**
 * Find Usages provider for Lua symbols.
 *
 * Covers NAV-02-01 (locals), NAV-02-02 (cross-file globals via stub index),
 * and NAV-02-03 (labels, preserved from LuaLabelFindUsagesProvider).
 *
 * The platform's ReferencesSearch + LuaNameReference.isReferenceTo do the
 * actual finding; this class only declares which elements are valid targets
 * and how to label them.
 */
class LuaFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        LuaLexer(),
        LuaSyntax.IdentifierTokens,
        LuaSyntax.CommentTokens,
        LuaSyntax.StringLiteralTokens,
    )

    /**
     * Returns true when [element] is a declaration-site identifier leaf
     * that LuaNameReference.resolve() can return.
     *
     * Accepted declaration sites (in PSI parent-chain terms):
     *   - LuaLabelName           — label declaration (NAV-02-03)
     *   - IDENTIFIER → LuaNameRef → LuaAttName          — local variable
     *   - IDENTIFIER → LuaNameRef → LuaLocalFuncDecl    — local function
     *   - IDENTIFIER → LuaNameRef → LuaFuncName         — global function
     *   - IDENTIFIER → LuaNameRef → LuaFuncNameMethod   — method (self-receiver)
     *   - IDENTIFIER → LuaNameRef → LuaNameList → LuaParList              — parameter
     *   - IDENTIFIER → LuaNameRef → LuaNameList → LuaGenericForStatement  — generic-for var
     *   - IDENTIFIER → LuaNumericForStatement            — numeric-for var
     */
    override fun canFindUsagesFor(element: PsiElement): Boolean {
        if (element is LuaLabelName) return true
        if (element.node?.elementType != LuaElementTypes.IDENTIFIER) return false
        val parent = element.parent
        if (parent is LuaNumericForStatement) return true
        if (parent !is LuaNameRef) return false
        return when (val grandParent = parent.parent) {
            is LuaAttName -> true
            is LuaLocalFuncDecl -> true
            is LuaFuncName -> true
            is LuaFuncNameMethod -> true
            is LuaNameList -> grandParent.parent is LuaParList || grandParent.parent is LuaGenericForStatement
            else -> false
        }
    }

    override fun getType(element: PsiElement): String = when {
        element is LuaLabelName -> "label"
        element.parent is LuaNumericForStatement -> "local variable"
        else -> typeFromNameRefGrandparent(element)
    }

    private fun typeFromNameRefGrandparent(element: PsiElement): String {
        val grandParent = element.parent?.parent ?: return ""
        return when {
            grandParent is LuaAttName -> "local variable"
            grandParent is LuaLocalFuncDecl -> "local function"
            grandParent is LuaFuncName || grandParent is LuaFuncNameMethod -> "global function"
            grandParent is LuaNameList && grandParent.parent is LuaParList -> "parameter"
            grandParent is LuaNameList && grandParent.parent is LuaGenericForStatement -> "local variable"
            else -> ""
        }
    }

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = element.text

    override fun getHelpId(psiElement: PsiElement): String? = null
}
