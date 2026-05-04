package net.internetisalie.lunar.lang.psi.types

class LuaPrimitiveType(override val name: String) : LuaType {
    override fun resolveMember(name: String): LuaTypeMember? = null

    override fun isAssignableTo(other: LuaType): Boolean {
        if (other == ANY) return true
        if (this == other) return true
        if (other is LuaUnionType) {
            return other.types.any { this.isAssignableTo(it) }
        }
        if (this == NIL && (other is LuaUnionType && other.types.contains(NIL))) return true
        return false
    }

    override fun toString(): String = name

    companion object {
        val ANY = LuaPrimitiveType("any")
        val NIL = LuaPrimitiveType("nil")
        val NUMBER = LuaPrimitiveType("number")
        val STRING = LuaPrimitiveType("string")
        val BOOLEAN = LuaPrimitiveType("boolean")
        val VOID = LuaPrimitiveType("void")
        val UNKNOWN = LuaPrimitiveType("unknown")

        val PRIMITIVES = listOf(ANY, NIL, NUMBER, STRING, BOOLEAN, VOID, UNKNOWN).associateBy { it.name }
    }
}
