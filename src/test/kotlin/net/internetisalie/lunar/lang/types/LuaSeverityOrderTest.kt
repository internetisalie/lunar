package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-112: whether an assignability mismatch is an ERROR or a HYPOTHESIS is a property of the pair
 * and every fact about it, not of which variable examined the pair first.
 *
 * `d` holds only the string, so a visit of `d` calls the pair certain. `e` also holds an unknown
 * write, so a visit of `e` calls it unknown provenance. Both variables hold the pair
 * (the string, `count(e)`'s demand); the two programs differ only in which was created first.
 */
@RunWith(JUnit4::class)
class LuaSeverityOrderTest : BasePlatformTestCase() {
    private val header = "---@param n number\nlocal function count(n) end\n"

    private fun severitiesOf(body: String): List<Pair<Int, ErrorSeverity>> {
        val file = myFixture.configureByText("t.lua", header + body + "\n") as LuaFile
        val text = file.text
        return LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .map { text.substring(0, it.element.textRange.startOffset).count { c -> c == '\n' } to it.severity }
            .sortedBy { it.first }
    }

    private fun severityCounts(found: List<Pair<Int, ErrorSeverity>>) = found.groupingBy { it.second }.eachCount()

    @Test
    fun testTheCreationOrderOfTheTwoVariablesDoesNotChangeTheSeverityCounts() {
        val certainFirst = severitiesOf("local d = \"s\"\nlocal e = wx.thing\ne = d\ncount(e)")
        val unknownFirst = severitiesOf("local e = wx.thing\nlocal d = \"s\"\ne = d\ncount(e)")
        assertEquals(
            "the same facts in another order must give the same severities. certainFirst=$certainFirst " +
                "unknownFirst=$unknownFirst",
            severityCounts(certainFirst),
            severityCounts(unknownFirst),
        )
    }

    @Test
    fun testAPairHeldByAVariableWithAnUnknownWriteIsAHypothesisAtItsUse() {
        for (body in listOf(
            "local d = \"s\"\nlocal e = wx.thing\ne = d\ncount(e)",
            "local e = wx.thing\nlocal d = \"s\"\ne = d\ncount(e)",
        )) {
            val callLine = body.lines().indexOfFirst { it.startsWith("count(") } + header.lines().size - 1
            val atCall = severitiesOf(body).filter { it.first == callLine }
            assertEquals("count(e) must be reported at all, body=$body", true, atCall.isNotEmpty())
            assertEquals(
                "`e` may hold `wx.thing`, so the string reaching `count(e)` is not certain. body=$body",
                listOf(ErrorSeverity.HYPOTHESIS),
                atCall.map { it.second }.distinct(),
            )
        }
    }

    /** The control: with no unknown write anywhere, the mismatch at the call stays an ERROR. */
    @Test
    fun testTheSameFlowWithoutTheUnknownWriteStaysAnError() {
        for (body in listOf(
            "local d = \"s\"\nlocal e = 1\ne = d\ncount(e)",
            "local e = 1\nlocal d = \"s\"\ne = d\ncount(e)",
        )) {
            val found = severitiesOf(body)
            assertTrue(
                "a certain mismatch must stay an ERROR, body=$body found=$found",
                found.any {
                    it.second ==
                        ErrorSeverity.ERROR
                },
            )
        }
    }
}
