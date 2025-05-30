package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestLuaRemoteResultFactory : BaseDocumentTest() {
    @Test
    fun testCreate() {

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
                local __ = {};
                _[1][3]._ENV[1]._G = _[1][3]._ENV[1];
                _[1][3]._ENV[1].math.atan = _[1][3]._ENV[1].math.atan2;
                _[1][3]._ENV[1].package.loaded.string = _[1][3]._ENV[1].string;
                _[1][3]._ENV[1].package.loaded.math = _[1][3]._ENV[1].math;
                _[1][3]._ENV[1].package.loaded.table = _[1][3]._ENV[1].table;
                _[1][3]._ENV[1].package.loaded.io = _[1][3]._ENV[1].io;
                _[1][3]._ENV[1].package.loaded["socket.core"].sinkt["keep-open"] = _[1][3]._ENV[1].package.loaded["socket.core"].sinkt.default;
                _[1][3]._ENV[1].package.loaded["socket.core"].sourcet["until-closed"] = _[1][3]._ENV[1].package.loaded["socket.core"].sourcet.default;
                _[1][3]._ENV[1].package.loaded.os = _[1][3]._ENV[1].os;
                _[1][3]._ENV[1].package.loaded._G = _[1][3]._ENV[1];
                _[1][3]._ENV[1].package.loaded.mobdebug.onexit = _[1][3]._ENV[1].os.exit;
                _[1][3]._ENV[1].package.loaded.mobdebug.loadstring = _[1][3]._ENV[1].load;
                _[1][3]._ENV[1].package.loaded.package = _[1][3]._ENV[1].package;
                _[1][3]._ENV[1].package.loaded.socket = _[1][3]._ENV[1].package.loaded["socket.core"];
                _[1][3]._ENV[1].package.loaded.debug = _[1][3]._ENV[1].debug;
                _[1][3]._ENV[1].utf8 = _[1][3]._ENV[1].package.loaded.utf8;
                _[1][3]._ENV[1].coroutine = _[1][3]._ENV[1].package.loaded.coroutine;
                _[2][3]._ENV[1] = _[1][3]._ENV[1];
                _[3][3]._ENV[1] = _[1][3]._ENV[1];
                _[4][3]._ENV[1] = _[1][3]._ENV[1];
                return _;
            end
        """.trimIndent(),
        )

        ApplicationManager.getApplication().runReadAction {
            val result = LuaRemoteResultFactory.create(myFixture.file)
            assertNotNull(result)
        }
    }
}