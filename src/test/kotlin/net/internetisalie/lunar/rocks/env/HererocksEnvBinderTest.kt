package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaToolType
import java.io.File
import java.nio.file.Files

/** Phase 4: bind/unbind against a fixture env dir (TC-6). */
class HererocksEnvBinderTest : BasePlatformTestCase() {

    private lateinit var envDir: File

    override fun setUp() {
        super.setUp()
        val root = Files.createTempDirectory("hererocks-bind-test").toFile()
        envDir = File(root, ".lua")
        val bin = File(envDir, "bin").also { it.mkdirs() }
        writeScript(File(bin, "lua"))
        writeScript(File(bin, "luarocks"))
    }

    override fun tearDown() {
        try {
            envDir.parentFile?.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun writeScript(file: File) {
        file.writeText("#!/bin/sh\necho fake\n")
        file.setExecutable(true)
    }

    fun testBindWiresInterpreterAndTool() {
        if (SystemInfo.isWindows) return
        val spec = HererocksEnvState(directory = envDir.absolutePath, flavor = HererocksFlavor.PUC, luaVersion = "5.4")

        HererocksEnvBinder.bind(project, spec)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val settings = LuaProjectSettings.getInstance(project)
        val boundId = settings.state.projectToolBindings[LuaToolType.LUAROCKS.name]
        assertNotNull("LUAROCKS binding must be set", boundId)
        assertEquals(File(envDir, "bin/lua").absolutePath, settings.state.interpreter?.path)
        assertNotNull("descriptor must be stored", settings.state.hererocksEnv)
        assertEquals(envDir.absolutePath, settings.state.hererocksEnv?.directory)
    }

    fun testUnbindClearsAll() {
        if (SystemInfo.isWindows) return
        val spec = HererocksEnvState(directory = envDir.absolutePath, flavor = HererocksFlavor.PUC, luaVersion = "5.4")
        HererocksEnvBinder.bind(project, spec)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        HererocksEnvBinder.unbind(project, deleteDir = false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val settings = LuaProjectSettings.getInstance(project)
        assertNull(settings.state.projectToolBindings[LuaToolType.LUAROCKS.name])
        assertNull(settings.state.interpreter)
        assertNull(settings.state.hererocksEnv)
    }
}
