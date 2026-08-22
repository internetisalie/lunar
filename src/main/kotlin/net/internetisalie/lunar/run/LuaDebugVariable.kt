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
    private val subscript: String?,
    private val value: LuaDebugValue,
    private val isLocal: Boolean,
    private val targetProject: Project?,
) : XNamedValue(name) {
    internal constructor(
        name: String,
        value: LuaDebugValue,
        isLocal: Boolean,
        targetProject: Project? = null,
    ) : this(name, null, null, value, isLocal, targetProject)

    override fun computeChildren(node: XCompositeNode) {
        if (!value.isTable) {
            super.computeChildren(node)
            return
        }

        val fields = value.raw.checkTable()?.pairs() ?: return
        val xValues = XValueChildrenList(fields.size)

        fields.forEach { field -> xValues.add(memberFor(field.first, field.second)) }

        node.addChildren(xValues, true)
    }

    /**
     * The Lua expression that re-evaluates to this value — what **Add to Watches** adds (BUG-447).
     * A root variable is its own name; a member appends the subscript that selected it from its
     * parent. Null when any link in the chain has no expressible form: the platform discards a null
     * rather than adding a watch that would silently evaluate to something else.
     */
    override fun getEvaluationExpression(): String? {
        if (parent == null) return name

        val parentExpression: String = parent.getEvaluationExpression() ?: return null
        val ownSubscript: String = subscript ?: return null

        return parentExpression + ownSubscript
    }

    private fun memberFor(
        key: LuaValue,
        fieldValue: LuaValue,
    ): LuaDebugVariable =
        LuaDebugVariable(
            name = displayNameFor(key),
            parent = this,
            subscript = subscriptFor(key),
            value = LuaDebugValue(fieldValue, null, AllIcons.Nodes.Field),
            isLocal = true,
            targetProject = targetProject,
        )

    private fun displayNameFor(key: LuaValue): String =
        when (key.kind) {
            LuaValueKind.String -> key.stringValue ?: "?"
            LuaValueKind.Number -> "[" + (key.numberValue?.toInt() ?: 0) + "]"
            else -> "[" + key.toDisplayString() + "]"
        }

    /**
     * The Lua subscript selecting [key] from this table, or null when the key has no literal form.
     * A table or function key cannot be written as an expression at all, and the number branch uses
     * [LuaValue.toDisplayString] rather than the display name's `toInt`, which would resolve a
     * non-integral key to a different element.
     */
    private fun subscriptFor(key: LuaValue): String? =
        when (key.kind) {
            LuaValueKind.String -> "[\"" + escapeLuaString(key.stringValue ?: return null) + "\"]"
            LuaValueKind.Number -> "[" + key.toDisplayString() + "]"
            else -> null
        }

    private fun escapeLuaString(raw: String): String = raw.replace("\\", "\\\\").replace("\"", "\\\"")

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
}
