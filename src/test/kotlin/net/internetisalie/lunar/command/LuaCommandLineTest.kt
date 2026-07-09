package net.internetisalie.lunar.command

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaInterpreter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Covers the surviving legacy [newLuaInterpreterCommandLine] factory (jar branch + system binary).
 *
 * TOOLING-05 Phase 4: the `newProjectLuaInterpreterCommandLine` / `newLuaDefaultInterpreterCommandLine`
 * tests were dropped — those factories were removed; their resolver-based replacement
 * (`toolchain.exec.LuaInterpreterCommandLines.forProject`) is covered by
 * `LuaInterpreterCommandLinesTest`. This file is deleted in Phase 5 with `command/LuaCommandLine.kt`.
 */
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
}
