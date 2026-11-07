package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

class LuaUnionType(
    val types: Set<LuaType>,
) : LuaType {
    override val name: String = types.joinToString(" | ") { it.name }

    override fun resolveMember(name: String): LuaTypeMember? {
        // In a union, we can only resolve a member if it's present in ALL types
        // OR we return a union of the members' types.
        // For now, let's keep it simple: if any type can't resolve it, it's null.
        val members = types.mapNotNull { it.resolveMember(name) }
        if (members.size != types.size) return null

        // TYPE-13-06/§4.4: carry the declaration forward — the first arm that has one wins,
        // matching getMembers' putIfAbsent below.
        return LuaTypeMember(
            name,
            LuaUnionType(members.map { it.type }.toSet()),
            sourceElement = members.firstNotNullOfOrNull { it.sourceElement },
        )
    }

    override fun getMembers(): Map<String, LuaTypeMember> {
        val typesByName = linkedMapOf<String, MutableSet<LuaType>>()
        val declarationByName = linkedMapOf<String, PsiElement>()
        for (type in types) {
            for ((name, member) in type.getMembers()) {
                typesByName.getOrPut(name) { mutableSetOf() }.add(member.type)
                member.sourceElement?.let { declarationByName.putIfAbsent(name, it) }
            }
        }
        return typesByName.mapValues { (name, typeSet) ->
            LuaTypeMember(
                name,
                if (typeSet.size == 1) typeSet.first() else LuaUnionType(typeSet),
                sourceElement = declarationByName[name],
            )
        }
    }

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (this == other) return true
        // Every type in this union must be assignable to 'other'
        return types.all { it.isAssignableTo(other) }
    }

    override fun toString(): String = name
}

class LuaArrayType(
    val elementType: LuaType,
) : LuaType {
    override val name: String = "${elementType.name}[]"

    override fun resolveMember(name: String): LuaTypeMember? = null

    override fun getMembers(): Map<String, LuaTypeMember> = emptyMap()

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (other is LuaArrayType) {
            return elementType.isAssignableTo(other.elementType)
        }
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableTo(it) }
        }
        return false
    }

    override fun toString(): String = name
}

class LuaGenericType(
    override val name: String,
    /** The `: Base` of `---@generic T : Base`; null when unconstrained. */
    val bound: LuaType? = null,
    /** True for the `` `T` `` spelling: the argument's string literal names the class bound to it. */
    val capture: Boolean = false,
) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? = bound?.resolveMember(name)

    override fun getMembers(): Map<String, LuaTypeMember> = bound?.getMembers() ?: emptyMap()

    /** A symbolic parameter is not a value that can fail an assignment; a bounded one is its bound. */
    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        return bound?.isAssignableTo(other) ?: true
    }

    override fun toString(): String = name
}

/**
 * `Box<string>` before resolution. Members are read through the instantiation when the base is a
 * generic class (TYPE-14-09), and through the base otherwise (`table<K, V>` has none).
 */
class LuaParameterizedType(
    val baseType: LuaType,
    val arguments: List<LuaType>,
) : LuaType {
    override val name: String = "${baseType.name}<${arguments.joinToString(", ") { it.name }}>"

    /** The base it was last instantiated from, and the result; the base is re-read so its sources replay. */
    @Volatile
    private var memo: Pair<LuaClassType, LuaClassType>? = null

    /** The class this names: the base instantiated with [arguments], or the base itself. */
    fun instantiated(): LuaType {
        val base =
            (if (baseType is LuaTypeReference) baseType.resolved else baseType) as? LuaClassType ?: return baseType
        if (base.typeParameters.isEmpty()) return base
        memo?.let { (from, result) -> if (from === base) return result }
        return LuaGenericSubstitution.instantiate(base, arguments).also { memo = base to it }
    }

    override fun resolveMember(name: String): LuaTypeMember? = instantiated().resolveMember(name)

    override fun getMembers(): Map<String, LuaTypeMember> = instantiated().getMembers()

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableTo(it) }
        }
        if (other is LuaParameterizedType) {
            return baseType.isAssignableTo(other.baseType) &&
                arguments.size == other.arguments.size &&
                arguments.zip(other.arguments).all { (a, b) -> a.isAssignableTo(b) }
        }
        return baseType.isAssignableTo(other)
    }

    override fun toString(): String = name
}

class LuaTableLiteralType(
    val localMembers: Map<String, LuaTypeMember>,
) : LuaType {
    override val name: String = "{ ${localMembers.entries.joinToString(", ") { "${it.key}: ${it.value.type.name}" }} }"

    override fun resolveMember(name: String): LuaTypeMember? = localMembers[name]

    override fun getMembers(): Map<String, LuaTypeMember> = localMembers

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableTo(it) }
        }
        if (other is LuaTableLiteralType) {
            return other.localMembers.all { (name, otherMember) ->
                val thisMember = localMembers[name]
                thisMember != null && thisMember.type.isAssignableTo(otherMember.type)
            }
        }
        return false
    }

    override fun toString(): String = name
}
