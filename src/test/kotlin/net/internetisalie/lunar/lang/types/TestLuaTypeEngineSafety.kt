package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.reflect.Field

@RunWith(JUnit4::class)
class TestLuaTypeEngineSafety : BasePlatformTestCase() {

    @Test
    fun testScopeShadowingAndRedeclaration() {
        val graph = LuaTypeGraph()
        val root = LuaScope.root()
        val v1 = graph.variable(myFixture.addFileToProject("test.lua", ""))

        // 1. Initial declaration
        assertFalse("Should not be redeclared", root.declare("x", v1))
        assertFalse("Should not be shadowing", root.isShadowing("x"))
        assertEquals(v1, root.lookup("x"))

        // 2. Redeclaration in same scope
        val v2 = graph.variable(myFixture.addFileToProject("test2.lua", ""))
        assertTrue("Should be detected as redeclared", root.declare("x", v2))
        assertEquals(v2, root.lookup("x"))

        // 3. Shadowing in child scope
        val child = root.child()
        val v3 = graph.variable(myFixture.addFileToProject("test3.lua", ""))
        assertTrue("Should be detected as shadowing", child.isShadowing("x"))
        assertFalse("Should not be redeclared in child scope", child.declare("x", v3))
        assertEquals(v3, child.lookup("x"))
        assertEquals(v2, root.lookup("x")) // Root remains unchanged
    }

    @Test
    fun testGenericIsolationAndMemoization() {
        val file = myFixture.configureByText("test.lua", """
            ---@generic T
            ---@param x T
            ---@return T
            local function f(x) return x end

            local a = f(1)
            ---@type number
            local a_check = a

            local b = f("hi")
            ---@type string
            local b_check = b

            local c = f(true)
            ---@type number
            local c_err = c -- Error: boolean not assignable to number
        """.trimIndent())

        val snapshot = LuaTypesVisitor.getTypes(file)
        val errors = snapshot.getErrors()

        // If isolation is working:
        // a should be number
        // b should be string
        // c should be boolean
        // c_err should have error: boolean not assignable to number

        // If isolation is BROKEN (current state):
        // param x becomes Union(number, string, boolean)
        // return becomes Union(number, string, boolean)
        // a_check might have error: number | string | boolean not assignable to number
        // b_check might have error: number | string | boolean not assignable to string

        val errorMessages = errors.map { it.message }

        // Verify we have the correct error for c_err

        assertTrue("Should have error for boolean -> number",
            errors.any { it.message.contains("boolean") && it.message.contains("number") })

        // If broken, we might also see errors for a_check and b_check
        val assignmentErrors = errors.filter { it.element.text == "a" || it.element.text == "b" }
        assertTrue("Call sites should be isolated; 'a' and 'b' should not have errors", assignmentErrors.isEmpty())
    }
    @Test
    fun testIterationLimitSafety() {
        // Create a circular constraint that might cause many iterations
        // We'll use a recursive table pattern.
        val file = myFixture.configureByText("test.lua", """
            local t = {}
            t.next = t
            local x = t.next.next.next.next.next.next.next.next.next.next.a
        """.trimIndent())

        val startTime = System.currentTimeMillis()
        val snapshot = LuaTypesVisitor.getTypes(file)
        val duration = System.currentTimeMillis() - startTime

        assertNotNull(snapshot)
        assertTrue("Type checking should complete within safety limit", duration < 5000)
    }
}
