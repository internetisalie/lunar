package net.internetisalie.lunar.corpus

import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
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

    private fun judge(
        source: String,
        level: LuaLanguageLevel,
    ) = ParseOracle.judge(repoRoot, source, level)

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
        val failure =
            runCatching { ParseOracle.requireBinary(repoRoot, LuaLanguageLevel.LUA50) }
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

    /**
     * TC-8's other branch — the level *is* pinned but the binary was never built.
     *
     * Distinct from [testUnpinnedLevelFailsFastWithTheRemedy], which exercises the no-manifest-entry
     * message and leaves this one untested. This is the branch a fresh checkout actually hits, so it
     * is the one that has to name `fetch-luac.py`.
     */
    fun testMissingBinaryNamesTheFetchScript() {
        val emptyRoot = FileUtil.createTempDirectory("lunar-luac-missing", null)
        File(emptyRoot, "tooling/corpus").mkdirs()
        File(repoRoot, MANIFEST_PATH).copyTo(File(emptyRoot, MANIFEST_PATH))

        val failure =
            runCatching { ParseOracle.requireBinary(emptyRoot, LuaLanguageLevel.LUA51) }
                .exceptionOrNull()
        assertNotNull("an unbuilt oracle must throw, not judge nothing", failure)
        val message = failure?.message.orEmpty()
        assertTrue("the error must name the remedy; was: $message", message.contains("fetch-luac.py"))
        assertTrue("the error must name the path it looked at; was: $message", message.contains("test/luac/5.1.5"))
    }

    /**
     * TC-9 — a checksum mismatch installs nothing.
     *
     * Driven through the real script with a `file://` entry, so it needs neither the network nor the
     * real pins. Asserting on the **absence of the stamp** is the point: the stamp is what makes a
     * later run skip the build, so a mismatch that stamped anyway would bless an unverified binary
     * permanently.
     */
    fun testChecksumMismatchInstallsNothing() {
        val work = FileUtil.createTempDirectory("lunar-luac-tc9", null)
        val tarball = File(work, "decoy.tar.gz").apply { writeText("not a lua tarball") }
        val manifest = File(work, "luac.json")
        val wrongDigest = "0".repeat(64)
        manifest.writeText(
            """{"builds":[{"level":"LUA51","version":"5.1.5",""" +
                """"url":"${tarball.toURI()}","sha256":"$wrongDigest"}]}""",
        )
        val luacRoot = File(work, "out")

        val exit = runFetchScript(manifest, luacRoot)
        assertTrue("a sha256 mismatch must fail the script; exit was $exit", exit != 0)
        assertFalse("no binary may be installed", File(luacRoot, "5.1.5/luac").exists())
        assertFalse(
            "no stamp may be written — it would make the next run skip the build",
            File(luacRoot, "5.1.5/.luac-sha").exists(),
        )
    }

    /**
     * MAINT-35-03's real content: the oracle must be shown to *discriminate*, not merely to exist.
     * An always-accepting binary passes `requireBinary` and makes the whole gate vacuous.
     */
    fun testDiscriminationCheckPassesForAPinnedBinary() {
        ParseOracle.assertDiscriminates(repoRoot, LuaLanguageLevel.LUA51)
        ParseOracle.assertDiscriminates(repoRoot, LuaLanguageLevel.LUA54)
    }

    /**
     * The **failure** path of that check, which is the half that matters and which nothing exercised
     * — the human checklist called it "the scenario that matters most" and then signed it off
     * against a success-path test.
     *
     * A stub that exits 0 for every input is the exact shape a vacuous gate takes: every metric
     * reads 0, the ratchet stays green, and nothing is being measured.
     */
    fun testAnAlwaysAcceptingOracleIsRefused() {
        val fakeRoot = stagedOracle("#!/bin/sh\nexit 0\n")
        val failure =
            runCatching { ParseOracle.assertDiscriminates(fakeRoot, LuaLanguageLevel.LUA51) }
                .exceptionOrNull()
        assertNotNull("an always-accepting oracle must be refused", failure)
        assertTrue(
            "the message must name the vacuity, was: ${failure?.message}",
            failure?.message.orEmpty().contains("accepted malformed Lua"),
        )
    }

    /**
     * Existence is not identity. Before this check `requireBinary` trusted whatever sat at the
     * resolved path — a stale build from an earlier pin, or a hand-copied distro binary, both of
     * which judge differently while the gate looks uniform.
     */
    fun testABinaryWithTheWrongStampIsRefused() {
        val fakeRoot = stagedOracle("#!/bin/sh\nexit 0\n", stamp = "not-the-pinned-digest")
        val failure =
            runCatching { ParseOracle.requireBinary(fakeRoot, LuaLanguageLevel.LUA51) }
                .exceptionOrNull()
        assertNotNull("an unstamped binary must be refused", failure)
        assertTrue(
            "the message must send the reader to the fetch script, was: ${failure?.message}",
            failure?.message.orEmpty().contains("fetch-luac.py"),
        )
    }

    /** A throwaway repo root holding the real manifest and a stand-in `luac` for LUA51. */
    private fun stagedOracle(
        script: String,
        stamp: String? = null,
    ): File {
        val root = FileUtil.createTempDirectory("lunar-luac-stub", null)
        val manifest = File(root, MANIFEST_PATH)
        manifest.parentFile.mkdirs()
        File(repoRoot, MANIFEST_PATH).copyTo(manifest)
        val pinned =
            JsonParser
                .parseString(manifest.readText())
                .asJsonObject
                .getAsJsonArray("builds")
                .map { it.asJsonObject }
                .first { it.get("level").asString == "LUA51" }
        val dir = File(root, "test/luac/${pinned.get("version").asString}").apply { mkdirs() }
        File(dir, "luac").apply {
            writeText(script)
            setExecutable(true)
        }
        File(dir, ".luac-sha").writeText((stamp ?: pinned.get("sha256").asString) + "\n")
        return root
    }

    /** R5a — luac echoes invalid bytes back on stderr; decoding must not throw. */
    fun testInvalidUtf8InputDoesNotBreakTheOracle() {
        val verdict = judge("local ÿþ = 1\n", LuaLanguageLevel.LUA51)
        assertFalse("a decodable verdict was expected, got $verdict", verdict is ParseOracle.Verdict.NotJudged)
    }

    /**
     * A source far larger than a pipe buffer, rejected on its **first line**. luac exits without
     * draining stdin, so writing the rest raises EPIPE — measured against the shipped 5.1.5 binary:
     * fine at 50 kB, broken pipe at 100 kB. The early exit *is* the verdict, so the failed write
     * must be swallowed rather than propagated.
     */
    fun testEarlyRejectionOfALargeSourceStillYieldsAVerdict() {
        val large = "local = = =\n" + FILLER.repeat(50_000)
        assertTrue("expected a Reject", judge(large, LuaLanguageLevel.LUA51) is ParseOracle.Verdict.Reject)
    }

    /**
     * The same size, accepted — the other half of the pair, proving the whole 1.1 MB really reached
     * luac rather than the write being lost.
     *
     * Comment filler, deliberately: `local a = 1` repeated hits PUC's *200 locals per function*
     * limit at line 201 and is rejected for a reason that has nothing to do with size (established
     * by running it, after it failed this test).
     */
    fun testLargeValidSourceIsAccepted() {
        val large = FILLER.repeat(50_000) + "local a = 1\n"
        assertEquals(ParseOracle.Verdict.Accept, judge(large, LuaLanguageLevel.LUA51))
    }

    private fun runFetchScript(
        manifest: File,
        luacRoot: File,
    ): Int {
        val script = File(repoRoot, "tooling/corpus/fetch-luac.py")
        val builder = ProcessBuilder("python3", script.path).redirectErrorStream(true)
        builder.environment()["LUNAR_LUAC_MANIFEST"] = manifest.path
        builder.environment()["LUNAR_LUAC_ROOT"] = luacRoot.path
        val process = builder.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val exit = process.waitFor()
        println("[fetch-luac] $output")
        return exit
    }

    private companion object {
        const val MANIFEST_PATH = "tooling/corpus/luac.json"

        /** 23 bytes; ×50 000 is ~1.1 MB, two orders of magnitude past a 64 KB pipe buffer. */
        const val FILLER = "-- filler comment line\n"
    }
}
