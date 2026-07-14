package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real-flow `completeBasic` tests for [LuaRedisCommandCompletionContributor] (AC-3).
 *
 * Covers TC-COMP-1..4 from requirements §Test Cases. The target is switched via the
 * same EDT/PlatformLibraryIndex idiom as [net.internetisalie.lunar.analysis.redis.RedisAmbientTypingTest]
 * and restored in [tearDown] to prevent leaking a Redis-target into alphabetically-later suites.
 *
 * All assertions operate on `myFixture.completeBasic()` (real completion invocation) and
 * on the presentation rendered from `myFixture.lookupElements` — never on engine mocks.
 */
@RunWith(JUnit4::class)
class LuaRedisCommandCompletionTest : IndexedBasePlatformTestCase() {

    // Restore the default target after each test so Redis library roots do not leak into
    // alphabetically-later test suites (mirrors RedisAmbientTypingTest.tearDown contract).
    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    private fun setRedisTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.REDIS, version))
            PlatformLibraryIndex.reload()
        }
    }

    private fun setStandardTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.STANDARD, version))
            PlatformLibraryIndex.reload()
        }
    }

    // -------------------------------------------------------------------------
    // TC-COMP-1: Redis 7+ target — core commands present and each carries tail text.
    // -------------------------------------------------------------------------
    @Test
    fun testRedis7CommandsOfferedWithTailText() {
        setRedisTarget("7+")
        myFixture.configureByText("s.lua", """redis.call("<caret>")""")
        myFixture.completeBasic()

        val elements = myFixture.lookupElements ?: emptyArray()
        val strings = elements.map { it.lookupString }

        assertTrue("GET must be offered under Redis 7+", strings.contains("GET"))
        assertTrue("SET must be offered under Redis 7+", strings.contains("SET"))
        assertTrue("HSET must be offered under Redis 7+", strings.contains("HSET"))

        // Assert that at least one well-known command carries a non-blank summary tail text.
        val getElement = elements.firstOrNull { it.lookupString == "GET" }
        assertNotNull("GET lookup element must exist", getElement)
        val presentation = LookupElementPresentation()
        getElement!!.renderElement(presentation)
        val tail = presentation.tailText
        assertNotNull("GET must have tail text (the spec summary)", tail)
        assertTrue("GET tail text must be non-blank, got: '$tail'", tail!!.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // TC-COMP-2: Redis 5 target — version-filtered; SINTERCARD (since 7.0.0) is absent.
    // -------------------------------------------------------------------------
    @Test
    fun testRedis5ExcludesNewerCommands() {
        setRedisTarget("5")
        myFixture.configureByText("s.lua", """redis.call("<caret>")""")
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings ?: emptyList()
        // Core Redis-5 commands must be present.
        assertTrue("GET must be offered under Redis 5", strings.contains("GET"))
        assertTrue("SET must be offered under Redis 5", strings.contains("SET"))
        // SINTERCARD was introduced in 7.0.0; must be absent under Redis 5.
        assertFalse(
            "SINTERCARD (since 7.0.0) must NOT be offered under Redis 5",
            strings.contains("SINTERCARD"),
        )
    }

    // -------------------------------------------------------------------------
    // TC-COMP-3: Non-literal first arg — contributor must not inject command names.
    // The LuaElementTypes.STRING pattern filter means the contributor's provider is never
    // reached when the caret is on an identifier, so completeBasic() returns no redis commands.
    // -------------------------------------------------------------------------
    @Test
    fun testNonLiteralFirstArgYieldsNoCommandCompletions() {
        setRedisTarget("7+")
        // `cmd` is a name-ref, not a string literal — caret on the identifier.
        myFixture.configureByText("s.lua", "local cmd = \"GET\"\nredis.call(c<caret>md)")
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings ?: emptyList()
        // No Redis command names (upper-case) should appear — they would only come from our
        // contributor and our contributor only fires on STRING tokens.
        val commandNames = strings.filter { it == it.uppercase() && it.length >= 3 && it.all { c -> c.isLetter() } }
        // Filter out known Lua keyword candidates that happen to be uppercase (none expected, but be safe).
        val redisCommandsInResult = commandNames.filter { name ->
            // A command from the spec will be present only if our contributor ran.
            // GET, SET, HSET are spec commands; if they appear the test fails.
            name in setOf("GET", "SET", "HSET", "DEL", "INCR", "SINTERCARD")
        }
        assertTrue(
            "No Redis command names must be injected for a non-literal first arg, got: $redisCommandsInResult",
            redisCommandsInResult.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-COMP-4: Standard target — contributor must no-op (no Redis commands offered).
    // -------------------------------------------------------------------------
    @Test
    fun testStandardTargetYieldsNoRedisCommandCompletions() {
        setStandardTarget("5.4")
        myFixture.configureByText("s.lua", """redis.call("<caret>")""")
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings ?: emptyList()
        val redisCommandsInResult = strings.filter { name ->
            name in setOf("GET", "SET", "HSET", "DEL", "INCR", "SINTERCARD")
        }
        assertTrue(
            "No Redis command names must be offered under the Standard target, got: $redisCommandsInResult",
            redisCommandsInResult.isEmpty(),
        )
    }
}
