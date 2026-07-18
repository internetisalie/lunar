package net.internetisalie.lunar.lang

import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveState
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.completion.LuaCrossFileCompletionProvider
import net.internetisalie.lunar.lang.completion.LuaMemberLookup
import net.internetisalie.lunar.lang.editor.LuaKeywordBlockCloser
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.settings.LuaEditorOptions
import net.internetisalie.lunar.settings.LuaProjectSettings

class LuaCompletionContributor : CompletionContributor() {
    companion object {
        private const val KEYWORD_PRIORITY = 80.0
        private const val SYMBOL_PRIORITY = 50.0

        private val STATEMENT_KEYWORDS = listOf(
            "if", "while", "function", "local", "for", "repeat", "return", "do", "break"
        )

        private val EXPRESSION_KEYWORDS = listOf(
            "nil", "true", "false", "not", "function"
        )

        private val SPACE_KEYWORDS = setOf(
            "if", "while", "function", "local", "for", "repeat", "return", "do", "until", "and", "or", "in", "elseif", "goto"
        )

        /** Keywords that, when accepted, scaffold a matching block terminator (EDITOR-01-05). */
        private val BLOCK_OPENER_KEYWORDS = setOf("do", "then", "function", "repeat")

        private fun addKeywords(result: CompletionResultSet, keywords: Collection<String>) {
            keywords.forEach { keyword ->
                val base = LookupElementBuilder.create(keyword).withBoldness(true)
                val withHandler = if (BLOCK_OPENER_KEYWORDS.contains(keyword)) {
                    base.withInsertHandler(::blockKeywordInsertHandler)
                } else {
                    base
                }
                val element = if (SPACE_KEYWORDS.contains(keyword)) {
                    TailTypeDecorator.withTail(withHandler, TailTypes.spaceType())
                } else {
                    withHandler
                }
                result.addElement(PrioritizedLookupElement.withPriority(element, KEYWORD_PRIORITY))
            }
        }

        private fun blockKeywordInsertHandler(context: InsertionContext, item: LookupElement) {
            if (!LuaEditorOptions.instance.autoCloseKeywordBlocks) return
            val file = context.file as? LuaFile ?: return
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
            LuaKeywordBlockCloser.closeIfNeeded(context.editor, file, context.tailOffset)
        }

        private fun addSymbolCompletions(position: PsiElement, result: CompletionResultSet) {
            val processor = LuaCompletionScopeProcessor()
            addSymbols(result, position, processor)
        }

        private fun addSymbols(
            result: CompletionResultSet,
            position: PsiElement,
            processor: LuaCompletionScopeProcessor,
        ) {
            // Walk up the PSI tree to collect declarations from all enclosing scopes
            var current: PsiElement? = position
            var last: PsiElement? = null
            while (current != null) {
                val state = ResolveState.initial()

                when (current) {
                    is LuaBlock -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaFuncDef -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaFuncDecl -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaLocalFuncDecl -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaNumericForStatement -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaGenericForStatement -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                    is LuaFile -> {
                        current.processDeclarations(processor, state, last, position)
                    }
                }
                last = current
                current = current.parent
            }

            // Add collected symbols to completion result
            processor.results.forEach { (symbolName, info) ->
                val icon = when (info.type) {
                    LuaCompletionScopeProcessor.SymbolType.LOCAL -> com.intellij.icons.AllIcons.Nodes.Variable
                    LuaCompletionScopeProcessor.SymbolType.PARAMETER -> com.intellij.icons.AllIcons.Nodes.Parameter
                    LuaCompletionScopeProcessor.SymbolType.GLOBAL -> com.intellij.icons.AllIcons.Nodes.Function
                }

                val tailText = when (info.type) {
                    LuaCompletionScopeProcessor.SymbolType.LOCAL -> " local"
                    LuaCompletionScopeProcessor.SymbolType.PARAMETER -> " parameter"
                    LuaCompletionScopeProcessor.SymbolType.GLOBAL -> " global"
                }

                val builder = LookupElementBuilder.create(symbolName)
                    .withIcon(icon)
                    .withTailText(tailText, true)

                val element = PrioritizedLookupElement.withPriority(builder, SYMBOL_PRIORITY)
                result.addElement(element)
            }
        }
    }

    init {
        // Main keyword completion provider
        extend(
            CompletionType.BASIC,
            psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.editor.project ?: return
                    val level = LuaProjectSettings.getInstance(project).state.getTarget().getImplicitLanguageLevel()
                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)

                    // 1. Statement Start Suggestions
                    var isStatementStart = false
                    val statement = PsiTreeUtil.getParentOfType(position, LuaStatement::class.java)
                    if (statement != null && statement.textRange.startOffset == position.textRange.startOffset) {
                        isStatementStart = true
                    }

                    if (!isStatementStart && (prevLeaf == null ||
                        prevLeaf.node.elementType == LuaElementTypes.THEN ||
                        prevLeaf.node.elementType == LuaElementTypes.DO ||
                        prevLeaf.node.elementType == LuaElementTypes.ELSE ||
                        prevLeaf.node.elementType == LuaElementTypes.ELSEIF ||
                        prevLeaf.node.elementType == LuaElementTypes.REPEAT ||
                        prevLeaf.node.elementType == LuaElementTypes.END ||
                        prevLeaf.node.elementType == LuaElementTypes.SEMI)) {
                        isStatementStart = true
                    }

                    if (isStatementStart) {
                        addKeywords(result, STATEMENT_KEYWORDS)
                        if (level >= LuaLanguageLevel.LUA52) {
                            addKeywords(result, listOf("goto"))
                        }
                    }

                    // 2. Expression Keywords
                    // Suggest in most contexts where a value could start
                    var canBeExpressionStart = false
                    val expr = PsiTreeUtil.getParentOfType(position, LuaExpr::class.java)
                    if (expr != null && expr.textRange.startOffset == position.textRange.startOffset) {
                        canBeExpressionStart = true
                    }

                    if (!canBeExpressionStart && (prevLeaf == null ||
                        prevLeaf.node.elementType == LuaElementTypes.ASSIGN ||
                        prevLeaf.node.elementType == LuaElementTypes.LPAREN ||
                        prevLeaf.node.elementType == LuaElementTypes.LBRACK ||
                        prevLeaf.node.elementType == LuaElementTypes.LCURLY ||
                        prevLeaf.node.elementType == LuaElementTypes.COMMA ||
                        isStatementStart)) {
                        canBeExpressionStart = true
                    }

                    if (canBeExpressionStart) {
                        // Add symbols in expression contexts
                        addSymbolCompletions(position, result)
                        
                        // Only add expression keywords if there's no typed prefix AND we're not at
                        // statement start (nil/true/false should only appear when explicitly starting
                        // an expression). #62: read the platform's authoritative typed prefix — the
                        // dummy identifier merges into the caret leaf, so prevVisibleLeaf(position)
                        // cannot see the user's own prefix.
                        val hasPrefix = result.prefixMatcher.prefix.isNotEmpty()
                        if (!hasPrefix && !isStatementStart) {
                            addKeywords(result, EXPRESSION_KEYWORDS)
                        }
                    }

                    // 3. Context-Specific Keywords
                    if (prevLeaf != null) {
                        // then, do, in, until
                        addContextualKeywords(prevLeaf, result)

                        // else, elseif, end
                        addBlockClosureKeywords(prevLeaf, result)
                    }
                }
            }
        )

        // Cross-file completion provider (COMP-03)
        extend(
            CompletionType.BASIC,
            psiElement().withElementType(LuaElementTypes.IDENTIFIER),
            LuaCrossFileCompletionProvider()
        )

        // Member completion provider
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(".", ":"),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position) ?: return
                    val isColon = prevLeaf.text == ":"

                    val receiver = PsiTreeUtil.prevVisibleLeaf(prevLeaf) ?: return
                    val receiverExpr = findReceiverExpr(receiver) ?: return

                    // Build the snapshot from the file that actually owns receiverExpr (the in-memory
                    // completion copy), not parameters.originalFile — otherwise the PSI identities
                    // differ and the elementNodes lookup misses.
                    val snapshot = LuaTypesSnapshot.forFile(receiverExpr.containingFile)
                    val type = snapshot.getValueType(receiverExpr)

                    val members = type.getMembers()
                    for ((name, memberNode) in members) {
                        val memberType = memberNode.write
                        // If it's a colon completion, only show functions
                        if (isColon && memberType !is LuaGraphType.Function) continue

                        val element = LuaMemberLookup.create(name, memberType)
                        result.addElement(PrioritizedLookupElement.withPriority(element, 100.0))
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

    private fun addContextualKeywords(prevLeaf: PsiElement, result: CompletionResultSet) {
        // Scan backwards for 'if' or 'elseif' to suggest 'then'
        var leaf: PsiElement? = prevLeaf
        var foundIf = false
        var foundThen = false
        var limit = 30
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
            addKeywords(result, listOf("then"))
        }

        // Scan backwards for 'while' or 'for' to suggest 'do' or 'in'
        leaf = prevLeaf
        var foundLoop = false
        var foundDo = false
        var foundIn = false
        limit = 30
        while (leaf != null && limit-- > 0) {
            val type = leaf.node.elementType
            if (type == LuaElementTypes.WHILE || type == LuaElementTypes.FOR) {
                foundLoop = true
                break
            }
            if (type == LuaElementTypes.DO || type == LuaElementTypes.SEMI || type == LuaElementTypes.END) {
                foundDo = true
                break
            }
            if (type == LuaElementTypes.IN) {
                foundIn = true
                break
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }

        if (foundLoop && !foundDo) {
            val isGenericFor = isGenericForContext(prevLeaf)
            if (isGenericFor && !foundIn) {
                addKeywords(result, listOf("in"))
            } else {
                addKeywords(result, listOf("do"))
            }
        }
    }

    private fun isGenericForContext(position: PsiElement): Boolean {
        // Scan backwards from the current position.
        // A generic for has the form: for <names> in <exprs> do
        // A numeric for has the form:  for <name> = <start>, <limit> [, <step>] do
        // Key distinction: a numeric for always has '=' between 'for' and 'do', a generic for never does.
        // So if we reach 'for' without seeing '=', 'in', 'do', or a statement boundary, it's generic.
        var leaf: PsiElement? = position
        var limit = 30
        while (leaf != null && limit-- > 0) {
            val type = leaf.node.elementType
            when (type) {
                LuaElementTypes.FOR -> return true   // reached 'for' with no '=' → generic for
                LuaElementTypes.ASSIGN,              // '=' seen → numeric for
                LuaElementTypes.IN,                  // already past 'in' → not the name-list position
                LuaElementTypes.DO,
                LuaElementTypes.SEMI,
                LuaElementTypes.END -> return false
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        return false
    }

    private fun addBlockClosureKeywords(prevLeaf: PsiElement, result: CompletionResultSet) {
        val prevType = prevLeaf.node.elementType

        // Suggest 'end' if we just started a block
        if (prevType == LuaElementTypes.THEN ||
            prevType == LuaElementTypes.ELSE ||
            prevType == LuaElementTypes.ELSEIF ||
            prevType == LuaElementTypes.DO ||
            prevType == LuaElementTypes.REPEAT
        ) {
            addKeywords(result, listOf("end"))
            if (prevType == LuaElementTypes.REPEAT) {
                addKeywords(result, listOf("until"))
            }
        }

        // Scan backwards to see if we are in an unclosed block
        var leaf: PsiElement? = prevLeaf
        var foundBlockStart = false
        var foundBlockEnd = false
        var blockStartType: IElementType? = null
        var limit = 100
        while (leaf != null && limit-- > 0) {
            val type = leaf.node.elementType
            if (type == LuaElementTypes.THEN || type == LuaElementTypes.ELSE || type == LuaElementTypes.ELSEIF || type == LuaElementTypes.DO || type == LuaElementTypes.REPEAT) {
                foundBlockStart = true
                blockStartType = type
                break
            }
            if (type == LuaElementTypes.END || type == LuaElementTypes.UNTIL) {
                foundBlockEnd = true
                break
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }

        if (foundBlockStart && !foundBlockEnd) {
            // 'else' and 'elseif' are only valid after 'if'/'elseif' blocks, not after 'do' blocks
            if (blockStartType == LuaElementTypes.DO || blockStartType == LuaElementTypes.REPEAT) {
                addKeywords(result, listOf("end"))
                if (blockStartType == LuaElementTypes.REPEAT) {
                    addKeywords(result, listOf("until"))
                }
            } else {
                addKeywords(result, listOf("end", "else", "elseif"))
            }
        }
    }

    private fun findReceiverExpr(receiver: PsiElement): PsiElement? {
        // If receiver is an identifier, it might be a NameRef or part of a Var/Expr
        var current: PsiElement? = receiver
        while (current != null) {
            if (current is LuaExpr || current is LuaVar || current is LuaNameRef) {
                // If it's part of a larger expression that ends here, we want the larger one.
                val parent = current.parent
                if (parent is LuaExpr || parent is LuaVar || parent is LuaNameRef) {
                    if (parent.textRange.endOffset == current.textRange.endOffset) {
                        current = parent
                        continue
                    }
                }
                return current
            }
            current = current.parent
        }
        return null
    }
}
