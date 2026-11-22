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

    /** TYPE-12: which colon-call site members are later sites of a hub, and where each hub's sites sit. */
    internal val memberDemandHubs = LuaMemberDemandHubs()

    /** BUG-110: where in the code each demand sits, for a variable's `read`. */
    internal val demandRanks = LuaDemandRanks()

    private var currentRevision: Long = 0L

    private val rootResolutions = LongArray(RootAccessor.entries.size)

    /**
     * Monotonic stamp over this graph's node and edge state, and the key every walk-root memo in
     * [VariableElement] is validated against (BUG-473).
     *
     * It is bumped by the four node factories below and — via [OrderedSet]'s mutation hook,
     * [onEdgeAdded] — by every successful `upSet`/`downSet` addition, including the ones that bypass [addEdge]
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

    private var edgeAdds = 0L
    private var pairAttempts = 0L
    private var scanComparisons = 0L
    private var reportedPairs = 0L

    /** MAINT-61: each variable's set sizes at its last visit, only while [checkTypes] runs. */
    private var scanMarks: java.util.IdentityHashMap<VariableElement, PairScanMark>? = null

    /** BUG-97: each representative's up- and down-sets with clusters expanded, only while [checkTypes] runs. */
    private var scanLogs: java.util.IdentityHashMap<VariableElement, MemberLogs>? = null

    /**
     * The [OrderedSet] hook of every `upSet`/`downSet`: counts the edge, so [runFixedPointPass]
     * reads its progress from [edgeAdds] instead of summing every set (MAINT-61 design §3.3), then
     * bumps [revision].
     */
    internal fun onEdgeAdded() {
        edgeAdds++
        bumpRevision()
    }

    private var edgesMerged = 0L

    /** BUG-97: set entries a cluster merge folded away, so [edgeAdds] less this is the live closure. */
    internal fun recordMergedEdges(count: Int) {
        edgesMerged += count
    }

    private var writeInsertions = 0L

    /** Counts the types one `write` walk offered to its result set (BUG-96). */
    internal fun recordWriteInsertions(count: Int) {
        writeInsertions += count
    }

    private var walkSteps = 0L

    /** Counts the set entries one `write` or `declaredDemand` walk iterated (BUG-97). */
    internal fun recordWalkSteps(count: Int) {
        walkSteps += count
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
        val node = ValueElement(element, type, declaredOrigin, _nodes.size)
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
        val node = LazyValueElement(element, compute, _nodes.size)
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
    ): UseNode = addUse(UseElement(element, type, declaredDemand, _nodes.size))

    /**
     * BUG-110: the demand of [owner], a converted signature's parameter or return
     * ([LuaGraphType.fromLuaType]). It ranks where code is first wired to [owner] — a call, or a
     * declaration meeting its converted signature ([LuaDemandRanks]) — not where it was converted.
     */
    fun convertedSlotUse(
        owner: VariableNode,
        demand: LuaGraphType,
        declaredDemand: Boolean,
    ): UseNode {
        val node = UseElement(owner.element, demand, declaredDemand, _nodes.size)
        demandRanks.registerSlot(owner, node)
        return addUse(node)
    }

    /**
     * BUG-110: the demand of [owner], a converted class member's declared type
     * ([LuaGraphType.fromLuaType]). It ranks at the variable [owner] is first wired to — the member's
     * declaration or first access ([LuaDemandRanks]) — not where the class was converted.
     */
    fun convertedMemberUse(
        owner: VariableNode,
        type: LuaGraphType,
    ): UseNode {
        val node = UseElement(owner.element, type, false, _nodes.size)
        demandRanks.registerMember(owner, node)
        return addUse(node)
    }

    private fun addUse(node: UseElement): UseNode {
        _nodes += node
        bumpRevision()
        return node
    }

    /**
     * Creates a mutable [VariableNode] with no type initially ([LuaGraphType.Undefined]).
     * Typical uses: local variable declarations, function parameter bindings.
     */
    fun variable(
        element: PsiElement,
        declaresMember: Boolean = false,
    ): VariableNode {
        val node = VariableElement(element, this, declaresMember, _nodes.size)
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
        if (demandRanks.noteEdge(from, to, _nodes.size)) bumpRevision()
        val source = canonical(from)
        val target = canonical(to)
        if (source !== target) addCanonicalEdge(source, target)
    }

    /** [addEdge] over cluster representatives, which is what the sets hold. */
    private fun addCanonicalEdge(
        from: TypeNode,
        to: TypeNode,
    ) {
        when {
            from is VariableElement && to is VariableElement -> {
                propagateBiEdge(from, to)
                collapseCycle(from, to)
            }
            to is VariableElement -> propagateDownward(from, to)
            from is VariableElement -> propagateUpward(from, to)
            from is ValueNode && to is UseNode -> if (phase == CheckPhase.WIRE) wireDirectPair(from, to)
        }
    }

    /**
     * A direct value→use edge is the expression's own value meeting its own demand — `nil .. "x"` —
     * so a Nil here is CERTAIN, not one reaching definition among many. Its wiring happens now; the
     * pair is kept for [reportFindings], which judges it against the final graph (BUG-114).
     */
    private fun wireDirectPair(
        from: ValueNode,
        to: UseNode,
    ) {
        directPairs += from to to
        checkCompatibility(from.write, to.read, from.element, to.element)
    }

    /**
     * BUG-97: after the edge [from] → [to] (both representatives), merges the strongly connected set
     * it closed, if any. [to] reaches [from] exactly when a member of [to]'s cluster is in [from]'s
     * up-set. The members of one cycle reach the same nodes, so their up- and down-sets are copies of
     * one another: kept apart, every walk and every edge propagation over a cluster of k variables
     * repeats its work k times.
     *
     * Merging changes no variable's reachable values or uses, only which node holds them. A variable's
     * `read` is a function of the SET of demands it reaches ([LuaDemandRanks]), so it does not depend
     * on which member's set the fold runs over.
     */
    private fun collapseCycle(
        from: VariableElement,
        to: VariableElement,
    ) {
        val fromUp = from.upSet
        if (to !in fromUp && to.clusterMembers().none { it in fromUp }) return
        val upstream = LinkedHashSet<VariableElement>()
        fromUp.forEach { if (it is VariableElement) upstream.add(it.rep()) }
        val cycle = LinkedHashSet<VariableElement>()
        cycle.add(from)
        cycle.add(to)
        from.downSet.forEach { if (it is VariableElement && it.rep() in upstream) cycle.add(it.rep()) }
        if (cycle.size < 2) return
        val root = cycle.maxWith(compareBy<VariableElement> { it.clusterSize }.thenByDescending { it.order })
        root.absorb(cycle.filter { it !== root })
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

    // ---------------------------------------------------------------------------
    // Error reporting — populated by checkTypes()
    // ---------------------------------------------------------------------------

    private val _errors: MutableList<ElementError> = mutableListOf()

    /** BUG-112: the declared-demand mismatches whose severity is settled once the graph is final. */
    private val severityClaims = LuaSeverityClaims()

    /** The `(value, use)` pair [checkCompatibility] is examining, so a mismatch can be claimed for it. */
    private var examiningValue: ValueNode? = null
    private var examiningUse: UseNode? = null

    /**
     * BUG-114: what [checkCompatibility] does. [CheckPhase.WIRE] adds the edges a pair implies and
     * reports nothing; [CheckPhase.REPORT] reports what the final graph says and adds no edge.
     */
    private enum class CheckPhase { WIRE, REPORT }

    private var phase = CheckPhase.WIRE

    private var certainty: LuaPairCertainty? = null

    /** The value→use edges [addEdge] met, for [reportFindings]: they sit in no variable's sets. */
    private val directPairs = mutableListOf<Pair<ValueNode, UseNode>>()
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
    ) {
        val anchor =
            when {
                !isFileWide(useElement) -> useElement
                !isFileWide(valueElement) -> valueElement
                else -> return
            }
        if (LuaMemberAssignmentSites.isMergedValue(useElement, valueElement)) return
        // BUG-419: an incompatibility is a DIAGNOSTIC only when the expectation is one the user
        // wrote. Against a demand the engine synthesized from usage, both sides are its own
        // inferences and the conflict is evidence the model is incomplete — measured at 7 430 of
        // 7 433 emissions across four corpus members.
        // BUG-441: criterion 1 of the same rule — *unknown-free provenance*: a declared demand licenses
        // the engine to speak only about a value it can account for, so a pair held by a variable with
        // an unknown sibling write is a hypothesis. That is a fact about every holder of the pair, and
        // settleSeverities() applies it to the claim recorded below.
        val tier =
            if (declaredDemand) ErrorSeverity.ERROR else ErrorSeverity.HYPOTHESIS
        // BUG-112: the claim lets settleSeverities() decide against every variable that holds the pair.
        val value = examiningValue
        val use = examiningUse
        if (value != null && use != null) {
            val mismatch = LuaSeverityClaims.Mismatch(anchor, message, inferredValueType)
            severityClaims.record(value to use, mismatch, declaredDemand)
        }
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
        val checkedPairs = LuaCheckedPairs()
        val budget = CheckBudget(maxIterations, timeLimitMs)

        // Dropped, not cleared, in the finally: a cached snapshot keeps this graph, and a cleared
        // IdentityHashMap keeps its table at its peak size (MAINT-61 design §5).
        scanMarks = java.util.IdentityHashMap()
        scanLogs = java.util.IdentityHashMap()
        try {
            runFixedPoint(checkedPairs, budget)
            reportFindings(checkedPairs)
            settleSeverities()
        } finally {
            scanMarks = null
            scanLogs = null
        }
    }

    /** The fixed point's safety cutoffs and how much of them one [checkTypes] run has used. */
    private class CheckBudget(
        val maxIterations: Int,
        val timeLimitMs: Long,
    ) {
        var iterations = 0
        val startTime = System.currentTimeMillis()
    }

    /**
     * Passes over the variables until one adds neither an edge nor a pair.
     *
     * A cutoff stops it early, and [reportFindings] then judges the graph as it stands.
     */
    private fun runFixedPoint(
        checkedPairs: LuaCheckedPairs,
        budget: CheckBudget,
    ) {
        do {
            budget.iterations++
            // Clear the compatibility memo each fixed-point iteration (not just once per run):
            // addEdge grows up/down sets between iterations, so a (value, use) result cached in an
            // earlier iteration can become stale and leak a false negative. Per-iteration clearing
            // keeps the within-pass cache benefit while guaranteeing soundness (TYPE-09-P2-05;
            // perf headroom confirmed by the P0 spike).
            compatMemo.clear()
            if (budget.iterations > budget.maxIterations) {
                log.warn(
                    "Type checking exceeded max iterations (${budget.maxIterations}). Potential infinite loop detected.",
                )
                return
            }
            if (System.currentTimeMillis() - budget.startTime > budget.timeLimitMs) {
                log.warn(
                    "Type checking exceeded time limit (${budget.timeLimitMs}ms). Potential performance bottleneck.",
                )
                return
            }
        } while (runFixedPointPass(checkedPairs))
    }

    /**
     * BUG-114: the findings of the final graph. The fixed point only wires; every admitted
     * `(value, use)` pair is judged here, against the closure and the `write`/`read` it ended with,
     * so a finding does not depend on the order the pairs were checked in. A fixed point that stopped
     * at its limit is judged as it stands.
     *
     * Pairs run in creation order, so which pair a finding is first attributed to is a property of the
     * graph. [LuaPairCertainty] answers the one fact that depended on the visit.
     */
    private fun reportFindings(checkedPairs: LuaCheckedPairs) {
        phase = CheckPhase.REPORT
        compatMemo.clear()
        certainty = LuaPairCertainty(_nodes)
        val lazyWrites = java.util.IdentityHashMap<ValueNode, LuaGraphType>()
        var readOf: UseNode? = null
        var read: LuaGraphType = LuaGraphType.Any
        try {
            checkedPairs.forEachInCreationOrder(_nodes) { value, use ->
                val write = if (value is LazyValueElement) lazyWrites.getOrPut(value) { value.write } else value.write
                if (use !== readOf) {
                    readOf = use
                    read = use.read
                }
                reportPair(AdmittedPair(value, use, write, read))
            }
            for ((value, use) in directPairs) reportDirectPair(value, use)
            warmWriteMemos()
        } finally {
            phase = CheckPhase.WIRE
            certainty = null
            compatMemo.clear()
        }
    }

    /**
     * Resolves the `write` of every value that reaches a variable, as each visit's facts did before
     * [reportFindings] (BUG-114): a later cross-file call-site resolution finds these walks memoized,
     * and fan-out stays one root resolution per site (NAV-13-06, MAINT-61 risks Gap 2.4).
     */
    private fun warmWriteMemos() {
        for (node in _nodes) {
            if (node !is VariableElement || !node.isRep) continue
            for (entry in node.upSet) if (entry is ValueNode) entry.write
        }
    }

    private fun reportDirectPair(
        value: ValueNode,
        use: UseNode,
    ) {
        examiningValue = value
        examiningUse = use
        checkCompatibility(
            value.write,
            use.read,
            value.element,
            use.element,
            certain = true,
            declaredDemand = use.declaredDemand,
        )
        examiningValue = null
        examiningUse = null
    }

    /** Whether the Nil of the pair being reported is the only definition its holder has (BUG-416). */
    private fun examiningPairIsCertain(): Boolean {
        val value = examiningValue ?: return false
        val use = examiningUse ?: return false
        return certainty?.isCertain(value, use) ?: false
    }

    /**
     * BUG-112: a mismatch is an ERROR only if every pair that yields it is against a declared demand
     * and no variable holding the pair has an unknown write in its up-set (BUG-441), whichever of
     * them examined the pair first. Only claimed diagnostics change, toward what the full set of
     * facts says.
     */
    private fun settleSeverities() {
        val variables = _nodes.asSequence().filterIsInstance<VariableElement>().filter { it.isRep }
        compatMemo.clear()
        val verdicts =
            severityClaims.resolve(
                variables,
                { isUnknown(it.write) },
                { value, use -> !isCompatible(value.write, use.read, CompatContext()) },
            )
        if (verdicts.isEmpty()) return
        _errors.replaceAll { error ->
            val verdict = verdicts[error.element to error.message] ?: return@replaceAll error
            error.copy(severity = verdict.severity, inferredValueType = verdict.inferredValueType)
        }
    }

    /**
     * One fixed-point pass over every variable node.
     *
     * @return whether the pass made progress. Progress = new edges OR new pairs EXAMINED —
     * deliberately not the error count. Errors-as-progress coupled convergence to diagnostics:
     * suppressing a false positive (BUG-416) silently ended iteration early, and the whole
     * inspection profile of a corpus member flipped (LuaUndeclaredVariable 829 → 1647). A
     * suppressed check still consumes its pair, so pair growth is the suppression-independent
     * signal.
     */
    private fun runFixedPointPass(checkedPairs: LuaCheckedPairs): Boolean {
        val initialCheckedCount = checkedPairs.size
        val initialEdgeCount = edgeAdds
        val schedule = if (scheduleReversed) _nodes.asReversed().toList() else _nodes.toList()
        for (node in schedule) {
            if (node is VariableElement && node.isRep) checkNodePairs(node, checkedPairs)
        }
        return edgeAdds > initialEdgeCount || checkedPairs.size > initialCheckedCount
    }

    /**
     * Total up/down-set membership across every cluster representative. Equal to [edgeAdds] at every point
     * (MAINT-61 design §3.3), which is what [runFixedPointPass] reads instead.
     */
    private fun edgeCount(): Int =
        _nodes.sumOf {
            (it as? VariableElement)?.takeIf { v -> v.isRep }?.let { v -> v.upSet.size + v.downSet.size }
                ?: 0
        }

    /**
     * Check every (reaching definition, use) pair of one variable, each pair at most once for the
     * life of the run.
     *
     * **MAINT-61.** A revisit tries only the pairs with a value or a use added since the variable's
     * last visit ([PairScanMark], [LuaPairScan.firstValueIndex]). The others are the previous visit's
     * product, which that visit left in [checkedPairs], so `checkedPairs.add` would fail on each of
     * them: the admitted pairs and their order are unchanged (design §3.1). [verifyScan] is the gate.
     *
     * **BUG-473 Phase 2.** `write` is resolved for a given `valueNode` AT MOST ONCE PER VISIT of
     * [node] — into [VisitScan]'s `writes`, filled lazily on first need — rather than once per admitted
     * pair. The inner loop re-reads the same value node for every use node, so this turns
     * Θ(|upSet| × |downSet|) walk roots into Θ(|upSet|). On the one-`---@class` reproduction at 160
     * call sites that is 27 372 root `write` resolutions down to 1 132 — quadratic to linear, 7n +
     * 12 — and 26 791 ms down to 3 556 ms, with the growth exponent per doubling ×11.2 → ×5.9.
     * Phase 1's per-node walk-root memo does not absorb any of it on its own, because this loop
     * bumps the graph revision tens of thousands of times as it runs and invalidates every memo it
     * would otherwise hit.
     *
     * Filled lazily and not up front: reading the whole `upSet` eagerly at the head of every visit
     * is the variant DR-8 measured, and it pays 448 ms of prelude at that size resolving values
     * many visits never ask for.
     *
     * The hoist changes WHEN a value is read, not what is resolved, and its staleness window is one
     * visit of [node]: a pair checked earlier in this visit can add an edge that moves a later
     * value node's `write`, and [checkedPairs] is monotone, so that pair is never re-examined.
     * That equivalence is measured rather than proven — DR-8 found 0 mismatches over 4 172 026
     * comparisons across the suite, the corpus sweeps and the annotated fixture — which is why
     * [LuaTypePairWriteDifferential] ships as a standing gate rather than as a one-off spike.
     */
    private fun checkNodePairs(
        node: VariableElement,
        checkedPairs: LuaCheckedPairs,
    ) {
        val logs = checkNotNull(scanLogs).getOrPut(node) { MemberLogs() }
        val upSnapshot = logs.up.extendedBy(node.upSet)
        val downSnapshot = logs.down.extendedBy(node.downSet)
        val marks = checkNotNull(scanMarks)
        val mark = marks[node]
        marks[node] = PairScanMark(upSnapshot.size, downSnapshot.size)
        val scan = VisitScan(upSnapshot, checkedPairs)
        for (downIndex in downSnapshot.indices) {
            val useNode = downSnapshot[downIndex]
            if (useNode is UseNode) scan.scanUse(useNode, LuaPairScan.firstValueIndex(downIndex, mark))
        }
        if (LuaTypePairScanDifferential.enabled) verifyScan(upSnapshot, downSnapshot, checkedPairs)
    }

    /** One visit's scan state (MAINT-61 design §3.1). */
    private inner class VisitScan(
        private val upSnapshot: List<TypeNode>,
        private val checkedPairs: LuaCheckedPairs,
    ) {
        private val writes = arrayOfNulls<LuaGraphType>(upSnapshot.size)

        fun scanUse(
            useNode: UseNode,
            firstValue: Int,
        ) {
            val examined = checkedPairs.forUse(useNode)
            for (valueIndex in firstValue until upSnapshot.size) {
                val valueNode = upSnapshot[valueIndex]
                if (valueNode !is ValueNode) continue
                pairAttempts++
                if (!examined.admit(valueNode)) continue

                val valueWrite = writes[valueIndex] ?: valueNode.write.also { writes[valueIndex] = it }
                if (LuaTypePairWriteDifferential.enabled) {
                    LuaTypePairWriteDifferential.compare(valueWrite, valueNode.write)
                }
                wirePair(AdmittedPair(valueNode, useNode, valueWrite, useNode.read))
            }
        }
    }

    /** Reports to [LuaTypePairScanDifferential] whether each pair of the visit's product was examined (§3.4). */
    private fun verifyScan(
        upSnapshot: List<TypeNode>,
        downSnapshot: List<TypeNode>,
        checkedPairs: LuaCheckedPairs,
    ) {
        for (useNode in downSnapshot) {
            if (useNode !is UseNode) continue
            for (valueNode in upSnapshot) {
                if (valueNode !is ValueNode) continue
                scanComparisons++
                LuaTypePairScanDifferential.observe(checkedPairs.contains(valueNode, useNode))
            }
        }
    }

    /** One `(reaching definition, use)` pair [checkNodePairs] admitted, with its hoisted `write`. */
    private class AdmittedPair(
        val valueNode: ValueNode,
        val useNode: UseNode,
        val valueWrite: LuaGraphType,
        val useRead: LuaGraphType,
    )

    /** BUG-114: a pair's wiring, at the state the graph is in now. It reports nothing. */
    private fun wirePair(pair: AdmittedPair) {
        checkCompatibility(pair.valueWrite, pair.useRead, pair.valueNode.element, pair.useNode.element)
    }

    /** BUG-114: what the graph, now final, says of a pair: the mismatch it holds, if any. */
    private fun reportPair(pair: AdmittedPair) {
        reportedPairs++
        val firstNewError = _errors.size
        val firstNewClaim = severityClaims.size
        examiningValue = pair.valueNode
        examiningUse = pair.useNode
        checkCompatibility(
            pair.valueWrite,
            pair.useRead,
            pair.valueNode.element,
            pair.useNode.element,
            declaredDemand = pair.useNode.declaredDemand,
        )
        examiningValue = null
        examiningUse = null
        replicateHubAnchoredErrors(pair, firstNewError, firstNewClaim)
    }

    /**
     * TYPE-12: re-reports at every later site of a hub the errors this pair anchored on the hub's own
     * element because the hub was the pair's VALUE. In the clique every site member was such a value
     * against the file-wide synthetic use, and [reportIncompatible] fell back to each site's own
     * anchor; under the hub only the hub is, so without this the later sites lose those entries.
     * An error anchored on the use is never replicated — a use belongs to one site. [addError]'s
     * de-duplication makes this idempotent across fixed-point iterations.
     */
    private fun replicateHubAnchoredErrors(
        pair: AdmittedPair,
        firstNewError: Int,
        firstNewClaim: Int,
    ) {
        val replicas = memberDemandHubs.replicaAnchorsFor(pair.valueNode)
        if (replicas.isEmpty()) return
        val hubElement = pair.valueNode.element
        if (pair.useNode.element === hubElement) return
        val anchoredAtHub = _errors.subList(firstNewError, _errors.size).filter { it.element === hubElement }
        for (error in anchoredAtHub) {
            for (site in replicas) addError(error.copy(element = site))
        }
        for (site in replicas) severityClaims.replicate(firstNewClaim, hubElement, site)
    }

    private fun checkCompatibility(
        valueType: LuaGraphType,
        useType: LuaGraphType,
        valueElement: PsiElement,
        useElement: PsiElement,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>> = mutableSetOf(),
        certain: Boolean = false,
        declaredDemand: Boolean = false,
    ) {
        if (valueType == LuaGraphType.Any || useType == LuaGraphType.Any) return
        if (valueType == LuaGraphType.Undefined || useType == LuaGraphType.Undefined) return
        if (valueType == useType) return
        if (valueType is LuaGraphType.Generic || useType is LuaGraphType.Generic) {
            if (valueType is LuaGraphType.Generic && phase == CheckPhase.REPORT) {
                reportUndefinedFields(valueType, useType, useElement)
            }
            LuaTypeAlgebra.genericStep(valueType, useType)?.let { (boundValue, boundUse) ->
                checkCompatibility(
                    boundValue,
                    boundUse,
                    valueElement,
                    useElement,
                    visited,
                    certain,
                    declaredDemand,
                )
            }
            return
        }

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
            val informative = LuaTypeAlgebra.informativeArms(valueType)
            // Value(Union(nil | A | B)) ≤ Use(T) iff (A ≤ T AND B ≤ T)
            if (!gradual &&
                informative.isNotEmpty() &&
                !informative.all { isCompatible(it, useType, CompatContext()) }
            ) {
                if (phase == CheckPhase.REPORT) {
                    reportIncompatible(
                        useElement,
                        valueElement,
                        "${valueType.displayName()} is not assignable to ${useType.displayName()}",
                        declaredDemand,
                        valueType.displayName(),
                    )
                }
                return
            }
            // Forgiving a nil arm must not ENABLE anything: this pair used to error-and-return, and
            // falling through to the structural loop below would wire member edges on pairs that
            // never propagated before. Suppressing the error is the entire fix. Asked of the nil
            // arm alone because isCompatible now forgives it too; a demand that admits nil is the
            // one that propagated before.
            if (!gradual &&
                informative.size < valueType.types.size &&
                !isCompatible(LuaGraphType.Nil, useType, CompatContext())
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
                    )
                }
            }
            return
        }

        // BUG-416: only a CERTAIN nil is a defect — a literal operand (`nil .. s`) or a variable whose
        // sole reaching definition is nil. Checked before the union-demand branch so that a union demand
        // forgives an uncertain nil exactly as a single type does (MAINT-51).
        if (valueType == LuaGraphType.Nil && !isCompatible(LuaGraphType.Nil, useType, CompatContext())) {
            if (certain || (phase == CheckPhase.REPORT && examiningPairIsCertain())) {
                reportIncompatible(
                    useElement,
                    valueElement,
                    "nil value is not assignable to ${useType.displayName()}",
                    declaredDemand,
                    valueType.displayName(),
                )
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
                            )
                        }
                    }
                }
                return
            }

            if (phase != CheckPhase.REPORT) return
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

        if (valueType is LuaGraphType.Table &&
            useType is LuaGraphType.Array &&
            isArrayCompatible(valueType, useType, CompatContext())
        ) {
            return
        }

        if (phase == CheckPhase.REPORT) {
            reportIncompatible(
                useElement,
                valueElement,
                "${valueType.displayName()} is not assignable to ${useType.displayName()}",
                declaredDemand,
                valueType.displayName(),
            )
        }
    }

    private fun isCompatible(
        value: LuaGraphType,
        use: LuaGraphType,
        ctx: CompatContext,
    ): Boolean {
        if (value == LuaGraphType.Any || use == LuaGraphType.Any) return true
        if (value == LuaGraphType.Undefined || use == LuaGraphType.Undefined) return true
        if (value == use) return true
        if (value is LuaGraphType.Generic || use is LuaGraphType.Generic) {
            return LuaTypeAlgebra.genericStep(value, use)?.let { (boundValue, boundUse) ->
                isCompatible(boundValue, boundUse, ctx)
            } ?: true
        }

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
        // BUG-481: equal tables may hold different constructor entries, so this pair is not memoised.
        val memoisable = !(value is LuaGraphType.Table && use is LuaGraphType.Array)
        if (memoisable) compatMemo[key]?.let { return it }

        if (!ctx.visited.add(value to use)) return true // Cycle = assume compatible (NOT memoized)

        // Depth grows ONLY on union-member recursion (distribution nesting); structural/array/
        // function recursion reuses ctx unchanged.
        val result =
            when {
                // A gradual union (Any arm, BUG-397) is compatible with everything. A nil arm is
                // optionality, not evidence (BUG-416), exactly as in checkCompatibility.
                value is LuaGraphType.Union ->
                    LuaGraphType.Any in value.types ||
                        LuaTypeAlgebra.informativeArms(value).all { isCompatible(it, use, ctx.deeper()) }
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
                value is LuaGraphType.Table && use is LuaGraphType.Array -> isArrayCompatible(value, use, ctx)
                value is LuaGraphType.Table && use is LuaGraphType.Table ->
                    isNominallyCompatible(value, use, mutableSetOf()) ||
                        isStructurallyCompatible(value, use, ctx)
                (value == LuaGraphType.String || value == LuaGraphType.Number || value == LuaGraphType.Boolean) &&
                    use is LuaGraphType.Table -> true
                value is LuaGraphType.Function && use is LuaGraphType.Function -> isFunctionCompatible(value, use, ctx)
                value == LuaGraphType.Nil -> use == LuaGraphType.Nil
                else -> false
            }
        if (memoisable) compatMemo[key] = result
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

    /**
     * BUG-481: an anonymous table constructor is an array when every positional entry fits the
     * element type. A `nil` entry is a hole, not a type. Only a constructor literal qualifies.
     */
    private fun isArrayCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Array,
        ctx: CompatContext,
    ): Boolean =
        value.arrayShaped &&
            value.positional.all { entry ->
                val entryType = entry.write
                entryType == LuaGraphType.Nil || isCompatible(entryType, use.elementType, ctx)
            }

    private fun isStructurallyCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        ctx: CompatContext,
    ): Boolean {
        val valueMembers = value.getMembers()
        for ((key, useNode) in use.getMembers()) {
            val valueNode = valueMembers[key]
            if (valueNode != null) {
                if (!isCompatible(valueNode.write, useNode.read, ctx)) return false
            } else if (!LuaTypeAlgebra.isOptional(useNode.read)) {
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
        if (use.params.size < minArity(value, use)) return false

        // Return types
        for (i in 0 until minOf(value.returns.size, use.returns.size)) {
            if (!isCompatible(value.returns[i].write, use.returns[i].read, ctx)) return false
        }

        // Param types (contravariant)
        for ((valueParam, useParam) in alignedParams(value, use)) {
            if (!isCompatible(useParam.node.write, valueParam.node.read, ctx)) return false
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
        if (phase == CheckPhase.REPORT) {
            reportArity(value, use, useElement)
        } else {
            wireFunction(value, use)
        }
    }

    private fun wireFunction(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
    ) {
        for (i in 0 until minOf(value.returns.size, use.returns.size)) {
            addEdge(value.returns[i], use.returns[i])
        }

        for ((valueParam, useParam) in alignedParams(value, use)) {
            addEdge(useParam.node, valueParam.node)
        }
    }

    /**
     * Reports a call [use] that passes fewer or more arguments than [value] accepts. Called for every
     * function-to-function check, and directly for a callee typed from another file's declaration,
     * which raises no call demand (BUG-38).
     */
    internal fun reportArity(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
        useElement: PsiElement,
    ) {
        val minParams = minArity(value, use)
        val maxParams = maxArity(value, use)
        val provided = use.params.size

        // BUG-419 defect 4. Arity reached the user through `addError` directly, so the rule defect 3
        // installed on `reportIncompatible` never applied to it — and against an *inferred* signature
        // there is nothing to violate: Lua adjusts arguments to parameters, so under- and
        // over-application are both legal. Demote those to the hypothesis tier; a signature the user
        // declared is still a contract, and breaking it is still a diagnostic.
        val tier = if (value.declaredSignature) ErrorSeverity.WARNING else ErrorSeverity.HYPOTHESIS

        if (provided < minParams && !use.openEnded) {
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
    }

    /**
     * BUG-8: how many more leading arguments [value] expects than [use] wrote. +1 when a dot call
     * passes the receiver of a `:` method explicitly, −1 when a colon call supplies the explicit
     * `self` of a `.` function. The arity bounds therefore count the arguments written in the call.
     */
    private fun receiverDelta(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
    ): Int = (if (value.implicitSelf) 1 else 0) - (if (use.implicitSelf) 1 else 0)

    /**
     * BUG-14: an implicit receiver is required only when a required parameter follows it. Lua fills a
     * missing receiver with nil like any other argument, and the `:` declares a slot, not a
     * requirement — only a `---@param` tag is a contract (BUG-419). A dot call that omits the receiver
     * of `function C:add(a)` with a required `a` still falls short, because `a` cannot be reached.
     */
    private fun minArity(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
    ): Int {
        val required = value.params.count { !it.isOptional && !it.isVararg }
        if (required == 0 || use.anySignature) return 0
        return (required + receiverDelta(value, use)).coerceAtLeast(0)
    }

    private fun maxArity(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
    ): Int =
        if (value.anySignature || value.params.any { it.isVararg }) {
            Int.MAX_VALUE
        } else {
            (value.params.size + receiverDelta(value, use)).coerceAtLeast(0)
        }

    /**
     * Pairs each parameter of [value] with the argument of [use] that fills it, shifted by
     * [receiverDelta]. An explicit receiver argument binds the implicit `self` and is paired with
     * nothing; an explicit `self` parameter filled by a colon call's receiver gets no argument.
     */
    private fun alignedParams(
        value: LuaGraphType.Function,
        use: LuaGraphType.Function,
    ): List<Pair<LuaGraphType.Function.Parameter, LuaGraphType.Function.Parameter>> {
        val delta = receiverDelta(value, use)
        return value.params.indices
            .filter { it + delta in use.params.indices }
            .map { value.params[it] to use.params[it + delta] }
    }

    /**
     * A field read through a constrained generic is looked up on its bound (LuaLS 3.19.2), and a field
     * the bound class lacks is undefined (BUG-94). A write declares the field, so it is not read here.
     */
    private fun reportUndefinedFields(
        value: LuaGraphType.Generic,
        use: LuaGraphType,
        useElement: PsiElement,
    ) {
        val bound = value.bound as? LuaGraphType.Table ?: return
        if (bound.className == null || use !is LuaGraphType.Table) return
        val declared = bound.getMembers()
        use.getMembers().forEach { (key, node) ->
            if (key !in declared && !node.declaresMember) {
                addError(ElementError(useElement, "Undefined field '$key'", ErrorSeverity.WARNING))
            }
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

        val valueMembers = value.getMembers()
        for ((key, useNode) in use.getMembers()) {
            val valueNode = valueMembers[key]
            if (valueNode != null) {
                // Bi-directional flow for mutable table fields (invariance)
                // This prevents unsound covariant widening of mutable properties.
                // TYPE-12: a later colon-call site is wired through its hub, never directly.
                if (phase == CheckPhase.WIRE) {
                    val target = memberDemandHubs.wiringTargetFor(useNode)
                    addEdge(valueNode, target)
                    addEdge(target, valueNode)
                }
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
                // BUG-75: a table literal written to a member is not a construction of the member's class.
                val constructs = !LuaMemberAssignmentSites.isWrittenToMember(valueElement)
                if (phase == CheckPhase.REPORT && !LuaTypeAlgebra.isOptional(readType) && isRequired && constructs) {
                    addError(ElementError(valueElement, "Missing required field '$key'", ErrorSeverity.ERROR))
                }
            }
        }
    }

    private fun isNominallyCompatible(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        visited: MutableSet<String>,
    ): Boolean {
        if (value.className != null && use.className != null) {
            if (sameClass(value.className, use.className)) return true
            if (!visited.add(value.className)) return false

            return value.superTypes.any {
                it is LuaGraphType.Table && isNominallyCompatible(it, use, visited)
            }
        }
        return false
    }

    /** Equal names, or an instantiation (`Box<string>`) against its raw class (`Box`) (TYPE-14 design §4.7). */
    private fun sameClass(
        valueClass: String,
        useClass: String,
    ): Boolean = valueClass == useClass || ('<' !in useClass && valueClass.substringBefore('<').trim() == useClass)

    /**
     * BUG-103: the closure walk keeps its frames on an explicit stack, not the thread's. Each frame
     * is one newly added pair, so its depth is the number of pairs one edge adds, which reached
     * 8,099 on luals `script/parser/compile.lua`. [DownwardStep] visits the pairs in the order the
     * recursive form did, so the sets' insertion order, and everything that iterates them, is
     * unchanged.
     */
    private fun propagateDownward(
        from: TypeNode,
        to: VariableElement,
    ) {
        if (!to.upSet.add(from)) return
        val steps = ArrayDeque<DownwardStep>()
        steps.addLast(DownwardStep(from, to))
        while (steps.isNotEmpty()) {
            val pair = steps.last().next()
            when {
                pair == null -> steps.removeLast()
                pair.second.upSet.add(
                    canonical(pair.first),
                ) -> steps.addLast(DownwardStep(canonical(pair.first), pair.second))
            }
        }
    }

    /** BUG-103: [propagateDownward]'s mirror, on an explicit stack of [UpwardStep]s. */
    private fun propagateUpward(
        from: VariableElement,
        to: TypeNode,
    ) {
        if (!from.downSet.add(to)) return
        val steps = ArrayDeque<UpwardStep>()
        steps.addLast(UpwardStep(from, to))
        while (steps.isNotEmpty()) {
            val pair = steps.last().next()
            when {
                pair == null -> steps.removeLast()
                pair.first.downSet.add(
                    canonical(pair.second),
                ) -> steps.addLast(UpwardStep(pair.first, canonical(pair.second)))
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
     * @return the sum of every variable node's up- and down-set sizes — the transitive closure TYPE-12
     * keeps linear on one receiver. Deterministic given a fixture, so a growth gate can assert it.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun closureSize(): Int = edgeCount()

    /**
     * @return how many times [accessor] was re-derived from scratch at a walk root over this
     * graph's lifetime — memo hits are not counted. This is the quantity BUG-473 regressed, and it
     * is deterministic given a fixture, so it is what the routine-loop budget test asserts rather
     * than a wall-clock figure.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun rootResolutionCount(accessor: RootAccessor): Long = rootResolutions[accessor.ordinal]

    /**
     * @return how many types every `write` walk over this graph's lifetime offered to its result
     * set (BUG-96). One per distinct reaching value and walk, however many variables hold it.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun writeInsertionCount(): Long = writeInsertions

    /**
     * @return how many up- and down-set entries every `write` and `declaredDemand` walk over this
     * graph's lifetime iterated (BUG-97). Deterministic given a fixture, so a test asserts it.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun walkStepCount(): Long = walkSteps

    /** MAINT-61: successful `upSet`/`downSet` additions over this graph's lifetime, less the entries a cluster merge (BUG-97) folded away. Equals [closureSize]. */
    @org.jetbrains.annotations.TestOnly
    internal fun edgeAddCount(): Long = edgeAdds - edgesMerged

    /** MAINT-61: `(value, use)` pairs the checker tried to admit, the revisit skip excluded. */
    @org.jetbrains.annotations.TestOnly
    internal fun pairAttemptCount(): Long = pairAttempts

    /** MAINT-61: pairs [verifyScan] compared while [LuaTypePairScanDifferential] was enabled. */
    @org.jetbrains.annotations.TestOnly
    internal fun scanComparisonCount(): Long = scanComparisons

    @org.jetbrains.annotations.TestOnly
    internal fun scanMarksRetained(): Boolean = scanMarks != null

    @org.jetbrains.annotations.TestOnly
    internal fun reportedPairCount(): Long = reportedPairs

    /**
     * BUG-114: the closure as a relation, independent of which cluster member is a representative, of
     * how variables were clustered and of the order the sets were filled in: the distinct
     * `(source, target)` pairs of creation positions the sets hold, cluster-expanded and sorted.
     */
    @org.jetbrains.annotations.TestOnly
    internal fun closureDigest(): String {
        val packed = ArrayList<Long>()
        for (node in _nodes) {
            if (node !is VariableElement || !node.isRep) continue
            val members = node.clusterMembers().map { creationOrder(it) }
            for (up in expandedOrders(node.upSet)) members.forEach { packed += (up.toLong() shl 32) or it.toLong() }
            for (down in expandedOrders(
                node.downSet,
            )) {
                members.forEach { packed += (it.toLong() shl 32) or down.toLong() }
            }
        }
        val edges = packed.sorted().distinct()
        return "${edges.size}/${edges.fold(0L) { digest, edge -> digest * 1_000_003L + edge }}"
    }

    private fun expandedOrders(set: OrderedSet<TypeNode>): List<Int> =
        set
            .flatMap { if (it is VariableElement) it.clusterMembers() else listOf(it) }
            .map { creationOrder(it) }
            .sorted()

    internal companion object {
        /** BUG-114: a test-only schedule, the fixed point visiting variables last-created first. */
        @org.jetbrains.annotations.TestOnly
        @Volatile
        internal var scheduleReversed: Boolean = false

        /** Distribution-nesting cutoff (TYPE-09-P2-04); assume-compatible beyond it (TYPE-DR-04). */
        internal const val MAX_DISTRIBUTION_DEPTH = 10

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

/**
 * BUG-473 Phase 2 — the equivalence gate for the per-visit `write` hoist in
 * [LuaTypeGraph.checkNodePairs].
 *
 * The hoist is **measured-equivalent, not proven**: its staleness window is one outer-node visit,
 * and the pair set is monotone, so a pair whose value node moved inside that window is never
 * re-examined. This differential is the evidence for that equivalence, and it ships as a gate
 * rather than as a discarded spike so a later change to the loop cannot silently invalidate it.
 *
 * While [enabled], every admitted pair additionally resolves `write` **live** and compares it with
 * the hoisted value that was actually used. The observation is inert: resolving `write` reads the
 * graph and never mutates it, so an observed run type-checks identically to an unobserved one — it
 * only costs the resolutions the hoist exists to avoid. Off in production, where the whole
 * instrument is one field read per pair.
 *
 * **Retention:** while enabled this holds two counters and at most [MAX_SAMPLES] rendered type
 * names. It never holds a [TypeNode], a `PsiElement`, a `PsiFile` or a [LuaTypeGraph], so a gate
 * left on leaks nothing beyond those strings, and [start] clears them at the head of every run.
 * The mutable state is a deliberate deviation from the immutability default, on the same ground as
 * [LuaTypeGraph.rootResolutionCount]: the quantity being asserted is a count over a run, and there
 * is no run-scoped object the engine's callers can hand a test.
 */
@org.jetbrains.annotations.TestOnly
internal object LuaTypePairWriteDifferential {
    private const val MAX_SAMPLES = 20

    private val mismatchShapes = linkedSetOf<String>()
    private var comparisonCount = 0L
    private var mismatchCount = 0L

    var enabled: Boolean = false
        private set

    /** Arm the differential and discard any previous run's counts. */
    fun start() {
        mismatchShapes.clear()
        comparisonCount = 0L
        mismatchCount = 0L
        enabled = true
    }

    /** Disarm. Callers arm in a test body and disarm in its `finally`. */
    fun stop() {
        enabled = false
    }

    fun comparisons(): Long = comparisonCount

    fun mismatches(): Long = mismatchCount

    /** Up to [MAX_SAMPLES] distinct `hoisted -> live` renderings, for adjudicating a non-zero run. */
    fun mismatchShapes(): List<String> = mismatchShapes.toList()

    fun compare(
        hoisted: LuaGraphType,
        live: LuaGraphType,
    ) {
        comparisonCount++
        if (hoisted == live) return
        mismatchCount++
        if (mismatchShapes.size < MAX_SAMPLES) {
            mismatchShapes.add("${hoisted.displayName()} -> ${live.displayName()}")
        }
    }
}

/**
 * MAINT-61: a variable's up- and down-set sizes at the start of its last checker visit. The sets
 * only grow by appending, so the pairs below both sizes are the product that visit examined.
 */
internal class PairScanMark(
    val up: Int,
    val down: Int,
)

/**
 * BUG-97: one representative's set with every merged cluster in it expanded to its member variables,
 * so each member is still a value and a use of its own with its own element as the report anchor.
 *
 * Members are listed in the order the visits first met them, and a member is only ever appended: a
 * cluster that gains a variable, or a set that gains a cluster, adds at the end. That is what keeps
 * MAINT-61's revisit skip sound, because the pairs below a visit's recorded sizes stay exactly the
 * product that visit examined. Expanding afresh each visit would grow the middle of the list.
 */
internal class MemberLog {
    private val seen: MutableSet<TypeNode> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
    private val members = ArrayList<TypeNode>()

    /** The log after appending whatever [set] holds that it has not listed, as a stable copy. */
    fun extendedBy(set: OrderedSet<TypeNode>): List<TypeNode> {
        for (node in set) {
            if (node is VariableElement) node.clusterMembers().forEach(::append) else append(node)
        }
        return members.toList()
    }

    private fun append(node: TypeNode) {
        if (seen.add(node)) members += node
    }
}

/** The two [MemberLog]s of one representative. */
internal class MemberLogs {
    val up = MemberLog()
    val down = MemberLog()
}

/** MAINT-61 design §2.3: where a revisit's scan of one use starts in the up-set snapshot. */
internal object LuaPairScan {
    /** A use seen at [mark] was already paired with every value below `mark.up`; a newer use with none. */
    fun firstValueIndex(
        downIndex: Int,
        mark: PairScanMark?,
    ): Int = if (mark == null || downIndex >= mark.down) 0 else mark.up
}

/**
 * MAINT-61 — the gate for the revisit skip in [LuaTypeGraph.checkNodePairs]. While [enabled], every
 * visit reports, for each pair of its product, whether the pair is in `checkedPairs`; a skipped pair
 * that was never examined is a miss. Shaped like [LuaTypePairWriteDifferential], and like it holds
 * only a counter, never a node, element, file or graph.
 */
@org.jetbrains.annotations.TestOnly
internal object LuaTypePairScanDifferential {
    private var missCount = 0L

    var enabled: Boolean = false
        private set

    /** Arm the differential and discard any previous run's count. */
    fun start() {
        missCount = 0L
        enabled = true
    }

    fun stop() {
        enabled = false
    }

    fun misses(): Long = missCount

    fun observe(examined: Boolean) {
        if (!examined) missCount++
    }
}

/**
 * BUG-103: one frame of [LuaTypeGraph.propagateDownward]'s walk: the pair it added, `from` into
 * `to.upSet`, and how far it has got through its two loops. The first iterator is opened when the
 * frame is, right after the add, and the second only when the first is exhausted, as in the
 * recursive form.
 */
private class DownwardStep(
    private val from: TypeNode,
    private val to: VariableElement,
) {
    private val upstreams: Iterator<TypeNode>? = (from as? VariableElement)?.upSet?.iterator()
    private var downstreams: Iterator<TypeNode>? = null

    /** The next pair to add, upstreams of [from] into [to] first, then [from] into each downstream of [to]. */
    fun next(): Pair<TypeNode, VariableElement>? {
        if (upstreams != null && upstreams.hasNext()) return upstreams.next() to to
        val pending = downstreams ?: to.downSet.iterator().also { downstreams = it }
        while (pending.hasNext()) {
            val downstream = pending.next()
            if (downstream is VariableElement) return from to downstream
        }
        return null
    }
}

/** BUG-103: [DownwardStep]'s mirror for [LuaTypeGraph.propagateUpward]: `to` into `from.downSet`. */
private class UpwardStep(
    private val from: VariableElement,
    private val to: TypeNode,
) {
    private val downstreams: Iterator<TypeNode>? = (to as? VariableElement)?.downSet?.iterator()
    private var upstreams: Iterator<TypeNode>? = null

    /** The next pair to add, [from] onto each downstream of [to] first, then each upstream of [from] onto [to]. */
    fun next(): Pair<VariableElement, TypeNode>? {
        if (downstreams != null && downstreams.hasNext()) return from to downstreams.next()
        val pending = upstreams ?: from.upSet.iterator().also { upstreams = it }
        while (pending.hasNext()) {
            val upstream = pending.next()
            if (upstream is VariableElement) return upstream to to
        }
        return null
    }
}
