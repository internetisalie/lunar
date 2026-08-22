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
import com.intellij.openapi.application.ApplicationManager
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
    private val targetProject: Project?,
) : XNamedValue(name) {
    internal constructor(
        name: String,
        value: LuaDebugValue,
        isLocal: Boolean,
        targetProject: Project? = null,
    ) : this(name, null, value, false, isLocal, targetProject)

    override fun computeChildren(node: XCompositeNode) {
        if (value.isTable) {
            val fields = value.raw.checkTable()?.pairs() ?: return
            val xValues = XValueChildrenList(fields.size)
            fields.forEach { field ->
                val key =
                    when (field.first.kind) {
                        LuaValueKind.String -> field.first.stringValue ?: "?"
                        LuaValueKind.Number -> "[" + (field.first.numberValue?.toInt() ?: 0) + "]"
                        else -> "[" + field.first.toDisplayString() + "]"
                    }
                val debugValue = LuaDebugValue(field.second, null, AllIcons.Nodes.Field)
                xValues.add(
                    LuaDebugVariable(
                        name = key,
                        parent = this,
                        value = debugValue,
                        isIndex = false,
                        isLocal = true,
                        targetProject = targetProject,
                    ),
                )
            }
            node.addChildren(xValues, true)
        } else {
            super.computeChildren(node)
        }
    }

    override fun computePresentation(
        node: XValueNode,
        place: XValuePlace,
    ) {
        value.computePresentation(node, place)
    }

    override fun computeSourcePosition(navigatable: XNavigatable) {
        val project: Project =
            targetProject ?: run {
                super.computeSourcePosition(navigatable)
                return
            }

        val debugSession: XDebugSession = XDebuggerManager.getInstance(project).currentSession ?: return
        val currentPosition: XSourcePosition = debugSession.currentPosition ?: return

        navigateFrom(project, currentPosition, navigatable)
    }

    /**
     * [computeSourcePosition] minus the platform session lookup, which no test can install.
     * The read action this takes is the whole point of the split: without a seam here, the walk
     * below is only reachable from a live debug session and its locking cannot be asserted.
     */
    internal fun navigateFrom(
        project: Project,
        currentPosition: XSourcePosition,
        navigatable: XNavigatable,
    ) {
        // A platform callback off the EDT: nothing guarantees a read lock here, so take one.
        val position: XSourcePosition? =
            ApplicationManager.getApplication().runReadAction<XSourcePosition?> {
                resolveDeclarationPosition(project, currentPosition)
            }

        if (position != null) navigatable.setSourcePosition(position)
    }

    /** Resolves this variable's declaration from the paused frame's context. Call under a read action. */
    private fun resolveDeclarationPosition(
        project: Project,
        currentPosition: XSourcePosition,
    ): XSourcePosition? {
        val contextElement: PsiElement =
            XDebuggerUtil.getInstance().findContextElement(
                currentPosition.getFile(),
                currentPosition.getOffset(),
                project,
                false,
            ) ?: return null

        val declaration: PsiElement = findDeclaration(contextElement) ?: return null

        return XDebuggerUtil.getInstance().createPositionByElement(declaration)
    }

    /** Walks enclosing scopes outward from [contextElement], then the file, using standard bindings resolution. */
    private fun findDeclaration(contextElement: PsiElement): PsiElement? {
        val processor = LuaScopeProcessor(name)
        var current: PsiElement = contextElement

        while (current !is PsiFile) {
            if (isScopeElement(current) &&
                !current.processDeclarations(processor, ResolveState.initial(), contextElement, contextElement)
            ) {
                return processor.result
            }

            current = current.parent ?: return processor.result
        }

        // Also process the file itself
        if (current is LuaFile && processor.result == null) {
            current.processDeclarations(processor, ResolveState.initial(), contextElement, contextElement)
        }

        return processor.result
    }

    /** The PSI types that introduce a Lua scope; every other element is walked through, not searched. */
    private fun isScopeElement(element: PsiElement): Boolean =
        element is LuaBlock ||
            element is LuaFuncDef ||
            element is LuaFuncDecl ||
            element is LuaLocalFuncDecl ||
            element is LuaNumericForStatement ||
            element is LuaGenericForStatement

//    val evaluationExpression: String?
//        get() {
//            if (isIndex) {
//                return parent.getEvaluationExpression() + "[" + getName() + "]"
//            }
//            return if (parent != null) parent.getEvaluationExpression() + "[\"" + getName() + "\"]" else getName()
//        }
}
