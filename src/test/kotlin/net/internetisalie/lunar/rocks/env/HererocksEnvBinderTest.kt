package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import net.internetisalie.lunar.tool.LuaToolType
import java.io.File
import java.nio.file.Files

/** Phase 4: bind/unbind against a fixture env dir (TC-6). */
class HererocksEnvBinderTest : EnvSettingsTestCase() {

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

        var changeCount = 0
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    changeCount++
                }
            },
        )

        HererocksEnvBinder.bind(project, spec)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue("bind must fire LuaSettingsChangedListener.TOPIC", changeCount >= 1)
        val settings = LuaProjectSettings.getInstance(project)
        val boundId = settings.state.projectToolBindings[LuaToolType.LUAROCKS.name]
        assertNotNull("LUAROCKS binding must be set", boundId)
        assertEquals(File(envDir, "bin/lua").absolutePath, settings.state.interpreter?.path)
    }

    fun testUnbindClearsAll() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        val spec = HererocksEnvState(id = "A", directory = envDir.absolutePath, flavor = HererocksFlavor.PUC, luaVersion = "5.4")
        settings.upsertAndActivate(project, spec)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        HererocksEnvBinder.unbind(project, deleteDir = false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNull(settings.state.projectToolBindings[LuaToolType.LUAROCKS.name])
        assertNull(settings.state.interpreter)
        assertTrue("env removed from set on unbind", settings.resolveAllEnvs().isEmpty())
    }
}
