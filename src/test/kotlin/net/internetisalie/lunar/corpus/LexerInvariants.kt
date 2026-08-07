package net.internetisalie.lunar.corpus

import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes

/**
 * Two properties the lexer must hold for **any** input, valid Lua or not — no reference
 * implementation required, which is what makes them cheap enough to run over a whole corpus.
 *
 * Each was chosen because a defect this repo shipped would have been caught by it:
 *
 * - **Round-trip** — catches text being *lost or duplicated*. It does **not** catch a mis-placed
 *   boundary, because re-partitioning the same characters concatenates identically.
 *
 *   Be precise about what it guards, because three attempts to falsify it failed: `while` → `if` in
 *   either merging adapter, and a dropped `advance()`, all left it green. That is structural —
 *   `MergingLexerAdapterBase` derives a merged token's end from the delegate's *next* start, so
 *   merged tokens are contiguous by construction and no adapter-level defect can lose a character.
 *   The round-trip therefore guards the layer **beneath** the adapters: `_LuaLexer`, where a `.flex`
 *   edit genuinely can drop or overlap text. It cannot be mutation-proved without regenerating the
 *   lexer, and it is not what catches merge bugs — [unmergedTokens] is.
 * - **Unmerged internal tokens** → BUG-392, the boundary defect the round-trip misses. The merging
 *   adapters exist to turn `LONGSTRING_BEGIN`/`LONGSTRING`/`LONGSTRING_END`/`NL_BEFORE_LONGSTRING`
 *   runs into one `STRING`. If any of those internal types survives into the merged stream, the
 *   merge stopped early and the grammar is about to receive tokens it has no rule for.
 * - **Crash-freedom** → BUG-390, whose failure mode was a `StackOverflowError`. `Throwable` is
 *   caught rather than `Exception` for exactly that reason.
 *
 * Lex-only by design. The parse half of MAINT-35-05 lives in `CorpusSweep`, which already holds a
 * parsed `PsiFile`; giving this object a `Project` purely to parse would be the wrong shape.
 */
internal object LexerInvariants {
    /**
     * [crash] is the throwable's class name, or null when the lex completed. [unmergedTokens] counts
     * internal long-string/long-comment token types that escaped the merging adapters.
     */
    data class Result(
        val roundTripFailed: Boolean,
        val unmergedTokens: Int,
        val crash: String?,
    )

    /**
     * Types the merging adapters must always consume. Any of these reaching the parser means a
     * merge terminated early — BUG-392's exact signature.
     *
     * `LuaTokenTypes.LONGCOMMENT` belongs here despite the name: it is the long-comment **body**
     * emitted by the flex lexer, the exact analogue of `LuaTokenTypes.LONGSTRING`. What
     * `LongCommentMergingLexerAdapter` returns is the *different* object
     * `LuaElementTypes.LONGCOMMENT` (`src/main/gen/.../LuaElementTypes.java:100`), which is what the
     * grammar knows. Omitting it left BUG-392's long-comment twin uncovered.
     */
    private val INTERNAL_TOKENS =
        setOf(
            LuaTokenTypes.LONGSTRING_BEGIN,
            LuaTokenTypes.LONGSTRING,
            LuaTokenTypes.LONGSTRING_END,
            LuaTokenTypes.NL_BEFORE_LONGSTRING,
            LuaTokenTypes.LONGCOMMENT_BEGIN,
            LuaTokenTypes.LONGCOMMENT,
            LuaTokenTypes.LONGCOMMENT_END,
        )

    /**
     * Lexes [source] with the **production** [LuaLexer] stack and checks both properties.
     * Never throws: a crash is a result, not an error.
     */
    fun check(source: CharSequence): Result {
        val outcome = runCatching { lex(source) }
        return outcome.fold(
            onSuccess = { it },
            // Only the class name — a message can carry absolute paths, which would churn the
            // baseline across machines.
            onFailure = {
                Result(roundTripFailed = false, unmergedTokens = 0, crash = it::class.java.simpleName)
            },
        )
    }

    /**
     * True when concatenating every token's text reproduces [source] exactly.
     *
     * [LuaLexer] — not `_LuaLexer` — is deliberate: the merging adapters are what the parser,
     * highlighter, TODO indexer and Find Usages all consume, so a merge defect is precisely what
     * this must catch. A raw-flex round-trip would have passed straight through BUG-392.
     */
    private fun lex(source: CharSequence): Result {
        val lexer = LuaLexer()
        lexer.start(source)
        val rebuilt = StringBuilder(source.length)
        var unmerged = 0
        // Read-then-advance: MergingLexerAdapterBase locates the token lazily, so the first token is
        // already available before any advance() and a leading advance() would skip it.
        while (true) {
            val type = lexer.tokenType ?: break
            if (type in INTERNAL_TOKENS) unmerged++
            rebuilt.append(lexer.tokenText)
            lexer.advance()
        }
        return Result(
            roundTripFailed = rebuilt.toString() != source.toString(),
            unmergedTokens = unmerged,
            crash = null,
        )
    }
}
