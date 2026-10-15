package net.internetisalie.lunar.definitions

import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection

/**
 * BUG-509: a name declared `local` in a file is never a project-wide global, whatever form the later
 * `function` statement takes. The global-declaration index used to sink the root of every dotted
 * `function R.a()` and every bare `function f()` regardless of a `local R` / `local f` in scope, so
 * an undeclared `R` or `f` read in any other file resolved to it and went unreported.
 *
 * Each case puts the declaring file(s) in the project, reads the names from a second file and
 * collects the ones the undeclared-variable inspection reports there.
 */
class LuaLocalNameIsNotGlobalTest : LibraryRootTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    private fun undeclaredWhenReading(
        names: List<String>,
        declaring: Map<String, String>,
    ): List<String> {
        declaring.forEach { (path, text) -> myFixture.addFileToProject(path, text) }
        val reader = names.mapIndexed { index, name -> "local v$index = $name" }.joinToString("\n")
        myFixture.configureByText("user_code.lua", reader)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith(UNDECLARED_PREFIX) }
            .map { it.removePrefix(UNDECLARED_PREFIX).removeSuffix("'") }
    }

    private fun undeclared(
        name: String,
        declaringText: String,
    ): List<String> = undeclaredWhenReading(listOf(name), mapOf("decl.lua" to declaringText))

    fun testBareFunctionAfterLocalIsNotAGlobal() {
        assertEquals(listOf("f1"), undeclared("f1", "local f1\nfunction f1() end"))
    }

    fun testBareFunctionAfterLocalFunctionIsNotAGlobal() {
        assertEquals(listOf("f2"), undeclared("f2", "local function f2() end\nfunction f2() end"))
    }

    fun testDottedFunctionOnLocalTableIsNotAGlobal() {
        assertEquals(listOf("M"), undeclared("M", "local M = {}\nfunction M.x() end\nreturn M"))
    }

    fun testNestedDottedFunctionOnLocalTableIsNotAGlobal() {
        assertEquals(listOf("N"), undeclared("N", "local N = { a = {} }\nfunction N.a.b() end"))
    }

    fun testMethodOnLocalTableIsNotAGlobal() {
        assertEquals(listOf("C"), undeclared("C", "local C = {}\nfunction C:m() end"))
    }

    fun testFunctionInNestedBlockSeesTheEnclosingLocal() {
        assertEquals(listOf("S"), undeclared("S", "local S = {}\nif S then\n  function S.len() end\nend"))
    }

    fun testFunctionParameterIsNotAGlobal() {
        assertEquals(listOf("P"), undeclared("P", "local function g(P)\n  function P.x() end\nend"))
    }

    fun testParameterOfAGlobalFunctionIsNotAGlobal() {
        assertEquals(listOf("P2"), undeclared("P2", "function g2(P2)\n  function P2.x() end\nend"))
    }

    fun testParameterOfAGlobalFunctionBindsABareFunction() {
        assertEquals(listOf("Q2"), undeclared("Q2", "function g3(Q2)\n  function Q2() end\nend"))
    }

    fun testParameterOfAClosureIsNotAGlobal() {
        assertEquals(listOf("P3"), undeclared("P3", "return function(P3)\n  function P3.x() end\nend"))
    }

    fun testSecondNameOfAMultiNameLocalIsNotAGlobal() {
        assertEquals(listOf("M2"), undeclared("M2", "local a, M2 = 1, {}\nfunction M2.x() end"))
    }

    fun testAttributedLocalIsNotAGlobal() {
        assertEquals(listOf("M3"), undeclared("M3", "local M3 <const> = {}\nfunction M3.x() end"))
    }

    fun testLocalFunctionBindsItsOwnNameInsideItsBody() {
        assertEquals(listOf("r"), undeclared("r", "local function r()\n  function r.x() end\nend"))
    }

    fun testGenericForVariableIsNotAGlobal() {
        assertEquals(listOf("K"), undeclared("K", "for K in pairs({}) do\n  function K.x() end\nend"))
    }

    fun testNumericForVariableIsNotAGlobal() {
        assertEquals(listOf("I"), undeclared("I", "for I = 1, 2 do\n  function I.x() end\nend"))
    }

    fun testFunctionBeforeTheLocalIsStillAGlobal() {
        assertEquals(emptyList<String>(), undeclared("early", "function early() end\nlocal early"))
    }

    fun testDottedFunctionBeforeTheLocalIsStillAGlobal() {
        assertEquals(emptyList<String>(), undeclared("E", "function E.x() end\nlocal E = {}"))
    }

    fun testLocalInAnUnrelatedBlockDoesNotHideTheGlobal() {
        assertEquals(emptyList<String>(), undeclared("B", "do\n  local B = {}\nend\nfunction B.x() end"))
    }

    fun testLocalBeingInitialisedDoesNotBindItsOwnInitialiser() {
        assertEquals(emptyList<String>(), undeclared("T", "local T = function()\n  function T.x() end\nend"))
    }

    fun testDottedFunctionWithNoLocalIsAGlobal() {
        assertEquals(emptyList<String>(), undeclared("D", "function D.f() end"))
    }

    fun testBareFunctionWithNoLocalIsAGlobal() {
        assertEquals(emptyList<String>(), undeclared("plain", "function plain() end"))
    }

    fun testGlobalAssignedInAnotherFileIsDeclared() {
        assertEquals(emptyList<String>(), undeclared("G", "G = {}"))
    }

    fun testLibraryRootWithNoLocalStaysAGlobalAndALocalOneDoesNot() {
        registerLibraryRoot(
            mapOf(
                "vim/a.lua" to "function vim.x() end",
                "mod/log.lua" to "local log = {}\nfunction log.f() end\nreturn log",
            ),
        )
        val reported = undeclaredWhenReading(listOf("vim", "log", "zz_never_declared"), emptyMap())
        assertEquals(listOf("log", "zz_never_declared"), reported)
    }

    private companion object {
        const val UNDECLARED_PREFIX = "Undeclared variable '"
    }
}
