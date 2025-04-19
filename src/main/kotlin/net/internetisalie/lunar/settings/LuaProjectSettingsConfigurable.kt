package net.internetisalie.lunar.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import net.internetisalie.lunar.lang.LuaFileType
import javax.swing.JComponent

class LuaProjectSettingsConfigurable(val project : Project) : Configurable {
    private var mySettingsPanel: LuaProjectSettingsPanel? = null

    override fun getDisplayName(): @ConfigurableName String {
        return LuaFileType.name
    }

    override fun createComponent(): JComponent? {
        mySettingsPanel = LuaProjectSettingsPanel(project)
        mySettingsPanel!!.setData(LuaProjectSettings.getInstance(project).state)
        return mySettingsPanel!!.mainPanel
    }

    override fun isModified(): Boolean {
        return mySettingsPanel!!.isModified(LuaProjectSettings.getInstance(project).state)
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        mySettingsPanel!!.apply(LuaProjectSettings.getInstance(project).state)
    }
}
