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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.lang.syntax.LuaSyntax

class LuaFormatBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    val spacer: LuaSpacingBuilder,
    // FORMAT-05: alignment to apply to this block's own `=` (ASSIGN) child, threaded
    // down from the enclosing BLOCK / FIELD_LIST so the `=` columns line up across a run.
    private val assignAlignment: Alignment? = null,
) : AbstractBlock(
    node,
    wrap,
    alignment,
) {
    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        addChildBlocks(blocks, myNode)
        return blocks.toList()
    }

    fun addChildBlocks(collected: MutableList<Block>, node: ASTNode) {
        // Blocks that return the same Alignment object will be aligned together.
        val listAlignment = when (node.elementType) {
            LuaElementTypes.NAME_LIST,
            LuaElementTypes.EXPR_LIST ->
                if (listHasMultipleItems(node)) Alignment.createAlignment() else null

            else -> null
        }

        // FORMAT-04: a shared wrap for the items of an argument / table list.
        val itemWrap = itemWrap(node)
        // FORMAT-05: per-statement / per-field `=` alignment groups.
        val assignAlignments = assignAlignmentGroups(node)

        for (child in node.children()) {
            if (child.elementType == TokenType.WHITE_SPACE) {
                continue
            }
            if (child.elementType == LuaElementTypes.BLOCK && child.firstChildNode == null) {
                continue
            }

            val childAlignment =
                if (child.elementType == LuaElementTypes.ASSIGN && assignAlignment != null) {
                    assignAlignment
                } else {
                    listAlignment
                }

            collected.add(
                LuaFormatBlock(
                    child,
                    childWrap(node, child, itemWrap),
                    childAlignment,
                    spacer,
                    assignAlignments[child],
                )
            )
        }
    }

    // FORMAT-04: WRAP_AS_NEEDED/ALWAYS/NONE wrap shared across an argument or table item list.
    private fun itemWrap(node: ASTNode): Wrap? = when {
        node.elementType == LuaElementTypes.EXPR_LIST &&
            node.treeParent?.elementType == LuaElementTypes.ARGS ->
            Wrap.createWrap(wrapType(spacer.luaSettings.WRAP_ARGUMENTS), true)

        node.elementType == LuaElementTypes.FIELD_LIST ->
            Wrap.createWrap(wrapType(spacer.luaSettings.WRAP_TABLE_CONSTRUCTOR), true)

        else -> null
    }

    private fun childWrap(parent: ASTNode, child: ASTNode, itemWrap: Wrap?): Wrap? {
        if (itemWrap == null) return null
        return when (parent.elementType) {
            LuaElementTypes.EXPR_LIST -> if (child.elementType != LuaElementTypes.COMMA) itemWrap else null
            LuaElementTypes.FIELD_LIST -> if (child.elementType == LuaElementTypes.FIELD) itemWrap else null
            else -> null
        }
    }

    private fun wrapType(setting: Int): WrapType = when (setting) {
        CommonCodeStyleSettings.DO_NOT_WRAP -> WrapType.NONE
        CommonCodeStyleSettings.WRAP_ALWAYS -> WrapType.ALWAYS
        else -> WrapType.CHOP_DOWN_IF_LONG
    }

    // FORMAT-05: map each assignment-statement / table-field child to the shared `=` alignment
    // for its run, so the threaded `assignAlignment` lines the `=` columns up.
    private fun assignAlignmentGroups(node: ASTNode): Map<ASTNode, Alignment> = when {
        node.elementType == LuaElementTypes.BLOCK && spacer.luaSettings.ALIGN_CONSECUTIVE_ASSIGNMENTS ->
            consecutiveAssignmentGroups(node)

        node.elementType == LuaElementTypes.FIELD_LIST && spacer.luaSettings.ALIGN_TABLE_FIELDS ->
            tableFieldGroup(node)

        else -> emptyMap()
    }

    private fun consecutiveAssignmentGroups(block: ASTNode): Map<ASTNode, Alignment> {
        val groups = HashMap<ASTNode, Alignment>()
        var run = mutableListOf<ASTNode>()
        var blankSeen = false

        fun flush() {
            if (run.size >= 2) {
                val alignment = Alignment.createAlignment(true)
                run.forEach { groups[it] = alignment }
            }
            run = mutableListOf()
        }

        for (child in block.children()) {
            if (child.elementType == TokenType.WHITE_SPACE) {
                if (child.text.count { it == '\n' } >= 2) blankSeen = true
                continue
            }
            if (isAlignableAssignment(child)) {
                if (blankSeen) flush()
                run.add(child)
            } else {
                flush()
            }
            blankSeen = false
        }
        flush()
        return groups
    }

    private fun tableFieldGroup(fieldList: ASTNode): Map<ASTNode, Alignment> {
        val fields = fieldList.children()
            .filter { it.elementType == LuaElementTypes.FIELD && it.findChildByType(LuaElementTypes.ASSIGN) != null }
            .toList()
        if (fields.size < 2) return emptyMap()
        val alignment = Alignment.createAlignment(true)
        return fields.associateWith { alignment }
    }

    private fun isAlignableAssignment(node: ASTNode): Boolean {
        val type = node.elementType
        if (type != LuaElementTypes.ASSIGNMENT_STATEMENT && type != LuaElementTypes.LOCAL_VAR_DECL) return false
        return node.findChildByType(LuaElementTypes.ASSIGN) != null
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
    val luaSettings: LuaCodeStyleSettings = LuaCodeStyleSettings.getInstance(settings)
        ?: LuaCodeStyleSettings(settings)
    val commonSettings = settings.getCommonSettings(LuaLanguage)

    fun getSpacing(
        parent: LuaFormatBlock,
        left: LuaFormatBlock?,
        right: LuaFormatBlock
    ): Spacing? {

        when {
            // Single space between consecutive unary minus
            left.hasElementType(LuaElementTypes.MINUS)
                -> if (right.hasElementType(LuaElementTypes.MINUS)) return SINGLE_SPACING

            // Single space after unary NOT (a keyword operator: `not x`),
            // no space after symbolic unary operators (`-x`, `#t`, `~x`).
            // The operator is the left `unOp` node; test ITS child, not the right operand.
            left.hasElementType(LuaElementTypes.UN_OP)
                -> return if (left?.node?.findChildByType(LuaElementTypes.NOT) != null) SINGLE_SPACING else NO_SPACING

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

            // FORMAT-03: separate functions by a settings-driven number of blank lines,
            // capping existing runs at KEEP_BLANK_LINES_IN_CODE.
            left.hasElementType(LuaElementTypes.FUNC_DECL) ||
                    left.hasElementType(LuaElementTypes.LOCAL_FUNC_DECL)
                -> return Spacing.createSpacing(
                    0,
                    0,
                    commonSettings.BLANK_LINES_AROUND_METHOD + 1,
                    true,
                    commonSettings.KEEP_BLANK_LINES_IN_CODE,
                )

            // No spacing between fields a . b. c -> a.b.c
            right.hasElementType(LuaElementTypes.INDEX_EXPR, LuaElementTypes.METHOD_EXPR)
                -> return NO_SPACING

            right.hasElementType(LuaElementTypes.NAME_REF) &&
                    anyOf(left?.node?.elementType, LuaElementTypes.DOT, LuaElementTypes.COLON)
                -> return NO_SPACING
        }

        // FORMAT-03-02: between statements that are already on separate lines, keep the line
        // break but cap blank-line runs at KEEP_BLANK_LINES_IN_CODE.
        if (left != null &&
            (parent.hasElementType(LuaElementTypes.BLOCK) || parent.node.treeParent == null)
        ) {
            val gap = right.node.treePrev
            if (gap?.elementType == TokenType.WHITE_SPACE && gap.text.contains('\n')) {
                return Spacing.createSpacing(0, 0, 1, true, commonSettings.KEEP_BLANK_LINES_IN_CODE)
            }
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