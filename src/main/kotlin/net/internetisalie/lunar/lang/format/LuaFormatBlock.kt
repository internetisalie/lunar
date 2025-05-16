/*
 * Copyright 2000-2009 JetBrains s.r.o.
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.lang.format

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.children
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.lang.syntax.LuaSyntax

class LuaFormatBlock(
    node: ASTNode,
    alignment: Alignment?,
    val spacer: LuaSpacingBuilder,
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

    fun addChildBlocks(collected: MutableList<Block>, node: ASTNode) {
        // Blocks that return the same Alignment object will be aligned together.
        val alignment = when (node.elementType) {
            LuaElementTypes.NAME_LIST,
            LuaElementTypes.EXPR_LIST ->
                if (listHasMultipleItems(node)) Alignment.createAlignment() else null

            else -> null
        }

        for (child in node.children()) {
            if (child.elementType == TokenType.WHITE_SPACE) {
                continue
            }
            if (child.elementType == LuaElementTypes.BLOCK && child.firstChildNode == null) {
                continue
            }
            collected.add(
                LuaFormatBlock(
                    child,
                    alignment,
                    spacer,
                )
            )
        }
    }

    private fun listHasMultipleItems(node: ASTNode): Boolean {
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

    override fun getSpacing(left: Block?, right: Block): Spacing? {
        return spacer.getSpacing(
            this,
            left as? LuaFormatBlock,
            right as LuaFormatBlock,
        );
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
                    LuaSpacingBuilder(codeStyleSettings),
                ),
                codeStyleSettings
            )
    }
}

class LuaSpacingBuilder(
    val settings: CodeStyleSettings,
) {
    private val spacingBuilder: SpacingBuilder = SpacingBuilder(settings, LuaLanguage)
    private val luaSettings = LuaCodeStyleSettings.getInstance(settings) as LuaCodeStyleSettings
    private val commonSettings = settings.getCommonSettings(LuaLanguage)

    fun getSpacing(
        parent: LuaFormatBlock,
        left: LuaFormatBlock?,
        right: LuaFormatBlock
    ): Spacing? {

        when {
            // Single space between consecutive unary minus
            left.hasElementType(LuaElementTypes.MINUS)
                -> if (right.hasElementType(LuaElementTypes.MINUS)) return SINGLE_SPACING

            // Single space after unary NOT,
            // no space after other unary operators
            left.hasElementType(LuaElementTypes.UN_OP)
                -> return if (right.hasElementType(LuaElementTypes.NOT)) SINGLE_SPACING else NO_SPACING

            // Single newline after LuaCATS comment before CommentOwner,
            // double newline after freestanding LuaCATS comment
            left.hasElementType(LuaLazyElementTypes.LUACATS_COMMENT)
                -> return if (right.hasElementType(
                    LuaElementTypes.BLOCK,
                    LuaElementTypes.FUNC_DECL,
                    LuaElementTypes.LOCAL_VAR_DECL,
                    LuaElementTypes.LOCAL_FUNC_DECL,
                )
            ) NEWLINE_SPACING
            else STANZA_SPACING

            // No spacing before parenthesized args
            // Single spacing before tableConstructor or string args
            right.hasElementType(LuaElementTypes.ARGS)
                -> return if (right.node.firstChildNode.elementType == LuaElementTypes.LPAREN) NO_SPACING
            else SINGLE_SPACING

            // No spacing inside brackets
            right.hasElementType(LuaElementTypes.RBRACK, LuaElementTypes.LBRACK) ||
                    left.hasElementType(LuaElementTypes.LBRACK)
                -> return SINGLE_SPACING

            // No spacing inside empty parens
            right.hasElementType(LuaElementTypes.RPAREN, LuaElementTypes.LPAREN) ||
                    left.hasElementType(LuaElementTypes.LPAREN)
                -> return NO_SPACING

            // Only 1 newline before block end, and at least one space
            right.hasElementType(
                LuaElementTypes.END,
                LuaElementTypes.ELSEIF,
                LuaElementTypes.ELSE,
                LuaElementTypes.UNTIL
            )
                -> return Spacing.createDependentLFSpacing(1, 1, right.node.psi.parent.textRange, false, 0)

            // No spacing between parens and arg/param lists
            left.hasElementType(LuaElementTypes.LPAREN) &&
                    right.hasElementType(LuaElementTypes.PAR_LIST)
                -> return NO_SPACING

            // No spacing between anonymous function and param list
            left.hasElementType(LuaElementTypes.FUNCTION) &&
                    right.hasElementType(LuaElementTypes.LPAREN)
                -> return NO_SPACING

            // Only 1 newline after field sep, and at least one space
            right.hasElementType(LuaElementTypes.FIELD) &&
                    left.hasElementType(LuaElementTypes.FIELD_SEP)
                -> return Spacing.createDependentLFSpacing(1, 1, right.node.psi.parent.textRange, false, 0)

            // separate functions by exactly 1 blank line
            left.hasElementType(LuaElementTypes.FUNC_DECL) ||
                    left.hasElementType(LuaElementTypes.LOCAL_FUNC_DECL)
                -> return STANZA_SPACING

            // No spacing between fields a . b. c -> a.b.c
            right.hasElementType(LuaElementTypes.INDEX_EXPR, LuaElementTypes.METHOD_EXPR)
                -> return NO_SPACING

            right.hasElementType(LuaElementTypes.NAME_REF) &&
                    anyOf(left?.node?.elementType, LuaElementTypes.DOT, LuaElementTypes.COLON)
                -> return NO_SPACING
        }

        return spacingBuilder.getSpacing(parent, left, right)
    }

    init {
        spacingBuilder.around(LuaElementTypes.ASSIGN).spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
        spacingBuilder.around(LuaSyntax.LogicalBinaryOperatorTokens)
            .spaceIf(commonSettings.SPACE_AROUND_LOGICAL_OPERATORS)
        spacingBuilder.around(LuaElementTypes.EQ).spaceIf(commonSettings.SPACE_AROUND_EQUALITY_OPERATORS)
        spacingBuilder.around(LuaSyntax.RelationalBinaryOperatorTokens)
            .spaceIf(commonSettings.SPACE_AROUND_RELATIONAL_OPERATORS)
        spacingBuilder.around(LuaSyntax.BitwiseBinaryOperatorTokens)
            .spaceIf(commonSettings.SPACE_AROUND_BITWISE_OPERATORS)
        spacingBuilder.around(LuaSyntax.AdditiveBinaryOperatorTokens)
            .spaceIf(commonSettings.SPACE_AROUND_ADDITIVE_OPERATORS)
        spacingBuilder.around(LuaSyntax.MultiplicativeBinaryOperatorTokens)
            .spaceIf(commonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
        spacingBuilder.around(LuaSyntax.ShiftBinaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_SHIFT_OPERATORS)
        spacingBuilder.around(LuaSyntax.UnaryOperatorTokens).spaceIf(commonSettings.SPACE_AROUND_UNARY_OPERATOR)
        spacingBuilder.after(LuaElementTypes.COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
        spacingBuilder.withinPair(LuaElementTypes.LPAREN, LuaElementTypes.RPAREN)
            .spaceIf(commonSettings.SPACE_WITHIN_PARENTHESES)
        spacingBuilder.withinPair(LuaElementTypes.LBRACK, LuaElementTypes.RBRACK)
            .spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
        spacingBuilder.withinPair(LuaElementTypes.LCURLY, LuaElementTypes.RCURLY)
            .spaceIf(commonSettings.SPACE_WITHIN_BRACES)

        spacingBuilder.around(LuaSyntax.KeywordTokens).spaces(1)
    }

    companion object {
        val NO_SPACING: Spacing = Spacing.createSpacing(0, 0, 0, false, 0)
        val SINGLE_SPACING: Spacing = Spacing.createSpacing(1, 1, 0, true, 1)
        val NEWLINE_SPACING: Spacing = Spacing.createSpacing(0, 0, 1, false, 0)
        val STANZA_SPACING: Spacing = Spacing.createSpacing(1, 1, 2, true, 1)
    }
}

fun LuaFormatBlock?.hasElementType(vararg elementTypes: IElementType?): Boolean {
    return elementTypes.any { it == this?.node?.elementType }
}

fun <T> anyOf(want: T, vararg gots: T) = gots.any { it == want }