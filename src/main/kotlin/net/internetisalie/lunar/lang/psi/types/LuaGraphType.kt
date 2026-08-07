package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * Graph-internal type representation. Not exposed outside the inference engine.
 * IDE consumers always receive [LuaType] via [LuaTypes.getValueType].
 *
 * Phase 1 includes only primitive heads and the lattice sentinels (ANY, UNDEFINED).
 * FunctionType, TableType, DisjunctionType etc. are stubs for later phases.
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §2
 */
sealed class LuaGraphType {
    /** ⊤ (top type). Every value is assignable to ANY. */
    data object Any : LuaGraphType()

    /** ⊥ (bottom type). UNDEFINED is assignable to every type. Represents uninferred/unknown. */
    data object Undefined : LuaGraphType()

    data object Nil : LuaGraphType()

    data object Boolean : LuaGraphType()

    data object Number : LuaGraphType()

    data object String : LuaGraphType()

    data class Function(
        val params: List<Parameter>,
        val returns: List<VariableNode>,
    ) : LuaGraphType() {
        data class Parameter(
            val node: VariableNode,
            val name: kotlin.String? = null,
            val isOptional: kotlin.Boolean = false,
            val isVararg: kotlin.Boolean = false,
        )
    }

    data class Table(
        val className: kotlin.String? = null,
        val localMembers: Map<kotlin.String, VariableNode> = emptyMap(),
        val superTypes: List<LuaGraphType> = emptyList(),
        val isExact: kotlin.Boolean = false,
    ) : LuaGraphType()

    data class Union(
        val types: Set<LuaGraphType>,
    ) : LuaGraphType() {
        companion object {
            /** Builds a canonical union (flattened, simplified, deduped, sorted, collapsed). */
            fun create(members: Collection<LuaGraphType>): LuaGraphType = LuaTypeAlgebra.canonicalize(members)
        }
    }

    data class Array(
        val elementType: LuaGraphType,
    ) : LuaGraphType()

    data class Generic(
        val name: kotlin.String,
    ) : LuaGraphType()

    /**
     * A **demand-only** type: what an operator POSITION requires its operand to be able to *do*,
     * rather than what it must *be* (BUG-423).
     *
     * `visitBinOpExpr` used to demand exactly [Number] at arithmetic and exactly [String] at `..`.
     * Lua coerces between the two at both — `"10" + 5` is 15, `1 .. "x"` is `"1x"`, measured
     * identical on 5.0.3, 5.4.7 and 5.5.0 — so the engine rejected legal code, 661 times on one
     * corpus member alone.
     *
     * **A trait must never surface through inference.** Widening the demand type to a plain
     * `number | string` union was tried and reverted, because the demand does double duty: it
     * constrains the operand *and* it feeds [VariableElement.resolveRead], so every
     * `function double(n) return n * 2 end` started hinting `n : number | string`. [inferredAs] is
     * how that is avoided — the checker sees the trait, inference only ever sees the primitive.
     * The type-engine design called for exactly this and named it "as use-type heads".
     *
     * **Missing arm — BUG-424.** In Lua an operand also satisfies these positions by carrying the
     * matching metamethod (`__add`, `__concat`, `__len`). That arm is not built: `setmetatable(t, mt)`
     * with a *named* metatable — the form all real code uses — currently infers [Undefined] (measured
     * 2026-08-07), so there is no typed table for a metamethod check to consult and no fixture that
     * could demonstrate one working. [admits] is where it belongs when that is fixed.
     *
     * **Do not add bitwise operators to [Numberable].** They are the one position whose admitted set
     * changes across supported levels: string coercion works up to 5.3 and was removed from the core
     * in 5.4 (manual §8.1), so sharing this trait would silently accept `"10" | 1` where it errors.
     * Lunar constrains bitwise operands not at all today, so it cannot currently get this wrong.
     */
    sealed class Trait : LuaGraphType() {
        /** Operand types the position accepts outright, by Lua's own coercion rules. */
        abstract val admits: Set<LuaGraphType>

        /** What inference reports for a variable constrained here. Never the trait itself. */
        abstract val inferredAs: LuaGraphType

        /** `+ - * / // ^ %` and unary `-`. */
        data object Numberable : Trait() {
            override val admits get() = setOf<LuaGraphType>(Number, String)
            override val inferredAs get() = Number
        }

        /** `..` */
        data object Stringable : Trait() {
            override val admits get() = setOf<LuaGraphType>(String, Number)
            override val inferredAs get() = String
        }

        /**
         * `#`. Already an unnamed trait before this change — `visitUnOpExpr` demanded the
         * `String | Table | Array` union inline — so naming it replaced a one-off rather than
         * adding a concept. [inferredAs] keeps that exact union so hints are unchanged.
         */
        data object Lengthable : Trait() {
            override val admits get() = setOf(String, Table(), Array(Any))
            override val inferredAs get() = Union.create(admits)
        }
    }

    /**
     * This type as INFERENCE should report it: a [Trait] collapses to the primitive it stands for,
     * everything else is itself. See [Trait] for why the distinction exists.
     */
    fun asInferred(): LuaGraphType = if (this is Trait) inferredAs else this

    /** Human-readable name for error messages. */
    fun displayName(): kotlin.String =
        when (this) {
            Any -> "any"
            Undefined -> "undefined"
            Nil -> "nil"
            Boolean -> "boolean"
            Number -> "number"
            String -> "string"
            is Table -> className ?: "{ ... }"
            is Array -> "${elementType.displayName()}[]"
            is Union -> types.joinToString(" | ") { it.displayName() }
            is Generic -> name
            // Diagnostics name the primitive, not the trait: "boolean is not assignable to string"
            // is what users understand, and what DuplicateNilAssignabilityTest pins.
            is Trait -> inferredAs.displayName()
            is Function -> {
                val paramsStr =
                    params.joinToString(", ") { param ->
                        val name = param.name ?: if (param.isVararg) "..." else "p"
                        val suffix = if (param.isOptional) "?" else ""
                        "$name$suffix"
                    }
                "fun($paramsStr)"
            }
        }

    fun getMembers(): Map<kotlin.String, VariableNode> =
        when (this) {
            is Table -> {
                val result = mutableMapOf<kotlin.String, VariableNode>()
                for (superType in superTypes.reversed()) {
                    result.putAll(superType.getMembers())
                }
                result.putAll(localMembers)
                result
            }
            is Union -> {
                val allMembers = mutableMapOf<kotlin.String, VariableNode>()
                for (type in types) {
                    allMembers.putAll(type.getMembers())
                }
                allMembers
            }
            else -> emptyMap()
        }

    companion object {
        /**
         * Converts [type] into a graph type inside a throwaway graph anchored at [anchor].
         *
         * For consumers that need a graph type but own no graph of their own — a completion provider
         * reading a type resolved out of *another* file (BUG-395). The scratch graph is seeded with
         * one node because [fromLuaType] anchors the member nodes it creates on an existing one.
         */
        fun materialize(
            type: LuaType,
            anchor: PsiElement,
        ): LuaGraphType {
            val graph = LuaTypeGraph()
            graph.variable(anchor)
            return fromLuaType(type, graph)
        }

        /**
         * Convert a [LuaType] to its graph equivalent.
         * Creates internal variable nodes for structural types (functions, tables).
         */
        fun fromLuaType(
            type: LuaType,
            graph: LuaTypeGraph,
            visited: MutableMap<LuaType, LuaGraphType> = mutableMapOf(),
        ): LuaGraphType {
            if (type in visited) return visited[type]!!

            return when (type) {
                LuaPrimitiveType.ANY -> Any
                LuaPrimitiveType.NIL -> Nil
                LuaPrimitiveType.BOOLEAN -> Boolean
                LuaPrimitiveType.NUMBER -> Number
                LuaPrimitiveType.STRING -> String
                LuaPrimitiveType.VOID -> Nil
                LuaPrimitiveType.UNKNOWN -> Undefined
                LuaPrimitiveType.FUNCTION -> Function(emptyList(), emptyList())
                LuaPrimitiveType.TABLE -> Table()
                LuaPrimitiveType.INTEGER -> Number

                is LuaFunctionType -> {
                    val result = Function(emptyList(), emptyList())
                    visited[type] = result

                    val params =
                        type.params.map { p ->
                            Function.Parameter(memberNodeFor(p.type, graph, visited), p.name, p.isOptional, p.isVararg)
                        }
                    val returnNode = memberNodeFor(type.returnType, graph, visited)

                    val finalFunc = Function(params, listOf(returnNode))
                    visited[type] = finalFunc
                    finalFunc
                }

                is LuaAliasType -> fromLuaType(type.targetType, graph, visited)
                is LuaTypeReference -> fromLuaType(type.resolveType(), graph, visited)

                is LuaArrayType -> {
                    Array(fromLuaType(type.elementType, graph, visited))
                }

                is LuaTableLiteralType -> {
                    val members = LinkedHashMap<kotlin.String, VariableNode>()
                    val result = Table(null, members, isExact = true)
                    visited[type] = result
                    type.getMembers().forEach { (name, member) ->
                        members[name] = memberNodeFor(member.type, graph, visited)
                    }
                    result
                }

                is LuaClassType -> {
                    val members = LinkedHashMap<kotlin.String, VariableNode>()
                    val supers = mutableListOf<LuaGraphType>()
                    val result = Table(type.name, members, supers, isExact = true)
                    visited[type] = result
                    type.getMembers().forEach { (name, member) ->
                        members[name] = memberNodeFor(member.type, graph, visited)
                    }
                    type.superTypes.forEach { supers.add(fromLuaType(it, graph, visited)) }
                    result
                }

                is LuaUnionType -> {
                    val result = Union(emptySet())
                    visited[type] = result
                    val memberTypes = type.types.map { fromLuaType(it, graph, visited) }.toSet()
                    val finalUnion = Union.create(memberTypes)
                    visited[type] = finalUnion
                    finalUnion
                }

                is LuaGenericType -> {
                    Generic(type.name)
                }

                is LuaParameterizedType -> {
                    // For Phase 5, we represent parameterized types as Tables with the full name
                    Table(type.name)
                }

                else -> Any
            }
        }

        private fun memberNodeFor(
            memberType: LuaType,
            graph: LuaTypeGraph,
            visited: MutableMap<LuaType, LuaGraphType>,
        ): VariableNode {
            val anchor = graph.firstNodeElement() ?: error("Graph must have at least one node")
            val memberNode = graph.variable(anchor)
            val graphType = fromLuaType(memberType, graph, visited)
            memberNode.upSet.add(graph.value(memberNode.element, graphType))
            memberNode.downSet.add(graph.use(memberNode.element, graphType))
            return memberNode
        }
    }
}
