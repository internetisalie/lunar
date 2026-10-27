package net.internetisalie.lunar.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lang.parser.GeneratedParserUtilBase.enter_section_
import com.intellij.lang.parser.GeneratedParserUtilBase.exit_section_
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * BUG-517: Lua's suffixed expression (`lparser.c` `suffixedexp`: a name or `( expr )`, then any
 * run of `.name`, `[expr]`, `:name args` and `args`), parsed in ONE pass.
 *
 * It builds exactly the tree the former ordered choice `funcCall | prefixExpr` built:
 * `FUNC_CALL(VAR_OR_EXP, NAME_AND_ARGS+)` when the chain ends in calls (the calling `suffixedExpr`
 * frame collapses its `PREFIX_EXPR` onto that node), else a bare `VAR_OR_EXP` the frame wraps as
 * `PREFIX_EXPR`. `VAR_OR_EXP` holds `VAR(primary VAR_SUFFIX*)` when the primary is a name or the
 * chain has an index, else the bare `( expr )`. Each `VAR_SUFFIX` is a run of calls ending in an
 * index. The `<var or exp>`, `<var>` and `<var suffix>` error frames of the generated rules are
 * kept so that error messages name the same constructs.
 */
internal object LuaSuffixedExprParser {
    /** Sub-rules are called one level down, as from a generated rule, so recursion depth is not inflated. */
    private const val RULE = 1
    private const val VAR_OR_EXP_FRAME = 1
    private const val VAR_FRAME = 2
    private const val SUFFIX_FRAME = 3

    /** Tokens on which a failed `nameAndArgs` gets past its first token, so its frame keeps its variants. */
    private val CALL_STARTS =
        TokenSet.create(LuaElementTypes.COLON, LuaElementTypes.LPAREN, LuaElementTypes.LCURLY, LuaElementTypes.STRING)

    private class OpenFrames(
        val level: Int,
        val varOrExp: PsiBuilder.Marker,
        val variable: PsiBuilder.Marker,
    ) {
        var isVar = false
    }

    private enum class Group { INDEX, END, TRAILING_CALLS }

    private enum class Frame(
        val elementType: IElementType,
        val label: String,
    ) {
        VAR_OR_EXP(LuaElementTypes.VAR_OR_EXP, "<var or exp>"),
        VAR(LuaElementTypes.VAR, "<var>"),
        VAR_SUFFIX(LuaElementTypes.VAR_SUFFIX, "<var suffix>"),
    }

    fun parse(
        builder: PsiBuilder,
        level: Int,
    ): Boolean {
        val outer = enter_section_(builder)
        val varOrExp = enterFrame(builder, level + VAR_OR_EXP_FRAME, Frame.VAR_OR_EXP)
        val variable = enterFrame(builder, level + VAR_FRAME, Frame.VAR)
        val frames = OpenFrames(level, varOrExp, variable)
        frames.isVar = LuaParser.nameRef(builder, level + RULE)
        if (!frames.isVar && !parseParenPrimary(builder, level + RULE)) {
            exit_section_(builder, level + VAR_FRAME, variable, false, false, null)
            exit_section_(builder, level + VAR_OR_EXP_FRAME, varOrExp, false, false, null)
            exit_section_(builder, outer, null, false)
            return false
        }
        val trailingCalls = parseSuffixChain(builder, frames)
        exitVarOrExpFrames(builder, frames, trailingCalls)
        if (!trailingCalls && !GeneratedParserUtilBase.nextTokenIsFast(builder, CALL_STARTS)) {
            GeneratedParserUtilBase.addVariant(builder, "<name and args>")
        }
        exit_section_(builder, outer, if (trailingCalls) LuaElementTypes.FUNC_CALL else null, true)
        return true
    }

    /** A Grammar-Kit error frame, as the generated rule of the same element type opens it. */
    private fun enterFrame(
        builder: PsiBuilder,
        level: Int,
        frame: Frame,
    ): PsiBuilder.Marker =
        enter_section_(
            builder,
            level,
            GeneratedParserUtilBase._NONE_,
            frame.elementType,
            frame.label,
        )

    private fun parseParenPrimary(
        builder: PsiBuilder,
        level: Int,
    ): Boolean =
        GeneratedParserUtilBase.consumeToken(builder, LuaElementTypes.LPAREN) &&
            LuaParser.expr(builder, level, -1) &&
            GeneratedParserUtilBase.consumeToken(builder, LuaElementTypes.RPAREN)

    /** Parses `{ nameAndArgs* indexExpr }* nameAndArgs*`; true when it ends in calls. */
    private fun parseSuffixChain(
        builder: PsiBuilder,
        frames: OpenFrames,
    ): Boolean {
        while (true) {
            when (parseSuffixGroup(builder, frames)) {
                Group.INDEX -> frames.isVar = true
                Group.END -> return false
                Group.TRAILING_CALLS -> return true
            }
        }
    }

    /** One `<var suffix>` frame: calls then an index is a VAR_SUFFIX; calls alone end the chain. */
    private fun parseSuffixGroup(
        builder: PsiBuilder,
        frames: OpenFrames,
    ): Group {
        val level = frames.level + SUFFIX_FRAME
        val group = enterFrame(builder, level, Frame.VAR_SUFFIX)
        var calls = 0
        while (LuaParser.nameAndArgs(builder, frames.level + RULE)) calls++
        if (LuaParser.indexExpr(builder, frames.level + RULE)) {
            exit_section_(builder, level, group, true, false, null)
            return Group.INDEX
        }
        if (calls == 0) {
            exit_section_(builder, level, group, false, false, null)
            return Group.END
        }
        closeVarOrExpBefore(frames, group)
        group.drop()
        exit_section_(builder, level, null, false, false, null)
        return Group.TRAILING_CALLS
    }

    /** Pops the VAR and VAR_OR_EXP frames; with trailing calls their nodes are already closed. */
    private fun exitVarOrExpFrames(
        builder: PsiBuilder,
        frames: OpenFrames,
        trailingCalls: Boolean,
    ) {
        if (!frames.isVar && !trailingCalls) frames.variable.drop()
        val variable = if (frames.isVar && !trailingCalls) frames.variable else null
        exit_section_(builder, frames.level + VAR_FRAME, variable, frames.isVar, false, null)
        val varOrExp = if (trailingCalls) null else frames.varOrExp
        exit_section_(builder, frames.level + VAR_OR_EXP_FRAME, varOrExp, true, false, null)
    }

    /** Trailing calls belong to FUNC_CALL: VAR and VAR_OR_EXP end before them. */
    private fun closeVarOrExpBefore(
        frames: OpenFrames,
        trailingCalls: PsiBuilder.Marker,
    ) {
        if (frames.isVar) {
            frames.variable.doneBefore(LuaElementTypes.VAR, trailingCalls)
        } else {
            frames.variable.drop()
        }
        frames.varOrExp.doneBefore(LuaElementTypes.VAR_OR_EXP, trailingCalls)
    }
}
