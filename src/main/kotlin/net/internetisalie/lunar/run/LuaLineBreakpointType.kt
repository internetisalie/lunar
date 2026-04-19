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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaStatement

class LuaLineBreakpointType : XLineBreakpointTypeBase(
    "lua-line",
    "Lua Line Breakpoints",
    LuaDebuggerEditorsProvider(),
) {

    override fun getDisplayText(breakpoint: XLineBreakpoint<XBreakpointProperties<*>?>): String {
        val sourcePosition: XSourcePosition = breakpoint.sourcePosition ?: return "Unknown position"
        val displayPath = FileUtil.toSystemDependentName(sourcePosition.file.path)
        return "Line ${sourcePosition.line} in file $displayPath"
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        val luaFile = PsiManager.getInstance(project).findFile(file) as? LuaFile ?: return false
        val document: Document = PsiDocumentManager.getInstance(project).getDocument(luaFile) ?: return false

        val result = Ref.create<Boolean?>(false)
        XDebuggerUtil.getInstance().iterateLine(project, document, line) { element: PsiElement? ->
            var element: PsiElement? = element

            // avoid comments
            if ((element is PsiWhiteSpace) || (PsiTreeUtil.getParentOfType<PsiComment?>(
                    element,
                    PsiComment::class.java
                ) !=
                        null)
            ) {
                return@iterateLine true
            }

            var parent: PsiElement? = element
            while (element != null) {
                val offset: Int = element.textOffset
                if (offset >= 0) {
                    if (document.getLineNumber(offset) != line) {
                        break
                    }
                }
                parent = element
                element = element.getParent()
            }

            if (parent is LuaStatement) {
                result.set(true)
            }
            true
        }

        return result.get()!!
    }
}
