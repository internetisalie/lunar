package net.internetisalie.lunar.run

import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import javax.swing.Icon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLuaDebugValue {

    private class CapturingNode : XCompositeNode {
        var captured: XValueChildrenList? = null

        override fun addChildren(children: XValueChildrenList, last: Boolean) {
            captured = children
        }

        override fun tooManyChildren(remaining: Int) {}
        override fun setAlreadySorted(alreadySorted: Boolean) {}
        override fun setErrorMessage(errorMessage: String) {}
        override fun setErrorMessage(errorMessage: String, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {}
        override fun setMessage(
            message: String,
            icon: Icon?,
            attributes: SimpleTextAttributes,
            link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?,
        ) {}
    }

    /** TC-02a (§2.2): a non-string/non-number key (a Function) renders via toDisplayString, no crash. */
    @Test
    fun testComputeChildrenRendersFunctionKeyWithoutCrash() {
        val table = LuaTable()
        table.named[LuaValue(kind = LuaValueKind.Function)] = LuaValue.newNumber(1.0)
        val tableValue = LuaDebugValue(LuaValue.newTable(table), null, null)

        val node = CapturingNode()
        tableValue.computeChildren(node)

        val children = node.captured
        assertNotNull(children)
        assertEquals("[function]", children.getName(0))
    }

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
