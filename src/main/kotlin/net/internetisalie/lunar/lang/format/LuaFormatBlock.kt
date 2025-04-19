package net.internetisalie.lunar.lang.format

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.children
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.IFileElementType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes

class LuaFormatBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    val spacingBuilder: SpacingBuilder,
) : AbstractBlock(node, wrap, alignment) {
    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        addChildBlocks(blocks, myNode)
        return blocks.toList()
    }

    fun addChildBlocks(collected: MutableList<Block>, node : ASTNode) {
        for (child in node.children()) {
            if (child.elementType == TokenType.WHITE_SPACE) { continue }
            collected.add(
                LuaFormatBlock(
                    child,
                    Wrap.createWrap(WrapType.NONE, false),
                    null,
                    spacingBuilder
                )
            )
        }
    }

    override fun getIndent(): Indent? {
        when {
            node.treeParent == null -> return Indent.getNoneIndent()
            node.treeParent.elementType is IFileElementType -> return Indent.getNoneIndent()
        }

        return when (node.elementType) {
            LuaElementTypes.BLOCK -> Indent.getNormalIndent()
            LuaElementTypes.LABEL -> Indent.getAbsoluteLabelIndent()
            LuaElementTypes.PAR_LIST -> Indent.getContinuationWithoutFirstIndent()
            LuaElementTypes.VAR_LIST -> Indent.getContinuationWithoutFirstIndent()
            LuaElementTypes.NAME_LIST -> Indent.getContinuationWithoutFirstIndent()
            LuaElementTypes.FIELD -> Indent.getNormalIndent()

            else -> return Indent.getNoneIndent()
        }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    override fun isLeaf(): Boolean {
        return myNode.firstChildNode == null;
    }
}

class LuaFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val codeStyleSettings = formattingContext.codeStyleSettings
        return FormattingModelProvider
            .createFormattingModelForPsiFile(
                formattingContext.containingFile,
                LuaFormatBlock(
                    formattingContext.node,
                    Wrap.createWrap(WrapType.NONE, false),
                    Alignment.createAlignment(),
                    createSpacingBuilder(codeStyleSettings)
                ),
                codeStyleSettings
            )
    }

    private fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
        return SpacingBuilder(settings, LuaLanguage)
    }
}