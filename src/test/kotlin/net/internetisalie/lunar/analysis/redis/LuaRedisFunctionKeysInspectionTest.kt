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
 * REDIS-05 Phase 2 — KEYS/ARGV-in-library inspection (AC-3):
 * TC-KEYS-1, TC-KEYS-2, TC-KEYS-3.
 *
 * Drives the full inspection machinery — [com.intellij.testFramework.fixtures.CodeInsightTestFixture.enableInspections]
 * + `configureByText` + `doHighlighting` — against the real [LuaRedisFunctionKeysInspection].
 *
 * Target switching mirrors [LuaRedisSandboxInspectionTest]; tearDown restores STANDARD 5.4
 * to prevent state leaks into alphabetically-later suites (mandatory).
 */
@RunWith(JUnit4::class)
class LuaRedisFunctionKeysInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaRedisFunctionKeysInspection())
    }

    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-1: Redis 7+ target + library file → KEYS and ARGV both warned.
    // -------------------------------------------------------------------------

    @Test
    fun testKeysAndArgvFlaggedInLibraryFile() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "lib.lua",
            "#!lua name=lib\nredis.register_function('f', function(keys, args) local a = KEYS[1]; local b = ARGV[1] end)",
        )
        val warnings = keysInspectionWarnings()
        assertTrue(
            "Expected warning for KEYS in library file, got: $warnings",
            warnings.any { it.contains("'KEYS'") },
        )
        assertTrue(
            "Expected warning for ARGV in library file, got: $warnings",
            warnings.any { it.contains("'ARGV'") },
        )
    }

    @Test
    fun testExactlyTwoWarningsForKeysAndArgv() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "lib.lua",
            "#!lua name=lib\nredis.register_function('f', function(keys, args) local a = KEYS[1]; local b = ARGV[1] end)",
        )
        val warnings = keysInspectionWarnings()
        assertEquals(
            "Expected exactly one warning for KEYS and one for ARGV, got: $warnings",
            2,
            warnings.size,
        )
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-2: Redis 7+ target + NON-library file → no warning.
    // -------------------------------------------------------------------------

    @Test
    fun testNoWarningInNonLibraryFile() {
        setRedisTarget("7+")
        myFixture.configureByText("eval.lua", "local x = KEYS[1]")
        val warnings = keysInspectionWarnings()
        assertTrue(
            "KEYS in a non-library (no shebang) file must not be flagged, got: $warnings",
            warnings.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-3: STANDARD 5.4 target + library shebang → no warning (target guard).
    // -------------------------------------------------------------------------

    @Test
    fun testNoWarningUnderStandardTarget() {
        setStandardTarget("5.4")
        myFixture.configureByText(
            "lib.lua",
            "#!lua name=lib\nlocal x = KEYS[1]",
        )
        val warnings = keysInspectionWarnings()
        assertTrue(
            "KEYS/ARGV inspection must be a no-op under STANDARD 5.4 target, got: $warnings",
            warnings.isEmpty(),
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

    /**
     * Returns description strings for all WARNING-level highlights produced by
     * [LuaRedisFunctionKeysInspection] (filtered by the diagnostic message substring).
     */
    private fun keysInspectionWarnings(): List<String> =
        myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("not available in a Redis Function library") }
}
