package net.internetisalie.lunar.lang

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.psi.LuaAttrib
import net.internetisalie.lunar.lang.psi.LuaAttribName

import net.internetisalie.lunar.lang.psi.*

import net.internetisalie.lunar.settings.LuaProjectSettings

class LuaCompletionContributor : CompletionContributor() {
    init {
        // Suggest 'const' and 'close' inside < >
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf("<"),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.editor.project ?: return
                    val level = LuaProjectSettings.getInstance(project).state.languageLevel
                    if (level < LuaLanguageLevel.LUA54) return

                    result.addElement(LookupElementBuilder.create("const"))
                    result.addElement(LookupElementBuilder.create("close"))
                }
            }
        )

        // Suggest '<' after a local variable name
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(LuaElementTypes.IDENTIFIER)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.editor.project ?: return
                    val level = LuaProjectSettings.getInstance(project).state.languageLevel
                    if (level < LuaLanguageLevel.LUA54) return

                    val position = parameters.position
                    if (psiElement().inside(LuaLocalVarDecl::class.java).accepts(position)) {
                        result.addElement(LookupElementBuilder.create("<"))
                    }
                }
            }
        )
    }
}
