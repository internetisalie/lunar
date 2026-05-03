package net.internetisalie.lunar.run

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestLuaDebugVariable : BaseDocumentTest() {

    @Test
    fun testDebugVariableCreation() {
        val value = LuaDebugValue("number", "42", null)
        val variable = LuaDebugVariable("myVar", value, true)

        assertNotNull(variable)
        assertEquals("myVar", variable.name)
    }

    @Test
    fun testDebugVariableWithLocalFlag() {
        val value = LuaDebugValue("string", "\"hello\"", null)
        val localVar = LuaDebugVariable("local_var", value, true)
        val globalVar = LuaDebugVariable("global_var", value, false)

        assertNotNull(localVar)
        assertNotNull(globalVar)
        assertEquals("local_var", localVar.name)
        assertEquals("global_var", globalVar.name)
    }

    @Test
    fun testDebugVariableWithTable() {
        myFixture.configureByText(LuaFileType, "{a = 1, b = 2}")
        runReadAction {
            val element = PsiTreeUtil.findChildOfType(myFixture.file, LuaTableConstructor::class.java)
            assertNotNull(element)
            val luaValue = LuaValue(element)
            val tableDebugValue = LuaDebugValue(luaValue, null, null)

            val variable = LuaDebugVariable("tableVar", tableDebugValue, true)

            assertNotNull(variable)
            assertEquals("tableVar", variable.name)
        }
    }

    @Test
    fun testDebugVariableMultipleVariables() {
        val var1 = LuaDebugValue("number", "1", null)
        val var2 = LuaDebugValue("string", "\"text\"", null)
        val var3 = LuaDebugValue("boolean", "true", null)

        val debug1 = LuaDebugVariable("x", var1, true)
        val debug2 = LuaDebugVariable("y", var2, true)
        val debug3 = LuaDebugVariable("z", var3, true)

        assertNotNull(debug1)
        assertNotNull(debug2)
        assertNotNull(debug3)
        assertEquals("x", debug1.name)
        assertEquals("y", debug2.name)
        assertEquals("z", debug3.name)
    }
}
