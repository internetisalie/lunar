package net.internetisalie.lunar.analysis.conformance

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.project.PlatformLibraryIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** MAINT-55 TC-12, TC-13, TC-14: running a case in the light fixture (design §3.3, §3.4). */
@RunWith(JUnit4::class)
class ConformanceRunnerTest : BasePlatformTestCase() {
    private fun case(
        id: String,
        text: String,
    ) = ConformanceCase(id, "field.assign", "$id.lua#1", text.removeSuffix("\n").split("\n"), emptySet(), emptySet())

    @Test
    fun testEarlierCasesDoNotLeakIntoLaterOnes_TC_12() {
        ConformanceRunner.findings(myFixture, case("p-001", POISON))
        assertEquals(emptyList<ConformanceFinding>(), ConformanceRunner.findings(myFixture, case("q-001", QUERY)))
        val control = ConformanceRunner.findings(myFixture, case("pq-001", POISON + QUERY))
        assertEquals(setOf(ConformanceFinding(4, 4, "fun() is not assignable to Poison")), control.toSet())
    }

    @Test
    fun testPinMovesAStoredLua51TargetTo54_TC_13() {
        val initial = CorpusTarget.pin(project, LuaLanguageLevel.LUA51)
        try {
            ConformanceRunner.pin(project)
            assertEquals("lua-5.4", PlatformLibraryIndex.getPlatformLibraryFolder(project)?.name)
        } finally {
            CorpusTarget.restore(project, initial)
        }
    }

    @Test
    fun testArgRuleFromTheFirstParamTag_TC_14() {
        assertEquals("arg.optional", argRuleOf("---@param x number?\nlocal function f(x) end\nf(true)\n"))
        assertEquals("arg.array", argRuleOf("---@param x string[]\nlocal function f(x) end\nf(true)\n"))
        assertEquals("arg.other", argRuleOf("local function f(x) end\nf(true)\n"))
    }

    private fun argRuleOf(text: String): String =
        ConformanceRules.argRule(myFixture.configureByText(ConformanceRunner.FILE_NAME, text))

    private companion object {
        const val POISON = "---@class Poison\nlocal P = {}\n"
        const val QUERY = "---@type Poison\nlocal q = function() end\n"
    }
}
