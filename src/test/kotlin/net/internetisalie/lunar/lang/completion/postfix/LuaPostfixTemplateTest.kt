package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaPostfixTemplateTest : BasePlatformTestCase() {

    fun testIfPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = 10
            x > 5.if<caret>
            """.trimIndent()
        )
        
        myFixture.type("\t") // Trigger postfix template expansion

        // Body indentation is applied by the formatter in the real IDE; the headless template
        // harness (setTemplateTesting) does not reformat, so the caret line is left unindented.
        myFixture.checkResult(
            """
            local x = 10
            if x > 5 then
            <caret>
            end
            """.trimIndent()
        )
    }
}
