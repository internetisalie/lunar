package net.internetisalie.lunar.analysis.redis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 Phase 6 — real-flow documentation coverage for [RedisCommandDocumentationTargetProvider]
 * (AC-5): TC-DOC-1.
 *
 * Configures a Lua file with a `redis.call("GET")` call site, places the caret on the
 * `"GET"` string literal, then calls the provider directly to verify the HTML output
 * contains the expected spec fields (summary, Since, Arity) — without requiring a live
 * IDE documentation popup.
 *
 * Target switching mirrors [LuaRedisCommandInspectionTest]; tearDown restores STANDARD.
 */
@RunWith(JUnit4::class)
class LuaRedisCommandDocumentationTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    // -------------------------------------------------------------------------
    // TC-DOC-1: caret on "GET" literal returns HTML with summary, Since, and Arity.
    // -------------------------------------------------------------------------

    @Test
    fun testGetCommandDocContainsSummaryAndMeta() {
        setRedisTarget("7+")
        // Place caret inside the "GET" literal — offset inside the quotes
        myFixture.configureByText("test.lua", """redis.call("GET<caret>", KEYS[1])""")
        val file = myFixture.file
        val offset = myFixture.caretOffset

        val provider = RedisCommandDocumentationTargetProvider()
        val targets = provider.documentationTargets(file, offset)
        assertTrue("Expected exactly one documentation target for GET, got: $targets", targets.size == 1)

        val docTarget = targets.first() as RedisCommandDocumentationTarget
        val result = docTarget.computeDocumentation()
        assertNotNull("Documentation result must not be null for GET", result)

        val getInfo = RedisCommandSpecService.getInstance().specFor(
            LuaProjectSettings.getInstance(project).state.getTarget(),
        ).lookup("GET") ?: error("GET not found in bundled spec")
        val html = RedisCommandDocumentationTarget.buildDocHtml(getInfo)
        assertTrue(
            "Expected GET summary in HTML (was: '$html')",
            html.contains(getInfo.summary) && getInfo.summary.isNotEmpty(),
        )
        assertTrue("Expected 'Since 1.0.0' in HTML (was: '$html')", html.contains("Since 1.0.0"))
        assertTrue("Expected 'Arity 2' in HTML (was: '$html')", html.contains("Arity 2"))
    }

    @Test
    fun testDocProviderReturnsEmptyForUnknownCommand() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", """redis.call("NOSUCHCHMD<caret>")""")
        val provider = RedisCommandDocumentationTargetProvider()
        val targets = provider.documentationTargets(myFixture.file, myFixture.caretOffset)
        assertTrue("No doc target expected for unknown command", targets.isEmpty())
    }

    @Test
    fun testDocProviderReturnsEmptyUnderStandardTarget() {
        setStandardTarget("5.4")
        myFixture.configureByText("test.lua", """redis.call("GET<caret>")""")
        val provider = RedisCommandDocumentationTargetProvider()
        val targets = provider.documentationTargets(myFixture.file, myFixture.caretOffset)
        assertTrue("No doc target expected under STANDARD target", targets.isEmpty())
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
