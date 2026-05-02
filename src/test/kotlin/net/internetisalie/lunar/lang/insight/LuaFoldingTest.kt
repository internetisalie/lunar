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
}
