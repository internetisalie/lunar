/*
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
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

import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryProvider
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Created by IntelliJ IDEA.
 * User: Jon S Akhtar
 * Date: Apr 20, 2010
 * Time: 7:08:52 PM
 */
class LuaProjectSettingsPanel {
    val mainPanel: JPanel
    private val platform : JComboBox<LuaPlatform>
    private val languageLevel : JComboBox<LuaLanguageLevel>

    init {
        platform = JComboBox<LuaPlatform>(arrayOf(
            LuaPlatform.PUC,
            LuaPlatform.LUAU,
            LuaPlatform.LOVE,
            LuaPlatform.PANDOC,
            LuaPlatform.REDIS,
        ))

        languageLevel = JComboBox<LuaLanguageLevel>(arrayOf(
            LuaLanguageLevel.LUA50,
            LuaLanguageLevel.LUA51,
            LuaLanguageLevel.LUA52,
            LuaLanguageLevel.LUA53,
            LuaLanguageLevel.LUA54,
        ))

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform", platform, 0)
            .addLabeledComponent("Language level", languageLevel, 2)
            .addComponentFillVertically(JPanel(), 2)
            .panel
    }

    fun apply(state: LuaProjectSettings.State) {
        val originalLanguageLevel = state.languageLevel
        getData(state)
        if (state.languageLevel !== originalLanguageLevel) {
            PlatformLibraryProvider.reload()
        }
    }

    fun reset() {
        setData(LuaProjectSettings.instance.state)
    }

    fun setData(data: LuaProjectSettings.State) {
        languageLevel.selectedItem = data.languageLevel
        platform.selectedItem = data.platform
    }

    fun getData(data: LuaProjectSettings.State) {
        data.platform = platform.selectedItem as LuaPlatform
        data.languageLevel = languageLevel.selectedItem as LuaLanguageLevel
    }

    fun isModified(data: LuaProjectSettings.State): Boolean {
        if (platform.selectedItem != data.platform) return true
        if (languageLevel.selectedItem != data.languageLevel) return true
        return false
    }
}
