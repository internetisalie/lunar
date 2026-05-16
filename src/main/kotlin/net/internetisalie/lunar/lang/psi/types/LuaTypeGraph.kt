package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * The type constraint graph for a single Lua file. It is built by [LuaTypesVisitor] during a
 * read-action PSI traversal and queried by [LuaTypes] / [LuaTypesSnapshot].
 *
 * Graph invariant (biunification): whenever a node N flows into M, N.upSet ⊆ M.upSet and
 * M.downSet ⊆ N.downSet. This is maintained by [addEdge] via O(n³) transitive closure.
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §4
 */
class LuaTypeGraph {

    private val _nodes: MutableList<TypeNode> = mutableListOf()

    /** All nodes in the order they were added. Immutable snapshot for callers. */
    val nodes: List<TypeNode> get() = _nodes.toList()

    // ---------------------------------------------------------------------------
    // Factory helpers — create nodes and register them
    // ---------------------------------------------------------------------------

    /**
     * Creates a [ValueNode] asserting that [element] produces a value of [type].
     * Typical uses: literal expressions, return values, @type annotations.
     */
    fun value(element: PsiElement, type: LuaGraphType): ValueNode {
        val node = ValueElement(element, type)
        _nodes += node
        return node
    }

    /** Convenience: creates a nil [ValueNode]. */
    fun nil(element: PsiElement): ValueNode = value(element, LuaGraphType.Nil)

    /**
     * Creates a [UseNode] demanding that whatever flows in must be compatible with [type].
     * Typical uses: parameter coercion sites, assignment left-hand sides with annotations.
     */
    fun use(element: PsiElement, type: LuaGraphType): UseNode {
        val node = UseElement(element, type)
        _nodes += node
        return node
    }

    /**
     * Creates a mutable [VariableNode] with no type initially ([LuaGraphType.Undefined]).
     * Typical uses: local variable declarations, function parameter bindings.
     */
    fun variable(element: PsiElement): VariableNode {
        val node = VariableElement(element)
        _nodes += node
        return node
    }

    // ---------------------------------------------------------------------------
    // Edge operations — the biunification constraint propagation core
    // ---------------------------------------------------------------------------

    /**
     * Records that the value from [from] flows into [to].
     *
     * After adding the edge, we maintain the transitive-closure invariant:
     *  - Everything that can reach [from] can now reach [to] (forward propagation).
     *  - Everything that [to] can reach now constrains [from] (backward propagation).
     */
    fun addEdge(from: TypeNode, to: TypeNode) {
        if (from === to) return

        when {
            from is VariableElement && to is VariableElement -> propagateBiEdge(from, to)
            to is VariableElement -> propagateDownward(from, to)
            from is VariableElement -> propagateUpward(from, to)
            from is ValueNode && to is UseNode -> {
                checkCompatibility(from.write, to.read, from.element, to.element)
            }
        }
    }

    /**
     * Adds edges for a list of parallel flows (e.g. multiple return values or
     * multiple values in an assignment list).
     */
    fun flowList(froms: List<TypeNode>, tos: List<VariableNode>) {
        if (froms.isEmpty()) return

        tos.forEachIndexed { i, to ->
            val from = froms.getOrNull(i) ?: nil(to.element)
            addEdge(from, to)
        }
    }

    /** Convenience for a single variable → variable data-flow edge. */
    fun flow(from: VariableNode, to: VariableNode) = addEdge(from, to)

    private val instantiationCache = mutableMapOf<Pair<LuaGraphType.Function, PsiElement>, LuaGraphType.Function>()

    /**
     * Creates a fresh instantiation of a generic function for a specific call site.
     * Replaces symbolic [LuaGraphType.Generic] nodes with fresh [VariableNode]s.
     */
    fun instantiateGeneric(
        template: LuaGraphType.Function,
        element: PsiElement,
    ): LuaGraphType.Function {
        return instantiationCache.getOrPut(template to element) {
            doInstantiateGeneric(template, element)
        }
    }

    private fun doInstantiateGeneric(
        template: LuaGraphType.Function,
        element: PsiElement,
    ): LuaGraphType.Function {
        // Find all unique generic type names used in the function signature

        val genericNames = mutableSetOf<String>()
        fun collectGenerics(type: LuaGraphType) {
            when (type) {
                is LuaGraphType.Generic -> genericNames.add(type.name)
                is LuaGraphType.Union -> type.types.forEach { collectGenerics(it) }
                is LuaGraphType.Function -> {
                    // Nested functions might have their own generics, but we only care about template's ones
                    // However, we should recurse into param/return types if they were resolved.
                    // For now, let's keep it simple.
                }
                else -> {}
            }
        }

        // This is a bit tricky because VariableNodes don't carry the "Generic" head directly,
        // their 'write' or 'read' side might resolve to it.
        // Let's assume the visitor identifies generics and passes them or we scan the resolved types.

        // Simpler approach: create a mapping for every Generic head we encounter during substitution.
        val substitutionMap = mutableMapOf<String, VariableNode>()

        fun substitute(type: LuaGraphType): LuaGraphType = when (type) {
            is LuaGraphType.Generic -> {
                substitutionMap.getOrPut(type.name) { variable(element) }
                // Return Undefined so the caller knows to use the variable node from map?
                // No, we need to return a type that represents the substituted variable.
                // But LuaGraphType doesn't have a 'VariableReference' type.
                // Wait, VariableElement IS a ValueNode, but its 'write' is what it produces.
                // Let's use 'Any' for now and handle the flow separately.
                type
            }
            is LuaGraphType.Union -> LuaGraphType.Union(type.types.map { substitute(it) }.toSet())
            else -> type
        }

        // Realistically, we need to clone the parameters and returns.
        val instantiatedParams = template.params.map { p ->
            val pType = p.node.write
            if (pType is LuaGraphType.Generic) {
                val freshVar = substitutionMap.getOrPut(pType.name) { variable(element) }
                LuaGraphType.Function.Parameter(freshVar, p.name, p.isOptional, p.isVararg)
            } else {
                // Not generic, but might contain generics (e.g. Union or nested)
                // For Phase 5, we only handle direct Generic parameters.
                p
            }
        }

        val instantiatedReturns = template.returns.map { r ->
            val rType = r.write
            if (rType is LuaGraphType.Generic) {
                substitutionMap.getOrPut(rType.name) { variable(element) }
            } else {
                r
            }
        }

        return LuaGraphType.Function(instantiatedParams, instantiatedReturns)
    }

    // ---------------------------------------------------------------------------
    // Error reporting — populated by checkTypes()
    // ---------------------------------------------------------------------------

    private val _errors: MutableList<ElementError> = mutableListOf()
    val errors: List<ElementError> get() = _errors.toList()

    internal fun addError(error: ElementError) {
        if (_errors.any { it.element == error.element && it.message == error.message }) return
        _errors += error
    }

    /**
     * Perform constraint checking on the fully-built graph.
     * For each variable node, check that all values flowing into it satisfy its constraints.
     */
    fun checkTypes() {
        val checkedPairs = mutableSetOf<Pair<TypeNode, TypeNode>>()
        var changed: Boolean
        var iterations = 0
        val maxIterations = 1000
        val startTime = System.currentTimeMillis()
        val timeLimitMs = 5000 // 5 seconds safety limit

        do {
            changed = false
            iterations++
            if (iterations > maxIterations) {
                val log = com.intellij.openapi.diagnostic.Logger.getInstance(LuaTypeGraph::class.java)
                log.error("Type checking exceeded max iterations ($maxIterations). Potential infinite loop detected.")
                break
            }
            if (System.currentTimeMillis() - startTime > timeLimitMs) {
                val log = com.intellij.openapi.diagnostic.Logger.getInstance(LuaTypeGraph::class.java)
                log.error("Type checking exceeded time limit (${timeLimitMs}ms). Potential performance bottleneck.")
                break
            }

            val initialErrorCount = _errors.size
            val initialEdgeCount = _nodes.sumOf { (it as? VariableElement)?.let { v -> v.upSet.size + v.downSet.size } ?: 0 }

            val currentNodes = _nodes.toList()
            for (node in currentNodes) {
                if (node is VariableElement) {
                    val currentDownSet = node.downSet.toList()
                    val currentUpSet = node.upSet.toList()
                    for (useNode in currentDownSet) {
                        if (useNode !is UseNode) continue
                        for (valueNode in currentUpSet) {
                            if (valueNode !is ValueNode) continue

                            val pair = Pair(valueNode, useNode)
                            if (checkedPairs.add(pair)) {
                                checkCompatibility(valueNode.write, useNode.read, valueNode.element, useNode.element)
                            }
                        }
                    }
                }
            }

            val finalEdgeCount = _nodes.sumOf { (it as? VariableElement)?.let { v -> v.upSet.size + v.downSet.size } ?: 0 }
            if (finalEdgeCount > initialEdgeCount || _errors.size > initialErrorCount) {
                changed = true
            }
        } while (changed)
    }

    private fun checkCompatibility(
        valueType: LuaGraphType,
        useType: LuaGraphType,
        valueElement: PsiElement,
        useElement: PsiElement,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>> = mutableSetOf(),
    ) {
        if (valueType == LuaGraphType.Any || useType == LuaGraphType.Any) return
        if (valueType == LuaGraphType.Undefined || useType == LuaGraphType.Undefined) return
        if (valueType == useType) return

        // Recursion guard for head-to-head structural matching
        if (!visited.add(valueType to useType)) return

        // Union distributive rules
        if (valueType is LuaGraphType.Union) {
            // Value(Union(A | B)) ≤ Use(T) iff (A ≤ T AND B ≤ T)
            if (!isCompatible(valueType, useType, mutableSetOf())) {
                addError(ElementError(valueElement, "${valueType.displayName()} is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
                return
            }
            // If compatible, still propagate structural constraints to the use
            for (member in valueType.types) {
                if (member is LuaGraphType.Table || member is LuaGraphType.Function || member is LuaGraphType.Union) {
                    checkCompatibility(member, useType, valueElement, useElement, visited)
                }
            }
            return
        }

        if (useType is LuaGraphType.Union) {
            // Value(T) ≤ Use(Union(A | B)) iff (T ≤ A OR T ≤ B)
            // Find all members that are compatible and propagate to them.
            val hasCompatible = useType.types.any { member ->
                isCompatible(valueType, member, mutableSetOf())
            }

            if (hasCompatible) {
                for (member in useType.types) {
                    // Propagate structural constraints to compatible members
                    if (isCompatible(valueType, member, mutableSetOf())) {
                        if (member is LuaGraphType.Table || member is LuaGraphType.Function || member is LuaGraphType.Union) {
                            checkCompatibility(valueType, member, valueElement, useElement, visited)
                        }
                    }
                }
                return
            }

            addError(ElementError(valueElement, "${valueType.displayName()} is not assignable to union ${useType.displayName()}", ErrorSeverity.ERROR))
            return
        }

        if ((valueType == LuaGraphType.String || valueType == LuaGraphType.Number || valueType == LuaGraphType.Boolean) && useType is LuaGraphType.Table) {
            // Primitives can have methods via metatables.
            return
        }

        if (valueType is LuaGraphType.Function && useType is LuaGraphType.Function) {
            checkFunctionCompatibility(valueType, useType, valueElement, useElement, visited)
            return
        }

        if (valueType is LuaGraphType.Table && useType is LuaGraphType.Table) {
            checkTableCompatibility(valueType, useType, valueElement, useElement, visited)
            return
        }

        if (valueType is LuaGraphType.Array && useType is LuaGraphType.Array) {
            // Arrays are invariant in Phase 1 for simplicity, or covariant?
            // Let's use structural matching if needed, but for now just element types.
            checkCompatibility(valueType.elementType, useType.elementType, valueElement, useElement, visited)
            return
        }

        if (valueType == LuaGraphType.Nil && useType != LuaGraphType.Nil) {
            addError(ElementError(valueElement, "nil value is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
            return
        }

        addError(ElementError(valueElement, "${valueType.displayName()} is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
    }

    private fun isCompatible(
        value: LuaGraphType,
        use: LuaGraphType,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ): Boolean {
        if (value == LuaGraphType.Any || use == LuaGraphType.Any) return true
        if (value == LuaGraphType.Undefined || use == LuaGraphType.Undefined) return true
        if (value == use) return true

        if (!visited.add(value to use)) return true // Cycle = assume compatible for now

        return when {
            value is LuaGraphType.Union -> value.types.all { isCompatible(it, use, visited) }
            use is LuaGraphType.Union -> use.types.any { isCompatible(value, it, visited) }
            value is LuaGraphType.Array && use is LuaGraphType.Array -> isCompatible(value.elementType, use.elementType, visited)
            value is LuaGraphType.Table && use is LuaGraphType.Table -> isNominallyCompatible(value, use, mutableSetOf()) || isStructurallyCompatible(value, use, visited)
            (value == LuaGraphType.String || value == LuaGraphType.Number || value == LuaGraphType.Boolean) && use is LuaGraphType.Table -> true
            value is LuaGraphType.Function && use is LuaGraphType.Function -> isFunctionCompatible(value, use, visited)
            value == LuaGraphType.Nil -> use == LuaGraphType.Nil
            else -> false
        }
    }

    private fun isStructurallyCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ): Boolean {
        for ((key, useNode) in use.getMembers()) {
            val valueNode = value.getMembers()[key]
            if (valueNode != null) {
                if (!isCompatible(valueNode.write, useNode.read, visited)) return false
            } else if (!isOptional(useNode.read)) {
                return false
            }
        }
        return true
    }

    private fun isFunctionCompatible(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ): Boolean {
        // Simple arity check for dry-run
        val minParams = value.params.count { !it.isOptional && !it.isVararg }
        if (use.params.size < minParams) return false

        // Return types
        for (i in 0 until minOf(value.returns.size, use.returns.size)) {
            if (!isCompatible(value.returns[i].write, use.returns[i].read, visited)) return false
        }

        // Param types (contravariant)
        for (i in 0 until minOf(value.params.size, use.params.size)) {
            if (!isCompatible(use.params[i].node.write, value.params[i].node.read, visited)) return false
        }

        return true
    }

    private fun checkFunctionCompatibility(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
        valueElement: PsiElement,
        useElement: PsiElement,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ) {
        val minParams = value.params.count { !it.isOptional && !it.isVararg }
        val maxParams = if (value.params.any { it.isVararg }) Int.MAX_VALUE else value.params.size
        val provided = use.params.size

        if (provided < minParams) {
            addError(ElementError(useElement, "Too few arguments: expected at least $minParams, got $provided", ErrorSeverity.WARNING))
        } else if (provided > maxParams) {
            addError(ElementError(useElement, "Too many arguments: expected at most $maxParams, got $provided", ErrorSeverity.WARNING))
        }

        for (i in 0 until minOf(value.returns.size, use.returns.size)) {
            addEdge(value.returns[i], use.returns[i])
        }

        for (i in 0 until minOf(value.params.size, use.params.size)) {
            addEdge(use.params[i].node, value.params[i].node)
        }
    }

    private fun checkTableCompatibility(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        valueElement: PsiElement,
        useElement: PsiElement,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ) {
        if (isNominallyCompatible(value, use, mutableSetOf())) return

        for ((key, useNode) in use.getMembers()) {
            val valueNode = value.getMembers()[key]
            if (valueNode != null) {
                // Bi-directional flow for mutable table fields (invariance)
                // This prevents unsound covariant widening of mutable properties.
                addEdge(valueNode, useNode)
                addEdge(useNode, valueNode)
            } else {
                val readType = useNode.read
                val writeType = useNode.write
                // If the use type is not marked as exact (e.g. inferred from usage),
                // missing fields are allowed (they might be assigned later).
                // If it is exact (e.g. from @type), missing fields are only allowed if they are functions
                // (methods on classes are typically provided via __index, not constructor literals).
                val isMethodOnClass = use.className != null && (readType is LuaGraphType.Function || writeType is LuaGraphType.Function)
                val isRequired = use.isExact && !isMethodOnClass

                // If the field is missing, it's only an error if it's required (non-optional).
                if (!isOptional(readType) && isRequired) {
                    addError(ElementError(valueElement, "Missing required field '$key'", ErrorSeverity.ERROR))
                }
            }
        }
    }

    private fun isOptional(type: LuaGraphType): Boolean = when (type) {
        is LuaGraphType.Union -> type.types.any { it == LuaGraphType.Nil }
        LuaGraphType.Undefined -> true
        else -> false
    }

    private fun isNominallyCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        visited: MutableSet<String>,
    ): Boolean {
        if (value.className != null && use.className != null) {
            if (value.className == use.className) return true
            if (!visited.add(value.className)) return false

            return value.superTypes.any {
                it is LuaGraphType.Table && isNominallyCompatible(it, use, visited)
            }
        }
        return false
    }

    private fun propagateDownward(from: TypeNode, to: VariableElement) {
        if (to.upSet.add(from)) {
            if (from is VariableElement) {
                for (upstream in from.upSet) propagateDownward(upstream, to)
            }
            for (downstream in to.downSet) {
                if (downstream is VariableElement) propagateDownward(from, downstream)
            }
        }
    }

    private fun propagateUpward(from: VariableElement, to: TypeNode) {
        if (from.downSet.add(to)) {
            if (to is VariableElement) {
                for (downstream in to.downSet) propagateUpward(from, downstream)
            }
            for (upstream in from.upSet) {
                if (upstream is VariableElement) propagateUpward(upstream, to)
            }
        }
    }

    private fun propagateBiEdge(from: VariableElement, to: VariableElement) {
        propagateDownward(from, to)
        propagateUpward(from, to)
    }
}

// ---------------------------------------------------------------------------
// Error types
// ---------------------------------------------------------------------------

enum class ErrorSeverity { ERROR, WARNING, WEAK_WARNING }

data class ElementError(
    val element: PsiElement,
    val message: String,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
)
