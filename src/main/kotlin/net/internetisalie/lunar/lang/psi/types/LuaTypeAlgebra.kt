package net.internetisalie.lunar.lang.psi.types

/**
 * Canonicalizes union members into a stable, simplified [LuaGraphType].
 *
 * The pipeline is flatten → simplify → dedupe → sort → collapse, so structurally
 * equivalent unions always yield identical shapes (TYPE-09-P1-02/03/04).
 */
object LuaTypeAlgebra {
    /**
     * Produces the canonical type for a union of [members].
     *
     * Nested unions are flattened, `Any` absorbs scalar members but structural arms survive
     * beside it (BUG-397), an `Undefined` member beside others becomes an `Any` arm, because a
     * union that cannot account for one member cannot rule anything out, duplicates collapse,
     * members are sorted by display name, and a single member collapses to itself. `nil` is a
     * normal distinct member and is never dropped.
     */
    fun canonicalize(members: Collection<LuaGraphType>): LuaGraphType {
        val flattened = flatten(members)
        val simplified = simplify(flattened) ?: return LuaGraphType.Any
        return collapse(sortedDistinct(simplified))
    }

    private fun flatten(members: Collection<LuaGraphType>): List<LuaGraphType> {
        val result = mutableListOf<LuaGraphType>()
        for (member in members) {
            if (member is LuaGraphType.Union) {
                result.addAll(flatten(member.types))
            } else {
                result.add(member)
            }
        }
        return result
    }

    /**
     * Returns simplified members, or `null` to signal the union reduces to [LuaGraphType.Any].
     *
     * An `Any` arm absorbs every scalar member, but structural arms (tables, arrays, functions)
     * survive beside it: `---@return any|{ err: string }` promises a narrowable shape, and
     * collapsing it to `any` is what erased `reply.err` in the reverted BUG-397 attempts. A union
     * that keeps an `Any` arm is *gradual* — [LuaTypeGraph] never reports an assignability error
     * against it, so the preserved arms only ever add information. An `Undefined` member beside
     * others is the engine saying it cannot tell, so it joins as `Any`; alone it is still no
     * information.
     */
    private fun simplify(members: List<LuaGraphType>): List<LuaGraphType>? {
        val known = members.filter { it != LuaGraphType.Undefined }
        if (known.isEmpty()) return members
        val gradual = if (known.size < members.size) known + LuaGraphType.Any else known
        if (gradual.none { it is LuaGraphType.Any }) return gradual
        val structural = gradual.filter { it.isStructural() }
        if (structural.isEmpty()) return null
        return listOf(LuaGraphType.Any) + structural
    }

    private fun LuaGraphType.isStructural(): Boolean =
        this is LuaGraphType.Table || this is LuaGraphType.Array || this is LuaGraphType.Function

    private fun sortedDistinct(members: List<LuaGraphType>): Set<LuaGraphType> {
        val deduped = LinkedHashSet(members)
        return deduped.sortedBy { it.displayName() }.toCollection(LinkedHashSet())
    }

    private fun collapse(members: Set<LuaGraphType>): LuaGraphType =
        when (members.size) {
            0 -> LuaGraphType.Undefined
            1 -> members.first()
            else -> LuaGraphType.Union(members)
        }

    /**
     * Removes [toRemove] from [union] and re-canonicalizes the remaining members (TYPE-08).
     *
     * Delegates to [canonicalize], so an empty result collapses to [LuaGraphType.Undefined] and a
     * single remaining member collapses to itself. Used by flow-sensitive narrowing to compute the
     * complement type ("original type minus the guard type") for `else`/`elseif` branches.
     */
    fun subtractMember(
        union: LuaGraphType.Union,
        toRemove: LuaGraphType,
    ): LuaGraphType = canonicalize(union.types.filterNot { it == toRemove })

    /**
     * Whether a field demanded as [type] may be absent: a missing field reads as `nil`, so a union with
     * `nil` admits it, and so does an unknown type, which cannot rule `nil` out.
     */
    fun isOptional(type: LuaGraphType): Boolean =
        when (type) {
            is LuaGraphType.Union -> type.types.any { it == LuaGraphType.Nil }
            LuaGraphType.Undefined, LuaGraphType.Any -> true
            else -> false
        }
}
