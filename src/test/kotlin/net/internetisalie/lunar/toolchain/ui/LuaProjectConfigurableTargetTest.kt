package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import javax.swing.JComponent

/**
 * TOOLING-08 Phase 2: the buffered *Platform Target* control on the *Lua Project* page (§3.2/§3.3).
 * Covers TC 1–5 (Auto default, Redis repopulation, explicit persist, Auto-return). Synchronizer
 * no-op on an explicit target (TC 4) is covered in [net.internetisalie.lunar.toolchain.resolve.LuaTargetSynchronizerTest].
 */
class LuaProjectConfigurableTargetTest : ToolchainSettingsTestCase() {

    override fun tearDown() {
        try {
            // MAINT-23 lesson: restore the default target + clear the explicit pin so it does not leak.
            EdtTestUtil.runInEdtAndWait<RuntimeException> {
                val state = LuaProjectSettings.getInstance(project).state
                state.explicitTarget = false
                LuaProjectSettings.getInstance(project).setTargetAndNotify(
                    PlatformVersionRegistry.resolveTarget(LuaPlatform.STANDARD, "5.4")
                )
            }
        } finally {
            super.tearDown()
        }
    }

    fun testAutoDefaultDisablesVersionCombo_TC1() {
        withConfigurable { _, panel ->
            val platform = platformCombo(panel)
            val version = versionCombo(panel)
            assertTrue("Platform combo selects Auto", platform.selectedItem is TargetItem.Auto)
            assertFalse("Version combo disabled in Auto", version.isEnabled)
        }
    }

    fun testRedisSelectionRepopulatesVersions_TC2() {
        withConfigurable { _, panel ->
            val platform = platformCombo(panel)
            selectPlatform(platform, LuaPlatform.REDIS)
            val labels = versionLabels(versionCombo(panel))
            assertEquals(listOf("5", "6", "7+"), labels)
            assertTrue(versionCombo(panel).isEnabled)
        }
    }

    fun testExplicitRedisTargetPersists_TC3() {
        withConfigurable { configurable, panel ->
            val platform = platformCombo(panel)
            selectPlatform(platform, LuaPlatform.REDIS)
            selectVersion(versionCombo(panel), "7+")
            assertTrue(configurable.isModified)
            configurable.apply()
        }

        val state = LuaProjectSettings.getInstance(project).state
        assertTrue("explicitTarget must be set", state.explicitTarget)
        assertEquals(LuaPlatform.REDIS, state.getTarget().platform)
        assertEquals("7+", state.getTarget().version.label)
    }

    fun testAutoReturnClearsExplicitFlag_TC5() {
        // Given an explicit Redis pin
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(PlatformVersionRegistry.resolveTarget(LuaPlatform.REDIS, "7+"))
            settings.state.explicitTarget = true
        }

        withConfigurable { configurable, panel ->
            val platform = platformCombo(panel)
            assertTrue("Reset selects the pinned Redis", platform.selectedItem is TargetItem.Platform)
            selectPlatformItem(platform, TargetItem.Auto)
            assertTrue(configurable.isModified)
            configurable.apply()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        assertFalse(LuaProjectSettings.getInstance(project).state.explicitTarget)
    }

    fun testUnchangedAutoIsNotModified() {
        withConfigurable { configurable, _ ->
            assertFalse(configurable.isModified)
        }
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

    private fun platformCombo(panel: JComponent): ComboBox<*> =
        combos(panel).first { combo -> (0 until combo.itemCount).any { combo.getItemAt(it) is TargetItem } }

    private fun versionCombo(panel: JComponent): ComboBox<*> =
        combos(panel).first { combo -> (0 until combo.itemCount).any { combo.getItemAt(it) is VersionEntry } }

    private fun versionLabels(combo: ComboBox<*>): List<String> =
        (0 until combo.itemCount).mapNotNull { (combo.getItemAt(it) as? VersionEntry)?.label }

    private fun selectPlatform(combo: ComboBox<*>, platform: LuaPlatform) =
        selectPlatformItem(combo, TargetItem.Platform(platform))

    private fun selectPlatformItem(combo: ComboBox<*>, item: TargetItem) {
        for (i in 0 until combo.itemCount) {
            if (combo.getItemAt(i) == item) {
                combo.selectedIndex = i
                return
            }
        }
        error("platform item $item not in combo")
    }

    private fun selectVersion(combo: ComboBox<*>, label: String) {
        for (i in 0 until combo.itemCount) {
            if ((combo.getItemAt(i) as? VersionEntry)?.label == label) {
                combo.selectedIndex = i
                return
            }
        }
        error("version $label not in combo")
    }
}
