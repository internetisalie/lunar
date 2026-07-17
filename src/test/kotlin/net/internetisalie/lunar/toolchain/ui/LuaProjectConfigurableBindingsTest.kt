package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * TOOLING-08 Phase 3: the common/advanced bindings split and platform-server eviction (TC 6, 7).
 * The classifier tiers are unit-tested in [LuaToolKindClassifierTest]; here we assert the rendered
 * project page reflects them and that `redis-server` still resolves despite its UI eviction.
 */
class LuaProjectConfigurableBindingsTest : ToolchainSettingsTestCase() {

    override fun tearDown() {
        try {
            EdtTestUtil.runInEdtAndWait<RuntimeException> {
                LuaProjectSettings.getInstance(project).state.explicitTarget = false
                LuaProjectSettings.getInstance(project).setTargetAndNotify(
                    PlatformVersionRegistry.resolveTarget(LuaPlatform.STANDARD, "5.4")
                )
            }
        } finally {
            super.tearDown()
        }
    }

    fun testBindingRowsExcludePlatformServers_TC6() {
        withConfigurable { _, panel ->
            val rowLabels = UIUtil.findComponentsOfType(panel, JLabel::class.java).map { it.text }
            assertFalse("Redis Server row must be absent", rowLabels.any { it.contains("Redis Server") })
            assertFalse("Valkey Server row must be absent", rowLabels.any { it.contains("Valkey Server") })
            // Common tools present as rows.
            assertTrue(rowLabels.any { it.startsWith("LuaRocks") })
            assertTrue(rowLabels.any { it.startsWith("luacheck") })
        }
    }

    fun testAdvancedToolRowRendered_TC6() {
        seedTool("luacov", usable = true)
        withConfigurable { _, panel ->
            // The advanced LuaCov binding combo is built (collapsed-by-default state is a VNC gate per
            // risks-and-gaps "Test Case Gaps"; DR-01 confirms collapsibleGroup defaults to collapsed).
            val comboItems = combos(panel).flatMap { combo ->
                (0 until combo.itemCount).mapNotNull { combo.getItemAt(it) as? LuaBindingItem.Tool }
            }
            assertTrue(comboItems.any { it.tool.kindId == "luacov" })
        }
    }

    fun testRedisServerStillResolvesDespiteEviction_TC7() {
        val redisTool = seedTool("redis-server", usable = true)
        val resolved = LuaToolResolver.getInstance().resolve(project, "redis-server")
        assertNotNull("redis-server must still resolve", resolved)
        assertEquals(redisTool.id, resolved?.id)
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
}
