package net.internetisalie.lunar.redis.debug

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Socket-free coverage of [LdbWire.encode] (design §3.2).
 *
 * Covers TC-LDB-ENC-1 (verb/arg token mapping), TC-LDB-ENC-2 (break add/remove/clear),
 * TC-LDB-SYNC-2 (`SCRIPT DEBUG YES|SYNC`).
 */
class TestLdbWire {

    private fun tokens(command: LdbCommand): List<String> =
        LdbWire.encode(command).map { it.toString(Charsets.UTF_8) }

    /** TC-LDB-ENC-1: each command encodes to its RESP array-of-bulk token vector. */
    @Test
    fun testEncodeCoreVerbsAndArguments() {
        assertEquals(listOf("break", "12"), tokens(LdbCommand.Break(12)))
        assertEquals(listOf("step"), tokens(LdbCommand.Step))
        assertEquals(listOf("continue"), tokens(LdbCommand.Continue))
        assertEquals(listOf("print"), tokens(LdbCommand.Print(null)))
        assertEquals(listOf("eval", "1+1"), tokens(LdbCommand.Eval("1+1")))
        assertEquals(listOf("redis", "GET", "k"), tokens(LdbCommand.RedisCmd(listOf("GET", "k"))))
        assertEquals(listOf("abort"), tokens(LdbCommand.Abort))
    }

    /** TC-LDB-ENC-1: `next` and `print <var>` map to their argument-bearing verbs. */
    @Test
    fun testEncodeNextAndPrintVariable() {
        assertEquals(listOf("next"), tokens(LdbCommand.Next))
        assertEquals(listOf("print", "t"), tokens(LdbCommand.Print("t")))
    }

    /** TC-LDB-ENC-2: break add, remove (negative line), and clear-all. */
    @Test
    fun testEncodeBreakAddRemoveClear() {
        assertEquals(listOf("break", "12"), tokens(LdbCommand.Break(12)))
        assertEquals(listOf("break", "-12"), tokens(LdbCommand.RemoveBreak(12)))
        assertEquals(listOf("break", "0"), tokens(LdbCommand.ClearBreaks))
    }

    /** TC-LDB-SYNC-2: forked vs sync enter-debug token vectors. */
    @Test
    fun testEncodeEnterDebugModes() {
        assertEquals(
            listOf("SCRIPT", "DEBUG", "YES"),
            tokens(LdbCommand.EnterDebug(LuaRedisDebugMode.FORKED)),
        )
        assertEquals(
            listOf("SCRIPT", "DEBUG", "SYNC"),
            tokens(LdbCommand.EnterDebug(LuaRedisDebugMode.SYNC)),
        )
    }

    /** Tokens are byte-accurate UTF-8 (multi-byte expression survives the round trip). */
    @Test
    fun testEncodeUsesUtf8Bytes() {
        val encoded = LdbWire.encode(LdbCommand.Eval("café"))
        assertEquals("café".toByteArray(Charsets.UTF_8).size, encoded[1].size)
        assertEquals("café", encoded[1].toString(Charsets.UTF_8))
    }
}
