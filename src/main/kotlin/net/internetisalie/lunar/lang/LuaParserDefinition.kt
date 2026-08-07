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
import net.internetisalie.lunar.lang.psi.LuaFileElementType
import net.internetisalie.lunar.lang.syntax.LuaSyntax
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsElementType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = LuaLexer()

    override fun createParser(project: Project): PsiParser = LuaParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = LuaSyntax.CommentTokens

    override fun getWhitespaceTokens(): TokenSet = LuaSyntax.WhiteSpaceTokens

    override fun getStringLiteralElements(): TokenSet = LuaSyntax.StringLiteralTokens

    override fun createElement(node: ASTNode): PsiElement {
        val type = node.elementType
        if (type is LuaCatsElementType) {
            return LuaCatsElementTypes.Factory.createElement(node)
        }
        return LuaElementTypes.Factory.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = LuaFile(viewProvider)

    companion object {
        val FILE = LuaFileElementType()
    }
}
