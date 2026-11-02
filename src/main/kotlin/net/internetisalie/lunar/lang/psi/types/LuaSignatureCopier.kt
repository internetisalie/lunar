package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * Per-call copies of a declared signature's parameters (BUG-19, BUG-425).
 *
 * A declared callee's signature lives in a memoized seed that every call in the file reads. A
 * structural check wires edges into the demand's own nodes, so checking an argument against the
 * seed's node would let one call rewrite the signature for the rest. Each argument therefore meets a
 * fresh demand, built here, and nowhere else.
 */
internal class LuaSignatureCopier(
    private val graph: LuaTypeGraph,
) {
    /**
     * A fresh `declaredDemand` use for [param] at [argument], demanding the parameter's declared
     * demand ([LuaTypeAlgebra.parameterDemand]). Null when that demand holds a table, which is not
     * copied (MAINT-51 design section 8).
     *
     * Wire the argument's node straight into it. A variable between the two would merge the
     * argument's reaching definitions into one write and check their union, where BUG-416 checks
     * each definition alone.
     */
    fun parameterUse(
        param: LuaGraphType.Function.Parameter,
        argument: PsiElement,
    ): UseNode? {
        val demand = detachedDemand(LuaTypeAlgebra.parameterDemand(param.node.read, param.isOptional)) ?: return null
        return graph.use(argument, demand, declaredDemand = true)
    }

    /** [type] with its functions copied onto fresh nodes, or null if it holds a table (BUG-19). */
    private fun detachedDemand(type: LuaGraphType): LuaGraphType? =
        when (type) {
            is LuaGraphType.Function -> detachedFunction(type)
            is LuaGraphType.Table -> null
            is LuaGraphType.Array -> detachedDemand(type.elementType)?.let { LuaGraphType.Array(it) }
            is LuaGraphType.Union -> {
                val members = type.types.map { detachedDemand(it) }
                if (null in members) null else LuaGraphType.Union.create(members.filterNotNull())
            }
            else -> type
        }

    /**
     * A copy that keeps the signature's parameters and leaves out its returns: `fun()` has a `nil`
     * return node, and a callback's return value is not an error against it.
     */
    private fun detachedFunction(declared: LuaGraphType.Function): LuaGraphType.Function =
        declared.copy(
            params = declared.params.map { it.copy(node = detachedParamNode(it.node)) },
            returns = emptyList(),
        )

    /** A fresh node of [shared]'s type, untyped when that type would carry the shared nodes along. */
    private fun detachedParamNode(shared: VariableNode): VariableNode {
        val fresh = graph.variable(shared.element)
        val type = shared.read
        if (!carriesGraphNodes(type)) {
            graph.addEdge(graph.value(shared.element, type), fresh)
        }
        return fresh
    }

    companion object {
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
         * [detachedDemand] copies function types instead (BUG-19). A `---@param w wxWindow` is still
         * unchecked, because a class table cannot be copied so cheaply.
         */
        fun carriesGraphNodes(type: LuaGraphType): Boolean =
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
        fun positionsAreUnambiguous(
            declared: LuaGraphType.Function,
            count: Int,
        ): Boolean = declared.params.none { it.isVararg } && count == declared.params.size
    }
}
