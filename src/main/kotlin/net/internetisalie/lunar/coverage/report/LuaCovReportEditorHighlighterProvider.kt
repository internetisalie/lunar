package net.internetisalie.lunar.coverage.report

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter

class LuaCovReportEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?, fileType: FileType, virtualFile: VirtualFile?, colors: EditorColorsScheme
    ): EditorHighlighter {
        return LuaCovReportEditorHighlighter(colors)
    }
}

class LuaCovReportEditorHighlighter(scheme: EditorColorsScheme) :
    LayeredLexerEditorHighlighter(LuaCovReportSyntaxHighlighter(), scheme) {
    init {
        val luaLayer = LayerDescriptor(LuaSyntaxHighlighter(), "\n")
        registerLayer(LuaCovReportLexer.LUA_CODE, luaLayer)
    }
}
