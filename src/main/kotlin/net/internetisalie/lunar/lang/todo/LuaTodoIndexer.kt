package net.internetisalie.lunar.lang.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import net.internetisalie.lunar.lang.lexer.LuaLexer

/**
 * TODO indexer for Lua files (`<todoIndexer filetype="Lua">`). Feeds the persisted per-file TODO
 * count using a **non-layered** [LuaLexer] via [LuaTodoFilterLexer], so single-line `---` LuaCATS doc
 * comments are counted. Without this, Lua files fall back to the platform's highlighter-driven count
 * path, which uses the *layered* editor highlighter and misses the lazy `LUACATS_COMMENT` token —
 * making `findTodoItems` short-circuit (count 0) before the range searcher ever runs.
 *
 * The actual TODO ranges are produced by [LuaTodoIndexPatternBuilder]; this indexer only supplies the
 * count that gates the search. (EDITOR-03-04)
 */
class LuaTodoIndexer : LexerBasedTodoIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer = LuaTodoFilterLexer(LuaLexer(), consumer)
}
