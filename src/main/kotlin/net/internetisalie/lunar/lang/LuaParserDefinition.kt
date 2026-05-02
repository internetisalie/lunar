package net.internetisalie.lunar.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.parser.LuaParser
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.syntax.LuaSyntax
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsElementType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer {
        return LuaLexer()
    }

    override fun getCommentTokens(): TokenSet {
        return LuaSyntax.CommentTokens
    }

    override fun getStringLiteralElements(): TokenSet {
        return LuaSyntax.StringLiteralTokens
    }

    override fun getWhitespaceTokens(): TokenSet {
        return LuaSyntax.WhiteSpaceTokens
    }

    override fun createParser(project: Project): PsiParser {
        return LuaParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return LuaFile(viewProvider)
    }

    override fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            is LuaCatsElementType -> LuaCatsElementTypes.Factory.createElement(node)
            else -> LuaElementTypes.Factory.createElement(node)
        }
    }

    companion object {
        val FILE = IFileElementType(LuaLanguage)
    }
}
