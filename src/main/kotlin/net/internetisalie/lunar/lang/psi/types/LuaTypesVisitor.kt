package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Traverses a Lua PSI tree and builds a [LuaTypeGraph] and [LuaTypesSnapshot] for the file.
 */
class LuaTypesVisitor : LuaRecursiveVisitor() {
    private val graph = LuaTypeGraph()
    private val elementNodes: MutableMap<PsiElement, List<TypeNode>> = mutableMapOf()
    private var fileReturnType: LuaGraphType = LuaGraphType.Any
    private val rootReturnNodes: MutableList<VariableNode> = mutableListOf()

    /** Current lexical scope. Updated as we enter/leave blocks and functions. */
    private var scope: LuaScope = LuaScope.root(rootReturnNodes)

    /**
     * BUG-395: the globals this file declares, by name. Every write to a file-scope global (a bare
     * `X = …`, a `function X.y()`) shares one node here, so `X = {}` and `function X.y()` describe a
     * single type instead of two unrelated ones — and so the snapshot can publish that type to other
     * files, which is what makes a library's members reachable at all.
     */
    private val globalNodes: MutableMap<String, VariableNode> = mutableMapOf()

    private fun globalNode(
        name: String,
        anchor: PsiElement,
    ): VariableNode = globalNodes.getOrPut(name) { graph.variable(anchor) }

    /**
     * `self` binding for the next [visitFunctionBody] call (COMP-04-09): the receiver's type node
     * plus a distinct PSI anchor for the injected `self` node. Set immediately before the call and
     * consumed (cleared) inside it, so the function keeps its ≤3 parameters instead of threading a
     * 4th argument.
     */
    private var pendingSelf: SelfBinding? = null

    /**
     * Declaration-typed seeds (BUG-397): member seeds from [seedDeclaredMember] and free-global
     * seeds from [freeGlobalSeed]. A node in this set carries a *declared* type read off another
     * file; it contributes values but never raises call demands — [visitFuncCall] flows a
     * declaration-typed callee's declared return into the call results and skips the call-demand
     * check, exactly matching the pre-BUG-397 "stub calls are not checked" contract — and
     * [visitIndexExpr] resolves its members through the declared route rather than a graph
     * `Table` constraint (annotations are authoritative; inference fills unannotated slots).
     */
    private val declarationTypedNodes: MutableSet<TypeNode> = mutableSetOf()

    /** Memoized [freeGlobalSeed] per name — one node per global, shared by every reference. */
    private val freeGlobalSeeds: MutableMap<String, TypeNode?> = mutableMapOf()

    private data class SelfBinding(
        val receiver: VariableNode,
        val anchor: PsiElement,
    )

    /**
     * TYPE-08: a type guard parsed from an `if`/`elseif` condition.
     *
     * [narrowedType] is the type matched by the guard; [isEquality] is true for `==`/`type()==`
     * (the match branch receives [narrowedType]) and false for `~=` (the match branch receives the
     * complement). [anchor] is the condition's [LuaBinOpExpr], used as the PSI anchor for the
     * synthetic narrowed graph nodes.
     */
    private data class TypeGuard(
        val variableName: String,
        val narrowedType: LuaGraphType,
        val isEquality: Boolean,
        val anchor: PsiElement,
    )

    private fun getNodes(element: PsiElement?): List<TypeNode> = elementNodes[element] ?: emptyList()

    private fun firstNode(element: PsiElement?): TypeNode? = getNodes(element).firstOrNull()

    private fun isRequireCall(callee: PsiElement?): Boolean = callee?.text == "require"

    private fun extractModuleName(o: LuaFuncCall): String? {
        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return null
        val args = nameAndArgs.args
        val stringElement =
            args.string
                ?: args.exprList
                    ?.exprList
                    ?.firstOrNull()
                    ?.let {
                        unwrapExpression(
                            it,
                        )
                    }?.let { (it as? LuaTerminalExpr)?.string }

        return stringElement?.text?.trim('\"', '\'')
    }

    /**
     * COMP-04-08: models `setmetatable(t, mt)` by adding `mt.__index`'s table type as a super type
     * of `t`, so `t.getMembers()` includes the index table's members (TC-05). The call's result
     * value is bound to the augmented `t` type.
     *
     * COMP-04-DR-01: only literal/locally-inferable `mt` tables are handled; a dynamic metatable
     * (no inferable `__index` table) falls through to normal call handling. Returns true when the
     * call was fully handled.
     */
    private fun handleSetMetatable(
        o: LuaFuncCall,
        resultNode: VariableNode,
    ): Boolean {
        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return false
        val argExprs = nameAndArgs.args.exprList?.exprList ?: return false
        if (argExprs.size < 2) return false

        val tType = tableArgumentType(argExprs[0]) ?: return false
        val metatable = tableArgumentType(argExprs[1]) ?: return false

        // BUG-426: a metatable with no resolvable `__index` establishes no members, but the call
        // still RETURNS its first argument. Bailing out here left the whole result `Undefined`,
        // which absorbs every check — so the pattern looked supported while being invisible.
        val indexType = indexTableOf(metatable)
        val augmented =
            tType.copy(
                superTypes = if (indexType == null) tType.superTypes else tType.superTypes + indexType,
                // BUG-424: which operators this value implements. Recorded from the METATABLE, not
                // from `__index` — a metatable may overload `+` without exposing any members at all.
                metamethods = metamethodsOf(metatable),
            )
        graph.addEdge(graph.value(o, augmented), resultNode)
        return true
    }

    /** The `__`-prefixed keys a metatable defines, both polarities (see [mergedTableOf]). */
    private fun metamethodsOf(metatable: LuaGraphType.Table): Set<String> =
        metatable
            .getMembers()
            .keys
            .filterTo(mutableSetOf()) { it.startsWith("__") }

    /** The table an argument denotes, across both polarities — see [mergedTableOf]. */
    private fun tableArgumentType(argExpr: PsiElement?): LuaGraphType.Table? =
        firstNode(unwrapExpression(argExpr))?.let { mergedTableOf(it) }

    /**
     * A node's table type merging what is WRITTEN to it with what is DEMANDED of it (BUG-426).
     *
     * Reading `write` alone is what confined `setmetatable` to inline table literals. `V.__index = V`
     * and `V.greet = …` go through `visitIndexExpr`, which records each member as a *demand*
     * (`graph.use`) on `V` and never touches its `write` — so a named metatable's write type is the
     * bare `{}` it was declared with, `__index` is nowhere in it, and the call bailed.
     *
     * The merge mirrors what [LuaTypesSnapshot] already does when it reports a variable's type, so
     * this is the view the rest of the IDE was seeing all along. A genuine write wins over a demand
     * on the same key: the demand is an inference about how the member is used, the write is what
     * the member is.
     */
    private fun mergedTableOf(node: TypeNode): LuaGraphType.Table? {
        val written = (node as? ValueNode)?.write as? LuaGraphType.Table
        val demanded = (node as? UseNode)?.read as? LuaGraphType.Table
        if (written == null && demanded == null) return null
        val members = LinkedHashMap<String, VariableNode>()
        demanded?.localMembers?.let { members.putAll(it) }
        written?.localMembers?.let { members.putAll(it) }
        return LuaGraphType.Table(
            className = written?.className ?: demanded?.className,
            localMembers = members,
            superTypes = written?.superTypes ?: emptyList(),
            isExact = written?.isExact ?: false,
        )
    }

    /**
     * The table exposed via a metatable's `__index` member: whatever flows INTO that member, or a
     * function's first table return.
     *
     * Resolved through the member's [VariableNode.upSet] rather than its `write`, because `write`
     * flattens to the source's write type — and for the `V.__index = V` idiom that is `V`'s bare
     * declared `{}`, dropping every member `V` gained by assignment. The source node itself still
     * carries both polarities.
     */
    private fun indexTableOf(metatable: LuaGraphType.Table): LuaGraphType.Table? {
        val indexNode = metatable.getMembers()["__index"] ?: return null
        indexNode.upSet.firstNotNullOfOrNull { mergedTableOf(it) }?.let { return it }
        return (indexNode.write as? LuaGraphType.Function)?.returns?.firstOrNull()?.write as? LuaGraphType.Table
    }

    private fun unwrapExpression(
        expr: PsiElement?,
        maxDepth: Int = 10,
    ): PsiElement? {
        var currentExpr = expr
        var depth = 0
        while (currentExpr != null && depth < maxDepth) {
            depth++
            if (currentExpr in elementNodes) return currentExpr

            val children = currentExpr.children.filter { it !is PsiWhiteSpace && it !is PsiComment }
            if (children.size == 1) {
                val child = children[0]
                if (child is LuaExpr ||
                    child is LuaNameRef ||
                    child is LuaVar ||
                    child is LuaPrefixExpr ||
                    child is LuaVarOrExp
                ) {
                    currentExpr = child
                    continue
                }
            }
            break
        }
        return currentExpr
    }

    private fun collectRhsNodes(exprs: List<LuaExpr>): List<TypeNode> {
        val result = mutableListOf<TypeNode>()
        exprs.forEachIndexed { i, expr ->
            val unwrapped = unwrapExpression(expr)
            val nodes = getNodes(unwrapped)
            if (i == exprs.size - 1) {
                // BUG-441 (RC-1): an expression the engine cannot model contributes NO node, so the
                // unknown vanishes instead of widening the reaching-definition set — measured,
                // `local d = wx.thing` gives `upSet.size=0`, making `d` byte-identical to a `d` that
                // was never assigned it. Absence is stronger erasure than a type: it is identical for
                // direct absorption but invisible to union formation and to the per-definition checks
                // in `checkTypes`. The branch below already does this for a non-last expression, with
                // a comment stating the principle; it was simply never applied to the position a
                // single-expression RHS always occupies.
                //
                // `Any`, not `Undefined`: `LuaTypeAlgebra.simplify` and `resolveWrite`'s `flatten`
                // both drop `Undefined` from a union, which would restore exactly today's behaviour
                // and fix nothing. `Any` already survives both and already makes a union gradual.
                if (nodes.isEmpty()) {
                    result.add(graph.value(expr, LuaGraphType.Any))
                } else {
                    result.addAll(nodes)
                }
            } else {
                nodes.firstOrNull()?.let { result.add(it) }
                    // Undefined, not nil: an RHS the engine cannot model is unknown, and a
                    // manufactured Nil would become a variable's sole — hence CERTAIN —
                    // reaching definition (BUG-416; the mechanism is BUG-359's).
                    ?: result.add(graph.value(expr, LuaGraphType.Undefined))
            }
        }
        return result
    }

    private fun getAllCatsComments(o: PsiElement): List<LuaCatsComment> {
        val result = mutableListOf<LuaCatsComment>()
        var current: PsiElement? = o.prevSibling
        while (current != null) {
            val typeStr = current.node.elementType.toString()
            val isCats = current is LuaCatsComment || typeStr.contains("LUACATS") || typeStr.contains("COMMENT")
            if (isCats) {
                val comment =
                    if (current is LuaCatsComment) {
                        current
                    } else {
                        PsiTreeUtil.findChildOfType(
                            current,
                            LuaCatsComment::class.java,
                        )
                    }
                if (comment != null) result.add(comment)
            } else if (current is PsiWhiteSpace || current is PsiComment) {
                // Skip
            } else {
                break
            }
            current = current.prevSibling
        }
        return result.reversed()
    }

    /**
     * BUG-427: the comments belonging to a function EXPRESSION, which owns none of its own.
     *
     * `---@param n number` above `count = function(n) end` is a sibling of the *assignment*, not of
     * the function, so [getAllCatsComments]' prev-sibling walk starts inside the statement and finds
     * nothing. The declaration form `function count(n) end` never had this problem, which is why
     * only the assignment form resolved with `fun(n: unknown)`.
     *
     * Restricted to a lone right-hand side. With several expressions there is no way to tell which
     * one a `@param` was written for, and guessing the first would attach a contract to code nobody
     * annotated.
     */
    private fun enclosingStatementCats(element: PsiElement): List<LuaCatsComment> {
        var statement: PsiElement? = element.parent
        while (statement != null && statement !is LuaAssignmentStatement && statement !is LuaLocalVarDecl) {
            if (statement is LuaBlock || statement is PsiFile) return emptyList()
            statement = statement.parent
        }
        val expressions =
            when (statement) {
                is LuaAssignmentStatement -> statement.exprList.exprList
                is LuaLocalVarDecl -> statement.exprList?.exprList ?: emptyList()
                else -> return emptyList()
            }
        val sole = expressions.singleOrNull() ?: return emptyList()
        if (!PsiTreeUtil.isAncestor(sole, element, false)) return emptyList()
        return statementCats(statement)
    }

    /**
     * The LuaCATS comments attached to a statement.
     *
     * `LuaLocalVarDecl` is a [LuaCommentOwner] and answers directly. `LuaAssignmentStatement` is
     * **not** — it is a bare `LuaStatement` — and its comment is frequently a sibling of an
     * *ancestor* rather than of the statement itself, so a flat prev-sibling walk finds nothing.
     * Climbing is what `LuaPsiImplUtil.getCatsComment` does for comment owners, with the same
     * ownership rule reproduced here: stop as soon as something else sits before us, because a
     * comment above *that* belongs to it.
     */
    private fun statementCats(statement: PsiElement): List<LuaCatsComment> {
        (statement as? LuaCommentOwner)?.catsComment?.let { return listOf(it) }
        var current: PsiElement? = statement
        while (current != null && current !is PsiFile) {
            val found = getAllCatsComments(current)
            if (found.isNotEmpty()) return found
            if (current.prevSibling != null) return emptyList()
            current = current.parent
        }
        return emptyList()
    }

    override fun visitFile(file: PsiFile) {
        if (rootReturnNodes.isEmpty()) {
            repeat(8) { rootReturnNodes.add(graph.variable(file)) }
        }

        declareFileGlobals(file)
        super.visitFile(file)

        val firstRet = rootReturnNodes.firstOrNull()?.write ?: LuaGraphType.Any
        fileReturnType = if (firstRet == LuaGraphType.Undefined) LuaGraphType.Any else firstRet
    }

    /**
     * BUG-395: binds every file-scope global into the **root** scope before the tree is walked.
     *
     * Lua globals are not order-dependent — `function table.concat()` in a stub file writes to the
     * same `table` its earlier `table = {}` created — but a single forward pass only learns a global
     * exists when it first meets a write, so the two ended up on unrelated nodes and the members were
     * stranded. Declaring in the root scope (blocks push children off it) also means the file's own
     * locals still shadow a same-named global, since [LuaScope.lookup] walks upward.
     */
    private fun declareFileGlobals(file: PsiFile) {
        val topLevel = (file as? LuaFile)?.getBlockList()?.flatMap { it.statementList } ?: return
        val fileLocals = fileScopeLocalNames(topLevel)
        globalWriteTargets(topLevel).forEach { (name, anchor) ->
            if (name !in fileLocals) scope.declare(name, globalNode(name, anchor))
        }
    }

    /** File-scope statements that write a global, as name → the PSI anchor for its node. */
    private fun globalWriteTargets(topLevel: List<PsiElement>): Map<String, PsiElement> {
        val targets = linkedMapOf<String, PsiElement>()
        topLevel.forEach { statement ->
            when (statement) {
                is LuaAssignmentStatement ->
                    statement.varList.varList.forEach { target ->
                        // A dotted target (`a.b = …`) writes a member, not the global itself.
                        val nameRef = target.nameRef?.takeIf { target.varSuffixList.isEmpty() }
                        if (nameRef != null) targets.putIfAbsent(nameRef.text, nameRef)
                    }
                is LuaFuncDecl -> {
                    val funcName = statement.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName
                    funcName?.nameRef?.let { targets.putIfAbsent(it.text, it) }
                }
                else -> Unit
            }
        }
        return targets
    }

    /** Names a file declares `local` at file scope — a write to those is a local write, not a global. */
    private fun fileScopeLocalNames(topLevel: List<PsiElement>): Set<String> {
        val names = mutableSetOf<String>()
        topLevel.forEach { statement ->
            // SYNTAX-18: a partially-parsed decl may lack its nameRef, and the generated getter is
            // @NotNull — reading it through the AST node keeps a broken file from logging an error.
            when (statement) {
                is LuaLocalVarDecl ->
                    statement.attNameList.forEach { attName ->
                        localName(attName)?.let { names += it }
                    }
                is LuaLocalFuncDecl -> localName(statement)?.let { names += it }
                else -> Unit
            }
        }
        return names
    }

    private fun localName(declaration: PsiElement): String? =
        declaration.node
            .findChildByType(LuaElementTypes.NAME_REF)
            ?.psi
            ?.text

    override fun visitBlock(o: LuaBlock) {
        val previousScope = scope
        scope = scope.child()
        try {
            super.visitBlock(o)
        } finally {
            scope = previousScope
        }
    }

    /**
     * TYPE-08: flow-sensitive narrowing. Each condition is parsed for a recognized type guard;
     * the matching block's child scope receives a narrowed binding for the guarded variable, and
     * a trailing `else` block receives the complement of every preceding guard. Narrowing is
     * block-local — the enclosing binding is restored on the way out (Risk 1.1).
     */
    override fun visitIfStatement(o: LuaIfStatement) {
        o.exprList.forEach { it.accept(this) }

        val conditions = o.exprList
        val guards = conditions.map { tryParseTypeofGuard(it) ?: tryParseNilGuard(it) }
        val blocks = o.getBlockList()
        val hasElseBlock = blocks.size > conditions.size

        blocks.forEachIndexed { index, block ->
            val previousScope = scope
            scope = scope.child()
            try {
                narrowBranchScope(guards, index, blocks.lastIndex, hasElseBlock)
                block.accept(this)
            } finally {
                scope = previousScope
            }
        }
    }

    /**
     * Injects narrowed bindings for branch [index] into the current (already-child) scope: a match
     * branch narrows on its own guard, while a trailing `else` block narrows on the complement of
     * every preceding guard in sequence (so chained `elseif` exclusions accumulate — TC-9/TC-10).
     */
    private fun narrowBranchScope(
        guards: List<TypeGuard?>,
        index: Int,
        lastIndex: Int,
        hasElseBlock: Boolean,
    ) {
        guards.getOrNull(index)?.let { injectNarrowedBinding(it, matchBranch = true) }
        if (hasElseBlock && index == lastIndex) {
            guards.filterNotNull().forEach { injectNarrowedBinding(it, matchBranch = false) }
        }
    }

    private fun injectNarrowedBinding(
        guard: TypeGuard,
        matchBranch: Boolean,
    ) {
        val originalNode = scope.lookup(guard.variableName) ?: return
        val narrowedType =
            if (matchBranch == guard.isEquality) {
                narrowType(originalNode.write, guard.narrowedType)
            } else {
                subtractType(originalNode.write, guard.narrowedType)
            }
        val narrowedValue = graph.value(guard.anchor, narrowedType)
        val narrowedVar = graph.variable(guard.anchor)
        graph.addEdge(narrowedValue, narrowedVar)
        scope.declare(guard.variableName, narrowedVar)
    }

    /**
     * BUG-435 — narrowing must never yield *less* than the type it narrows.
     *
     * The match branch took `guard.narrowedType` wholesale, so `if type(Shadow) == "table"` replaced
     * the local's real `Table(localMembers={fromLocal=…})` with the guard's bare
     * `Table(localMembers={})`. Read off the node rather than inferred:
     *
     * ```
     * originalWrite = Table(className=null, localMembers={fromLocal=…}, isExact=false)
     * guardNarrowed = Table(className=null, localMembers={},           isExact=false)
     * chosen        = Table(className=null, localMembers={},           isExact=false)   <- the bare one won
     * ```
     *
     * Inside the guard the value **is** a table, so a guard that only restates what the type already
     * says adds nothing and must not subtract: when [original] is already of the narrowed kind it is
     * kept, being at least as specific. Anything else — a union, or a type the guard genuinely
     * contradicts — is unchanged from before, so a narrowing that really does say something new
     * still wins.
     */
    private fun narrowType(
        original: LuaGraphType,
        to: LuaGraphType,
    ): LuaGraphType = if (original::class == to::class) original else to

    /** Removes [remove] from [original], delegating union subtraction to [LuaTypeAlgebra]. */
    private fun subtractType(
        original: LuaGraphType,
        remove: LuaGraphType,
    ): LuaGraphType =
        when {
            original == remove -> LuaGraphType.Undefined
            original is LuaGraphType.Union -> LuaTypeAlgebra.subtractMember(original, remove)
            else -> original
        }

    /** Recognizes `type(v) == "name"` / `type(v) ~= "name"`. Returns null on no match (silent). */
    private fun tryParseTypeofGuard(condition: LuaExpr): TypeGuard? {
        val binOp = condition as? LuaBinOpExpr ?: return null
        val op = binOp.node.findChildByType(LuaElementTypes.BIN_OP)?.text ?: return null
        if (op != "==" && op != "~=") return null

        val left = binOp.left
        val right = binOp.right ?: return null
        val typeCall = typeCallOf(left) ?: typeCallOf(right) ?: return null
        val stringSide = if (typeCallOf(left) != null) right else left

        val variableName = typeofArgumentName(typeCall) ?: return null
        val terminal = stringSide as? LuaTerminalExpr ?: return null
        val typeName = terminal.string?.text?.trim('"', '\'') ?: return null
        val narrowedType = TYPEOF_MAP[typeName]?.invoke() ?: LuaGraphType.Any

        return TypeGuard(variableName, narrowedType, isEquality = op == "==", anchor = binOp)
    }

    /** Returns the `type(...)` [LuaFuncCall] reachable from [expr], or null. */
    private fun typeCallOf(expr: LuaExpr?): LuaFuncCall? {
        expr ?: return null
        val funcCall = expr as? LuaFuncCall ?: PsiTreeUtil.findChildOfType(expr, LuaFuncCall::class.java) ?: return null
        return if (funcCall.varOrExp.text == "type") funcCall else null
    }

    /** Extracts the single positional variable name from a `type(v)` call. */
    private fun typeofArgumentName(typeCall: LuaFuncCall): String? {
        val nameAndArgs = typeCall.nameAndArgsList.singleOrNull() ?: return null
        val argExprs = nameAndArgs.args.exprList?.exprList ?: return null
        val arg = argExprs.singleOrNull() ?: return null
        return PsiTreeUtil.findChildOfType(arg, LuaNameRef::class.java)?.text
    }

    /** Recognizes `v == nil` / `v ~= nil`. Returns null on no match (silent). */
    private fun tryParseNilGuard(condition: LuaExpr): TypeGuard? {
        val binOp = condition as? LuaBinOpExpr ?: return null
        val op = binOp.node.findChildByType(LuaElementTypes.BIN_OP)?.text ?: return null
        if (op != "==" && op != "~=") return null

        val left = binOp.left
        val right = binOp.right ?: return null
        val nilSide = nilTerminalOf(left) ?: nilTerminalOf(right) ?: return null
        val nameSide = if (nilSide == left) right else left

        val variableName =
            PsiTreeUtil.findChildOfType(nameSide, LuaNameRef::class.java)?.text
                ?: (nameSide as? LuaNameRef)?.text ?: return null
        return TypeGuard(variableName, LuaGraphType.Nil, isEquality = op == "==", anchor = binOp)
    }

    /** Returns [expr] iff it is a `nil` terminal expression. */
    private fun nilTerminalOf(expr: LuaExpr?): LuaExpr? {
        val terminal = expr as? LuaTerminalExpr ?: return null
        return if (terminal.firstChild?.elementType == LuaElementTypes.NIL) terminal else null
    }

    override fun visitGenericForStatement(o: LuaGenericForStatement) {
        val previousScope = scope
        scope = scope.child()
        try {
            o.nameList.nameRefList.forEach {
                val varNode = graph.variable(it)
                scope.declare(it.text, varNode)
                elementNodes[it] = listOf(varNode)
            }
            super.visitGenericForStatement(o)
        } finally {
            scope = previousScope
        }
    }

    override fun visitNumericForStatement(o: LuaNumericForStatement) {
        val previousScope = scope
        scope = scope.child()
        try {
            val identifier = o.identifier
            if (identifier != null) {
                val varNode = graph.variable(identifier)
                scope.declare(identifier.text, varNode)
                elementNodes[identifier] = listOf(varNode)
            }
            super.visitNumericForStatement(o)
        } finally {
            scope = previousScope
        }
    }

    override fun visitTerminalExpr(o: LuaTerminalExpr) {
        super.visitTerminalExpr(o)
        val firstChildType = o.firstChild?.elementType
        val graphType =
            when {
                o.number != null -> LuaGraphType.Number
                o.string != null -> LuaGraphType.String
                firstChildType == LuaElementTypes.NIL -> LuaGraphType.Nil
                firstChildType == LuaElementTypes.TRUE -> LuaGraphType.Boolean
                firstChildType == LuaElementTypes.FALSE -> LuaGraphType.Boolean
                firstChildType == LuaElementTypes.ELLIPSIS -> {
                    val bound = scope.lookup("...")
                    if (bound != null) {
                        elementNodes[o] = listOf(bound)
                        return
                    }
                    LuaGraphType.Any
                }
                else -> return
            }
        val valueNode = graph.value(o, graphType)
        elementNodes[o] = listOf(valueNode)
    }

    override fun visitBinOpExpr(o: LuaBinOpExpr) {
        super.visitBinOpExpr(o)
        val left = o.left
        val right = o.right
        val op = o.node.findChildByType(LuaElementTypes.BIN_OP)?.text ?: ""

        // Undefined, not nil: these feed DIRECT operator edges, where a value is treated as
        // certain — a manufactured Nil here is a manufactured error (BUG-359 / BUG-416).
        val leftNode = firstNode(unwrapExpression(left)) ?: graph.value(left, LuaGraphType.Undefined)
        val rightNode = firstNode(unwrapExpression(right)) ?: graph.value(right ?: o, LuaGraphType.Undefined)

        val resType =
            when (op) {
                // The demand is a TRAIT, not the result type (BUG-423): Lua coerces a string
                // operand at arithmetic and a number operand at `..`. The result stays exact.
                "+", "-", "*", "/", "//", "^", "%" -> {
                    graph.addEdge(leftNode, graph.use(o, LuaGraphType.Trait.Numberable, declaredDemand = true))
                    graph.addEdge(rightNode, graph.use(o, LuaGraphType.Trait.Numberable, declaredDemand = true))
                    LuaGraphType.Number
                }
                ".." -> {
                    graph.addEdge(leftNode, graph.use(o, LuaGraphType.Trait.Stringable, declaredDemand = true))
                    graph.addEdge(rightNode, graph.use(o, LuaGraphType.Trait.Stringable, declaredDemand = true))
                    LuaGraphType.String
                }
                "==", "~=", "<", ">", "<=", ">=" -> {
                    LuaGraphType.Boolean
                }
                "and", "or" -> {
                    val leftType = (leftNode as? ValueNode)?.write ?: LuaGraphType.Any
                    val rightType = (rightNode as? ValueNode)?.write ?: LuaGraphType.Any
                    // `a and b` yields a's FALSY value or b; `a or b` yields a's TRUTHY value or b.
                    // Unioning both operands whole instead injected a `boolean` arm into every
                    // `cond and x or y` — Lua's ternary — and the arm then failed any string or
                    // number demand downstream. Invisible until BUG-427 let such a value cross a
                    // file boundary, at which point it produced 312 of 335 new corpus errors.
                    // An unknown right operand makes the whole expression unknown. Without this the
                    // surviving arm is the left's falsy part alone, so `value and f(...)` with an
                    // un-inferable `f` collapsed to a bare `nil` and every use of the result was
                    // reported — 40 corpus false positives, worse than the imprecision being fixed.
                    // BUG-441: `Any`, not `Undefined`, when the LEFT operand is unmodellable.
                    // `Union.create` canonicalizes an `Undefined` arm away (`LuaTypeAlgebra.simplify`
                    // filters it, and `truthyPart`'s own KDoc says so), so `wx.thing or "s"` collapsed
                    // to a bare `string` and the unknown half of the expression was gone by the time
                    // anything could act on it. `Any` survives the same canonicalization and already
                    // makes a union gradual, so the arm reaches `checkTypes` where it belongs.
                    val carriedPart = if (op == "and") falsyPart(leftType) else truthyPart(leftType)
                    val carried =
                        if (carriedPart == LuaGraphType.Undefined) LuaGraphType.Any else carriedPart
                    if (rightType == LuaGraphType.Undefined) {
                        LuaGraphType.Undefined
                    } else {
                        LuaGraphType.Union.create(setOf(carried, rightType))
                    }
                }
                else -> LuaGraphType.Any
            }
        elementNodes[o] = listOf(graph.value(o, resType))
    }

    /**
     * The arms of [type] that survive Lua's truthiness test — everything but `nil` and `false`.
     *
     * `boolean` goes unconditionally, not just when another arm remains. Without literal types
     * there is no way to say "`true`", and the arm the engine would keep is `false` — which by
     * definition never reaches the left-hand result. Keeping it "when nothing else remains" was
     * tried and left all 312 corpus false positives in place, because the informative arms of a
     * `cond and x or y` chain are frequently `Undefined` and `Union.create` drops those first, so
     * the boolean was the last arm standing exactly where it was most wrong.
     *
     * The cost is `flag or default` reporting only `default`'s type. That is under-reporting, which
     * is the posture BUG-419 chose deliberately.
     */
    private fun truthyPart(type: LuaGraphType): LuaGraphType {
        val arms = if (type is LuaGraphType.Union) type.types else setOf(type)
        val truthy = arms.filterNot { it == LuaGraphType.Nil || it == LuaGraphType.Boolean }
        return if (truthy.isEmpty()) LuaGraphType.Undefined else LuaGraphType.Union.create(truthy)
    }

    /**
     * The arms of [type] that fail Lua's truthiness test. `nil` and `false` are the only ones, so a
     * type with neither contributes nothing to `a and b` — the result is then just `b`.
     */
    private fun falsyPart(type: LuaGraphType): LuaGraphType {
        val arms = if (type is LuaGraphType.Union) type.types else setOf(type)
        val falsy = arms.filter { it == LuaGraphType.Nil || it == LuaGraphType.Boolean }
        return if (falsy.isEmpty()) LuaGraphType.Undefined else LuaGraphType.Union.create(falsy)
    }

    override fun visitUnOpExpr(o: LuaUnOpExpr) {
        super.visitUnOpExpr(o)
        val op = o.unOp.text
        val right = o.right
        // Undefined, not nil — same reasoning as visitBinOpExpr (BUG-416).
        val rightNode = firstNode(unwrapExpression(right)) ?: graph.value(right ?: o, LuaGraphType.Undefined)

        val resType =
            when (op) {
                "#" -> {
                    // string, table, or array (REDIS-04 §3.1b: #ARGV over string[]) — the same set
                    // as before, now named: it was already a trait, spelled as an inline union.
                    graph.addEdge(rightNode, graph.use(o, LuaGraphType.Trait.Lengthable, declaredDemand = true))
                    LuaGraphType.Number
                }
                "-" -> {
                    // `-"5"` is −5, so unary minus coerces exactly as binary arithmetic does.
                    graph.addEdge(rightNode, graph.use(o, LuaGraphType.Trait.Numberable, declaredDemand = true))
                    LuaGraphType.Number
                }
                "not" -> LuaGraphType.Boolean
                else -> LuaGraphType.Any
            }
        elementNodes[o] = listOf(graph.value(o, resType))
    }

    override fun visitTableConstructor(o: LuaTableConstructor) {
        super.visitTableConstructor(o)

        val localMembers = mutableMapOf<String, VariableNode>()
        o.fieldList?.fieldList?.forEach { field ->
            val key = field.identifier?.text
            val valExpr = field.exprList.lastOrNull()
            if (key != null && valExpr != null) {
                // Undefined, not nil: an unmodeled field initializer must not declare the
                // member to be nil — that is the placeholder misreading of BUG-416.
                val valNode = firstNode(unwrapExpression(valExpr)) ?: graph.value(valExpr, LuaGraphType.Undefined)
                val memberNode = graph.variable(field, declaresMember = true)
                graph.addEdge(valNode, memberNode)
                localMembers[key] = memberNode
            }
        }
        val tableType = LuaGraphType.Table(null, localMembers)
        elementNodes[o] = listOf(graph.value(o, tableType))
    }

    override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
        super.visitLocalVarDecl(o)

        val names = o.attNameList.map { it.nameRef }
        val exprs = o.exprList?.exprList ?: emptyList()
        val rhsNodes = collectRhsNodes(exprs)

        val varNodes =
            names.map { nameRef ->
                val varNode = graph.variable(nameRef)
                scope.declare(nameRef.text, varNode)
                elementNodes[nameRef] = listOf(varNode)
                varNode
            }
        elementNodes[o] = varNodes

        graph.flowList(rhsNodes, varNodes)

        // LuaCATS @type or @class injection
        val cats = o.catsComment
        if (cats != null) {
            varNodes.forEach { varNode ->
                LuaTypeGraphBridge.injectTypeAnnotation(cats, o, varNode, graph, o)
            }
        }
    }

    override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) {
        val funcNode = graph.variable(o)
        // SYNTAX-18: a pinned partial decl may lack its nameRef; the stub getter is @NotNull.
        o.node
            .findChildByType(LuaElementTypes.NAME_REF)
            ?.psi
            ?.let { scope.declare(it.text, funcNode) }
        elementNodes[o] = listOf(funcNode)

        visitFunctionBody(
            element = o,
            parList = o.parList,
            funcNode = funcNode,
        )
    }

    override fun visitFuncDef(o: LuaFuncDef) {
        val funcNode = graph.variable(o)
        elementNodes[o] = listOf(funcNode)
        visitFunctionBody(element = o, parList = o.parList, funcNode = funcNode)
    }

    override fun visitFuncDecl(o: LuaFuncDecl) {
        val funcNode = graph.variable(o)
        elementNodes[o] = listOf(funcNode)

        // SYNTAX-18: a pinned partial decl may lack its funcName; the stub getter is @NotNull.
        val funcName = o.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName ?: return
        val baseName = funcName.nameRef.text
        val baseVar =
            scope.lookup(baseName) ?: run {
                // Not in scope means a global written from a nested block; share the file's node for it
                // so its members join the same type (BUG-395).
                val fresh = globalNode(baseName, funcName.nameRef)
                scope.declare(baseName, fresh)
                fresh
            }

        var calleeNode: VariableNode = baseVar
        funcName.funcNamePropertyList.forEach { prop ->
            val propName = prop.nameRef?.text
            if (propName != null) {
                val memberNode = graph.variable(prop, declaresMember = true)
                val tableConstraint = LuaGraphType.Table(localMembers = mapOf(propName to memberNode))
                graph.addEdge(calleeNode, graph.use(prop, tableConstraint))
                calleeNode = memberNode
            }
        }

        val method = funcName.funcNameMethod
        // COMP-04-09: for a `:` method, `calleeNode` here still holds the receiver's type node
        // (e.g. `C` in `function C:m()`), so `self` can flow from it. Captured before the branch
        // below reassigns `calleeNode` to the method member node.
        val selfReceiver: VariableNode? = if (method?.nameRef?.text != null) calleeNode else null
        val selfAnchor: PsiElement? = method?.nameRef
        if (method != null) {
            val methodName = method.nameRef?.text
            if (methodName != null) {
                val memberNode = graph.variable(method, declaresMember = true)
                val tableConstraint = LuaGraphType.Table(localMembers = mapOf(methodName to memberNode))
                graph.addEdge(calleeNode, graph.use(method, tableConstraint))
                calleeNode = memberNode
            }
        }

        graph.addEdge(graph.value(o, LuaGraphType.Undefined), calleeNode) // Initial placeholder
        graph.addEdge(funcNode, calleeNode)

        if (selfReceiver != null && selfAnchor != null) {
            pendingSelf = SelfBinding(selfReceiver, selfAnchor)
        }
        visitFunctionBody(element = o, parList = o.parList, funcNode = funcNode)
    }

    override fun visitFuncCall(o: LuaFuncCall) {
        super.visitFuncCall(o)

        val calleeExpr = o.varOrExp
        val calleeUnwrapped = unwrapExpression(calleeExpr)

        val callResultNodes = List(8) { graph.variable(o) }
        elementNodes[o] = callResultNodes

        // Special handling for require() — must come before the calleeNode guard because
        // `require` is a Lua built-in and has no scope binding, so firstNode() returns null.
        if (isRequireCall(calleeUnwrapped)) {
            val moduleName = extractModuleName(o)
            if (moduleName != null) {
                val project = o.project
                val moduleType = LuaTypeManager.getInstance(project).resolveModule(moduleName, o)
                if (moduleType != null) {
                    val moduleGraphType = LuaGraphType.fromLuaType(moduleType, graph)
                    graph.addEdge(graph.value(o, moduleGraphType), callResultNodes.first())
                    return
                }
            }
        }

        // COMP-04-08: setmetatable(t, mt) exposes mt.__index's members on t.
        if (calleeUnwrapped?.text == "setmetatable") {
            if (handleSetMetatable(o, callResultNodes.first())) return
        }

        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return
        val argExprs = argExpressionsOf(nameAndArgs)

        // TYPE-10: seed passed lambdas' params from the callee's expected callback types. Runs
        // before the calleeNode guard so bundled-stub callees (redis.register_function, table.sort),
        // which have no in-file graph node, are still handled.
        propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)

        val calleeNodeRef = firstNode(calleeUnwrapped) ?: return

        // BUG-397: a declaration-typed callee (a free global like `print`, or a free-global
        // member like `redis.pcall`) contributes its declared return to the call results and
        // raises no CALL demand on the callee itself — wiring one is what regressed
        // `redis.register_function`'s `@overload` table form in the reverted attempts, and it
        // would also push call-site argument nodes into the shared seed's parameter nodes (one
        // seed serves every call in the file).
        //
        // BUG-425: its declared PARAMETER types are still checked, per argument and per call site.
        // Without that, a `---@param` one file away — every stdlib signature, every definition
        // library — was not merely demoted, it was never looked at.
        if (calleeNodeRef in declarationTypedNodes) {
            val declared = declaredFunctionOf(calleeNodeRef, nameAndArgs.methodExpr?.nameRef?.text)
            declared?.let {
                seedDeclaredReturns(it, callResultNodes)
                demandDeclaredParams(it, argExprs)
            }
            return
        }

        var calleeNode = calleeNodeRef
        val methodExpr = nameAndArgs.methodExpr
        if (methodExpr != null) {
            val methodName = methodExpr.nameRef?.text
            if (methodName != null) {
                val memberNode = graph.variable(methodExpr)
                val tableConstraint = LuaGraphType.Table(localMembers = mapOf(methodName to memberNode))
                graph.addEdge(calleeNode, graph.use(methodExpr, tableConstraint))
                calleeNode = memberNode
            }
        }

        val argNodes =
            argExprs.map { argExpr ->
                val unwrapped = unwrapExpression(argExpr)
                val nodes = getNodes(unwrapped)
                // Undefined, not nil: a node-less argument is an expression the engine has no opinion
                // about (a free global's placeholder member, an unmodeled construct), and encoding "no
                // information" as "exactly nil" is BUG-359's mechanism at the call-arg site — it turns
                // every such argument into a manufactured "nil value is not assignable to …" (BUG-416).
                // A literal `nil` argument is unaffected; it gets a real Nil node from its own visit.
                val node = nodes.firstOrNull() ?: graph.value(argExpr, LuaGraphType.Undefined)
                LuaGraphType.Function.Parameter(
                    graph.variable(argExpr).apply {
                        graph.addEdge(node, this)
                    },
                )
            }

        val callDemand =
            LuaGraphType.Function(
                params = argNodes,
                returns = callResultNodes,
            )

        // If the callee is a generic function template, instantiate it for this call site.
        if (calleeNode is ValueNode && calleeNode.write is LuaGraphType.Function) {
            val funcTemplate = calleeNode.write as LuaGraphType.Function
            val isGeneric =
                funcTemplate.params.any { it.node.write is LuaGraphType.Generic } ||
                    funcTemplate.returns.any { it.write is LuaGraphType.Generic }

            if (isGeneric) {
                val instantiated = graph.instantiateGeneric(funcTemplate, o)
                graph.addEdge(graph.value(o, instantiated), graph.use(o, callDemand))
                return
            }
        }

        graph.addEdge(calleeNode, graph.use(o, callDemand))
    }

    /**
     * The declared signature a declaration-typed callee names.
     *
     * For a `:` method call the callee seed holds the *receiver*; the method's function type is
     * projected out of the seed's declared members. A non-function (or missing) declared type
     * resolves to null, leaving everything downstream exactly as before (BUG-397).
     */
    private fun declaredFunctionOf(
        calleeNode: TypeNode,
        methodName: String?,
    ): LuaGraphType.Function? {
        val write = (calleeNode as? ValueNode)?.write ?: return null
        return if (methodName == null) {
            write as? LuaGraphType.Function
        } else {
            write.getMembers()[methodName]?.write as? LuaGraphType.Function
        }
    }

    /** Flows a declared signature's returns into the call's result nodes (BUG-397). */
    private fun seedDeclaredReturns(
        funcType: LuaGraphType.Function,
        callResultNodes: List<VariableNode>,
    ) {
        funcType.returns.forEachIndexed { index, returnNode ->
            callResultNodes.getOrNull(index)?.let { graph.addEdge(returnNode, it) }
        }
    }

    /**
     * BUG-425: check each argument against the declared parameter type at that position.
     *
     * A **fresh use node per argument**, rather than an edge into the signature's own parameter
     * node: [freeGlobalSeed] memoizes one seed per name, so those parameter nodes are shared by
     * every call in the file and writing call-site values into them would make call sites
     * contaminate one another.
     *
     * The demand is `declaredDemand = true` — a stub signature is a contract somebody wrote, which
     * is exactly BUG-419's distinction between a contract and a guess.
     */
    private fun demandDeclaredParams(
        declared: LuaGraphType.Function,
        argExprs: List<PsiElement>,
    ) {
        if (!positionsAreUnambiguous(declared, argExprs.size)) return
        argExprs.forEachIndexed { index, argExpr ->
            val param = declared.params.getOrNull(index)?.takeUnless { it.isVararg } ?: return@forEachIndexed
            val declaredType = param.node.read
            if (carriesGraphNodes(declaredType)) return@forEachIndexed
            val argNode = firstNode(unwrapExpression(argExpr)) ?: return@forEachIndexed
            graph.addEdge(argNode, graph.use(argExpr, declaredType, declaredDemand = true))
        }
    }

    /**
     * Whether [type] embeds graph nodes, and therefore cannot safely be used as a demand here.
     *
     * A structural check against such a type does not merely compare: `checkFunctionCompatibility`
     * and `checkTableCompatibility` **wire edges into the type's own member and parameter nodes**.
     * Those nodes belong to the memoized seed shared by every call in the file, so a single call
     * site would rewrite the signature everybody else reads — measured, as
     * `ExpectedCallbackResolverTest.testTableSortComparatorSlotResolves` going red when the
     * comparator slot picked up one call's lambda.
     *
     * So BUG-425 checks scalar contracts only. A `---@param cb fun(...)` or `---@param w wxWindow`
     * is still unchecked; doing those needs a demand built from a *copy* of the declared type, which
     * is a larger change than this bug. TYPE-10's `propagateExpectedLambdaParams` already covers the
     * callback case for inference, which is the part users see.
     */
    private fun carriesGraphNodes(type: LuaGraphType): Boolean =
        when (type) {
            is LuaGraphType.Function, is LuaGraphType.Table -> true
            is LuaGraphType.Array -> carriesGraphNodes(type.elementType)
            is LuaGraphType.Union -> type.types.any { carriesGraphNodes(it) }
            else -> false
        }

    /**
     * Whether argument *positions* can be matched to parameters at all — the guard that keeps
     * overloaded and optional-parameter calls unreported.
     *
     * Requires an exact count and no vararg, which is much stricter than "the arity fits", and
     * deliberately so. **Measured**: a fits-the-arity rule put 244 false positives on the corpus,
     * 123 of them from `table.insert` alone:
     *
     * ```lua
     * ---@overload fun(list: table, value: any): nil
     * ---@param list table
     * ---@param pos? integer
     * ---@param value? any
     * function table.insert(list, pos, value) end
     *
     * table.insert(stack, "block start")   -- 2 args, arity fits, position 1 is `pos: integer`
     * ```
     *
     * The caller omitted a *middle* parameter, which positional matching cannot see. Only the
     * primary signature is modelled — `@overload` never reaches the type engine — so any call that
     * does not fill every slot is a call the engine cannot align, and aligning it anyway checks
     * arguments against the wrong parameters. That is the same reasoning that already keeps arity
     * itself unreported (`testDeclarationTypedCalleeIsNotArityChecked`), applied to types.
     *
     * The cost is under-reporting on optional-parameter calls. That is BUG-419's posture, and the
     * alternative was measured to be worse than the bug being fixed.
     */
    private fun positionsAreUnambiguous(
        declared: LuaGraphType.Function,
        count: Int,
    ): Boolean = declared.params.none { it.isVararg } && count == declared.params.size

    /** The per-argument expression list for a call (string / exprList / tableConstructor form). */
    private fun argExpressionsOf(nameAndArgs: LuaNameAndArgs): List<PsiElement> {
        val args = nameAndArgs.args
        return when {
            args.string != null -> args.string?.let { listOf(it) } ?: emptyList()
            args.exprList != null -> args.exprList?.exprList ?: emptyList()
            args.tableConstructor != null -> listOf(args.tableConstructor!!)
            else -> emptyList()
        }
    }

    /**
     * TYPE-10 §3.1: for each lambda argument passed into a `fun(...)`-typed callee slot, seed the
     * lambda's un-annotated parameters from the expected callback type. Additive — only new
     * `value → lambda-paramNode` edges are added; a direct `---@param` on a lambda parameter wins
     * ([isAlreadyAnnotated]). No-op when the callee has no resolvable `LuaFunctionType`.
     */
    private fun propagateExpectedLambdaParams(
        o: LuaFuncCall,
        argExprs: List<PsiElement>,
        calleeUnwrapped: PsiElement?,
    ) {
        val resolver = LuaExpectedCallbackResolver(o, calleeUnwrapped)
        val calleeType = resolver.resolveCalleeType() ?: return
        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return
        val selfOffset = if (nameAndArgs.methodExpr != null && calleeType.params.firstOrNull()?.name == "self") 1 else 0
        argExprs.forEachIndexed { index, argExpr ->
            val lambda = unwrapExpression(argExpr) as? LuaFuncDef ?: return@forEachIndexed
            val expected = resolver.expectedCallbackAt(index, calleeType, selfOffset) ?: return@forEachIndexed
            seedLambdaParams(lambda, expected)
        }
    }

    /** TYPE-10 §3.1 step 3: positional, arity-clamped seeding of one lambda's parameter nodes. */
    private fun seedLambdaParams(
        lambda: LuaFuncDef,
        expected: LuaFunctionType,
    ) {
        val lambdaParams = lambda.parList?.nameList?.nameRefList ?: emptyList()
        lambdaParams.forEachIndexed { i, nameRef ->
            val expectedParam = expected.params.getOrNull(i) ?: return
            val paramNode = elementNodes[nameRef]?.firstOrNull() as? VariableNode ?: return@forEachIndexed
            if (isAlreadyAnnotated(paramNode)) return@forEachIndexed
            val graphType = LuaGraphType.fromLuaType(expectedParam.type, graph)
            graph.addEdge(graph.value(nameRef, graphType), paramNode)
        }
    }

    /**
     * TYPE-10 §3.1 step 4 (TYPE-10-03 precedence): a lambda parameter carrying its own `---@param`
     * already has a non-`Undefined` `write` from the injected value edge, so the expected-type seed
     * must skip it. An un-annotated parameter's `write` is `Undefined` at propagation time.
     */
    private fun isAlreadyAnnotated(paramNode: VariableNode): Boolean = paramNode.write != LuaGraphType.Undefined

    override fun visitAssignmentStatement(o: LuaAssignmentStatement) {
        super.visitAssignmentStatement(o)

        val vars = o.varList.varList
        val exprs = o.exprList.exprList
        val rhsNodes = collectRhsNodes(exprs)

        val varNodes =
            vars.map { v ->
                val unwrapped = unwrapExpression(v)
                firstNode(unwrapped) as? VariableNode ?: graph.variable(v)
            }

        val cats = getAllCatsComments(o)
        cats.forEach { cat ->
            varNodes.firstOrNull()?.let {
                LuaTypeGraphBridge.injectTypeAnnotation(cat, o, it, graph, o)
            }
        }

        graph.flowList(rhsNodes, varNodes)
    }

    override fun visitVar(o: LuaVar) {
        super.visitVar(o)
        val suffixes = o.varSuffixList
        if (suffixes.isNotEmpty()) {
            elementNodes[o] = getNodes(suffixes.last())
        } else {
            elementNodes[o] = getNodes(o.nameRef)
        }
    }

    override fun visitVarSuffix(o: LuaVarSuffix) {
        super.visitVarSuffix(o)
        elementNodes[o] = getNodes(o.indexExpr)
    }

    override fun visitIndexExpr(o: LuaIndexExpr) {
        super.visitIndexExpr(o)
        val nameRef = o.nameRef
        if (nameRef != null) {
            val varElement = PsiTreeUtil.getParentOfType(o, LuaVar::class.java)
            if (varElement != null) {
                val receiverNode = firstNode(unwrapExpression(varElement.firstChild))
                // A declaration-typed receiver resolves members through the declared route first
                // (annotations are authoritative); the graph constraint is the fallback for its
                // undeclared members and for ordinary in-file receivers.
                val declarationTyped = receiverNode == null || receiverNode in declarationTypedNodes
                if (declarationTyped) {
                    if (seedDeclaredMember(o, varElement, nameRef)) return
                    // The graph path below anchors EVERY suffix on the bare receiver, so letting
                    // a later suffix of a declaration-typed chain fall through would resolve
                    // `A.b.c` as `A.c` — checking `c` against A's declared members and wiring
                    // bi-directional edges into the shared seed's member nodes (false positives
                    // + cross-chain pollution; adversarial-review F1/F2). An undeclared chain
                    // member stays node-less instead, exactly as before BUG-397.
                    if (varElement.varSuffixList.firstOrNull()?.indexExpr != o) return
                }
                if (receiverNode == null) return
                val memberNode = graph.variable(o, declaresMember = isAssignmentTarget(o))
                val tableConstraint = LuaGraphType.Table(localMembers = mapOf(nameRef.text to memberNode))
                graph.addEdge(receiverNode, graph.use(o, tableConstraint))
                elementNodes[o] = listOf(memberNode)
            }
        } else if (o.expr != null) {
            seedSubscriptElement(o)
        }
    }

    /**
     * TYPE-13 design §3.3, property (P): the node minted above for `o` may claim a declaration only
     * when the assignment writes the name `o` carries directly on the `var`'s bare head — that is,
     * when no navigation step stands between the head and `o`. A `var` applies exactly two step
     * kinds to its head (`varSuffix ::= nameAndArgs* indexExpr`), so both must be tested: counting
     * suffixes alone bounds the index steps but says nothing about a call step bundled inside the
     * sole suffix (`t().m = f` is one `varSuffix` whose `nameAndArgsList` is `[()]`).
     */
    private fun isAssignmentTarget(o: LuaIndexExpr): Boolean {
        val enclosingVar = PsiTreeUtil.getParentOfType(o, LuaVar::class.java) ?: return false
        if (enclosingVar.parent !is LuaVarList) return false
        val soleSuffix = enclosingVar.varSuffixList.singleOrNull() ?: return false
        return soleSuffix.nameAndArgsList.isEmpty() && soleSuffix.indexExpr === o
    }

    /**
     * BUG-397 Phase 2: a member access whose receiver is a *free global* — unbound in every scope,
     * so the graph path above has no receiver node — is typed from the receiver's cross-file
     * declaration via [LuaTypeManager.resolveGlobal], the same source member completion uses
     * (BUG-395). `package.path` reads `string` off the stdlib stub instead of falling into the
     * `graph.nil` fallbacks (which is what manufactured BUG-359's "nil value is not assignable to
     * string" on the concat), and `redis.pcall` carries its declared function type.
     *
     * Deliberately **typing, not checking**: the seed is a [ValueNode] with no use constraint, and
     * [visitFuncCall] flows a seeded callee's declared *return* into the call results without
     * raising the call-demand check — the twice-reverted wire-up regressed precisely because the
     * checker started checking stub calls it previously skipped (arity on `redis.register_function`'s
     * `@overload` table form). An unresolvable receiver seeds nothing, keeping today's behavior for
     * genuinely undefined names. A chain (`A.b.c`) resolves each suffix against the previous
     * suffix's declared type; an undeclared link leaves the rest of the chain node-less — it must
     * NOT fall back to the bare-receiver-anchored graph path, which would resolve `A.b.c` as `A.c`
     * (adversarial-review F1/F2).
     */
    private fun seedDeclaredMember(
        o: LuaIndexExpr,
        varElement: LuaVar,
        nameRef: LuaNameRef,
    ): Boolean {
        val suffixes = varElement.varSuffixList
        val index = suffixes.indexOfFirst { it.indexExpr == o }
        if (index < 0) return false
        val declared =
            if (index == 0) {
                val receiverName = (unwrapExpression(varElement.firstChild) as? LuaNameRef)?.text ?: return false
                declaredMemberType(receiverName, nameRef.text, o)
            } else {
                // A later suffix chains through the PREVIOUS suffix's declared type (visited just
                // before this one), never through the bare receiver — `A.b.c` reads `c` off `A.b`.
                val previous = suffixes[index - 1].indexExpr?.let { firstNode(it) } ?: return false
                if (previous !in declarationTypedNodes) return false
                (previous as? ValueNode)
                    ?.write
                    ?.getMembers()
                    ?.get(nameRef.text)
                    ?.write
                    ?: LuaGraphType.Undefined
            }
        if (declared == LuaGraphType.Undefined) return false
        // A member DECLARED as exactly `nil` is the declare-now-fill-later placeholder —
        // `ide = { frame = nil }` (zerobrane main.lua:64) means "exists later, type unknown", not
        // "always nil". Seeding it as Nil made every later use an error (959× on one member,
        // BUG-416). Node-less is the honest reading: pre-BUG-397 behaviour, no claim either way.
        if (declared == LuaGraphType.Nil) return false
        val seed = graph.value(o, declared, declaredOrigin = true)
        declarationTypedNodes.add(seed)
        elementNodes[o] = listOf(seed)
        return true
    }

    /** The declared type of `<receiverName>.<memberName>` per the receiver's cross-file global. */
    private fun declaredMemberType(
        receiverName: String,
        memberName: String,
        context: PsiElement,
    ): LuaGraphType {
        val globalType =
            LuaTypeManager.getInstance(context.project).resolveGlobal(receiverName, context)
                ?: return LuaGraphType.Undefined
        val member = globalType.getMembers()[memberName] ?: return LuaGraphType.Undefined
        return LuaGraphType.fromLuaType(member.type, graph)
    }

    /**
     * REDIS-04 §3.1b: bracket-subscript element inference. For a subscript `receiver[index]` whose
     * receiver's value type resolves to an `Array(T)` (directly, or as a member of a union — e.g.
     * a `---@type string[]` local infers as `{ ... } | string[]`), record the subscript's element
     * type as `T`. A non-array receiver projects to `Undefined` → the subscript stays `Undefined`,
     * exactly as before (§3.1c regression contract, invariant 2).
     *
     * TYPE-10 §3.4: the element type is a **lazy** projection over the receiver's `write`, computed
     * at read time rather than eagerly at visit time. This closes the intra-traversal ordering
     * hazard — a lambda body's `keys[1]` subscript is visited during `super.visitFuncCall`, before
     * `propagateExpectedLambdaParams` seeds the `keys` receiver; laziness lets the later seed edge
     * be observed (the snapshot is read only after the full traversal + `checkTypes()`).
     */
    private fun seedSubscriptElement(o: LuaIndexExpr) {
        val varElement = PsiTreeUtil.getParentOfType(o, LuaVar::class.java) ?: return
        val receiverNode = firstNode(unwrapExpression(varElement.firstChild)) as? ValueNode ?: return
        elementNodes[o] =
            listOf(
                graph.lazyValue(o) { visited ->
                    arrayElementType(receiverNode.writeWith(visited)) ?: LuaGraphType.Undefined
                },
            )
    }

    private fun arrayElementType(type: LuaGraphType): LuaGraphType? =
        when (type) {
            is LuaGraphType.Array -> type.elementType
            is LuaGraphType.Union ->
                type.types
                    .filterIsInstance<LuaGraphType.Array>()
                    .firstOrNull()
                    ?.elementType
            else -> null
        }

    override fun visitFinalStatement(o: LuaFinalStatement) {
        super.visitFinalStatement(o)
        if (o.text.startsWith("return")) {
            val exprs = o.exprList?.exprList ?: emptyList()
            val rhsNodes = collectRhsNodes(exprs)
            elementNodes[o] = rhsNodes
            val returnNodes = scope.enclosingReturnNodes()
            if (returnNodes != null) {
                while (returnNodes.size < rhsNodes.size) {
                    returnNodes.add(graph.variable(o))
                }
                graph.flowList(rhsNodes, returnNodes)
            }
        }
    }

    /**
     * Binds a name to the node its scope holds — and, deliberately, to nothing otherwise.
     *
     * BUG-397: an unbound name falls back to its cross-file declaration via
     * [LuaTypeManager.resolveGlobal], so every free global (`table`, `redis`, `package`, a
     * project-wide `Lib`) is typed for *all* consumers — hover, inlays, inspections — not only for
     * completion. This wire-up was tried twice before and reverted twice, because **binding the
     * receiver displaced a better-informed member path**: with `redis` unbound, [visitIndexExpr]
     * returned early and `redis.pcall`'s type came from the stub-derived route with its
     * `---@return any|{ err: string }` union intact; bound, the member flowed through a graph
     * `Table` constraint where [LuaTypeAlgebra.canonicalize] collapsed `any | X` to `Any` (killing
     * `reply.err`), `redis.register_function`'s `@overload` table form grew arity errors, and
     * `table.sort`'s comparator degraded. Landing it required settling two ownership rules first:
     *
     * 1. **`any | <structural>` keeps its structural arms** ([LuaTypeAlgebra.canonicalize]), and a
     *    union keeping an `Any` arm is gradual — never an assignability error (Phase 1).
     * 2. **Declared types are authoritative; graph inference fills unannotated slots.** A seed in
     *    [declarationTypedNodes] contributes values but never raises call demands, and
     *    [visitIndexExpr] resolves its members through the declared route (Phase 2).
     *
     * With those in place the fallback displaces nothing: it only adds types where there were
     * none. `FreeGlobalMemberTypingTest` characterizes the three historical regression shapes.
     */
    override fun visitNameRef(o: LuaNameRef) {
        super.visitNameRef(o)
        val boundNode = scope.lookup(o.text)
        if (boundNode != null) {
            elementNodes[o] = listOf(boundNode)
            return
        }
        // Seed only value positions (a bare `redis`, a receiver `package` in `package.path`, both
        // LuaVar-wrapped). Member names, func-decl properties and method names are also LuaNameRefs
        // and must not be mistaken for a read of a same-named global.
        if (o.parent is LuaVar) {
            freeGlobalSeed(o)?.let { elementNodes[o] = listOf(it) }
        }
    }

    /**
     * The declaration-typed seed for the free global [o] names, or null if no other file declares
     * it — in which case the reference stays node-less and every downstream fallback behaves
     * exactly as before BUG-397 (a genuinely undefined name still reads as `nil`/`Undefined`).
     * Memoized per name so all references — and [visitIndexExpr]'s member routing — share one node.
     */
    private fun freeGlobalSeed(o: LuaNameRef): TypeNode? {
        val name = o.text
        if (freeGlobalSeeds.containsKey(name)) return freeGlobalSeeds[name]
        val globalType = LuaTypeManager.getInstance(o.project).resolveGlobal(name, o)
        val seed = globalType?.let { graph.value(o, LuaGraphType.fromLuaType(it, graph), declaredOrigin = true) }
        seed?.let { declarationTypedNodes.add(it) }
        freeGlobalSeeds[name] = seed
        return seed
    }

    /**
     * REDIS-04 §3.1a: seed the active target's ambient stub globals (e.g. `KEYS`/`ARGV` declared
     * `---@type string[]` in the target's bundled `global.lua`) into the root scope BEFORE the file
     * is visited, so a bare reference to such a global infers its stub `@type` rather than
     * `Undefined`. Off a target with no such stub (e.g. STANDARD), this seeds nothing — the
     * structural no-leak guarantee (TC-KEYS-3).
     */
    fun seedAmbientGlobals(file: PsiFile) {
        val currentProject = file.project
        val currentTarget = LuaProjectSettings.getInstance(currentProject).state.getTarget()
        val stubFiles = RuntimeLibraryProvider(currentProject).getLibraryFiles(currentTarget)
        val psiManager = PsiManager.getInstance(currentProject)
        stubFiles
            .filter { it.name == "global.lua" }
            .mapNotNull { psiManager.findFile(it) as? LuaFile }
            .forEach { stubFile ->
                PsiTreeUtil
                    .findChildrenOfType(stubFile, LuaAssignmentStatement::class.java)
                    .forEach { seedGlobalAssignment(it) }
            }
    }

    private fun seedGlobalAssignment(statement: LuaAssignmentStatement) {
        val cats = ambientCatsComments(statement)
        statement.varList.varList.forEach { globalVar ->
            val nameRef = globalVar.nameRef ?: return@forEach
            if (globalVar.varSuffixList.isNotEmpty()) return@forEach
            val globalNode = graph.variable(nameRef)
            scope.declare(nameRef.text, globalNode)
            cats.forEach { comment ->
                LuaTypeGraphBridge.injectTypeAnnotation(comment, statement, globalNode, graph, statement)
            }
        }
    }

    /**
     * Finds the LuaCATS comment attached to a stub global assignment. The bundled `global.lua`
     * wraps its statements in a `BLOCK`, so the first statement's `---@type` comment is a sibling of
     * the enclosing block (a file-level leading comment), not of the statement itself. This walks up
     * through parents (mirroring [LuaPsiImplUtil.getCatsComment]) until a cats comment is found or the
     * file is reached, so both the block-nested first statement and later siblings resolve their type.
     */
    private fun ambientCatsComments(statement: LuaAssignmentStatement): List<LuaCatsComment> {
        var current: PsiElement? = statement
        while (current != null && current !is PsiFile) {
            val found = getAllCatsComments(current)
            if (found.isNotEmpty()) return found
            current = current.parent
        }
        return emptyList()
    }

    fun buildSnapshot(contextFile: PsiFile? = null): LuaTypesSnapshot =
        LuaTypesSnapshot(graph, elementNodes.toMap(), fileReturnType, contextFile, globalNodes.toMap())

    private fun visitFunctionBody(
        element: PsiElement,
        parList: LuaParList?,
        funcNode: VariableNode? = null,
    ) {
        val allCats =
            (element as? LuaCommentOwner)?.catsComment?.let { listOf(it) }
                ?: getAllCatsComments(element).ifEmpty { enclosingStatementCats(element) }
        val returnDescriptors = allCats.flatMap { it.getReturnTagList() }.flatMap { it.returnTypeDescriptorList }
        val returnCount = returnDescriptors.size
        val returnNodes: MutableList<VariableNode> =
            MutableList(maxOf(1, returnCount)) {
                graph.variable(element)
            }.toMutableList()

        val paramNodesMap: MutableMap<String, VariableNode> = mutableMapOf()
        val paramNodesList = mutableListOf<VariableNode>()
        val paramNamesList = mutableListOf<String>()
        val enclosingScope = scope
        val funcScope = enclosingScope.createFunctionScope(returnNodes)
        val previousScope = scope
        scope = funcScope

        val selfBinding = pendingSelf
        pendingSelf = null
        if (selfBinding != null) {
            val selfNode = graph.variable(selfBinding.anchor)
            funcScope.declare("self", selfNode)
            graph.addEdge(selfBinding.receiver, selfNode)
        }

        try {
            val catsParams = allCats.flatMap { it.getParamTagList() }

            val params =
                parList?.nameList?.nameRefList?.mapIndexed { index, nameRef ->
                    val astName = nameRef.text
                    val paramNode = graph.variable(nameRef)
                    funcScope.declare(astName, paramNode)
                    paramNodesMap[astName] = paramNode
                    paramNodesList.add(paramNode)
                    paramNamesList.add(astName)
                    elementNodes[nameRef] = listOf(paramNode)

                    val matchingCat =
                        catsParams.find { it.argName?.text == astName }
                            ?: catsParams.getOrNull(index)

                    val paramName = matchingCat?.argName?.text ?: astName
                    val isOptional = matchingCat?.argSymbol?.text == "?"

                    LuaGraphType.Function.Parameter(paramNode, paramName, isOptional, false)
                } ?: emptyList()

            val hasVararg = parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null
            val finalParams =
                if (hasVararg) {
                    val varargNode = graph.variable(element)
                    params + LuaGraphType.Function.Parameter(varargNode, "...", false, true)
                } else {
                    params
                }

            allCats.forEach { cats ->
                LuaTypeGraphBridge.injectParamAnnotations(cats, paramNodesList, paramNamesList, graph, element)
                LuaTypeGraphBridge.injectReturnAnnotations(cats, returnNodes, graph, element)
            }

            // The bare parameter list declares names, not requirements — Lua fills missing arguments
            // with nil. Only a `---@param` tag makes this signature's arity a contract (BUG-419).
            val funcType = LuaGraphType.Function(finalParams, returnNodes, catsParams.isNotEmpty())
            if (funcNode != null) {
                graph.addEdge(graph.value(element, funcType), funcNode)
            }

            val block =
                when (element) {
                    is LuaLocalFuncDecl -> element.node.findChildByType(LuaElementTypes.BLOCK)?.psi as? LuaBlock
                    is LuaFuncDef -> element.node.findChildByType(LuaElementTypes.BLOCK)?.psi as? LuaBlock
                    is LuaFuncDecl -> element.node.findChildByType(LuaElementTypes.BLOCK)?.psi as? LuaBlock
                    else -> null
                }
            block?.let { visitBlock(it) }
        } finally {
            scope = previousScope
        }
    }

    companion object {
        internal val KEY: Key<CachedValue<LuaTypes>> = Key.create("LuaTypesSnapshotV3")

        /**
         * TYPE-10: files whose snapshot is being built on the current thread, mapped to their
         * in-progress visitor. Guards against re-entrant [LuaTypes.forFile] on the file under
         * construction — a same-file callee resolved by [LuaExpectedCallbackResolver] during
         * `visitFuncCall` would otherwise recurse into `buildSnapshot`. The in-progress snapshot
         * already reflects earlier-visited declarations (a callee decl precedes its call site).
         */
        private val inProgressBuilds: ThreadLocal<MutableMap<PsiFile, LuaTypesVisitor>> =
            ThreadLocal.withInitial { mutableMapOf() }

        /** TYPE-10: the partially-built snapshot for [file] if it is under construction on this thread. */
        internal fun inProgressSnapshot(file: PsiFile): LuaTypes? = inProgressBuilds.get()[file]?.buildSnapshot(file)

        /**
         * NAV-13: is a snapshot for [file] under construction on this thread? An O(1) map probe that
         * builds nothing — [inProgressSnapshot] answers the same question by *building* a snapshot,
         * which is the work `LuaColonCallResolution`'s guard exists to avoid (design §2.3).
         */
        internal fun isSnapshotUnderConstruction(file: PsiFile): Boolean = inProgressBuilds.get().containsKey(file)

        /**
         * TYPE-08: maps `type()` return strings to a factory yielding a **fresh** graph type per
         * lookup (requirements §TYPE-08-01). MAINT-25-01: `table`/`function` must be distinct
         * instances per narrowing site so a later copy-on-augment (setmetatable) never leaks members
         * into the shared session singleton across files.
         */
        private val TYPEOF_MAP: Map<String, () -> LuaGraphType> =
            mapOf(
                "string" to { LuaGraphType.String },
                "number" to { LuaGraphType.Number },
                "boolean" to { LuaGraphType.Boolean },
                "nil" to { LuaGraphType.Nil },
                "table" to { LuaGraphType.Table() },
                "function" to { LuaGraphType.Function(emptyList(), emptyList()) },
                "thread" to { LuaGraphType.Any },
                "userdata" to { LuaGraphType.Any },
            )

        internal fun buildSnapshot(file: PsiFile): LuaTypes {
            val visitor = LuaTypesVisitor()
            val builds = inProgressBuilds.get()
            builds[file] = visitor
            try {
                visitor.seedAmbientGlobals(file)
                file.accept(visitor)
                visitor.graph.checkTypes()
                return visitor.buildSnapshot(file)
            } finally {
                builds.remove(file)
            }
        }
    }
}
