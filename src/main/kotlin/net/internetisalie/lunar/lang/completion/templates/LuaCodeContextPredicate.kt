package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.syntax.LuaSyntax

/**
 * Shared string/comment/number suppression set for the Lua code-aware live-template contexts
 * (COMP-07-10). Referenced lazily so the singleton [com.intellij.psi.tree.IElementType]s are
 * resolved after platform registration.
 */
internal val SUPPRESS: TokenSet = TokenSet.orSet(
    LuaSyntax.CommentTokens,
    LuaSyntax.StringLiteralTokens,
    TokenSet.create(
        LuaTokenTypes.LONGSTRING,
        LuaTokenTypes.LONGSTRING_BEGIN,
        LuaTokenTypes.LONGSTRING_END,
        LuaTokenTypes.NUMBER,
        LuaElementTypes.NUMBER,
    ),
)

/**
 * Returns true when the caret/selection start in [templateActionContext] sits in real Lua code,
 * i.e. not inside a string, comment, or numeric literal (design §3.1). Shared by
 * [LuaCodeContextType] and [LuaSurroundContextType].
 */
internal fun isInLuaCodeContext(templateActionContext: TemplateActionContext): Boolean {
    val file = templateActionContext.file
    if (file !is LuaFile) return false
    val offset = templateActionContext.startOffset
    val leaf = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return true
    if (PsiUtilCore.getElementType(leaf) in SUPPRESS) return false
    var ancestor = leaf.parent
    while (ancestor != null) {
        if (PsiUtilCore.getElementType(ancestor) in SUPPRESS) return false
        ancestor = ancestor.parent
    }
    return true
}
