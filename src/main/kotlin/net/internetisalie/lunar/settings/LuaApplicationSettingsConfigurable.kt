package net.internetisalie.lunar.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import net.internetisalie.lunar.lang.LuaFileType
import javax.swing.JComponent

/**
 * App-level *Lua* settings page. TOOLING-08 (review #44): gains the full Configurable lifecycle —
 * [reset] re-loads the persisted state into the panel and [disposeUIResources] drops the UI ref.
 * The panel is the buffer: it only commits to the live [LuaApplicationSettings.State] in [apply], so
 * *Cancel* truly reverts (the UI never mutates persisted state pre-apply) and [isModified] compares
 * the UI against the live state honestly.
 */
class LuaApplicationSettingsConfigurable : Configurable {
    private var settingsPanel: LuaApplicationSettingsPanel? = null

    override fun getDisplayName(): @ConfigurableName String = LuaFileType.name

    override fun createComponent(): JComponent {
        val panel = LuaApplicationSettingsPanel()
        panel.setData(LuaApplicationSettings.instance.state)
        settingsPanel = panel
        return panel.mainPanel
    }

    override fun isModified(): Boolean =
        settingsPanel?.isModified(LuaApplicationSettings.instance.state) ?: false

    override fun reset() {
        settingsPanel?.reset()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settingsPanel?.apply(LuaApplicationSettings.instance.state)
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
