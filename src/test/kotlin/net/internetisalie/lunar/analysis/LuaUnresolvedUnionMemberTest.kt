package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-16. A union member that does not resolve could be anything, so the union cannot rule an
 * argument out. The resolved-union rows are the control: they must keep reporting.
 */
@RunWith(JUnit4::class)
class LuaUnresolvedUnionMemberTest : BasePlatformTestCase() {
    private fun errors(source: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", source)
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { it.message }
            .distinct()
    }

    private fun call(paramTag: String) = "$paramTag\nlocal function f(opts) end\nf({ pattern = \"x\" })\n"

    @Test
    fun testOptionalUnionWithUnresolvedMemberAcceptsATable() =
        assertEquals(emptyList<String>(), errors(call("---@param opts? Unres.ns | {buffer: boolean}")))

    @Test
    fun testRequiredUnionWithUnresolvedMemberAcceptsATable() =
        assertEquals(emptyList<String>(), errors(call("---@param opts Unres.ns | {buffer: boolean}")))

    @Test
    fun testUnresolvedMemberBesideAScalarAcceptsATable() =
        assertEquals(emptyList<String>(), errors(call("---@param opts Unres.ns | string")))

    @Test
    fun testOptionalUnionOfResolvedMembersStillReports() =
        assertTrue(errors(call("---@param opts? string | {buffer: boolean}")).isNotEmpty())

    @Test
    fun testUnionOfScalarsStillReports() = assertTrue(errors(call("---@param opts string | number")).isNotEmpty())
}
