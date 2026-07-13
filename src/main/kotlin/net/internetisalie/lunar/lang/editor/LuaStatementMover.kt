package net.internetisalie.lunar.lang.editor

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaBlockParent
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaRepeatStatement
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * Structural Move Statement Up/Down (Ctrl+Shift+↑/↓) for Lua. Moves a whole [LuaStatement] over its
 * sibling, enters an adjacent block body (swap with the block's opening line going down / its `end` line
 * going up) and leaves the enclosing block over its delimiter — all as platform line-range swaps, with the
 * moved line re-indented by the platform (`MoveInfo.indentTarget`). Block delimiters (`end`/`until`/`then`/
 * `do`) are never `LuaStatement`s, so they are never swapped away (no corruption). A `repeat…until` body or
 * a file-edge statement prohibits the move; a caret with no enclosing statement / inside a multi-line token
 * returns `false` so the platform `LineMover` runs. Application-level EP (self-guards on [LuaFile]).
 * EDITOR-07-01/-02/-04. Design §2.1 / §3.1.
 */
class LuaStatementMover : StatementUpDownMover() {

    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is LuaFile) return false
        val document = editor.document
        val offset = getLineStartSafeOffset(document, getLineRangeFromSelection(editor).startLine)
        if (isInsideMultilineToken(file, offset)) return false
        val statement = enclosingStatement(file, offset) ?: return false
        info.toMove = lineRangeOf(statement, document)
        val target = targetRange(statement, down, document)
        if (target == null) return info.prohibitMove()
        info.toMove2 = target
        return true
    }

    private fun isInsideMultilineToken(file: PsiFile, offset: Int): Boolean {
        val leaf = file.findElementAt(offset) ?: return false
        return leaf !is PsiWhiteSpace && leaf.text.contains('\n')
    }

    private fun enclosingStatement(file: PsiFile, offset: Int): LuaStatement? {
        val text = file.text
        var scan = offset
        while (scan < text.length && (text[scan] == ' ' || text[scan] == '\t')) scan++
        var element: PsiElement = file.findElementAt(scan) ?: return null
        if (element is PsiComment) element = PsiTreeUtil.nextVisibleLeaf(element) ?: return null
        return PsiTreeUtil.getParentOfType(element, LuaStatement::class.java, false)
    }

    private fun targetRange(statement: LuaStatement, down: Boolean, document: Document): LineRange? {
        val sibling = adjacentStatement(statement, down)
        if (sibling != null) {
            return if (sibling is LuaBlockParent && isMultiLine(sibling, document)) {
                delimiterLine(sibling, enteringStart = down, document = document)
            } else {
                lineRangeOf(sibling, document)
            }
        }
        val construct = (statement.parent as? LuaBlock)?.parent
        if (construct !is LuaBlockParent || construct is LuaFile || construct is LuaRepeatStatement) return null
        return delimiterLine(construct, enteringStart = !down, document = document)
    }

    private fun adjacentStatement(statement: LuaStatement, down: Boolean): LuaStatement? {
        var sibling = if (down) statement.nextSibling else statement.prevSibling
        while (sibling != null && (sibling is PsiWhiteSpace || sibling is PsiComment)) {
            sibling = if (down) sibling.nextSibling else sibling.prevSibling
        }
        return sibling as? LuaStatement
    }

    /** The single line holding [construct]'s opening ([enteringStart]) or closing delimiter. */
    private fun delimiterLine(construct: PsiElement, enteringStart: Boolean, document: Document): LineRange {
        val anchor = if (enteringStart) construct.textRange.startOffset else construct.textRange.endOffset - 1
        val line = document.getLineNumber(anchor)
        return LineRange(line, line + 1)
    }

    private fun isMultiLine(psi: PsiElement, document: Document): Boolean =
        document.getLineNumber(psi.textRange.startOffset) != document.getLineNumber(psi.textRange.endOffset - 1)

    private fun lineRangeOf(psi: PsiElement, document: Document): LineRange {
        val end = (psi.textRange.endOffset - 1).coerceAtLeast(psi.textRange.startOffset)
        return LineRange(document.getLineNumber(psi.textRange.startOffset), document.getLineNumber(end) + 1)
    }
}
