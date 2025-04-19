/*
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import net.internetisalie.lunar.platform.LuaInterpreter

/**
 * Created by IntelliJ IDEA.
 * User: Jon S Akhtar
 * Date: Sep 19, 2010
 * Time: 5:33:53 PM
 */
@Service(Service.Level.APP)
@State(
    name = "LuaApplicationSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaApplicationSettings : PersistentStateComponent<LuaApplicationSettings.State> {
    class State {
        var includeAllFieldsInCompletions: Boolean = false
        var enableTypeInference: Boolean = true
        var interpreters: List<LuaInterpreter> = ArrayList()
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: LuaApplicationSettings
            get() = ApplicationManager.getApplication()
                .getService(LuaApplicationSettings::class.java)

        fun findInterpreter(interpreterPath : String) : LuaInterpreter? {
            return instance.state.interpreters.firstOrNull { interpreterPath == it.path }
        }

        fun validInterpreters() : List<LuaInterpreter> {
            return instance.state.interpreters.filter { it.valid }
        }
    }
}
