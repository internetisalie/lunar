package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaCreateFunctionIntentionTest : BasePlatformTestCase() {

    // TC4: two positional args → 2-param stub inserted above.
    @Test
    fun `test create function with two args`() {
        myFixture.configureByText("test.lua", "myFunc<caret>(1, 2)")
        val intention = myFixture.findSingleIntention("Create function 'myFunc'")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            local function myFunc(arg1, arg2)
            end

            myFunc(1, 2)
            """.trimIndent(),
        )
    }

    // TC5: zero args → 0-param stub.
    @Test
    fun `test create function with zero args`() {
        myFixture.configureByText("test.lua", "f<caret>()")
        val intention = myFixture.findSingleIntention("Create function 'f'")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            local function f()
            end

            f()
            """.trimIndent(),
        )
    }

    // Risk 1.4: call nested in an expression → stub above the enclosing statement.
    @Test
    fun `test create function for call nested in expression`() {
        myFixture.configureByText("test.lua", "local v = myFunc<caret>(1)")
        val intention = myFixture.findSingleIntention("Create function 'myFunc'")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            local function myFunc(arg1)
            end

            local v = myFunc(1)
            """.trimIndent(),
        )
    }

    // TC6: already-declared callee → not offered.
    @Test
    fun `test create function not offered when already declared`() {
        myFixture.configureByText(
            "test.lua",
            """
            local function f() end
            f<caret>()
            """.trimIndent(),
        )
        assertEmpty(myFixture.filterAvailableIntentions("Create function 'f'"))
    }

    // TC7: member-access callee → not offered.
    @Test
    fun `test create function not offered on member access`() {
        myFixture.configureByText("test.lua", "obj.method<caret>(1)")
        assertEmpty(myFixture.filterAvailableIntentions("Create function 'method'"))
    }
}
