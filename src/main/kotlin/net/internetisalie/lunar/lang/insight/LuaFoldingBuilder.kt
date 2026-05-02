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
                    val descriptor = FoldingDescriptor(
                        start.node,
                        TextRange(start.textRange.startOffset, comment.textRange.endOffset)
                    )
                    descriptors.add(descriptor)
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
            LuaElementTypes.SHORTCOMMENT -> {
                if (text.startsWith("---")) {
                    "--- " + summarize(extractLuaComment(text))
                } else {
                    "-- " + summarize(extractLuaComment(text))
                }
            }
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
        if (blocks.isEmpty()) return
        
        // For statements with multiple blocks (if/elseif/else), we only want ONE fold
        // spanning from the first keyword to END, not one fold per block
        val firstBlock = blocks.first()
        val lastBlock = blocks.last()
        
        // Find the start: walk backwards from first block to find first keyword
        var start: PsiElement? = firstBlock.prevSibling
        while (start is PsiWhiteSpace) {
            start = start.prevSibling
        }
        if (start == null) start = firstBlock
        
        // Find the end: walk forwards from last block to find END keyword  
        var end: PsiElement? = lastBlock.nextSibling
        while (end is PsiWhiteSpace) {
            end = end.nextSibling
        }
        if (end == null) end = lastBlock
        
        val startOffset = start.textRange.startOffset
        var endOffset = end.textRange.endOffset
        
        // Handle trailing newline
        if (end is PsiWhiteSpace && end.text.lastIndexOf(NEWLINE) == end.textLength - 1) {
            endOffset--
        }
        
        descriptors.add(
            FoldingDescriptor(
                firstBlock.node,
                TextRange(startOffset, endOffset)
            )
        )
    }
    
    private fun foldConsecutiveDocComments(comment: PsiComment) {
        // Fold consecutive --- doc comments together
        val text = comment.text
        if (!text.startsWith("---")) return
        
        // Check if there's a doc comment before this one
        var prev = comment.prevSibling
        while (prev is PsiWhiteSpace) {
            prev = prev.prevSibling
        }
        if (prev is PsiComment && prev.text.startsWith("---")) {
            // There's a doc comment before this one, skip (will be handled by the first comment)
            return
        }
        
        // Find the last consecutive --- comment
        var last: PsiElement = comment
        var next = comment.nextSibling
        while (next != null) {
            if (next is PsiWhiteSpace) {
                next = next.nextSibling
                continue
            }
            if (next is PsiComment && next.text.startsWith("---")) {
                last = next
                next = next.nextSibling
            } else {
                break
            }
        }
        
        // Fold from this comment to the last consecutive one
        descriptors.add(
            FoldingDescriptor(
                comment.node,
                TextRange(comment.textRange.startOffset, last.textRange.endOffset)
            )
        )
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
            element is LuaBlockParent -> {
                // Handle specific statement types
                when (element) {
                    is LuaIfStatement -> foldIfStatement(element)
                    is LuaWhileStatement -> foldStatementWithEnd(element, element.node)
                    is LuaNumericForStatement -> foldStatementWithEnd(element, element.node)
                    is LuaGenericForStatement -> foldStatementWithEnd(element, element.node)
                    is LuaRepeatStatement -> foldStatementWithEnd(element, element.node)
                    is LuaDoStatement -> foldStatementWithEnd(element, element.node)
                    is LuaFuncDecl -> foldFunctionDecl(element, element.node)
                    is LuaLocalFuncDecl -> foldFunctionDecl(element, element.node)
                    is LuaFuncDef -> foldFunctionDecl(element, element.node)
                    else -> foldBlocks(element.getBlockList())
                }
            }
            element is PsiComment -> {
                val text = element.text
                if (text.startsWith("---")) {
                    foldConsecutiveDocComments(element)
                } else if (!text.startsWith("--#region") && !text.startsWith("-- #region") && 
                           !text.startsWith("--#endregion") && !text.startsWith("-- #endregion")) {
                    // Don't fold region markers here - they're handled by foldCustomRegions
                    foldComment(element.node)
                }
            }
            element is LuaTableConstructor -> foldTable(element)
            element.elementType == LuaElementTypes.STRING -> {
                if (element.textContains('\n')) foldString(element.node)
            }
        }
    }
    
    private fun foldIfStatement(ifStmt: LuaIfStatement) {
        // Fold from IF keyword to END keyword
        val children = ifStmt.node.getChildren(null)
        val ifKeyword = children.find { it.elementType == LuaElementTypes.IF }
        val endKeyword = children.findLast { it.elementType == LuaElementTypes.END }
        
        if (ifKeyword != null && endKeyword != null) {
            descriptors.add(
                FoldingDescriptor(
                    ifStmt.node,
                    TextRange(ifKeyword.textRange.startOffset, endKeyword.textRange.endOffset)
                )
            )
        }
    }
    
    private fun foldStatementWithEnd(element: LuaBlockParent, node: ASTNode) {
        // Generic handler for statements that have END keyword (while, for, repeat, do)
        val children = node.getChildren(null)
        val startNode = children.find { 
            it.elementType in setOf(LuaElementTypes.WHILE, LuaElementTypes.FOR, LuaElementTypes.REPEAT, LuaElementTypes.DO)
        }
        val endNode = children.findLast { it.elementType == LuaElementTypes.END }
        
        if (startNode != null && endNode != null) {
            descriptors.add(
                FoldingDescriptor(
                    node,
                    TextRange(startNode.textRange.startOffset, endNode.textRange.endOffset)
                )
            )
        }
    }
    
    private fun foldFunctionDecl(element: LuaBlockParent, node: ASTNode) {
        // Fold from FUNCTION keyword to END keyword
        val children = node.getChildren(null)
        val funcKeyword = children.find { it.elementType == LuaElementTypes.FUNCTION }
        val endKeyword = children.findLast { it.elementType == LuaElementTypes.END }
        
        if (funcKeyword != null && endKeyword != null) {
            descriptors.add(
                FoldingDescriptor(
                    node,
                    TextRange(funcKeyword.textRange.startOffset, endKeyword.textRange.endOffset)
                )
            )
        }
    }

}

class LuaLazyFoldingVisitor(
    val descriptors: ArrayList<FoldingDescriptor>
) : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
        val allComments = PsiTreeUtil.findChildrenOfType(element, LuaCatsCommentImpl::class.java)
        val processedComments = mutableSetOf<LuaCatsCommentImpl>()
        
        for (comment in allComments) {
            if (processedComments.contains(comment)) continue
            
            // Check if there's a doc comment before this one
            var prev = comment.prevSibling
            while (prev is PsiWhiteSpace) {
                prev = prev.prevSibling
            }
            if (prev is LuaCatsCommentImpl && !processedComments.contains(prev)) {
                // There's an unprocessed doc comment before this one, skip
                continue
            }
            
            // Find the range of consecutive --- comments
            var last: LuaCatsCommentImpl = comment
            var next = comment.nextSibling
            while (next != null) {
                if (next is PsiWhiteSpace) {
                    next = next.nextSibling
                    continue
                }
                if (next is LuaCatsCommentImpl && !processedComments.contains(next)) {
                    last = next
                    processedComments.add(next)
                    next = next.nextSibling
                } else {
                    break
                }
            }
            
            // Always fold doc comments, even if there's only one
            descriptors.add(
                FoldingDescriptor(
                    comment.node,
                    TextRange(comment.textRange.startOffset, last.textRange.endOffset)
                )
            )
            processedComments.add(comment)
        }
    }
}
