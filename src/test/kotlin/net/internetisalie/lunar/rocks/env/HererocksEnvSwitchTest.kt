package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import java.io.File
import java.nio.file.Files

/** Phase 2: active-env switch rebinds + resolves (TC-3, TC-4, TC-5). */
class HererocksEnvSwitchTest : EnvSettingsTestCase() {

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
        // Note (TOOLING-05 Phase 2): LuaRocksEnvironment.resolveExecutable now routes through the
        // TOOLING-01/02 toolchain resolver, not the hererocks env binding. Per-environment luarocks
        // resolution is covered by TOOLING-02 environment tests + the Phase 5 matrix rewrite; this
        // legacy suite is deleted in Phase 5. The switch/activate semantics above are the assertion
        // of record here.
    }

    /**
     * ROCKS-15 remediation defect A: a live [LuaProjectSettings.upsertAndActivate] (the create/
     * detect success path) must surface the env in the set + activate it WITHOUT loadState/migration.
     * Would have caught the "batch/create env missing from switcher until reload" live defect.
     */
    fun testLiveActivateAddsToSetWithoutMigration() {
        if (SystemInfo.isWindows) return
        val a = fakeEnv("A")
        val settings = LuaProjectSettings.getInstance(project)

        settings.upsertAndActivate(project, a)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals(listOf(a), settings.resolveAllEnvs())
        assertEquals("A", settings.state.activeEnvId)
        assertEquals(a, settings.activeEnv())
    }

    /** ROCKS-15 remediation: re-activating the same directory upserts (no duplicate row). */
    fun testLiveActivateDedupsByDirectory() {
        if (SystemInfo.isWindows) return
        val a = fakeEnv("A")
        val settings = LuaProjectSettings.getInstance(project)

        settings.upsertAndActivate(project, a)
        settings.upsertAndActivate(project, a.copy(id = "A2"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("same directory must not append a twin", 1, settings.resolveAllEnvs().size)
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
