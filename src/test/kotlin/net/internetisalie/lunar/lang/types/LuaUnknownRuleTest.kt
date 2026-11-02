package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** MAINT-52: an unknown type behaves the same in a union and in a demand. */
@RunWith(JUnit4::class)
class LuaUnknownRuleTest : BasePlatformTestCase() {
    private fun errors(
        source: String,
        library: String? = null,
    ): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        library?.let { myFixture.addFileToProject("lib.lua", it) }
        val tFile = myFixture.addFileToProject("t.lua", source)
        myFixture.configureFromExistingVirtualFile(tFile.virtualFile)
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { it.message }
            .distinct()
            .sorted()
    }

    private fun typeOf(name: String): String {
        val nameRef =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
                .flatMap { it.attNameList }
                .first { it.nameRef.text == name }
                .nameRef
        return LuaTypesSnapshot.forFile(myFixture.file as LuaFile).getValueType(nameRef).displayName()
    }

    @Test
    fun testTC02UnionWithUnresolvedMemberAcceptsScalar() {
        assertEquals(emptyList<String>(), errors("---@param o string|Unres\nlocal function f(o) end\nf(1)\n"))
    }

    @Test
    fun testTC03OptionalUnionWithUnresolvedMemberAcceptsTable() {
        val source =
            "---@param o? Unres.ns | {buffer: boolean}\nlocal function f(o) end\nf({ pattern = \"x\" })\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testTC04UnionWithUnknownMemberAcceptsTable() {
        val source = "---@param o unknown | {buffer: boolean}\nlocal function f(o) end\nf({ pattern = \"x\" })\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testTC05CrossFileOptionalUnresolvedParam() {
        val library = "---@param x? Unres\nfunction take04(x) end\n"
        assertEquals(emptyList<String>(), errors("take04({})\ntake04(1)\ntake04(nil)\n", library))
    }

    @Test
    fun testTC07OrAndAndKeepTheUnknownArm() {
        val source =
            "local d = wx.thing or \"s\"\nlocal m = wx.thing and {a = 1}\n---@param n number\n" +
                "local function g(n) end\ng(d)\n"
        assertEquals(emptyList<String>(), errors(source))
        assertEquals("any", typeOf("d"))
        assertEquals("any | { ... }", typeOf("m"))
    }

    @Test
    fun testTC08UnmodelledCallOrDefaultIsUnknown() {
        val source = "local q = a:b():c() or \"s\"\n---@param n number\nlocal function g(n) end\ng(q)\n"
        assertEquals(emptyList<String>(), errors(source))
        assertEquals("any", typeOf("q"))
    }

    @Test
    fun testTC09AnyFieldIsOptional() {
        val source =
            "---@class C07\n---@field a any\n---@field b string\n\n---@param c C07\n" +
                "local function f(c) end\nf({ b = \"x\" })\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testTC10KnownFieldStillRequired() {
        val source =
            "---@class C19\n---@field a string\n---@field b string\n\n---@param c C19\n" +
                "local function f(c) end\nf({ b = \"x\" })\n"
        assertEquals(listOf("Missing required field 'a'"), errors(source))
    }

    @Test
    fun testTC11ResolvedOptionalUnionStillReports() {
        val source =
            "---@param o? string | {buffer: boolean}\nlocal function f(o) end\nf({ pattern = \"x\" })\n"
        assertEquals(
            listOf("{ ... } is not assignable to union nil | string | { ... }"),
            errors(source),
        )
    }

    @Test
    fun testTC12ResolvedOptionalParamStillReports() {
        val library = "---@param x? string\nfunction take18(x) end\n"
        assertEquals(
            listOf("number is not assignable to union nil | string"),
            errors("take18(1)\ntake18(nil)\n", library),
        )
    }

    @Test
    fun testTC14TypeAnnotationsWithUnknownAreUnknown() {
        val source =
            "---@type unknown\nlocal v = 1\n---@type Unres\nlocal w\n---@type Unres|string\nlocal z = 1\n" +
                "---@param n number\nlocal function g(n) end\ng(w)\ng(z)\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testTC15OptionalUnknownParamInFileDemandsNoNil() {
        val source =
            "---@param o? unknown\nlocal function f(o) end\nf(1)\nf({})\nf(nil)\n" +
                "---@param p? unknown\nfunction g27(p) end\ng27(1)\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testTC16AllUnresolvedDeclaredUnionIsAny() {
        val source =
            "---@type Unres1|Unres2\nlocal w2\n---@type Unres1|Unres2\nlocal z2 = 1\n" +
                "---@param n number\nlocal function g(n) end\ng(w2)\ng(z2)\n"
        assertEquals(emptyList<String>(), errors(source))
        assertEquals("any", typeOf("w2"))
    }

    @Test
    fun testTC17AllUnresolvedReturnUnionIsAny() {
        val source =
            "---@return Unres1|Unres2\nlocal function h() end\nlocal r = h()\n" +
                "---@param n number\nlocal function g(n) end\ng(r)\n"
        assertEquals(emptyList<String>(), errors(source))
        assertEquals("any", typeOf("r"))
    }

    @Test
    fun testTC18ClosestMatchIgnoresAnyField() {
        val source =
            "---@class E1A\n---@field x number\n---@field a any\nlocal E1A = {}\n" +
                "---@class E1B\n---@field y string\nlocal E1B = {}\n" +
                "---@param o E1A|E1B\nlocal function f(o) end\nf({ x = \"s\" })\n"
        assertEquals(
            listOf("{ ... } is not assignable to E1A | E1B; closest match 'E1A': incompatible fields"),
            errors(source),
        )
    }
}
