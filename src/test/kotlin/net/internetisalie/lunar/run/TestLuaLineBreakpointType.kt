package net.internetisalie.lunar.run

import kotlin.test.Test
import kotlin.test.assertNotNull

class TestLuaLineBreakpointType {

    @Test
    fun testBreakpointType() {
        val breakpointType = LuaLineBreakpointType()
        assertNotNull(breakpointType)
    }
}
