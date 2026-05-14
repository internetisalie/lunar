package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment

/**
 * Bridge between the Layer-1 [LuaType] system and the graph-internal [LuaGraphType] nodes.
 *
 * Responsibilities:
 *  1. Convert a [LuaType] to a [ValueNode] the graph can reason about.
 *  2. Parse LuaCATS annotation tags and inject their constraints into the graph.
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §7
 */
object LuaTypeGraphBridge {

    /**
     * Returns true if [typeName] is a simple identifier that can be resolved in Phase 1.
     * Complex expressions (generics, unions, function types, array types) are handled via [TypeParser].
     */
    private fun isSimpleTypeName(typeName: String): Boolean =
        typeName.none { it in "<>|()[]" }

    /**
     * Converts a Layer-1 [LuaType] to a [ValueNode] in [graph] anchored at [element].
     */
    fun typeToValueNode(luaType: LuaType, element: PsiElement, graph: LuaTypeGraph): ValueNode {
        val graphType = LuaGraphType.fromLuaType(luaType, graph)
        return graph.value(element, graphType)
    }

    private fun resolveTypeWithGenerics(
        typeName: String,
        context: PsiElement,
        genericNames: Set<String>
    ): LuaType? {
        if (typeName in genericNames) return LuaGenericType(typeName)
        return if (isSimpleTypeName(typeName)) {
            LuaTypeManager.getInstance(context.project).resolveType(typeName, context)
        } else {
            // TODO: Pass genericNames to TypeParser once it supports nested generics
            TypeParser.parse(typeName, context)
        }
    }

    /**
     * Processes a `@type` annotation and adds a flow edge from the annotated type into [variable].
     *
     * If [cats] has no `@type` tag, or the type string cannot be resolved, this is a no-op.
     *
     * @param cats       The LuaCATS comment on the variable's declaration.
     * @param element    The PSI element that anchors the resulting graph nodes.
     * @param variable   The [VariableNode] representing the annotated variable.
     * @param graph      The graph to add nodes and edges to.
     * @param context    A PSI element used as resolution context for [LuaTypeManager].
     */
    fun injectTypeAnnotation(
        cats: LuaCatsComment,
        element: PsiElement,
        variable: VariableNode,
        graph: LuaTypeGraph,
        context: PsiElement,
    ) {
        val typeTag = cats.getTypeTagList().firstOrNull() ?: return
        val typeName = typeTag.argType.text.trim()
        val genericNames = cats.getGenericTagList()
            .flatMap { it.genericTypeParams?.genericTypeParamList ?: emptyList() }
            .map { it.argName.text }
            .toSet()

        val resolvedType = resolveTypeWithGenerics(typeName, context, genericNames) ?: return

        val graphType = LuaGraphType.fromLuaType(resolvedType, graph)

        // Create a ValueNode edge so that the annotation type flows as a value to readers
        // of this variable (e.g., when assigning tags to other, other receives the union type).
        graph.addEdge(graph.value(element, graphType), variable)

        // Create a UseNode constraint that represents the variable's declared type.
        // The constraint validates that values flowing into this variable are assignable to the declared type.
        val useNode = graph.use(element, graphType)
        graph.addEdge(variable, useNode)
    }

    /**
     * Processes `@param` annotations and adds flow edges from annotated types into each
     * parameter's [VariableNode].
     *
     * [paramNodes] is a `name → VariableNode` map built by the visitor for the function's
     * parameter list.  Tags whose names don't match any parameter are silently ignored.
     *
     * @param cats       The LuaCATS comment on the function declaration.
     * @param paramNodes Map from parameter name to its [VariableNode].
     * @param graph      The graph to add nodes and edges to.
     * @param context    A PSI element used as resolution context for [LuaTypeManager].
     */
    fun injectParamAnnotations(
        cats: LuaCatsComment,
        paramNodes: List<VariableNode>,
        paramNames: List<String>,
        graph: LuaTypeGraph,
        context: PsiElement,
    ) {
        val genericNames = cats.getGenericTagList()
            .flatMap { it.genericTypeParams?.genericTypeParamList ?: emptyList() }
            .map { it.argName.text }
            .toSet()

        cats.getParamTagList().forEachIndexed { index, paramTag ->
            val paramName = paramTag.argName?.text?.trim() ?: return@forEachIndexed
            var astIndex = paramNames.indexOf(paramName)
            if (astIndex == -1) {
                astIndex = index
            }
            val paramNode = paramNodes.getOrNull(astIndex) ?: return@forEachIndexed
            val typeName = paramTag.argType.text.trim()
            val resolvedType = resolveTypeWithGenerics(typeName, context, genericNames) ?: return@forEachIndexed

            val graphType = LuaGraphType.fromLuaType(resolvedType, graph)
            // For generics, we must inject both a source and a sink so that the variable's
            // 'write' type becomes Generic, triggering instantiation at call sites.
            if (graphType is LuaGraphType.Generic) {
                graph.addEdge(graph.value(context, graphType), paramNode)
            } else {
                // Also inject the type as a source so it propagates
                graph.addEdge(graph.value(context, graphType), paramNode)
            }

            // Create a UseNode constraint that represents the parameter's declared type
            val useNode = graph.use(context, graphType)
            // Use the graph API to add the edge instead of direct downSet manipulation.
            graph.addEdge(paramNode, useNode)
        }
    }

    /**
     * Processes `@return` annotations and adds flow edges from annotated types into the
     * function's return [VariableNode]s.
     *
     * Multiple `@return` tags map to multiple return positions (index-matched).
     * Extra tags beyond the number of [returnNodes] are silently ignored.
     *
     * @param cats        The LuaCATS comment on the function declaration.
     * @param returnNodes The list of return [VariableNode]s (one per return position).
     * @param graph       The graph to add nodes and edges to.
     * @param context     A PSI element used as resolution context for [LuaTypeManager].
     */
    fun injectReturnAnnotations(
        cats: LuaCatsComment,
        returnNodes: List<VariableNode>,
        graph: LuaTypeGraph,
        context: PsiElement,
    ) {
        val genericNames = cats.getGenericTagList()
            .flatMap { it.genericTypeParams?.genericTypeParamList ?: emptyList() }
            .map { it.argName.text }
            .toSet()

        cats.getReturnTagList().forEachIndexed { i, returnTag ->
            val retNode = returnNodes.getOrNull(i) ?: return@forEachIndexed
            val typeName = returnTag.argType.text.trim()
            val resolvedType = resolveTypeWithGenerics(typeName, context, genericNames) ?: return@forEachIndexed

            val graphType = LuaGraphType.fromLuaType(resolvedType, graph)
            // For generics, we must inject both a source and a sink so that the variable's
            // 'write' type becomes Generic, triggering instantiation at call sites.
            if (graphType is LuaGraphType.Generic) {
                graph.addEdge(graph.value(context, graphType), retNode)
            } else {
                // Also inject the type as a source so it propagates
                graph.addEdge(graph.value(context, graphType), retNode)
            }

            // Create a UseNode constraint that represents the return type requirement
            val useNode = graph.use(context, graphType)
            // Use the graph API to add the edge instead of direct downSet manipulation.
            graph.addEdge(retNode, useNode)
        }
    }
}
