package net.internetisalie.lunar.lang

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
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
                    val level = LuaProjectSettings.getInstance(project).state.getTarget().getImplicitLanguageLevel()
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
                    val level = LuaProjectSettings.getInstance(project).state.getTarget().getImplicitLanguageLevel()
                    if (level < LuaLanguageLevel.LUA54) return

                    val position = parameters.position
                    val prevLeaf = com.intellij.psi.util.PsiTreeUtil.prevVisibleLeaf(position)

                    if (prevLeaf != null && prevLeaf.node.elementType == LuaElementTypes.IDENTIFIER) {
                        if (com.intellij.psi.util.PsiTreeUtil.getParentOfType(prevLeaf, LuaLocalVarDecl::class.java) != null) {
                            result.addElement(LookupElementBuilder.create("<"))
                        }
                    }
                }
            }
        )
    }
}
