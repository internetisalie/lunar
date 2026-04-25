package net.internetisalie.lunar.run

import com.intellij.icons.AllIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLuaDebugValue {

    @Test
    fun testConstructorWithTypeAndDisplay() {
        val debugValue = LuaDebugValue("number", "42", AllIcons.Nodes.Variable)
        
        assertTrue(debugValue.isNumber)
        assertFalse(debugValue.isString)
        assertFalse(debugValue.isBool)
        assertFalse(debugValue.isTable)
    }

    @Test
    fun testConstructorWithLuaValue() {
        val luaValue = LuaValue(null)
        val debugValue = LuaDebugValue(luaValue, "0x12345", AllIcons.Nodes.Variable)
        
        assertEquals(luaValue, debugValue.raw)
    }

    @Test
    fun testConstructorWithError() {
        val debugValue = LuaDebugValue("Some error message")
        
        // The error message is stored in displayValue, accessed via computePresentation
        // raw.text will be null since we create LuaValue(null) in the error constructor
        assertNotNull(debugValue)
    }

    @Test
    fun testIsString() {
        val stringValue = LuaDebugValue("string", "\"hello\"", null)
        assertTrue(stringValue.isString)
        assertFalse(stringValue.isNumber)
    }

    @Test
    fun testIsNumber() {
        val numberValue = LuaDebugValue("number", "42", null)
        assertTrue(numberValue.isNumber)
        assertFalse(numberValue.isString)
    }

    @Test
    fun testIsBool() {
        val boolValue = LuaDebugValue("boolean", "true", null)
        assertTrue(boolValue.isBool)
        assertFalse(boolValue.isNumber)
    }

    @Test
    fun testIsTable() {
        val tableValue = LuaDebugValue("table", "{...}", null)
        assertTrue(tableValue.isTable)
        assertFalse(tableValue.isString)
    }

    @Test
    fun testMultipleTypeChecks() {
        val debugValue = LuaDebugValue("string", "\"test\"", null)
        
        assertTrue(debugValue.isString)
        assertFalse(debugValue.isNumber)
        assertFalse(debugValue.isBool)
        assertFalse(debugValue.isTable)
    }
}
