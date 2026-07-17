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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import java.io.File

data class LuaPosition(
    val path: String,
    val line: Int,
) {
    fun args() : List<String> {
        return listOf(path, line.toString())
    }

    fun localPosition() : XSourcePosition?{
        return createLocalPosition(
            LocalFileSystem.getInstance().findFileByPath(path),
            line,
        )
    }

    companion object {
        fun createRemotePosition(xSourcePosition: XSourcePosition, workingDir: File?): LuaPosition {
            val target = File(xSourcePosition.file.path)
            val relative = FileUtil.getRelativePath(workingDir, target) ?: target.path
            return LuaPosition(
                relative.replace('\\', '/'),
                xSourcePosition.line + 1,
            )
        }

        fun createLocalPosition(virtualFile : VirtualFile?, line: Int): XSourcePosition? {
            if (virtualFile == null) return null
            return XDebuggerUtil.getInstance().createPosition(virtualFile, line - 1)
        }
    }
}
