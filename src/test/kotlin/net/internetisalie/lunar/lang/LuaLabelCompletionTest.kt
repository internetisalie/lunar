package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaLabelCompletionTest : BasePlatformTestCase() {

    private val luaKeywords = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
    )

    private fun getLabelsOnly(strings: List<String>): List<String> {
        return strings.filter { it !in luaKeywords }
    }

    @Test
    fun testVisibleLabelsCompletion() {
        myFixture.configureByText("test.lua", """
            ::alpha::
            ::beta::
            goto <caret>
        """.trimIndent())
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(getLabelsOnly(strings), "alpha", "beta")
    }

    @Test
    fun testEnclosingBlockCompletion() {
        myFixture.configureByText("test.lua", """
            ::outer::
            do
                ::inner::
                goto <caret>
            end
        """.trimIndent())
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(getLabelsOnly(strings), "outer", "inner")
    }

    @Test
    fun testSiblingBlockCompletion() {
        myFixture.configureByText("test.lua", """
            do
                ::sibling::
            end
            ::current::
            goto <caret>
        """.trimIndent())
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(getLabelsOnly(strings), "current")
    }

    @Test
    fun testFunctionBoundaryCompletion() {
        myFixture.configureByText("test.lua", """
            ::outer::
            local f = function()
                ::inner::
                goto <caret>
            end
        """.trimIndent())
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertSameElements(getLabelsOnly(strings), "inner")
    }
}
