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
import net.internetisalie.lunar.lang.syntax.LuaSyntax

class LuaFormatBlock(
    node: ASTNode,
    alignment: Alignment?,
    val spacingBuilder: SpacingBuilder,
) : AbstractBlock(
    node,
    Wrap.createWrap(WrapType.NONE, false),
    alignment,
) {
    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        addChildBlocks(blocks, myNode)
        return blocks.toList()
    }

    fun addChildBlocks(collected: MutableList<Block>, node : ASTNode) {
        // Blocks that return the same Alignment object will be aligned together.
        val alignment = when (node.elementType) {
            LuaElementTypes.NAME_LIST,
            LuaElementTypes.EXPR_LIST ->
                if (listHasMultipleItems(node)) Alignment.createAlignment() else null
            else -> null
        }

        for (child in node.children()) {
            if (child.elementType == TokenType.WHITE_SPACE) { continue }
            if (child.elementType == LuaElementTypes.BLOCK && child.firstChildNode == null) { continue }
            collected.add(
                LuaFormatBlock(
                    child,
                    alignment,
                    spacingBuilder,
                )
            )
        }
    }

    private fun listHasMultipleItems(node : ASTNode) : Boolean {
        return node.children().filter {
            it.elementType != LuaElementTypes.COMMA && it.elementType != TokenType.WHITE_SPACE
        }.count() > 1
    }

    // Calculate the indent relative to this block's parent
    override fun getIndent(): Indent? {
        when {
            node.treeParent == null -> return Indent.getNoneIndent()
            node.treeParent.elementType is IFileElementType -> return Indent.getNoneIndent()
        }

        return when (node.elementType) {
            LuaElementTypes.BLOCK -> Indent.getNormalIndent()
            LuaElementTypes.LABEL -> Indent.getAbsoluteLabelIndent()
            LuaElementTypes.NAME_LIST -> {
                if (listHasMultipleItems(node)) Indent.getContinuationIndent()
                else Indent.getNoneIndent()
            }
            LuaElementTypes.NAME_REF -> Indent.getNoneIndent()
            LuaElementTypes.VAR_LIST -> Indent.getContinuationWithoutFirstIndent()
            LuaElementTypes.VAR ->
                if (node.treeParent.elementType == LuaElementTypes.VAR_LIST) Indent.getContinuationWithoutFirstIndent()
                else Indent.getNoneIndent()
            LuaElementTypes.EXPR_LIST -> Indent.getContinuationIndent()
            LuaElementTypes.FIELD -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    override fun isLeaf(): Boolean {
        return myNode.firstChildNode == null;
    }

    // Calculate a new first child's default indent relative to this block
    override fun getChildIndent(): Indent? {
        return when (node.elementType) {
            is IFileElementType -> Indent.getNoneIndent()
            LuaElementTypes.BLOCK -> Indent.getNoneIndent() // block is already indented
            LuaElementTypes.TABLE_CONSTRUCTOR -> Indent.getNormalIndent()
            LuaElementTypes.EXPR -> Indent.getContinuationWithoutFirstIndent()
            LuaElementTypes.PAR_LIST -> this.indent
            LuaElementTypes.NAME_LIST -> this.indent
            LuaElementTypes.VAR_LIST -> this.indent
            else -> Indent.getNoneIndent()
        }
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
                    null,
                    createSpacingBuilder(codeStyleSettings),
                ),
                codeStyleSettings
            )
    }

    private fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
        val builder = SpacingBuilder(settings, LuaLanguage)
        val luaSettings = LuaCodeStyleSettings.getInstance(settings) as LuaCodeStyleSettings
        val commonSettings = settings.getCommonSettings(LuaLanguage)

        // Build rules
        builder.around(LuaElementTypes.ASSIGN).spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
        builder.around(LuaSyntax.LogicalBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_LOGICAL_OPERATORS)
        builder.around(LuaElementTypes.EQ).spaceIf(commonSettings.SPACE_AROUND_EQUALITY_OPERATORS)
        builder.around(LuaSyntax.RelationalBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_RELATIONAL_OPERATORS)
        builder.around(LuaSyntax.BitwiseBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_BITWISE_OPERATORS)
        builder.around(LuaSyntax.AdditiveBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_ADDITIVE_OPERATORS)
        builder.around(LuaSyntax.MultiplicativeBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
        builder.around(LuaSyntax.ShiftBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_SHIFT_OPERATORS)
        builder.around(LuaSyntax.UnaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_UNARY_OPERATOR)
        builder.after(LuaElementTypes.COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
        builder.withinPair(LuaElementTypes.LPAREN, LuaElementTypes.RPAREN).spaceIf(commonSettings.SPACE_WITHIN_PARENTHESES)
        builder.withinPair(LuaElementTypes.LBRACK, LuaElementTypes.RBRACK).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
        builder.withinPair(LuaElementTypes.LCURLY, LuaElementTypes.RCURLY).spaceIf(commonSettings.SPACE_WITHIN_BRACES)

        return builder
    }
}