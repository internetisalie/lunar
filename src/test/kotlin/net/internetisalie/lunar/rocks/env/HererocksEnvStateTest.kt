package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Phase 1: descriptor round-trip + path helpers (design §2.1, §2.2). */
class HererocksEnvStateTest : EnvSettingsTestCase() {

    fun testPosixPathHelpers() {
        if (SystemInfo.isWindows) return
        val spec = HererocksEnvState(directory = "/p/.lua", flavor = HererocksFlavor.PUC, luaVersion = "5.4")
        assertEquals("/p/.lua/bin", spec.binDir())
        assertEquals("/p/.lua/bin/lua", spec.luaExe())
        assertEquals("/p/.lua/bin/luarocks", spec.luarocksExe())
    }

    fun testLuajitLuaExe() {
        if (SystemInfo.isWindows) return
        val spec = HererocksEnvState(directory = "/p/.lua", flavor = HererocksFlavor.LUAJIT, luaVersion = "2.1")
        assertEquals("/p/.lua/bin/luajit", spec.luaExe())
    }

    fun testDisplayLabelFallback() {
        val spec = HererocksEnvState(flavor = HererocksFlavor.PUC, luaVersion = "5.3")
        assertEquals("PUC 5.3", spec.displayLabel())
        assertEquals("custom", spec.copy(label = "custom").displayLabel())
    }

    fun testStateRoundTrip() {
        val settings = LuaProjectSettings.getInstance(project)
        val spec = HererocksEnvState(
            id = "abc",
            directory = "/p/.lua",
            flavor = HererocksFlavor.LUAJIT,
            luaVersion = "2.1",
            luarocksVersion = "3.11.1",
            label = "matrix",
        )
        settings.state.hererocksEnvs.add(spec)
        settings.state.activeEnvId = spec.id

        val serialized = settings.state
        settings.loadState(serialized)
        val reloaded = LuaProjectSettings.getInstance(project).state
        assertEquals(listOf(spec), reloaded.hererocksEnvs)
        assertEquals(spec.id, reloaded.activeEnvId)
    }
}
