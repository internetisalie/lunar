package net.internetisalie.lunar.analysis.inspections

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * REDIS-03 Phase 3 — real-flow coverage of [LuaValkeyPortabilityInspection] and
 * [LuaValkeyToRedisQuickFix] (TC-INSP-1..6).
 *
 * Uses the real platform inspection machinery: [com.intellij.testFramework.fixtures.CodeInsightTestFixture.enableInspections]
 * + `configureByText` + `doHighlighting(WARNING)` for detection, and `findSingleIntention` /
 * `launchAction` + file-text assertion for the quick fix. The project target is set exactly as
 * production reads it: `LuaProjectSettings.getInstance(project).state.setTarget(Target(platform, …))`.
 */
class LuaValkeyPortabilityInspectionTest : BaseDocumentTest() {

    @BeforeEach
    fun setupInspection() {
        myFixture.enableInspections(LuaValkeyPortabilityInspection())
    }

    // TC-INSP-1: server.* flagged under a Redis target.
    @Test
    fun serverAccessFlaggedUnderRedisTarget() {
        setTarget(LuaPlatform.REDIS)
        myFixture.configureByText(LuaFileType, "server.call(\"PING\")")
        val warnings = portabilityWarnings()
        Assertions.assertTrue(
            warnings.any { it.contains("`server.*` is a Valkey-only namespace") },
            "Expected a server.* portability warning under a Redis target",
        )
    }

    // TC-INSP-2: SERVER_* global flagged, no quick fix.
    @Test
    fun serverGlobalFlaggedUnderRedisTarget() {
        setTarget(LuaPlatform.REDIS)
        myFixture.configureByText(LuaFileType, "local x = SERVER_NAME")
        val warnings = portabilityWarnings()
        Assertions.assertTrue(
            warnings.any { it.contains("`SERVER_NAME` is a Valkey-only global") },
            "Expected a SERVER_NAME portability warning under a Redis target",
        )
    }

    // TC-INSP-2 (cont.): the SERVER_* case offers no "Replace 'server' with 'redis'" quick fix.
    @Test
    fun serverGlobalOffersNoQuickFix() {
        setTarget(LuaPlatform.REDIS)
        myFixture.configureByText(LuaFileType, "local x = SERVER_<caret>NAME")
        myFixture.doHighlighting(HighlightSeverity.WARNING)
        val fix = myFixture.getAvailableIntention("Replace 'server' with 'redis'")
        Assertions.assertNull(fix, "SERVER_* globals must not offer the server->redis quick fix")
    }

    // TC-INSP-3: applying the quick fix rewrites server.call -> redis.call and clears the warning.
    @Test
    fun quickFixRewritesServerToRedis() {
        setTarget(LuaPlatform.REDIS)
        myFixture.configureByText(LuaFileType, "ser<caret>ver.call(\"PING\")")
        myFixture.doHighlighting(HighlightSeverity.WARNING)
        val fix = myFixture.findSingleIntention("Replace 'server' with 'redis'")
        myFixture.launchAction(fix)
        Assertions.assertEquals("redis.call(\"PING\")", myFixture.file.text)
        Assertions.assertTrue(portabilityWarnings().isEmpty(), "No portability warning should remain after the fix")
    }

    // TC-INSP-4: silent under a Valkey target (both server.* and SERVER_* are legal).
    @Test
    fun silentUnderValkeyTarget() {
        setTarget(LuaPlatform.VALKEY)
        myFixture.configureByText(LuaFileType, "server.call(\"PING\")\nlocal x = SERVER_NAME")
        Assertions.assertTrue(portabilityWarnings().isEmpty(), "No portability warnings under a Valkey target")
    }

    // TC-INSP-5: silent under a non-Redis/Valkey target (Standard).
    @Test
    fun silentUnderStandardTarget() {
        setTarget(LuaPlatform.STANDARD)
        myFixture.configureByText(LuaFileType, "server.call(\"PING\")")
        Assertions.assertTrue(portabilityWarnings().isEmpty(), "No portability warnings under a Standard target")
    }

    // TC-INSP-6: redis.* (the compat namespace) is never flagged under a Redis target.
    @Test
    fun redisAccessNeverFlagged() {
        setTarget(LuaPlatform.REDIS)
        myFixture.configureByText(LuaFileType, "redis.call(\"PING\")")
        Assertions.assertTrue(portabilityWarnings().isEmpty(), "redis.* is portable and must never be flagged")
    }

    private fun setTarget(platform: LuaPlatform) {
        val version = PlatformVersionRegistry.defaultVersion(platform)
            ?: throw IllegalStateException("No default version for $platform")
        LuaProjectSettings.getInstance(myFixture.project).state.setTarget(Target(platform, version))
    }

    private fun portabilityWarnings(): List<String> =
        myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("Valkey-only") }
}
