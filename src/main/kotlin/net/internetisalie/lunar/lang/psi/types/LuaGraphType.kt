package net.internetisalie.lunar.lang.psi.types

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
            fun create(members: Collection<LuaGraphType>): LuaGraphType =
                LuaTypeAlgebra.canonicalize(members)
        }
    }

    data class Array(
        val elementType: LuaGraphType,
    ) : LuaGraphType()

    data class Generic(
        val name: kotlin.String,
    ) : LuaGraphType()

    /** Human-readable name for error messages. */
    fun displayName(): kotlin.String = when (this) {
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
        is Function -> {
            val paramsStr = params.joinToString(", ") { param ->
                val name = param.name ?: if (param.isVararg) "..." else "p"
                val suffix = if (param.isOptional) "?" else ""
                "$name$suffix"
            }
            "fun($paramsStr)"
        }
    }

    fun getMembers(): Map<kotlin.String, VariableNode> = when (this) {
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

                    val params = type.params.map { p ->
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
