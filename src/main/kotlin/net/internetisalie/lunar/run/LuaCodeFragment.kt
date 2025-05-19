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

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.TokenType
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightVirtualFile
import net.internetisalie.lunar.lang.psi.LuaFile
import org.jetbrains.annotations.NonNls

class LuaCodeFragment(
    project: Project?,
    contentElementType: IElementType,
    isPhysical: Boolean,
    name: @NonNls String,
    text: CharSequence,
    private val fragmentContext: PsiElement?
) : LuaFile(
    TokenType.CODE_FRAGMENT,
    contentElementType,
    PsiManagerEx.getInstanceEx(project).fileManager.createFileViewProvider(
        LightVirtualFile(name, FileTypeManager.getInstance().getFileTypeByFileName(name), text), isPhysical
    )
) {
    init {
        (viewProvider as SingleRootFileViewProvider).forceCachedPsi(this)
    }

    override fun getContext(): PsiElement? {
        return fragmentContext ?: super.getContext()
    }
}
