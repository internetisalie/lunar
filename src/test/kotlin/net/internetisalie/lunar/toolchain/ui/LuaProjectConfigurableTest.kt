package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import javax.swing.JComponent

/**
 * TOOLING-06 Phase 2 unit coverage for the rewritten *Lua Project* page (§2.3–§3.6). Covers TC 7,
 * 8, 9, 10, 11, 12, 14 — including the silent-apply regression (TC 11) and the no-op-apply silence
 * guard (TC 12). EP-registration assertions (TC 1/2) are owned by Phase 3.
 */
class LuaProjectConfigurableTest : ToolchainSettingsTestCase() {

    fun testBindingComboAppliesFiresTopic_TC7() {
        seedTool(kindId = "luacheck")
        val toolB = seedTool(kindId = "luacheck")
        val events = recordEvents()

        withConfigurable { configurable, panel ->
            val combo = bindingComboForTool(panel, toolB.id)
            assertTrue(combo.selectedItem is LuaBindingItem.Inherit)
            selectToolInCombo(combo, toolB.id)
            assertTrue(configurable.isModified)
            configurable.apply()
        }

        assertEquals(toolB.id, settings.binding("luacheck"))
        synchronized(events) {
            assertTrue(events.any { it.kindId == "luacheck" })
        }
    }

    fun testEnvironmentComboAppliesFiresTopic_TC8() {
        settings.upsertEnvironment(LuaEnvironmentState(name = "E1", rootDir = "/env/e1"))
        val e2 = settings.upsertEnvironment(LuaEnvironmentState(name = "E2", rootDir = "/env/e2"))
        assertNull(settings.activeEnvironment())
        val events = recordEvents()

        withConfigurable { configurable, panel ->
            val combo = environmentCombo(panel)
            selectEnvironmentInCombo(combo, e2.id)
            assertTrue(configurable.isModified)
            configurable.apply()
        }

        assertEquals(e2.id, settings.activeEnvironment()?.id)
        synchronized(events) {
            assertTrue(events.any { it.environmentId == e2.id })
        }
    }

    fun testResolvedRuntimeDisplayForBoundRuntime_TC9() {
        val runtime = runtimeInfo(LuaPlatform.STANDARD, "5.4.6", LuaLanguageLevel.LUA54)
            .copy(product = "Lua")
        val luaTool = seedTool(kindId = "lua", runtime = runtime)

        withConfigurable { _, panel ->
            val runtimeLabel = labels(panel)
            assertTrue(runtimeLabel.any { it.text.contains(luaTool.path) && it.text.contains("Lua 5.4.6") })
            assertTrue(runtimeLabel.any { it.text == "Lua 5.4" })
            assertNoInterpreterEraControls(panel)
        }
    }

    fun testResolvedRuntimeDisplayFallback_TC10() {
        withConfigurable { _, panel ->
            val labels = labels(panel)
            assertTrue(labels.any { it.text == "No runtime configured" })
            val defaultLevel = "${Target.default().getImplicitLanguageLevel()} (default)"
            assertTrue(labels.any { it.text == defaultLevel })
        }
    }

    fun testSourcePathApplyFiresSettingsTopic_TC11() {
        var fired = false
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    fired = true
                }
            }
        )

        withConfigurable { configurable, panel ->
            sourcePathField(panel).text = "src/?.lua"
            assertTrue(configurable.isModified)
            configurable.apply()
        }

        assertEquals("src/?.lua", LuaProjectSettings.getInstance(project).state.sourcePath)
        assertTrue(fired)
    }

    fun testNoOpApplyFiresNothing_TC12() {
        val events = recordEvents()
        var settingsFired = false
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    settingsFired = true
                }
            }
        )

        withConfigurable { configurable, _ ->
            assertFalse(configurable.isModified)
            configurable.apply()
        }

        synchronized(events) { assertTrue(events.isEmpty()) }
        assertFalse(settingsFired)
    }

    fun testProjectLuacheckOverrideRoundTrips_TC14() {
        registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")
        val events = recordEvents()

        withConfigurable { configurable, panel ->
            luacheckArgsField(panel).text = "--globals foo"
            assertTrue(configurable.isModified)
            configurable.apply()
        }
        assertEquals("--globals foo", settings.state.kindOptions[LuaKindOptionKeys.LUACHECK_ARGUMENTS])
        synchronized(events) {
            assertTrue(events.any { it.optionKey == LuaKindOptionKeys.LUACHECK_ARGUMENTS })
        }

        withConfigurable { configurable, panel ->
            luacheckArgsField(panel).text = ""
            assertTrue(configurable.isModified)
            configurable.apply()
        }
        assertNull(settings.state.kindOptions[LuaKindOptionKeys.LUACHECK_ARGUMENTS])
    }

    private fun withConfigurable(action: (LuaProjectConfigurable, DialogPanel) -> Unit) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val configurable = LuaProjectConfigurable(project)
            val panel = configurable.createComponent() as DialogPanel
            try {
                action(configurable, panel)
            } finally {
                configurable.disposeUIResources()
            }
        }
    }

    private fun combos(panel: JComponent): List<ComboBox<*>> =
        UIUtil.findComponentsOfType(panel, ComboBox::class.java)

    private fun labels(panel: JComponent) =
        UIUtil.findComponentsOfType(panel, com.intellij.ui.components.JBLabel::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun environmentCombo(panel: JComponent): ComboBox<LuaEnvironmentItem> =
        combos(panel).first { comboHoldsEnvironmentItems(it) } as ComboBox<LuaEnvironmentItem>

    private fun comboHoldsEnvironmentItems(combo: ComboBox<*>): Boolean =
        (0 until combo.itemCount).any { combo.getItemAt(it) is LuaEnvironmentItem }

    @Suppress("UNCHECKED_CAST")
    private fun bindingComboForTool(panel: JComponent, toolId: String): ComboBox<LuaBindingItem> =
        combos(panel).first { combo ->
            (0 until combo.itemCount).any {
                val item = combo.getItemAt(it)
                item is LuaBindingItem.Tool && item.tool.id == toolId
            }
        } as ComboBox<LuaBindingItem>

    private fun selectToolInCombo(combo: ComboBox<LuaBindingItem>, toolId: String) {
        for (i in 0 until combo.itemCount) {
            val item = combo.getItemAt(i)
            if (item is LuaBindingItem.Tool && item.tool.id == toolId) {
                combo.selectedIndex = i
                return
            }
        }
        error("tool $toolId not in combo")
    }

    private fun selectEnvironmentInCombo(combo: ComboBox<LuaEnvironmentItem>, envId: String) {
        for (i in 0 until combo.itemCount) {
            val item = combo.getItemAt(i)
            if (item is LuaEnvironmentItem.Env && item.env.id == envId) {
                combo.selectedIndex = i
                return
            }
        }
        error("environment $envId not in combo")
    }

    private fun expandableFields(panel: JComponent): List<ExpandableTextField> =
        UIUtil.findComponentsOfType(panel, ExpandableTextField::class.java)

    // Layout order (§2.3): the Luacheck group's args field precedes the Source & Completion
    // source-path field, so the two ExpandableTextFields appear in that fixed order.
    private fun luacheckArgsField(panel: JComponent): ExpandableTextField =
        expandableFields(panel)[0]

    private fun sourcePathField(panel: JComponent): ExpandableTextField =
        expandableFields(panel)[1]

    private fun assertNoInterpreterEraControls(panel: JComponent) {
        val texts = UIUtil.findComponentsOfType(panel, JBTextField::class.java).map { it.text }
        assertTrue(UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).isNotEmpty())
        assertFalse(texts.any { it.contains("interpreter", ignoreCase = true) })
        assertNoPlatformCombo(panel)
    }

    private fun assertNoPlatformCombo(panel: JComponent) {
        val platformCombos = combos(panel).filter { combo ->
            (0 until combo.itemCount).any { combo.getItemAt(it) is LuaPlatform }
        }
        assertTrue("Platform combo must be gone", platformCombos.isEmpty())
    }
}
