/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
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
package net.internetisalie.lunar.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup

class LuaStackFrame(
    val project: Project?,
    val controller: LuaDebuggerController,
    val position: XSourcePosition?,
    val contextName: String?,
    val index: Int,
    val entry: LuaRemoteStackEntry?,
) : XStackFrame() {
    constructor(
        project: Project?,
        controller: LuaDebuggerController,
        position: XSourcePosition?,
        index: Int
    ) : this(project, controller, position, null, index, null)

    override fun getSourcePosition(): XSourcePosition? {
        return position
    }

    override fun getEvaluator(): XDebuggerEvaluator {
        return LuaDebuggerEvaluator(controller)
    }

    override fun computeChildren(node: XCompositeNode) {
        if (entry == null) return

        val locals: MutableList<LuaDebugVariable> = mutableListOf()
        val upvalues: MutableList<LuaDebugVariable> = mutableListOf()

        ApplicationManager.getApplication().runReadAction {
            locals.addAll(entry.locals.variables.map {
                LuaDebugVariable(
                    it.name,
                    LuaDebugValue(
                        it.value,
                        it.displayValue ?: "",
                        AllIcons.Nodes.Variable,
                    ),
                    true,
                    project
                )
            })

            upvalues.addAll(entry.upvalues.variables.map {
                LuaDebugVariable(
                    it.name,
                    LuaDebugValue(
                        it.value,
                        it.displayValue ?: "",
                        AllIcons.Nodes.Variable,
                    ),
                    false,
                    project
                )
            })
        }

        val xValues = XValueChildrenList()

        if (locals.isNotEmpty()) {
            xValues.addTopGroup(object : XValueGroup("Locals") {
                override fun isAutoExpand(): Boolean {
                    return true
                }

                override fun computeChildren(node: XCompositeNode) {
                    val xValues = XValueChildrenList()

                    for (v in locals) xValues.add(v.name, v)
                    node.addChildren(xValues, true)
                    node.setAlreadySorted(false)
                }
            })
        }

        if (upvalues.isNotEmpty()) {
            xValues.addTopGroup(object : XValueGroup("Upvalues") {
                override fun isAutoExpand(): Boolean {
                    return true
                }

                override fun computeChildren(node: XCompositeNode) {
                    val xValues = XValueChildrenList()

                    for (v in upvalues) xValues.add(v.name, v)
                    node.addChildren(xValues, true)
                    node.setAlreadySorted(false)
                }
            })
        }

        node.addChildren(xValues, true)
        node.setAlreadySorted(false)
    }

    override fun customizePresentation(component: ColoredTextContainer) {
        if (position != null) {
            super.customizePresentation(component)

            if (contextName != null) {
                component.append(String.format(" (%s)", contextName), SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
            }
        } else {
            component.append("<internal C>", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            component.setIcon(AllIcons.Debugger.ShowCurrentFrame)
        }
    }
}
