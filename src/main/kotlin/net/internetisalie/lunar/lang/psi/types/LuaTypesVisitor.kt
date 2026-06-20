package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment

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
     * `self` binding for the next [visitFunctionBody] call (COMP-04-09): the receiver's type node
     * plus a distinct PSI anchor for the injected `self` node. Set immediately before the call and
     * consumed (cleared) inside it, so the function keeps its ≤3 parameters instead of threading a
     * 4th argument.
     */
    private var pendingSelf: SelfBinding? = null

    private data class SelfBinding(val receiver: VariableNode, val anchor: PsiElement)

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

    private fun getNodes(element: PsiElement?): List<TypeNode> {
        return elementNodes[element] ?: emptyList()
    }

    private fun firstNode(element: PsiElement?): TypeNode? {
        return getNodes(element).firstOrNull()
    }

    private fun isRequireCall(callee: PsiElement?): Boolean {
        return callee?.text == "require"
    }

    private fun extractModuleName(o: LuaFuncCall): String? {
        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return null
        val args = nameAndArgs.args
        val stringElement = args.string
            ?: args.exprList?.exprList?.firstOrNull()?.let { unwrapExpression(it) }?.let { (it as? LuaTerminalExpr)?.string }

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
    private fun handleSetMetatable(o: LuaFuncCall, resultNode: VariableNode): Boolean {
        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return false
        val argExprs = nameAndArgs.args.exprList?.exprList ?: return false
        if (argExprs.size < 2) return false

        val tType = (firstNode(unwrapExpression(argExprs[0])) as? ValueNode)?.write as? LuaGraphType.Table ?: return false
        val mtType = (firstNode(unwrapExpression(argExprs[1])) as? ValueNode)?.write as? LuaGraphType.Table ?: return false

        val indexType = indexTableOf(mtType) ?: return false
        tType.superTypes.add(indexType)
        graph.addEdge(graph.value(o, tType), resultNode)
        return true
    }

    /** Resolves the table exposed via a metatable's `__index` member (a table, or a function's first table return). */
    private fun indexTableOf(metatable: LuaGraphType.Table): LuaGraphType.Table? {
        val indexWrite = metatable.getMembers()["__index"]?.write ?: return null
        return when (indexWrite) {
            is LuaGraphType.Table -> indexWrite
            is LuaGraphType.Function -> indexWrite.returns.firstOrNull()?.write as? LuaGraphType.Table
            else -> null
        }
    }

    private fun unwrapExpression(expr: PsiElement?, maxDepth: Int = 10): PsiElement? {
        var currentExpr = expr
        var depth = 0
        while (currentExpr != null && depth < maxDepth) {
            depth++
            if (currentExpr in elementNodes) return currentExpr

            val children = currentExpr.children.filter { it !is PsiWhiteSpace && it !is PsiComment }
            if (children.size == 1) {
                val child = children[0]
                if (child is LuaExpr || child is LuaNameRef || child is LuaVar || child is LuaPrefixExpr || child is LuaVarOrExp) {
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
                result.addAll(nodes)
            } else {
                nodes.firstOrNull()?.let { result.add(it) } ?: result.add(graph.nil(expr))
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
                val comment = if (current is LuaCatsComment) current else PsiTreeUtil.findChildOfType(current, LuaCatsComment::class.java)
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

    override fun visitFile(file: PsiFile) {
        if (rootReturnNodes.isEmpty()) {
            repeat(8) { rootReturnNodes.add(graph.variable(file)) }
        }

        super.visitFile(file)

        val firstRet = rootReturnNodes.firstOrNull()?.write ?: LuaGraphType.Any
        fileReturnType = if (firstRet == LuaGraphType.Undefined) LuaGraphType.Any else firstRet
    }

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

    private fun injectNarrowedBinding(guard: TypeGuard, matchBranch: Boolean) {
        val originalNode = scope.lookup(guard.variableName) ?: return
        val narrowedType = if (matchBranch == guard.isEquality) {
            guard.narrowedType
        } else {
            subtractType(originalNode.write, guard.narrowedType)
        }
        val narrowedValue = graph.value(guard.anchor, narrowedType)
        val narrowedVar = graph.variable(guard.anchor)
        graph.addEdge(narrowedValue, narrowedVar)
        scope.declare(guard.variableName, narrowedVar)
    }

    /** Removes [remove] from [original], delegating union subtraction to [LuaTypeAlgebra]. */
    private fun subtractType(original: LuaGraphType, remove: LuaGraphType): LuaGraphType = when {
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
        val narrowedType = TYPEOF_MAP[typeName] ?: LuaGraphType.Any

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

        val variableName = PsiTreeUtil.findChildOfType(nameSide, LuaNameRef::class.java)?.text
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
        val graphType = when {
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

        val leftNode = firstNode(unwrapExpression(left)) ?: graph.nil(left)
        val rightNode = firstNode(unwrapExpression(right)) ?: graph.nil(right ?: o)

        val resType = when (op) {
            "+", "-", "*", "/", "//", "^", "%" -> {
                graph.addEdge(leftNode, graph.use(o, LuaGraphType.Number))
                graph.addEdge(rightNode, graph.use(o, LuaGraphType.Number))
                LuaGraphType.Number
            }
            ".." -> {
                graph.addEdge(leftNode, graph.use(o, LuaGraphType.String))
                graph.addEdge(rightNode, graph.use(o, LuaGraphType.String))
                LuaGraphType.String
            }
            "==", "~=", "<", ">", "<=", ">=" -> {
                LuaGraphType.Boolean
            }
            "and", "or" -> {
                // Simplified: result is one of the operands
                val leftType = (leftNode as? ValueNode)?.write ?: LuaGraphType.Any
                val rightType = (rightNode as? ValueNode)?.write ?: LuaGraphType.Any
                LuaGraphType.Union.create(setOf(leftType, rightType))
            }
            else -> LuaGraphType.Any
        }
        elementNodes[o] = listOf(graph.value(o, resType))
    }

    override fun visitUnOpExpr(o: LuaUnOpExpr) {
        super.visitUnOpExpr(o)
        val op = o.unOp.text
        val right = o.right
        val rightNode = firstNode(unwrapExpression(right)) ?: graph.nil(right ?: o)

        val resType = when (op) {
            "#" -> {
                // # right implies right is string or table
                graph.addEdge(rightNode, graph.use(o, LuaGraphType.Union.create(setOf(LuaGraphType.String, LuaGraphType.Table()))))
                LuaGraphType.Number
            }
            "-" -> {
                graph.addEdge(rightNode, graph.use(o, LuaGraphType.Number))
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
        val tableType = LuaGraphType.Table(null, localMembers)
        o.fieldList?.fieldList?.forEach { field ->
            val key = field.identifier?.text
            val valExpr = field.exprList.lastOrNull()
            if (key != null && valExpr != null) {
                val valNode = firstNode(unwrapExpression(valExpr)) ?: graph.nil(valExpr)
                val memberNode = graph.variable(field)
                graph.addEdge(valNode, memberNode)
                localMembers[key] = memberNode
            }
        }
        elementNodes[o] = listOf(graph.value(o, tableType))
    }

    override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
        super.visitLocalVarDecl(o)

        val names = o.attNameList.map { it.nameRef }
        val exprs = o.exprList?.exprList ?: emptyList()
        val rhsNodes = collectRhsNodes(exprs)

        val varNodes = names.map { nameRef ->
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
        o.nameRef?.let { scope.declare(it.text, funcNode) }
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

        val funcName = o.funcName
        val baseName = funcName.nameRef.text
        val baseVar = scope.lookup(baseName) ?: run {
            val fresh = graph.variable(funcName.nameRef)
            scope.declare(baseName, fresh)
            fresh
        }

        var calleeNode: VariableNode = baseVar
        funcName.funcNamePropertyList.forEach { prop ->
            val propName = prop.nameRef?.text
            if (propName != null) {
                val memberNode = graph.variable(prop)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.localMembers[propName] = memberNode
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
                val memberNode = graph.variable(method)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.localMembers[methodName] = memberNode
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

        val calleeNodeRef = firstNode(calleeUnwrapped) ?: return

        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return

        var calleeNode = calleeNodeRef
        val methodExpr = nameAndArgs.methodExpr
        if (methodExpr != null) {
            val methodName = methodExpr.nameRef?.text
            if (methodName != null) {
                val memberNode = graph.variable(methodExpr)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.localMembers[methodName] = memberNode
                graph.addEdge(calleeNode, graph.use(methodExpr, tableConstraint))
                calleeNode = memberNode
            }
        }

        val args = nameAndArgs.args
        val argExprs = when {
            args.string != null -> args.string?.let { listOf(it) } ?: emptyList()
            args.exprList != null -> args.exprList?.exprList ?: emptyList()
            args.tableConstructor != null -> listOf(args.tableConstructor!!)
            else -> emptyList()
        }

        val argNodes = argExprs.map { argExpr ->
            val unwrapped = unwrapExpression(argExpr)
            val nodes = getNodes(unwrapped)
            val node = nodes.firstOrNull() ?: graph.nil(argExpr)
            LuaGraphType.Function.Parameter(
                graph.variable(argExpr).apply {
                    graph.addEdge(node, this)
                },
            )
        }

        val callDemand = LuaGraphType.Function(
            params = argNodes,
            returns = callResultNodes,
        )

        // If the callee is a generic function template, instantiate it for this call site.
        if (calleeNode is ValueNode && calleeNode.write is LuaGraphType.Function) {
            val funcTemplate = calleeNode.write as LuaGraphType.Function
            val isGeneric = funcTemplate.params.any { it.node.write is LuaGraphType.Generic } ||
                            funcTemplate.returns.any { it.write is LuaGraphType.Generic }

            if (isGeneric) {
                val instantiated = graph.instantiateGeneric(funcTemplate, o)
                graph.addEdge(graph.value(o, instantiated), graph.use(o, callDemand))
                return
            }
        }

        graph.addEdge(calleeNode, graph.use(o, callDemand))
    }

    override fun visitAssignmentStatement(o: LuaAssignmentStatement) {
        super.visitAssignmentStatement(o)

        val vars = o.varList.varList
        val exprs = o.exprList.exprList
        val rhsNodes = collectRhsNodes(exprs)

        val varNodes = vars.map { v ->
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
                val receiverNode = firstNode(unwrapExpression(varElement.firstChild)) ?: return
                val memberNode = graph.variable(o)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.localMembers[nameRef.text] = memberNode
                graph.addEdge(receiverNode, graph.use(o, tableConstraint))
                elementNodes[o] = listOf(memberNode)
            }
        }
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

    override fun visitNameRef(o: LuaNameRef) {
        super.visitNameRef(o)
        val boundNode = scope.lookup(o.text) ?: return
        elementNodes[o] = listOf(boundNode)
    }

    fun buildSnapshot(contextFile: PsiFile? = null): LuaTypesSnapshot =
        LuaTypesSnapshot(graph, elementNodes.toMap(), fileReturnType, contextFile)

    private fun visitFunctionBody(
        element: PsiElement,
        parList: LuaParList?,
        funcNode: VariableNode? = null,
    ) {
        val allCats = (element as? LuaCommentOwner)?.catsComment?.let { listOf(it) } ?: getAllCatsComments(element)
        val returnTags = allCats.flatMap { it.getReturnTagList() }
        val returnCount = returnTags.size
        val returnNodes: MutableList<VariableNode> = MutableList(maxOf(1, returnCount)) { graph.variable(element) }.toMutableList()

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

            val params = parList?.nameList?.nameRefList?.mapIndexed { index, nameRef ->
                val astName = nameRef.text
                val paramNode = graph.variable(nameRef)
                funcScope.declare(astName, paramNode)
                paramNodesMap[astName] = paramNode
                paramNodesList.add(paramNode)
                paramNamesList.add(astName)
                elementNodes[nameRef] = listOf(paramNode)

                val matchingCat = catsParams.find { it.argName?.text == astName }
                    ?: catsParams.getOrNull(index)

                val paramName = matchingCat?.argName?.text ?: astName
                val isOptional = matchingCat?.argSymbol?.text == "?"

                LuaGraphType.Function.Parameter(paramNode, paramName, isOptional, false)
            } ?: emptyList()

            val hasVararg = parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null
            val finalParams = if (hasVararg) {
                val varargNode = graph.variable(element)
                params + LuaGraphType.Function.Parameter(varargNode, "...", false, true)
            } else {
                params
            }

            allCats.forEach { cats ->
                LuaTypeGraphBridge.injectParamAnnotations(cats, paramNodesList, paramNamesList, graph, element)
                LuaTypeGraphBridge.injectReturnAnnotations(cats, returnNodes, graph, element)
            }

            val funcType = LuaGraphType.Function(finalParams, returnNodes)
            if (funcNode != null) {
                graph.addEdge(graph.value(element, funcType), funcNode)
            }

            val block = when (element) {
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
        internal val KEY: Key<FileUserData<LuaTypes>> = Key.create("LuaTypesSnapshotV3")

        /** TYPE-08: maps `type()` return strings to their graph type (requirements §TYPE-08-01). */
        private val TYPEOF_MAP: Map<String, LuaGraphType> = mapOf(
            "string" to LuaGraphType.String,
            "number" to LuaGraphType.Number,
            "boolean" to LuaGraphType.Boolean,
            "nil" to LuaGraphType.Nil,
            "table" to LuaGraphType.Table(),
            "function" to LuaGraphType.Function(emptyList(), emptyList()),
            "thread" to LuaGraphType.Any,
            "userdata" to LuaGraphType.Any,
        )

        fun getTypes(element: PsiElement): LuaTypes {
            return KEY.cacheFileUserData(element) { file -> buildSnapshot(file) }
        }

        internal fun buildSnapshot(file: PsiFile): LuaTypes {
            val visitor = LuaTypesVisitor()
            file.accept(visitor)
            visitor.graph.checkTypes()
            return visitor.buildSnapshot(file)
        }
    }
}
