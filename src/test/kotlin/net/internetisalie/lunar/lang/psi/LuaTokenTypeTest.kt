package net.internetisalie.lunar.lang.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaTokenTypeTest : BasePlatformTestCase() {

    fun testToStringPrefixesDebugName() {
        assertEquals("LuaTokenType.(", LuaTokenType("(").toString())
    }
}
