package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-14 Phase 3: a `T : Base` bound is checked at the call exactly as a plain `---@param p Base` is
 * checked, it is usable in the body, and a `` `T` `` parameter binds the class its string argument
 * names. Cases 19-21 of the feature's requirements.
 */
@RunWith(JUnit4::class)
class Type14ConstraintTest : IndexedBasePlatformTestCase() {
    private fun nameOf(
        file: PsiFile,
        variable: String,
    ): PsiElement =
        PsiTreeUtil
            .findChildrenOfType(file, LuaLocalVarDecl::class.java)
            .first { it.text.startsWith("local $variable ") || it.text == "local $variable" }
            .attNameList
            .first()
            .nameRef

    private fun typeOf(
        file: PsiFile,
        variable: String,
    ): String = LuaTypesSnapshot.forFile(file).getValueType(nameOf(file, variable)).displayName()

    /**
     * Every error as message, anchor text and severity, sorted: discovery order is not a contract, but an
     * extra, a duplicate or a moved report still changes the list.
     */
    private fun errorsOf(file: PsiFile): List<String> =
        LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .map { "${it.message} @ ${it.element.text} [${it.severity}]" }
            .sorted()

    private fun errorsFor(
        fileName: String,
        text: String,
    ): List<String> = errorsOf(myFixture.configureByText(fileName, text))

    @Test
    fun testCase19aPrimitiveBoundReportsAsAPlainParameterDoes() {
        val plain = errorsFor("plain.lua", "---@param p string\nlocal function f(p) end\nf(1)\nf(\"s\")\n")
        val bounded =
            errorsFor("bounded.lua", "---@generic T : string\n---@param p T\nlocal function f(p) end\nf(1)\nf(\"s\")\n")
        assertEquals(
            listOf(
                "$PRIMITIVE_ERROR @ local function f(p) end [ERROR]",
                "$PRIMITIVE_ERROR @ p [ERROR]",
            ),
            plain,
        )
        assertEquals(plain, bounded)
    }

    @Test
    fun testCase19bClassBoundReportsAMissingFieldAsAPlainParameterDoes() {
        val plain = errorsFor("plain.lua", "$BASE---@param p Base\nlocal function f(p) end\nf({})\n")
        val bounded =
            errorsFor("bounded.lua", "$BASE---@generic T : Base\n---@param p T\nlocal function f(p) end\nf({})\n")
        assertEquals(listOf("Missing required field 'k' @ {} [ERROR]"), plain)
        assertEquals(plain, bounded)
    }

    /** The checker accepts a primitive against a class demand (metatables); a bound keeps that parity. */
    @Test
    fun testCase19cClassBoundAcceptsAPrimitiveAsAPlainParameterDoes() {
        val plain = errorsFor("plain.lua", "$BASE---@param p Base\nlocal function f(p) end\nf(1)\n")
        val bounded =
            errorsFor(
                "bounded.lua",
                "$BASE---@generic T : Base\n---@param p T\nlocal function f(p) end\nf(1)\n---@type Base\nlocal base\nf(base)\n",
            )
        assertEquals(emptyList<String>(), plain)
        assertEquals(emptyList<String>(), bounded)
    }

    @Test
    fun testBoundMentionedTwiceChecksEachArgumentOnce() {
        val plain =
            errorsFor(
                "plain.lua",
                "---@param a string\n---@param b string\nlocal function f(a, b) end\nf(\"s\", 1)\n",
            )
        val file =
            myFixture.configureByText(
                "bounded.lua",
                "---@generic T : string\n---@param a T\n---@param b T\n---@return T\n" +
                    "local function f(a, b) end\nlocal r = f(\"s\", 1)\n",
            )
        assertEquals(
            listOf("$PRIMITIVE_ERROR @ b [ERROR]", "$PRIMITIVE_ERROR @ local function f(a, b) end [ERROR]"),
            plain,
        )
        assertEquals(plain, errorsOf(file))
        assertEquals(setOf("number", "string"), typeOf(file, "r").split(" | ").toSet())
    }

    @Test
    fun testDeclaredBoundReportsOnceOnTheArgument() {
        myFixture.addFileToProject(
            "lib.lua",
            "---@generic T : string\n---@param p T\n---@return T\nfunction sf(p) end\n" +
                "---@param p string\nfunction pf(p) end\n",
        )
        val plain = errorsFor("plain.lua", "pf(1)\n")
        val file = myFixture.configureByText("bounded.lua", "local r = sf(1)\nlocal s = sf(\"s\")\n")
        assertEquals(listOf("$PRIMITIVE_ERROR @ 1 [ERROR]"), plain)
        assertEquals(plain, errorsOf(file))
        assertEquals("string", typeOf(file, "s"))
    }

    @Test
    fun testDeclaredClassBoundReportsAsAPlainDeclaredParameterDoes() {
        myFixture.addFileToProject(
            "lib.lua",
            "$BASE---@generic T : Base\n---@param p T\nfunction gf(p) end\n---@param p Base\nfunction pf(p) end\n",
        )
        val plain = errorsFor("plain.lua", "pf({})\npf(1)\n")
        val bounded = errorsFor("bounded.lua", "gf({})\ngf(1)\n")
        assertEquals(plain, bounded)
    }

    /** BUG-19: a declared class bound is the seed's own table; no call may write into its members. */
    @Test
    fun testDeclaredClassBoundLeavesTheSeedsMembersUnchanged() {
        myFixture.addFileToProject("lib.lua", "$BASE---@generic T : Base\n---@param p T\nfunction gf(p) end\n")
        val file = myFixture.configureByText("main.lua", "gf({ k = 1 })\ngf({ k = true })\n")
        val seedReference =
            PsiTreeUtil
                .findChildrenOfType(file, LuaNameRef::class.java)
                .last { it.text == "gf" }
        val seed = LuaTypesSnapshot.forFile(file).getValueType(seedReference) as LuaGraphType.Function
        val bound =
            (
                seed.params
                    .single()
                    .node.write as LuaGraphType.Generic
            ).bound
        assertNotNull("the declared signature lost its bound", bound)
        assertEquals(
            "string",
            bound
                ?.getMembers()
                ?.get("k")
                ?.write
                ?.displayName(),
        )
    }

    @Test
    fun testCase20BoundIsUsableInTheBody() {
        val file =
            myFixture.configureByText(
                "test.lua",
                "$BASE---@generic T : Base\n---@param p T\nlocal function f(p)\n  local m = p.k\nend\n",
            )
        assertEquals("string", typeOf(file, "m"))
        assertEquals(emptyList<String>(), errorsOf(file))
    }

    @Test
    fun testCase20CompletionOnABoundedParameterOffersTheBoundsMembers() {
        myFixture.configureByText(
            "test.lua",
            "$BASE---@generic T : Base\n---@param p T\nlocal function f(p)\n  return p.<caret>\nend\n",
        )
        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings
        assertNotNull("completion returned no lookup", offered)
        assertTrue("expected k, got $offered", offered.orEmpty().contains("k"))
    }

    @Test
    fun testCase21BacktickCaptureBindsTheNamedClass() {
        val file =
            myFixture.configureByText(
                "test.lua",
                "$WIDGET---@generic T\n---@param cls `T`\n---@return T\nlocal function new(cls) end\n" +
                    "local w = new(\"Widget\")\nlocal u = new(name)\n",
            )
        assertEquals("Widget", typeOf(file, "w"))
        assertEquals("undefined", typeOf(file, "u"))
        assertEquals(emptyList<String>(), errorsOf(file))
    }

    @Test
    fun testCaptureMentionedTwiceReadsTheSameClass() {
        val file =
            myFixture.configureByText(
                "test.lua",
                "$WIDGET---@generic T\n---@param cls `T`\n---@return T|T[]\nlocal function new(cls) end\n" +
                    "local w = new('Widget')\n",
            )
        assertEquals(setOf("Widget", "Widget[]"), typeOf(file, "w").split(" | ").toSet())
    }

    @Test
    fun testCaptureOnADeclaredCallee() {
        myFixture.addFileToProject(
            "lib.lua",
            "$WIDGET---@generic T\n---@param cls `T`\n---@return T\nfunction make(cls) end\n",
        )
        val file = myFixture.configureByText("main.lua", "local w = make(\"Widget\")\nlocal u = make(name)\n")
        assertEquals("Widget", typeOf(file, "w"))
        assertEquals("undefined", typeOf(file, "u"))
    }

    private companion object {
        const val PRIMITIVE_ERROR = "number is not assignable to string"
        const val BASE = "---@class Base\n---@field k string\nlocal Base = {}\n"
        const val WIDGET = "---@class Widget\n---@field w number\nlocal Widget = {}\n"
    }
}
