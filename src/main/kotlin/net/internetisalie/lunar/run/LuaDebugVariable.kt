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
package net.internetisalie.lunar.run

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveState
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import net.internetisalie.lunar.lang.LuaScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement

class LuaDebugVariable private constructor(
    name: String,
    private val parent: LuaDebugVariable?,
    private val value: LuaDebugValue,
    private val isIndex: Boolean,
    private val isLocal: Boolean,
) : XNamedValue(name) {
    internal constructor(name: String, value: LuaDebugValue, isLocal: Boolean) : this(name, null, value, false, isLocal)

    override fun computeChildren(node: XCompositeNode) {
        if (value.isTable) {
            val fields = value.raw.checkTable()?.pairs() ?: return
            val xValues = XValueChildrenList(fields.size);
            fields.forEach { field ->
                val key = if (field.first.kind == LuaValueKind.String) {
                    field.first.stringValue!!
                } else {
                    "[" + field.first.numberValue!!.toInt() + "]"
                }
                val debugValue = LuaDebugValue(field.second, null, AllIcons.Nodes.Field)
                xValues.add(
                    LuaDebugVariable(
                        name = key,
                        parent = this,
                        value = debugValue,
                        isIndex = false,
                        isLocal = true,
                    ),
                )
            }
            node.addChildren(xValues, true);
        } else {
            super.computeChildren(node)
        }
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        value.computePresentation(node, place)
    }

    override fun computeSourcePosition(navigatable: XNavigatable) {
        val dataManager: DataManager? = DataManager.getInstance()
        // TODO: Clean up deprecation: 'val dataContext: DataContext' is deprecated.
        val dataContext: DataContext? = dataManager?.dataContext

        if (dataContext == null) {
            super.computeSourcePosition(navigatable)
            return
        }

        val project: Project = PlatformDataKeys.PROJECT.getData(dataContext) ?: return
        val debugSession: XDebugSession = XDebuggerManager.getInstance(project).currentSession ?: return
        val currentPosition: XSourcePosition = debugSession.currentPosition ?: return

        val contextElement: PsiElement? = XDebuggerUtil.getInstance().findContextElement(
            currentPosition.getFile(),
            currentPosition.getOffset(), project, false,
        )

        if (contextElement == null) return

        // Use standard bindings resolution to find the variable declaration
        val processor = LuaScopeProcessor(name)
        var current: PsiElement? = contextElement

        while (current != null && current !is PsiFile) {
            val state = ResolveState.initial()

            // Process declarations in scope elements
            val matchFound = when (current) {
                is LuaBlock -> !current.processDeclarations(processor, state, contextElement, contextElement)
                is LuaFuncDef -> !current.processDeclarations(processor, state, contextElement, contextElement)
                is LuaFuncDecl -> !current.processDeclarations(processor, state, contextElement, contextElement)
                is LuaLocalFuncDecl -> !current.processDeclarations(processor, state, contextElement, contextElement)
                is LuaNumericForStatement -> !current.processDeclarations(processor, state, contextElement, contextElement)
                is LuaGenericForStatement -> !current.processDeclarations(processor, state, contextElement, contextElement)
                else -> false
            }

            if (matchFound) break
            current = current.parent
        }

        // Also process the file itself
        if (current is LuaFile && processor.result == null) {
            current.processDeclarations(processor, ResolveState.initial(), contextElement, contextElement)
        }

        if (processor.result != null) {
            navigatable.setSourcePosition(XDebuggerUtil.getInstance().createPositionByElement(processor.result))
        }
    }

//    val evaluationExpression: String?
//        get() {
//            if (isIndex) {
//                return parent.getEvaluationExpression() + "[" + getName() + "]"
//            }
//            return if (parent != null) parent.getEvaluationExpression() + "[\"" + getName() + "\"]" else getName()
//        }
}
