package net.internetisalie.lunar.lang.editor

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlockParent
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaRepeatStatement
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.syntax.LuaBlockPairs

/**
 * Single reusable implementation of keyword-block terminator insertion (EDITOR-01-05).
 *
 * Called from both the [LuaTypedHandler] keystroke path and the completion InsertHandler
 * in [net.internetisalie.lunar.lang.LuaCompletionContributor]. Both entry points commit the
 * document before calling here (design §3.4).
 *
 * All edits run inside the caller's existing write context (typed-action or completion command);
 * no second WriteCommandAction is opened — see design §6.
 */
object LuaKeywordBlockCloser {

    /**
     * Inserts the matching terminator (`end` or `until`) on the next line if the block whose
     * opener ends at [openerEndOffset] is not already balanced. Returns true if a terminator
     * was inserted.
     *
     * Caller contract: document must be committed before this call.
     */
    fun closeIfNeeded(editor: Editor, file: PsiFile, openerEndOffset: Int): Boolean {
        val opener = file.findElementAt(openerEndOffset - 1) ?: return false
        val terminatorType = LuaBlockPairs.terminatorByOpener[opener.node.elementType]
            ?: return false
        if (!needsTerminator(opener, terminatorType)) return false
        val insertText = LuaBlockPairs.insertTextFor[terminatorType] ?: return false
        editor.document.insertString(openerEndOffset, "\n$insertText")
        return true
    }

    /**
     * SYNTAX-18: a pinned partial ancestor block can have its terminator stolen by the opener's
     * own (now greedily parsed) node, so "the owner has a terminator" no longer proves balance —
     * the opener is still unbalanced if any enclosing same-terminator block lost its own.
     * Shared with [net.internetisalie.lunar.lang.completion.LuaEnterHandler], which makes the
     * same balance decision on Enter.
     */
    internal fun needsTerminator(opener: PsiElement, terminatorType: IElementType): Boolean {
        val owner = findOwner(opener, terminatorType) ?: return true
        if (owner.node.findChildByType(terminatorType) == null) return true
        val parentClass =
            if (terminatorType == LuaElementTypes.RCURLY) LuaTableConstructor::class.java
            else LuaBlockParent::class.java
        return generateSequence(PsiTreeUtil.getParentOfType(owner, parentClass, true)) {
            PsiTreeUtil.getParentOfType(it, parentClass, true)
        }.any { terminatorFor(it) == terminatorType && it.node.findChildByType(terminatorType) == null }
    }

    private fun terminatorFor(block: PsiElement): IElementType = when (block) {
        is LuaRepeatStatement -> LuaElementTypes.UNTIL
        is LuaTableConstructor -> LuaElementTypes.RCURLY
        else -> LuaElementTypes.END
    }

    private fun findOwner(opener: PsiElement, terminatorType: IElementType): PsiElement? {
        val parentClass =
            if (terminatorType == LuaElementTypes.RCURLY) LuaTableConstructor::class.java
            else LuaBlockParent::class.java
        return PsiTreeUtil.getParentOfType(opener, parentClass, false)
    }
}
