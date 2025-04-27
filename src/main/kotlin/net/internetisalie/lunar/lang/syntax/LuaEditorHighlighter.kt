// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.internetisalie.lunar.lang.syntax

import com.intellij.lexer.LayeredLexer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.lang.syntax.LuaHighlight.DOC_COMMENT
import net.internetisalie.lunar.luacats.lang.syntax.LuaCatsSyntaxHighlighter

class LuaEditorHighlighter(scheme: EditorColorsScheme) : LayeredLexerEditorHighlighter(LuaSyntaxHighlighter(), scheme) {
    init {
        if (java.lang.Boolean.TRUE != LayeredLexer.ourDisableLayersFlag.get()) {
            registerLuaDocHighlighter()
        }
    }

    private fun registerLuaDocHighlighter() {
        // Register LuaDoc Highlighter
        val luaCatsHighlighter: SyntaxHighlighter = LuaCatsSyntaxHighlighter()
        val luaCatsLayer = LayerDescriptor(luaCatsHighlighter, "\n", DOC_COMMENT)
        registerLayer(LuaLazyElementTypes.LUACATS_COMMENT, luaCatsLayer)
    }
}
