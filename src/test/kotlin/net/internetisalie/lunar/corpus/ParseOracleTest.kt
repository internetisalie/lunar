package net.internetisalie.lunar.corpus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File

/**
 * MAINT-35-01/-03. The class name deliberately contains **no** `Corpus`: `build.gradle.kts:266`
 * excludes `*Corpus*` unless `-PwithCorpus` is passed, and these are cheap unit tests that should
 * run in the routine suite.
 *
 * They need the pinned binaries from `tooling/corpus/fetch-luac.py`, which live in the out-of-repo
 * `test/` tree — the same prerequisite `LuaRecursiveReferenceTest` and `LuaDescriptionIndexTest`
 * already have, and which CI skips via `-PexcludeExternalFixtureTests`.
 */
class ParseOracleTest : BasePlatformTestCase() {

    private val repoRoot = File(System.getProperty("user.dir"))

    private fun judge(source: String, level: LuaLanguageLevel) =
        ParseOracle.judge(repoRoot, source, level)

    /** TC-1 — valid Lua is accepted. */
    fun testValidLuaIsAccepted() {
        assertEquals(ParseOracle.Verdict.Accept, judge("local x = 1\n", LuaLanguageLevel.LUA51))
    }

    /** TC-2 — malformed Lua is rejected, and the message is carried for diagnosis. */
    fun testMalformedLuaIsRejected() {
        val verdict = judge("local = = =\n", LuaLanguageLevel.LUA51)
        assertTrue("expected a Reject, got $verdict", verdict is ParseOracle.Verdict.Reject)
        assertTrue(
            "the reject message should be diagnostic, was empty",
            (verdict as ParseOracle.Verdict.Reject).message.isNotBlank(),
        )
    }

    /**
     * TC-3 + TC-4 — the same input, opposite verdicts, by level.
     *
     * This is the test that proves version matching is *real* rather than incidental: integer
     * division arrived in Lua 5.3, so a 5.1 oracle must reject what a 5.4 oracle accepts. If both
     * assertions ever pass with one binary, the pinning has silently collapsed.
     */
    fun testIntegerDivisionDiscriminatesByLevel() {
        val source = "local a = 1 // 2\n"
        assertTrue(
            "luac5.1 must REJECT integer division",
            judge(source, LuaLanguageLevel.LUA51) is ParseOracle.Verdict.Reject,
        )
        assertEquals(
            "luac5.4 must ACCEPT integer division",
            ParseOracle.Verdict.Accept,
            judge(source, LuaLanguageLevel.LUA54),
        )
    }

    /** BUG-392's fixture: a long string opening on a blank line is valid Lua. */
    fun testBug392FixtureIsValidLua() {
        assertEquals(
            ParseOracle.Verdict.Accept,
            judge("local s = [[\n\n  body\n]]\n", LuaLanguageLevel.LUA51),
        )
    }

    /**
     * TC-8 — an unpinned level fails fast, naming the remedy.
     *
     * `LUA50` is deliberately absent from `luac.json`: PUC 5.0 is not a supported target. The
     * failure must arrive *before* any file is judged, so a sweep can never quietly measure nothing.
     */
    fun testUnpinnedLevelFailsFastWithTheRemedy() {
        val failure = runCatching { ParseOracle.requireBinary(repoRoot, LuaLanguageLevel.LUA50) }
            .exceptionOrNull()
        assertNotNull("LUA50 has no pinned luac and must throw", failure)
        assertTrue(
            "the error must name the level; was: ${failure?.message}",
            failure?.message.orEmpty().contains("LUA50"),
        )
    }

    /** The manifest is the single source of truth for which binary answers for a level. */
    fun testPinnedVersionsComeFromTheManifest() {
        assertEquals("5.1.5", ParseOracle.pinnedVersion(repoRoot, LuaLanguageLevel.LUA51))
        assertEquals("5.4.8", ParseOracle.pinnedVersion(repoRoot, LuaLanguageLevel.LUA54))
        assertEquals("5.5.1", ParseOracle.pinnedVersion(repoRoot, LuaLanguageLevel.LUA55))
        assertNull(ParseOracle.pinnedVersion(repoRoot, LuaLanguageLevel.LUA50))
    }

    /** R5a — luac echoes invalid bytes back on stderr; decoding must not throw. */
    fun testInvalidUtf8InputDoesNotBreakTheOracle() {
        val verdict = judge("local ÿþ = 1\n", LuaLanguageLevel.LUA51)
        assertTrue("expected a verdict, not an exception; got $verdict", verdict is ParseOracle.Verdict)
    }
}
