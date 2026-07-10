package net.internetisalie.lunar.lang.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes

/** The Lua comment token kinds scanned for TODOs (SHEBANG excluded — design §6). Shared with [LuaTodoFilterLexer]. */
internal val LUA_TODO_COMMENT_TOKENS: TokenSet = TokenSet.create(
    LuaElementTypes.SHORTCOMMENT,
    LuaElementTypes.LONGCOMMENT,
    LuaLazyElementTypes.LUACATS_COMMENT,
)

/**
 * Surfaces `TODO` / `FIXME` (and any custom `TodoConfiguration` pattern) inside Lua comments to the
 * platform TODO machinery — the TODO tool window, editor gutter, error stripe, and
 * `PsiTodoSearchHelper` (EDITOR-03).
 *
 * This half drives the on-demand *range* search (`IndexPatternSearcher`): it reports the Lua comment
 * [TokenSet] and per-token marker deltas so only the comment *body* is matched (`--`=2, `---`=3, and a
 * text-aware long-bracket delta so `--[[`=4 / `--[==[`=6). The companion [LuaTodoIndexer] drives the
 * persisted TODO *count* — both are needed, because `findTodoItems` only runs the searcher when the
 * count is non-zero, and the platform's default count path (the layered editor highlighter) misses the
 * lazy `LUACATS_COMMENT` (`---`) token.
 *
 * Stateless — retains no `Project`/`Editor`/`PsiFile`/`VirtualFile`; the `PsiFile` arrives only as a
 * method parameter. See design §2.1, §3.1.
 */
class LuaTodoIndexPatternBuilder : IndexPatternBuilder {

    override fun getIndexingLexer(file: PsiFile): Lexer? =
        if (file is LuaFile) LuaLexer() else null

    override fun getCommentTokenSet(file: PsiFile): TokenSet? =
        if (file is LuaFile) LUA_TODO_COMMENT_TOKENS else null

    override fun getCommentStartDelta(tokenType: IElementType): Int = fixedStartDelta(tokenType)

    override fun getCommentStartDelta(tokenType: IElementType, tokenText: CharSequence): Int =
        longBracketStartDelta(tokenType, tokenText)

    override fun getCommentEndDelta(tokenType: IElementType): Int =
        if (tokenType == LuaElementTypes.LONGCOMMENT) LONG_BRACKET_MIN_TAIL else 0

    /** Fixed marker length: `--` line comment = 2, `---` LuaCATS = 3; the long form is handled below. */
    private fun fixedStartDelta(tokenType: IElementType): Int =
        when (tokenType) {
            LuaElementTypes.SHORTCOMMENT -> SHORT_MARKER
            LuaLazyElementTypes.LUACATS_COMMENT -> LUACATS_MARKER
            else -> 0
        }

    /**
     * Opening-bracket length for a `LONGCOMMENT`, read from the text so leveled brackets
     * (`--[==[`, delta 6) work as well as `--[[` (delta 4). Non-long or malformed text caps at 2.
     */
    private fun longBracketStartDelta(tokenType: IElementType, tokenText: CharSequence): Int {
        if (tokenType != LuaElementTypes.LONGCOMMENT) return fixedStartDelta(tokenType)
        if (!startsWithLongOpener(tokenText)) return SHORT_MARKER
        var level = 0
        while (BRACKET_SCAN_START + level < tokenText.length && tokenText[BRACKET_SCAN_START + level] == '=') {
            level++
        }
        val bracketIndex = BRACKET_SCAN_START + level
        val closed = bracketIndex < tokenText.length && tokenText[bracketIndex] == '['
        return if (closed) SHORT_MARKER + 2 + level else SHORT_MARKER
    }

    private fun startsWithLongOpener(text: CharSequence): Boolean =
        text.length >= BRACKET_SCAN_START && text[0] == '-' && text[1] == '-' && text[2] == '['

    private companion object {
        const val SHORT_MARKER = 2 // "--"
        const val LUACATS_MARKER = 3 // "---"
        const val BRACKET_SCAN_START = 3 // index just past "--["
        const val LONG_BRACKET_MIN_TAIL = 2 // closing "]]" (a longer "]==]" tail never matches a TODO pattern)
    }
}
