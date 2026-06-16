package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class LuaPostfixTemplateProvider : PostfixTemplateProvider {
    private val templates = setOf<PostfixTemplate>(
        LuaIfPostfixTemplate(this), LuaNotPostfixTemplate(this), LuaVarPostfixTemplate(this),
        LuaForPostfixTemplate(this), LuaForPairsPostfixTemplate(this), LuaForIpairsPostfixTemplate(this),
        LuaIfNotPostfixTemplate(this), LuaNilPostfixTemplate(this), LuaNotNilPostfixTemplate(this),
        LuaReturnPostfixTemplate(this), LuaPrintPostfixTemplate(this),
    )

    override fun getTemplates(): Set<PostfixTemplate> = templates

    override fun isTerminalSymbol(currentChar: Char): Boolean {
        return currentChar == '.' || currentChar == '!'
    }

    override fun preExpand(file: PsiFile, editor: Editor) {}

    override fun afterExpand(file: PsiFile, editor: Editor) {}

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile {
        return copyFile
    }
}
