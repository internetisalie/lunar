package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

/**
 * TOOLING-08 §3.5 unit coverage for the *Global Default Bindings* group on the app *Toolchain*
 * page: selecting an inventory tool writes through [net.internetisalie.lunar.toolchain.registry.
 * LuaToolchainRegistry.setGlobalBinding] on apply (TC 8) and choosing *Inherit* clears the binding
 * (TC 9). Combos are driven through the real [LuaToolchainConfigurable] lifecycle (create → mutate
 * → apply) on the EDT, mirroring the existing [LuaToolchainConfigurableTest] fixture pattern.
 */
class LuaToolchainConfigurableGlobalBindingsTest : ToolchainSettingsTestCase() {

    fun testSelectingToolWritesGlobalBinding_TC8() {
        val tool = seedTool("luacheck")
        assertGlobalBindingApplied(tool)
    }

    private fun assertGlobalBindingApplied(tool: LuaRegisteredTool) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val configurable = LuaToolchainConfigurable()
            val panel = configurable.createComponent() as DialogPanel
            try {
                assertFalse(configurable.isModified)

                selectBinding(panel, "luacheck", tool.id)
                assertTrue(configurable.isModified)

                configurable.apply()
                assertFalse(configurable.isModified)
                assertEquals(tool.id, registry.globalBindings()["luacheck"])
            } finally {
                configurable.disposeUIResources()
            }
        }
    }

    fun testSelectingInheritClearsGlobalBinding_TC9() {
        val tool = seedTool("luacheck")
        registry.setGlobalBinding("luacheck", tool.id)
        assertInheritClearsBinding()
    }

    private fun assertInheritClearsBinding() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val configurable = LuaToolchainConfigurable()
            val panel = configurable.createComponent() as DialogPanel
            try {
                assertFalse(configurable.isModified)

                selectInherit(panel, "luacheck")
                assertTrue(configurable.isModified)

                configurable.apply()
                assertFalse(configurable.isModified)
                assertFalse("luacheck" in registry.globalBindings())
            } finally {
                configurable.disposeUIResources()
            }
        }
    }

    private fun bindingCombo(panel: DialogPanel, kindId: String): ComboBox<*> =
        UIUtil.findComponentsOfType(panel, ComboBox::class.java).firstOrNull { combo ->
            (0 until combo.itemCount).any { index ->
                (combo.getItemAt(index) as? LuaBindingItem.Tool)?.tool?.kindId == kindId
            }
        } ?: error("global binding combo for $kindId not found")

    private fun selectBinding(panel: DialogPanel, kindId: String, toolId: String) {
        val combo = bindingCombo(panel, kindId)
        val item = (0 until combo.itemCount).map { combo.getItemAt(it) }
            .firstOrNull { (it as? LuaBindingItem.Tool)?.tool?.id == toolId }
            ?: error("tool $toolId not present in $kindId combo")
        combo.selectedItem = item
    }

    private fun selectInherit(panel: DialogPanel, kindId: String) {
        bindingCombo(panel, kindId).selectedItem = LuaBindingItem.Inherit
    }
}
