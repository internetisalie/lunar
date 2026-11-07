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
 * TYPE-14 Phase 2: a generic callee binds its parameters through their structure and rebuilds its
 * returns at the same depth, per call site, on both the same-file and the declared call path.
 * Cases 5-11 of the feature's requirements; case 9 (generic methods) is Phase 5's.
 */
@RunWith(JUnit4::class)
class Type14InstantiationTest : IndexedBasePlatformTestCase() {
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

    private fun errorsOf(file: PsiFile): List<String> = LuaTypesSnapshot.forFile(file).getErrors().map { it.message }

    private fun addIdentLibrary() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@generic T
            ---@param v T
            ---@return T
            function ident(v) return v end
            """.trimIndent(),
        )
    }

    @Test
    fun testCase5ArrayParameterBindsElementType() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param xs T[]
                ---@return T
                local function first(xs) return xs[1] end
                ---@type string[]
                local xs
                local a = first(xs)
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "a"))
    }

    @Test
    fun testCase6ArrayReturnIsRebuiltWithTheBinding() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T
                ---@return T[]
                local function wrap(v) end
                local w = wrap(1)
                """.trimIndent(),
            )
        assertEquals("number[]", typeOf(file, "w"))
    }

    @Test
    fun testCase7TableTypeArgumentBindsValueType() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic K, V
                ---@param t table<K, V>
                ---@return V
                local function get(t) end
                ---@type table<string, number>
                local t
                local v = get(t)
                """.trimIndent(),
            )
        assertEquals("number", typeOf(file, "v"))
    }

    @Test
    fun testCase8EachCallSiteBindsIndependently() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T
                ---@return T
                local function ident(v) return v end
                local s = ident("x")
                local n = ident(1)
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "s"))
        assertEquals("number", typeOf(file, "n"))
    }

    /** Case 9 (TYPE-14-04): a generic method instantiates per call, through an instance and through the class. */
    @Test
    fun testCase9GenericMethodInstantiatesPerCall() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class Box
                local Box = {}
                ---@generic T
                ---@param v T
                ---@return T
                function Box:echo(v) return v end
                ---@type Box
                local b
                local r = b:echo("x")
                local q = Box:echo(1)
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "r"))
        assertEquals("number", typeOf(file, "q"))
        assertEquals("[]", errorsOf(file).toString())
    }

    /**
     * Risks-and-gaps Gap 2.9: a generic method's class member is not checked against the method
     * itself in its own file, whatever order its `@param` tags are written in (lazynvim's `filter`).
     */
    @Test
    fun testGenericMethodSignatureRaisesNothingInItsOwnFile() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class Lst
                local M = {}
                ---@generic T
                ---@param list T[]
                ---@param fn fun(v: T):boolean?
                ---@return T[]
                function M.filter(fn, list) return list end
                ---@generic T
                ---@param list T[]
                ---@param fn fun(v: T):boolean?
                ---@return T[]
                function M.keep(list, fn) return list end
                return M
                """.trimIndent(),
            )
        assertEquals("[]", errorsOf(file).toString())
    }

    @Test
    fun testCase10CrossFileGenericCalleeInstantiatesPerCall() {
        addIdentLibrary()
        val file =
            myFixture.configureByText(
                "main.lua",
                """
                local s = ident("x")
                local n = ident(1)
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "s"))
        assertEquals("number", typeOf(file, "n"))
        assertEquals(emptyList<String>(), errorsOf(file))
    }

    @Test
    fun testCase11NoCallSiteWritesIntoTheDeclaredSeed() {
        addIdentLibrary()
        val file =
            myFixture.configureByText(
                "main.lua",
                """
                local s = ident("x")
                local n = ident(1)
                local b = ident(true)
                """.trimIndent(),
            )
        val seedReference =
            PsiTreeUtil
                .findChildrenOfType(file, LuaNameRef::class.java)
                .last { it.text == "ident" }
        val seed = LuaTypesSnapshot.forFile(file).getValueType(seedReference)
        assertTrue("expected the seed's signature, got ${seed.displayName()}", seed is LuaGraphType.Function)
        val parameterWrite =
            (seed as LuaGraphType.Function)
                .params
                .single()
                .node.write
        assertEquals(LuaGraphType.Generic("T"), parameterWrite)
        assertEquals("boolean", typeOf(file, "b"))
    }

    /** A union display, order-free: the checker canonicalises arms, the test names a set. */
    private fun armsOf(display: String): Set<String> = display.split(" | ").toSet()

    @Test
    fun testRepeatedNameInAReturnReadsTheSameBindingTwice() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T
                ---@return T|T[]
                local function both(v) end
                local u = both(1)
                """.trimIndent(),
            )
        assertEquals(setOf("number", "number[]"), armsOf(typeOf(file, "u")))
    }

    @Test
    fun testRepeatedNameInTypeArgumentsReadsTheSameBindingTwice() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T
                ---@return table<T, T>
                local function pair(v) end
                local t = pair(1)
                """.trimIndent(),
            )
        assertEquals("table<number, number>", typeOf(file, "t"))
    }

    @Test
    fun testFunctionParameterPositionBinds() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param fn fun(a: T)
                ---@return T
                local function take(fn) end
                ---@type fun(a: number)
                local cb
                local r = take(cb)
                """.trimIndent(),
            )
        assertEquals("number", typeOf(file, "r"))
    }

    @Test
    fun testFunctionReturnPositionBinds() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param fn fun(): T
                ---@return T
                local function call(fn) end
                ---@type fun(): string
                local make
                local r = call(make)
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "r"))
    }

    @Test
    fun testTableMemberPositionBinds() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param box { value: T }
                ---@return T
                local function unbox(box) end
                local r = unbox({ value = 1 })
                """.trimIndent(),
            )
        assertEquals("number", typeOf(file, "r"))
    }

    @Test
    fun testUnionArmPositionBindsTheNonNilValue() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T|nil
                ---@return T
                local function must(v) end
                local m = must("q")
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "m"))
    }

    @Test
    fun testGenericPositionStillChecksItsShapeSameFile() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param xs T[]
                ---@return T
                local function first(xs) end
                local a = first(5)
                """.trimIndent(),
            )
        assertTrue("expected the declared shape error, got ${errorsOf(file)}", SHAPE_ERROR in errorsOf(file))
    }

    @Test
    fun testGenericPositionStillChecksItsShapeDeclared() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@generic T
            ---@param xs T[]
            ---@return T
            function first(xs) end
            """.trimIndent(),
        )
        val file = myFixture.configureByText("main.lua", "local a = first(5)")
        val errors = LuaTypesSnapshot.forFile(file).getErrors().map { it.message to it.element.text }
        assertEquals(listOf(SHAPE_ERROR to "5"), errors)
    }

    private companion object {
        /** What base e8fc4248 reports for `first(5)` against `---@param xs T[]`, on both call paths. */
        const val SHAPE_ERROR = "number is not assignable to T[]"
    }
}
