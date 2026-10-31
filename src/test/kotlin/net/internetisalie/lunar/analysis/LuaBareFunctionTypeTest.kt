package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-15. A bare `function` names any function and says nothing about its parameters, so it neither
 * demands nor promises a parameter list. `fun()` does make that promise, and a call with no arguments
 * is a real demand; those are the controls.
 *
 * Messages are read from the graph, not the editor, so an inferred-signature HYPOTHESIS counts too.
 */
@RunWith(JUnit4::class)
class LuaBareFunctionTypeTest : IndexedBasePlatformTestCase() {
    private val takeBare = "---@param fn function\nlocal function take(fn) end\n"
    private val takeEmptyFun = "---@param fn fun()\nlocal function take(fn) end\n"

    private fun signatureMessages(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        myFixture.doHighlighting()
        return runReadAction { LuaTypesSnapshot.forFile(myFixture.file).getErrors().map { it.message } }
            .filter { "arguments" in it || "not assignable" in it }
            .distinct()
            .sorted()
    }

    private fun assertSilent(text: String) = assertEquals(emptyList<String>(), signatureMessages(text))

    @Test
    fun testParameterlessFunctionMeetsBareDemand() = assertSilent("${takeBare}local function f() end\ntake(f)\n")

    @Test
    fun testOneRequiredParamMeetsBareDemand() =
        assertSilent("$takeBare---@param a string\nlocal function f(a) end\ntake(f)\n")

    @Test
    fun testManyRequiredParamsMeetBareDemand() =
        assertSilent(
            "$takeBare---@param a string\n---@param b number\n---@param c table\nlocal function f(a, b, c) end\ntake(f)\n",
        )

    @Test
    fun testInferredParamsMeetBareDemand() = assertSilent("${takeBare}local function f(a, b) end\ntake(f)\n")

    @Test
    fun testVarargFunctionMeetsBareDemand() =
        assertSilent("$takeBare---@param a string\nlocal function f(a, ...) end\ntake(f)\n")

    @Test
    fun testAnonymousFunctionMeetsBareDemand() = assertSilent("${takeBare}take(function(a, b) end)\n")

    @Test
    fun testColonMethodWithRequiredParamMeetsBareDemand() =
        assertSilent(
            "$takeBare---@class C\nlocal C = {}\n---@param a string\nfunction C:m(a) end\n---@type C\nlocal c\ntake(c.m)\n",
        )

    @Test
    fun testFunTypedValueMeetsBareDemand() =
        assertSilent("$takeBare---@type fun(a: string, b: string)\nlocal f\ntake(f)\n")

    @Test
    fun testBareMemberOfUnionDemandAcceptsAnyFunction() =
        assertSilent(
            "---@param fn string|function\nlocal function take(fn) end\n---@param a string\nlocal function f(a) end\ntake(f)\n",
        )

    @Test
    fun testBareTypeAnnotationAcceptsAnyFunction() =
        assertSilent("---@param a string\nlocal function f(a) end\n---@type function\nlocal g = f\n")

    @Test
    fun testBareFieldDemand() =
        assertSilent(
            "---@class H\n---@field cb function\nlocal H = {}\n---@param a string\nlocal function f(a) end\n" +
                "---@type H\nlocal h = { cb = f }\n",
        )

    @Test
    fun testBareParameterMayBeCalledWithAnyArguments() =
        assertSilent("---@param fn function\nlocal function take(fn)\n  fn(1, 2, 3)\nend\n")

    @Test
    fun testBareLocalMayBeCalledWithAnyArguments() = assertSilent("---@type function\nlocal g\ng(1, 2)\n")

    @Test
    fun testBareReturnMayBeCalledWithAnyArgumentsAcrossFiles() {
        myFixture.addFileToProject(
            "defs.lua",
            "---@return function\nfunction make() end\n",
        )
        assertSilent("local g = make()\ng(1, 2)\n")
    }

    /** `function` says nothing about what a call returns, so the result is unknown, not `nil`. */
    @Test
    fun testResultOfCallingBareReturnIsNotNilAcrossFiles() {
        myFixture.addFileToProject("defs.lua", "---@return function\nfunction make() end\n")
        assertSilent("local g = make()\nlocal r = g()\n---@type string\nlocal s = r\n")
    }

    @Test
    fun testBareFieldOfClassInAnotherFileMayBeCalledWithAnyArguments() {
        myFixture.addFileToProject("defs.lua", "---@class H\n---@field cb function\nlocal H = {}\nreturn H\n")
        assertSilent("---@type H\nlocal h\nh.cb(1, 2)\n")
    }

    @Test
    fun testBareFieldOfClassInAnotherFileAcceptsAnyFunction() {
        myFixture.addFileToProject("defs.lua", "---@class H\n---@field cb function\nlocal H = {}\nreturn H\n")
        assertSilent("---@type H\nlocal h\n---@param a string\nh.cb = function(a) end\n")
    }

    @Test
    fun testBareValueMeetsDeclaredCallbackDemand() =
        assertSilent(
            "---@param fn fun(a: string, b: string)\nlocal function take(fn) end\n---@type function\nlocal g\ntake(g)\n",
        )

    @Test
    fun testTypeGuardNarrowingToFunctionMayBeCalledWithAnyArguments() =
        assertSilent(
            "---@param x string|number\nlocal function t(x)\n  if type(x) == \"function\" then\n    x(1, 2)\n  end\nend\n",
        )

    @Test
    fun testTypeGuardSubtractsBareMemberFromUnion() {
        val text =
            "---@param x string|function\nlocal function t(x)\n  if type(x) ~= \"function\" then\n" +
                "    local s = x\n    ---@type string\n    local u = s\n  end\nend\n"
        assertSilent(text)
    }

    // -------------------------------------------------------------------------------------------
    // Controls: every one of these must keep reporting.
    // -------------------------------------------------------------------------------------------

    @Test
    fun testNonFunctionStillFailsBareDemand() =
        assertEquals(listOf("number is not assignable to function"), signatureMessages("${takeBare}take(1)\n"))

    @Test
    fun testRequiredParamStillFailsEmptyFunDemand() =
        assertEquals(
            listOf("Too few arguments: expected at least 1, got 0"),
            signatureMessages("$takeEmptyFun---@param a string\nlocal function f(a) end\ntake(f)\n"),
        )

    @Test
    fun testParameterlessFunctionStillMeetsEmptyFunDemand() =
        assertSilent("${takeEmptyFun}local function f() end\ntake(f)\n")

    @Test
    fun testEmptyFunStillRejectsAnArgument() =
        assertEquals(
            listOf("Too many arguments: expected at most 0, got 1"),
            signatureMessages("---@type fun()\nlocal g\ng(1)\n"),
        )

    @Test
    fun testZeroArgumentCallStillFallsShortOfRequiredParam() =
        assertEquals(
            listOf("Too few arguments: expected at least 1, got 0"),
            signatureMessages("---@param a string\nlocal function f(a) end\nf()\n"),
        )

    @Test
    fun testInferredZeroArgumentDemandStillFallsShortOfRequiredParam() =
        assertEquals(
            listOf("Too few arguments: expected at least 1, got 0"),
            signatureMessages(
                "local function take(fn)\n  fn()\nend\n---@param a string\nlocal function f(a) end\ntake(f)\n",
            ),
        )

    @Test
    fun testDeclaredParameterlessFunctionStillRejectsAnArgumentAsHypothesis() =
        assertEquals(
            listOf("Too many arguments: expected at most 0, got 1"),
            signatureMessages("local function f() end\nf(1)\n"),
        )
}
