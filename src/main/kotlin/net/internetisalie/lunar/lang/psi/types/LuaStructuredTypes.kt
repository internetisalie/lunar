package net.internetisalie.lunar.lang.psi.types

/**
 * A `@class`. [name] is `Box` for the declaration and `Box<string>` for an instantiation; the
 * declaration carries [typeParameters] (`[T]`) and an instantiation its [typeArguments] (`[string]`).
 */
class LuaClassType(
    override val name: String,
    val superTypes: List<LuaType> = emptyList(),
    val localMembers: Map<String, LuaTypeMember> = emptyMap(),
    val typeParameters: List<LuaGenericType> = emptyList(),
    val typeArguments: List<LuaType> = emptyList(),
    val methodTemplates: Map<String, LuaFunctionType> = emptyMap(),
) : LuaType {
    /** [name] before the first `<`: what the class indexes are keyed on. */
    val headName: String get() = name.substringBefore('<').trim()

    /**
     * This declaration's instantiations by name. One `Node<number>` per base is what lets a
     * recursive class (`---@field next Node<T>`) close its cycle in `LuaGraphType.fromLuaType`,
     * whose guard is keyed on the instance; it lives exactly as long as this class does.
     */
    internal val instantiations = java.util.concurrent.ConcurrentHashMap<String, LuaClassType>()

    override fun resolveMember(name: String): LuaTypeMember? = resolveMemberInternal(name, mutableSetOf())

    /**
     * The generic call template of method [name], own or inherited (TYPE-14-04). Kept apart from
     * the members: the member stays the lazy, graph-inert signature it always was (risks-and-gaps
     * Gap 2.9), and only a call site that instantiates reads this.
     */
    fun genericMethod(name: String): LuaFunctionType? = genericMethodInternal(name, mutableSetOf())

    private fun genericMethodInternal(
        name: String,
        visited: MutableSet<String>,
    ): LuaFunctionType? {
        if (!visited.add(this.name)) return null
        methodTemplates[name]?.let { return it }
        return superTypes.firstNotNullOfOrNull { superType ->
            val superClass =
                when (superType) {
                    is LuaTypeReference -> superType.resolved
                    is LuaParameterizedType -> superType.instantiated()
                    else -> superType
                } as? LuaClassType
            superClass?.genericMethodInternal(name, visited)
        }
    }

    override fun getMembers(): Map<String, LuaTypeMember> = getMembersInternal(mutableSetOf())

    private fun getMembersInternal(visited: MutableSet<String>): Map<String, LuaTypeMember> {
        if (!visited.add(this.name)) return emptyMap()

        val result = mutableMapOf<String, LuaTypeMember>()
        // Super types first (so local members override them)
        for (superType in superTypes.reversed()) {
            when (superType) {
                is LuaClassType -> result.putAll(superType.getMembersInternal(visited))
                else -> result.putAll(superType.getMembers())
            }
        }
        result.putAll(localMembers)
        return result
    }

    private fun resolveMemberInternal(
        name: String,
        visited: MutableSet<String>,
    ): LuaTypeMember? {
        // Prevent circular inheritance
        if (!visited.add(this.name)) return null

        // 1. Local member
        localMembers[name]?.let { return it }

        // 2. Inherited member
        for (superType in superTypes) {
            when (superType) {
                is LuaClassType -> superType.resolveMemberInternal(name, visited)?.let { return it }
                else -> superType.resolveMember(name)?.let { return it }
            }
        }

        return null
    }

    override fun isAssignableTo(other: LuaType): Boolean = isAssignableToInternal(other, mutableSetOf())

    private fun isAssignableToInternal(
        other: LuaType,
        visited: MutableSet<String>,
    ): Boolean {
        // Prevent circular inheritance
        if (!visited.add(this.name)) return false

        if (other == LuaPrimitiveType.ANY) return true
        if (this === other) return true
        if (other is LuaClassType && this.name == other.name) return true
        if (other is LuaClassType && sameHead(other)) return true
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableToInternal(it, visited) }
        }
        // Inheritance check
        return superTypes.any {
            when (it) {
                is LuaClassType -> it.isAssignableToInternal(other, visited)
                else -> it.isAssignableTo(other)
            }
        }
    }

    /** `Box<string>` ≤ `Box` and ≤ `Box<string>`, never ≤ `Box<number>` (TYPE-14-09). */
    private fun sameHead(other: LuaClassType): Boolean {
        if (headName != other.headName) return false
        if (other.typeArguments.isEmpty()) return true
        return typeArguments.size == other.typeArguments.size &&
            typeArguments.zip(other.typeArguments).all { (mine, theirs) -> mine.isAssignableTo(theirs) }
    }

    override fun toString(): String = name
}

class LuaAliasType(
    override val name: String,
    val targetType: LuaType,
) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? = targetType.resolveMember(name)

    override fun getMembers(): Map<String, LuaTypeMember> = targetType.getMembers()

    override fun isAssignableTo(other: LuaType): Boolean = targetType.isAssignableTo(other)

    override fun toString(): String = name
}

data class LuaParameter(
    val name: String,
    val type: LuaType,
    val isOptional: Boolean = false,
    val isVararg: Boolean = false,
)

class LuaFunctionType(
    val params: List<LuaParameter>,
    val returnType: LuaType,
    val typeParameters: List<LuaGenericType> = emptyList(),
    /**
     * True when [params] came from something a user wrote — `---@param` tags, a `fun(...)` type
     * annotation, a stub signature — rather than from the engine reading a bare parameter list
     * (BUG-419 defect 4). Only a declared signature makes arity a contract; see
     * [LuaGraphType.Function.declaredSignature].
     *
     * Defaults to **false**, which is the safe direction: a signature wrongly marked declared
     * produces a false ERROR, the class this flag exists to remove. `graphTypeToLuaType` rebuilds a
     * `LuaFunctionType` from an *inferred* graph function and must keep the default.
     */
    val declaredSignature: Boolean = false,
    /**
     * True when [params] leaves out a receiver that Lua passes first, because the function was
     * declared with `:` (a value) or the call was written with `:` (a demand). [LuaTypeGraph] aligns
     * a value with a demand by this flag (BUG-8).
     */
    val implicitSelf: Boolean = false,
    /**
     * True when a call from another file counts its arguments against [params]: a `---@param`
     * signature with no `@overload`, declared in the project's own source (BUG-38). Bundled stubs and
     * definition libraries are not precise enough to count against. Read by that count only, unlike
     * [declaredSignature], which also selects the parameter demand.
     */
    val countsArguments: Boolean = false,
) : LuaType {
    override val name: String =
        run {
            val paramsStr =
                params.joinToString(", ") { param ->
                    val prefix = if (param.isVararg) "..." else param.name
                    val suffix = if (param.isOptional) "?" else ""
                    "$prefix$suffix: ${param.type.name}"
                }
            "fun($paramsStr): ${returnType.name}"
        }

    override fun resolveMember(name: String): LuaTypeMember? = null

    override fun getMembers(): Map<String, LuaTypeMember> = emptyMap()

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (other is LuaFunctionType) {
            // Function covariance/contravariance rules could be applied here
            return true
        }
        return false
    }

    override fun toString(): String = name
}
