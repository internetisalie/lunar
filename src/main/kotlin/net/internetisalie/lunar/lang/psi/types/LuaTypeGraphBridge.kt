package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

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
    private fun isSimpleTypeName(typeName: String): Boolean = typeName.none { it in "<>|()[]" }

    /**
     * Converts a Layer-1 [LuaType] to a [ValueNode] in [graph] anchored at [element].
     */
    fun typeToValueNode(
        luaType: LuaType,
        element: PsiElement,
        graph: LuaTypeGraph,
    ): ValueNode {
        val graphType = LuaGraphType.fromLuaType(luaType, graph)
        return graph.value(element, graphType, declaredOrigin = true)
    }

    /**
     * Resolves [typeName], reading any name in [typeParameters] as a generic wherever it occurs.
     *
     * A string that mentions no parameter keeps the two-way split it always had, nullability included:
     * `resolveType` answers null for an unresolvable simple name and the callers skip the tag, whereas
     * [TypeParser.parse] never does, so routing every name through it would inject `Undefined` where
     * nothing is injected today.
     */
    private fun resolveTypeWithGenerics(
        typeName: String,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType? {
        if (TypeParser.mentionsAny(typeName, typeParameters.keys)) {
            return TypeParser.parse(typeName, context, typeParameters)
        }
        return if (isSimpleTypeName(typeName)) {
            LuaTypeManager.getInstance(context.project).resolveType(typeName, context)
        } else {
            TypeParser.parse(typeName, context)
        }
    }

    /**
     * The `@generic` names on [cats], each mapped to its `: Bound` (null when unconstrained). A bound is
     * parsed with the parameters declared before it, so `---@generic K, V : K` reads `V`'s bound as `K`
     * (design §5.1 step 2).
     */
    private fun typeParametersOf(
        cats: LuaCatsComment,
        context: PsiElement,
    ): Map<String, LuaType?> {
        val typeParameters = LinkedHashMap<String, LuaType?>()
        LuaCatsDeclarations.genericParameters(cats).forEach { parameter ->
            typeParameters[parameter.name] =
                parameter.boundText?.let { TypeParser.parse(it, context, typeParameters.toMap()) }
        }
        return typeParameters
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
        val typeTag = cats.getTypeTagList().firstOrNull()
        val classTag = cats.getClassTagList().firstOrNull()

        val typeName =
            typeTag?.let(LuaCatsDeclarations::typeName)
                ?: classTag?.let { LuaCatsDeclarations.classHeadName(it) }
                ?: return

        val typeParameters = typeParametersOf(cats, context)

        val resolvedType = resolveTypeWithGenerics(typeName, context, typeParameters) ?: return

        val graphType = LuaGraphType.fromLuaType(resolvedType, graph)

        // Create a ValueNode edge so that the annotation type flows as a value to readers
        // of this variable (e.g., when assigning tags to other, other receives the union type).
        graph.addEdge(graph.value(element, graphType, declaredOrigin = true), variable)

        // Create a UseNode constraint that represents the variable's declared type.
        // The constraint validates that values flowing into this variable are assignable to the declared type.
        val demand = if (typeTag == null) declaringTableDemand(graphType) else graphType
        val useNode = graph.use(element, demand, declaredDemand = true)
        graph.addEdge(variable, useNode)
    }

    /** The table a `@class` annotates is the class, not an instance: it need hold no field (BUG-558). */
    private fun declaringTableDemand(classType: LuaGraphType): LuaGraphType =
        if (classType is LuaGraphType.Table) classType.copy(isExact = false) else classType

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
        val typeParameters = typeParametersOf(cats, context)

        cats.getParamTagList().forEachIndexed { index, paramTag ->
            val paramName = paramTag.argName?.text?.trim() ?: return@forEachIndexed
            var astIndex = paramNames.indexOf(paramName)
            if (astIndex == -1) {
                astIndex = index
            }
            val paramNode = paramNodes.getOrNull(astIndex) ?: return@forEachIndexed
            val typeName = paramTag.argType.text.trim()
            val resolvedType = resolveTypeWithGenerics(typeName, context, typeParameters) ?: return@forEachIndexed

            val graphType = LuaGraphType.fromLuaType(resolvedType, graph)
            // The declared type is also a source, so a generic parameter's 'write' becomes Generic and
            // triggers instantiation at call sites.
            graph.addEdge(graph.value(context, graphType, declaredOrigin = true), paramNode)

            // Create a UseNode constraint that represents the parameter's declared type
            val demand = LuaTypeAlgebra.parameterDemand(graphType, LuaCatsDeclarations.isOptionalParam(paramTag))
            val useNode = graph.use(context, demand, declaredDemand = true)
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
        val typeParameters = typeParametersOf(cats, context)

        val descriptors = cats.getReturnTagList().flatMap { it.returnTypeDescriptorList }
        descriptors.forEachIndexed { i, desc ->
            val retNode = returnNodes.getOrNull(i) ?: return@forEachIndexed
            val typeName = desc.argType.text.trim()
            val resolvedType = resolveTypeWithGenerics(typeName, context, typeParameters) ?: return@forEachIndexed

            val graphType = LuaGraphType.fromLuaType(resolvedType, graph)
            // The declared type is also a source, so a generic parameter's 'write' becomes Generic and
            // triggers instantiation at call sites.
            graph.addEdge(graph.value(context, graphType, declaredOrigin = true), retNode)

            // Create a UseNode constraint that represents the return type requirement
            val useNode = graph.use(context, graphType, declaredDemand = true)
            // Use the graph API to add the edge instead of direct downSet manipulation.
            graph.addEdge(retNode, useNode)
        }
    }
}
