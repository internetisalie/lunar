package net.internetisalie.lunar.lang

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.settings.LuaProjectSettings

class LuaCompletionContributor : CompletionContributor() {
    private val KEYWORDS = listOf(
        "if", "while", "function", "local", "for", "repeat", "return", "do", "break", "goto"
    )

    init {
        // Suggest basic keywords and contextual keywords
        extend(
            CompletionType.BASIC,
            psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)

                    // Statement start suggestions
                    val isStatementStart = prevLeaf == null ||
                        prevLeaf.node.elementType == LuaElementTypes.THEN ||
                        prevLeaf.node.elementType == LuaElementTypes.DO ||
                        prevLeaf.node.elementType == LuaElementTypes.ELSE ||
                        prevLeaf.node.elementType == LuaElementTypes.ELSEIF ||
                        prevLeaf.node.elementType == LuaElementTypes.REPEAT ||
                        prevLeaf.node.elementType == LuaElementTypes.END ||
                        prevLeaf.node.elementType == LuaElementTypes.SEMI

                    if (isStatementStart) {
                        KEYWORDS.forEach { result.addElement(LookupElementBuilder.create(it)) }
                    }

                    // Context-specific suggestions
                    if (prevLeaf != null) {
                        // Scan backwards for 'if' or 'elseif' to suggest 'then'
                        var leaf: PsiElement? = prevLeaf
                        var foundIf = false
                        var foundThen = false
                        var limit = 20
                        while (leaf != null && limit-- > 0) {
                            val type = leaf.node.elementType
                            if (type == LuaElementTypes.IF || type == LuaElementTypes.ELSEIF) {
                                foundIf = true
                                break
                            }
                            if (type == LuaElementTypes.THEN || type == LuaElementTypes.SEMI || type == LuaElementTypes.END) {
                                foundThen = true
                                break
                            }
                            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
                        }

                        if (foundIf && !foundThen) {
                            result.addElement(LookupElementBuilder.create("then"))
                        }

                        // Suggest 'end' if we are inside a block that needs it
                        if (prevLeaf.node.elementType == LuaElementTypes.THEN ||
                            prevLeaf.node.elementType == LuaElementTypes.ELSE ||
                            prevLeaf.node.elementType == LuaElementTypes.ELSEIF ||
                            prevLeaf.node.elementType == LuaElementTypes.DO ||
                            prevLeaf.node.elementType == LuaElementTypes.REPEAT
                        ) {
                            result.addElement(LookupElementBuilder.create("end"))
                        }

                        // After 'then' or in an if-block, suggest 'else', 'elseif', 'end'
                        // Again, token based walking
                        leaf = prevLeaf
                        foundThen = false
                        var foundEnd = false
                        limit = 50
                        while (leaf != null && limit-- > 0) {
                             val type = leaf.node.elementType
                             if (type == LuaElementTypes.THEN || type == LuaElementTypes.ELSE || type == LuaElementTypes.ELSEIF) {
                                 foundThen = true
                                 break
                             }
                             if (type == LuaElementTypes.END) {
                                 foundEnd = true
                                 break
                             }
                             leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
                        }
                        if (foundThen && !foundEnd) {
                            result.addElement(LookupElementBuilder.create("else"))
                            result.addElement(LookupElementBuilder.create("elseif"))
                            result.addElement(LookupElementBuilder.create("end"))
                        }
                    }
                }
            }
        )

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
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)

                    if (prevLeaf != null && prevLeaf.node.elementType == LuaElementTypes.IDENTIFIER) {
                        if (PsiTreeUtil.getParentOfType(prevLeaf, LuaLocalVarDecl::class.java) != null) {
                            result.addElement(LookupElementBuilder.create("<"))
                        }
                    }
                }
            }
        )
    }
}
