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

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementFactory

class LuaDebuggerEditorsProvider : XDebuggerEditorsProviderBase() {
    override fun createExpressionCodeFragment(
        project: Project,
        text: String,
        context: PsiElement?,
        isPhysical: Boolean
    ): PsiFile {
        return LuaElementFactory.createExpressionCodeFragment(project, text, context, true)
    }

    override fun getFileType(): FileType {
        return LuaFileType
    }

    override fun getSupportedLanguages(
        project: Project,
        sourcePosition: XSourcePosition?
    ): MutableCollection<Language> {
        return mutableSetOf(LuaLanguage)
    }
}
