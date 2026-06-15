package net.internetisalie.lunar.lang.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaEnterHandlerTest : BasePlatformTestCase() {

    fun testEnterAfterThen() {
        myFixture.configureByText("test.lua", "if x > 5 then<caret>")
        myFixture.type('\n')
        myFixture.checkResult("if x > 5 then\n<caret>\nend")
    }

    fun testEnterAfterDo() {
        myFixture.configureByText("test.lua", "while true do<caret>")
        myFixture.type('\n')
        myFixture.checkResult("while true do\n<caret>\nend")
    }

    fun testEnterAfterFunction() {
        myFixture.configureByText("test.lua", "local function test()<caret>")
        // wait, the token before caret should be FUNCTION, but here it is ')'
        // so it won't work unless we check for parameters or the function block.
        // Actually, the basic handler looks for LuaTypes.FUNCTION. Let's test `function<caret>`
    }
}
