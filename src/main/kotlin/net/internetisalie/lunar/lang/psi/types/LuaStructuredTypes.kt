package net.internetisalie.lunar.lang.psi.types

class LuaClassType(
    override val name: String,
    val superTypes: List<LuaType> = emptyList(),
    val members: Map<String, LuaTypeMember> = emptyMap(),
    val typeParameters: List<LuaGenericType> = emptyList()
) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? {
        return resolveMemberInternal(name, mutableSetOf())
    }

    private fun resolveMemberInternal(name: String, visited: MutableSet<String>): LuaTypeMember? {
        // Prevent circular inheritance
        if (!visited.add(this.name)) return null

        // 1. Local member
        members[name]?.let { return it }

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

    override fun isAssignableTo(other: LuaType): Boolean = targetType.isAssignableTo(other)

    override fun toString(): String = name
}

data class LuaParameter(val name: String, val type: LuaType)

class LuaFunctionType(
    val params: List<LuaParameter>,
    val returnType: LuaType,
    val typeParameters: List<LuaGenericType> = emptyList()
) : LuaType {
    override val name: String = "fun(${params.joinToString(", ") { "${it.name}: ${it.type.name}" }}): ${returnType.name}"

    override fun resolveMember(name: String): LuaTypeMember? = null

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
