package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test

class LuaFoldingTest : BaseDocumentTest() {

    @Test
    fun testBlockStringFolding() {
        myFixture.configureByText("test.lua", """
            local s = <fold text='[=[multi...]=]'>[=[
                multi
                line
            ]=]</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testQuotedStringFolding() {
        myFixture.configureByText("test.lua", """
            local s = <fold text='"multi..."'>"
                multi
                line
            "</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testLongCommentFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='--[=[comment...]=]'>--[=[
                comment
                lines
            ]=]</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testDocCommentFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='--- doc...'>--- doc
            --- line 2
            --- line 3</fold>
            function f() end
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testRegionFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='My Region'>--#region My Region
            local x = 1
            --#endregion</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testFunctionFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='...'>function f()
                print(1)
            end</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testTableFolding() {
        myFixture.configureByText("test.lua", """
            local t = <fold text='{...}'>{
                a = 1,
                b = 2
            }</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testIfFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='...'>if true then
                print(1)
            else
                print(2)
            end</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }

    @Test
    fun testWhileFolding() {
        myFixture.configureByText("test.lua", """
            <fold text='...'>while true do
                print(1)
            end</fold>
        """.trimIndent())
        myFixture.testFolding("test.lua")
    }
}
