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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.AnActionButton
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.LocalPathCellEditor
import javax.swing.table.TableCellEditor

class LuaInterpretersTable : ListTableWithButtons<LuaInterpreter>() {
    private class CellModelBase(
        title: String,
        val editable: Boolean,
        val getter: (i: LuaInterpreter) -> String?,
        val setter: ((i: LuaInterpreter, value: String) -> Unit)? = null,
        val editor: ((i: LuaInterpreter) -> TableCellEditor?)? = null,
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
    }

    private var myListModel : ListTableModel<LuaInterpreter>? = null

    override fun createListModel(): ListTableModel<LuaInterpreter> {
        val name = CellModelBase(
            "Name", true,
            { luaInterpreter: LuaInterpreter -> luaInterpreter.name },
            { luaInterpreter: LuaInterpreter, name: String -> luaInterpreter.name = name },
        )

        val path = CellModelBase("Executable", true,
            { luaInterpreter: LuaInterpreter -> luaInterpreter.path },
            { luaInterpreter: LuaInterpreter, path: String ->
                luaInterpreter.path = path
                LuaInterpreterFinder.INSTANCE.describeInBackground(luaInterpreter, { setModified() })
            },
            { luaInterpeter: LuaInterpreter ->
                val chooserDescriptor = FileChooserDescriptor(
                    true, false, false, false, false, false
                )
                val cellEditor = LocalPathCellEditor(null)
                cellEditor.fileChooserDescriptor(chooserDescriptor)
                cellEditor
            })

        val family = CellModelBase("Family", false,
            { luaInterpreter -> luaInterpreter.familyOrUnknown.interpreterName })

        val version = CellModelBase("Version", false,
            { luaInterpreter -> luaInterpreter.version })

        val listModel = object : ListTableModel<LuaInterpreter>(name, path, family, version) {
            override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
                super.setValueAt(aValue, rowIndex, columnIndex)

                if (columnIndex == 2) {
                    // Manually redraw the derived values
                    this.fireTableCellUpdated(rowIndex, 3)
                    this.fireTableCellUpdated(rowIndex, 4)
                }
            }
        }

        myListModel = listModel
        return listModel
    }

    override fun createElement(): LuaInterpreter {
        return LuaInterpreter()
    }

    override fun isEmpty(element: LuaInterpreter?): Boolean {
        return element?.name == null || element.name!!.isEmpty()
                || element.path == null || element.path!!.isEmpty()
    }

    override fun cloneElement(interpreter: LuaInterpreter): LuaInterpreter {
        return LuaInterpreter(interpreter)
    }

    override fun canDeleteElement(selection: LuaInterpreter): Boolean {
        return true
    }

    override fun createExtraActions(): Array<AnActionButton> {
        return arrayOf(object : AnActionButton("Re-scan", AllIcons.Actions.ForceRefresh) {
            protected fun findByPath(path: String?): LuaInterpreter? {
                for (element in elements) if (element!!.path == path) return element
                return null
            }

            protected fun update(target: LuaInterpreter, source: LuaInterpreter) {
                target.familyKey = source.familyKey
                target.path = source.path
                target.version = source.version
            }

            override fun actionPerformed(e: AnActionEvent) {
                object : Task.Backgroundable(
                    ProjectManager.getInstance().defaultProject,
                    "Locating lua interpreters",
                    false) {
                    override fun run(indicator: ProgressIndicator) {
                        val finder = LuaInterpreterFinder()
                        val interpreters = finder.findInterpreters()

                        for (interpreter in interpreters) {
                            val original = findByPath(interpreter.path)
                            if (original == null) elements.add(interpreter)
                            else update(original, interpreter)
                        }

                        if (interpreters.size != 0) {
                            tableView.tableViewModel.items = elements
                            setModified()
                        }
                    }
                }.queue()

            }
        })
    }

    fun isModified(from: List<LuaInterpreter>): Boolean {
        // compare from to current state
        if (myListModel == null) return true;
        val current = myListModel?.items ?: emptyList();
        if (from.size != current.size) return false
        return current.zip(from).all { (a, b) -> a.name.equals(b.name) && a.path.equals(b.path) }
    }

    fun refresh() {
        this.setModified()
    }
}
