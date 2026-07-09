package net.internetisalie.lunar.toolchain.health

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.io.File
import java.util.UUID

class LuaToolEditorNotificationProviderTest : BasePlatformTestCase() {

    private val provider = LuaToolEditorNotificationProvider()

    override fun setUp() {
        super.setUp()
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        // The light project (and its LuaToolHealthMonitor) is reused across tests in this class, so
        // the session-scoped runtimeBannerDismissed flag would leak; give each test a fresh monitor.
        project.replaceService(LuaToolHealthMonitor::class.java, LuaToolHealthMonitor(project), testRootDisposable)
    }

    override fun tearDown() {
        try {
            LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
            LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        } finally {
            super.tearDown()
        }
    }

    private fun luaFile(): VirtualFile = myFixture.configureByText("Sample.lua", "local x = 1").virtualFile

    private fun collectPanel(file: VirtualFile): EditorNotificationPanel? {
        val function = provider.collectNotificationData(project, file) ?: return null
        val editor = myFixture.editor
        val fileEditor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .getSelectedEditor(file) ?: firstEditorFallback()
        return function.apply(fileEditor) as? EditorNotificationPanel
    }

    private fun firstEditorFallback(): FileEditor =
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).allEditors.first()

    private fun registerTool(kindId: String, health: LuaToolHealth): LuaRegisteredTool {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = kindId,
            path = "/does/not/matter/$kindId",
            version = null,
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = health
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool
    }

    private fun brokenHealth(reason: String) =
        LuaToolHealth(fileExists = false, executable = false, probeOk = null, probedAtMtime = null, reason = reason)

    private fun usableRuntimeTool(): LuaRegisteredTool {
        val binary = File.createTempFile("lunar-lua", "").also {
            it.writeText("#!/bin/sh\n")
            it.setExecutable(true)
            it.deleteOnExit()
        }
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "lua",
            path = binary.absolutePath,
            version = "5.4.6",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(true, true, true, binary.lastModified(), "OK 5.4.6")
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool
    }

    private fun disableLuaCheckInspection() {
        // Register the tool in the profile (enableInspections) so it is disable-able, then disable it.
        myFixture.enableInspections(LuaCheckInspection())
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        profile.setToolEnabled(LuaCheckInspection.SHORT_NAME, false, project)
        val displayKey = HighlightDisplayKey.find(LuaCheckInspection.SHORT_NAME)
            ?: error("LuaCheck display key must exist after enableInspections")
        assertFalse("guard: inspection must be disabled", profile.isToolEnabled(displayKey))
    }

    // TC-TOOLING-07-04: broken engaged luacheck → Warning banner naming the kind + reason.
    fun testBrokenEngagedLuaCheckBanner() {
        myFixture.enableInspections(LuaCheckInspection())
        val broken = registerTool("luacheck", brokenHealth("Binary missing"))
        LuaToolchainProjectSettings.getInstance(project).setBinding("luacheck", broken.id)
        // A usable runtime so the runtime banner does not pre-empt the broken-tool banner.
        usableRuntimeTool()

        val panel = collectPanel(luaFile())
        assertNotNull("engaged broken luacheck must produce a banner", panel)
        val text = panel!!.text
        assertTrue("banner names the kind: $text", text.contains("luacheck"))
        assertTrue("banner names the reason: $text", text.contains("Binary missing"))
    }

    // TC-TOOLING-07-04: non-Lua file → null.
    fun testNonLuaFileNoBanner() {
        val broken = registerTool("luacheck", brokenHealth("Binary missing"))
        LuaToolchainProjectSettings.getInstance(project).setBinding("luacheck", broken.id)
        val txt = myFixture.configureByText("notes.txt", "hello").virtualFile
        assertNull(provider.collectNotificationData(project, txt))
    }

    // TC-TOOLING-07-04: binding removed + inspection disabled → not engaged → null.
    fun testNotEngagedNoBanner() {
        disableLuaCheckInspection()
        registerTool("luacheck", brokenHealth("Binary missing"))
        // no binding: kind not engaged
        usableRuntimeTool()
        assertNull("unengaged broken tool must not banner", collectPanel(luaFile()))
    }

    // TC-TOOLING-07-05: no usable runtime → runtime banner text.
    fun testNoUsableRuntimeBanner() {
        val panel = collectPanel(luaFile())
        assertNotNull("empty inventory must show the runtime banner", panel)
        assertEquals("No usable Lua runtime for this project.", panel!!.text)
    }

    // TC-TOOLING-07-05: after dismissRuntimeBanner() → null.
    fun testRuntimeBannerDismissed() {
        LuaToolHealthMonitor.getInstance(project).dismissRuntimeBanner()
        assertNull("dismissed runtime banner is suppressed", collectPanel(luaFile()))
    }

    // TC-TOOLING-07-05: with a usable lua tool → null.
    fun testUsableRuntimeNoBanner() {
        usableRuntimeTool()
        assertNull("a usable runtime suppresses the runtime banner", collectPanel(luaFile()))
    }
}
