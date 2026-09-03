package net.internetisalie.lunar.lang.insight

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.syntax.LuaSyntax
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations

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
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            LuaLexer(),
            LuaSyntax.IdentifierTokens,
            LuaSyntax.CommentTokens,
            LuaSyntax.StringLiteralTokens,
        )

    /**
     * Returns true when [element] is a declaration site — one rule, shared with Safe Delete,
     * reference search and rename, and owned by [LuaDeclarationSite] (REFACT-01 design §2.1).
     * Before that, this method *was* the rule and three other predicates copied it.
     *
     * The `REFACT-08` clause admits a LuaCATS `@class`/`@alias` declaration leaf too —
     * [LuaDeclarationSite] has no LuaCATS member, so without it Find Usages on a type name offered
     * no action at all.
     */
    override fun canFindUsagesFor(element: PsiElement): Boolean =
        LuaDeclarationSite.kindOf(element) != null || LuaCatsTypeDeclarations.isDeclarationLeaf(element)

    override fun getType(element: PsiElement): String =
        LuaDeclarationSite.kindOf(element)?.usageViewType
            ?: if (LuaCatsTypeDeclarations.isDeclarationLeaf(element)) "type" else ""

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(
        element: PsiElement,
        useFullName: Boolean,
    ): String = element.text

    override fun getHelpId(psiElement: PsiElement): String? = null
}
