package net.internetisalie.lunar.analysis.redis

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncCall

/**
 * REDIS-04 Phase 3 — call-site matcher (design §2.10). The shared seam consumed by
 * completion (§2.3), the command inspection (§2.4), quick doc (§3.6), and REDIS-05.
 *
 * Covers: a literal `redis.call`, member/namespace variants (`redis.pcall`,
 * `server.call`/`server.pcall`), a dynamic (non-literal) command, and non-Redis calls.
 * Feeds TC-COMP-3 / TC-UNK-2 downstream (dynamic command never flagged).
 */
class RedisCallSiteMatcherTest : BasePlatformTestCase() {

    private fun firstFuncCall(script: String): LuaFuncCall {
        myFixture.configureByText("site.lua", script)
        return PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncCall::class.java)
            ?: error("no LuaFuncCall parsed from: $script")
    }

    private fun matchOf(script: String): RedisCallSite? =
        RedisCallSiteMatcher.match(firstFuncCall(script))

    /** (1) `redis.call("GET", k)` → matched: command GET, argCount 2, redis/call. */
    fun testLiteralRedisCall() {
        val site = matchOf("""local k = "x"; redis.call("GET", k)""")

        assertNotNull("redis.call should match", site)
        assertEquals("GET", site?.commandName)
        assertEquals("redis", site?.namespace)
        assertEquals("call", site?.member)
        assertEquals("command name + one value arg", 2, site?.argCount)
        assertNotNull("literal command has a backing PSI literal", site?.nameLiteral)
    }

    /** Lower-case literal is upper-cased. */
    fun testCommandNameIsUpperCased() {
        assertEquals("SET", matchOf("""redis.call("set", "k", "v")""")?.commandName)
    }

    /** (2) member variant `redis.pcall`. */
    fun testRedisPcall() {
        val site = matchOf("""redis.pcall("GET", "k")""")

        assertEquals("redis", site?.namespace)
        assertEquals("pcall", site?.member)
        assertEquals("GET", site?.commandName)
    }

    /** (2) namespace variant `server.call` (Valkey). */
    fun testServerCall() {
        val site = matchOf("""server.call("GET", "k")""")

        assertEquals("server", site?.namespace)
        assertEquals("call", site?.member)
        assertEquals("GET", site?.commandName)
    }

    /** (2) namespace + member variant `server.pcall` (Valkey). */
    fun testServerPcall() {
        val site = matchOf("""server.pcall("PING")""")

        assertEquals("server", site?.namespace)
        assertEquals("pcall", site?.member)
        assertEquals("PING", site?.commandName)
        assertEquals(1, site?.argCount)
    }

    /** Shared seam: an arbitrary member (REDIS-05 `register_function`) is carried verbatim. */
    fun testArbitraryMemberIsCarried() {
        val site = matchOf("""redis.register_function("f", g)""")

        assertEquals("redis", site?.namespace)
        assertEquals("register_function", site?.member)
    }

    /** (3) non-literal command `redis.call(cmd, k)` → matched shape, commandName null. */
    fun testDynamicCommandMatchesButHasNullName() {
        val site = matchOf("""local cmd = "GET"; redis.call(cmd, "k")""")

        assertNotNull("dynamic call still matches the shape", site)
        assertNull("dynamic command name is null (never flagged downstream)", site?.commandName)
        assertNull("no backing literal for a dynamic command", site?.nameLiteral)
        assertEquals("arg count still reflects both args", 2, site?.argCount)
        assertEquals("call", site?.member)
    }

    /** (4) a non-Redis member call `foo.bar("x")` → null. */
    fun testNonRedisMemberCallIsNull() {
        assertNull(matchOf("""foo.bar("x")"""))
    }

    /** (4) a bare global call `print("x")` → null (no namespace member shape). */
    fun testBareGlobalCallIsNull() {
        assertNull(matchOf("""print("x")"""))
    }

    /** A deeper chain `redis.a.b("x")` → null (only a single dotted member matches). */
    fun testDeeperChainIsNull() {
        assertNull(matchOf("""redis.a.b("x")"""))
    }

    /** The matcher resolves from a child element (a string literal), not only the call. */
    fun testMatchesFromStringLiteralAnchor() {
        myFixture.configureByText("site.lua", """redis.call("GET", "k")""")
        val literal = PsiTreeUtil.collectElements(myFixture.file) { it.text == "\"GET\"" }
            .firstOrNull()
        assertNotNull(literal)
        val site = literal?.let { RedisCallSiteMatcher.match(it) }
        assertEquals("GET", site?.commandName)
    }
}
