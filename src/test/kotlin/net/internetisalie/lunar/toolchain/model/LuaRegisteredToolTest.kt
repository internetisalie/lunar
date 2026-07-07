package net.internetisalie.lunar.toolchain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LuaRegisteredToolTest {

    private fun createTool(fileExists: Boolean, executable: Boolean, probeOk: Boolean?): LuaRegisteredTool {
        return LuaRegisteredTool(
            id = "test-id",
            kindId = "lua",
            path = "/usr/bin/lua",
            version = "5.4.6",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(
                fileExists = fileExists,
                executable = executable,
                probeOk = probeOk,
                probedAtMtime = 123456789L,
                reason = "OK"
            )
        )
    }

    @Test
    fun testIsUsableTruthTable() {
        // (a) fileExists=T, executable=T, probeOk=true -> true
        assertEquals(true, createTool(fileExists = true, executable = true, probeOk = true).isUsable)

        // (b) fileExists=T, executable=T, probeOk=null -> true
        assertEquals(true, createTool(fileExists = true, executable = true, probeOk = null).isUsable)

        // (c) fileExists=T, executable=T, probeOk=false -> false
        assertEquals(false, createTool(fileExists = true, executable = true, probeOk = false).isUsable)

        // (d) fileExists=T, executable=false, probeOk=true -> false
        assertEquals(false, createTool(fileExists = true, executable = false, probeOk = true).isUsable)

        // (e) fileExists=false, executable=false, probeOk=true -> false
        assertEquals(false, createTool(fileExists = false, executable = false, probeOk = true).isUsable)
    }
}
