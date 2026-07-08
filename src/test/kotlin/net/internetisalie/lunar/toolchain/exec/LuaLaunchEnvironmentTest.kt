package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.configurations.GeneralCommandLine
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-logic tests for [LuaLaunchEnvironment.applyTo] (TOOLING-03-10). Ports the PATH-merge
 * assertions of the legacy `tool/LuaToolEnvironmentTest.kt` to the new value type and adds the
 * LUA_PATH / LUA_CPATH / LUAROCKS_CONFIG application semantics (TCs 14, 15, 21, 22).
 */
class LuaLaunchEnvironmentTest {

    private val sep = File.pathSeparator

    private fun env(
        dirs: List<Path> = emptyList(),
        luaPath: String? = null,
        luaCPath: String? = null,
        luarocksConfig: String? = null,
    ) = LuaLaunchEnvironment(dirs, luaPath, luaCPath, luarocksConfig)

    @Test
    fun `TC14 prepend places dirs ahead of overridden PATH preserving order`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = "A${sep}B"
        val dirs = listOf(Path.of("/opt/x"), Path.of("/usr/bin"))

        env(dirs = dirs).applyTo(cmd)

        assertEquals("/opt/x$sep/usr/bin${sep}A${sep}B", cmd.environment["PATH"])
    }

    @Test
    fun `TC15 prepend with no PATH override falls back to process PATH`() {
        val cmd = GeneralCommandLine("echo")
        val dirs = listOf(Path.of("/opt/x"))

        env(dirs = dirs).applyTo(cmd)

        val expected = "/opt/x" + sep + (System.getenv("PATH") ?: "")
        assertEquals(expected, cmd.environment["PATH"])
    }

    @Test
    fun `empty dirs leaves PATH untouched`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = "/orig/bin"

        env(dirs = emptyList()).applyTo(cmd)

        assertEquals("/orig/bin", cmd.environment["PATH"])
    }

    @Test
    fun `single dir prepended to blank PATH yields just the dir`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = ""

        env(dirs = listOf(Path.of("/opt/x/bin"))).applyTo(cmd)

        assertEquals("/opt/x/bin", cmd.environment["PATH"])
    }

    @Test
    fun `separator join across multiple dirs`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = "/orig"
        val dirs = listOf(Path.of("/a"), Path.of("/b"), Path.of("/c"))

        env(dirs = dirs).applyTo(cmd)

        assertEquals("/a$sep/b$sep/c$sep/orig", cmd.environment["PATH"])
    }

    @Test
    fun `null luaPath leaves LUA_PATH untouched`() {
        val cmd = GeneralCommandLine("echo")

        env(luaPath = null).applyTo(cmd)

        assertFalse("LUA_PATH" in cmd.environment)
    }

    @Test
    fun `non-null luaPath and luaCPath are set verbatim`() {
        val cmd = GeneralCommandLine("echo")

        env(luaPath = "/p/?.lua;;", luaCPath = "/p/?.so;;").applyTo(cmd)

        assertEquals("/p/?.lua;;", cmd.environment["LUA_PATH"])
        assertEquals("/p/?.so;;", cmd.environment["LUA_CPATH"])
    }

    @Test
    fun `null luaCPath leaves LUA_CPATH untouched`() {
        val cmd = GeneralCommandLine("echo")

        env(luaCPath = null).applyTo(cmd)

        assertFalse("LUA_CPATH" in cmd.environment)
    }

    @Test
    fun `TC21 luarocksConfig fills an absent key`() {
        val cmd = GeneralCommandLine("echo")

        env(luarocksConfig = "/env/luarocks-config.lua").applyTo(cmd)

        assertEquals("/env/luarocks-config.lua", cmd.environment["LUAROCKS_CONFIG"])
    }

    @Test
    fun `luarocksConfig does not overwrite a user-set key`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["LUAROCKS_CONFIG"] = "/user/config.lua"

        env(luarocksConfig = "/env/luarocks-config.lua").applyTo(cmd)

        assertEquals("/user/config.lua", cmd.environment["LUAROCKS_CONFIG"])
    }

    @Test
    fun `TC22 null luarocksConfig leaves the key absent`() {
        val cmd = GeneralCommandLine("echo")

        env(luarocksConfig = null).applyTo(cmd)

        assertFalse("LUAROCKS_CONFIG" in cmd.environment)
    }

    @Test
    fun `applyTo returns the command line for chaining`() {
        val cmd = GeneralCommandLine("echo")

        assertTrue(env().applyTo(cmd) === cmd)
    }
}
