package net.internetisalie.lunar.luacats.lang.lexer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaCatsElementTypeTest : BasePlatformTestCase() {

    fun testToStringReturnsRawDebugNameWithoutPrefix() {
        assertEquals("LCATS_NAME", LuaCatsElementType("LCATS_NAME").toString())
    }
}
