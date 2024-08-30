package net.internetisalie.lunar.luadoc.lang

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.luadoc.lang.syntax.LuaDocSyntaxHighlighter

class LuaDocSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return LuaDocSyntaxHighlighter()
    }
}
