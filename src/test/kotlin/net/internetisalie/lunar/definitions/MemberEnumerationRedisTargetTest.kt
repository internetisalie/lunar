package net.internetisalie.lunar.definitions

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * COMP-09 Phase 2 — the `@field` superset on the **bundled** Redis/Valkey stubs (TC 7f, TC 7f-bis) and
 * the one `LuaScope.declare` site Rule S deliberately excludes (TC 10h).
 *
 * These are the tests DR-21/DR-22's armed suite count could not make: both ran entirely on the STANDARD
 * 5.4 target, so "exactly two tests move" is a STANDARD-target statement and says nothing about a
 * Redis-targeted project. Every expectation below is read off design §1.10.8a's pasted probe output.
 *
 * The mechanism is `@field`-**only** declaration. `redis.lua` is `---@class redis` + ten `---@field`
 * constants + a bare `redis = {}` + `function redis.*`, with the ten constants **never assigned**, so
 * they exist only in the `@class` comment — which today's global door does not read and design §4.3's
 * source 3 does. `server.lua` carries the *same* ten `---@field`s but also writes
 * `server.LOG_DEBUG = 0` for all ten, and is measured **unchanged**: that non-movement is the control
 * that stops a reviewer dismissing TC 7f as "any `@class` stub moves".
 *
 * **The function count is per version — 10 / 11 / 13 / 12 / 12 on Redis 5, Redis 6, Redis 7+,
 * Valkey 7.2, Valkey 8** — so each test names its target and spells its set out. Blast radius: five
 * `redis` receivers move, `server` does not; gating the two targets whose function sets differ covers
 * the family without one test per version.
 */
class MemberEnumerationRedisTargetTest : IndexedBasePlatformTestCase() {
    /**
     * `LuaProjectSettings` is a project-level service and `BasePlatformTestCase` reuses one light
     * project across the whole module run, so a leaked Redis target poisons the alphabetically later
     * `lang.indexing` / `lang.types` tests that correctly assume the STANDARD default.
     */
    override fun tearDown() {
        try {
            setTarget(LuaPlatform.STANDARD, "5.4")
        } finally {
            super.tearDown()
        }
    }

    /** TC 7f — Redis 7+: the dot door gains the ten constants, the colon door does not move. */
    fun testRedisSevenGainsTheFieldDeclaredConstantsAtTheDotDoorOnly() {
        setTarget(LuaPlatform.REDIS, "7+")
        assertOffered("redis.", REDIS_7_FUNCTIONS + FIELD_CONSTANTS)
        assertOffered("redis:", REDIS_7_FUNCTIONS)
    }

    /**
     * TC 7f-bis — Valkey 8, the control.
     *
     * `redis` moves exactly as on Redis 7+ but over **twelve** functions, while `server` — same
     * `---@class`, same ten `---@field`s, plus real assignments — does not move at either door. Note
     * `---@class server : redis` does **not** inherit on the completion door either way, which is why
     * `server.` is a clean equality at 21 rather than a near-one (design §1.10.8a, finding e).
     */
    fun testValkeyEightMovesRedisAndLeavesServerAlone() {
        setTarget(LuaPlatform.VALKEY, "8")
        assertOffered("redis.", VALKEY_8_FUNCTIONS + FIELD_CONSTANTS)
        assertOffered("redis:", VALKEY_8_FUNCTIONS)
        assertOffered("server.", SERVER_FUNCTIONS + FIELD_CONSTANTS)
        assertOffered("server:", SERVER_FUNCTIONS)
    }

    /**
     * TC 10h — `seedAmbientGlobals` (`LuaTypesVisitor.kt:1360`) is outside Rule S, and this is the only
     * target family it fires on.
     *
     * `runtime/redis/redis-7/global.lua` declares `KEYS = {}` and `ARGV = {}` bare. Both are empty table
     * literals, so design §4.3's source 4 records no member and source 5 no opacity sentinel: the index
     * answers `found = true, authoritative = true, members = []` and today offers `[]` — identical.
     * Until this test the exclusion had neither a gate nor a measurement while being live on a shipped
     * target.
     */
    fun testAmbientSeededGlobalsAreUnchangedAtBothDoors() {
        setTarget(LuaPlatform.REDIS, "7+")
        assertOffered("KEYS.", emptySet())
        assertOffered("KEYS:", emptySet())
        assertOffered("ARGV.", emptySet())
        assertOffered("ARGV:", emptySet())
    }

    /** Built the way `RedisAmbientTypingTest.setRedisTarget` does — the second parameter is a `VersionEntry`. */
    private fun setTarget(
        platform: LuaPlatform,
        label: String,
    ) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(platform, version))
            PlatformLibraryIndex.reload()
        }
    }

    /**
     * No fixture here has a single perfect match, so the BUG-431 auto-insert recovery cannot apply and a
     * null return means the fixture changed rather than that nothing was offered.
     */
    private fun assertOffered(
        receiverAndSeparator: String,
        expected: Set<String>,
    ) {
        myFixture.configureByText("consumer.lua", "$receiverAndSeparator<caret>\n")
        val elements = myFixture.completeBasic()
        val found = requireNotNull(elements) { "`$receiverAndSeparator` auto-inserted a single match" }
        val offered = found.map { it.lookupString }.toSet()
        println("COMP-09 target door | $receiverAndSeparator<caret> | offered=${offered.sorted()}")
        assertEquals("`$receiverAndSeparator<caret>` offered ${offered.sorted()}", expected, offered)
    }

    private companion object {
        /** The ten `---@field` constants, declared and never assigned, on all five Redis/Valkey stubs. */
        val FIELD_CONSTANTS =
            setOf(
                "LOG_DEBUG",
                "LOG_NOTICE",
                "LOG_VERBOSE",
                "LOG_WARNING",
                "REDIS_VERSION",
                "REDIS_VERSION_NUM",
                "REPL_ALL",
                "REPL_AOF",
                "REPL_NONE",
                "REPL_REPLICA",
            )

        val REDIS_7_FUNCTIONS =
            setOf(
                "acl_check_cmd",
                "breakpoint",
                "call",
                "debug",
                "error_reply",
                "log",
                "pcall",
                "register_function",
                "replicate_commands",
                "set_repl",
                "setresp",
                "sha1hex",
                "status_reply",
            )

        val VALKEY_8_FUNCTIONS = REDIS_7_FUNCTIONS - "register_function"

        val SERVER_FUNCTIONS = VALKEY_8_FUNCTIONS - "replicate_commands"
    }
}
