package net.internetisalie.lunar.run

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.ui.LuaRuntimeComboBox
import org.junit.jupiter.api.AfterEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestLuaRunConfiguration : BaseDocumentTest() {

    @AfterEach
    fun resetToolchain() {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(myFixture.project).loadState(LuaToolchainProjectState())
    }

    private fun seedRuntime(path: String): LuaRegisteredTool {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "lua",
            path = path,
            version = "5.4.0",
            luaVersion = "5.4",
            runtime = LuaRuntimeInfo("Lua", "5.4.0", LuaLanguageLevel.LUA54, LuaPlatform.STANDARD, "Lua 5.4.0"),
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool
    }

    @Test
    fun testOptionsPersistence() {
        val config = LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "Test Config")
        config.scriptName = "myscript.lua"
        config.workingDirectory = "/home/user/work"
        config.programArguments = "--arg1 val1"

        assertEquals("myscript.lua", config.scriptName)
        assertEquals("/home/user/work", config.workingDirectory)
        assertEquals("--arg1 val1", config.programArguments)
    }

    /** TC 10: unset config resolves to the project RUNTIME binding (the project default). */
    @Test
    fun testResolveInterpreterFallsBackToProjectBinding() {
        val project = myFixture.project
        val toolB = seedRuntime("/opt/luajit/bin/luajit")
        LuaToolchainProjectSettings.getInstance(project).setBinding("lua", toolB.id)

        val config = LuaRunConfiguration(project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")
        assertNull(config.interpreter)
        assertEquals("/opt/luajit/bin/luajit", config.resolveInterpreter()?.path)
    }

    /** TC 11: an explicit stored path (not registered) resolves to an ad-hoc runtime and wins. */
    @Test
    fun testExplicitPathWinsAsAdHoc() {
        val project = myFixture.project
        val toolB = seedRuntime("/opt/luajit/bin/luajit")
        LuaToolchainProjectSettings.getInstance(project).setBinding("lua", toolB.id)

        val config = LuaRunConfiguration(project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")
        config.interpreter = adHocRuntime("/x/custom-lua")
        assertEquals("/x/custom-lua", config.resolveInterpreter()?.path)
        assertEquals("/x/custom-lua", config.interpreter?.path)
    }

    /**
     * TC 9: the combo model lists the project default (B, initial selection) and registry runtime A,
     * de-duplicated by path; typing an unregistered path keeps an ad-hoc entry selected AND keeps B.
     */
    @Test
    fun testInterpreterComboOffersDefaultAndInventory() {
        val project = myFixture.project
        val toolA = seedRuntime("/opt/lua54/bin/lua")
        val toolB = seedRuntime("/opt/luajit/bin/luajit")
        LuaToolchainProjectSettings.getInstance(project).setBinding("lua", toolB.id)

        val combo = ComboBox<LuaRegisteredTool>()
        LuaRuntimeComboBox.customize(project, combo)

        val paths = modelPaths(combo)
        assertTrue(paths.contains(toolA.path), "model must contain inventory tool A; had $paths")
        assertTrue(paths.contains(toolB.path), "model must contain default tool B; had $paths")
        assertEquals(toolB.path, combo.item?.path, "initial selection is the project default B")

        val editorComponent = combo.editor.editorComponent as javax.swing.JTextField
        editorComponent.text = "/x/lua"
        val afterPaths = modelPaths(combo)
        assertTrue(afterPaths.contains("/x/lua"), "ad-hoc typed path must be present; had $afterPaths")
        assertTrue(afterPaths.contains(toolB.path), "default B must remain listed (ROCKS-16); had $afterPaths")
    }

    /** TC-06a (#26): the editor writes the Source-path field back on Apply and reloads it. */
    @Test
    fun testSourcePathRoundTripsThroughEditor() {
        val config = LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")
        config.sourcePath = "foo;bar"

        val editor = LuaRunSettingsEditor(myFixture.project)
        try {
            editor.resetFrom(config)

            val reapplied = LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg2")
            editor.applyTo(reapplied)

            assertEquals("foo;bar", reapplied.sourcePath)
        } finally {
            Disposer.dispose(editor)
        }
    }

    /** TC-06b (#56): an empty working directory resolves to the project base path. */
    @Test
    fun testEmptyWorkingDirectoryFallsBackToBasePath() {
        val config = LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")

        config.workingDirectory = ""
        assertEquals(myFixture.project.basePath, config.effectiveWorkDirectory())

        config.workingDirectory = "/explicit/dir"
        assertEquals("/explicit/dir", config.effectiveWorkDirectory())
    }

    /** TC-06c (#56): checkConfiguration rejects a config with no resolvable runtime. */
    @Test
    fun testCheckConfigurationThrowsWithoutRuntime() {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(myFixture.project).loadState(LuaToolchainProjectState())

        val config = LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")
        assertNull(config.resolveInterpreter())
        assertFailsWith<RuntimeConfigurationException> { config.checkConfiguration() }
    }

    private fun modelPaths(combo: ComboBox<LuaRegisteredTool>): List<String> =
        (0 until combo.model.size).mapNotNull { combo.model.getElementAt(it)?.path }
}
