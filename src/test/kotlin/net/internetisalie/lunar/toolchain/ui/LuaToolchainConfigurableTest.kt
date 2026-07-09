package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
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
 * TOOLING-06 Phase 1 unit coverage for the Toolchain application page (§2.1/§2.2). Covers
 * requirements TC 3, 4, 5, 6, 13. EP-registration assertions (TC 1/2) are owned by Phase 3.
 */
class LuaToolchainConfigurableTest : ToolchainSettingsTestCase() {

    fun testInventoryTableColumnsAndValues_TC3() {
        seedRuntimeTool()
        seedLuacheckTool()

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

        assertEquals("--std max", registry.kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS))
        synchronized(events) {
            val optionEvents = events.filter { it.optionKey == LuaKindOptionKeys.LUACHECK_ARGUMENTS }
            assertEquals(1, optionEvents.size)
        }
    }

    fun testResetRestoresAppliedArguments_TC6() {
        registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")

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

        ApplicationManager.getApplication().executeOnPooledThread {
            probeThread.set(Thread.currentThread())
            registry.refreshTool(tool.id)
            latch.countDown()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        val runner = probeThread.get()
        assertNotNull(runner)
        assertFalse("Re-check must probe off the EDT", runner.name.contains("AWT-EventQueue"))
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
}
