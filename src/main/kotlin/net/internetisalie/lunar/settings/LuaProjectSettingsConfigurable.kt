package net.internetisalie.lunar.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import net.internetisalie.lunar.lang.LuaFileType
import javax.swing.JComponent

class LuaProjectSettingsConfigurable : Configurable {
    private var mySettingsPanel: LuaProjectSettingsPanel? = null

    override fun getDisplayName(): @ConfigurableName String {
        return LuaFileType.name
    }


    override fun createComponent(): JComponent? {
        mySettingsPanel = LuaProjectSettingsPanel()
        mySettingsPanel!!.setData(LuaProjectSettings.instance.state)
        return mySettingsPanel!!.mainPanel
    }

    override fun isModified(): Boolean {
        return mySettingsPanel!!.isModified(LuaProjectSettings.instance.state)
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        mySettingsPanel!!.apply(LuaProjectSettings.instance.state)
    }
}
