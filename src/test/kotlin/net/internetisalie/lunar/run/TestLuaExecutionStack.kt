package net.internetisalie.lunar.run

import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestLuaExecutionStack : BaseDocumentTest() {

    @Test
    fun testExecutionStackRemoteStack() {
        myFixture.configureByText(LuaFileType, "-- test file")
        
        // Create a remote stack with null table  
        val remoteStack = LuaRemoteStack(null)
        
        assertNotNull(remoteStack)
        // entries should be empty since we passed null
        assert(remoteStack.entries.isEmpty())
    }
}
