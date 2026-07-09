package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * TOOLING-06 unit coverage for the Toolchain application page (§2.1/§2.2) and the consolidated
 * settings-tree registration. Covers requirements TC 1, 2 (EP-registration tree, Phase 3) and TC 3,
 * 4, 5, 6, 13 (Phase 1).
 *
 * NOTE: JUnit3 auto-suites the `test*` methods reflectively and rejects any non-public method whose
 * name also matches `test*`. A non-inline lambda (e.g. `EdtTestUtil.runInEdtAndWait`, which takes a
 * Java `ThrowableRunnable` SAM) compiles to a synthetic static method named after its enclosing
 * function, so such lambdas must live in private helpers NOT prefixed with "test" to avoid emitting a
 * `test…$lambda$N` synthetic the scanner would reject (surfaces only in the full suite).
 */
class LuaToolchainConfigurableTest : ToolchainSettingsTestCase() {

    fun testConsolidatedTreeRegistration_TC1() {
        val appEp = applicationConfigurableEps().singleOrNull {
            it.id == "net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable"
        }
        assertNotNull("Toolchain application configurable must be registered", appEp)
        assertEquals(APPLICATION_PARENT_ID, appEp?.parentId)

        val projectEp = projectConfigurableEps().singleOrNull {
            it.id == "net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
        }
        assertNotNull("Lua Project project configurable must be registered", projectEp)
        assertEquals(APPLICATION_PARENT_ID, projectEp?.parentId)
        assertTrue("Lua Project must be non-default-project", projectEp?.nonDefaultProject == true)
    }

    fun testLegacyConfigurablesAbsent_TC2() {
        val registeredIds = (applicationConfigurableEps() + projectConfigurableEps())
            .mapNotNull { it.id }
            .toSet()
        LEGACY_CONFIGURABLE_IDS.forEach { legacyId ->
            assertFalse("Legacy configurable must be removed: $legacyId", legacyId in registeredIds)
        }
    }

    fun testInventoryTableColumnsAndValues_TC3() {
        seedRuntimeTool()
        seedLuacheckTool()
        assertInventoryTableColumnsAndValues()
    }

    private fun assertInventoryTableColumnsAndValues() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val table = LuaToolchainInventoryTable()
            val model = table.model()

            val columnNames = (0 until model.columnCount).map { model.getColumnName(it) }
            assertEquals(listOf("Kind", "Name", "Path", "Version", "Origin", "Health"), columnNames)
            assertEquals(2, model.rowCount)

            val luaIndex = model.items.indexOfFirst { it.kindId == "lua" }
            assertEquals("Lua", model.getValueAt(luaIndex, columnNames.indexOf("Kind")))
            assertEquals("Lua 5.4.6", model.getValueAt(luaIndex, columnNames.indexOf("Name")))
            assertEquals("Discovered", model.getValueAt(luaIndex, columnNames.indexOf("Origin")))
            assertEquals("OK", healthCell(model.items[luaIndex].health).text)
        }
    }

    fun testHealthCellMissing_TC4() {
        val missing = LuaToolHealth(
            fileExists = false,
            executable = true,
            probeOk = null,
            probedAtMtime = null,
            reason = "binary not found at /gone"
        )

        val cell = healthCell(missing)

        assertEquals("Missing", cell.text)
        assertEquals("binary not found at /gone", cell.tooltip)
    }

    fun testLuacheckArgumentsApplyFiresTopicOnce_TC5() {
        val events = recordEvents()
        applyLuacheckArgumentsOnEdt()

        assertEquals("--std max", registry.kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS))
        synchronized(events) {
            val optionEvents = events.filter { it.optionKey == LuaKindOptionKeys.LUACHECK_ARGUMENTS }
            assertEquals(1, optionEvents.size)
        }
    }

    private fun applyLuacheckArgumentsOnEdt() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val configurable = LuaToolchainConfigurable()
            val panel = configurable.createComponent() as DialogPanel
            try {
                assertFalse(configurable.isModified)

                setLuacheckField(panel, "--std max")
                assertTrue(configurable.isModified)

                configurable.apply()
                assertFalse(configurable.isModified)
            } finally {
                configurable.disposeUIResources()
            }
        }
    }

    fun testResetRestoresAppliedArguments_TC6() {
        registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")
        assertResetRestoresArgumentsOnEdt()
    }

    private fun assertResetRestoresArgumentsOnEdt() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val configurable = LuaToolchainConfigurable()
            val panel = configurable.createComponent() as DialogPanel
            try {
                setLuacheckField(panel, "junk --nonsense")
                assertTrue(configurable.isModified)

                configurable.reset()
                assertFalse(configurable.isModified)
                assertEquals("--std max", readLuacheckField(panel))
            } finally {
                configurable.disposeUIResources()
            }
        }
    }

    fun testRecheckProbesOffEdt_TC13() {
        val tool = seedLuacheckTool()
        val probeThread = AtomicReference<Thread>()
        val latch = CountDownLatch(1)

        refreshToolOffEdt(tool.id, probeThread, latch)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        val runner = probeThread.get()
        assertNotNull(runner)
        assertFalse("Re-check must probe off the EDT", runner.name.contains("AWT-EventQueue"))
    }

    private fun refreshToolOffEdt(toolId: String, probeThread: AtomicReference<Thread>, latch: CountDownLatch) {
        ApplicationManager.getApplication().executeOnPooledThread {
            probeThread.set(Thread.currentThread())
            registry.refreshTool(toolId)
            latch.countDown()
        }
    }

    private fun luacheckField(panel: DialogPanel): ExpandableTextField =
        UIUtil.findComponentOfType(panel, ExpandableTextField::class.java)
            ?: error("luacheck arguments field not found")

    private fun setLuacheckField(panel: DialogPanel, value: String) {
        luacheckField(panel).text = value
    }

    private fun readLuacheckField(panel: DialogPanel): String = luacheckField(panel).text

    private fun seedRuntimeTool(): LuaRegisteredTool {
        val runtime = runtimeInfo(LuaPlatform.STANDARD, "5.4.6", LuaLanguageLevel.LUA54)
            .copy(product = "Lua")
        val tool = seededModel(kindId = "lua", origin = Origin.DISCOVERED, runtime = runtime)
        registry.registerProvisioned(tool)
        return tool
    }

    private fun seedLuacheckTool(): LuaRegisteredTool {
        val tool = seededModel(kindId = "luacheck", origin = Origin.DISCOVERED, runtime = null)
        registry.registerProvisioned(tool)
        return tool
    }

    private fun seededModel(
        kindId: String,
        origin: Origin,
        runtime: LuaRuntimeInfo?
    ): LuaRegisteredTool {
        val id = UUID.randomUUID().toString()
        return LuaRegisteredTool(
            id = id,
            kindId = kindId,
            path = "/seed/$kindId/$id",
            version = if (kindId == "luacheck") "1.1.0" else "5.4.6",
            luaVersion = null,
            runtime = runtime,
            origin = origin,
            environmentId = null,
            health = LuaToolHealth(
                fileExists = true,
                executable = true,
                probeOk = true,
                probedAtMtime = 1L,
                reason = null
            )
        )
    }

    private fun applicationConfigurableEps(): List<ConfigurableEP<Configurable>> =
        Configurable.APPLICATION_CONFIGURABLE.extensionList

    private fun projectConfigurableEps(): List<ConfigurableEP<Configurable>> =
        Configurable.PROJECT_CONFIGURABLE.getExtensions(project)

    private companion object {
        const val APPLICATION_PARENT_ID = "net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"

        val LEGACY_CONFIGURABLE_IDS = listOf(
            "net.internetisalie.lunar.tool.ui.LuaToolsConfigurable",
            "net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable",
            "net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel",
            "net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable"
        )
    }
}
