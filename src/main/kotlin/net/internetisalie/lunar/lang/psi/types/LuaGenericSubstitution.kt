package net.internetisalie.lunar.lang.psi.types

import java.util.IdentityHashMap

/**
 * Layer-1 substitution of a generic class's parameters (TYPE-14 design §4.3, §5.9).
 *
 * `Box<string>` is built from `Box` by replacing `T` with `string` in every member and every
 * supertype, at every depth. Runs inside `LuaTypeManagerImpl`'s cached resolution, or lazily from a
 * [LuaParameterizedType] member lookup.
 */
object LuaGenericSubstitution {
    /** `Box<string>` from `Box` and `[string]` (design §5.9). */
    fun instantiate(
        base: LuaClassType,
        arguments: List<LuaType>,
    ): LuaClassType {
        val name = "${base.headName}<${arguments.joinToString(", ") { it.name }}>"
        base.instantiations[name]?.let { return it }
        val bindings = bindingsOf(base, arguments)
        val visited = IdentityHashMap<LuaType, LuaType>()
        val members =
            base.localMembers.mapValues { (_, member) ->
                member.copy(type = substitute(member.type, bindings, visited))
            }
        val supers = base.superTypes.map { substitute(it, bindings, visited) }
        val templates =
            base.methodTemplates.mapValues { (_, template) ->
                substitute(template, bindings, visited) as? LuaFunctionType
                    ?: template
            }
        val instantiated = LuaClassType(name, supers, members, typeArguments = arguments, methodTemplates = templates)
        return base.instantiations.putIfAbsent(name, instantiated) ?: instantiated
    }

    /**
     * Each of [base]'s parameters bound to its argument by position. A missing argument, or one that
     * names nothing (the `T` of a module's exported `Box<T>`), leaves the parameter symbolic.
     */
    private fun bindingsOf(
        base: LuaClassType,
        arguments: List<LuaType>,
    ): Map<String, LuaType> =
        base.typeParameters
            .mapIndexed { index, parameter ->
                val argument = arguments.getOrNull(index)?.takeUnless { isUnresolvedReference(it) }
                parameter.name to (argument ?: parameter)
            }.toMap()

    private fun isUnresolvedReference(type: LuaType): Boolean =
        type is LuaTypeReference && type.resolved == LuaPrimitiveType.UNKNOWN

    /** [type] with every [LuaGenericType] named in [bindings] replaced, at every depth. */
    fun substitute(
        type: LuaType,
        bindings: Map<String, LuaType>,
        visited: IdentityHashMap<LuaType, LuaType> = IdentityHashMap(),
    ): LuaType {
        if (bindings.isEmpty()) return type
        visited[type]?.let { return it }
        return when (type) {
            is LuaGenericType -> bindings[type.name] ?: type
            is LuaTypeReference -> substituteReference(type, bindings, visited)
            is LuaArrayType -> LuaArrayType(substitute(type.elementType, bindings, visited))
            is LuaUnionType -> LuaUnionType(type.types.map { substitute(it, bindings, visited) }.toSet())
            is LuaFunctionType -> substituteFunction(type, bindings, visited)
            is LuaParameterizedType ->
                LuaParameterizedType(
                    type.baseType,
                    type.arguments.map { substitute(it, bindings, visited) },
                )
            is LuaTableLiteralType -> substituteTable(type, bindings, visited)
            is LuaAliasType -> LuaAliasType(type.name, substitute(type.targetType, bindings, visited))
            else -> type
        }
    }

    /** A reference stays lazy unless its name spells a bound parameter. */
    private fun substituteReference(
        reference: LuaTypeReference,
        bindings: Map<String, LuaType>,
        visited: IdentityHashMap<LuaType, LuaType>,
    ): LuaType {
        if (!TypeParser.mentionsAny(reference.name, bindings.keys)) return reference
        return substitute(reference.resolved, bindings, visited)
    }

    /** A method's own `@generic` names shadow the class's (design §5.1 step 3), so they are not replaced. */
    private fun substituteFunction(
        function: LuaFunctionType,
        bindings: Map<String, LuaType>,
        visited: IdentityHashMap<LuaType, LuaType>,
    ): LuaType {
        val outer = bindings - function.typeParameters.map { it.name }.toSet()
        return LuaFunctionType(
            function.params.map { it.copy(type = substitute(it.type, outer, visited)) },
            substitute(function.returnType, outer, visited),
            function.typeParameters,
            function.declaredSignature,
            function.implicitSelf,
        )
    }

    /** Registered before its members are substituted, so a member that refers back to the table ends. */
    private fun substituteTable(
        table: LuaTableLiteralType,
        bindings: Map<String, LuaType>,
        visited: IdentityHashMap<LuaType, LuaType>,
    ): LuaType {
        val members = LinkedHashMap<String, LuaTypeMember>()
        val result = LuaTableLiteralType(members)
        visited[table] = result
        table.localMembers.forEach { (name, member) ->
            members[name] =
                member.copy(type = substitute(member.type, bindings, visited))
        }
        return result
    }
}
