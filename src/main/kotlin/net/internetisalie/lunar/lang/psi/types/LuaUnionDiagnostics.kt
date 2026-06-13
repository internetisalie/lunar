package net.internetisalie.lunar.lang.psi.types

/**
 * Closest-match diagnostics for union OR-distribution failures (TYPE-09-P3-03).
 *
 * When a value fails against a union and no member is compatible, this names the union member the
 * value most nearly matches structurally and reports that member's specific missing field — a much
 * more actionable message than the generic "not assignable to union".
 *
 * The reason is computed directly here (missing required fields) rather than by re-invoking
 * `checkCompatibility`, which emits to an error list rather than returning a reason; computing it
 * here keeps the helper side-effect-free.
 */
object LuaUnionDiagnostics {

    /** Unions wider than this skip overlap scoring (mirrors the engine's breadth guard). */
    private const val MAX_SCORED_UNION = 100

    /** The union member [value] most nearly matches, plus a concise reason — or null if none is close. */
    data class ClosestMatch(val member: LuaGraphType.Table, val reason: String)

    /**
     * Returns the closest table member of [members] for table [value], or null when [value] is not a
     * table, the union is too wide to score, or no table member shares any field with [value].
     */
    fun closestMatch(value: LuaGraphType, members: Collection<LuaGraphType>): ClosestMatch? {
        if (value !is LuaGraphType.Table || members.size > MAX_SCORED_UNION) return null

        val valueKeys = value.getMembers().keys
        val best = members
            .filterIsInstance<LuaGraphType.Table>()
            .map { member -> Scored(member, overlap(valueKeys, member), missingRequiredFields(value, member)) }
            .filter { it.overlap > 0 }
            .minWithOrNull(compareByDescending<Scored> { it.overlap }.thenBy { it.missing.size })
            ?: return null

        return ClosestMatch(best.member, reasonFor(best.missing))
    }

    private data class Scored(val member: LuaGraphType.Table, val overlap: Int, val missing: List<String>)

    private fun overlap(valueKeys: Set<String>, member: LuaGraphType.Table): Int =
        member.getMembers().keys.count { it in valueKeys }

    /** Required field keys of [member] absent from [value] (a field is required if its read type is not optional). */
    private fun missingRequiredFields(value: LuaGraphType.Table, member: LuaGraphType.Table): List<String> {
        val valueKeys = value.getMembers().keys
        return member.getMembers()
            .filter { (key, node) -> key !in valueKeys && !isOptional(node.read) }
            .keys
            .toList()
    }

    private fun reasonFor(missing: List<String>): String = when {
        missing.isEmpty() -> "incompatible fields"
        missing.size == 1 -> "missing field '${missing.first()}'"
        else -> "missing fields ${missing.joinToString(", ") { "'$it'" }}"
    }

    /** A field is optional when its demanded type is a union containing nil, or is the bottom sentinel. */
    private fun isOptional(type: LuaGraphType): Boolean = when (type) {
        is LuaGraphType.Union -> type.types.any { it == LuaGraphType.Nil }
        LuaGraphType.Undefined -> true
        else -> false
    }
}
