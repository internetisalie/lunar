package net.internetisalie.lunar.lang.insight

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.*
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsCommentImpl

const val PLACEHOLDER_TEXT: String = "..."

class LuaFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        val descriptors = ArrayList<FoldingDescriptor>()
        root.accept(LuaFoldingVisitor(descriptors))
        root.accept(LuaLazyFoldingVisitor(descriptors))
        foldCustomRegions(root, descriptors)
        return descriptors.toTypedArray()
    }

    private fun foldCustomRegions(root: PsiElement, descriptors: MutableList<FoldingDescriptor>) {
        val comments = PsiTreeUtil.findChildrenOfType(root, PsiComment::class.java)
        val stack = mutableListOf<PsiComment>()
        for (comment in comments) {
            val text = comment.text.trim()
            if (text.startsWith("--#region") || text.startsWith("-- #region")) {
                stack.add(comment)
            } else if (text.startsWith("--#endregion") || text.startsWith("-- #endregion")) {
                if (stack.isNotEmpty()) {
                    val start = stack.removeAt(stack.size - 1)
                    descriptors.add(
                        FoldingDescriptor(
                            start.node,
                            TextRange(start.textRange.startOffset, comment.textRange.endOffset)
                        )
                    )
                }
            }
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return when (node.elementType) {
            LuaElementTypes.LONGCOMMENT,
            LuaElementTypes.STRING,
            LuaCatsElementTypes.COMMENT,
                -> true
            else -> false
        }
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        val text = node.text
        if (text.startsWith("--#region") || text.startsWith("-- #region")) {
            return text.substringAfter("region").trim().ifEmpty { PLACEHOLDER_TEXT }
        }
        return when (node.elementType) {
            LuaElementTypes.STRING -> {
                val delimiterLength = getLuaStringDelimiterLength(text)
                if (delimiterLength > 1) {
                    val opening = text.substring(0, delimiterLength)
                    val closing = text.substring(text.length - delimiterLength)
                    opening + summarize(extractLuaString(text)) + closing
                } else if (delimiterLength == 1) {
                    val quote = text.substring(0, 1)
                    quote + summarize(extractLuaString(text)) + quote
                } else {
                    "[[" + summarize(extractLuaString(text)) + "]]"
                }
            }
            LuaElementTypes.LONGCOMMENT -> {
                val delimiterLength = getLuaCommentDelimiterLength(text)
                if (delimiterLength > 2) {
                    val opening = text.substring(0, delimiterLength)
                    val closing = text.substring(text.length - delimiterLength + 2)
                    opening + summarize(extractLuaComment(text)) + closing
                } else {
                    "--[[" + summarize(extractLuaComment(text)) + "]]"
                }
            }
            LuaElementTypes.SHORTCOMMENT -> "-- " + summarize(extractLuaComment(text))
            LuaCatsElementTypes.COMMENT -> "--- " + summarize(LuaCatsSummary.getText(node.psi as LuaCatsComment) ?: "")
            LuaElementTypes.TABLE_CONSTRUCTOR -> "{...}"
            else -> PLACEHOLDER_TEXT
        }
    }
}

const val NEWLINE = '\n'

class LuaFoldingVisitor(
    private val descriptors: MutableList<FoldingDescriptor>
) : LuaRecursiveVisitor() {

    private fun foldBlocks(blocks: List<LuaBlock>) {
        for (block in blocks) {
            var prev: PsiElement = block
            var prevPrev = prev.prevSibling
            while (prevPrev is PsiWhiteSpace) {
                prev = prevPrev
                prevPrev = prev.prevSibling
            }

            var next: PsiElement = block
            var nextNext = next.nextSibling
            while (nextNext is PsiWhiteSpace) {
                next = nextNext
                nextNext = next.nextSibling
            }

            val prevStart = prev.textRange.startOffset
            var nextEnd = next.textRange.endOffset
            if (blocks.size > 2 &&
                next is PsiWhiteSpace &&
                next.text.lastIndexOf(NEWLINE) == next.textLength - 1) {
                nextEnd--
            }

            descriptors.add(
                FoldingDescriptor(
                    block.node,
                    TextRange(prevStart, nextEnd)
                )
            )
        }
    }

    private fun foldTable(table : LuaTableConstructor) {
        if (table.textLength<3) return
        descriptors.add(
            FoldingDescriptor(
                table.node,
                TextRange(
                    table.textRange.startOffset,
                    table.textRange.endOffset
                )
            )
        )
    }

    private fun foldString(node: ASTNode) {
        descriptors.add(
            FoldingDescriptor(
                node,
                node.textRange
            )
        )
    }

    private fun foldComment(node: ASTNode) {
        descriptors.add(
            FoldingDescriptor(
                node,
                node.textRange,
            )
        )
    }

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        when {
            element is LuaBlockParent -> foldBlocks(element.getBlockList())
            element is PsiComment -> foldComment(element.node)
            element is LuaTableConstructor -> foldTable(element)
            element.elementType == LuaElementTypes.STRING -> {
                if (element.textContains('\n')) foldString(element.node)
            }
        }
    }

}

class LuaLazyFoldingVisitor(
    val descriptors: ArrayList<FoldingDescriptor>
) : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
        PsiTreeUtil.findChildrenOfType(element, LuaCatsCommentImpl::class.java).forEach {
            if (it.textContains('\n')) foldComment(it.node)
        }
    }

    private fun foldComment(node: ASTNode) {
        descriptors.add(
            FoldingDescriptor(
                node,
                node.textRange,
            )
        )
    }
}
