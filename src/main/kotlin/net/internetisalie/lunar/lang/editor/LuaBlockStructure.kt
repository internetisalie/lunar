package net.internetisalie.lunar.lang.editor

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * Shared block-structure PSI helpers for statement-list surrounds/unwraps (EDITOR-05; EDITOR-06 will
 * extend this same object with body/branch queries). Stateless [object]: holds no `Project`/`Editor`/
 * `PsiFile` references. Read methods are pure PSI reads; [replaceStatements]/[caretAfterWrap] mutate and
 * must be called inside a write command. Design §2.1 / §3.3.
 */
object LuaBlockStructure {

    /** Sentinel identifier a header surrounder embeds at its caret site; [caretAfterWrap] locates + removes it. */
    const val CARET = "__lunar_surround_caret__"

    /** The innermost [LuaBlock] that is a common ancestor of both offsets, or null. Design §3.3. */
    fun enclosingBlock(file: PsiFile, startOffset: Int, endOffset: Int): LuaBlock? {
        val start = leafAt(file, startOffset) ?: return null
        val end = if (endOffset > startOffset) leafAt(file, endOffset - 1) ?: start else start
        val common = PsiTreeUtil.findCommonParent(start, end) ?: return null
        return common as? LuaBlock ?: PsiTreeUtil.getParentOfType(common, LuaBlock::class.java, false)
    }

    private fun leafAt(file: PsiFile, offset: Int): PsiElement? =
        file.findElementAt(offset) ?: if (offset > 0) file.findElementAt(offset - 1) else null

    /** Whole statements of [block] fully inside the whitespace-trimmed selection; empty if any is split. §3.3. */
    fun statementsInRange(block: LuaBlock, startOffset: Int, endOffset: Int): List<LuaStatement> {
        val statements = block.statementList
        if (statements.isEmpty()) return emptyList()
        if (startOffset == endOffset) return statements.filter { it.textRange.contains(startOffset) }.take(1)
        val (start, end) = trimToText(block.containingFile.text, startOffset, endOffset)
        if (statements.any { splitsBoundary(it.textRange, start, end) }) return emptyList()
        return statements.filter { it.textRange.startOffset >= start && it.textRange.endOffset <= end }
    }

    private fun trimToText(text: CharSequence, from: Int, to: Int): Pair<Int, Int> {
        var start = from
        var end = to
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return start to end
    }

    private fun splitsBoundary(range: TextRange, start: Int, end: Int): Boolean {
        val overlaps = range.startOffset < end && range.endOffset > start
        val fullyInside = range.startOffset >= start && range.endOffset <= end
        return overlaps && !fullyInside
    }

    /** Source text of a contiguous statement run, joined with newlines. Design §2.1. */
    fun statementsText(statements: List<LuaStatement>): String = statements.joinToString("\n") { it.text }

    /**
     * Replace the sibling run [first]..[last] with [replacement] and reformat the inserted node, returning it.
     * Must run inside a write command. Design §2.1 / §3.1.
     */
    fun replaceStatements(first: LuaStatement, last: LuaStatement, replacement: PsiElement): PsiElement {
        if (first !== last) first.parent.deleteChildRange(first.nextSibling, last)
        val inserted = first.replace(replacement)
        return CodeStyleManager.getInstance(inserted.project).reformat(inserted)
    }

    /**
     * Caret offset after a wrap: a header template's [CARET] sentinel site (the sentinel is deleted), else the
     * wrapped body's first statement. Read from the post-reformat PSI, so it is indentation-safe. Design §3.1–§3.2.
     */
    fun caretAfterWrap(inserted: PsiElement): Int {
        val sentinel = PsiTreeUtil.collectElements(inserted) { it.text == CARET }.firstOrNull()
        if (sentinel != null) {
            val offset = sentinel.textRange.startOffset
            sentinel.delete()
            return offset
        }
        val body = PsiTreeUtil.findChildOfType(inserted, LuaBlock::class.java)
        return body?.statementList?.firstOrNull()?.textRange?.startOffset ?: inserted.textRange.startOffset
    }
}
