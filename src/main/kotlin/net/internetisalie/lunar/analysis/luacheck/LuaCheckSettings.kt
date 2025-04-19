package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "LuaCheckSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaCheckSettings : SimplePersistentStateComponent<LuaCheckSettings.State>(State()) {
    class State : BaseState() {
        var executablePath by string("/usr/local/bin/luacheck")
        var arguments by string("")

        val valid: Boolean get() = !StringUtil.isEmpty(executablePath)
    }

    companion object {
        fun getInstance(): LuaCheckSettings {
            return ApplicationManager.getApplication()
                .getService(LuaCheckSettings::class.java)
        }
    }
}
