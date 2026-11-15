package net.internetisalie.lunar.analysis

import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-81. A call whose last argument is `...` or another call supplies an unknown number of values,
 * and a parameter typed `T|nil` may be omitted, so neither is *Too few arguments*. A call that
 * really leaves a required parameter unset is still reported. Each case runs against a callee in
 * the open file and one declared in another file, which reach the arity check by different routes.
 */
@RunWith(JUnit4::class)
class LuaOpenEndedCallArityTest : IndexedBasePlatformTestCase() {
    private val twoRequired =
        """
        ---@param a integer
        ---@param b integer
        function f(a, b) end

        function g() return 1, 2 end
        """.trimIndent()

    private val nilUnionOptional =
        """
        ---@param a integer
        ---@param b integer|nil
        function h(a, b) end
        """.trimIndent()

    private fun arityHits(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Too few arguments") || it.startsWith("Too many arguments") }
            .distinct()
    }

    private fun crossFileHits(
        defs: String,
        call: String,
    ): List<String> {
        myFixture.addFileToProject("defs.lua", defs)
        return arityHits("$call\n")
    }

    @Test
    fun testVarargArgumentIsSilentInFile() = assertEquals(emptyList<String>(), arityHits("$twoRequired\nf(...)\n"))

    @Test
    fun testVarargArgumentIsSilentCrossFile() = assertEquals(emptyList<String>(), crossFileHits(twoRequired, "f(...)"))

    @Test
    fun testCallResultArgumentIsSilentInFile() = assertEquals(emptyList<String>(), arityHits("$twoRequired\nf(g())\n"))

    @Test
    fun testCallResultArgumentIsSilentCrossFile() =
        assertEquals(emptyList<String>(), crossFileHits(twoRequired, "f(g())"))

    @Test
    fun testNilUnionParameterMayBeOmittedInFile() =
        assertEquals(emptyList<String>(), arityHits("$nilUnionOptional\nh(1)\n"))

    @Test
    fun testNilUnionParameterMayBeOmittedCrossFile() =
        assertEquals(emptyList<String>(), crossFileHits(nilUnionOptional, "h(1)"))

    @Test
    fun testNoArgumentsIsStillReportedInFile() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 0"), arityHits("$twoRequired\nf()\n"))

    @Test
    fun testNoArgumentsIsStillReportedCrossFile() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 0"), crossFileHits(twoRequired, "f()"))

    @Test
    fun testOneLiteralArgumentIsStillReported() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 1"), arityHits("$twoRequired\nf(1)\n"))

    @Test
    fun testParenthesizedCallIsTruncatedToOneValueAndReported() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 1"), arityHits("$twoRequired\nf((g()))\n"))

    @Test
    fun testRequiredParameterAfterNilUnionIsStillReported() =
        assertEquals(listOf("Too few arguments: expected at least 1, got 0"), arityHits("$nilUnionOptional\nh()\n"))
}
