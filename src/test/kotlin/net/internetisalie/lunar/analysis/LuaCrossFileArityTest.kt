package net.internetisalie.lunar.analysis

import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-38. A function declared with `---@param` in another file of the project is checked for its
 * argument count, as one declared in the same file is. A bundled stub is not counted against, and
 * neither is a function with an `@overload`.
 */
@RunWith(JUnit4::class)
class LuaCrossFileArityTest : IndexedBasePlatformTestCase() {
    private val defs =
        """
        ---@param s string
        function g1(s) end
        U = {}
        ---@param s string
        function U.f(s) end
        ---@param s string
        ---@overload fun(n: number, m: number)
        function over(s) end
        ---@param s? string
        ---@param ... any
        function loose(s, ...) end
        function untagged(a) end
        """.trimIndent()

    private fun errors(caller: String): List<String> {
        myFixture.addFileToProject("d.lua", defs)
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("caller.lua", caller)
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .filter { it.severity != ErrorSeverity.HYPOTHESIS }
            .map { "${it.severity}:${it.message}" }
            .distinct()
    }

    private val tooFew = "WARNING:Too few arguments: expected at least 1, got 0"
    private val tooMany = "WARNING:Too many arguments: expected at most 1, got 2"

    @Test
    fun testGlobalTooFew() = assertEquals(listOf(tooFew), errors("g1()\n"))

    @Test
    fun testGlobalTooMany() = assertEquals(listOf(tooMany), errors("g1('a', true)\n"))

    @Test
    fun testGlobalExactIsSilent() = assertEquals(emptyList<String>(), errors("g1('a')\n"))

    @Test
    fun testMemberTooFew() = assertEquals(listOf(tooFew), errors("U.f()\n"))

    @Test
    fun testMemberTooMany() = assertEquals(listOf(tooMany), errors("U.f('a', true)\n"))

    @Test
    fun testOverloadedCalleeIsNotCounted() = assertEquals(emptyList<String>(), errors("over(1, 2)\nover()\n"))

    @Test
    fun testOptionalAndVarargAreSilent() = assertEquals(emptyList<String>(), errors("loose()\nloose('a', 1, 2, 3)\n"))

    @Test
    fun testUntaggedGlobalIsNotCounted() = assertEquals(emptyList<String>(), errors("untagged()\nuntagged(1, 2)\n"))

    @Test
    fun testBundledStubGlobalIsNotCounted() =
        assertEquals(emptyList<String>(), errors("error()\nlocal s = tostring(1, 2)\nlocal t = type()\n"))

    @Test
    fun testBundledStubMemberIsNotCounted() =
        assertEquals(emptyList<String>(), errors("local s = table.unpack()\nlocal m = table.unpack({}, 1, 2, 3)\n"))
}
