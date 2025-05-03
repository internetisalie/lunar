/*
 * Copyright 2016 Jon S Akhtar (Sylvanaar)
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

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.ui.AnActionButton
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.LocalPathCellEditor
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterService
import net.internetisalie.lunar.util.newAppBackgroundTask
import net.internetisalie.lunar.util.newProjectBackgroundTask
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class LuaInterpretersTable : ListTableWithButtons<LuaInterpreter>() {
    private class CellModelBase(
        title: String,
        val editable: Boolean,
        val getter: (i: LuaInterpreter) -> String?,
        val setter: ((i: LuaInterpreter, value: String) -> Unit)? = null,
        val editor: ((i: LuaInterpreter) -> TableCellEditor?)? = null,
        val tooltip: ((i : LuaInterpreter) -> String?)? = null,
    ) : ElementsColumnInfoBase<LuaInterpreter>(title) {

        override fun valueOf(interpreter: LuaInterpreter): String {
            return getter(interpreter) ?: ""
        }

        override fun setValue(interpreter: LuaInterpreter, value: String) {
            setter?.invoke(interpreter, value)
        }

        override fun isCellEditable(interpreter: LuaInterpreter): Boolean {
            return editable
        }

        override fun getDescription(interpreter: LuaInterpreter): String? {
            return valueOf(interpreter)
        }

        override fun getEditor(luaInterpreter: LuaInterpreter): TableCellEditor? {
            return editor?.invoke(luaInterpreter)
        }

        override fun getRenderer(element: LuaInterpreter?): TableCellRenderer? {
            val renderer = super.getRenderer(element) as DefaultTableCellRenderer
            val tooltipGenerator = tooltip
            if (tooltipGenerator != null && element != null) {
                renderer.toolTipText = tooltipGenerator(element)
            }
            return renderer
        }
    }

    private var myListModel: ListTableModel<LuaInterpreter>? = null

    override fun createListModel(): ListTableModel<LuaInterpreter> {
        val path = CellModelBase(
            LuaBundle.message("application.interpreters.executable"), true,
            { luaInterpreter -> luaInterpreter.path },
            { luaInterpreter, path ->
                luaInterpreter.path = path
                newAppBackgroundTask(LuaBundle.message("action.inspect.interpreter")) {
                    LuaInterpreterService.getInstance().identify(luaInterpreter)
                    tableView.tableViewModel.items = elements
                    refresh()
                }.queue()
            },
            { luaInterpreter ->
                val chooserDescriptor = FileChooserDescriptor(
                    true, false, false, false, false, false
                )
                val cellEditor = LocalPathCellEditor(null)
                cellEditor.fileChooserDescriptor(chooserDescriptor)
                cellEditor
            })

        val product = CellModelBase(
            LuaBundle.message("application.interpreters.product"), false,
            { luaInterpreter ->
                if (!luaInterpreter.valid) "Invalid"
                else luaInterpreter.familyOrUnknown.interpreterName
            },
            null,
            null,
            { luaInterpreter ->
                if (!luaInterpreter.valid) luaInterpreter.banner
                else luaInterpreter.familyOrUnknown.interpreterName
            })

        val version = CellModelBase(
            LuaBundle.message("application.interpreters.version"), false,
            { luaInterpreter -> luaInterpreter.version })

        val platform = CellModelBase(
            LuaBundle.message("application.interpreters.platform"), false,
            { luaInterpreter -> luaInterpreter.platform }
        )

        val languageLevel = CellModelBase(
            LuaBundle.message("application.interpreters.languageLevel"), false,
            { luaInterpreter -> luaInterpreter.languageLevel }
        )

        val listModel = ListTableModel<LuaInterpreter>(path, product, version, platform, languageLevel)

        myListModel = listModel
        return listModel
    }

    override fun createElement(): LuaInterpreter {
        return LuaInterpreter()
    }

    override fun isEmpty(element: LuaInterpreter?): Boolean {
        return element?.path?.isEmpty() ?: true
    }

    override fun cloneElement(interpreter: LuaInterpreter): LuaInterpreter {
        return LuaInterpreter(interpreter)
    }

    override fun canDeleteElement(selection: LuaInterpreter): Boolean {
        return true
    }

    override fun createExtraToolbarActions(): Array<AnAction> {
        return arrayOf(
            object : AnActionButton("Re-scan", AllIcons.Actions.ForceRefresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    newProjectBackgroundTask(
                        LuaBundle.message("action.locate.interpreter"),
                        e.project!!,
                    ) {
                        val finder = LuaInterpreterService.getInstance()
                        val interpreters = finder.findInterpreters()
                        if (elements != interpreters) {
                            for (interpreter in interpreters) {
                                val original = elements.indexOfFirst { it.path == interpreter.path }
                                when (original) {
                                    -1 -> elements.add(interpreter)
                                    else -> elements[original] = interpreter
                                }
                            }

                            tableView.tableViewModel.items = elements
                            refresh()
                        }
                    }.queue()
                }
            },
        )
    }

    fun refresh() {
        this.setModified()
    }
}
