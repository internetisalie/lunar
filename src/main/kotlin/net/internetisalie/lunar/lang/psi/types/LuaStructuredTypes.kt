package net.internetisalie.lunar.lang.psi.types

class LuaClassType(
    override val name: String,
    val superTypes: List<LuaType> = emptyList(),
    val localMembers: Map<String, LuaTypeMember> = emptyMap(),
    val typeParameters: List<LuaGenericType> = emptyList()
) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? {
        return resolveMemberInternal(name, mutableSetOf())
    }

    override fun getMembers(): Map<String, LuaTypeMember> {
        return getMembersInternal(mutableSetOf())
    }

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

    private fun resolveMemberInternal(name: String, visited: MutableSet<String>): LuaTypeMember? {
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

    override fun isAssignableTo(other: LuaType): Boolean {
        return isAssignableToInternal(other, mutableSetOf())
    }

    private fun isAssignableToInternal(other: LuaType, visited: MutableSet<String>): Boolean {
        // Prevent circular inheritance
        if (!visited.add(this.name)) return false

        if (other == LuaPrimitiveType.ANY) return true
        if (this === other) return true
        if (other is LuaClassType && this.name == other.name) return true
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

    override fun toString(): String = name
}

class LuaAliasType(
    override val name: String,
    val targetType: LuaType
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
    val isVararg: Boolean = false
)

class LuaFunctionType(
    val params: List<LuaParameter>,
    val returnType: LuaType,
    val typeParameters: List<LuaGenericType> = emptyList()
) : LuaType {
    override val name: String = run {
        val paramsStr = params.joinToString(", ") { param ->
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
