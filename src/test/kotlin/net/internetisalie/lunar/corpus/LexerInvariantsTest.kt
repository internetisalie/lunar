package net.internetisalie.lunar.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAINT-35-04/-05. Plain JUnit — no fixture, no `Project`, and deliberately **no** `Corpus` in the
 * class name, so these run in the routine suite and in CI.
 */
class LexerInvariantsTest {

    private fun assertRoundTrips(label: String, source: String) {
        val result = LexerInvariants.check(source)
        assertNull("$label: lexer threw ${result.crash}", result.crash)
        assertTrue("$label: tokens do not reconstitute the source", !result.roundTripFailed)
        assertEquals("$label: internal tokens escaped the merge", 0, result.unmergedTokens)
    }

    /** TC-6 — the ordinary shapes, including the ones with their own lexer states. */
    @Test
    fun ordinaryLuaRoundTrips() {
        assertRoundTrips("simple", "local x = 1\n")
        assertRoundTrips("long comment", "--[==[ a comment ]==]\nlocal y = 2\n")
        assertRoundTrips("escapes", "local y = \"a\\z\\n b\"\n")
        assertRoundTrips("long string", "local s = [[body]]\n")
        assertRoundTrips("mixed", "-- short\nlocal t = { 1, 2 } --[[ inline ]] print(t)\n")
    }

    /**
     * TC-5's lexer half — BUG-392's exact fixture.
     *
     * **The round-trip alone does NOT catch this**, which was established by mutation rather than
     * assumed: reintroducing the defect (`while` → `if` in `LongStringMergingLexerAdapter`) left
     * every round-trip assertion green, because re-partitioning the same characters concatenates
     * identically. `unmergedTokens` is the assertion that actually fails — the leaked
     * `LONGSTRING`/`LONGSTRING_END` tokens are internal types the grammar has no rule for.
     *
     * No adapter-level mutation falsifies the round-trip; see `LexerInvariants` for why that is
     * structural rather than a gap in these fixtures.
     */
    @Test
    fun bug392LongStringOnBlankLineIsFullyMerged() {
        assertRoundTrips("BUG-392", "local s = [[\n\n  body\n]]\n")
        assertRoundTrips("BUG-392 many blanks", "local s = [[\n\n\n\n  body\n]]\n")
        assertRoundTrips("BUG-392 levelled", "local s = [==[\n\n  body\n]==]\n")
        assertRoundTrips("long comment on blank line", "--[[\n\n  body\n]]\nlocal a = 1\n")
    }

    /**
     * BUG-392's long-comment twin. `LongCommentMergingLexerAdapter` consumes a *run* of
     * `LuaTokenTypes.LONGCOMMENT` body tokens; truncating that loop to a single `if` leaks the rest
     * into the merged stream.
     *
     * Mutation-proved, not assumed: `while` → `if` at `LuaLexer.kt:160` leaves every round-trip
     * assertion green (the same characters re-partitioned concatenate identically) and fails only
     * on `unmergedTokens`. That is also why `LuaTokenTypes.LONGCOMMENT` had to be added to
     * `INTERNAL_TOKENS` — without it this mutation was invisible.
     */
    @Test
    fun multiLineLongCommentBodyIsFullyMerged() {
        assertRoundTrips("multi-line body", "--[[\n  line one\n  line two\n  line three\n]]\nlocal a = 1\n")
        assertRoundTrips("levelled multi-line body", "--[==[\n  one\n  two\n]==]\nlocal a = 1\n")
        assertRoundTrips("long comment holding brackets", "--[[\n  not ]] the end? yes it is\n")
    }

    /**
     * The case that makes `LuaTokenTypes.LONGCOMMENT` in `INTERNAL_TOKENS` load-bearing rather than
     * decorative.
     *
     * With a *closing* bracket present, truncating the body run also strands `LONGCOMMENT_END`,
     * which was already listed — so the omission was invisible. Unterminated, there is no
     * `LONGCOMMENT_END` to strand and the leaked body token is the only evidence. Measured under the
     * `while` → `if` mutation: **1 unmerged with the body type listed, 0 without it** — this test
     * red, then green, from that one line of `INTERNAL_TOKENS`.
     */
    @Test
    fun unterminatedLongCommentBodyIsFullyMerged() {
        assertRoundTrips("unterminated long comment", "--[[\n  one\n  two\n  three\n")
    }

    /** Whitespace and line endings are tokens too — dropping them would break the round-trip. */
    @Test
    fun whitespaceAndLineEndingsSurvive() {
        assertRoundTrips("crlf", "local a = 1\r\nlocal b = 2\r\n")
        assertRoundTrips("trailing ws", "local a = 1   \n\n\n")
        assertRoundTrips("tabs", "local\ta\t=\t1\n")
        assertRoundTrips("no trailing newline", "local a = 1")
    }

    /** Input that is not valid Lua must still round-trip — the lexer has no opinion on grammar. */
    @Test
    fun invalidLuaStillRoundTrips() {
        assertRoundTrips("garbage", "local = = =\n")
        assertRoundTrips("unterminated string", "local s = \"no end\n")
        assertRoundTrips("unterminated long", "local s = [[no end\n")
        assertRoundTrips("stray bytes", "local ÿþ = 1\n")
        assertRoundTrips("empty", "")
    }

    /**
     * TC-7's **lexer half** only.
     *
     * Named for what it does: this lexes 5 000 nested parens and never parses them, so it cannot
     * reach BUG-390's `StackOverflowError`, which happens in the type engine well past the lexer.
     * The claim that it covers BUG-390 was wrong. The parse half of crash-freedom is
     * `CorpusSweep.tallyGuarded` / `LuaTortureCorpusTest.judge`, which wrap a real parse in
     * `runCatching` and gate the result as `crash.parse:<Class>`; the torture member exercises it
     * over 1 696 hostile inputs every corpus run.
     */
    @Test
    fun deeplyNestedInputDoesNotCrashTheLexer() {
        val deep = "local x = " + "(".repeat(5_000) + "1" + ")".repeat(5_000) + "\n"
        val result = LexerInvariants.check(deep)
        assertNull("deep nesting crashed the lexer: ${result.crash}", result.crash)
        assertTrue("deep nesting broke the round-trip", !result.roundTripFailed)
        assertEquals(0, result.unmergedTokens)
    }

    /** A crash is reported as a result, never propagated — the sweep must survive one bad file. */
    @Test
    fun resultShapeIsWellFormedForOrdinaryInput() {
        assertEquals(
            LexerInvariants.Result(roundTripFailed = false, unmergedTokens = 0, crash = null),
            LexerInvariants.check("local x = 1\n"),
        )
    }
}
