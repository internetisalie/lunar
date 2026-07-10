package net.internetisalie.lunar.lang.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer

/**
 * Filter lexer for [LuaTodoIndexer]: counts TODO/FIXME occurrences inside every Lua comment kind —
 * crucially including the lazy `LUACATS_COMMENT` (`---`) token, which the wrapped non-layered
 * [net.internetisalie.lunar.lang.lexer.LuaLexer] reports as a single comment token.
 *
 * This feeds the persisted TODO count so `findTodoItems`' count gate is satisfied for `---` doc
 * comments; the platform's default count path iterates the *layered* editor highlighter, which
 * re-lexes `---` into inner LuaCats tokens and never counts the outer comment. Range matching is done
 * separately by [LuaTodoIndexPatternBuilder]. (EDITOR-03-04)
 */
class LuaTodoFilterLexer(delegate: Lexer, consumer: OccurrenceConsumer) :
    BaseFilterLexer(delegate, consumer) {

    override fun advance() {
        val tokenType = delegate.tokenType
        if (tokenType != null && LUA_TODO_COMMENT_TOKENS.contains(tokenType)) {
            advanceTodoItemCountsInToken()
        }
        delegate.advance()
    }
}
