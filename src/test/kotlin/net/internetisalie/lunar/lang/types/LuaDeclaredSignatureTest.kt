package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-51: a declared parameter is checked by one rule on every path (the per-call copy, the
 * materialized signature, the in-file function), and a `nil` is judged the same way against a union
 * demand as against a single type. Each test states the errors of `t.lua` against fixed declarations.
 */
@RunWith(JUnit4::class)
class LuaDeclaredSignatureTest : IndexedBasePlatformTestCase() {
    private val defs =
        """
        ---@param a string
        ---@param s? string
        function opt2(a, s) end
        ---@param fn fun()
        function take(fn) end
        ---@param n number
        function takeNumber(n) end
        ---@param v string|number
        function takeSN(v) end
        ---@param xs string[]|number
        function takeArrOrN(xs) end
        ---@class C
        local C = {}
        ---@param a string
        ---@param b? string
        function C:m(a, b) end
        """.trimIndent()

    private fun errors(vararg lines: String): List<String> {
        myFixture.addFileToProject("defs.lua", defs)
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", lines.joinToString("\n"))
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { "${it.severity}:${it.message}@${it.element.text.take(20).replace("\n", " ")}" }
            .distinct()
            .sorted()
    }

    @Test
    fun testTC01PerCallOptionalAcceptsNil() = assertEquals(emptyList<String>(), errors("opt2(\"x\", nil)"))

    @Test
    fun testTC02MaterializedOptionalAcceptsNil() =
        assertEquals(emptyList<String>(), errors("---@type C", "local c", "c:m(\"x\", nil)"))

    @Test
    fun testTC03InFileOptionalAcceptsNil() =
        assertEquals(
            emptyList<String>(),
            errors("---@param a string", "---@param s? string", "local function o(a, s) end", "o(\"x\", nil)"),
        )

    @Test
    fun testTC04PerCallOptionalStillRejectsOthers() =
        assertEquals(
            listOf("ERROR:number is not assignable to union nil | string@1"),
            errors("opt2(\"x\", 1)"),
        )

    @Test
    fun testTC05MaterializedOptionalStillRejectsOthers() =
        assertEquals(
            listOf("ERROR:number is not assignable to union nil | string@1"),
            errors("---@type C", "local c", "c:m(\"x\", 1)"),
        )

    @Test
    fun testTC06NilArmInArrayIsNotEvidenceInFile() =
        assertEquals(
            emptyList<String>(),
            errors(
                "---@return (string|nil)[]",
                "local function arr() end",
                "---@param xs string[]|number",
                "local function h(xs) end",
                "h(arr())",
            ),
        )

    @Test
    fun testTC07NilArmInArrayIsNotEvidencePerCall() =
        assertEquals(
            emptyList<String>(),
            errors("---@return (string|nil)[]", "local function arr() end", "takeArrOrN(arr())"),
        )

    @Test
    fun testTC08NilArmInFieldIsNotEvidence() =
        assertEquals(
            emptyList<String>(),
            errors(
                "---@return {a: string|nil}",
                "local function rec() end",
                "---@param o {a: string}|number",
                "local function h(o) end",
                "h(rec())",
            ),
        )

    @Test
    fun testTC09NonNilMismatchStillReported() =
        assertEquals(
            listOf(
                "ERROR:boolean | string[] is not assignable to union number | string[]@arr()",
                "ERROR:boolean | string[] is not assignable to union number | string[]@local function h(xs)",
                "ERROR:boolean | string[] is not assignable to union number | string[]@xs",
            ),
            errors(
                "---@return (string|boolean)[]",
                "local function arr() end",
                "---@param xs string[]|number",
                "local function h(xs) end",
                "h(arr())",
            ),
        )

    @Test
    fun testTC10UncertainNilInFileUnionDemand() =
        assertEquals(
            emptyList<String>(),
            errors(
                "---@param v string|number",
                "local function k(v) end",
                "local v = nil",
                "if os.time() then v = 1 end",
                "k(v)",
            ),
        )

    @Test
    fun testTC11UncertainNilPerCallUnionDemand() =
        assertEquals(
            emptyList<String>(),
            errors("local v = nil", "if os.time() then v = 1 end", "takeSN(v)"),
        )

    @Test
    fun testTC12CertainNilInFileUnionDemand() =
        assertEquals(
            listOf("ERROR:nil value is not assignable to number | string@v"),
            errors("---@param v string|number", "local function k(v) end", "k(nil)"),
        )

    @Test
    fun testTC13CertainNilPerCallUnionDemand() =
        assertEquals(
            listOf("ERROR:nil value is not assignable to number | string@nil"),
            errors("takeSN(nil)"),
        )

    @Test
    fun testTC14FunctionDemandStaysError() =
        assertEquals(
            listOf("ERROR:number is not assignable to fun()@1"),
            errors("take(1)"),
        )

    @Test
    fun testTC15CertainNilVariableIsError() =
        assertEquals(
            listOf("ERROR:nil value is not assignable to number@z"),
            errors("local z = nil", "takeNumber(z)"),
        )

    @Test
    fun testTC16PassedFunctionKeepsItsOwnSignature() =
        assertEquals(
            listOf("WARNING:Too few arguments: expected at least 1, got 0@f"),
            errors("---@param a string", "local function f(a) end", "take(f)"),
        )
}
