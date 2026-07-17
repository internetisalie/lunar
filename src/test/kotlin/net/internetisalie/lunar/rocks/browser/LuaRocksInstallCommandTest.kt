package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

/**
 * Unit tests for [LuaRocksInstallCommand] canonical argument assembly (ROCKS-16, design §3.1).
 *
 * Covers TC-ROCKS-16-01, -02, -03. The `--tree <root>` pair always precedes the package name so
 * every install/uninstall lands in the project tree the rest of the plugin reads.
 */
class LuaRocksInstallCommandTest {

    private val treeRoot: Path = Path.of("/proj/lua_modules")

    @Test
    fun `TC-ROCKS-16-01 install args carry tree then name then version`() {
        val args = LuaRocksInstallCommand.buildInstallArgs(treeRoot, "inspect", "3.1.3-0")
        assertEquals(listOf("install", "--tree", "/proj/lua_modules", "inspect", "3.1.3-0"), args)
    }

    @Test
    fun `TC-ROCKS-16-02 install args omit version when null`() {
        val args = LuaRocksInstallCommand.buildInstallArgs(treeRoot, "inspect", null)
        assertEquals(listOf("install", "--tree", "/proj/lua_modules", "inspect"), args)
    }

    @Test
    fun `install args omit version when blank`() {
        val args = LuaRocksInstallCommand.buildInstallArgs(treeRoot, "inspect", "   ")
        assertEquals(listOf("install", "--tree", "/proj/lua_modules", "inspect"), args)
    }

    @Test
    fun `TC-ROCKS-16-03 remove args target the canonical tree`() {
        val args = LuaRocksInstallCommand.buildRemoveArgs(treeRoot, "inspect")
        assertEquals(listOf("remove", "--tree", "/proj/lua_modules", "inspect"), args)
    }
}
