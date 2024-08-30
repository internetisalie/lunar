// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.internetisalie.lunar.lang.syntax

import com.intellij.lexer.LayeredLexer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import net.internetisalie.lunar.lang.syntax.LuaHighlight.DOC_COMMENT
import net.internetisalie.lunar.luadoc.lang.syntax.LuaDocSyntaxHighlighter
import net.internetisalie.lunar.luadoc.lang.parser.LuaDocElementTypes

class LuaEditorHighlighter(scheme: EditorColorsScheme) : LayeredLexerEditorHighlighter(LuaSyntaxHighlighter(), scheme) {
    init {
        if (java.lang.Boolean.TRUE != LayeredLexer.ourDisableLayersFlag.get()) {
            registerLuaDocHighlighter()
        }
    }

    private fun registerLuaDocHighlighter() {
        // Register LuaDoc Highlighter
        val luaDocHighlighter: SyntaxHighlighter = LuaDocSyntaxHighlighter()
        val groovyDocLayer = LayerDescriptor(luaDocHighlighter, "\n", DOC_COMMENT)
        registerLayer(LuaDocElementTypes.LUADOC_COMMENT, groovyDocLayer)
    }
}
