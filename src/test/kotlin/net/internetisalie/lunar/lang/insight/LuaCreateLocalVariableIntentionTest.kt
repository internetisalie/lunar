package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaCreateLocalVariableIntentionTest : BasePlatformTestCase() {

    // TC1: simple write target → local declaration.
    @Test
    fun `test create local on simple write target`() {
        myFixture.configureByText("test.lua", "x<caret> = 1")
        val intention = myFixture.findSingleIntention("Create local variable 'x'")
        myFixture.launchAction(intention)
        myFixture.checkResult("local x = 1")
    }

    // TC2: read use → not offered.
    @Test
    fun `test create local not offered on read`() {
        myFixture.configureByText("test.lua", "print(y<caret>)")
        assertEmpty(myFixture.filterAvailableIntentions("Create local variable 'y'"))
    }

    // TC3: already-declared name → not offered.
    @Test
    fun `test create local not offered when already declared`() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = 0
            x<caret> = 1
            """.trimIndent(),
        )
        assertEmpty(myFixture.filterAvailableIntentions("Create local variable 'x'"))
    }

    // Parity guard: a known standard global is never offered create-local.
    @Test
    fun `test create local not offered on standard global write`() {
        myFixture.configureByText("test.lua", "print<caret> = 1")
        assertEmpty(myFixture.filterAvailableIntentions("Create local variable 'print'"))
    }
}
