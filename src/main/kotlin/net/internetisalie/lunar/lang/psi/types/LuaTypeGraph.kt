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

    private var currentRevision: Long = 0L

    private val rootResolutions = LongArray(RootAccessor.entries.size)

    /**
     * Monotonic stamp over this graph's node and edge state, and the key every walk-root memo in
     * [VariableElement] is validated against (BUG-473).
     *
     * It is bumped by the four node factories below and — via [OrderedSet]'s mutation hook — by
     * every successful `upSet`/`downSet` addition, including the ones that bypass [addEdge]
     * (`LuaGraphType.memberNodeFor`, and graph-building tests). Putting the bump in the set rather
     * than at those call sites is what makes the key complete without a checklist.
     *
     * Node creation bumps as well, for the narrow case a later member cannot reach through
     * aliasing: `VariableElement.mergeTableDemands` builds a *new* `LuaGraphType.Table` copying its
     * inputs' members, so a member added to a source table afterwards is not visible through that
     * copy. A memoized table the engine returns unchanged needs no bump — it holds the same mutable
     * `localMembers` instance that is later populated. No test covers the copy case; the bump is
     * kept because over-invalidation is always sound — it forces a recomputation that returns the
     * same answer, so an imprecise stamp costs speed and never correctness.
     *
     * Per-graph rather than JVM-global (BUG-473 DR-1): a graph is per file, so a global stamp would
     * let one file's traversal flush every other file's memos, and it would make the root-resolution
     * counts below order-dependent across a shared-JVM test run.
     */
    internal val revision: Long get() = currentRevision

    internal fun bumpRevision() {
        currentRevision++
    }

    /** Counts a walk root that missed its memo and was actually re-derived (BUG-473). */
    internal fun recordRootResolution(accessor: RootAccessor) {
        rootResolutions[accessor.ordinal]++
    }

    /** All nodes in the order they were added. Immutable snapshot for callers. */
    val nodes: List<TypeNode> get() = _nodes.toList()

    /**
     * The PSI element of the first-added node, without copying the whole node list.
     * Used as an anchor for synthetic member/parameter nodes in [LuaGraphType.fromLuaType].
     */
    fun firstNodeElement(): PsiElement? = _nodes.firstOrNull()?.element

    // ---------------------------------------------------------------------------
    // Factory helpers — create nodes and register them
    // ---------------------------------------------------------------------------

    /**
     * Creates a [ValueNode] asserting that [element] produces a value of [type].
     * Typical uses: literal expressions, return values, @type annotations.
     */
    fun value(
        element: PsiElement,
        type: LuaGraphType,
        declaredOrigin: Boolean = false,
    ): ValueNode {
        val node = ValueElement(element, type, declaredOrigin)
        _nodes += node
        bumpRevision()
        return node
    }

    /** Convenience: creates a nil [ValueNode]. */
    fun nil(element: PsiElement): ValueNode = value(element, LuaGraphType.Nil)

    /**
     * TYPE-10 §3.4: creates a [ValueNode] whose type is computed lazily by [compute] at read time.
     * Used for array-subscript element types so a receiver seeded *after* the subscript is visited
     * is still observed (the snapshot is read only after the full traversal + `checkTypes()`).
     *
     * [compute] receives the caller's cycle-guard set and **must** thread it into any further
     * graph walk (BUG-390) — resolving a node through its plain `write` restarts the guard.
     */
    fun lazyValue(
        element: PsiElement,
        compute: (MutableSet<VariableNode>) -> LuaGraphType,
    ): ValueNode {
        val node = LazyValueElement(element, compute)
        _nodes += node
        bumpRevision()
        return node
    }

    /**
     * Creates a [UseNode] demanding that whatever flows in must be compatible with [type].
     * Typical uses: parameter coercion sites, assignment left-hand sides with annotations.
     */
    fun use(
        element: PsiElement,
        type: LuaGraphType,
        declaredDemand: Boolean = false,
    ): UseNode {
        val node = UseElement(element, type, declaredDemand)
        _nodes += node
        bumpRevision()
        return node
    }

    /**
     * Creates a mutable [VariableNode] with no type initially ([LuaGraphType.Undefined]).
     * Typical uses: local variable declarations, function parameter bindings.
     */
    fun variable(element: PsiElement): VariableNode {
        val node = VariableElement(element, this)
        _nodes += node
        bumpRevision()
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
    fun addEdge(
        from: TypeNode,
        to: TypeNode,
    ) {
        if (from === to) return

        when {
            from is VariableElement && to is VariableElement -> propagateBiEdge(from, to)
            to is VariableElement -> propagateDownward(from, to)
            from is VariableElement -> propagateUpward(from, to)
            from is ValueNode && to is UseNode -> {
                // A direct value→use edge is the expression's own value meeting its own demand —
                // `nil .. "x"` — so a Nil here is CERTAIN, not one reaching definition among many.
                checkCompatibility(
                    from.write,
                    to.read,
                    from.element,
                    to.element,
                    certain = true,
                    declaredDemand = to.declaredDemand,
                )
            }
        }
    }

    /**
     * Adds edges for a list of parallel flows (e.g. multiple return values or
     * multiple values in an assignment list).
     */
    fun flowList(
        froms: List<TypeNode>,
        tos: List<VariableNode>,
    ) {
        if (froms.isEmpty()) return

        tos.forEachIndexed { i, to ->
            val from = froms.getOrNull(i) ?: nil(to.element)
            addEdge(from, to)
        }
    }

    /** Convenience for a single variable → variable data-flow edge. */
    fun flow(
        from: VariableNode,
        to: VariableNode,
    ) = addEdge(from, to)

    private val instantiationCache = mutableMapOf<Pair<LuaGraphType.Function, PsiElement>, LuaGraphType.Function>()

    /**
     * Creates a fresh instantiation of a generic function for a specific call site.
     * Replaces symbolic [LuaGraphType.Generic] nodes with fresh [VariableNode]s.
     */
    fun instantiateGeneric(
        template: LuaGraphType.Function,
        element: PsiElement,
    ): LuaGraphType.Function =
        instantiationCache.getOrPut(template to element) {
            doInstantiateGeneric(template, element)
        }

    private fun doInstantiateGeneric(
        template: LuaGraphType.Function,
        element: PsiElement,
    ): LuaGraphType.Function {
        val substitutionMap = mutableMapOf<String, VariableNode>()

        val instantiatedParams =
            template.params.map { p ->
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

        val instantiatedReturns =
            template.returns.map { r ->
                val rType = r.write
                if (rType is LuaGraphType.Generic) {
                    substitutionMap.getOrPut(rType.name) { variable(element) }
                } else {
                    r
                }
            }

        return LuaGraphType.Function(instantiatedParams, instantiatedReturns, template.declaredSignature)
    }

    // ---------------------------------------------------------------------------
    // Error reporting — populated by checkTypes()
    // ---------------------------------------------------------------------------

    private val _errors: MutableList<ElementError> = mutableListOf()
    val errors: List<ElementError> get() = _errors.toList()

    internal fun addError(error: ElementError) {
        // BUG-417: an error spanning the whole file has no user-actionable location — and worse,
        // the platform hides lower-severity infos under an ERROR range, so ONE file-wide type error
        // silently buried every other inspection's results in the file (measured: filetree.lua,
        // five file-wide errors, all 126 undeclared-variable warnings gone). The compatibility
        // sites re-anchor to the value element first; this is the safety net for everything else.
        if (isFileWide(error.element)) return
        if (_errors.any { it.element == error.element && it.message == error.message }) return
        _errors += error
    }

    /** True when [element]'s range covers its whole containing file (or is the file itself). */
    private fun isFileWide(element: PsiElement): Boolean {
        val file = element.containingFile ?: return true
        if (element == file) return true
        val range = element.textRange ?: return true
        return file.textLength > 0 && range.length >= file.textLength
    }

    /**
     * Reports a compatibility failure at the most useful anchor (BUG-417): the use site when it is
     * a real element, else the value site, else nowhere — see [addError] for why a file-wide error
     * is worse than no error.
     */

    /**
     * BUG-441: the engine cannot account for this value. Either spelling counts — `Undefined` is what
     * an unmodellable expression yields directly, `Any` is what [LuaTypesVisitor.collectRhsNodes]
     * contributes for one that yields no node at all, and a union carrying either arm is gradual for
     * the same reason.
     */
    private fun isUnknown(type: LuaGraphType): Boolean =
        type == LuaGraphType.Any ||
            type == LuaGraphType.Undefined ||
            (type is LuaGraphType.Union && type.types.any { it == LuaGraphType.Any || it == LuaGraphType.Undefined })

    private fun reportIncompatible(
        useElement: PsiElement,
        valueElement: PsiElement,
        message: String,
        declaredDemand: Boolean = false,
        inferredValueType: String? = null,
        unknownProvenance: Boolean = false,
    ) {
        val anchor =
            when {
                !isFileWide(useElement) -> useElement
                !isFileWide(valueElement) -> valueElement
                else -> return
            }
        // BUG-419: an incompatibility is a DIAGNOSTIC only when the expectation is one the user
        // wrote. Against a demand the engine synthesized from usage, both sides are its own
        // inferences and the conflict is evidence the model is incomplete — measured at 7 430 of
        // 7 433 emissions across four corpus members.
        // BUG-441: criterion 1 of the same rule — *unknown-free provenance*. A declared demand
        // licenses the engine to speak only about a value it can actually account for. When a
        // sibling reaching definition is unknown (`local d = wx.thing; if c then d = "s" end`), the
        // string write is real but it is not the whole story, and reporting it as an ERROR asserts
        // more than the model knows. Downgraded rather than dropped: BUG-416 requires that
        // suppression never ENABLE anything, and returning here would skip the member-edge wiring
        // the surrounding checks still perform.
        val tier =
            if (declaredDemand && !unknownProvenance) ErrorSeverity.ERROR else ErrorSeverity.HYPOTHESIS
        addError(ElementError(anchor, message, tier, inferredValueType.takeIf { tier == ErrorSeverity.HYPOTHESIS }))
    }

    /**
     * Perform constraint checking on the fully-built graph.
     * For each variable node, check that all values flowing into it satisfy its constraints.
     *
     * [maxIterations] and [timeLimitMs] are the fixed-point safety cutoffs; they are defaulted
     * parameters (production callers pass nothing) so cutoff-behavior tests can trip them
     * deterministically (MAINT-25-04 / TC-07) without a pathological input. A tripped cutoff is a
     * designed break — it logs `warn` (never `error`, which would raise an IDE fatal-error popup).
     */
    fun checkTypes(
        maxIterations: Int = 1000,
        timeLimitMs: Long = 5000,
    ) {
        val checkedPairs = mutableSetOf<Pair<TypeNode, TypeNode>>()
        var changed: Boolean
        var iterations = 0
        val startTime = System.currentTimeMillis()

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
                log.warn("Type checking exceeded max iterations ($maxIterations). Potential infinite loop detected.")
                break
            }
            if (System.currentTimeMillis() - startTime > timeLimitMs) {
                log.warn("Type checking exceeded time limit (${timeLimitMs}ms). Potential performance bottleneck.")
                break
            }

            // Progress = new edges OR new pairs EXAMINED — deliberately not the error count.
            // Errors-as-progress coupled convergence to diagnostics: suppressing a false positive
            // (BUG-416) silently ended iteration early, and the whole inspection profile of a
            // corpus member flipped (LuaUndeclaredVariable 829 → 1647). A suppressed check still
            // consumes its pair, so pair growth is the suppression-independent signal.
            val initialCheckedCount = checkedPairs.size
            val initialEdgeCount =
                _nodes.sumOf {
                    (it as? VariableElement)?.let { v -> v.upSet.size + v.downSet.size }
                        ?: 0
                }

            val currentNodes = _nodes.toList()
            for (node in currentNodes) {
                if (node is VariableElement) {
                    val currentDownSet = node.downSet.toList()
                    val currentUpSet = node.upSet.toList()
                    // BUG-416: a value reaching a use through a variable is one REACHING DEFINITION.
                    // With several of them, a Nil among the writes is optionality — the branch that
                    // did not run — and flagging it produced 959 false positives on one corpus
                    // member. With exactly one, the variable can hold nothing else, and a Nil is
                    // certain (`local nothing = nil; count(nothing)` stays an error).
                    val certain = currentUpSet.count { it is ValueNode && !it.declaredOrigin } == 1
                    // BUG-441 (RC-2): this loop checks each reaching definition against the demand
                    // ON ITS OWN — the variable's merged type is never the thing being checked, which
                    // is why every "make the union gradual" design aimed at the type algebra failed.
                    // Unknown-ness is therefore a property of the upSet as a whole and can only be
                    // computed here: the string write in `local d = wx.thing; if c then d = "s" end`
                    // is itself perfectly known, and what makes it unreportable is a SIBLING.
                    val unknownProvenance =
                        currentUpSet.any { it is ValueNode && isUnknown(it.write) }
                    for (useNode in currentDownSet) {
                        if (useNode !is UseNode) continue
                        for (valueNode in currentUpSet) {
                            if (valueNode !is ValueNode) continue

                            val pair = Pair(valueNode, useNode)
                            if (checkedPairs.add(pair)) {
                                checkCompatibility(
                                    valueNode.write,
                                    useNode.read,
                                    valueNode.element,
                                    useNode.element,
                                    certain = certain,
                                    declaredDemand = useNode.declaredDemand,
                                    unknownProvenance = unknownProvenance,
                                )
                            }
                        }
                    }
                }
            }

            val finalEdgeCount =
                _nodes.sumOf {
                    (it as? VariableElement)?.let { v -> v.upSet.size + v.downSet.size }
                        ?: 0
                }
            if (finalEdgeCount > initialEdgeCount || checkedPairs.size > initialCheckedCount) {
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
        certain: Boolean = false,
        declaredDemand: Boolean = false,
        unknownProvenance: Boolean = false,
    ) {
        if (valueType == LuaGraphType.Any || useType == LuaGraphType.Any) return
        if (valueType == LuaGraphType.Undefined || useType == LuaGraphType.Undefined) return
        if (valueType == useType) return

        // Recursion guard for head-to-head structural matching
        if (!visited.add(valueType to useType)) return

        // A trait is satisfied or it is not — there is no structure to propagate into (BUG-423).
        // Falling through when unsatisfied is deliberate: the branches below produce the specific
        // wording ("nil value is not assignable to string", the union closest-match diagnostic)
        // that this position had before it was named, and that the tests pin.
        if (useType is LuaGraphType.Trait && isCompatible(valueType, useType, CompatContext())) return

        // Union distributive rules
        if (valueType is LuaGraphType.Union) {
            // A union keeping an `Any` arm is gradual (BUG-397): the value may be anything at
            // runtime, so no assignability error is ever justified, and only the arms that
            // structurally match the use contribute constraints to it.
            val gradual = LuaGraphType.Any in valueType.types
            // A `nil` arm is optionality, not evidence (BUG-416). Reading an absent table field
            // *is* `nil` in Lua, so every branch-dependent value is `T | nil` and demanding the
            // nil arm match the use flagged idiomatic code 1 801 times across one corpus member.
            // The nil arm forgives only itself: the informative arms must still all fit.
            val informative = valueType.types.filter { it != LuaGraphType.Nil }
            // Value(Union(nil | A | B)) ≤ Use(T) iff (A ≤ T AND B ≤ T)
            if (!gradual &&
                informative.isNotEmpty() &&
                !informative.all { isCompatible(it, useType, CompatContext()) }
            ) {
                reportIncompatible(
                    useElement,
                    valueElement,
                    "${valueType.displayName()} is not assignable to ${useType.displayName()}",
                    declaredDemand,
                    valueType.displayName(),
                    unknownProvenance,
                )
                return
            }
            // Forgiving a nil arm must not ENABLE anything: this pair used to error-and-return, and
            // falling through to the structural loop below would wire member edges on pairs that
            // never propagated before. Suppressing the error is the entire fix.
            if (!gradual &&
                informative.size < valueType.types.size &&
                !isCompatible(valueType, useType, CompatContext())
            ) {
                return
            }
            // If compatible, still propagate structural constraints to the use
            for (member in valueType.types) {
                if (member is LuaGraphType.Table || member is LuaGraphType.Function || member is LuaGraphType.Union) {
                    if (gradual && !isCompatible(member, useType, CompatContext())) continue
                    checkCompatibility(
                        member,
                        useType,
                        valueElement,
                        useElement,
                        visited,
                        certain,
                        declaredDemand,
                        unknownProvenance,
                    )
                }
            }
            return
        }

        if (useType is LuaGraphType.Union) {
            // Value(T) ≤ Use(Union(A | B)) iff (T ≤ A OR T ≤ B)
            // Find all members that are compatible and propagate to them.
            val hasCompatible =
                useType.types.any { member ->
                    isCompatible(valueType, member, CompatContext())
                }

            if (hasCompatible) {
                for (member in useType.types) {
                    // Propagate structural constraints to compatible members
                    if (isCompatible(valueType, member, CompatContext())) {
                        if (member is LuaGraphType.Table ||
                            member is LuaGraphType.Function ||
                            member is LuaGraphType.Union
                        ) {
                            checkCompatibility(
                                valueType,
                                member,
                                valueElement,
                                useElement,
                                visited,
                                certain,
                                declaredDemand,
                                unknownProvenance,
                            )
                        }
                    }
                }
                return
            }

            val closest = LuaUnionDiagnostics.closestMatch(valueType, useType.types)
            val message =
                if (closest != null) {
                    "${valueType.displayName()} is not assignable to ${useType.displayName()}; closest match '${closest.member.displayName()}': ${closest.reason}"
                } else {
                    "${valueType.displayName()} is not assignable to union ${useType.displayName()}"
                }
            reportIncompatible(
                useElement,
                valueElement,
                message,
                declaredDemand,
                valueType.displayName(),
                unknownProvenance,
            )
            return
        }

        if ((
                valueType == LuaGraphType.String ||
                    valueType == LuaGraphType.Number ||
                    valueType == LuaGraphType.Boolean
            ) &&
            useType is LuaGraphType.Table
        ) {
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
            checkCompatibility(
                valueType.elementType,
                useType.elementType,
                valueElement,
                useElement,
                visited,
                certain,
                declaredDemand,
            )
            return
        }

        if (valueType == LuaGraphType.Nil && useType != LuaGraphType.Nil) {
            // BUG-416: only a CERTAIN nil is a defect — a literal operand (`nil .. s`) or a
            // variable whose sole reaching definition is nil. A Nil arriving as one of several
            // reaching definitions is the not-yet-assigned branch of ordinary Lua, and reporting
            // it flagged a shipped IDE 959 times.
            if (certain) {
                val message = "nil value is not assignable to ${useType.displayName()}"
                reportIncompatible(
                    useElement,
                    valueElement,
                    message,
                    declaredDemand,
                    valueType.displayName(),
                    unknownProvenance,
                )
            }
            return
        }

        reportIncompatible(
            useElement,
            valueElement,
            "${valueType.displayName()} is not assignable to ${useType.displayName()}",
            declaredDemand,
            valueType.displayName(),
            unknownProvenance,
        )
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
        if (value is LuaGraphType.Union &&
            value.types.size > MAX_UNION_BREADTH ||
            use is LuaGraphType.Union &&
            use.types.size > MAX_UNION_BREADTH
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
        val result =
            when {
                // A gradual union (Any arm, BUG-397) is compatible with everything — the Any arm
                // means the value may be exactly the use type at runtime.
                value is LuaGraphType.Union ->
                    LuaGraphType.Any in value.types || value.types.all { isCompatible(it, use, ctx.deeper()) }
                use is LuaGraphType.Union -> use.types.any { isCompatible(value, it, ctx.deeper()) }
                // BUG-423. Ordered after the union branches so a union VALUE still distributes
                // (every arm must satisfy the position), and before the structural ones because a
                // trait has no structure to match against.
                use is LuaGraphType.Trait ->
                    use.admits.any { isCompatible(value, it, ctx) } || implementsOperator(value, use)
                value is LuaGraphType.Array && use is LuaGraphType.Array ->
                    isCompatible(
                        value.elementType,
                        use.elementType,
                        ctx,
                    )
                value is LuaGraphType.Table && use is LuaGraphType.Table ->
                    isNominallyCompatible(value, use, mutableSetOf()) ||
                        isStructurallyCompatible(value, use, ctx)
                (value == LuaGraphType.String || value == LuaGraphType.Number || value == LuaGraphType.Boolean) &&
                    use is LuaGraphType.Table -> true
                value is LuaGraphType.Function && use is LuaGraphType.Function -> isFunctionCompatible(value, use, ctx)
                value == LuaGraphType.Nil -> use == LuaGraphType.Nil
                else -> false
            }
        compatMemo[key] = result
        return result
    }

    /**
     * BUG-424: a table satisfies an operator position when its metatable implements the operator —
     * `a + b` is legal for any `a` carrying `__add`, however un-numeric it looks.
     *
     * Supertypes are walked because `setmetatable` records the metatable's members as a supertype,
     * so an instance can inherit the capability from a base class's metatable.
     */
    private fun implementsOperator(
        value: LuaGraphType,
        trait: LuaGraphType.Trait,
    ): Boolean =
        when (value) {
            is LuaGraphType.Table ->
                value.metamethods.any { it in trait.metamethods } ||
                    value.superTypes.any { implementsOperator(it, trait) }
            else -> false
        }

    /**
     * Cheap over-approximation used when a union exceeds [MAX_UNION_BREADTH] (TYPE-09-P2-04): true
     * iff some member shares the other operand's head kind (same [LuaGraphType] subclass). A
     * deliberate soundness/perf trade-off (parent design §2.3.1) — never memoized.
     */
    private fun shallowHeadMatch(
        value: LuaGraphType,
        use: LuaGraphType,
    ): Boolean {
        val valueHeads = headKinds(value)
        val useHeads = headKinds(use)
        return valueHeads.any { it in useHeads }
    }

    private fun headKinds(type: LuaGraphType): Set<Class<out LuaGraphType>> =
        when (type) {
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

        // BUG-419 defect 4. Arity reached the user through `addError` directly, so the rule defect 3
        // installed on `reportIncompatible` never applied to it — and against an *inferred* signature
        // there is nothing to violate: Lua adjusts arguments to parameters, so under- and
        // over-application are both legal. Demote those to the hypothesis tier; a signature the user
        // declared is still a contract, and breaking it is still a diagnostic.
        val tier = if (value.declaredSignature) ErrorSeverity.WARNING else ErrorSeverity.HYPOTHESIS

        if (provided < minParams) {
            addError(
                ElementError(
                    useElement,
                    "Too few arguments: expected at least $minParams, got $provided",
                    tier,
                ),
            )
        } else if (provided > maxParams) {
            addError(
                ElementError(
                    useElement,
                    "Too many arguments: expected at most $maxParams, got $provided",
                    tier,
                ),
            )
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
                val isMethodOnClass =
                    use.className != null && (readType is LuaGraphType.Function || writeType is LuaGraphType.Function)
                val isRequired = use.isExact && !isMethodOnClass

                // If the field is missing, it's only an error if it's required (non-optional).
                if (!isOptional(readType) && isRequired) {
                    addError(ElementError(valueElement, "Missing required field '$key'", ErrorSeverity.ERROR))
                }
            }
        }
    }

    private fun isOptional(type: LuaGraphType): Boolean =
        when (type) {
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

    private fun propagateDownward(
        from: TypeNode,
        to: VariableElement,
    ) {
        if (to.upSet.add(from)) {
            if (from is VariableElement) {
                for (upstream in from.upSet) propagateDownward(upstream, to)
            }
            for (downstream in to.downSet) {
                if (downstream is VariableElement) propagateDownward(from, downstream)
            }
        }
    }

    private fun propagateUpward(
        from: VariableElement,
        to: TypeNode,
    ) {
        if (from.downSet.add(to)) {
            if (to is VariableElement) {
                for (downstream in to.downSet) propagateUpward(from, downstream)
            }
            for (upstream in from.upSet) {
                if (upstream is VariableElement) propagateUpward(upstream, to)
            }
        }
    }

    private fun propagateBiEdge(
        from: VariableElement,
        to: VariableElement,
    ) {
        propagateDownward(from, to)
        propagateUpward(from, to)
    }

    /** @return current per-run memo size; for tests asserting cache behaviour. */
    @org.jetbrains.annotations.TestOnly
    internal fun compatMemoSize(): Int = compatMemo.size

    /**
     * @return how many times [accessor] was re-derived from scratch at a walk root over this
     * graph's lifetime — memo hits are not counted. This is the quantity BUG-473 regressed, and it
     * is deterministic given a fixture, so it is what the routine-loop budget test asserts rather
     * than a wall-clock figure.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun rootResolutionCount(accessor: RootAccessor): Long = rootResolutions[accessor.ordinal]

    private companion object {
        /** Distribution-nesting cutoff (TYPE-09-P2-04); assume-compatible beyond it (TYPE-DR-04). */
        private const val MAX_DISTRIBUTION_DEPTH = 10

        /** Union-member cap (TYPE-09-P2-04); larger unions fall back to shallow head matching. */
        private const val MAX_UNION_BREADTH = 100

        private val log =
            com.intellij.openapi.diagnostic.Logger
                .getInstance(LuaTypeGraph::class.java)
    }
}

// ---------------------------------------------------------------------------
// Error types
// ---------------------------------------------------------------------------

/**
 * [HYPOTHESIS] is not a diagnostic: it is the engine's own guess conflicting with another of its
 * guesses (BUG-419). Inspections must not report it — it carries no claim about the user's code,
 * only the observation that annotating something would let the engine speak with authority.
 */
enum class ErrorSeverity { ERROR, WARNING, WEAK_WARNING, HYPOTHESIS }

data class ElementError(
    val element: PsiElement,
    val message: String,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    /**
     * The type the engine inferred for the VALUE, on [ErrorSeverity.HYPOTHESIS] errors only.
     *
     * Carried rather than parsed back out of [message]: the annotate-it fix needs the type to
     * scaffold a `---@type`, and recovering it from prose would break the moment the wording did.
     */
    val inferredValueType: String? = null,
)
