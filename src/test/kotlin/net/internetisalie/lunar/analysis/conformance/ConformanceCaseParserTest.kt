package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/** MAINT-55 TC-1, TC-2, TC-19, TC-3: the case format of design §2.1. */
@RunWith(JUnit4::class)
class ConformanceCaseParserTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun testTwoCasesWithMustAndMayLines_TC_1() {
        val text =
            listOf(
                "-- preamble",
                "-- ==== case a-001 rule=arg source=a.lua#1",
                "local x = 1",
                "f(x) -- E",
                "g() -- E?",
                "",
                "-- ==== case a-002 rule=nil.check source=a.lua#2",
                "print(1)",
            ).joinToString("\n")
        val cases = ConformanceCaseParser.parse(text, "a.lua")

        assertEquals(listOf("a-001", "a-002"), cases.map { it.id })
        val first = cases[0]
        assertEquals(3, first.body.size)
        assertEquals(setOf(2), first.must)
        assertEquals(setOf(3), first.may)
        assertTrue("text was ${first.text}", first.text.endsWith("g() -- E?\n"))
        assertEquals(emptySet<Int>(), cases[1].must)
        assertEquals(emptySet<Int>(), cases[1].may)
    }

    @Test
    fun testMalformedHeaderNamesFileAndLine_TC_2() {
        val text = "-- preamble\nlocal x = 1\n-- ==== case b-001 rule=\nprint(x)\n"
        val error = assertThrows(IllegalArgumentException::class.java) { ConformanceCaseParser.parse(text, "b.lua") }
        assertTrue("message was ${error.message}", error.message.orEmpty().contains("b.lua:3"))
    }

    @Test
    fun testUnknownRuleNamesFileLineAndRule_TC_19() {
        val text = "-- ==== case b-002 rule=bogus source=b.lua#2\nprint(1)\n"
        val error = assertThrows(IllegalArgumentException::class.java) { ConformanceCaseParser.parse(text, "b.lua") }
        val message = error.message.orEmpty()
        assertTrue("message was $message", message.contains("b.lua:1") && message.contains("bogus"))
    }

    @Test
    fun testDuplicateIdAcrossFilesIsAnError_TC_3() {
        val dir = temp.newFolder("cases")
        File(dir, "one.lua").writeText("-- ==== case c-001 rule=nil.check source=one.lua#1\nprint(1)\n")
        File(dir, "two.lua").writeText("-- ==== case c-001 rule=nil.check source=two.lua#1\nprint(2)\n")
        val error = assertThrows(IllegalArgumentException::class.java) { ConformanceCaseParser.loadAll(dir) }
        assertTrue("message was ${error.message}", error.message.orEmpty().contains("c-001"))
    }
}
