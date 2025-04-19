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
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.platform.LuaInterpreterFamily
import net.internetisalie.lunar.platform.LuaInterpreterService
import net.internetisalie.lunar.util.newAppBackgroundTask
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * Created by IntelliJ IDEA.
 * User: Jon S Akhtar
 * Date: Apr 20, 2010
 * Time: 7:08:52 PM
 */
class LuaApplicationSettingsPanel {
    val mainPanel: JPanel
    private val addAdditionalCompletionsCheckBox: JCheckBox
    private val enableTypeInference: JCheckBox
    private val interpretersTable: LuaInterpretersTable

    init {
        enableTypeInference = JCheckBox(LuaBundle.message("application.enableTypeInference"))
        addAdditionalCompletionsCheckBox = JCheckBox(LuaBundle.message("application.addAdditionalCompletions"))

        val editorPanel = FormBuilder.createFormBuilder()
            .addComponent(enableTypeInference, 2)
            .addComponent(addAdditionalCompletionsCheckBox, 2)
            .panel
        editorPanel.border = IdeBorderFactory.createTitledBorder("Editor Features", false)

        val interpretersPanel = JPanel(BorderLayout())
        interpretersPanel.border = IdeBorderFactory.createTitledBorder("Lua Interpreters", false)
        interpretersTable = LuaInterpretersTable()
        interpretersPanel.add(interpretersTable.component)

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(editorPanel, 0)
            .addComponentFillVertically(interpretersPanel, 2)
            .panel
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
        interpretersTable.setValues(data.interpreters)

        for (interpreter in data.interpreters) {
            if (LuaInterpreterFamily.UNKNOWN_PRODUCT == interpreter.product)
                newAppBackgroundTask(LuaBundle.message("action.inspect.interpreter")) {
                    LuaInterpreterService.getInstance().identify(interpreter)
                    interpretersTable.refreshValues()
                }.queue()
        }
    }

    fun getData(data: LuaApplicationSettings.State) {
        data.includeAllFieldsInCompletions = addAdditionalCompletionsCheckBox.isSelected
        data.enableTypeInference = enableTypeInference.isSelected
        if (data.enableTypeInference) {
            for (project in ProjectManager.getInstance().openProjects) PsiManager.getInstance(
                project
            ).dropResolveCaches()
        }

        data.interpreters = interpretersTable.tableView.items
    }

    fun isModified(data: LuaApplicationSettings.State): Boolean {
        if (addAdditionalCompletionsCheckBox.isSelected != data.includeAllFieldsInCompletions) return true
        if (enableTypeInference.isSelected != data.enableTypeInference) return true
        if (interpretersTable.tableView.items != data.interpreters) return true

        return false
    }
}
