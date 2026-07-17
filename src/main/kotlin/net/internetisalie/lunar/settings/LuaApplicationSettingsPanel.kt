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

import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBCheckBox
import net.internetisalie.lunar.LuaBundle
import javax.swing.JPanel

/**
 * Application-level Lua settings page (interim slimming, TOOLING-05 §6.3). The interpreters table was
 * removed — external Lua runtimes are now registered/discovered through the toolchain subsystem
 * (TOOLING-01/02) and surfaced by the Toolchain page (TOOLING-06). Only the editor-feature toggles
 * remain here.
 *
 * TOOLING-08 §2.7 (BUG-369): rebuilt on the Kotlin UI DSL (`panel { }`), replacing the FormBuilder /
 * IdeBorderFactory layout. The checkboxes are the buffer (clone-edit-commit, review #44): [setData]
 * loads a copied [LuaApplicationSettings.State] into the UI, [isModified] compares UI vs that copy,
 * and [getData] commits UI → the live state only when the configurable's `apply()` runs — so Cancel
 * never touches persisted state. The public API is unchanged so callers stay untouched.
 */
class LuaApplicationSettingsPanel {
    private val enableTypeInference = JBCheckBox(LuaBundle.message("application.enableTypeInference"))
    private val addAdditionalCompletionsCheckBox =
        JBCheckBox(LuaBundle.message("application.addAdditionalCompletions"))

    val mainPanel: JPanel = com.intellij.ui.dsl.builder.panel {
        group("Editor Features") {
            row { cell(enableTypeInference) }
            row { cell(addAdditionalCompletionsCheckBox) }
        }
    }

    fun apply(state: LuaApplicationSettings.State) {
        getData(state)
    }

    fun reset() {
        setData(LuaApplicationSettings.instance.state)
    }

    fun setData(data: LuaApplicationSettings.State) {
        addAdditionalCompletionsCheckBox.isSelected = data.includeAllFieldsInCompletions
        enableTypeInference.isSelected = data.enableTypeInference
    }

    fun getData(data: LuaApplicationSettings.State) {
        data.includeAllFieldsInCompletions = addAdditionalCompletionsCheckBox.isSelected
        data.enableTypeInference = enableTypeInference.isSelected
        if (data.enableTypeInference) {
            for (project in ProjectManager.getInstance().openProjects) {
                PsiManager.getInstance(project).dropResolveCaches()
            }
        }
    }

    fun isModified(data: LuaApplicationSettings.State): Boolean {
        if (addAdditionalCompletionsCheckBox.isSelected != data.includeAllFieldsInCompletions) return true
        return enableTypeInference.isSelected != data.enableTypeInference
    }
}
