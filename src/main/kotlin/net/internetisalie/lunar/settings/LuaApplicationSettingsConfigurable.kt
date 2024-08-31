package net.internetisalie.lunar.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import net.internetisalie.lunar.lang.LuaFileType
import javax.swing.JComponent

class LuaApplicationSettingsConfigurable : Configurable {
    private var myOptionsPanel: LuaOptionsPanel? = null

    override fun getDisplayName(): @ConfigurableName String {
        return LuaFileType.name
    }


    override fun createComponent(): JComponent? {
        myOptionsPanel = LuaOptionsPanel()
        myOptionsPanel!!.setData(LuaApplicationSettings.instance.state)
        return myOptionsPanel!!.mainPanel
    }

    override fun isModified(): Boolean {
        return myOptionsPanel!!.isModified(LuaApplicationSettings.instance.state)
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        myOptionsPanel!!.apply(LuaApplicationSettings.instance.state)
    }
}
