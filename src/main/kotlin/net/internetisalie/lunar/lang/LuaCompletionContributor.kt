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
import com.intellij.psi.ResolveState
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.completion.LuaCrossFileCompletionProvider
import net.internetisalie.lunar.lang.completion.LuaMemberLookup
import net.internetisalie.lunar.lang.editor.LuaKeywordBlockCloser
import net.internetisalie.lunar.lang.indexing.LuaReceiverMember
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalBindingScan
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.VariableNode
import net.internetisalie.lunar.settings.LuaEditorOptions
import net.internetisalie.lunar.settings.LuaProjectSettings

class LuaCompletionContributor : CompletionContributor() {
    companion object {
        private const val KEYWORD_PRIORITY = 80.0
        private const val SYMBOL_PRIORITY = 50.0

        private val STATEMENT_KEYWORDS =
            listOf(
                "if",
                "while",
                "function",
                "local",
                "for",
                "repeat",
                "return",
                "do",
                "break",
            )

        private val EXPRESSION_KEYWORDS =
            listOf(
                "nil",
                "true",
                "false",
                "not",
                "function",
            )

        private val SPACE_KEYWORDS =
            setOf(
                "if",
                "while",
                "function",
                "local",
                "for",
                "repeat",
                "return",
                "do",
                "until",
                "and",
                "or",
                "in",
                "elseif",
                "goto",
            )

        /** Keywords that, when accepted, scaffold a matching block terminator (EDITOR-01-05). */
        private val BLOCK_OPENER_KEYWORDS = setOf("do", "then", "function", "repeat")

        private fun addKeywords(
            result: CompletionResultSet,
            keywords: Collection<String>,
        ) {
            keywords.forEach { keyword ->
                val base = LookupElementBuilder.create(keyword).withBoldness(true)
                val withHandler =
                    if (BLOCK_OPENER_KEYWORDS.contains(keyword)) {
                        base.withInsertHandler(::blockKeywordInsertHandler)
                    } else {
                        base
                    }
                val element =
                    if (SPACE_KEYWORDS.contains(keyword)) {
                        TailTypeDecorator.withTail(withHandler, TailTypes.spaceType())
                    } else {
                        withHandler
                    }
                result.addElement(PrioritizedLookupElement.withPriority(element, KEYWORD_PRIORITY))
            }
        }

        private fun blockKeywordInsertHandler(
            context: InsertionContext,
            item: LookupElement,
        ) {
            if (!LuaEditorOptions.instance.autoCloseKeywordBlocks) return
            val file = context.file as? LuaFile ?: return
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
            LuaKeywordBlockCloser.closeIfNeeded(context.editor, file, context.tailOffset)
        }

        /**
         * BUG-395: the members of a global the current file never declares — a bundled stdlib stub's
         * `table`, a definition library's `assert`, a project-wide `Lib = {}`.
         *
         * The type graph is built one file at a time, so such a receiver has no in-file node and the
         * member list came back empty. [LuaTypeManager.resolveGlobal] finds the declaring file and
         * hands back its type; materializing it into a scratch graph keeps the borrowed member nodes
         * off the declaring file's graph, where a write here must never land.
         */
        private fun crossFileGlobalMembers(receiver: PsiElement): Map<String, VariableNode> {
            val nameRef = bareNameOf(receiver) ?: return emptyMap()
            val global =
                LuaTypeManager.getInstance(nameRef.project).resolveGlobal(nameRef.text, nameRef)
                    ?: return emptyMap()
            return LuaGraphType.materialize(global, nameRef).getMembers()
        }

        /**
         * COMP-09 §4.13 — **the change site**. True when the index answered and the caller must not
         * build the snapshot.
         *
         * Everything below the call is untouched: this arm either answers outright or **declines by
         * falling through**, never by re-implementing the graph arm. `crossFileGlobalMembers`, the
         * `type == LuaGraphType.Undefined` guard and the shared emit loop are byte-for-byte as they
         * were, which is why the golden's `global` and `class` door rows cannot move (design §1.10.5).
         *
         * **Order is measured, not stylistic.** `globalMembership` is asked FIRST and Rule S runs only
         * if the index could answer. Routing is identical either way — DR-21's whole table was re-taken
         * under both orderings and is byte-identical — so this is a pure cost decision: Rule S is
         * O(file) and the reverse order costs 10 875–21 501 µs per completion on a 4 002-line file,
         * where `found = false` short-circuits it to nothing here (design §1.10.6).
         *
         * **`exclude` is `parameters.originalFile`, not `containingFile`** (BL-8): during completion
         * `receiverExpr.containingFile` is a *copy* with its own `VirtualFile`, so the plain form
         * matches no candidate and the exclusion silently never fires. Rule S reads the **copy** — the
         * file the user is typing in — and the two are wrong in opposite directions if swapped.
         *
         * Threading is unchanged: the provider already runs on the completion thread inside the
         * platform's read action, `globalMembership` is `FileBasedIndex` reads with
         * `ProgressManager.checkCanceled()` per callback, and Rule S is a cancellable PSI walk. While
         * dumb, `globalMembership` reports `found = false` and this declines, preserving today's
         * behaviour verbatim (design §4.9, DR-10).
         *
         * Four arguments rather than three: the ≤3-argument tripwire is waived here the same way the
         * enclosing `addCompletions(parameters, context, result)` is, and [emitIndexed] is split out to
         * keep each function inside the 30-logic-line rule (design §4.13, "Contract conformance").
         */
        private fun addIndexedGlobalMembers(
            receiverExpr: PsiElement,
            parameters: CompletionParameters,
            isColon: Boolean,
            result: CompletionResultSet,
        ): Boolean {
            val (receiverName, rootRef) = dottedReceiverOf(receiverExpr) ?: return false
            val membership =
                LuaReceiverMemberIndex.globalMembership(receiverName, rootRef.project, parameters.originalFile)
            if (!membership.found || !membership.authoritative) return false
            // The shadowing test is on the ROOT name, not the dotted key: `local Foo` shadows
            // `Foo.bar` too, and there is no such thing as a local named `Foo.bar`.
            if (LuaLocalBindingScan.binds(receiverExpr.containingFile, rootRef.text)) return false
            emitIndexed(membership.members, isColon, result)
            return true
        }

        /**
         * The index arm's emit loop. Its `isColon` filter is **syntactic** — how the member was
         * declared — where the graph arm's is semantic, on the inferred type. Design §4.3's D3 records
         * the divergence: `---@field onClose fun(): nil` indexes `Kind.FUNCTION` and so survives at a
         * `:` caret, while `R.aliased = someFn` cannot be classified without resolution, is recorded
         * `Kind.FIELD`, and does not.
         */
        private fun emitIndexed(
            members: List<LuaReceiverMember>,
            isColon: Boolean,
            result: CompletionResultSet,
        ) {
            for (member in members) {
                if (isColon && member.kind != LuaReceiverMember.Kind.FUNCTION) continue
                result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(member), 100.0))
            }
        }

        /**
         * The receiver as a single unqualified name, or null if it is anything else.
         *
         * `findReceiverExpr` hands back whichever expression ends at the caret's `.`/`:`, and that is
         * a bare [LuaNameRef] for `table.` but a wrapping expression for `table:`. Requiring the same
         * text range keeps the wrapper case working while still rejecting a qualified receiver — only
         * a bare name can be a global.
         */

        /**
         * BUG-430 (G-2): the dotted key for a receiver, plus its root reference.
         *
         * `Foo` gives `("Foo", Foo)`; `Foo.bar` gives `("Foo.bar", Foo)`. Null for anything with a
         * call or a bracket suffix, which have no dotted name — mirroring the indexer's own
         * [LuaReceiverMemberIndex] rule so the two cannot drift apart.
         *
         * The arm previously asked [bareNameOf], which is null for an index expression, so it
         * declined for every multi-segment receiver and the graph arm answered empty. Fixing the
         * indexer alone changes nothing user-visible, because nothing would query the new key.
         */
        private fun dottedReceiverOf(receiver: PsiElement): Pair<String, LuaNameRef>? {
            val nameRef = bareNameOf(receiver) ?: return null
            return qualifiedPrefixOf(nameRef) ?: (nameRef.text to nameRef)
        }

        /**
         * `Foo.bar` for the `bar` in `Foo.bar.<caret>`, or null when [nameRef] is not a suffix index.
         *
         * **The receiver element at a nested caret is the LAST segment only.** `findReceiverExpr`
         * returns a bare `LuaNameRef` reading `bar` — measured, not assumed — so there is no `LuaVar`
         * to read a suffix list off and the prefix has to be rebuilt by walking up to the enclosing
         * var and back down its suffixes. Asking the index for `bar` is what made `Foo.bar.` decline
         * to the graph arm, which answers empty.
         *
         * Suffixes are taken up to and including the one holding [nameRef], so the completion's own
         * dummy-identifier suffix is excluded by construction rather than by name.
         */
        private fun qualifiedPrefixOf(nameRef: LuaNameRef): Pair<String, LuaNameRef>? {
            val target = PsiTreeUtil.getParentOfType(nameRef, LuaVar::class.java) ?: return null
            val rootRef = target.nameRef ?: return null
            if (rootRef === nameRef) return null
            val end = nameRef.textRange.endOffset
            val names = mutableListOf(rootRef.text)
            var reached = false
            for (suffix in target.varSuffixList) {
                if (suffix.nameAndArgsList.isNotEmpty()) return null
                val name = suffix.indexExpr.nameRef ?: return null
                names.add(name.text)
                if (name.textRange.endOffset >= end) {
                    reached = true
                    break
                }
            }
            if (!reached) return null
            return names.joinToString(".") to rootRef
        }

        private fun bareNameOf(receiver: PsiElement): LuaNameRef? =
            receiver as? LuaNameRef
                ?: PsiTreeUtil
                    .findChildOfType(receiver, LuaNameRef::class.java)
                    ?.takeIf { it.textRange == receiver.textRange }

        private fun addSymbolCompletions(
            position: PsiElement,
            result: CompletionResultSet,
        ) {
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
                val icon =
                    when (info.type) {
                        LuaCompletionScopeProcessor.SymbolType.LOCAL -> com.intellij.icons.AllIcons.Nodes.Variable
                        LuaCompletionScopeProcessor.SymbolType.PARAMETER -> com.intellij.icons.AllIcons.Nodes.Parameter
                        LuaCompletionScopeProcessor.SymbolType.GLOBAL -> com.intellij.icons.AllIcons.Nodes.Function
                    }

                val tailText =
                    when (info.type) {
                        LuaCompletionScopeProcessor.SymbolType.LOCAL -> " local"
                        LuaCompletionScopeProcessor.SymbolType.PARAMETER -> " parameter"
                        LuaCompletionScopeProcessor.SymbolType.GLOBAL -> " global"
                    }

                val builder =
                    LookupElementBuilder
                        .create(symbolName)
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
                    result: CompletionResultSet,
                ) {
                    val project = parameters.editor.project ?: return
                    val level =
                        LuaProjectSettings
                            .getInstance(project)
                            .state
                            .getTarget()
                            .getImplicitLanguageLevel()
                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)

                    // 1. Statement Start Suggestions
                    var isStatementStart = false
                    val statement = PsiTreeUtil.getParentOfType(position, LuaStatement::class.java)
                    if (statement != null && statement.textRange.startOffset == position.textRange.startOffset) {
                        isStatementStart = true
                    }

                    if (!isStatementStart &&
                        (
                            prevLeaf == null ||
                                prevLeaf.node.elementType == LuaElementTypes.THEN ||
                                prevLeaf.node.elementType == LuaElementTypes.DO ||
                                prevLeaf.node.elementType == LuaElementTypes.ELSE ||
                                prevLeaf.node.elementType == LuaElementTypes.ELSEIF ||
                                prevLeaf.node.elementType == LuaElementTypes.REPEAT ||
                                prevLeaf.node.elementType == LuaElementTypes.END ||
                                prevLeaf.node.elementType == LuaElementTypes.SEMI
                        )
                    ) {
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

                    if (!canBeExpressionStart &&
                        (
                            prevLeaf == null ||
                                prevLeaf.node.elementType == LuaElementTypes.ASSIGN ||
                                prevLeaf.node.elementType == LuaElementTypes.LPAREN ||
                                prevLeaf.node.elementType == LuaElementTypes.LBRACK ||
                                prevLeaf.node.elementType == LuaElementTypes.LCURLY ||
                                prevLeaf.node.elementType == LuaElementTypes.COMMA ||
                                isStatementStart
                        )
                    ) {
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
            },
        )

        // Cross-file completion provider (COMP-03).
        //
        // Excluded from two carets where a global is not a valid completion at all:
        //  - after `.`/`:` — a member position. This provider offers project-wide globals and
        //    `---@class`-carrying locals, none of which can follow a dot, so `assert.` was answered
        //    with the *declaring file's locals* alongside the receiver's real members (BUG-398).
        //  - after `goto` — a label position, served by `LuaLabelReference`'s variants. Harmless
        //    while the provider had nothing to offer a fresh fixture; once BUG-394 let library
        //    symbols through, `goto <caret>` started listing the entire Lua standard library.
        extend(
            CompletionType.BASIC,
            psiElement()
                .withElementType(LuaElementTypes.IDENTIFIER)
                .andNot(psiElement().afterLeaf(".", ":"))
                .andNot(psiElement().afterLeaf("goto")),
            LuaCrossFileCompletionProvider(),
        )

        // Member completion provider
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(".", ":"),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position) ?: return
                    val isColon = prevLeaf.text == ":"

                    val receiver = PsiTreeUtil.prevVisibleLeaf(prevLeaf) ?: return
                    val receiverExpr = findReceiverExpr(receiver) ?: return

                    // COMP-09 §4.13 — answer from the index before the type graph is built.
                    if (addIndexedGlobalMembers(receiverExpr, parameters, isColon, result)) return

                    // Build the snapshot from the file that actually owns receiverExpr (the in-memory
                    // completion copy), not parameters.originalFile — otherwise the PSI identities
                    // differ and the elementNodes lookup misses.
                    // MAINT-28-05 (§3.4): no per-session caching is added here — LuaTypesSnapshot.forFile
                    // already memoizes per file-text via PsiFile UserData, so checkTypes runs at most
                    // once per distinct copy-file text within a session; a second cache would be redundant.
                    val snapshot = LuaTypesSnapshot.forFile(receiverExpr.containingFile)
                    val type = snapshot.getValueType(receiverExpr)

                    // Undefined means this file's scope never bound the receiver at all — the shape a
                    // global declared somewhere else has. Anything the file *did* bind keeps its own
                    // members, so an empty local table never picks up a same-named global's (BUG-395).
                    val members =
                        if (type == LuaGraphType.Undefined) {
                            crossFileGlobalMembers(receiverExpr)
                        } else {
                            type.getMembers()
                        }
                    for ((name, memberNode) in members) {
                        val memberType = memberNode.write
                        // If it's a colon completion, only show functions
                        if (isColon && memberType !is LuaGraphType.Function) continue

                        val element = LuaMemberLookup.create(name, memberType)
                        result.addElement(PrioritizedLookupElement.withPriority(element, 100.0))
                    }
                }
            },
        )

        // Suggest 'const' and 'close' inside < >
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf("<"),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val project = parameters.editor.project ?: return
                    val level =
                        LuaProjectSettings
                            .getInstance(project)
                            .state
                            .getTarget()
                            .getImplicitLanguageLevel()
                    if (level < LuaLanguageLevel.LUA54) return

                    result.addElement(LookupElementBuilder.create("const"))
                    result.addElement(LookupElementBuilder.create("close"))
                }
            },
        )

        // Suggest '<' after a local variable name
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(LuaElementTypes.IDENTIFIER)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val project = parameters.editor.project ?: return
                    val level =
                        LuaProjectSettings
                            .getInstance(project)
                            .state
                            .getTarget()
                            .getImplicitLanguageLevel()
                    if (level < LuaLanguageLevel.LUA54) return

                    val position = parameters.position
                    val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)

                    if (prevLeaf != null && prevLeaf.node.elementType == LuaElementTypes.IDENTIFIER) {
                        if (PsiTreeUtil.getParentOfType(prevLeaf, LuaLocalVarDecl::class.java) != null) {
                            result.addElement(LookupElementBuilder.create("<"))
                        }
                    }
                }
            },
        )
    }

    private fun addContextualKeywords(
        prevLeaf: PsiElement,
        result: CompletionResultSet,
    ) {
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
                LuaElementTypes.FOR -> return true // reached 'for' with no '=' → generic for
                LuaElementTypes.ASSIGN, // '=' seen → numeric for
                LuaElementTypes.IN, // already past 'in' → not the name-list position
                LuaElementTypes.DO,
                LuaElementTypes.SEMI,
                LuaElementTypes.END,
                -> return false
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        return false
    }

    private fun addBlockClosureKeywords(
        prevLeaf: PsiElement,
        result: CompletionResultSet,
    ) {
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
            if (type == LuaElementTypes.THEN ||
                type == LuaElementTypes.ELSE ||
                type == LuaElementTypes.ELSEIF ||
                type == LuaElementTypes.DO ||
                type == LuaElementTypes.REPEAT
            ) {
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
