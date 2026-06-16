package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlockParent
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.syntax.LuaBlockPairs

class LuaEnterHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (file !is LuaFile) return EnterHandlerDelegate.Result.Continue

        val offset = caretOffset.get()
        if (offset == 0) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        val opener = file.findElementAt(offset - 1) ?: return EnterHandlerDelegate.Result.Continue
        val terminatorType = LuaBlockPairs.terminatorByOpener[opener.node.elementType]
            ?: return EnterHandlerDelegate.Result.Continue

        return completeBlock(editor, opener, terminatorType, offset)
    }

    private fun completeBlock(
        editor: Editor,
        opener: PsiElement,
        terminatorType: com.intellij.psi.tree.IElementType,
        offset: Int
    ): EnterHandlerDelegate.Result {
        val parentClass =
            if (terminatorType == LuaElementTypes.RCURLY) LuaTableConstructor::class.java else LuaBlockParent::class.java
        val statement = PsiTreeUtil.getParentOfType(opener, parentClass, false)

        if (statement != null && statement.node.findChildByType(terminatorType) != null) {
            // Already balanced: open an indented body line, insert no second terminator (§3.2).
            return EnterHandlerDelegate.Result.DefaultForceIndent
        }

        val insertText = LuaBlockPairs.insertTextFor[terminatorType] ?: return EnterHandlerDelegate.Result.Continue
        editor.document.insertString(offset, "\n$insertText")
        editor.caretModel.moveToOffset(offset)
        return EnterHandlerDelegate.Result.DefaultForceIndent
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        if (file !is LuaFile) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        val bodyLine = document.getLineNumber(editor.caretModel.offset)
        val terminatorLine = bodyLine + 1
        if (terminatorLine >= document.lineCount) return EnterHandlerDelegate.Result.Continue
        if (!lineIsFreshTerminator(file, document, terminatorLine)) return EnterHandlerDelegate.Result.Continue

        return reindentBody(file, document, editor, bodyLine, terminatorLine)
    }

    private fun lineIsFreshTerminator(file: PsiFile, document: com.intellij.openapi.editor.Document, line: Int): Boolean {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        var leaf = file.findElementAt(start)
        while (leaf != null && leaf.textRange.startOffset < end && leaf.text.isBlank()) {
            leaf = com.intellij.psi.util.PsiTreeUtil.nextLeaf(leaf)
        }
        val type = leaf?.node?.elementType ?: return false
        return type == LuaElementTypes.END || type == LuaElementTypes.UNTIL || type == LuaElementTypes.RCURLY
    }

    private fun reindentBody(
        file: PsiFile,
        document: com.intellij.openapi.editor.Document,
        editor: Editor,
        bodyLine: Int,
        terminatorLine: Int
    ): EnterHandlerDelegate.Result {
        val csm = CodeStyleManager.getInstance(file.project)
        val bodyOffset = csm.adjustLineIndent(file, document.getLineStartOffset(bodyLine))
        csm.adjustLineIndent(file, document.getLineStartOffset(terminatorLine))
        editor.caretModel.moveToOffset(bodyOffset)
        return EnterHandlerDelegate.Result.Stop
    }
}
