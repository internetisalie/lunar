package net.internetisalie.lunar.redis.debug

import net.internetisalie.lunar.redis.resp.RespValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Socket-free coverage of [LdbPrintParser] (design §3.4).
 *
 * Covers TC-LDB-PRINT-1 (scalar + nested table into an expandable tree) and TC-LDB-PRINT-2 (a
 * server-truncated table parses to a `truncated = true` node without throwing or using `!!`).
 */
class TestLdbPrintParser {

    private fun block(vararg lines: String): RespValue.Array =
        RespValue.Array(lines.map { RespValue.Bulk(it.toByteArray(Charsets.UTF_8)) })

    /** TC-LDB-PRINT-1: a scalar and a nested table become a two-level value tree. */
    @Test
    fun testParseLocalsScalarAndNestedTable() {
        val reply = block(
            "<value> x = 10",
            """<value> t = {"a": 1, "b": {"c": 2}}""",
        )
        val expected = listOf(
            LuaLdbLocal("x", LdbValueNode.Scalar("10")),
            LuaLdbLocal(
                "t",
                LdbValueNode.Table(
                    listOf(
                        "a" to LdbValueNode.Scalar("1"),
                        "b" to LdbValueNode.Table(listOf("c" to LdbValueNode.Scalar("2"))),
                    ),
                ),
            ),
        )
        assertEquals(expected, LdbPrintParser.parseLocals(reply))
    }

    /** TC-LDB-PRINT-2: a `maxlen`-truncated table sets `truncated = true` and never throws. */
    @Test
    fun testParseLocalsTruncatedTableFlagsTruncationWithoutThrowing() {
        val reply = block(
            """<value> t = {"a": 1, "b": {"c": 2, "d": {"e": 3 (truncated)""",
        )
        val locals = LdbPrintParser.parseLocals(reply)
        assertEquals(1, locals.size)
        val value = locals.first().value
        assertTrue(value is LdbValueNode.Table, "truncated table still parses as a Table node")
        assertTrue(value.truncated, "an unbalanced/truncated table must carry truncated = true")
    }

    /** A truncated scalar strips the marker and flags truncation. */
    @Test
    fun testParseTruncatedScalar() {
        val reply = block("""<value> s = "a very long stri (truncated)""")
        val value = LdbPrintParser.parseLocals(reply).first().value
        assertEquals(LdbValueNode.Scalar("\"a very long stri", truncated = true), value)
    }

    /** `parseValue` reads a single `eval` result line into one node. */
    @Test
    fun testParseSingleEvalValue() {
        assertEquals(
            LdbValueNode.Scalar("42"),
            LdbPrintParser.parseValue(block("<value> 42")),
        )
    }

    /** Status-noise lines without ` = ` are skipped, not turned into locals. */
    @Test
    fun testParseLocalsSkipsNonAssignmentLines() {
        val reply = block("* some status", "<value> y = true")
        assertEquals(
            listOf(LuaLdbLocal("y", LdbValueNode.Scalar("true"))),
            LdbPrintParser.parseLocals(reply),
        )
    }
}
