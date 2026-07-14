package net.internetisalie.lunar.analysis.redis

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 Phase 5 — real-flow inspection coverage for [LuaRedisCommandInspection]
 * (AC-4, AC-9): TC-ARITY-1, TC-ARITY-2, TC-UNK-1, TC-UNK-2, TC-DET-1..4.
 *
 * Drives the full inspection machinery — [com.intellij.testFramework.fixtures.CodeInsightTestFixture.enableInspections]
 * + `configureByText` + `doHighlighting` — so registration, severity, element-pinning,
 * and quick-fix offering are all exercised against the bundled command specs.
 *
 * Target switching mirrors [RedisAmbientTypingTest]: `LuaProjectSettings.setTargetAndNotify`
 * is called on the EDT; tearDown restores the standard target so leaked Redis state cannot
 * pollute alphabetically-later test suites that assume STANDARD.
 */
@RunWith(JUnit4::class)
class LuaRedisCommandInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaRedisCommandInspection())
    }

    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    // -------------------------------------------------------------------------
    // TC-ARITY-1: redis.call("GET") — GET needs 1 key arg, found 0.
    // -------------------------------------------------------------------------
    @Test
    fun testArityTooFewFlagsWarning() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", """redis.call("GET")""")
        val arityWarnings = arityWarnings()
        assertTrue(
            "Expected arity warning for GET with no args, got: $arityWarnings",
            arityWarnings.any { it.contains("'GET'") && it.contains("at least 1") && it.contains("found 0") },
        )
    }

    // -------------------------------------------------------------------------
    // TC-ARITY-2: redis.call("GET", KEYS[1]) — satisfies minimum arity, no warning.
    // -------------------------------------------------------------------------
    @Test
    fun testArityOkSilent() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", """redis.call("GET", KEYS[1])""")
        assertTrue("No arity or unknown warning expected when arity satisfied", arityWarnings().isEmpty())
        assertTrue("No unknown warning expected", unknownCommandWarnings().isEmpty())
    }

    // -------------------------------------------------------------------------
    // TC-UNK-1: redis.call("Gte") — unknown command, did-you-mean fix for GET offered.
    // -------------------------------------------------------------------------
    @Test
    fun testUnknownCommandFlagsWarningWithDidYouMean() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", """redis.call("G<caret>te")""")
        myFixture.doHighlighting(HighlightSeverity.WARNING)
        val unknownWarnings = unknownCommandWarnings()
        assertTrue(
            "Expected unknown command warning for 'GTE', got: $unknownWarnings",
            unknownWarnings.any { it.contains("'GTE'") },
        )
        val fixes = myFixture.getAllQuickFixes("Change to 'GET'")
        assertTrue(
            "Expected a 'Change to GET' quick fix for the typo 'GTE' (Levenshtein ≤ 2)",
            fixes.isNotEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-UNK-2: redis.pcall(commandName) — dynamic first arg, never flagged.
    // -------------------------------------------------------------------------
    @Test
    fun testDynamicCommandNameNotFlagged() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local cmd = \"GET\"\nredis.pcall(cmd)")
        assertTrue("Dynamic command names must never be flagged", unknownCommandWarnings().isEmpty())
        assertTrue("Dynamic command names must never be arity-flagged", arityWarnings().isEmpty())
    }

    // -------------------------------------------------------------------------
    // TC-DET-1: TIME before SET under Redis 6 → determinism warning.
    // -------------------------------------------------------------------------
    @Test
    fun testDeterminismWarningWhenNondetBeforeWrite() {
        setRedisTarget("6")
        myFixture.configureByText(
            "test.lua",
            """local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])""",
        )
        val detWarnings = determinismWarnings()
        assertTrue(
            "Expected determinism warning for TIME before SET under Redis 6, got: $detWarnings",
            detWarnings.any { it.contains("'TIME'") && it.contains("replicate_commands") },
        )
    }

    // -------------------------------------------------------------------------
    // TC-DET-2: replicate_commands() guard before TIME → no determinism warning.
    // -------------------------------------------------------------------------
    @Test
    fun testDeterminismSilentWhenGuardPrecedes() {
        setRedisTarget("6")
        myFixture.configureByText(
            "test.lua",
            """redis.replicate_commands(); local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])""",
        )
        assertTrue(
            "No determinism warning expected when replicate_commands() precedes TIME",
            determinismWarnings().isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-DET-3: TIME before SET under Redis 7+ → no determinism warning (version-gated).
    // -------------------------------------------------------------------------
    @Test
    fun testDeterminismSilentUnderRedis7Plus() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "test.lua",
            """local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])""",
        )
        assertTrue(
            "No determinism warning expected under Redis 7+ (effects replication)",
            determinismWarnings().isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-DET-4: TIME only (no write follows) → no determinism warning.
    // -------------------------------------------------------------------------
    @Test
    fun testDeterminismSilentWhenNoWriteFollows() {
        setRedisTarget("6")
        myFixture.configureByText("test.lua", """local t = redis.call("TIME"); return t""")
        assertTrue(
            "No determinism warning expected when no write follows the nondeterministic call",
            determinismWarnings().isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun setRedisTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, label)) {
            "No Redis version '$label' in registry"
        }
        LuaProjectSettings.getInstance(project).state.setTarget(Target(LuaPlatform.REDIS, version))
    }

    private fun setStandardTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label)) {
            "No STANDARD version '$label' in registry"
        }
        LuaProjectSettings.getInstance(project).state.setTarget(Target(LuaPlatform.STANDARD, version))
    }

    private fun allWarningDescriptions(): List<String> =
        myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }

    private fun arityWarnings(): List<String> =
        allWarningDescriptions().filter { it.contains("expects at least") }

    private fun unknownCommandWarnings(): List<String> =
        allWarningDescriptions().filter { it.contains("Unknown Redis command") }

    private fun determinismWarnings(): List<String> =
        allWarningDescriptions().filter { it.contains("Nondeterministic command") }
}
