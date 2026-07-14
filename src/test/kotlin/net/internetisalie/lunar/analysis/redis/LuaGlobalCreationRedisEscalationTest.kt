package net.internetisalie.lunar.analysis.redis

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaGlobalCreationInspection
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 Phase 6 — real-flow coverage of the Global-creation escalation (AC-8):
 * TC-GLOB-1, TC-GLOB-2.
 *
 * TC-GLOB-1: under Redis 7+, a bare global assignment produces ERROR severity;
 *            under STANDARD, the same input produces WARNING severity (not ERROR).
 * TC-GLOB-2: under Redis 7+, a suppressed global assignment (`---@diagnostic disable-next-line`)
 *            produces no problem (escalation still honors `LuaInspectionSuppression`).
 *
 * Target switching mirrors [LuaRedisCommandInspectionTest]; tearDown restores STANDARD.
 */
@RunWith(JUnit4::class)
class LuaGlobalCreationRedisEscalationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaGlobalCreationInspection())
    }

    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    // -------------------------------------------------------------------------
    // TC-GLOB-1: severity escalates to ERROR under Redis; stays WARNING off Redis.
    // -------------------------------------------------------------------------

    @Test
    fun testGlobalCreationEscalatesToErrorUnderRedis() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "myGlobal = 1")
        val problems = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Global creation") == true }
        assertTrue(
            "Expected exactly one Global creation problem under Redis target, got: $problems",
            problems.size == 1,
        )
        assertEquals(
            "Expected ERROR severity under Redis target (AC-8 escalation)",
            HighlightSeverity.ERROR,
            problems.first().severity,
        )
    }

    @Test
    fun testGlobalCreationIsWarningUnderStandardTarget() {
        setStandardTarget("5.4")
        myFixture.configureByText("test.lua", "myGlobal = 1")
        val problems = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Global creation") == true }
        assertTrue(
            "Expected exactly one Global creation problem under STANDARD target, got: $problems",
            problems.size == 1,
        )
        // Under STANDARD the severity is WARNING (GENERIC_ERROR_OR_WARNING maps to WARNING highlight)
        assertTrue(
            "Expected non-ERROR severity under STANDARD target (no escalation), got: ${problems.first().severity}",
            problems.first().severity != HighlightSeverity.ERROR,
        )
    }

    // -------------------------------------------------------------------------
    // TC-GLOB-2: suppression still short-circuits the escalated problem under Redis.
    // -------------------------------------------------------------------------

    @Test
    fun testEscalatedGlobalCreationIsSuppressible() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "test.lua",
            """
            ---@diagnostic disable-next-line: undefined-global
            myGlobal = 1
            """.trimIndent(),
        )
        val problems = myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.startsWith("Global creation") == true }
        assertTrue(
            "Suppressed global creation must produce no problem even under Redis (TC-GLOB-2), got: $problems",
            problems.isEmpty(),
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
}
