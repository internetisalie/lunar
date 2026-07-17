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

    /**
     * Threaded compatibility context (TYPE-09-P2). Replaces a bare `visited` set so we can carry
     * the union-distribution [depth] without pushing `isCompatible` over the contract's 3-arg cap.
     * The [visited] set is SHARED across [deeper] frames (cycle guard); only [depth] grows, and
     * only on union-member recursion (it tracks distribution nesting, not structural depth).
     */
    private class CompatContext(
        val visited: MutableSet<Pair<LuaGraphType, LuaGraphType>> = mutableSetOf(),
        val depth: Int = 0,
    ) {
        fun deeper(): CompatContext = CompatContext(visited, depth + 1)
    }

    /** Per-run memo for [isCompatible] (TYPE-09-P2-05). Cleared at the start of each [checkTypes]. */
    private data class CompatKey(
        val value: LuaGraphType,
        val use: LuaGraphType,
        // reserved for explicit substitution context — generics are pre-instantiated to identity-
        // distinct VariableNodes before isCompatible runs, so (value, use) is already a sound key.
    )

    private val compatMemo = HashMap<CompatKey, Boolean>()

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
     * TYPE-10 §3.4: creates a [ValueNode] whose type is computed lazily by [compute] at read time.
     * Used for array-subscript element types so a receiver seeded *after* the subscript is visited
     * is still observed (the snapshot is read only after the full traversal + `checkTypes()`).
     */
    fun lazyValue(element: PsiElement, compute: () -> LuaGraphType): ValueNode {
        val node = LazyValueElement(element, compute)
        _nodes += node
        return node
    }

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
        val substitutionMap = mutableMapOf<String, VariableNode>()

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
            // Clear the compatibility memo each fixed-point iteration (not just once per run):
            // addEdge grows up/down sets between iterations, so a (value, use) result cached in an
            // earlier iteration can become stale and leak a false negative. Per-iteration clearing
            // keeps the within-pass cache benefit while guaranteeing soundness (TYPE-09-P2-05;
            // perf headroom confirmed by the P0 spike).
            compatMemo.clear()
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
            if (!isCompatible(valueType, useType, CompatContext())) {
                addError(ElementError(useElement, "${valueType.displayName()} is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
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
                isCompatible(valueType, member, CompatContext())
            }

            if (hasCompatible) {
                for (member in useType.types) {
                    // Propagate structural constraints to compatible members
                    if (isCompatible(valueType, member, CompatContext())) {
                        if (member is LuaGraphType.Table || member is LuaGraphType.Function || member is LuaGraphType.Union) {
                            checkCompatibility(valueType, member, valueElement, useElement, visited)
                        }
                    }
                }
                return
            }

            val closest = LuaUnionDiagnostics.closestMatch(valueType, useType.types)
            val message = if (closest != null) {
                "${valueType.displayName()} is not assignable to ${useType.displayName()}; closest match '${closest.member.displayName()}': ${closest.reason}"
            } else {
                "${valueType.displayName()} is not assignable to union ${useType.displayName()}"
            }
            addError(ElementError(useElement, message, ErrorSeverity.ERROR))
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
            addError(ElementError(useElement, "nil value is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
            return
        }

        addError(ElementError(useElement, "${valueType.displayName()} is not assignable to ${useType.displayName()}", ErrorSeverity.ERROR))
    }

    private fun isCompatible(
        value: LuaGraphType,
        use: LuaGraphType,
        ctx: CompatContext,
    ): Boolean {
        if (value == LuaGraphType.Any || use == LuaGraphType.Any) return true
        if (value == LuaGraphType.Undefined || use == LuaGraphType.Undefined) return true
        if (value == use) return true

        // Safety limits (TYPE-09-P2-04). Both fall through to assume-compatible-ish approximations
        // and must NOT be memoized (they are context-dependent, not genuine structural results).
        if (ctx.depth > MAX_DISTRIBUTION_DEPTH) {
            // Assume compatible (TYPE-DR-04): returning false would emit false-positive errors on
            // legitimately deep but valid types. The visited guard is the primary bound; on real
            // code this cutoff effectively never trips, so a once-style debug log suffices.
            log.debug("Distribution depth exceeded $MAX_DISTRIBUTION_DEPTH; assuming compatibility")
            return true
        }
        if (value is LuaGraphType.Union && value.types.size > MAX_UNION_BREADTH ||
            use is LuaGraphType.Union && use.types.size > MAX_UNION_BREADTH
        ) {
            return shallowHeadMatch(value, use)
        }

        // Memo (TYPE-09-P2-05): only genuine structural results below are stored, so reusing a hit
        // is sound — depth/breadth limits never trip on the cached path on real code.
        val key = CompatKey(value, use)
        compatMemo[key]?.let { return it }

        if (!ctx.visited.add(value to use)) return true // Cycle = assume compatible (NOT memoized)

        // Depth grows ONLY on union-member recursion (distribution nesting); structural/array/
        // function recursion reuses ctx unchanged.
        val result = when {
            value is LuaGraphType.Union -> value.types.all { isCompatible(it, use, ctx.deeper()) }
            use is LuaGraphType.Union -> use.types.any { isCompatible(value, it, ctx.deeper()) }
            value is LuaGraphType.Array && use is LuaGraphType.Array -> isCompatible(value.elementType, use.elementType, ctx)
            value is LuaGraphType.Table && use is LuaGraphType.Table -> isNominallyCompatible(value, use, mutableSetOf()) || isStructurallyCompatible(value, use, ctx)
            (value == LuaGraphType.String || value == LuaGraphType.Number || value == LuaGraphType.Boolean) && use is LuaGraphType.Table -> true
            value is LuaGraphType.Function && use is LuaGraphType.Function -> isFunctionCompatible(value, use, ctx)
            value == LuaGraphType.Nil -> use == LuaGraphType.Nil
            else -> false
        }
        compatMemo[key] = result
        return result
    }

    /**
     * Cheap over-approximation used when a union exceeds [MAX_UNION_BREADTH] (TYPE-09-P2-04): true
     * iff some member shares the other operand's head kind (same [LuaGraphType] subclass). A
     * deliberate soundness/perf trade-off (parent design §2.3.1) — never memoized.
     */
    private fun shallowHeadMatch(value: LuaGraphType, use: LuaGraphType): Boolean {
        val valueHeads = headKinds(value)
        val useHeads = headKinds(use)
        return valueHeads.any { it in useHeads }
    }

    private fun headKinds(type: LuaGraphType): Set<Class<out LuaGraphType>> = when (type) {
        is LuaGraphType.Union -> type.types.map { it::class.java }.toSet()
        else -> setOf(type::class.java)
    }

    private fun isStructurallyCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        ctx: CompatContext,
    ): Boolean {
        for ((key, useNode) in use.getMembers()) {
            val valueNode = value.getMembers()[key]
            if (valueNode != null) {
                if (!isCompatible(valueNode.write, useNode.read, ctx)) return false
            } else if (!isOptional(useNode.read)) {
                return false
            }
        }
        return true
    }

    private fun isFunctionCompatible(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
        ctx: CompatContext,
    ): Boolean {
        // Simple arity check for dry-run
        val minParams = value.params.count { !it.isOptional && !it.isVararg }
        if (use.params.size < minParams) return false

        // Return types
        for (i in 0 until minOf(value.returns.size, use.returns.size)) {
            if (!isCompatible(value.returns[i].write, use.returns[i].read, ctx)) return false
        }

        // Param types (contravariant)
        for (i in 0 until minOf(value.params.size, use.params.size)) {
            if (!isCompatible(use.params[i].node.write, value.params[i].node.read, ctx)) return false
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

    /** @return current per-run memo size; for tests asserting cache behaviour. */
    @org.jetbrains.annotations.TestOnly
    internal fun compatMemoSize(): Int = compatMemo.size

    private companion object {
        /** Distribution-nesting cutoff (TYPE-09-P2-04); assume-compatible beyond it (TYPE-DR-04). */
        private const val MAX_DISTRIBUTION_DEPTH = 10

        /** Union-member cap (TYPE-09-P2-04); larger unions fall back to shallow head matching. */
        private const val MAX_UNION_BREADTH = 100

        private val log = com.intellij.openapi.diagnostic.Logger.getInstance(LuaTypeGraph::class.java)
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
