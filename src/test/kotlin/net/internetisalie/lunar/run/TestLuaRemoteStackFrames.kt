package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestLuaRemoteStackFrames : BaseDocumentTest() {

    @Test
    fun testRemoteStackFrames() {
        myFixture.configureByText(
            LuaFileType,
            """
            do
                local _ = {
                    { { "c", "stack.lua", 3, 5, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }, { f = { 3, "3" } }, { d = { 1, "1" }, e = { 2, "2" }, _ENV = { { }, "table: 0x5e930bee3c50" } } },
                    { { "b", "stack.lua", 2, 6, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }, { e = { 2, "2" }, c = { function() --[[..skipped..]] end, "function: 0x5e930bf1ebd0" } }, { d = { 1, "1" }, _ENV = { nil, "table: 0x5e930bee3c50" } } },
                    { { "a", "stack.lua", 1, 8, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }, { b = { function() --[[..skipped..]] end, "function: 0x5e930bf1fdb0" }, d = { 1, "1" } }, { _ENV = { nil, "table: 0x5e930bee3c50" } } },
                    { { nil, "stack.lua", 0, 10, "main", "", "/home/mini/Documents/src/lua/test/stack.lua" }, { a = { function() --[[..skipped..]] end, "function: 0x5e930bf1fd30" } }, { _ENV = { nil, "table: 0x5e930bee3c50" } } },
                    { { nil, "=[C]", -1, -1, "C", "", "[C]" }, {}, {} }
                };
                return _;
            end
        """.trimIndent())

        ApplicationManager.getApplication().runReadAction {
            val stackEntries = LuaRemoteStack.create(myFixture.file)
            assertNotNull(stackEntries)

            val entries = stackEntries.entries
            assertEquals(5, entries.size)

            val stackEntryB = entries[1]
            assertNotNull(stackEntryB)

            val stackFrameB = stackEntryB.frame
            assertNotNull(stackFrameB)

            assertEquals("b", stackFrameB.name)
            assertEquals("stack.lua", stackFrameB.file)

            val localsScopeB = stackEntryB.locals
            assertNotNull(localsScopeB)

            val variableE = localsScopeB.getVariable("e")
            assertNotNull(variableE)
            assertEquals("2", variableE.value.numberValue?.toInt().toString())
            assertEquals("2", variableE.displayValue)
        }
    }
}