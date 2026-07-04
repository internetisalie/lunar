package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import java.io.File
import java.nio.file.Files

/** Phase 2: active-env switch rebinds + resolves (TC-3, TC-4, TC-5). */
class HererocksEnvSwitchTest : BasePlatformTestCase() {

    private lateinit var root: File

    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("hererocks-switch-test").toFile()
    }

    override fun tearDown() {
        try {
            root.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun fakeEnv(id: String): HererocksEnvState {
        val dir = File(root, id)
        val bin = File(dir, "bin").also { it.mkdirs() }
        writeScript(File(bin, "lua"), "Lua 5.4.0")
        writeScript(File(bin, "luarocks"), "LuaRocks 3.11.1 for Lua 5.4")
        return HererocksEnvState(id = id, directory = dir.absolutePath, flavor = HererocksFlavor.PUC, luaVersion = "5.4")
    }

    private fun writeScript(file: File, banner: String) {
        file.writeText("#!/bin/sh\necho \"$banner\"\n")
        file.setExecutable(true)
    }

    fun testSwitchBindsAndActivates() {
        if (SystemInfo.isWindows) return
        val a = fakeEnv("A")
        val b = fakeEnv("B")
        val settings = LuaProjectSettings.getInstance(project)
        settings.loadState(
            LuaProjectSettings.State().also {
                it.hererocksEnvs = mutableListOf(a, b)
                it.activeEnvId = "A"
            },
        )

        var changeCount = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    changeCount++
                }
            },
        )

        HererocksEnvSet.switch(project, "B")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("B", settings.state.activeEnvId)
        assertTrue("switch must fire TOPIC at least once", changeCount >= 1)
        assertEquals(File(root, "B/bin/luarocks").absolutePath, LuaRocksEnvironment.resolveExecutable(project))
    }

    fun testUnknownIdIsNoOp() {
        if (SystemInfo.isWindows) return
        val a = fakeEnv("A")
        val settings = LuaProjectSettings.getInstance(project)
        settings.loadState(
            LuaProjectSettings.State().also {
                it.hererocksEnvs = mutableListOf(a)
                it.activeEnvId = "A"
            },
        )

        var changeCount = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    changeCount++
                }
            },
        )

        HererocksEnvSet.switch(project, "Z")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("A", settings.state.activeEnvId)
        assertEquals(0, changeCount)
    }
}
