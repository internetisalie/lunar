package net.internetisalie.lunar.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.parser.LuaParser

class LuaExpressionFragmentElementType : ICodeFragmentElementType("EXPRESSION_FRAGMENT", LuaLanguage) {
    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
        val project: Project = psi.project
        val languageForParser = getLanguageForParser(psi)
        val builder: PsiBuilder = PsiBuilderFactory.getInstance()
            .createBuilder(project, chameleon, null, languageForParser, chameleon.getChars())
        val parser: LuaParser =
            LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser).createParser(project) as LuaParser
        val node: ASTNode = parser.parse(this, builder)
        return node.firstChildNode
    }
}
