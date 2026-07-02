package net.internetisalie.lunar.command

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Files
import java.nio.file.Path

class LuaCommandLineTest : BasePlatformTestCase() {

    private lateinit var base: Path

    override fun setUp() {
        super.setUp()
        base = Files.createTempDirectory("lunar-cmdline-test")
    }

    override fun tearDown() {
        try {
            base.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun createAndRefresh(relative: String): Path {
        val path = base.resolve(relative)
        Files.createFile(path)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        return path
    }

    fun testSystemBinaryBuildsPlainCommandLine() {
        val luaPath = createAndRefresh("lua")

        val cmd = newLuaInterpreterCommandLine(LuaInterpreter(path = luaPath.toString()))

        assertNotNull(cmd)
        assertTrue(cmd!!.exePath.endsWith("lua"))
        assertFalse(cmd.parametersList.list.contains("-cp"))
    }

    fun testJarInterpreterBuildsJavaClasspathCommand() {
        val jarPath = createAndRefresh("foo.jar")

        val cmd = newLuaInterpreterCommandLine(LuaInterpreter(path = jarPath.toString()))

        assertNotNull(cmd)
        assertEquals("java", cmd!!.exePath)
        assertEquals(listOf("-cp", jarPath.toString(), "lua"), cmd.parametersList.list)
    }

    fun testNullExecutableReturnsNull() {
        val cmd = newLuaInterpreterCommandLine(LuaInterpreter(path = "/no/such/lua"))

        assertNull(cmd)
    }

    fun testProjectCommandLineInjectsLuaPath() {
        val luaPath = createAndRefresh("lua-proj")
        val sourcePath = "/tmp/lunar-src/?.lua"
        val settingsState = LuaProjectSettings.getInstance(project).state
        settingsState.interpreter = LuaInterpreter(path = luaPath.toString())
        settingsState.sourcePath = sourcePath

        val cmd = newProjectLuaInterpreterCommandLine(project)

        assertNotNull(cmd)
        assertEquals(sourcePath, cmd!!.environment["LUA_PATH"])
    }

    fun testProjectCommandLineNullInterpreterReturnsNull() {
        LuaProjectSettings.getInstance(project).state.interpreter = null

        val cmd = newProjectLuaInterpreterCommandLine(project)

        assertNull(cmd)
    }
}
