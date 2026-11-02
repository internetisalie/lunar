package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-14 Phase 1: a `@generic` name parses in every position of a type expression, and a symbolic
 * parameter that reaches the checker never reports. Cases 1-4 of the feature's requirements.
 */
@RunWith(JUnit4::class)
class Type14GenericParsingTest : IndexedBasePlatformTestCase() {
    private fun nameOf(
        file: PsiFile,
        variable: String,
    ): PsiElement =
        PsiTreeUtil
            .findChildrenOfType(file, LuaLocalVarDecl::class.java)
            .first { it.text.startsWith("local $variable") }
            .attNameList
            .first()
            .nameRef

    private fun genericMentions(file: PsiFile): List<String> =
        LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .map { it.message }
            .filter { Regex("\\bT\\b").containsMatchIn(it) }

    @Test
    fun testCase1NestedGenericParsesInArrayElement() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param xs T[]
                ---@return T
                local function first(xs) return xs[1] end
                local f = first
                """.trimIndent(),
            )
        val function = LuaTypesSnapshot.forFile(file).getValueType(nameOf(file, "f")) as LuaGraphType.Function
        val written =
            function.params
                .single()
                .node.write
        assertTrue("expected an array, got ${written.displayName()}", written is LuaGraphType.Array)
        assertEquals(LuaGraphType.Generic("T"), (written as LuaGraphType.Array).elementType)
    }

    @Test
    fun testCase2GenericParameterShadowsClassOfTheSameName() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class T
                local T = {}

                ---@generic T
                ---@param xs T[]
                ---@return T
                local function first(xs) return xs[1] end
                local f = first
                """.trimIndent(),
            )
        val function = LuaTypesSnapshot.forFile(file).getValueType(nameOf(file, "f")) as LuaGraphType.Function
        val written =
            function.params
                .single()
                .node.write
        assertTrue("expected an array, got ${written.displayName()}", written is LuaGraphType.Array)
        assertEquals(LuaGraphType.Generic("T"), (written as LuaGraphType.Array).elementType)
    }

    @Test
    fun testCase3CrossFileGenericCalleeReportsNothing() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@generic T
            ---@param v T
            ---@return T
            function ident(v) return v end
            """.trimIndent(),
        )
        val file =
            myFixture.configureByText(
                "c.lua",
                """
                local s = ident("x")
                local n = ident(1)
                """.trimIndent(),
            )
        assertEquals(emptyList<String>(), genericMentions(file))
    }

    @Test
    fun testCase4UnionWithNilParameterReportsNothing() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@generic T
                ---@param v T|nil
                ---@return T
                local function must(v) return v end
                local m = must("q")
                """.trimIndent(),
            )
        assertEquals(emptyList<String>(), genericMentions(file))
    }
}
