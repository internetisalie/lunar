package net.internetisalie.lunar.lang.psi.types

class LuaUnionType(val types: Set<LuaType>) : LuaType {
    override val name: String = types.joinToString(" | ") { it.name }

    override fun resolveMember(name: String): LuaTypeMember? {
        // In a union, we can only resolve a member if it's present in ALL types
        // OR we return a union of the members' types.
        // For now, let's keep it simple: if any type can't resolve it, it's null.
        val members = types.map { it.resolveMember(name) }
        if (members.any { it == null }) return null
        
        // Return a member with a union type of all resolved members
        return LuaTypeMember(name, LuaUnionType(members.map { it!!.type }.toSet()))
    }

    override fun getMembers(): Map<String, LuaTypeMember> {
        val allMembers = mutableMapOf<String, MutableSet<LuaType>>()
        for (type in types) {
            for ((name, member) in type.getMembers()) {
                allMembers.getOrPut(name) { mutableSetOf() }.add(member.type)
            }
        }
        return allMembers.mapValues { (name, typeSet) ->
            LuaTypeMember(name, if (typeSet.size == 1) typeSet.first() else LuaUnionType(typeSet))
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

class LuaArrayType(val elementType: LuaType) : LuaType {
    override val name: String = "${elementType.name}[]"

    override fun resolveMember(name: String): LuaTypeMember? {
        return null
    }

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

class LuaGenericType(override val name: String) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? = null
    override fun getMembers(): Map<String, LuaTypeMember> = emptyMap()

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == LuaPrimitiveType.ANY) return true
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableTo(it) }
        }
        return this == other
    }
    
    override fun toString(): String = name
}

class LuaParameterizedType(val baseType: LuaType, val arguments: List<LuaType>) : LuaType {
    override val name: String = "${baseType.name}<${arguments.joinToString(", ") { it.name }}>"

    override fun resolveMember(name: String): LuaTypeMember? {
        return baseType.resolveMember(name)
    }

    override fun getMembers(): Map<String, LuaTypeMember> = baseType.getMembers()

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

class LuaTableLiteralType(val localMembers: Map<String, LuaTypeMember>) : LuaType {
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
