package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaInvertIfIntentionTest : BasePlatformTestCase() {

    private val actionText = "Invert 'if' statement"

    private fun invert(input: String, expected: String) {
        myFixture.configureByText("test.lua", input)
        val intention = myFixture.findSingleIntention(actionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(expected)
    }

    @Test
    fun `test invert relational eq and swap`() {
        invert(
            """
            if x<caret> == 1 then
                foo()
            else
                bar()
            end
            """.trimIndent(),
            """
            if x ~= 1 then
                bar()
            else
                foo()
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `test unwrap not condition`() {
        invert(
            """
            if not<caret> ready then
                wait()
            else
                proceed()
            end
            """.trimIndent(),
            """
            if ready then
                proceed()
            else
                wait()
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `test wrap non relational condition`() {
        invert(
            """
            if is<caret>Valid() then
                accept()
            else
                reject()
            end
            """.trimIndent(),
            """
            if not (isValid()) then
                reject()
            else
                accept()
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `test wrap and chain no de morgan`() {
        invert(
            """
            if a<caret> and b then
                first()
            else
                second()
            end
            """.trimIndent(),
            """
            if not (a and b) then
                second()
            else
                first()
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `test flip less than to ge`() {
        invert(
            """
            if x<caret> < 10 then
                low()
            else
                high()
            end
            """.trimIndent(),
            """
            if x >= 10 then
                high()
            else
                low()
            end
            """.trimIndent(),
        )
    }

    @Test
    fun `test elseif not offered`() {
        myFixture.configureByText(
            "test.lua",
            """
            if x<caret> == 1 then
                a()
            elseif x == 2 then
                b()
            else
                c()
            end
            """.trimIndent(),
        )
        assertEmpty(myFixture.filterAvailableIntentions(actionText))
    }

    @Test
    fun `test no else not offered`() {
        myFixture.configureByText(
            "test.lua",
            """
            if x<caret> == 1 then
                a()
            end
            """.trimIndent(),
        )
        assertEmpty(myFixture.filterAvailableIntentions(actionText))
    }
}
