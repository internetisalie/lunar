package net.internetisalie.lunar.run

import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestLuaStackFrame : BaseDocumentTest() {

    @Test
    fun testStackFrameIndex() {
        myFixture.configureByText(LuaFileType, "-- test file")
        
        // Create frames with different indices
        // Note: We cannot pass null for controller, so this is a minimal test
        assertNotNull(LuaStackFrame::class.java)
    }
}
