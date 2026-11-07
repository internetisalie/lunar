package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-call-site instantiation of a generic function (TYPE-14 design §4.4, §5.3-§5.8).
 *
 * Each call mints one fresh node per type parameter. A parameter binds its names through its
 * structure by lazy projections out of the argument's type, and a return is rebuilt by a lazy
 * substitution of those nodes, so an argument seeded after the call is visited is still observed.
 * The template's own nodes are never fed: the callee's body is inferred once, against its symbolic
 * types, and two call sites never share a binding.
 *
 * A read never mints into the file's graph. Function copies are minted when the call is
 * instantiated; a type read back out of a parameterized name is materialized in a scratch graph and
 * memoized per instantiation (BUG-473: a lazy node that minted on read would move the revision every
 * reader validates against, and the snapshot is read concurrently).
 */
internal class LuaGenericInstantiation(
    private val graph: LuaTypeGraph,
    private val copier: LuaSignatureCopier,
) {
    /** One step of a path into a type (design §5.3). */
    sealed interface Step {
        data object Elem : Step

        data class Arm(
            val index: Int,
        ) : Step

        data class Param(
            val index: Int,
        ) : Step

        data class Return(
            val index: Int,
        ) : Step

        data class Arg(
            val index: Int,
        ) : Step

        data class Member(
            val name: String,
        ) : Step
    }

    /**
     * A template copied for one call: fresh nodes where it was generic, the node bound to each name, and
     * each `` `T` `` parameter's index with the name its string argument binds (design §5.11).
     */
    class Instantiated(
        val function: LuaGraphType.Function,
        val bindings: Map<String, VariableNode>,
        val captures: Map<Int, String>,
    )

    /** A call to a declaration-typed callee: each argument's node (null when it has none), and the result nodes. */
    class DeclaredCall(
        val call: PsiElement,
        val argNodes: List<TypeNode?>,
        val argExprs: List<PsiElement>,
        val resultNodes: List<VariableNode>,
    )

    private data class CacheKey(
        val template: LuaGraphType.Function,
        val call: PsiElement,
    )

    private val instantiationCache = HashMap<CacheKey, Instantiated>()

    /** True when [type] holds a [LuaGraphType.Generic] at any depth a binding can reach. */
    fun containsGeneric(type: LuaGraphType): Boolean = projectionsOf(type, emptySet()).isNotEmpty()

    /** The same-file template [template] instantiated for [call] (design §5.5). Memoized per (template, call). */
    fun instantiate(
        template: LuaGraphType.Function,
        call: PsiElement,
    ): Instantiated =
        instantiationCache.getOrPut(CacheKey(template, call)) { Instantiator(template, call, false).build() }

    /**
     * The declared-path application (design §5.8): a full copy sharing no node with the seed, bound
     * from the arguments only where positions are unambiguous, with its returns flowing into the call's
     * result nodes. No call demand and no arity check reach the seed (BUG-397, BUG-425).
     */
    fun applyDeclared(
        declared: LuaGraphType.Function,
        site: DeclaredCall,
    ) {
        val instantiated =
            instantiationCache.getOrPut(CacheKey(declared, site.call)) {
                Instantiator(declared, site.call, true).build()
            }
        if (LuaSignatureCopier.positionsAreUnambiguous(declared, site.argExprs.size)) {
            bindArguments(declared, instantiated, site)
            bindCaptures(instantiated, site.argExprs)
        }
        instantiated.function.returns.forEachIndexed { index, returned ->
            site.resultNodes.getOrNull(index)?.let { graph.addEdge(returned, it) }
        }
    }

    /**
     * Every argument is checked against its declared parameter as [LuaSignatureCopier] checks it, a
     * generic position included — `first(5)` against `T[]` is still an error. A generic position
     * also flows into the copy, which is what binds its names.
     */
    private fun bindArguments(
        declared: LuaGraphType.Function,
        instantiated: Instantiated,
        site: DeclaredCall,
    ) {
        declared.params.forEachIndexed { index, param ->
            val argNode = site.argNodes.getOrNull(index) ?: return@forEachIndexed
            copier.parameterUse(param, site.argExprs[index])?.let { graph.addEdge(argNode, it) }
            if (projectionsOf(param.node.write, instantiated.bindings.keys).isNotEmpty()) {
                graph.addEdge(argNode, instantiated.function.params[index].node)
            }
        }
    }

    /**
     * Backtick capture (design §5.11): a string literal at a `` `T` `` position binds `T` to the type the
     * literal names. Any other argument, or a name that resolves to nothing, leaves `T` unbound.
     */
    fun bindCaptures(
        instantiated: Instantiated,
        argExprs: List<PsiElement>,
    ) {
        instantiated.captures.forEach { (index, name) ->
            val argument = argExprs.getOrNull(index) ?: return@forEach
            val captured = capturedType(argument) ?: return@forEach
            graph.addEdge(graph.value(argument, captured, declaredOrigin = true), instantiated.bindings.getValue(name))
        }
    }

    /** The type a quoted string literal names; a long-bracket string is not a capture. */
    private fun capturedType(argument: PsiElement): LuaGraphType? {
        val literal = (argument as? LuaTerminalExpr)?.string?.text ?: return null
        if (literal.length < 2 || literal.first() !in QUOTES || literal.last() != literal.first()) return null
        val named = literal.substring(1, literal.length - 1)
        val resolved = LuaTypeManager.getInstance(argument.project).resolveType(named, argument) ?: return null
        return LuaGraphType.fromLuaType(resolved, graph)
    }

    /**
     * Every path at which a generic occurs in [type], by name (design §5.3). [names] are the type
     * parameters in scope: a parameterized name carries its arguments only as text, so an argument is
     * a generic position when its text is one of them.
     */
    private fun projectionsOf(
        type: LuaGraphType,
        names: Set<String>,
    ): Map<String, List<List<Step>>> = PathCollector(names).apply { collect(type, emptyList()) }.paths

    /**
     * The walk behind [projectionsOf]. A named class's members belong to the class's scope, so only
     * anonymous tables are entered.
     */
    private class PathCollector(
        private val names: Set<String>,
    ) {
        val paths = LinkedHashMap<String, MutableList<List<Step>>>()
        private val entered: MutableSet<LuaGraphType> = Collections.newSetFromMap(IdentityHashMap())

        fun collect(
            type: LuaGraphType,
            path: List<Step>,
        ) {
            if (path.size > LuaTypeGraph.MAX_DISTRIBUTION_DEPTH) return
            when (type) {
                is LuaGraphType.Generic -> record(type.name, path)
                is LuaGraphType.Array -> collect(type.elementType, path + Step.Elem)
                is LuaGraphType.Union -> type.types.forEachIndexed { i, arm -> collect(arm, path + Step.Arm(i)) }
                is LuaGraphType.Function -> if (entered.add(type)) collectFunction(type, path)
                is LuaGraphType.Table -> if (entered.add(type)) collectTable(type, path)
                else -> Unit
            }
        }

        private fun record(
            name: String,
            path: List<Step>,
        ) {
            paths.getOrPut(name) { mutableListOf() }.add(path)
        }

        private fun collectFunction(
            function: LuaGraphType.Function,
            path: List<Step>,
        ) {
            function.params.forEachIndexed { index, param -> collect(param.node.write, path + Step.Param(index)) }
            function.returns.forEachIndexed { index, returned -> collect(returned.write, path + Step.Return(index)) }
        }

        private fun collectTable(
            table: LuaGraphType.Table,
            path: List<Step>,
        ) {
            typeArgumentsOf(table.className).forEachIndexed { index, argument ->
                if (argument in names) record(argument, path + Step.Arg(index))
            }
            if (table.className != null) return
            table.localMembers.forEach { (name, node) -> collect(node.write, path + Step.Member(name)) }
            table.superTypes.forEach { collect(it, path) }
        }
    }

    /**
     * Builds one [Instantiated]. [copyUnchanged] is the declared path's rule: a position without a
     * generic is still copied onto a fresh node, so the result shares no node with the seed.
     */
    private inner class Instantiator(
        private val template: LuaGraphType.Function,
        private val call: PsiElement,
        private val copyUnchanged: Boolean,
    ) {
        private val names: Set<String> = projectionsOf(template, emptySet()).keys
        private val bindings: Map<String, VariableNode> = names.associateWith { graph.variable(call) }
        private val reader = BindingReader(bindings, call)
        private val captures = LinkedHashMap<Int, String>()

        fun build(): Instantiated {
            val params = template.params.mapIndexed { index, param -> instantiateParam(index, param) }
            val returns = template.returns.map { instantiateReturn(it) }
            return Instantiated(template.copy(params = params, returns = returns), bindings, captures.toMap())
        }

        private fun instantiateParam(
            index: Int,
            param: LuaGraphType.Function.Parameter,
        ): LuaGraphType.Function.Parameter {
            val written = param.node.write
            if (written is LuaGraphType.Generic) return param.copy(node = genericParameter(index, param.node, written))
            val paths = projectionsOf(written, names)
            if (paths.isNotEmpty()) return param.copy(node = projectedParameter(param.node, paths))
            return if (copyUnchanged) param.copy(node = graph.variable(call)) else param
        }

        /**
         * A parameter typed exactly `T` (design §5.5 step 2, path ε). Unconstrained, it is `T`'s binding
         * node, as before. A `` `T` `` capture gets a fresh node the string argument stops at, because the
         * literal's text binds `T`, not its type (§5.11). A same-file bounded `T` gets a fresh node, anchored
         * where the template parameter is, feeding the binding and meeting the template parameter's declared
         * demands: the argument is checked against the bound once, with the message and on the elements a
         * plain `---@param p Bound` reports (§5.5 step 1). A declared callee keeps the binding node, since
         * [LuaSignatureCopier.parameterUse] already checks the bound there.
         */
        private fun genericParameter(
            index: Int,
            original: VariableNode,
            generic: LuaGraphType.Generic,
        ): VariableNode {
            val binding = bindings.getValue(generic.name)
            if (generic.capture) {
                captures[index] = generic.name
                return graph.variable(call)
            }
            if (generic.bound == null || copyUnchanged) return binding
            val parameter = graph.variable(original.element)
            graph.addEdge(parameter, binding)
            declaredDemandsOf(original).forEach { graph.addEdge(parameter, it) }
            return parameter
        }

        /**
         * A fresh parameter node, each generic it mentions bound by a lazy projection out of what flows
         * in. On the same-file path it also meets the template parameter's declared demands, so the
         * argument's shape is checked exactly where the template's own node would have checked it
         * (design §5.5 step 2). The declared path checks through [LuaSignatureCopier] instead.
         */
        private fun projectedParameter(
            original: VariableNode,
            paths: Map<String, List<List<Step>>>,
        ): VariableNode {
            val parameter = graph.variable(call)
            if (!copyUnchanged) declaredDemandsOf(original).forEach { graph.addEdge(parameter, it) }
            paths.forEach { (name, namePaths) ->
                namePaths.forEach { path ->
                    val projection =
                        graph.lazyValue(call) { visited ->
                            reader.projectAt(parameter.writeWith(visited), path, visited)
                        }
                    graph.addEdge(projection, bindings.getValue(name))
                }
            }
            return parameter
        }

        /** The user-written demands on a template parameter; the body's inferred demands stay with the template. */
        private fun declaredDemandsOf(original: VariableNode): List<UseNode> =
            original.downSet.filterIsInstance<UseNode>().filter { it !is VariableNode && it.declaredDemand }

        private fun instantiateReturn(returned: VariableNode): VariableNode {
            val written = returned.write
            return when {
                written is LuaGraphType.Generic -> bindings.getValue(written.name)
                projectionsOf(written, names).isNotEmpty() -> substitutedReturn(written)
                copyUnchanged -> declaredReturn(written)
                else -> returned
            }
        }

        private fun substitutedReturn(written: LuaGraphType): VariableNode {
            reader.prepareFunctions(written)
            val fresh = graph.variable(call)
            graph.addEdge(graph.lazyValue(call, reader.substitution(written)), fresh)
            return fresh
        }

        private fun declaredReturn(written: LuaGraphType): VariableNode {
            val fresh = graph.variable(call)
            graph.addEdge(graph.value(call, written, declaredOrigin = true), fresh)
            return fresh
        }
    }

    /**
     * Reads one instantiation's bindings back into types: projection out of an argument (design §5.4)
     * and substitution into a return (§5.6). Functions met in a return are copied up front by
     * [prepareFunctions], at instantiation time, so no read mints a node into the file's graph.
     */
    private inner class BindingReader(
        private val bindings: Map<String, VariableNode>,
        private val call: PsiElement,
    ) {
        private val preparedFunctions = HashMap<LuaGraphType.Function, LuaGraphType.Function>()
        private val namedTypes = ConcurrentHashMap<String, LuaGraphType>()
        private val argumentTypes = ConcurrentHashMap<String, LuaGraphType>()

        fun projectAt(
            type: LuaGraphType,
            path: List<Step>,
            visited: MutableSet<VariableNode>,
        ): LuaGraphType {
            val step = path.firstOrNull() ?: return withoutPlaceholders(type)
            val next =
                when (step) {
                    Step.Elem -> LuaTypesVisitor.arrayElementType(type)
                    is Step.Arm -> withoutNil(type)
                    is Step.Param ->
                        functionArm(type)
                            ?.params
                            ?.getOrNull(step.index)
                            ?.node
                            ?.writeWith(visited)
                    is Step.Return -> functionArm(type)?.returns?.getOrNull(step.index)?.writeWith(visited)
                    is Step.Arg -> typeArgument(type, step.index)
                    is Step.Member -> type.getMembers()[step.name]?.writeWith(visited)
                } ?: return LuaGraphType.Undefined
            return projectAt(next, path.drop(1), visited)
        }

        /**
         * A generic placeholder in a projected argument is never a binding. On the same-file path the
         * argument also meets the template's declared demand, and that check wires the demand's own
         * nodes — `T` among them — back into the argument's structure, the same edges base e8fc4248
         * wired. Read as a binding, it would make `take(cb)` with `cb: fun(a: number)` read `number | T`.
         */
        private fun withoutPlaceholders(type: LuaGraphType): LuaGraphType =
            when (type) {
                is LuaGraphType.Generic -> LuaGraphType.Undefined
                is LuaGraphType.Union -> LuaGraphType.Union.create(type.types.filterNot { it is LuaGraphType.Generic })
                else -> type
            }

        /** A union position binds the whole non-nil value, never one arm by position (design §5.4 step 3). */
        private fun withoutNil(type: LuaGraphType): LuaGraphType {
            val arms = (if (type is LuaGraphType.Union) type.types else setOf(type)) - LuaGraphType.Nil
            return if (arms.isEmpty()) LuaGraphType.Undefined else LuaGraphType.Union.create(arms)
        }

        private fun functionArm(type: LuaGraphType): LuaGraphType.Function? =
            when (type) {
                is LuaGraphType.Function -> type
                is LuaGraphType.Union -> type.types.firstNotNullOfOrNull { it as? LuaGraphType.Function }
                else -> null
            }

        /** The [index]th argument of a parameterized name, read back out of the name's text. */
        private fun typeArgument(
            type: LuaGraphType,
            index: Int,
        ): LuaGraphType? {
            val argument = typeArgumentsOf((type as? LuaGraphType.Table)?.className).getOrNull(index) ?: return null
            return argumentTypes.memoized(argument) { LuaGraphType.materialize(TypeParser.parse(argument, call), call) }
        }

        /**
         * The lazy compute that substitutes [written]. A walk that already passed one of the bindings
         * [written] mentions reaches this value a second time, through the variable it feeds, and the
         * read contributes nothing — as a revisited variable does (BUG-390). Bindings themselves are
         * read through copies of the walk's set, so this fires only when the walk passed a binding's
         * node directly (risks-and-gaps Gap 2.5).
         */
        fun substitution(written: LuaGraphType): (MutableSet<VariableNode>) -> LuaGraphType {
            val mentioned = projectionsOf(written, bindings.keys).keys.associateWith { bindings.getValue(it) }
            return { visited ->
                if (mentioned.values.any { it in visited }) {
                    LuaGraphType.Undefined
                } else {
                    substitute(written, resolvedOnce(mentioned, visited))
                }
            }
        }

        /**
         * Each binding read once, from the walk's guard as it stood on entry. The guard is never popped,
         * so a second occurrence of a name read through the shared set would find its own binding
         * already visited and substitute `undefined` (`T|T[]` read as `number | undefined[]`). Each
         * read gets its own copy, which still holds the entry path and so still stops a real cycle.
         */
        private fun resolvedOnce(
            mentioned: Map<String, VariableNode>,
            visited: MutableSet<VariableNode>,
        ): Map<String, LuaGraphType> = mentioned.mapValues { (_, binding) -> binding.writeWith(HashSet(visited)) }

        private fun substitute(
            type: LuaGraphType,
            resolved: Map<String, LuaGraphType>,
        ): LuaGraphType =
            when (type) {
                is LuaGraphType.Generic -> resolved[type.name] ?: type
                is LuaGraphType.Array -> LuaGraphType.Array(substitute(type.elementType, resolved))
                is LuaGraphType.Union -> LuaGraphType.Union.create(type.types.map { substitute(it, resolved) })
                is LuaGraphType.Function -> preparedFunctions[type] ?: type
                is LuaGraphType.Table -> substituteName(type, resolved)
                else -> type
            }

        /** `Head<T>` with `T` bound becomes the type the instantiated name resolves to (design §5.6). */
        private fun substituteName(
            table: LuaGraphType.Table,
            resolved: Map<String, LuaGraphType>,
        ): LuaGraphType {
            val className = table.className ?: return table
            val arguments = typeArgumentsOf(className)
            if (arguments.none { it in resolved }) return table
            val substituted = arguments.joinToString(", ") { resolved[it]?.displayName() ?: it }
            val name = "${className.substringBefore('<').trim()}<$substituted>"
            return namedTypes.memoized(name) {
                val found = LuaTypeManager.getInstance(call.project).resolveType(name, call)
                found?.let { LuaGraphType.materialize(it, call) } ?: LuaGraphType.Table(name)
            }
        }

        /** Computed outside the map so a recursive read never holds a lock; a racing duplicate is discarded. */
        private fun ConcurrentHashMap<String, LuaGraphType>.memoized(
            key: String,
            compute: () -> LuaGraphType,
        ): LuaGraphType {
            get(key)?.let { return it }
            val computed = compute()
            return putIfAbsent(key, computed) ?: computed
        }

        /**
         * Copies every generic function in [type] onto fresh nodes, each fed by a lazy substitution of
         * the original's node: a function returned from a generic call is a new value per call.
         */
        fun prepareFunctions(type: LuaGraphType) {
            when (type) {
                is LuaGraphType.Array -> prepareFunctions(type.elementType)
                is LuaGraphType.Union -> type.types.forEach { prepareFunctions(it) }
                is LuaGraphType.Function -> if (needsCopy(type)) prepareFunction(type)
                else -> Unit
            }
        }

        private fun needsCopy(function: LuaGraphType.Function): Boolean =
            function !in preparedFunctions && projectionsOf(function, bindings.keys).isNotEmpty()

        private fun prepareFunction(function: LuaGraphType.Function) {
            preparedFunctions[function] = function
            val params = function.params.map { it.copy(node = substitutedNode(it.node)) }
            preparedFunctions[function] =
                function.copy(params = params, returns = function.returns.map { substitutedNode(it) })
        }

        private fun substitutedNode(original: VariableNode): VariableNode {
            val written = original.write
            prepareFunctions(written)
            val fresh = graph.variable(call)
            graph.addEdge(graph.lazyValue(call, substitution(written)), fresh)
            return fresh
        }
    }

    private companion object {
        val QUOTES = setOf('"', '\'')

        /** The type arguments a parameterized name spells, split at depth 0; empty for a plain name. */
        fun typeArgumentsOf(className: String?): List<String> {
            if (className == null || !className.endsWith(">") || '<' !in className) return emptyList()
            val inner = className.substring(className.indexOf('<') + 1, className.length - 1)
            val arguments = mutableListOf<String>()
            var depth = 0
            var start = 0
            inner.forEachIndexed { index, char ->
                when (char) {
                    '<', '(', '{', '[' -> depth++
                    '>', ')', '}', ']' -> depth--
                    ',' ->
                        if (depth == 0) {
                            arguments += inner.substring(start, index).trim()
                            start = index + 1
                        }
                }
            }
            return arguments + inner.substring(start).trim()
        }
    }
}
