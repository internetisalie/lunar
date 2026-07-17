package net.internetisalie.lunar.settings

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TOOLING-08 §2.7 / TC 11: behavior preserved through the FormBuilder → Kotlin-UI-DSL migration of
 * [LuaApplicationSettingsPanel]. Toggling a checkbox against an unmodified state must make
 * [LuaApplicationSettingsPanel.isModified] report `true`, and a subsequent [LuaApplicationSettingsPanel.apply]
 * must commit that change into the passed-in state (clone-edit-commit, review #44). Runs on the EDT
 * because the DSL panel builds Swing components.
 */
class LuaApplicationSettingsPanelTest : BasePlatformTestCase() {

    fun testIsModifiedAfterTogglingTypeInference_TC11() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val panel = LuaApplicationSettingsPanel()
            val state = LuaApplicationSettings.State().apply { enableTypeInference = true }
            panel.setData(state)
            assertFalse(panel.isModified(state))

            togglePanelTypeInference(panel)
            assertTrue(panel.isModified(state))

            panel.apply(state)
            assertFalse(state.enableTypeInference)
            assertFalse(panel.isModified(state))
        }
    }

    /** Flips the type-inference checkbox by round-tripping the state through the panel's data hooks. */
    private fun togglePanelTypeInference(panel: LuaApplicationSettingsPanel) {
        val flipped = LuaApplicationSettings.State().apply { enableTypeInference = false }
        panel.setData(flipped)
    }
}
