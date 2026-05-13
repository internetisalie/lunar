package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaRecursiveReferenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return System.getProperty("user.dir")
    }

    @Test
    fun testRecursiveLocalFunction() {
        myFixture.configureByText("test.lua", """
            local function factorial(n)
                if n == 0 then return 1 end
                return n * <caret>factorial(n - 1)
            end
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull("Reference should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Recursive reference to 'factorial' should be resolved", resolved)
        assertEquals("factorial", resolved!!.text)
        assertEquals(15, resolved.textOffset)
    }

    @Test
    fun testRecursiveGlobalFunction() {
        myFixture.configureByText("test.lua", """
            function factorial(n)
                if n == 0 then return 1 end
                return n * <caret>factorial(n - 1)
            end
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull("Reference should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Recursive reference to global 'factorial' should be resolved", resolved)
        assertEquals("factorial", resolved!!.text)
        assertEquals(9, resolved.textOffset)
    }

    @Test
    fun testParameterShadowsFunction() {
        myFixture.configureByText("test.lua", """
            local function f(f)
                print(<caret>f)
            end
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        assertNotNull(reference)

        val resolved = reference!!.resolve()
        assertNotNull(resolved)
        // Should resolve to the parameter 'f', not the function 'f'
        // Parameter 'f' is at offset 17
        assertEquals(17, resolved!!.textOffset)
    }

    @Test
    fun testLocalFunctionShadowsOuterLocal() {
        myFixture.configureByText("test.lua", """
            local f = 1
            local function f()
                print(<caret>f)
            end
        """.trimIndent())

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        val reference = element.reference
        val resolved = reference!!.resolve()

        assertNotNull(resolved)
        // Should resolve to 'local function f', not 'local f = 1'
        // 'local f = 1' is at offset 6
        // 'local function f' is at offset 27
        assertEquals(27, resolved!!.textOffset)
    }

    @Test
    fun testLuacheckParserRecursiveCall() {
        val virtualFile = myFixture.copyFileToProject("test/luacheck/src/luacheck/parser.lua", "luacheck/parser.lua")
        myFixture.configureFromExistingVirtualFile(virtualFile)

        // Find "parse_subexpression" call at line 621 (1-based)
        // L621:       local operand = parse_subexpression(state, unary_priority)
        val offset = myFixture.editor.document.getLineStartOffset(620) + 22 // Approximate offset for "parse_subexpression"
        myFixture.editor.caretModel.moveToOffset(offset)

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        assertEquals("parse_subexpression", element.text)

        val reference = element.reference
        assertNotNull(reference)

        val resolved = reference!!.resolve()
        assertNotNull("Recursive reference to 'parse_subexpression' should be resolved", resolved)
        // It should resolve to the declaration at line 613
        val declLine = myFixture.editor.document.getLineNumber(resolved!!.textOffset)
        assertEquals(612, declLine) // 0-based
    }
}
