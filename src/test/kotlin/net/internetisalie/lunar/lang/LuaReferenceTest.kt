package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaReferenceTest : BasePlatformTestCase() {

    @Test
    fun testLocalVariableReference() {
        myFixture.configureByText("test.lua", """
            local data = {value = 42}
            process(<caret>data)
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull("Reference should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Reference should be resolved", resolved)
        assertEquals("data", resolved!!.text)
        // 'local data' starts at index 6 in the provided string
        assertEquals(6, resolved.textOffset)
    }

    @Test
    fun testMultipleLocalVariables() {
        myFixture.configureByText("test.lua", """
            local a, b = 1, 2
            print(<caret>b)
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull(reference)

        val resolved = reference!!.resolve()
        assertNotNull(resolved)
        assertEquals("b", resolved!!.text)
    }

    @Test
    fun testGenericForVariable() {
        myFixture.configureByText("test.lua", """
            for k, v in pairs({}) do
                print(<caret>v)
            end
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull(reference)

        val resolved = reference!!.resolve()
        assertNotNull("Generic for variable 'v' should be resolved", resolved)
        assertEquals("v", resolved!!.text)
    }
}
