package net.internetisalie.lunar.run

import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestLuaValue : BaseDocumentTest() {

    @Test
    fun testLuaValueCreation() {
        myFixture.configureByText(LuaFileType, "42")
        val element = myFixture.file.firstChild
        val luaValue = LuaValue(element)
        
        assertNotNull(luaValue)
        assertNotNull(luaValue.kind)
    }

    @Test
    fun testLuaValueWithNull() {
        val luaValue = LuaValue(null)
        
        assertNotNull(luaValue)
        assertNull(luaValue.text)
    }

    @Test
    fun testLuaValueNone() {
        val luaValue = LuaValue(null)
        
        assertNotNull(luaValue.kind)
    }

    @Test
    fun testCheckTableNull() {
        val luaValue = LuaValue(null)
        
        assertNull(luaValue.checkTable())
    }

    @Test
    fun testLuaValueCompanionObject() {
        val noneValue = LuaValue.NONE
        assertNotNull(noneValue)
    }
}
