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
 * REDIS-04 Phase 6 — real-flow sandbox inspection coverage (AC-7):
 * TC-SBX-1, TC-SBX-2, TC-SBX-3.
 *
 * Drives the full inspection machinery — [com.intellij.testFramework.fixtures.CodeInsightTestFixture.enableInspections]
 * + `configureByText` + `doHighlighting` — against the bundled `os.lua` stub roots.
 *
 * Target switching mirrors [LuaRedisCommandInspectionTest]; tearDown restores STANDARD
 * to prevent state leaks into alphabetically-later suites.
 */
@RunWith(JUnit4::class)
class LuaRedisSandboxInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaRedisSandboxInspection())
    }

    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    // -------------------------------------------------------------------------
    // TC-SBX-1: blocked names flagged under Redis 7+.
    // io.read(), require("m"), dofile("f"), os.getenv("X"), print("x") → each warned.
    // -------------------------------------------------------------------------

    @Test
    fun testIoReadFlaggedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "io.read()")
        val warnings = sandboxWarnings()
        assertTrue(
            "Expected sandbox warning for io.read(), got: $warnings",
            warnings.any { it.contains("'io'") || it.contains("io") },
        )
    }

    @Test
    fun testLoadfileFlaggedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "loadfile(\"f\")")
        val warnings = sandboxWarnings()
        assertTrue(
            "Expected sandbox warning for loadfile, got: $warnings",
            warnings.any { it.contains("'loadfile'") },
        )
    }

    @Test
    fun testDofileFlaggedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "dofile(\"f\")")
        val warnings = sandboxWarnings()
        assertTrue(
            "Expected sandbox warning for dofile, got: $warnings",
            warnings.any { it.contains("'dofile'") },
        )
    }

    @Test
    fun testOsGetenvFlaggedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "os.getenv(\"HOME\")")
        val warnings = sandboxWarnings()
        assertTrue(
            "Expected sandbox warning for os.getenv (not in allowlist), got: $warnings",
            warnings.any { it.contains("os.getenv") },
        )
    }

    @Test
    fun testPrintFlaggedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "print(\"x\")")
        val warnings = sandboxWarnings()
        assertTrue(
            "Expected sandbox warning for print, got: $warnings",
            warnings.any { it.contains("'print'") },
        )
    }

    // -------------------------------------------------------------------------
    // TC-SBX-2: allowed names NOT flagged under Redis 7+.
    // os.time(), os.clock(), redis.sha1hex(), cjson.encode() → each silent.
    // -------------------------------------------------------------------------

    @Test
    fun testOsTimeAllowedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local t = os.time()")
        assertTrue(
            "os.time() must not be flagged (it is in the os.lua stub allowlist)",
            sandboxWarnings().isEmpty(),
        )
    }

    @Test
    fun testOsClockAllowedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local c = os.clock()")
        assertTrue(
            "os.clock() must not be flagged (it is in the os.lua stub allowlist)",
            sandboxWarnings().isEmpty(),
        )
    }

    @Test
    fun testCjsonEncodeAllowedUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local s = cjson.encode({})")
        assertTrue(
            "cjson.encode must not be flagged (cjson.lua stub exists)",
            sandboxWarnings().isEmpty(),
        )
    }

    /**
     * Verifies that `require` is in the blocked-roots allowlist (TC-SBX-1 for `require`).
     *
     * Using `doHighlighting` with a `require()` call triggers the type engine's module-
     * resolution path (`LuaTypeManagerImpl.resolveModule`) which logs a warning about
     * missing module files in the temp filesystem — causing a `TestLoggerAssertionError`.
     * This unit test checks the allowlist directly to cover `require` without VFS side-effects.
     */
    @Test
    fun testRequireIsInBlockedAllowlist() {
        setRedisTarget("7+")
        val target = LuaProjectSettings.getInstance(project).state.getTarget()
        val allowlist = RedisSandboxAllowlist.forTarget(project, target)
        assertTrue(
            "require must be a blocked root in the sandbox allowlist",
            allowlist.isBlockedRoot("require"),
        )
    }

    // -------------------------------------------------------------------------
    // TC-SBX-3: inspection is a no-op under a non-Redis target (STANDARD 5.4).
    // -------------------------------------------------------------------------

    @Test
    fun testInspectionNoOpUnderStandardTarget() {
        setStandardTarget("5.4")
        myFixture.configureByText("test.lua", "io.read()\nprint(\"x\")\ndofile(\"f\")")
        assertTrue(
            "Sandbox inspection must be a no-op under STANDARD target",
            sandboxWarnings().isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // REDIS-06-01: shadowed-local exemption. A name bound to a local (var, table,
    // parameter, numeric/generic for-variable) is NOT flagged; a genuine global still is,
    // and a self-shadow initializer does NOT exempt its own RHS.
    // -------------------------------------------------------------------------

    /** TC 1: `local print = redis.log` shadows `print`; the use is exempt. */
    @Test
    fun testShadowingLocalVarExemptsPrint() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local print = redis.log\nprint(\"x\")")
        assertTrue(
            "Shadowing local 'print' must exempt the print use, got: ${sandboxWarnings()}",
            sandboxWarnings().isEmpty(),
        )
    }

    /** TC 2: `local io = {}` shadows `io`; the member access is exempt. */
    @Test
    fun testShadowingLocalTableExemptsIo() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local io = {}\nio.read()")
        assertTrue(
            "Shadowing local 'io' must exempt the io.read() use, got: ${sandboxWarnings()}",
            sandboxWarnings().isEmpty(),
        )
    }

    /** TC 3: no shadowing local — a genuine global `print` is still flagged. */
    @Test
    fun testGenuineGlobalPrintStillFlagged() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "print(\"x\")")
        assertTrue(
            "Genuine global 'print' must still be flagged, got: ${sandboxWarnings()}",
            sandboxWarnings().any { it.contains("'print'") },
        )
    }

    /** TC 4: a parameter named `print` shadows the global inside the function body. */
    @Test
    fun testParameterShadowExemptsPrint() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local function f(print) print(\"x\") end")
        assertTrue(
            "Parameter 'print' must exempt the print use in the body, got: ${sandboxWarnings()}",
            sandboxWarnings().isEmpty(),
        )
    }

    /** TC 4a: `local print = print` — the RHS `print` is the genuine global (not yet in scope). */
    @Test
    fun testSelfShadowInitializerStillFlagsRhs() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local print = print")
        assertTrue(
            "Self-shadow initializer must still flag the RHS global 'print', got: ${sandboxWarnings()}",
            sandboxWarnings().any { it.contains("'print'") },
        )
    }

    /** TC 4b (numeric-for): `for io = 1, 3 do io.read() end` — the loop var shadows `io`. */
    @Test
    fun testNumericForVarExemptsIo() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "for io = 1, 3 do io.read() end")
        assertTrue(
            "Numeric-for loop var 'io' must exempt the io.read() use, got: ${sandboxWarnings()}",
            sandboxWarnings().isEmpty(),
        )
    }

    /** TC 4b (generic-for): `for io in pairs(t) do io.x() end` — the loop var shadows `io`. */
    @Test
    fun testGenericForVarExemptsIo() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "for io in pairs(t) do io.x() end")
        assertTrue(
            "Generic-for loop var 'io' must exempt the io.x() use, got: ${sandboxWarnings()}",
            sandboxWarnings().isEmpty(),
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

    private fun sandboxWarnings(): List<String> =
        myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("Redis script sandbox") }
}
