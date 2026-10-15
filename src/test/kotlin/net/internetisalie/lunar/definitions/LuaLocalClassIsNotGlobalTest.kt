package net.internetisalie.lunar.definitions

import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection

/**
 * BUG-510: a name declared `local` in one file is never a variable in another.
 *
 * A `---@class K` (or `---@alias K`) tag on `local K = {}` makes `K` a TYPE name usable anywhere,
 * but the VALUE `K` stays file-local. Bare-name resolution used to consult the class- and
 * alias-name stub indexes, whose every entry is a `LuaLocalVarDecl`, so any such local resolved as
 * a variable in every file of the project and its libraries.
 *
 * A library root is used because a light fixture's files are all project content; the defect
 * reached library tables (Neovim's `uv`, openresty's `cjson`) as well as project ones.
 */
class LuaLocalClassIsNotGlobalTest : LibraryRootTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    fun testALocalClassTableIsNotAGlobal() {
        registerLibraryRoot(mapOf("mod/iter.lua" to "---@class Iter\nlocal Iter = {}\nreturn Iter\n"))
        myFixture.addFileToProject("cls.lua", "---@class K\nlocal K = {}\nreturn K\n")

        val warnings = undeclaredIn("local a = K\nlocal b = Iter\n")

        assertTrue("project-local class table: $warnings", warnings.contains("Undeclared variable 'K'"))
        assertTrue("library-local class table: $warnings", warnings.contains("Undeclared variable 'Iter'"))
    }

    fun testALocalAliasHostIsNotAGlobal() {
        myFixture.addFileToProject("modes.lua", "---@alias Mode string\nlocal Mode = {}\nreturn Mode\n")

        val warnings = undeclaredIn("local m = Mode\n")

        assertEquals(listOf("Undeclared variable 'Mode'"), warnings)
    }

    fun testAGlobalClassTableStaysDeclared() {
        myFixture.addFileToProject("gcls.lua", "---@class C\nC = {}\n")

        val warnings = undeclaredIn("local c = C\n")

        assertEquals(emptyList<String>(), warnings)
    }

    fun testALocalClassStaysATypeInAnotherFile() {
        myFixture.addFileToProject("cls.lua", "---@class K\n---@field name string\nlocal K = {}\nreturn K\n")

        assertTrue("---@type K", completionsFor("---@type K\nlocal p\np.<caret>\n").contains("name"))
        assertTrue(
            "---@param x K",
            completionsFor("---@param x K\nlocal function f(x)\n  return x.<caret>\nend\n").contains("name"),
        )
    }

    private fun undeclaredIn(text: String): List<String> {
        myFixture.configureByText("user_code.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Undeclared variable '") }
    }
}
