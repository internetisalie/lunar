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

    override fun visitIfStatement(o: LuaIfStatement) {
        o.exprList.forEach { it.accept(this) }
        o.getBlockList().forEach { block ->
            val previousScope = scope
            scope = scope.child()
            try {
                block.accept(this)
            } finally {
                scope = previousScope
            }
        }
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

    override fun visitTableConstructor(o: LuaTableConstructor) {
        super.visitTableConstructor(o)

        val tableType = LuaGraphType.Table()
        o.fieldList?.fieldList?.forEach { field ->
            val key = field.identifier?.text
            val valExpr = field.exprList.lastOrNull()
            if (key != null && valExpr != null) {
                val valNode = firstNode(unwrapExpression(valExpr)) ?: graph.nil(valExpr)
                val memberNode = graph.variable(field)
                graph.addEdge(valNode, memberNode)
                tableType.members[key] = memberNode
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
                tableConstraint.members[propName] = memberNode
                graph.addEdge(calleeNode, graph.use(prop, tableConstraint))
                calleeNode = memberNode
            }
        }

        val method = funcName.funcNameMethod
        if (method != null) {
            val methodName = method.nameRef?.text
            if (methodName != null) {
                val memberNode = graph.variable(method)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.members[methodName] = memberNode
                graph.addEdge(calleeNode, graph.use(method, tableConstraint))
                calleeNode = memberNode
            }
        }

        graph.addEdge(graph.value(o, LuaGraphType.Undefined), calleeNode) // Initial placeholder
        graph.addEdge(funcNode, calleeNode)

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

        val calleeNodeRef = firstNode(calleeUnwrapped) ?: return

        val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return

        var calleeNode = calleeNodeRef
        val methodExpr = nameAndArgs.methodExpr
        if (methodExpr != null) {
            val methodName = methodExpr.nameRef?.text
            if (methodName != null) {
                val memberNode = graph.variable(methodExpr)
                val tableConstraint = LuaGraphType.Table()
                tableConstraint.members[methodName] = memberNode
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
                tableConstraint.members[nameRef.text] = memberNode
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
                graph.flowList(rhsNodes, returnNodes)
            }
        }
    }

    override fun visitNameRef(o: LuaNameRef) {
        super.visitNameRef(o)
        val boundNode = scope.lookup(o.text) ?: return
        elementNodes[o] = listOf(boundNode)
    }

    fun buildSnapshot(): LuaTypesSnapshot = LuaTypesSnapshot(graph, elementNodes.toMap(), fileReturnType)

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

        fun getTypes(element: PsiElement): LuaTypes {
            return KEY.cacheFileUserData(element) { file -> buildSnapshot(file) }
        }

        internal fun buildSnapshot(file: PsiFile): LuaTypes {
            val visitor = LuaTypesVisitor()
            file.accept(visitor)
            visitor.graph.checkTypes()
            return visitor.buildSnapshot()
        }
    }
}
