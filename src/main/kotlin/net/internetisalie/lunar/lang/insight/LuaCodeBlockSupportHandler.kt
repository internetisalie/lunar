package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.highlighting.AbstractCodeBlockSupportHandler
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.psi.LuaElementTypes

class LuaCodeBlockSupportHandler : AbstractCodeBlockSupportHandler() {
    private val keywordTokens = mapOf(
        LuaElementTypes.EMPTY_STATEMENT to TokenSet.create(
            LuaElementTypes.SEMI,
        ),
        LuaElementTypes.GOTO_STATEMENT to TokenSet.create(
            LuaElementTypes.GOTO,
        ),
        LuaElementTypes.BREAK_STATEMENT to TokenSet.create(
            LuaElementTypes.BREAK,
        ),
        LuaElementTypes.DO_STATEMENT to TokenSet.create(
            LuaElementTypes.DO,
            LuaElementTypes.END,
        ),
        LuaElementTypes.IF_STATEMENT to TokenSet.create(
            LuaElementTypes.IF,
            LuaElementTypes.THEN,
            LuaElementTypes.ELSEIF,
            LuaElementTypes.ELSE,
            LuaElementTypes.END,
        ),
        LuaElementTypes.NUMERIC_FOR_STATEMENT to TokenSet.create(
            LuaElementTypes.FOR,
            LuaElementTypes.DO,
            LuaElementTypes.END,
        ),
        LuaElementTypes.GENERIC_FOR_STATEMENT to TokenSet.create(
            LuaElementTypes.FOR,
            LuaElementTypes.DO,
            LuaElementTypes.IN,
            LuaElementTypes.END,
        ),
        LuaElementTypes.REPEAT_STATEMENT to TokenSet.create(
            LuaElementTypes.REPEAT,
            LuaElementTypes.UNTIL,
            LuaElementTypes.END,
        ),
        LuaElementTypes.WHILE_STATEMENT to TokenSet.create(
            LuaElementTypes.WHILE,
            LuaElementTypes.DO,
            LuaElementTypes.END,
        ),
        LuaElementTypes.FUNC_DECL to TokenSet.create(
            LuaElementTypes.FUNCTION,
            LuaElementTypes.END,
        ),
        LuaElementTypes.LOCAL_FUNC_DECL to TokenSet.create(
            LuaElementTypes.LOCAL,
            LuaElementTypes.FUNCTION,
            LuaElementTypes.END,
        ),
        LuaElementTypes.FUNC_DEF to TokenSet.create(
            LuaElementTypes.FUNCTION,
            LuaElementTypes.END,
        ),
    )

    private val topLevelElementTypes = TokenSet.create(*keywordTokens.keys.toTypedArray())

    private val keywordElementTypes = TokenSet.create(*keywordTokens.values.map{ it.types.toList() }.flatten().toTypedArray())

    private val blockElementTypes = TokenSet.create(
        LuaElementTypes.BLOCK
    )

    override fun getTopLevelElementTypes(): TokenSet {
        return topLevelElementTypes
    }

    override fun getKeywordElementTypes(): TokenSet {
        return keywordElementTypes
    }

    override fun getBlockElementTypes(): TokenSet {
        return blockElementTypes
    }

    override fun getDirectChildrenElementTypes(elementType: IElementType?): TokenSet {
        return if (keywordTokens.contains(elementType)) {
            TokenSet.orSet(keywordTokens[elementType], blockElementTypes)
        } else {
            TokenSet.EMPTY
        }
    }
}