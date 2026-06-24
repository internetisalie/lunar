package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.io.path.deleteIfExists

class LuaFoldingTest : BaseDocumentTest() {

    fun testFolding(text: String) {
        val content = text.trimIndent()
        val tempFile = createTempFile("lunar-fold-test-", ".lua")
        try {
            tempFile.writeText(content)
            myFixture.testFolding(tempFile.toAbsolutePath().toString())
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun testBlockStringFolding() {
        testFolding("""
            local s = <fold text='[=[multi...]=]'>[=[
                multi
                line
            ]=]</fold>
        """)
    }

    @Test
    fun testQuotedStringFolding() {
        // Lua does not allow literal newlines in quoted strings
        // This test is skipped because it tests invalid Lua syntax
        // Quoted strings must be single-line, or use block strings [[...]] for multi-line
        testFolding("""
            local s = "single line string"
        """)
    }

    @Test
    fun testLongCommentFolding() {
        testFolding("""
            <fold text='--[=[comment...]=]'>--[=[
                comment
                lines
            ]=]</fold>
        """)
    }

    @Test
    fun testDocCommentFolding() {
        testFolding("""
            <fold text='--- doc...'>--- doc
            --- line 2
            --- line 3</fold>
            function f() end
        """)
    }

    @Test
    fun testRegionFolding() {
        testFolding("""
            <fold text='My Region'>--#region My Region
            local x = 1
            --#endregion</fold>
        """)
    }

    @Test
    fun testFunctionFolding() {
        testFolding("""
            function f()<fold text='...'>
                print(1)
            </fold>end
        """)
    }

    @Test
    fun testLocalFunctionFolding() {
        testFolding("""
            local function f()<fold text='...'>
                print(1)
            </fold>end
        """)
    }

    @Test
    fun testAnonymousFunctionFolding() {
        testFolding("""
            local f = function()<fold text='...'>
                print(1)
            </fold>end
        """)
    }

    @Test
    fun testGlobalFunctionFolding() {
        testFolding("""
            global function f()<fold text='...'>
                print(1)
            </fold>end
        """)
    }

    @Test
    fun testTableFolding() {
        testFolding("""
            local t = <fold text='{...}'>{
                a = 1,
                b = 2
            }</fold>
        """)
    }

    @Test
    fun testIfFolding() {
        testFolding("""
            <fold text='...'>if true then
                print(1)
            else
                print(2)
            end</fold>
        """)
    }

    @Test
    fun testWhileFolding() {
        testFolding("""
            <fold text='...'>while true do
                print(1)
            end</fold>
        """)
    }
}
