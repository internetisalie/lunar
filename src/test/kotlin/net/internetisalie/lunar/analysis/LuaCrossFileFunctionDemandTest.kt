package net.internetisalie.lunar.analysis

import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-19. A function-typed `---@param` of a global in another file is checked at each call, as it is
 * in one file. An untagged global makes no such claim.
 */
@RunWith(JUnit4::class)
class LuaCrossFileFunctionDemandTest : IndexedBasePlatformTestCase() {
    private val defs =
        """
        ---@param fn fun()
        function take(fn) end
        ---@param fn function
        function takeAny(fn) end
        ---@param n number
        function takeNumber(n) end
        ---@param s? string
        function takeOptional(s) end
        function untagged(a) end
        """.trimIndent()

    private fun errors(caller: String): List<String> {
        myFixture.addFileToProject("defs.lua", defs)
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", caller)
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { it.message }
            .distinct()
    }

    private val localF = "---@param a string\nlocal function f(a) end\n"

    @Test
    fun testFunctionWithRequiredParameterAgainstFunDemand() =
        assertEquals(listOf("Too few arguments: expected at least 1, got 0"), errors(localF + "take(f)\n"))

    @Test
    fun testNumberAgainstFunDemand() = assertEquals(listOf("number is not assignable to fun()"), errors("take(1)\n"))

    @Test
    fun testBareDemandAcceptsAnyFunction() = assertEquals(emptyList<String>(), errors(localF + "takeAny(f)\n"))

    @Test
    fun testNumberAgainstBareDemand() =
        assertEquals(listOf("number is not assignable to function"), errors("takeAny(1)\n"))

    @Test
    fun testScalarDemandStillReports() =
        assertEquals(listOf("string is not assignable to number"), errors("takeNumber('x')\n"))

    @Test
    fun testCallbackReturnValueIsNotAnError() =
        assertEquals(emptyList<String>(), errors("take(function() return 1 end)\n"))

    @Test
    fun testOneCallDoesNotChangeTheSignatureForTheNext() =
        assertEquals(
            listOf("number is not assignable to fun()"),
            errors("take(function() end)\ntake(1)\n"),
        )

    @Test
    fun testOmittedOptionalIsSilent() = assertEquals(emptyList<String>(), errors("takeOptional()\n"))

    @Test
    fun testUntaggedGlobalMakesNoArityClaim() = assertEquals(emptyList<String>(), errors("untagged(1, 2)\n"))
}
